package plugins.fmp.multicafe.dlg.capillaries;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import icy.gui.frame.IcyFrame;
import icy.gui.frame.progress.ProgressFrame;
import icy.gui.viewer.Viewer;
import icy.gui.viewer.ViewerEvent;
import icy.gui.viewer.ViewerListener;
import icy.sequence.DimensionId;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillaries.tracking.TrackingBoundary;
import plugins.fmp.multitools.experiment.capillaries.tracking.TrackingTimeline;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryMeasuredTipsOverlay;
import plugins.fmp.multitools.service.CapillaryLengthDetector;
import plugins.fmp.multitools.service.CapillaryLengthDetectorOptions;
import plugins.fmp.multitools.service.CapillaryLengthResult;
import plugins.fmp.multitools.service.tracking.ExperimentMovementPrescanner;
import plugins.fmp.multitools.service.tracking.ExperimentMovementPrescanner.TransitionAnalysis;
import plugins.fmp.multitools.service.tracking.ExperimentMovementPrescanner.TransitionProposal;
import plugins.fmp.multitools.service.tracking.RackTrackingService;
import plugins.fmp.multitools.service.tracking.PhysicalCapillaryPhases;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.JComponents.CapillaryLengthMeasureDialog;
import plugins.kernel.roi.roi2d.ROI2DLine;

/*
 * CODEX
 */
public class TrackCapillaries extends JPanel implements ViewerListener {

	private static final long serialVersionUID = 1L;

	private IcyFrame dialogFrame = null;
	private MultiCAFE parent0 = null;

	private JSpinner tStartSpinner;
	private JSpinner tEndSpinner;
	private JSpinner transitionThresholdSpinner;
	private JButton rackTrackingButton = new JButton("Track rack from image 0");
	private JButton stopRackButton = new JButton("Stop rack tracking");
	private volatile boolean rackCancelled;
	private boolean rackRunning;
	private JSpinner rackPhaseSpinner = new JSpinner(new SpinnerNumberModel(2.0, 0.5, 30.0, 0.5));
	private JButton saveButton = new JButton("Save");
	private JButton validateBlueButton = new JButton("Update current phase");
	private JButton addPositionButton = new JButton("Add phase at current frame");
	private JLabel geometryStatus = new JLabel();
	private JButton plotEndpointsButton = new JButton("Plot endpoint trajectories");
	private JButton initializeBlueButton = new JButton("Detect endpoints at image 0...");
	private JButton deletePhaseButton = new JButton("Remove selected phase");
	private JButton analyzeTransitionsButton = new JButton("Analyze transitions (read-only)");
	private JButton stopAnalysisButton = new JButton("Stop analysis");
	private JButton acceptProposalButton = new JButton("Create phase from selected proposal");
	private JButton acceptAllProposalsButton = new JButton("Create phases from all proposals");
	private JButton moveProposalButton = new JButton("Move proposal to current T");
	private JButton deleteProposalButton = new JButton("Remove proposal");
	private JTextArea proposalStatusLabel = new JTextArea("No transition analysis run.", 2, 82);
	private DefaultListModel<TransitionProposal> proposalListModel = new DefaultListModel<TransitionProposal>();
	private JList<TransitionProposal> proposalList = new JList<TransitionProposal>(proposalListModel);
	private DefaultListModel<Long> phaseListModel = new DefaultListModel<Long>();
	private JList<Long> phaseList = new JList<Long>(phaseListModel);
	private boolean refreshingPhases;
	private Viewer trackedViewer;
	private EndpointTrajectoryChart endpointChart;
	private Experiment trackedExperiment;
	private AtomicBoolean transitionAnalysisCancelToken;
	private SwingWorker<TransitionAnalysis, Void> transitionAnalysisWorker;
	private ProgressFrame transitionAnalysisProgress;
	private int transitionAnalysisGeneration;
	private final ItemListener experimentSelectionListener = e -> {
		if (e.getStateChange() == ItemEvent.SELECTED)
			SwingUtilities.invokeLater(() -> bindSelectedExperiment());
	};

	public void initialize(MultiCAFE parent0, Point pt) {
		this.parent0 = parent0;
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSeqCamData() == null)
			return;

		int nFrames = exp.getSeqCamData().getImageLoader().getNTotalFrames();
		if (nFrames <= 0)
			nFrames = 1000;

		tStartSpinner = new JSpinner(new SpinnerNumberModel(0, 0, nFrames - 1, 1));
		tEndSpinner = new JSpinner(new SpinnerNumberModel(Math.min(nFrames - 1, 100), 0, nFrames - 1, 1));
		transitionThresholdSpinner = new JSpinner(new SpinnerNumberModel(2.0, 0.5, 30.0, 0.5));
		initializeBlueButton.setToolTipText("Optional: detect physical endpoints at image 0. If blue data exists, choose whether to keep it or replace it.");
		validateBlueButton.setToolTipText("Apply adjusted blue lines to their existing blue phase starts, leaving earlier and later phases intact. Use Save to persist.");
		addPositionButton.setToolTipText("Store adjusted blue lines as a new position starting at the displayed frame, until the next blue phase. Use Save to persist.");
		plotEndpointsButton.setToolTipText("Plot stored physical capillary endpoint positions over time to inspect blue geometry.");
		saveButton.setToolTipText("Save physical capillary phases, measurements, and phase review status.");
		tStartSpinner.setToolTipText("First frame for transition analysis only; legacy rack tracking always starts at image 0.");
		tEndSpinner.setToolTipText("Last frame for transition analysis only; legacy rack tracking scans the full series.");

		JPanel topPanel = new JPanel();
		topPanel.setLayout(new javax.swing.BoxLayout(topPanel, javax.swing.BoxLayout.Y_AXIS));
		FlowLayout flow = new FlowLayout(FlowLayout.LEFT);
		proposalStatusLabel.setEditable(false);
		proposalStatusLabel.setFocusable(false);
		proposalStatusLabel.setOpaque(false);
		proposalStatusLabel.setLineWrap(true);
		proposalStatusLabel.setWrapStyleWord(true);
		proposalStatusLabel.setFont(new JLabel().getFont());

		JPanel p1 = new JPanel(flow);
		p1.add(new JLabel("Frame range:"));
		p1.add(new JLabel("from"));
		p1.add(tStartSpinner);
		p1.add(new JLabel("to"));
		p1.add(tEndSpinner);

		stopRackButton.setEnabled(false);
		JPanel rackPanel = new JPanel(flow);

		JPanel p6 = new JPanel(flow);
		p6.add(analyzeTransitionsButton);
		stopAnalysisButton.setEnabled(false);
		p6.add(stopAnalysisButton);
		p6.add(new JLabel("minimum movement"));
		p6.add(transitionThresholdSpinner);
		p6.add(new JLabel("px"));

		JPanel p7 = new JPanel(flow);
		p7.add(acceptProposalButton);
		p7.add(acceptAllProposalsButton);
		p7.add(moveProposalButton);
		p7.add(deleteProposalButton);

		// Keep analysis feedback on a dedicated row. On ordinary-width dialogs it
		// was previously laid out beyond the right edge after the four action buttons.
		JPanel p8 = new JPanel(flow);
		p8.add(proposalStatusLabel);

		phaseList.setVisibleRowCount(4);
		phaseList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
		phaseList.setToolTipText("Actual blue keyframe starts. Hover for capillary details; select a row to display that image.");
		phaseList.setCellRenderer((list, value, index, selected, focus) -> {
			JLabel label = new JLabel(phaseDescription(value));
			label.setToolTipText(phaseDetails(value));
			if (selected) {
				label.setOpaque(true);
				label.setBackground(list.getSelectionBackground());
				label.setForeground(list.getSelectionForeground());
			}
			return label;
		});
		proposalList.setVisibleRowCount(5);
		proposalList.setCellRenderer((list, value, index, selected, focus) -> {
			JLabel label = new JLabel(value.summary());
			if (selected) {
				label.setOpaque(true);
				label.setBackground(list.getSelectionBackground());
				label.setForeground(list.getSelectionForeground());
			}
			return label;
		});

		dialogFrame = new IcyFrame("Track physical capillaries along time", true, true);
		dialogFrame.add(topPanel, BorderLayout.NORTH);
		JScrollPane proposalsScroll = new JScrollPane(proposalList);
		proposalsScroll.setBorder(BorderFactory.createTitledBorder("Candidate phase starts (not yet applied)"));
		JScrollPane phasesScroll = new JScrollPane(phaseList);
		phasesScroll.setBorder(BorderFactory.createTitledBorder("Physical capillary phases — select to display the starting image"));
		phasesScroll.setPreferredSize(new java.awt.Dimension(740, 110));
		JPanel prepare = new JPanel(flow);
		prepare.setBorder(BorderFactory.createTitledBorder("1. Starting geometry"));
		prepare.add(initializeBlueButton);
		prepare.add(new JLabel("Optional if blue geometry already exists."));
		topPanel.add(prepare);
		JPanel manual = new JPanel();
		manual.setLayout(new javax.swing.BoxLayout(manual, javax.swing.BoxLayout.Y_AXIS));
		manual.setBorder(BorderFactory.createTitledBorder("2. Physical capillary phases / manual positions"));
		manual.add(new JLabel("Choose an image in the viewer, adjust the blue lines, then add a position or update a phase."));
		manual.add(geometryStatus);
		manual.add(phasesScroll);
		JPanel validate = new JPanel(flow);
		validate.add(addPositionButton);
		validate.add(validateBlueButton);
		validate.add(deletePhaseButton);
		deletePhaseButton.setToolTipText("Remove blue keyframes at the selected start for the capillaries listed there. Previous positions continue until their next phase. Frame 0 is protected.");
		manual.add(validate);
		manual.add(new JLabel("Removing a later phase extends previous positions. The image-zero reference is protected."));
		topPanel.add(manual);
		JPanel track = new JPanel(flow);
		JButton structuralReference = new JButton("Structural reference: polygon test");
		structuralReference.setToolTipText("Preview polygon edges at image 0 and load or save reference bands; this does not track capillaries.");
		structuralReference.addActionListener(e -> StructuralReferenceDialog.open((Experiment) parent0.expListComboLazy.getSelectedItem()));
		track.add(structuralReference);
		track.add(new JLabel("Image-0 edge preview only; no tracking yet."));
		JPanel automatic = new JPanel(new GridLayout(0, 1));
		automatic.setBorder(BorderFactory.createTitledBorder("3. Automatic tracking"));
		rackPanel.add(rackTrackingButton);
		rackPanel.add(stopRackButton);
		rackPanel.add(new JLabel("New phase after movement of"));
		rackPanel.add(rackPhaseSpinner);
		rackPanel.add(new JLabel("px"));
		automatic.add(rackPanel);
		automatic.add(new JLabel("Tracks shared rack translation over the full series; replaces later blue phases and keeps image 0."));
		automatic.add(new JLabel("Inspect results before saving. Uncertain images retain the last accepted position."));
		topPanel.add(automatic);
		JPanel inspect = new JPanel(flow);
		inspect.setBorder(BorderFactory.createTitledBorder("4. Inspect"));
		inspect.add(plotEndpointsButton);
		topPanel.add(inspect);
		JPanel warning = new JPanel(flow);
		warning.setBorder(BorderFactory.createTitledBorder("5. Save"));
		warning.add(saveButton);
		warning.add(new JLabel("Manual and automatic positions stay in memory until saved."));
		topPanel.add(warning);

		JPanel advanced = new JPanel();
		advanced.setLayout(new javax.swing.BoxLayout(advanced, javax.swing.BoxLayout.Y_AXIS));
		JPanel backwardHelp = new JPanel(flow);
		backwardHelp.add(new JLabel("Analyze movement to propose phase starts. Accepting copies preceding blue positions; adjust them in the viewer."));
		advanced.add(backwardHelp);
		advanced.add(track);
		advanced.add(p1);
		advanced.add(p6);
		advanced.add(p7);
		proposalStatusLabel.setColumns(55);
		advanced.add(p8);
		proposalsScroll.setPreferredSize(new java.awt.Dimension(680, 140));
		advanced.add(proposalsScroll);
		JScrollPane advancedScroll = new JScrollPane(advanced);
		advancedScroll.setPreferredSize(new java.awt.Dimension(740, 300));
		advancedScroll.setVisible(false);
		javax.swing.JToggleButton advancedToggle = new javax.swing.JToggleButton("Show phase proposals / reference tools");
		advancedToggle.setToolTipText("Analyze movement and turn proposed transitions into editable physical capillary phases, or preview structural references.");
		JPanel toggleRow = new JPanel(flow);
		toggleRow.add(advancedToggle);
		topPanel.add(toggleRow);
		dialogFrame.add(advancedScroll, BorderLayout.CENTER);
		advancedToggle.addActionListener(e -> {
			advancedScroll.setVisible(advancedToggle.isSelected());
			advancedToggle.setText(advancedToggle.isSelected() ? "Hide phase proposals / reference tools" : "Show phase proposals / reference tools");
			dialogFrame.pack();
		});
		rackTrackingButton.setToolTipText("Estimate shared rack translation from image 0 over all images and replace later blue phases. Use Save to persist.");
		rackPhaseSpinner.setToolTipText("Minimum displacement from the last stored rack position required to create a new blue phase, in pixels.");
		dialogFrame.setLocation(pt);
		dialogFrame.pack();
		dialogFrame.addToDesktopPane();
		dialogFrame.setVisible(true);
		parent0.expListComboLazy.removeItemListener(experimentSelectionListener);
		parent0.expListComboLazy.addItemListener(experimentSelectionListener);
		bindSelectedExperiment();

		rackTrackingButton.addActionListener(e -> runRackTracking());
		stopRackButton.addActionListener(e -> rackCancelled = true);
		saveButton.addActionListener(e -> save());
		validateBlueButton.addActionListener(e -> validateBlueAtCurrentT());
		addPositionButton.addActionListener(e -> editBluePosition(true));
		plotEndpointsButton.addActionListener(e -> plotEndpointTrajectories());
		initializeBlueButton.addActionListener(e -> initializeBlueAtT0());
		deletePhaseButton.addActionListener(e -> deleteSelectedPhase());
		analyzeTransitionsButton.addActionListener(e -> analyzeTransitions());
		stopAnalysisButton.addActionListener(e -> stopTransitionAnalysis());
		acceptProposalButton.addActionListener(e -> acceptSelectedProposal());
		acceptAllProposalsButton.addActionListener(e -> acceptAllProposals());
		moveProposalButton.addActionListener(e -> moveSelectedProposalToCurrentT());
		deleteProposalButton.addActionListener(e -> deleteSelectedProposal());
		proposalList.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting() && proposalList.getSelectedValue() != null)
				setViewerPositionT(proposalList.getSelectedValue().frame);
		});
		phaseList.addListSelectionListener(e -> {
			if (!refreshingPhases && !e.getValueIsAdjusting() && phaseList.getSelectedValue() != null) {
				deletePhaseButton.setEnabled(!rackRunning && phaseList.getSelectedValue() > 0);
				setViewerPositionT(phaseList.getSelectedValue().intValue());
			}
		});
		refreshPhases();
	}

	private TrackingTimeline timeline() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		return exp != null && exp.getCapillaries() != null ? exp.getCapillaries().getTrackingTimeline() : null;
	}

	private String phaseDescription(long frame) {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		int count = 0;
		int total = exp == null ? 0 : exp.getCapillaries().getList().size();
		if (exp != null)
			for (Capillary cap : exp.getCapillaries().getList())
				if (cap.getPhaseGeometry().getBlueStartingAt(frame) != null
						|| (frame == 0 && cap.getProperties().hasMeasuredEndpoints()))
					count++;
		TrackingBoundary metadata = timeline() == null || frame == 0 ? null : timeline().get((int) frame);
		String review = metadata != null && metadata.getStatus() == TrackingBoundary.Status.UNRESOLVED
				? " | adjust copied positions" : "";
		return "T=" + frame + (frame == 0 ? " — Reference" : "") + " — "
				+ (count == total ? "All " + total : count + " of " + total) + " capillaries" + review;
	}

	private String phaseDetails(long frame) {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		List<String> included = new ArrayList<>();
		List<String> missing = new ArrayList<>();
		if (exp != null)
			for (Capillary cap : exp.getCapillaries().getList()) {
				String name = cap.getRoiName() == null ? cap.getKymographName() : cap.getRoiName();
				boolean startsHere = cap.getPhaseGeometry().getBlueStartingAt(frame) != null
						|| (frame == 0 && cap.getProperties().hasMeasuredEndpoints());
				(startsHere ? included : missing).add(name);
			}
		return "Positions start here: " + String.join(", ", included)
				+ (missing.isEmpty() ? "" : " | No position starts here: " + String.join(", ", missing));
	}

	private void refreshPhases() {
		Long selected = phaseList.getSelectedValue();
		refreshingPhases = true;
		try {
			phaseListModel.clear();
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null)
				for (Long frame : PhysicalCapillaryPhases.starts(exp.getCapillaries()))
					phaseListModel.addElement(frame);
			if (selected != null) phaseList.setSelectedValue(selected, true);
		} finally {
			refreshingPhases = false;
		}
		deletePhaseButton.setEnabled(!rackRunning && phaseList.getSelectedValue() != null && phaseList.getSelectedValue() > 0);
	}

	private void deleteSelectedPhase() {
		Long frame = phaseList.getSelectedValue();
		if (rackRunning || frame == null || frame <= 0) return;
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null) return;
		int choice = JOptionPane.showConfirmDialog(this,
				"Remove blue positions starting at T=" + frame + " for the listed capillaries?\n"
				+ "Each preceding position will continue until its next phase. Green sampling ROIs are unchanged.",
				"Remove physical capillary phase", JOptionPane.OK_CANCEL_OPTION);
		if (choice != JOptionPane.OK_OPTION) return;
		int removed = PhysicalCapillaryPhases.remove(exp.getCapillaries(), frame);
		if (removed == 0)
			JOptionPane.showMessageDialog(this, "This phase is the first stored reference and cannot be removed.");
		showBlueOnly();
		refreshPhases();
		if (endpointChart != null) endpointChart.refresh();
	}

	private void selectPhase(int frame) {
		phaseList.setSelectedValue((long) frame, true);
	}
	private void setViewerPositionT(int frame) {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null)
			return;
		icy.gui.viewer.Viewer viewer = exp.getSeqCamData().getSequence().getFirstViewer();
		if (viewer != null) {
			viewer.setPositionT(frame);
			exp.updateROIsAt(frame);
			showBlueOnly();
		}
	}

	private int getViewerPositionT() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSeqCamData() == null)
			return 0;
		icy.gui.viewer.Viewer v = exp.getSeqCamData().getSequence() != null
				? exp.getSeqCamData().getSequence().getFirstViewer()
				: null;
		return v != null ? v.getPositionT() : 0;
	}






	private void save() {
		if (rackRunning) {
			JOptionPane.showMessageDialog(this, "Wait for tracking to finish before saving.");
			return;
		}
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null)
			exp.save_capillaries_description_and_measures();
	}

	private void validateBlueAtCurrentT() {
		editBluePosition(false);
	}

	private void editBluePosition(boolean addAtCurrentFrame) {
		if (rackRunning)
			return;
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSeqCamData() == null)
			return;
		int t = getViewerPositionT();
		if (addAtCurrentFrame && exp.getCapillaries().getList().stream()
				.anyMatch(cap -> cap.getPhaseGeometry().getBlueStartingAt(t) != null)) {
			int choice = JOptionPane.showConfirmDialog(this,
					"A blue position already starts at frame " + t + ". Replace positions at this frame with the adjusted lines?",
					"Existing blue position", JOptionPane.OK_CANCEL_OPTION);
			if (choice != JOptionPane.OK_OPTION)
				return;
		}
		int updated = CapillaryMeasuredTipsOverlay.editBluePositionFromSequence(exp.getCapillaries(),
				exp.getSeqCamData(), t, addAtCurrentFrame);
		if (updated == 0) {
			JOptionPane.showMessageDialog(this, "No valid editable blue lines with an applicable phase were found. Detect image-zero endpoints first.",
					"Physical capillaries", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		CapillaryMeasuredTipsOverlay.transferTipsToSequence(exp.getCapillaries(), exp.getSeqCamData(), t);
		if (timeline() != null)
			for (Long start : PhysicalCapillaryPhases.starts(exp.getCapillaries())) {
				TrackingBoundary metadata = start > 0 ? timeline().get(start.intValue()) : null;
				if (metadata != null && metadata.getStatus() == TrackingBoundary.Status.UNRESOLVED
						&& (addAtCurrentFrame ? start == t : exp.getCapillaries().getList().stream()
								.anyMatch(cap -> start.equals(cap.getPhaseGeometry().getBluePhaseStartAt(t)))))
					timeline().put(metadata.withStatus(TrackingBoundary.Status.CONFIRMED));
			}
		refreshGeometryStatus();
		refreshPhases();
		if (addAtCurrentFrame) selectPhase(t);
		if (endpointChart != null) endpointChart.refresh();
	}

	private void refreshGeometryStatus() {
		refreshPhases();
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		int t = getViewerPositionT();
		java.util.Set<Long> starts = new java.util.TreeSet<>();
		int initialized = 0;
		if (exp != null && exp.getCapillaries() != null)
			for (Capillary cap : exp.getCapillaries().getList()) {
				Long start = cap.getPhaseGeometry().getBluePhaseStartAt(t);
				if (start != null) {
					starts.add(start);
					initialized++;
				}
			}
		geometryStatus.setText("Current frame: " + t + " | " + initialized + " capillaries with blue positions | Active blue phase starts: "
				+ (starts.isEmpty() ? "none" : starts.toString()));
	}

	private void initializeBlueAtT0() {
		if (rackRunning)
			return;
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSeqCamData() == null)
			return;
		boolean replaceExisting = exp.getCapillaries().getList().stream().anyMatch(cap ->
				!cap.getPhaseGeometry().getBlueKeyframes().isEmpty() || cap.getProperties().hasMeasuredEndpoints());
		if (replaceExisting) {
			Object[] choices = { "Keep existing data", "Erase and reinitialize" };
			int choice = JOptionPane.showOptionDialog(this,
					"Physical capillary data already exists.\n"
					+ "Keep it, or erase all blue positions (including later tracked phases) and detect new endpoints at image 0?\n"
					+ "Erasure occurs only after you confirm the new measurements. Green ROIs and tracking boundaries are kept.",
					"Existing physical capillary data", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
					null, choices, choices[0]);
			if (choice != 1)
				return;
		}
		CapillaryLengthDetectorOptions options = new CapillaryLengthDetectorOptions();
		options.frameIndex = 0;
		options.nFramesAveraged = 1;
		CapillaryLengthResult result = new CapillaryLengthDetector().measure(exp, options);
		if (result.hasError()) {
			JOptionPane.showMessageDialog(this, result.getErrorMessage(), "Initialize physical capillaries",
					JOptionPane.WARNING_MESSAGE);
			return;
		}
		showInitialGeometryPreview(exp, result);
		setViewerPositionT(0);
		int reviewCount = 0;
		int missingCount = 0;
		for (CapillaryLengthResult.Measure measure : result.getMeasures()) {
			if (!measure.hasDetectedEndpoints())
				missingCount++;
			else if (measure.needsReview())
				reviewCount++;
		}
		boolean accepted = CapillaryLengthMeasureDialog.showAndConfirm(this, result,
				"T=0: " + reviewCount + " orange (review), " + missingCount + " missing");
		if (!accepted) {
			CapillaryMeasuredTipsOverlay.transferTipsToSequence(exp.getCapillaries(), exp.getSeqCamData(), 0);
			return;
		}
		boolean hasReplacement = result.getMeasures().stream().anyMatch(measure -> measure.isSelected()
				&& measure.getCapillary() != null && measure.hasDetectedEndpoints() && measure.getRoundedPixels() > 0);
		if (!hasReplacement) {
			CapillaryMeasuredTipsOverlay.transferTipsToSequence(exp.getCapillaries(), exp.getSeqCamData(), 0);
			JOptionPane.showMessageDialog(this, "No detected physical endpoints were selected. Existing blue data was kept.",
					"Initialize physical capillaries", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		if (replaceExisting)
			for (Capillary cap : exp.getCapillaries().getList()) {
				cap.getPhaseGeometry().clear();
				cap.getProperties().setPixelsAutoMeasured(false);
			}
		int updated = CapillaryLengthDetector.apply(result, 0);
		CapillaryMeasuredTipsOverlay.transferTipsToSequence(exp.getCapillaries(), exp.getSeqCamData(), 0);
		refreshGeometryStatus();
		refreshPhases();
		if (endpointChart != null) endpointChart.refresh();
		proposalStatusLabel.setText(updated + " provisional blue ROI(s) accepted at T=0.");
	}

	private void showInitialGeometryPreview(Experiment exp, CapillaryLengthResult result) {
		CapillaryMeasuredTipsOverlay.removeTipsFromSequence(exp.getSeqCamData());
		int fallback = 0;
		for (CapillaryLengthResult.Measure measure : result.getMeasures()) {
			if (!measure.hasDetectedEndpoints() || measure.getCapillary() == null)
				continue;
			ROI2DLine roi = new ROI2DLine(measure.getDetectedStart(), measure.getDetectedEnd());
			roi.setName(CapillaryMeasuredTipsOverlay.ROI_PREFIX
					+ CapillaryMeasuredTipsOverlay.tipSuffix(measure.getCapillary(), fallback++));
			roi.setColor(measure.needsReview() ? Color.ORANGE : CapillaryMeasuredTipsOverlay.ROI_COLOR);
			roi.setStroke(3);
			roi.setReadOnly(false);
			roi.setT(-1);
			exp.getSeqCamData().getSequence().addROI(roi);
		}
	}

	private void runRackTracking() {
		final Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSeqCamData() == null || rackRunning)
			return;
		final double threshold = ((Number) rackPhaseSpinner.getValue()).doubleValue();
		if (exp.getCapillaries().getList().stream()
				.anyMatch(cap -> cap.getPhaseGeometry().getBlueKeyframes().keySet().stream().anyMatch(t -> t > 0))) {
			int choice = JOptionPane.showConfirmDialog(this,
					"Rack tracking replaces all later blue positions with shared rack translations. Continue?",
					"Replace later blue positions", JOptionPane.OK_CANCEL_OPTION);
			if (choice != JOptionPane.OK_OPTION)
				return;
		}
		rackCancelled = false;
		rackRunning = true;
		deletePhaseButton.setEnabled(false);
		acceptProposalButton.setEnabled(false);
		acceptAllProposalsButton.setEnabled(false);
		rackTrackingButton.setEnabled(false);
		stopRackButton.setEnabled(true);
		validateBlueButton.setEnabled(false);
		addPositionButton.setEnabled(false);
		initializeBlueButton.setEnabled(false);
		final ProgressFrame pf = new ProgressFrame("Tracking rack from image 0");
		new SwingWorker<RackTrackingService.Scan, Void>() {
			@Override
			protected RackTrackingService.Scan doInBackground() throws Exception {
				return new RackTrackingService().scan(exp, threshold, () -> rackCancelled,
						frame -> SwingUtilities.invokeLater(() -> pf.setMessage("Frame " + frame)));
			}

			@Override
			protected void done() {
				try {
					RackTrackingService.Scan scan = get();
					if (scan.cancelled || rackCancelled)
						return;
					scan.apply();
					refreshPhases();
					if (endpointChart != null) endpointChart.refresh();
					showBlueOnly();
					JOptionPane.showMessageDialog(TrackCapillaries.this, scan.frames + " frames, " + scan.phases
							+ " geometry phases, " + scan.uncertain
							+ " uncertain frames (last accepted position retained).\n"
							+ "Blue phases are ready to inspect with the viewer and endpoint plot. Use Save to persist.\n"
							+ "Report: " + scan.reportPath, "Rack tracking complete", JOptionPane.INFORMATION_MESSAGE);
				} catch (Exception ex) {
					JOptionPane.showMessageDialog(TrackCapillaries.this, "Rack tracking failed: " + ex.getMessage());
				} finally {
					pf.close();
					rackRunning = false;
					rackTrackingButton.setEnabled(true);
					stopRackButton.setEnabled(false);
					validateBlueButton.setEnabled(true);
					addPositionButton.setEnabled(true);
					initializeBlueButton.setEnabled(true);
					acceptProposalButton.setEnabled(true);
					acceptAllProposalsButton.setEnabled(true);
					refreshPhases();
				}
			}
		}.execute();
	}

	private void analyzeTransitions() {
		if (transitionAnalysisWorker != null && !transitionAnalysisWorker.isDone())
			return;
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSeqCamData() == null)
			return;
		final int from = (Integer) tStartSpinner.getValue();
		final int to = (Integer) tEndSpinner.getValue();
		if (to <= from) {
			JOptionPane.showMessageDialog(this, "The analysis range must contain at least two frames.",
					"Transition analysis", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		final double minimum = ((Number) transitionThresholdSpinner.getValue()).doubleValue();
		final AtomicBoolean cancelToken = new AtomicBoolean(false);
		transitionAnalysisCancelToken = cancelToken;
		final int analysisGeneration = ++transitionAnalysisGeneration;
		analyzeTransitionsButton.setEnabled(false);
		stopAnalysisButton.setEnabled(true);
		proposalStatusLabel.setText("Analyzing T=" + from + " to " + to + "...");
		closeTransitionProgress();
		final ProgressFrame progress = new ProgressFrame("Analyzing image transitions (read-only)");
		progress.setLength(to - from);
		transitionAnalysisProgress = progress;
		transitionAnalysisWorker = new SwingWorker<TransitionAnalysis, Void>() {
			@Override
			protected TransitionAnalysis doInBackground() {
				return new ExperimentMovementPrescanner().analyzeTransitions(exp, from, to, minimum, cancelToken::get,
						(current, total) -> SwingUtilities.invokeLater(() -> {
							if (analysisGeneration != transitionAnalysisGeneration)
								return;
							progress.setMessage("Analyzing frame " + (from + current) + " / " + to);
							progress.setLength(total);
							progress.setPosition((double) current / Math.max(1, total));
							proposalStatusLabel.setText("Analyzing frame " + (from + current) + " / " + to + "...");
						}));
			}

			@Override
			protected void done() {
				try {
					TransitionAnalysis result = get();
					if (analysisGeneration != transitionAnalysisGeneration)
						return;
					proposalListModel.clear();
					for (TransitionProposal proposal : result.proposals)
						proposalListModel.addElement(proposal);
					if (!result.succeeded())
						proposalStatusLabel.setText("Analysis failed: " + result.error);
					else {
						String status = result.proposals.size() + " proposal(s), " + result.comparedFrames
								+ " pairs, threshold " + String.format("%.1f px", result.thresholdUsed)
								+ (result.proposals.isEmpty() && result.baselinePeakFrame >= 0
										? String.format(" — baseline peak %.2f px at T=%d, tail %d/%d, inliers %.0f%%",
												result.baselinePeakScore, result.baselinePeakFrame,
												result.baselineTailSupported, result.baselineTailFrames,
												result.baselinePeakInlierFraction * 100)
										: "")
								+ (result.proposals.isEmpty() && result.strongest != null
										? " — strongest below threshold: " + result.strongest.summary()
										: "")
								+ (result.cancelled ? " (stopped)" : "");
						proposalStatusLabel.setText(status);
						proposalStatusLabel.setToolTipText(status);
					}
				} catch (Exception ex) {
					if (analysisGeneration == transitionAnalysisGeneration) {
						proposalStatusLabel.setText("Analysis failed: " + ex.getMessage());
						Logger.warn("Transition analysis failed: " + ex.getMessage());
					}
				} finally {
					progress.close();
					if (transitionAnalysisProgress == progress)
						transitionAnalysisProgress = null;
					if (analysisGeneration == transitionAnalysisGeneration) {
						analyzeTransitionsButton.setEnabled(true);
						stopAnalysisButton.setEnabled(false);
						transitionAnalysisCancelToken = null;
						transitionAnalysisWorker = null;
					}
				}
			}
		};
		transitionAnalysisWorker.execute();
	}

	private void stopTransitionAnalysis() {
		if (transitionAnalysisCancelToken != null)
			transitionAnalysisCancelToken.set(true);
		stopAnalysisButton.setEnabled(false);
		proposalStatusLabel.setText("Stopping transition analysis...");
		closeTransitionProgress();
	}

	private void closeTransitionProgress() {
		if (transitionAnalysisProgress != null) {
			transitionAnalysisProgress.close();
			transitionAnalysisProgress = null;
		}
	}

	private void cancelTransitionAnalysis() {
		transitionAnalysisGeneration++;
		if (transitionAnalysisCancelToken != null)
			transitionAnalysisCancelToken.set(true);
		transitionAnalysisCancelToken = null;
		if (transitionAnalysisWorker != null && !transitionAnalysisWorker.isDone())
			transitionAnalysisWorker.cancel(true);
		transitionAnalysisWorker = null;
		closeTransitionProgress();
		stopAnalysisButton.setEnabled(false);
		analyzeTransitionsButton.setEnabled(true);
	}

	private void acceptSelectedProposal() {
		TransitionProposal proposal = proposalList.getSelectedValue();
		if (proposal == null)
			return;
		if (acceptProposal(proposal)) proposalListModel.removeElement(proposal);
	}

	private void acceptAllProposals() {
		List<TransitionProposal> proposals = new ArrayList<TransitionProposal>();
		for (int i = 0; i < proposalListModel.size(); i++)
			proposals.add(proposalListModel.get(i));
		for (TransitionProposal proposal : proposals)
			if (acceptProposal(proposal)) proposalListModel.removeElement(proposal);
	}

	private boolean acceptProposal(TransitionProposal proposal) {
		TrackingTimeline timeline = timeline();
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (rackRunning || timeline == null || proposal == null || proposal.endFrame < 1 || exp == null)
			return false;
		int added = PhysicalCapillaryPhases.addFromPrevious(exp.getCapillaries(), proposal.endFrame);
		if (added == 0 && !PhysicalCapillaryPhases.starts(exp.getCapillaries()).contains((long) proposal.endFrame)) {
			proposalStatusLabel.setText("No preceding physical geometry available. Detect image-zero endpoints before accepting proposals.");
			return false;
		}
		if (added > 0)
			timeline.put(new TrackingBoundary(proposal.endFrame, TrackingBoundary.Origin.AUTOMATIC,
					TrackingBoundary.Status.UNRESOLVED, proposal.summary(), proposal.score));
		refreshPhases();
		selectPhase(proposal.endFrame);
		proposalStatusLabel.setText(added > 0 ? "Phase created: preceding blue positions copied. Adjust lines, then Update current phase and Save."
				: "Phase already exists; existing positions were kept.");
		if (endpointChart != null) endpointChart.refresh();
		return true;
	}

	private void moveSelectedProposalToCurrentT() {
		int index = proposalList.getSelectedIndex();
		int frame = getViewerPositionT();
		if (index < 0 || frame < 1)
			return;
		proposalListModel.set(index, proposalListModel.get(index).atFrame(frame));
		proposalList.setSelectedIndex(index);
	}

	private void deleteSelectedProposal() {
		int index = proposalList.getSelectedIndex();
		if (index >= 0)
			proposalListModel.remove(index);
	}

	private void plotEndpointTrajectories() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSeqCamData() == null)
			return;
		if (endpointChart == null)
			endpointChart = new EndpointTrajectoryChart();
		Point location = dialogFrame == null ? new Point(100, 100) : dialogFrame.getLocation();
		endpointChart.open(exp, location);
	}

	void close() {
		rackCancelled = true;
		cancelTransitionAnalysis();
		if (parent0 != null)
			parent0.expListComboLazy.removeItemListener(experimentSelectionListener);
		if (trackedViewer != null) {
			trackedViewer.removeListener(this);
			trackedViewer = null;
		}
		showGreenAgain(trackedExperiment);
		trackedExperiment = null;
		if (endpointChart != null)
			endpointChart.close();
		if (dialogFrame != null)
			dialogFrame.close();
	}

	private void showBlueOnly() {
		refreshGeometryStatus();
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSeqCamData() == null)
			return;
		CapillaryMeasuredTipsOverlay.transferTipsToSequence(exp.getCapillaries(), exp.getSeqCamData(),
				getViewerPositionT());
		Viewer viewer = exp.getSeqCamData().getSequence() == null ? null
				: exp.getSeqCamData().getSequence().getFirstViewer();
		parent0.paneExperiment.tabOptions.displayROIsCategory(viewer, "line", false);
		parent0.paneExperiment.tabOptions.displayROIsCategory(viewer, CapillaryMeasuredTipsOverlay.ROI_PREFIX, true);
	}

	private void showGreenAgain(Experiment exp) {
		if (exp != null && exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null)
			parent0.paneExperiment.tabOptions.displayROIsCategory(exp.getSeqCamData().getSequence().getFirstViewer(),
					"line", parent0.viewOptions.isViewCapillaries());
	}

	private void bindSelectedExperiment() {
		Experiment selected = parent0 == null ? null : (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (endpointChart != null)
			endpointChart.updateExperiment(selected);
		if (selected == trackedExperiment) {
			refreshPhases();
			showBlueOnly();
			return;
		}
		cancelTransitionAnalysis();
		showGreenAgain(trackedExperiment);
		if (trackedViewer != null)
			trackedViewer.removeListener(this);
		trackedExperiment = selected;
		if (rackRunning)
			rackCancelled = true;
		proposalListModel.clear();
		proposalStatusLabel.setText("No transition analysis run for this experiment.");
		trackedViewer = null;
		if (selected == null || selected.getSeqCamData() == null || selected.getSeqCamData().getSequence() == null) {
			phaseListModel.clear();
			return;
		}
		int nFrames = selected.getSeqCamData().getImageLoader().getNTotalFrames();
		if (nFrames <= 0)
			nFrames = 1000;
		int maxT = nFrames - 1;
		trackedViewer = selected.getSeqCamData().getSequence().getFirstViewer();
		int currentT = trackedViewer == null ? 0 : trackedViewer.getPositionT();
		tStartSpinner.setModel(new SpinnerNumberModel(Math.min(currentT, maxT), 0, maxT, 1));
		tEndSpinner.setModel(new SpinnerNumberModel(maxT, 0, maxT, 1));
		if (trackedViewer != null) {
			trackedViewer.removeListener(this);
			trackedViewer.addListener(this);
		}
		refreshPhases();
		showBlueOnly();
	}

	@Override
	public void viewerChanged(ViewerEvent event) {
		if (event.getType() == ViewerEvent.ViewerEventType.POSITION_CHANGED && event.getDim() == DimensionId.T)
			SwingUtilities.invokeLater(() -> showBlueOnly());
	}

	@Override
	public void viewerClosed(Viewer viewer) {
		if (viewer == trackedViewer)
			trackedViewer = null;
	}
}
