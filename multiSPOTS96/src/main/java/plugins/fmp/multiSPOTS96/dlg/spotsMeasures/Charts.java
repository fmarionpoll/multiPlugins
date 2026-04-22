package plugins.fmp.multiSPOTS96.dlg.spotsMeasures;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import icy.gui.viewer.Viewer;
import icy.roi.ROI2D;
import icy.sequence.Sequence;
import icy.sequence.SequenceEvent;
import icy.sequence.SequenceListener;
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.CageString;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spots.EnumSpotMeasures;
import plugins.fmp.multitools.tools.chart.ChartCagePair;
import plugins.fmp.multitools.tools.chart.ChartCagesFrame;
import plugins.fmp.multitools.tools.chart.ChartInteractionHandler;
import plugins.fmp.multitools.tools.chart.ChartInteractionHandlerFactory;
import plugins.fmp.multitools.tools.chart.ChartSpotsOverlayFrame;
import plugins.fmp.multitools.tools.chart.builders.CageSpotSeriesBuilder;
import plugins.fmp.multitools.tools.chart.interaction.SpotChartInteractionHandler;
import plugins.fmp.multitools.tools.chart.strategies.ComboBoxUIControlsFactory;
import plugins.fmp.multitools.tools.chart.strategies.GridLayoutStrategy;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.results.ResultsOptionsBuilder;

public class Charts extends JPanel implements SequenceListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7079184380174992501L;
	private ChartCagesFrame chartCageArrayFrame = null;
	private ChartSpotsOverlayFrame chartSpotsOverlayFrame = null;
	private MultiSPOTS96 parent0 = null;
	private JButton displayResultsButton = new JButton("Display results");
	private JButton axisOptionsButton = new JButton("Axis options");
	private AxisOptions graphOptions = null;
	private JComboBox<EnumSpotMeasures> exportTypeComboBox = null;
	private JCheckBox relativeToCheckbox = new JCheckBox("relative to max", false);
	private JRadioButton displayAllButton = new JRadioButton("all cages");
	private JRadioButton displaySelectedButton = new JRadioButton("cage selected");
	private JRadioButton displaySelectedSpotsButton = new JRadioButton("spot(s) selected");

	// ----------------------------------------

	void init(GridLayout capLayout, MultiSPOTS96 parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;
		setLayout(capLayout);
		FlowLayout layout = new FlowLayout(FlowLayout.LEFT);
		layout.setVgap(0);

		exportTypeComboBox = new JComboBox<>(buildChartMeasureChoices());

		JPanel panel01 = new JPanel(layout);
		panel01.add(new JLabel("Measure"));
		panel01.add(exportTypeComboBox);
		panel01.add(new JLabel(" display"));
		panel01.add(displayAllButton);
		panel01.add(displaySelectedButton);
		panel01.add(displaySelectedSpotsButton);
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
		group1.add(displaySelectedSpotsButton);
		displayAllButton.setSelected(true);

		if (exportTypeComboBox.getItemCount() > 1) {
			exportTypeComboBox.setSelectedIndex(1);
		} else if (exportTypeComboBox.getItemCount() == 1) {
			exportTypeComboBox.setSelectedIndex(0);
		}
		defineActionListeners();
	}

	private static EnumSpotMeasures[] buildChartMeasureChoices() {
		String[] keys = { "AREA_SUM", "AREA_SUMNOFLY", "AREA_SUMCLEAN", "AREA_FLYPRESENT" };
		List<EnumSpotMeasures> list = new ArrayList<>();
		for (String key : keys) {
			EnumSpotMeasures v = EnumSpotMeasures.findByText(key);
			if (v != null) {
				list.add(v);
			}
		}
		if (list.isEmpty()) {
			for (EnumSpotMeasures v : EnumSpotMeasures.values()) {
				String n = v.name();
				if (n.startsWith("AREA_")) {
					list.add(v);
				}
			}
		}
		return list.toArray(new EnumSpotMeasures[0]);
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

		ActionListener refreshOnChange = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				axisOptionsButton.setEnabled(!displaySelectedSpotsButton.isSelected());
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					displayChartPanels(exp);
			}
		};
		displayAllButton.addActionListener(refreshOnChange);
		displaySelectedButton.addActionListener(refreshOnChange);
		displaySelectedSpotsButton.addActionListener(refreshOnChange);
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
		if (chartSpotsOverlayFrame != null) {
			chartSpotsOverlayFrame.dispose();
			chartSpotsOverlayFrame = null;
		}

		if (iChart != null) {
			iChart.getMainChartFrame().dispose();
		}

		int first = 0;
		int last = exp.getCages().cagesList.size() - 1;
		if (displaySelectedSpotsButton.isSelected()) {
			return plotSelectedSpotsOverlay(exp, exportType);
		} else if (!displayAllButton.isSelected()) {
			Cage cageFound = exp.getCages().findFirstSelectedCage();
			if (cageFound == null)
				cageFound = exp.getCages().findFirstCageWithSelectedSpot(exp.getSpots());
			if (cageFound == null)
				cageFound = findCageFromSelectedSpotRoisOnSequence(exp);
			if (cageFound == null)
				return null;
			applyExclusiveCageRoiSelection(exp, cageFound);
			exp.getSeqCamData().centerDisplayOnRoi(cageFound.getRoi());
			String cageNumber = CageString.getCageNumberFromCageRoiName(cageFound.getRoi().getName());
			first = Integer.parseInt(cageNumber);
			last = first;
		}

		EnumResults resultType = convertSpotMeasureToResult(exportType);
		if (resultType == null) {
			return null;
		}
		ResultsOptions options = ResultsOptionsBuilder.forChart().withBuildExcelStepMs(60000).withResultType(resultType)
				.withCageRange(first, last).build();
		options.relativeToMaximum = relativeToCheckbox.isSelected();

		ChartInteractionHandlerFactory handlerFactory = new ChartInteractionHandlerFactory() {
			@Override
			public ChartInteractionHandler createHandler(Experiment exp, ResultsOptions options,
					ChartCagePair[][] charts) {
				return new SpotChartInteractionHandler(exp, options, charts);
			}
		};

		iChart = new ChartCagesFrame(new CageSpotSeriesBuilder(), handlerFactory, new GridLayoutStrategy(),
				createChartUIControlsFactory());
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

	private ChartCagesFrame plotSelectedSpotsOverlay(Experiment exp, EnumSpotMeasures exportType) {
		List<Spot> selectedSpots = SpotSequenceRois.selectedSpotsFromSequence(exp);
		if (selectedSpots.isEmpty())
			return null;

		EnumResults resultType = convertSpotMeasureToResult(exportType);
		if (resultType == null) {
			return null;
		}
		ResultsOptions options = ResultsOptionsBuilder.forChart().withBuildExcelStepMs(60000).withResultType(resultType)
				.withCageRange(0, 0).build();
		options.relativeToMaximum = relativeToCheckbox.isSelected();

		chartSpotsOverlayFrame = new ChartSpotsOverlayFrame();
		chartSpotsOverlayFrame.createMainChartPanel("Spots measures (selected)", options);
		chartSpotsOverlayFrame.setSelectedSpotsProvider(
				() -> ChartSpotsOverlayFrame.dedupeSpots(SpotSequenceRois.selectedSpotsFromSequence(exp)));
		chartSpotsOverlayFrame.setAvailableSpotsProvider(
				() -> ChartSpotsOverlayFrame.dedupeSpots(SpotSequenceRois.allSpotsFromSequence(exp)));
		chartSpotsOverlayFrame.setSpotExclusiveSelectionController(spot -> selectExclusiveSpotRoi(exp, spot));
		chartSpotsOverlayFrame.setChartUpperLeftLocation(getInitialUpperLeftPosition(exp));
		chartSpotsOverlayFrame.displayData(exp, options, ChartSpotsOverlayFrame.dedupeSpots(selectedSpots));
		return null;
	}

	private void selectExclusiveSpotRoi(Experiment exp, Spot spot) {
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null || spot == null
				|| spot.getName() == null) {
			return;
		}
		Sequence seq = exp.getSeqCamData().getSequence();
		List<ROI2D> roiList = seq.getROI2Ds();
		if (roiList == null || roiList.isEmpty())
			return;

		ROI2D target = null;
		for (ROI2D roi : roiList) {
			if (roi == null)
				continue;
			String name = roi.getName();
			if (name == null || !name.startsWith("spot"))
				continue;
			roi.setSelected(false);
			if (name.equals(spot.getName())) {
				target = roi;
			}
		}
		if (target != null) {
			target.setSelected(true);
			seq.setSelectedROI(target);
			exp.getSeqCamData().centerDisplayOnRoi(target);
		}
	}

	private ComboBoxUIControlsFactory createChartUIControlsFactory() {
		ComboBoxUIControlsFactory ui = new ComboBoxUIControlsFactory();
		ui.setMeasurementTypes(buildChartEnumResultsChoices());
		return ui;
	}

	private static EnumResults[] buildChartEnumResultsChoices() {
		String[] keys = { "AREA_SUM", "AREA_SUMNOFLY", "AREA_SUMCLEAN", "AREA_FLYPRESENT" };
		List<EnumResults> list = new ArrayList<>();
		for (String key : keys) {
			EnumResults v = EnumResults.findByText(key);
			if (v != null) {
				list.add(v);
			}
		}
		if (list.isEmpty()) {
			for (EnumResults v : EnumResults.values()) {
				if (v.name().startsWith("AREA_")) {
					list.add(v);
				}
			}
		}
		if (list.isEmpty()) {
			EnumResults d = defaultEnumResultForSpotsChart("AREA_SUM");
			if (d != null) {
				list.add(d);
			}
		}
		return list.toArray(new EnumResults[0]);
	}

	private static Cage findCageFromSelectedSpotRoisOnSequence(Experiment exp) {
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null
				|| exp.getSpots() == null) {
			return null;
		}
		List<ROI2D> roiList = exp.getSeqCamData().getSequence().getROI2Ds();
		if (roiList == null || roiList.isEmpty()) {
			return null;
		}
		for (ROI2D roi : roiList) {
			if (roi == null || !roi.isSelected()) {
				continue;
			}
			String name = roi.getName();
			if (!SpotSequenceRois.nameLooksLikeSpotRoi(name)) {
				continue;
			}
			Cage cage = exp.getCages().getCageFromSpotROIName(name, exp.getSpots());
			if (cage != null) {
				return cage;
			}
		}
		return null;
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

	private EnumResults convertSpotMeasureToResult(EnumSpotMeasures spotMeasure) {
		if (spotMeasure == null) {
			return defaultEnumResultForSpotsChart("AREA_SUM");
		}
		EnumResults mapped = EnumResults.findByText(spotMeasure.toString());
		if (mapped != null) {
			return mapped;
		}
		return defaultEnumResultForSpotsChart("AREA_SUM");
	}

	private static EnumResults defaultEnumResultForSpotsChart(String label) {
		EnumResults r = EnumResults.findByText(label);
		if (r != null) {
			return r;
		}
		for (EnumResults v : EnumResults.values()) {
			if (v.name().startsWith("AREA_")) {
				return v;
			}
		}
		EnumResults[] all = EnumResults.values();
		return all.length > 0 ? all[0] : null;
	}

	public void closeAllCharts() {
		if (chartCageArrayFrame != null)
			chartCageArrayFrame.getMainChartFrame().dispose();
		chartCageArrayFrame = null;
		if (chartSpotsOverlayFrame != null)
			chartSpotsOverlayFrame.dispose();
		chartSpotsOverlayFrame = null;
	}

	private boolean isThereAnyDataToDisplay(Experiment exp, EnumSpotMeasures option) {
		EnumResults resultType = convertSpotMeasureToResult(option);
		if (resultType == null) {
			return false;
		}
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
