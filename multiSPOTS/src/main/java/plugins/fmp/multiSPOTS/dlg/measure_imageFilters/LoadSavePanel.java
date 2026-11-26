package plugins.fmp.multiSPOTS.dlg.measure_imageFilters;

import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.EnumSet;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import icy.gui.util.FontUtil;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMeasurementMethod;
import plugins.fmp.multitools.experiment.spots.SpotsSequenceMapper;

public class LoadSavePanel extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -4019075448319252245L;

	private JButton loadButton = new JButton("Load...");
	private JButton saveButton = new JButton("Save...");
	private JButton clearButton = new JButton("Clear...");
	private MultiSPOTS parent0 = null;
	private SpotMeasurementMethod clearMethod = SpotMeasurementMethod.FILTER;

	public void init(GridLayout capLayout, MultiSPOTS parent0) {
		init(capLayout, parent0, SpotMeasurementMethod.FILTER);
	}

	public void init(GridLayout capLayout, MultiSPOTS parent0, SpotMeasurementMethod clearMethod) {
		this.clearMethod = clearMethod == null ? SpotMeasurementMethod.FILTER : clearMethod;
		JLabel loadsaveText = new JLabel("-> Spot measures (csv) ", SwingConstants.RIGHT);
		loadsaveText.setFont(FontUtil.setStyle(loadsaveText.getFont(), Font.ITALIC));
		FlowLayout flowLayout = new FlowLayout(FlowLayout.RIGHT);
		flowLayout.setVgap(0);
		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(loadsaveText);
		panel1.add(loadButton);
		panel1.add(saveButton);
		panel1.validate();

		JPanel panelClear = new JPanel(flowLayout);
		panelClear.add(clearButton);
		panelClear.validate();
		SpotsMeasuresUi.layoutStackedRows(this, panel1, panelClear);

		this.parent0 = parent0;
		defineActionListeners();
	}

	private void defineActionListeners() {
		loadButton.addActionListener(e -> {
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null) {
				dlg_spotsmeasures_loadSpotsArray_File(exp);
				firePropertyChange("CAP_ROIS_OPEN", false, true);
			}
		});

		saveButton.addActionListener(e -> {
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null) {
				saveSpotsArray_file(exp);
				firePropertyChange("CAP_ROIS_SAVE", false, true);
			}
		});

		clearButton.addActionListener(e -> clearSelectedMeasures());
	}

	private void clearSelectedMeasures() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null) {
			return;
		}
		String kept = clearMethod == SpotMeasurementMethod.COLOR
				? "Filter, line, and fly-position results are kept."
				: "Color, line, and fly-position results are kept.";
		String erased = clearMethod == SpotMeasurementMethod.COLOR ? "color-distance measurements"
				: "filter grey measurements";
		int choice = JOptionPane.showConfirmDialog(this,
				"Erase " + erased + " for this experiment?\n" + kept,
				"Clear measurements", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
		if (choice != JOptionPane.OK_OPTION) {
			return;
		}
		if (!exp.clearSpotMeasurements(EnumSet.of(clearMethod))) {
			JOptionPane.showMessageDialog(this, "Could not erase the selected measurements.",
					"Clear measurements", JOptionPane.ERROR_MESSAGE);
			return;
		}
		if (clearMethod == SpotMeasurementMethod.COLOR) {
			if (parent0.dlgMeasureColor != null && parent0.dlgMeasureColor.chartsColorPanel != null) {
				parent0.dlgMeasureColor.chartsColorPanel.closeAllCharts();
			}
		} else if (parent0.dlgMeasure != null && parent0.dlgMeasure.chartsPanel != null) {
			parent0.dlgMeasure.chartsPanel.closeAllCharts();
		}
	}

	public boolean dlg_spotsmeasures_loadSpotsArray_File(Experiment exp) {
		boolean flag = exp.load_cages_description_and_measures();
		if (flag) {
			exp.load_spots_description_and_measures();
			SpotsSequenceMapper.transferROIsToSequence(exp.getSpots(), exp.getSeqCamData());
			parent0.dlgExperiment.optionsPanel.applyViewOptionsToCurrentExperiment();
		}
		return flag;
	}

	public boolean saveSpotsArray_file(Experiment exp) {
		parent0.dlgExperiment.getExperimentInfosFromDialog(exp);
		boolean flag = exp.saveExperimentDescriptors();
		SpotsSequenceMapper.transferROIsFromSequence(exp.getSpots(), exp.getSeqCamData());
		flag &= exp.save_cages_description_and_measures();
		flag &= exp.save_spots_description_and_measures();
		return flag;
	}

}
