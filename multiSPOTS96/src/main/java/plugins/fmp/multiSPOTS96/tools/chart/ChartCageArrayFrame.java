package plugins.fmp.multiSPOTS96.tools.chart;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.logging.Logger;

import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.entity.ChartEntity;
import org.jfree.chart.entity.XYItemEntity;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.Range;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeriesCollection;

import icy.gui.frame.IcyFrame;
import icy.gui.util.GuiUtil;
import icy.gui.viewer.Viewer;
import icy.roi.ROI2D;
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cages.Cage;
import plugins.fmp.multitools.experiment.spots.Spot;
import plugins.fmp.multitools.tools.toExcel.XLSExportOptions;

/**
 * Chart display class for spot data visualization. This class creates and
 * manages a grid of charts displaying spot measurements for different cages in
 * an experiment. It provides interactive functionality for clicking on chart
 * elements to select corresponding spots and navigate to relevant data views.
 * 
 * <p>
 * ChartSpots creates a grid layout of charts where each chart represents a cage
 * and displays spot measurement data over time. Users can click on chart
 * elements to select spots and navigate to the corresponding data in the main
 * application.
 * </p>
 * 
 * <p>
 * Usage example:
 * 
 * <pre>
 * ChartSpots chartSpots = new ChartSpots();
 * chartSpots.createPanel("Spot Charts", experiment, exportOptions, parent);
 * chartSpots.displayData(experiment, exportOptions);
 * </pre>
 * 
 * @author MultiSPOTS96
 * @see org.jfree.chart.ChartPanel
 * @see plugins.fmp.multitools.experiment.Experiment
 * @see plugins.fmp.multitools.experiment.cages.Cage
 * @see plugins.fmp.multitools.experiment.spots.Spot
 */
public class ChartCageArrayFrame extends IcyFrame {

	/** Logger for this class */
	private static final Logger LOGGER = Logger.getLogger(ChartCageArrayFrame.class.getName());

	/** Default chart width in pixels */
	private static final int DEFAULT_CHART_WIDTH = 200;

	/** Default chart height in pixels */
	private static final int DEFAULT_CHART_HEIGHT = 100;

	/** Default minimum chart width in pixels */
	private static final int MIN_CHART_WIDTH = 50;

	/** Default maximum chart width in pixels */
	private static final int MAX_CHART_WIDTH = 1200;

	/** Default minimum chart height in pixels */
	private static final int MIN_CHART_HEIGHT = 25;

	/** Default maximum chart height in pixels */
	private static final int MAX_CHART_HEIGHT = 600;

	/** Default frame width in pixels */
	private static final int DEFAULT_FRAME_WIDTH = 300;

	/** Default frame height in pixels */
	private static final int DEFAULT_FRAME_HEIGHT = 70;

	/** Default Y-axis range for relative data */
	private static final double RELATIVE_Y_MIN = -0.2;

	/** Default Y-axis range for relative data */
	private static final double RELATIVE_Y_MAX = 1.2;

	/** Chart ID delimiter */
	private static final String CHART_ID_DELIMITER = ":";

	/** Maximum description length for spot identification */
	private static final int MAX_DESCRIPTION_LENGTH = 16;

	/** Mouse button for left click */
	private static final int LEFT_MOUSE_BUTTON = MouseEvent.BUTTON1;

	/** Main chart panel containing all charts */
	private JPanel mainChartPanel = null;

	/** Main chart frame */
	private IcyFrame mainChartFrame = null;

	/** Y-axis range for charts */
	private Range yRange = null;

	/** X-axis range for charts */
	private Range xRange = null;

	/** Chart location */
	private Point graphLocation = new Point(0, 0);

	/** Number of panels along X axis */
	private int nPanelsAlongX = 1;

	/** Number of panels along Y axis */
	private int nPanelsAlongY = 1;

	/** Array of chart panel pairs */
	public ChartCagePair[][] chartPanelArray = null;

	/** Current experiment */
	private Experiment experiment = null;

	/** Parent MultiSPOTS96 instance */
	private MultiSPOTS96 parent = null;

	/**
	 * Creates the main chart panel and frame.
	 * 
	 * @param title            the title for the chart window
	 * @param exp              the experiment containing the data
	 * @param xlsExportOptions the export options for data processing
	 * @param parent0          the parent MultiSPOTS96 instance
	 * @throws IllegalArgumentException if any required parameter is null
	 */
	public void createMainChartPanel(String title, Experiment exp, XLSExportOptions xlsExportOptions,
			MultiSPOTS96 parent0) {
		if (exp == null) {
			throw new IllegalArgumentException("Experiment cannot be null");
		}
		if (xlsExportOptions == null) {
			throw new IllegalArgumentException("Export options cannot be null");
		}
		if (title == null || title.trim().isEmpty()) {
			throw new IllegalArgumentException("Title cannot be null or empty");
		}

		this.parent = parent0;
		this.experiment = exp;

		mainChartPanel = new JPanel();
		boolean flag = (xlsExportOptions.cageIndexFirst == xlsExportOptions.cageIndexLast);
		nPanelsAlongX = flag ? 1 : exp.getCages().nCagesAlongX;
		nPanelsAlongY = flag ? 1 : exp.getCages().nCagesAlongY;

		mainChartPanel.setLayout(new GridLayout(nPanelsAlongY, nPanelsAlongX));
		mainChartFrame = GuiUtil.generateTitleFrame(title, new JPanel(),
				new Dimension(DEFAULT_FRAME_WIDTH, DEFAULT_FRAME_HEIGHT), true, true, true, true);
		JScrollPane scrollPane = new JScrollPane(mainChartPanel);
		mainChartFrame.add(scrollPane);

		chartPanelArray = new ChartCagePair[nPanelsAlongY][nPanelsAlongX];

//		LOGGER.info("Created chart panel with " + nPanelsAlongY + "x" + nPanelsAlongX + " grid");
	}

	/**
	 * Sets up the Y-axis for a chart.
	 * 
	 * @param title            the axis title
	 * @param row              the row index
	 * @param col              the column index
	 * @param xlsExportOptions the export options
	 * @return configured NumberAxis
	 */
	private NumberAxis setYaxis(String label, int row, int col, XLSExportOptions xlsExportOptions) {
		NumberAxis yAxis = new NumberAxis();
		row = row * experiment.getCages().nRowsPerCage;
		col = col * experiment.getCages().nColumnsPerCage;
		String yLegend = label + " " + String.valueOf((char) (row + 'A')) + Integer.toString(col);
		yAxis.setLabel(yLegend);

		if (xlsExportOptions.relativeToT0 || xlsExportOptions.relativeToMedianT0) {
			yAxis.setAutoRange(false);
			yAxis.setRange(RELATIVE_Y_MIN, RELATIVE_Y_MAX);
		} else {
			yAxis.setAutoRange(true);
			yAxis.setAutoRangeIncludesZero(false);
		}

		return yAxis;
	}

	/**
	 * Sets up the X-axis for a chart.
	 * 
	 * @param title            the axis title
	 * @param xlsExportOptions the export options
	 * @return configured NumberAxis
	 */
	private NumberAxis setXaxis(String label, XLSExportOptions xlsExportOptions) {
		NumberAxis xAxis = new NumberAxis();
		xAxis.setLabel(label);
		xAxis.setAutoRange(true);
		xAxis.setAutoRangeIncludesZero(false);
		return xAxis;
	}

	/**
	 * Displays spot data for the experiment.
	 * 
	 * @param exp              the experiment containing the data
	 * @param xlsExportOptions the export options for data processing
	 * @throws IllegalArgumentException if exp or xlsExportOptions is null
	 */
	public void displayData(Experiment exp, XLSExportOptions xlsExportOptions) {
		if (exp == null) {
			throw new IllegalArgumentException("Experiment cannot be null");
		}
		if (xlsExportOptions == null) {
			throw new IllegalArgumentException("Export options cannot be null");
		}

		this.experiment = exp;
		createChartPanelArray(xlsExportOptions);
		arrangePanelsInDisplay(xlsExportOptions);
		displayChartFrame();

//		LOGGER.info("Displayed spot charts for experiment");
	}

	/**
	 * Creates chart panels for all cages in the experiment.
	 * 
	 * @param xlsExportOptions the export options
	 */
	private void createChartPanelArray(XLSExportOptions xlsExportOptions) {
		int indexCage = 0;
		for (int row = 0; row < experiment.getCages().nCagesAlongY; row++) {
			for (int col = 0; col < experiment.getCages().nCagesAlongX; col++, indexCage++) {
				if (indexCage < xlsExportOptions.cageIndexFirst || indexCage > xlsExportOptions.cageIndexLast)
					continue;

				Cage cage = experiment.getCages().getCageFromRowColCoordinates(row, col);
				if (cage == null) {
					LOGGER.warning("No cage found at row " + row + ", col " + col);
					continue;
				}

				ChartPanel chartPanel = createChartPanelForCage(cage, row, col, xlsExportOptions);
				chartPanelArray[row][col] = new ChartCagePair(chartPanel, cage);
			}
		}

//		LOGGER.info("Created " + createdCharts + " in " + indexCage + " chart panels");
	}

	/**
	 * Creates a chart panel for a specific cage.
	 * 
	 * @param chartCage        the chart builder
	 * @param cage             the cage to create chart for
	 * @param xlsResultsArray  the primary data array
	 * @param xlsResultsArray2 the secondary data array
	 * @param row              the row index
	 * @param col              the column index
	 * @param xlsExportOptions the export options
	 * @return configured ChartPanel
	 */
	private ChartCagePanel createChartPanelForCage(Cage cage, int row, int col, XLSExportOptions xlsExportOptions) {

		if (cage.spotsArray.getSpotsCount() < 1) {
//			LOGGER.fine("Skipping cage " + cage.getProperties().getCageID() + " - no spots");
			ChartCagePanel chartPanel = new ChartCagePanel(null, // jfreechart
					DEFAULT_CHART_WIDTH, DEFAULT_CHART_HEIGHT, // preferred width, height of the panel
					MIN_CHART_WIDTH, MIN_CHART_HEIGHT, // minimal drawing width, drawing height
					MAX_CHART_WIDTH, MAX_CHART_HEIGHT, // maximumDrawWidth, maximumDrawHeight
					true, // use memory buffer to improve performance.
					true, // chart property editor available via popup menu
					true, // copy option available via popup menu
					true, // print option available via popup menu
					false, // zoom options not added to the popup menu
					true); // tooltips enabled for the chart;

			return chartPanel;
		}

		ChartCageBuild.initMaxMin();
		XYSeriesCollection xyDataSetList = ChartCageBuild.getSpotDataDirectlyFromOneCage(experiment, cage,
				xlsExportOptions);

		NumberAxis xAxis = setXaxis("", xlsExportOptions);
		NumberAxis yAxis = setYaxis(cage.getRoi().getName(), row, col, xlsExportOptions);
		XYPlot xyPlot = ChartCageBuild.buildXYPlot(xyDataSetList, xAxis, yAxis);

		JFreeChart chart = new JFreeChart(null, // title - the chart title (null permitted).
				null, // titleFont - the font for displaying the chart title (null permitted)
				xyPlot, // plot - controller of the visual representation of the data
				false); // createLegend - legend not created for the chart

		chart.setID("row:" + row + ":icol:" + col + ":cageID:" + cage.getProperties().getCagePosition());

		ChartCagePanel chartCagePanel = new ChartCagePanel(chart, // jfreechart
				DEFAULT_CHART_WIDTH, DEFAULT_CHART_HEIGHT, // preferred width, height of the panel
				MIN_CHART_WIDTH, MIN_CHART_HEIGHT, // minimal drawing width, drawing height
				MAX_CHART_WIDTH, MAX_CHART_HEIGHT, // maximumDrawWidth, maximumDrawHeight
				true, // use memory buffer to improve performance.
				true, // chart property editor available via popup menu
				true, // copy option available via popup menu
				true, // print option available via popup menu
				false, // zoom options not added to the popup menu
				true); // tooltips enabled for the chart

		chartCagePanel.addChartMouseListener(new SpotChartMouseListener(experiment, xlsExportOptions));
		chartCagePanel.subscribeToCagePropertiesUpdates(cage);
		return chartCagePanel;
	}

	/**
	 * Arranges panels in the display based on export options.
	 * 
	 * @param xlsExportOptions the export options
	 */
	private void arrangePanelsInDisplay(XLSExportOptions xlsExportOptions) {
		if (xlsExportOptions.cageIndexFirst == xlsExportOptions.cageIndexLast) {
			int indexCage = xlsExportOptions.cageIndexFirst;
			int row = indexCage / experiment.getCages().nCagesAlongX;
			int col = indexCage % experiment.getCages().nCagesAlongX;

			if (row >= 0 && row < chartPanelArray.length && col >= 0 && col < chartPanelArray[0].length) {
				ChartCagePair pair = chartPanelArray[row][col];
				if (pair != null && pair.getChartPanel() != null) {
					mainChartPanel.add(pair.getChartPanel());
				} else {
					mainChartPanel.add(new JPanel());
				}
			}
		} else {
			for (int row = 0; row < nPanelsAlongY; row++) {
				for (int col = 0; col < nPanelsAlongX; col++) {
					ChartPanel chartPanel = null;
					if (row < chartPanelArray.length && col < chartPanelArray[0].length) {
						ChartCagePair pair = chartPanelArray[row][col];
						if (pair != null) {
							chartPanel = pair.getChartPanel();
						}
					}

					if (chartPanel == null) {
						mainChartPanel.add(new JPanel());
					} else {
						mainChartPanel.add(chartPanel);
					}
				}
			}
		}
	}

	/**
	 * Displays the chart frame.
	 */
	private void displayChartFrame() {
		mainChartFrame.pack();
		mainChartFrame.setLocation(graphLocation);
		mainChartFrame.addToDesktopPane();
		mainChartFrame.setVisible(true);
//		LOGGER.fine("Displayed chart frame at location: " + graphLocation);
	}

	/**
	 * Sets the chart location relative to a rectangle.
	 * 
	 * @param rectv the reference rectangle
	 * @throws IllegalArgumentException if rectv is null
	 */
	public void setChartSpotUpperLeftLocation(Rectangle rectv) {
		if (rectv == null) {
			throw new IllegalArgumentException("Reference rectangle cannot be null");
		}

		graphLocation = new Point(rectv.x, rectv.y);
//		LOGGER.fine("Set chart location to: " + graphLocation);
	}

	/**
	 * Gets the spot from a clicked chart.
	 * 
	 * @param e the chart mouse event
	 * @return the selected spot or null if not found
	 */
	private Spot getSpotFromClickedChart(ChartMouseEvent e) {
		if (e == null) {
			LOGGER.warning("Chart mouse event is null");
			return null;
		}

		final MouseEvent trigger = e.getTrigger();
		if (trigger.getButton() != LEFT_MOUSE_BUTTON) {
			return null;
		}

		JFreeChart chart = e.getChart();
		if (chart == null || chart.getID() == null) {
			LOGGER.warning("Chart or chart ID is null");
			return null;
		}

		String[] chartID = chart.getID().split(CHART_ID_DELIMITER);
		if (chartID.length < 4) {
			LOGGER.warning("Invalid chart ID format: " + chart.getID());
			return null;
		}

		try {
			int row = Integer.parseInt(chartID[1]);
			int col = Integer.parseInt(chartID[3]);

			if (row < 0 || row >= chartPanelArray.length || col < 0 || col >= chartPanelArray[0].length) {
				LOGGER.warning("Invalid chart coordinates: row=" + row + ", col=" + col);
				return null;
			}

			Cage cage = chartPanelArray[row][col].getCage();
			if (cage == null) {
				LOGGER.warning("Clicked chart has no associated cage");
				return null;
			}

			ChartPanel panel = chartPanelArray[row][col].getChartPanel();
			if (panel == null) {
				LOGGER.warning("Clicked chart has no associated panel");
				return null;
			}

			PlotRenderingInfo plotInfo = panel.getChartRenderingInfo().getPlotInfo();
			Point2D pointClicked = panel.translateScreenToJava2D(trigger.getPoint());

			// Get chart
			int subplotIndex = plotInfo.getSubplotIndex(pointClicked);
			XYPlot xyPlot = (XYPlot) chart.getPlot();

			// Get item in the chart
			Spot spotFound = null;
			String description = null;
			ChartEntity chartEntity = e.getEntity();

			if (chartEntity != null && chartEntity instanceof XYItemEntity) {
				spotFound = getSpotFromXYItemEntity((XYItemEntity) chartEntity);
			} else if (subplotIndex >= 0) {
				XYDataset xyDataset = xyPlot.getDataset(0);
				if (xyDataset != null && xyDataset.getSeriesCount() > 0) {
					description = (String) xyDataset.getSeriesKey(0);
					spotFound = experiment.getCages().getSpotFromROIName(description);
				}
			} else {
				if (cage.spotsArray.getSpotsCount() > 0) {
					spotFound = cage.spotsArray.getSpotsList().get(0);
				}
			}

			if (spotFound == null) {
				LOGGER.warning("Failed to find spot from clicked chart");
				return null;
			}

			int index = experiment.getCages().getSpotGlobalPosition(spotFound);
			spotFound.setSpotKymographT(index);
			return spotFound;

		} catch (NumberFormatException ex) {
			LOGGER.warning("Could not parse chart coordinates: " + ex.getMessage());
			return null;
		}
	}

	/**
	 * Gets the spot from an XY item entity.
	 * 
	 * @param xyItemEntity the XY item entity
	 * @return the selected spot or null if not found
	 */
	private Spot getSpotFromXYItemEntity(XYItemEntity xyItemEntity) {
		if (xyItemEntity == null) {
			LOGGER.warning("XY item entity is null");
			return null;
		}

		int seriesIndex = xyItemEntity.getSeriesIndex();
		XYDataset xyDataset = xyItemEntity.getDataset();

		if (xyDataset == null) {
			LOGGER.warning("XY dataset is null");
			return null;
		}

		String description = (String) xyDataset.getSeriesKey(seriesIndex);
		if (description == null) {
			LOGGER.warning("Series description is null");
			return null;
		}

		description = description.substring(0, Math.min(description.length(), MAX_DESCRIPTION_LENGTH));

		Spot spotFound = experiment.getCages().getSpotFromROIName(description);
		if (spotFound == null) {
			LOGGER.warning("Graph clicked but source not found - description (roiName)=" + description);
			return null;
		}

		spotFound.setSpotCamDataT(xyItemEntity.getItem());
		return spotFound;
	}

	/**
	 * Selects a spot in the experiment.
	 * 
	 * @param exp  the experiment
	 * @param spot the spot to select
	 */
	private void chartSelectSpot(Experiment exp, Spot spot) {
		if (exp == null || spot == null) {
			LOGGER.warning("Cannot select spot: experiment or spot is null");
			return;
		}

		ROI2D roi = spot.getRoi();
		if (roi != null) {
			exp.getSeqCamData().getSequence().setFocusedROI(roi);
			exp.getSeqCamData().centerDisplayOnRoi(roi);
		}
	}

	/**
	 * Selects the time position for a spot.
	 * 
	 * @param exp              the experiment
	 * @param xlsExportOptions the export options
	 * @param spot             the spot to select time for
	 */
	private void selectT(Experiment exp, XLSExportOptions xlsExportOptions, Spot spot) {
		if (exp == null || spot == null) {
			LOGGER.warning("Cannot select time: experiment or spot is null");
			return;
		}

		Viewer v = exp.getSeqCamData().getSequence().getFirstViewer();
		if (v != null && spot.getSpotCamDataT() > 0) {
			int frameIndex = (int) (spot.getSpotCamDataT() * xlsExportOptions.buildExcelStepMs
					/ exp.getSeqCamData().getTimeManager().getBinDurationMs());
			v.setPositionT(frameIndex);
		}
	}

	/**
	 * Selects the kymograph for a spot.
	 * 
	 * @param exp  the experiment
	 * @param spot the spot to select kymograph for
	 */
	private void chartSelectKymograph(Experiment exp, Spot spot) {
		if (exp == null || spot == null) {
			LOGGER.warning("Cannot select kymograph: experiment or spot is null");
			return;
		}

//		if (exp.seqKymos != null && exp.seqKymos.getSequence() != null) {
//			Viewer v = exp.seqKymos.getSequence().getFirstViewer();
//			if (v != null) {
//				v.setPositionT(spot.getSpotKymographT());
//			}
//		}
	}

	/**
	 * Handles the selection of a clicked spot.
	 * 
	 * @param exp              the experiment
	 * @param xlsExportOptions the export options
	 * @param clickedSpot      the clicked spot
	 */
	private void chartSelectClickedSpot(Experiment exp, XLSExportOptions xlsExportOptions, Spot clickedSpot) {
		if (clickedSpot == null) {
			LOGGER.warning("Clicked spot is null");
			return;
		}

		chartSelectSpot(exp, clickedSpot);
		selectT(exp, xlsExportOptions, clickedSpot);
		chartSelectKymograph(exp, clickedSpot);

		ROI2D roi = clickedSpot.getRoi();
		if (roi != null) {
			exp.getSeqCamData().getSequence().setSelectedROI(roi);
		}

		String spotName = clickedSpot.getRoi().getName();
		Cage cage = exp.getCages().getCageFromSpotROIName(spotName);
		if (cage != null) {
			ROI2D cageRoi = cage.getRoi();
			exp.getSeqCamData().centerDisplayOnRoi(cageRoi);
		} else {
			LOGGER.warning("Could not find cage for spot: " + spotName);
		}
	}

	// Accessors for testing and external use

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
	 * Gets the chart panel array.
	 * 
	 * @return the chart panel array
	 */
	public ChartCagePair[][] getChartCagePairArray() {
		return chartPanelArray;
	}

	/**
	 * Gets the number of panels along X axis.
	 * 
	 * @return the number of panels along X
	 */
	public int getPanelsAlongX() {
		return nPanelsAlongX;
	}

	/**
	 * Gets the number of panels along Y axis.
	 * 
	 * @return the number of panels along Y
	 */
	public int getPanelsAlongY() {
		return nPanelsAlongY;
	}

	public Range getXRange() {
		return xRange;
	}

	public void setXRange(Range range) {
		xRange = range;
	}

	public Range getYRange() {
		return yRange;
	}

	public void setYRange(Range range) {
		yRange = range;
	}

	/**
	 * Inner class for handling chart mouse events.
	 */
	private class SpotChartMouseListener implements ChartMouseListener {
		private final Experiment experiment;
		private final XLSExportOptions xlsOptions;

		/**
		 * Creates a new mouse listener.
		 * 
		 * @param exp     the experiment
		 * @param options the export options
		 */
		public SpotChartMouseListener(Experiment exp, XLSExportOptions options) {
			this.experiment = exp;
			this.xlsOptions = options;
		}

		@Override
		public void chartMouseClicked(ChartMouseEvent e) {
			Spot clickedSpot = getSpotFromClickedChart(e);
			if (clickedSpot != null) {
				chartSelectClickedSpot(experiment, xlsOptions, clickedSpot);
				Cage cage = experiment.getCages().getCageFromID(clickedSpot.getProperties().getCageID());
				if (cage != null && parent != null && parent.dlgSpots != null) {
					parent.dlgSpots.tabInfos.selectCage(cage);
					parent.dlgSpots.tabInfos.selectSpot(clickedSpot);
				}
			}
		}

		@Override
		public void chartMouseMoved(ChartMouseEvent e) {
			// No action needed for mouse movement
		}
	}
}
