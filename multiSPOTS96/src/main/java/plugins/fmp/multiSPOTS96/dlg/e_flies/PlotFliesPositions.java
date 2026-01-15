package plugins.fmp.multiSPOTS96.dlg.e_flies;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import icy.sequence.Sequence;
import icy.sequence.SequenceEvent;
import icy.sequence.SequenceListener;
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cages.Cage;
import plugins.fmp.multitools.experiment.cages.FlyPositions;
import plugins.fmp.multitools.tools.chart.ChartFlyPositions;
import plugins.fmp.multitools.tools.toExcel.EnumXLSExport;

public class PlotFliesPositions extends JPanel implements SequenceListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7079184380174992501L;

	private ChartFlyPositions ypositionsChart = null;
	private ChartFlyPositions distanceChart = null;
	private ChartFlyPositions aliveChart = null;
	private ChartFlyPositions sleepChart = null;
	private MultiSPOTS96 parent0 = null;

	public JCheckBox moveCheckbox = new JCheckBox("y position", true);
	private JCheckBox distanceCheckbox = new JCheckBox("distance t/t+1", false);
	JCheckBox aliveCheckbox = new JCheckBox("fly alive", true);
	JCheckBox sleepCheckbox = new JCheckBox("sleep", false);
	JSpinner aliveThresholdSpinner = new JSpinner(new SpinnerNumberModel(50.0, 0., 100000., .1));
	public JButton displayResultsButton = new JButton("Display results");

	void init(GridLayout capLayout, MultiSPOTS96 parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);
		flowLayout.setVgap(2);
		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(moveCheckbox);
		panel1.add(distanceCheckbox);
		panel1.add(aliveCheckbox);
		panel1.add(sleepCheckbox);
		add(panel1);

		JPanel panel2 = new JPanel(flowLayout);
		panel2.add(new JLabel("Alive threshold"));
		panel2.add(aliveThresholdSpinner);
		add(panel2);

		JPanel panel3 = new JPanel(flowLayout);
		panel3.add(displayResultsButton);
		add(panel3);

		defineActionListeners();
	}

	private void defineActionListeners() {
		displayResultsButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				displayGraphsPanels();
				firePropertyChange("DISPLAY_RESULTS", false, true);
			}
		});
	}

	private void displayGraphsPanels() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;
		final Rectangle rectv = exp.getSeqCamData().getSequence().getFirstViewer().getBounds();
		Point ptRelative = new Point(0, 30);
		final int deltay = 230;
		exp.getSeqCamData().getSequence().addListener(this);

		if (moveCheckbox.isSelected()) {
			displayYPos("flies Y positions", ypositionsChart, rectv, ptRelative, exp, EnumXLSExport.XYTOPCAGE);
			ptRelative.y += deltay;
		}

		if (distanceCheckbox.isSelected()) {
			displayYPos("distance between positions at t+1 and t", distanceChart, rectv, ptRelative, exp,
					EnumXLSExport.DISTANCE);
			ptRelative.y += deltay;
		}

		if (aliveCheckbox.isSelected()) {
			double threshold = (double) aliveThresholdSpinner.getValue();
			for (Cage cage : exp.getCages().cagesList) {
				FlyPositions posSeries = cage.flyPositions;
				posSeries.moveThreshold = threshold;
				posSeries.computeIsAlive();
			}
			displayYPos("flies alive", aliveChart, rectv, ptRelative, exp, EnumXLSExport.ISALIVE);
			ptRelative.y += deltay;
		}

		if (sleepCheckbox.isSelected()) {
			for (Cage cage : exp.getCages().cagesList) {
				FlyPositions posSeries = cage.flyPositions;
				posSeries.computeSleep();
			}
			displayYPos("flies asleep", sleepChart, rectv, ptRelative, exp, EnumXLSExport.SLEEP);
			ptRelative.y += deltay;
		}
	}

	private void displayYPos(String title, ChartFlyPositions iChart, Rectangle rectv, Point ptRelative, Experiment exp,
			EnumXLSExport option) {
		if (iChart == null || !iChart.getMainChartPanel().isValid()) {
			iChart = new ChartFlyPositions();
			iChart.createPanel(title);
			iChart.setLocationRelativeToPoint(new Point(rectv.x, rectv.y), ptRelative);
		}
		iChart.displayData(exp.getCages().cagesList, option);
		iChart.mainChartFrame.toFront();
	}

	public void closeAllCharts() {
		close(ypositionsChart);
		close(distanceChart);
		close(aliveChart);
		close(sleepChart);
	}

	private void close(ChartFlyPositions chart) {
		if (chart != null) {
			chart.mainChartFrame.close();
			chart = null;
		}
	}

	@Override
	public void sequenceChanged(SequenceEvent sequenceEvent) {
	}

	@Override
	public void sequenceClosed(Sequence sequence) {
		sequence.removeListener(this);
		closeAllCharts();
	}
}
