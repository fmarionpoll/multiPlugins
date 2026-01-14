package plugins.fmp.multiSPOTS.series;

import java.awt.Point;
import java.awt.geom.Rectangle2D;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import javax.swing.SwingUtilities;

import icy.gui.frame.progress.ProgressFrame;
import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageCursor;
import icy.roi.BooleanMask2D;
import icy.sequence.Sequence;
import icy.system.SystemUtil;
import icy.system.thread.Processor;
import plugins.fmp.multiSPOTS.experiment.Experiment;
import plugins.fmp.multiSPOTS.experiment.SequenceCamData;
import plugins.fmp.multiSPOTS.experiment.spots.Spot;
import plugins.fmp.multiSPOTS.tools.ViewerFMP;
import plugins.fmp.multiSPOTS.tools.ROI2D.ROI2DAlongT;
import plugins.fmp.multiSPOTS.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multiSPOTS.tools.imageTransform.ImageTransformOptions;
import plugins.kernel.roi.roi2d.ROI2DRectangle;

public class BuildSpotsMeasures extends BuildSeries {
	public Sequence seqData = new Sequence();
	private ViewerFMP vData = null;
	private ImageTransformOptions transformOptions01 = null;
	ImageTransformInterface transformFunctionSpot = null;
	ImageTransformOptions transformOptions02 = null;
	ImageTransformInterface transformFunctionFly = null;

	// --------------------------------------------

	void analyzeExperiment(Experiment exp) {
		loadExperimentDataToMeasureSpots(exp);
		exp.spotsArray.setFilterOfSpotsToAnalyze(true, options);

		openViewers(exp);
		getTimeLimitsOfSequence(exp);
		if (measureSpots(exp))
			saveComputation(exp);

		exp.spotsArray.setFilterOfSpotsToAnalyze(false, options);
		closeViewers();
	}

	private boolean loadExperimentDataToMeasureSpots(Experiment exp) {
		boolean flag = exp.load_Spots();
		exp.seqCamData.seq = exp.seqCamData.initSequenceFromFirstImage(exp.seqCamData.getImagesList(true));
		return flag;
	}

	private void saveComputation(Experiment exp) {
		if (options.doCreateBinDir)
			exp.setBinSubDirectory(exp.getBinNameFromKymoFrameStep());
		String directory = exp.getDirectoryToSaveResults();
		if (directory == null)
			return;

		exp.spotsArray.transferSumToSumClean();
		exp.spotsArray.initLevel2DMeasures();
		exp.saveXML_MCExperiment();
		exp.save_SpotsMeasures();
	}

	private void initMeasureSpots(Experiment exp) {
		initMasks2D(exp);
		initSpotsDataArrays(exp);

		if (transformFunctionSpot == null) {
			transformOptions01 = new ImageTransformOptions();
			transformOptions01.transformOption = options.transform01;
			transformOptions01.copyResultsToThe3planes = false;
			transformOptions01.setSingleThreshold(options.spotThreshold, options.spotThresholdUp);
			transformFunctionSpot = options.transform01.getFunction();

			transformOptions02 = new ImageTransformOptions();
			transformOptions02.transformOption = options.transform02;
			transformOptions02.copyResultsToThe3planes = false;
			transformFunctionFly = options.transform02.getFunction();
		}
	}

	private boolean measureSpots(Experiment exp) {
		if (exp.spotsArray.spotsList.size() < 1) {
			System.out.println("DetectAreas:measureAreas Abort (1): nbspots = 0");
			return false;
		}

		threadRunning = true;
		stopFlag = false;
		exp.build_MsTimeIntervalsArray_From_SeqCamData_FileNamesList();
		int iiFirst = 0;
		int iiLast = exp.seqCamData.fixedNumberOfImages > 0 ? (int) exp.seqCamData.fixedNumberOfImages
				: exp.seqCamData.nTotalFrames;
		vData.setTitle(exp.seqCamData.getCSCamFileName() + ": " + iiFirst + "-" + iiLast);
		ProgressFrame progressBar1 = new ProgressFrame("Analyze stack");

		final Processor processor = new Processor(SystemUtil.getNumberOfCPUs());
		processor.setThreadName("measureSpots");
		processor.setPriority(Processor.NORM_PRIORITY);
		int ntasks = iiLast - iiFirst;
		ArrayList<Future<?>> tasks = new ArrayList<Future<?>>(ntasks);
		tasks.clear();

		initMeasureSpots(exp);

		for (int ii = iiFirst; ii < iiLast; ii++) {
			if (options.concurrentDisplay) {
				IcyBufferedImage sourceImage0 = imageIORead(exp.seqCamData.getFileNameFromImageList(ii));
				seqData.setImage(0, 0, sourceImage0);
				vData.setTitle("Frame #" + ii + " /" + iiLast);
			}

			final int t = ii;
			final IcyBufferedImage sourceImage = imageIORead(exp.seqCamData.getFileNameFromImageList(t));
//			double background = 0.;		
//			final IcyBufferedImage transformToMeasureArea = transformFunctionSpot.getTransformedImage(sourceImage,
//					transformOptions01);
//			final IcyBufferedImage transformToDetectFly = transformFunctionFly.getTransformedImage(sourceImage,
//					transformOptions02);
//			final IcyBufferedImageCursor cursorToDetectFly = new IcyBufferedImageCursor(transformToDetectFly);
//			final IcyBufferedImageCursor cursorToMeasureArea = new IcyBufferedImageCursor(transformToMeasureArea);
//			if (options.compensateBackground) {
//				final ROI2DAlongT outerRoiT = getROI2DAlongTEnclosingAllSpots(exp, t);
//				final ResultsThreshold outerResult = measureSpotOverThreshold(cursorToMeasureArea, cursorToDetectFly,
//						outerRoiT);
//				background = outerResult.sumTot_no_fly_over_threshold / outerResult.nPoints_no_fly;
//			}
//			final double final_background = background;
			tasks.add(processor.submit(new Runnable() {
				@Override
				public void run() {
					progressBar1.setMessage("Analyze frame: " + t + "//" + iiLast);
					double background = 0.;
					final IcyBufferedImage transformToMeasureArea = transformFunctionSpot
							.getTransformedImage(sourceImage, transformOptions01);
					final IcyBufferedImage transformToDetectFly = transformFunctionFly.getTransformedImage(sourceImage,
							transformOptions02);
					final IcyBufferedImageCursor cursorToDetectFly = new IcyBufferedImageCursor(transformToDetectFly);
					final IcyBufferedImageCursor cursorToMeasureArea = new IcyBufferedImageCursor(
							transformToMeasureArea);
					if (options.compensateBackground) {
						final ROI2DAlongT outerRoiT = getROI2DAlongTEnclosingAllSpots(exp, t);
						final ResultsThreshold outerResult = measureSpotOverThreshold(cursorToMeasureArea,
								cursorToDetectFly, outerRoiT);
						background = outerResult.sumTot_no_fly_over_threshold / outerResult.nPoints_no_fly;
					}
					final double final_background = background;

					int ii = t - iiFirst;
					for (Spot spot : exp.spotsArray.spotsList) {
						if (!spot.okToAnalyze)
							continue;
//						int i = spot.plateIndex % 2;
//						if (0 == i && !options.detectL)
//							continue;
//						if (1 == i && !options.detectR)
//							continue;
						ROI2DAlongT roiT = spot.getROIAtT(t);
						ResultsThreshold results = measureSpotOverThreshold(cursorToMeasureArea, cursorToDetectFly,
								roiT);
						spot.flyPresent.isPresent[ii] = results.nPoints_fly_present;
						spot.sum_in.values[ii] = results.sumOverThreshold / results.npoints_in - final_background;
						if (results.nPoints_no_fly != results.npoints_in)
							spot.sum_in.values[ii] = results.sumTot_no_fly_over_threshold / results.nPoints_no_fly
									- final_background;
					}
				}
			}));
		}

		waitFuturesCompletion(processor, tasks, null);
		progressBar1.close();
		return true;
	}

	ROI2DAlongT getROI2DAlongTEnclosingAllSpots(Experiment exp, int t) {

		Rectangle2D rect = getRectangleEnclosingAllSpots(exp, t);
		ROI2DRectangle roiRect = new ROI2DRectangle(rect);
		try {
			BooleanMask2D roiRectMask = roiRect.getBooleanMask(true);
			for (Spot spot : exp.spotsArray.spotsList) {
				BooleanMask2D mask = spot.getROIAtT(t).getMask2D_in();
				roiRectMask.subtract(mask.bounds, mask.mask);
			}
			ROI2DAlongT roiT = new ROI2DAlongT(0, roiRect);
			roiT.setMask2D_in(roiRectMask);
			roiT.mask2DPoints_in = roiT.mask2D_in.getPoints();
			return roiT;

		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

	Rectangle2D getRectangleEnclosingAllSpots(Experiment exp, int t) {

		Rectangle2D outerRectangle = (Rectangle2D) exp.spotsArray.spotsList.get(0).getROIAtT(t).getRoi_in().getBounds();
		for (Spot spot : exp.spotsArray.spotsList) {
			Rectangle2D rect = (Rectangle2D) spot.getROIAtT(t).getRoi_in().getBounds();
			Rectangle2D.union(outerRectangle, rect, outerRectangle);
		}
		return outerRectangle;
	}

	private ResultsThreshold measureSpotOverThreshold(IcyBufferedImageCursor cursorToMeasureArea,
			IcyBufferedImageCursor cursorToDetectFly, ROI2DAlongT roiT) {

		ResultsThreshold result = new ResultsThreshold();
		result.npoints_in = roiT.mask2DPoints_in.length;

		for (int offset = 0; offset < roiT.mask2DPoints_in.length; offset++) {
			Point pt = roiT.mask2DPoints_in[offset];
			int value = (int) cursorToMeasureArea.get((int) pt.getX(), (int) pt.getY(), 0);
			int value_to_detect_fly = (int) cursorToDetectFly.get((int) pt.getX(), (int) pt.getY(), 0);

			boolean isFlyThere = isFlyPresent(value_to_detect_fly);
			if (!isFlyThere) {
				result.nPoints_no_fly++;
			} else
				result.nPoints_fly_present++;

			if (isOverThreshold(value)) {
				result.sumOverThreshold += value;
				result.nPointsOverThreshold++;
				if (!isFlyThere) {
					result.sumTot_no_fly_over_threshold += value;
				}
			}
		}
		return result;
	}

	private boolean isFlyPresent(double value) {
		boolean flag = value > options.flyThreshold;
		if (!options.flyThresholdUp)
			flag = !flag;
		return flag;
	}

	private boolean isOverThreshold(double value) {

		boolean flag = value > options.spotThreshold;
		if (!options.spotThresholdUp)
			flag = !flag;
		return flag;
	}

	private void initSpotsDataArrays(Experiment exp) {

		int nFrames = exp.seqCamData.nTotalFrames;
		for (Spot spot : exp.spotsArray.spotsList) {
			int i = spot.plateIndex % 2;
			if (0 == i && !options.detectL)
				continue;
			if (1 == i && !options.detectR)
				continue;
			spot.sum_in.values = new double[nFrames];
			spot.sum_clean.values = new double[nFrames];
			spot.flyPresent.isPresent = new int[nFrames];
		}
	}

	private void initMasks2D(Experiment exp) {
		SequenceCamData seqCamData = exp.seqCamData;
		if (seqCamData.seq == null)
			seqCamData.seq = exp.seqCamData.initSequenceFromFirstImage(exp.seqCamData.getImagesList(true));

		for (Spot spot : exp.spotsArray.spotsList) {
//			int i = spot.plateIndex % 2;
//			if (0 == i && !options.detectL)
//				continue;
//			if (1 == i && !options.detectR)
//				continue;
			List<ROI2DAlongT> listRoiT = spot.getROIAlongTList();
			for (ROI2DAlongT roiT : listRoiT) {
				if (roiT.getMask2D_in() == null)
					roiT.buildMask2DFromRoi_in();
			}
		}
	}

	private void closeViewers() {
		closeViewer(vData);
		closeSequence(seqData);
	}

	private void openViewers(Experiment exp) {
		try {
			SwingUtilities.invokeAndWait(new Runnable() {
				public void run() {
					seqData = newSequence(exp.seqCamData.getCSCamFileName(), exp.seqCamData.getSeqImage(0, 0));
					vData = new ViewerFMP(seqData, true, true);
				}
			});
		} catch (InvocationTargetException | InterruptedException e) {
			e.printStackTrace();
		}
	}

}