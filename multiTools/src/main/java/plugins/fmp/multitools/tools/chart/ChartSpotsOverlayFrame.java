package plugins.fmp.multitools.tools.chart;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import icy.gui.frame.IcyFrame;
import icy.gui.util.GuiUtil;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotMeasure;
import plugins.fmp.multitools.tools.chart.interaction.SpotOverlayChartInteractionHandler;
import plugins.fmp.multitools.tools.chart.plot.CageChartPlotFactory;
import plugins.fmp.multitools.tools.chart.style.SeriesStyleCodec;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

public class ChartSpotsOverlayFrame {
	public enum OverlayMode {
		SPOTS_SAME_MEASURE, MEASURES_SAME_SPOT
	}

	private IcyFrame mainChartFrame = null;
	private JPanel mainChartPanel = null;
	private ChartPanel chartPanel = null;

	public void createMainChartPanel(String title, ResultsOptions options) {
		if (title == null || title.trim().isEmpty())
			throw new IllegalArgumentException("Title cannot be null or empty");
		if (options == null)
			throw new IllegalArgumentException("ResultsOptions cannot be null");

		mainChartPanel = new JPanel(new BorderLayout());
		String finalTitle = title + ": " + (options.resultType != null ? options.resultType.toString() : "");
		if (mainChartFrame != null && (mainChartFrame.getParent() != null || mainChartFrame.isVisible())) {
			mainChartFrame.setTitle(finalTitle);
			mainChartFrame.removeAll();
		} else {
			mainChartFrame = GuiUtil.generateTitleFrame(finalTitle, new JPanel(), new Dimension(500, 200), true, true,
					true, true);
		}
		mainChartFrame.setLayout(new BorderLayout());
		mainChartFrame.add(new JScrollPane(mainChartPanel), BorderLayout.CENTER);
	}

	public void displayData(Experiment exp, ResultsOptions options, List<Spot> selectedSpots, OverlayMode mode) {
		if (mainChartPanel == null || mainChartFrame == null)
			throw new IllegalStateException("createMainChartPanel must be called first");
		if (exp == null || options == null || selectedSpots == null || selectedSpots.isEmpty() || mode == null)
			return;

		mainChartPanel.removeAll();

		XYSeriesCollection dataset = buildDataset(exp, options, selectedSpots, mode);

		NumberAxis xAxis = new NumberAxis("time (min)");
		xAxis.setAutoRangeIncludesZero(false);

		String yLabel = options.resultType != null ? options.resultType.toUnit() : "";
		NumberAxis yAxis = new NumberAxis(yLabel);
		yAxis.setAutoRangeIncludesZero(false);

		XYPlot plot = CageChartPlotFactory.buildXYPlot(dataset, xAxis, yAxis);
		JFreeChart chart = new JFreeChart(plot);

		chartPanel = new ChartPanel(chart, 900, 500, 300, 200, 2000, 2000, true, true, true, true, false, true);
		chartPanel.addChartMouseListener(new SpotOverlayChartInteractionHandler(exp, options).createMouseListener());

		mainChartPanel.add(chartPanel, BorderLayout.CENTER);

		mainChartFrame.pack();
		if (mainChartFrame.getParent() == null) {
			mainChartFrame.addToDesktopPane();
		}
		mainChartFrame.setVisible(true);
		mainChartFrame.toFront();
		mainChartFrame.requestFocus();
	}

	public void setChartUpperLeftLocation(Rectangle rect) {
		if (rect == null)
			return;
		if (mainChartFrame != null) {
			mainChartFrame.setLocation(rect.getLocation());
		}
	}

	public IcyFrame getMainChartFrame() {
		return mainChartFrame;
	}

	public void dispose() {
		if (mainChartFrame != null) {
			mainChartFrame.dispose();
		}
		mainChartFrame = null;
		mainChartPanel = null;
		chartPanel = null;
	}

	private static XYSeriesCollection buildDataset(Experiment exp, ResultsOptions options, List<Spot> selectedSpots,
			OverlayMode mode) {
		switch (mode) {
		case SPOTS_SAME_MEASURE:
			return buildDatasetOverlaySpots(exp, options, selectedSpots);
		case MEASURES_SAME_SPOT:
			return buildDatasetOverlayMeasures(exp, options, selectedSpots.get(0));
		default:
			return new XYSeriesCollection();
		}
	}

	private static XYSeriesCollection buildDatasetOverlaySpots(Experiment exp, ResultsOptions options,
			List<Spot> selectedSpots) {
		XYSeriesCollection dataset = new XYSeriesCollection();
		for (Spot spot : selectedSpots) {
			XYSeries series = createXYSeriesFromSpotMeasure(exp, spot, options, options.resultType, spot.getName());
			if (series == null)
				continue;
			applySpotStyle(exp, spot, series, spot.getProperties() != null ? spot.getProperties().getColor() : null);
			dataset.addSeries(series);
		}
		return dataset;
	}

	private static XYSeriesCollection buildDatasetOverlayMeasures(Experiment exp, ResultsOptions baseOptions, Spot spot) {
		XYSeriesCollection dataset = new XYSeriesCollection();
		if (spot == null)
			return dataset;

		EnumResults[] measures = new EnumResults[] { EnumResults.AREA_SUM, EnumResults.AREA_SUMNOFLY,
				EnumResults.AREA_SUMCLEAN, EnumResults.AREA_FLYPRESENT };
		Color[] colors = new Color[] { new Color(0, 0, 0), new Color(0, 102, 204), new Color(0, 153, 0),
				new Color(153, 0, 153) };

		for (int i = 0; i < measures.length; i++) {
			EnumResults resultType = measures[i];
			XYSeries series = createXYSeriesFromSpotMeasure(exp, spot, baseOptions, resultType,
					spot.getName() + "::" + resultType.name());
			if (series == null)
				continue;
			applySpotStyle(exp, spot, series, colors[i % colors.length]);
			dataset.addSeries(series);
		}
		return dataset;
	}

	private static void applySpotStyle(Experiment exp, Spot spot, XYSeries series, Color color) {
		if (exp == null || exp.getCages() == null || exp.getSpots() == null || spot == null || series == null)
			return;
		Cage cage = exp.getCages().getCageFromSpotROIName(spot.getName(), exp.getSpots());
		if (cage == null || cage.getProperties() == null || spot.getProperties() == null)
			return;
		series.setDescription(SeriesStyleCodec.buildDescription(cage.getProperties().getCageID(),
				cage.getProperties().getCageID(), cage.getProperties().getCageNFlies(), color));
	}

	private static XYSeries createXYSeriesFromSpotMeasure(Experiment exp, Spot spot, ResultsOptions baseOptions,
			EnumResults resultType, String seriesKey) {
		if (exp == null || spot == null || baseOptions == null || resultType == null || seriesKey == null)
			return null;

		XYSeries seriesXY = new XYSeries(seriesKey, false);

		if (exp.getSeqCamData().getTimeManager().getCamImagesTime_Ms() == null)
			exp.getSeqCamData().build_MsTimesArray_From_FileNamesList();
		double[] camImages_time_min = exp.getSeqCamData().getTimeManager().getCamImagesTime_Minutes();

		SpotMeasure spotMeasure = spot.getMeasurements(resultType);
		if (spotMeasure == null)
			return null;

		double divider = 1.0;
		if (baseOptions.relativeToMaximum && resultType != EnumResults.AREA_FLYPRESENT) {
			divider = spotMeasure.getMaximumValue();
			if (divider == 0)
				divider = 1.0;
		}

		int npoints = spotMeasure.getCount();
		if (camImages_time_min != null && npoints > camImages_time_min.length)
			npoints = camImages_time_min.length;

		for (int j = 0; j < npoints; j++) {
			double x = camImages_time_min != null ? camImages_time_min[j] : j;
			double y = spotMeasure.getValueAt(j) / divider;
			seriesXY.add(x, y);
		}
		return seriesXY;
	}

	public static List<Spot> dedupeSpots(List<Spot> spots) {
		if (spots == null || spots.isEmpty())
			return Collections.emptyList();
		List<Spot> out = new ArrayList<>();
		for (Spot s : spots) {
			if (s == null)
				continue;
			boolean seen = false;
			for (Spot o : out) {
				if (o != null && o.getName() != null && o.getName().equals(s.getName())) {
					seen = true;
					break;
				}
			}
			if (!seen)
				out.add(s);
		}
		return out;
	}
}

