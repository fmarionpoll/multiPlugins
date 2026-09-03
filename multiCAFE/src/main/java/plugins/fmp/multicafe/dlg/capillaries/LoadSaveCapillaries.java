package plugins.fmp.multicafe.dlg.capillaries;

import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JOptionPane;
import javax.swing.SwingConstants;

import icy.gui.util.FontUtil;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillaries.CapillariesPersistence;
import plugins.fmp.multitools.experiment.capillary.CapillaryMeasuredTipsOverlay;
import plugins.fmp.multitools.service.CapillaryGroundTruthLoader;

public class LoadSaveCapillaries extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -4019075448319252245L;

	private JButton openButtonCapillaries = new JButton("Load...");
	private JButton saveButtonCapillaries = new JButton("Save normal CSV");
	private JButton saveGroundTruthButton = new JButton("Save ground truth CSV");
	private JButton loadGroundTruthButton = new JButton("Load ground truth measurements");
	private MultiCAFE parent0 = null;

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);

		JLabel loadsaveText = new JLabel("-> Capillaries (CSV) ", SwingConstants.RIGHT);
		loadsaveText.setFont(FontUtil.setStyle(loadsaveText.getFont(), Font.ITALIC));
		FlowLayout flowLayout = new FlowLayout(FlowLayout.RIGHT);
		flowLayout.setVgap(0);
		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(loadsaveText);
		panel1.add(openButtonCapillaries);
		panel1.add(saveButtonCapillaries);
		JPanel referencePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		referencePanel.add(loadGroundTruthButton);
		referencePanel.add(saveGroundTruthButton);
		saveButtonCapillaries.setToolTipText("Save the selected experiment, including edited blue tips, as CapillariesDescription.csv");
		saveGroundTruthButton.setToolTipText("Save image-0 reference tips as " + CapillariesPersistence.GROUND_TRUTH_CSV
				+ "; leaves normal files unchanged");
		panel1.validate();
		add(panel1);
		add(referencePanel);
		loadGroundTruthButton.setToolTipText("Load blue endpoints for image 0 only; no files are overwritten");

		this.parent0 = parent0;
		defineActionListeners();
	}

	private void defineActionListeners() {
		loadGroundTruthButton.addActionListener(e -> loadGroundTruth());
		saveGroundTruthButton.addActionListener(e -> saveGroundTruth());
		openButtonCapillaries.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					loadCapillaries_File(exp);
					firePropertyChange("CAP_ROIS_OPEN", false, true);
				}
			}
		});

		saveButtonCapillaries.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					if (saveCapillaries_file(exp))
						firePropertyChange("CAP_ROIS_SAVE", false, true);
					else
						JOptionPane.showMessageDialog(LoadSaveCapillaries.this, "Could not save capillaries. See the log.",
								"Save normal CSV", JOptionPane.ERROR_MESSAGE);
				}
			}
		});
	}

	private void loadGroundTruth() {
		String title = "Load ground truth measurements";
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getResultsDirectory() == null || exp.getCapillaries().getList().isEmpty()) {
			JOptionPane.showMessageDialog(this, "Select an experiment with capillaries first.", title,
					JOptionPane.WARNING_MESSAGE);
			return;
		}
		if (exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null
				|| exp.getSeqCamData().getSequence().getFirstViewer() == null
				|| exp.getSeqCamData().getSequence().getFirstViewer().getPositionT() != 0) {
			JOptionPane.showMessageDialog(this, "Display image 0 before loading ground truth measurements.", title,
					JOptionPane.WARNING_MESSAGE);
			return;
		}
		try {
			File source = CapillaryGroundTruthLoader.findFile(new File(exp.getResultsDirectory()));
			CapillaryGroundTruthLoader.Preview preview = CapillaryGroundTruthLoader.read(source, exp.getCapillaries());
			if (preview.count() == 0) {
				JOptionPane.showMessageDialog(this, preview.summary(), title, JOptionPane.WARNING_MESSAGE);
				return;
			}
			if (JOptionPane.showConfirmDialog(this, source.getAbsolutePath() + "\n" + preview.summary()
					+ "\nReplace the matched blue measurements (including unsaved edits)?\n"
					+ "Green ROIs and files on disk will not be changed.", title,
					JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
			// Preserve unsaved blue edits for unmatched capillaries before rebuilding the overlay.
			CapillaryMeasuredTipsOverlay.transferTipsFromSequence(exp.getCapillaries(), exp.getSeqCamData(), 0);
			preview.apply();
			parent0.paneCapillaries.tabInfos.displayLoadedGroundTruth(exp);
			JOptionPane.showMessageDialog(this, "Loaded " + preview.count()
					+ " measurements. No files were changed.\nUse Save normal CSV only if you want to adopt these values.",
					title, JOptionPane.INFORMATION_MESSAGE);
		} catch (java.io.IOException ex) {
			JOptionPane.showMessageDialog(this, ex.getMessage(), title, JOptionPane.ERROR_MESSAGE);
		}
	}

	private void saveGroundTruth() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		String title = "Save ground truth CSV";
		if (exp == null || exp.getResultsDirectory() == null || exp.getCapillaries().getList().isEmpty()) {
			JOptionPane.showMessageDialog(this, "Select an experiment with capillaries first.", title,
					JOptionPane.WARNING_MESSAGE);
			return;
		}
		if (exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null
				|| exp.getSeqCamData().getSequence().getFirstViewer() == null
				|| exp.getSeqCamData().getSequence().getFirstViewer().getPositionT() != 0) {
			JOptionPane.showMessageDialog(this, "Display image 0 and adjust the blue tips before saving ground truth.",
					title, JOptionPane.WARNING_MESSAGE);
			return;
		}
		File target = new File(exp.getResultsDirectory(), CapillariesPersistence.GROUND_TRUTH_CSV);
		if (target.exists() && JOptionPane.showConfirmDialog(this,
				"Replace the existing ground truth file?\n" + target.getAbsolutePath(), title,
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION)
			return;

		// Capture the user's visible edits, but do not call the normal save pipeline.
		parent0.paneCapillaries.getDialogCapillariesInfos(exp);
		exp.getCapillaries().transferROIsFromSequence(exp.getSeqCamData());
		CapillaryMeasuredTipsOverlay.transferTipsFromSequence(exp.getCapillaries(), exp.getSeqCamData(), 0);
		boolean saved = exp.getCapillaries().getPersistence()
				.saveGroundTruthDescriptions(exp.getCapillaries(), exp.getResultsDirectory());
		JOptionPane.showMessageDialog(this,
				saved ? "Ground truth saved:\n" + target.getAbsolutePath() + "\nNormal files were not changed."
						: "Could not save ground truth. See the log.",
				title, saved ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
	}

	public boolean loadCapillaries_File(Experiment exp) {
		boolean flag = exp.loadMCCapillaries_Only();
		exp.getCapillaries().transferROIsToSequence(exp.getSeqCamData().getSequence());
		CapillaryMeasuredTipsOverlay.transferTipsToSequence(exp.getCapillaries(), exp.getSeqCamData());
		if (parent0 != null && parent0.paneExperiment != null && parent0.paneExperiment.tabOptions != null)
			parent0.paneExperiment.tabOptions.applyCentralViewOptionsToCamViewer(exp);
		if (flag && exp.getCages() != null)
			exp.getCages().transferNFliesFromCapillariesToCageBox(exp.getCapillaries().getList());
		return flag;
	}

	public boolean saveCapillaries_file(Experiment exp) {
		parent0.paneCapillaries.getDialogCapillariesInfos(exp); // get data into desc
		parent0.paneExperiment.getExperimentInfosFromDialog(exp);
		exp.getCapillaries().transferDescriptionToCapillaries();

		if (exp.getCages() != null)
			exp.getCages().transferNFliesFromCapillariesToCageBox(exp.getCapillaries().getList());

		exp.saveExperimentDescriptors();
		exp.getCapillaries().transferROIsFromSequence(exp.getSeqCamData());
		CapillaryMeasuredTipsOverlay.transferTipsFromSequence(exp.getCapillaries(), exp.getSeqCamData());
		// Use new CSV-based persistence: descriptions in results/, measures in binXX/
		return exp.save_capillaries_description_and_measures();
	}

}
