package plugins.fmp.multiSPOTS96.dlg.d_spotsMeasures;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

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
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.chart.ChartCagePair;
import plugins.fmp.multitools.tools.chart.ChartCagesFrame;

public class AxisOptions extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	IcyFrame dialogFrame = null;
	private MultiSPOTS96 parent0 = null;
	private ChartCagesFrame chartCagesFrame = null;
	private JSpinner lowerXSpinner = new JSpinner(new SpinnerNumberModel(0., 0., 255., 1.));
	private JSpinner upperXSpinner = new JSpinner(new SpinnerNumberModel(120., 0., 255., 1.));
	private JSpinner lowerYSpinner = new JSpinner(new SpinnerNumberModel(0., 0., 255., 1.));
	private JSpinner upperYSpinner = new JSpinner(new SpinnerNumberModel(80., 0., 255., 1.));
	private JButton setYaxis = new JButton("set Y axis values");
	private JButton setXaxis = new JButton("set X axis values");

	public void initialize(MultiSPOTS96 parent0, ChartCagesFrame chartSpots) {
		this.parent0 = parent0;
		this.chartCagesFrame = chartSpots;

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

		if (chartCagesFrame.getXRange() != null) {
			lowerXSpinner.setValue(chartCagesFrame.getXRange().getLowerBound());
			upperXSpinner.setValue(chartCagesFrame.getXRange().getUpperBound());
		}
		if (chartCagesFrame.getYRange() != null) {
			lowerYSpinner.setValue(chartCagesFrame.getYRange().getLowerBound());
			upperYSpinner.setValue(chartCagesFrame.getYRange().getUpperBound());
		}
	}

	private void updateXAxis() {
		if (chartCagesFrame != null) {
			updateXAxisChartCagesFrame();
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
		}
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
