package plugins.fmp.multiSPOTS.dlg.flyPosition;

import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import icy.gui.frame.progress.ProgressFrame;
import icy.gui.util.FontUtil;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multitools.experiment.Experiment;

public class LoadSavePositions extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -5257698990389571518L;
	private JButton openCagesButton = new JButton("Load...");
	private JButton saveCagesButton = new JButton("Save...");
	private JButton clearButton = new JButton("Clear...");
	private MultiSPOTS parent0 = null;

	void init(GridLayout capLayout, MultiSPOTS parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout flowLayout = new FlowLayout(FlowLayout.RIGHT);
		flowLayout.setVgap(0);
		JPanel panel1 = new JPanel(flowLayout);
		JLabel loadsaveText = new JLabel("-> Fly positions (csv) ", SwingConstants.RIGHT);
		loadsaveText.setFont(FontUtil.setStyle(loadsaveText.getFont(), Font.ITALIC));
		panel1.add(loadsaveText);
		panel1.add(openCagesButton);
		panel1.add(saveCagesButton);
		panel1.validate();
		add(panel1);

		JPanel panelClear = new JPanel(flowLayout);
		panelClear.add(clearButton);
		panelClear.validate();
		add(panelClear);

		defineActionListeners();
	}

	private void defineActionListeners() {
		openCagesButton.addActionListener(e -> {
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null) {
				loadCages(exp);
				firePropertyChange("LOAD_DATA", false, true);
				parent0.dlgDetectFlies.tabsPane.setSelectedIndex(parent0.dlgDetectFlies.iTAB_EDIT);
			}
		});

		saveCagesButton.addActionListener(e -> {
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null) {
				saveMeasures(exp);
				parent0.dlgDetectFlies.tabsPane.setSelectedIndex(parent0.dlgDetectFlies.iTAB_EDIT);
			}
		});

		clearButton.addActionListener(e -> clearPositions());
	}

	private void clearPositions() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null) {
			return;
		}
		int choice = JOptionPane.showConfirmDialog(this,
				"Erase fly positions for this experiment?\nCage outlines are kept.",
				"Clear fly positions", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
		if (choice != JOptionPane.OK_OPTION) {
			return;
		}
		if (!exp.clearFlyPositions()) {
			JOptionPane.showMessageDialog(this, "Could not erase fly positions.",
					"Clear fly positions", JOptionPane.ERROR_MESSAGE);
			return;
		}
		exp.updateROIsAt(0);
		if (parent0.dlgDetectFlies != null && parent0.dlgDetectFlies.tabGraphics != null) {
			parent0.dlgDetectFlies.tabGraphics.closeAllCharts();
		}
	}

	public boolean loadCages(Experiment exp) {
		if (exp == null)
			return false;
		ProgressFrame progress = new ProgressFrame("load fly positions");
		boolean flag = exp.loadFlyPositions();
		if (flag) {
			exp.updateROIsAt(0);
		}
		progress.close();
		return flag;
	}

	public void saveMeasures(Experiment exp) {
		if (exp != null) {
			exp.saveFlyPositions();
		}
	}
}
