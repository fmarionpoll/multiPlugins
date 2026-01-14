package plugins.fmp.multicafe.dlg.experiment;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import plugins.fmp.multitools.fmp_tools.Logger;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.fmp_experiment.Experiment;
import plugins.fmp.multitools.fmp_experiment.capillaries.Capillary;
import plugins.fmp.multitools.fmp_tools.toExcel.enums.EnumXLSColumnHeader;

public class EditCapillariesConditional extends JPanel {
	private static final long serialVersionUID = 1L;

	// Available fields for conditions - all fields from Edit.java
	private static final EnumXLSColumnHeader[] CONDITION_FIELDS = {
			// Experiment-level fields
			EnumXLSColumnHeader.EXP_EXPT, EnumXLSColumnHeader.EXP_BOXID, EnumXLSColumnHeader.EXP_STIM1,
			EnumXLSColumnHeader.EXP_CONC1, EnumXLSColumnHeader.EXP_STRAIN, EnumXLSColumnHeader.EXP_SEX,
			EnumXLSColumnHeader.EXP_STIM2, EnumXLSColumnHeader.EXP_CONC2,
			// Capillary-level fields
			EnumXLSColumnHeader.CAP_STIM, EnumXLSColumnHeader.CAP_CONC, EnumXLSColumnHeader.CAP_VOLUME };

	// UI Components
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

	void init(GridLayout capLayout, MultiCAFE parent0) {
		this.parent0 = parent0;
		setLayout(capLayout);

		FlowLayout flowlayout = new FlowLayout(FlowLayout.LEFT);
		flowlayout.setVgap(1);

		int bWidth = 100;
		int bHeight = 21;

		// Condition 1: Field and Value
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

		// Condition 2: Optional second condition with checkbox
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

		// Initially disable condition 2 components
		updateCondition2Enabled();

		// Target field and new value
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
		updateConditionValueCombo(conditionField1Combo, conditionValue1Combo);
		updateConditionValueCombo(conditionField2Combo, conditionValue2Combo);
	}

	private void updateConditionValueCombo(JComboBox<EnumXLSColumnHeader> fieldCombo, JComboBox<String> valueCombo) {
		EnumXLSColumnHeader selectedField = (EnumXLSColumnHeader) fieldCombo.getSelectedItem();
		if (selectedField != null) {
			parent0.expListComboLazy.getFieldValuesToComboLightweight(valueCombo, selectedField);
		}
	}

	private void defineActionListeners() {
		// Update button for condition 1
		updateValue1Button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				updateConditionValueCombo(conditionField1Combo, conditionValue1Combo);
			}
		});

		// Update button for condition 2
		updateValue2Button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				updateConditionValueCombo(conditionField2Combo, conditionValue2Combo);
			}
		});

		// Field change listeners to update value combos
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

		// Checkbox to enable/disable condition 2
		useCondition2CheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				updateCondition2Enabled();
			}
		});

		// Apply button
		applyButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				applyChange();
				newValueTextField.setText("");
				initEditCombos();
			}
		});
	}

	/**
	 * Updates the enabled state of condition 2 components based on checkbox.
	 */
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

		// Validation for required fields
		if (conditionField1 == null || conditionValue1 == null || targetField == null || newValue == null
				|| newValue.isEmpty()) {
			Logger.warn("EditCapillariesConditional: Missing required fields");
			return;
		}

		// Condition 2 is optional
		boolean useCondition2 = useCondition2CheckBox.isSelected();
		EnumXLSColumnHeader conditionField2 = null;
		String conditionValue2 = null;

		if (useCondition2) {
			conditionField2 = (EnumXLSColumnHeader) conditionField2Combo.getSelectedItem();
			conditionValue2 = (String) conditionValue2Combo.getSelectedItem();

			// Validate condition 2 if enabled
			if (conditionField2 == null || conditionValue2 == null) {
				Logger.warn("EditCapillariesConditional: Condition 2 is enabled but missing values");
				return;
			}

			// Ensure condition fields are different
			if (conditionField1 == conditionField2) {
				Logger.warn("EditCapillariesConditional: Condition fields must be different");
				return;
			}
		}

		// Determine field types
		boolean condition1IsCapillary = isCapillaryField(conditionField1);
		boolean condition2IsCapillary = useCondition2 && isCapillaryField(conditionField2);
		boolean targetIsCapillary = isCapillaryField(targetField);

		int nExperiments = parent0.expListComboLazy.getItemCount();
		int totalUpdated = 0;

		for (int i = 0; i < nExperiments; i++) {
			Experiment exp = parent0.expListComboLazy.getItemAt(i);

			// Wait for any ongoing async save operations to complete
			waitForSaveToComplete(exp, i);

			exp.loadExperimentDescriptors();
			exp.load_cages_description_and_measures();

			// Only load capillaries if needed
			if (condition1IsCapillary || (useCondition2 && condition2IsCapillary) || targetIsCapillary) {
				exp.load_capillaries_description_and_measures();
			}

			int updated = replaceFieldWithConditions(exp, conditionField1, conditionValue1, useCondition2,
					conditionField2, conditionValue2, targetField, newValue, condition1IsCapillary,
					condition2IsCapillary, targetIsCapillary);

			// Save based on what was updated
			if (updated > 0) {
				if (targetIsCapillary) {
					exp.saveMCCapillaries_Only();
					exp.save_capillaries_description_and_measures();
				} else {
					exp.saveExperimentDescriptors();
					exp.save_cages_description_and_measures();
				}
				totalUpdated += updated;
			}
		}

		String updateType = targetIsCapillary ? "capillaries" : "experiments";
		Logger.info(
				"EditCapillariesConditional: Updated " + totalUpdated + " " + updateType + " across all experiments");
	}

	/**
	 * Checks if a field is a capillary-level field.
	 */
	private boolean isCapillaryField(EnumXLSColumnHeader field) {
		return field == EnumXLSColumnHeader.CAP_STIM || field == EnumXLSColumnHeader.CAP_CONC
				|| field == EnumXLSColumnHeader.CAP_VOLUME;
	}

	/**
	 * Replaces the target field value based on one or two conditions. Handles: -
	 * Experiment-level fields as conditions/targets - Capillary-level fields as
	 * conditions/targets - Mixed combinations (experiment + capillary) - Single
	 * condition (when useCondition2 is false)
	 * 
	 * @param exp                   The experiment
	 * @param conditionField1       First condition field (required)
	 * @param conditionValue1       First condition value to match (required)
	 * @param useCondition2         Whether to use a second condition
	 * @param conditionField2       Second condition field (null if useCondition2 is
	 *                              false)
	 * @param conditionValue2       Second condition value to match (null if
	 *                              useCondition2 is false)
	 * @param targetField           Field to update
	 * @param newValue              New value to set
	 * @param condition1IsCapillary Whether condition1 is a capillary field
	 * @param condition2IsCapillary Whether condition2 is a capillary field
	 * @param targetIsCapillary     Whether target is a capillary field
	 * @return Number of items updated (experiments or capillaries)
	 */
	private int replaceFieldWithConditions(Experiment exp, EnumXLSColumnHeader conditionField1, String conditionValue1,
			boolean useCondition2, EnumXLSColumnHeader conditionField2, String conditionValue2,
			EnumXLSColumnHeader targetField, String newValue, boolean condition1IsCapillary,
			boolean condition2IsCapillary, boolean targetIsCapillary) {

		// Check condition 1
		String expValue1 = null;
		boolean condition1Matches = false;

		if (condition1IsCapillary) {
			// For capillary conditions, we need to check if ANY capillary matches
			// This will be checked per-capillary in the loop below
			condition1Matches = true; // Will be checked per capillary
		} else {
			// Experiment-level condition
			expValue1 = exp.getExperimentField(conditionField1);
			condition1Matches = (expValue1 != null && expValue1.equals(conditionValue1));
		}

		// Check condition 2 (if used)
		String expValue2 = null;
		boolean condition2Matches = true; // Default to true if condition 2 is not used

		if (useCondition2) {
			if (condition2IsCapillary) {
				// For capillary conditions, we need to check if ANY capillary matches
				condition2Matches = true; // Will be checked per capillary
			} else {
				// Experiment-level condition
				expValue2 = exp.getExperimentField(conditionField2);
				condition2Matches = (expValue2 != null && expValue2.equals(conditionValue2));
			}
		}

		// If both conditions are experiment-level (or only condition 1 if condition 2
		// not used)
		if (!condition1IsCapillary && (!useCondition2 || !condition2IsCapillary)) {
			if (condition1Matches && condition2Matches) {
				if (targetIsCapillary) {
					// Update all capillaries in this experiment
					int updated = 0;
					for (Capillary cap : exp.getCapillaries().getList()) {
						cap.setField(targetField, newValue);
						updated++;
					}
					return updated;
				} else {
					// Update experiment field
					exp.setExperimentFieldNoTest(targetField, newValue);
					return 1;
				}
			}
			return 0;
		}

		// If at least one condition is capillary-level, we need to iterate through
		// capillaries
		if (condition1IsCapillary || (useCondition2 && condition2IsCapillary) || targetIsCapillary) {
			// First check experiment-level conditions if any
			if (!condition1IsCapillary && !condition1Matches) {
				return 0; // Experiment doesn't match condition 1
			}
			if (useCondition2 && !condition2IsCapillary && !condition2Matches) {
				return 0; // Experiment doesn't match condition 2
			}

			// Now check capillaries
			int updated = 0;
			for (Capillary cap : exp.getCapillaries().getList()) {
				boolean capMatchesCondition1 = true;
				boolean capMatchesCondition2 = true;

				// Check capillary condition 1
				if (condition1IsCapillary) {
					String capValue1 = cap.getField(conditionField1);
					capMatchesCondition1 = (capValue1 != null && capValue1.equals(conditionValue1));
				}

				// Check capillary condition 2 (if used)
				if (useCondition2 && condition2IsCapillary) {
					String capValue2 = cap.getField(conditionField2);
					capMatchesCondition2 = (capValue2 != null && capValue2.equals(conditionValue2));
				}

				// If all conditions match, update the target
				if (capMatchesCondition1 && capMatchesCondition2) {
					if (targetIsCapillary) {
						cap.setField(targetField, newValue);
						updated++;
					} else {
						// Update experiment field (but only once per experiment)
						if (updated == 0) {
							exp.setExperimentFieldNoTest(targetField, newValue);
							updated = 1;
						}
						// Break after first match since experiment field is the same for all
						// capillaries
						break;
					}
				}
			}
			return updated;
		}

		return 0;
	}

	/**
	 * Waits for any ongoing async save operation to complete for the given
	 * experiment. This prevents conflicts between Edit's synchronous saves and
	 * LoadSaveExperiment's async saves.
	 * 
	 * @param exp      The experiment to wait for
	 * @param expIndex The index of the experiment (for logging)
	 */
	private void waitForSaveToComplete(Experiment exp, int expIndex) {
		if (!exp.isSaving()) {
			return; // No save in progress, proceed immediately
		}

		// Wait for save to complete with a timeout to avoid infinite waits
		long timeoutMs = 30000; // 30 seconds timeout
		long startTime = System.currentTimeMillis();
		long pollIntervalMs = 100; // Check every 100ms

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
