package plugins.fmp.multitools.tools.chart.interaction;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.entity.XYItemEntity;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYDataset;

import icy.sequence.Sequence;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.chart.JFreeChartPlotCompat;

/**
 * Maps chart domain values (minutes) to {@code seqCamData} frame indices.
 */
public final class ChartCamFrameNavigation {

	private ChartCamFrameNavigation() {
	}

	public static double getTimeMinutesFromXYItem(XYItemEntity item) {
		if (item == null) {
			return -1;
		}
		XYDataset dataset = item.getDataset();
		if (dataset == null) {
			return -1;
		}
		return dataset.getXValue(item.getSeriesIndex(), item.getItem());
	}

	public static double getTimeMinutesFromEvent(ChartMouseEvent e, ChartPanel panel, XYPlot plot) {
		if (e == null || panel == null || plot == null) {
			return -1;
		}
		Point screenPoint = e.getTrigger().getPoint();
		Point2D java2DPoint = panel.translateScreenToJava2D(screenPoint);
		Rectangle2D dataArea = panel.getScreenDataArea();
		ValueAxis domainAxis = plot.getDomainAxis();
		return JFreeChartPlotCompat.domainJava2DToValue(domainAxis, java2DPoint.getX(), dataArea, plot);
	}

	public static int getFrameIndexFromTimeMinutes(Experiment exp, double timeMinutes) {
		if (exp == null || exp.getSeqCamData() == null || timeMinutes < 0) {
			return -1;
		}
		int nTotalFrames = exp.getSeqCamData().getImageLoader().getNTotalFrames();
		if (nTotalFrames <= 0) {
			Sequence seq = exp.getSeqCamData().getSequence();
			if (seq != null) {
				nTotalFrames = seq.getSizeT();
			}
		}
		if (nTotalFrames <= 0) {
			return -1;
		}

		long[] timeArray = exp.getSeqCamData().getTimeManager().getCamImagesTime_Ms();
		if (timeArray != null && timeArray.length == nTotalFrames) {
			return exp.getSeqCamData().getTimeManager().findNearestIntervalWithBinarySearch(
					(long) (timeMinutes * 60000), 0, nTotalFrames - 1);
		}

		long binMs = exp.getSeqCamData().getTimeManager().getBinDurationMs();
		if (binMs > 0) {
			long tMs = (long) (timeMinutes * 60000.0);
			int frame = (int) Math.round((double) tMs / (double) binMs);
			if (frame < 0) {
				frame = 0;
			}
			if (frame >= nTotalFrames) {
				frame = nTotalFrames - 1;
			}
			return frame;
		}

		return -1;
	}
}
