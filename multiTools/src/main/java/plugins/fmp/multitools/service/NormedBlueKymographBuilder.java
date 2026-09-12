package plugins.fmp.multitools.service;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import icy.gui.frame.progress.ProgressFrame;
import icy.image.IcyBufferedImage;
import icy.system.SystemUtil;
import icy.system.thread.Processor;
import icy.type.DataType;
import icy.type.collection.array.Array1DUtil;
import loci.formats.FormatException;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.geometry.NormedBlueKymoGeometry;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.Comparators;
import plugins.fmp.multitools.tools.Logger;

/**
 * Kymograph builder that samples expanded blue physical lines onto a constant
 * height (max expanded length). Does not modify green {@code roiCap} / AlongT.
 */
public class NormedBlueKymographBuilder {

	private final Map<Capillary, ArrayList<int[]>> capIntegerArrays = new HashMap<Capillary, ArrayList<int[]>>();

	public boolean buildKymograph(Experiment exp, BuildSeriesOptions options) {
		if (exp == null || exp.getCapillaries() == null || exp.getCapillaries().getList().size() < 1) {
			Logger.warn("NormedBlueKymographBuilder: abort, no capillaries");
			return false;
		}

		prepareCapillaries(exp);
		double expansion = expansionRatio(options);
		int withBlue = 0;
		for (Capillary cap : exp.getCapillaries().getList()) {
			if (!cap.getPhaseGeometry().isInitialized()) {
				cap.setKymographBuild(false);
				Logger.warn("NormedBlueKymographBuilder: skip " + capName(cap) + " (no blue geometry)");
			} else {
				withBlue++;
			}
		}
		if (withBlue < 1) {
			Logger.warn("NormedBlueKymographBuilder: abort, no initialized blue geometry");
			return false;
		}

		int anyHeight = 0;
		for (Capillary cap : exp.getCapillaries().getList()) {
			if (!cap.getKymographBuild())
				continue;
			int h = NormedBlueKymoGeometry.kymoHeight(cap, expansion);
			if (h > anyHeight)
				anyHeight = h;
		}
		if (anyHeight < 2) {
			Logger.warn("NormedBlueKymographBuilder: abort, expanded blue height < 2");
			return false;
		}

		SequenceLoaderService loader = new SequenceLoaderService();
		long camReferenceMs = exp.getSeqCamData().getFirstValidFrameEpochMs();
		if (camReferenceMs < 0)
			camReferenceMs = exp.getCamImageFirst_ms();
		if (camReferenceMs < 0)
			camReferenceMs = 0;
		exp.build_MsTimeIntervalsArray_From_SeqCamData_FileNamesList(camReferenceMs);

		long first_ms = 0;
		long last_ms = exp.getSeqCamData().getTimeManager().getBinLast_ms();
		if (last_ms <= first_ms && exp.getKymoLast_ms() > exp.getKymoFirst_ms()) {
			first_ms = exp.getKymoFirst_ms();
			last_ms = exp.getKymoLast_ms();
		}

		int nTotalFrames = exp.getSeqCamData().getImageLoader().getNTotalFrames();
		long[] camImages_ms = exp.getCamImages_ms();
		if (camImages_ms != null && nTotalFrames > 0 && nTotalFrames <= camImages_ms.length)
			last_ms = Math.min(last_ms, camImages_ms[nTotalFrames - 1]);

		ArrayList<Integer> frameIndices = new ArrayList<Integer>();
		boolean fixedWindow = options != null && options.isFrameFixed;
		for (int i = 0; i < nTotalFrames; i++) {
			if (fixedWindow) {
				long t = (camImages_ms != null && i < camImages_ms.length) ? camImages_ms[i] : -1;
				if (t < 0 || t < first_ms || t > last_ms)
					continue;
			}
			frameIndices.add(i);
		}
		if (frameIndices.isEmpty() && nTotalFrames > 0) {
			Logger.warn("NormedBlueKymographBuilder: fixed window excluded all frames; using all " + nTotalFrames);
			for (int i = 0; i < nTotalFrames; i++)
				frameIndices.add(i);
		}
		int factor = 1;
		if (options != null && options.kymoDownsampleFactor > 1)
			factor = options.kymoDownsampleFactor;
		if (factor > 1 && frameIndices.size() > 1) {
			ArrayList<Integer> subsampled = new ArrayList<Integer>();
			for (int i = 0; i < frameIndices.size(); i += factor)
				subsampled.add(frameIndices.get(i));
			frameIndices = subsampled;
		}
		int expectedWidth = Math.max(1, frameIndices.size());
		Logger.info("NormedBlueKymographBuilder: kymo width=" + expectedWidth + " (frames=" + nTotalFrames
				+ ", downsample=x" + factor + ", expand=" + expansion + ", per-cap height)");
		if (expectedWidth <= 0 || nTotalFrames <= 0) {
			Logger.error("NormedBlueKymographBuilder: no frames to process");
			return false;
		}

		SequenceCamData seqCamData = exp.getSeqCamData();
		if (seqCamData.getSequence() == null)
			seqCamData.setSequence(seqCamData.getImageLoader()
					.initSequenceFromFirstImage(seqCamData.getImagesList(true)));
		final int kymoSizeC = Math.max(1, seqCamData.getSequence().getSizeC());
		final int refSizex = seqCamData.getSequence().getSizeX();
		final int refSizey = seqCamData.getSequence().getSizeY();
		allocateKymoBuffers(exp.getCapillaries().getList(), expectedWidth, kymoSizeC, expansion);

		ProgressFrame progress = new ProgressFrame("Analyze series (blue)");
		final Processor processor = new Processor(SystemUtil.getNumberOfCPUs());
		processor.setThreadName("buildNormedBlueKymograph");
		processor.setPriority(Processor.NORM_PRIORITY);
		ArrayList<Future<?>> tasks = new ArrayList<Future<?>>();

		for (int iToColumn = 0; iToColumn < expectedWidth; iToColumn++) {
			int sourceImageIndex = frameIndices.get(iToColumn);
			if (sourceImageIndex < 0)
				continue;
			final int viewT = sourceImageIndex;
			final int kymographColumn = iToColumn;
			progress.setMessage("Processing file: " + (sourceImageIndex + 1) + "//" + nTotalFrames);
			final IcyBufferedImage sourceImage = loader
					.imageIORead(seqCamData.getFileNameFromImageList(sourceImageIndex));
			if (sourceImage == null) {
				Logger.warn("NormedBlueKymographBuilder: could not read frame " + sourceImageIndex);
				continue;
			}
			tasks.add(processor.submit(() -> {
				for (Capillary capi : exp.getCapillaries().getList()) {
					if (!capi.getKymographBuild())
						continue;
					analyzeImageUnderCapillary(sourceImage, capi, viewT, kymographColumn,
							capi.getCap_Image() != null ? capi.getCap_Image().getHeight() : 0, refSizex, refSizey,
							options);
				}
			}));
		}
		progress.close();
		waitFuturesCompletion(processor, tasks);

		if (options != null && options.doCreateBinDir) {
			String previousBinDir = exp.getBinSubDirectory();
			String binDir = KymographBuilder.chooseWritableBinSubDirectoryForKymograph(exp,
					exp.getBinNameFromKymoFrameStep(), options);
			exp.setBinSubDirectory(binDir);
			exp.setGenerationMode(plugins.fmp.multitools.experiment.GenerationMode.KYMOGRAPH);
			if (exp.getActiveBinDescription() != null) {
				exp.getActiveBinDescription().setKymoFromNormedBlue(true);
				exp.getActiveBinDescription().setKymoBlueExpansionRatio(expansion);
			}
			exp.saveBinDescription(binDir);
			if (previousBinDir != null && !previousBinDir.equals(binDir))
				KymographBuilder.copyMeasuresBetweenBinsForExperiment(exp, previousBinDir, binDir);
		}

		KymographBuilder.preArchiveExistingKymographsInCurrentBin(exp);
		exportCapillaryKymographs(exp);
		return true;
	}

	void allocateKymoBuffers(List<Capillary> capillaries, int imageWidth, int sizeC, double expansion) {
		int kymoSizeC = Math.max(1, sizeC);
		capIntegerArrays.clear();
		for (Capillary cap : capillaries) {
			if (cap == null || !cap.getKymographBuild())
				continue;
			int imageHeight = NormedBlueKymoGeometry.kymoHeight(cap, expansion);
			cap.setCap_Image(new IcyBufferedImage(imageWidth, imageHeight, kymoSizeC, DataType.UBYTE));
			int len = imageWidth * imageHeight;
			ArrayList<int[]> capInteger = new ArrayList<int[]>(kymoSizeC);
			for (int chan = 0; chan < kymoSizeC; chan++)
				capInteger.add(new int[len]);
			capIntegerArrays.put(cap, capInteger);
		}
	}

	void allocateKymoBuffers(List<Capillary> capillaries, int imageWidth, int imageHeight, int sizeC) {
		int kymoSizeC = Math.max(1, sizeC);
		capIntegerArrays.clear();
		for (Capillary cap : capillaries) {
			if (cap == null || !cap.getKymographBuild())
				continue;
			cap.setCap_Image(new IcyBufferedImage(imageWidth, imageHeight, kymoSizeC, DataType.UBYTE));
			int len = imageWidth * imageHeight;
			ArrayList<int[]> capInteger = new ArrayList<int[]>(kymoSizeC);
			for (int chan = 0; chan < kymoSizeC; chan++)
				capInteger.add(new int[len]);
			capIntegerArrays.put(cap, capInteger);
		}
	}

	void analyzeImageUnderCapillary(IcyBufferedImage sourceImage, Capillary cap, int t, int kymographColumn, int height,
			int refSizex, int refSizey, BuildSeriesOptions options) {
		if (sourceImage == null || cap == null || height < 2)
			return;
		if (sourceImage.getWidth() != refSizex || sourceImage.getHeight() != refSizey)
			Logger.warn("NormedBlueKymographBuilder: source size " + sourceImage.getWidth() + "x"
					+ sourceImage.getHeight() + " differs from reference " + refSizex + "x" + refSizey + " (t=" + t
					+ ")");
		Line2D sampling = NormedBlueKymoGeometry.expandedSamplingLine(cap, t, expansionRatio(options));
		if (sampling == null) {
			Logger.warn("NormedBlueKymographBuilder: no expanded blue at t=" + t + " cap=" + capName(cap));
			return;
		}
		IcyBufferedImage capImage = cap.getCap_Image();
		ArrayList<int[]> capInteger = capIntegerArrays.get(cap);
		if (capImage == null || capInteger == null || capInteger.isEmpty())
			return;
		int kymoImageWidth = capImage.getWidth();
		int kymoSizeC = Math.max(1, capImage.getSizeC());
		int srcW = sourceImage.getWidth();
		int srcH = sourceImage.getHeight();
		final int srcSizeC = Math.max(1, sourceImage.getSizeC());
		int[] src0 = Array1DUtil.arrayToIntArray(sourceImage.getDataXY(0), sourceImage.isSignedDataType());
		int[] src1 = (srcSizeC > 1)
				? Array1DUtil.arrayToIntArray(sourceImage.getDataXY(1), sourceImage.isSignedDataType())
				: null;
		int[] src2 = (srcSizeC > 2)
				? Array1DUtil.arrayToIntArray(sourceImage.getDataXY(2), sourceImage.isSignedDataType())
				: null;
		int diskRadius = options != null ? Math.max(0, options.diskRadius) : 0;
		Point2D[] samples = NormedBlueKymoGeometry.sampleArc(sampling, height);
		for (int row = 0; row < height && row < capImage.getHeight(); row++) {
			Point2D pt = samples[row];
			int px = (int) Math.round(pt.getX());
			int py = (int) Math.round(pt.getY());
			if (px < 0 || py < 0 || px >= srcW || py >= srcH)
				continue;
			ArrayList<int[]> mask = diskAt(px, py, diskRadius, srcW, srcH);
			if (mask.isEmpty())
				continue;
			long sum0 = 0, sum1 = 0, sum2 = 0;
			for (int[] m : mask) {
				int idx = m[0] + m[1] * srcW;
				sum0 += src0[idx];
				if (src1 != null)
					sum1 += src1[idx];
				if (src2 != null)
					sum2 += src2[idx];
			}
			int dst = row * kymoImageWidth + kymographColumn;
			capInteger.get(0)[dst] = (int) (sum0 / mask.size());
			if (kymoSizeC > 1 && src1 != null && capInteger.size() > 1)
				capInteger.get(1)[dst] = (int) (sum1 / mask.size());
			if (kymoSizeC > 2 && src2 != null && capInteger.size() > 2)
				capInteger.get(2)[dst] = (int) (sum2 / mask.size());
		}
	}

	void commitBuffersToCapImages() {
		for (Map.Entry<Capillary, ArrayList<int[]>> entry : capIntegerArrays.entrySet()) {
			IcyBufferedImage capImage = entry.getKey().getCap_Image();
			ArrayList<int[]> capInteger = entry.getValue();
			if (capImage == null || capInteger == null)
				continue;
			boolean signed = capImage.isSignedDataType();
			int nch = Math.min(capImage.getSizeC(), capInteger.size());
			for (int chan = 0; chan < nch; chan++) {
				Object destArray = capImage.getDataXY(chan);
				Array1DUtil.intArrayToSafeArray(capInteger.get(chan), 0, destArray, 0, -1, signed, signed);
				capImage.setDataXY(chan, destArray);
			}
		}
	}

	private void exportCapillaryKymographs(Experiment exp) {
		String directory = exp.getDirectoryToSaveResults();
		if (directory == null)
			return;
		commitBuffersToCapImages();
		final Processor processor = new Processor(SystemUtil.getNumberOfCPUs());
		processor.setThreadName("exportNormedBlueKymograph");
		processor.setPriority(Processor.NORM_PRIORITY);
		ArrayList<Future<?>> tasks = new ArrayList<Future<?>>();
		for (Capillary cap : exp.getCapillaries().getList()) {
			if (!cap.getKymographBuild())
				continue;
			final Capillary capi = cap;
			tasks.add(processor.submit(() -> {
				IcyBufferedImage capImage = capi.getCap_Image();
				String filename = directory + File.separator + capi.getKymographFileName();
				try {
					KymographBuilder.saveKymographTiffSafely(capImage, new File(filename));
					capi.setCap_Image(null);
					capIntegerArrays.remove(capi);
				} catch (FormatException e) {
					Logger.error("NormedBlueKymographBuilder: format error saving " + filename, e);
				} catch (IOException e) {
					Logger.error("NormedBlueKymographBuilder: IO error saving " + filename, e);
				}
			}));
		}
		waitFuturesCompletion(processor, tasks);
	}

	private void prepareCapillaries(Experiment exp) {
		Collections.sort(exp.getCapillaries().getList(), new Comparators.Capillary_ROIName());
		int index = 0;
		for (Capillary cap : exp.getCapillaries().getList()) {
			String roiName = cap.getRoiName();
			if (roiName != null) {
				String kymographName = Capillary.replace_LR_with_12(roiName);
				cap.setKymographName(kymographName);
				cap.setKymographFileName(kymographName + ".tiff");
			}
			if (cap.getKymographIndex() < 0)
				cap.setKymographIndex(index);
			index++;
			cap.setKymographBuild(true);
		}
	}

	private static double expansionRatio(BuildSeriesOptions options) {
		if (options == null || !Double.isFinite(options.kymoBlueExpansionRatio) || options.kymoBlueExpansionRatio < 0)
			return 0.10;
		return options.kymoBlueExpansionRatio;
	}

	private static String capName(Capillary cap) {
		if (cap.getRoiName() != null)
			return cap.getRoiName();
		return cap.getKymographName();
	}

	private static ArrayList<int[]> diskAt(int x, int y, int diskRadius, int sizex, int sizey) {
		ArrayList<int[]> mask = new ArrayList<int[]>();
		int radius = Math.max(0, diskRadius);
		double radiusSquared = (double) radius * radius;
		int minX = clip(x - radius, 0, sizex - 1);
		int maxX = clip(x + radius, minX, sizex - 1);
		int minY = clip(y, 0, sizey - 1);
		int maxY = minY;
		for (int ix = minX; ix <= maxX; ix++) {
			for (int iy = minY; iy <= maxY; iy++) {
				double dx = ix - x;
				double dy = iy - y;
				if (dx * dx + dy * dy <= radiusSquared)
					mask.add(new int[] { ix, iy });
			}
		}
		return mask;
	}

	private static int clip(int v, int min, int max) {
		if (v < min)
			return min;
		if (v > max)
			return max;
		return v;
	}

	private static void waitFuturesCompletion(Processor processor, ArrayList<Future<?>> futuresArray) {
		while (!futuresArray.isEmpty()) {
			final Future<?> f = futuresArray.get(futuresArray.size() - 1);
			try {
				f.get();
			} catch (ExecutionException e) {
				Logger.error("NormedBlueKymographBuilder: execution exception", e);
			} catch (InterruptedException e) {
				Logger.warn("NormedBlueKymographBuilder: interrupted: " + e.getMessage());
				Thread.currentThread().interrupt();
			}
			futuresArray.remove(f);
		}
	}
}
