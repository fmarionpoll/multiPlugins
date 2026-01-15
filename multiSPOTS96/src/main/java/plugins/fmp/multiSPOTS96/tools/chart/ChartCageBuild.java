package plugins.fmp.multiSPOTS96.tools.chart;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Stroke;
import java.util.logging.Logger;

import org.jfree.chart.ChartColor;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cages.Cage;
import plugins.fmp.multitools.experiment.cages.CageProperties;
import plugins.fmp.multitools.experiment.spots.Spot;
import plugins.fmp.multitools.experiment.spots.SpotMeasure;
import plugins.fmp.multitools.tools.toExcel.EnumXLSExport;
import plugins.fmp.multitools.tools.toExcel.XLSExportOptions;

/**
 * Utility class for creating and managing cage charts. This class provides
 * functionality to build XY plots from cage data, including data extraction,
 * plot configuration, and rendering setup.
 * 
 * <p>
 * ChartCage handles the conversion of cage data into chart-ready formats,
 * manages global min/max values for axis scaling, and configures plot
 * appearance based on data characteristics.
 * </p>
 * 
 * <p>
 * Usage example:
 * 
 * <pre>
 * ChartCageSpots chartBuilder = new ChartCageSpots();
 * chartBuilder.initMaxMin();
 * 
 * XYSeriesCollection data = chartBuilder.combineResults(cage, resultsArray1, resultsArray2);
 * NumberAxis xAxis = new NumberAxis("Time");
 * NumberAxis yAxis = new NumberAxis("Value");
 * XYPlot plot = chartBuilder.buildXYPlot(data, xAxis, yAxis);
 * </pre>
 * 
 * @author MultiSPOTS96
 * @see org.jfree.chart.plot.XYPlot
 * @see org.jfree.data.xy.XYSeriesCollection
 * @see plugins.fmp.multitools.experiment.cages.Cage
 */
public class ChartCageBuild {

	/** Logger for this class */
	private static final Logger LOGGER = Logger.getLogger(ChartCageBuild.class.getName());

	/** Default stroke width for chart lines */
	private static final float DEFAULT_STROKE_WIDTH = 0.5f;

	/** Default dash pattern for secondary data series */
	private static final float[] DASH_PATTERN = { 2.0f, 4.0f };

	/** Default dash phase for secondary data series */
	private static final float DASH_PHASE = 0.0f;

	/** Background color for charts with data */
	private static final Color BACKGROUND_WITH_DATA = Color.WHITE;

	/** Background color for charts without data */
	private static final Color BACKGROUND_WITHOUT_DATA = Color.LIGHT_GRAY;

	/** Grid color for charts with data */
	private static final Color GRID_WITH_DATA = Color.GRAY;

	/** Grid color for charts without data */
	private static final Color GRID_WITHOUT_DATA = Color.WHITE;

	/** Token used to mark secondary data series */
	private static final String SECONDARY_DATA_TOKEN = "*";

	/** Delimiter used in series descriptions */
	private static final String DESCRIPTION_DELIMITER = ":";

	/** Flag indicating if global min/max values have been set */
	private static boolean flagMaxMinSet = false;

	/** Global maximum Y value across all series */
	private static double globalYMax = 0;

	/** Global minimum Y value across all series */
	private static double globalYMin = 0;

	/** Global maximum X value across all series */
	private static double globalXMax = 0;

	/** Current maximum Y value for the current series */
	private static double ymax = 0;

	/** Current minimum Y value for the current series */
	private static double ymin = 0;

	/** Current maximum X value for the current series */
	private static double xmax = 0;

	/**
	 * Initializes the global min/max tracking variables. This method should be
	 * called before processing new data to reset the global extrema tracking.
	 */
	static public void initMaxMin() {
		ymax = 0;
		ymin = 0;
		xmax = 0;
		flagMaxMinSet = false;
		globalYMax = 0;
		globalYMin = 0;
		globalXMax = 0;

		// LOGGER.fine("Initialized max/min tracking variables");
	}

	/**
	 * Builds an XY plot from the given dataset and axes.
	 * 
	 * @param xySeriesCollection the dataset to plot
	 * @param xAxis              the X-axis to use
	 * @param yAxis              the Y-axis to use
	 * @return configured XYPlot ready for chart creation
	 * @throws IllegalArgumentException if any parameter is null
	 */
	static public XYPlot buildXYPlot(XYSeriesCollection xySeriesCollection, NumberAxis xAxis, NumberAxis yAxis) {
		if (xySeriesCollection == null) {
			throw new IllegalArgumentException("XYSeriesCollection cannot be null");
		}
		if (xAxis == null) {
			throw new IllegalArgumentException("X-axis cannot be null");
		}
		if (yAxis == null) {
			throw new IllegalArgumentException("Y-axis cannot be null");
		}

//		//LOGGER.fine("Building XY plot with " + xySeriesCollection.getSeriesCount() + " series");

		XYLineAndShapeRenderer subPlotRenderer = getSubPlotRenderer(xySeriesCollection);
		XYPlot xyPlot = new XYPlot(xySeriesCollection, xAxis, yAxis, subPlotRenderer);
		updatePlotBackgroundAccordingToNFlies(xySeriesCollection, xyPlot);

		return xyPlot;
	}

	/**
	 * Updates the plot background and grid colors based on the number of flies in
	 * the data.
	 * 
	 * @param xySeriesCollection the dataset to analyze
	 * @param xyPlot             the plot to update
	 */
	private static void updatePlotBackgroundAccordingToNFlies(XYSeriesCollection xySeriesCollection, XYPlot xyPlot) {
		int nFlies = getNFliesFromxySeriesCollectionDescription(xySeriesCollection);
		setXYPlotBackGroundAccordingToNFlies(xyPlot, nFlies);
	}

	private static int getNFliesFromxySeriesCollectionDescription(XYSeriesCollection xySeriesCollection) {
		int nFlies = -1;
		if (xySeriesCollection == null || xySeriesCollection.getSeriesCount() == 0) {
			LOGGER.warning("Cannot update plot background: dataset is null or empty");
			return nFlies;
		}

		try {
			String[] description = xySeriesCollection.getSeries(0).getDescription().split(DESCRIPTION_DELIMITER);
			if (description.length < 6) {
				LOGGER.warning("Invalid series description format, using default background");
				return nFlies;
			}
			nFlies = Integer.parseInt(description[5]);
		} catch (NumberFormatException e) {
			LOGGER.warning("Could not parse number of flies from description: " + e.getMessage());
		}
		return nFlies;
	}

	private static void setXYPlotBackGroundAccordingToNFlies(XYPlot xyPlot, int nFlies) {
		if (nFlies > 0) {
			xyPlot.setBackgroundPaint(BACKGROUND_WITH_DATA);
			xyPlot.setDomainGridlinePaint(GRID_WITH_DATA);
			xyPlot.setRangeGridlinePaint(GRID_WITH_DATA);
			// LOGGER.fine("Set background for chart with " + nflies + " flies");
		} else {
			xyPlot.setBackgroundPaint(BACKGROUND_WITHOUT_DATA);
			xyPlot.setDomainGridlinePaint(GRID_WITHOUT_DATA);
			xyPlot.setRangeGridlinePaint(GRID_WITHOUT_DATA);
			// LOGGER.fine("Set background for chart with no flies");
		}
	}

	/**
	 * Extracts spot data from one cage in the results array.
	 * 
	 * @param experiment the stack of images and assoc items
	 * @param cage       the cage to get data for
	 * @param options    list of options
	 * @return XYSeriesCollection containing the cage's data
	 */
	static XYSeriesCollection getSpotDataDirectlyFromOneCage(Experiment exp, Cage cage,
			XLSExportOptions xlsExportOptions) {
		if (cage == null || cage.spotsArray == null || cage.spotsArray.getSpotsCount() < 1) {
			LOGGER.warning("Cannot get spot data: spot array is empty or cage is null");
			return new XYSeriesCollection();
		}

		XYSeriesCollection xySeriesCollection = null;

		for (Spot spot : cage.spotsArray.getSpotsList()) {
			if (xySeriesCollection == null) {
				xySeriesCollection = new XYSeriesCollection();
			}

			XYSeries seriesXY = createXYSeriesFromSpotMeasure(exp, spot, xlsExportOptions);
			if (seriesXY != null) {
				seriesXY.setDescription(buildSeriesDescriptionFromCageAndSpot(cage, spot));
				xySeriesCollection.addSeries(seriesXY);
				updateGlobalMaxMin();
			}
		}

		// LOGGER.fine("Extracted " + seriesCount + " series for cage ID: " +
		// cage.getProperties().getCageID());
		return xySeriesCollection;
	}

	/**
	 * Builds a description string for a series.
	 * 
	 * @param cage the cage
	 * @param spot the spot
	 * @return formatted description string
	 */
	private static String buildSeriesDescriptionFromCageAndSpot(Cage cage, Spot spot) {
		CageProperties cageProp = cage.getProperties();
		Color color = spot.getProperties().getColor();
		return "ID:" + cageProp.getCageID() + ":Pos:" + cageProp.getCagePosition() + ":nflies:"
				+ cageProp.getCageNFlies() + ":R:" + color.getRed() + ":G:" + color.getGreen() + ":B:"
				+ color.getBlue();
	}

	private static XYSeries createXYSeriesFromSpotMeasure(Experiment exp, Spot spot,
			XLSExportOptions xlsExportOptions) {
		XYSeries seriesXY = new XYSeries(spot.getName(), false);

		if (exp.seqCamData.getTimeManager().getCamImagesTime_Ms() == null)
			exp.seqCamData.build_MsTimesArray_From_FileNamesList();
		double[] camImages_time_min = exp.seqCamData.getTimeManager().getCamImagesTime_Minutes();
		SpotMeasure spotMeasure = spot.getMeasurements(xlsExportOptions.exportType);
		double divider = 1.;
		if (xlsExportOptions.relativeToT0 && xlsExportOptions.exportType != EnumXLSExport.AREA_FLYPRESENT) {
			divider = spotMeasure.getMaximumValue();
		}

		int npoints = spotMeasure.getCount();

		for (int j = 0; j < npoints; j++) {
			double x = camImages_time_min[j];
			double y = spotMeasure.getValueAt(j) / divider;
			seriesXY.add(x, y);

			if (ymax < y) {
				ymax = y;
			}
			if (ymin > y) {
				ymin = y;
			}
			x++;
		}
		return seriesXY;
	}

	/**
	 * Updates the global min/max values based on current series extrema.
	 */
	private static void updateGlobalMaxMin() {
		if (!flagMaxMinSet) {
			globalYMax = ymax;
			globalYMin = ymin;
			globalXMax = xmax;
			flagMaxMinSet = true;
			// LOGGER.fine(
//					"Set initial global extrema: Y[" + globalYMin + ", " + globalYMax + "], X[0, " + globalXMax + "]");
		} else {
//			boolean updated = false;
			if (globalYMax < ymax) {
				globalYMax = ymax;
//				updated = true;
			}
			if (globalYMin > ymin) {
				globalYMin = ymin;
//				updated = true;
			}
			if (globalXMax < xmax) {
				globalXMax = xmax;
//				updated = true;
			}

//			if (updated) {
//				LOGGER.fine(
//						"Updated global extrema: Y[" + globalYMin + ", " + globalYMax + "], X[0, " + globalXMax + "]");
//			}
		}
	}

	/**
	 * Creates a renderer for the XY plot with appropriate styling.
	 * 
	 * @param xySeriesCollection the dataset to render
	 * @return configured XYLineAndShapeRenderer
	 */
	private static XYLineAndShapeRenderer getSubPlotRenderer(XYSeriesCollection xySeriesCollection) {
		if (xySeriesCollection == null) {
			LOGGER.warning("Cannot create renderer: dataset is null");
			return null;
		}

		XYLineAndShapeRenderer subPlotRenderer = new XYLineAndShapeRenderer(true, false);
		Stroke stroke = new BasicStroke(DEFAULT_STROKE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1.0f,
				DASH_PATTERN, DASH_PHASE);

		for (int i = 0; i < xySeriesCollection.getSeriesCount(); i++) {
			try {
				String[] description = xySeriesCollection.getSeries(i).getDescription().split(DESCRIPTION_DELIMITER);
				if (description.length >= 12) {
					int r = Integer.parseInt(description[7]);
					int g = Integer.parseInt(description[9]);
					int b = Integer.parseInt(description[11]);
					subPlotRenderer.setSeriesPaint(i, new ChartColor(r, g, b));

					String key = (String) xySeriesCollection.getSeriesKey(i);
					if (key != null && key.contains(SECONDARY_DATA_TOKEN)) {
						subPlotRenderer.setSeriesStroke(i, stroke);
					}
				} else {
					LOGGER.warning("Invalid description format for series " + i + ", using default color");
					subPlotRenderer.setSeriesPaint(i, ChartColor.BLACK);
				}
			} catch (NumberFormatException e) {
				LOGGER.warning("Could not parse color values for series " + i + ": " + e.getMessage());
				subPlotRenderer.setSeriesPaint(i, ChartColor.BLACK);
			}
		}

		// LOGGER.fine("Created renderer for " + xySeriesCollection.getSeriesCount() + "
		// series");
		return subPlotRenderer;
	}

	/**
	 * Gets the global maximum Y value.
	 * 
	 * @return the global Y maximum
	 */
	public double getGlobalYMax() {
		return globalYMax;
	}

	/**
	 * Gets the global minimum Y value.
	 * 
	 * @return the global Y minimum
	 */
	public double getGlobalYMin() {
		return globalYMin;
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
	 * Checks if global min/max values have been set.
	 * 
	 * @return true if global extrema are set, false otherwise
	 */
	public boolean isGlobalMaxMinSet() {
		return flagMaxMinSet;
	}

}
