package plugins.fmp.multiSPOTS96.tools.chart;

import java.util.logging.Logger;

import org.jfree.data.xy.XYSeriesCollection;

import plugins.fmp.multiSPOTS96.tools.MaxMinDouble;

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

	/** Logger for this class */
	private static final Logger LOGGER = Logger.getLogger(ChartData.class.getName());

	/** Y-axis range information */
	private MaxMinDouble yMaxMin;

	/** X-axis range information */
	private MaxMinDouble xMaxMin;

	/** The XY dataset containing the chart data */
	private XYSeriesCollection xyDataset;

	/**
	 * Creates a new ChartData with null values for all components. Use the setter
	 * methods to populate the data.
	 */
	public ChartData() {
		this(null, null, null);
	}

	/**
	 * Creates a new ChartData with the specified components.
	 * 
	 * @param xMaxMin   the X-axis range information
	 * @param yMaxMin   the Y-axis range information
	 * @param xyDataset the XY dataset containing the chart data
	 */
	public ChartData(MaxMinDouble xMaxMin, MaxMinDouble yMaxMin, XYSeriesCollection xyDataset) {
		this.xMaxMin = xMaxMin;
		this.yMaxMin = yMaxMin;
		this.xyDataset = xyDataset;

//		LOGGER.fine("Created ChartData with " + (xyDataset != null ? xyDataset.getSeriesCount() : 0) + " series");
	}

	/**
	 * Gets the Y-axis range information.
	 * 
	 * @return the Y-axis range, or null if not set
	 */
	public MaxMinDouble getYMaxMin() {
		return yMaxMin;
	}

	/**
	 * Sets the Y-axis range information.
	 * 
	 * @param yMaxMin the Y-axis range to set
	 */
	public void setYMaxMin(MaxMinDouble yMaxMin) {
		this.yMaxMin = yMaxMin;
//		LOGGER.fine("Updated Y-axis range: " + yMaxMin);
	}

	/**
	 * Gets the X-axis range information.
	 * 
	 * @return the X-axis range, or null if not set
	 */
	public MaxMinDouble getXMaxMin() {
		return xMaxMin;
	}

	/**
	 * Sets the X-axis range information.
	 * 
	 * @param xMaxMin the X-axis range to set
	 */
	public void setXMaxMin(MaxMinDouble xMaxMin) {
		this.xMaxMin = xMaxMin;
//		LOGGER.fine("Updated X-axis range: " + xMaxMin);
	}

	/**
	 * Gets the XY dataset containing the chart data.
	 * 
	 * @return the XY dataset, or null if not set
	 */
	public XYSeriesCollection getXYDataset() {
		return xyDataset;
	}

	/**
	 * Sets the XY dataset containing the chart data.
	 * 
	 * @param xyDataset the XY dataset to set
	 */
	public void setXYDataset(XYSeriesCollection xyDataset) {
		this.xyDataset = xyDataset;
//		LOGGER.fine("Updated XY dataset with " + (xyDataset != null ? xyDataset.getSeriesCount() : 0) + " series");
	}

	/**
	 * Checks if this ChartData has all required components set.
	 * 
	 * @return true if all components are set, false otherwise
	 */
	public boolean isComplete() {
		return xMaxMin != null && yMaxMin != null && xyDataset != null;
	}

	/**
	 * Checks if the XY dataset has any data series.
	 * 
	 * @return true if the dataset has at least one series, false otherwise
	 */
	public boolean hasData() {
		return xyDataset != null && xyDataset.getSeriesCount() > 0;
	}

	/**
	 * Gets the number of data series in the dataset.
	 * 
	 * @return the number of series, or 0 if dataset is null
	 */
	public int getSeriesCount() {
		return xyDataset != null ? xyDataset.getSeriesCount() : 0;
	}

	/**
	 * Gets the Y-axis range as a formatted string.
	 * 
	 * @return a string representation of the Y-axis range
	 */
	public String getYRangeString() {
		if (yMaxMin == null) {
			return "Y-axis range: not set";
		}
		return String.format("Y-axis range: [%.2f, %.2f]", yMaxMin.getMin(), yMaxMin.getMax());
	}

	/**
	 * Gets the X-axis range as a formatted string.
	 * 
	 * @return a string representation of the X-axis range
	 */
	public String getXRangeString() {
		if (xMaxMin == null) {
			return "X-axis range: not set";
		}
		return String.format("X-axis range: [%.2f, %.2f]", xMaxMin.getMin(), xMaxMin.getMax());
	}

	/**
	 * Returns a string representation of this ChartData.
	 * 
	 * @return a string describing this ChartData
	 */
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

	/**
	 * Creates a copy of this ChartData. Note that the XYSeriesCollection is not
	 * deeply copied.
	 * 
	 * @return a new ChartData with the same values
	 */
	public ChartData copy() {
		return new ChartData(xMaxMin, yMaxMin, xyDataset);
	}

	/**
	 * Clears all data from this ChartData. Sets all components to null.
	 */
	public void clear() {
		this.xMaxMin = null;
		this.yMaxMin = null;
		this.xyDataset = null;
//		LOGGER.fine("Cleared ChartData");
	}
}
