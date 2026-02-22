package plugins.fmp.multicafe.dlg.levels;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import icy.roi.ROI;
import icy.roi.ROI2D;
import icy.sequence.Sequence;
import icy.type.geom.Polyline2D;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryGulps;
import plugins.fmp.multitools.experiment.capillary.CapillaryMeasure;
import plugins.fmp.multitools.experiment.sequence.SequenceKymos;
import plugins.fmp.multitools.tools.polyline.Level2D;
import plugins.kernel.roi.roi2d.ROI2DLine;
import plugins.kernel.roi.roi2d.ROI2DPolyLine;

public class EditLevels extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2580935598417087197L;
	private MultiCAFE parent0;
	private boolean[] isInside = null;
//	private ArrayList<ROI> 		listGulpsSelected = null;
	private JComboBox<String> roiTypeCombo = new JComboBox<String>(
			new String[] { " top level", "bottom level", "top & bottom levels", "derivative", "gulps" });
	private JButton cutAndInterpolateButton = new JButton("Cut & interpolate");
	private JButton addGulpButton = new JButton("Add gulp");
	private JButton validateGulpsButton = new JButton("Validate changes");
	private JButton cropButton = new JButton("Crop from left");
	private JButton restoreButton = new JButton("Restore");

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;
		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);
		layoutLeft.setVgap(0);

		JPanel panel1 = new JPanel(layoutLeft);
		panel1.add(new JLabel("Apply to "));
		panel1.add(roiTypeCombo);
		add(panel1);

		JPanel panelCutAdd = new JPanel(layoutLeft);
		panelCutAdd.add(cutAndInterpolateButton);
		panelCutAdd.add(addGulpButton);
		panelCutAdd.add(validateGulpsButton);
		add(panelCutAdd);

		addGulpButton.setVisible(false);
		validateGulpsButton.setVisible(false);
		updateGulpButtonsVisibility();

		JPanel panel2 = new JPanel(layoutLeft);
		panel2.add(cropButton);
		panel2.add(restoreButton);
		add(panel2);

		defineListeners();
	}

	private void updateGulpButtonsVisibility() {
		String option = (String) roiTypeCombo.getSelectedItem();
		boolean gulpsSelected = option != null && option.contains("gulp");
		addGulpButton.setVisible(gulpsSelected);
		validateGulpsButton.setVisible(gulpsSelected);
	}

	private void defineListeners() {
		roiTypeCombo.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				updateGulpButtonsVisibility();
			}
		});

		cutAndInterpolateButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					cutAndInterpolate(exp);
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

		validateGulpsButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					updateGulps(exp);
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

	List<ROI> selectGulpsWithinRoi(ROI2D roiReference, Sequence seq, int t) {
		List<ROI> allRois = seq.getROIs();
		List<ROI> listGulpsSelected = new ArrayList<ROI>();
		for (ROI roi : allRois) {
			roi.setSelected(false);
			if (roi instanceof ROI2D) {
				if (((ROI2D) roi).getT() != t)
					continue;
				if (roi.getName().contains("gulp")) {
					listGulpsSelected.add(roi);
					roi.setSelected(true);
				}
			}
		}
		return listGulpsSelected;
	}

	void deleteGulps(SequenceKymos seqKymos, List<ROI> listGulpsSelected) {
		Sequence seq = seqKymos.getSequence();
		if (seq == null || listGulpsSelected == null)
			return;
		for (ROI roi : listGulpsSelected)
			seq.removeROI(roi);
	}

	void addGulp(Experiment exp) {
		SequenceKymos seqKymos = exp.getSeqKymos();
		int t = seqKymos.getSequence().getFirstViewer().getPositionT();
		Capillary cap = exp.getCapillaries().getList().size() > t ? exp.getCapillaries().getList().get(t) : null;
		if (cap == null) {
			JOptionPane.showMessageDialog(this, "Capillary not found for current frame.");
			return;
		}
		seqKymos.transferKymosRoi_atT_ToCapillaries_Measures(t, cap);

		ROI2D roi = seqKymos.getSequence().getSelectedROI2D();
		if (roi == null) {
			JOptionPane.showMessageDialog(this, "Select a line ROI on the kymograph.");
			return;
		}
		if (!(roi instanceof ROI2DLine)) {
			JOptionPane.showMessageDialog(this, "Select a line ROI (not a polygon or other shape).");
			return;
		}

		Line2D line = ((ROI2DLine) roi).getLine();
		double x1 = line.getX1(), y1 = line.getY1(), x2 = line.getX2(), y2 = line.getY2();
		int xPixel = (int) Math.round((x1 + x2) / 2);
		double yBottom = Math.min(y1, y2);
		double yTop = Math.max(y1, y2);
		double amplitude = yTop - yBottom;
		if (amplitude <= 0) {
			JOptionPane.showMessageDialog(this, "Line has no vertical extent (draw a vertical or diagonal line).");
			return;
		}

		int npoints = cap.getTopLevel() != null ? cap.getTopLevel().getNPoints() : 0;
		if (npoints <= 0) {
			JOptionPane.showMessageDialog(this, "No level data for this capillary.");
			return;
		}
		if (xPixel < 0 || xPixel >= npoints) {
			JOptionPane.showMessageDialog(this, "Line position is outside capillary range (0-" + (npoints - 1) + ").");
			return;
		}

		CapillaryGulps gulps = cap.getGulps();
		if (gulps == null)
			return;
		gulps.addGulpFromVerticalSegment(xPixel, yBottom, yTop, npoints);

		String prefix = cap.getKymographPrefix();
		if (prefix == null)
			prefix = "";
		List<Point2D> points = new ArrayList<>(2);
		points.add(new Point2D.Double(xPixel, yBottom));
		points.add(new Point2D.Double(xPixel, yTop));
		ROI2DPolyLine gulpRoi = new ROI2DPolyLine(points);
		gulpRoi.setName(prefix + "_gulp" + String.format("%07d", xPixel));
		gulpRoi.setColor(Color.red);
		gulpRoi.setT(t);
		seqKymos.getSequence().addROI(gulpRoi);
		seqKymos.getSequence().roiChanged(gulpRoi);
	}

	void updateGulps(Experiment exp) {
		SequenceKymos seqKymos = exp.getSeqKymos();
		int t = seqKymos.getSequence().getFirstViewer().getPositionT();
		Capillary cap = exp.getCapillaries().getList().size() > t ? exp.getCapillaries().getList().get(t) : null;
		if (cap == null) {
			JOptionPane.showMessageDialog(this, "Capillary not found for current frame.");
			return;
		}
		Sequence seq = seqKymos.getSequence();
		List<ROI2D> allRois = seq.getROI2Ds();
		List<ROI2D> toRemove = new ArrayList<>();
		for (ROI2D r : allRois) {
			if (r.getT() == t && r.getName() != null && r.getName().contains("gulp"))
				toRemove.add(r);
		}
		for (ROI2D r : toRemove)
			seq.removeROI(r);
		List<ROI2D> fromCap = cap.transferMeasuresToROIs(seqKymos.getImagesList());
		if (fromCap == null)
			return;
		List<ROI2D> gulpRois = new ArrayList<>();
		for (ROI2D r : fromCap) {
			if (r.getName() != null && r.getName().contains("gulp")) {
				r.setT(t);
				gulpRois.add(r);
			}
		}
		if (!gulpRois.isEmpty()) {
			seq.addROIs(gulpRois, false);
			for (ROI2D r : gulpRois)
				seq.roiChanged(r);
		}
	}

	void cutAndInterpolate(Experiment exp) {
		SequenceKymos seqKymos = exp.getSeqKymos();
		int t = seqKymos.getSequence().getFirstViewer().getPositionT();
		ROI2D roi = seqKymos.getSequence().getSelectedROI2D();
		if (roi == null)
			return;

		if (exp.getCapillaries() == null || exp.getCapillaries().getList().size() < 1) {
			System.out.println("capillaries not defined!");
			return;
		}

		Capillary cap = exp.getCapillaries().getList().get(t);
		if (cap == null) {
			System.out.println("capillary not found!");
			return;
		}

		seqKymos.transferKymosRoi_atT_ToCapillaries_Measures(t, cap);

		String optionSelected = (String) roiTypeCombo.getSelectedItem();
		if (optionSelected.contains("gulp")) {
			List<ROI> listGulpsSelected = selectGulpsWithinRoi(roi, seqKymos.getSequence(), seqKymos.getCurrentFrame());
			deleteGulps(seqKymos, listGulpsSelected);
			seqKymos.removeROIsPolylineAtT(t);
			List<ROI2D> listOfRois = cap.transferMeasuresToROIs();
			seqKymos.getSequence().addROIs(listOfRois, false);
			for (ROI lroi : listOfRois)
				seqKymos.getSequence().roiChanged(lroi);
		} else {
			if (optionSelected.contains("top"))
				removeAndUpdate(seqKymos, cap, cap.getTopLevel(), roi);
			if (optionSelected.contains("bottom"))
				removeAndUpdate(seqKymos, cap, cap.getBottomLevel(), roi);
			if (optionSelected.contains("deriv"))
				removeAndUpdate(seqKymos, cap, cap.getDerivative(), roi);
		}
	}

	private void removeAndUpdate(SequenceKymos seqKymos, Capillary cap, CapillaryMeasure caplimits, ROI2D roi) {
		removeMeasuresEnclosedInRoi(caplimits, roi);
		seqKymos.updateROIFromCapillaryMeasure(cap, caplimits);
	}

	void removeMeasuresEnclosedInRoi(CapillaryMeasure caplimits, ROI2D roi) {
		Polyline2D polyline = caplimits.polylineLevel;
		int npointsOutside = polyline.npoints - getPointsWithinROI(polyline, roi);
		if (npointsOutside > 0) {
			double[] xpoints = new double[npointsOutside];
			double[] ypoints = new double[npointsOutside];
			int index = 0;
			for (int i = 0; i < polyline.npoints; i++) {
				if (!isInside[i]) {
					xpoints[index] = polyline.xpoints[i];
					ypoints[index] = polyline.ypoints[i];
					index++;
				}
			}
			caplimits.polylineLevel = new Level2D(xpoints, ypoints, npointsOutside);
		} else {
			caplimits.polylineLevel = null;
		}
	}

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
