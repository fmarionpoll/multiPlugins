package plugins.fmp.multitools.tools.chart;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;

import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

/**
 * Selected-spot overlay for V5 spot measures. Uses explicit checkboxes for
 * {@code AREA_COUNT_V5}, {@code GREY_SUM_V5}, {@code GREY_SUM_CLEAN_V5}, and fly presence;
 * legacy sum / clean controls remain on {@link ChartSpotsOverlayFrame}.
 */
public class ChartV5SpotsOverlayFrame extends ChartSpotsOverlayFrame {

	private final JCheckBox cbAreaCountV5 = new JCheckBox("AREA_COUNT_V5");
	private final JCheckBox cbGreySumV5 = new JCheckBox("GREY_SUM_V5");
	private final JCheckBox cbGreySumCleanV5 = new JCheckBox("GREY_SUM_CLEAN_V5");

	@Override
	protected JPanel createTopControlsPanel(ResultsOptions options) {
		JPanel panel = new JPanel(new BorderLayout());
		JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));

		setCheckboxDefaults(options);

		spotSelectorCombo = new JComboBox<>();
		refreshSpotSelectorModel(false);
		spotSelectorCombo.addActionListener(e -> onSpotSelectorChanged());

		row1.add(cbAreaCountV5);
		row1.add(cbGreySumV5);
		row1.add(cbGreySumCleanV5);
		row1.add(cbFlyPresent);
		row1.add(cbDepletion);
		row1.add(spotSelectorCombo);
		row1.add(updateButton);

		panel.add(row1, BorderLayout.NORTH);
		panel.add(row2, BorderLayout.SOUTH);

		updateButton.addActionListener(e -> refreshChart());

		cbAreaCountV5.addActionListener(e -> onMeasureCheckboxChanged());
		cbGreySumV5.addActionListener(e -> onMeasureCheckboxChanged());
		cbGreySumCleanV5.addActionListener(e -> onMeasureCheckboxChanged());
		cbFlyPresent.addActionListener(e -> onMeasureCheckboxChanged());
		cbDepletion.addActionListener(e -> refreshChart());

		return panel;
	}

	@Override
	protected void setCheckboxDefaults(ResultsOptions options) {
		EnumResults rt = options != null ? options.resultType : null;
		cbSum.setSelected(false);
		cbNoFly.setSelected(false);
		cbClean.setSelected(false);
		cbCleanV3.setSelected(false);
		cbAreaCountV5.setSelected(rt == EnumResults.AREA_COUNT_V5);
		cbGreySumV5.setSelected(rt == EnumResults.GREY_SUM_V5);
		cbGreySumCleanV5.setSelected(rt == EnumResults.GREY_SUM_CLEAN_V5);
		cbFlyPresent.setSelected(resultLabelEquals(rt, "AREA_FLYPRESENT"));
		if (!cbAreaCountV5.isSelected() && !cbGreySumV5.isSelected() && !cbGreySumCleanV5.isSelected()
				&& !cbFlyPresent.isSelected()) {
			if (rt == EnumResults.AREA_COUNT_V5) {
				cbAreaCountV5.setSelected(true);
			} else if (rt == EnumResults.GREY_SUM_V5) {
				cbGreySumV5.setSelected(true);
			} else if (rt == EnumResults.GREY_SUM_CLEAN_V5) {
				cbGreySumCleanV5.setSelected(true);
			} else if (resultLabelEquals(rt, "AREA_FLYPRESENT")) {
				cbFlyPresent.setSelected(true);
			} else {
				cbGreySumCleanV5.setSelected(true);
			}
		}
	}

	@Override
	protected void maybeEnforceSingleMeasureSelection() {
		if (lastSelectedSpots == null || lastSelectedSpots.size() <= 1) {
			return;
		}
		int nv = countSelectedV5OverlayMeasures();
		if (nv <= 1) {
			return;
		}
		JCheckBox keep = cbGreySumCleanV5.isSelected() ? cbGreySumCleanV5
				: cbGreySumV5.isSelected() ? cbGreySumV5 : cbAreaCountV5.isSelected() ? cbAreaCountV5 : cbFlyPresent;
		cbAreaCountV5.setSelected(keep == cbAreaCountV5);
		cbGreySumV5.setSelected(keep == cbGreySumV5);
		cbGreySumCleanV5.setSelected(keep == cbGreySumCleanV5);
		cbFlyPresent.setSelected(keep == cbFlyPresent);
	}

	private int countSelectedV5OverlayMeasures() {
		int n = 0;
		if (cbAreaCountV5.isSelected()) {
			n++;
		}
		if (cbGreySumV5.isSelected()) {
			n++;
		}
		if (cbGreySumCleanV5.isSelected()) {
			n++;
		}
		if (cbFlyPresent.isSelected()) {
			n++;
		}
		return n;
	}

	@Override
	protected List<EnumResults> getEnabledMeasures(ResultsOptions options) {
		List<EnumResults> out = new ArrayList<>();
		if (cbAreaCountV5.isSelected()) {
			out.add(EnumResults.AREA_COUNT_V5);
		}
		if (cbGreySumV5.isSelected()) {
			out.add(EnumResults.GREY_SUM_V5);
		}
		if (cbGreySumCleanV5.isSelected()) {
			out.add(EnumResults.GREY_SUM_CLEAN_V5);
		}
		if (cbFlyPresent.isSelected()) {
			addResultIfDefined(out, "AREA_FLYPRESENT");
		}
		if (out.isEmpty() && options != null && options.resultType != null) {
			out.add(options.resultType);
		}
		return out;
	}

	@Override
	protected Color pickMeasureColor(EnumResults resultType) {
		if (resultType == null) {
			return Color.BLACK;
		}
		switch (resultType.name()) {
		case "AREA_COUNT_V5":
			return new Color(0, 102, 153);
		case "GREY_SUM_V5":
			return new Color(0, 120, 70);
		case "GREY_SUM_CLEAN_V5":
			return new Color(0, 170, 95);
		default:
			return super.pickMeasureColor(resultType);
		}
	}
}
