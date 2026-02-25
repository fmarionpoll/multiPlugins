package plugins.fmp.multicafe.dlg.levels;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import icy.canvas.IcyCanvas;
import icy.canvas.IcyCanvas2D;
import icy.gui.viewer.Viewer;
import icy.roi.ROI;
import icy.roi.ROI2D;
import icy.roi.ROIEvent;
import icy.roi.ROIListener;
import icy.sequence.Sequence;
import icy.type.geom.Polyline2D;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryMeasure;
import plugins.fmp.multitools.experiment.capillary.MeasureEditTarget;
import plugins.fmp.multitools.experiment.sequence.SequenceKymos;
import plugins.fmp.multitools.tools.polyline.Level2D;
import plugins.kernel.roi.roi2d.ROI2DLine;

public class EditLevels extends JPanel {

	private static final String[] ROI_TYPE_OPTIONS = new String[] {
			" top level", "bottom level", "top & bottom levels", "derivative", "gulps" };

	/**
	 * 
	 */
	private static final long serialVersionUID = 2580935598417087197L;
	private MultiCAFE parent0;
	private boolean[] isInside = null;
	private JComboBox<String> roiTypeCombo = new JComboBox<String>(ROI_TYPE_OPTIONS);
	String textForGulps = "Delete gulps inside rectangle/polygon";
	String textForLevels = "Cut points within rectangle/polygon";
	private JButton cutButton = new JButton(textForLevels);
	private JButton addGulpButton = new JButton("Add gulp");
	private JButton validateChangesButton = new JButton("Validate changes");
	private JButton cropButton = new JButton("Crop from left");
	private JButton restoreButton = new JButton("Restore");

	private int gulpAddedCounter = 0;
	private int gulpAddedFrame = -1;
	private final Map<ROI, ROIListener> gulpRoiListeners = new HashMap<>();

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;
		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);
		layoutLeft.setVgap(0);

		JPanel panel0 = new JPanel(layoutLeft);
		panel0.add(new JLabel("Apply to "));
		panel0.add(roiTypeCombo);
		panel0.add(validateChangesButton);
		add(panel0);

		JPanel panel1 = new JPanel(layoutLeft);
		panel1.add(cutButton);
		panel1.add(addGulpButton);

		add(panel1);

		addGulpButton.setVisible(false);
		updateGulpButtonsVisibility();

		JPanel panel2 = new JPanel(layoutLeft);
		add(panel2);

		JPanel panel3 = new JPanel(layoutLeft);
		panel3.add(cropButton);
		panel3.add(restoreButton);
		add(panel3);

		defineListeners();
	}

	private MeasureEditTarget getMeasureEditTarget() {
		int idx = roiTypeCombo.getSelectedIndex();
		if (idx < 0 || idx >= MeasureEditTarget.values().length)
			return null;
		return MeasureEditTarget.values()[idx];
	}

	private static final class EditContext {
		final SequenceKymos seqKymos;
		final Capillary cap;
		final ROI2D selectedROI;

		EditContext(SequenceKymos seqKymos, Capillary cap, ROI2D selectedROI) {
			this.seqKymos = seqKymos;
			this.cap = cap;
			this.selectedROI = selectedROI;
		}

		int getCurrentFrame() {
			if (seqKymos == null || seqKymos.getSequence() == null)
				return -1;
			Viewer v = seqKymos.getSequence().getFirstViewer();
			return v != null ? v.getPositionT() : -1;
		}
	}

	private EditContext getEditContext(Experiment exp, boolean requireSelectedROI) {
		if (exp == null)
			return null;
		SequenceKymos seqKymos = exp.getSeqKymos();
		if (seqKymos == null || seqKymos.getSequence() == null)
			return null;
		int t = seqKymos.getSequence().getFirstViewer().getPositionT();
		Capillary cap = seqKymos.getCapillaryForFrame(t, exp.getCapillaries());
		if (cap == null) {
			JOptionPane.showMessageDialog(this, "Capillary not found for current frame.");
			return null;
		}
		ROI2D selectedROI = requireSelectedROI ? seqKymos.getSequence().getSelectedROI2D() : null;
		if (requireSelectedROI && selectedROI == null) {
			JOptionPane.showMessageDialog(this, "Select a polygon ROI to define a region to be cleared out");
			return null;
		}
		return new EditContext(seqKymos, cap, selectedROI);
	}

	private void updateGulpButtonsVisibility() {
		boolean gulpsSelected = getMeasureEditTarget() == MeasureEditTarget.GULPS;
		if (gulpsSelected) {
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null)
				attachGulpRoiListeners(exp);
			cutButton.setText(textForGulps);
			addGulpButton.setVisible(true);
		} else {
			removeGulpRoiListeners();
			cutButton.setText(textForLevels);
			addGulpButton.setVisible(false);
		}
	}

	private void removeGulpRoiListeners() {
		for (Map.Entry<ROI, ROIListener> e : gulpRoiListeners.entrySet()) {
			e.getKey().removeListener(e.getValue());
		}
		gulpRoiListeners.clear();
	}

	private void attachGulpRoiListeners(Experiment exp) {
		removeGulpRoiListeners();
		SequenceKymos seqKymos = exp.getSeqKymos();
		if (seqKymos == null || seqKymos.getSequence() == null)
			return;
		int t = seqKymos.getSequence().getFirstViewer().getPositionT();
		Capillary cap = seqKymos.getCapillaryForFrame(t, exp.getCapillaries());
		if (cap == null)
			return;
		Capillary capRef = cap;
		for (ROI2D r : seqKymos.getSequence().getROI2Ds()) {
			if (r.getT() != t || r.getName() == null || !r.getName().contains("gulp"))
				continue;
			ROIListener listener = (ROIEvent evt) -> {
				capRef.setGulpMeasuresDirty(true);
			};
			r.addListener(listener);
			gulpRoiListeners.put(r, listener);
		}
	}

	private void defineListeners() {
		roiTypeCombo.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				updateGulpButtonsVisibility();
			}
		});

		cutButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp == null)
					return;

				cut(exp);
			}
		});

		addGulpButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					addGulp(exp);
			}
		});

		validateChangesButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					validateROIs(exp);
			}
		});

		cropButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					cropPointsToLeftLimit(exp);
			}
		});

		restoreButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					restoreCroppedPoints(exp);
			}
		});
	}

	void cropPointsToLeftLimit(Experiment exp) {
		SequenceKymos seqKymos = exp.getSeqKymos();
		int t = seqKymos.getCurrentFrame();
		ROI2D roiRef = seqKymos.getSequence().getSelectedROI2D();
		if (roiRef == null)
			return;

		Capillary cap = exp.getCapillaries().getList().get(t);
		seqKymos.transferKymosRoisToCapillaries_Measures(exp.getCapillaries());

		int lastX = findLastXLeftOfRoi(cap, roiRef);
		cap.cropMeasuresToNPoints(lastX + 1);

		seqKymos.updateROIFromCapillaryMeasure(cap, cap.getTopLevel());
		seqKymos.updateROIFromCapillaryMeasure(cap, cap.getBottomLevel());
		seqKymos.updateROIFromCapillaryMeasure(cap, cap.getDerivative());
	}

	int findLastXLeftOfRoi(Capillary cap, ROI2D roiRef) {
		int lastX = -1;
		Rectangle2D rectRef = roiRef.getBounds2D();
		double xleft = rectRef.getX();

		Polyline2D polyline = cap.getTopLevel().polylineLevel;
		for (int i = 0; i < polyline.npoints; i++) {
			if (polyline.xpoints[i] < xleft)
				continue;
			lastX = i - 1;
			break;
		}
		return lastX;
	}

	void restoreCroppedPoints(Experiment exp) {
		SequenceKymos seqKymos = exp.getSeqKymos();
		int t = seqKymos.getCurrentFrame();
		Capillary cap = exp.getCapillaries().getList().get(t);
		cap.restoreClippedMeasures();

		seqKymos.updateROIFromCapillaryMeasure(cap, cap.getTopLevel());
		seqKymos.updateROIFromCapillaryMeasure(cap, cap.getBottomLevel());
		seqKymos.updateROIFromCapillaryMeasure(cap, cap.getDerivative());
	}

	List<ROI> getGulpsWithinRoi(ROI2D roiReference, Sequence seq, int t) {
		List<ROI> allRois = seq.getROIs();
		List<ROI> listGulpsSelected = new ArrayList<ROI>();
		for (ROI roi : allRois) {
			if (roi instanceof ROI2D) {
				if (((ROI2D) roi).getT() != t)
					continue;
				if (roi.getName().contains("gulp")) {
					try {
						if (roiReference.contains(roi)) {
							listGulpsSelected.add(roi);
						}
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
		return listGulpsSelected;
	}

	void deleteGulps(SequenceKymos seqKymos, List<ROI> listGulpsSelected) {
		Sequence seq = seqKymos.getSequence();
		if (seq == null || listGulpsSelected == null)
			return;
		seq.beginUpdate();
		for (ROI roi : listGulpsSelected)
			seq.removeROI(roi);
		seq.endUpdate();
	}

	private static final double GULP_SAME_PLACE_TOLERANCE = 2.0;

	private boolean hasGulpAddedAt(Sequence seq, int t, double cx, double cy) {
		List<ROI2D> rois = seq.getROI2Ds();
		for (ROI2D r : rois) {
			if (r.getT() != t || r.getName() == null || !r.getName().contains("_gulp_added_"))
				continue;
			if (r instanceof ROI2DLine) {
				if (pointNearLineSegment((ROI2DLine) r, cx, cy))
					return true;
			} else if (r.contains(cx, cy)) {
				return true;
			}
		}
		return false;
	}

	private boolean pointNearLineSegment(ROI2DLine lineRoi, double px, double py) {
		Line2D line = lineRoi.getLine();
		double x1 = line.getX1(), y1 = line.getY1(), x2 = line.getX2(), y2 = line.getY2();
		double dx = x2 - x1, dy = y2 - y1;
		double len2 = dx * dx + dy * dy;
		double t;
		if (len2 <= 0)
			t = 0;
		else {
			t = ((px - x1) * dx + (py - y1) * dy) / len2;
			t = Math.max(0, Math.min(1, t));
		}
		double qx = x1 + t * dx, qy = y1 + t * dy;
		return Math.hypot(px - qx, py - qy) <= GULP_SAME_PLACE_TOLERANCE;
	}

	void addGulp(Experiment exp) {
		SequenceKymos seqKymos = exp.getSeqKymos();
		if (seqKymos == null || seqKymos.getSequence() == null)
			return;
		int t = seqKymos.getSequence().getFirstViewer().getPositionT();
		Capillary cap = seqKymos.getCapillaryForFrame(t, exp.getCapillaries());
		if (cap == null) {
			JOptionPane.showMessageDialog(this, "Capillary not found for current frame.");
			return;
		}
		Sequence seq = seqKymos.getSequence();
		int cx = seq.getWidth() / 2;
		int cy = 10; // seq.getHeight() / 2;
		Viewer v = seq.getFirstViewer();
		if (v != null) {
			IcyCanvas canvas = v.getCanvas();
			if (canvas instanceof IcyCanvas2D) {
				Rectangle2D rect = ((IcyCanvas2D) canvas).getImageVisibleRect();

				cx = (int) (rect.getX() + rect.getWidth() / 2);
				cy = (int) (rect.getY() + rect.getHeight() / 2);
			}
		}

		String prefix = cap.getKymographPrefix();
		if (prefix == null)
			prefix = "";
		String prefix2 = prefix.length() >= 2 ? prefix.substring(0, 2) : prefix;
		if (t != gulpAddedFrame) {
			gulpAddedFrame = t;
			gulpAddedCounter = 0;
		}
		gulpAddedCounter++;

		int tryCx = cx;
		int tryCy = cy;
		for (int attempt = 0; attempt < 10; attempt++) {
			tryCx = cx + 5 * attempt;
			tryCy = cy + 5 * attempt;
			if (!hasGulpAddedAt(seq, t, tryCx, tryCy))
				break;
		}
		ROI2DLine lineRoi = new ROI2DLine(tryCx, tryCy - 5, tryCx, tryCy + 5);
		lineRoi.setName(prefix2 + "_gulp_added_" + gulpAddedCounter);
		lineRoi.setColor(Color.green);
		lineRoi.setT(t);
		seq.addROI(lineRoi);
		lineRoi.setSelected(true);
		seq.roiChanged(lineRoi);
		cap.setGulpMeasuresDirty(true);
		attachGulpRoiListeners(exp);
	}

	void validateROIs(Experiment exp) {
		EditContext ctx = getEditContext(exp, false);
		if (ctx == null)
			return;
		MeasureEditTarget target = getMeasureEditTarget();
		if (target == null)
			return;
		switch (target) {
		case GULPS:
			ctx.seqKymos.validateGulpROIsAtT(ctx.cap);
			attachGulpRoiListeners(exp);
			break;
		default:
			ctx.seqKymos.validateLinearROIsAtT(ctx.cap);
			break;
		}
		Viewer v = ctx.seqKymos.getSequence().getFirstViewer();
		if (v != null)
			parent0.paneKymos.tabIntervals.applyCentralViewOptionsToKymosViewer(v);
	}

	void cut(Experiment exp) {
		EditContext ctx = getEditContext(exp, true);
		if (ctx == null)
			return;
		MeasureEditTarget target = getMeasureEditTarget();
		if (target == null)
			return;
		int t = ctx.getCurrentFrame();
		switch (target) {
		case GULPS:
			List<ROI> list = getGulpsWithinRoi(ctx.selectedROI, ctx.seqKymos.getSequence(), t);
			deleteGulps(ctx.seqKymos, list);
			ctx.cap.setGulpMeasuresDirty(true);
			break;
		default:
			ctx.seqKymos.transferKymosRoi_at_T_To_Capillaries_Measures(ctx.cap);
			if (target == MeasureEditTarget.TOP_LEVEL || target == MeasureEditTarget.TOP_AND_BOTTOM)
				cutAndUpdate(ctx.seqKymos, ctx.cap, ctx.cap.getTopLevel(), ctx.selectedROI);
			if (target == MeasureEditTarget.BOTTOM_LEVEL || target == MeasureEditTarget.TOP_AND_BOTTOM)
				cutAndUpdate(ctx.seqKymos, ctx.cap, ctx.cap.getBottomLevel(), ctx.selectedROI);
			if (target == MeasureEditTarget.DERIVATIVE)
				cutAndUpdate(ctx.seqKymos, ctx.cap, ctx.cap.getDerivative(), ctx.selectedROI);
			break;
		}
	}

	private void cutAndUpdate(SequenceKymos seqKymos, Capillary cap, CapillaryMeasure caplimits, ROI2D roiRemovedArea) {
		removeMeasuresEnclosedInROI(caplimits, roiRemovedArea);
		seqKymos.updateROIFromCapillaryMeasure(cap, caplimits);
	}

	void removeMeasuresEnclosedInROI(CapillaryMeasure caplimits, ROI2D roiRemovedArea) {
		Level2D level2D = caplimits.polylineLevel;
		if (level2D == null || level2D.npoints == 0)
			return;

		List<Double> keepX = new ArrayList<>(level2D.npoints);
		List<Double> keepY = new ArrayList<>(level2D.npoints);
		for (int i = 0; i < level2D.npoints; i++) {
			double x = level2D.xpoints[i];
			double y = level2D.ypoints[i];
			if (!roiRemovedArea.contains(x, y)) {
				keepX.add(x);
				keepY.add(y);
			}
		}

		int nKeep = keepX.size();
		if (nKeep == 0) {
			caplimits.polylineLevel = new Level2D(
					new double[] { level2D.xpoints[0], level2D.xpoints[level2D.npoints - 1] },
					new double[] { level2D.ypoints[0], level2D.ypoints[level2D.npoints - 1] }, 2);
			return;
		}
		if (nKeep == level2D.npoints)
			return;

		double[] newX = new double[nKeep];
		double[] newY = new double[nKeep];
		for (int i = 0; i < nKeep; i++) {
			newX[i] = keepX.get(i);
			newY[i] = keepY.get(i);
		}
		caplimits.polylineLevel = new Level2D(newX, newY, nKeep);
	}

	/*
	 * 
	 * void removeMeasuresEnclosedInRoi(CapillaryMeasure caplimits, ROI2D roi) {
	 * Polyline2D polyline = caplimits.polylineLevel; if (polyline == null ||
	 * polyline.npoints == 0) return; Rectangle2D rect = roi.getBounds2D(); double
	 * xLeft = rect.getX(); double xRight = rect.getMaxX(); int iLeft = -1; for (int
	 * i = 0; i < polyline.npoints; i++) { if (polyline.xpoints[i] >= xLeft) { iLeft
	 * = i; break; } } int iRight = -1; for (int i = polyline.npoints - 1; i >= 0;
	 * i--) { if (polyline.xpoints[i] <= xRight) { iRight = i; break; } } if (iLeft
	 * < 0 || iRight < 0 || iRight <= iLeft) return; double y1 =
	 * polyline.ypoints[iLeft]; double y2 = polyline.ypoints[iRight]; double denom =
	 * iRight - iLeft; for (int i = iLeft; i <= iRight; i++) { polyline.ypoints[i] =
	 * y1 + (y2 - y1) * (i - iLeft) / denom; } }
	 */

	int getPointsWithinROI(Polyline2D polyline, ROI2D roi) {
		isInside = new boolean[polyline.npoints];
		int npointsInside = 0;
		for (int i = 0; i < polyline.npoints; i++) {
			isInside[i] = (roi.contains(polyline.xpoints[i], polyline.ypoints[i]));
			npointsInside += isInside[i] ? 1 : 0;
		}
		return npointsInside;
	}

}
