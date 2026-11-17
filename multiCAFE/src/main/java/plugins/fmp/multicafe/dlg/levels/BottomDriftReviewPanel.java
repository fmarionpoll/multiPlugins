package plugins.fmp.multicafe.dlg.levels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import icy.gui.viewer.Viewer;
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
	private Sequence baselineSequence;
	private ROI2DLine baselineMarker;
	private final JSpinner baselineYSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 10000.0, 1.0));
	private List<Capillary> rankedCaps = new ArrayList<>();
	private int rankedIndex = -1;
	private final JButton computeAllButton = new JButton("Baseline all");
	private final JButton computeCurrentButton = new JButton("Baseline current");
	private final JButton clearBaselineButton = new JButton("Clear baseline");
	private final JButton setBaselineButton = new JButton("Set Y");
	private final JButton prevNoiseButton = new JButton("< noisy");
	private final JButton nextNoiseButton = new JButton("noisy >");
	private final JLabel qcLabel = new JLabel("QC: no bottom measures");

	BottomDriftReviewPanel(MultiCAFE parent, Component owner) {
		this.parent0 = parent;
		this.owner = owner;
		setLayout(new BorderLayout());
		add(row(search, scanRemaining), BorderLayout.NORTH);

		candidates.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		JScrollPane list = new JScrollPane(candidates);
		list.setPreferredSize(new Dimension(660, 180));
		add(list, BorderLayout.CENTER);

		JPanel actions = new JPanel(new GridLayout(0, 1));
		actions.add(row(status));
		actions.add(row(previous, next, showKymograph));
		JLabel cutoffLabel = new JLabel("First unreachable frame (0-based)");
		cutoffLabel.setLabelFor(firstInvalidFrame);
		actions.add(row(cutoffLabel, firstInvalidFrame, validate, dismiss));
		JLabel baselineYLabel = new JLabel("Baseline Y (px)");
		baselineYLabel.setLabelFor(baselineYSpinner);
		actions.add(row(computeCurrentButton, computeAllButton, clearBaselineButton, baselineYLabel, baselineYSpinner,
				setBaselineButton));
		actions.add(row(prevNoiseButton, nextNoiseButton, qcLabel));
		add(actions, BorderLayout.SOUTH);

		search.addActionListener(e -> search());
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
		computeCurrentButton.addActionListener(e -> computeBaseline(false));
		computeAllButton.addActionListener(e -> computeBaseline(true));
		clearBaselineButton.addActionListener(e -> clearBaselines());
		setBaselineButton.addActionListener(e -> setManualBaseline());
		prevNoiseButton.addActionListener(e -> navigateNoise(-1));
		nextNoiseButton.addActionListener(e -> navigateNoise(1));
		configureToolTips(cutoffLabel, baselineYLabel);
		updateControls();
	}

	private void configureToolTips(JLabel cutoffLabel, JLabel baselineYLabel) {
		search.setToolTipText("Find recordings where the detected bottom meniscus moves toward the top meniscus and stays displaced.");
		scanRemaining.setToolTipText("Checked scans from the selected experiment through the end of the list. Unchecked scans only the selected experiment.");
		candidates.setToolTipText("Select a candidate to open its kymograph. Each row shows the experiment, capillary, and proposed first unreachable frame.");
		previous.setToolTipText("Open the previous drift candidate and its kymograph.");
		next.setToolTipText("Open the next drift candidate and its kymograph.");
		showKymograph.setToolTipText("Open the selected candidate's kymograph with the orange cutoff marker.");
		String cutoffTip = "<html>First excluded frame, shown by the orange vertical line.<br>"
				+ "Frame 0 is the leftmost column of the kymograph. Adjust the frame before validating.</html>";
		cutoffLabel.setToolTipText(cutoffTip);
		setSpinnerToolTip(firstInvalidFrame, cutoffTip);
		validate.setToolTipText("Save the cutoff. This frame and all subsequent frames are excluded from the usable range.");
		dismiss.setToolTipText("Save this candidate as reviewed, keep the full recording usable, and skip it in future searches.");

		computeCurrentButton.setToolTipText("Estimate and save the bottom baseline for the capillary shown in the kymograph.");
		computeAllButton.setToolTipText("Estimate and save bottom baselines for all capillaries in the selected experiment.");
		clearBaselineButton.setToolTipText("Remove the saved bottom baselines and their noise statistics for all capillaries in the selected experiment.");
		String baselineTip = "<html>Vertical pixel coordinate of the bottom reference line in the kymograph, measured from the top.<br>"
				+ "An automatic baseline is the median of detected bottom positions after outlier filtering.<br>"
				+ "Use Set Y to save an entered value. The baseline is shown as a horizontal magenta line during review.</html>";
		baselineYLabel.setToolTipText(baselineTip);
		setSpinnerToolTip(baselineYSpinner, baselineTip);
		setBaselineButton.setToolTipText("Save the entered Y as the bottom baseline for the capillary shown in the kymograph.");
		prevNoiseButton.setToolTipText("Show the previous capillary in the selected experiment's noise ranking, which starts with the noisiest.");
		nextNoiseButton.setToolTipText("Show the next capillary in the selected experiment's noise ranking, which starts with the noisiest.");
		qcLabel.setToolTipText("<html>Quality control shows the noise rank, number of capillaries with bottom measures, and capillary name.<br>"
				+ "Y is the saved bottom baseline in kymograph pixels. MAD is the median absolute deviation of<br>"
				+ "the points used to estimate that baseline, in pixels. NaN indicates an unavailable value.</html>");
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
				clearBaselineMarker();
				displayedHit = null;
				dialog = null;
			}
		});
		refreshQcList(selectedExperiment());
		updateBaselineSpinner(getCurrentCapillary(selectedExperiment()));
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
			clearBaselineMarker();
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

			@Override
			protected List<DriftHit> doInBackground() {
				List<DriftHit> found = new ArrayList<>();
				for (int i = 0; i < experiments.size() && !isCancelled(); i++) {
					Experiment experiment = experiments.get(i);
					if (experiment != null && !scanExperiment(experiment, i == 0, found))
						unreadableCount++;
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
						hits.addElement(hit);
					String summary = hits.isEmpty() ? "No drift candidates." : hits.size() + " drift candidate(s).";
					if (unreadableCount > 0)
						summary += " Could not load " + unreadableCount + " experiment(s).";
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

	private boolean scanExperiment(Experiment experiment, boolean current, List<DriftHit> found) {
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
				return false;
		}
		for (Capillary cap : source.getCapillaries().getList()) {
			int frame = ReachableEnd.findReviewFrame(cap);
			if (frame >= 0)
				found.add(new DriftHit(experiment, cap.getRoiName(), frame));
		}
		return true;
	}

	private void clearCandidates() {
		clearCutoffMarker();
		displayedHit = null;
		updatingList = true;
		hits.clear();
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
		clearBaselineMarker();
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
			return;
		}
		int proposal = ReachableEnd.findReviewFrame(cap);
		if (proposal < 0 || proposal >= sequence.getSizeX()) {
			status.setText("The candidate changed. Run a new search.");
			return;
		}
		firstInvalidFrame.setModel(new SpinnerNumberModel(proposal, 0, sequence.getSizeX() - 1, 1));
		displayedHit = hit;
		displayedProposal = proposal;
		updateBaselineSpinner(cap);
		refreshQcList(hit.experiment);
		status.setText("Candidate " + (candidates.getSelectedIndex() + 1) + " / " + hits.size()
				+ "   Proposed frame " + proposal);
		showBaseline(hit.experiment, cap);
		updateCutoffMarker();
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
		if (cap == null || ReachableEnd.findReviewFrame(cap) != displayedProposal) {
			clearCutoffMarker();
			status.setText("The candidate is no longer displayed or has changed. Reopen it to review.");
			return;
		}
		if (accept) {
			try {
				firstInvalidFrame.commitEdit();
			} catch (ParseException ex) {
				status.setText("Enter a frame within the kymograph.");
				return;
			}
			cap.setNValid(((Number) firstInvalidFrame.getValue()).intValue());
		} else {
			cap.setDriftDismissed(true);
		}
		Experiment exp = displayedHit.experiment;
		if (!CapillariesPersistence.Persistence.saveDescription(exp.getCapillaries(), exp.getResultsDirectory())) {
			cap.setNValid(ReachableEnd.UNSET);
			cap.setDriftDismissed(false);
			status.setText("Could not save the decision. Check the experiment folder and retry.");
			return;
		}

		int index = candidates.getSelectedIndex();
		clearCutoffMarker();
		displayedHit = null;
		updatingList = true;
		candidates.clearSelection();
		hits.remove(index);
		updatingList = false;
		if (hits.isEmpty())
			status.setText("All drift candidates reviewed.");
		else
			selectCandidate(Math.min(index, hits.size() - 1));
		updateControls();
	}

	private void updateControls() {
		boolean ready = scan == null && !detectionRunning;
		int index = candidates.getSelectedIndex();
		search.setEnabled(ready);
		scanRemaining.setEnabled(ready);
		candidates.setEnabled(ready);
		previous.setEnabled(ready && index > 0);
		next.setEnabled(ready && index + 1 < hits.size());
		showKymograph.setEnabled(ready && index >= 0);
		firstInvalidFrame.setEnabled(ready && displayedHit != null);
		validate.setEnabled(firstInvalidFrame.isEnabled());
		dismiss.setEnabled(firstInvalidFrame.isEnabled());
		for (Component control : new Component[] { computeCurrentButton, computeAllButton, clearBaselineButton,
				baselineYSpinner, setBaselineButton, prevNoiseButton, nextNoiseButton })
			control.setEnabled(ready);
	}

	private void updateCutoffMarker() {
		clearCutoffMarker();
		if (displayedCapillary() == null)
			return;
		markerSequence = kymographSequence(displayedHit.experiment);
		int frame = ((Number) firstInvalidFrame.getValue()).intValue();
		cutoffMarker = new ROI2DLine(frame, 0, frame, Math.max(1, markerSequence.getSizeY() - 1));
		cutoffMarker.setName("reachable_end");
		cutoffMarker.setColor(Color.ORANGE);
		cutoffMarker.setReadOnly(true);
		cutoffMarker.setT(currentViewerFrame(markerSequence));
		markerSequence.addROI(cutoffMarker);
	}

	private void clearCutoffMarker() {
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

	private void computeBaseline(boolean all) {
		Experiment exp = selectedExperiment();
		if (exp == null)
			return;
		Capillary current = getCurrentCapillary(exp);
		if (all) {
			for (Capillary cap : exp.getCapillaries().getList())
				BottomBaselineEstimator.estimateAndApply(cap);
		} else if (current != null) {
			BottomBaselineEstimator.estimateAndApply(current);
		} else {
			return;
		}
		saveBaselines(exp);
	}

	private void clearBaselines() {
		Experiment exp = selectedExperiment();
		if (exp == null)
			return;
		for (Capillary cap : exp.getCapillaries().getList())
			cap.clearBottomBaseline();
		saveBaselines(exp);
	}

	private void setManualBaseline() {
		Experiment exp = selectedExperiment();
		Capillary cap = getCurrentCapillary(exp);
		if (cap == null)
			return;
		try {
			baselineYSpinner.commitEdit();
		} catch (ParseException ex) {
			status.setText("Enter a valid baseline Y coordinate.");
			return;
		}
		cap.setBottomBaselineY(((Number) baselineYSpinner.getValue()).doubleValue());
		cap.setBottomBaselineMad(Double.NaN);
		cap.setBottomBaselineOutlierFrac(Double.NaN);
		saveBaselines(exp);
	}

	private void saveBaselines(Experiment exp) {
		if (!CapillariesPersistence.Persistence.saveDescription(exp.getCapillaries(), exp.getResultsDirectory()))
			status.setText("Could not save the bottom baseline.");
		Capillary cap = getCurrentCapillary(exp);
		updateBaselineSpinner(cap);
		refreshQcList(exp);
		showBaseline(exp, cap);
	}

	private void updateBaselineSpinner(Capillary cap) {
		if (cap != null && Double.isFinite(cap.getBottomBaselineY()))
			baselineYSpinner.setValue(cap.getBottomBaselineY());
	}

	private void refreshQcList(Experiment exp) {
		Capillary visible = getCurrentCapillary(exp);
		rankedCaps = BottomBaselineEstimator.rankByBottomNoise(exp == null ? null : exp.getCapillaries().getList());
		rankedIndex = rankedCaps.indexOf(visible);
		if (rankedIndex < 0) {
			qcLabel.setText(rankedCaps.isEmpty() ? "QC: no bottom measures" : "QC: " + rankedCaps.size() + " capillaries");
			return;
		}
		qcLabel.setText(String.format("QC: %d/%d  %s  Y=%.1f  MAD=%.1f", rankedIndex + 1, rankedCaps.size(),
				visible.getRoiName(), visible.getBottomBaselineY(), visible.getBottomBaselineMad()));
	}

	private void navigateNoise(int delta) {
		Experiment exp = selectedExperiment();
		if (exp == null)
			return;
		refreshQcList(exp);
		if (rankedCaps.isEmpty())
			return;
		int target = rankedIndex < 0 ? 0 : Math.floorMod(rankedIndex + delta, rankedCaps.size());
		Capillary cap = rankedCaps.get(target);
		int frame = cap.getKymographIndex();
		if (frame < 0 || parent0.paneKymos.tabIntervals.selectKymographImage(frame, false) != frame)
			return;
		clearCutoffMarker();
		displayedHit = null;
		updateControls();
		parent0.paneLevels.selectCapillaryForDetectionDialogs(cap);
		updateBaselineSpinner(cap);
		refreshQcList(exp);
		CapillariesSequenceMapper.transferMeasuresToKymos(exp.getCapillaries(), exp.getSeqKymos());
		showBaseline(exp, cap);
	}

	private void showBaseline(Experiment exp, Capillary cap) {
		clearBaselineMarker();
		Sequence sequence = kymographSequence(exp);
		if (sequence == null || cap == null || !Double.isFinite(cap.getBottomBaselineY()))
			return;
		double y = cap.getBottomBaselineY();
		baselineSequence = sequence;
		baselineMarker = new ROI2DLine(0, y, Math.max(1, sequence.getSizeX() - 1), y);
		baselineMarker.setName("bottom_baseline");
		baselineMarker.setColor(Color.MAGENTA);
		baselineMarker.setReadOnly(true);
		baselineMarker.setT(currentViewerFrame(sequence));
		sequence.addROI(baselineMarker);
	}

	private void clearBaselineMarker() {
		if (baselineMarker != null)
			baselineSequence.removeROI(baselineMarker);
		baselineMarker = null;
		baselineSequence = null;
	}

	private static final class DriftHit {
		final Experiment experiment;
		final String capillaryName;
		final int proposedFrame;

		DriftHit(Experiment experiment, String capillaryName, int proposedFrame) {
			this.experiment = experiment;
			this.capillaryName = capillaryName;
			this.proposedFrame = proposedFrame;
		}

		@Override
		public String toString() {
			return experiment + "  |  " + capillaryName + "  |  frame " + proposedFrame;
		}
	}
}

