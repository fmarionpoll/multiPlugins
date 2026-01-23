package plugins.fmp.multicafe.dlg.levels;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CombinedRangeXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeriesCollection;

import icy.gui.frame.IcyFrame;
import icy.gui.util.GuiUtil;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.tools.chart.plot.CageChartPlotFactory;
import plugins.fmp.multitools.tools.chart.style.SeriesStyleCodec;
import plugins.fmp.multitools.tools.chart.builders.CageCapillarySeriesBuilder;
import plugins.fmp.multitools.tools.chart.builders.CageSeriesBuilder;
import plugins.fmp.multitools.tools.chart.builders.CageSpotSeriesBuilder;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

/**
 * Displays all cages in a single chart using a {@link CombinedRangeXYPlot}.
 *
 * Each cage contributes one subplot (stacked vertically) sharing a common Y axis.
 * This is an alternative to the grid view implemented by {@link ChartCageArrayFrame}.
 */
public class ChartCombinedFrame {
	private IcyFrame mainChartFrame = null;
	private JPanel mainChartPanel = null;
	private ChartPanel chartPanel = null;

	public void createMainChartPanel(String title, Experiment exp, ResultsOptions options) {
		if (title == null || title.trim().isEmpty())
			throw new IllegalArgumentException("Title cannot be null or empty");
		if (exp == null)
			throw new IllegalArgumentException("Experiment cannot be null");
		if (options == null)
			throw new IllegalArgumentException("ResultsOptions cannot be null");

		mainChartPanel = new JPanel(new BorderLayout());

		String finalTitle = title + ": " + options.resultType;
		if (mainChartFrame != null && (mainChartFrame.getParent() != null || mainChartFrame.isVisible())) {
			mainChartFrame.setTitle(finalTitle);
			mainChartFrame.removeAll();
		} else {
			mainChartFrame = GuiUtil.generateTitleFrame(finalTitle, new JPanel(), new Dimension(300, 70), true, true,
					true, true);
		}
		mainChartFrame.setLayout(new BorderLayout());

		mainChartFrame.add(new JScrollPane(mainChartPanel), BorderLayout.CENTER);
	}

	public void displayData(Experiment exp, ResultsOptions options) {
		if (mainChartPanel == null || mainChartFrame == null)
			throw new IllegalStateException("createMainChartPanel must be called first");

		mainChartPanel.removeAll();

		NumberAxis sharedYAxis = new NumberAxis(options.resultType != null ? options.resultType.toUnit() : "");
		sharedYAxis.setAutoRangeIncludesZero(false);

		CombinedRangeXYPlot combined = new CombinedRangeXYPlot(sharedYAxis);

		CageSeriesBuilder builder = selectDataBuilder(options.resultType);
		for (Cage cage : filterCages(exp, options)) {
			XYSeriesCollection dataset = builder.build(exp, cage, options);
			if (dataset == null || dataset.getSeriesCount() == 0)
				continue;

			int cageId = cage.getProperties() != null ? cage.getProperties().getCageID() : -1;
			NumberAxis xAxis = new NumberAxis("cage " + cageId);
			xAxis.setAutoRangeIncludesZero(false);

			// Build subplot using the same styling logic as grid charts
			NumberAxis dummyYAxis = new NumberAxis();
			dummyYAxis.setAutoRangeIncludesZero(false);
			XYPlot subplot = CageChartPlotFactory.buildXYPlot(dataset, xAxis, dummyYAxis);
			subplot.setRangeAxis(null); // range axis handled by CombinedRangeXYPlot

			// Keep background behavior aligned with existing charts
			int nFlies = SeriesStyleCodec.getNFliesOrDefault(dataset, -1);
			CageChartPlotFactory.setXYPlotBackGroundAccordingToNFlies(subplot, nFlies);

			combined.add(subplot, 1);
		}

		JFreeChart chart = new JFreeChart(combined);

		chartPanel = new ChartPanel(chart, 900, 500, 300, 200, 2000, 2000, true, true, true, true, false, true);
		mainChartPanel.add(chartPanel, BorderLayout.CENTER);

		mainChartFrame.pack();
		mainChartFrame.addToDesktopPane();
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

	private CageSeriesBuilder selectDataBuilder(EnumResults resultType) {
		if (isSpotResultType(resultType)) {
			return new CageSpotSeriesBuilder();
		}
		return new CageCapillarySeriesBuilder();
	}

	private boolean isSpotResultType(EnumResults resultType) {
		if (resultType == null)
			return false;
		switch (resultType) {
		case AREA_SUM:
		case AREA_SUMCLEAN:
		case AREA_OUT:
		case AREA_DIFF:
		case AREA_FLYPRESENT:
			return true;
		default:
			return false;
		}
	}

	private List<Cage> filterCages(Experiment exp, ResultsOptions options) {
		List<Cage> cages = exp.getCages() != null ? exp.getCages().getCageList() : null;
		if (cages == null)
			return List.of();

		boolean singleCageMode = options.cageIndexFirst == options.cageIndexLast && options.cageIndexFirst >= 0;
		if (!singleCageMode)
			return cages;

		List<Cage> out = new ArrayList<>();
		for (Cage cage : cages) {
			if (cage == null || cage.getProperties() == null)
				continue;
			if (cage.getProperties().getCageID() == options.cageIndexFirst) {
				out.add(cage);
				break;
			}
		}
		return out;
	}
}

