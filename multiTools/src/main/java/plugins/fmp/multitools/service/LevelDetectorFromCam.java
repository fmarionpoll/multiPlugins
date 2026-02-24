package plugins.fmp.multitools.service;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import icy.image.IcyBufferedImage;
import icy.system.SystemUtil;
import icy.system.thread.Processor;
import icy.type.DataType;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.Comparators;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.ROI2D.AlongT;
import plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformInterface;

/**
 * Detects capillary liquid levels directly from cam images (no kymograph). For
 * each time bin, loads the cam frame, extracts a 1D profile along each
 * capillary ROI, applies pass1/pass2 transforms and transition detection, and
 * stores the same polylineLevel format as LevelDetector.
 */
public class LevelDetectorFromCam {

	private static final int JITTER_PASS1 = 10;

	public void detectLevels(Experiment exp, BuildSeriesOptions options) {
		Capillaries capillaries = exp.getCapillaries();
		if (capillaries == null || capillaries.getList().isEmpty())
			return;
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

		for (Capillary cap : toProcess) {
			cap.getProperties().getLimitsOptions().copyFrom(options);
			cap.getTopLevelDirect().limit = new int[nTimeBins];
			cap.getBottomLevelDirect().limit = new int[nTimeBins];
		}

		SequenceLoaderService loader = new SequenceLoaderService();
		ImageTransformInterface transformPass1 = options.transform01.getFunction();

		int diskRadius = Math.max(0, options.diskRadius);
		LevelDetectorFromKymo levelDetector = new LevelDetectorFromKymo();

		Processor processor = new Processor(SystemUtil.getNumberOfCPUs());
		processor.setThreadName("detectlevel-cam");
		processor.setPriority(Processor.NORM_PRIORITY);
		ArrayList<Future<?>> futures = new ArrayList<>();

		for (int t = 0; t < nTimeBins; t++) {
			long timeMs = firstMs + t * stepMs;
			final int camFrameIndex = exp.findNearestIntervalWithBinarySearch(timeMs, 0, Math.max(0, nCamFrames - 1));
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
					AlongT at = cap.getAlongTAtT(camFrameIndex);
					if (at == null || at.getRoi() == null)
						continue;
					List<ArrayList<int[]>> masks = options.profilePerpendicular
							? CapillaryProfileExtractor.buildMasksAlongRoiPerpendicular(at.getRoi(), camW, camH,
									diskRadius)
							: CapillaryProfileExtractor.buildMasksAlongRoi(at.getRoi(), camW, camH, diskRadius);
					if (masks.isEmpty())
						continue;
					int[][] rgbProfile = CapillaryProfileExtractor.extractRgbProfileFromMasks(camImage, masks);
					if (rgbProfile == null || rgbProfile[0].length == 0)
						continue;

					IcyBufferedImage thinImage = rgbProfileToImage(rgbProfile);
					int profileLen = rgbProfile[0].length;
					Rectangle searchRect = new Rectangle(0, 0, 1, profileLen);
					detectPass1OneColumn(thinImage, transformPass1, cap, profileLen, searchRect, timeIndex, options,
							levelDetector);

				}
			}));
		}

		waitFutures(processor, futures);

		int columnFirst = 0;
		int columnLast = nTimeBins - 1;
		for (Capillary cap : toProcess) {
			String name = cap.getLast2ofCapillaryName();
			if (name != null) {
				if (cap.getTopLevelDirect() != null)
					cap.getTopLevelDirect().setPolylineLevelFromTempData(name + "_topleveldirect", 0, columnFirst,
							columnLast);
				if (cap.getBottomLevelDirect() != null)
					cap.getBottomLevelDirect().setPolylineLevelFromTempData(name + "_bottomleveldirect", 0, columnFirst,
							columnLast);
			}
			cap.getTopLevelDirect().limit = null;
			cap.getBottomLevelDirect().limit = null;
		}

		exp.save_capillaries_description_and_measures();
		exp.saveMCCapillaries_Only();
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
			LevelDetectorFromKymo levelDetector) {
		CanvasImageTransformOptions transformOptions = new CanvasImageTransformOptions();
		IcyBufferedImage transformed = transformPass1.getTransformedImage(thinImage, transformOptions);
		Object data = transformed.getDataXY(0);
		int[] arr = Array1DUtil.arrayToIntArray(data, transformed.isSignedDataType());

		int imageWidth = 1;
		int imageHeight = profileLen;
		int ix = 0;
		int topSearchFrom = timeIndex > 0 ? capi.getTopLevelDirect().limit[timeIndex - 1] : 0;
		int iyTop = detectThresholdFromTop(ix, topSearchFrom, JITTER_PASS1, arr, imageWidth, imageHeight, options,
				searchRect);
		int iyBottom = detectThresholdFromBottom(ix, JITTER_PASS1, arr, imageWidth, imageHeight, options, searchRect);
		if (iyBottom <= iyTop)
			iyTop = topSearchFrom;
		capi.getTopLevelDirect().limit[timeIndex] = iyTop;
		capi.getBottomLevelDirect().limit[timeIndex] = iyBottom;
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

	private static void waitFutures(Processor processor, ArrayList<Future<?>> futures) {
		for (Future<?> f : futures) {
			try {
				f.get();
			} catch (ExecutionException | InterruptedException e) {
				Logger.error("LevelDetectorFromCam:waitFutures", e);
			}
		}
		processor.shutdown();
	}
}
