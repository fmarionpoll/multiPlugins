package plugins.fmp.multitools.tools.chart.interaction;

import java.awt.geom.Point2D;
import java.util.List;

import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.CombinedRangeXYPlot;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.XYPlot;

/**
 * Helpers for {@link CombinedDomainXYPlot} and {@link CombinedRangeXYPlot}
 * (shared domain vs shared range).
 */
public final class CombinedXYPlots {

	private CombinedXYPlots() {
	}

	public static boolean isCombinedXYPlot(Plot plot) {
		return plot instanceof CombinedDomainXYPlot || plot instanceof CombinedRangeXYPlot;
	}

	public static XYPlot findSubplot(Plot plot, PlotRenderingInfo info, Point2D pt) {
		if (plot instanceof CombinedDomainXYPlot) {
			return ((CombinedDomainXYPlot) plot).findSubplot(info, pt);
		}
		if (plot instanceof CombinedRangeXYPlot) {
			return ((CombinedRangeXYPlot) plot).findSubplot(info, pt);
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public static int indexOfSubplot(Plot plot, XYPlot subplot) {
		List<XYPlot> subplots = null;
		if (plot instanceof CombinedDomainXYPlot) {
			subplots = ((CombinedDomainXYPlot) plot).getSubplots();
		} else if (plot instanceof CombinedRangeXYPlot) {
			subplots = ((CombinedRangeXYPlot) plot).getSubplots();
		}
		if (subplots == null) {
			return -1;
		}
		return subplots.indexOf(subplot);
	}

	public static ValueAxis resolveDomainAxis(XYPlot subplot, Plot combined) {
		if (subplot != null) {
			ValueAxis axis = subplot.getDomainAxis();
			if (axis != null) {
				return axis;
			}
		}
		if (combined instanceof CombinedDomainXYPlot) {
			return ((CombinedDomainXYPlot) combined).getDomainAxis();
		}
		if (combined instanceof XYPlot) {
			return ((XYPlot) combined).getDomainAxis();
		}
		return null;
	}

	public static ValueAxis resolveRangeAxis(XYPlot subplot, Plot combined) {
		if (subplot != null) {
			ValueAxis axis = subplot.getRangeAxis();
			if (axis != null) {
				return axis;
			}
		}
		if (combined instanceof CombinedRangeXYPlot) {
			return ((CombinedRangeXYPlot) combined).getRangeAxis();
		}
		if (combined instanceof XYPlot) {
			return ((XYPlot) combined).getRangeAxis();
		}
		return null;
	}
}