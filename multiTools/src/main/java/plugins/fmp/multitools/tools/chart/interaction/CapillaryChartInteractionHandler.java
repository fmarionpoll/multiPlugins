package plugins.fmp.multitools.tools.chart.interaction;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.entity.ChartEntity;
import org.jfree.chart.entity.XYItemEntity;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import icy.gui.viewer.Viewer;
import icy.roi.ROI2D;
import icy.sequence.Sequence;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.ViewerFMP;
import plugins.fmp.multitools.tools.chart.ChartCagePair;
import plugins.fmp.multitools.tools.chart.ChartCagePanel;
import plugins.fmp.multitools.tools.chart.ChartInteractionHandler;

/**
 * Capillary-related chart interactions (click-to-select ROI, jump to nearest T,
 * show kymograph) for cage grid charts.
 */
public class CapillaryChartInteractionHandler implements ChartInteractionHandler {

	private static final int LEFT_MOUSE_BUTTON = MouseEvent.BUTTON1;

	private final Experiment experiment;

	public CapillaryChartInteractionHandler(Experiment experiment, @SuppressWarnings("unused") ChartCagePair[][] chart) {
		this.experiment = experiment;
	}

	@Override
	public ChartMouseListener createMouseListener() {
		return new CapillaryChartMouseListener();
	}

	private Capillary getCapillaryFromClickedChart(ChartMouseEvent e) {
		if (e == null) {
			Logger.warn("Chart mouse event is null");
			return null;
		}

		final MouseEvent trigger = e.getTrigger();
		if (trigger.getButton() != LEFT_MOUSE_BUTTON) {
			return null;
		}

		JFreeChart chart = e.getChart();
		if (chart == null) {
			Logger.warn("Chart is null");
			return null;
		}

		Object source = trigger.getSource();
		ChartPanel panel = null;
		Cage cage = null;
		if (source instanceof ChartCagePanel) {
			panel = (ChartCagePanel) source;
			cage = ((ChartCagePanel) panel).getCage();
		} else if (source instanceof ChartPanel) {
			panel = (ChartPanel) source;
			Logger.warn("Event source is ChartPanel but not ChartCagePanel: " + source);
			return null;
		} else {
			Logger.warn("Event source is not a ChartPanel: " + source);
			return null;
		}

		if (cage == null) {
			Logger.warn("Could not get cage from ChartCagePanel");
			return null;
		}

		XYPlot xyPlot = (XYPlot) chart.getPlot();
		ChartEntity chartEntity = e.getEntity();

		if (chartEntity instanceof XYItemEntity) {
			return getCapillaryFromXYItemEntity((XYItemEntity) chartEntity, cage);
		}

		return findClosestCapillaryFromPoint(e, cage, xyPlot, panel);
	}

	private Capillary getCapillaryFromXYItemEntity(XYItemEntity xyItemEntity, Cage cage) {
		if (xyItemEntity == null || cage == null) {
			return null;
		}

		int seriesIndex = xyItemEntity.getSeriesIndex();
		XYDataset xyDataset = xyItemEntity.getDataset();
		if (xyDataset == null) {
			Logger.warn("XY dataset is null");
			return null;
		}

		String seriesKey = (String) xyDataset.getSeriesKey(seriesIndex);
		if (seriesKey == null) {
			Logger.warn("Series key is null");
			return null;
		}
		return getCapillaryFromSeriesKey(seriesKey, cage);
	}

	private Capillary getCapillaryFromSeriesKey(String seriesKey, Cage cage) {
		if (seriesKey == null || cage == null || experiment == null) {
			return null;
		}

		String[] parts = seriesKey.split("_");
		if (parts.length < 2) {
			Logger.warn("Invalid series key format: " + seriesKey);
			return null;
		}

		String sideOrType = parts[1];

		if ("Sum".equals(sideOrType) || "PI".equals(sideOrType)) {
			List<Capillary> capillaries = cage.getCapillaries(experiment.getCapillaries());
			if (capillaries == null || capillaries.isEmpty()) {
				return null;
			}

			for (Capillary cap : capillaries) {
				String capSide = cap.getCapillarySide();
				if ("Sum".equals(sideOrType) && ("L".equals(capSide) || "1".equals(capSide))) {
					return cap;
				} else if ("PI".equals(sideOrType) && ("R".equals(capSide) || "2".equals(capSide))) {
					return cap;
				}
			}
			return capillaries.get(0);
		}

		List<Capillary> capillaries = cage.getCapillaries(experiment.getCapillaries());
		if (capillaries == null) {
			return null;
		}
		for (Capillary cap : capillaries) {
			String capSide = cap.getCapillarySide();
			if (sideOrType.equals(capSide) || (sideOrType.equals("1") && ("L".equals(capSide) || "1".equals(capSide)))
					|| (sideOrType.equals("2") && ("R".equals(capSide) || "2".equals(capSide)))) {
				return cap;
			}
		}

		Logger.warn("Could not find capillary for series key: " + seriesKey);
		return null;
	}

	private Capillary findClosestCapillaryFromPoint(ChartMouseEvent e, Cage cage, XYPlot xyPlot, ChartPanel panel) {
		if (e == null || cage == null || xyPlot == null || panel == null) {
			return null;
		}

		Point screenPoint = e.getTrigger().getPoint();
		Point2D java2DPoint = panel.translateScreenToJava2D(screenPoint);

		Rectangle2D dataArea = panel.getScreenDataArea();
		ValueAxis domainAxis = xyPlot.getDomainAxis();
		ValueAxis rangeAxis = xyPlot.getRangeAxis();

		double clickedX = domainAxis.java2DToValue(java2DPoint.getX(), dataArea, xyPlot.getDomainAxisEdge());
		double clickedY = rangeAxis.java2DToValue(java2DPoint.getY(), dataArea, xyPlot.getRangeAxisEdge());

		XYDataset dataset = xyPlot.getDataset();
		if (!(dataset instanceof XYSeriesCollection)) {
			return null;
		}

		XYSeriesCollection seriesCollection = (XYSeriesCollection) dataset;

		Map<Capillary, List<XYSeries>> capillaryToSeriesMap = new HashMap<>();
		for (int seriesIndex = 0; seriesIndex < seriesCollection.getSeriesCount(); seriesIndex++) {
			XYSeries series = seriesCollection.getSeries(seriesIndex);
			String seriesKey = (String) series.getKey();
			Capillary cap = getCapillaryFromSeriesKey(seriesKey, cage);
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
			Capillary cap = entry.getKey();
			List<XYSeries> seriesList = entry.getValue();

			for (XYSeries series : seriesList) {
				for (int itemIndex = 0; itemIndex < series.getItemCount(); itemIndex++) {
					double x = series.getX(itemIndex).doubleValue();
					double y = series.getY(itemIndex).doubleValue();
					double distance = Math.sqrt(Math.pow(x - clickedX, 2) + Math.pow(y - clickedY, 2));
					if (distance < minDistance) {
						minDistance = distance;
						closestCapillary = cap;
					}
				}
			}
		}

		return closestCapillary;
	}

	private void selectCapillaryAtT(Experiment exp, Capillary capillary, int frameIndex) {
		if (exp == null || capillary == null) {
			Logger.warn("Cannot select capillary: experiment or capillary is null");
			return;
		}

		if (exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null) {
			return;
		}

		Sequence seq = exp.getSeqCamData().getSequence();
		Viewer v = seq.getFirstViewer();
		if (v == null) {
			v = new ViewerFMP(seq, true, true);
		}
		v.toFront();
		if (frameIndex >= 0) {
			v.setPositionT(frameIndex);
		}

		ROI2D roi = capillary.getRoiAtFrameT(frameIndex);
		if (roi != null) {
			seq.setFocusedROI(roi);
			seq.setSelectedROI(roi);
		}
	}

	private void chartSelectKymographForCapillary(Experiment exp, Capillary capillary) {
		if (exp == null || capillary == null) {
			Logger.warn("Cannot select kymograph: experiment or capillary is null");
			return;
		}
		if (exp.getSeqKymos() == null || exp.getSeqKymos().getSequence() == null) {
			return;
		}

		Viewer v = exp.getSeqKymos().getSequence().getFirstViewer();
		if (v == null) {
			v = new ViewerFMP(exp.getSeqKymos().getSequence(), true, true);
		}

		List<String> kymographImagesList = exp.getSeqKymos().getImagesList();
		int kymographIndex = capillary.deriveKymographIndexFromImageList(kymographImagesList);
		if (kymographIndex >= 0 && kymographIndex < exp.getSeqKymos().getSequence().getSizeT()) {
			v.setPositionT(kymographIndex);
			v.toFront();
		}
	}

	private void chartSelectClickedCapillary(Experiment exp, Capillary clickedCapillary, double timeMinutes) {
		if (clickedCapillary == null) {
			Logger.warn("Clicked capillary is null");
			return;
		}

		int frameIndex = getFrameIndexFromTimeMinutes(exp, timeMinutes);
		chartSelectKymographForCapillary(exp, clickedCapillary);

		selectCapillaryAtT(exp, clickedCapillary, frameIndex);
	}

	private int getFrameIndexFromTimeMinutes(Experiment exp, double timeMinutes) {
		if (exp == null || exp.getSeqCamData() == null || timeMinutes < 0) {
			return -1;
		}
		int nTotalFrames = exp.getSeqCamData().getImageLoader().getNTotalFrames();
		int actualImageCount = exp.getSeqCamData().getImageLoader().getImagesCount();
		if (nTotalFrames <= 1 || nTotalFrames != actualImageCount) {
			return -1;
		}
		long[] timeArray = exp.getSeqCamData().getTimeManager().getCamImagesTime_Ms();
		if (timeArray == null || timeArray.length != nTotalFrames) {
			return -1;
		}
		return exp.getSeqCamData().getTimeManager().findNearestIntervalWithBinarySearch((long) (timeMinutes * 60000), 0,
				nTotalFrames);
	}

	private double getTimeMinutesFromEvent(ChartMouseEvent e, ChartPanel panel, XYPlot plot) {
		if (e == null || panel == null || plot == null) {
			return -1;
		}
		Point screenPoint = e.getTrigger().getPoint();
		Point2D java2DPoint = panel.translateScreenToJava2D(screenPoint);
		Rectangle2D dataArea = panel.getScreenDataArea();
		ValueAxis domainAxis = plot.getDomainAxis();
		return domainAxis.java2DToValue(java2DPoint.getX(), dataArea, plot.getDomainAxisEdge());
	}

	private class CapillaryChartMouseListener implements ChartMouseListener {
		@Override
		public void chartMouseClicked(ChartMouseEvent e) {
			Capillary clickedCapillary = getCapillaryFromClickedChart(e);
			if (clickedCapillary == null) {
				return;
			}

			Object source = e.getTrigger().getSource();
			if (!(source instanceof ChartPanel)) {
				return;
			}

			ChartPanel panel = (ChartPanel) source;
			JFreeChart chart = e.getChart();
			if (chart == null) {
				return;
			}
			XYPlot plot = (XYPlot) chart.getPlot();
			double timeMinutes = getTimeMinutesFromEvent(e, panel, plot);
			chartSelectClickedCapillary(experiment, clickedCapillary, timeMinutes);
		}

		@Override
		public void chartMouseMoved(ChartMouseEvent e) {
		}
	}
}

