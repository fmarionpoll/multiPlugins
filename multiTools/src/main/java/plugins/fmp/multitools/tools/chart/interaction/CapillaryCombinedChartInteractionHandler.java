package plugins.fmp.multitools.tools.chart.interaction;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.entity.ChartEntity;
import org.jfree.chart.entity.XYItemEntity;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.chart.JFreeChartPlotCompat;
import plugins.fmp.multitools.tools.chart.builders.CapillaryChartSeriesKeys;

/**
 * Capillary click interactions for the MultiCAFE combined chart view (one
 * subplot per cage), supporting {@link org.jfree.chart.plot.CombinedDomainXYPlot}
 * and {@link org.jfree.chart.plot.CombinedRangeXYPlot}.
 */
public class CapillaryCombinedChartInteractionHandler {

	private static final int LEFT_MOUSE_BUTTON = MouseEvent.BUTTON1;

	private final Experiment experiment;
	/** Cages in the same order as subplots added to the combined plot. */
	private final List<Cage> subplotCages;

	public CapillaryCombinedChartInteractionHandler(Experiment experiment, List<Cage> subplotCages) {
		this.experiment = experiment;
		if (subplotCages != null) {
			this.subplotCages = Collections.unmodifiableList(new ArrayList<Cage>(subplotCages));
		} else {
			this.subplotCages = Collections.emptyList();
		}
	}

	public ChartMouseListener createMouseListener() {
		return new CombinedChartMouseListener();
	}

	private Capillary getCapillaryFromClickedChart(ChartMouseEvent e) {
		if (e == null || experiment == null || subplotCages.isEmpty()) {
			return null;
		}
		MouseEvent trigger = e.getTrigger();
		if (trigger == null || trigger.getButton() != LEFT_MOUSE_BUTTON) {
			return null;
		}
		if (!(trigger.getSource() instanceof ChartPanel)) {
			return null;
		}
		ChartPanel panel = (ChartPanel) trigger.getSource();
		JFreeChart chart = e.getChart();
		if (chart == null || !CombinedXYPlots.isCombinedXYPlot(chart.getPlot())) {
			return null;
		}
		Plot combined = chart.getPlot();

		Point2D java2DPoint = panel.translateScreenToJava2D(trigger.getPoint());
		ChartRenderingInfo chartInfo = panel.getChartRenderingInfo();
		if (chartInfo == null) {
			return null;
		}
		PlotRenderingInfo plotInfo = chartInfo.getPlotInfo();
		XYPlot subplot = CombinedXYPlots.findSubplot(combined, plotInfo, java2DPoint);
		if (subplot == null) {
			return null;
		}

		int subplotIndex = CombinedXYPlots.indexOfSubplot(combined, subplot);
		if (subplotIndex < 0 || subplotIndex >= subplotCages.size()) {
			Logger.warn("Combined chart subplot index out of range: " + subplotIndex);
			return null;
		}
		Cage cage = subplotCages.get(subplotIndex);
		if (cage == null) {
			return null;
		}

		ChartEntity entity = e.getEntity();
		if (entity instanceof XYItemEntity) {
			return getCapillaryFromXYItemEntity((XYItemEntity) entity, cage);
		}

		Rectangle2D dataArea = subplotDataArea(plotInfo, java2DPoint);
		return findClosestCapillaryFromPoint(java2DPoint, cage, subplot, combined, dataArea);
	}

	private static Rectangle2D subplotDataArea(PlotRenderingInfo plotInfo, Point2D java2DPoint) {
		if (plotInfo == null || java2DPoint == null) {
			return null;
		}
		int idx = plotInfo.getSubplotIndex(java2DPoint);
		if (idx < 0) {
			return plotInfo.getDataArea();
		}
		return plotInfo.getSubplotInfo(idx).getDataArea();
	}

	private Capillary getCapillaryFromXYItemEntity(XYItemEntity xyItemEntity, Cage cage) {
		if (xyItemEntity == null || cage == null) {
			return null;
		}
		XYDataset xyDataset = xyItemEntity.getDataset();
		if (xyDataset == null) {
			return null;
		}
		String seriesKey = (String) xyDataset.getSeriesKey(xyItemEntity.getSeriesIndex());
		return getCapillaryFromSeriesKey(seriesKey, cage);
	}

	private Capillary getCapillaryFromSeriesKey(String seriesKey, Cage cage) {
		if (seriesKey == null || cage == null || experiment == null) {
			return null;
		}
		String sideOrType = CapillaryChartSeriesKeys.sideOrTypeFromKey(seriesKey);
		if (sideOrType == null) {
			return null;
		}
		if ("threshold".equals(sideOrType) || "evaporation".equals(sideOrType)) {
			return null;
		}
		Capillary cap = CapillaryChartSeriesKeys.resolve(experiment, cage, seriesKey);
		if (cap == null && !"Sum".equals(sideOrType) && !"PI".equals(sideOrType)) {
			Logger.warn("Could not find capillary for series key: " + seriesKey);
		}
		return cap;
	}

	private Capillary findClosestCapillaryFromPoint(Point2D java2DPoint, Cage cage, XYPlot subplot, Plot combined,
			Rectangle2D dataArea) {
		if (java2DPoint == null || cage == null || subplot == null || dataArea == null) {
			return null;
		}

		ValueAxis domainAxis = CombinedXYPlots.resolveDomainAxis(subplot, combined);
		ValueAxis rangeAxis = CombinedXYPlots.resolveRangeAxis(subplot, combined);
		if (domainAxis == null || rangeAxis == null) {
			return null;
		}

		Plot domainPlot = subplot.getDomainAxis() != null ? subplot : combined;
		Plot rangePlot = subplot.getRangeAxis() != null ? subplot : combined;
		double clickedX = JFreeChartPlotCompat.domainJava2DToValue(domainAxis, java2DPoint.getX(), dataArea,
				domainPlot);
		double clickedY = JFreeChartPlotCompat.rangeJava2DToValue(rangeAxis, java2DPoint.getY(), dataArea, rangePlot);

		XYDataset dataset = subplot.getDataset();
		if (!(dataset instanceof XYSeriesCollection)) {
			return null;
		}
		XYSeriesCollection seriesCollection = (XYSeriesCollection) dataset;

		Map<Capillary, List<XYSeries>> capillaryToSeriesMap = new HashMap<>();
		for (int seriesIndex = 0; seriesIndex < seriesCollection.getSeriesCount(); seriesIndex++) {
			XYSeries series = seriesCollection.getSeries(seriesIndex);
			Capillary cap = getCapillaryFromSeriesKey((String) series.getKey(), cage);
			if (cap != null) {
				capillaryToSeriesMap.computeIfAbsent(cap, k -> new ArrayList<>()).add(series);
			}
		}
		if (capillaryToSeriesMap.isEmpty()) {
			return null;
		}

		double minDistance = Double.MAX_VALUE;
		Capillary closestCapillary = null;
		for (Map.Entry<Capillary, List<XYSeries>> entry : capillaryToSeriesMap.entrySet()) {
			for (XYSeries series : entry.getValue()) {
				for (int itemIndex = 0; itemIndex < series.getItemCount(); itemIndex++) {
					double x = series.getX(itemIndex).doubleValue();
					double y = series.getY(itemIndex).doubleValue();
					double distance = Math.hypot(x - clickedX, y - clickedY);
					if (distance < minDistance) {
						minDistance = distance;
						closestCapillary = entry.getKey();
					}
				}
			}
		}
		return closestCapillary;
	}

	private double getTimeMinutesFromEvent(ChartMouseEvent e, ChartPanel panel, Plot combined) {
		if (e == null || panel == null || combined == null) {
			return -1;
		}
		if (e.getEntity() instanceof XYItemEntity) {
			return ChartCamFrameNavigation.getTimeMinutesFromXYItem((XYItemEntity) e.getEntity());
		}

		Point screenPoint = e.getTrigger().getPoint();
		Point2D java2DPoint = panel.translateScreenToJava2D(screenPoint);
		ChartRenderingInfo chartInfo = panel.getChartRenderingInfo();
		if (chartInfo == null) {
			return -1;
		}
		PlotRenderingInfo plotInfo = chartInfo.getPlotInfo();
		XYPlot subplot = CombinedXYPlots.findSubplot(combined, plotInfo, java2DPoint);
		if (subplot == null) {
			return -1;
		}
		Rectangle2D dataArea = subplotDataArea(plotInfo, java2DPoint);
		ValueAxis domainAxis = CombinedXYPlots.resolveDomainAxis(subplot, combined);
		Plot domainPlot = subplot.getDomainAxis() != null ? subplot : combined;
		return JFreeChartPlotCompat.domainJava2DToValue(domainAxis, java2DPoint.getX(), dataArea, domainPlot);
	}

	private class CombinedChartMouseListener implements ChartMouseListener {
		@Override
		public void chartMouseClicked(ChartMouseEvent e) {
			Capillary clickedCapillary = getCapillaryFromClickedChart(e);
			if (clickedCapillary == null) {
				return;
			}
			Object source = e.getTrigger().getSource();
			if (!(source instanceof ChartPanel) || e.getChart() == null
					|| !CombinedXYPlots.isCombinedXYPlot(e.getChart().getPlot())) {
				return;
			}
			ChartPanel panel = (ChartPanel) source;
			Plot combined = e.getChart().getPlot();
			double timeMinutes = getTimeMinutesFromEvent(e, panel, combined);
			CapillaryChartRoiFocus.selectClickedCapillary(experiment, clickedCapillary, timeMinutes);
		}

		@Override
		public void chartMouseMoved(ChartMouseEvent e) {
		}
	}
}