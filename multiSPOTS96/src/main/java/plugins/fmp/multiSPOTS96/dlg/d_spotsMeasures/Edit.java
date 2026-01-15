package plugins.fmp.multiSPOTS96.dlg.d_spotsMeasures;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import icy.util.StringUtil;
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multitools.experiment.Experiment;

public class Edit extends JPanel implements PropertyChangeListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2580935598417087197L;
	private JComboBox<String> roiTypeCombo = new JComboBox<String>(
			new String[] { "sum", "clean", "fly present/absent" });
	private JButton cutAndInterpolateButton = new JButton("Cut & interpolate");
	private JButton compensateButton = new JButton("Compensate (poop deposit)");
	private JComboBox<String> directionCombo = new JComboBox<String>(new String[] { "up", "down" });
	private String buildMedianString = "Build median";
	private JButton buildMedianButton = new JButton(buildMedianString);

	private MultiSPOTS96 parent0 = null;

	void init(GridLayout capLayout, MultiSPOTS96 parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;
		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);
		layoutLeft.setVgap(0);

		JPanel panel1 = new JPanel(layoutLeft);
		panel1.add(cutAndInterpolateButton);
		panel1.add(new JLabel("Apply to ", SwingConstants.LEFT));
		panel1.add(roiTypeCombo);
		add(panel1);

		JPanel panel2 = new JPanel(layoutLeft);
		panel2.add(compensateButton);
		panel2.add(directionCombo);
		add(panel2);

		JPanel panel3 = new JPanel(layoutLeft);
		add(panel3);

		roiTypeCombo.setSelectedIndex(1);
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

		compensateButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					compensate(exp);
			}
		});

	}

	void cutAndInterpolate(Experiment exp) {
//		SequenceKymos seqKymos = exp.seqKymos;
//		ROI2D roiRect = seqKymos.getSequence().getSelectedROI2D();
//		if (roiRect == null)
//			return;
//
//		int t = seqKymos.getSequence().getFirstViewer().getPositionT();
//		Spot spot = exp.getCages().getSpotAtGlobalIndex(t);
//		String optionSelected = (String) roiTypeCombo.getSelectedItem();
//		if (optionSelected.contains("sum"))
//			removeAndUpdate(seqKymos, spot, spot.getSum(), roiRect);
//		else if (optionSelected.contains("clean"))
//			removeAndUpdate(seqKymos, spot, spot.getSumClean(), roiRect);
//		else if (optionSelected.contains("fly"))
//			removeAndUpdate(seqKymos, spot, spot.getFlyPresent(), roiRect);
	}

	void compensate(Experiment exp) {
//		SequenceKymos seqKymos = exp.seqKymos;
//		ROI2D roiRect = seqKymos.getSequence().getSelectedROI2D();
//		if (roiRect == null)
//			return;
//
//		int t = seqKymos.getSequence().getFirstViewer().getPositionT();
//		Spot spot = exp.getCages().getSpotAtGlobalIndex(t);
//		String optionSelected = (String) roiTypeCombo.getSelectedItem();
//		if (optionSelected.contains("sum"))
//			compensateAndUpdate(seqKymos, spot, spot.getSum(), roiRect);
//		else if (optionSelected.contains("clean"))
//			compensateAndUpdate(seqKymos, spot, spot.getSumClean(), roiRect);
//		else if (optionSelected.contains("fly"))
//			compensateAndUpdate(seqKymos, spot, spot.getFlyPresent(), roiRect);
	}

//	private void removeAndUpdate(SequenceKymos seqKymos, Spot spot, SpotMeasure spotMeasure, ROI2D roi) {
//		spotMeasure.getSpotLevel2D().cutAndInterpolatePointsEnclosedInSelectedRoi(roi);
//		spotMeasure.getSpotLevel2D().transferROItoLevel2D();
//	}
//
//	private void compensateAndUpdate(SequenceKymos seqKymos, Spot spot, SpotMeasure spotMeasure, ROI2D roi) {
//		boolean bAdd = (directionCombo.getSelectedIndex() == 0);
//		spotMeasure.getSpotLevel2D().compensateOffsetUsingSelectedRoi(roi, bAdd);
//		spotMeasure.getSpotLevel2D().transferROItoLevel2D();
//	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (StringUtil.equals("thread_ended", evt.getPropertyName())) {
			buildMedianButton.setText(buildMedianString);
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null) {
				exp.load_spots_description_and_measures();
				parent0.dlgMeasure.tabCharts.displayChartPanels(exp);
			}
		}
	}

}
