package plugins.fmp.multicafe.dlg.cages;

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
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.FlyPositions;
import plugins.fmp.multitools.tools.chart.ChartPositions;
import plugins.fmp.multitools.tools.results.EnumResults;

public class ChartPositionsPanel extends JPanel implements SequenceListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7079184380174992501L;

	private ChartPositions xpositionsChart = null;
	private ChartPositions ypositionsChart = null;
	private ChartPositions distanceChart = null;
	private ChartPositions aliveChart = null;
	private ChartPositions sleepChart = null;
	private ChartPositions illumChart = null;

	private MultiCAFE parent0 = null;

	public JCheckBox xCheckbox = new JCheckBox("x position", false);
	public JCheckBox yCheckbox = new JCheckBox("y position", true);
	private JCheckBox distanceCheckbox = new JCheckBox("distance t/t+1", false);
	JCheckBox aliveCheckbox = new JCheckBox("fly alive", false);
	JCheckBox sleepCheckbox = new JCheckBox("sleep", false);
	JCheckBox illumCheckbox = new JCheckBox("light/dark", false);
	JSpinner aliveThresholdSpinner = new JSpinner(new SpinnerNumberModel(50.0, 0., 100000., .1));
	public JButton displayResultsButton = new JButton("Display results");

	// If true, move graphs should follow experiment navigation (when global "graphs"
	// option is enabled). This is set when the user explicitly displayed graphs once.
	private boolean followExperimentNavigation = false;

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);
		flowLayout.setVgap(2);
		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(xCheckbox);
		panel1.add(yCheckbox);
		panel1.add(distanceCheckbox);
		panel1.add(aliveCheckbox);
		panel1.add(sleepCheckbox);
		panel1.add(illumCheckbox);
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
				Experiment exp = (Experiment) ChartPositionsPanel.this.parent0.expListComboLazy.getSelectedItem();
				followExperimentNavigation = true;
				displayGraphsPanels(exp);
				firePropertyChange("DISPLAY_RESULTS", false, true);
			}
		});
	}

	/**
	 * Refreshes move graphs for the given experiment if at least one move graph is
	 * already displayed. This is used when navigating between experiments, so the
	 * move graph dialogs follow the current experiment (like capillary level
	 * graphs).
	 */
	public void refreshIfDisplayed(Experiment exp) {
		if (!followExperimentNavigation) {
			return;
		}
		displayGraphsPanels(exp);
	}

	private void displayGraphsPanels(Experiment exp) {
		if (exp == null)
			return;
		final Rectangle rectv = exp.getSeqCamData().getSequence().getFirstViewer().getBounds();
		Point ptRelative = new Point(0, 30);
		final int deltay = 230;
		exp.getSeqCamData().getSequence().addListener(this);

		if (xCheckbox.isSelected()) {
			xpositionsChart = plotYToChart("flies X positions", xpositionsChart, rectv, ptRelative, exp,
					EnumResults.XTOPCAGE);
			ptRelative.y += deltay;
		} else if (xpositionsChart != null) {
			closeChart(xpositionsChart);
		}

		if (yCheckbox.isSelected()) {
			ypositionsChart = plotYToChart("flies Y positions", ypositionsChart, rectv, ptRelative, exp,
					EnumResults.YTOPCAGE);
			ptRelative.y += deltay;
		} else if (ypositionsChart != null)
			closeChart(ypositionsChart);

		if (distanceCheckbox.isSelected()) {
			distanceChart = plotYToChart("distance between positions at t+1 and t", distanceChart, rectv, ptRelative,
					exp, EnumResults.DISTANCE);
			ptRelative.y += deltay;
		} else if (distanceChart != null)
			closeChart(distanceChart);

		if (aliveCheckbox.isSelected()) {
			double threshold = (double) aliveThresholdSpinner.getValue();
			for (Cage cage : exp.getCages().getCageList()) {
				FlyPositions posSeries = cage.getFlyPositions();
				posSeries.setMoveThreshold(threshold);
				posSeries.computeIsAlive();
			}
			aliveChart = plotYToChart("flies alive", aliveChart, rectv, ptRelative, exp, EnumResults.ISALIVE);
			ptRelative.y += deltay;
		} else if (aliveChart != null)
			closeChart(aliveChart);

		if (sleepCheckbox.isSelected()) {
			for (Cage cage : exp.getCages().getCageList()) {
				FlyPositions posSeries = cage.getFlyPositions();
				posSeries.computeSleep();
			}
			sleepChart = plotYToChart("flies asleep", sleepChart, rectv, ptRelative, exp, EnumResults.SLEEP);
			ptRelative.y += deltay;
		} else if (sleepChart != null)
			closeChart(sleepChart);

		if (illumCheckbox.isSelected()) {
			illumChart = plotYToChart("lighting phase (Detect2)", illumChart, rectv, ptRelative, exp,
					EnumResults.ILLUM_PHASE);
			ptRelative.y += deltay;
		} else if (illumChart != null)
			closeChart(illumChart);
	}

	private ChartPositions plotYToChart(String title, ChartPositions iChart, Rectangle rectv, Point ptRelative,
			Experiment exp, EnumResults resultType) {
		if (iChart != null)
			iChart.mainChartFrame.dispose();

		iChart = new ChartPositions();
		iChart.createPanel(title);
		iChart.setLocationRelativeToRectangle(rectv, ptRelative);
		iChart.displayData(exp, exp.getCages().getCageList(), resultType);
		iChart.mainChartFrame.toFront();
		iChart.mainChartFrame.requestFocus();
		return iChart;
	}

	private ChartPositions closeChart(ChartPositions chart) {
		if (chart != null)
			chart.mainChartFrame.dispose();
		chart = null;
		return chart;
	}

	public void closeAllCharts() {
		followExperimentNavigation = false;
		ypositionsChart = closeChart(ypositionsChart);
		xpositionsChart = closeChart(xpositionsChart);
		distanceChart = closeChart(distanceChart);
		aliveChart = closeChart(aliveChart);
		sleepChart = closeChart(sleepChart);
		illumChart = closeChart(illumChart);
	}

	@Override
	public void sequenceChanged(SequenceEvent sequenceEvent) {
	}

	@Override
	public void sequenceClosed(Sequence sequence) {
		sequence.removeListener(this);
		// When switching experiments, the previous sequence is closed. Do not dispose
		// the graph windows here: if the user requested graphs, we refresh them when
		// the next experiment loads (driven by the global "graphs" option).
	}
}
