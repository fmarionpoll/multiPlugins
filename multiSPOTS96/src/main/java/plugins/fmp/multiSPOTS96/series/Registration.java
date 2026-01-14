package plugins.fmp.multiSPOTS96.series;

import java.awt.Rectangle;
import java.awt.image.RenderedImage;
import java.io.File;
import java.util.logging.Logger;

import javax.vecmath.Vector2d;

import icy.gui.frame.progress.ProgressFrame;
import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;
import icy.image.ImageUtil;
import icy.type.geom.Polygon2D;
import plugins.fmp.multiSPOTS96.dlg.a_experiment.CorrectDrift;
import plugins.fmp.multiSPOTS96.experiment.Experiment;
import plugins.fmp.multiSPOTS96.tools.GaspardRigidRegistration;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformOptions;

public class Registration extends BuildSeries {
//	private final ImageProcessor imageProcessor;
	private final ProgressReporter progressReporter;

	// Constructor with dependency injection
	public Registration() {
		this(new SafeImageProcessor(), ProgressReporter.NO_OP);
	}

	public Registration(ImageProcessor imageProcessor, ProgressReporter progressReporter) {
//		this.imageProcessor = imageProcessor;
		this.progressReporter = progressReporter;
	}

	@Override
	void analyzeExperiment(Experiment experiment) {
		ProcessingResult<Void> result = analyzeExperimentSafely(experiment);

		if (result.isFailure()) {
			System.err.println("Background analysis failed: " + result.getErrorMessage());
			progressReporter.failed(result.getErrorMessage());
		} else {
			progressReporter.completed();
		}
	}

	/**
	 * Safely analyzes an experiment with proper error handling. Replaces the
	 * original analyzeExperiment method with better error handling.
	 */
	private ProcessingResult<Void> analyzeExperimentSafely(Experiment experiment) {
		try {
			// Validate inputs
			ProcessingResult<Void> validationResult = validateExperiment(experiment);
			if (validationResult.isFailure()) {
				return validationResult;
			}

			// Load experiment data
			ProcessingResult<Void> loadResult = loadExperimentData(experiment);
			if (loadResult.isFailure()) {
				return loadResult;
			}

			// Validate bounds
//	            ProcessingResult<Void> boundsResult = validateBoundsForCages(experiment);
//	            if (boundsResult.isFailure()) {
//	                return boundsResult;
//	            }

			// Execute background image analysis
			int iiFirst = options.fromFrame;
			int iiLast = options.toFrame;
			int referenceFrame = options.referenceFrame;
			ProcessingResult<Void> buildResult = correctDriftAndRotation(experiment, iiFirst, iiLast, referenceFrame);
			if (buildResult.isFailure()) {
				return buildResult;
			}

			return ProcessingResult.success();

		} finally {
			cleanupResources();
		}
	}

	/**
	 * Validates the experiment for required data.
	 */
	private ProcessingResult<Void> validateExperiment(Experiment experiment) {
		if (experiment == null) {
			return ProcessingResult.failure("Experiment cannot be null");
		}

		if (experiment.seqCamData == null) {
			return ProcessingResult.failure("Experiment must have camera data");
		}

		return ProcessingResult.success();
	}

	/**
	 * Loads experiment data with proper error handling.
	 */
	private ProcessingResult<Void> loadExperimentData(Experiment experiment) {
		try {
			boolean loadSuccess = loadSeqCamDataAndCages(experiment);
			if (!loadSuccess) {
				return ProcessingResult.failure("Failed to load DrosoTrack data");
			}
			return ProcessingResult.success();
		} catch (Exception e) {
			return ProcessingResult.failure("Error loading experiment data", e);
		}
	}

	/**
	 * Cleans up resources properly.
	 */
	private void cleanupResources() {
//	        closeViewer(referenceViewer);
//	        closeViewer(dataViewer);
//	        closeSequence(referenceSequence);
//	        closeSequence(dataSequence);
	}

	/** Logger for this class */
	private static final Logger LOGGER = Logger.getLogger(CorrectDrift.class.getName());
	/** Minimum threshold for considering a translation significant */
	private static final double MIN_TRANSLATION_THRESHOLD = 0.001;
	/** Minimum threshold for considering a rotation significant */
	private static final double MIN_ROTATION_THRESHOLD = 0.001;

	private ProcessingResult<Void> correctDriftAndRotation(Experiment exp, int iiFirst, int iiLast,
			int referenceFrame) {
		ProgressFrame progressBar1 = new ProgressFrame("Analyze stack");

		ImageTransformOptions transformOptions = new ImageTransformOptions();
		transformOptions.transformOption = options.transformop;
		ImageTransformInterface transformFunction = transformOptions.transformOption.getFunction();

		Polygon2D polygon2D = exp.cagesArray.getPolygon2DEnclosingAllCages();
		Rectangle rect = polygon2D.getBounds();

		String fileNameReference = exp.seqCamData.getFileNameFromImageList(referenceFrame);
		IcyBufferedImage referenceImage = imageIORead(fileNameReference);
		IcyBufferedImage refImageTransformed = transformFunction.getTransformedImage(referenceImage, transformOptions);
		final IcyBufferedImage reducedReferenceImage = IcyBufferedImageUtil.getSubImage(refImageTransformed, rect.x,
				rect.y, rect.height, rect.width);
		final int referenceChannel = 0;

		for (int frame = iiFirst; frame < iiLast && !stopFlag; frame++) {
			// Update progress
			progressBar1.setMessage("Analyze frame: " + frame + "//" + iiLast);

			String fileName = exp.seqCamData.getFileNameFromImageList(frame);
			if (fileName == null) {
				System.out.println("filename null at t=" + frame);
				continue;
			}

			IcyBufferedImage workImage = imageIORead(fileName);
			IcyBufferedImage workImageTransformed = transformFunction.getTransformedImage(workImage, transformOptions);
			IcyBufferedImage reducedWorkImage = IcyBufferedImageUtil.getSubImage(workImageTransformed, rect.x, rect.y,
					rect.height, rect.width);

			Vector2d translation = GaspardRigidRegistration.findTranslation2D(reducedWorkImage, referenceChannel,
					reducedReferenceImage, referenceChannel);
			boolean change = false;
			if (translation.lengthSquared() > MIN_TRANSLATION_THRESHOLD) {
				change = true;
				workImage = GaspardRigidRegistration.applyTranslation2D(workImage, -1, translation, true);
				LOGGER.info("Applied translation correction: (" + translation.x + ", " + translation.y + ")");
			}

			boolean rotate = false;
			if (!change)
				translation = null;
			else {
				workImageTransformed = transformFunction.getTransformedImage(workImage, transformOptions);
				reducedWorkImage = IcyBufferedImageUtil.getSubImage(workImageTransformed, rect.x, rect.y, rect.height,
						rect.width);
			}
			double angle = GaspardRigidRegistration.findRotation2D(reducedWorkImage, referenceChannel,
					reducedReferenceImage, referenceChannel, translation);
			if (Math.abs(angle) > MIN_ROTATION_THRESHOLD) {
				rotate = true;
				workImage = GaspardRigidRegistration.applyRotation2D(workImage, -1, angle, true);
				LOGGER.info("Applied rotation correction: " + Math.toDegrees(angle) + " degrees");

				workImageTransformed = transformFunction.getTransformedImage(workImage, transformOptions);
				reducedWorkImage = IcyBufferedImageUtil.getSubImage(workImageTransformed, rect.x, rect.y, rect.height,
						rect.width);
				Vector2d translation2 = GaspardRigidRegistration.findTranslation2D(reducedWorkImage, referenceChannel,
						reducedReferenceImage, referenceChannel);
				if (translation2.lengthSquared() > MIN_TRANSLATION_THRESHOLD) {
					workImage = GaspardRigidRegistration.applyTranslation2D(workImage, -1, translation2, true);
				}
			}

			if (rotate)
				GaspardRigidRegistration.getTranslation2D(workImage, referenceImage, referenceChannel);

			if (change || rotate) {
				System.out.println("image:" + frame + "  change=" + change + "(" + translation + ") --  rotation="
						+ rotate + "(" + angle + ")");
				File outputfile = new File(fileName);
				RenderedImage image = ImageUtil.toRGBImage(workImage);
				boolean success = ImageUtil.save(image, "jpg", outputfile);
				System.out.println("save file " + fileName + " --->" + success);
			}
		}

		progressBar1.close();
		return ProcessingResult.success();
	}

//	public IcyBufferedImage imageIORead(String name) {
//		BufferedImage image = null;
//		try {
//			image = ImageIO.read(new File(name));
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//		return IcyBufferedImage.createFrom(image);
//	}

}
