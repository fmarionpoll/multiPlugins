package plugins.fmp.multitools.fmp_tools.chart;

import org.jfree.data.xy.XYSeriesCollection;

import plugins.fmp.multitools.fmp_tools.MaxMinDouble;

/**
 * Container class for chart data including axis ranges and dataset. This class
 * encapsulates the data needed to create and configure charts, including the XY
 * dataset and the min/max values for both X and Y axes.
 * 
 * <p>
 * ChartData is used to store the complete information needed to render a chart,
 * including the actual data points and the axis scaling information. This makes
 * it easier to pass chart configuration between different chart creation and
 * rendering components.
 * </p>
 * 
 * <p>
 * Usage example:
 * 
 * <pre>
 * XYSeriesCollection dataset = new XYSeriesCollection();
 * MaxMinDouble xRange = new MaxMinDouble(0.0, 100.0);
 * MaxMinDouble yRange = new MaxMinDouble(0.0, 50.0);
 * 
 * ChartData chartData = new ChartData(xRange, yRange, dataset);
 * 
 * // Access the components
 * XYSeriesCollection data = chartData.getXYDataset();
 * MaxMinDouble xAxis = chartData.getXMaxMin();
 * MaxMinDouble yAxis = chartData.getYMaxMin();
 * </pre>
 * 
 * @author MultiSPOTS96
 * @see org.jfree.data.xy.XYSeriesCollection
 * @see plugins.fmp.multiSPOTS96.tools.MaxMinDouble
 */
public class ChartData {

	private MaxMinDouble yMaxMin;
	private MaxMinDouble xMaxMin;
	private XYSeriesCollection xyDataset;

	public ChartData() {
		this(null, null, null);
	}

	public ChartData(MaxMinDouble xMaxMin, MaxMinDouble yMaxMin, XYSeriesCollection xyDataset) {
		this.xMaxMin = xMaxMin;
		this.yMaxMin = yMaxMin;
		this.xyDataset = xyDataset;
	}

	public MaxMinDouble getYMaxMin() {
		return yMaxMin;
	}

	public void setYMaxMin(MaxMinDouble yMaxMin) {
		this.yMaxMin = yMaxMin;
	}

	public MaxMinDouble getXMaxMin() {
		return xMaxMin;
	}

	public void setXMaxMin(MaxMinDouble xMaxMin) {
		this.xMaxMin = xMaxMin;
	}

	public XYSeriesCollection getXYDataset() {
		return xyDataset;
	}

	public void setXYDataset(XYSeriesCollection xyDataset) {
		this.xyDataset = xyDataset;
	}

	public boolean isComplete() {
		return xMaxMin != null && yMaxMin != null && xyDataset != null;
	}

	public boolean hasData() {
		return xyDataset != null && xyDataset.getSeriesCount() > 0;
	}

	public int getSeriesCount() {
		return xyDataset != null ? xyDataset.getSeriesCount() : 0;
	}

	public String getYRangeString() {
		if (yMaxMin == null) {
			return "Y-axis range: not set";
		}
		return String.format("Y-axis range: [%.2f, %.2f]", yMaxMin.getMin(), yMaxMin.getMax());
	}

	public String getXRangeString() {
		if (xMaxMin == null) {
			return "X-axis range: not set";
		}
		return String.format("X-axis range: [%.2f, %.2f]", xMaxMin.getMin(), xMaxMin.getMax());
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("ChartData[");
		sb.append("seriesCount=").append(getSeriesCount());
		if (xMaxMin != null) {
			sb.append(", xRange=[").append(xMaxMin.getMin()).append(", ").append(xMaxMin.getMax()).append("]");
		}
		if (yMaxMin != null) {
			sb.append(", yRange=[").append(yMaxMin.getMin()).append(", ").append(yMaxMin.getMax()).append("]");
		}
		sb.append("]");
		return sb.toString();
	}

	public ChartData copy() {
		return new ChartData(xMaxMin, yMaxMin, xyDataset);
	}

	public void clear() {
		this.xMaxMin = null;
		this.yMaxMin = null;
		this.xyDataset = null;
	}
}
