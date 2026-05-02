package plugins.fmp.multiSPOTS96.dlg.excel;

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

	JCheckBox areaCheckBox = new JCheckBox("area (AREA_SUM)", true);
	JCheckBox sumNoFlyCheckBox = new JCheckBox("no fly (AREA_SUMNOFLY)", false);
	JCheckBox sumCleanCheckBox = new JCheckBox("clean (AREA_SUMCLEAN)", false);
	JCheckBox t0CheckBox = new JCheckBox("(max-t)/max", true);
	JCheckBox discardNoFlyCageCheckBox = new JCheckBox("discard cages with no fly", true);

	void init(GridLayout capLayout) {
		setLayout(capLayout);

		FlowLayout flowLayout0 = new FlowLayout(FlowLayout.LEFT);
		flowLayout0.setVgap(0);
		JPanel panel0 = new JPanel(flowLayout0);
		panel0.add(areaCheckBox);
		panel0.add(sumNoFlyCheckBox);
		panel0.add(sumCleanCheckBox);

		add(panel0);

		JPanel panel1 = new JPanel(flowLayout0);
		panel1.add(t0CheckBox);
		panel1.add(discardNoFlyCageCheckBox);
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
				firePropertyChange("EXPORT_SPOTSMEASURES", false, true);
			}
		});

	}

}
