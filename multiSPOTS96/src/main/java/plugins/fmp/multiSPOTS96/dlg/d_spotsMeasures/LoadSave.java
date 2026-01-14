package plugins.fmp.multiSPOTS96.dlg.d_spotsMeasures;

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
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multiSPOTS96.experiment.Experiment;

public class LoadSave extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -4019075448319252245L;

	private JButton loadButton = new JButton("Load...");
	private JButton saveButton = new JButton("Save...");
	private MultiSPOTS96 parent0 = null;

	void init(GridLayout capLayout, MultiSPOTS96 parent0) {
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

		this.parent0 = parent0;
		defineActionListeners();
	}

	private void defineActionListeners() {
		loadButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					dlg_spotsmeasures_loadSpotsArray_File(exp);
					firePropertyChange("CAP_ROIS_OPEN", false, true);
				}
			}
		});

		saveButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					saveSpotsArray_file(exp);
					firePropertyChange("CAP_ROIS_SAVE", false, true);
				}
			}
		});
	}

	public boolean dlg_spotsmeasures_loadSpotsArray_File(Experiment exp) {
		boolean flag = exp.load_MS96_cages();
		if (flag) {
			exp.load_MS96_spotsMeasures();
			exp.seqCamData.removeROIsContainingString("spot");
			exp.cagesArray.transferCageSpotsToSequenceAsROIs(exp.seqCamData);
//			if (exp.seqKymos != null) {
//				Sequence seq = exp.seqKymos.getSequence();
//				if (seq != null && seq.getName() != null)
//					exp.cagesArray.transferSpotsMeasuresToSequenceAsROIs(exp.seqKymos.getSequence());
//			}
		}
		return flag;
	}

	public boolean saveSpotsArray_file(Experiment exp) {
		parent0.dlgExperiment.getExperimentInfosFromDialog(exp);
		boolean flag = exp.save_MS96_experiment();
		exp.cagesArray.transferROIsFromSequenceToCageSpots(exp.seqCamData);
		flag &= exp.save_MS96_cages();
		flag &= exp.save_MS96_spotsMeasures();
		return flag;
	}

}
