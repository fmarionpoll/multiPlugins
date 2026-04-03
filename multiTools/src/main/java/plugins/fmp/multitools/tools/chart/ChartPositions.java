package plugins.fmp.multitools.tools.chart;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Ellipse2D;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JPanel;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import icy.gui.frame.IcyFrame;
import icy.gui.util.GuiUtil;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.tools.MaxMinDouble;
import plugins.fmp.multitools.tools.chart.builders.CageFlyPositionSeriesBuilder;
import plugins.fmp.multitools.tools.chart.builders.CageSeriesBuilder;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.results.ResultsOptionsBuilder;

/**
 * Chart display class for fly position data. Displays multiple cages horizontally
 * in a single row. This class uses CageFlyPositionSeriesBuilder for data building,
 * providing a unified data extraction interface while maintaining the existing
 * horizontal layout display format.
 * 
 * <p>
 * This class is kept for backward compatibility with ChartPositionsPanel.
 * The data building logic has been extracted to CageFlyPositionSeriesBuilder,
 * allowing for code reuse while maintaining the existing API.
 * </p>
 * 
 * <p>
 * Future migration could extend CageChartArrayFrame to support List&lt;Cage&gt; mode
 * instead of requiring an Experiment with grid layout, enabling full framework integration.
 * </p>
 */
public class ChartPositions extends IcyFrame {
	public JPanel mainChartPanel = null;
	private ArrayList<ChartPanel> chartsInMainChartPanel = null;
	public IcyFrame mainChartFrame = null;
	private String title;
	private Point pt = new Point(0, 0);
	private double globalXMax = 0;
	
	// Remember last screen location for fly-position graphs across experiments.
	private static Point globalUpperLeftLocation = null;
	
	/** Builder for creating fly position datasets */
	private final CageSeriesBuilder dataBuilder = new CageFlyPositionSeriesBuilder();

	public void createPanel(String cstitle) {
		title = cstitle;
		mainChartFrame = GuiUtil.generateTitleFrame(title, new JPanel(), new Dimension(300, 70), true, true, true,
				true);
		mainChartPanel = new JPanel();
		mainChartPanel.setLayout(new BoxLayout(mainChartPanel, BoxLayout.LINE_AXIS));
		mainChartFrame.add(mainChartPanel);
		
		// Track user moves of the chart window so we remember the last location across
		// experiments. This listener updates the shared location whenever the frame is
		// moved (e.g. drag by the user).
		mainChartFrame.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentMoved(ComponentEvent e) {
				globalUpperLeftLocation = mainChartFrame.getLocation();
			}
		});
	}

	public void setLocationRelativeToRectangle(Rectangle rectv, Point deltapt) {
		// If a previous ChartPositions window was moved by the user, reuse its location
		// so graphs appear where the user left them. Otherwise, default relative to
		// the camera viewer rectangle.
		if (globalUpperLeftLocation != null) {
			pt = new Point(globalUpperLeftLocation);
		} else if (rectv != null && deltapt != null) {
			pt = new Point(rectv.x + deltapt.x, rectv.y + deltapt.y);
			globalUpperLeftLocation = new Point(pt);
		}
	}

	public void displayData(List<Cage> cageList, EnumResults resultType) {
		if (cageList == null || resultType == null) {
			return;
		}
		
		// Create a dummy experiment for the builder (it may not need it, but builder interface requires it)
		// In practice, we'll build datasets directly for each cage
		List<XYSeriesCollection> xyDataSetList = new ArrayList<XYSeriesCollection>();
		MaxMinDouble yMaxMin = new MaxMinDouble();
		int count = 0;
		
		// Create results options for the builder
		ResultsOptions options = ResultsOptionsBuilder.forChart()
			.withResultType(resultType)
			.build();
		
		// Determine multi-fly mode (pseudo identity).
		boolean multiFly = false;
		for (Cage cage : cageList) {
			if (cage != null && cage.getFlyPositions() != null && cage.getFlyPositions().getNflies() > 1) {
				multiFly = true;
				break;
			}
		}

		boolean scatterOnly = multiFly && isPositionResultType(resultType);

		// Use the builder to create datasets for each cage
		for (Cage cage : cageList) {
			if (cage == null) {
				continue;
			}
			
			// Check if cage has fly positions data
			if (cage.getFlyPositions() != null && cage.getFlyPositions().getFlyPositionList().size() > 0) {
				// Create a dummy experiment - builder may not use it for fly positions
				// We need to pass something, so create minimal experiment
				Experiment dummyExp = createDummyExperiment(cage);
				
				XYSeriesCollection xyDataset = dataBuilder.build(dummyExp, cage, options);
				
				if (xyDataset != null && xyDataset.getSeriesCount() > 0) {
					// Calculate Y range from the dataset
					MaxMinDouble cageYMaxMin = calculateYRange(xyDataset);
					if (count == 0) {
						yMaxMin = cageYMaxMin;
					} else {
						yMaxMin.getMaxMin(cageYMaxMin);
					}
					
					// Calculate X max
					updateGlobalXMax(xyDataset);
					
					xyDataSetList.add(xyDataset);
					count++;
				}
			}
		}

		cleanChartsPanel(chartsInMainChartPanel);
		int width = 100;
		boolean displayLabels = false;

		if (xyDataSetList.isEmpty()) {
			return;
		}

		for (XYSeriesCollection xyDataset : xyDataSetList) {
			JFreeChart xyChart = ChartFactory.createXYLineChart(null, null, null, xyDataset, PlotOrientation.VERTICAL,
					true, true, true);
			xyChart.setAntiAlias(true);
			xyChart.setTextAntiAlias(true);

			if (scatterOnly) {
				XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(false, true);
				renderer.setDefaultShape(new Ellipse2D.Double(-2.0, -2.0, 4.0, 4.0));
				xyChart.getXYPlot().setRenderer(renderer);
			}

			ValueAxis yAxis = xyChart.getXYPlot().getRangeAxis(0);
			if (yMaxMin.hasValues()) {
				double min = yMaxMin.getMin();
				double max = yMaxMin.getMax();
				if (max > min) {
					yAxis.setRange(min, max);
				} else {
					double delta = (min != 0) ? Math.abs(min) * 0.1 : 1.0;
					yAxis.setRange(min - delta, min + delta);
				}
			}
			yAxis.setTickLabelsVisible(displayLabels);

			ValueAxis xAxis = xyChart.getXYPlot().getDomainAxis(0);
			double xMax = globalXMax;
			if (xMax <= 0) {
				xMax = 1.0;
			}
			xAxis.setRange(0, xMax);

			ChartPanel xyChartPanel = new ChartPanel(xyChart, width, 200, 50, 100, 100, 200, false, false, true, true,
					true, true);
			mainChartPanel.add(xyChartPanel);
			width = 100;
			displayLabels = false;
		}

		mainChartFrame.pack();
		if (pt != null) {
			mainChartFrame.setLocation(pt);
			// Store actual screen location so subsequent charts reuse it even if the user
			// moved the window.
			globalUpperLeftLocation = mainChartFrame.getLocation();
		}
		mainChartFrame.addToDesktopPane();
		mainChartFrame.setVisible(true);
	}

	private boolean isPositionResultType(EnumResults resultType) {
		switch (resultType) {
		case XYIMAGE:
		case YVSCAGETOP:
		case YVSCAGEBOTTOM:
		case XTOPCAGE:
		case YTOPCAGE:
		case YVSTIPCAPS:
		case ELLIPSEAXES:
			return true;
		default:
			return false;
		}
	}
	
	/**
	 * Creates a minimal dummy experiment for the builder.
	 * The builder doesn't actually need the experiment for fly positions, but the interface requires it.
	 */
	private Experiment createDummyExperiment(Cage cage) {
		// The builder doesn't use the experiment parameter for fly positions
		// Return null - builder should handle this gracefully
		return null;
	}
	
	/**
	 * Calculates the Y-axis range from a dataset.
	 */
	private MaxMinDouble calculateYRange(XYSeriesCollection dataset) {
		MaxMinDouble range = new MaxMinDouble();
		for (int i = 0; i < dataset.getSeriesCount(); i++) {
			XYSeries series = dataset.getSeries(i);
			for (int j = 0; j < series.getItemCount(); j++) {
				double y = series.getY(j).doubleValue();
				if (!Double.isNaN(y)) {
					range.getMaxMin(y);
				}
			}
		}
		return range;
	}
	
	/**
	 * Updates the global X maximum from a dataset.
	 */
	private void updateGlobalXMax(XYSeriesCollection dataset) {
		for (int i = 0; i < dataset.getSeriesCount(); i++) {
			XYSeries series = dataset.getSeries(i);
			for (int j = 0; j < series.getItemCount(); j++) {
				double x = series.getX(j).doubleValue();
				if (!Double.isNaN(x) && x > globalXMax) {
					globalXMax = x;
				}
			}
		}
	}

	private void cleanChartsPanel(ArrayList<ChartPanel> chartsPanel) {
		if (chartsPanel != null && chartsPanel.size() > 0)
			chartsPanel.clear();
	}
}
