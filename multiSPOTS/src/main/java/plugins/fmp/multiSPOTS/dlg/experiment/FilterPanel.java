package plugins.fmp.multiSPOTS.dlg.experiment;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.LazyExperiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.tools.DialogTools;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;
import plugins.fmp.multitools.tools.JComponents.MultiSelectDialog;
import plugins.fmp.multitools.tools.toExcel.enums.EnumXLSColumnHeader;

public class FilterPanel extends JPanel {
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
	private JButton spotStimBtn = new JButton("Select...");
	private JButton spotConcBtn = new JButton("Select...");

	private List<String> selStim1 = new ArrayList<String>();
	private List<String> selConc1 = new ArrayList<String>();
	private List<String> selBoxID = new ArrayList<String>();
	private List<String> selExpt = new ArrayList<String>();
	private List<String> selStrain = new ArrayList<String>();
	private List<String> selSex = new ArrayList<String>();
	private List<String> selStim2 = new ArrayList<String>();
	private List<String> selConc2 = new ArrayList<String>();
	private List<String> selSpotStim = new ArrayList<String>();
	private List<String> selSpotConc = new ArrayList<String>();

	private JCheckBox experimentCheck = new JCheckBox(EnumXLSColumnHeader.EXP_EXPT.toString());
	private JCheckBox boxIDCheck = new JCheckBox(EnumXLSColumnHeader.EXP_ID.toString());
	private JCheckBox stim1Check = new JCheckBox(EnumXLSColumnHeader.EXP_STIM1.toString());
	private JCheckBox conc1Check = new JCheckBox(EnumXLSColumnHeader.EXP_CONC1.toString());
	private JCheckBox strainCheck = new JCheckBox(EnumXLSColumnHeader.EXP_STRAIN.toString());
	private JCheckBox sexCheck = new JCheckBox(EnumXLSColumnHeader.EXP_SEX.toString());
	private JCheckBox stim2Check = new JCheckBox(EnumXLSColumnHeader.EXP_STIM2.toString());
	private JCheckBox conc2Check = new JCheckBox(EnumXLSColumnHeader.EXP_CONC2.toString());
	private JCheckBox spotStimCheck = new JCheckBox(EnumXLSColumnHeader.SPOT_STIM.toString());
	private JCheckBox spotConcCheck = new JCheckBox(EnumXLSColumnHeader.SPOT_CONC.toString());

	private JButton applyButton = new JButton("Apply");
	private JButton clearButton = new JButton("Clear");
	private JLabel indexStatusLabel = new JLabel("index: loading...");

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
		DialogTools.addFiveComponentOnARow(this, experimentCheck, exptBtn, boxIDCheck, boxIDBtn, applyButton, c, delta1,
				delta2);
		// line 2
		c.gridy = 1;
		c.gridx = 0;
		DialogTools.addFiveComponentOnARow(this, strainCheck, strainBtn, sexCheck, sexBtn, clearButton, c, delta1,
				delta2);
		// line 1
		c.gridy = 2;
		c.gridx = 0;
		DialogTools.addFiveComponentOnARow(this, stim1Check, stim1Btn, conc1Check, conc1Btn, null, c, delta1, delta2);
		// line 3
		c.gridy = 3;
		c.gridx = 0;
		DialogTools.addFiveComponentOnARow(this, stim2Check, stim2Btn, conc2Check, conc2Btn, null, c, delta1, delta2);
		c.gridy = 4;
		c.gridx = 0;
		DialogTools.addFiveComponentOnARow(this, spotStimCheck, spotStimBtn, spotConcCheck, spotConcBtn, null, c, delta1,
				delta2);
		c.gridy = 5;
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
		List<String> list = filterExpList.getFieldValuesFromAllExperimentsLightweight(field);
		java.util.Collections.sort(list);
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

	private ActionListener createFilterButtonListener(JButton button, EnumXLSColumnHeader field,
			List<String> selectionList, JCheckBox checkBox) {
		return new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				List<String> all = getValuesForField(field);
				List<String> chosen = MultiSelectDialog.showDialog(button, field.toString(), all, selectionList);
				if (chosen != null) {
					selectionList.clear();
					selectionList.addAll(chosen);
					updateButtonLabel(button, selectionList);
					checkBox.setSelected(!selectionList.isEmpty());
				}
			}
		};
	}

	private void updateIndexStatus() {
		if (parent0 != null && parent0.descriptorIndex != null && parent0.descriptorIndex.isReady())
			indexStatusLabel.setText("index: ready");
		else
			indexStatusLabel.setText("index: loading...");
	}

	private void defineActionListeners() {
		updateIndexStatus();
		exptBtn.addActionListener(
				createFilterButtonListener(exptBtn, EnumXLSColumnHeader.EXP_EXPT, selExpt, experimentCheck));
		boxIDBtn.addActionListener(createFilterButtonListener(boxIDBtn, EnumXLSColumnHeader.EXP_ID, selBoxID, boxIDCheck));
		stim1Btn.addActionListener(
				createFilterButtonListener(stim1Btn, EnumXLSColumnHeader.EXP_STIM1, selStim1, stim1Check));
		conc1Btn.addActionListener(
				createFilterButtonListener(conc1Btn, EnumXLSColumnHeader.EXP_CONC1, selConc1, conc1Check));
		sexBtn.addActionListener(createFilterButtonListener(sexBtn, EnumXLSColumnHeader.EXP_SEX, selSex, sexCheck));
		strainBtn.addActionListener(
				createFilterButtonListener(strainBtn, EnumXLSColumnHeader.EXP_STRAIN, selStrain, strainCheck));
		stim2Btn.addActionListener(
				createFilterButtonListener(stim2Btn, EnumXLSColumnHeader.EXP_STIM2, selStim2, stim2Check));
		conc2Btn.addActionListener(
				createFilterButtonListener(conc2Btn, EnumXLSColumnHeader.EXP_CONC2, selConc2, conc2Check));
		spotStimBtn.addActionListener(
				createFilterButtonListener(spotStimBtn, EnumXLSColumnHeader.SPOT_STIM, selSpotStim, spotStimCheck));
		spotConcBtn.addActionListener(
				createFilterButtonListener(spotConcBtn, EnumXLSColumnHeader.SPOT_CONC, selSpotConc, spotConcCheck));
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
			if (parent0.descriptorIndex != null && filterExpList.getItemCount() > 0) {
				parent0.descriptorIndex.preloadFromCombo(filterExpList, new Runnable() {
					@Override
					public void run() {
						parent0.dlgExperiment.infosPanel.initCombos();
						updateIndexStatus();
					}
				});
			}
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
		spotStimCheck.setSelected(select);
		spotConcCheck.setSelected(select);
		selExpt.clear();
		selBoxID.clear();
		selStim1.clear();
		selConc1.clear();
		selStrain.clear();
		selSex.clear();
		selStim2.clear();
		selConc2.clear();
		selSpotStim.clear();
		selSpotConc.clear();
		updateButtonLabel(exptBtn, selExpt);
		updateButtonLabel(boxIDBtn, selBoxID);
		updateButtonLabel(stim1Btn, selStim1);
		updateButtonLabel(conc1Btn, selConc1);
		updateButtonLabel(strainBtn, selStrain);
		updateButtonLabel(sexBtn, selSex);
		updateButtonLabel(stim2Btn, selStim2);
		updateButtonLabel(conc2Btn, selConc2);
		updateButtonLabel(spotStimBtn, selSpotStim);
		updateButtonLabel(spotConcBtn, selSpotConc);
	}

	private List<Experiment> filterAllItems() {
		List<Experiment> filteredList = new ArrayList<Experiment>(filterExpList.getExperimentsAsListNoLoad());
		if (experimentCheck.isSelected())
			filterItemMulti(filteredList, EnumXLSColumnHeader.EXP_EXPT, selExpt);
		if (boxIDCheck.isSelected())
			filterItemMulti(filteredList, EnumXLSColumnHeader.EXP_ID, selBoxID);
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

		boolean spotStimOn = spotStimCheck.isSelected() && !selSpotStim.isEmpty();
		boolean spotConcOn = spotConcCheck.isSelected() && !selSpotConc.isEmpty();
		if (spotStimOn && spotConcOn)
			filterSameSpotStimulusAndConcentration(filteredList, selSpotStim, selSpotConc);
		else {
			if (spotStimOn)
				filterSpotFieldMulti(filteredList, EnumXLSColumnHeader.SPOT_STIM, selSpotStim);
			if (spotConcOn)
				filterSpotFieldMulti(filteredList, EnumXLSColumnHeader.SPOT_CONC, selSpotConc);
		}
		return filteredList;
	}

	void filterItemMulti(List<Experiment> filteredList, EnumXLSColumnHeader header, List<String> allowedValues) {
		if (allowedValues == null || allowedValues.isEmpty())
			return; // nothing selected -> don't restrict
		java.util.HashSet<String> allowed = new java.util.HashSet<String>(allowedValues.size());
		for (String v : allowedValues) {
			if (v == null)
				continue;
			String n0 = v.trim();
			if (n0.isEmpty())
				n0 = "..";
			String n = n0.toLowerCase();
			if (!n.isEmpty())
				allowed.add(n);
		}
		Iterator<Experiment> iterator = filteredList.iterator();
		while (iterator.hasNext()) {
			Experiment exp = iterator.next();
			String value;
			if (exp instanceof LazyExperiment) {
				value = ((LazyExperiment) exp).getFieldValue(header);
			} else {
				value = exp.getExperimentField(header);
			}
			String v0 = value != null ? value.trim() : "";
			if (v0.isEmpty())
				v0 = "..";
			String norm = v0.toLowerCase();
			if (!allowed.contains(norm))
				iterator.remove();
		}
	}

	private static HashSet<String> normalizedAllowedSet(List<String> allowedValues) {
		HashSet<String> allowed = new HashSet<String>(allowedValues.size());
		for (String v : allowedValues) {
			if (v == null)
				continue;
			String n0 = v.trim();
			if (n0.isEmpty())
				n0 = "..";
			String n = n0.toLowerCase();
			if (!n.isEmpty())
				allowed.add(n);
		}
		return allowed;
	}

	private static String normalizeFilterToken(String value) {
		String v0 = value != null ? value.trim() : "";
		if (v0.isEmpty())
			v0 = "..";
		return v0.toLowerCase();
	}

	/**
	 * Loads full experiment spot state from disk for filter predicates. This runs the same
	 * {@code load_spots_description_and_measures()} path as a normal open (including
	 * {@link plugins.fmp.multitools.experiment.SpotCageHeuristicLayout} and cage geometry heuristics)
	 * but <strong>without</strong> loading the camera sequence first (unlike
	 * {@code ExperimentOpenPipeline}). Bulk filtering on large series can therefore perturb ROI
	 * geometry in memory; avoid saving spots until spots are re-opened from disk or use a
	 * lightweight read path if added in future.
	 */
	private void ensureLoadedForSpotFields(Experiment exp) {
		if (exp instanceof LazyExperiment)
			((LazyExperiment) exp).loadIfNeeded();
		exp.loadExperimentDescriptors();
		exp.load_cages_description_and_measures();
		exp.load_spots_description_and_measures();
	}

	private void filterSpotFieldMulti(List<Experiment> filteredList, EnumXLSColumnHeader header,
			List<String> allowedValues) {
		if (allowedValues == null || allowedValues.isEmpty())
			return;
		HashSet<String> allowed = normalizedAllowedSet(allowedValues);
		Iterator<Experiment> iterator = filteredList.iterator();
		while (iterator.hasNext()) {
			Experiment exp = iterator.next();
			try {
				ensureLoadedForSpotFields(exp);
				List<String> values = exp.getFieldValues(header);
				if (!experimentSpotValuesIntersectAllowed(values, allowed))
					iterator.remove();
			} catch (Exception e) {
				iterator.remove();
			}
		}
	}

	private static boolean experimentSpotValuesIntersectAllowed(List<String> values, HashSet<String> allowedNorm) {
		if (values == null || values.isEmpty())
			return false;
		for (String raw : values) {
			if (allowedNorm.contains(normalizeFilterToken(raw)))
				return true;
		}
		return false;
	}

	private void filterSameSpotStimulusAndConcentration(List<Experiment> filteredList, List<String> stimAllowed,
			List<String> concAllowed) {
		if (stimAllowed == null || stimAllowed.isEmpty() || concAllowed == null || concAllowed.isEmpty())
			return;
		HashSet<String> stimSet = normalizedAllowedSet(stimAllowed);
		HashSet<String> concSet = normalizedAllowedSet(concAllowed);
		Iterator<Experiment> iterator = filteredList.iterator();
		while (iterator.hasNext()) {
			Experiment exp = iterator.next();
			try {
				ensureLoadedForSpotFields(exp);
				if (!experimentHasSpotMatchingStimAndConc(exp, stimSet, concSet))
					iterator.remove();
			} catch (Exception e) {
				iterator.remove();
			}
		}
	}

	private static boolean experimentHasSpotMatchingStimAndConc(Experiment exp, HashSet<String> stimSet,
			HashSet<String> concSet) {
		if (exp.getCages() == null || exp.getCages().cagesList == null)
			return false;
		if (exp.getSpots() == null)
			return false;
		for (Cage cage : exp.getCages().cagesList) {
			List<Spot> spotList = cage.getSpotList(exp.getSpots());
			if (spotList == null)
				continue;
			for (Spot spot : spotList) {
				String stim = normalizeFilterToken(spot.getField(EnumXLSColumnHeader.SPOT_STIM));
				String conc = normalizeFilterToken(spot.getField(EnumXLSColumnHeader.SPOT_CONC));
				if (stimSet.contains(stim) && concSet.contains(conc))
					return true;
			}
		}
		return false;
	}

}
