package plugins.fmp.multiSPOTS96.dlg.f_excel;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;

public class SpotsAreas extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1290058998782225526L;

	JButton exportToXLSButton2 = new JButton("save XLS");
	JButton exportQToXLSButton = new JButton("save XLS as Query");

	JCheckBox areaCheckBox = new JCheckBox("area", false);
	JCheckBox nPixelsCheckBox = new JCheckBox("n pixels", false);
	JCheckBox t0CheckBox = new JCheckBox("(max-t)/max", true);
	JCheckBox discardNoFlyCageCheckBox = new JCheckBox("discard cages with no fly", true);

	void init(GridLayout capLayout) {
		setLayout(capLayout);

		FlowLayout flowLayout0 = new FlowLayout(FlowLayout.LEFT);
		flowLayout0.setVgap(0);
		JPanel panel0 = new JPanel(flowLayout0);
		panel0.add(areaCheckBox);
		panel0.add(nPixelsCheckBox);
		panel0.add(t0CheckBox);
		panel0.add(discardNoFlyCageCheckBox);
		add(panel0);

		JPanel panel1 = new JPanel(flowLayout0);
		add(panel1);

		FlowLayout flowLayout2 = new FlowLayout(FlowLayout.RIGHT);
		flowLayout2.setVgap(0);
		JPanel panel2 = new JPanel(flowLayout2);
		panel2.add(exportToXLSButton2);
		panel2.add(exportQToXLSButton);
		add(panel2);

		areaCheckBox.setEnabled(false);
		nPixelsCheckBox.setEnabled(false);

		defineActionListeners();
	}

	private void defineActionListeners() {
		exportToXLSButton2.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				firePropertyChange("EXPORT_SPOTSMEASURES", false, true);
			}
		});

		exportQToXLSButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				firePropertyChange("EXPORT_SPOTSMEASURES_AS_Q", false, true);
			}
		});
	}

}
