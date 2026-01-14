package plugins.fmp.multiSPOTS96.tools.chart;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.swing.BoxLayout;
import javax.swing.JPanel;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import icy.gui.frame.IcyFrame;
import icy.gui.util.GuiUtil;
import plugins.fmp.multiSPOTS96.experiment.cages.Cage;
import plugins.fmp.multiSPOTS96.experiment.cages.FlyPositions;
import plugins.fmp.multiSPOTS96.tools.MaxMinDouble;
import plugins.fmp.multiSPOTS96.tools.toExcel.EnumXLSExport;

/**
 * Chart display class for fly position data. This class creates and manages
 * charts that display various fly position measurements over time, including
 * distance, alive status, sleep status, and vertical position.
 * 
 * <p>
 * ChartPositions provides a comprehensive view of fly behavior by creating
 * multiple charts in a horizontal layout, each representing different cages or
 * measurement types.
 * </p>
 * 
 * <p>
 * Usage example:
 * 
 * <pre>
 * ChartPositions chartPositions = new ChartPositions();
 * chartPositions.createPanel("Fly Positions");
 * chartPositions.displayData(cageList, EnumXLSExport.DISTANCE);
 * </pre>
 * 
 * @author MultiSPOTS96
 * @see org.jfree.chart.ChartPanel
 * @see plugins.fmp.multiSPOTS96.experiment.cages.Cage
 * @see plugins.fmp.multiSPOTS96.tools.toExcel.EnumXLSExport
 */
public class ChartFlyPositions extends IcyFrame {

	/** Logger for this class */
	private static final Logger LOGGER = Logger.getLogger(ChartFlyPositions.class.getName());

	/** Default chart width in pixels */
	private static final int DEFAULT_CHART_WIDTH = 100;

	/** Default chart height in pixels */
	private static final int DEFAULT_CHART_HEIGHT = 200;

	/** Default minimum chart width in pixels */
	private static final int MIN_CHART_WIDTH = 50;

	/** Default maximum chart width in pixels */
	private static final int MAX_CHART_WIDTH = 100;

	/** Default minimum chart height in pixels */
	private static final int MIN_CHART_HEIGHT = 100;

	/** Default maximum chart height in pixels */
	private static final int MAX_CHART_HEIGHT = 200;

	/** Default frame width in pixels */
	private static final int DEFAULT_FRAME_WIDTH = 300;

	/** Default frame height in pixels */
	private static final int DEFAULT_FRAME_HEIGHT = 70;

	/** Default Y-axis range for boolean data (alive/sleep) */
	private static final double BOOLEAN_Y_MIN = 0.0;

	/** Default Y-axis range for boolean data (alive/sleep) */
	private static final double BOOLEAN_Y_MAX = 1.2;

	/** Default Y-axis range for alive status */
	private static final double ALIVE_Y_MAX = 1.2;

	/** Default Y-axis range for sleep status */
	private static final double SLEEP_Y_MAX = 1.2;

	/** Default Y-axis range for distance data */
	private static final double DISTANCE_Y_MIN = 0.0;

	/** Default Y-axis range multiplier for position data */
	private static final double POSITION_Y_MULTIPLIER = 1.2;

	/** Value representing alive status */
	private static final double ALIVE_VALUE = 1.0;

	/** Value representing dead status */
	private static final double DEAD_VALUE = 0.0;

	/** Value representing sleep status */
	private static final double SLEEP_VALUE = 1.0;

	/** Value representing awake status */
	private static final double AWAKE_VALUE = 0.0;

	/** Main chart panel containing all charts */
	private JPanel mainChartPanel = null;

	/** List of chart panels for cleanup */
	private ArrayList<ChartPanel> chartsInMainChartPanel = null;

	/** Main chart frame */
	public IcyFrame mainChartFrame = null;

	/** Chart title */
	private String title;

	/** Chart location */
	private Point pt = new Point(0, 0);

	/** Global maximum X value across all charts */
	private double globalXMax = 0;

	/**
	 * Creates the main chart panel and frame.
	 * 
	 * @param cstitle the title for the chart window
	 * @throws IllegalArgumentException if title is null or empty
	 */
	public void createPanel(String cstitle) {
		if (cstitle == null || cstitle.trim().isEmpty()) {
			throw new IllegalArgumentException("Chart title cannot be null or empty");
		}

		title = cstitle;
		mainChartFrame = GuiUtil.generateTitleFrame(title, new JPanel(),
				new Dimension(DEFAULT_FRAME_WIDTH, DEFAULT_FRAME_HEIGHT), true, true, true, true);
		mainChartPanel = new JPanel();
		mainChartPanel.setLayout(new BoxLayout(mainChartPanel, BoxLayout.LINE_AXIS));
		mainChartFrame.add(mainChartPanel);

//		LOGGER.info("Created chart panel with title: " + title);
	}

	/**
	 * Sets the location of the chart frame relative to a rectangle.
	 * 
	 * @param rectv   the reference rectangle
	 * @param deltapt the offset from the rectangle
	 * @throws IllegalArgumentException if rectv or deltapt is null
	 */
	public void setLocationRelativeToPoint(Point originPoint, Point deltapt) {
		if (originPoint == null) {
			throw new IllegalArgumentException("Reference point cannot be null");
		}
		if (deltapt == null) {
			throw new IllegalArgumentException("Delta point cannot be null");
		}

		pt = new Point(originPoint.x + deltapt.x, originPoint.y + deltapt.y);
//		LOGGER.fine("Set chart location to: " + pt);
	}

	/**
	 * Displays position data for a list of cages.
	 * 
	 * @param cageList the list of cages to display data for
	 * @param option   the type of data to display
	 * @throws IllegalArgumentException if cageList is null
	 */
	public void displayData(List<Cage> cageList, EnumXLSExport option) {
		if (cageList == null) {
			throw new IllegalArgumentException("Cage list cannot be null");
		}
		if (option == null) {
			throw new IllegalArgumentException("Export option cannot be null");
		}

//		LOGGER.info("Displaying " + option + " data for " + cageList.size() + " cages");

		List<XYSeriesCollection> xyDataSetList = new ArrayList<XYSeriesCollection>();
		MaxMinDouble yMaxMin = new MaxMinDouble();
		int count = 0;

		for (Cage cage : cageList) {
			if (cage == null) {
				LOGGER.warning("Null cage in list, skipping");
				continue;
			}

			if (cage.flyPositions != null && cage.flyPositions.flyPositionList.size() > 0) {
				ChartData chartData = getDataSet(cage, option);
				XYSeriesCollection xyDataset = chartData.getXYDataset();
				yMaxMin = chartData.getYMaxMin();

				if (count != 0) {
					yMaxMin.getMaxMin(chartData.getYMaxMin());
				}

				xyDataSetList.add(xyDataset);
				count++;
//				LOGGER.fine("Added data for cage ID: " + cage.getProperties().getCageID());
//			} else {
//				LOGGER.fine("Skipping cage ID " + cage.getProperties().getCageID() + " - no position data");
			}
		}

		cleanChartsPanel(chartsInMainChartPanel);
		int width = DEFAULT_CHART_WIDTH;
		boolean displayLabels = false;

		for (XYSeriesCollection xyDataset : xyDataSetList) {
			if (xyDataset == null || xyDataset.getSeriesCount() == 0) {
				LOGGER.warning("Skipping null or empty dataset");
				continue;
			}

			JFreeChart xyChart = ChartFactory.createXYLineChart(null, // title - the chart title (null permitted).
					null, // title - the chart title (null permitted).
					null, // yAxisLabel - a label for the Y-axis (null permitted)
					xyDataset, // dataset - the dataset for the chart (null permitted)
					PlotOrientation.VERTICAL, // orientation - the plot orientation (horizontal or vertical) (null NOT
												// permitted)
					true, // legend - a flag specifying whether or not a legend is required
					true, // tooltips - configure chart to generate tool tips?
					true); // urls - configure chart to generate URLs?
			xyChart.setAntiAlias(true);
			xyChart.setTextAntiAlias(true);

			ValueAxis yAxis = xyChart.getXYPlot().getRangeAxis(0);
			if (yMaxMin != null) {
				yAxis.setRange(yMaxMin.getMin(), yMaxMin.getMax());
			}
			yAxis.setTickLabelsVisible(displayLabels);

			ValueAxis xAxis = xyChart.getXYPlot().getDomainAxis(0);
			xAxis.setRange(0, globalXMax);

			ChartPanel xyChartPanel = new ChartPanel(xyChart, width, DEFAULT_CHART_HEIGHT, MIN_CHART_WIDTH,
					MIN_CHART_HEIGHT, MAX_CHART_WIDTH, MAX_CHART_HEIGHT, false, false, true, true, true, true);
			mainChartPanel.add(xyChartPanel);
			width = DEFAULT_CHART_WIDTH;
			displayLabels = false;
		}

		mainChartFrame.pack();
		mainChartFrame.setLocation(pt);
		mainChartFrame.addToDesktopPane();
		mainChartFrame.setVisible(true);

//		LOGGER.info("Displayed " + xyDataSetList.size() + " charts");
	}

	/**
	 * Adds data points to an XY series based on the export option.
	 * 
	 * @param cage     the cage containing the data
	 * @param option   the type of data to extract
	 * @param seriesXY the series to add points to
	 * @return MaxMinDouble containing the Y-axis range
	 */
	private MaxMinDouble addPointsToXYSeries(Cage cage, EnumXLSExport option, XYSeries seriesXY) {
		if (cage == null || seriesXY == null) {
			LOGGER.warning("Cannot add points: cage or series is null");
			return new MaxMinDouble(0.0, 1.0);
		}

		FlyPositions results = cage.flyPositions;
		if (results == null || results.flyPositionList == null) {
			LOGGER.warning("No fly positions data for cage ID: " + cage.getProperties().getCageID());
			return new MaxMinDouble(0.0, 1.0);
		}

		int itmax = results.flyPositionList.size();
		MaxMinDouble yMaxMin = null;

		if (itmax > 0) {
			switch (option) {
			case DISTANCE:
				yMaxMin = processDistanceData(results, seriesXY, itmax, cage);
				break;

			case ISALIVE:
				yMaxMin = processAliveData(results, seriesXY, itmax);
				break;

			case SLEEP:
				yMaxMin = processSleepData(results, seriesXY, itmax);
				break;

			default:
				yMaxMin = processPositionData(results, seriesXY, itmax, cage);
				break;
			}
		} else {
			LOGGER.warning("No data points for cage ID: " + cage.getProperties().getCageID());
			yMaxMin = new MaxMinDouble(0.0, 1.0);
		}

		return yMaxMin;
	}

	/**
	 * Processes distance data for a cage.
	 * 
	 * @param results  the fly positions data
	 * @param seriesXY the series to add points to
	 * @param itmax    the number of data points
	 * @param cage     the cage data
	 * @return MaxMinDouble containing the Y-axis range
	 */
	private MaxMinDouble processDistanceData(FlyPositions results, XYSeries seriesXY, int itmax, Cage cage) {
		double previousY = results.flyPositionList.get(0).rectPosition.getY()
				+ results.flyPositionList.get(0).rectPosition.getHeight() / 2;

		for (int it = 0; it < itmax; it++) {
			double currentY = results.flyPositionList.get(it).rectPosition.getY()
					+ results.flyPositionList.get(it).rectPosition.getHeight() / 2;
			double ypos = currentY - previousY;
			addxyPos(seriesXY, results, it, ypos);
			previousY = currentY;
		}

		Rectangle rect = cage.getRoi().getBounds();
		double length_diagonal = Math.sqrt((rect.height * rect.height) + (rect.width * rect.width));
		return new MaxMinDouble(DISTANCE_Y_MIN, length_diagonal);
	}

	/**
	 * Processes alive status data for a cage.
	 * 
	 * @param results  the fly positions data
	 * @param seriesXY the series to add points to
	 * @param itmax    the number of data points
	 * @return MaxMinDouble containing the Y-axis range
	 */
	private MaxMinDouble processAliveData(FlyPositions results, XYSeries seriesXY, int itmax) {
		for (int it = 0; it < itmax; it++) {
			boolean alive = results.flyPositionList.get(it).bAlive;
			double ypos = alive ? ALIVE_VALUE : DEAD_VALUE;
			addxyPos(seriesXY, results, it, ypos);
		}
		return new MaxMinDouble(BOOLEAN_Y_MIN, ALIVE_Y_MAX);
	}

	/**
	 * Processes sleep status data for a cage.
	 * 
	 * @param results  the fly positions data
	 * @param seriesXY the series to add points to
	 * @param itmax    the number of data points
	 * @return MaxMinDouble containing the Y-axis range
	 */
	private MaxMinDouble processSleepData(FlyPositions results, XYSeries seriesXY, int itmax) {
		for (int it = 0; it < itmax; it++) {
			boolean sleep = results.flyPositionList.get(it).bSleep;
			double ypos = sleep ? SLEEP_VALUE : AWAKE_VALUE;
			addxyPos(seriesXY, results, it, ypos);
		}
		return new MaxMinDouble(BOOLEAN_Y_MIN, SLEEP_Y_MAX);
	}

	/**
	 * Processes position data for a cage.
	 * 
	 * @param results  the fly positions data
	 * @param seriesXY the series to add points to
	 * @param itmax    the number of data points
	 * @param cage     the cage data
	 * @return MaxMinDouble containing the Y-axis range
	 */
	private MaxMinDouble processPositionData(FlyPositions results, XYSeries seriesXY, int itmax, Cage cage) {
		Rectangle rect1 = cage.getRoi().getBounds();
		double yOrigin = rect1.getY() + rect1.getHeight();

		for (int it = 0; it < itmax; it++) {
			Rectangle2D itRect = results.flyPositionList.get(it).rectPosition;
			double ypos = yOrigin - itRect.getY();
			addxyPos(seriesXY, results, it, ypos);
		}

		return new MaxMinDouble(0.0, rect1.height * POSITION_Y_MULTIPLIER);
	}

	/**
	 * Adds a single data point to the series.
	 * 
	 * @param seriesXY    the series to add the point to
	 * @param positionxyt the fly positions data
	 * @param it          the index of the data point
	 * @param ypos        the Y value to add
	 */
	private void addxyPos(XYSeries seriesXY, FlyPositions positionxyt, int it, Double ypos) {
		if (seriesXY == null || positionxyt == null || positionxyt.flyPositionList == null) {
			LOGGER.warning("Cannot add position: series or position data is null");
			return;
		}

		if (it < 0 || it >= positionxyt.flyPositionList.size()) {
			LOGGER.warning("Invalid index " + it + " for position list of size " + positionxyt.flyPositionList.size());
			return;
		}

		double indexT = positionxyt.flyPositionList.get(it).flyIndexT;
		seriesXY.add(indexT, ypos);

		if (globalXMax < indexT) {
			globalXMax = indexT;
		}
	}

	/**
	 * Gets the dataset for a specific cage and export option.
	 * 
	 * @param cage   the cage to get data for
	 * @param option the export option
	 * @return ChartData containing the dataset and axis information
	 */
	private ChartData getDataSet(Cage cage, EnumXLSExport option) {
		if (cage == null) {
			LOGGER.warning("Cannot get dataset: cage is null");
			return new ChartData();
		}

		XYSeriesCollection xyDataset = new XYSeriesCollection();
		String name = cage.getRoi().getName();
		XYSeries seriesXY = new XYSeries(name);
		seriesXY.setDescription(name);

		MaxMinDouble yMaxMin = addPointsToXYSeries(cage, option, seriesXY);
		xyDataset.addSeries(seriesXY);

		return new ChartData(new MaxMinDouble(globalXMax, 0), yMaxMin, xyDataset);
	}

	/**
	 * Cleans up the charts panel by removing all chart panels.
	 * 
	 * @param chartsPanel the list of chart panels to clean
	 */
	private void cleanChartsPanel(ArrayList<ChartPanel> chartsPanel) {
		if (chartsPanel != null && chartsPanel.size() > 0) {
			chartsPanel.clear();
//			LOGGER.fine("Cleaned up " + chartsPanel.size() + " chart panels");
		}
	}

	/**
	 * Gets the main chart panel.
	 * 
	 * @return the main chart panel
	 */
	public JPanel getMainChartPanel() {
		return mainChartPanel;
	}

	/**
	 * Gets the main chart frame.
	 * 
	 * @return the main chart frame
	 */
	public IcyFrame getMainChartFrame() {
		return mainChartFrame;
	}

	/**
	 * Gets the global maximum X value.
	 * 
	 * @return the global X maximum
	 */
	public double getGlobalXMax() {
		return globalXMax;
	}

	/**
	 * Sets the global maximum X value.
	 * 
	 * @param globalXMax the new global X maximum
	 */
	public void setGlobalXMax(double globalXMax) {
		this.globalXMax = globalXMax;
	}
}
