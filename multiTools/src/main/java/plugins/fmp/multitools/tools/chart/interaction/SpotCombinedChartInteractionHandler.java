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
import java.util.function.Consumer;

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
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.chart.JFreeChartPlotCompat;
import plugins.fmp.multitools.tools.chart.builders.SpotChartSeriesKeys;

/**
 * Spot click interactions for the combined chart view (one subplot per cage),
 * supporting {@link org.jfree.chart.plot.CombinedDomainXYPlot} and
 * {@link org.jfree.chart.plot.CombinedRangeXYPlot}. Used by multiSPOTS kymo charts.
 */
public class SpotCombinedChartInteractionHandler {

	private static final int LEFT_MOUSE_BUTTON = MouseEvent.BUTTON1;

	private final Experiment experiment;
	/** Cages in the same order as subplots added to the combined plot. */
	private final List<Cage> subplotCages;
	private final Consumer<Spot> onSpotSelectedFromChart;

	public SpotCombinedChartInteractionHandler(Experiment experiment, List<Cage> subplotCages,
			Consumer<Spot> onSpotSelectedFromChart) {
		this.experiment = experiment;
		if (subplotCages != null) {
			this.subplotCages = Collections.unmodifiableList(new ArrayList<Cage>(subplotCages));
		} else {
			this.subplotCages = Collections.emptyList();
		}
		this.onSpotSelectedFromChart = onSpotSelectedFromChart;
	}

	public ChartMouseListener createMouseListener() {
		return new CombinedChartMouseListener();
	}

	private Spot getSpotFromClickedChart(ChartMouseEvent e) {
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
			return getSpotFromXYItemEntity((XYItemEntity) entity, cage);
		}

		Rectangle2D dataArea = subplotDataArea(plotInfo, java2DPoint);
		Spot closest = findClosestSpotFromPoint(java2DPoint, cage, subplot, combined, dataArea);
		if (closest != null) {
			return closest;
		}
		return firstSpotOfCage(cage);
	}

	private Spot firstSpotOfCage(Cage cage) {
		if (cage == null || experiment == null || experiment.getSpots() == null) {
			return null;
		}
		List<Spot> spots = cage.getSpotList(experiment.getSpots());
		if (spots == null) {
			return null;
		}
		for (Spot s : spots) {
			if (s != null) {
				s.setSpotCamDataT(0);
				return s;
			}
		}
		return null;
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

	private Spot getSpotFromXYItemEntity(XYItemEntity xyItemEntity, Cage cage) {
		if (xyItemEntity == null || cage == null) {
			return null;
		}
		XYDataset xyDataset = xyItemEntity.getDataset();
		if (xyDataset == null) {
			return null;
		}
		String seriesKey = (String) xyDataset.getSeriesKey(xyItemEntity.getSeriesIndex());
		return getSpotFromSeriesKey(seriesKey, cage);
	}

	private Spot getSpotFromSeriesKey(String seriesKey, Cage cage) {
		if (seriesKey == null || cage == null || experiment == null) {
			return null;
		}
		if (SpotChartSeriesKeys.isAggregateSeriesKey(seriesKey)
				|| SpotChartSeriesKeys.isMedianRefSeriesKey(seriesKey)) {
			return null;
		}
		Spot spot = SpotChartSeriesKeys.resolveSpot(experiment, cage, seriesKey);
		if (spot == null) {
			Logger.warn("Could not find spot for series key: " + seriesKey);
		}
		return spot;
	}

	private Spot findClosestSpotFromPoint(Point2D java2DPoint, Cage cage, XYPlot subplot, Plot combined,
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

		Map<Spot, List<XYSeries>> spotToSeriesMap = new HashMap<>();
		for (int seriesIndex = 0; seriesIndex < seriesCollection.getSeriesCount(); seriesIndex++) {
			XYSeries series = seriesCollection.getSeries(seriesIndex);
			Spot spot = getSpotFromSeriesKey((String) series.getKey(), cage);
			if (spot != null) {
				List<XYSeries> list = spotToSeriesMap.get(spot);
				if (list == null) {
					list = new ArrayList<>();
					spotToSeriesMap.put(spot, list);
				}
				list.add(series);
			}
		}
		if (spotToSeriesMap.isEmpty()) {
			return null;
		}

		double minDistance = Double.MAX_VALUE;
		Spot closestSpot = null;
		for (Map.Entry<Spot, List<XYSeries>> entry : spotToSeriesMap.entrySet()) {
			for (XYSeries series : entry.getValue()) {
				for (int itemIndex = 0; itemIndex < series.getItemCount(); itemIndex++) {
					double x = series.getX(itemIndex).doubleValue();
					double y = series.getY(itemIndex).doubleValue();
					double distance = Math.hypot(x - clickedX, y - clickedY);
					if (distance < minDistance) {
						minDistance = distance;
						closestSpot = entry.getKey();
					}
				}
			}
		}
		return closestSpot;
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

	private static void applyExclusiveCageRoiSelection(Experiment exp, Cage cageToSelect) {
		if (exp == null || exp.getCages() == null || cageToSelect == null) {
			return;
		}
		for (Cage cage : exp.getCages().cagesList) {
			if (cage == null || cage.getRoi() == null) {
				continue;
			}
			cage.getRoi().setSelected(cage == cageToSelect);
		}
	}

	private class CombinedChartMouseListener implements ChartMouseListener {
		@Override
		public void chartMouseClicked(ChartMouseEvent e) {
			Spot clickedSpot = getSpotFromClickedChart(e);
			if (clickedSpot == null) {
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
			int frameIndex = ChartCamFrameNavigation.getFrameIndexFromTimeMinutes(experiment, timeMinutes);
			if (frameIndex >= 0) {
				clickedSpot.setSpotCamDataT(frameIndex);
			} else if (!(e.getEntity() instanceof XYItemEntity)) {
				clickedSpot.setSpotCamDataT(0);
			}

			Cage cageForSpot = null;
			Point2D java2DPoint = panel.translateScreenToJava2D(e.getTrigger().getPoint());
			ChartRenderingInfo chartInfo = panel.getChartRenderingInfo();
			if (chartInfo != null) {
				PlotRenderingInfo plotInfo = chartInfo.getPlotInfo();
				XYPlot subplot = CombinedXYPlots.findSubplot(combined, plotInfo, java2DPoint);
				int subplotIndex = CombinedXYPlots.indexOfSubplot(combined, subplot);
				if (subplotIndex >= 0 && subplotIndex < subplotCages.size()) {
					cageForSpot = subplotCages.get(subplotIndex);
				}
			}
			if (cageForSpot != null && !(e.getEntity() instanceof XYItemEntity)) {
				applyExclusiveCageRoiSelection(experiment, cageForSpot);
			}

			if (experiment.getSeqCamData() != null) {
				SpotChartRoiFocus.moveViewerToSpotTAndSelectRoi(experiment.getSeqCamData(), clickedSpot);
			}
			if (onSpotSelectedFromChart != null) {
				onSpotSelectedFromChart.accept(clickedSpot);
			}
		}

		@Override
		public void chartMouseMoved(ChartMouseEvent e) {
		}
	}
}