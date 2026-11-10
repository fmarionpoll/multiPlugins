package plugins.fmp.multicafe.dlg.levels;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.SpinnerNumberModel;

import plugins.fmp.multitools.experiment.capillary.Capillary;

/**
 * Widgets and task layout only. Detection, session state and persistence live
 * outside this view.
 */
final class IntakeControls {
	final JTabbedPane tasks = new JTabbedPane();
	final JComboBox<String> experimentMode = new JComboBox<>(
			new String[] { "Automatic detection", "Current thresholds and edits" });
	final JComboBox<String> saveScope = new JComboBox<>(new String[] { "This capillary", "Whole experiment" });
	final JLabel thresholdMode = new JLabel("Automatic");
	final JLabel warningSummary = new JLabel();
	final JComboBox<String> seriesRange = new JComboBox<>(
			new String[] { "Current experiment", "Current experiment to end", "All experiments (0 to end)" });
	final JButton runSeriesSave = new JButton("Detect & save");
	final JButton runSeries = new JButton("Detect, replace gulps & save"), stopSeries = new JButton("Stop");
	final JLabel seriesStatus = new JLabel("Uses automatic thresholds for every capillary.");
	final JPanel seriesControls = new JPanel(new BorderLayout());
	final JButton detectAll = new JButton("Detect all"), saveAll = new JButton("Save all intake"),
			cancelAll = new JButton("Cancel");
	final JButton copyAllToGulps = new JButton("Replace all gulps...");
	final JLabel batchStatus = new JLabel(" ");
	final JPanel batchControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
	final JComboBox<Capillary> capillaries = new JComboBox<>();
	final JButton previousButton = new JButton("<");
	final JButton nextButton = new JButton(">");
	final JSpinner threshold = new JSpinner(new SpinnerNumberModel(1.0, 0.0, 10000.0, .05));
	final JButton automatic = new JButton("Use automatic"), export = new JButton("Export CSV...");
	final JButton refreshDetection = new JButton("Refresh detection");
	final JButton review = new JButton("Review interval...");
	final JButton exportExperiment = new JButton("Export experiment review...");
	final JButton saveAllEdited = new JButton("Save intake for all capillaries...");
	final JButton copyAllEdited = new JButton("Replace gulps for all capillaries...");
	final JButton save = new JButton("Save intake"), copyToGulps = new JButton("Replace gulps...");
	final JCheckBox addGulp = new JCheckBox("Edit intakes", true);
	final JComboBox<IntakeAlignmentView.BarColor> intakeColor = new JComboBox<>(IntakeAlignmentView.BarColor.values());
	final JCheckBox previewPlacement = new JCheckBox("Preview image alignment");
	final JSpinner positionOffset = new JSpinner(new SpinnerNumberModel(0.0, -20.0, 20.0, .25));
	final JCheckBox includeManualPositions = new JCheckBox("Include hand-placed bars");
	final JButton applyPositionOffset = new JButton("Apply offset");
	final JButton applyToprawOffset = new JButton("Apply to topraw");
	final JButton resetManualGulps = new JButton("Reset edits...");
	final JCheckBox persistentEpisodes = new JCheckBox("Persistent episodes (1-6 bins)", true);
	final JCheckBox localEvaporation = new JCheckBox("Local evaporation", false);
	final JCheckBox showGaps = new JCheckBox("Show diagnostics", true);
	final JCheckBox showRois = new JCheckBox("Show on kymograph", true);
	final JCheckBox compensateEvaporation = new JCheckBox("Evaporation-corrected alignment", true);
	final JLabel detectorVersion = new JLabel();
	final JLabel source = new JLabel(" "),
			status = new JLabel("Select a capillary to analyze its accepted topraw levels.");

	IntakeControls() {
		saveScope.setSelectedIndex(1);
		capillaries.setRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
					boolean focus) {
				super.getListCellRendererComponent(list, value, index, selected, focus);
				if (value instanceof Capillary) {
					Capillary cap = (Capillary) value;
					setText(cap.getLast2ofCapillaryName() + (cap.getNFlies() == 0 ? " (no-fly control)" : ""));
				}
				return this;
			}
		});
	}

	/** Task controls stay separate from the shared plot and kymograph. */
	JPanel buildControls() {
		JPanel reviewTask = new JPanel();
		reviewTask.setLayout(new BoxLayout(reviewTask, BoxLayout.Y_AXIS));
		previousButton.setMargin(new Insets(0, 6, 0, 6));
		nextButton.setMargin(new Insets(0, 6, 0, 6));
		int bWidth = 30;
		int bHeight = 21;
		previousButton.setPreferredSize(new Dimension(bWidth, bHeight));
		nextButton.setPreferredSize(new Dimension(bWidth, bHeight));

		reviewTask.add(row(new JLabel("Capillary"), previousButton, capillaries, nextButton,
				new JLabel("Threshold (pixels/min)"), threshold, thresholdMode, automatic, refreshDetection));
		JLabel intakeColorLabel = new JLabel("Intake bar color");
		intakeColorLabel.setLabelFor(intakeColor);
		intakeColor.setToolTipText("Choose the intake bar color in the kymograph preview.");
		reviewTask.add(row(addGulp, review, resetManualGulps, export, intakeColorLabel, intakeColor));
		previewPlacement.setToolTipText("Preview a vertical shift for automatic bars using nearby image edges. "
				+ "Amber dots mark bars without a confident shift. Accept a position in Edit intakes to keep it.");
		positionOffset.setToolTipText("Preview an additional vertical offset on this capillary. Negative moves up; positive moves down.");
		includeManualPositions.setToolTipText("Also move intake bars that already have a manual position.");
		applyPositionOffset.setToolTipText("Keep the previewed positions as edits for this capillary. Save intake to store them.");
		applyToprawOffset.setToolTipText("Move topraw by the total previewed offset and recalculate intake from it. Save intake to store both.");
		reviewTask.add(row(previewPlacement, new JLabel("Offset preview (px)"), positionOffset, includeManualPositions,
				applyPositionOffset, applyToprawOffset));
		reviewTask.add(row(new JLabel("Apply to"), saveScope, save, copyToGulps));
		saveScope.setToolTipText(
				"Whole experiment uses each capillary's current threshold and retained manual edits, including edits made before changing capillary.");
		saveScope.addActionListener(e -> updateSaveScopeLabels());
		updateSaveScopeLabels();
		batchControls.add(detectAll);
		batchControls.add(saveAll);
		batchControls.add(copyAllToGulps);
		batchControls.add(cancelAll);
		JPanel automaticTask = new JPanel(new BorderLayout());
		automaticTask.add(batchControls, BorderLayout.NORTH);
		automaticTask.add(batchStatus, BorderLayout.SOUTH);
		JPanel experimentCards = new JPanel(new CardLayout());
		experimentCards.add(automaticTask, "automatic");
		experimentCards.add(row(saveAllEdited, copyAllEdited), "edited");
		JLabel experimentHelp = new JLabel(
				"<html>Recompute every capillary with its automatic threshold.<br>Saving replaces previous intake results. Ordinary gulps change only with Replace all gulps.</html>");
		experimentHelp.setAlignmentX(Component.LEFT_ALIGNMENT);
		experimentCards.setAlignmentX(Component.LEFT_ALIGNMENT);
		experimentMode.addActionListener(e -> {
			boolean automaticMode = experimentMode.getSelectedIndex() == 0;
			((CardLayout) experimentCards.getLayout()).show(experimentCards, automaticMode ? "automatic" : "edited");
			experimentHelp.setText(automaticMode
					? "<html>Recompute every capillary with its automatic threshold.<br>Saving replaces previous intake results. Ordinary gulps change only with Replace all gulps.</html>"
					: "<html>Save every capillary using its current threshold and manual overrides.<br>Unvisited capillaries use saved settings or automatic defaults.</html>");
		});
		JPanel experimentTask = new JPanel();
		experimentTask.setLayout(new BoxLayout(experimentTask, BoxLayout.Y_AXIS));
		experimentTask.add(row(new JLabel("Results"), experimentMode, exportExperiment));
		experimentTask.add(experimentCards);
		experimentTask.add(experimentHelp);
		seriesControls.add(row(new JLabel("Range"), seriesRange, runSeriesSave, runSeries, stopSeries),
				BorderLayout.NORTH);
		seriesControls.add(seriesStatus, BorderLayout.CENTER);
		seriesControls.add(new JLabel(
				"<html>Automatic thresholds for every capillary; previous intake results are replaced.<br>Stop finishes any save already started.</html>"),
				BorderLayout.SOUTH);
		tasks.addTab("Review & edit", reviewTask);
		tasks.addTab("Experiment", experimentTask);
		tasks.addTab("Series", seriesControls);
		JPanel settings = new JPanel();
		settings.setLayout(new BoxLayout(settings, BoxLayout.Y_AXIS));
		settings.add(row(new JLabel("Detection"), persistentEpisodes, localEvaporation));
		settings.add(row(new JLabel("Display"), showGaps, showRois, compensateEvaporation));
		settings.setVisible(false);
		JButton settingsButton = new JButton("Detection & display settings");
		settingsButton.addActionListener(e -> {
			settings.setVisible(!settings.isVisible());
			settingsButton.setText(settings.isVisible() ? "Hide settings" : "Detection & display settings");
			tasks.getParent().revalidate();
		});
		JPanel controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
		tasks.setAlignmentX(Component.LEFT_ALIGNMENT);
		settings.setAlignmentX(Component.LEFT_ALIGNMENT);
		source.setAlignmentX(Component.LEFT_ALIGNMENT);
		warningSummary.setAlignmentX(Component.LEFT_ALIGNMENT);
		controls.add(tasks);
		controls.add(row(settingsButton, detectorVersion));
		controls.add(settings);
		warningSummary.setVisible(false);
		controls.add(warningSummary);
		source.setVisible(false);
		controls.add(source);
		return controls;
	}

	private void updateSaveScopeLabels() {
		boolean wholeExperiment = saveScope.getSelectedIndex() == 1;
		save.setText(wholeExperiment ? "Save experiment intake..." : "Save capillary intake");
		copyToGulps.setText(wholeExperiment ? "Replace experiment gulps..." : "Replace capillary gulps...");
		save.setToolTipText(
				wholeExperiment ? "Save intake for every capillary using current thresholds and manual edits."
						: "Save intake for the selected capillary.");
		copyToGulps.setToolTipText(
				wholeExperiment ? "Confirm replacement of ordinary gulps for every capillary, then save."
						: "Confirm replacement of ordinary gulps for the selected capillary, then save.");
	}

	private static JPanel row(Component... components) {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (Component component : components)
			panel.add(component);
		return panel;
	}

	JPanel buildFooter() {
		JPanel footer = new JPanel(new BorderLayout(4, 4));
		footer.add(status, BorderLayout.NORTH);
		JLabel legend = new JLabel("<html>Grey: evaporation + cumulative intake. "
				+ "Amber: steps; teal: drift; purple: anchor/offset warnings.<br>"
				+ "Drag the red line to change threshold; click a time point to inspect it. "
				+ "Save intake stores separate results; Replace gulps installs them in ordinary measures.</html>");
		legend.setVisible(false);
		JButton details = new JButton("Details");
		details.addActionListener(e -> {
			source.setVisible(!source.isVisible());
			legend.setVisible(source.isVisible());
			details.setText(source.isVisible() ? "Hide details" : "Details");
			tasks.getParent().revalidate();
		});
		footer.add(details, BorderLayout.EAST);
		footer.add(legend, BorderLayout.SOUTH);
		return footer;
	}
}
