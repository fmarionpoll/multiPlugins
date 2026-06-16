package plugins.fmp.multiSPOTS.dlg.export;

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
	JCheckBox greySumCleanV5CheckBox = new JCheckBox("GREY_SUM_CLEAN_V5", false);
	JCheckBox kymoFractCheckBox = new JCheckBox("KYMO_FRACT", false);
	JCheckBox kymoAbsDeltaCheckBox = new JCheckBox("KYMO_ABS_DELTA", false);
	JCheckBox kymoGreenHeightCheckBox = new JCheckBox("KYMO_GREEN_HEIGHT", false);
	JCheckBox kymoGreenHeightRatioCheckBox = new JCheckBox("KYMO_GREEN_HEIGHT_RATIO", false);
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
		add(panel0);

		JPanel panel1 = new JPanel(flowLayout0);
		panel1.add(greySumV5CheckBox);
		panel1.add(greySumCleanV5CheckBox);
		panel1.add(kymoFractCheckBox);
		panel1.add(kymoAbsDeltaCheckBox);
		add(panel1);

		JPanel panel11 = new JPanel(flowLayout0);
		panel11.add(kymoGreenHeightCheckBox);
		panel11.add(kymoGreenHeightRatioCheckBox);
		panel11.add(t0CheckBox);
		panel11.add(discardNoFlyCageCheckBox);
		add(panel11);

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
