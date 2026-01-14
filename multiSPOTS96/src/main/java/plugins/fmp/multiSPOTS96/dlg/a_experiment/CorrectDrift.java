package plugins.fmp.multiSPOTS96.dlg.a_experiment;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.RenderedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.vecmath.Vector2d;

import icy.gui.viewer.Viewer;
import icy.gui.viewer.ViewerEvent;
import icy.gui.viewer.ViewerListener;
import icy.image.IcyBufferedImage;
import icy.image.ImageUtil;
import icy.sequence.DimensionId;
import icy.sequence.Sequence;
import icy.type.collection.array.Array1DUtil;
import icy.util.StringUtil;
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multiSPOTS96.experiment.Experiment;
import plugins.fmp.multiSPOTS96.series.ProcessingResult;
import plugins.fmp.multiSPOTS96.series.ProgressReporter;
import plugins.fmp.multiSPOTS96.series.RegistrationOptions;
import plugins.fmp.multiSPOTS96.series.RegistrationProcessor;
import plugins.fmp.multiSPOTS96.series.SafeRegistrationProcessor;
import plugins.fmp.multiSPOTS96.tools.GaspardRigidRegistration;
import plugins.fmp.multiSPOTS96.tools.JComponents.JComboBoxExperimentLazy;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformOptions;

public class CorrectDrift extends JPanel implements ViewerListener, PropertyChangeListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static final Logger LOGGER = Logger.getLogger(CorrectDrift.class.getName());

	int val = 0; // set your own value, I used to check if it works
	int min = 0;
	int max = 10000;
	int step = 1;
	int maxLast = 99999999;
	private final JSpinner startFrameJSpinner = new JSpinner(new SpinnerNumberModel(val, min, max, step));
	private final JSpinner referenceFrameJSpinner = new JSpinner(new SpinnerNumberModel(val, min, max, step));
	private final JButton runButton = new JButton("Run registration");

	private final JSpinner xSpinner = new JSpinner(new SpinnerNumberModel(0, -500, 500, 1));
	private final JSpinner ySpinner = new JSpinner(new SpinnerNumberModel(0, -500, 500, 1));
	private final JButton testTranslationButton = new JButton("Test");
	private final JButton applyTranslationButton = new JButton("Apply");
	private final JButton restoreTranslationButton = new JButton("Back -1");
	int previousX = 0;
	int previousY = 0;
	int previousT = 0;
	double previousAngle = 0.;

	private final JSpinner angleSpinner = new JSpinner(new SpinnerNumberModel(0., -180., 180., 1.));
	private final JButton testRotationButton = new JButton("Test");
	private final JButton applyRotationButton = new JButton("Apply");
	private final JButton restoreRotationButton = new JButton("Back-1");

	private final JSpinner squareSizeSpinner = new JSpinner(new SpinnerNumberModel(10, 0, 500, 1));

//	private MultiSPOTS96 parent0 = null;
	private JComboBoxExperimentLazy experimentList = new JComboBoxExperimentLazy();
//	private Registration registration = null;

	private CompletableFuture<Void> currentTask;
	private final RegistrationProcessor registrationProcessor = new SafeRegistrationProcessor();

	void init(GridLayout capLayout, MultiSPOTS96 parent0) {
		// this.parent0 = parent0;
		this.experimentList = parent0.expListComboLazy;

		initializeUI(capLayout);
		defineActionListeners();
		updateButtonStates();
	}

	private void initializeUI(GridLayout capLayout) {
		setLayout(capLayout);

		FlowLayout flowlayout = new FlowLayout(FlowLayout.LEFT);
		flowlayout.setVgap(1);

		JPanel referencePanel = new JPanel(flowlayout);
		referencePanel.add(new JLabel("Start frame"));
		referencePanel.add(startFrameJSpinner);
		startFrameJSpinner.setPreferredSize(new Dimension(50, 20));
		referencePanel.add(new JLabel("Ref. frame"));
		referencePanel.add(referenceFrameJSpinner);
		referenceFrameJSpinner.setPreferredSize(new Dimension(50, 20));
		referencePanel.add(runButton);

		add(referencePanel);

		JPanel translationPanel = new JPanel(flowlayout);
		translationPanel.add(new JLabel("Translate X"));
		translationPanel.add(xSpinner);
		xSpinner.setPreferredSize(new Dimension(50, 20));
		translationPanel.add(new JLabel("Y"));
		translationPanel.add(ySpinner);
		ySpinner.setPreferredSize(new Dimension(50, 20));
		translationPanel.add(testTranslationButton);
		translationPanel.add(applyTranslationButton);
		translationPanel.add(restoreTranslationButton);
		add(translationPanel);

		JPanel rotationPanel = new JPanel(flowlayout);
		rotationPanel.add(new JLabel("rotate (degrees)"));
		rotationPanel.add(angleSpinner);
		rotationPanel.add(testRotationButton);
		rotationPanel.add(applyRotationButton);
		rotationPanel.add(restoreRotationButton);
		add(rotationPanel);

		JPanel chessPanel = new JPanel(flowlayout);
		chessPanel.add(new JLabel("Size of test square (pixels)"));
		chessPanel.add(squareSizeSpinner);
		add(chessPanel);

		restoreTranslationButton.setEnabled(false);
		restoreRotationButton.setEnabled(false);
	}

	private void defineActionListeners() {
		runButtonListener();
		startFrameJSpinnerListener();
		testTranslationButtonListener();
		applyTranslationButtonListener();
		restoreTranslationButtonListener();
		testRotationButtonListener();
		applyRotationButtonListener();
		restoreRotationButtonListener();
	}

	private void runButtonListener() {
		runButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = getCurrentExperiment();
				if (exp != null) {
					if (runButton.getText().equals("Run"))
						executeRegistration(exp);
					else
						stopComputation();
				}
			}
		});
	}

	private void startFrameJSpinnerListener() {
		startFrameJSpinner.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				Experiment exp = getCurrentExperiment();
				if (exp != null && exp.seqCamData.getSequence() != null) {
					Viewer v = exp.seqCamData.getSequence().getFirstViewer();
					if (v != null) {
						int newValue = (int) startFrameJSpinner.getValue();
						if (v.getPositionT() != newValue)
							v.setPositionT((int) newValue);
					}
				}
			}
		});
	}

	private void applyTranslationButtonListener() {
		applyTranslationButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = getCurrentExperiment();
				if (exp != null) {
					int x = (int) xSpinner.getValue();
					int y = (int) ySpinner.getValue();
					applyTranslation(exp, x, y);

					restoreTranslationButton.setEnabled(true);
					previousX = x;
					previousY = y;
				}
			}
		});
	}

	private void testTranslationButtonListener() {
		testTranslationButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = getCurrentExperiment();
				if (exp != null) {
					int x = (int) xSpinner.getValue();
					int y = (int) ySpinner.getValue();
					testTranslation(exp, x, y);
				}
			}
		});
	}

	private void restoreTranslationButtonListener() {
		restoreTranslationButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = getCurrentExperiment();
				if (exp != null) {
					applyTranslation(exp, -previousX, -previousY);

					restoreTranslationButton.setEnabled(false);
					previousX = 0;
					previousY = 0;
				}
			}
		});
	}

	private void applyRotationButtonListener() {
		applyRotationButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = getCurrentExperiment();
				if (exp != null) {
					double angle = (double) angleSpinner.getValue();
					applyRotation(exp, angle);

					restoreRotationButton.setEnabled(true);
					previousAngle = angle;
				}
			}
		});
	}

	private void restoreRotationButtonListener() {
		restoreRotationButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = getCurrentExperiment();
				if (exp != null) {
					applyRotation(exp, previousAngle);

					restoreRotationButton.setEnabled(false);
					previousAngle = 0.;
				}
			}
		});
	}

	private void testRotationButtonListener() {
		testRotationButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = getCurrentExperiment();
				if (exp != null) {

				}
			}
		});
	}

	public void resetFrameIndex() {
		startFrameJSpinner.setValue(0);
		referenceFrameJSpinner.setValue(0);
	}

	/**
	 * Gets the currently selected experiment.
	 */
	private Experiment getCurrentExperiment() {
		return (Experiment) experimentList.getSelectedItem();
	}

	private void updateButtonStates() {
		boolean isRunning = currentTask != null && !currentTask.isDone();

		runButton.setEnabled(true);
		startFrameJSpinner.setEnabled(!isRunning);
		referenceFrameJSpinner.setEnabled(!isRunning);
		applyTranslationButton.setEnabled(!isRunning);
		applyRotationButton.setEnabled(!isRunning);
		testTranslationButton.setEnabled(!isRunning);
		testRotationButton.setEnabled(!isRunning);

		// Enable restore button only if there's a previous translation
		restoreTranslationButton.setEnabled(!isRunning && (previousX != 0 || previousY != 0));
	}

	@Override
	public void viewerChanged(ViewerEvent event) {
		if ((event.getType() == ViewerEvent.ViewerEventType.POSITION_CHANGED) && (event.getDim() == DimensionId.T)) {
			Viewer v = event.getSource();
			int t = v.getPositionT();
			if (t >= 0) {
				if (t != previousT) {
					previousT = t;
					previousX = 0;
					previousY = 0;
					restoreTranslationButton.setEnabled(false);
				}
				startFrameJSpinner.setValue(t);
			}
		}
	}

	@Override
	public void viewerClosed(Viewer viewer) {
		viewer.removeListener(this);
	}

	private void executeRegistration(Experiment experiment) {
		if (currentTask != null && !currentTask.isDone()) {
			showError("Registration already in progress");
			return;
		}

		// Create registration options
		RegistrationOptions options = createRegistrationOptions(experiment);

		// Validate options
		ProcessingResult<Void> validationResult = options.validate();
		if (validationResult.isFailure()) {
			showError("Invalid registration options: " + validationResult.getErrorMessage());
			return;
		}

		// Execute registration asynchronously
		currentTask = CompletableFuture.runAsync(() -> {
			try {
				LOGGER.info("Starting registration for experiment: " + experiment.getResultsDirectory());

				ProcessingResult<RegistrationProcessor.RegistrationResult> result = registrationProcessor
						.correctDriftAndRotation(experiment, options);

				if (result.isFailure()) {
					showError("Registration failed: " + result.getErrorMessage());
				} else {
					RegistrationProcessor.RegistrationResult registrationResult = result.getDataOrThrow();
					showSuccess(
							"Registration completed successfully. Processed: " + registrationResult.getFramesProcessed()
									+ " frames, Corrected: " + registrationResult.getFramesCorrected() + " frames");
				}

			} catch (Exception e) {
				LOGGER.severe("Unexpected error during registration: " + e.getMessage());
				showError("Unexpected error during registration: " + e.getMessage());
			}
		});

		updateButtonStates();
		runButton.setText("STOP");
	}

	/**
	 * Stops the current computation.
	 */
	private void stopComputation() {
		if (currentTask != null && !currentTask.isDone()) {
			currentTask.cancel(true);
			LOGGER.info("Registration stopped by user");
		}
		updateButtonStates();
		runButton.setText("Run");
	}

	/**
	 * Creates registration options from current UI state.
	 */
	private RegistrationOptions createRegistrationOptions(Experiment experiment) {
		int referenceFrame = (int) referenceFrameJSpinner.getValue();
		int startFrame = 0; // (int) startFrameJSpinner.getValue();

		return new RegistrationOptions() //
				.fromFrame(startFrame) //
				.toFrame(referenceFrame - 1) //
				.referenceFrame(referenceFrame) //
				.translationThreshold(0.001) //
				.rotationThreshold(0.001) //
				.transformOptions(createTransformOptions(ImageTransformEnums.NONE)) //
				.saveCorrectedImages(true) //
				.preserveImageSize(true) //
				.referenceChannel(0) //
				.progressReporter(new ProgressReporter() {
					@Override
					public void updateMessage(String message) {
						// Update progress message if needed
					}

					@Override
					public void updateProgress(int percentage) {
						// Update progress percentage if needed
					}

					@Override
					public void completed() {
						updateButtonStates();
					}

					@Override
					public void failed(String errorMessage) {
						showError(errorMessage);
						updateButtonStates();
					}

					@Override
					public boolean isCancelled() {
						return false;
					}
				});
	}

	private ImageTransformOptions createTransformOptions(ImageTransformEnums transform) {
		ImageTransformOptions options = new ImageTransformOptions();
		options.transformOption = transform;
		return options;
	}

	/**
	 * Shows an error message to the user.
	 */
	private void showError(String message) {
		LOGGER.warning("User error: " + message);
		// In a real implementation, you'd show this in the UI
		// For now, we'll just log it
	}

	/**
	 * Shows a success message to the user.
	 */
	private void showSuccess(String message) {
		LOGGER.info("Success: " + message);
		// In a real implementation, you'd show this in the UI
		// For now, we'll just log it
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (StringUtil.equals("thread_ended", evt.getPropertyName())) {
			runButton.setText("Run registration");
			runButton.removePropertyChangeListener(this);
		}
	}

	private boolean testTranslation(Experiment exp, int x, int y) {
		int squareSize = (int) squareSizeSpinner.getValue();

		int testFrame = (int) startFrameJSpinner.getValue();
		int referenceFrame = (int) referenceFrameJSpinner.getValue();
		Sequence seq = exp.seqCamData.getSequence();
		IcyBufferedImage imageTest = seq.getImage(testFrame, 0);
		IcyBufferedImage imageReference = seq.getImage(referenceFrame, 0);
		Sequence chessSeq = createChessboardImage(imageTest, imageReference, squareSize);
		Viewer v = new Viewer(chessSeq, true);
		return (v != null);
	}

	private boolean applyTranslation(Experiment exp, int x, int y) {
		Vector2d translation = new Vector2d(x, y);
		int t = (int) referenceFrameJSpinner.getValue();
		Sequence seq = exp.seqCamData.getSequence();
		IcyBufferedImage workImage = seq.getImage(t, 0);
		workImage = GaspardRigidRegistration.applyTranslation2D(workImage, -1, translation, true);
		seq.setImage(t, 0, workImage);

		String fileName = exp.seqCamData.getFileNameFromImageList(t);
		File outputfile = new File(fileName);
		RenderedImage image = ImageUtil.toRGBImage(workImage);
		return ImageUtil.save(image, "jpg", outputfile);
	}

	private boolean applyRotation(Experiment exp, double angleDegrees) {
		int t = (int) referenceFrameJSpinner.getValue();
		double angleRadians = Math.toRadians(angleDegrees);
		Sequence seq = exp.seqCamData.getSequence();
		IcyBufferedImage workImage = seq.getImage(t, 0);
		workImage = GaspardRigidRegistration.applyRotation2D(workImage, -1, angleRadians, true);
		seq.setImage(t, 0, workImage);

		String fileName = exp.seqCamData.getFileNameFromImageList(t);
		File outputfile = new File(fileName);
		RenderedImage image = ImageUtil.toRGBImage(workImage);
		return ImageUtil.save(image, "jpg", outputfile);
	}

	private Sequence createChessboardImage(IcyBufferedImage s1Image, IcyBufferedImage s2Image, int squareSize) {

		Dimension resultDim = new Dimension(Math.max(s1Image.getWidth(), s2Image.getWidth()),
				Math.max(s1Image.getHeight(), s2Image.getHeight()));
		IcyBufferedImage resultImage = new IcyBufferedImage(resultDim.width, resultDim.height, s1Image.getSizeC(),
				s1Image.getDataType_());
		resultImage.beginUpdate();

		for (int ch = 0; ch < resultImage.getSizeC(); ch++) {
			double[] resultData = Array1DUtil.arrayToDoubleArray(resultImage.getDataXY(ch),
					resultImage.isSignedDataType());
			double[] s1Data = Array1DUtil.arrayToDoubleArray(s1Image.getDataXY(ch), resultImage.isSignedDataType());
			double[] s2Data = Array1DUtil.arrayToDoubleArray(s2Image.getDataXY(ch), resultImage.isSignedDataType());

			for (int j = 0; j < resultImage.getHeight(); j++) {

				int jResultOffset = j * resultImage.getWidth();
				int js1Offset = j * s1Image.getWidth();
				int js2Offset = j * s2Image.getWidth();

				for (int i = 0; i < resultImage.getWidth(); i++) {
					boolean showS1 = (((i / squareSize) % 2) + ((j / squareSize) % 2)) % 2 == 0;
					if (showS1) {
						if (i < s1Image.getWidth() && j < s1Image.getHeight()) {
							resultData[jResultOffset + i] = s1Data[js1Offset + i];
						} else {
							resultData[jResultOffset + i] = 0;
						}
					} else {
						if (i < s2Image.getWidth() && j < s2Image.getHeight()) {
							resultData[jResultOffset + i] = s2Data[js2Offset + i];
						} else {
							resultData[jResultOffset + i] = 0;
						}
					}
				}
			}

			Array1DUtil.doubleArrayToArray(resultData, resultImage.getDataXY(ch));
		}

		resultImage.endUpdate();
		Sequence result = new Sequence("composite image", resultImage);
		return result;
	}

}
