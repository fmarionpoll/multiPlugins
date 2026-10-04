package plugins.fmp.multiSPOTS.dlg.measure_imageFilters;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.function.Supplier;

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

import icy.gui.frame.IcyFrame;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.chart.ChartCagePair;
import plugins.fmp.multitools.tools.chart.ChartCagesFrame;

public class AxisOptions extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Wide bounds so measures like {@code GREY_SUM_V5} (large sums) stay within the
	 * {@link SpinnerNumberModel}; otherwise the editor becomes invalid and appears disabled.
	 */
	private static final double AXIS_SPINNER_MIN = -1e15;
	private static final double AXIS_SPINNER_MAX = 1e15;

	IcyFrame dialogFrame = null;
	private MultiSPOTS parent0 = null;
	private ChartCagesFrame chartCagesFrame = null;
	private Supplier<ChartPanel> singleChartPanel = null;
	private JSpinner lowerXSpinner = new JSpinner(new SpinnerNumberModel(0., AXIS_SPINNER_MIN, AXIS_SPINNER_MAX, 0.1));
	private JSpinner upperXSpinner = new JSpinner(
			new SpinnerNumberModel(120., AXIS_SPINNER_MIN, AXIS_SPINNER_MAX, 0.1));
	private JSpinner lowerYSpinner = new JSpinner(new SpinnerNumberModel(0., AXIS_SPINNER_MIN, AXIS_SPINNER_MAX, 1.));
	private JSpinner upperYSpinner = new JSpinner(
			new SpinnerNumberModel(1000., AXIS_SPINNER_MIN, AXIS_SPINNER_MAX, 1.));
	private JButton setYaxis = new JButton("set Y axis values");
	private JButton setXaxis = new JButton("set X axis values");

	public void initialize(MultiSPOTS parent0, ChartCagesFrame chartSpots) {
		this.parent0 = parent0;
		this.chartCagesFrame = chartSpots;
		this.singleChartPanel = null;
		buildDialog();
	}

	public void initialize(MultiSPOTS parent0, Supplier<ChartPanel> chartPanel) {
		this.parent0 = parent0;
		this.chartCagesFrame = null;
		this.singleChartPanel = chartPanel;
		buildDialog();
	}

	private void buildDialog() {
		JPanel topPanel = new JPanel(new GridLayout(2, 1));
		FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);

		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(new JLabel("x axis values:"));
		panel1.add(lowerXSpinner);
		panel1.add(upperXSpinner);
		panel1.add(setXaxis);
		topPanel.add(panel1);

		JPanel panel2 = new JPanel(flowLayout);
		panel2.add(new JLabel("y axis values:"));
		panel2.add(lowerYSpinner);
		panel2.add(upperYSpinner);
		panel2.add(setYaxis);
		topPanel.add(panel2);

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
		dialogFrame.close();
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null) {
			exp.saveSpots_File();
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

		setYaxis.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					updateYAxis();
				}
			}
		});
	}

	private void collectValuesFromAllCharts() {
		if (chartCagesFrame != null) {
			collectValuesFromChartCagesFrame();
		} else if (singleChartPanel != null) {
			ChartPanel chartPanel = singleChartPanel.get();
			applyCollectedRange(rangeOf(chartPanel, true), rangeOf(chartPanel, false));
		}
	}

	private void collectValuesFromChartCagesFrame() {
		ChartCagePair[][] chartPanelArray = chartCagesFrame.getChartCagePairArray();
		if (chartPanelArray == null || chartPanelArray.length == 0 || chartPanelArray[0].length == 0)
			return;

		int nrows = chartPanelArray.length;
		int ncolumns = chartPanelArray[0].length;
		chartCagesFrame.setXRange(null);
		chartCagesFrame.setYRange(null);

		for (int column = 0; column < ncolumns; column++) {
			for (int row = 0; row < nrows; row++) {
				ChartPanel chartPanel = chartPanelArray[row][column] != null
						? chartPanelArray[row][column].getChartPanel()
						: null;
				if (chartPanel == null)
					continue;
				XYPlot plot = (XYPlot) chartPanel.getChart().getPlot();
				if (plot == null)
					continue;
				ValueAxis xAxis = plot.getDomainAxis();
				ValueAxis yAxis = plot.getRangeAxis();

				if (xAxis != null)
					chartCagesFrame.setXRange(Range.combine(chartCagesFrame.getXRange(), xAxis.getRange()));
				if (yAxis != null)
					chartCagesFrame.setYRange(Range.combine(chartCagesFrame.getYRange(), yAxis.getRange()));
			}
		}

		applyCollectedRange(chartCagesFrame.getXRange(), chartCagesFrame.getYRange());
	}

	private void applyCollectedRange(Range xRange, Range yRange) {
		if (xRange != null) {
			lowerXSpinner.setValue(xRange.getLowerBound());
			upperXSpinner.setValue(xRange.getUpperBound());
		}
		if (yRange != null) {
			lowerYSpinner.setValue(yRange.getLowerBound());
			upperYSpinner.setValue(yRange.getUpperBound());
		}
		lowerYSpinner.setEnabled(true);
		upperYSpinner.setEnabled(true);
		setYaxis.setEnabled(true);
	}

	private static Range rangeOf(ChartPanel chartPanel, boolean domain) {
		if (chartPanel == null || chartPanel.getChart() == null || !(chartPanel.getChart().getPlot() instanceof XYPlot)) {
			return null;
		}
		XYPlot plot = (XYPlot) chartPanel.getChart().getPlot();
		ValueAxis axis = domain ? plot.getDomainAxis() : plot.getRangeAxis();
		return axis != null ? axis.getRange() : null;
	}

	private void updateXAxis() {
		if (chartCagesFrame != null) {
			updateXAxisChartCagesFrame();
		} else {
			setAxisRange(currentSingleChart(), true, (double) lowerXSpinner.getValue(),
					(double) upperXSpinner.getValue());
		}
	}

	private void updateXAxisChartCagesFrame() {
		ChartCagePair[][] chartPanelArray = chartCagesFrame.getChartCagePairArray();
		if (chartPanelArray == null || chartPanelArray.length == 0 || chartPanelArray[0].length == 0)
			return;

		int nrows = chartPanelArray.length;
		int ncolumns = chartPanelArray[0].length;

		double upper = (double) upperXSpinner.getValue();
		double lower = (double) lowerXSpinner.getValue();
		for (int column = 0; column < ncolumns; column++) {
			for (int row = 0; row < nrows; row++) {
				ChartPanel chartPanel = chartPanelArray[row][column] != null
						? chartPanelArray[row][column].getChartPanel()
						: null;
				if (chartPanel == null)
					continue;
				XYPlot xyPlot = (XYPlot) chartPanel.getChart().getPlot();
				NumberAxis xAxis = (NumberAxis) xyPlot.getDomainAxis();
				xAxis.setAutoRange(false);
				xAxis.setRange(lower, upper);
			}
		}
	}

	private void updateYAxis() {
		if (chartCagesFrame != null) {
			updateYAxisChartCagesFrame();
		} else {
			setAxisRange(currentSingleChart(), false, (double) lowerYSpinner.getValue(),
					(double) upperYSpinner.getValue());
		}
	}

	private ChartPanel currentSingleChart() {
		return singleChartPanel != null ? singleChartPanel.get() : null;
	}

	private static void setAxisRange(ChartPanel chartPanel, boolean domain, double lower, double upper) {
		if (chartPanel == null || chartPanel.getChart() == null || !(chartPanel.getChart().getPlot() instanceof XYPlot)) {
			return;
		}
		if (lower > upper) {
			double swap = lower;
			lower = upper;
			upper = swap;
		}
		XYPlot plot = (XYPlot) chartPanel.getChart().getPlot();
		ValueAxis axis = domain ? plot.getDomainAxis() : plot.getRangeAxis();
		if (!(axis instanceof NumberAxis)) {
			return;
		}
		NumberAxis numberAxis = (NumberAxis) axis;
		numberAxis.setAutoRange(false);
		numberAxis.setRange(lower, upper);
	}

	private void updateYAxisChartCagesFrame() {
		ChartCagePair[][] chartPanelArray = chartCagesFrame.getChartCagePairArray();
		if (chartPanelArray == null || chartPanelArray.length == 0 || chartPanelArray[0].length == 0)
			return;

		int nrows = chartPanelArray.length;
		int ncolumns = chartPanelArray[0].length;

		double upper = (double) upperYSpinner.getValue();
		double lower = (double) lowerYSpinner.getValue();
		for (int column = 0; column < ncolumns; column++) {
			for (int row = 0; row < nrows; row++) {
				ChartPanel chartPanel = chartPanelArray[row][column] != null
						? chartPanelArray[row][column].getChartPanel()
						: null;
				if (chartPanel == null)
					continue;
				XYPlot xyPlot = (XYPlot) chartPanel.getChart().getPlot();
				NumberAxis yAxis = (NumberAxis) xyPlot.getRangeAxis();
				yAxis.setAutoRange(false);
				yAxis.setRange(lower, upper);
			}
		}
	}

}
