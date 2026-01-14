package plugins.fmp.multiSPOTS.series;

import java.lang.reflect.InvocationTargetException;

import javax.swing.SwingUtilities;

import icy.gui.frame.progress.ProgressFrame;
import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageCursor;
import icy.image.IcyBufferedImageUtil;
import icy.sequence.Sequence;
import plugins.fmp.multiSPOTS.experiment.Experiment;
import plugins.fmp.multiSPOTS.tools.ViewerFMP;
import plugins.fmp.multiSPOTS.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multiSPOTS.tools.imageTransform.ImageTransformOptions;

public class BuildBackground extends BuildSeries {
	public Sequence seqData = new Sequence();
	public Sequence seqReference = null;

	private ViewerFMP vData = null;
	private ViewerFMP vReference = null;

	private FlyDetectTools flyDetectTools = new FlyDetectTools();

	// -----------------------------------------

	void analyzeExperiment(Experiment exp) {
		if (!loadDrosoTrack(exp))
			return;
		if (!checkBoundsForCages(exp))
			return;

		runBuildBackground(exp);

	}

	private void closeSequences() {
		closeSequence(seqReference);
		closeSequence(seqData);
	}

	private void closeViewers() {
		closeViewer(vData);
		closeViewer(vReference);
		closeSequences();
	}

	private void openBackgroundViewers(Experiment exp) {
		try {
			SwingUtilities.invokeAndWait(new Runnable() {
				public void run() {
					seqData = newSequence("data recorded", exp.seqCamData.getSeqImage(0, 0));
					vData = new ViewerFMP(seqData, true, true);

					seqReference = newSequence("referenceImage", exp.seqCamData.refImage);
					exp.seqReference = seqReference;
					vReference = new ViewerFMP(seqReference, true, true);
				}
			});
		} catch (InvocationTargetException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void runBuildBackground(Experiment exp) {
		exp.cleanPreviousDetectedFliesROIs();
		flyDetectTools.initParametersForDetection(exp, options);
		exp.cagesArray.initFlyPositions(options.detectCage);
		options.threshold = options.thresholdDiff;

		openBackgroundViewers(exp);
		try {
			ImageTransformOptions transformOptions = new ImageTransformOptions();
			transformOptions.transformOption = ImageTransformEnums.SUBTRACT;
			transformOptions.setSingleThreshold(options.backgroundThreshold, stopFlag);
			transformOptions.background_delta = options.background_delta;
			transformOptions.background_jitter = options.background_jitter;
			buildBackgroundImage(exp, transformOptions);
			exp.saveReferenceImage(seqReference.getFirstImage());

		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		closeViewers();
	}

	private void buildBackgroundImage(Experiment exp, ImageTransformOptions transformOptions)
			throws InterruptedException {
		ProgressFrame progress = new ProgressFrame("Build background image...");
		flyDetectTools.initParametersForDetection(exp, options);

		transformOptions.backgroundImage = imageIORead(
				exp.seqCamData.getFileNameFromImageList(options.backgroundFirst));

		long first_ms = exp.cagesArray.detectFirst_Ms + (options.backgroundFirst * exp.seqCamData.binImage_ms);
		final int t_first = (int) ((first_ms - exp.cagesArray.detectFirst_Ms) / exp.seqCamData.binImage_ms);

		int t_last = options.backgroundFirst + options.backgroundNFrames;
		if (t_last > exp.seqCamData.nTotalFrames)
			t_last = exp.seqCamData.nTotalFrames;

		for (int t = t_first + 1; t <= t_last && !stopFlag; t++) {
			IcyBufferedImage currentImage = imageIORead(exp.seqCamData.getFileNameFromImageList(t));
			seqData.setImage(0, 0, currentImage);
			progress.setMessage("Frame #" + t + "/" + t_last);

			transformBackground(currentImage, transformOptions);
			seqReference.setImage(0, 0, transformOptions.backgroundImage);

			if (transformOptions.npixels_changed < 10)
				break;
		}
		exp.seqCamData.refImage = IcyBufferedImageUtil.getCopy(seqReference.getFirstImage());
		progress.close();
	}

	void transformBackground(IcyBufferedImage sourceImage, ImageTransformOptions transformOptions) {
		if (transformOptions.backgroundImage == null)
			return;

		int width = sourceImage.getSizeX();
		int height = sourceImage.getSizeY();
		int planes = sourceImage.getSizeC();
		transformOptions.npixels_changed = 0;
		int changed = 0;

		IcyBufferedImageCursor sourceCursor = new IcyBufferedImageCursor(sourceImage);
		IcyBufferedImageCursor backgroundCursor = new IcyBufferedImageCursor(transformOptions.backgroundImage);

		double smallThreshold = transformOptions.background_delta;
		;
		try {
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					for (int c = 0; c < planes; c++) {
						double backgroundValue = backgroundCursor.get(x, y, c);
						double sourceValue = sourceCursor.get(x, y, c);
						if (sourceValue < transformOptions.simplethreshold)
							continue;

						double differenceValue = sourceValue - backgroundValue;
						if (backgroundValue < transformOptions.simplethreshold && differenceValue > smallThreshold) {
							changed++;
							for (int yy = y - transformOptions.background_jitter; yy < y
									+ transformOptions.background_jitter; yy++) {
								if (yy < 0 || yy >= height)
									continue;
								for (int xx = x - transformOptions.background_jitter; xx < x
										+ transformOptions.background_jitter; xx++) {
									if (xx < 0 || xx >= width)
										continue;
									for (int cc = 0; cc < planes; cc++) {
										backgroundCursor.set(xx, yy, cc, sourceCursor.get(xx, yy, cc));
									}
								}
							}
						}
					}
				}
			}
		} finally {
			backgroundCursor.commitChanges();
			transformOptions.npixels_changed = changed;
		}
	}

}