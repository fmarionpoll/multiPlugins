package plugins.fmp.multitools.tools.chart;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JPanel;

import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeriesCollection;

import plugins.fmp.multitools.tools.chart.builders.SpotChartSeriesKeys;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

/**
 * Selected-spot overlay for color-distance spot measures. Uses explicit checkboxes for
 * on-disk keys {@code AREA_COUNT_V6}, {@code GREY_SUM_V6}, {@code GREY_SUM_V6_PREFLY},
 * {@code GREY_SUM_CLEAN_V6}, and fly presence,
 * plus a second row for legacy area series {@code FILTER_GREY}, {@code FILTER_GREY_FLY_MASKED},
 * {@code FILTER_GREY_SMOOTHED} (same as {@link ChartSpotsOverlayFrame}).
 */
public class ChartColorSpotsOverlayFrame extends ChartSpotsOverlayFrame {

	private final JCheckBox cbAreaCountColor = new JCheckBox(EnumResults.COLOR_PIXEL_COUNT.toDisplayLabel());
	private final JCheckBox cbGreySumColor = new JCheckBox(EnumResults.COLOR_GREY.toDisplayLabel());
	private final JCheckBox cbGreySumColorPreFly = new JCheckBox(EnumResults.COLOR_GREY_PRE_GATE.toDisplayLabel());
	private final JCheckBox cbGreySumCleanColor = new JCheckBox(EnumResults.COLOR_GREY_SMOOTHED.toDisplayLabel());

	/** When several spots are overlaid, prefer the measure the user just toggled on. */
	private JCheckBox lastOverlayMeasureActionSource = null;

	@Override
	protected JPanel createTopControlsPanel(ResultsOptions options) {
		JPanel panel = new JPanel(new BorderLayout());
		JPanel row1 = new JPanel(new GridLayout(0, 2));
		JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));

		setCheckboxDefaults(options);


		row1.add(cbAreaCountColor);
		row1.add(cbGreySumColor);
		row1.add(cbGreySumColorPreFly);
		row1.add(cbGreySumCleanColor);
		row1.add(cbFlyPresent);
		row1.add(cbDepletion);
		row1.add(cbSum);
		row1.add(cbNoFly);
		row1.add(cbClean);
		addSpotNavigation(row2);
		row2.add(updateButton);

		panel.add(row1, BorderLayout.NORTH);
		panel.add(row2, BorderLayout.SOUTH);

		updateButton.addActionListener(e -> refreshChart());

		cbAreaCountColor.addActionListener(this::onOverlayMeasureCheckboxAction);
		cbGreySumColor.addActionListener(this::onOverlayMeasureCheckboxAction);
		cbGreySumColorPreFly.addActionListener(this::onOverlayMeasureCheckboxAction);
		cbGreySumCleanColor.addActionListener(this::onOverlayMeasureCheckboxAction);
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
		return cb == cbAreaCountColor || cb == cbGreySumColor || cb == cbGreySumColorPreFly || cb == cbGreySumCleanColor
				|| cb == cbFlyPresent
				|| cb == cbSum || cb == cbNoFly || cb == cbClean;
	}

	@Override
	protected void setCheckboxDefaults(ResultsOptions options) {
		EnumResults rt = options != null ? options.resultType : null;
		cbAreaCountColor.setSelected(false);
		cbGreySumColor.setSelected(false);
		cbGreySumColorPreFly.setSelected(false);
		cbGreySumCleanColor.setSelected(false);
		cbFlyPresent.setSelected(false);
		cbSum.setSelected(false);
		cbNoFly.setSelected(false);
		cbClean.setSelected(false);

		if (rt == EnumResults.COLOR_PIXEL_COUNT) {
			cbAreaCountColor.setSelected(true);
		} else if (rt == EnumResults.COLOR_GREY) {
			cbGreySumColor.setSelected(true);
		} else if (rt == EnumResults.COLOR_GREY_PRE_GATE) {
			cbGreySumColorPreFly.setSelected(true);
		} else if (rt == EnumResults.COLOR_GREY_SMOOTHED) {
			cbGreySumCleanColor.setSelected(true);
		} else if (resultLabelEquals(rt, "AREA_FLYPRESENT")) {
			cbFlyPresent.setSelected(true);
		} else if (resultLabelEquals(rt, "AREA_SUM")) {
			cbSum.setSelected(true);
		} else if (resultLabelEquals(rt, "AREA_SUMNOFLY")) {
			cbNoFly.setSelected(true);
		} else if (resultLabelEquals(rt, "AREA_SUMCLEAN")) {
			cbClean.setSelected(true);
		} else {
			cbGreySumCleanColor.setSelected(true);
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
			keep = cbGreySumCleanColor.isSelected() ? cbGreySumCleanColor
					: cbGreySumColorPreFly.isSelected() ? cbGreySumColorPreFly
							: cbGreySumColor.isSelected() ? cbGreySumColor
									: cbAreaCountColor.isSelected() ? cbAreaCountColor
											: cbClean.isSelected() ? cbClean
													: cbNoFly.isSelected() ? cbNoFly
															: cbSum.isSelected() ? cbSum : cbFlyPresent;
		}
		cbAreaCountColor.setSelected(keep == cbAreaCountColor);
		cbGreySumColor.setSelected(keep == cbGreySumColor);
		cbGreySumColorPreFly.setSelected(keep == cbGreySumColorPreFly);
		cbGreySumCleanColor.setSelected(keep == cbGreySumCleanColor);
		cbFlyPresent.setSelected(keep == cbFlyPresent);
		cbSum.setSelected(keep == cbSum);
		cbNoFly.setSelected(keep == cbNoFly);
		cbClean.setSelected(keep == cbClean);
	}

	private int countSelectedOverlayMeasures() {
		int n = 0;
		if (cbAreaCountColor.isSelected()) {
			n++;
		}
		if (cbGreySumColor.isSelected()) {
			n++;
		}
		if (cbGreySumColorPreFly.isSelected()) {
			n++;
		}
		if (cbGreySumCleanColor.isSelected()) {
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
		if (cbAreaCountColor.isSelected()) {
			out.add(EnumResults.COLOR_PIXEL_COUNT);
		}
		if (cbGreySumColor.isSelected()) {
			out.add(EnumResults.COLOR_GREY);
		}
		if (cbGreySumColorPreFly.isSelected()) {
			out.add(EnumResults.COLOR_GREY_PRE_GATE);
		}
		if (cbGreySumCleanColor.isSelected()) {
			out.add(EnumResults.COLOR_GREY_SMOOTHED);
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
			boolean legacyDashed = overlaySpots ? isLegacySecondRowAreaMeasure(overlayChosen)
					: seriesKeyUsesLegacySecondRowMeasure(key);
			if (legacyDashed) {
				renderer.setSeriesStroke(i, dashed);
			} else {
				renderer.setSeriesStroke(i, solid);
			}
		}
	}

	private static boolean isLegacySecondRowAreaMeasure(EnumResults r) {
		if (r == null) {
			return false;
		}
		String n = r.toPersistenceKey();
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
		switch (resultType) {
		case COLOR_PIXEL_COUNT:
			return new Color(0, 102, 153);
		case COLOR_GREY:
			return new Color(0, 120, 70);
		case COLOR_GREY_PRE_GATE:
			return new Color(180, 95, 20);
		case COLOR_GREY_SMOOTHED:
			return new Color(0, 170, 95);
		default:
			return super.pickMeasureColor(resultType);
		}
	}
}
