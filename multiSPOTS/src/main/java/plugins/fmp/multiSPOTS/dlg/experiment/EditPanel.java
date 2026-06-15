package plugins.fmp.multiSPOTS.dlg.experiment;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

import icy.gui.frame.progress.ProgressFrame;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.LazyExperiment;
import plugins.fmp.multitools.experiment.persistence.MigrationBackupFieldRestore;
import plugins.fmp.multitools.tools.DescriptorsIO;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;
import plugins.fmp.multitools.tools.toExcel.enums.EnumXLSColumnHeader;

public class EditPanel extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2190848825783418962L;

	private JComboBox<EnumXLSColumnHeader> fieldNamesCombo = new JComboBox<EnumXLSColumnHeader>(
			new EnumXLSColumnHeader[] { EnumXLSColumnHeader.EXP_EXPT, EnumXLSColumnHeader.EXP_ID,
					EnumXLSColumnHeader.EXP_STIM1, EnumXLSColumnHeader.EXP_CONC1, EnumXLSColumnHeader.EXP_STRAIN,
					EnumXLSColumnHeader.EXP_SEX, EnumXLSColumnHeader.EXP_STIM2, EnumXLSColumnHeader.EXP_CONC2,
					EnumXLSColumnHeader.SPOT_STIM, EnumXLSColumnHeader.SPOT_CONC, EnumXLSColumnHeader.SPOT_VOLUME,
					EnumXLSColumnHeader.CAGE_SEX, EnumXLSColumnHeader.CAGE_STRAIN, EnumXLSColumnHeader.CAGE_AGE });

	private JComboBox<String> fieldOldValuesCombo = new JComboBox<String>();
	private JButton refreshButton = new JButton("Refresh");
	private JButton restoreFromBackupButton = new JButton("Restore from backup");
	private JTextField newValueTextField = new JTextField(10);
	private JButton applyButton = new JButton("Apply");
	private JButton undoLastApplyButton = new JButton("Undo last apply");
	private JLabel statusLabel = new JLabel("");

	private MultiSPOTS parent0 = null;
	JComboBoxExperimentLazy editExpList = new JComboBoxExperimentLazy();
	private EditApplyUndoSnapshot lastApplyUndoSnapshot = null;

	void init(GridLayout capLayout, MultiSPOTS parent0) {
		this.parent0 = parent0;
		setLayout(capLayout);

		FlowLayout flowlayout = new FlowLayout(FlowLayout.LEFT);
		flowlayout.setVgap(1);

		int bWidth = 100;
		int bHeight = 21;

		JPanel panel0 = new JPanel(flowlayout);
		panel0.add(new JLabel("Field name "));
		panel0.add(fieldNamesCombo);
		fieldNamesCombo.setPreferredSize(new Dimension(bWidth, bHeight));
		panel0.add(refreshButton);
		restoreFromBackupButton.setToolTipText(
				"Reload the selected field from MS96_experiment.xml / MS96_cages.xml in backup_before_migration (migrated experiments only).");
		panel0.add(restoreFromBackupButton);
		add(panel0);

		bWidth = 200;
		JPanel panel1 = new JPanel(flowlayout);
		panel1.add(new JLabel("Field value "));
		panel1.add(fieldOldValuesCombo);
		fieldOldValuesCombo.setPreferredSize(new Dimension(bWidth, bHeight));
		add(panel1);

		JPanel panel2 = new JPanel(flowlayout);
		panel2.add(new JLabel("replace with"));
		panel2.add(newValueTextField);
		newValueTextField.setPreferredSize(new Dimension(bWidth, bHeight));
		panel2.add(applyButton);
		undoLastApplyButton.setToolTipText(
				"Reverts the last successful Apply for the same field (one step, in memory only).");
		panel2.add(undoLastApplyButton);
		add(panel2);

		JPanel panel3 = new JPanel(flowlayout);
		panel3.add(statusLabel);
		add(panel3);

		defineActionListeners();
		updateUndoLastApplyButtonState();
	}

	private void syncEditExpListFromProject() {
		parent0.dlgExperiment.filterPanel.initCombos();
		JComboBoxExperimentLazy src;
		if (parent0.dlgBrowse.loadSaveExperiment.filteredCheck.isSelected())
			src = parent0.expListComboLazy;
		else {
			src = parent0.dlgExperiment.filterPanel.filterExpList;
			if (src.getItemCount() < 1)
				src = parent0.expListComboLazy;
		}
		editExpList.setExperimentsFromList(src.getExperimentsAsListNoLoad());
	}

	private static boolean fieldUsesLiveCageOrSpotScan(EnumXLSColumnHeader field) {
		switch (field) {
		case CAGE_SEX:
		case CAGE_STRAIN:
		case CAGE_AGE:
		case SPOT_STIM:
		case SPOT_CONC:
		case SPOT_VOLUME:
			return true;
		default:
			return false;
		}
	}

	public void initEditCombos() {
		syncEditExpListFromProject();
		EnumXLSColumnHeader field = (EnumXLSColumnHeader) fieldNamesCombo.getSelectedItem();
		fieldOldValuesCombo.removeAllItems();
		if (fieldUsesLiveCageOrSpotScan(field)) {
			editExpList.getFieldValuesToComboLightweight(fieldOldValuesCombo, field);
			updateRestoreFromBackupButtonState();
			updateUndoLastApplyButtonState();
			return;
		}
		java.util.List<String> values;
		if (parent0.descriptorIndex != null && parent0.descriptorIndex.isReady()) {
			values = parent0.descriptorIndex.getDistinctValues(field);
		} else {
			editExpList.getFieldValuesToComboLightweight(fieldOldValuesCombo, field);
			updateRestoreFromBackupButtonState();
			updateUndoLastApplyButtonState();
			return;
		}
		java.util.Collections.sort(values);
		for (String v : values)
			fieldOldValuesCombo.addItem(v);
		updateRestoreFromBackupButtonState();
		updateUndoLastApplyButtonState();
	}

	private void updateUndoLastApplyButtonState() {
		boolean ok = lastApplyUndoSnapshot != null && !lastApplyUndoSnapshot.isEmpty()
				&& lastApplyUndoSnapshot.getField() == fieldNamesCombo.getSelectedItem();
		undoLastApplyButton.setEnabled(ok);
	}

	private void clearApplyUndoSnapshot() {
		lastApplyUndoSnapshot = null;
		updateUndoLastApplyButtonState();
	}

	private void updateRestoreFromBackupButtonState() {
		int n = editExpList.getItemCount();
		boolean any = false;
		for (int i = 0; i < n; i++) {
			Experiment exp = editExpList.getItemAtNoLoad(i);
			if (exp == null) {
				continue;
			}
			String rd = exp.getResultsDirectory();
			if (rd == null && exp instanceof LazyExperiment) {
				LazyExperiment le = (LazyExperiment) exp;
				if (le.getMetadata() != null) {
					rd = le.getMetadata().getResultsDirectory();
				}
			}
			if (rd != null && MigrationBackupFieldRestore.isMigrationBackupPresent(rd)) {
				any = true;
				break;
			}
		}
		restoreFromBackupButton.setEnabled(any);
	}

	private void defineActionListeners() {
		applyButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				applyChange();
				newValueTextField.setText("");
			}
		});

		fieldNamesCombo.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				clearApplyUndoSnapshot();
				syncEditExpListFromProject();
				EnumXLSColumnHeader field = (EnumXLSColumnHeader) fieldNamesCombo.getSelectedItem();
				fieldOldValuesCombo.removeAllItems();
				if (fieldUsesLiveCageOrSpotScan(field)) {
					editExpList.getFieldValuesToComboLightweight(fieldOldValuesCombo, field);
					updateRestoreFromBackupButtonState();
					updateUndoLastApplyButtonState();
					return;
				}
				java.util.List<String> values;
				if (parent0.descriptorIndex != null && parent0.descriptorIndex.isReady()) {
					values = parent0.descriptorIndex.getDistinctValues(field);
					java.util.Collections.sort(values);
					for (String v : values)
						fieldOldValuesCombo.addItem(v);
				} else {
					editExpList.getFieldValuesToComboLightweight(fieldOldValuesCombo, field);
				}
				updateRestoreFromBackupButtonState();
				updateUndoLastApplyButtonState();
			}
		});

		refreshButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				final ProgressFrame pf = new ProgressFrame("Refreshing descriptors");
				parent0.dlgExperiment.filterPanel.initCombos();
				JComboBoxExperimentLazy indexSource = parent0.dlgExperiment.filterPanel.filterExpList;
				if (indexSource.getItemCount() < 1)
					indexSource = parent0.expListComboLazy;
				parent0.descriptorIndex.preloadFromCombo(indexSource, new Runnable() {
					@Override
					public void run() {
						pf.close();
						initEditCombos();
					}
				});
			}
		});

		restoreFromBackupButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				restoreFieldFromMigrationBackup();
			}
		});

		undoLastApplyButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				undoLastApply();
			}
		});

	}

	void applyChange() {
		syncEditExpListFromProject();
		final int nExperiments = editExpList.getItemCount();
		final EnumXLSColumnHeader fieldEnumCode = (EnumXLSColumnHeader) fieldNamesCombo.getSelectedItem();
		final String oldValue = (String) fieldOldValuesCombo.getSelectedItem();
		final String newValue = newValueTextField.getText();

		final ProgressFrame progress = new ProgressFrame("Apply changes to " + fieldEnumCode);
		progress.setLength(nExperiments);
		statusLabel.setText("Applying changes to " + fieldEnumCode + "...");
		applyButton.setEnabled(false);
		refreshButton.setEnabled(false);
		restoreFromBackupButton.setEnabled(false);
		undoLastApplyButton.setEnabled(false);
		fieldNamesCombo.setEnabled(false);
		fieldOldValuesCombo.setEnabled(false);
		newValueTextField.setEnabled(false);
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

		SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
			private boolean anyChanged = false;
			private final EditApplyUndoSnapshot[] snapshotHolder = new EditApplyUndoSnapshot[1];

			@Override
			protected Void doInBackground() throws Exception {
				snapshotHolder[0] = EditApplyUndoSnapshot.capture(editExpList, nExperiments, fieldEnumCode, oldValue);
				for (int i = 0; i < nExperiments; i++) {
					Experiment exp = editExpList.getItemAtNoLoad(i);
					boolean isChanged = false;
					progress.setMessage("Updating (" + (i + 1) + "/" + nExperiments + ")");
					if (exp == null) {
						Logger.warn("Edit.applyChange: null experiment at index " + i);
						progress.incPosition();
						continue;
					}
					// Apply change without triggering image loads
					switch (fieldEnumCode) {
					case EXP_EXPT:
					case EXP_ID:
					case EXP_STIM1:
					case EXP_CONC1:
					case EXP_STRAIN:
					case EXP_SEX:
					case EXP_STIM2:
					case EXP_CONC2:
						exp.loadExperimentDescriptors();
						isChanged = exp.replaceExperimentFieldIfEqualOldValue(fieldEnumCode, oldValue, newValue);
						if (isChanged)
							exp.saveExperimentDescriptors();
						break;
					case CAGE_SEX:
					case CAGE_STRAIN:
					case CAGE_AGE:
						isChanged = exp.replaceCageFieldValueWithNewValueIfOld(fieldEnumCode, oldValue, newValue);
						if (isChanged)
							exp.save_cages_description_and_measures();
						break;
					case SPOT_STIM:
					case SPOT_CONC:
					case SPOT_VOLUME:
						isChanged = exp.replaceSpotsFieldValueWithNewValueIfOld(fieldEnumCode, oldValue, newValue);
						if (isChanged) {
							exp.save_spots_description_and_measures();
						}
						break;
					default:
						break;
					}
					// keep descriptors file in sync for this experiment
					if (isChanged)
						DescriptorsIO.buildFromExperiment(exp);
					anyChanged |= isChanged;

					progress.incPosition();
				}
				return null;
			}

			@Override
			protected void done() {
				progress.close();
				statusLabel.setText("Done applying changes to " + fieldEnumCode + ".");
				if (anyChanged && snapshotHolder[0] != null && !snapshotHolder[0].isEmpty()) {
					lastApplyUndoSnapshot = snapshotHolder[0];
				}
				applyButton.setEnabled(true);
				refreshButton.setEnabled(true);
				updateRestoreFromBackupButtonState();
				updateUndoLastApplyButtonState();
				fieldNamesCombo.setEnabled(true);
				fieldOldValuesCombo.setEnabled(true);
				newValueTextField.setEnabled(true);
				setCursor(Cursor.getDefaultCursor());
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					exp.load_spots_description_and_measures();
					parent0.dlgMeasure.chartsPanel.displayChartPanels(exp);
				}
				if (anyChanged && parent0.descriptorIndex != null) {
					final ProgressFrame pf = new ProgressFrame("Refreshing descriptors");
					parent0.dlgExperiment.filterPanel.initCombos();
					JComboBoxExperimentLazy indexSource = parent0.dlgExperiment.filterPanel.filterExpList;
					if (indexSource.getItemCount() < 1)
						indexSource = parent0.expListComboLazy;
					parent0.descriptorIndex.preloadFromCombo(indexSource, new Runnable() {
						@Override
						public void run() {
							pf.close();
							parent0.dlgExperiment.infosPanel.initCombos();
							parent0.dlgExperiment.filterPanel.initCombos();
							initEditCombos();
						}
					});
				} else {
					parent0.dlgExperiment.infosPanel.initCombos();
					parent0.dlgExperiment.filterPanel.initCombos();
					initEditCombos();
				}
			}
		};
		worker.execute();
	}

	void restoreFieldFromMigrationBackup() {
		syncEditExpListFromProject();
		final int nExperiments = editExpList.getItemCount();
		final EnumXLSColumnHeader fieldEnumCode = (EnumXLSColumnHeader) fieldNamesCombo.getSelectedItem();
		if (nExperiments < 1) {
			statusLabel.setText("No experiments in the edit list.");
			return;
		}
		int r = JOptionPane.showConfirmDialog(this,
				"Replace the selected field \"" + fieldEnumCode
						+ "\" for every experiment in the list\nwith values read from backup_before_migration/MS96_*.xml,\n"
						+ "when that backup exists for the experiment's results folder.\n"
						+ "Spots are matched by spot ID when present in the backup, otherwise by cage ID and cage position.\n\n"
						+ "Continue?",
				"Restore from migration backup", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (r != JOptionPane.YES_OPTION) {
			return;
		}

		final ProgressFrame progress = new ProgressFrame("Restore from migration backup: " + fieldEnumCode);
		progress.setLength(nExperiments);
		statusLabel.setText("Restoring " + fieldEnumCode + " from backup...");
		applyButton.setEnabled(false);
		refreshButton.setEnabled(false);
		restoreFromBackupButton.setEnabled(false);
		undoLastApplyButton.setEnabled(false);
		fieldNamesCombo.setEnabled(false);
		fieldOldValuesCombo.setEnabled(false);
		newValueTextField.setEnabled(false);
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

		SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
			private boolean anyChanged = false;
			private int nSkipped = 0;

			@Override
			protected Void doInBackground() throws Exception {
				for (int i = 0; i < nExperiments; i++) {
					Experiment exp = editExpList.getItemAtNoLoad(i);
					progress.setMessage("Restore (" + (i + 1) + "/" + nExperiments + ")");
					if (exp == null) {
						Logger.warn("Edit.restoreFieldFromMigrationBackup: null experiment at index " + i);
						progress.incPosition();
						continue;
					}
					String rd = exp.getResultsDirectory();
					if (rd == null && exp instanceof LazyExperiment) {
						LazyExperiment le = (LazyExperiment) exp;
						if (le.getMetadata() != null) {
							rd = le.getMetadata().getResultsDirectory();
						}
					}
					if (rd == null || !MigrationBackupFieldRestore.isMigrationBackupPresent(rd)) {
						nSkipped++;
						progress.incPosition();
						continue;
					}
					if (MigrationBackupFieldRestore.restoreFieldFromMigrationBackup(exp, fieldEnumCode)) {
						anyChanged = true;
					} else {
						nSkipped++;
					}
					progress.incPosition();
				}
				return null;
			}

			@Override
			protected void done() {
				progress.close();
				statusLabel.setText("Restore finished (" + fieldEnumCode + "). Updated: " + (anyChanged ? "yes" : "no")
						+ ", skipped (no backup or no match): " + nSkipped + ".");
				if (anyChanged) {
					clearApplyUndoSnapshot();
				}
				applyButton.setEnabled(true);
				refreshButton.setEnabled(true);
				updateRestoreFromBackupButtonState();
				updateUndoLastApplyButtonState();
				fieldNamesCombo.setEnabled(true);
				fieldOldValuesCombo.setEnabled(true);
				newValueTextField.setEnabled(true);
				setCursor(Cursor.getDefaultCursor());
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					exp.load_spots_description_and_measures();
					parent0.dlgMeasure.chartsPanel.displayChartPanels(exp);
				}
				if (anyChanged && parent0.descriptorIndex != null) {
					final ProgressFrame pf = new ProgressFrame("Refreshing descriptors");
					parent0.dlgExperiment.filterPanel.initCombos();
					JComboBoxExperimentLazy indexSource = parent0.dlgExperiment.filterPanel.filterExpList;
					if (indexSource.getItemCount() < 1)
						indexSource = parent0.expListComboLazy;
					parent0.descriptorIndex.preloadFromCombo(indexSource, new Runnable() {
						@Override
						public void run() {
							pf.close();
							parent0.dlgExperiment.infosPanel.initCombos();
							parent0.dlgExperiment.filterPanel.initCombos();
							initEditCombos();
						}
					});
				} else {
					parent0.dlgExperiment.infosPanel.initCombos();
					parent0.dlgExperiment.filterPanel.initCombos();
					initEditCombos();
				}
			}
		};
		worker.execute();
	}

	void undoLastApply() {
		syncEditExpListFromProject();
		final int nExperiments = editExpList.getItemCount();
		final EnumXLSColumnHeader fieldEnumCode = (EnumXLSColumnHeader) fieldNamesCombo.getSelectedItem();
		if (lastApplyUndoSnapshot == null || lastApplyUndoSnapshot.isEmpty()) {
			statusLabel.setText("Nothing to undo.");
			return;
		}
		if (lastApplyUndoSnapshot.getField() != fieldEnumCode) {
			statusLabel.setText("Undo is only available for field \"" + lastApplyUndoSnapshot.getField()
					+ "\" (select that field name first).");
			return;
		}

		final ProgressFrame progress = new ProgressFrame("Undo last apply: " + fieldEnumCode);
		progress.setLength(nExperiments);
		statusLabel.setText("Undoing last apply for " + fieldEnumCode + "...");
		applyButton.setEnabled(false);
		refreshButton.setEnabled(false);
		restoreFromBackupButton.setEnabled(false);
		undoLastApplyButton.setEnabled(false);
		fieldNamesCombo.setEnabled(false);
		fieldOldValuesCombo.setEnabled(false);
		newValueTextField.setEnabled(false);
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

		final EditApplyUndoSnapshot snap = lastApplyUndoSnapshot;
		SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
			@Override
			protected Boolean doInBackground() throws Exception {
				return Boolean.valueOf(snap.undo(editExpList, nExperiments, fieldEnumCode));
			}

			@Override
			protected void done() {
				progress.close();
				boolean ok = false;
				try {
					ok = Boolean.TRUE.equals(get());
				} catch (Exception ex) {
					Logger.warn("Edit.undoLastApply: " + ex.getMessage());
				}
				if (ok) {
					lastApplyUndoSnapshot = null;
					statusLabel.setText("Undo completed for " + fieldEnumCode + ".");
				} else {
					statusLabel.setText("Undo failed or nothing matched (list may have changed).");
				}
				applyButton.setEnabled(true);
				refreshButton.setEnabled(true);
				updateRestoreFromBackupButtonState();
				updateUndoLastApplyButtonState();
				fieldNamesCombo.setEnabled(true);
				fieldOldValuesCombo.setEnabled(true);
				newValueTextField.setEnabled(true);
				setCursor(Cursor.getDefaultCursor());
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					exp.load_spots_description_and_measures();
					parent0.dlgMeasure.chartsPanel.displayChartPanels(exp);
				}
				if (ok && parent0.descriptorIndex != null) {
					final ProgressFrame pf = new ProgressFrame("Refreshing descriptors");
					parent0.dlgExperiment.filterPanel.initCombos();
					JComboBoxExperimentLazy indexSource = parent0.dlgExperiment.filterPanel.filterExpList;
					if (indexSource.getItemCount() < 1)
						indexSource = parent0.expListComboLazy;
					parent0.descriptorIndex.preloadFromCombo(indexSource, new Runnable() {
						@Override
						public void run() {
							pf.close();
							parent0.dlgExperiment.infosPanel.initCombos();
							parent0.dlgExperiment.filterPanel.initCombos();
							initEditCombos();
						}
					});
				} else if (ok) {
					parent0.dlgExperiment.infosPanel.initCombos();
					parent0.dlgExperiment.filterPanel.initCombos();
					initEditCombos();
				} else {
					updateUndoLastApplyButtonState();
				}
			}
		};
		worker.execute();
	}

}
