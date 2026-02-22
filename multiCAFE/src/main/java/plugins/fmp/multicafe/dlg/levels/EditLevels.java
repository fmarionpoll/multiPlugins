package plugins.fmp.multicafe.dlg.levels;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
import plugins.fmp.multitools.experiment.sequence.SequenceKymos;
import plugins.fmp.multitools.tools.polyline.Level2D;
import plugins.kernel.roi.roi2d.ROI2DLine;

public class EditLevels extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2580935598417087197L;
	private MultiCAFE parent0;
	private boolean[] isInside = null;
	private JComboBox<String> roiTypeCombo = new JComboBox<String>(
			new String[] { " top level", "bottom level", "top & bottom levels", "derivative", "gulps" });
	private JButton cutAndInterpolateButton = new JButton("Cut & interpolate");
	private JButton addGulpButton = new JButton("Add gulp");
	private JButton validateGulpsButton = new JButton("Validate gulps");
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
		if (gulpsSelected) {
			cutAndInterpolateButton.setText("Delete inside polygon");
			addGulpButton.setVisible(true);
			validateGulpsButton.setVisible(true);
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null)
				attachGulpRoiListeners(exp);
			refreshValidateButtonState();
		} else {
			removeGulpRoiListeners();
			cutAndInterpolateButton.setText("Cut & interpolate");
			addGulpButton.setVisible(false);
			validateGulpsButton.setVisible(false);
		}
	}

	private void refreshValidateButtonState() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null) {
			validateGulpsButton.setEnabled(false);
			return;
		}
		SequenceKymos seqKymos = exp.getSeqKymos();
		if (seqKymos == null || seqKymos.getSequence() == null) {
			validateGulpsButton.setEnabled(false);
			return;
		}
		int t = seqKymos.getSequence().getFirstViewer().getPositionT();
		Capillary cap = seqKymos.getCapillaryForFrame(t, exp.getCapillaries());
		validateGulpsButton.setEnabled(cap != null && cap.isGulpMeasuresDirty());
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
				refreshValidateButtonState();
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

		cutAndInterpolateButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp == null)
					return;
				String option = (String) roiTypeCombo.getSelectedItem();
				if (option != null && option.contains("gulp"))
					deleteGulpsInRegion(exp);
				else
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
					validateGulps(exp);
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

	void deleteGulpsInRegion(Experiment exp) {
		SequenceKymos seqKymos = exp.getSeqKymos();
		if (seqKymos == null || seqKymos.getSequence() == null)
			return;
		int t = seqKymos.getSequence().getFirstViewer().getPositionT();
		ROI2D roi = seqKymos.getSequence().getSelectedROI2D();
		if (roi == null) {
			JOptionPane.showMessageDialog(this, "Select a polygon ROI to define the region.");
			return;
		}
		Capillary cap = seqKymos.getCapillaryForFrame(t, exp.getCapillaries());
		if (cap == null)
			return;
		List<ROI> list = getGulpsWithinRoi(roi, seqKymos.getSequence(), t);
		deleteGulps(seqKymos, list);
		cap.setGulpMeasuresDirty(true);
		refreshValidateButtonState();
	}

	private boolean hasGulpAddedAt(Sequence seq, int t, double cx, double cy) {
		List<ROI2D> rois = seq.getROI2Ds();
		for (ROI2D r : rois) {
			if (r.getT() != t || r.getName() == null || !r.getName().contains("_gulp_added_"))
				continue;
			if (r.contains(cx, cy))
				return true;
		}
		return false;
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
		int width = seq.getWidth();
		int height = seq.getHeight();
		String prefix = cap.getKymographPrefix();
		if (prefix == null)
			prefix = "";
		String prefix2 = prefix.length() >= 2 ? prefix.substring(0, 2) : prefix;
		if (t != gulpAddedFrame) {
			gulpAddedFrame = t;
			gulpAddedCounter = 0;
		}
		gulpAddedCounter++;
		int cx = width / 2;
		int cy = height / 2;
		int tryCx = cx;
		int tryCy = cy;
		for (int attempt = 0; attempt < 10; attempt++) {
			tryCx = cx - 2 * attempt;
			tryCy = cy + 2 * attempt;
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
		refreshValidateButtonState();
	}

	void validateGulps(Experiment exp) {
		SequenceKymos seqKymos = exp.getSeqKymos();
		if (seqKymos == null || seqKymos.getSequence() == null)
			return;
		int t = seqKymos.getSequence().getFirstViewer().getPositionT();
		Capillary cap = seqKymos.getCapillaryForFrame(t, exp.getCapillaries());
		if (cap == null) {
			JOptionPane.showMessageDialog(this, "Capillary not found for current frame.");
			return;
		}
		seqKymos.validateGulpROIsAtT(cap, t);
		attachGulpRoiListeners(exp);
		refreshValidateButtonState();
	}

	void cutAndInterpolate(Experiment exp) {
		SequenceKymos seqKymos = exp.getSeqKymos();
		int t = seqKymos.getSequence().getFirstViewer().getPositionT();
		ROI2D roi = seqKymos.getSequence().getSelectedROI2D();
		if (roi == null)
			return;
		Capillary cap = seqKymos.getCapillaryForFrame(t, exp.getCapillaries());
		if (cap == null)
			return;
		seqKymos.transferKymosRoi_atT_ToCapillaries_Measures(t, cap);
		String optionSelected = (String) roiTypeCombo.getSelectedItem();
		if (optionSelected.contains("top"))
			removeAndUpdate(seqKymos, cap, cap.getTopLevel(), roi);
		if (optionSelected.contains("bottom"))
			removeAndUpdate(seqKymos, cap, cap.getBottomLevel(), roi);
		if (optionSelected.contains("deriv"))
			removeAndUpdate(seqKymos, cap, cap.getDerivative(), roi);
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
