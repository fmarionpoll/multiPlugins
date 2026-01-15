package plugins.fmp.multicafe.dlg.levels;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.Range;
import org.jfree.data.xy.XYSeriesCollection;

import icy.gui.frame.IcyFrame;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.chart.ChartCagePair;

public class AxisOptions extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	IcyFrame dialogFrame = null;
	private MultiCAFE parent0 = null;
	private ChartCageArrayFrame chartCageArrayFrame = null;
	private JSpinner lowerXSpinner = new JSpinner(new SpinnerNumberModel(0., -255., 255., 1.));
	private JSpinner upperXSpinner = new JSpinner(new SpinnerNumberModel(120., -255., 255., 1.));

	// Dynamic y-axis components
	private List<JSpinner> lowerYSpinners = new ArrayList<>();
	private List<JSpinner> upperYSpinners = new ArrayList<>();
	private List<JButton> setYaxisButtons = new ArrayList<>();
	private List<String> yAxisLabels = new ArrayList<>();

	private JButton setXaxis = new JButton("set X axis values");

	public void initialize(MultiCAFE parent0, ChartCageArrayFrame chartSpots) {
		this.parent0 = parent0;
		this.chartCageArrayFrame = chartSpots;

		// Detect axes first to determine UI layout
		int maxYAxisCount = detectMaxYAxisCount();
		detectYAxisPurposes(maxYAxisCount);

		// Calculate grid layout: 1 row for X-axis + N rows for Y-axes
		JPanel topPanel = new JPanel(new GridLayout(1 + maxYAxisCount, 1));
		FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);

		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(new JLabel("x axis values:"));
		panel1.add(lowerXSpinner);
		panel1.add(upperXSpinner);
		panel1.add(setXaxis);
		topPanel.add(panel1);

		// Dynamically create panels for each y-axis
		for (int axisIndex = 0; axisIndex < maxYAxisCount; axisIndex++) {
			JPanel yAxisPanel = createYAxisPanel(axisIndex);
			topPanel.add(yAxisPanel);
		}

		dialogFrame = new IcyFrame("Chart options", true, true);
		dialogFrame.add(topPanel, BorderLayout.NORTH);

		dialogFrame.pack();
		dialogFrame.addToDesktopPane();
		dialogFrame.requestFocus();
		dialogFrame.center();
		dialogFrame.setVisible(true);

		collectValuesFromAllCharts();
		defineActionListeners();
	}

	public void close() {
		if (dialogFrame != null) {
			dialogFrame.close();
		}
		if (parent0 != null) {
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null) {
				exp.saveSpotsArray_file();
			}
		}
	}

	private void defineActionListeners() {
		setXaxis.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					updateXAxis();
				}
			}
		});

		// Add action listeners for each y-axis button
		for (int i = 0; i < setYaxisButtons.size(); i++) {
			final int axisIndex = i;
			setYaxisButtons.get(i).addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(final ActionEvent e) {
					Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
					if (exp != null) {
						updateYAxis(axisIndex);
					}
				}
			});
		}
	}

	private void collectValuesFromAllCharts() {
		ChartCagePair[][] chartPanelArray = chartCageArrayFrame.getChartCagePairArray();
		if (chartPanelArray == null || chartPanelArray.length == 0 || chartPanelArray[0].length == 0) {
			return;
		}
		int nrows = chartPanelArray.length;
		int ncolumns = chartPanelArray[0].length;
		chartCageArrayFrame.setXRange(null);
		chartCageArrayFrame.setYRange(null);

		// Collect X-axis ranges
		for (int column = 0; column < ncolumns; column++) {
			for (int row = 0; row < nrows; row++) {
				ChartCagePair chartPair = chartPanelArray[row][column];
				if (chartPair == null)
					continue;
				ChartPanel chartPanel = chartPair.getChartPanel();
				if (chartPanel == null)
					continue;
				XYPlot plot = (XYPlot) chartPanel.getChart().getPlot();
				if (plot == null)
					continue;
				ValueAxis xAxis = plot.getDomainAxis();

				if (xAxis != null)
					chartCageArrayFrame.setXRange(Range.combine(chartCageArrayFrame.getXRange(), xAxis.getRange()));
			}
		}

		// Collect Y-axis ranges for each axis index
		int maxYAxisCount = lowerYSpinners.size();
		List<Range> yAxisRanges = new ArrayList<>();
		for (int axisIndex = 0; axisIndex < maxYAxisCount; axisIndex++) {
			yAxisRanges.add(null);
		}

		for (int column = 0; column < ncolumns; column++) {
			for (int row = 0; row < nrows; row++) {
				ChartCagePair chartPair = chartPanelArray[row][column];
				if (chartPair == null)
					continue;
				ChartPanel chartPanel = chartPair.getChartPanel();
				if (chartPanel == null)
					continue;
				XYPlot plot = (XYPlot) chartPanel.getChart().getPlot();
				if (plot == null)
					continue;

				int rangeAxisCount = plot.getRangeAxisCount();
				for (int axisIndex = 0; axisIndex < rangeAxisCount && axisIndex < maxYAxisCount; axisIndex++) {
					ValueAxis yAxis = plot.getRangeAxis(axisIndex);
					if (yAxis != null) {
						Range axisRange = yAxis.getRange();
						yAxisRanges.set(axisIndex, Range.combine(yAxisRanges.get(axisIndex), axisRange));
					}
				}
			}
		}

		// Update spinners
		if (chartCageArrayFrame.getXRange() != null) {
			lowerXSpinner.setValue(chartCageArrayFrame.getXRange().getLowerBound());
			upperXSpinner.setValue(chartCageArrayFrame.getXRange().getUpperBound());
		}

		for (int axisIndex = 0; axisIndex < maxYAxisCount; axisIndex++) {
			Range yRange = yAxisRanges.get(axisIndex);
			if (yRange != null && axisIndex < lowerYSpinners.size()) {
				lowerYSpinners.get(axisIndex).setValue(yRange.getLowerBound());
				upperYSpinners.get(axisIndex).setValue(yRange.getUpperBound());
			}
		}
	}

	private void updateXAxis() {
		ChartCagePair[][] chartPanelArray = chartCageArrayFrame.getChartCagePairArray();
		if (chartPanelArray == null || chartPanelArray.length == 0 || chartPanelArray[0].length == 0) {
			return;
		}
		int nrows = chartPanelArray.length;
		int ncolumns = chartPanelArray[0].length;

		double upper = (double) upperXSpinner.getValue();
		double lower = (double) lowerXSpinner.getValue();
		for (int column = 0; column < ncolumns; column++) {
			for (int row = 0; row < nrows; row++) {
				ChartCagePair chartPair = chartPanelArray[row][column];
				if (chartPair == null)
					continue;
				ChartPanel chartPanel = chartPair.getChartPanel();
				if (chartPanel == null)
					continue;
				XYPlot xyPlot = (XYPlot) chartPanel.getChart().getPlot();
				NumberAxis xAxis = (NumberAxis) xyPlot.getDomainAxis();
				xAxis.setAutoRange(false);
				xAxis.setRange(lower, upper);
			}
		}
	}

	private void updateYAxis(int axisIndex) {
		if (axisIndex < 0 || axisIndex >= lowerYSpinners.size() || axisIndex >= upperYSpinners.size()) {
			return;
		}

		ChartCagePair[][] chartPanelArray = chartCageArrayFrame.getChartCagePairArray();
		if (chartPanelArray == null || chartPanelArray.length == 0 || chartPanelArray[0].length == 0) {
			return;
		}
		int nrows = chartPanelArray.length;
		int ncolumns = chartPanelArray[0].length;

		double upper = (double) upperYSpinners.get(axisIndex).getValue();
		double lower = (double) lowerYSpinners.get(axisIndex).getValue();

		for (int column = 0; column < ncolumns; column++) {
			for (int row = 0; row < nrows; row++) {
				ChartCagePair chartPair = chartPanelArray[row][column];
				if (chartPair == null)
					continue;
				ChartPanel chartPanel = chartPair.getChartPanel();
				if (chartPanel == null)
					continue;
				XYPlot xyPlot = (XYPlot) chartPanel.getChart().getPlot();
				if (xyPlot == null)
					continue;

				int rangeAxisCount = xyPlot.getRangeAxisCount();
				if (axisIndex < rangeAxisCount) {
					ValueAxis yAxis = xyPlot.getRangeAxis(axisIndex);
					if (yAxis instanceof NumberAxis) {
						NumberAxis numberAxis = (NumberAxis) yAxis;
						numberAxis.setAutoRange(false);
						numberAxis.setRange(lower, upper);
					}
				}
			}
		}
	}

	/**
	 * Detects the maximum number of y-axes across all charts.
	 * 
	 * @return the maximum range axis count found
	 */
	private int detectMaxYAxisCount() {
		int maxCount = 1; // Default to at least 1 axis
		ChartCagePair[][] chartPanelArray = chartCageArrayFrame.getChartCagePairArray();
		if (chartPanelArray == null || chartPanelArray.length == 0 || chartPanelArray[0].length == 0) {
			return maxCount;
		}
		int nrows = chartPanelArray.length;
		int ncolumns = chartPanelArray[0].length;

		for (int column = 0; column < ncolumns; column++) {
			for (int row = 0; row < nrows; row++) {
				ChartCagePair chartPair = chartPanelArray[row][column];
				if (chartPair == null)
					continue;
				ChartPanel chartPanel = chartPair.getChartPanel();
				if (chartPanel == null)
					continue;
				XYPlot plot = (XYPlot) chartPanel.getChart().getPlot();
				if (plot == null)
					continue;

				int rangeAxisCount = plot.getRangeAxisCount();
				if (rangeAxisCount > maxCount) {
					maxCount = rangeAxisCount;
				}
			}
		}
		return maxCount;
	}

	/**
	 * Detects the purpose of each y-axis (Sum vs PI) and stores labels.
	 * 
	 * @param maxYAxisCount the maximum number of y-axes to check
	 */
	private void detectYAxisPurposes(int maxYAxisCount) {
		// Clear existing lists
		lowerYSpinners.clear();
		upperYSpinners.clear();
		setYaxisButtons.clear();
		yAxisLabels.clear();

		for (int axisIndex = 0; axisIndex < maxYAxisCount; axisIndex++) {
			String axisLabel = getAxisLabel(axisIndex);
			yAxisLabels.add(axisLabel);

			// Create spinners with reasonable defaults
			JSpinner lowerSpinner = new JSpinner(new SpinnerNumberModel(0., -255., 255., 1.));
			JSpinner upperSpinner = new JSpinner(new SpinnerNumberModel(80., -255., 255., 1.));
			JButton setButton = new JButton("set " + axisLabel + " values");

			lowerYSpinners.add(lowerSpinner);
			upperYSpinners.add(upperSpinner);
			setYaxisButtons.add(setButton);
		}
	}

	/**
	 * Gets the label for a y-axis based on its index.
	 * 
	 * @param axisIndex the index of the axis
	 * @return a descriptive label like "Y axis (Sum)" or "Y axis (PI)"
	 */
	private String getAxisLabel(int axisIndex) {
		// Check a sample chart to determine axis purpose
		ChartCagePair[][] chartPanelArray = chartCageArrayFrame.getChartCagePairArray();
		if (chartPanelArray == null || chartPanelArray.length == 0 || chartPanelArray[0].length == 0) {
			return "Y axis " + (axisIndex + 1);
		}
		int nrows = chartPanelArray.length;
		int ncolumns = chartPanelArray[0].length;

		for (int column = 0; column < ncolumns; column++) {
			for (int row = 0; row < nrows; row++) {
				ChartCagePair chartPair = chartPanelArray[row][column];
				if (chartPair == null)
					continue;
				ChartPanel chartPanel = chartPair.getChartPanel();
				if (chartPanel == null)
					continue;
				XYPlot plot = (XYPlot) chartPanel.getChart().getPlot();
				if (plot == null)
					continue;

				String purpose = getAxisPurpose(plot, axisIndex);
				if (purpose != null) {
					return "Y axis (" + purpose + ")";
				}
			}
		}

		// Default labels if we can't determine purpose
		if (axisIndex == 0) {
			return "Y axis (Sum)";
		} else if (axisIndex == 1) {
			return "Y axis (PI)";
		} else {
			return "Y axis " + (axisIndex + 1);
		}
	}

	/**
	 * Determines the purpose of a y-axis (Sum or PI) by checking dataset mappings.
	 * Based on CageChartPlotFactory, dataset 0 (Sum) maps to axis 0, dataset 1 (PI)
	 * maps to axis 1.
	 * 
	 * @param plot      the XYPlot to analyze
	 * @param axisIndex the index of the range axis
	 * @return "Sum", "PI", or null if unknown
	 */
	private String getAxisPurpose(XYPlot plot, int axisIndex) {
		if (plot == null)
			return null;

		// Based on CageChartPlotFactory.buildXYPlotLR:
		// - Dataset 0 contains Sum data and is mapped to range axis 0
		// - Dataset 1 contains PI data and is mapped to range axis 1
		// So we can check dataset 1 to see if it has PI series
		if (axisIndex == 1 && plot.getDatasetCount() > 1) {
			org.jfree.data.xy.XYDataset dataset = plot.getDataset(1);
			if (dataset instanceof XYSeriesCollection) {
				XYSeriesCollection collection = (XYSeriesCollection) dataset;
				for (int seriesIndex = 0; seriesIndex < collection.getSeriesCount(); seriesIndex++) {
					Object key = collection.getSeriesKey(seriesIndex);
					if (key instanceof String) {
						String keyStr = (String) key;
						if (keyStr.endsWith("_PI")) {
							return "PI";
						}
					}
				}
			}
		}

		// Check dataset 0 for Sum series (or default to Sum for axis 0)
		if (axisIndex == 0) {
			if (plot.getDatasetCount() > 0) {
				org.jfree.data.xy.XYDataset dataset = plot.getDataset(0);
				if (dataset instanceof XYSeriesCollection) {
					XYSeriesCollection collection = (XYSeriesCollection) dataset;
					for (int seriesIndex = 0; seriesIndex < collection.getSeriesCount(); seriesIndex++) {
						Object key = collection.getSeriesKey(seriesIndex);
						if (key instanceof String) {
							String keyStr = (String) key;
							if (keyStr.endsWith("_Sum") || keyStr.contains("Sum")) {
								return "Sum";
							}
						}
					}
				}
			}
			// Default to Sum for axis 0
			return "Sum";
		}

		return null;
	}

	/**
	 * Creates a panel for a y-axis with label, spinners, and button.
	 * 
	 * @param axisIndex the index of the axis
	 * @return a JPanel containing the y-axis controls
	 */
	private JPanel createYAxisPanel(int axisIndex) {
		FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);
		JPanel panel = new JPanel(flowLayout);

		if (axisIndex < yAxisLabels.size()) {
			panel.add(new JLabel(yAxisLabels.get(axisIndex) + ":"));
		} else {
			panel.add(new JLabel("y axis values:"));
		}

		if (axisIndex < lowerYSpinners.size()) {
			panel.add(lowerYSpinners.get(axisIndex));
		}
		if (axisIndex < upperYSpinners.size()) {
			panel.add(upperYSpinners.get(axisIndex));
		}
		if (axisIndex < setYaxisButtons.size()) {
			panel.add(setYaxisButtons.get(axisIndex));
		}

		return panel;
	}

}
