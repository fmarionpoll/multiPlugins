package plugins.fmp.multitools.tools.chart;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.data.Range;
import org.jfree.data.xy.XYSeriesCollection;

import icy.gui.frame.IcyFrame;
import icy.gui.util.GuiUtil;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cages.cage.Cage;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.chart.builders.CageSeriesBuilder;
import plugins.fmp.multitools.tools.chart.plot.CageChartPlotFactory;
import plugins.fmp.multitools.tools.chart.strategies.ChartLayoutStrategy;
import plugins.fmp.multitools.tools.chart.strategies.ChartUIControlsFactory;
import plugins.fmp.multitools.tools.chart.strategies.ComboBoxUIControlsFactory;
import plugins.fmp.multitools.tools.results.ResultsOptions;

/**
 * Generic base class for displaying cage-based charts in a grid or horizontal
 * layout. This class provides a flexible framework for displaying different
 * types of measurements (capillaries, spots, fly positions) using a strategy
 * pattern for layout and UI controls.
 * 
 * <p>
 * Usage example:
 * 
 * <pre>
 * CageChartArrayFrame chartFrame = new ChartCagesFrame(
 *     new CageCapillarySeriesBuilder(),
 *     new CapillaryChartInteractionHandler(...),
 *     new GridLayoutStrategy(),
 *     new ComboBoxUIControlsFactory()
 * );
 * chartFrame.createMainChartPanel("Title", experiment, options);
 * chartFrame.displayData(experiment, options);
 * </pre>
 * 
 * @author MultiCAFE
 */
public class ChartCagesFrame extends IcyFrame {

	/** Default chart width in pixels */
	protected static final int DEFAULT_CHART_WIDTH = 200;

	/** Default chart height in pixels */
	protected static final int DEFAULT_CHART_HEIGHT = 100;

	/** Default minimum chart width in pixels */
	protected static final int MIN_CHART_WIDTH = 50;

	/** Default maximum chart width in pixels */
	protected static final int MAX_CHART_WIDTH = 1200;

	/** Default minimum chart height in pixels */
	protected static final int MIN_CHART_HEIGHT = 25;

	/** Default maximum chart height in pixels */
	protected static final int MAX_CHART_HEIGHT = 600;

	/** Default frame width in pixels */
	protected static final int DEFAULT_FRAME_WIDTH = 300;

	/** Default frame height in pixels */
	protected static final int DEFAULT_FRAME_HEIGHT = 70;

	/** Default Y-axis range for relative data */
	protected static final double RELATIVE_Y_MIN = -0.2;

	/** Default Y-axis range for relative data */
	protected static final double RELATIVE_Y_MAX = 1.2;

	/** Main chart panel containing all charts */
	protected JPanel mainChartPanel = null;

	/** Main chart frame */
	public IcyFrame mainChartFrame = null;

	/** Y-axis range for charts */
	private Range yRange = null;

	/** X-axis range for charts */
	private Range xRange = null;

	/** Chart location */
	private Point graphLocation = new Point(0, 0);

	/** Number of panels along X axis */
	protected int nPanelsAlongX = 1;

	/** Number of panels along Y axis */
	protected int nPanelsAlongY = 1;

	/** Array of chart panel pairs */
	public ChartCagePair[][] chartPanelArray = null;

	/** Current experiment */
	protected Experiment experiment = null;

	/** Chart interaction handler factory */
	private ChartInteractionHandlerFactory interactionHandlerFactory = null;

	/** Current interaction handler (created after array is built) */
	private ChartInteractionHandler interactionHandler = null;

	/** Current results options */
	protected ResultsOptions currentOptions = null;

	/** Base title for the chart window */
	private String baseTitle = null;

	/** Data builder for creating chart datasets */
	private final CageSeriesBuilder dataBuilder;

	/** Layout strategy */
	private final ChartLayoutStrategy layoutStrategy;

	/** UI controls factory */
	private final ChartUIControlsFactory uiControlsFactory;

	/**
	 * Creates a new cage chart array frame with the specified strategies.
	 * 
	 * @param dataBuilder               the builder for creating chart data
	 * @param interactionHandlerFactory the factory for creating interaction
	 *                                  handlers (can be null)
	 * @param layoutStrategy            the layout strategy
	 * @param uiControlsFactory         the UI controls factory
	 */
	public ChartCagesFrame(CageSeriesBuilder dataBuilder, ChartInteractionHandlerFactory interactionHandlerFactory,
			ChartLayoutStrategy layoutStrategy, ChartUIControlsFactory uiControlsFactory) {
		if (dataBuilder == null) {
			throw new IllegalArgumentException("Data builder cannot be null");
		}
		if (layoutStrategy == null) {
			throw new IllegalArgumentException("Layout strategy cannot be null");
		}
		if (uiControlsFactory == null) {
			throw new IllegalArgumentException("UI controls factory cannot be null");
		}

		this.dataBuilder = dataBuilder;
		this.interactionHandlerFactory = interactionHandlerFactory;
		this.layoutStrategy = layoutStrategy;
		this.uiControlsFactory = uiControlsFactory;
	}

	/**
	 * Creates the main chart panel and frame.
	 * 
	 * @param title   the title for the chart window
	 * @param exp     the experiment containing the data
	 * @param options the export options for data processing
	 * @throws IllegalArgumentException if any required parameter is null
	 */
	public void createMainChartPanel(String title, Experiment exp, ResultsOptions options) {
		if (exp == null) {
			throw new IllegalArgumentException("Experiment cannot be null");
		}
		if (options == null) {
			throw new IllegalArgumentException("Export options cannot be null");
		}
		if (title == null || title.trim().isEmpty()) {
			throw new IllegalArgumentException("Title cannot be null or empty");
		}

		this.experiment = exp;
		this.currentOptions = options;
		this.baseTitle = title;

		mainChartPanel = new JPanel();
		// Single-cage mode: first == last and both are positive
		boolean flag = (options.cageIndexFirst == options.cageIndexLast && options.cageIndexFirst >= 0);
		nPanelsAlongX = flag ? 1 : exp.getCages().nCagesAlongX;
		nPanelsAlongY = flag ? 1 : exp.getCages().nCagesAlongY;

		// Set layout using strategy
		layoutStrategy.setLayoutOnPanel(mainChartPanel, nPanelsAlongX, nPanelsAlongY);

		String finalTitle = title + ": " + options.resultType.toString();

		// Reuse existing frame if it's still valid
		if (mainChartFrame != null && (mainChartFrame.getParent() != null || mainChartFrame.isVisible())) {
			mainChartFrame.setTitle(finalTitle);
			mainChartFrame.removeAll();
		} else {
			mainChartFrame = GuiUtil.generateTitleFrame(finalTitle, new JPanel(),
					new Dimension(DEFAULT_FRAME_WIDTH, DEFAULT_FRAME_HEIGHT), true, true, true, true);
		}

		mainChartFrame.setLayout(new BorderLayout());

		// Create top panel with UI controls
		JPanel topPanel = uiControlsFactory.createTopPanel(options, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (currentOptions != null && experiment != null) {
					updateFrameTitle();
					displayData(experiment, currentOptions);
				}
			}
		});
		if (topPanel != null) {
			mainChartFrame.add(topPanel, BorderLayout.NORTH);
		}

		JScrollPane scrollPane = new JScrollPane(mainChartPanel);
		mainChartFrame.add(scrollPane, BorderLayout.CENTER);

		// Create bottom panel (legend, etc.)
		JPanel bottomPanel = uiControlsFactory.createBottomPanel(options, exp);
		if (bottomPanel != null) {
			mainChartFrame.add(bottomPanel, BorderLayout.SOUTH);
		}

		mainChartFrame.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				savePreferences();
			}

			@Override
			public void componentMoved(ComponentEvent e) {
				savePreferences();
			}
		});

		chartPanelArray = new ChartCagePair[nPanelsAlongY][nPanelsAlongX];
	}

	/**
	 * Sets up the Y-axis for a chart.
	 * 
	 * @param label          the axis label
	 * @param row            the row index
	 * @param col            the column index
	 * @param resultsOptions the export options
	 * @return configured NumberAxis
	 */
	protected NumberAxis setYaxis(String label, int row, int col, ResultsOptions resultsOptions) {
		NumberAxis yAxis = new NumberAxis();
		if (experiment != null && experiment.getCages() != null) {
			row = row * experiment.getCages().nRowsPerCage;
			col = col * experiment.getCages().nColumnsPerCage;
		}
		String yLegend = label + resultsOptions.resultType.toUnit();
		yAxis.setLabel(yLegend);

		if (resultsOptions.relativeToMaximum || resultsOptions.relativeToMedianT0) {
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
	 * @param label          the axis label
	 * @param resultsOptions the export options
	 * @return configured NumberAxis
	 */
	protected NumberAxis setXaxis(String label, ResultsOptions resultsOptions) {
		NumberAxis xAxis = new NumberAxis();
		xAxis.setLabel(label);
		xAxis.setAutoRange(true);
		xAxis.setAutoRangeIncludesZero(false);
		return xAxis;
	}

	/**
	 * Displays data for the experiment.
	 * 
	 * @param exp            the experiment containing the data
	 * @param resultsOptions the export options for data processing
	 * @throws IllegalArgumentException if exp or resultsOptions is null
	 */
	public void displayData(Experiment exp, ResultsOptions resultsOptions) {
		if (exp == null) {
			throw new IllegalArgumentException("Experiment cannot be null");
		}
		if (resultsOptions == null) {
			throw new IllegalArgumentException("Export options cannot be null");
		}

		this.experiment = exp;
		this.currentOptions = resultsOptions;

		// Update experiment reference in factory if it supports it
		if (uiControlsFactory instanceof ComboBoxUIControlsFactory) {
			((ComboBoxUIControlsFactory) uiControlsFactory).setExperiment(exp);
		}

		// Update UI controls
		uiControlsFactory.updateControls(resultsOptions.resultType, resultsOptions);

		// Clear any previously displayed charts
		if (mainChartPanel != null) {
			mainChartPanel.removeAll();
		}

		// Ensure derived measures are computed before building datasets
		exp.getCages().prepareComputations(exp, resultsOptions);

		// Create interaction handler if factory is provided
		if (interactionHandlerFactory != null) {
			interactionHandler = interactionHandlerFactory.createHandler(exp, resultsOptions, chartPanelArray);
		}

		createChartsPanel(resultsOptions);
		arrangePanelsInDisplay(resultsOptions);
		displayChartFrame();
	}

	/**
	 * Creates chart panels for all cages in the experiment.
	 * 
	 * @param resultsOptions the export options
	 */
	protected void createChartsPanel(ResultsOptions resultsOptions) {
		// Determine if we're in single-cage mode: first == last and both are positive
		boolean flag = (resultsOptions.cageIndexFirst == resultsOptions.cageIndexLast
				&& resultsOptions.cageIndexFirst >= 0);

		// Filter cages based on the new semantics:
		// - If first == last (and positive): display only that specific cage ID
		// - Otherwise: display all available cages (ignore the range)
		List<Cage> availableCages = new ArrayList<>();
		for (Cage cage : experiment.getCages().getCageList()) {
			if (flag) {
				// Single cage mode: only include the cage with the matching ID
				int cageID = cage.getProperties().getCageID();
				if (cageID == resultsOptions.cageIndexFirst) {
					availableCages.add(cage);
				}
			} else {
				// Display all cages mode: include all cages
				availableCages.add(cage);
			}
		}

		if (flag || availableCages.isEmpty()) {
			// Single cage mode or no cages
			nPanelsAlongX = 1;
			nPanelsAlongY = 1;
		} else {
			// Calculate optimal grid dimensions based on actual number of cages
			// This ensures all cages are displayed and uses available space efficiently
			int numCages = availableCages.size();
			// Try to maintain aspect ratio close to the configured one, but adapt to actual
			// number
			nPanelsAlongX = experiment.getCages().nCagesAlongX;
			nPanelsAlongY = (numCages + nPanelsAlongX - 1) / nPanelsAlongX; // Ceiling division
			// Ensure we have enough columns to fit all cages
			if (nPanelsAlongX * nPanelsAlongY < numCages) {
				nPanelsAlongY++;
			}
		}

		// Set layout using strategy
		layoutStrategy.setLayoutOnPanel(mainChartPanel, nPanelsAlongX, nPanelsAlongY);

		// Reset array to ensure no stale panels
		chartPanelArray = new ChartCagePair[nPanelsAlongY][nPanelsAlongX];

		// Create interaction handler now that array is initialized
		if (interactionHandlerFactory != null) {
			interactionHandler = interactionHandlerFactory.createHandler(experiment, resultsOptions, chartPanelArray);
		}

		ChartCageBuild.initMaxMin();
		Map<Cage, XYSeriesCollection> datasets = new HashMap<Cage, XYSeriesCollection>();

		// Build datasets for all available cages (only iterate over existing cages)
		for (Cage cage : availableCages) {
			XYSeriesCollection xyDataSetList = dataBuilder.build(experiment, cage, resultsOptions);
			datasets.put(cage, xyDataSetList);
		}

		// Create chart panels - iterate over existing cages only
		// Use sequential positioning based on available cages with valid data
		// This ensures continuous layout without gaps for missing cages
		int validIndex = 0;
		for (Cage cage : availableCages) {
			XYSeriesCollection xyDataSetList = datasets.get(cage);
			if (xyDataSetList == null) {
				continue;
			}

			// Use sequential positioning based on valid cages index
			// Grid dimensions are already calculated to accommodate all cages
			int row = validIndex / nPanelsAlongX;
			int col = validIndex % nPanelsAlongX;

			ChartPanel chartPanel = createChartPanelForCage(cage, row, col, resultsOptions, xyDataSetList);
			int arrayRow = flag ? 0 : row;
			int arrayCol = flag ? 0 : col;

			// Ensure array indices are within bounds
			if (arrayRow >= 0 && arrayRow < nPanelsAlongY && arrayCol >= 0 && arrayCol < nPanelsAlongX) {
				chartPanelArray[arrayRow][arrayCol] = new ChartCagePair(chartPanel, cage);
			}
			validIndex++;
		}
	}

	/**
	 * Creates a chart panel for a specific cage.
	 * 
	 * @param cage           the cage to create chart for
	 * @param row            the row index
	 * @param col            the column index
	 * @param resultsOptions the export options
	 * @param xyDataSetList  the dataset for this cage
	 * @return configured ChartPanel
	 */
	protected ChartCagePanel createChartPanelForCage(Cage cage, int row, int col, ResultsOptions resultsOptions,
			XYSeriesCollection xyDataSetList) {

		// If no data, show placeholder
		if (xyDataSetList == null || xyDataSetList.getSeriesCount() == 0) {
			NumberAxis xAxis = setXaxis("", resultsOptions);
			NumberAxis yAxis = setYaxis("", row, col, resultsOptions);
			XYPlot xyPlot = CageChartPlotFactory.buildXYPlot(new XYSeriesCollection(), xAxis, yAxis);
			JFreeChart chart = new JFreeChart(null, null, xyPlot, false);

			TextTitle title = new TextTitle("Cage " + cage.getProperties().getCageID() + " (no data)",
					new Font("SansSerif", Font.PLAIN, 12));
			title.setPosition(RectangleEdge.BOTTOM);
			chart.addSubtitle(title);
			chart.setID("row:" + row + ":icol:" + col + ":cageID:" + cage.getProperties().getCagePosition());

			ChartCagePanel chartCagePanel = new ChartCagePanel(chart, DEFAULT_CHART_WIDTH, DEFAULT_CHART_HEIGHT,
					MIN_CHART_WIDTH, MIN_CHART_HEIGHT, MAX_CHART_WIDTH, MAX_CHART_HEIGHT, true, true, true, true, false,
					true);
			chartCagePanel.subscribeToCagePropertiesUpdates(cage);
			return chartCagePanel;
		}

		// Check if cage has required data (subclasses can override this)
		if (!hasRequiredData(cage)) {
			ChartCagePanel chartPanel = new ChartCagePanel(null, DEFAULT_CHART_WIDTH, DEFAULT_CHART_HEIGHT,
					MIN_CHART_WIDTH, MIN_CHART_HEIGHT, MAX_CHART_WIDTH, MAX_CHART_HEIGHT, true, true, true, true, false,
					true);
			return chartPanel;
		}

		NumberAxis xAxis = setXaxis("", resultsOptions);
		NumberAxis yAxis = setYaxis("", row, col, resultsOptions);

		if (!resultsOptions.relativeToMaximum && !resultsOptions.relativeToMedianT0) {
			if (ChartCageBuild.isGlobalMaxMinSet()) {
				double min = ChartCageBuild.getGlobalYMin();
				double max = ChartCageBuild.getGlobalYMax();
				double range = max - min;
				if (range == 0)
					range = 1.0;
				yAxis.setRange(min - range * 0.05, max + range * 0.05);
			}
		}

		XYPlot xyPlot = CageChartPlotFactory.buildXYPlot(xyDataSetList, xAxis, yAxis);

		JFreeChart chart = new JFreeChart(null, null, xyPlot, false);

		TextTitle title = new TextTitle("Cage " + cage.getProperties().getCageID(),
				new Font("SansSerif", Font.PLAIN, 12));
		title.setPosition(RectangleEdge.BOTTOM);
		chart.addSubtitle(title);

		chart.setID("row:" + row + ":icol:" + col + ":cageID:" + cage.getProperties().getCagePosition());

		ChartCagePanel chartCagePanel = new ChartCagePanel(chart, DEFAULT_CHART_WIDTH, DEFAULT_CHART_HEIGHT,
				MIN_CHART_WIDTH, MIN_CHART_HEIGHT, MAX_CHART_WIDTH, MAX_CHART_HEIGHT, true, true, true, true, false,
				true);

		if (interactionHandler != null) {
			chartCagePanel.addChartMouseListener(interactionHandler.createMouseListener());
		}
		chartCagePanel.subscribeToCagePropertiesUpdates(cage);
		return chartCagePanel;
	}

	/**
	 * Checks if the cage has the required data for display. Subclasses can override
	 * this to provide custom validation.
	 * 
	 * @param cage the cage to check
	 * @return true if the cage has required data
	 */
	protected boolean hasRequiredData(Cage cage) {
		// Default implementation: always return true
		// Subclasses can override to check for specific data (e.g., capillaries, spots)
		return true;
	}

	/**
	 * Arranges panels in the display based on export options.
	 * 
	 * @param resultsOptions the export options
	 */
	protected void arrangePanelsInDisplay(ResultsOptions resultsOptions) {
		// Single-cage mode: first == last and both are positive
		boolean singleCageMode = (resultsOptions.cageIndexFirst == resultsOptions.cageIndexLast
				&& resultsOptions.cageIndexFirst >= 0);
		layoutStrategy.arrangePanels(mainChartPanel, chartPanelArray, nPanelsAlongX, nPanelsAlongY, singleCageMode);
	}

	/**
	 * Displays the chart frame.
	 */
	protected void displayChartFrame() {
		if (mainChartFrame == null) {
			Logger.warn("Cannot display chart frame: mainChartFrame is null");
			return;
		}

		mainChartFrame.pack();
		loadPreferences();

		// Only add to desktop pane if not already added
		if (mainChartFrame.getParent() == null) {
			mainChartFrame.addToDesktopPane();
		}

		mainChartFrame.setVisible(true);
	}

	private void loadPreferences() {
		Preferences prefs = Preferences.userNodeForPackage(ChartCagesFrame.class);
		int x = prefs.getInt("window_x", graphLocation.x);
		int y = prefs.getInt("window_y", graphLocation.y);
		int w = prefs.getInt("window_w", DEFAULT_FRAME_WIDTH);
		int h = prefs.getInt("window_h", DEFAULT_FRAME_HEIGHT);
		mainChartFrame.setBounds(new Rectangle(x, y, w, h));
	}

	private void savePreferences() {
		Preferences prefs = Preferences.userNodeForPackage(ChartCagesFrame.class);
		Rectangle r = mainChartFrame.getBounds();
		prefs.putInt("window_x", r.x);
		prefs.putInt("window_y", r.y);
		prefs.putInt("window_w", r.width);
		prefs.putInt("window_h", r.height);
	}

	/**
	 * Sets the chart location relative to a rectangle.
	 * 
	 * @param rectv the reference rectangle
	 * @throws IllegalArgumentException if rectv is null
	 */
	public void setChartUpperLeftLocation(Rectangle rectv) {
		if (rectv == null) {
			throw new IllegalArgumentException("Reference rectangle cannot be null");
		}

		graphLocation = new Point(rectv.x, rectv.y);
	}

	/**
	 * Updates the frame title to reflect the current measurement type.
	 */
	protected void updateFrameTitle() {
		if (mainChartFrame != null && baseTitle != null && currentOptions != null) {
			String finalTitle = baseTitle + ": " + currentOptions.resultType.toString();
			mainChartFrame.setTitle(finalTitle);
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
}
