package plugins.fmp.multitools.fmp_tools.chart.plot;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Stroke;

import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import plugins.fmp.multitools.fmp_tools.chart.style.SeriesStyleCodec;

/**
 * Central place to build and style cage XY plots from an
 * {@link XYSeriesCollection}.
 *
 * <p>
 * This class does not know how to extract data (Spot vs Capillary). It only
 * cares about plotting concerns.
 * </p>
 */
public final class CageChartPlotFactory {
	private static final float DEFAULT_STROKE_WIDTH = 0.5f;
	private static final float[] DASH_PATTERN = { 2.0f, 4.0f };
	private static final float DASH_PHASE = 0.0f;

	private static final Color BACKGROUND_WITH_DATA = Color.WHITE;
	private static final Color BACKGROUND_WITHOUT_DATA = Color.LIGHT_GRAY;
//	private static final Color BACKGROUND_WITH_ARTEFACT = Color.DARK_GRAY;
	private static final Color GRID_WITH_DATA = Color.GRAY;
	private static final Color GRID_WITHOUT_DATA = Color.WHITE;

	// Token used historically to mark secondary series (dashed)
	private static final String SECONDARY_DATA_TOKEN = "*";

	private CageChartPlotFactory() {
	}

	public static XYPlot buildXYPlot(XYSeriesCollection dataset, NumberAxis xAxis, NumberAxis yAxis) {
		if (dataset == null)
			throw new IllegalArgumentException("XYSeriesCollection cannot be null");
		if (xAxis == null)
			throw new IllegalArgumentException("X-axis cannot be null");
		if (yAxis == null)
			throw new IllegalArgumentException("Y-axis cannot be null");

		if (isLRData(dataset)) {
			return buildXYPlotLR(dataset, xAxis, yAxis);
		}

		XYLineAndShapeRenderer renderer = createRenderer(dataset);
		XYPlot xyPlot = new XYPlot(dataset, xAxis, yAxis, renderer);
		updatePlotBackgroundAccordingToNFlies(dataset, xyPlot);
		return xyPlot;
	}

	private static boolean isLRData(XYSeriesCollection dataset) {
		for (int i = 0; i < dataset.getSeriesCount(); i++) {
			String key = (String) dataset.getSeriesKey(i);
			if (key != null && (key.endsWith("_PI") || key.endsWith("_Sum")))
				return true;
		}
		return false;
	}

	private static XYPlot buildXYPlotLR(XYSeriesCollection dataset, NumberAxis xAxis, NumberAxis yAxis) {
		XYSeriesCollection sumCollection = new XYSeriesCollection();
		XYSeriesCollection piCollection = new XYSeriesCollection();

		for (int i = 0; i < dataset.getSeriesCount(); i++) {
			XYSeries series = dataset.getSeries(i);
			String key = (String) series.getKey();
			if (key != null && key.endsWith("_PI")) {
				piCollection.addSeries(series);
			} else {
				sumCollection.addSeries(series);
			}
		}

		XYLineAndShapeRenderer sumRenderer = createRenderer(sumCollection);
		XYPlot xyPlot = new XYPlot(sumCollection, xAxis, yAxis, sumRenderer);

		XYLineAndShapeRenderer piRenderer = createRenderer(piCollection);
		NumberAxis yAxisPI = new NumberAxis("");
		yAxisPI.setAutoRange(false);
		yAxisPI.setRange(-1.0, 1.0);

		xyPlot.setDataset(1, piCollection);
		xyPlot.setRenderer(1, piRenderer);
		xyPlot.setRangeAxis(1, yAxisPI);
		xyPlot.mapDatasetToRangeAxis(1, 1);

		updatePlotBackgroundAccordingToNFlies(sumCollection, xyPlot);
		return xyPlot;
	}

	private static void updatePlotBackgroundAccordingToNFlies(XYSeriesCollection dataset, XYPlot xyPlot) {
		int nFlies = SeriesStyleCodec.getNFliesOrDefault(dataset, -1);
		setXYPlotBackGroundAccordingToNFlies(xyPlot, nFlies);
	}

	public static void setXYPlotBackGroundAccordingToNFlies(XYPlot xyPlot, int nFlies) {
		if (xyPlot == null)
			return;
		if (nFlies > 0) {
			xyPlot.setBackgroundPaint(BACKGROUND_WITH_DATA);
			xyPlot.setDomainGridlinePaint(GRID_WITH_DATA);
			xyPlot.setRangeGridlinePaint(GRID_WITH_DATA);
		} else if (nFlies == 0) {
			xyPlot.setBackgroundPaint(BACKGROUND_WITHOUT_DATA);
			xyPlot.setDomainGridlinePaint(GRID_WITHOUT_DATA);
			xyPlot.setRangeGridlinePaint(GRID_WITHOUT_DATA);
		} // else {
//			xyPlot.setBackgroundPaint(BACKGROUND_WITH_ARTEFACT);
//			xyPlot.setDomainGridlinePaint(GRID_WITHOUT_DATA);
//			xyPlot.setRangeGridlinePaint(GRID_WITHOUT_DATA);
//		}
	}

	private static XYLineAndShapeRenderer createRenderer(XYSeriesCollection dataset) {
		XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
		SeriesStyleCodec.applySeriesPaintsFromDescription(dataset, renderer);

		Stroke dashedStroke = new BasicStroke(DEFAULT_STROKE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1.0f,
				DASH_PATTERN, DASH_PHASE);
		for (int i = 0; i < dataset.getSeriesCount(); i++) {
			String key = (String) dataset.getSeriesKey(i);
			if (key != null && key.contains(SECONDARY_DATA_TOKEN)) {
				renderer.setSeriesStroke(i, dashedStroke);
			}
		}
		return renderer;
	}
}
