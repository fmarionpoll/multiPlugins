package plugins.fmp.multicafe.dlg.excel;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

public class Gulps extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1290058998782225526L;

	JButton exportToXLSButton = new JButton("save XLS (v1)");
	JButton exportToXLSButton2 = new JButton("save XLS");
	JCheckBox sumGulpsCheckBox = new JCheckBox("sum", true);
	JCheckBox nbGulpsCheckBox = new JCheckBox("number/bin", true);
	JCheckBox amplitudeGulpsCheckBox = new JCheckBox("amplitude/bin", true);
	JCheckBox sumCheckBox = new JCheckBox("L+R & ratio", false); // true);
	JCheckBox derivativeCheckBox = new JCheckBox("derivative", false);
	JCheckBox markovChainCheckBox = new JCheckBox("Markov chain", true); // false);

//	JCheckBox autocorrelationCheckBox = new JCheckBox("autocorrelation", false);
//	JCheckBox crosscorrelationCheckBox = new JCheckBox("crosscorrelation", false);
//	JLabel nbinsLabel = new JLabel("n bins:");
	JSpinner nbinsJSpinner = new JSpinner(new SpinnerNumberModel(40, 1, 99999999, 1));

	void init(GridLayout capLayout) {
		setLayout(capLayout);

		FlowLayout flowLayout0 = new FlowLayout(FlowLayout.LEFT);
		flowLayout0.setVgap(0);
		JPanel panel0 = new JPanel(flowLayout0);
		panel0.add(derivativeCheckBox);
		panel0.add(sumGulpsCheckBox);
		panel0.add(nbGulpsCheckBox);
		panel0.add(amplitudeGulpsCheckBox);
		panel0.add(sumCheckBox);
		add(panel0);

		JPanel panel1 = new JPanel(flowLayout0);
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

	private void defineActionListeners() {
		exportToXLSButton2.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				firePropertyChange("EXPORT_GULPSDATA", false, true);
			}
		});
	}

}
