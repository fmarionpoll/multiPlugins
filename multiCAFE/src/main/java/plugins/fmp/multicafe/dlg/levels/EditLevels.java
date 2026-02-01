package plugins.fmp.multicafe.dlg.levels;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import icy.gui.util.GuiUtil;
import icy.roi.ROI;
import icy.roi.ROI2D;
import icy.sequence.Sequence;
import icy.type.geom.Polyline2D;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryMeasure;
import plugins.fmp.multitools.experiment.sequence.SequenceKymos;
import plugins.fmp.multitools.tools.polyline.Level2D;

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
	private JButton cropButton = new JButton("Crop from left");
	private JButton restoreButton = new JButton("Restore");

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		JPanel panel1 = new JPanel();
		panel1.setLayout(new BorderLayout());
		panel1.add(new JLabel("Apply to ", SwingConstants.LEFT), BorderLayout.WEST);
		panel1.add(roiTypeCombo, BorderLayout.CENTER);

		add(GuiUtil.besidesPanel(new JLabel(" "), panel1));
		add(GuiUtil.besidesPanel(new JLabel(" "), cutAndInterpolateButton));

		JPanel panel2 = new JPanel();
		panel2.setLayout(new BorderLayout());
		panel2.add(cropButton, BorderLayout.CENTER);
		panel2.add(restoreButton, BorderLayout.EAST);
		add(GuiUtil.besidesPanel(new JLabel(" "), panel2));

		defineListeners();
	}

	private void defineListeners() {
		cutAndInterpolateButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					cutAndInterpolate(exp);
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
