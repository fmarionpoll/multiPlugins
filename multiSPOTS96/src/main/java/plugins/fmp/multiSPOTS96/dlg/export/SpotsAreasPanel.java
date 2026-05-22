package plugins.fmp.multiSPOTS96.dlg.export;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;

public class SpotsAreasPanel extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1290058998782225526L;

	JButton exportToXLSButton2 = new JButton("save XLS");

	JCheckBox areaCheckBox = new JCheckBox("AREA_SUM", true);
	JCheckBox sumNoFlyCheckBox = new JCheckBox("AREA_SUMNOFLY", false);
	JCheckBox sumCleanCheckBox = new JCheckBox("AREA_SUMCLEAN", false);
	JCheckBox areaCountV5CheckBox = new JCheckBox("AREA_COUNT_V5", false);
	JCheckBox greySumV5CheckBox = new JCheckBox("GREY_SUM_V5", false);
//	JCheckBox areaV2CheckBox = new JCheckBox("AREA_SUM_V2", false);
//	JCheckBox sumNoFlyV2CheckBox = new JCheckBox("AREA_SUMNOFLY_V2", false);
//	JCheckBox sumCleanV2CheckBox = new JCheckBox("AREA_SUMCLEAN_V2", false);
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
		panel0.add(areaCountV5CheckBox);
		panel0.add(greySumV5CheckBox);
//		panel0.add(areaV2CheckBox);
//		panel0.add(sumNoFlyV2CheckBox);
//		panel0.add(sumCleanV2CheckBox);
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
