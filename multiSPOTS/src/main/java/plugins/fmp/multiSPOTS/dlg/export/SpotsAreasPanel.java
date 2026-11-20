package plugins.fmp.multiSPOTS.dlg.export;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

public class SpotsAreasPanel extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1290058998782225526L;

	private static final EnumResults[] FRAME_MEASURES = { EnumResults.FILTER_GREY, EnumResults.FILTER_GREY_FLY_MASKED,
			EnumResults.FILTER_GREY_SMOOTHED, EnumResults.FILTER_V5_PIXEL_COUNT, EnumResults.FILTER_V5_GREY,
			EnumResults.FILTER_V5_GREY_SMOOTHED, EnumResults.KYMO_CROSS_RATIO, EnumResults.KYMO_RIM_RATIO };

	private static final EnumResults[] KYMO_MEASURES = { EnumResults.KYMO_ROW_FRACTION,
			EnumResults.KYMO_ROW_FRACTION_CHANGE, EnumResults.KYMO_GREEN_HEIGHT, EnumResults.KYMO_GREEN_HEIGHT_RATIO };

	private final JButton exportToXLSButton2 = new JButton("save");
	private final JCheckBox includeGroupsCheckBox = new JCheckBox("Include group SUM, PI and PInorm (CSV)", false);
	private final JCheckBox t0CheckBox = new JCheckBox("(max-t)/max", true);
	private final JCheckBox discardNoFlyCageCheckBox = new JCheckBox("discard cages with no fly", true);
	private MeasureList frameMeasuresList;
	private MeasureList kymoMeasuresList;

	void init(GridLayout capLayout) {
		setLayout(new BorderLayout(0, 6));

		frameMeasuresList = new MeasureList(FRAME_MEASURES);
		frameMeasuresList.setSelectedValue(EnumResults.FILTER_GREY, true);
		kymoMeasuresList = new MeasureList(KYMO_MEASURES);

		JPanel measures = new JPanel(new GridLayout(1, 2, 8, 0));
		measures.add(wrapList("Measures on each frame", frameMeasuresList));
		measures.add(wrapList("Measures on kymographs", kymoMeasuresList));
		add(measures, BorderLayout.CENTER);

		includeGroupsCheckBox
				.setToolTipText("Include camera and kymograph group results in the normalized CSV package.");

		JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		options.setBorder(BorderFactory.createTitledBorder("Options"));
		options.add(t0CheckBox);
		options.add(discardNoFlyCageCheckBox);
		options.add(includeGroupsCheckBox);

		JPanel optionsRow = new JPanel(new BorderLayout(8, 0));
		optionsRow.add(options, BorderLayout.CENTER);
		optionsRow.add(exportToXLSButton2, BorderLayout.EAST);
		add(optionsRow, BorderLayout.SOUTH);

		exportToXLSButton2.addActionListener(e -> firePropertyChange("EXPORT_SPOTSMEASURES", false, true));
	}

	/** Copy this panel's current selections, including unchecked flags, into export options. */
	void applyTo(ResultsOptions options) {
		options.spotAreas = true;
		options.sum = exports(EnumResults.FILTER_GREY);
		options.spotSumNoFly = exports(EnumResults.FILTER_GREY_FLY_MASKED);
		options.spotSumClean = exports(EnumResults.FILTER_GREY_SMOOTHED);
		options.spotAreaCountV5 = exports(EnumResults.FILTER_V5_PIXEL_COUNT);
		options.spotGreySumV5 = exports(EnumResults.FILTER_V5_GREY);
		options.spotGreySumCleanV5 = exports(EnumResults.FILTER_V5_GREY_SMOOTHED);
		options.spotCrossRatio = exports(EnumResults.KYMO_CROSS_RATIO);
		options.spotRimRatio = exports(EnumResults.KYMO_RIM_RATIO);
		options.spotKymoFract = exports(EnumResults.KYMO_ROW_FRACTION);
		options.spotKymoAbsDelta = exports(EnumResults.KYMO_ROW_FRACTION_CHANGE);
		options.spotKymoGreenHeight = exports(EnumResults.KYMO_GREEN_HEIGHT);
		options.spotKymoGreenHeightRatio = exports(EnumResults.KYMO_GREEN_HEIGHT_RATIO);
		options.relativeToMaximum = t0CheckBox.isSelected();
		options.onlyalive = discardNoFlyCageCheckBox.isSelected();
	}

	private boolean exports(EnumResults measure) {
		return selected(frameMeasuresList, measure) || selected(kymoMeasuresList, measure);
	}

	boolean includeGroups() {
		return includeGroupsCheckBox.isSelected();
	}

	private static boolean selected(JList<EnumResults> list, EnumResults measure) {
		return list != null && measure != null && list.getSelectedValuesList().contains(measure);
	}

	private static JPanel wrapList(String title, MeasureList list) {
		JScrollPane scroll = new JScrollPane(list);
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(BorderFactory.createTitledBorder(title));
		panel.add(scroll, BorderLayout.CENTER);
		return panel;
	}

}
