package plugins.fmp.multicafe.dlg.levels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import icy.canvas.IcyCanvas;
import icy.canvas.IcyCanvas2D;
import icy.gui.viewer.Viewer;
import icy.painter.Overlay;
import icy.sequence.Sequence;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.LazyExperiment;
import plugins.fmp.multitools.experiment.capillaries.CapillariesPersistence;
import plugins.fmp.multitools.experiment.capillaries.CapillariesSequenceMapper;
import plugins.fmp.multitools.experiment.capillary.BottomBaselineEstimator;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.ReachableEnd;
import plugins.fmp.multitools.tools.Logger;
import plugins.kernel.roi.roi2d.ROI2DLine;

final class BottomDriftReviewPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	private final MultiCAFE parent0;
	private final Component owner;
	private JDialog dialog;
	private final JCheckBox scanRemaining = new JCheckBox("Current experiment through last");
	private final JButton search = new JButton("Search drift");
	private final JSpinner minimumRisePercent = new JSpinner(new SpinnerNumberModel(100.0 * 10.0 / 430.0, 0.01, 100.0, 0.1));
	private final List<DriftHit> allHits = new ArrayList<>();
	private final JComboBox<String> reviewFilter = new JComboBox<>(new String[] { "Pending", "Validated drift", "Dismissed", "All", "Tagged" });
	private final JButton showTagged = new JButton("Show tagged");
	private final JButton resetReview = new JButton("Reset review");
	private final DefaultListModel<DriftHit> hits = new DefaultListModel<>();
	private final JList<DriftHit> candidates = new JList<>(hits);
	private final JLabel status = new JLabel("Search for bottom meniscus drift.");
	private final JButton previous = new JButton("Previous");
	private final JButton next = new JButton("Next");
	private final JButton showKymograph = new JButton("Show kymograph");
	private final JSpinner firstInvalidFrame = new JSpinner(new SpinnerNumberModel(0, 0, 0, 1));
	private final JButton validate = new JButton("Validate cutoff");
	private final JButton dismiss = new JButton("Not a drift");
	private SwingWorker<List<DriftHit>, Integer> scan;
	private boolean detectionRunning;
	private boolean updatingList;
	private DriftHit displayedHit;
	private int displayedProposal;
	private Sequence markerSequence;
	private ROI2DLine cutoffMarker;
	private Overlay cutoffRiseOverlay;
	private List<Capillary> rankedCaps = new ArrayList<>();
	private int rankedIndex = -1;
	private final JButton prevNoiseButton = new JButton("< noisy");
	private final JButton nextNoiseButton = new JButton("noisy >");
	private final JLabel qcLabel = new JLabel("QC: no bottom measures");
	private final JLabel riseLabel = new JLabel("Bottom rise: select a capillary");

	BottomDriftReviewPanel(MultiCAFE parent, Component owner) {
		this.parent0 = parent;
		this.owner = owner;
		setLayout(new BorderLayout());
		JPanel searchControls = new JPanel(new GridLayout(0, 1));
		searchControls.add(row(search, showTagged, scanRemaining, new JLabel("Show"), reviewFilter));
		JLabel minimumRiseLabel = new JLabel("Minimum rise (% capillary length)");
		minimumRiseLabel.setLabelFor(minimumRisePercent);
		minimumRisePercent.setEditor(new JSpinner.NumberEditor(minimumRisePercent, "0.###"));
		searchControls.add(row(minimumRiseLabel, minimumRisePercent));
		add(searchControls, BorderLayout.NORTH);

		candidates.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		JScrollPane list = new JScrollPane(candidates);
		list.setPreferredSize(new Dimension(660, 180));
		add(list, BorderLayout.CENTER);

		JPanel actions = new JPanel(new GridLayout(0, 1));
		actions.add(row(status));
		actions.add(row(previous, next, showKymograph));
		JLabel cutoffLabel = new JLabel("Proposed cutoff frame (0-based)");
		cutoffLabel.setLabelFor(firstInvalidFrame);
		actions.add(row(cutoffLabel, firstInvalidFrame, validate, dismiss, resetReview));
		actions.add(row(prevNoiseButton, nextNoiseButton, qcLabel));
		actions.add(row(riseLabel));
		add(actions, BorderLayout.SOUTH);

		search.addActionListener(e -> search());
		showTagged.addActionListener(e -> {
			reviewFilter.setSelectedItem("Tagged");
			search();
		});
		showTagged.setToolTipText("Scan the selected experiment range and list all saved cutoffs and dismissals.");
		reviewFilter.addActionListener(e -> filterHits(null));
		resetReview.addActionListener(e -> resetReview());
		resetReview.setToolTipText("Clear the saved cutoff and dismissal. The whole recording becomes valid again.");
		reviewFilter.setToolTipText("Show pending candidates, saved cutoffs, dismissed capillaries, or all review entries.");
		candidates.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting() && !updatingList)
				openCandidate();
		});
		previous.addActionListener(e -> selectCandidate(candidates.getSelectedIndex() - 1));
		next.addActionListener(e -> selectCandidate(candidates.getSelectedIndex() + 1));
		showKymograph.addActionListener(e -> openCandidate());
		firstInvalidFrame.addChangeListener(e -> updateCutoffMarker());
		validate.addActionListener(e -> decide(true));
		dismiss.addActionListener(e -> decide(false));
		prevNoiseButton.addActionListener(e -> navigateNoise(-1));
		nextNoiseButton.addActionListener(e -> navigateNoise(1));
		minimumRisePercent.addChangeListener(e -> {
			clearCandidates();
			status.setText("Rise threshold changed. Search again to update the candidates.");
			updateControls();
			refreshRise(selectedExperiment());
		});
		String riseTip = "<html>Movement toward the top meniscus divided by the calibrated full capillary length.<br>"
				+ "10 pixels / 430 pixels = 2.326%. The reference is the median of the first few valid bottom samples (up to 10).<br>"
				+ "Search requires a sustained rise that also exceeds the early measurement noise.<br>"
				+ "Use this adjustable rule of thumb to find candidates for review.</html>";
		minimumRiseLabel.setToolTipText(riseTip);
		setSpinnerToolTip(minimumRisePercent, riseTip);
		riseLabel.setToolTipText("<html>Bottom movement relative to its early position, as a percentage of the calibrated capillary length.<br>"
				+ "Positive values indicate movement toward the top meniscus. The inspected frame is shown beside the value.<br>"
				+ "Calibration uses the blue geometry or the stored capillary pixel length and accounts for normalized kymographs.</html>");
		configureToolTips(cutoffLabel);
		updateControls();
	}

	private void configureToolTips(JLabel cutoffLabel) {
		search.setToolTipText("Find sustained bottom rises reaching the minimum percentage of capillary length. Review each proposed cutoff.");
		scanRemaining.setToolTipText("Checked scans from the selected experiment through the end of the list. Unchecked scans only the selected experiment.");
		candidates.setToolTipText("Select a candidate to open its kymograph. Each row shows the experiment, capillary, and proposed first unreachable frame.");
		previous.setToolTipText("Open the previous drift candidate and its kymograph.");
		next.setToolTipText("Open the next drift candidate and its kymograph.");
		showKymograph.setToolTipText("Open the selected candidate's kymograph with the orange cutoff marker.");
		String cutoffTip = "<html>First excluded frame, shown by the orange vertical line.<br>"
				+ "The label where it crosses the bottom trace shows the rise as a percentage of capillary length.<br>"
				+ "Frame 0 is the leftmost column of the kymograph. Adjust the frame before validating.</html>";
		cutoffLabel.setToolTipText(cutoffTip);
		setSpinnerToolTip(firstInvalidFrame, cutoffTip);
		validate.setToolTipText("Save the cutoff. This frame and all subsequent frames are excluded from the usable range.");
		dismiss.setToolTipText("Save this candidate as reviewed, keep the full recording usable, and skip it in future searches.");
		prevNoiseButton.setToolTipText("Show the previous capillary in the selected experiment's noise ranking, which starts with the noisiest.");
		nextNoiseButton.setToolTipText("Show the next capillary in the selected experiment's noise ranking, which starts with the noisiest.");
		qcLabel.setToolTipText("<html>Noise rank and statistics are calculated from the current bottom measurements.<br>"
				+ "MAD is the median absolute deviation after outlier filtering, expressed as a percentage of capillary length.</html>");
	}

	private static void setSpinnerToolTip(JSpinner spinner, String tip) {
		spinner.setToolTipText(tip);
		((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setToolTipText(tip);
		spinner.addPropertyChangeListener("editor",
				e -> ((JSpinner.DefaultEditor) e.getNewValue()).getTextField().setToolTipText(tip));
	}

	private static JPanel row(Component... components) {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		for (Component component : components)
			panel.add(component);
		return panel;
	}

	void showDialog(boolean allSeries) {
		if (dialog != null) {
			dialog.toFront();
			return;
		}
		scanRemaining.setSelected(allSeries);
		dialog = new JDialog(SwingUtilities.getWindowAncestor(owner), "Bottom drift review", Dialog.ModalityType.MODELESS);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		dialog.setContentPane(this);
		dialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				if (scan != null) {
					cancelScan();
					status.setText("Search canceled.");
				}
				clearCutoffMarker();
				displayedHit = null;
				dialog = null;
			}
		});
		refreshRise(selectedExperiment());
		updateControls();
		dialog.pack();
		dialog.setLocationRelativeTo(owner);
		dialog.setVisible(true);
		if (candidates.getSelectedIndex() >= 0)
			openCandidate();
	}

	void setDetectionRunning(boolean running) {
		detectionRunning = running;
		if (running) {
			cancelScan();
			clearCandidates();
			status.setText("Search again after bottom detection finishes.");
		}
		updateControls();
	}

	private void cancelScan() {
		if (scan != null) {
			scan.cancel(true);
			scan = null;
		}
	}

	private Experiment selectedExperiment() {
		return (Experiment) parent0.expListComboLazy.getSelectedItem();
	}

	private void search() {
		try {
			minimumRisePercent.commitEdit();
		} catch (ParseException ex) {
			status.setText("Enter a rise percentage between 0.01 and 100.");
			return;
		}
		double minimumFraction = minimumRiseFraction();
		int start = parent0.expListComboLazy.getSelectedIndex();
		if (start < 0) {
			status.setText("Select an experiment first.");
			return;
		}
		int end = scanRemaining.isSelected() ? parent0.expListComboLazy.getItemCount() - 1 : start;
		List<Experiment> experiments = new ArrayList<>();
		for (int i = start; i <= end; i++)
			experiments.add(parent0.expListComboLazy.getItemAtNoLoad(i));

		clearCandidates();
		status.setText("Scanning 0 / " + experiments.size());
		scan = new SwingWorker<List<DriftHit>, Integer>() {
			private int unreadableCount;
			private int uncalibratedCount;

			@Override
			protected List<DriftHit> doInBackground() {
				List<DriftHit> found = new ArrayList<>();
				for (int i = 0; i < experiments.size() && !isCancelled(); i++) {
					Experiment experiment = experiments.get(i);
					if (experiment != null) {
						int skipped = scanExperiment(experiment, i == 0, minimumFraction, found);
						if (skipped < 0)
							unreadableCount++;
						else
							uncalibratedCount += skipped;
					}
					publish(i + 1);
				}
				return found;
			}

			@Override
			protected void process(List<Integer> progress) {
				if (scan == this)
					status.setText("Scanning " + progress.get(progress.size() - 1) + " / " + experiments.size());
			}

			@Override
			protected void done() {
				if (scan != this)
					return;
				scan = null;
				try {
					for (DriftHit hit : get())
						allHits.add(hit);
					filterHits(null);
					String summary = allHits.isEmpty() ? "No drift candidates or reviewed capillaries." : allHits.size() + " review entries found.";
					if (unreadableCount > 0)
						summary += " Could not load " + unreadableCount + " experiment(s).";
					if (uncalibratedCount > 0)
						summary += " Missing length calibration for " + uncalibratedCount + " capillary/capillaries.";
					status.setText(summary);
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					status.setText("Drift search interrupted.");
				} catch (ExecutionException ex) {
					Logger.warn("Bottom drift search failed", ex.getCause());
					status.setText("Drift search failed.");
				}
				updateControls();
			}
		};
		updateControls();
		scan.execute();
	}

	private int scanExperiment(Experiment experiment, boolean current, double minimumFraction, List<DriftHit> found) {
		Experiment source = experiment;
		boolean hasBottom = current && experiment.getCapillaries().getList().stream()
				.anyMatch(cap -> cap.getBottomRaw().isThereAnyMeasuresDone());
		if (!hasBottom) {
			if (experiment instanceof LazyExperiment) {
				source = new LazyExperiment(((LazyExperiment) experiment).getMetadata());
				((LazyExperiment) source).loadIfNeeded();
				if (experiment.getBinDirectory() != null)
					source.setBinDirectory(experiment.getBinDirectory());
			} else {
				source = new Experiment();
				source.setResultsDirectory(experiment.getResultsDirectory());
				source.setBinDirectory(experiment.getBinDirectory());
			}
			if (!source.load_capillaries_description_and_measures())
				return -1;
			source.loadBinDescription(source.getBinDirectory());
		}
		int uncalibrated = 0;
		for (Capillary cap : source.getCapillaries().getList()) {
			if (cap.getNValid() >= 0 || cap.isDriftDismissed()) {
				found.add(new DriftHit(experiment, cap, ReachableEnd.find(cap, source, minimumFraction)));
				continue;
			}
			if (!Double.isFinite(ReachableEnd.capillaryLengthPixels(cap, source))) {
				uncalibrated++;
				continue;
			}
			int frame = ReachableEnd.findReviewFrame(cap, source, minimumFraction);
			if (frame >= 0)
				found.add(new DriftHit(experiment, cap, frame));
		}
		return uncalibrated;
	}

	private double minimumRiseFraction() {
		return ((Number) minimumRisePercent.getValue()).doubleValue() / 100.0;
	}

	private void clearCandidates() {
		clearCutoffMarker();
		displayedHit = null;
		updatingList = true;
		hits.clear();
		allHits.clear();
		updatingList = false;
	}

	private void selectCandidate(int index) {
		if (index >= 0 && index < hits.size()) {
			candidates.setSelectedIndex(index);
			candidates.ensureIndexIsVisible(index);
		}
	}

	private void openCandidate() {
		clearCutoffMarker();
		displayedHit = null;
		updateControls();
		DriftHit hit = candidates.getSelectedValue();
		if (hit == null)
			return;
		int index = 0;
		while (index < parent0.expListComboLazy.getItemCount()
				&& parent0.expListComboLazy.getItemAtNoLoad(index) != hit.experiment)
			index++;
		if (index == parent0.expListComboLazy.getItemCount()) {
			status.setText("Experiment is no longer in the list. Run a new search.");
			return;
		}
		if (selectedExperiment() != hit.experiment)
			parent0.expListComboLazy.setSelectedIndex(index);
		SwingUtilities.invokeLater(() -> displayCandidate(hit));
	}

	private void displayCandidate(DriftHit hit) {
		if (dialog == null || hit != candidates.getSelectedValue() || detectionRunning)
			return;
		if (selectedExperiment() != hit.experiment) {
			status.setText("Experiment selection was canceled or is still loading. Use Show kymograph to retry.");
			return;
		}
		Sequence sequence = kymographSequence(hit.experiment);
		Capillary cap = null;
		int kymoIndex = 0;
		for (; sequence != null && kymoIndex < sequence.getSizeT(); kymoIndex++) {
			Capillary atFrame = hit.experiment.getSeqKymos().getCapillaryForFrame(kymoIndex, hit.experiment.getCapillaries());
			if (atFrame != null && Objects.equals(hit.capillaryName, atFrame.getRoiName())) {
				cap = atFrame;
				break;
			}
		}
		if (cap == null || parent0.paneKymos.tabIntervals.selectKymographImage(kymoIndex, false) != kymoIndex
				|| currentViewerFrame(sequence) != kymoIndex) {
			status.setText("Could not open this kymograph. Use Show kymograph to retry.");
			updateControls();
			return;
		}
		hit.nValid = cap.getNValid();
		hit.dismissed = cap.isDriftDismissed();
		hit.proposedFrame = ReachableEnd.find(cap, hit.experiment, minimumRiseFraction());
		int proposal = cap.getNValid() >= 0 ? cap.getNValid()
				: hit.proposedFrame;
		if (proposal < 0 && hit.reviewed())
			proposal = 0;
		if (proposal < 0 || proposal >= sequence.getSizeX()) {
			status.setText("The candidate changed. Run a new search.");
			return;
		}
		firstInvalidFrame.setModel(new SpinnerNumberModel(proposal, 0, sequence.getSizeX() - 1, 1));
		displayedHit = hit;
		displayedProposal = proposal;
		refreshRise(hit.experiment);
		status.setText("Candidate " + (candidates.getSelectedIndex() + 1) + " / " + hits.size()
				+ "   Proposed frame " + proposal);
		updateCutoffMarker();
		if (hit.reviewed())
			status.setText(hit.nValid >= 0 ? "Saved cutoff frame " + hit.nValid + ". Adjust it or reset the review."
					: "Dismissed capillary. Reset the review to allow detection again.");
		updateControls();
	}

	private Capillary displayedCapillary() {
		if (displayedHit == null || selectedExperiment() != displayedHit.experiment)
			return null;
		Capillary cap = getCurrentCapillary(displayedHit.experiment);
		return cap != null && Objects.equals(displayedHit.capillaryName, cap.getRoiName()) ? cap : null;
	}

	private void decide(boolean accept) {
		Capillary cap = displayedCapillary();
		if (cap == null || cap.getNValid() != displayedHit.nValid || cap.isDriftDismissed() != displayedHit.dismissed
				|| (!displayedHit.reviewed() && ReachableEnd.findReviewFrame(cap, displayedHit.experiment,
						minimumRiseFraction()) != displayedProposal)) {
			clearCutoffMarker();
			status.setText("The candidate is no longer displayed or has changed. Reopen it to review.");
			return;
		}
		int previousCutoff = cap.getNValid();
		boolean previousDismissal = cap.isDriftDismissed();
		if (accept) {
			try {
				firstInvalidFrame.commitEdit();
			} catch (ParseException ex) {
				status.setText("Enter a frame within the kymograph.");
				return;
			}
			cap.setNValid(((Number) firstInvalidFrame.getValue()).intValue());
			cap.setDriftDismissed(false);
		} else {
			cap.setNValid(ReachableEnd.UNSET);
			cap.setDriftDismissed(true);
		}
		Experiment exp = displayedHit.experiment;
		if (!CapillariesPersistence.Persistence.saveDescription(exp.getCapillaries(), exp.getResultsDirectory())) {
			cap.setNValid(previousCutoff);
			cap.setDriftDismissed(previousDismissal);
			status.setText("Could not save the decision. Check the experiment folder and retry.");
			return;
		}

		DriftHit decided = displayedHit;
		decided.nValid = cap.getNValid();
		decided.dismissed = cap.isDriftDismissed();
		filterHits(null);
		status.setText("Decision saved. Use the review filter to reopen it.");
	}

	private void filterHits(DriftHit selected) {
		clearCutoffMarker();
		displayedHit = null;
		updatingList = true;
		hits.clear();
		int filter = reviewFilter.getSelectedIndex();
		for (DriftHit hit : allHits) {
			if (filter == 3 || (filter == 4 && hit.reviewed()) || (filter == 0 && !hit.reviewed())
					|| (filter == 1 && hit.nValid >= 0)
					|| (filter == 2 && hit.nValid < 0 && hit.dismissed))
				hits.addElement(hit);
		}
		updatingList = false;
		if (selected != null)
			candidates.setSelectedValue(selected, true);
		updateControls();
	}

	private Capillary selectedReviewCapillary() {
		DriftHit hit = candidates.getSelectedValue();
		if (hit == null || selectedExperiment() != hit.experiment)
			return null;
		for (Capillary cap : hit.experiment.getCapillaries().getList()) {
			if (Objects.equals(hit.capillaryName, cap.getRoiName()))
				return cap;
		}
		return null;
	}

	private void resetReview() {
		DriftHit hit = candidates.getSelectedValue();
		Capillary cap = selectedReviewCapillary();
		if (cap == null || hit == null) {
			status.setText("Open the selected experiment before resetting its review.");
			return;
		}
		int previousCutoff = cap.getNValid();
		boolean previousDismissal = cap.isDriftDismissed();
		cap.setNValid(ReachableEnd.UNSET);
		cap.setDriftDismissed(false);
		if (!CapillariesPersistence.Persistence.saveDescription(hit.experiment.getCapillaries(),
				hit.experiment.getResultsDirectory())) {
			cap.setNValid(previousCutoff);
			cap.setDriftDismissed(previousDismissal);
			status.setText("Could not save the reset. Check the experiment folder and retry.");
			return;
		}
		hit.nValid = ReachableEnd.UNSET;
		hit.dismissed = false;
		hit.proposedFrame = ReachableEnd.findReviewFrame(cap, hit.experiment, minimumRiseFraction());
		filterHits(null);
		status.setText("Review reset. The full recording is valid. Search again to refresh drift candidates.");
	}
	private void updateControls() {
		boolean ready = scan == null && !detectionRunning;
		int index = candidates.getSelectedIndex();
		search.setEnabled(ready);
		reviewFilter.setEnabled(ready);
		showTagged.setEnabled(ready);
		Capillary selected = selectedReviewCapillary();
		resetReview.setEnabled(ready && selected != null && (selected.getNValid() >= 0 || selected.isDriftDismissed()));
		scanRemaining.setEnabled(ready);
		minimumRisePercent.setEnabled(ready);
		candidates.setEnabled(ready);
		previous.setEnabled(ready && index > 0);
		next.setEnabled(ready && index + 1 < hits.size());
		showKymograph.setEnabled(ready && index >= 0);
		firstInvalidFrame.setEnabled(ready && displayedHit != null);
		validate.setEnabled(firstInvalidFrame.isEnabled());
		dismiss.setEnabled(firstInvalidFrame.isEnabled());
		prevNoiseButton.setEnabled(ready && !rankedCaps.isEmpty());
		nextNoiseButton.setEnabled(ready && !rankedCaps.isEmpty());
	}

	private void updateCutoffMarker() {
		clearCutoffMarker();
		Capillary cap = displayedCapillary();
		if (cap == null)
			return;
		markerSequence = kymographSequence(displayedHit.experiment);
		int frame = ((Number) firstInvalidFrame.getValue()).intValue();
		cutoffMarker = new ROI2DLine(frame, 0, frame, Math.max(1, markerSequence.getSizeY() - 1));
		cutoffMarker.setName("reachable_end");
		cutoffMarker.setColor(Color.ORANGE);
		cutoffMarker.setReadOnly(true);
		cutoffMarker.setT(currentViewerFrame(markerSequence));
		markerSequence.addROI(cutoffMarker);
		double risePercent = 100.0 * ReachableEnd.riseFraction(cap, displayedHit.experiment, frame);
		if (Double.isFinite(risePercent)) {
			double bottomY = cap.getBottomRaw().getValueAt(frame);
			cutoffRiseOverlay = new CutoffRiseOverlay(frame, bottomY, risePercent, currentViewerFrame(markerSequence));
			markerSequence.addOverlay(cutoffRiseOverlay);
		}
		refreshRise(displayedHit.experiment);
	}

	private void clearCutoffMarker() {
		if (cutoffRiseOverlay != null)
			markerSequence.removeOverlay(cutoffRiseOverlay);
		cutoffRiseOverlay = null;
		if (cutoffMarker != null)
			markerSequence.removeROI(cutoffMarker);
		cutoffMarker = null;
		markerSequence = null;
	}

	private static Sequence kymographSequence(Experiment exp) {
		return exp == null || exp.getSeqKymos() == null ? null : exp.getSeqKymos().getSequence();
	}

	private static int currentViewerFrame(Sequence sequence) {
		Viewer viewer = sequence == null ? null : sequence.getFirstViewer();
		return viewer == null ? -1 : viewer.getPositionT();
	}

	private Capillary getCurrentCapillary(Experiment exp) {
		int index = currentViewerFrame(kymographSequence(exp));
		return index < 0 ? null : exp.getSeqKymos().getCapillaryForFrame(index, exp.getCapillaries());
	}

	private void refreshRise(Experiment exp) {
		Capillary visible = getCurrentCapillary(exp);
		rankedCaps = BottomBaselineEstimator.rankByBottomNoise(exp == null ? null : exp.getCapillaries().getList());
		rankedIndex = rankedCaps.indexOf(visible);
		if (visible == null || !visible.getBottomRaw().isThereAnyMeasuresDone()) {
			qcLabel.setText(rankedCaps.isEmpty() ? "QC: no bottom measures" : "QC: " + rankedCaps.size() + " capillaries");
			riseLabel.setText("Bottom rise: select a capillary with bottom measures");
			return;
		}
		double length = ReachableEnd.capillaryLengthPixels(visible, exp);
		BottomBaselineEstimator.Result bottom = BottomBaselineEstimator.estimate(visible.getBottomRaw());
		qcLabel.setText(String.format("QC: %d/%d  %s  MAD=%.2f%% length", rankedIndex + 1, rankedCaps.size(),
				visible.getRoiName(), 100.0 * bottom.mad / length));
		int frame = visible.getBottomRaw().polylineLevel.npoints - 1;
		if (displayedCapillary() == visible)
			frame = ((Number) firstInvalidFrame.getValue()).intValue();
		double rise = 100.0 * ReachableEnd.riseFraction(visible, exp, frame);
		if (Double.isFinite(rise))
			riseLabel.setText(String.format("Bottom rise at frame %d: %.2f%% length (%.1f px / %.1f px)", frame, rise,
					rise * length / 100.0, length));
		else
			riseLabel.setText("Bottom rise unavailable. Check the length calibration and early top/bottom measures.");
	}

	private void navigateNoise(int delta) {
		Experiment exp = selectedExperiment();
		if (exp == null)
			return;
		refreshRise(exp);
		if (rankedCaps.isEmpty())
			return;
		int target = rankedIndex < 0 ? 0 : Math.floorMod(rankedIndex + delta, rankedCaps.size());
		Capillary cap = rankedCaps.get(target);
		int frame = cap.getKymographIndex();
		if (frame < 0 || parent0.paneKymos.tabIntervals.selectKymographImage(frame, false) != frame)
			return;
		clearCutoffMarker();
		displayedHit = null;
		parent0.paneLevels.selectCapillaryForDetectionDialogs(cap);
		CapillariesSequenceMapper.transferMeasuresToKymos(exp.getCapillaries(), exp.getSeqKymos());
		refreshRise(exp);
		updateControls();
	}

	private static final class CutoffRiseOverlay extends Overlay {
		private final Point2D crossing;
		private final String label;
		private final int kymograph;

		CutoffRiseOverlay(int frame, double bottomY, double risePercent, int kymograph) {
			super("Bottom rise at cutoff", OverlayPriority.TEXT_TOP);
			crossing = new Point2D.Double(frame, bottomY);
			label = String.format("Rise %.2f%% of length", risePercent);
			this.kymograph = kymograph;
			setPersistent(false);
			setReadOnly(true);
		}

		@Override
		public void paint(Graphics2D graphics, Sequence sequence, IcyCanvas canvas) {
			if (graphics == null || !(canvas instanceof IcyCanvas2D) || canvas.getPositionT() != kymograph)
				return;
			Graphics2D g = (Graphics2D) graphics.create();
			try {
				// Keep the label and crossing marker at a readable size at every zoom level.
				Point2D point = g.getTransform().transform(crossing, null);
				g.setTransform(new AffineTransform());
				Rectangle clip = g.getClipBounds();
				if (clip != null && !clip.contains(point))
					return;
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setFont(g.getFont().deriveFont(13f));
				FontMetrics metrics = g.getFontMetrics();
				int width = metrics.stringWidth(label) + 12;
				int height = metrics.getHeight() + 6;
				int x = (int) Math.round(point.getX());
				int y = (int) Math.round(point.getY());
				int left = x + 10;
				int top = y - height - 10;
				if (clip != null) {
					left = Math.max(clip.x, Math.min(left, clip.x + clip.width - width));
					top = Math.max(clip.y, Math.min(top, clip.y + clip.height - height));
				}
				g.setColor(Color.ORANGE);
				g.drawLine(x, y, Math.max(left, Math.min(x, left + width)),
						Math.max(top, Math.min(y, top + height)));
				g.fillOval(x - 3, y - 3, 6, 6);
				g.setColor(new Color(0, 0, 0, 210));
				g.fillRoundRect(left, top, width, height, 6, 6);
				g.setColor(Color.ORANGE);
				g.drawRoundRect(left, top, width, height, 6, 6);
				g.drawString(label, left + 6, top + 3 + metrics.getAscent());
			} finally {
				g.dispose();
			}
		}
	}

	private static final class DriftHit {
		final Experiment experiment;
		final String capillaryName;
		int proposedFrame;
		int nValid;
		boolean dismissed;

		boolean reviewed() {
			return nValid >= 0 || dismissed;
		}

		DriftHit(Experiment experiment, Capillary cap, int proposedFrame) {
			this.experiment = experiment;
			this.capillaryName = cap.getRoiName();
			this.nValid = cap.getNValid();
			this.dismissed = cap.isDriftDismissed();
			this.proposedFrame = proposedFrame;
		}

		@Override
		public String toString() {
			String review = nValid >= 0 ? "Validated drift, cutoff " + nValid
					: dismissed ? "Dismissed" : "Pending, proposed frame " + proposedFrame;
			return experiment + "  |  " + capillaryName + "  |  " + review;
		}
	}
}

