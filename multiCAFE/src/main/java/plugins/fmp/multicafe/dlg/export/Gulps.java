package plugins.fmp.multicafe.dlg.export;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

public class Gulps extends JPanel {
	private static final long serialVersionUID = 1290058998782225526L;

	JButton exportToXLSButton2 = new JButton("save");
	JCheckBox sumGulpsCheckBox = new JCheckBox("sum", false);
	JCheckBox sumCheckBox = new JCheckBox("L+R & ratio", false);
	JCheckBox nbGulpsCheckBox = new JCheckBox("number/bin", true);
	JCheckBox amplitudeGulpsCheckBox = new JCheckBox("amplitude/bin", true);
	JCheckBox markovChainCheckBox = new JCheckBox("Markov chain", false);
	JCheckBox savedIntakeCheckBox = new JCheckBox("saved intake", true);

	JSpinner nbinsJSpinner = new JSpinner(new SpinnerNumberModel(40, 1, 99999999, 1));

	void init(GridLayout capLayout) {
		setLayout(capLayout);

		FlowLayout flowLayout0 = new FlowLayout(FlowLayout.LEFT);
		flowLayout0.setVgap(0);

		JPanel panel0 = new JPanel(flowLayout0);
		panel0.add(sumGulpsCheckBox);
		panel0.add(sumCheckBox);
		panel0.add(savedIntakeCheckBox);
		defineToolTips();
		add(panel0);

		JPanel panel1 = new JPanel(flowLayout0);
		panel1.add(nbGulpsCheckBox);
		panel1.add(amplitudeGulpsCheckBox);
		panel1.add(markovChainCheckBox);
		int bWidth = 50;
		int bHeight = 21;
		nbinsJSpinner.setPreferredSize(new Dimension(bWidth, bHeight));
		panel1.add(nbinsJSpinner);
		add(panel1);

		FlowLayout flowLayout2 = new FlowLayout(FlowLayout.RIGHT);
		flowLayout2.setVgap(0);
		JPanel panel2 = new JPanel(flowLayout2);
		panel2.add(exportToXLSButton2);
		add(panel2);

		defineActionListeners();
	}

	private void defineToolTips() {
		savedIntakeCheckBox.setToolTipText(
				"Export separately saved intake: raw/corrected volumes, running totals and detector settings, alongside ordinary gulps.");
	}

	private void defineActionListeners() {
		exportToXLSButton2.addActionListener(e -> firePropertyChange("EXPORT_GULPSDATA", false, true));
	}

}
