package plugins.fmp.multiSPOTS96.series;

import java.awt.Point;
import java.lang.reflect.InvocationTargetException;

import javax.swing.SwingUtilities;

import icy.gui.frame.progress.ProgressFrame;
import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageCursor;
import icy.sequence.Sequence;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cages.Cage;
import plugins.fmp.multitools.experiment.spots.Spot;
import plugins.fmp.multitools.tools.GaspardRigidRegistration;
import plugins.fmp.multitools.tools.ViewerFMP;
import plugins.fmp.multitools.tools.ROI2D.ROI2DWithMask;

public class BuildSpotsKymos extends BuildSeries {
	public Sequence seqData = new Sequence();
	private ViewerFMP vData = null;
	private int kymoImageWidth = 0;

	// -----------------------------------

	void analyzeExperiment(Experiment exp) {
		if (!loadExperimentDataToBuildKymos(exp) || exp.cagesArray.getTotalNumberOfSpots() < 1)
			return;
		openKymoViewers(exp);
		getTimeLimitsOfSequence(exp);
		if (buildKymo(exp))
			saveComputation(exp);

		closeKymoViewers(exp);

	}

	private boolean loadExperimentDataToBuildKymos(Experiment exp) {
		boolean flag = exp.load_MS96_cages();
		exp.seqCamData.attachSequence(
				exp.seqCamData.getImageLoader().initSequenceFromFirstImage(exp.seqCamData.getImagesList(true)));
		return flag;
	}

	private void saveComputation(Experiment exp) {
//		if (options.doCreateBinDir)
//			exp.setBinSubDirectory(exp.getBinNameFromKymoFrameStep());
		String directory = exp.getDirectoryToSaveResults();
		if (directory == null)
			return;

		ProgressFrame progressBar = new ProgressFrame("Save kymographs");

//		int nframes = exp.seqKymos.getSequence().getSizeT();
//		int nCPUs = SystemUtil.getNumberOfCPUs();
//		final Processor processor = new Processor(nCPUs);
//		processor.setThreadName("buildkymo2");
//		processor.setPriority(Processor.NORM_PRIORITY);
//		ArrayList<Future<?>> futuresArray = new ArrayList<Future<?>>(nframes);
//		futuresArray.clear();
//
//		SpotsArray spotsArray = exp.cagesArray.getAllSpotsArray();
//		for (int t = 0; t < exp.seqKymos.getSequence().getSizeT(); t++) {
//			final int t_index = t;
//			futuresArray.add(processor.submit(new Runnable() {
//				@Override
//				public void run() {
//					Spot spot = spotsArray.getSpotsList().get(t_index);
//					String filename = directory + File.separator + spot.getRoi().getName() + ".tiff";
//
//					File file = new File(filename);
//					IcyBufferedImage image = exp.seqKymos.getSeqImage(t_index, 0);
//					try {
//						Saver.saveImage(image, file, true);
//					} catch (FormatException e) {
//						e.printStackTrace();
//					} catch (IOException e) {
//						e.printStackTrace();
//					}
//				}
//			}));
//		}
//		waitFuturesCompletion(processor, futuresArray, progressBar);
		progressBar.close();
		exp.save_MS96_experiment();
	}

	private boolean buildKymo(Experiment exp) {
		if (exp.cagesArray.getTotalNumberOfSpots() < 1) {
			System.out.println("BuildKymoSpots:buildKymo Abort (1): nb spots = 0");
			return false;
		}

		initArraysToBuildKymographImages(exp);

		threadRunning = true;
		stopFlag = false;

		final int iiFirst = 0;
		int iiLast = exp.seqCamData.getImageLoader().getFixedNumberOfImages() > 0
				? (int) exp.seqCamData.getImageLoader().getFixedNumberOfImages()
				: exp.seqCamData.getImageLoader().getNTotalFrames();
//		final int iiDelta = (int) exp.seqKymos.getTimeManager().getDeltaImage();
//		ProgressFrame progressBar1 = new ProgressFrame("Analyze stack frame ");
//
//		final Processor processor = new Processor(SystemUtil.getNumberOfCPUs());
//		processor.setThreadName("buildKymograph");
//		processor.setPriority(Processor.NORM_PRIORITY);
//		int ntasks = iiLast - iiFirst; // exp.spotsArray.getSpotsList().size(); //
//		ArrayList<Future<?>> tasks = new ArrayList<Future<?>>(ntasks);
//		tasks.clear();
//
//		vData.setTitle(exp.seqCamData.getCSCamFileName());
//
//		for (int ii = iiFirst; ii < iiLast; ii += iiDelta) {
//			final int t = ii;
//
//			if (options.concurrentDisplay) {
//				IcyBufferedImage sourceImage0 = imageIORead(exp.seqCamData.getFileNameFromImageList(t));
//				seqData.setImage(0, 0, sourceImage0);
//				vData.setTitle("Frame #" + ii + " /" + iiLast);
//			}
//
//			progressBar1.setMessage("Analyze frame: " + t + "//" + iiLast);
//			final IcyBufferedImage sourceImage = loadImageFromIndex(exp, t);
//
//			tasks.add(processor.submit(new Runnable() {
//				@Override
//				public void run() {
//					int sizeC = sourceImage.getSizeC();
//					IcyBufferedImageCursor cursorSource = new IcyBufferedImageCursor(sourceImage);
//					for (Cage cage : exp.cagesArray.cagesList) {
//						for (Spot spot : cage.spotsArray.getSpotsList()) {
//							analyzeImageWithSpot2(cursorSource, spot, t - iiFirst, sizeC);
//						}
//					}
//				}
//			}));
//		}
//
//		waitFuturesCompletion(processor, tasks, null);
//		progressBar1.close();
//
//		ProgressFrame progressBar2 = new ProgressFrame("Combine results into kymograph");
//		int sizeC = seqData.getSizeC();
//		exportSpotImages_to_Kymograph(exp, sizeC);
//		progressBar2.close();

		return true;
	}

	private void analyzeImageWithSpot2(IcyBufferedImageCursor cursorSource, Spot spot, int t, int sizeC) {
		ROI2DWithMask roiT = spot.getROIMask();
		Point[] maskPoints = roiT.getMaskPoints();
		if (maskPoints == null) {
			return; // No mask points available
		}

		for (int chan = 0; chan < sizeC; chan++) {
			IcyBufferedImageCursor cursor = new IcyBufferedImageCursor(spot.getSpotImage());
			try {
				int i = 0;
				for (int j = roiT.getYMin(); j < roiT.getYMax(); j++) {
					double iSum = 0;
					int iN = 0;
					for (int y = 0; y < maskPoints.length; y++) {
						Point pt = maskPoints[y];
						if (pt.y == j) {
							iSum += cursorSource.get((int) pt.getX(), (int) pt.getY(), chan);
							iN++;
						}
					}
					if (iN == 0)
						iN = 1;
					cursor.set(t, i, chan, iSum / iN);
					i++;
				}
			} finally {
				cursor.commitChanges();
			}
		}
	}

	private IcyBufferedImage loadImageFromIndex(Experiment exp, int frameIndex) {
		IcyBufferedImage sourceImage = imageIORead(exp.seqCamData.getFileNameFromImageList(frameIndex));
		if (options.doRegistration) {
			String referenceImageName = exp.seqCamData.getFileNameFromImageList(options.referenceFrame);
			IcyBufferedImage referenceImage = imageIORead(referenceImageName);
			adjustImage(sourceImage, referenceImage);
		}
		return sourceImage;
	}

	private void exportSpotImages_to_Kymograph(Experiment exp, final int sizeC) {
//		Sequence seqKymo = exp.seqKymos.getSequence();
//		seqKymo.beginUpdate();
//		final Processor processor = new Processor(SystemUtil.getNumberOfCPUs());
//		processor.setThreadName("buildKymograph");
//		processor.setPriority(Processor.NORM_PRIORITY);
//		int nbspots = exp.cagesArray.getTotalNumberOfSpots();
//		ArrayList<Future<?>> tasks = new ArrayList<Future<?>>(nbspots);
//		tasks.clear();
//		int vertical_resolution = getMaxImageHeight(exp);
//
//		int indexSpot = 0;
//		for (Cage cage : exp.cagesArray.cagesList) {
//			for (Spot spot : cage.spotsArray.getSpotsList()) {
//				final int indexSpotKymo = indexSpot;
//				tasks.add(processor.submit(new Runnable() {
//					@Override
//					public void run() {
//						IcyBufferedImage kymoImage = IcyBufferedImageUtil.scale(spot.getSpotImage(),
//								spot.getSpotImage().getWidth(), vertical_resolution);
//						seqKymo.setImage(indexSpotKymo, 0, kymoImage);
//						spot.setSpotImage(null);
//					}
//				}));
//				indexSpot++;
//			}
//		}
//		waitFuturesCompletion(processor, tasks, null);
//		seqKymo.endUpdate();
	}

	private int getMaxImageHeight(Experiment exp) {
		int maxImageHeight = 0;
		for (Cage cage : exp.cagesArray.cagesList) {
			for (Spot spot : cage.spotsArray.getSpotsList()) {
				int height = spot.getSpotImage().getHeight();
				if (height > maxImageHeight)
					maxImageHeight = height;
			}
		}
		return maxImageHeight;
	}

	private void initArraysToBuildKymographImages(Experiment exp) {
//		if (exp.seqKymos == null) {
//			// Use builder pattern with quality processing configuration for kymograph
//			// building
//			exp.seqKymos = SequenceKymos.kymographBuilder()
//					.withConfiguration(KymographConfiguration.qualityProcessing()).build();
//		}
//		SequenceKymos seqKymos = exp.seqKymos;
//		seqKymos.attachSequence(new Sequence());
//
//		SequenceCamData seqCamData = exp.seqCamData;
//		if (seqCamData.getSequence() == null)
//			seqCamData.attachSequence(
//					exp.seqCamData.getImageLoader().initSequenceFromFirstImage(exp.seqCamData.getImagesList(true)));
//
//		kymoImageWidth = exp.seqCamData.getImageLoader().getNTotalFrames();
//		int numC = seqCamData.getSequence().getSizeC();
//		if (numC <= 0)
//			numC = 3;
//
//		DataType dataType = seqCamData.getSequence().getDataType_();
//		if (dataType.toString().equals("undefined"))
//			dataType = DataType.UBYTE;
//
//		for (Cage cage : exp.cagesArray.cagesList) {
//			for (Spot spot : cage.spotsArray.getSpotsList()) {
//				int imageHeight = 0;
//				ROI2DWithMask roiT = null;
//				try {
//					roiT = new ROI2DWithMask(spot.getRoi());
//					roiT.buildMask2DFromInputRoi();
//					int imageHeight_i = roiT.getMask2DHeight();
//					if (imageHeight_i > imageHeight)
//						imageHeight = imageHeight_i;
//				} catch (ROI2DProcessingException | ROI2DValidationException e) {
//					System.err.println("Error getting mask height for ROI at time " + spot.getRoi().getName() + ": "
//							+ e.getMessage());
//					e.printStackTrace();
//				}
//				spot.setROIMask(roiT);
//
//				spot.setSpotImage(new IcyBufferedImage(kymoImageWidth, imageHeight, numC, dataType));
//			}
//		}
	}

	private void adjustImage(IcyBufferedImage workImage, IcyBufferedImage referenceImage) {
		int referenceChannel = 0;
		GaspardRigidRegistration.getTranslation2D(workImage, referenceImage, referenceChannel);
		boolean rotate = GaspardRigidRegistration.correctRotation2D(workImage, referenceImage, referenceChannel);
		if (rotate)
			GaspardRigidRegistration.getTranslation2D(workImage, referenceImage, referenceChannel);
	}

	private void closeKymoViewers(Experiment exp) {
		closeViewer(vData);
		closeSequence(seqData);
//		exp.seqKymos.closeSequence();
	}

	private void openKymoViewers(Experiment exp) {
		try {
			SwingUtilities.invokeAndWait(new Runnable() {
				public void run() {
					seqData = newSequence("analyze stack starting with file " + exp.seqCamData.getSequence().getName(),
							exp.seqCamData.getSeqImage(0, 0));
					vData = new ViewerFMP(seqData, true, true);
				}
			});
		} catch (InvocationTargetException | InterruptedException e) {
			e.printStackTrace();
		}
	}

}
