package plugins.fmp.multitools.tools.chart;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.Range;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import plugins.fmp.multitools.tools.chart.SpotChartMeasures.Family;
import plugins.fmp.multitools.tools.chart.builders.KymoSpotChartSupport;
import plugins.fmp.multitools.tools.chart.interaction.SpotOverlayChartInteractionHandler;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

/** Line measures use the same spot navigation and comparison rules as image measures. */
public class ChartLineSpotsOverlayFrame extends ChartSpotsOverlayFrame {
	private final Map<EnumResults, JCheckBox> measures = new LinkedHashMap<>();
	private JCheckBox lastAction;

	@Override protected JPanel createTopControlsPanel(ResultsOptions options) {
		JPanel panel = new JPanel(new BorderLayout());
		JPanel choices = new JPanel(new GridLayout(0, 2));
		for (EnumResults result : Family.LINES.measures(false)) {
			JCheckBox check = measureCheckBox(result);
			check.setSelected(result == options.resultType);
			measures.put(result, check);
			choices.add(check);
			check.addActionListener(e -> {
				lastAction = check;
				try { onMeasureCheckboxChanged(); } finally { lastAction = null; }
			});
		}
		JPanel navigation = new JPanel(new FlowLayout(FlowLayout.LEFT));
		addSpotNavigation(navigation);
		navigation.add(updateButton);
		navigation.setToolTipText("One spot: compare measures. Selected ROI(s): compare spots using one measure.");
		updateButton.addActionListener(e -> refreshChart());
		panel.add(choices, BorderLayout.NORTH);
		panel.add(navigation, BorderLayout.SOUTH);
		return panel;
	}

	@Override protected void setCheckboxDefaults(ResultsOptions options) {
		for (Map.Entry<EnumResults, JCheckBox> item : measures.entrySet())
			item.getValue().setSelected(item.getKey() == options.resultType);
	}

	@Override protected List<EnumResults> getEnabledMeasures(ResultsOptions options) {
		List<EnumResults> selected = new ArrayList<>();
		for (Map.Entry<EnumResults, JCheckBox> item : measures.entrySet())
			if (item.getValue().isSelected()) selected.add(item.getKey());
		return selected;
	}

	@Override protected void maybeEnforceSingleMeasureSelection() {
		if (lastSelectedSpots == null || lastSelectedSpots.size() < 2) return;
		JCheckBox keep = lastAction != null && lastAction.isSelected() ? lastAction : null;
		if (keep == null) for (JCheckBox check : measures.values())
			if (check.isSelected()) { keep = check; break; }
		for (JCheckBox check : measures.values()) check.setSelected(check == keep);
	}

	@Override protected void refreshChart() {
		if (!refreshSelection()) return;
		maybeEnforceSingleMeasureSelection();
		List<EnumResults> selected = getEnabledMeasures(lastOptions);
		Range keepX = null;
		Map<String, Range> keepY = new LinkedHashMap<>();
		if (chartPanel != null) {
			XYPlot old = chartPanel.getChart().getXYPlot();
			if (!old.getDomainAxis().isAutoRange()) keepX = old.getDomainAxis().getRange();
			for (int i = 0; i < old.getRangeAxisCount(); i++)
				if (old.getRangeAxis(i) != null && !old.getRangeAxis(i).isAutoRange())
					keepY.put(old.getRangeAxis(i).getLabel(), old.getRangeAxis(i).getRange());
		}
		mainChartPanel.removeAll();
		chartPanel = null;
		if (selected.isEmpty()) {
			mainChartPanel.add(new JLabel("Select at least one measure."), BorderLayout.CENTER);
			mainChartPanel.revalidate(); mainChartPanel.repaint(); return;
		}
		NumberAxis time = new NumberAxis("Time (min)");
		time.setAutoRangeIncludesZero(false);
		if (keepX != null) time.setRange(keepX);
		XYPlot plot = new XYPlot(null, time, null, null);
		plot.setNoDataMessage("No data");
		Map<String, Integer> axes = new LinkedHashMap<>();
		Color[] colors = { Color.BLUE, Color.RED, new Color(0, 130, 0), Color.MAGENTA, Color.ORANGE, Color.CYAN };
		int datasetIndex = 0;
		for (EnumResults result : selected) {
			String unit = result.toUnit();
			if (!axes.containsKey(unit)) {
				int index = axes.size();
				axes.put(unit, index);
				NumberAxis axis = new NumberAxis(unit);
				axis.setAutoRangeIncludesZero(true);
				if (keepY.containsKey(unit)) axis.setRange(keepY.get(unit));
				plot.setRangeAxis(index, axis);
			}
			ResultsOptions options = new ResultsOptions();
			options.copy(lastOptions);
			options.resultType = result;
			XYSeriesCollection dataset = KymoSpotChartSupport.buildOverlayForSpots(lastExperiment, options, lastSelectedSpots);
			XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
			for (int i = 0; i < dataset.getSeriesCount(); i++) {
				XYSeries series = dataset.getSeries(i);
				if (lastSelectedSpots.size() == 1)
					series.setKey(lastSelectedSpots.get(0).getName() + "::" + result.name());
				renderer.setSeriesPaint(i, colors[(lastSelectedSpots.size() > 1 ? i : datasetIndex) % colors.length]);
			}
			plot.setDataset(datasetIndex, dataset);
			plot.setRenderer(datasetIndex, renderer);
			plot.mapDatasetToRangeAxis(datasetIndex, axes.get(unit));
			datasetIndex++;
		}
		JFreeChart chart = new JFreeChart(plot);
		chartPanel = new ChartPanel(chart);
		// Each dataset may use a different source timeline: route clicks with its measure.
		chartPanel.addChartMouseListener(new org.jfree.chart.ChartMouseListener() {
			@Override public void chartMouseClicked(org.jfree.chart.ChartMouseEvent event) {
				if (!(event.getEntity() instanceof org.jfree.chart.entity.XYItemEntity)) return;
				org.jfree.data.xy.XYDataset clicked = ((org.jfree.chart.entity.XYItemEntity) event.getEntity()).getDataset();
				for (int i = 0; i < selected.size(); i++) if (plot.getDataset(i) == clicked) {
					ResultsOptions options = new ResultsOptions(); options.copy(lastOptions); options.resultType = selected.get(i);
					new SpotOverlayChartInteractionHandler(lastExperiment, options,
							spotExclusiveSelectionController == null ? null : spotExclusiveSelectionController::selectExclusiveSpot)
							.createMouseListener().chartMouseClicked(event);
					break;
				}
			}
			@Override public void chartMouseMoved(org.jfree.chart.ChartMouseEvent event) { }
		});
		mainChartPanel.add(chartPanel, BorderLayout.CENTER);
		updateFrameTitleWithSelection(selected, lastSelectedSpots);
		mainChartPanel.revalidate(); mainChartPanel.repaint();
	}
}
