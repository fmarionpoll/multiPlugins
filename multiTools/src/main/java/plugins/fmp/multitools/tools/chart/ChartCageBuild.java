package plugins.fmp.multiSPOTS96.tools.chart;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Stroke;

import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.chart.style.SeriesStyleCodec;
import plugins.fmp.multitools.tools.results.EnumResults;

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
 */
public class ChartCageBuild {

	/** Default stroke width for chart lines */
	private static final float DEFAULT_STROKE_WIDTH = 0.5f;

	/** Default dash pattern for secondary data series */
	private static final float[] DASH_PATTERN = { 2.0f, 4.0f };

	/** Default dash phase for secondary data series */
	private static final float DASH_PHASE = 0.0f;

	/** Background color for charts with data */
	// kept for backward compatibility with earlier versions; unused in current
	// refactor
	@SuppressWarnings("unused")
	private static final Color BACKGROUND_WITH_DATA = Color.WHITE;

	/** Background color for charts without data */
	@SuppressWarnings("unused")
	private static final Color BACKGROUND_WITHOUT_DATA = Color.LIGHT_GRAY;

	/** Grid color for charts with data */
	@SuppressWarnings("unused")
	private static final Color GRID_WITH_DATA = Color.GRAY;

	/** Grid color for charts without data */
	@SuppressWarnings("unused")
	private static final Color GRID_WITHOUT_DATA = Color.WHITE;

	/** Token used to mark secondary data series */
	private static final String SECONDARY_DATA_TOKEN = "*";

	/** Delimiter used in series descriptions */
	@SuppressWarnings("unused")
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
		ymax = Double.NaN;
		ymin = Double.NaN;
		xmax = 0;
		flagMaxMinSet = false;
		globalYMax = 0;
		globalYMin = 0;
		globalXMax = 0;
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

	public static boolean isLRType(EnumResults resultType) {
		return resultType == EnumResults.TOPLEVEL_LR || resultType == EnumResults.TOPLEVELDELTA_LR
				|| resultType == EnumResults.SUMGULPS_LR;
	}

	/**
	 * Updates the global max/min values by scanning a dataset.
	 *
	 * Kept on this legacy class because callers rely on the static global extrema.
	 */
	public static void updateGlobalExtremaFromDataset(XYSeriesCollection dataset) {
		if (dataset == null || dataset.getSeriesCount() == 0)
			return;

		// Reset per-dataset extrema, update per-series, then fold into global.
		ymax = Double.NaN;
		ymin = Double.NaN;
		xmax = 0;

		for (int i = 0; i < dataset.getSeriesCount(); i++) {
			XYSeries series = dataset.getSeries(i);
			for (int j = 0; j < series.getItemCount(); j++) {
				double y = series.getY(j).doubleValue();
				double x = series.getX(j).doubleValue();
				if (!Double.isNaN(y)) {
					if (Double.isNaN(ymax) || y > ymax)
						ymax = y;
					if (Double.isNaN(ymin) || y < ymin)
						ymin = y;
				}
				if (x > xmax)
					xmax = x;
			}
		}
		updateGlobalMaxMin();
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
		} else {
			if (Double.isNaN(globalYMax) || globalYMax < ymax) {
				globalYMax = ymax;
			}
			if (Double.isNaN(globalYMin) || globalYMin > ymin) {
				globalYMin = ymin;
			}
			if (globalXMax < xmax) {
				globalXMax = xmax;
			}
		}
	}

	/**
	 * Creates a renderer for the XY plot with appropriate styling.
	 * 
	 * @param xySeriesCollection the dataset to render
	 * @return configured XYLineAndShapeRenderer
	 */
	@SuppressWarnings("unused")
	private static XYLineAndShapeRenderer getSubPlotRenderer(XYSeriesCollection xySeriesCollection) {
		if (xySeriesCollection == null) {
			Logger.warn("Cannot create renderer: dataset is null");
			return null;
		}

		XYLineAndShapeRenderer subPlotRenderer = new XYLineAndShapeRenderer(true, false);
		Stroke stroke = new BasicStroke(DEFAULT_STROKE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1.0f,
				DASH_PATTERN, DASH_PHASE);

		SeriesStyleCodec.applySeriesPaintsFromDescription(xySeriesCollection, subPlotRenderer);
		for (int i = 0; i < xySeriesCollection.getSeriesCount(); i++) {
			String key = (String) xySeriesCollection.getSeriesKey(i);
			if (key != null && key.contains(SECONDARY_DATA_TOKEN)) {
				subPlotRenderer.setSeriesStroke(i, stroke);
			}
		}
		return subPlotRenderer;
	}

	/**
	 * Gets the global maximum Y value.
	 * 
	 * @return the global Y maximum
	 */
	public static double getGlobalYMax() {
		return globalYMax;
	}

	/**
	 * Gets the global minimum Y value.
	 * 
	 * @return the global Y minimum
	 */
	public static double getGlobalYMin() {
		return globalYMin;
	}

	/**
	 * Gets the global maximum X value.
	 * 
	 * @return the global X maximum
	 */
	public static double getGlobalXMax() {
		return globalXMax;
	}

	/**
	 * Checks if global min/max values have been set.
	 * 
	 * @return true if global extrema are set, false otherwise
	 */
	public static boolean isGlobalMaxMinSet() {
		return flagMaxMinSet;
	}

}
