package plugins.fmp.multicafe.dlg.browse;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;
import plugins.fmp.multitools.tools.toExcel.enums.EnumXLSColumnHeader;

public class EditCapillariesConditional extends JPanel {
	private static final long serialVersionUID = 1L;

	private static final EnumXLSColumnHeader[] CONDITION_FIELDS = {
			EnumXLSColumnHeader.EXP_EXPT, EnumXLSColumnHeader.EXP_ID, EnumXLSColumnHeader.EXP_STIM1,
			EnumXLSColumnHeader.EXP_CONC1, EnumXLSColumnHeader.EXP_STRAIN, EnumXLSColumnHeader.EXP_SEX,
			EnumXLSColumnHeader.EXP_STIM2, EnumXLSColumnHeader.EXP_CONC2,
			EnumXLSColumnHeader.CAGE_SEX, EnumXLSColumnHeader.CAGE_STRAIN, EnumXLSColumnHeader.CAGE_AGE,
			EnumXLSColumnHeader.CAP_STIM, EnumXLSColumnHeader.CAP_CONC, EnumXLSColumnHeader.CAP_VOLUME };

	private JComboBox<EnumXLSColumnHeader> conditionField1Combo = new JComboBox<>(CONDITION_FIELDS);
	private JComboBox<String> conditionValue1Combo = new JComboBox<>();
	private JButton updateValue1Button = new JButton("Update");

	private JCheckBox useCondition2CheckBox = new JCheckBox("AND", false);
	private JComboBox<EnumXLSColumnHeader> conditionField2Combo = new JComboBox<>(CONDITION_FIELDS);
	private JComboBox<String> conditionValue2Combo = new JComboBox<>();
	private JButton updateValue2Button = new JButton("Update");

	private JComboBox<EnumXLSColumnHeader> targetFieldCombo = new JComboBox<>(CONDITION_FIELDS);
	private JTextField newValueTextField = new JTextField(10);
	private JButton applyButton = new JButton("Apply");

	private MultiCAFE parent0 = null;
	private JComboBoxExperimentLazy editExpList = new JComboBoxExperimentLazy();

	void init(GridLayout capLayout, MultiCAFE parent0) {
		this.parent0 = parent0;
		setBorder(javax.swing.BorderFactory.createTitledBorder("Edit descriptors"));
		setLayout(capLayout);

		FlowLayout flowlayout = new FlowLayout(FlowLayout.LEFT);
		flowlayout.setVgap(1);

		int bWidth = 100;
		int bHeight = 21;

		JPanel panel1 = new JPanel(flowlayout);
		conditionField1Combo.setPreferredSize(new Dimension(bWidth, bHeight));
		panel1.add(conditionField1Combo);
		panel1.add(new JLabel(" Value: "));
		bWidth = 200;
		conditionValue1Combo.setPreferredSize(new Dimension(bWidth, bHeight));
		panel1.add(conditionValue1Combo);
		updateValue1Button.setPreferredSize(new Dimension(80, bHeight));
		panel1.add(updateValue1Button);
		add(panel1);

		JPanel panel2 = new JPanel(flowlayout);
		useCondition2CheckBox.setPreferredSize(new Dimension(150, bHeight));
		panel2.add(useCondition2CheckBox);
		add(panel2);

		JPanel panel3 = new JPanel(flowlayout);
		bWidth = 100;
		conditionField2Combo.setPreferredSize(new Dimension(bWidth, bHeight));
		panel3.add(conditionField2Combo);
		panel3.add(new JLabel(" Value: "));
		bWidth = 200;
		conditionValue2Combo.setPreferredSize(new Dimension(bWidth, bHeight));
		panel3.add(conditionValue2Combo);
		updateValue2Button.setPreferredSize(new Dimension(80, bHeight));
		panel3.add(updateValue2Button);
		add(panel3);

		updateCondition2Enabled();

		JPanel panel4 = new JPanel(flowlayout);
		panel4.add(new JLabel("Change field: "));
		bWidth = 100;
		targetFieldCombo.setPreferredSize(new Dimension(bWidth, bHeight));
		panel4.add(targetFieldCombo);
		panel4.add(new JLabel(" to: "));
		bWidth = 200;
		newValueTextField.setPreferredSize(new Dimension(bWidth, bHeight));
		panel4.add(newValueTextField);
		applyButton.setPreferredSize(new Dimension(80, bHeight));
		panel4.add(applyButton);
		add(panel4);

		defineActionListeners();
		initEditCombos();
	}

	public void initEditCombos() {
		syncEditExpListFromBrowse();
		updateConditionValueCombo(conditionField1Combo, conditionValue1Combo);
		updateConditionValueCombo(conditionField2Combo, conditionValue2Combo);
	}

	/**
	 * When Filter is active, the Browse combo holds only matching experiments — Apply
	 * uses that list. When not filtered, use Filter's master copy of the full series.
	 */
	private void syncEditExpListFromBrowse() {
		parent0.paneBrowse.filterPanel.initCombos();
		JComboBoxExperimentLazy src;
		if (parent0.paneBrowse.browsePanel.isListFiltered()) {
			src = parent0.expListComboLazy;
		} else {
			src = parent0.paneBrowse.filterPanel.filterExpList;
			if (src.getItemCount() < 1)
				src = parent0.expListComboLazy;
		}
		editExpList.setExperimentsFromList(src.getExperimentsAsListNoLoad());
	}

	private void updateConditionValueCombo(JComboBox<EnumXLSColumnHeader> fieldCombo, JComboBox<String> valueCombo) {
		EnumXLSColumnHeader selectedField = (EnumXLSColumnHeader) fieldCombo.getSelectedItem();
		if (selectedField != null) {
			syncEditExpListFromBrowse();
			editExpList.getFieldValuesToComboLightweight(valueCombo, selectedField);
		}
	}

	private void defineActionListeners() {
		updateValue1Button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				updateConditionValueCombo(conditionField1Combo, conditionValue1Combo);
			}
		});

		updateValue2Button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				updateConditionValueCombo(conditionField2Combo, conditionValue2Combo);
			}
		});

		conditionField1Combo.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				updateConditionValueCombo(conditionField1Combo, conditionValue1Combo);
			}
		});

		conditionField2Combo.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				updateConditionValueCombo(conditionField2Combo, conditionValue2Combo);
			}
		});

		useCondition2CheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				updateCondition2Enabled();
			}
		});

		applyButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				applyChange();
				newValueTextField.setText("");
				initEditCombos();
			}
		});
	}

	private void updateCondition2Enabled() {
		boolean enabled = useCondition2CheckBox.isSelected();
		conditionField2Combo.setEnabled(enabled);
		conditionValue2Combo.setEnabled(enabled);
		updateValue2Button.setEnabled(enabled);
	}

	void applyChange() {
		EnumXLSColumnHeader conditionField1 = (EnumXLSColumnHeader) conditionField1Combo.getSelectedItem();
		String conditionValue1 = (String) conditionValue1Combo.getSelectedItem();
		EnumXLSColumnHeader targetField = (EnumXLSColumnHeader) targetFieldCombo.getSelectedItem();
		String newValue = newValueTextField.getText();

		if (conditionField1 == null || conditionValue1 == null || targetField == null || newValue == null
				|| newValue.isEmpty()) {
			Logger.warn("EditCapillariesConditional: Missing required fields");
			return;
		}

		boolean useCondition2 = useCondition2CheckBox.isSelected();
		EnumXLSColumnHeader conditionField2 = null;
		String conditionValue2 = null;

		if (useCondition2) {
			conditionField2 = (EnumXLSColumnHeader) conditionField2Combo.getSelectedItem();
			conditionValue2 = (String) conditionValue2Combo.getSelectedItem();
			if (conditionField2 == null || conditionValue2 == null) {
				Logger.warn("EditCapillariesConditional: Condition 2 is enabled but missing values");
				return;
			}
			if (conditionField1 == conditionField2) {
				Logger.warn("EditCapillariesConditional: Condition fields must be different");
				return;
			}
		}

		boolean condition1IsCage = isCageField(conditionField1);
		boolean condition1IsCapillary = isCapillaryField(conditionField1);
		boolean condition2IsCage = useCondition2 && isCageField(conditionField2);
		boolean condition2IsCapillary = useCondition2 && isCapillaryField(conditionField2);
		boolean targetIsCage = isCageField(targetField);
		boolean targetIsCapillary = isCapillaryField(targetField);

		syncEditExpListFromBrowse();
		int nExperiments = editExpList.getItemCount();
		int totalUpdated = 0;

		for (int i = 0; i < nExperiments; i++) {
			Experiment exp = editExpList.getItemAtNoLoad(i);
			if (exp == null)
				continue;

			waitForSaveToComplete(exp, i);

			exp.loadExperimentDescriptors();
			exp.load_cages_description_and_measures();

			if (condition1IsCapillary || condition2IsCapillary || targetIsCapillary) {
				exp.load_capillaries_description_and_measures();
			}

			int updated = replaceFieldWithConditions(exp, conditionField1, conditionValue1, useCondition2, conditionField2,
					conditionValue2, targetField, newValue, condition1IsCage, condition1IsCapillary, condition2IsCage,
					condition2IsCapillary, targetIsCage, targetIsCapillary);

			if (updated > 0) {
				if (targetIsCapillary) {
					exp.saveMCCapillaries_Only();
					exp.save_capillaries_description_and_measures();
				} else if (targetIsCage) {
					exp.save_cages_description_and_measures();
				} else {
					exp.saveExperimentDescriptors();
					exp.save_cages_description_and_measures();
				}
				totalUpdated += updated;
			}
		}

		String updateType = targetIsCapillary ? "capillaries" : (targetIsCage ? "cages" : "experiments");
		Logger.info("EditCapillariesConditional: Updated " + totalUpdated + " " + updateType + " across "
				+ nExperiments + " experiment(s) in the current Browse list");
	}

	private boolean isCapillaryField(EnumXLSColumnHeader field) {
		return field == EnumXLSColumnHeader.CAP_STIM || field == EnumXLSColumnHeader.CAP_CONC
				|| field == EnumXLSColumnHeader.CAP_VOLUME;
	}

	private boolean isCageField(EnumXLSColumnHeader field) {
		return field == EnumXLSColumnHeader.CAGE_SEX || field == EnumXLSColumnHeader.CAGE_STRAIN
				|| field == EnumXLSColumnHeader.CAGE_AGE;
	}

	private int replaceFieldWithConditions(Experiment exp, EnumXLSColumnHeader conditionField1, String conditionValue1,
			boolean useCondition2, EnumXLSColumnHeader conditionField2, String conditionValue2,
			EnumXLSColumnHeader targetField, String newValue, boolean condition1IsCage, boolean condition1IsCapillary,
			boolean condition2IsCage, boolean condition2IsCapillary, boolean targetIsCage, boolean targetIsCapillary) {

		boolean condition1IsExp = !condition1IsCage && !condition1IsCapillary;
		boolean condition2IsExp = useCondition2 && !condition2IsCage && !condition2IsCapillary;
		boolean anyCageCond = condition1IsCage || condition2IsCage;
		boolean anyCapCond = condition1IsCapillary || condition2IsCapillary;

		if (condition1IsExp) {
			String expValue1 = exp.getExperimentField(conditionField1);
			if (expValue1 == null || !expValue1.equals(conditionValue1))
				return 0;
		}
		if (condition2IsExp) {
			String expValue2 = exp.getExperimentField(conditionField2);
			if (expValue2 == null || !expValue2.equals(conditionValue2))
				return 0;
		}

		List<Cage> cages = exp.getCages() != null ? exp.getCages().cagesList : null;
		if (cages == null)
			cages = Collections.emptyList();

		if (!anyCageCond && !anyCapCond && !targetIsCage && !targetIsCapillary) {
			exp.setExperimentFieldNoTest(targetField, newValue);
			return 1;
		}

		if (!anyCageCond && !anyCapCond) {
			if (targetIsCage) {
				int updated = 0;
				for (Cage cage : cages) {
					cage.setField(targetField, newValue);
					updated++;
				}
				return updated;
			}
			if (targetIsCapillary) {
				int updated = 0;
				for (Capillary cap : getAllCapillaries(exp)) {
					cap.setField(targetField, newValue);
					updated++;
				}
				return updated;
			}
		}

		int updated = 0;
		for (Cage cage : cages) {
			if (anyCageCond && !cageMatchesConditions(cage, conditionField1, conditionValue1, condition1IsCage,
					useCondition2, conditionField2, conditionValue2, condition2IsCage)) {
				continue;
			}

			List<Capillary> capsInCage = capillariesForCage(exp, cage);

			if (!anyCapCond) {
				if (targetIsCage) {
					cage.setField(targetField, newValue);
					updated++;
				} else if (targetIsCapillary) {
					for (Capillary cap : capsInCage) {
						cap.setField(targetField, newValue);
						updated++;
					}
				} else {
					exp.setExperimentFieldNoTest(targetField, newValue);
					return 1;
				}
				continue;
			}

			boolean cageTargetDone = false;
			for (Capillary cap : capsInCage) {
				if (!capillaryMatchesConditions(cap, conditionField1, conditionValue1, condition1IsCapillary,
						useCondition2, conditionField2, conditionValue2, condition2IsCapillary)) {
					continue;
				}
				if (targetIsCapillary) {
					cap.setField(targetField, newValue);
					updated++;
				} else if (targetIsCage) {
					if (!cageTargetDone) {
						cage.setField(targetField, newValue);
						updated++;
						cageTargetDone = true;
					}
					break;
				} else {
					exp.setExperimentFieldNoTest(targetField, newValue);
					return 1;
				}
			}
		}

		// Capillary conditions with no cages loaded: fall back to flat capillary list
		if (anyCapCond && cages.isEmpty()) {
			for (Capillary cap : getAllCapillaries(exp)) {
				if (!capillaryMatchesConditions(cap, conditionField1, conditionValue1, condition1IsCapillary,
						useCondition2, conditionField2, conditionValue2, condition2IsCapillary)) {
					continue;
				}
				if (targetIsCapillary) {
					cap.setField(targetField, newValue);
					updated++;
				} else if (!targetIsCage) {
					exp.setExperimentFieldNoTest(targetField, newValue);
					return 1;
				}
			}
		}

		return updated;
	}

	private List<Capillary> getAllCapillaries(Experiment exp) {
		if (exp.getCapillaries() == null || exp.getCapillaries().getList() == null)
			return Collections.emptyList();
		return exp.getCapillaries().getList();
	}

	private List<Capillary> capillariesForCage(Experiment exp, Cage cage) {
		List<Capillary> fromIds = cage.getCapillaries(exp.getCapillaries());
		if (fromIds != null && !fromIds.isEmpty())
			return fromIds;
		List<Capillary> byCageId = new ArrayList<>();
		int cageId = cage.getCageID();
		for (Capillary cap : getAllCapillaries(exp)) {
			if (cap.getCageID() == cageId)
				byCageId.add(cap);
		}
		return byCageId;
	}

	private boolean cageMatchesConditions(Cage cage, EnumXLSColumnHeader conditionField1, String conditionValue1,
			boolean condition1IsCage, boolean useCondition2, EnumXLSColumnHeader conditionField2,
			String conditionValue2, boolean condition2IsCage) {
		if (condition1IsCage) {
			String v = cage.getField(conditionField1);
			if (v == null || !v.equals(conditionValue1))
				return false;
		}
		if (useCondition2 && condition2IsCage) {
			String v = cage.getField(conditionField2);
			if (v == null || !v.equals(conditionValue2))
				return false;
		}
		return true;
	}

	private boolean capillaryMatchesConditions(Capillary cap, EnumXLSColumnHeader conditionField1,
			String conditionValue1, boolean condition1IsCapillary, boolean useCondition2,
			EnumXLSColumnHeader conditionField2, String conditionValue2, boolean condition2IsCapillary) {
		if (condition1IsCapillary) {
			String v = cap.getField(conditionField1);
			if (v == null || !v.equals(conditionValue1))
				return false;
		}
		if (useCondition2 && condition2IsCapillary) {
			String v = cap.getField(conditionField2);
			if (v == null || !v.equals(conditionValue2))
				return false;
		}
		return true;
	}

	private void waitForSaveToComplete(Experiment exp, int expIndex) {
		if (!exp.isSaving()) {
			return;
		}

		long timeoutMs = 30000;
		long startTime = System.currentTimeMillis();
		long pollIntervalMs = 100;

		Logger.info("Waiting for save operation to complete for experiment [" + expIndex + "]: " + exp.toString());

		while (exp.isSaving() && (System.currentTimeMillis() - startTime) < timeoutMs) {
			try {
				Thread.sleep(pollIntervalMs);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				Logger.warn("Interrupted while waiting for save to complete for experiment [" + expIndex + "]");
				return;
			}
		}

		if (exp.isSaving()) {
			Logger.warn("Timeout waiting for save operation to complete for experiment [" + expIndex
					+ "]. Proceeding anyway, but save may not have completed: " + exp.toString());
		} else {
			Logger.info("Save operation completed for experiment [" + expIndex + "]");
		}
	}
}
