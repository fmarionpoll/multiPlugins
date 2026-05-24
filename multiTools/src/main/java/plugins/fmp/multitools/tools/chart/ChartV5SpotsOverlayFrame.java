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
 * Selected-spot overlay for V5 spot measures. Uses explicit checkboxes for
 * {@code AREA_COUNT_V5}, {@code GREY_SUM_V5}, {@code GREY_SUM_V5_PREFLY} (grey before fly-occupancy NaN),
 * {@code GREY_SUM_CLEAN_V5}, and fly presence,
 * plus a second row for legacy area series {@code AREA_SUM}, {@code AREA_SUMNOFLY},
 * {@code AREA_SUMCLEAN} (same as {@link ChartSpotsOverlayFrame}).
 */
public class ChartV5SpotsOverlayFrame extends ChartSpotsOverlayFrame {

	private final JCheckBox cbAreaCountV5 = new JCheckBox("AREA_COUNT_V5");
	private final JCheckBox cbGreySumV5 = new JCheckBox("GREY_SUM_V5");
	private final JCheckBox cbGreySumV5PreFly = new JCheckBox("GREY_SUM_V5_PREFLY");
	private final JCheckBox cbGreySumCleanV5 = new JCheckBox("GREY_SUM_CLEAN_V5");

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

		row1.add(cbAreaCountV5);
		row1.add(cbGreySumV5);
		row1.add(cbGreySumV5PreFly);
		row1.add(cbGreySumCleanV5);
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

		cbAreaCountV5.addActionListener(this::onOverlayMeasureCheckboxAction);
		cbGreySumV5.addActionListener(this::onOverlayMeasureCheckboxAction);
		cbGreySumV5PreFly.addActionListener(this::onOverlayMeasureCheckboxAction);
		cbGreySumCleanV5.addActionListener(this::onOverlayMeasureCheckboxAction);
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
		return cb == cbAreaCountV5 || cb == cbGreySumV5 || cb == cbGreySumV5PreFly || cb == cbGreySumCleanV5
				|| cb == cbFlyPresent
				|| cb == cbSum || cb == cbNoFly || cb == cbClean;
	}

	@Override
	protected void setCheckboxDefaults(ResultsOptions options) {
		EnumResults rt = options != null ? options.resultType : null;
		cbCleanV3.setSelected(false);
		cbAreaCountV5.setSelected(false);
		cbGreySumV5.setSelected(false);
		cbGreySumV5PreFly.setSelected(false);
		cbGreySumCleanV5.setSelected(false);
		cbFlyPresent.setSelected(false);
		cbSum.setSelected(false);
		cbNoFly.setSelected(false);
		cbClean.setSelected(false);

		if (rt == EnumResults.AREA_COUNT_V5) {
			cbAreaCountV5.setSelected(true);
		} else if (rt == EnumResults.GREY_SUM_V5) {
			cbGreySumV5.setSelected(true);
		} else if (rt == EnumResults.GREY_SUM_V5_PREFLY) {
			cbGreySumV5PreFly.setSelected(true);
		} else if (rt == EnumResults.GREY_SUM_CLEAN_V5) {
			cbGreySumCleanV5.setSelected(true);
		} else if (resultLabelEquals(rt, "AREA_FLYPRESENT")) {
			cbFlyPresent.setSelected(true);
		} else if (resultLabelEquals(rt, "AREA_SUM")) {
			cbSum.setSelected(true);
		} else if (resultLabelEquals(rt, "AREA_SUMNOFLY")) {
			cbNoFly.setSelected(true);
		} else if (resultLabelEquals(rt, "AREA_SUMCLEAN") || resultLabelEquals(rt, "AREA_SUMCLEAN_V3")) {
			cbClean.setSelected(true);
		} else {
			cbGreySumCleanV5.setSelected(true);
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
			keep = cbGreySumCleanV5.isSelected() ? cbGreySumCleanV5
					: cbGreySumV5PreFly.isSelected() ? cbGreySumV5PreFly
							: cbGreySumV5.isSelected() ? cbGreySumV5
									: cbAreaCountV5.isSelected() ? cbAreaCountV5
											: cbClean.isSelected() ? cbClean
													: cbNoFly.isSelected() ? cbNoFly
															: cbSum.isSelected() ? cbSum : cbFlyPresent;
		}
		cbAreaCountV5.setSelected(keep == cbAreaCountV5);
		cbGreySumV5.setSelected(keep == cbGreySumV5);
		cbGreySumV5PreFly.setSelected(keep == cbGreySumV5PreFly);
		cbGreySumCleanV5.setSelected(keep == cbGreySumCleanV5);
		cbFlyPresent.setSelected(keep == cbFlyPresent);
		cbSum.setSelected(keep == cbSum);
		cbNoFly.setSelected(keep == cbNoFly);
		cbClean.setSelected(keep == cbClean);
	}

	private int countSelectedOverlayMeasures() {
		int n = 0;
		if (cbAreaCountV5.isSelected()) {
			n++;
		}
		if (cbGreySumV5.isSelected()) {
			n++;
		}
		if (cbGreySumV5PreFly.isSelected()) {
			n++;
		}
		if (cbGreySumCleanV5.isSelected()) {
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
		if (cbAreaCountV5.isSelected()) {
			out.add(EnumResults.AREA_COUNT_V5);
		}
		if (cbGreySumV5.isSelected()) {
			out.add(EnumResults.GREY_SUM_V5);
		}
		if (cbGreySumV5PreFly.isSelected()) {
			out.add(EnumResults.GREY_SUM_V5_PREFLY);
		}
		if (cbGreySumCleanV5.isSelected()) {
			out.add(EnumResults.GREY_SUM_CLEAN_V5);
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
		case "AREA_COUNT_V5":
			return new Color(0, 102, 153);
		case "GREY_SUM_V5":
			return new Color(0, 120, 70);
		case "GREY_SUM_V5_PREFLY":
			return new Color(180, 95, 20);
		case "GREY_SUM_CLEAN_V5":
			return new Color(0, 170, 95);
		default:
			return super.pickMeasureColor(resultType);
		}
	}
}
