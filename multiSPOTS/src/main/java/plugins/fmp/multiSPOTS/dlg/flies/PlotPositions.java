package plugins.fmp.multiSPOTS.dlg.flies;

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
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multiSPOTS.experiment.Experiment;
import plugins.fmp.multiSPOTS.experiment.cages.Cage;
import plugins.fmp.multiSPOTS.experiment.cages.FlyPositions;
import plugins.fmp.multiSPOTS.tools.chart.ChartPositions;
import plugins.fmp.multiSPOTS.tools.toExcel.EnumXLSExportType;

public class PlotPositions extends JPanel implements SequenceListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7079184380174992501L;

	private ChartPositions ypositionsChart = null;
	private ChartPositions distanceChart = null;
	private ChartPositions aliveChart = null;
	private ChartPositions sleepChart = null;
	private MultiSPOTS parent0 = null;

	public JCheckBox moveCheckbox = new JCheckBox("y position", true);
	private JCheckBox distanceCheckbox = new JCheckBox("distance t/t+1", false);
	JCheckBox aliveCheckbox = new JCheckBox("fly alive", true);
	JCheckBox sleepCheckbox = new JCheckBox("sleep", false);
	JSpinner aliveThresholdSpinner = new JSpinner(new SpinnerNumberModel(50.0, 0., 100000., .1));
	public JButton displayResultsButton = new JButton("Display results");

	void init(GridLayout capLayout, MultiSPOTS parent0) {
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
		Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
		if (exp == null)
			return;
		final Rectangle rectv = exp.seqCamData.seq.getFirstViewer().getBounds();
		Point ptRelative = new Point(0, 30);
		final int deltay = 230;
		exp.seqCamData.seq.addListener(this);

		if (moveCheckbox.isSelected()) {
			displayYPos("flies Y positions", ypositionsChart, rectv, ptRelative, exp, EnumXLSExportType.XYTOPCAGE);
			ptRelative.y += deltay;
		}

		if (distanceCheckbox.isSelected()) {
			displayYPos("distance between positions at t+1 and t", distanceChart, rectv, ptRelative, exp,
					EnumXLSExportType.DISTANCE);
			ptRelative.y += deltay;
		}

		if (aliveCheckbox.isSelected()) {
			double threshold = (double) aliveThresholdSpinner.getValue();
			for (Cage cage : exp.getCages().cagesList) {
				FlyPositions posSeries = cage.flyPositions;
				posSeries.moveThreshold = threshold;
				posSeries.computeIsAlive();
			}
			displayYPos("flies alive", aliveChart, rectv, ptRelative, exp, EnumXLSExportType.ISALIVE);
			ptRelative.y += deltay;
		}

		if (sleepCheckbox.isSelected()) {
			for (Cage cage : exp.getCages().cagesList) {
				FlyPositions posSeries = cage.flyPositions;
				posSeries.computeSleep();
			}
			displayYPos("flies asleep", sleepChart, rectv, ptRelative, exp, EnumXLSExportType.SLEEP);
			ptRelative.y += deltay;
		}
	}

	private void displayYPos(String title, ChartPositions iChart, Rectangle rectv, Point ptRelative, Experiment exp,
			EnumXLSExportType option) {
		if (iChart == null || !iChart.mainChartPanel.isValid()) {
			iChart = new ChartPositions();
			iChart.createPanel(title);
			iChart.setLocationRelativeToRectangle(rectv, ptRelative);
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

	private void close(ChartPositions chart) {
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
