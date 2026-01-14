package plugins.fmp.multicafe.dlg.experiment;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import icy.canvas.Canvas2D;
import icy.gui.viewer.Viewer;
import icy.sequence.Sequence;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.fmp_experiment.Experiment;
import plugins.fmp.multitools.fmp_tools.JComponents.SortedComboBoxModel;
import plugins.fmp.multitools.fmp_tools.toExcel.enums.EnumXLSColumnHeader;

public class Infos extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2190848825783418962L;

	private JComboBox<String> stim1Combo = new JComboBox<String>(new SortedComboBoxModel());
	private JComboBox<String> stim2Combo = new JComboBox<String>(new SortedComboBoxModel());
	private JComboBox<String> boxIDCombo = new JComboBox<String>(new SortedComboBoxModel());
	private JComboBox<String> exptCombo = new JComboBox<String>(new SortedComboBoxModel());
	private JComboBox<String> strainCombo = new JComboBox<String>(new SortedComboBoxModel());
	private JComboBox<String> sexCombo = new JComboBox<String>(new SortedComboBoxModel());
	private JComboBox<String> conc1Combo = new JComboBox<String>(new SortedComboBoxModel());
	private JComboBox<String> conc2Combo = new JComboBox<String>(new SortedComboBoxModel());

	private JLabel experimentLabel = new JLabel(EnumXLSColumnHeader.EXP_EXPT.toString());
	private JLabel boxIDLabel = new JLabel(EnumXLSColumnHeader.EXP_BOXID.toString());
	private JLabel stim1Label = new JLabel(EnumXLSColumnHeader.EXP_STIM1.toString());
	private JLabel conc1Label = new JLabel(EnumXLSColumnHeader.EXP_CONC1.toString());
	private JLabel strainLabel = new JLabel(EnumXLSColumnHeader.EXP_STRAIN.toString());
	private JLabel sexLabel = new JLabel(EnumXLSColumnHeader.EXP_SEX.toString());
	private JLabel stim2Label = new JLabel(EnumXLSColumnHeader.EXP_STIM2.toString());
	private JLabel conc2Label = new JLabel(EnumXLSColumnHeader.EXP_CONC2.toString());

	private JButton openButton = new JButton("Load...");
	private JButton saveButton = new JButton("Save...");
	private JButton duplicateButton = new JButton("Get previous");
	private JButton zoomButton = new JButton("zoom top");

	private MultiCAFE parent0 = null;
	public boolean disableChangeFile = false;

	void init(GridLayout capLayout, MultiCAFE parent0) {
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

		c.gridy = 0;
		addLineOfElements(c, experimentLabel, exptCombo, boxIDLabel, boxIDCombo, openButton);
		c.gridy = 1;
		addLineOfElements(c, strainLabel, strainCombo, sexLabel, sexCombo, saveButton);
		c.gridy = 2;
		addLineOfElements(c, stim1Label, stim1Combo, conc1Label, stim2Combo, duplicateButton);
		c.gridy = 3;
		addLineOfElements(c, stim2Label, conc1Combo, conc2Label, conc2Combo, zoomButton);

		zoomButton.setEnabled(false);
		boxIDCombo.setEditable(true);
		exptCombo.setEditable(true);
		stim1Combo.setEditable(true);
		stim2Combo.setEditable(true);
		strainCombo.setEditable(true);
		sexCombo.setEditable(true);
		conc1Combo.setEditable(true);
		conc2Combo.setEditable(true);

		defineActionListeners();
	}

	void addLineOfElements(GridBagConstraints c, JComponent element1, JComponent element2, JComponent element3,
			JComponent element4, JComponent element5) {
		c.gridx = 0;
		int delta1 = 1;
		int delta2 = 3;
		if (element1 != null)
			add(element1, c);
		c.gridx += delta1;
		if (element2 != null)
			add(element2, c);
		c.gridx += delta2;
		if (element3 != null)
			add(element3, c);
		c.gridx += delta1;
		if (element4 != null)
			add(element4, c);
		c.gridx += delta2;
		if (element5 != null)
			add(element5, c);
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

	// set/ get

	public void transferPreviousExperimentInfosToDialog(Experiment exp_source, Experiment exp_destination) {
		setInfoCombo(exp_destination, exp_source, boxIDCombo, EnumXLSColumnHeader.EXP_BOXID);
		setInfoCombo(exp_destination, exp_source, exptCombo, EnumXLSColumnHeader.EXP_EXPT);
		setInfoCombo(exp_destination, exp_source, stim1Combo, EnumXLSColumnHeader.EXP_STIM1);
		setInfoCombo(exp_destination, exp_source, stim2Combo, EnumXLSColumnHeader.EXP_CONC1);
		setInfoCombo(exp_destination, exp_source, strainCombo, EnumXLSColumnHeader.EXP_STRAIN);
		setInfoCombo(exp_destination, exp_source, sexCombo, EnumXLSColumnHeader.EXP_SEX);
		setInfoCombo(exp_destination, exp_source, conc1Combo, EnumXLSColumnHeader.EXP_STIM2);
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
		exp.setExperimentFieldNoTest(EnumXLSColumnHeader.EXP_CONC1, (String) stim2Combo.getSelectedItem());
		exp.setExperimentFieldNoTest(EnumXLSColumnHeader.EXP_STRAIN, (String) strainCombo.getSelectedItem());
		exp.setExperimentFieldNoTest(EnumXLSColumnHeader.EXP_SEX, (String) sexCombo.getSelectedItem());
		exp.setExperimentFieldNoTest(EnumXLSColumnHeader.EXP_STIM2, (String) conc1Combo.getSelectedItem());
		exp.setExperimentFieldNoTest(EnumXLSColumnHeader.EXP_CONC2, (String) conc2Combo.getSelectedItem());
	}

	private void addItemToComboIfNew(String toAdd, JComboBox<String> combo) {
		if (toAdd == null)
			return;
		SortedComboBoxModel model = (SortedComboBoxModel) combo.getModel();
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
		stim2Combo.removeAllItems();
		boxIDCombo.removeAllItems();
		strainCombo.removeAllItems();
		sexCombo.removeAllItems();
		conc1Combo.removeAllItems();
		conc2Combo.removeAllItems();
	}

	void duplicatePreviousDescriptors() {
		int iprevious = parent0.expListComboLazy.getSelectedIndex() - 1;
		if (iprevious < 0)
			return;

		Experiment exp0 = (Experiment) parent0.expListComboLazy.getItemAt(iprevious);
		Experiment exp = (Experiment) parent0.expListComboLazy.getItemAt(iprevious + 1);
		transferPreviousExperimentInfosToDialog(exp0, exp);
		transferPreviousExperimentCapillariesInfos(exp0, exp);
	}

	void transferPreviousExperimentCapillariesInfos(Experiment exp0, Experiment exp) {
		exp.getCapillaries().getCapillariesDescription()
				.setGrouping(exp0.getCapillaries().getCapillariesDescription().getGrouping());
		parent0.paneCapillaries.tabCreate
				.setGroupedBy2(exp0.getCapillaries().getCapillariesDescription().getGrouping() == 2);
		exp.getCapillaries().getCapillariesDescription()
				.setVolume(exp0.getCapillaries().getCapillariesDescription().getVolume());
		parent0.paneCapillaries.tabInfos.setDlgInfosCapillaryDescriptors(exp0.getCapillaries());
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
