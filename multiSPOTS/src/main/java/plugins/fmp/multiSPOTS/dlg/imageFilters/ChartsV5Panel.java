package plugins.fmp.multiSPOTS.dlg.imageFilters;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
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
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.CageString;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.tools.chart.ChartCagePair;
import plugins.fmp.multitools.tools.chart.ChartCagesFrame;
import plugins.fmp.multitools.tools.chart.ChartInteractionHandler;
import plugins.fmp.multitools.tools.chart.ChartInteractionHandlerFactory;
import plugins.fmp.multitools.tools.chart.ChartV5SpotsOverlayFrame;
import plugins.fmp.multitools.tools.chart.builders.CageSpotSeriesBuilder;
import plugins.fmp.multitools.tools.chart.interaction.SpotChartInteractionHandler;
import plugins.fmp.multitools.tools.chart.strategies.ComboBoxUIControlsFactory;
import plugins.fmp.multitools.tools.chart.strategies.GridLayoutStrategy;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.results.ResultsOptionsBuilder;

public class ChartsV5Panel extends JPanel implements SequenceListener {
	private static final long serialVersionUID = 1L;

	private static final EnumResults[] SPOT_CHART_RESULTS = { EnumResults.AREA_COUNT_V5, EnumResults.GREY_SUM_V5,
			EnumResults.GREY_SUM_V5_PREFLY, EnumResults.GREY_SUM_CLEAN_V5, EnumResults.AGG_SUMCLEAN_V5, EnumResults.AGG_AREA_COUNT_V5,
			EnumResults.AREA_FLYPRESENT };
	private ChartCagesFrame chartCageArrayFrame = null;
	private ChartV5SpotsOverlayFrame chartSpotsOverlayFrame = null;
	private MultiSPOTS parent0 = null;
	private JButton displayResultsButton = new JButton("Display results");
	private JButton axisOptionsButton = new JButton("Axis options");
	private AxisOptions graphOptions = null;
	private JComboBox<EnumResults> exportTypeComboBox = null;
	private JCheckBox relativeToCheckbox = new JCheckBox("relative to max", false);
	private JRadioButton displayAllButton = new JRadioButton("all cages");
	private JRadioButton displaySelectedCageButton = new JRadioButton("cage selected");
	private JRadioButton displaySelectedSpotsButton = new JRadioButton("spot(s) selected");

	void init(GridLayout capLayout, MultiSPOTS parent0) {
		this.parent0 = parent0;
		FlowLayout layout = new FlowLayout(FlowLayout.LEFT);
		layout.setVgap(0);

		exportTypeComboBox = new JComboBox<>(SPOT_CHART_RESULTS);

		JPanel panel01 = new JPanel(layout);
		panel01.add(new JLabel("Measure"));
		panel01.add(exportTypeComboBox);
		panel01.add(relativeToCheckbox);

		JPanel panel02 = new JPanel(layout);
		panel02.add(new JLabel(" display"));
		panel02.add(displayAllButton);
		panel02.add(displaySelectedCageButton);
		panel02.add(displaySelectedSpotsButton);

		JPanel panel04 = new JPanel(layout);
		panel04.add(displayResultsButton);
		panel04.add(axisOptionsButton);
		SpotsMeasuresUi.layoutStackedRows(this, panel01, panel02, panel04);

		ButtonGroup group1 = new ButtonGroup();
		group1.add(displayAllButton);
		group1.add(displaySelectedCageButton);
		group1.add(displaySelectedSpotsButton);
		displayAllButton.setSelected(true);

		if (exportTypeComboBox.getItemCount() > 1) {
			exportTypeComboBox.setSelectedIndex(1);
		} else if (exportTypeComboBox.getItemCount() == 1) {
			exportTypeComboBox.setSelectedIndex(0);
		}
		defineActionListeners();
	}

	private void defineActionListeners() {

		exportTypeComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				updateMeasureDependentControls();
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
		displaySelectedCageButton.addActionListener(refreshOnChange);
		displaySelectedSpotsButton.addActionListener(refreshOnChange);

		updateMeasureDependentControls();
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
		if (exp == null || exp.getSeqCamData() == null) {
			return;
		}
		Sequence seq = exp.getSeqCamData().getSequence();
		if (seq == null) {
			return;
		}
		seq.removeListener(this);
		EnumResults exportType = (EnumResults) exportTypeComboBox.getSelectedItem();
		if (isThereAnyDataToDisplay(exp, exportType)) {
			chartCageArrayFrame = plotSpotMeasuresToChart(exp, exportType, chartCageArrayFrame);
		}
		seq.addListener(this);
	}

	private ChartCagesFrame plotSpotMeasuresToChart(Experiment exp, EnumResults exportType, ChartCagesFrame iChart) {
		if (chartSpotsOverlayFrame != null) {
			chartSpotsOverlayFrame.dispose();
			chartSpotsOverlayFrame = null;
		}

		if (iChart != null) {
			iChart.getMainChartFrame().dispose();
		}

		if ((exportType == EnumResults.AGG_SUMCLEAN_V5 || exportType == EnumResults.AGG_AREA_COUNT_V5)
				&& displaySelectedSpotsButton.isSelected()) {
			displayAllButton.setSelected(true);
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

		int chartStepMs = resolveSpotChartStepMs(exp);
		ResultsOptions options = ResultsOptionsBuilder.forChart().withBuildExcelStepMs(chartStepMs).withResultType(exportType)
				.withCageRange(first, last).build();
		options.relativeToMaximum = relativeToCheckbox.isSelected() && exportType != EnumResults.AREA_FLYPRESENT
				&& exportType != EnumResults.AGG_SUMCLEAN_V5 && exportType != EnumResults.AGG_AREA_COUNT_V5;
		options.spotAggregateByStimulusConc = false;

		ChartInteractionHandlerFactory handlerFactory = new ChartInteractionHandlerFactory() {
			@Override
			public ChartInteractionHandler createHandler(Experiment exp, ResultsOptions options,
					ChartCagePair[][] charts) {
				return new SpotChartInteractionHandler(exp, options, charts,
						spot -> parent0.dlgSpots.onMeasureChartSpotClicked(spot));
			}
		};

		iChart = new ChartCagesFrame(new CageSpotSeriesBuilder(), handlerFactory, new GridLayoutStrategy(),
				createChartUIControlsFactory());
		iChart.createMainChartPanel("Spots measures V5", exp, options);
		iChart.setChartUpperLeftLocation(getInitialUpperLeftPosition(exp));
		iChart.displayData(exp, options);
		if (iChart.getMainChartFrame() != null) {
			iChart.getMainChartFrame().toFront();
			iChart.getMainChartFrame().requestFocus();
		}
		return iChart;
	}

	private int resolveSpotChartStepMs(Experiment exp) {
		if (exp == null) {
			return 60000;
		}
		long kymoBinMs = exp.getKymoBin_ms();
		if (kymoBinMs > 0 && kymoBinMs <= Integer.MAX_VALUE) {
			return (int) kymoBinMs;
		}
		long camBinMs = exp.getCamImageBin_ms();
		if (camBinMs > 0 && camBinMs <= Integer.MAX_VALUE) {
			return (int) camBinMs;
		}
		return 60000;
	}

	private ChartCagesFrame plotSelectedSpotsOverlay(Experiment exp, EnumResults exportType) {
		List<Spot> selectedSpots = SpotSequenceRois.selectedSpotsFromSequence(exp);
		if (selectedSpots.isEmpty())
			return null;

		ResultsOptions options = ResultsOptionsBuilder.forChart().withBuildExcelStepMs(resolveSpotChartStepMs(exp))
				.withResultType(exportType).withCageRange(0, 0).build();
		options.relativeToMaximum = exportType != EnumResults.AREA_FLYPRESENT && exportType != EnumResults.AGG_SUMCLEAN_V5
				&& exportType != EnumResults.AGG_AREA_COUNT_V5 && relativeToCheckbox.isSelected();
		options.spotAggregateByStimulusConc = false;

		chartSpotsOverlayFrame = new ChartV5SpotsOverlayFrame();
		chartSpotsOverlayFrame.createMainChartPanel("Spots measures V5 (selected)", options);
		chartSpotsOverlayFrame.setSelectedSpotsProvider(
				() -> ChartV5SpotsOverlayFrame.dedupeSpots(SpotSequenceRois.selectedSpotsFromSequence(exp)));
		chartSpotsOverlayFrame.setAvailableSpotsProvider(
				() -> ChartV5SpotsOverlayFrame.dedupeSpots(SpotSequenceRois.allSpotsFromSequence(exp)));
		chartSpotsOverlayFrame.setSpotExclusiveSelectionController(spot -> selectExclusiveSpotRoi(exp, spot));
		chartSpotsOverlayFrame.setChartUpperLeftLocation(getInitialUpperLeftPosition(exp));
		chartSpotsOverlayFrame.displayData(exp, options, ChartV5SpotsOverlayFrame.dedupeSpots(selectedSpots));
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
		return Arrays.copyOf(SPOT_CHART_RESULTS, SPOT_CHART_RESULTS.length);
	}

	private void updateMeasureDependentControls() {
		EnumResults sel = exportTypeComboBox != null ? (EnumResults) exportTypeComboBox.getSelectedItem() : null;
		boolean agg = sel == EnumResults.AGG_SUMCLEAN_V5 || sel == EnumResults.AGG_AREA_COUNT_V5;
		relativeToCheckbox.setEnabled(!agg);
		if (agg) {
			relativeToCheckbox.setSelected(false);
		}
		// Per-spot overlay is only meaningful for native spot series, not cage aggregates.
		displaySelectedSpotsButton.setEnabled(true);
		if (agg && displaySelectedSpotsButton.isSelected()) {
			displayAllButton.setSelected(true);
		}
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

	public void closeAllCharts() {
		if (graphOptions != null) {
			graphOptions.close();
			graphOptions = null;
		}
		if (parent0 != null) {
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null && exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null)
				exp.getSeqCamData().getSequence().removeListener(this);
		}
		if (chartCageArrayFrame != null && chartCageArrayFrame.getMainChartFrame() != null)
			chartCageArrayFrame.getMainChartFrame().dispose();
		chartCageArrayFrame = null;
		if (chartSpotsOverlayFrame != null)
			chartSpotsOverlayFrame.dispose();
		chartSpotsOverlayFrame = null;
	}

	private boolean isThereAnyDataToDisplay(Experiment exp, EnumResults option) {
		EnumResults probe = option == EnumResults.AGG_SUMCLEAN_V5 ? EnumResults.GREY_SUM_CLEAN_V5
				: option == EnumResults.AGG_AREA_COUNT_V5 ? EnumResults.AREA_COUNT_V5 : option;
		for (Cage cage : exp.getCages().cagesList) {
			for (Spot spot : cage.getSpotList(exp.getSpots())) {
				if (spot.isThereAnyMeasuresDone(probe) > 0) {
					return true;
				}
			}
		}
		return false;
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
