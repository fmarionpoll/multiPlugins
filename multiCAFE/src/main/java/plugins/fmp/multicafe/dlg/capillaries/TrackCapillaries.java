package plugins.fmp.multicafe.dlg.capillaries;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import icy.gui.frame.IcyFrame;
import icy.gui.frame.progress.ProgressFrame;
import icy.gui.viewer.Viewer;
import icy.gui.viewer.ViewerEvent;
import icy.gui.viewer.ViewerListener;
import icy.roi.ROI2D;
import icy.sequence.DimensionId;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryMeasuredTipsOverlay;
import plugins.fmp.multitools.experiment.capillaries.tracking.TrackingBoundary;
import plugins.fmp.multitools.experiment.capillaries.tracking.TrackingTimeline;
import plugins.fmp.multitools.series.ProgressReporter;
import plugins.fmp.multitools.series.TrackCapillariesAlongTime;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.ROI2D.AlongT;
import plugins.fmp.multitools.tools.ROI2D.ROI2DUtilities;

public class TrackCapillaries extends JPanel implements ViewerListener {

	private static final long serialVersionUID = 1L;

	private IcyFrame dialogFrame = null;
	private MultiCAFE parent0 = null;

	private JSpinner tStartSpinner;
	private JSpinner tEndSpinner;
	private JSpinner outlierMadFactorSpinner;
	private JSpinner outlierMinPxSpinner;
	private JButton runFrameByFrameButton = new JButton("Run tracking");
	private JButton runFromCurrentTButton = new JButton("Run from current T");
	private JButton runBackwardFromCurrentTButton = new JButton("Run backwards from current T");
	private JButton saveButton = new JButton("Save");
	private JButton validateBlueButton = new JButton("Validate blue at current T");
	private JButton addBoundaryButton = new JButton("Add boundary at current T");
	private JButton moveBoundaryButton = new JButton("Move selected boundary here");
	private JButton deleteBoundaryButton = new JButton("Delete boundary / merge");
	private DefaultListModel<TrackingBoundary> boundaryListModel = new DefaultListModel<TrackingBoundary>();
	private JList<TrackingBoundary> boundaryList = new JList<TrackingBoundary>(boundaryListModel);
	private Viewer trackedViewer;
	private Experiment trackedExperiment;
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
		outlierMadFactorSpinner = new JSpinner(new SpinnerNumberModel(2.5, 1.0, 8.0, 0.5));
		outlierMinPxSpinner = new JSpinner(new SpinnerNumberModel(5, 0, 50, 1));

		JPanel topPanel = new JPanel(new GridLayout(6, 1));
		FlowLayout flow = new FlowLayout(FlowLayout.LEFT);

		JPanel p1 = new JPanel(flow);
		p1.add(new JLabel("Frame range:"));
		p1.add(new JLabel("from"));
		p1.add(tStartSpinner);
		p1.add(new JLabel("to"));
		p1.add(tEndSpinner);
		topPanel.add(p1);

		JPanel p1b = new JPanel(flow);
		p1b.add(new JLabel("Inconsistent capillary motions are rejected automatically by the shared frame fit."));
		topPanel.add(p1b);

		JPanel p2 = new JPanel(flow);
		p2.add(runFrameByFrameButton);
		p2.add(runFromCurrentTButton);
		p2.add(runBackwardFromCurrentTButton);
		p2.add(validateBlueButton);
		p2.add(saveButton);
		topPanel.add(p2);

		JPanel p3 = new JPanel(flow);
		p3.add(new JLabel(
				"From current T: sets From then runs forward. Backwards: sets To to viewer, tracks from To down to From."));
		topPanel.add(p3);

		JPanel p4 = new JPanel(flow);
		p4.add(new JLabel(
				"Tracking will replace AlongT intervals in the selected range. Intervals outside the range are unchanged."));
		topPanel.add(p4);

		JPanel p5 = new JPanel(flow);
		p5.add(addBoundaryButton);
		p5.add(moveBoundaryButton);
		p5.add(deleteBoundaryButton);
		topPanel.add(p5);

		boundaryList.setVisibleRowCount(6);
		boundaryList.setCellRenderer((list, value, index, selected, focus) -> {
			String prefix = value.getOrigin() == TrackingBoundary.Origin.MANUAL ? "manual" : "suggested";
			String reason = value.getReason().isEmpty() ? "" : " — " + value.getReason();
			JLabel label = new JLabel("T=" + value.getFrame() + "  " + prefix + " / "
					+ value.getStatus().name().toLowerCase() + reason);
			if (selected) {
				label.setOpaque(true);
				label.setBackground(list.getSelectionBackground());
				label.setForeground(list.getSelectionForeground());
			}
			return label;
		});

		dialogFrame = new IcyFrame("Track capillaries along time", true, true);
		dialogFrame.add(topPanel, BorderLayout.NORTH);
		dialogFrame.add(new JScrollPane(boundaryList), BorderLayout.CENTER);
		dialogFrame.setLocation(pt);
		dialogFrame.pack();
		dialogFrame.addToDesktopPane();
		dialogFrame.setVisible(true);
		parent0.expListComboLazy.removeItemListener(experimentSelectionListener);
		parent0.expListComboLazy.addItemListener(experimentSelectionListener);
		bindSelectedExperiment();

		runFrameByFrameButton.addActionListener(e -> runTrackingFrameByFrame());
		runFromCurrentTButton.addActionListener(e -> runFromCurrentT());
		runBackwardFromCurrentTButton.addActionListener(e -> runBackwardFromCurrentT());
		saveButton.addActionListener(e -> save());
		validateBlueButton.addActionListener(e -> validateBlueAtCurrentT());
		addBoundaryButton.addActionListener(e -> addBoundaryAtCurrentT());
		moveBoundaryButton.addActionListener(e -> moveSelectedBoundaryToCurrentT());
		deleteBoundaryButton.addActionListener(e -> deleteSelectedBoundary());
		boundaryList.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting() && boundaryList.getSelectedValue() != null)
				setViewerPositionT(boundaryList.getSelectedValue().getFrame());
		});
		refreshBoundaries();
	}

	private TrackingTimeline timeline() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		return exp != null && exp.getCapillaries() != null ? exp.getCapillaries().getTrackingTimeline() : null;
	}

	private void refreshBoundaries() {
		boundaryListModel.clear();
		TrackingTimeline timeline = timeline();
		if (timeline != null)
			for (TrackingBoundary boundary : timeline.getBoundaries())
				boundaryListModel.addElement(boundary);
	}

	private void addBoundaryAtCurrentT() {
		int frame = getViewerPositionT();
		if (frame < 1) {
			JOptionPane.showMessageDialog(this, "Frame 0 cannot begin a new segment.", "Tracking boundary",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		TrackingTimeline timeline = timeline();
		if (timeline != null) {
			timeline.addManual(frame, "user boundary");
			refreshBoundaries();
			selectBoundary(frame);
		}
	}

	private void moveSelectedBoundaryToCurrentT() {
		TrackingBoundary selected = boundaryList.getSelectedValue();
		int frame = getViewerPositionT();
		if (selected == null || frame < 1)
			return;
		TrackingTimeline timeline = timeline();
		if (timeline != null) {
			timeline.move(selected.getFrame(), frame);
			refreshBoundaries();
			selectBoundary(frame);
		}
	}

	private void deleteSelectedBoundary() {
		TrackingBoundary selected = boundaryList.getSelectedValue();
		TrackingTimeline timeline = timeline();
		if (selected != null && timeline != null) {
			timeline.remove(selected.getFrame());
			refreshBoundaries();
		}
	}

	private void selectBoundary(int frame) {
		for (int i = 0; i < boundaryListModel.size(); i++)
			if (boundaryListModel.get(i).getFrame() == frame) {
				boundaryList.setSelectedIndex(i);
				boundaryList.ensureIndexIsVisible(i);
				return;
			}
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

	private void runFromCurrentT() {
		int t = getViewerPositionT();
		tStartSpinner.setValue(t);
		runTrackingFrameByFrame();
	}

	private static final double LENGTH_TOLERANCE_PX = 2.0;
	private static final double LENGTH_TOLERANCE_RATIO = 0.03;

	private void runBackwardFromCurrentT() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSeqCamData() == null)
			return;
		int tCurr = getViewerPositionT();
		tEndSpinner.setValue(tCurr);
		int tFrom = (Integer) tStartSpinner.getValue();

		List<int[]> mismatches = collectLengthMismatches(exp, tCurr);
		if (!mismatches.isEmpty()) {
			String msg = buildLengthMismatchMessage(exp.getCapillaries(), mismatches);
			String[] options = { "Cancel", "Continue anyway", "Normalize length and run" };
			int choice = JOptionPane.showOptionDialog(getParent(), msg, "Capillary length changed",
					JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
			if (choice == 0)
				return;
			if (choice == 2) {
				normalizeLengthsAndSync(exp, tCurr, mismatches);
			}
		}
		runTrackingInWorker(tCurr, tFrom);
	}

	private List<int[]> collectLengthMismatches(Experiment exp, int tCurr) {
		Capillaries caps = exp.getCapillaries();
		List<int[]> out = new ArrayList<>();
		List<Capillary> list = caps.getList();
		for (int i = 0; i < list.size(); i++) {
			Capillary cap = list.get(i);
			if (!cap.getKymographBuild())
				continue;
			AlongT atCurr = cap.getAlongTAtT(tCurr);
			AlongT atRef = cap.getAlongTAtT(tCurr - 1);
			if (atCurr == null || atCurr.getRoi() == null || atRef == null || atRef.getRoi() == null)
				continue;
			ROI2D roiCurr = atCurr.getRoi();
			ROI2D roiRef = atRef.getRoi();
			double lenCurr = ROI2DUtilities.getPolylinePathLength(roiCurr);
			double lenRef = ROI2DUtilities.getPolylinePathLength(roiRef);
			int nCurr = ROI2DUtilities.getPolylinePointCount(roiCurr);
			int nRef = ROI2DUtilities.getPolylinePointCount(roiRef);
			boolean lengthDiff = Math.abs(lenCurr - lenRef) > LENGTH_TOLERANCE_PX
					|| (lenRef > 0 && Math.abs(lenCurr - lenRef) / lenRef > LENGTH_TOLERANCE_RATIO);
			boolean npointsDiff = nCurr != nRef;
			if (lengthDiff || npointsDiff)
				out.add(new int[] { i, nRef });
		}
		return out;
	}

	private String buildLengthMismatchMessage(Capillaries caps, List<int[]> mismatches) {
		StringBuilder sb = new StringBuilder();
		sb.append("Some capillaries have different length or point count than the previous frame (reference). ");
		sb.append("This can affect kymograph consistency.\n\n");
		for (int[] m : mismatches) {
			int i = m[0];
			Capillary cap = caps.getList().get(i);
			String name = cap.getKymographName() != null ? cap.getKymographName() : ("cap " + i);
			sb.append("• ").append(name).append(" (reference npoints: ").append(m[1]).append(")\n");
		}
		sb.append(
				"\nCancel to fix manually, Continue to run anyway, or Normalize to resample current ROIs to reference point count.");
		return sb.toString();
	}

	private void normalizeLengthsAndSync(Experiment exp, int tCurr, List<int[]> mismatches) {
		Capillaries caps = exp.getCapillaries();
		List<Capillary> list = caps.getList();
		for (int[] m : mismatches) {
			int i = m[0];
			int refNpoints = m[1];
			Capillary cap = list.get(i);
			AlongT atCurr = cap.getAlongTAtT(tCurr);
			if (atCurr == null || atCurr.getRoi() == null)
				continue;
			ROI2D resampled = ROI2DUtilities.resamplePolylineToNPoints(atCurr.getRoi(), refNpoints);
			if (resampled != null)
				cap.updateROIAtFrameT(tCurr, resampled);
		}
		caps.invalidateKymoIntervalsCache();
		if (exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null)
			caps.transferROIsToSequence(exp.getSeqCamData().getSequence());
	}

	private void runTrackingFrameByFrame() {
		int t0 = (Integer) tStartSpinner.getValue();
		int t1 = (Integer) tEndSpinner.getValue();
		runTrackingInWorker(t0, t1);
	}

	private void runTrackingInWorker(int tStart, int tEnd) {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSeqCamData() == null)
			return;

		final int t0 = tStart;
		final int t1 = tEnd;
		final double madFactor = ((Number) outlierMadFactorSpinner.getValue()).doubleValue();
		final double minPx = ((Number) outlierMinPxSpinner.getValue()).intValue();
		final ProgressFrame pf = new ProgressFrame("Tracking capillaries (dlg)");
		ProgressReporter progress = progressReporterFor(pf);

		new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() {
				long t0Ms = System.currentTimeMillis();
				new TrackCapillariesAlongTime().run(exp, t0, t1, progress, madFactor, minPx);
				long elapsed = System.currentTimeMillis() - t0Ms;
				Logger.debug("Track capillaries: " + elapsed + " ms");
				return null;
			}

			@Override
			protected void done() {
				pf.close();
			}
		}.execute();
	}

	private ProgressReporter progressReporterFor(ProgressFrame pf) {
		return new ProgressReporter() {
			@Override
			public void updateMessage(String message) {
				SwingUtilities.invokeLater(() -> pf.setMessage(message));
			}

			@Override
			public void updateProgress(int percentage) {
				SwingUtilities.invokeLater(() -> pf.setMessage(percentage + "%"));
			}

			@Override
			public void updateProgress(String message, int current, int total) {
				SwingUtilities.invokeLater(() -> {
					pf.setMessage(message);
					pf.setLength(total);
					if (total > 0 && current >= 0)
						pf.setPosition((double) current / total);
				});
			}

			@Override
			public void completed() {
				SwingUtilities.invokeLater(() -> pf.close());
			}

			@Override
			public void failed(String errorMessage) {
				SwingUtilities.invokeLater(() -> {
					pf.setMessage("Failed: " + errorMessage);
					pf.close();
				});
			}

			@Override
			public boolean isCancelled() {
				return false;
			}

		};
	}

	private void save() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null)
			exp.save_capillaries_description_and_measures();
	}

	private void validateBlueAtCurrentT() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSeqCamData() == null)
			return;
		int t = getViewerPositionT();
		int updated = CapillaryMeasuredTipsOverlay.transferTipsFromSequence(exp.getCapillaries(),
				exp.getSeqCamData(), t);
		if (updated == 0) {
			JOptionPane.showMessageDialog(this, "No editable light-blue capillary lines were found.",
					"Physical capillaries", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		exp.save_capillaries_description_and_measures();
		CapillaryMeasuredTipsOverlay.transferTipsToSequence(exp.getCapillaries(), exp.getSeqCamData(), t);
	}

	void close() {
		if (parent0 != null)
			parent0.expListComboLazy.removeItemListener(experimentSelectionListener);
		if (trackedViewer != null) {
			trackedViewer.removeListener(this);
			trackedViewer = null;
		}
		showGreenAgain(trackedExperiment);
		trackedExperiment = null;
		if (dialogFrame != null)
			dialogFrame.close();
	}

	private void showBlueOnly() {
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

	private void showGreenAgain() {
		Experiment exp = parent0 == null ? null : (Experiment) parent0.expListComboLazy.getSelectedItem();
		showGreenAgain(exp);
	}

	private void showGreenAgain(Experiment exp) {
		if (exp != null && exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null)
			parent0.paneExperiment.tabOptions.displayROIsCategory(
					exp.getSeqCamData().getSequence().getFirstViewer(), "line",
					parent0.viewOptions.isViewCapillaries());
	}

	private void bindSelectedExperiment() {
		Experiment selected = parent0 == null ? null : (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (selected == trackedExperiment) {
			refreshBoundaries();
			showBlueOnly();
			return;
		}
		showGreenAgain(trackedExperiment);
		if (trackedViewer != null)
			trackedViewer.removeListener(this);
		trackedExperiment = selected;
		trackedViewer = null;
		if (selected == null || selected.getSeqCamData() == null
				|| selected.getSeqCamData().getSequence() == null) {
			boundaryListModel.clear();
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
		refreshBoundaries();
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
