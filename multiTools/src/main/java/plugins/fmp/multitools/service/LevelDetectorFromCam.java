package plugins.fmp.multitools.service;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import icy.image.IcyBufferedImage;
import icy.roi.ROI2D;
import icy.system.SystemUtil;
import icy.system.thread.Processor;
import icy.type.DataType;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multitools.experiment.capillaries.DetectionProvenanceSupport;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.Comparators;
import plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformInterface;

/**
 * Detects capillary liquid levels directly from cam images (no kymograph). For
 * each time bin, loads the cam frame, extracts a 1D profile along each
	 * capillary ROI, applies the selected transform and transition detection, and
 * stores the same polylineLevel format as LevelDetector.
 */
public class LevelDetectorFromCam {

	private static final int JITTER_PASS1 = 10;

	public void detectLevels(Experiment exp, BuildSeriesOptions options) {
		Capillaries capillaries = exp.getCapillaries();
		if (capillaries == null || capillaries.getList().isEmpty())
			return;
		exp.setGenerationMode(plugins.fmp.multitools.experiment.GenerationMode.DIRECT_FROM_STACK);
		Collections.sort(capillaries.getList(), new Comparators.Capillary_ROIName());

		int nCamFrames = exp.getSeqCamData().getImageLoader().getNTotalFrames();
		if (nCamFrames <= 0)
			return;

		long firstMs = exp.getKymoFirst_ms();
		long lastMs = exp.getKymoLast_ms();
		long stepMs = exp.getKymoBin_ms();
		if (stepMs <= 0)
			stepMs = 1;
		if (firstMs == 0 && lastMs == 0) {
			long camFirst = exp.getCamImageFirst_ms();
			long camLast = exp.getCamImageLast_ms();
			if (camFirst >= 0 && camLast > camFirst) {
				firstMs = 0;
				lastMs = camLast - camFirst;
				long camBin = exp.getCamImageBin_ms();
				if (camBin > 0)
					stepMs = camBin;
			} else {
				firstMs = 0;
				lastMs = (nCamFrames - 1) * stepMs;
			}
		}
		int nTimeBins = (int) ((lastMs - firstMs) / stepMs + 1);
		if (nTimeBins <= 0)
			return;

		List<Capillary> built = buildCapillariesToProcess(capillaries, options);
		if (options.detectSelectedKymo) {
			int selectedKymoIdx = capillaries.getSelectedCapillary();
			if (selectedKymoIdx >= 0) {
				List<Capillary> filtered = new ArrayList<>();
				for (Capillary cap : built) {
					if (cap.getKymographIndex() == selectedKymoIdx) {
						filtered.add(cap);
						break;
					}
				}
				built = filtered;
			}
		}
		final List<Capillary> toProcess = built;
		if (toProcess.isEmpty())
			return;
		int[] cameraFrames = new int[nTimeBins];
		for (int t = 0; t < nTimeBins; t++) {
			cameraFrames[t] = exp.findNearestIntervalWithBinarySearch(firstMs + t * stepMs, 0, nCamFrames - 1);
			// Validate blue geometry before replacing any measurements.
			for (Capillary cap : toProcess)
				DirectCapillarySampling.roiAt(cap, cameraFrames[t], options);
		}
		Map<Capillary, double[]> pixelSteps = new HashMap<>();
		Map<Capillary, double[]> originOffsets = new HashMap<>();

		for (Capillary cap : toProcess) {
			cap.clearKymoMeasuresOnly();
			pixelSteps.put(cap, new double[nTimeBins]);
			originOffsets.put(cap, new double[nTimeBins]);
			DetectionProvenanceSupport.copyLevelRecipeTo(cap.getProperties().getLimitsOptions(), options);
			cap.getTopRaw().limit = new int[nTimeBins];
			cap.getBottomRaw().limit = new int[nTimeBins];
		}

		SequenceLoaderService loader = new SequenceLoaderService();
		ImageTransformInterface transformPass1 = options.transform01.getFunction();

		int diskRadius = Math.max(0, options.diskRadius);

		Processor processor = new Processor(SystemUtil.getNumberOfCPUs());
		processor.setThreadName("detectlevel-cam");
		processor.setPriority(Processor.NORM_PRIORITY);
		ArrayList<Future<?>> futures = new ArrayList<>();

		try {
			for (int t = 0; t < nTimeBins; t++) {
				final int camFrameIndex = cameraFrames[t];
				if (camFrameIndex < 0 || camFrameIndex >= nCamFrames)
					continue;

				String path = exp.getSeqCamData().getFileNameFromImageList(camFrameIndex);
				final IcyBufferedImage camImage = loader.imageIORead(path);
				if (camImage == null)
					continue;

				final int timeIndex = t;
				final int camW = camImage.getSizeX();
				final int camH = camImage.getSizeY();

				futures.add(processor.submit(() -> {
					for (int capIdx = 0; capIdx < toProcess.size(); capIdx++) {
						Capillary cap = toProcess.get(capIdx);
						ROI2D samplingRoi = DirectCapillarySampling.roiAt(cap, camFrameIndex, options);
						if (samplingRoi == null)
							continue;
						List<ArrayList<int[]>> masks = options.profilePerpendicular
								? CapillaryProfileExtractor.buildMasksAlongRoiPerpendicular(samplingRoi, camW, camH,
										diskRadius)
								: CapillaryProfileExtractor.buildMasksAlongRoi(samplingRoi, camW, camH, diskRadius);
						if (masks.isEmpty())
							continue;
						int[][] rgbProfile = CapillaryProfileExtractor.extractRgbProfileFromMasks(camImage, masks);
						if (rgbProfile == null || rgbProfile[0].length == 0)
							continue;

						IcyBufferedImage thinImage = rgbProfileToImage(rgbProfile);
						int profileLen = rgbProfile[0].length;
						pixelSteps.get(cap)[timeIndex] = DirectCapillarySampling.pixelStep(samplingRoi, profileLen, options);
						originOffsets.get(cap)[timeIndex] = DirectCapillarySampling.originOffset(samplingRoi, options);
						int previousLevel = timeIndex > 0 ? cap.getTopRaw().limit[timeIndex - 1] : 0;
						if (timeIndex > 0 && options.directSampling != BuildSeriesOptions.DirectSampling.GREEN) {
							double previousStep = pixelSteps.get(cap)[timeIndex - 1];
							double nativeLevel = previousLevel * previousStep - originOffsets.get(cap)[timeIndex - 1];
							previousLevel = previousStep > 0 ? (int) Math.round((nativeLevel
									+ originOffsets.get(cap)[timeIndex]) / pixelSteps.get(cap)[timeIndex]) : 0;
						}
						Rectangle searchRect = new Rectangle(0, 0, 1, profileLen);
						detectPass1OneColumn(thinImage, transformPass1, cap, profileLen, searchRect, timeIndex, options,
								previousLevel);

					}
				}));
				// Detection uses the preceding frame's level: finish it before advancing.
				waitFutures(futures);
				futures.clear();
			}
		} finally { processor.shutdown(); }

		int columnFirst = 0;
		int columnLast = nTimeBins - 1;
		for (Capillary cap : toProcess) {
			String name = cap.getLast2ofCapillaryName();
			if (name != null) {
				if (cap.getTopRaw() != null)
					cap.getTopRaw().setPolylineLevelFromTempData(name + "_toplevel", 0, columnFirst,
							columnLast);
				if (cap.getBottomRaw() != null)
					cap.getBottomRaw().setPolylineLevelFromTempData(name + "_bottomlevel", 0, columnFirst,
							columnLast);
			}
			cap.getTopRaw().limit = null;
			cap.getBottomRaw().limit = null;
			if (options.directSampling != BuildSeriesOptions.DirectSampling.GREEN) {
				for (int t = 0; t < nTimeBins; t++) {
					double scale = pixelSteps.get(cap)[t];
					double offset = originOffsets.get(cap)[t];
					cap.getTopRaw().polylineLevel.ypoints[t] = scale > 0
							? cap.getTopRaw().polylineLevel.ypoints[t] * scale - offset : Double.NaN;
					cap.getBottomRaw().polylineLevel.ypoints[t] = scale > 0
							? cap.getBottomRaw().polylineLevel.ypoints[t] * scale - offset : Double.NaN;
				}
			}
		}

		exp.save_capillaries_description_and_measures();
		exp.saveMCCapillaries_Only();
		exp.applyLevelDetectionDefaultsFrom(options);
		exp.saveExperimentDescriptors();
	}

	private static List<Capillary> buildCapillariesToProcess(Capillaries capillaries, BuildSeriesOptions options) {
		List<Capillary> toProcess = new ArrayList<>();
		for (Capillary cap : capillaries.getList()) {
			if (!options.detectR && cap.getKymographName() != null && cap.getKymographName().endsWith("2"))
				continue;
			if (!options.detectL && cap.getKymographName() != null && cap.getKymographName().endsWith("1"))
				continue;
			toProcess.add(cap);
		}
		return toProcess;
	}

	private static IcyBufferedImage rgbProfileToImage(int[][] rgbProfile) {
		int h = rgbProfile[0].length;
		IcyBufferedImage img = new IcyBufferedImage(1, h, 3, DataType.UBYTE);
		for (int c = 0; c < 3; c++) {
			byte[] channel = new byte[h];
			for (int y = 0; y < h; y++) {
				int v = rgbProfile[c][y];
				if (v < 0)
					v = 0;
				if (v > 255)
					v = 255;
				channel[y] = (byte) v;
			}
			img.setDataXY(c, channel);
		}
		return img;
	}

	private static void detectPass1OneColumn(IcyBufferedImage thinImage, ImageTransformInterface transformPass1,
			Capillary capi, int profileLen, Rectangle searchRect, int timeIndex, BuildSeriesOptions options,
			int topSearchFrom) {
		CanvasImageTransformOptions transformOptions = new CanvasImageTransformOptions();
		IcyBufferedImage transformed = transformPass1.getTransformedImage(thinImage, transformOptions);
		Object data = transformed.getDataXY(0);
		int[] arr = Array1DUtil.arrayToIntArray(data, transformed.isSignedDataType());

		int imageWidth = 1;
		int imageHeight = profileLen;
		int ix = 0;
		int iyTop = detectThresholdFromTop(ix, topSearchFrom, JITTER_PASS1, arr, imageWidth, imageHeight, options,
				searchRect);
		int iyBottom = detectThresholdFromBottom(ix, JITTER_PASS1, arr, imageWidth, imageHeight, options, searchRect);
		if (iyBottom <= iyTop)
			iyTop = topSearchFrom;
		capi.getTopRaw().limit[timeIndex] = iyTop;
		capi.getBottomRaw().limit[timeIndex] = iyBottom;
	}

	private static int detectThresholdFromTop(int ix, int searchFrom, int jitter, int[] tabValues, int imageWidth,
			int imageHeight, BuildSeriesOptions options, Rectangle searchRect) {
		int y = imageHeight - 1;
		searchFrom = Math.max(0, Math.min(searchFrom - jitter, imageHeight - 1));
		if (searchFrom < searchRect.y)
			searchFrom = searchRect.y;
		for (int iy = searchFrom; iy < imageHeight; iy++) {
			boolean flag = options.directionUp1 ? tabValues[ix + iy * imageWidth] > options.detectLevel1Threshold
					: tabValues[ix + iy * imageWidth] < options.detectLevel1Threshold;
			if (flag) {
				y = iy;
				break;
			}
		}
		return y;
	}

	private static int detectThresholdFromBottom(int ix, int jitter, int[] tabValues, int imageWidth, int imageHeight,
			BuildSeriesOptions options, Rectangle searchRect) {
		int y = 0;
		int searchFrom = Math.min(imageHeight - 1, searchRect.y + searchRect.height - 1);
		for (int iy = searchFrom; iy >= 0; iy--) {
			boolean flag = options.directionUp1 ? tabValues[ix + iy * imageWidth] > options.detectLevel1Threshold
					: tabValues[ix + iy * imageWidth] < options.detectLevel1Threshold;
			if (flag) {
				y = iy;
				break;
			}
		}
		return y;
	}

	private static void waitFutures(ArrayList<Future<?>> futures) {
		for (Future<?> f : futures) {
			try {
				f.get();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Direct detection interrupted", e);
			} catch (ExecutionException e) {
				throw new IllegalStateException("Direct detection failed", e.getCause());
			}
		}
	}
}
