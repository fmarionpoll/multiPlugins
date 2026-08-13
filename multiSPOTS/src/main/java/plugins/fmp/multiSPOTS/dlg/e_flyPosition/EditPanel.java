package plugins.fmp.multiSPOTS.dlg.e_flyPosition;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Rectangle2D;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import icy.gui.dialog.MessageDialog;
import icy.gui.viewer.Viewer;
import icy.roi.ROI2D;
import icy.sequence.Sequence;
import icy.sequence.SequenceEvent;
import icy.sequence.SequenceEvent.SequenceEventSourceType;
import icy.sequence.SequenceListener;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.Experiment.FlyEditValidateResult;
import plugins.fmp.multitools.experiment.Experiment.FlyPositionsAtTSnapshot;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.kernel.roi.roi2d.ROI2DRectangle;

public class EditPanel extends JPanel implements ChangeListener, SequenceListener {
	private static final long serialVersionUID = -5257698990389571518L;
	private static final Color FLY_ROI_COLOR = Color.YELLOW;
	private static final double DEFAULT_FLY_W = 10;
	private static final double DEFAULT_FLY_H = 5;
	private static final AtomicInteger DET_NEW_COUNTER = new AtomicInteger(0);

	private static final String TIP_ADD = "Add a yellow fly rectangle at the selected cage center (or image center). Move/resize it, then Validate.";
	private static final String TIP_DELETE = "Remove the selected yellow fly rectangle from the screen. Validate to commit.";
	private static final String TIP_RESTORE = "Restore flies at current T to the state when this frame was entered (undo unvalidated edits and any Validate done during this visit).";
	private static final String TIP_VALIDATE = "Validate changes at current T: keep yellow fly rectangles on screen, assign each to a cage by position, and store coordinates and size for this frame. Use Load/Save to write to disk.";

	private MultiSPOTS parent0;
	private final JButton addButton = new JButton("Add");
	private final JButton deleteButton = new JButton("Delete");
	private final JButton restoreButton = new JButton("Restore");
	private final JButton validateButton = new JButton("Validate");

	private boolean dirty = false;
	private int sessionT = -1;
	private FlyPositionsAtTSnapshot entryBaseline = null;
	private boolean suppressRoiEvents = false;
	private boolean suppressTChange = false;
	private Sequence listenedSequence = null;

	void init(GridLayout capLayout, MultiSPOTS parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;
		FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);
		flowLayout.setVgap(0);

		addButton.setToolTipText(TIP_ADD);
		deleteButton.setToolTipText(TIP_DELETE);
		restoreButton.setToolTipText(TIP_RESTORE);
		validateButton.setToolTipText(TIP_VALIDATE);

		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(addButton);
		panel1.add(deleteButton);
		panel1.add(restoreButton);
		panel1.add(validateButton);
		add(panel1);

		defineActionListeners();
	}

	private void defineActionListeners() {
		addButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				addFlyRoi();
			}
		});
		deleteButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				deleteSelectedFlyRoi();
			}
		});
		restoreButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				restoreEntryBaseline();
			}
		});
		validateButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				validateCurrentT(true);
			}
		});
	}

	boolean isEditTabActive() {
		if (parent0 == null || parent0.dlgDetectFlies == null)
			return false;
		return parent0.dlgDetectFlies.tabsPane.getSelectedIndex() == parent0.dlgDetectFlies.iTAB_EDIT;
	}

	/**
	 * Called from the camera viewer T-change path before {@code updateROIsAt}.
	 * Returns false if the user cancelled leaving the current frame (viewer T was
	 * reverted).
	 */
	public boolean allowCamTChange(Experiment exp, Viewer viewer, int newT) {
		if (suppressTChange)
			return false;
		if (exp == null)
			return true;
		if (!isEditTabActive()) {
			clearSession();
			return true;
		}
		ensureSequenceListener(exp);
		if (sessionT < 0) {
			beginSessionAtT(exp, newT);
			return true;
		}
		if (newT == sessionT)
			return true;
		if (!dirty) {
			beginSessionAtT(exp, newT);
			return true;
		}
		int choice = JOptionPane.showOptionDialog(this,
				"Validate changes, discard them, or cancel and stay on this frame?",
				"Unvalidated edits at T=" + sessionT, JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE,
				null, new Object[] { "Validate", "Discard", "Cancel" }, "Validate");
		if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
			suppressTChange = true;
			try {
				if (viewer != null)
					viewer.setPositionT(sessionT);
			} finally {
				SwingUtilities.invokeLater(() -> suppressTChange = false);
			}
			return false;
		}
		if (choice == JOptionPane.YES_OPTION) {
			validateAtT(exp, sessionT, false);
		} else {
			applyEntryBaseline(exp);
		}
		beginSessionAtT(exp, newT);
		return true;
	}

	void onEditTabSelected() {
		Experiment exp = getExperiment();
		if (exp == null)
			return;
		enableFliesRectVisible();
		ensureSequenceListener(exp);
		int t = getCurrentT(exp);
		if (t >= 0) {
			suppressRoiEvents = true;
			try {
				exp.updateROIsAt(t);
			} finally {
				suppressRoiEvents = false;
			}
			beginSessionAtT(exp, t);
			exp.getSeqCamData().displaySpecificROIs(true, "det");
		}
	}

	void onEditTabDeselected() {
		detachSequenceListener();
		clearSession();
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		if (!isEditTabActive()) {
			onEditTabDeselected();
			return;
		}
		onEditTabSelected();
	}

	@Override
	public void sequenceChanged(SequenceEvent sequenceEvent) {
		if (suppressRoiEvents || !isEditTabActive() || !dirtyPossible())
			return;
		if (sequenceEvent == null || sequenceEvent.getSourceType() != SequenceEventSourceType.SEQUENCE_ROI)
			return;
		Object source = sequenceEvent.getSource();
		if (source instanceof ROI2D) {
			ROI2D roi = (ROI2D) source;
			if (Cage.isFlyEditRectangleName(roi.getName()))
				markDirty();
		} else {
			markDirty();
		}
	}

	@Override
	public void sequenceClosed(Sequence sequence) {
		if (sequence == listenedSequence)
			detachSequenceListener();
	}

	public void beginSuppressRoiEvents() {
		suppressRoiEvents = true;
	}

	public void endSuppressRoiEvents() {
		suppressRoiEvents = false;
	}

	private boolean dirtyPossible() {
		return sessionT >= 0;
	}

	private void markDirty() {
		dirty = true;
	}

	private void clearDirty() {
		dirty = false;
	}

	private void clearSession() {
		entryBaseline = null;
		sessionT = -1;
		clearDirty();
	}

	private void beginSessionAtT(Experiment exp, int t) {
		sessionT = t;
		entryBaseline = exp.snapshotFlyPositionsAtT(t);
		clearDirty();
	}

	private Experiment getExperiment() {
		if (parent0 == null)
			return null;
		return (Experiment) parent0.expListComboLazy.getSelectedItem();
	}

	private int getCurrentT(Experiment exp) {
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null)
			return -1;
		Viewer v = exp.getSeqCamData().getSequence().getFirstViewer();
		if (v != null)
			return v.getPositionT();
		return exp.getSeqCamData().getCurrentFrame();
	}

	private void ensureSequenceListener(Experiment exp) {
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null)
			return;
		Sequence seq = exp.getSeqCamData().getSequence();
		if (seq == listenedSequence)
			return;
		detachSequenceListener();
		listenedSequence = seq;
		seq.addListener(this);
	}

	private void detachSequenceListener() {
		if (listenedSequence != null) {
			listenedSequence.removeListener(this);
			listenedSequence = null;
		}
	}

	private void enableFliesRectVisible() {
		if (parent0 == null || parent0.dlgExperiment == null || parent0.dlgExperiment.optionsPanel == null)
			return;
		parent0.dlgExperiment.optionsPanel.viewFlyRectCheckbox.setSelected(true);
		parent0.dlgExperiment.optionsPanel.displayROIsCategory(true, "det");
	}

	private void addFlyRoi() {
		Experiment exp = getExperiment();
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null) {
			MessageDialog.showDialog("No experiment or camera sequence.", MessageDialog.WARNING_MESSAGE);
			return;
		}
		Sequence seq = exp.getSeqCamData().getSequence();
		int t = getCurrentT(exp);
		if (t < 0)
			t = 0;
		if (sessionT != t)
			beginSessionAtT(exp, t);

		double cx;
		double cy;
		double w = DEFAULT_FLY_W;
		double h = DEFAULT_FLY_H;
		Cage cage = findSelectedCage(exp);
		if (cage != null && cage.getCageRoi2D() != null) {
			Rectangle bounds = cage.getCageRoi2D().getBounds();
			cx = bounds.getCenterX();
			cy = bounds.getCenterY();
			for (Rectangle2D r : cage.copyValidRectsAtFrame(t)) {
				if (r.getWidth() > 0 && r.getHeight() > 0) {
					w = r.getWidth();
					h = r.getHeight();
					break;
				}
			}
		} else {
			cx = seq.getWidth() / 2.0;
			cy = seq.getHeight() / 2.0;
		}

		ROI2DRectangle roi = new ROI2DRectangle(
				new Rectangle2D.Double(cx - w / 2.0, cy - h / 2.0, w, h));
		roi.setName(Cage.DET_NEW_ROI_PREFIX + DET_NEW_COUNTER.incrementAndGet());
		roi.setT(t);
		roi.setColor(FLY_ROI_COLOR);
		suppressRoiEvents = true;
		try {
			seq.addROI(roi);
			seq.setSelectedROI(roi);
		} finally {
			suppressRoiEvents = false;
		}
		markDirty();
		enableFliesRectVisible();
	}

	private Cage findSelectedCage(Experiment exp) {
		ROI2D selected = exp.getSeqCamData().getSequence().getSelectedROI2D();
		if (selected == null || selected.getName() == null)
			return null;
		String name = selected.getName();
		if (name.contains("cage") || name.contains("cell"))
			return exp.getCages().getCageFromName(name);
		return null;
	}

	private void deleteSelectedFlyRoi() {
		Experiment exp = getExperiment();
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null) {
			MessageDialog.showDialog("No experiment or camera sequence.", MessageDialog.WARNING_MESSAGE);
			return;
		}
		Sequence seq = exp.getSeqCamData().getSequence();
		ROI2D selected = seq.getSelectedROI2D();
		if (selected == null || !Cage.isFlyEditRectangleName(selected.getName())) {
			MessageDialog.showDialog("Select a yellow fly rectangle first.", MessageDialog.WARNING_MESSAGE);
			return;
		}
		int t = getCurrentT(exp);
		if (sessionT != t && t >= 0)
			beginSessionAtT(exp, t);
		suppressRoiEvents = true;
		try {
			seq.removeROI(selected);
		} finally {
			suppressRoiEvents = false;
		}
		markDirty();
	}

	private void restoreEntryBaseline() {
		Experiment exp = getExperiment();
		if (exp == null)
			return;
		int t = getCurrentT(exp);
		if (t < 0)
			return;
		if (entryBaseline == null || entryBaseline.t != t) {
			beginSessionAtT(exp, t);
			MessageDialog.showDialog("Nothing to restore (baseline refreshed for current T).",
					MessageDialog.INFORMATION_MESSAGE);
			return;
		}
		if (!dirty && positionsMatchBaseline(exp, entryBaseline)) {
			MessageDialog.showDialog("Nothing to restore.", MessageDialog.INFORMATION_MESSAGE);
			return;
		}
		applyEntryBaseline(exp);
	}

	private boolean positionsMatchBaseline(Experiment exp, FlyPositionsAtTSnapshot snap) {
		FlyPositionsAtTSnapshot current = exp.snapshotFlyPositionsAtT(snap.t);
		if (current.rectsByCageId.size() != snap.rectsByCageId.size())
			return false;
		for (Integer cageId : snap.rectsByCageId.keySet()) {
			java.util.List<Rectangle2D> a = snap.rectsByCageId.get(cageId);
			java.util.List<Rectangle2D> b = current.rectsByCageId.get(cageId);
			if (a == null || b == null || a.size() != b.size())
				return false;
			for (int i = 0; i < a.size(); i++) {
				Rectangle2D ra = a.get(i);
				Rectangle2D rb = b.get(i);
				if (Math.abs(ra.getX() - rb.getX()) > 1e-6 || Math.abs(ra.getY() - rb.getY()) > 1e-6
						|| Math.abs(ra.getWidth() - rb.getWidth()) > 1e-6
						|| Math.abs(ra.getHeight() - rb.getHeight()) > 1e-6)
					return false;
			}
		}
		return true;
	}

	private void applyEntryBaseline(Experiment exp) {
		if (entryBaseline == null)
			return;
		suppressRoiEvents = true;
		try {
			exp.restoreFlyPositionsFromSnapshot(entryBaseline);
		} finally {
			suppressRoiEvents = false;
		}
		clearDirty();
		enableFliesRectVisible();
	}

	private void validateCurrentT(boolean showDialog) {
		Experiment exp = getExperiment();
		if (exp == null || exp.getSeqCamData() == null) {
			MessageDialog.showDialog("No experiment or camera sequence.", MessageDialog.WARNING_MESSAGE);
			return;
		}
		int t = getCurrentT(exp);
		if (t < 0) {
			MessageDialog.showDialog("No current frame.", MessageDialog.WARNING_MESSAGE);
			return;
		}
		validateAtT(exp, t, showDialog);
	}

	private void validateAtT(Experiment exp, int t, boolean showDialog) {
		if (sessionT < 0)
			beginSessionAtT(exp, t);
		suppressRoiEvents = true;
		FlyEditValidateResult result;
		try {
			result = exp.validateFlyPositionsFromScreenAtT(t);
		} finally {
			suppressRoiEvents = false;
		}
		clearDirty();
		enableFliesRectVisible();
		if (showDialog) {
			String msg = result.flyCount + " flies stored in " + result.cageCountWithFlies + " cages at T=" + t + ".";
			if (result.orphanCount > 0)
				msg += "\n" + result.orphanCount + " rectangle(s) outside all cages were discarded.";
			msg += "\nUse Load/Save to write to disk.";
			MessageDialog.showDialog(msg, MessageDialog.INFORMATION_MESSAGE);
		}
	}
}
