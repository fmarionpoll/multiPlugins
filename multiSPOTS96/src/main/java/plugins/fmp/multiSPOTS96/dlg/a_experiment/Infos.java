package plugins.fmp.multiSPOTS96.dlg.a_experiment;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import icy.canvas.Canvas2D;
import icy.gui.viewer.Viewer;
import icy.sequence.Sequence;
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.DialogTools;
import plugins.fmp.multitools.tools.JComponents.JComboBoxModelSorted;
import plugins.fmp.multitools.tools.toExcel.enums.EnumXLSColumnHeader;

public class Infos extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2190848825783418962L;

	private JComboBox<String> stim1Combo = new JComboBox<String>(new JComboBoxModelSorted());
	private JComboBox<String> conc1Combo = new JComboBox<String>(new JComboBoxModelSorted());
	private JComboBox<String> boxIDCombo = new JComboBox<String>(new JComboBoxModelSorted());
	private JComboBox<String> exptCombo = new JComboBox<String>(new JComboBoxModelSorted());
	private JComboBox<String> strainCombo = new JComboBox<String>(new JComboBoxModelSorted());
	private JComboBox<String> sexCombo = new JComboBox<String>(new JComboBoxModelSorted());
	private JComboBox<String> stim2Combo = new JComboBox<String>(new JComboBoxModelSorted());
	private JComboBox<String> conc2Combo = new JComboBox<String>(new JComboBoxModelSorted());

	private JLabel experimentCheck = new JLabel(EnumXLSColumnHeader.EXP_EXPT.toString());
	private JLabel boxIDCheck = new JLabel(EnumXLSColumnHeader.EXP_BOXID.toString());
	private JLabel stim1Check = new JLabel(EnumXLSColumnHeader.EXP_STIM1.toString());
	private JLabel conc1Check = new JLabel(EnumXLSColumnHeader.EXP_CONC1.toString());
	private JLabel strainCheck = new JLabel(EnumXLSColumnHeader.EXP_STRAIN.toString());
	private JLabel sexCheck = new JLabel(EnumXLSColumnHeader.EXP_SEX.toString());
	private JLabel stim2Check = new JLabel(EnumXLSColumnHeader.EXP_STIM2.toString());
	private JLabel conc2Check = new JLabel(EnumXLSColumnHeader.EXP_CONC2.toString());

	private JButton openButton = new JButton("Load...");
	private JButton saveButton = new JButton("Save...");
	private JButton duplicateButton = new JButton("Get previous");
	private JButton zoomButton = new JButton("zoom top");

	private MultiSPOTS96 parent0 = null;

	void init(GridLayout capLayout, MultiSPOTS96 parent0) {
		this.parent0 = parent0;
		GridBagLayout layoutThis = new GridBagLayout();
		setLayout(layoutThis);

		GridBagConstraints c = new GridBagConstraints();
		c.gridwidth = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.BASELINE;
		c.ipadx = 0;
		c.ipady = 0;
		c.insets = new Insets(1, 2, 1, 2);
		int delta1 = 1;
		int delta2 = 3;

		// line 0
		c.gridx = 0;
		c.gridy = 0;
		DialogTools.addFiveComponentOnARow(this, experimentCheck, exptCombo, boxIDCheck, boxIDCombo, openButton, c,
				delta1, delta2);
		// line 1
		c.gridy = 1;
		c.gridx = 0;
		DialogTools.addFiveComponentOnARow(this, strainCheck, strainCombo, sexCheck, sexCombo, saveButton, c, delta1,
				delta2);
		// line 2
		c.gridy = 2;
		c.gridx = 0;
		DialogTools.addFiveComponentOnARow(this, stim1Check, stim1Combo, conc1Check, conc1Combo, duplicateButton, c,
				delta1, delta2);
		// line 3
		c.gridy = 3;
		c.gridx = 0;
		DialogTools.addFiveComponentOnARow(this, stim2Check, stim2Combo, conc2Check, conc2Combo, zoomButton, c, delta1,
				delta2);

		boxIDCombo.setEditable(true);
		exptCombo.setEditable(true);
		stim1Combo.setEditable(true);
		conc1Combo.setEditable(true);
		strainCombo.setEditable(true);
		sexCombo.setEditable(true);
		stim2Combo.setEditable(true);
		conc2Combo.setEditable(true);

		defineActionListeners();
	}

	private void defineActionListeners() {
		openButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					exp.xmlLoad_MCExperiment();
					transferPreviousExperimentInfosToDialog(exp, exp);
				}
			}
		});

		saveButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					getExperimentInfosFromDialog(exp);
					exp.saveExperimentDescriptors();
				}
			}
		});

		duplicateButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				duplicatePreviousDescriptors();
			}
		});

		zoomButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					zoomToUpperCorner(exp);
			}
		});
	}

	public void transferPreviousExperimentInfosToDialog(Experiment exp_source, Experiment exp_destination) {
		setInfoCombo(exp_destination, exp_source, boxIDCombo, EnumXLSColumnHeader.EXP_BOXID);
		setInfoCombo(exp_destination, exp_source, exptCombo, EnumXLSColumnHeader.EXP_EXPT);
		setInfoCombo(exp_destination, exp_source, stim1Combo, EnumXLSColumnHeader.EXP_STIM1);
		setInfoCombo(exp_destination, exp_source, conc1Combo, EnumXLSColumnHeader.EXP_CONC1);
		setInfoCombo(exp_destination, exp_source, strainCombo, EnumXLSColumnHeader.EXP_STRAIN);
		setInfoCombo(exp_destination, exp_source, sexCombo, EnumXLSColumnHeader.EXP_SEX);
		setInfoCombo(exp_destination, exp_source, stim2Combo, EnumXLSColumnHeader.EXP_STIM2);
		setInfoCombo(exp_destination, exp_source, conc2Combo, EnumXLSColumnHeader.EXP_CONC2);
	}

	private void setInfoCombo(Experiment exp_dest, Experiment exp_source, JComboBox<String> combo,
			EnumXLSColumnHeader field) {
		String altText = exp_source.getExperimentField(field);
		String text = exp_dest.getExperimentField(field);
		if (text.equals(".."))
			exp_dest.setExperimentFieldNoTest(field, altText);
		text = exp_dest.getExperimentField(field);
		addItemToComboIfNew(text, combo);
		combo.setSelectedItem(text);
	}

	public void getExperimentInfosFromDialog(Experiment exp) {
		exp.setExperimentFieldNoTest(EnumXLSColumnHeader.EXP_BOXID, (String) boxIDCombo.getSelectedItem());
		exp.setExperimentFieldNoTest(EnumXLSColumnHeader.EXP_EXPT, (String) exptCombo.getSelectedItem());
		exp.setExperimentFieldNoTest(EnumXLSColumnHeader.EXP_STIM1, (String) stim1Combo.getSelectedItem());
		exp.setExperimentFieldNoTest(EnumXLSColumnHeader.EXP_CONC1, (String) conc1Combo.getSelectedItem());
		exp.setExperimentFieldNoTest(EnumXLSColumnHeader.EXP_STRAIN, (String) strainCombo.getSelectedItem());
		exp.setExperimentFieldNoTest(EnumXLSColumnHeader.EXP_SEX, (String) sexCombo.getSelectedItem());
		exp.setExperimentFieldNoTest(EnumXLSColumnHeader.EXP_STIM2, (String) stim2Combo.getSelectedItem());
		exp.setExperimentFieldNoTest(EnumXLSColumnHeader.EXP_CONC2, (String) conc2Combo.getSelectedItem());
	}

	private void addItemToComboIfNew(String toAdd, JComboBox<String> combo) {
		if (toAdd == null)
			return;
		JComboBoxModelSorted model = (JComboBoxModelSorted) combo.getModel();
		if (model.getIndexOf(toAdd) == -1)
			model.addElement(toAdd);
	}

	public void initCombos() {
		// Prefer DescriptorIndex when ready; fallback to lightweight combo collection
		if (parent0.descriptorIndex != null && parent0.descriptorIndex.isReady()) {
			refreshComboFromIndex(exptCombo, EnumXLSColumnHeader.EXP_EXPT);
			refreshComboFromIndex(stim1Combo, EnumXLSColumnHeader.EXP_STIM1);
			refreshComboFromIndex(conc1Combo, EnumXLSColumnHeader.EXP_CONC1);
			refreshComboFromIndex(boxIDCombo, EnumXLSColumnHeader.EXP_BOXID);
			refreshComboFromIndex(strainCombo, EnumXLSColumnHeader.EXP_STRAIN);
			refreshComboFromIndex(sexCombo, EnumXLSColumnHeader.EXP_SEX);
			refreshComboFromIndex(stim2Combo, EnumXLSColumnHeader.EXP_STIM2);
			refreshComboFromIndex(conc2Combo, EnumXLSColumnHeader.EXP_CONC2);
		} else {
			// Use lightweight version to avoid loading all experiments
			parent0.expListComboLazy.getFieldValuesToComboLightweight(exptCombo, EnumXLSColumnHeader.EXP_EXPT);
			parent0.expListComboLazy.getFieldValuesToComboLightweight(stim1Combo, EnumXLSColumnHeader.EXP_STIM1);
			parent0.expListComboLazy.getFieldValuesToComboLightweight(conc1Combo, EnumXLSColumnHeader.EXP_CONC1);
			parent0.expListComboLazy.getFieldValuesToComboLightweight(boxIDCombo, EnumXLSColumnHeader.EXP_BOXID);
			parent0.expListComboLazy.getFieldValuesToComboLightweight(strainCombo, EnumXLSColumnHeader.EXP_STRAIN);
			parent0.expListComboLazy.getFieldValuesToComboLightweight(sexCombo, EnumXLSColumnHeader.EXP_SEX);
			parent0.expListComboLazy.getFieldValuesToComboLightweight(stim2Combo, EnumXLSColumnHeader.EXP_STIM2);
			parent0.expListComboLazy.getFieldValuesToComboLightweight(conc2Combo, EnumXLSColumnHeader.EXP_CONC2);
		}
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null)
			transferPreviousExperimentInfosToDialog(exp, exp);
	}

	private void refreshComboFromIndex(JComboBox<String> combo, EnumXLSColumnHeader field) {
		combo.removeAllItems();
		for (String text : parent0.descriptorIndex.getDistinctValues(field)) {
			combo.addItem(text);
		}
	}

	public void clearCombos() {
		exptCombo.removeAllItems();
		stim1Combo.removeAllItems();
		conc1Combo.removeAllItems();
		boxIDCombo.removeAllItems();
		strainCombo.removeAllItems();
		sexCombo.removeAllItems();
	}

	void duplicatePreviousDescriptors() {
		int iprevious = parent0.expListComboLazy.getSelectedIndex() - 1;
		if (iprevious < 0)
			return;

		Experiment exp0 = (Experiment) parent0.expListComboLazy.getItemAt(iprevious);
		Experiment exp = (Experiment) parent0.expListComboLazy.getItemAt(iprevious + 1);
		transferPreviousExperimentInfosToDialog(exp0, exp);
	}

	void zoomToUpperCorner(Experiment exp) {
		Sequence seq = exp.getSeqCamData().getSequence();
		Viewer v = seq.getFirstViewer();
		if (v != null) {
			Canvas2D canvas = (Canvas2D) v.getCanvas();
			canvas.setScale(2., 2., true);
			canvas.setOffset(0, 0, true);
		}

	}

}
