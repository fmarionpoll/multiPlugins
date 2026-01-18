package plugins.fmp.multiSPOTS96.dlg.d_spotsMeasures;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import icy.gui.viewer.Viewer;
import icy.sequence.Sequence;
import icy.sequence.SequenceEvent;
import icy.sequence.SequenceListener;
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.CageString;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spots.EnumSpotMeasures;
import plugins.fmp.multitools.tools.chart.ChartCagesFrame;
import plugins.fmp.multitools.tools.chart.builders.CageSpotSeriesBuilder;
import plugins.fmp.multitools.tools.chart.strategies.GridLayoutStrategy;
import plugins.fmp.multitools.tools.chart.strategies.NoUIControlsFactory;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.results.ResultsOptionsBuilder;

public class Charts extends JPanel implements SequenceListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7079184380174992501L;
	private ChartCagesFrame chartCageArrayFrame = null;
	private MultiSPOTS96 parent0 = null;
	private JButton displayResultsButton = new JButton("Display results");
	private JButton axisOptionsButton = new JButton("Axis options");
	private AxisOptions graphOptions = null;
	private EnumSpotMeasures[] measures = new EnumSpotMeasures[] { //
			EnumSpotMeasures.AREA_SUM, //
			EnumSpotMeasures.AREA_SUMCLEAN // ,
			// EnumXLSExportType.AREA_DIFF
	};
	private JComboBox<EnumSpotMeasures> exportTypeComboBox = new JComboBox<EnumSpotMeasures>(measures);
	private JCheckBox relativeToCheckbox = new JCheckBox("relative to max", false);
	private JRadioButton displayAllButton = new JRadioButton("all cages");
	private JRadioButton displaySelectedButton = new JRadioButton("cage selected");

	// ----------------------------------------

	void init(GridLayout capLayout, MultiSPOTS96 parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;
		setLayout(capLayout);
		FlowLayout layout = new FlowLayout(FlowLayout.LEFT);
		layout.setVgap(0);

		JPanel panel01 = new JPanel(layout);
		panel01.add(new JLabel("Measure"));
		panel01.add(exportTypeComboBox);
		panel01.add(new JLabel(" display"));
		panel01.add(displayAllButton);
		panel01.add(displaySelectedButton);
		add(panel01);

		JPanel panel02 = new JPanel(layout);
		panel02.add(relativeToCheckbox);
		add(panel02);

		JPanel panel04 = new JPanel(layout);
		panel04.add(displayResultsButton);
		panel04.add(axisOptionsButton);
		add(panel04);

		ButtonGroup group1 = new ButtonGroup();
		group1.add(displayAllButton);
		group1.add(displaySelectedButton);
		displayAllButton.setSelected(true);

		exportTypeComboBox.setSelectedIndex(1);
		defineActionListeners();
	}

	private void defineActionListeners() {

		exportTypeComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					displayChartPanels(exp);
			}
		});

		displayResultsButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					displayChartPanels(exp);
			}
		});

		axisOptionsButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null && chartCageArrayFrame != null) {
					if (graphOptions != null) {
						graphOptions.close();
					}
					graphOptions = new AxisOptions();
					graphOptions.initialize(parent0, chartCageArrayFrame);
					graphOptions.requestFocus();
				}
			}
		});

		relativeToCheckbox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					displayChartPanels(exp);
			}
		});
	}

	private Rectangle getInitialUpperLeftPosition(Experiment exp) {
		Rectangle rectv = new Rectangle(50, 500, 10, 10);
		Viewer v = exp.getSeqCamData().getSequence().getFirstViewer();
		if (v != null) {
			rectv = v.getBounds();
			rectv.translate(0, rectv.height);
		} else {
			rectv = parent0.mainFrame.getBounds();
			rectv.translate(rectv.width, rectv.height + 100);
		}

		int dx = 5;
		int dy = 10;
		rectv.translate(dx, dy);
		return rectv;
	}

	public void displayChartPanels(Experiment exp) {
		exp.getSeqCamData().getSequence().removeListener(this);
		EnumSpotMeasures exportType = (EnumSpotMeasures) exportTypeComboBox.getSelectedItem();
		if (isThereAnyDataToDisplay(exp, exportType))
			chartCageArrayFrame = plotSpotMeasuresToChart(exp, exportType, chartCageArrayFrame);
		exp.getSeqCamData().getSequence().addListener(this);
	}

	private ChartCagesFrame plotSpotMeasuresToChart(Experiment exp, EnumSpotMeasures exportType,
			ChartCagesFrame iChart) {
		if (iChart != null)
			iChart.getMainChartFrame().dispose();

		int first = 0;
		int last = exp.getCages().cagesList.size() - 1;
		if (!displayAllButton.isSelected()) {
			Cage cageFound = exp.getCages().findFirstCageWithSelectedSpot(exp.getSpots());
			if (cageFound == null)
				cageFound = exp.getCages().findFirstSelectedCage();
			if (cageFound == null)
				return null;
			exp.getSeqCamData().centerDisplayOnRoi(cageFound.getRoi());
			String cageNumber = CageString.getCageNumberFromCageRoiName(cageFound.getRoi().getName());
			first = Integer.parseInt(cageNumber);
			last = first;
		}

		EnumResults resultType = convertSpotMeasureToResult(exportType);
		ResultsOptions options = ResultsOptionsBuilder.forChart().withBuildExcelStepMs(60000).withResultType(resultType)
				.withCageRange(first, last).build();
		options.relativeToMaximum = relativeToCheckbox.isSelected();

		if (iChart == null) {
			iChart = new ChartCagesFrame(new CageSpotSeriesBuilder(), null, new GridLayoutStrategy(),
					new NoUIControlsFactory());
		}
		iChart.createMainChartPanel("Spots measures", exp, options);
		Rectangle initialPos = getInitialUpperLeftPosition(exp);
		if (iChart.getMainChartFrame() != null) {
			iChart.getMainChartFrame().setLocation(initialPos.x, initialPos.y);
		}
		iChart.displayData(exp, options);
		if (iChart.getMainChartFrame() != null) {
			iChart.getMainChartFrame().toFront();
			iChart.getMainChartFrame().requestFocus();
		}
		return iChart;
	}

	private EnumResults convertSpotMeasureToResult(EnumSpotMeasures spotMeasure) {
		switch (spotMeasure) {
		case AREA_SUM:
			return EnumResults.AREA_SUM;
		case AREA_SUMCLEAN:
			return EnumResults.AREA_SUMCLEAN;
		case AREA_FLYPRESENT:
			return EnumResults.AREA_FLYPRESENT;
		default:
			return EnumResults.AREA_SUM;
		}
	}

	public void closeAllCharts() {
		if (chartCageArrayFrame != null)
			chartCageArrayFrame.getMainChartFrame().dispose();
		chartCageArrayFrame = null;
	}

	private boolean isThereAnyDataToDisplay(Experiment exp, EnumSpotMeasures option) {
		EnumResults resultType = convertSpotMeasureToResult(option);
		boolean flag = false;
		for (Cage cage : exp.getCages().cagesList) {
			for (Spot spot : cage.getSpotList(exp.getSpots())) {
				if (spot.isThereAnyMeasuresDone(resultType) > 0) {
					flag = true;
					break;
				}
			}
			if (flag)
				break;
		}
		return flag;
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
