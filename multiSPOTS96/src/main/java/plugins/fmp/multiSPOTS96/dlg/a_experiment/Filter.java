package plugins.fmp.multiSPOTS96.dlg.a_experiment;

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
import javax.swing.JPanel;
import javax.swing.JLabel;

import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multiSPOTS96.experiment.Experiment;
import plugins.fmp.multiSPOTS96.experiment.ExperimentProperties;
import plugins.fmp.multiSPOTS96.tools.DialogTools;
import plugins.fmp.multiSPOTS96.tools.JComponents.JComboBoxExperimentLazy;
import plugins.fmp.multiSPOTS96.tools.toExcel.EnumXLSColumnHeader;
import plugins.fmp.multiSPOTS96.tools.LazyExperiment;

public class Filter extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2190848825783418962L;

	private JButton stim1Btn = new JButton("Select...");
	private JButton conc1Btn = new JButton("Select...");
	private JButton boxIDBtn = new JButton("Select...");
	private JButton exptBtn = new JButton("Select...");
	private JButton strainBtn = new JButton("Select...");
	private JButton sexBtn = new JButton("Select...");
	private JButton stim2Btn = new JButton("Select...");
	private JButton conc2Btn = new JButton("Select...");

	private List<String> selStim1 = new ArrayList<String>();
	private List<String> selConc1 = new ArrayList<String>();
	private List<String> selBoxID = new ArrayList<String>();
	private List<String> selExpt = new ArrayList<String>();
	private List<String> selStrain = new ArrayList<String>();
	private List<String> selSex = new ArrayList<String>();
	private List<String> selStim2 = new ArrayList<String>();
	private List<String> selConc2 = new ArrayList<String>();

	private JCheckBox experimentCheck = new JCheckBox(EnumXLSColumnHeader.EXP_EXPT.toString());
	private JCheckBox boxIDCheck = new JCheckBox(EnumXLSColumnHeader.EXP_BOXID.toString());
	private JCheckBox stim1Check = new JCheckBox(EnumXLSColumnHeader.EXP_STIM1.toString());
	private JCheckBox conc1Check = new JCheckBox(EnumXLSColumnHeader.EXP_CONC1.toString());
	private JCheckBox strainCheck = new JCheckBox(EnumXLSColumnHeader.EXP_STRAIN.toString());
	private JCheckBox sexCheck = new JCheckBox(EnumXLSColumnHeader.EXP_SEX.toString());
	private JCheckBox stim2Check = new JCheckBox(EnumXLSColumnHeader.EXP_STIM2.toString());
	private JCheckBox conc2Check = new JCheckBox(EnumXLSColumnHeader.EXP_CONC2.toString());

	private JButton applyButton = new JButton("Apply");
	private JButton clearButton = new JButton("Clear");
    private JLabel indexStatusLabel = new JLabel("index: loading...");

	private MultiSPOTS96 parent0 = null;
	public JComboBoxExperimentLazy filterExpList = new JComboBoxExperimentLazy();

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
		DialogTools.addFiveComponentOnARow(this, experimentCheck, exptBtn, boxIDCheck, boxIDBtn, applyButton, c,
				delta1, delta2);
		// line 2
		c.gridy = 1;
		c.gridx = 0;
		DialogTools.addFiveComponentOnARow(this, strainCheck, strainBtn, sexCheck, sexBtn, clearButton, c, delta1,
				delta2);
		// line 1
		c.gridy = 2;
		c.gridx = 0;
		DialogTools.addFiveComponentOnARow(this, stim1Check, stim1Btn, conc1Check, conc1Btn, null, c, delta1,
				delta2);
		// line 3
		c.gridy = 3;
		c.gridx = 0;
		DialogTools.addFiveComponentOnARow(this, stim2Check, stim2Btn, conc2Check, conc2Btn, null, c, delta1,
				delta2);
		// line 4 - index status
		c.gridy = 4;
		c.gridx = 0;
		DialogTools.addFiveComponentOnARow(this, indexStatusLabel, null, null, null, null, c, delta1, delta2);

		defineActionListeners();
	}

	public void initCombos() {
		if (!parent0.dlgBrowse.loadSaveExperiment.filteredCheck.isSelected())
			filterExpList.setExperimentsFromList(parent0.expListComboLazy.getExperimentsAsListNoLoad());
		// nothing else to populate up-front; values are loaded on demand via dialogs
		updateIndexStatus();
	}

	private List<String> getValuesForField(EnumXLSColumnHeader field) {
		List<String> list;
		if (parent0.descriptorIndex != null && parent0.descriptorIndex.isReady())
			list = parent0.descriptorIndex.getDistinctValues(field);
		else {
			list = filterExpList.getFieldValuesFromAllExperimentsLightweight(field);
			java.util.Collections.sort(list);
		}
		return list;
	}

	private void updateButtonLabel(JButton btn, List<String> selected) {
		if (selected == null || selected.isEmpty())
			btn.setText("Select...");
		else if (selected.size() == 1)
			btn.setText(selected.get(0));
		else
			btn.setText(selected.size() + " selected");
	}

	private void updateIndexStatus() {
		if (parent0 != null && parent0.descriptorIndex != null && parent0.descriptorIndex.isReady())
			indexStatusLabel.setText("index: ready");
		else
			indexStatusLabel.setText("index: loading...");
	}

	private void defineActionListeners() {
		updateIndexStatus();
		exptBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				List<String> all = getValuesForField(EnumXLSColumnHeader.EXP_EXPT);
				List<String> chosen = plugins.fmp.multiSPOTS96.tools.JComponents.MultiSelectDialog.showDialog(exptBtn,
						EnumXLSColumnHeader.EXP_EXPT.toString(), all, selExpt);
				if (chosen != null) {
					selExpt.clear();
					selExpt.addAll(chosen);
					updateButtonLabel(exptBtn, selExpt);
				}
			}
		});
		boxIDBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				List<String> all = getValuesForField(EnumXLSColumnHeader.EXP_BOXID);
				List<String> chosen = plugins.fmp.multiSPOTS96.tools.JComponents.MultiSelectDialog.showDialog(boxIDBtn,
						EnumXLSColumnHeader.EXP_BOXID.toString(), all, selBoxID);
				if (chosen != null) {
					selBoxID.clear();
					selBoxID.addAll(chosen);
					updateButtonLabel(boxIDBtn, selBoxID);
				}
			}
		});
		stim1Btn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				List<String> all = getValuesForField(EnumXLSColumnHeader.EXP_STIM1);
				List<String> chosen = plugins.fmp.multiSPOTS96.tools.JComponents.MultiSelectDialog.showDialog(stim1Btn,
						EnumXLSColumnHeader.EXP_STIM1.toString(), all, selStim1);
				if (chosen != null) {
					selStim1.clear();
					selStim1.addAll(chosen);
					updateButtonLabel(stim1Btn, selStim1);
				}
			}
		});
		conc1Btn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				List<String> all = getValuesForField(EnumXLSColumnHeader.EXP_CONC1);
				List<String> chosen = plugins.fmp.multiSPOTS96.tools.JComponents.MultiSelectDialog.showDialog(conc1Btn,
						EnumXLSColumnHeader.EXP_CONC1.toString(), all, selConc1);
				if (chosen != null) {
					selConc1.clear();
					selConc1.addAll(chosen);
					updateButtonLabel(conc1Btn, selConc1);
				}
			}
		});
		sexBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				List<String> all = getValuesForField(EnumXLSColumnHeader.EXP_SEX);
				List<String> chosen = plugins.fmp.multiSPOTS96.tools.JComponents.MultiSelectDialog.showDialog(sexBtn,
						EnumXLSColumnHeader.EXP_SEX.toString(), all, selSex);
				if (chosen != null) {
					selSex.clear();
					selSex.addAll(chosen);
					updateButtonLabel(sexBtn, selSex);
				}
			}
		});
		strainBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				List<String> all = getValuesForField(EnumXLSColumnHeader.EXP_STRAIN);
				List<String> chosen = plugins.fmp.multiSPOTS96.tools.JComponents.MultiSelectDialog.showDialog(strainBtn,
						EnumXLSColumnHeader.EXP_STRAIN.toString(), all, selStrain);
				if (chosen != null) {
					selStrain.clear();
					selStrain.addAll(chosen);
					updateButtonLabel(strainBtn, selStrain);
				}
			}
		});
		stim2Btn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				List<String> all = getValuesForField(EnumXLSColumnHeader.EXP_STIM2);
				List<String> chosen = plugins.fmp.multiSPOTS96.tools.JComponents.MultiSelectDialog.showDialog(stim2Btn,
						EnumXLSColumnHeader.EXP_STIM2.toString(), all, selStim2);
				if (chosen != null) {
					selStim2.clear();
					selStim2.addAll(chosen);
					updateButtonLabel(stim2Btn, selStim2);
				}
			}
		});
		conc2Btn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				List<String> all = getValuesForField(EnumXLSColumnHeader.EXP_CONC2);
				List<String> chosen = plugins.fmp.multiSPOTS96.tools.JComponents.MultiSelectDialog.showDialog(conc2Btn,
						EnumXLSColumnHeader.EXP_CONC2.toString(), all, selConc2);
				if (chosen != null) {
					selConc2.clear();
					selConc2.addAll(chosen);
					updateButtonLabel(conc2Btn, selConc2);
				}
			}
		});
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
			parent0.expListComboLazy.setExperimentsFromList(filterAllItems());
		} else {
			clearAllCheckBoxes();
			parent0.expListComboLazy.setExperimentsFromList(filterExpList.getExperimentsAsListNoLoad());
		}

		if (parent0.expListComboLazy.getItemCount() > 0)
			parent0.expListComboLazy.setSelectedIndex(0);
		if (setFilter != parent0.dlgBrowse.loadSaveExperiment.filteredCheck.isSelected())
			parent0.dlgBrowse.loadSaveExperiment.filteredCheck.setSelected(setFilter);
	}

	public void clearAllCheckBoxes() {
		boolean select = false;
		experimentCheck.setSelected(select);
		boxIDCheck.setSelected(select);
		stim1Check.setSelected(select);
		conc1Check.setSelected(select);
		strainCheck.setSelected(select);
		sexCheck.setSelected(select);
		stim2Check.setSelected(select);
		conc2Check.setSelected(select);
		selExpt.clear();
		selBoxID.clear();
		selStim1.clear();
		selConc1.clear();
		selStrain.clear();
		selSex.clear();
		selStim2.clear();
		selConc2.clear();
		updateButtonLabel(exptBtn, selExpt);
		updateButtonLabel(boxIDBtn, selBoxID);
		updateButtonLabel(stim1Btn, selStim1);
		updateButtonLabel(conc1Btn, selConc1);
		updateButtonLabel(strainBtn, selStrain);
		updateButtonLabel(sexBtn, selSex);
		updateButtonLabel(stim2Btn, selStim2);
		updateButtonLabel(conc2Btn, selConc2);
	}

	private List<Experiment> filterAllItems() {
		List<Experiment> filteredList = new ArrayList<Experiment>(filterExpList.getExperimentsAsListNoLoad());
		if (experimentCheck.isSelected())
			filterItemMulti(filteredList, EnumXLSColumnHeader.EXP_EXPT, selExpt);
		if (boxIDCheck.isSelected())
			filterItemMulti(filteredList, EnumXLSColumnHeader.EXP_BOXID, selBoxID);
		if (stim1Check.isSelected())
			filterItemMulti(filteredList, EnumXLSColumnHeader.EXP_STIM1, selStim1);
		if (conc1Check.isSelected())
			filterItemMulti(filteredList, EnumXLSColumnHeader.EXP_CONC1, selConc1);
		if (sexCheck.isSelected())
			filterItemMulti(filteredList, EnumXLSColumnHeader.EXP_SEX, selSex);
		if (strainCheck.isSelected())
			filterItemMulti(filteredList, EnumXLSColumnHeader.EXP_STRAIN, selStrain);
		if (stim2Check.isSelected())
			filterItemMulti(filteredList, EnumXLSColumnHeader.EXP_STIM2, selStim2);
		if (conc2Check.isSelected())
			filterItemMulti(filteredList, EnumXLSColumnHeader.EXP_CONC2, selConc2);
		return filteredList;
	}

	void filterItemMulti(List<Experiment> filteredList, EnumXLSColumnHeader header, List<String> allowedValues) {
		if (allowedValues == null || allowedValues.isEmpty())
			return; // nothing selected -> don't restrict
		java.util.HashSet<String> allowed = new java.util.HashSet<String>(allowedValues);
		Iterator<Experiment> iterator = filteredList.iterator();
		while (iterator.hasNext()) {
			Experiment exp = iterator.next();
			String value;
			if (exp instanceof LazyExperiment) {
				value = ((LazyExperiment) exp).getFieldValue(header);
			} else {
				ExperimentProperties prop = exp.getProperties();
				value = prop.getExperimentField(header);
			}
			if (!allowed.contains(value))
				iterator.remove();
		}
	}

}
