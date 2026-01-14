package plugins.fmp.multiSPOTS96.dlg.a_experiment;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

import icy.gui.frame.progress.ProgressFrame;
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multiSPOTS96.experiment.Experiment;
import plugins.fmp.multiSPOTS96.tools.DescriptorsIO;
import plugins.fmp.multiSPOTS96.tools.JComponents.JComboBoxExperimentLazy;
import plugins.fmp.multiSPOTS96.tools.toExcel.EnumXLSColumnHeader;

public class Edit extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2190848825783418962L;

	private JComboBox<EnumXLSColumnHeader> fieldNamesCombo = new JComboBox<EnumXLSColumnHeader>(
			new EnumXLSColumnHeader[] { EnumXLSColumnHeader.EXP_EXPT, EnumXLSColumnHeader.EXP_BOXID,
					EnumXLSColumnHeader.EXP_STIM1, EnumXLSColumnHeader.EXP_CONC1, EnumXLSColumnHeader.EXP_STRAIN,
					EnumXLSColumnHeader.EXP_SEX, EnumXLSColumnHeader.EXP_STIM2, EnumXLSColumnHeader.EXP_CONC2,
					EnumXLSColumnHeader.SPOT_STIM, EnumXLSColumnHeader.SPOT_CONC, EnumXLSColumnHeader.SPOT_VOLUME,
					EnumXLSColumnHeader.CAGE_SEX, EnumXLSColumnHeader.CAGE_STRAIN, EnumXLSColumnHeader.CAGE_AGE });

	private JComboBox<String> fieldOldValuesCombo = new JComboBox<String>();
	private JButton refreshButton = new JButton("Refresh");
	private JTextField newValueTextField = new JTextField(10);
	private JButton applyButton = new JButton("Apply");
	private JLabel statusLabel = new JLabel("");

	private MultiSPOTS96 parent0 = null;
	JComboBoxExperimentLazy editExpList = new JComboBoxExperimentLazy();

	void init(GridLayout capLayout, MultiSPOTS96 parent0) {
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
		add(panel2);

		JPanel panel3 = new JPanel(flowlayout);
		panel3.add(statusLabel);
		add(panel3);

		defineActionListeners();
	}

	public void initEditCombos() {
		editExpList.setExperimentsFromList(parent0.expListComboLazy.getExperimentsAsListNoLoad());
		EnumXLSColumnHeader field = (EnumXLSColumnHeader) fieldNamesCombo.getSelectedItem();
		fieldOldValuesCombo.removeAllItems();
		java.util.List<String> values;
		if (parent0.descriptorIndex != null && parent0.descriptorIndex.isReady()) {
			values = parent0.descriptorIndex.getDistinctValues(field);
		} else {
			// fallback: use existing lightweight scan
			editExpList.getFieldValuesToComboLightweight(fieldOldValuesCombo, field);
			return;
		}
		java.util.Collections.sort(values);
		for (String v : values)
			fieldOldValuesCombo.addItem(v);
	}

	private void defineActionListeners() {
		applyButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				applyChange();
				newValueTextField.setText("");
				initEditCombos();
			}
		});

		fieldNamesCombo.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				EnumXLSColumnHeader field = (EnumXLSColumnHeader) fieldNamesCombo.getSelectedItem();
				fieldOldValuesCombo.removeAllItems();
				java.util.List<String> values;
				if (parent0.descriptorIndex != null && parent0.descriptorIndex.isReady()) {
					values = parent0.descriptorIndex.getDistinctValues(field);
					java.util.Collections.sort(values);
					for (String v : values)
						fieldOldValuesCombo.addItem(v);
				} else {
					editExpList.getFieldValuesToComboLightweight(fieldOldValuesCombo, field);
				}
			}
		});

		refreshButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				final ProgressFrame pf = new ProgressFrame("Refreshing descriptors");
				parent0.descriptorIndex.preloadFromCombo(parent0.expListComboLazy, new Runnable() {
					@Override
					public void run() {
						pf.close();
						initEditCombos();
					}
				});
			}
		});

	}

	void applyChange() {
		final int nExperiments = editExpList.getItemCount();
		final EnumXLSColumnHeader fieldEnumCode = (EnumXLSColumnHeader) fieldNamesCombo.getSelectedItem();
		final String oldValue = (String) fieldOldValuesCombo.getSelectedItem();
		final String newValue = newValueTextField.getText();

		final ProgressFrame progress = new ProgressFrame("Apply changes to " + fieldEnumCode);
		progress.setLength(nExperiments);
		statusLabel.setText("Applying changes to " + fieldEnumCode + "...");
		applyButton.setEnabled(false);
		refreshButton.setEnabled(false);
		fieldNamesCombo.setEnabled(false);
		fieldOldValuesCombo.setEnabled(false);
		newValueTextField.setEnabled(false);
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

		SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
			private boolean anyChanged = false;

			@Override
			protected Void doInBackground() throws Exception {
				for (int i = 0; i < nExperiments; i++) {
					Experiment exp = editExpList.getItemAtNoLoad(i);
					boolean isChanged = false;
					progress.setMessage("Updating (" + (i + 1) + "/" + nExperiments + ")");
					// Apply change without triggering image loads
					switch (fieldEnumCode) {
					case EXP_EXPT:
					case EXP_BOXID:
					case EXP_STIM1:
					case EXP_CONC1:
					case EXP_STRAIN:
					case EXP_SEX:
					case EXP_STIM2:
					case EXP_CONC2:
						isChanged = exp.replaceExperimentFieldIfEqualOldValue(fieldEnumCode, oldValue, newValue);
						if (isChanged)
							exp.save_MS96_experiment();
						break;
					case CAGE_SEX:
					case CAGE_STRAIN:
					case CAGE_AGE:
						isChanged = exp.replaceCageFieldValueWithNewValueIfOld(fieldEnumCode, oldValue, newValue);
						if (isChanged)
							exp.save_MS96_cages();
						break;
					case SPOT_STIM:
					case SPOT_CONC:
					case SPOT_VOLUME:
						isChanged = exp.replaceSpotsFieldValueWithNewValueIfOld(fieldEnumCode, oldValue, newValue);
						if (isChanged)
							exp.save_MS96_cages();
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
				applyButton.setEnabled(true);
				refreshButton.setEnabled(true);
				fieldNamesCombo.setEnabled(true);
				fieldOldValuesCombo.setEnabled(true);
				newValueTextField.setEnabled(true);
				setCursor(Cursor.getDefaultCursor());
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					exp.load_MS96_spotsMeasures();
					parent0.dlgMeasure.tabCharts.displayChartPanels(exp);
				}
				// Incremental update of in-memory index for the edited field only
				if (anyChanged) {
					parent0.descriptorIndex.removeValue(fieldEnumCode, oldValue);
					parent0.descriptorIndex.addValue(fieldEnumCode, newValue);
				}
				// Refresh UI combos from current in-memory index
				parent0.dlgExperiment.tabInfos.initCombos();
				parent0.dlgExperiment.tabFilter.initCombos();
				initEditCombos();
			}
		};
		worker.execute();
	}

}
