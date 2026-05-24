package plugins.fmp.multitools.tools.chart;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;

import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeriesCollection;

import plugins.fmp.multitools.tools.chart.builders.SpotChartSeriesKeys;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

/**
 * Selected-spot overlay for V6 spot measures. Uses explicit checkboxes for
 * {@code AREA_COUNT_V6}, {@code GREY_SUM_V6}, {@code GREY_SUM_V6_PREFLY} (grey before fly-occupancy NaN),
 * {@code GREY_SUM_CLEAN_V6}, and fly presence,
 * plus a second row for legacy area series {@code AREA_SUM}, {@code AREA_SUMNOFLY},
 * {@code AREA_SUMCLEAN} (same as {@link ChartSpotsOverlayFrame}).
 */
public class ChartV6SpotsOverlayFrame extends ChartSpotsOverlayFrame {

	private final JCheckBox cbAreaCountV6 = new JCheckBox("AREA_COUNT_V6");
	private final JCheckBox cbGreySumV6 = new JCheckBox("GREY_SUM_V6");
	private final JCheckBox cbGreySumV6PreFly = new JCheckBox("GREY_SUM_V6_PREFLY");
	private final JCheckBox cbGreySumCleanV6 = new JCheckBox("GREY_SUM_CLEAN_V6");

	/** When several spots are overlaid, prefer the measure the user just toggled on. */
	private JCheckBox lastOverlayMeasureActionSource = null;

	@Override
	protected JPanel createTopControlsPanel(ResultsOptions options) {
		JPanel panel = new JPanel(new BorderLayout());
		JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));

		setCheckboxDefaults(options);

		spotSelectorCombo = new JComboBox<>();
		refreshSpotSelectorModel(false);
		spotSelectorCombo.addActionListener(e -> onSpotSelectorChanged());

		row1.add(cbAreaCountV6);
		row1.add(cbGreySumV6);
		row1.add(cbGreySumV6PreFly);
		row1.add(cbGreySumCleanV6);
		row1.add(cbFlyPresent);
		row1.add(cbDepletion);
		row1.add(spotSelectorCombo);
		row1.add(updateButton);

		row2.add(cbSum);
		row2.add(cbNoFly);
		row2.add(cbClean);

		panel.add(row1, BorderLayout.NORTH);
		panel.add(row2, BorderLayout.SOUTH);

		updateButton.addActionListener(e -> refreshChart());

		cbAreaCountV6.addActionListener(this::onOverlayMeasureCheckboxAction);
		cbGreySumV6.addActionListener(this::onOverlayMeasureCheckboxAction);
		cbGreySumV6PreFly.addActionListener(this::onOverlayMeasureCheckboxAction);
		cbGreySumCleanV6.addActionListener(this::onOverlayMeasureCheckboxAction);
		cbFlyPresent.addActionListener(this::onOverlayMeasureCheckboxAction);
		cbSum.addActionListener(this::onOverlayMeasureCheckboxAction);
		cbNoFly.addActionListener(this::onOverlayMeasureCheckboxAction);
		cbClean.addActionListener(this::onOverlayMeasureCheckboxAction);
		cbDepletion.addActionListener(e -> refreshChart());

		return panel;
	}

	private void onOverlayMeasureCheckboxAction(ActionEvent e) {
		Object src = e.getSource();
		lastOverlayMeasureActionSource = src instanceof JCheckBox ? (JCheckBox) src : null;
		try {
			onMeasureCheckboxChanged();
		} finally {
			lastOverlayMeasureActionSource = null;
		}
	}

	private boolean isTrackedOverlayMeasureCheckbox(JCheckBox cb) {
		return cb == cbAreaCountV6 || cb == cbGreySumV6 || cb == cbGreySumV6PreFly || cb == cbGreySumCleanV6
				|| cb == cbFlyPresent
				|| cb == cbSum || cb == cbNoFly || cb == cbClean;
	}

	@Override
	protected void setCheckboxDefaults(ResultsOptions options) {
		EnumResults rt = options != null ? options.resultType : null;
		cbCleanV3.setSelected(false);
		cbAreaCountV6.setSelected(false);
		cbGreySumV6.setSelected(false);
		cbGreySumV6PreFly.setSelected(false);
		cbGreySumCleanV6.setSelected(false);
		cbFlyPresent.setSelected(false);
		cbSum.setSelected(false);
		cbNoFly.setSelected(false);
		cbClean.setSelected(false);

		if (rt == EnumResults.AREA_COUNT_V6) {
			cbAreaCountV6.setSelected(true);
		} else if (rt == EnumResults.GREY_SUM_V6) {
			cbGreySumV6.setSelected(true);
		} else if (rt == EnumResults.GREY_SUM_V6_PREFLY) {
			cbGreySumV6PreFly.setSelected(true);
		} else if (rt == EnumResults.GREY_SUM_CLEAN_V6) {
			cbGreySumCleanV6.setSelected(true);
		} else if (resultLabelEquals(rt, "AREA_FLYPRESENT")) {
			cbFlyPresent.setSelected(true);
		} else if (resultLabelEquals(rt, "AREA_SUM")) {
			cbSum.setSelected(true);
		} else if (resultLabelEquals(rt, "AREA_SUMNOFLY")) {
			cbNoFly.setSelected(true);
		} else if (resultLabelEquals(rt, "AREA_SUMCLEAN") || resultLabelEquals(rt, "AREA_SUMCLEAN_V3")) {
			cbClean.setSelected(true);
		} else {
			cbGreySumCleanV6.setSelected(true);
		}
	}

	@Override
	protected void maybeEnforceSingleMeasureSelection() {
		if (lastSelectedSpots == null || lastSelectedSpots.size() <= 1) {
			return;
		}
		int nv = countSelectedOverlayMeasures();
		if (nv <= 1) {
			return;
		}
		JCheckBox keep = null;
		if (lastOverlayMeasureActionSource != null && lastOverlayMeasureActionSource.isSelected()
				&& isTrackedOverlayMeasureCheckbox(lastOverlayMeasureActionSource)) {
			keep = lastOverlayMeasureActionSource;
		}
		if (keep == null) {
			keep = cbGreySumCleanV6.isSelected() ? cbGreySumCleanV6
					: cbGreySumV6PreFly.isSelected() ? cbGreySumV6PreFly
							: cbGreySumV6.isSelected() ? cbGreySumV6
									: cbAreaCountV6.isSelected() ? cbAreaCountV6
											: cbClean.isSelected() ? cbClean
													: cbNoFly.isSelected() ? cbNoFly
															: cbSum.isSelected() ? cbSum : cbFlyPresent;
		}
		cbAreaCountV6.setSelected(keep == cbAreaCountV6);
		cbGreySumV6.setSelected(keep == cbGreySumV6);
		cbGreySumV6PreFly.setSelected(keep == cbGreySumV6PreFly);
		cbGreySumCleanV6.setSelected(keep == cbGreySumCleanV6);
		cbFlyPresent.setSelected(keep == cbFlyPresent);
		cbSum.setSelected(keep == cbSum);
		cbNoFly.setSelected(keep == cbNoFly);
		cbClean.setSelected(keep == cbClean);
	}

	private int countSelectedOverlayMeasures() {
		int n = 0;
		if (cbAreaCountV6.isSelected()) {
			n++;
		}
		if (cbGreySumV6.isSelected()) {
			n++;
		}
		if (cbGreySumV6PreFly.isSelected()) {
			n++;
		}
		if (cbGreySumCleanV6.isSelected()) {
			n++;
		}
		if (cbFlyPresent.isSelected()) {
			n++;
		}
		if (cbSum.isSelected()) {
			n++;
		}
		if (cbNoFly.isSelected()) {
			n++;
		}
		if (cbClean.isSelected()) {
			n++;
		}
		return n;
	}

	@Override
	protected List<EnumResults> getEnabledMeasures(ResultsOptions options) {
		List<EnumResults> out = new ArrayList<>();
		if (cbAreaCountV6.isSelected()) {
			out.add(EnumResults.AREA_COUNT_V6);
		}
		if (cbGreySumV6.isSelected()) {
			out.add(EnumResults.GREY_SUM_V6);
		}
		if (cbGreySumV6PreFly.isSelected()) {
			out.add(EnumResults.GREY_SUM_V6_PREFLY);
		}
		if (cbGreySumCleanV6.isSelected()) {
			out.add(EnumResults.GREY_SUM_CLEAN_V6);
		}
		if (cbFlyPresent.isSelected()) {
			addResultIfDefined(out, "AREA_FLYPRESENT");
		}
		if (cbSum.isSelected()) {
			addResultIfDefined(out, "AREA_SUM");
		}
		if (cbNoFly.isSelected()) {
			addResultIfDefined(out, "AREA_SUMNOFLY");
		}
		if (cbClean.isSelected()) {
			addResultIfDefined(out, "AREA_SUMCLEAN");
		}
		if (out.isEmpty() && options != null && options.resultType != null) {
			out.add(options.resultType);
		}
		return out;
	}

	@Override
	protected void customizeSpotOverlayRendererStrokes(XYSeriesCollection dataset, XYLineAndShapeRenderer renderer,
			boolean overlaySpots, List<EnumResults> enabledMeasures, boolean flyDatasetSlice) {
		if (dataset == null || renderer == null) {
			return;
		}
		if (flyDatasetSlice) {
			return;
		}
		Stroke solid = new BasicStroke(1.6f);
		Stroke dashed = new BasicStroke(1.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f,
				new float[] { 6f, 5f }, 0f);

		EnumResults overlayChosen = null;
		if (overlaySpots && enabledMeasures != null && !enabledMeasures.isEmpty()) {
			overlayChosen = enabledMeasures.get(0);
		}

		for (int i = 0; i < dataset.getSeriesCount(); i++) {
			String key = String.valueOf(dataset.getSeriesKey(i));
			boolean v2v3 = key.contains("AREA_SUM_V2") || key.contains("AREA_SUMNOFLY_V2")
					|| key.contains("AREA_SUMCLEAN_V2") || key.contains("_V2") || key.contains("AREA_SUMCLEAN_V3");
			boolean legacyDashed = overlaySpots ? isLegacySecondRowAreaMeasure(overlayChosen)
					: seriesKeyUsesLegacySecondRowMeasure(key);
			if (legacyDashed) {
				renderer.setSeriesStroke(i, dashed);
			} else if (!v2v3) {
				renderer.setSeriesStroke(i, solid);
			}
		}
	}

	private static boolean isLegacySecondRowAreaMeasure(EnumResults r) {
		if (r == null) {
			return false;
		}
		String n = r.name();
		return "AREA_SUM".equals(n) || "AREA_SUMNOFLY".equals(n) || "AREA_SUMCLEAN".equals(n);
	}

	private static boolean seriesKeyUsesLegacySecondRowMeasure(String key) {
		if (key == null) {
			return false;
		}
		String sep = SpotChartSeriesKeys.SEP;
		return key.endsWith(sep + "AREA_SUM") || key.endsWith(sep + "AREA_SUMNOFLY")
				|| key.endsWith(sep + "AREA_SUMCLEAN");
	}

	@Override
	protected Color pickMeasureColor(EnumResults resultType) {
		if (resultType == null) {
			return Color.BLACK;
		}
		switch (resultType.name()) {
		case "AREA_COUNT_V6":
			return new Color(0, 102, 153);
		case "GREY_SUM_V6":
			return new Color(0, 120, 70);
		case "GREY_SUM_V6_PREFLY":
			return new Color(180, 95, 20);
		case "GREY_SUM_CLEAN_V6":
			return new Color(0, 170, 95);
		default:
			return super.pickMeasureColor(resultType);
		}
	}
}
