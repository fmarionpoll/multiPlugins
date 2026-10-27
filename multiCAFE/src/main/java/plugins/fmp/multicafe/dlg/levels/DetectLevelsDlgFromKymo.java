package plugins.fmp.multicafe.dlg.levels;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import icy.canvas.IcyCanvas;
import icy.gui.viewer.Viewer;
import icy.sequence.Sequence;
import icy.util.StringUtil;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.canvas2D.Canvas2D_3Transforms;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.series.DetectLevels;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.series.options.LevelDetectV2Options;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.overlay.OverlayThreshold;
import plugins.kernel.roi.roi2d.ROI2DRectangle;

public class DetectLevelsDlgFromKymo extends JPanel implements PropertyChangeListener {
	private static final long serialVersionUID = -6329863521455897561L;

	private JCheckBox pass1CheckBox = new JCheckBox("First detection pass", true);
	private JComboBox<String> direction1ComboBox = new JComboBox<String>(
			new String[] { ">", "<" });
	private JSpinner threshold1Spinner = new JSpinner(new SpinnerNumberModel(35, 1, 255, 1));
	private ImageTransformEnums[] transformPass1 = new ImageTransformEnums[] { ImageTransformEnums.R_RGB,
			ImageTransformEnums.G_RGB, ImageTransformEnums.B_RGB, ImageTransformEnums.B_MINUS_MINRG,
			ImageTransformEnums.B_MINUS_MEANGREY_CTR, ImageTransformEnums.R2MINUS_GB, ImageTransformEnums.G2MINUS_RB,
			ImageTransformEnums.B2MINUS_RG, ImageTransformEnums.RGB, ImageTransformEnums.GBMINUS_2R,
			ImageTransformEnums.RBMINUS_2G, ImageTransformEnums.RGMINUS_2B, ImageTransformEnums.RGB_DIFFS,
			ImageTransformEnums.H_HSB, ImageTransformEnums.S_HSB, ImageTransformEnums.B_HSB };
	public JComboBox<ImageTransformEnums> transformPass1ComboBox = new JComboBox<ImageTransformEnums>(transformPass1);
	private JToggleButton transformPass1DisplayButton = new JToggleButton("View");

	private JCheckBox pass2CheckBox = new JCheckBox("Second detection pass", false);
	private JComboBox<String> direction2ComboBox = new JComboBox<String>(
			new String[] { ">", "<" });
	private JSpinner threshold2Spinner = new JSpinner(new SpinnerNumberModel(40, 1, 255, 1));
	private ImageTransformEnums[] transformPass2 = new ImageTransformEnums[] { ImageTransformEnums.DERICHE_COLOR,
			ImageTransformEnums.DERICHE, ImageTransformEnums.YDIFFN, ImageTransformEnums.YDIFFN2,
			ImageTransformEnums.MINUSHORIZAVG, ImageTransformEnums.COLORDISTANCE_L1_Y,
			ImageTransformEnums.COLORDISTANCE_L2_Y, ImageTransformEnums.SUBTRACT_1RSTCOL,
			ImageTransformEnums.L1DIST_TO_1RSTCOL };
	private JComboBox<ImageTransformEnums> transformPass2ComboBox = new JComboBox<ImageTransformEnums>(transformPass2);
	private JToggleButton transformPass2DisplayButton = new JToggleButton("View");
	private JSpinner jitter2Spinner = new JSpinner(new SpinnerNumberModel(5, 0, 255, 1));

	private JComboBox<String> kymographRangeComboBox = new JComboBox<>(
			new String[] { "All kymographs", "Selected kymograph" });
	private JSpinner spanTopSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 100, 1));
	private String detectString = "Detect";
	private JButton detectButton = new JButton(detectString);
	private JCheckBox fromRectangleCheckBox = new JCheckBox("Rectangle ROI (upper meniscus only)", false);

	private JComboBox<String> experimentRangeComboBox = new JComboBox<>(
			new String[] { "Current experiment", "Current to last experiment" });
	private JCheckBox leftCheckBox = new JCheckBox("Left", true);
	private JCheckBox rightCheckBox = new JCheckBox("Right", true);
	private JCheckBox runBackwardsCheckBox = new JCheckBox("run backwards", false);

	private JComboBox<String> detectionMethodComboBox = new JComboBox<>(
			new String[] { "Independent columns", "Track across time" });
	private JCheckBox smoothCheckBox = new JCheckBox("Smooth detected curve", false);
	private JCheckBox bottomCheckBox = new JCheckBox("Detect bottom level", true);
	private boolean detectBottomBeforeRectangle = true;
	private JCheckBox removeHzAvgCheckBox = new JCheckBox("Subtract horizontal bands", false);
	private JCheckBox tapePrepassCheckBox = new JCheckBox("Suppress tape seams", false);
	private JCheckBox edgePeakCheckBox = new JCheckBox("Prefer edge peaks", true);
	private JSpinner trackUpSpinner = new JSpinner(new SpinnerNumberModel(3, 0, 100, 1));
	private JSpinner trackDownSpinner = new JSpinner(new SpinnerNumberModel(25, 0, 500, 1));
	private JSpinner medianWinSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 51, 2));
	private JSpinner maxSpikeSpinner = new JSpinner(new SpinnerNumberModel(4, 0, 100, 1));
	private JPanel methodSettingsPanel;
	private JPanel smoothingPanel;
	private MultiCAFE parent0 = null;
	private DetectLevels threadDetectLevels = null;

	private JButton refineMeniscus = new JButton("Refine existing meniscus...");

	private String SEARCHRECT = new String("search_rectangle");
	private ROI2DRectangle searchRectangleROI2D = null;
	private OverlayThreshold overlayThreshold = null;
	private int currentKymographImage = 0;
	private boolean suppressDisplayUpdate = false;

	// -----------------------------------------------------

	void init(GridLayout capLayout, MultiCAFE parent0) {
		this.parent0 = parent0;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		JPanel scope = section("Scope and search region");
		scope.add(row(experimentRangeComboBox, kymographRangeComboBox, leftCheckBox, rightCheckBox));
		scope.add(row(fromRectangleCheckBox, bottomCheckBox));

		JPanel detection = section("Detection");
		detection.add(row(new JLabel("Method:"), detectionMethodComboBox));
		detection.add(row(new JLabel("Image:"), transformPass1ComboBox,
				new JLabel("Threshold:"), direction1ComboBox, threshold1Spinner, transformPass1DisplayButton));

		JPanel independent = new JPanel(new GridLayout(2, 1));
		independent.add(row(pass1CheckBox));
		independent.add(row(pass2CheckBox, transformPass2ComboBox, new JLabel("Threshold:"), direction2ComboBox,
				threshold2Spinner, new JLabel("Vertical jitter (px):"), jitter2Spinner,
				transformPass2DisplayButton));

		runBackwardsCheckBox.setText("Forward + backward tracking");
		JPanel tracking = new JPanel(new GridLayout(2, 1));
		tracking.add(row(new JLabel("Maximum movement (px): up"), trackUpSpinner,
				new JLabel("down"), trackDownSpinner, runBackwardsCheckBox));
		tracking.add(row(removeHzAvgCheckBox, tapePrepassCheckBox, edgePeakCheckBox));

		// Reserve space for both methods so switching does not move other sections.
		methodSettingsPanel = new JPanel(new CardLayout());
		methodSettingsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		methodSettingsPanel.add(independent, "columns");
		methodSettingsPanel.add(tracking, "tracking");
		detection.add(methodSettingsPanel);

		JPanel cleanup = section("Curve cleanup");
		smoothingPanel = row(new JLabel("Median window (time columns):"), medianWinSpinner,
				new JLabel("Maximum spike (px):"), maxSpikeSpinner);
		cleanup.add(row(smoothCheckBox, new JLabel("May alter short gulp events.")));
		cleanup.add(smoothingPanel);

		JPanel refinement = section("Refinement");
		refinement.add(row(refineMeniscus, new JLabel("Correlation refinement with preview and undo.")));
		add(scope);
		add(detection);
		add(cleanup);
		add(refinement);
		add(row(detectButton));
		for (JCheckBox control : new JCheckBox[] { removeHzAvgCheckBox, tapePrepassCheckBox, edgePeakCheckBox,
				runBackwardsCheckBox })
			control.addActionListener(e -> updateOverlayThreshold());
		for (JSpinner control : new JSpinner[] { trackUpSpinner, trackDownSpinner, medianWinSpinner, maxSpikeSpinner })
			control.addChangeListener(e -> updateOverlayThreshold());
		updateModeControls();

		defineToolTips();
		defineActionListeners();
		defineItemListeners();
		allowItemsAccordingToSelection();
		transformPass1ComboBox.setSelectedItem(ImageTransformEnums.RGB_DIFFS);
	}

	private void defineToolTips() {
		experimentRangeComboBox
				.setToolTipText("Run level detection for the current experiment through the last in the browse list.");
		pass1CheckBox.setToolTipText("First (coarse) threshold pass on the kymograph.");
		pass2CheckBox.setToolTipText("Second (refined) threshold pass on the kymograph.");
		detectionMethodComboBox.setToolTipText("Detect each time column independently, or track the meniscus across time.");
		smoothCheckBox.setToolTipText(
				"Apply median and spike filtering to the top-level curve; may alter short gulp events.");
		bottomCheckBox.setToolTipText("Also detect the bottom boundary using the first transform and threshold.");
		fromRectangleCheckBox.setToolTipText(
				"Repair the upper meniscus inside a rectangle ROI; bottom detection is temporarily disabled.");
		runBackwardsCheckBox.setToolTipText("Combine forward and backward tracks to reject inconsistent jumps.");
		removeHzAvgCheckBox
				.setToolTipText("Subtract each row mean to suppress persistent full-width horizontal bands.");
		tapePrepassCheckBox
				.setToolTipText("Identify thin persistent tape seams and prefer a liquid boundary below them.");
		refineMeniscus.setToolTipText("Refine an existing upper meniscus curve, with preview, acceptance and undo.");
	}

	private Experiment currentExperiment() {
		return parent0 == null ? null : (Experiment) parent0.expListComboLazy.getSelectedItem();
	}

	private static JPanel row(Component... controls) {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 1));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (Component control : controls)
			panel.add(control);
		return panel;
	}

	private static JPanel section(String title) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createTitledBorder(title));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	private boolean isTracking() {
		return detectionMethodComboBox.getSelectedIndex() == 1;
	}

	private void updateModeControls() {
		((CardLayout) methodSettingsPanel.getLayout()).show(methodSettingsPanel,
				isTracking() ? "tracking" : "columns");
		boolean secondPass = !isTracking() && pass2CheckBox.isSelected();
		for (Component control : new Component[] { transformPass2ComboBox, direction2ComboBox,
				threshold2Spinner, jitter2Spinner, transformPass2DisplayButton })
			control.setEnabled(secondPass);
		for (Component control : smoothingPanel.getComponents())
			control.setEnabled(smoothCheckBox.isSelected());
		revalidate();
		repaint();
	}

	private LevelDetectV2Options readTrackingOptions() {
		LevelDetectV2Options v2 = new LevelDetectV2Options();
		v2.transform = (ImageTransformEnums) transformPass1ComboBox.getSelectedItem();
		v2.threshold = (int) threshold1Spinner.getValue();
		v2.directionUp = direction1ComboBox.getSelectedIndex() == 0;
		v2.removeHorizontalAverage = removeHzAvgCheckBox.isSelected();
		v2.tapePrepass = tapePrepassCheckBox.isSelected();
		v2.edgePeak = edgePeakCheckBox.isSelected();
		v2.runBackwards = runBackwardsCheckBox.isSelected();
		v2.trackUp = (int) trackUpSpinner.getValue();
		v2.trackDown = (int) trackDownSpinner.getValue();
		v2.medianWindow = (int) medianWinSpinner.getValue();
		v2.maxSpikePx = (int) maxSpikeSpinner.getValue();
		return v2;
	}

	private void defineItemListeners() {
		threshold1Spinner.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				updateOverlayThreshold();
			}
		});

		threshold2Spinner.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				updateOverlayThreshold();
			}
		});

		jitter2Spinner.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				updateOverlayThreshold();
			}
		});
	}

	private void defineActionListeners() {
		transformPass1ComboBox.addActionListener(e -> {
			if (suppressDisplayUpdate) {
				return;
			}
			Experiment exp = currentExperiment();
			if (exp != null && exp.getSeqKymos() != null) {
				Canvas2D_3Transforms canvas = getKymosCanvas(exp);
				if (canvas != null && transformPass1DisplayButton.isSelected()) {
					int index = transformPass1ComboBox.getSelectedIndex();
					canvas.updateTransformsStep1(transformPass1);
					canvas.setTransformStep1(index + 1, null);
					updateOverlayThreshold();
				}
			}
		});

		transformPass2ComboBox.addActionListener(e -> {
			if (suppressDisplayUpdate) {
				return;
			}
			allowItemsAccordingToSelection();
			Experiment exp = currentExperiment();
			if (exp != null && exp.getSeqKymos() != null) {
				Canvas2D_3Transforms canvas = getKymosCanvas(exp);
				if (canvas != null && transformPass2DisplayButton.isSelected()) {
					int index = transformPass2ComboBox.getSelectedIndex();
					canvas.updateTransformsStep1(transformPass2);
					canvas.setTransformStep1(index + 1, null);
					updateOverlayThreshold();
				}
			}
		});

		detectButton.addActionListener(e -> {
			if (detectButton.getText().equals(detectString))
				startLevelsDetection();
			else
				stopLevelsDetection();
		});

		transformPass1DisplayButton.addActionListener(e -> {
			Experiment exp = currentExperiment();
			if (exp == null)
				return;

			if (transformPass1DisplayButton.isSelected()) {
				if (parent0.paneLevels != null)
					parent0.paneLevels.clearLevelsV2View();
				transformPass2DisplayButton.setSelected(false);
				Canvas2D_3Transforms canvas = getKymosCanvas(exp);
				if (canvas != null) {
					canvas.updateTransformsStep1(transformPass1);
					int index = transformPass1ComboBox.getSelectedIndex();
					canvas.setTransformStep1(index + 1, null);
				}
				addOverlayToSequence(exp);
			} else {
				removeOverlay(exp);
				Canvas2D_3Transforms canvas = getKymosCanvas(exp);
				if (canvas != null)
					canvas.setTransformStep2Index(0);
				canvas.setTransformStep1Index(0);
			}
		});

		transformPass2DisplayButton.addActionListener(e -> {
			Experiment exp = currentExperiment();
			if (exp == null)
				return;

			if (transformPass2DisplayButton.isSelected()) {
				if (parent0.paneLevels != null)
					parent0.paneLevels.clearLevelsV2View();
				transformPass1DisplayButton.setSelected(false);
				Canvas2D_3Transforms canvas = getKymosCanvas(exp);
				if (canvas != null) {
					canvas.updateTransformsStep1(transformPass2);
					int index = transformPass2ComboBox.getSelectedIndex();
					canvas.setTransformStep1(index + 1, null);
				}
				addOverlayToSequence(exp);
			} else {
				removeOverlay(exp);
				Canvas2D_3Transforms canvas = getKymosCanvas(exp);
				if (canvas != null)
					canvas.setTransformStep2Index(0);
				canvas.setTransformStep1Index(0);
			}
		});

		experimentRangeComboBox.addActionListener(e -> {
			Color color = Color.BLACK;
			if (experimentRangeComboBox.getSelectedIndex() == 1)
				color = Color.RED;
			experimentRangeComboBox.setForeground(color);
			detectButton.setForeground(color);
		});

		fromRectangleCheckBox.addItemListener(e -> {
			if (fromRectangleCheckBox.isSelected()) {
				detectBottomBeforeRectangle = bottomCheckBox.isSelected();
				bottomCheckBox.setSelected(false);
			} else {
				bottomCheckBox.setSelected(detectBottomBeforeRectangle);
			}
			bottomCheckBox.setEnabled(!fromRectangleCheckBox.isSelected());
			Experiment exp = currentExperiment();
			if (exp == null)
				return;
			if (fromRectangleCheckBox.isSelected())
				displaySearchArea(exp);
			else if (searchRectangleROI2D != null)
				exp.getSeqKymos().getSequence().removeROI(searchRectangleROI2D);
		});

		direction1ComboBox.addActionListener(e -> updateOverlayThreshold());
		direction2ComboBox.addActionListener(e -> updateOverlayThreshold());

		detectionMethodComboBox.addActionListener(e -> {
			if (!suppressDisplayUpdate)
				resetDisplayToRaw(currentExperiment());
			updateModeControls();
		});
		smoothCheckBox.addActionListener(e -> {
			updateModeControls();
			updateOverlayThreshold();
		});

		pass2CheckBox.addActionListener(e -> {
			if (!pass2CheckBox.isSelected() && transformPass2DisplayButton.isSelected())
				resetDisplayToRaw(currentExperiment());
			updateModeControls();
		});
		refineMeniscus.addActionListener(e -> MeniscusRefinementBrowser.open(this, parent0.expListComboLazy, null, null));
	}

	void allowItemsAccordingToSelection() {
		updateModeControls();
	}

	void setDialogFromOptions(Capillary cap) {
		if (cap == null) {
			return;
		}
		boolean keepView1 = transformPass1DisplayButton.isSelected();
		boolean keepView2 = transformPass2DisplayButton.isSelected();
		// While View is on, keep the user's current knobs so browsing kymos does not
		// overwrite threshold/transform before a Detect-all.
		if (keepView1 || keepView2) {
			reapplyViewIfSelected();
			return;
		}
		applyLevelOptionsToDialog(cap.getProperties().getLimitsOptions());
	}

	public void loadLevelDefaultsFromExperiment(Experiment exp) {
		if (exp != null) {
			applyLevelOptionsToDialog(exp.getLevelDetectionDefaults());
			resetDisplayToRaw(exp);
		}
	}

	/**
	 * Keep transform View + overlay when browsing kymographs (same idea as Levels
	 * v2).
	 */
	public void reapplyViewIfSelected() {
		Experiment exp = currentExperiment();
		if (exp == null || exp.getSeqKymos() == null)
			return;
		if (transformPass1DisplayButton.isSelected()) {
			Canvas2D_3Transforms canvas = getKymosCanvas(exp);
			if (canvas != null) {
				canvas.updateTransformsStep1(transformPass1);
				canvas.setTransformStep1(transformPass1ComboBox.getSelectedIndex() + 1, null);
			}
			addOverlayToSequence(exp);
		} else if (transformPass2DisplayButton.isSelected()) {
			Canvas2D_3Transforms canvas = getKymosCanvas(exp);
			if (canvas != null) {
				canvas.updateTransformsStep1(transformPass2);
				canvas.setTransformStep1(transformPass2ComboBox.getSelectedIndex() + 1, null);
			}
			addOverlayToSequence(exp);
		}
	}

	void resetDisplayToRaw(Experiment exp) {
		boolean wasViewing = transformPass1DisplayButton.isSelected() || transformPass2DisplayButton.isSelected();
		transformPass1DisplayButton.setSelected(false);
		transformPass2DisplayButton.setSelected(false);
		if (exp != null) {
			removeOverlay(exp);
			if (wasViewing) {
				Canvas2D_3Transforms canvas = getKymosCanvas(exp);
				if (canvas != null) {
					canvas.setTransformStep2Index(0);
					canvas.setTransformStep1Index(0);
				}
			}
		}
	}

	private void applyLevelOptionsToDialog(BuildSeriesOptions options) {
		if (options == null) {
			return;
		}

		suppressDisplayUpdate = true;
		try {
			// Leave ROI mode before loading another recipe, restoring its temporary state.
			fromRectangleCheckBox.setSelected(false);
			detectionMethodComboBox.setSelectedIndex(options.levelTracking ? 1 : 0);
			smoothCheckBox.setSelected(options.levelSmoothing);
			bottomCheckBox.setSelected(options.detectBottom);
			LevelDetectV2Options v2 = options.levelV2;
			removeHzAvgCheckBox.setSelected(v2.removeHorizontalAverage);
			tapePrepassCheckBox.setSelected(v2.tapePrepass);
			edgePeakCheckBox.setSelected(v2.edgePeak);
			runBackwardsCheckBox.setSelected(v2.runBackwards);
			trackUpSpinner.setValue(v2.trackUp);
			trackDownSpinner.setValue(v2.trackDown);
			medianWinSpinner.setValue(v2.medianWindow);
			maxSpikeSpinner.setValue(v2.maxSpikePx);
			pass1CheckBox.setSelected(options.pass1);
			pass2CheckBox.setSelected(options.pass2);

			transformPass1ComboBox.setSelectedItem(options.transform01);
			int index = options.directionUp1 ? 0 : 1;
			direction1ComboBox.setSelectedIndex(index);
			threshold1Spinner.setValue(options.detectLevel1Threshold);

			transformPass2ComboBox.setSelectedItem(options.transform02);
			index = options.directionUp2 ? 0 : 1;
			direction2ComboBox.setSelectedIndex(index);
			threshold2Spinner.setValue(options.detectLevel2Threshold);
			jitter2Spinner.setValue(options.jitter2);
			leftCheckBox.setSelected(options.detectL);
			rightCheckBox.setSelected(options.detectR);
		} finally {
			suppressDisplayUpdate = false;
		}
		allowItemsAccordingToSelection();
		updateModeControls();
	}

	void restoreDialogFromSessionAfterDetection(BuildSeriesOptions session, Experiment exp) {
		if (session == null) {
			return;
		}
		boolean restoreBottom = session.analyzePartOnly;
		boolean previousBottom = detectBottomBeforeRectangle;
		applyLevelOptionsToDialog(session);
		if (restoreBottom)
			bottomCheckBox.setSelected(previousBottom);
		resetDisplayToRaw(exp);
	}

	void setOptionsFromDialog(Capillary cap) {
		BuildSeriesOptions options = cap.getProperties().getLimitsOptions();
		options.pass1 = pass1CheckBox.isSelected();
		options.pass2 = pass2CheckBox.isSelected();
		options.transform01 = (ImageTransformEnums) transformPass1ComboBox.getSelectedItem();
		options.transform02 = (ImageTransformEnums) transformPass2ComboBox.getSelectedItem();
		options.directionUp1 = (direction1ComboBox.getSelectedIndex() == 0);
		options.detectLevel1Threshold = (int) threshold1Spinner.getValue();
		options.directionUp2 = (direction2ComboBox.getSelectedIndex() == 0);
		options.detectLevel2Threshold = (int) threshold2Spinner.getValue();
		options.detectSelectedKymo = kymographRangeComboBox.getSelectedIndex() == 1;

		options.detectL = leftCheckBox.isSelected();
		options.detectR = rightCheckBox.isSelected();
	}

	private BuildSeriesOptions initBuildParameters(Experiment exp) {
		BuildSeriesOptions options = new BuildSeriesOptions();
		// list of stack experiments
		options.expList = parent0.expListComboLazy;
		options.expList.index0 = parent0.expListComboLazy.getSelectedIndex();
		if (experimentRangeComboBox.getSelectedIndex() == 1)
			options.expList.index1 = options.expList.getItemCount() - 1;
		else
			options.expList.index1 = parent0.expListComboLazy.getSelectedIndex();
		options.detectSelectedKymo = kymographRangeComboBox.getSelectedIndex() == 1;

		if (kymographRangeComboBox.getSelectedIndex() == 1) {
			options.kymoFirst = exp.getSeqKymos().getSequence().getFirstViewer().getPositionT();
			options.kymoLast = options.kymoFirst;
			currentKymographImage = options.kymoFirst;
		} else {
			options.kymoFirst = 0;
			options.kymoLast = exp.getSeqKymos().getSequence().getSizeT() - 1;
			currentKymographImage = 0;
		}
		// other parameters
		options.pass1 = pass1CheckBox.isSelected();
		options.transform01 = (ImageTransformEnums) transformPass1ComboBox.getSelectedItem();
		options.directionUp1 = (direction1ComboBox.getSelectedIndex() == 0);
		options.detectLevel1Threshold = (int) threshold1Spinner.getValue();

		options.pass2 = pass2CheckBox.isSelected();
		options.transform02 = (ImageTransformEnums) transformPass2ComboBox.getSelectedItem();
		options.directionUp2 = (direction2ComboBox.getSelectedIndex() == 0);
		options.detectLevel2Threshold = (int) threshold2Spinner.getValue();
		options.jitter2 = (int) jitter2Spinner.getValue();

		options.analyzePartOnly = fromRectangleCheckBox.isSelected();
		options.searchArea = getSearchAreaFromSearchRectangle(exp,
				fromRectangleCheckBox.isSelected() && searchRectangleROI2D != null);

		options.spanDiffTop = (int) spanTopSpinner.getValue();
		options.detectL = leftCheckBox.isSelected();
		options.detectR = rightCheckBox.isSelected();
		options.parent0Rect = parent0.mainFrame.getBoundsInternal();
		options.binSubDirectory = parent0.expListComboLazy.expListBinSubDirectory;
		options.runBackwards = runBackwardsCheckBox.isSelected();
		options.sourceCamDirect = false;
		options.detectTop = true;
		options.detectBottom = !fromRectangleCheckBox.isSelected() && bottomCheckBox.isSelected();
		options.levelTracking = isTracking();
		options.levelSmoothing = smoothCheckBox.isSelected();
		options.levelV2 = readTrackingOptions();
		options.transformBottom = options.transform01;
		options.directionUpBottom = options.directionUp1;
		options.detectLevelBottomThreshold = options.detectLevel1Threshold;
		options.bottomSearchFromBottomPx = 0;
		return options;
	}

	void startLevelsDetection() {
		Experiment exp = currentExperiment();
		if (exp != null) {

			threadDetectLevels = new DetectLevels();
			threadDetectLevels.options = initBuildParameters(exp);
			if (!fromRectangleCheckBox.isSelected()) {
				exp.getCapillaries().clearKymoMeasuresOnly(threadDetectLevels.options.kymoFirst,
						threadDetectLevels.options.kymoLast, threadDetectLevels.options.detectL,
						threadDetectLevels.options.detectR, threadDetectLevels.options.detectTop,
						threadDetectLevels.options.detectBottom);
			}
			threadDetectLevels.addPropertyChangeListener(this);
			threadDetectLevels.execute();
			detectionMethodComboBox.setEnabled(false);
			smoothCheckBox.setEnabled(false);
			bottomCheckBox.setEnabled(false);
			fromRectangleCheckBox.setEnabled(false);
			detectButton.setText("STOP");
		}
	}

	private void stopLevelsDetection() {
		if (threadDetectLevels != null && !threadDetectLevels.stopFlag)
			threadDetectLevels.stopFlag = true;
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (StringUtil.equals("thread_ended", evt.getPropertyName())) {
			detectButton.setText(detectString);
			detectionMethodComboBox.setEnabled(true);
			smoothCheckBox.setEnabled(true);
			bottomCheckBox.setEnabled(!fromRectangleCheckBox.isSelected());
			fromRectangleCheckBox.setEnabled(true);
			Logger.debug("thread_ended");
			Experiment exp = currentExperiment();
			if (exp != null && threadDetectLevels != null && threadDetectLevels.options != null) {
				exp.applyLevelDetectionDefaultsFrom(threadDetectLevels.options);
				exp.saveExperimentDescriptors();
			}
			parent0.paneKymos.tabIntervals.selectKymographImage(currentKymographImage, false);
			if (exp != null && threadDetectLevels != null && threadDetectLevels.options != null) {
				restoreDialogFromSessionAfterDetection(threadDetectLevels.options, exp);
			}
			parent0.paneKymos.tabIntervals.indexImagesCombo = -1;
			fromRectangleCheckBox.setSelected(false);
		}
	}

	private void displaySearchArea(Experiment exp) {
		if (exp.getSeqKymos() == null || exp.getSeqKymos().getSequence() == null)
			return;

		Sequence seq = exp.getSeqKymos().getSequence();
		if (searchRectangleROI2D == null) {
			Rectangle searchRectangle = seq.getBounds2D();
			searchRectangleROI2D = new ROI2DRectangle(searchRectangle);
			searchRectangleROI2D.setName(SEARCHRECT);
			searchRectangleROI2D.setColor(Color.ORANGE);
		}
		searchRectangleROI2D.setT(-1);
		seq.addROI(searchRectangleROI2D);
		seq.setSelectedROI(searchRectangleROI2D);
		seq.roiChanged(searchRectangleROI2D);
	}

	private Rectangle getSearchAreaFromSearchRectangle(Experiment exp, boolean fitSmallerRectangle) {
		Rectangle seqBounds = exp.getSeqKymos().getSequence().getBounds2D();
		int seqW = Math.max(0, seqBounds.width);
		int seqH = Math.max(0, seqBounds.height);
		if (fitSmallerRectangle && searchRectangleROI2D != null) {
			Rectangle r = searchRectangleROI2D.getBounds();
			int x = Math.max(0, r.x);
			int y = Math.max(0, r.y);
			int w = r.width;
			int h = r.height;
			if (x + w > seqW)
				w = seqW - x;
			if (y + h > seqH)
				h = seqH - y;
			return new Rectangle(x, y, Math.max(0, w), Math.max(0, h));
		}
		return new Rectangle(0, 0, seqW, seqH);
	}

	protected Canvas2D_3Transforms getKymosCanvas(Experiment exp) {
		if (exp.getSeqKymos() == null || exp.getSeqKymos().getSequence() == null)
			return null;
		if (exp.getSeqKymos().getSequence().getFirstViewer() == null)
			parent0.paneKymos.tabIntervals.displayON();
		Viewer v = exp.getSeqKymos().getSequence().getFirstViewer();
		if (v == null)
			return null;
		if (v.getCanvas() instanceof Canvas2D_3Transforms)
			return (Canvas2D_3Transforms) v.getCanvas();
		return null;
	}

	void addOverlayToSequence(Experiment exp) {
		if (exp.getSeqKymos() == null || exp.getSeqKymos().getSequence() == null)
			return;

		Sequence seq = exp.getSeqKymos().getSequence();
		if (seq.getFirstViewer() == null)
			parent0.paneKymos.tabIntervals.displayON();

		if (overlayThreshold == null)
			overlayThreshold = new OverlayThreshold(seq);
		else {
			seq.removeOverlay(overlayThreshold);
			overlayThreshold.setSequence(seq);
		}
		overlayThreshold.setBoundaryOnlyMode(true);
		seq.addOverlay(overlayThreshold);

		Viewer v = seq.getFirstViewer();
		if (v != null) {
			IcyCanvas canvas = v.getCanvas();
			if (canvas != null) {
				if (!canvas.hasLayer(overlayThreshold))
					canvas.addLayer(overlayThreshold);
				if (!canvas.isLayersVisible())
					canvas.setLayersVisible(true);
			}
		}

		updateOverlayThreshold();
		seq.overlayChanged(overlayThreshold);
		seq.dataChanged();
	}

	void updateOverlayThreshold() {
		if (suppressDisplayUpdate || overlayThreshold == null)
			return;

		if (isTracking() && transformPass1DisplayButton.isSelected()) {
			Canvas2D_3Transforms canvas = getKymosCanvas(currentExperiment());
			if (canvas != null) {
				if (removeHzAvgCheckBox.isSelected()) {
					canvas.updateTransformsStep1(new ImageTransformEnums[] { ImageTransformEnums.MINUSHORIZAVG });
					canvas.setTransformStep1(ImageTransformEnums.MINUSHORIZAVG, null);
					canvas.setTransformStep2((ImageTransformEnums) transformPass1ComboBox.getSelectedItem(), null);
				} else {
					canvas.setTransformStep2Index(0);
					canvas.updateTransformsStep1(transformPass1);
					canvas.setTransformStep1(transformPass1ComboBox.getSelectedIndex() + 1, null);
				}
			}
			LevelDetectV2Options v2 = readTrackingOptions();
			if (!smoothCheckBox.isSelected()) {
				v2.medianWindow = 1;
				v2.maxSpikePx = 0;
			}
			Experiment exp = currentExperiment();
			Rectangle search = exp == null ? null
					: getSearchAreaFromSearchRectangle(exp,
							fromRectangleCheckBox.isSelected() && searchRectangleROI2D != null);
			overlayThreshold.setThresholdV2Preview(v2, search);
		} else if (transformPass1DisplayButton.isSelected()) {
			boolean ifGreater = (direction1ComboBox.getSelectedIndex() == 0);
			int threshold = (int) threshold1Spinner.getValue();
			ImageTransformEnums transform = (ImageTransformEnums) transformPass1ComboBox.getSelectedItem();
			overlayThreshold.setThresholdSingle(threshold, transform, ifGreater);

		} else if (transformPass2DisplayButton.isSelected()) {
			boolean ifGreater = (direction2ComboBox.getSelectedIndex() == 0);
			int threshold = (int) threshold2Spinner.getValue();
			ImageTransformEnums transform = (ImageTransformEnums) transformPass2ComboBox.getSelectedItem();
			int jitter2 = (int) jitter2Spinner.getValue();

			int[] initialLevels = getInitialLevelPositions();
			if (initialLevels != null && jitter2 > 0) {
				overlayThreshold.setThresholdSingleWithJitter(threshold, transform, ifGreater, jitter2, initialLevels);
			} else {
				overlayThreshold.setThresholdSingle(threshold, transform, ifGreater);
			}

		} else {
			return;
		}
		overlayThreshold.setBoundaryOnlyMode(true);
		overlayThreshold.painterChanged();
		if (overlayThreshold.getSequence() != null) {
			overlayThreshold.getSequence().overlayChanged(overlayThreshold);
			overlayThreshold.getSequence().dataChanged();
		}
	}

	private int[] getInitialLevelPositions() {
		Experiment exp = currentExperiment();
		if (exp == null || exp.getSeqKymos() == null)
			return null;

		int currentKymoIndex = exp.getSeqKymos().getSequence().getFirstViewer().getPositionT();
		if (currentKymoIndex < 0 || exp.getCapillaries() == null || exp.getCapillaries().getList() == null)
			return null;

		if (currentKymoIndex >= exp.getCapillaries().getList().size())
			return null;

		Capillary cap = exp.getCapillaries().getList().get(currentKymoIndex);
		if (cap == null || cap.getTopRaw() == null)
			return null;

		if (cap.getTopRaw().polylineLevel == null || cap.getTopRaw().polylineLevel.npoints == 0) {
			if (cap.getTopRaw().limit != null && cap.getTopRaw().limit.length > 0) {
				return cap.getTopRaw().limit;
			}
			return null;
		}

		int npoints = cap.getTopRaw().polylineLevel.npoints;
		int[] levels = new int[npoints];
		for (int i = 0; i < npoints; i++) {
			levels[i] = (int) cap.getTopRaw().polylineLevel.ypoints[i];
		}
		return levels;
	}

	void removeOverlay(Experiment exp) {
		if (exp.getSeqKymos() == null || exp.getSeqKymos().getSequence() == null)
			return;
		Sequence seq = exp.getSeqKymos().getSequence();
		Viewer v = seq.getFirstViewer();
		if (v != null) {
			IcyCanvas canvas = v.getCanvas();
			if (canvas != null && canvas.hasLayer(overlayThreshold))
				canvas.removeLayer(overlayThreshold);
		}
		seq.removeOverlay(overlayThreshold);
	}

}
