package plugins.fmp.multiSPOTS.dlg.experiment;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multiSPOTS.experiment.Experiment;

public class Options extends JPanel {
	private static final long serialVersionUID = 6565346204580890307L;

	public JCheckBox viewSpotsCheckBox = new JCheckBox("spots", true);
	public JCheckBox viewCagesCheckbox = new JCheckBox("cages", true);
	// TODO _CAGES JCheckBox viewFlyCheckbox = new JCheckBox("flies center", false);
	// TODO _CAGES JCheckBox viewFlyRectCheckbox = new JCheckBox("flies rect",
	// false);
	private MultiSPOTS parent0 = null;

	void init(GridLayout capLayout, MultiSPOTS parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout layout = new FlowLayout(FlowLayout.LEFT);
		layout.setVgap(1);

		JPanel panel1 = new JPanel(layout);
		panel1.add(new JLabel("View : "));
		panel1.add(viewSpotsCheckBox);
		panel1.add(viewCagesCheckbox);
		// TODO _CAGES panel1.add(viewFlyCheckbox);
		// TODO _CAGES panel1.add(viewFlyRectCheckbox);
		add(panel1);

		defineActionListeners();
	}

	private void defineActionListeners() {
		viewSpotsCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				displayROIsCategory(viewSpotsCheckBox.isSelected(), "line");
				displayROIsCategory(viewSpotsCheckBox.isSelected(), "spot");
			}
		});

		viewCagesCheckbox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				displayROIsCategory(viewCagesCheckbox.isSelected(), "cage");
			}
		});

		// TODO _CAGES viewFlyCheckbox.addActionListener(new ActionListener() {
		// TODO _CAGES @Override
		// TODO _CAGES public void actionPerformed(final ActionEvent e) {
		// TODO _CAGES displayROIsCategory(viewFlyCheckbox.isSelected(), "det");
		// TODO _CAGES }
		// TODO _CAGES });

		// TODO _CAGES viewFlyRectCheckbox.addActionListener(new ActionListener() {
		// TODO _CAGES @Override
		// TODO _CAGES public void actionPerformed(final ActionEvent e) {
		// TODO _CAGES displayROIsCategory(viewFlyRectCheckbox.isSelected(), "det");
		// TODO _CAGES }
		// TODO _CAGES });
	}

	public void displayROIsCategory(boolean isVisible, String pattern) {
		Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
		if (exp == null)
			return;
		exp.seqCamData.displaySpecificROIs(isVisible, pattern);
	}

}
