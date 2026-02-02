package plugins.fmp.multiSPOTS.dlg.experiment;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;

import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;
import plugins.fmp.multitools.tools.JComponents.JComboBoxModelSorted;
import plugins.fmp.multitools.tools.toExcel.enums.EnumXLSColumnHeader;

public class Filter extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2190848825783418962L;

	private JComboBox<String> cmt1Combo = new JComboBox<String>(new JComboBoxModelSorted());
	private JComboBox<String> comt2Combo = new JComboBox<String>(new JComboBoxModelSorted());
	private JComboBox<String> boxIDCombo = new JComboBox<String>(new JComboBoxModelSorted());
	private JComboBox<String> exptCombo = new JComboBox<String>(new JComboBoxModelSorted());
	private JComboBox<String> strainCombo = new JComboBox<String>(new JComboBoxModelSorted());
	private JComboBox<String> sexCombo = new JComboBox<String>(new JComboBoxModelSorted());
	private JComboBox<String> cond1Combo = new JComboBox<String>(new JComboBoxModelSorted());
	private JComboBox<String> cond2Combo = new JComboBox<String>(new JComboBoxModelSorted());

	private JCheckBox experimentCheck = new JCheckBox(EnumXLSColumnHeader.EXP_EXPT.toString());
	private JCheckBox boxIDCheck = new JCheckBox(EnumXLSColumnHeader.EXP_BOXID.toString());
	private JCheckBox comment1Check = new JCheckBox(EnumXLSColumnHeader.EXP_STIM1.toString());
	private JCheckBox comment2Check = new JCheckBox(EnumXLSColumnHeader.EXP_CONC1.toString());
	private JCheckBox strainCheck = new JCheckBox(EnumXLSColumnHeader.EXP_STRAIN.toString());
	private JCheckBox sexCheck = new JCheckBox(EnumXLSColumnHeader.EXP_SEX.toString());
	private JCheckBox cond1Check = new JCheckBox(EnumXLSColumnHeader.EXP_STIM2.toString());
	private JCheckBox cond2Check = new JCheckBox(EnumXLSColumnHeader.EXP_CONC2.toString());
	private JButton applyButton = new JButton("Apply");
	private JButton clearButton = new JButton("Clear");

	private MultiSPOTS parent0 = null;
	public JComboBoxExperimentLazy filterExpList = new JComboBoxExperimentLazy();

	void init(GridLayout capLayout, MultiSPOTS parent0) {
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
		add(experimentCheck, c);
		c.gridx += delta1;
		add(exptCombo, c);
		c.gridx += delta2;
		add(boxIDCheck, c);
		c.gridx += delta1;
		add(boxIDCombo, c);
		c.gridx += delta2;
		add(applyButton, c);

		// line 1
		c.gridy = 1;
		c.gridx = 0;
		add(comment1Check, c);
		c.gridx += delta1;
		add(cmt1Combo, c);
		c.gridx += delta2;
		add(comment2Check, c);
		c.gridx += delta1;
		add(comt2Combo, c);
		c.gridx += delta2;
		add(clearButton, c);

		// line 2
		c.gridy = 2;
		c.gridx = 0;
		add(strainCheck, c);
		c.gridx += delta1;
		add(strainCombo, c);
		c.gridx += delta2;
		add(sexCheck, c);
		c.gridx += delta1;
		add(sexCombo, c);

		// line 3
		c.gridy = 3;
		c.gridx = 0;
		add(cond1Check, c);
		c.gridx += delta1;
		add(cond1Combo, c);
		c.gridx += delta2;
		add(cond2Check, c);
		c.gridx += delta1;
		add(cond2Combo, c);

		defineActionListeners();
	}

	public void initFilterCombos() {
		if (!parent0.dlgBrowse.panelLoadSave.filteredCheck.isSelected())
			filterExpList.setExperimentsFromList(parent0.expListCombo.getExperimentsAsList());
		filterExpList.getFieldValuesToCombo(exptCombo, EnumXLSColumnHeader.EXP_EXPT);
		filterExpList.getFieldValuesToCombo(cmt1Combo, EnumXLSColumnHeader.EXP_STIM1);
		filterExpList.getFieldValuesToCombo(comt2Combo, EnumXLSColumnHeader.EXP_CONC1);
		filterExpList.getFieldValuesToCombo(boxIDCombo, EnumXLSColumnHeader.EXP_BOXID);
		filterExpList.getFieldValuesToCombo(sexCombo, EnumXLSColumnHeader.EXP_SEX);
		filterExpList.getFieldValuesToCombo(strainCombo, EnumXLSColumnHeader.EXP_STRAIN);
		filterExpList.getFieldValuesToCombo(cond1Combo, EnumXLSColumnHeader.EXP_STIM2);
		filterExpList.getFieldValuesToCombo(cond2Combo, EnumXLSColumnHeader.EXP_CONC2);
	}

	private void defineActionListeners() {
		applyButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				filterExperimentList(true);
				parent0.dlgExperiment.tabsPane.setSelectedIndex(0);
			}
		});

		clearButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				filterExperimentList(false);
			}
		});
	}

	public void filterExperimentList(boolean setFilter) {
		if (setFilter) {
			parent0.expListCombo.setExperimentsFromList(filterAllItems());
		} else {
			clearAllCheckBoxes();
			parent0.expListCombo.setExperimentsFromList(filterExpList.getExperimentsAsList());
		}

		if (parent0.expListCombo.getItemCount() > 0)
			parent0.expListCombo.setSelectedIndex(0);
		if (setFilter != parent0.dlgBrowse.panelLoadSave.filteredCheck.isSelected())
			parent0.dlgBrowse.panelLoadSave.filteredCheck.setSelected(setFilter);
	}

	public void clearAllCheckBoxes() {
		boolean select = false;
		experimentCheck.setSelected(select);
		boxIDCheck.setSelected(select);
		comment1Check.setSelected(select);
		comment2Check.setSelected(select);
		strainCheck.setSelected(select);
		sexCheck.setSelected(select);
	}

	private List<Experiment> filterAllItems() {
		List<Experiment> filteredList = new ArrayList<Experiment>(filterExpList.getExperimentsAsList());
		if (experimentCheck.isSelected())
			filterItem(filteredList, EnumXLSColumnHeader.EXP_EXPT, (String) exptCombo.getSelectedItem());
		if (boxIDCheck.isSelected())
			filterItem(filteredList, EnumXLSColumnHeader.EXP_BOXID, (String) boxIDCombo.getSelectedItem());
		if (comment1Check.isSelected())
			filterItem(filteredList, EnumXLSColumnHeader.EXP_STIM1, (String) cmt1Combo.getSelectedItem());
		if (comment2Check.isSelected())
			filterItem(filteredList, EnumXLSColumnHeader.EXP_CONC1, (String) comt2Combo.getSelectedItem());
		if (sexCheck.isSelected())
			filterItem(filteredList, EnumXLSColumnHeader.EXP_SEX, (String) sexCombo.getSelectedItem());
		if (strainCheck.isSelected())
			filterItem(filteredList, EnumXLSColumnHeader.EXP_STRAIN, (String) strainCombo.getSelectedItem());
		if (cond1Check.isSelected())
			filterItem(filteredList, EnumXLSColumnHeader.EXP_STIM2, (String) cond1Combo.getSelectedItem());
		if (cond2Check.isSelected())
			filterItem(filteredList, EnumXLSColumnHeader.EXP_CONC2, (String) cond2Combo.getSelectedItem());
		return filteredList;
	}

	void filterItem(List<Experiment> filteredList, EnumXLSColumnHeader header, String filter) {
		Iterator<Experiment> iterator = filteredList.iterator();
		while (iterator.hasNext()) {
			Experiment exp = iterator.next();
			int compare = exp.getProperties().getExperimentField(header).compareTo(filter);
			if (compare != 0)
				iterator.remove();
		}
	}

}
