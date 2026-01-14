package plugins.fmp.multicafe.dlg.capillaries;

import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import icy.gui.util.FontUtil;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.fmp_experiment.Experiment;

public class LoadSaveCapillaries extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -4019075448319252245L;

	private JButton openButtonCapillaries = new JButton("Load...");
	private JButton saveButtonCapillaries = new JButton("Save...");
	private MultiCAFE parent0 = null;

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);

		JLabel loadsaveText = new JLabel("-> Capillaries (xml) ", SwingConstants.RIGHT);
		loadsaveText.setFont(FontUtil.setStyle(loadsaveText.getFont(), Font.ITALIC));
		FlowLayout flowLayout = new FlowLayout(FlowLayout.RIGHT);
		flowLayout.setVgap(0);
		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(loadsaveText);
		panel1.add(openButtonCapillaries);
		panel1.add(saveButtonCapillaries);
		panel1.validate();
		add(panel1);

		this.parent0 = parent0;
		defineActionListeners();
	}

	private void defineActionListeners() {
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
					saveCapillaries_file(exp);
					firePropertyChange("CAP_ROIS_SAVE", false, true);
				}
			}
		});
	}

	public boolean loadCapillaries_File(Experiment exp) {
		boolean flag = exp.loadMCCapillaries_Only();
		exp.getCapillaries().transferCapillaryRoiToSequence(exp.getSeqCamData().getSequence());
		return flag;
	}

	public boolean saveCapillaries_file(Experiment exp) {
		parent0.paneCapillaries.getDialogCapillariesInfos(exp); // get data into desc
		parent0.paneExperiment.getExperimentInfosFromDialog(exp);
		exp.getCapillaries().transferDescriptionToCapillaries();

		exp.saveExperimentDescriptors();
		exp.getCapillaries().updateCapillariesFromSequence(exp.getSeqCamData());
		return exp.saveMCCapillaries_Only();
	}

}
