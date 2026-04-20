package plugins.fmp.multitools.service;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import icy.file.Saver;
import icy.gui.frame.progress.ProgressFrame;
import icy.image.IcyBufferedImage;
import icy.sequence.Sequence;
import icy.system.SystemUtil;
import icy.system.thread.Processor;
import icy.type.DataType;
import icy.type.collection.array.Array1DUtil;
import loci.formats.FormatException;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.Comparators;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.ROI2D.AlongT;
import plugins.fmp.multitools.tools.ROI2D.ROI2DUtilities;
import plugins.fmp.multitools.tools.polyline.Bresenham;

public class KymographBuilder {

	private Map<Capillary, ArrayList<int[]>> capIntegerArrays = new HashMap<>();

	public boolean buildKymograph(Experiment exp, BuildSeriesOptions options) {
		if (exp.getCapillaries().getList().size() < 1) {
			Logger.warn("KymographBuilder:buildKymo Abort (1): nbcapillaries = 0");
			return false;
		}

		getCapillariesToProcess(exp, options);
		clearAllAlongTMasks(exp);

		SequenceLoaderService loader = new SequenceLoaderService();
		// Canonical behavior: time origin for analyses is the first valid (visible)
		// frame, i.e. frame 0 of the clipped image list.
		long camReferenceMs = exp.getSeqCamData().getFirstValidFrameEpochMs();
		if (camReferenceMs < 0) {
			// Fallback to persisted experiment-level timing if file timestamps aren't available.
			camReferenceMs = exp.getCamImageFirst_ms();
		}
		if (camReferenceMs < 0) {
			camReferenceMs = 0;
		}
		exp.build_MsTimeIntervalsArray_From_SeqCamData_FileNamesList(camReferenceMs);

		long first_ms = 0;
		long last_ms = exp.getSeqCamData().getTimeManager().getBinLast_ms();
		if (last_ms <= first_ms && exp.getKymoLast_ms() > exp.getKymoFirst_ms()) {
			first_ms = exp.getKymoFirst_ms();
			last_ms = exp.getKymoLast_ms();
		}

		long step_ms = exp.getKymoBin_ms();

		int nTotalFrames = exp.getSeqCamData().getImageLoader().getNTotalFrames();
		int lowIndex = 0;
		int highIndex = (nTotalFrames > 0) ? (nTotalFrames - 1) : 0;
		if (highIndex < lowIndex)
			highIndex = lowIndex;
		long[] camImages_ms = exp.getCamImages_ms();
		if (camImages_ms != null && highIndex < camImages_ms.length)
			last_ms = Math.min(last_ms, camImages_ms[highIndex]);

		int expectedWidth = (step_ms > 0) ? Math.max(1, 1 + (int) Math.ceil((last_ms - first_ms) / (double) step_ms))
				: 1;
		initArraysToBuildKymographImages(exp, options, expectedWidth);

		int sourceLastImageIndex = nTotalFrames;
		final int refSizex = exp.getSeqCamData().getSequence().getSizeX();
		final int refSizey = exp.getSeqCamData().getSequence().getSizeY();

		ProgressFrame progress = new ProgressFrame("Analyze series");

		final Processor processor = new Processor(SystemUtil.getNumberOfCPUs());
		processor.setThreadName("buildKymograph");
		processor.setPriority(Processor.NORM_PRIORITY);
		int ntasks = exp.getCapillaries().getList().size();
		ArrayList<Future<?>> tasks = new ArrayList<Future<?>>(ntasks);
		tasks.clear();

		for (int iToColumn = 0; iToColumn < expectedWidth; iToColumn++) {
			long ii_ms = first_ms + iToColumn * step_ms;
			int sourceImageIndex = exp.findNearestIntervalWithBinarySearch(ii_ms, lowIndex, highIndex);
			if (sourceImageIndex < 0)
				continue;

			final int fromSourceImageIndex = sourceImageIndex;

			final int viewT = fromSourceImageIndex;
			final int kymographColumn = iToColumn;
			progress.setMessage("Processing file: " + (sourceImageIndex + 1) + "//" + sourceLastImageIndex);

			final IcyBufferedImage sourceImage = loader
					.imageIORead(exp.getSeqCamData().getFileNameFromImageList(fromSourceImageIndex));

			tasks.add(processor.submit(() -> {
				for (Capillary capi : exp.getCapillaries().getList()) {
					if (!capi.getKymographBuild())
						continue;
					analyzeImageUnderCapillary(sourceImage, capi, viewT, kymographColumn, refSizex, refSizey, options);
				}
			}));
		}

		progress.close();
		waitFuturesCompletion(processor, tasks);

		SequenceCamData seqCamData = exp.getSeqCamData();
		int sizeC = seqCamData.getSequence().getSizeC();
		if (options.doCreateBinDir) {
			String binDir = exp.getBinNameFromKymoFrameStep();
			exp.setBinSubDirectory(binDir);
			exp.setGenerationMode(plugins.fmp.multitools.experiment.GenerationMode.KYMOGRAPH);
			exp.saveBinDescription(binDir);
		}

		exportCapillaryKymographs(exp, sizeC);
		return true;
	}

	public void saveComputation(Experiment exp, BuildSeriesOptions options) {
		exp.saveExperimentDescriptors();
	}

	private void getCapillariesToProcess(Experiment exp, BuildSeriesOptions options) {
		Collections.sort(exp.getCapillaries().getList(), new Comparators.Capillary_ROIName());
		int index = 0;

		for (Capillary cap : exp.getCapillaries().getList()) {
			// Derive kymograph name from current ROI name (not from potentially stale
			// metadata)
			String roiName = cap.getRoiName();
			if (roiName != null) {
				String kymographName = Capillary.replace_LR_with_12(roiName);
				cap.setKymographName(kymographName);
				cap.setKymographFileName(kymographName + ".tiff");
			}

			// Assign sequential index for building (will be corrected later when matching
			// to files)
			int i = cap.getKymographIndex();
			if (i < 0) {
				i = index;
				cap.setKymographIndex(i);
			}

			index++;
			cap.setKymographBuild(i >= options.kymoFirst && i <= options.kymoLast);
		}
	}

	private void waitFuturesCompletion(Processor processor, ArrayList<Future<?>> futuresArray) {
		while (!futuresArray.isEmpty()) {
			final Future<?> f = futuresArray.get(futuresArray.size() - 1);
			try {
				f.get();
			} catch (ExecutionException e) {
				Logger.error("KymographBuilder:waitFuturesCompletion - Execution exception", e);
			} catch (InterruptedException e) {
				Logger.warn("KymographBuilder:waitFuturesCompletion - Interrupted exception: " + e.getMessage());
			}
			futuresArray.remove(f);
		}
	}

	void analyzeImageUnderCapillary(IcyBufferedImage sourceImage, Capillary cap, int t, int kymographColumn,
			int refSizex, int refSizey, BuildSeriesOptions options) {
		AlongT alongT = cap.getAlongTAtT(t);
		if (alongT == null) {
			Logger.warn("KymographBuilder:analyzeImageUnderCapillary - no AlongT for t=" + t + " cap="
					+ (cap.getRoiName() != null ? cap.getRoiName() : cap.getKymographName()) + ", skipping column "
					+ kymographColumn);
			return;
		}
		int imgW = sourceImage.getWidth();
		int imgH = sourceImage.getHeight();
		if (imgW != refSizex || imgH != refSizey)
			Logger.warn("KymographBuilder:analyzeImageUnderCapillary - source image size " + imgW + "x" + imgH
					+ " differs from reference " + refSizex + "x" + refSizey + " (t=" + t
					+ "), mask indices may be wrong");

		if (alongT.getMasksList() == null || alongT.getMasksList().isEmpty())
			buildMasks(alongT, refSizex, refSizey, options);
		ArrayList<ArrayList<int[]>> masksList = alongT.getMasksList();
		if (masksList == null) {
			Logger.warn("KymographBuilder:analyzeImageUnderCapillary - masksList still null after build for t=" + t
					+ " cap=" + (cap.getRoiName() != null ? cap.getRoiName() : cap.getKymographName())
					+ ", skipping column " + kymographColumn);
			return;
		}
		if (masksList.isEmpty()) {
			// A degenerate ROI (e.g. tracking failure at this frame) can produce zero
			// masks.
			// Leaving the column uninitialized results in a black column; instead, copy
			// previous column if available so the kymograph remains visually consistent.
			IcyBufferedImage capImage = cap.getCap_Image();
			int kymoImageWidth = capImage.getWidth();
			ArrayList<int[]> capInteger = capIntegerArrays.get(cap);
			if (capInteger != null && kymographColumn > 0) {
				for (int chan = 0; chan < capInteger.size(); chan++) {
					int[] capImageChannel = capInteger.get(chan);
					for (int row = 0; row < capImage.getHeight(); row++) {
						int dst = row * kymoImageWidth + kymographColumn;
						int src = row * kymoImageWidth + (kymographColumn - 1);
						capImageChannel[dst] = capImageChannel[src];
					}
				}
			}
			Logger.warn("KymographBuilder:analyzeImageUnderCapillary - empty masksList (degenerate ROI?) t=" + t
					+ " cap=" + (cap.getRoiName() != null ? cap.getRoiName() : cap.getKymographName()) + " column="
					+ kymographColumn);
			return;
		}

		IcyBufferedImage capImage = cap.getCap_Image();
		int kymoImageWidth = capImage.getWidth();
		ArrayList<int[]> capInteger = capIntegerArrays.get(cap);
		if (capInteger == null || capInteger.isEmpty()) {
			Logger.warn("KymographBuilder:analyzeImageUnderCapillary - capInteger missing for cap="
					+ (cap.getRoiName() != null ? cap.getRoiName() : cap.getKymographName()));
			return;
		}
		final int kymoSizeC = Math.max(1, capImage.getSizeC());
		if (capInteger.size() < kymoSizeC) {
			Logger.warn("KymographBuilder:analyzeImageUnderCapillary - capInteger.size=" + capInteger.size()
					+ " < kymoSizeC=" + kymoSizeC + " for cap="
					+ (cap.getRoiName() != null ? cap.getRoiName() : cap.getKymographName()));
			return;
		}

		int sourceImageWidth = sourceImage.getWidth();
		final int srcSizeC = Math.max(1, sourceImage.getSizeC());
		int[] src0 = Array1DUtil.arrayToIntArray(sourceImage.getDataXY(0), sourceImage.isSignedDataType());
		int[] src1 = (srcSizeC > 1)
				? Array1DUtil.arrayToIntArray(sourceImage.getDataXY(1), sourceImage.isSignedDataType())
				: null;
		int[] src2 = (srcSizeC > 2)
				? Array1DUtil.arrayToIntArray(sourceImage.getDataXY(2), sourceImage.isSignedDataType())
				: null;

		int cnt = 0;
		for (ArrayList<int[]> mask : masksList) {
			long sum0 = 0;
			long sum1 = 0;
			long sum2 = 0;
			for (int[] m : mask) {
				int idx = m[0] + m[1] * sourceImageWidth;
				sum0 += src0[idx];
				if (src1 != null)
					sum1 += src1[idx];
				if (src2 != null)
					sum2 += src2[idx];
			}
			if (!mask.isEmpty()) {
				int dst = cnt * kymoImageWidth + kymographColumn;
				capInteger.get(0)[dst] = (int) (sum0 / mask.size());
				if (kymoSizeC > 1 && src1 != null)
					capInteger.get(1)[dst] = (int) (sum1 / mask.size());
				if (kymoSizeC > 2 && src2 != null)
					capInteger.get(2)[dst] = (int) (sum2 / mask.size());
			}
			cnt++;
		}
	}

	private void exportCapillaryKymographs(Experiment exp, final int sizeC) {
		final Processor processor = new Processor(SystemUtil.getNumberOfCPUs());
		processor.setThreadName("buildKymograph");
		processor.setPriority(Processor.NORM_PRIORITY);
		int nbcapillaries = exp.getCapillaries().getList().size();
		ArrayList<Future<?>> tasks = new ArrayList<Future<?>>(nbcapillaries);
		tasks.clear();

		String directory = exp.getDirectoryToSaveResults();
		if (directory == null)
			return;

		for (int icap = 0; icap < nbcapillaries; icap++) {
			final Capillary cap = exp.getCapillaries().getList().get(icap);
			if (!cap.getKymographBuild())
				continue;

			tasks.add(processor.submit(new Runnable() {
				@Override
				public void run() {
					IcyBufferedImage capImage = cap.getCap_Image();
					ArrayList<int[]> capInteger = capIntegerArrays.get(cap);
					if (capInteger != null) {
						int kymoSizeC = capImage.getSizeC();
						if (capInteger.size() != kymoSizeC) {
							Logger.warn("KymographBuilder:exportCapillaryKymographs - channel count mismatch for cap="
									+ (cap.getRoiName() != null ? cap.getRoiName() : cap.getKymographName()) + " kymoSizeC="
									+ kymoSizeC + " capInteger.size=" + capInteger.size() + " (input sizeC=" + sizeC + ")");
						}
						boolean isSignedDataType = capImage.isSignedDataType();
						int nch = Math.min(kymoSizeC, capInteger.size());
						for (int chan = 0; chan < nch; chan++) {
							int[] tabValues = capInteger.get(chan);
							Object destArray = capImage.getDataXY(chan);
							Array1DUtil.intArrayToSafeArray(tabValues, 0, destArray, 0, -1, isSignedDataType,
									isSignedDataType);
							capImage.setDataXY(chan, destArray);
						}
					}

					String filename = directory + File.separator + cap.getKymographFileName();
					File file = new File(filename);
					Logger.debug("file saved= " + filename);
					try {
						saveImageSafely(capImage, file);
						cap.setCap_Image(null);
						capIntegerArrays.remove(cap);
					} catch (FormatException e) {
						Logger.error("KymographBuilder: Failed to save kymograph (format error): " + filename, e);
					} catch (IOException e) {
						Logger.error("KymographBuilder: Failed to save kymograph (IO error): " + filename, e);
					}
				}
			}));
		}

		waitFuturesCompletion(processor, tasks);
	}

	/**
	 * Avoid in-place overwrite of existing TIFFs: write to a temporary file, then
	 * replace the target. This prevents rare corruption/size blow-ups observed
	 * when overwriting kymographs.
	 */
	private static void saveImageSafely(IcyBufferedImage image, File target) throws FormatException, IOException {
		if (image == null || target == null) {
			throw new IOException("saveImageSafely: null image or target");
		}
		Path targetPath = target.toPath();
		Path parent = targetPath.getParent();
		if (parent == null) {
			throw new IOException("saveImageSafely: target has no parent directory: " + target);
		}
		Files.createDirectories(parent);

		String baseName = targetPath.getFileName().toString();
		String tmpName = baseName + ".tmp";
		Path tmpPath = parent.resolve(tmpName);
		int n = 1;
		while (Files.exists(tmpPath) && n < 100) {
			tmpPath = parent.resolve(tmpName + "." + n);
			n++;
		}

		File tmpFile = tmpPath.toFile();
		try {
			// Always overwrite the tmp file if it already exists.
			Saver.saveImage(image, tmpFile, true);
			try {
				Files.move(tmpPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException atomicNotSupported) {
				Files.move(tmpPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			try {
				Files.deleteIfExists(tmpPath);
			} catch (IOException ignored) {
				// best-effort cleanup
			}
		}
	}

	private void clearAllAlongTMasks(Experiment exp) {
		for (Capillary cap : exp.getCapillaries().getList()) {
			if (!cap.getKymographBuild())
				continue;
			for (AlongT capT : cap.getAlongTList())
				capT.setMasksList(null);
		}
	}

	private void initArraysToBuildKymographImages(Experiment exp, BuildSeriesOptions options, int kymoImageWidthHint) {
		SequenceCamData seqCamData = exp.getSeqCamData();
		if (seqCamData.getSequence() == null)
			seqCamData.setSequence(exp.getSeqCamData().getImageLoader()
					.initSequenceFromFirstImage(exp.getSeqCamData().getImagesList(true)));
		// Keep kymographs aligned with camera channel count (typically RGB = 3).
		// This preserves the historical behaviour (color kymographs) for multiCAFE.
		final int kymoSizeC = Math.max(1, seqCamData.getSequence().getSizeC());
		int sizex = seqCamData.getSequence().getSizeX();
		int sizey = seqCamData.getSequence().getSizeY();

		long firstMs = seqCamData.getTimeManager().getBinFirst_ms();
		long lastMs = seqCamData.getTimeManager().getBinLast_ms();
		if (lastMs <= firstMs)
			lastMs = exp.getKymoLast_ms();
		if (lastMs <= firstMs)
			firstMs = exp.getKymoFirst_ms();
		long stepMs = exp.getKymoBin_ms();
		int kymoImageWidth = (kymoImageWidthHint > 0) ? kymoImageWidthHint
				: ((stepMs > 0) ? (int) ((lastMs - firstMs) / stepMs + 1) : 1);
		if (kymoImageWidth <= 0)
			kymoImageWidth = (int) ((exp.getKymoLast_ms() - exp.getKymoFirst_ms()) / stepMs + 1);

		int imageHeight = 0;
		for (Capillary cap : exp.getCapillaries().getList()) {
			if (!cap.getKymographBuild())
				continue;
			for (AlongT capT : cap.getAlongTList()) {
				int imageHeight_i = buildMasksCount(capT, sizex, sizey, options);
				if (imageHeight_i > imageHeight)
					imageHeight = imageHeight_i;
			}
			buildCapInteger(cap, exp.getSeqCamData().getSequence(), kymoImageWidth, imageHeight, kymoSizeC);
		}
	}

	private int buildMasksCount(AlongT capT, int sizex, int sizey, BuildSeriesOptions options) {
		ArrayList<ArrayList<int[]>> masks = new ArrayList<ArrayList<int[]>>();
		ArrayList<Point2D> capPoints = ROI2DUtilities.getCapillaryPoints(capT.getRoi());
		getPointsfromROIPolyLineUsingBresenham(capPoints, masks, options.diskRadius, sizex, sizey);
		return masks.size();
	}

	private int buildMasks(AlongT capT, int sizex, int sizey, BuildSeriesOptions options) {
		ArrayList<Point2D> capPoints = ROI2DUtilities.getCapillaryPoints(capT.getRoi());
		ArrayList<ArrayList<int[]>> masks = new ArrayList<ArrayList<int[]>>();
		getPointsfromROIPolyLineUsingBresenham(capPoints, masks, options.diskRadius, sizex, sizey);
		capT.setMasksList(masks);
		return masks.size();
	}

	private void buildCapInteger(Capillary cap, Sequence seq, int imageWidth, int imageHeight, int sizeC) {
		int kymoSizeC = Math.max(1, sizeC);
		cap.setCap_Image(new IcyBufferedImage(imageWidth, imageHeight, kymoSizeC, DataType.UBYTE));

		int len = imageWidth * imageHeight;
		ArrayList<int[]> capInteger = new ArrayList<int[]>(kymoSizeC);
		for (int chan = 0; chan < kymoSizeC; chan++) {
			capInteger.add(new int[len]);
		}
		capIntegerArrays.put(cap, capInteger);
	}

	private void getPointsfromROIPolyLineUsingBresenham(ArrayList<Point2D> pointsList, List<ArrayList<int[]>> masks,
			double diskRadius, int sizex, int sizey) {
		ArrayList<int[]> pixels = Bresenham.getPixelsAlongLineFromROI2D(pointsList);
		int idiskRadius = (int) diskRadius;
		for (int[] pixel : pixels)
			masks.add(getAllPixelsAroundPixel(pixel, idiskRadius, sizex, sizey));
	}

	private ArrayList<int[]> getAllPixelsAroundPixel(int[] pixel, int diskRadius, int sizex, int sizey) {
		ArrayList<int[]> maskAroundPixel = new ArrayList<int[]>();
		double m1 = pixel[0];
		double m2 = pixel[1];
		double radiusSquared = diskRadius * diskRadius;
		int minX = clipValueToLimits(pixel[0] - diskRadius, 0, sizex - 1);
		int maxX = clipValueToLimits(pixel[0] + diskRadius, minX, sizex - 1);
		int minY = pixel[1];
		int maxY = pixel[1];

		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				double dx = x - m1;
				double dy = y - m2;
				double distanceSquared = dx * dx + dy * dy;
				if (distanceSquared <= radiusSquared) {
					maskAroundPixel.add(new int[] { x, y });
				}
			}
		}
		return maskAroundPixel;
	}

	private int clipValueToLimits(int x, int min, int max) {
		if (x < min)
			x = min;
		if (x > max)
			x = max;
		return x;
	}
}
