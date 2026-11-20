package plugins.fmp.multiSPOTS.dlg.export;

import java.awt.FlowLayout;
import java.awt.GridLayout;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;

import plugins.fmp.multitools.tools.results.EnumResults;

public class SpotsAreasPanel extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1290058998782225526L;

	JButton exportToXLSButton2 = new JButton("save");

	JCheckBox areaCheckBox = new JCheckBox(EnumResults.FILTER_GREY.toDisplayLabel(), true);
	JCheckBox sumNoFlyCheckBox = new JCheckBox(EnumResults.FILTER_GREY_FLY_MASKED.toDisplayLabel(), false);
	JCheckBox sumCleanCheckBox = new JCheckBox(EnumResults.FILTER_GREY_SMOOTHED.toDisplayLabel(), false);
	JCheckBox areaCountV5CheckBox = new JCheckBox(EnumResults.FILTER_V5_PIXEL_COUNT.toDisplayLabel(), false);
	JCheckBox greySumV5CheckBox = new JCheckBox(EnumResults.FILTER_V5_GREY.toDisplayLabel(), false);
	JCheckBox greySumCleanV5CheckBox = new JCheckBox(EnumResults.FILTER_V5_GREY_SMOOTHED.toDisplayLabel(), false);
	JCheckBox kymoFractCheckBox = new JCheckBox(EnumResults.KYMO_ROW_FRACTION.toDisplayLabel(), false);
	JCheckBox kymoAbsDeltaCheckBox = new JCheckBox(EnumResults.KYMO_ROW_FRACTION_CHANGE.toDisplayLabel(), false);
	JCheckBox kymoGreenHeightCheckBox = new JCheckBox(EnumResults.KYMO_GREEN_HEIGHT.toDisplayLabel(), false);
	JCheckBox kymoGreenHeightRatioCheckBox = new JCheckBox(EnumResults.KYMO_GREEN_HEIGHT_RATIO.toDisplayLabel(), false);
//	JCheckBox areaV2CheckBox = new JCheckBox(EnumResults.FILTER_GREY_CONTRAST.toDisplayLabel(), false);
//	JCheckBox sumNoFlyV2CheckBox = new JCheckBox(EnumResults.FILTER_GREY_CONTRAST_MASKED.toDisplayLabel(), false);
//	JCheckBox sumCleanV2CheckBox = new JCheckBox(EnumResults.FILTER_GREY_CONTRAST_SMOOTHED.toDisplayLabel(), false);
	JCheckBox includeGroupsCheckBox = new JCheckBox("Include group SUM, PI and PInorm (CSV)", false);
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
		includeGroupsCheckBox.setToolTipText("Include camera and kymograph group results in the normalized CSV package.");
		panel2.add(includeGroupsCheckBox);
		panel2.add(exportToXLSButton2);
		add(panel2);

		defineActionListeners();
	}

	private void defineActionListeners() {
		exportToXLSButton2.addActionListener(e -> firePropertyChange("EXPORT_SPOTSMEASURES", false, true));

	}

}
