package plugins.fmp.multiSPOTS.dlg.define_spots;

import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import icy.gui.frame.progress.ProgressFrame;
import icy.gui.util.FontUtil;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.Logger;

public class LoadSaveSpotsPanel extends JPanel {
	private static final long serialVersionUID = -4019075448319252245L;

	private JButton loadButton = new JButton("Load...");
	private JButton saveButton = new JButton("Save...");
	private JButton rebuildFromMs96Button = new JButton("Rebuild from MS96 (dev)…");
	private JCheckBox rebuildAllFromCurrentCheckBox = new JCheckBox("All (from current)", false);
	private MultiSPOTS parent0 = null;

	void init(GridLayout capLayout, MultiSPOTS parent0) {
		setLayout(capLayout);

		JLabel loadsaveText = new JLabel("-> Spots, polylines (xml) ", SwingConstants.RIGHT);
		loadsaveText.setFont(FontUtil.setStyle(loadsaveText.getFont(), Font.ITALIC));
		FlowLayout flowLayout = new FlowLayout(FlowLayout.RIGHT);
		flowLayout.setVgap(0);
		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(loadsaveText);
		panel1.add(loadButton);
		panel1.add(saveButton);
		panel1.validate();
		add(panel1);

		JPanel panelMs96 = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
		panelMs96.add(rebuildAllFromCurrentCheckBox);
		panelMs96.add(rebuildFromMs96Button);
		add(panelMs96);

		this.parent0 = parent0;
		defineActionListeners();
	}

	private void defineActionListeners() {
		loadButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					exp.load_cages_description_and_measures();
					exp.load_spots_description_and_measures();
					exp.transferCagesROI_toSequence();
					exp.transferSpotsROI_toSequence();
					parent0.dlgExperiment.optionsPanel.applyViewOptionsToCurrentExperiment();
					firePropertyChange("SPOTS_ROIS_OPEN", false, true);
				}
			}
		});

		saveButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					exp.saveExperimentDescriptors();
					exp.saveCages_File();
					exp.saveSpots_File();
					firePropertyChange("SPOTS_ROIS_SAVE", false, true);
				}
			}
		});

		rebuildFromMs96Button.addActionListener(e -> onRebuildFromMs96Clicked());
	}

	private void onRebuildFromMs96Clicked() {
		final String msg = "Rebuild spots from MS96_cages.xml (development tool).\n\n"
				+ "This overwrites SpotsDescription.csv and updates cage spot references.\n"
				+ "Spot IDs will be reassigned — existing SpotsMeasures.csv may no longer match.\n"
				+ "Back up results/ before continuing.\n\n"
				+ "Proceed?";
		int choice = JOptionPane.showConfirmDialog(this, msg, "Rebuild spots from MS96",
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
		if (choice != JOptionPane.OK_OPTION) {
			return;
		}

		final int i0 = parent0.expListComboLazy.getSelectedIndex();
		if (i0 < 0) {
			JOptionPane.showMessageDialog(this, "Select an experiment in the list first.", "Rebuild spots from MS96",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		final int last = parent0.expListComboLazy.getItemCount() - 1;
		final int i1 = rebuildAllFromCurrentCheckBox.isSelected() ? last : i0;
		rebuildFromMs96Button.setEnabled(false);

		new SwingWorker<int[], Void>() {
			private final ProgressFrame progress = new ProgressFrame("Rebuild spots from MS96");

			@Override
			protected int[] doInBackground() {
				int ok = 0;
				int fail = 0;
				int total = i1 - i0 + 1;
				int n = 0;
				for (int i = i0; i <= i1; i++) {
					n++;
					Experiment exp = parent0.expListComboLazy.getItemAt(i);
					progress.setMessage("Experiment " + n + " / " + total);
					if (exp == null) {
						fail++;
						continue;
					}
					try {
						if (exp.rebuildSpotsFromMs96Dev()) {
							ok++;
						} else {
							fail++;
						}
					} catch (Exception ex) {
						Logger.warn("LoadSaveSpotsPanel: rebuild failed at index " + i + ": " + ex.getMessage(), ex);
						fail++;
					}
				}
				return new int[] { ok, fail };
			}

			@Override
			protected void done() {
				progress.close();
				rebuildFromMs96Button.setEnabled(true);
				try {
					int[] r = get();
					int ok = r[0];
					int fail = r[1];
					SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(LoadSaveSpotsPanel.this,
							"Finished: " + ok + " succeeded, " + fail + " skipped or failed.", "Rebuild spots from MS96",
							fail > 0 ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE));
					firePropertyChange("SPOTS_ROIS_SAVE", false, true);
				} catch (Exception ex) {
					Logger.error("LoadSaveSpotsPanel: rebuild worker failed", ex);
					SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(LoadSaveSpotsPanel.this,
							"Rebuild failed: " + ex.getMessage(), "Rebuild spots from MS96", JOptionPane.ERROR_MESSAGE));
				}
			}
		}.execute();
	}

}
