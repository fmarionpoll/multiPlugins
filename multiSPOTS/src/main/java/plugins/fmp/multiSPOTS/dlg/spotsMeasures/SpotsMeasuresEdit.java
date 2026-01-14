package plugins.fmp.multiSPOTS.dlg.spotsMeasures;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import icy.roi.ROI2D;
import icy.util.StringUtil;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multiSPOTS.experiment.Experiment;
import plugins.fmp.multiSPOTS.experiment.SequenceKymos;
import plugins.fmp.multiSPOTS.experiment.spots.Spot;
import plugins.fmp.multiSPOTS.experiment.spots.SpotMeasure;
import plugins.fmp.multiSPOTS.series.BuildMedianFromSpotMeasure;
import plugins.fmp.multiSPOTS.series.BuildSeriesOptions;

public class SpotsMeasuresEdit extends JPanel implements PropertyChangeListener {
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
	private JCheckBox allSeriesCheckBox = new JCheckBox("ALL (current to last)", false);
	private BuildMedianFromSpotMeasure threadbuildMedian = null;
	private MultiSPOTS parent0 = null;

	void init(GridLayout capLayout, MultiSPOTS parent0) {
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
		panel3.add(buildMedianButton);
		panel3.add(allSeriesCheckBox);
		add(panel3);

		roiTypeCombo.setSelectedIndex(1);
		defineListeners();
	}

	private void defineListeners() {
		cutAndInterpolateButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
				if (exp != null)
					cutAndInterpolate(exp);
			}
		});

		compensateButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
				if (exp != null)
					compensate(exp);
			}
		});

		buildMedianButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				if (buildMedianButton.getText().equals(buildMedianString))
					startDetection();
				else
					stopDetection();
			}
		});
	}

	void startDetection() {
		Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
		if (exp != null) {
			threadbuildMedian = new BuildMedianFromSpotMeasure();
			threadbuildMedian.options = initDetectOptions(exp);
			threadbuildMedian.addPropertyChangeListener(this);
			threadbuildMedian.execute();
			buildMedianButton.setText("STOP");
		}
	}

	private void stopDetection() {
		if (threadbuildMedian != null && !threadbuildMedian.stopFlag)
			threadbuildMedian.stopFlag = true;
	}

	private BuildSeriesOptions initDetectOptions(Experiment exp) {
		BuildSeriesOptions options = new BuildSeriesOptions();
		// list of stack experiments
		options.expList = parent0.expListCombo;
		options.expList.index0 = parent0.expListCombo.getSelectedIndex();
		if (allSeriesCheckBox.isSelected())
			options.expList.index1 = options.expList.getItemCount() - 1;
		else
			options.expList.index1 = parent0.expListCombo.getSelectedIndex();
		options.detectAllSeries = allSeriesCheckBox.isSelected();
		if (!allSeriesCheckBox.isSelected()) {
			options.seriesLast = options.seriesFirst;
		} else {
			options.seriesFirst = 0;
		}

		return options;
	}

	void cutAndInterpolate(Experiment exp) {
		SequenceKymos seqKymos = exp.seqSpotKymos;
		ROI2D roiRect = seqKymos.seq.getSelectedROI2D();
		if (roiRect == null)
			return;

		int t = seqKymos.seq.getFirstViewer().getPositionT();
		Spot spot = exp.spotsArray.spotsList.get(t);
		String optionSelected = (String) roiTypeCombo.getSelectedItem();
		if (optionSelected.contains("sum"))
			removeAndUpdate(seqKymos, spot, spot.sum_in, roiRect);
		else if (optionSelected.contains("clean"))
			removeAndUpdate(seqKymos, spot, spot.sum_clean, roiRect);
		else if (optionSelected.contains("fly"))
			removeAndUpdate(seqKymos, spot, spot.flyPresent, roiRect);
	}

	void compensate(Experiment exp) {
		SequenceKymos seqKymos = exp.seqSpotKymos;
		ROI2D roiRect = seqKymos.seq.getSelectedROI2D();
		if (roiRect == null)
			return;

		int t = seqKymos.seq.getFirstViewer().getPositionT();
		Spot spot = exp.spotsArray.spotsList.get(t);
		String optionSelected = (String) roiTypeCombo.getSelectedItem();
		if (optionSelected.contains("sum"))
			compensateAndUpdate(seqKymos, spot, spot.sum_in, roiRect);
		else if (optionSelected.contains("clean"))
			compensateAndUpdate(seqKymos, spot, spot.sum_clean, roiRect);
		else if (optionSelected.contains("fly"))
			compensateAndUpdate(seqKymos, spot, spot.flyPresent, roiRect);
	}

	private void removeAndUpdate(SequenceKymos seqKymos, Spot spot, SpotMeasure spotMeasure, ROI2D roi) {
		spotMeasure.cutAndInterpolatePointsEnclosedInSelectedRoi(roi);
		spotMeasure.transferROItoLevel2D();
	}

	private void compensateAndUpdate(SequenceKymos seqKymos, Spot spot, SpotMeasure spotMeasure, ROI2D roi) {
		boolean bAdd = (directionCombo.getSelectedIndex() == 0);
		spotMeasure.compensateOffetUsingSelectedRoi(roi, bAdd);
		spotMeasure.transferROItoLevel2D();
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (StringUtil.equals("thread_ended", evt.getPropertyName())) {
			buildMedianButton.setText(buildMedianString);
			Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
			if (exp != null) {
				exp.load_SpotsMeasures();
				parent0.dlgMeasure.tabGraphs.displayGraphsPanels(exp);
			}
		}
	}

}
