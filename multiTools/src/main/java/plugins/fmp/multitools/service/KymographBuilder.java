package plugins.fmp.multitools.service;

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
		initArraysToBuildKymographImages(exp, options);

		final Processor processor = new Processor(SystemUtil.getNumberOfCPUs());
		processor.setThreadName("buildKymograph");
		processor.setPriority(Processor.NORM_PRIORITY);
		int ntasks = exp.getCapillaries().getList().size();
		ArrayList<Future<?>> tasks = new ArrayList<Future<?>>(ntasks);

		tasks.clear();
		SequenceLoaderService loader = new SequenceLoaderService();
		long first_ms = exp.getKymoFirst_ms();
		long last_ms = exp.getKymoLast_ms();
		long step_ms = exp.getKymoBin_ms();
		int sourceLastImageIndex = exp.getSeqCamData().getImageLoader().getNTotalFrames();
		int iToColumn = 0;

		ProgressFrame progress = new ProgressFrame("Analyze series");

		for (long ii_ms = first_ms; ii_ms <= last_ms; ii_ms += step_ms, iToColumn++) {
			int sourceImageIndex = exp.findNearestIntervalWithBinarySearch(ii_ms, 0,
					exp.getSeqCamData().getImageLoader().getNTotalFrames());
			final int fromSourceImageIndex = sourceImageIndex;
			final int kymographColumn = iToColumn;
			progress.setMessage("Processing file: " + (sourceImageIndex + 1) + "//" + sourceLastImageIndex);

			final IcyBufferedImage sourceImage = loader
					.imageIORead(exp.getSeqCamData().getFileNameFromImageList(fromSourceImageIndex));

			tasks.add(processor.submit(new Runnable() {
				@Override
				public void run() {
					for (Capillary capi : exp.getCapillaries().getList()) {
						if (!capi.getKymographBuild())
							continue;
						analyzeImageUnderCapillary(sourceImage, capi, fromSourceImageIndex, kymographColumn);
					}
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
			// Save bin description with current parameters
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

	private void analyzeImageUnderCapillary(IcyBufferedImage sourceImage, Capillary cap, int t, int kymographColumn) {
		AlongT capiROI2DatT = cap.getAlongTAtT(t);
		int sizeC = sourceImage.getSizeC();
		IcyBufferedImage capImage = cap.getCap_Image();
		int kymoImageWidth = capImage.getWidth();
		ArrayList<int[]> capInteger = capIntegerArrays.get(cap);

		for (int chan = 0; chan < sizeC; chan++) {
			int[] sourceImageChannel = Array1DUtil.arrayToIntArray(sourceImage.getDataXY(chan),
					sourceImage.isSignedDataType());
			int[] capImageChannel = capInteger.get(chan);

			int cnt = 0;
			int sourceImageWidth = sourceImage.getWidth();
			for (ArrayList<int[]> mask : capiROI2DatT.getMasksList()) {
				int sum = 0;
				for (int[] m : mask)
					sum += sourceImageChannel[m[0] + m[1] * sourceImageWidth];
				if (mask.size() > 0)
					capImageChannel[cnt * kymoImageWidth + kymographColumn] = (int) (sum / mask.size());
				cnt++;
			}
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
						boolean isSignedDataType = capImage.isSignedDataType();
						for (int chan = 0; chan < sizeC; chan++) {
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
						Saver.saveImage(capImage, file, true);
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

	private void initArraysToBuildKymographImages(Experiment exp, BuildSeriesOptions options) {
		SequenceCamData seqCamData = exp.getSeqCamData();
		if (seqCamData.getSequence() == null)
			seqCamData.setSequence(exp.getSeqCamData().getImageLoader()
					.initSequenceFromFirstImage(exp.getSeqCamData().getImagesList(true)));
		int sizex = seqCamData.getSequence().getSizeX();
		int sizey = seqCamData.getSequence().getSizeY();

		int kymoImageWidth = (int) ((exp.getKymoLast_ms() - exp.getKymoFirst_ms()) / exp.getKymoBin_ms() + 1);
		int imageHeight = 0;
		for (Capillary cap : exp.getCapillaries().getList()) {
			if (!cap.getKymographBuild())
				continue;
			for (AlongT capT : cap.getROIsForKymo()) {
				int imageHeight_i = buildMasks(capT, sizex, sizey, options);
				if (imageHeight_i > imageHeight)
					imageHeight = imageHeight_i;
			}
			buildCapInteger(cap, exp.getSeqCamData().getSequence(), kymoImageWidth, imageHeight);
		}
	}

	private int buildMasks(AlongT capT, int sizex, int sizey, BuildSeriesOptions options) {
		ArrayList<ArrayList<int[]>> masks = new ArrayList<ArrayList<int[]>>();
		getPointsfromROIPolyLineUsingBresenham(ROI2DUtilities.getCapillaryPoints(capT.getRoi()), masks,
				options.diskRadius, sizex, sizey);
		capT.setMasksList(masks);
		return masks.size();
	}

	private void buildCapInteger(Capillary cap, Sequence seq, int imageWidth, int imageHeight) {
		int numC = seq.getSizeC();
		if (numC <= 0)
			numC = 3;

		DataType dataType = seq.getDataType_();
		if (dataType == null || dataType.toString().equals("undefined"))
			dataType = DataType.UBYTE;

		cap.setCap_Image(new IcyBufferedImage(imageWidth, imageHeight, numC, dataType));

		int len = imageWidth * imageHeight;
		ArrayList<int[]> capInteger = new ArrayList<int[]>(numC);
		for (int chan = 0; chan < numC; chan++) {
			int[] tabValues = new int[len];
			capInteger.add(tabValues);
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
