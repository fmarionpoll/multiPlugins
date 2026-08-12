package plugins.fmp.multiSPOTS.dlg.e_flyPosition;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;

import icy.gui.viewer.Viewer;
import icy.sequence.Sequence;
import icy.sequence.SequenceEvent;
import icy.sequence.SequenceListener;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.CageString;
import plugins.fmp.multitools.experiment.cage.FlyPositions;
import plugins.fmp.multitools.tools.chart.ChartCagesFrame;
import plugins.fmp.multitools.tools.chart.ChartInteractionHandler;
import plugins.fmp.multitools.tools.chart.ChartInteractionHandlerFactory;
import plugins.fmp.multitools.tools.chart.builders.CageFlyPositionSeriesBuilder;
import plugins.fmp.multitools.tools.chart.interaction.FlyPositionChartInteractionHandler;
import plugins.fmp.multitools.tools.chart.strategies.ComboBoxUIControlsFactory;
import plugins.fmp.multitools.tools.chart.strategies.GridLayoutStrategy;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.results.ResultsOptionsBuilder;

/**
 * Fly-position charts in a scrollable per-cage grid (same approach as kymograph
 * {@link plugins.fmp.multiSPOTS.dlg.measure_kymograph.GraphPanel}).
 */
public class PlotFliesPositions extends JPanel implements SequenceListener {
	private static final long serialVersionUID = -7079184380174992501L;

	private static final EnumResults[] FLY_MEASURES = { EnumResults.YVSFOOD, EnumResults.DISTANCE,
			EnumResults.ISALIVE, EnumResults.SLEEP, EnumResults.VISIBLE_FLY_COUNT };

	private MultiSPOTS parent0 = null;

	private final JComboBox<EnumResults> measureComboBox = new JComboBox<>(FLY_MEASURES);
	private final JRadioButton displayAllButton = new JRadioButton("all cages", true);
	private final JRadioButton displaySelectedCageButton = new JRadioButton("cage selected", false);
	public JButton displayResultsButton = new JButton("Display charts");
	private final JLabel graphStatusLabel = new JLabel(" ", SwingConstants.LEFT);
	JSpinner aliveThresholdSpinner = new JSpinner(new SpinnerNumberModel(50.0, 0., 100000., .1));

	private ChartCagesFrame chartCagesFrame;

	void init(GridLayout capLayout, MultiSPOTS parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);
		flowLayout.setVgap(2);

		JPanel panel0 = new JPanel(flowLayout);
		panel0.add(displayResultsButton);
		panel0.add(new JLabel("Measure"));
		panel0.add(measureComboBox);
		add(panel0);

		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(new JLabel("Display"));
		panel1.add(displayAllButton);
		panel1.add(displaySelectedCageButton);
		ButtonGroup displayGroup = new ButtonGroup();
		displayGroup.add(displayAllButton);
		displayGroup.add(displaySelectedCageButton);
		add(panel1);

		JPanel panel2 = new JPanel(flowLayout);
		panel2.add(new JLabel("Alive threshold"));
		panel2.add(aliveThresholdSpinner);
		add(panel2);

		JPanel panel3 = new JPanel(flowLayout);
		panel3.add(new JLabel("Status: "));
		panel3.add(graphStatusLabel);
		add(panel3);

		measureComboBox.setSelectedItem(EnumResults.YVSFOOD);

		defineActionListeners();
	}

	private void defineActionListeners() {
		displayResultsButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				onDisplayCharts();
				firePropertyChange("DISPLAY_RESULTS", false, true);
			}
		});

		ActionListener refreshListener = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				maybeRefreshVisibleCharts();
			}
		};
		measureComboBox.addActionListener(refreshListener);
		displayAllButton.addActionListener(refreshListener);
		displaySelectedCageButton.addActionListener(refreshListener);
		aliveThresholdSpinner.addChangeListener(e -> maybeRefreshVisibleCharts());
	}

	void maybeRefreshVisibleCharts() {
		if (chartCagesFrame != null && chartCagesFrame.getMainChartFrame() != null
				&& chartCagesFrame.getMainChartFrame().isVisible()) {
			onDisplayCharts();
		}
	}

	private EnumResults selectedMeasure() {
		Object o = measureComboBox.getSelectedItem();
		return o instanceof EnumResults ? (EnumResults) o : EnumResults.YVSFOOD;
	}

	private void onDisplayCharts() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null) {
			graphStatusLabel.setText("No experiment selected.");
			return;
		}
		if (exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null) {
			graphStatusLabel.setText("Open the camera sequence first.");
			return;
		}
		if (!hasFlyPositionData(exp)) {
			graphStatusLabel.setText("No fly position data. Run detection or load saved positions first.");
			return;
		}

		EnumResults measure = selectedMeasure();
		prepareFlyMeasureData(exp, measure);

		ResultsOptions options;
		if (displayAllButton.isSelected()) {
			options = ResultsOptionsBuilder.forChart().withResultType(measure).withCageRange(-1, -1).build();
		} else {
			Cage cageFound = exp.getCages().findFirstSelectedCage();
			if (cageFound == null) {
				graphStatusLabel.setText("Select a cage ROI on the camera sequence.");
				return;
			}
			applyExclusiveCageRoiSelection(exp, cageFound);
			if (cageFound.getRoi() != null) {
				exp.getSeqCamData().centerDisplayOnRoi(cageFound.getRoi());
			}
			int cageNumber;
			try {
				cageNumber = Integer.parseInt(CageString.getCageNumberFromCageRoiName(cageFound.getRoi().getName()));
			} catch (NumberFormatException ex) {
				graphStatusLabel.setText("Could not parse cage id from ROI name.");
				return;
			}
			options = ResultsOptionsBuilder.forChart().withResultType(measure).withCageRange(cageNumber, cageNumber)
					.build();
		}

		closeAllCharts();
		exp.getSeqCamData().getSequence().addListener(this);

		ChartInteractionHandlerFactory handlerFactory = new ChartInteractionHandlerFactory() {
			@Override
			public ChartInteractionHandler createHandler(Experiment exp2, ResultsOptions options2,
					plugins.fmp.multitools.tools.chart.ChartCagePair[][] charts) {
				return new FlyPositionChartInteractionHandler(exp2, options2);
			}
		};
		chartCagesFrame = new ChartCagesFrame(new CageFlyPositionSeriesBuilder(), handlerFactory,
				new GridLayoutStrategy(), createFlyChartUIControlsFactory());
		chartCagesFrame.createMainChartPanel("Fly positions", exp, options);
		chartCagesFrame.setChartUpperLeftLocation(getInitialUpperLeftPosition(exp));
		chartCagesFrame.displayData(exp, options);
		if (chartCagesFrame.getMainChartFrame() != null) {
			chartCagesFrame.getMainChartFrame().toFront();
			chartCagesFrame.getMainChartFrame().requestFocus();
		}
		graphStatusLabel.setText(" ");
	}

	private ComboBoxUIControlsFactory createFlyChartUIControlsFactory() {
		ComboBoxUIControlsFactory ui = new ComboBoxUIControlsFactory();
		ui.setMeasurementTypes(FLY_MEASURES);
		ui.setParentComboBox(measureComboBox);
		return ui;
	}

	private static boolean hasFlyPositionData(Experiment exp) {
		if (exp == null || exp.getCages() == null) {
			return false;
		}
		for (Cage cage : exp.getCages().getCageList()) {
			FlyPositions flyPositions = cage.getFlyPositions();
			if (flyPositions != null && flyPositions.flyPositionList != null
					&& !flyPositions.flyPositionList.isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private void prepareFlyMeasureData(Experiment exp, EnumResults measure) {
		if (exp == null || exp.getCages() == null) {
			return;
		}
		if (measure == EnumResults.ISALIVE) {
			double threshold = (double) aliveThresholdSpinner.getValue();
			for (Cage cage : exp.getCages().getCageList()) {
				FlyPositions posSeries = cage.getFlyPositions();
				if (posSeries != null) {
					posSeries.setMoveThreshold(threshold);
					posSeries.computeIsAlive();
				}
			}
		} else if (measure == EnumResults.SLEEP) {
			for (Cage cage : exp.getCages().getCageList()) {
				FlyPositions posSeries = cage.getFlyPositions();
				if (posSeries != null) {
					posSeries.computeSleep();
				}
			}
		}
	}

	private static void applyExclusiveCageRoiSelection(Experiment exp, Cage cageToSelect) {
		if (exp == null || exp.getCages() == null || cageToSelect == null) {
			return;
		}
		for (Cage cage : exp.getCages().cagesList) {
			if (cage == null || cage.getRoi() == null) {
				continue;
			}
			cage.getRoi().setSelected(cage == cageToSelect);
		}
	}

	private Rectangle getInitialUpperLeftPosition(Experiment exp) {
		Rectangle rectv = new Rectangle(50, 500, 10, 10);
		if (exp != null && exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null) {
			Viewer v = exp.getSeqCamData().getSequence().getFirstViewer();
			if (v != null) {
				rectv = v.getBounds();
				rectv.translate(0, rectv.height);
			}
		}
		if (rectv.width <= 10 && parent0 != null && parent0.mainFrame != null) {
			rectv = parent0.mainFrame.getBounds();
			rectv.translate(rectv.width, rectv.height + 100);
		}
		rectv.translate(5, 10);
		return rectv;
	}

	public void closeAllCharts() {
		if (chartCagesFrame != null && chartCagesFrame.getMainChartFrame() != null) {
			chartCagesFrame.getMainChartFrame().dispose();
		}
		chartCagesFrame = null;
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
