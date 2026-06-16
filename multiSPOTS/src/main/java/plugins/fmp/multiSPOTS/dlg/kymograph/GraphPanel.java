package plugins.fmp.multiSPOTS.dlg.kymograph;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.util.List;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingConstants;

import icy.gui.viewer.Viewer;
import icy.roi.ROI2D;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multiSPOTS.dlg.imageFilters.SpotSequenceRois;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.CageSpotStimulusAggregation;
import plugins.fmp.multitools.experiment.cage.CageString;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.service.KymoAnalysisResult;
import plugins.fmp.multitools.tools.chart.ChartCagePair;
import plugins.fmp.multitools.tools.chart.ChartCagesFrame;
import plugins.fmp.multitools.tools.chart.ChartInteractionHandler;
import plugins.fmp.multitools.tools.chart.ChartInteractionHandlerFactory;
import plugins.fmp.multitools.tools.chart.ChartSpotsOverlayFrame;
import plugins.fmp.multitools.tools.chart.builders.CageSpotSeriesBuilder;
import plugins.fmp.multitools.tools.chart.interaction.SpotChartInteractionHandler;
import plugins.fmp.multitools.tools.chart.builders.KymoSpotChartSupport;
import plugins.fmp.multitools.tools.chart.strategies.ComboBoxUIControlsFactory;
import plugins.fmp.multitools.tools.chart.strategies.GridLayoutStrategy;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.KymoFractionTraceMode;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.results.ResultsOptionsBuilder;

/**
 * Kymograph metric charts: measure, display mode, and chart windows (cages or spot overlay).
 */
public class GraphPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private static final EnumResults[] KYMO_MEASURES = { EnumResults.KYMO_GREEN_HEIGHT_RATIO,
			EnumResults.AGG_GREENHEIGHT_CONSO, EnumResults.KYMO_GREEN_HEIGHT, EnumResults.KYMO_FRACT,
			EnumResults.KYMO_ABS_DELTA, EnumResults.KYMO_CAGE_MEAN_GREEN_HEIGHT_RATIO,
			EnumResults.KYMO_CAGE_MEAN_FRACT, EnumResults.KYMO_CAGE_MEAN_ABS_DELTA };

	private final MultiSPOTS parent0;
	private final AnalysisPanel analysisPanel;

	private final JComboBox<EnumResults> measureComboBox = new JComboBox<>(KYMO_MEASURES);
	private final JRadioButton displayAllButton = new JRadioButton("all cages", true);
	private final JRadioButton displaySelectedCageButton = new JRadioButton("cage selected", false);
	private final JRadioButton displaySelectedSpotsButton = new JRadioButton("spot(s) selected", false);
	private final JButton displayChartsButton = new JButton("Display charts");
	private final JLabel graphStatusLabel = new JLabel(" ", SwingConstants.LEFT);

	private ChartCagesFrame chartCagesFrame;
	private KymoOverlayFrame overlayFrame;

	public GraphPanel(MultiSPOTS parent0, AnalysisPanel analysisPanel) {
		super(new GridLayout(3, 1));
		this.parent0 = parent0;
		this.analysisPanel = analysisPanel;
		FlowLayout left = new FlowLayout(FlowLayout.LEFT);
		left.setVgap(0);

		JPanel p0 = new JPanel(left);
		p0.add(displayChartsButton);
		p0.add(new JLabel("Measure"));
		p0.add(measureComboBox);
		add(p0);

		JPanel p1 = new JPanel(left);
		p1.add(new JLabel("Display"));
		p1.add(displayAllButton);
		p1.add(displaySelectedCageButton);
		p1.add(displaySelectedSpotsButton);
		ButtonGroup g = new ButtonGroup();
		g.add(displayAllButton);
		g.add(displaySelectedCageButton);
		g.add(displaySelectedSpotsButton);
		add(p1);

		JPanel p2 = new JPanel(left);
		p2.add(new JLabel("Status: "));
		p2.add(graphStatusLabel);
		add(p2);

		measureComboBox.setSelectedItem(EnumResults.KYMO_GREEN_HEIGHT_RATIO);

		analysisPanel.addKymoResultListener(e -> maybeRefreshVisibleCharts());

		displayChartsButton.addActionListener(e -> onDisplayCharts());
		measureComboBox.addActionListener(e -> maybeRefreshVisibleCharts());
		displayAllButton.addActionListener(e -> maybeRefreshVisibleCharts());
		displaySelectedCageButton.addActionListener(e -> maybeRefreshVisibleCharts());
		displaySelectedSpotsButton.addActionListener(e -> maybeRefreshVisibleCharts());
	}

	void maybeRefreshVisibleCharts() {
		if (!hasChartableKymoData()) {
			return;
		}
		boolean cagesVisible = chartCagesFrame != null && chartCagesFrame.getMainChartFrame() != null
				&& chartCagesFrame.getMainChartFrame().isVisible();
		boolean overlayVisible = overlayFrame != null && overlayFrame.getMainChartFrame() != null
				&& overlayFrame.getMainChartFrame().isVisible();
		if (cagesVisible || overlayVisible) {
			onDisplayCharts();
		}
	}

	private EnumResults selectedMeasure() {
		Object o = measureComboBox.getSelectedItem();
		return o instanceof EnumResults ? (EnumResults) o : EnumResults.KYMO_FRACT;
	}

	private static void applyKymoAggregateChartOptions(Experiment exp, ResultsOptions options) {
		if (options == null || exp == null || exp.getSpots() == null) {
			return;
		}
		if (options.resultType == EnumResults.AGG_GREENHEIGHT_CONSO) {
			options.spotAggregateGlobalKeyOrder = CageSpotStimulusAggregation
					.globalStimulusConcKeysFirstSeenOrder(exp, exp.getSpots());
		} else {
			options.spotAggregateGlobalKeyOrder = null;
		}
	}

	private boolean hasChartableKymoData() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null && KymoSpotChartSupport.experimentHasKymoSpotMeasures(exp)) {
			return true;
		}
		KymoAnalysisResult lastResult = analysisPanel.getLastResult();
		return lastResult != null && !lastResult.byCageId.isEmpty();
	}

	/** Called when an experiment is opened and auto-graph kymo measures is enabled. */
	public void displayChartsOnExperimentOpen() {
		onDisplayCharts();
	}

	private void onDisplayCharts() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null) {
			graphStatusLabel.setText("No experiment selected.");
			return;
		}
		if (!KymoSpotChartSupport.experimentHasKymoSpotMeasures(exp)) {
			KymoAnalysisResult lastResult = analysisPanel.getLastResult();
			if (lastResult == null || lastResult.byCageId.isEmpty()) {
				graphStatusLabel.setText("Run Analyze or load saved kymo measures first.");
				return;
			}
		}
		closeCharts();

		int stepMs = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, exp.getKymoBin_ms()));
		EnumResults measure = selectedMeasure();
		ResultsOptions options;

		if (displaySelectedSpotsButton.isSelected()) {
			options = ResultsOptionsBuilder.forChart().withResultType(measure).withBuildExcelStepMs(stepMs)
					.withCageRange(-1, -1).withKymoFractionTraceMode(KymoFractionTraceMode.FINAL).build();
			options.relativeToMaximum = false;
			applyKymoAggregateChartOptions(exp, options);
			plotSpotsOverlay(exp, options);
			graphStatusLabel.setText(" ");
			return;
		}

		if (displayAllButton.isSelected()) {
			options = ResultsOptionsBuilder.forChart().withResultType(measure).withBuildExcelStepMs(stepMs)
					.withCageRange(-1, -1).withKymoFractionTraceMode(KymoFractionTraceMode.FINAL).build();
		} else {
			Cage cageFound = exp.getCages().findFirstSelectedCage();
			if (cageFound == null) {
				cageFound = exp.getCages().findFirstCageWithSelectedSpot(exp.getSpots());
			}
			if (cageFound == null) {
				cageFound = findCageFromSelectedSpotRoisOnSequence(exp);
			}
			if (cageFound == null) {
				graphStatusLabel.setText("Select a cage ROI or spot ROI on the camera sequence.");
				return;
			}
			applyExclusiveCageRoiSelection(exp, cageFound);
			if (exp.getSeqCamData() != null && cageFound.getRoi() != null) {
				exp.getSeqCamData().centerDisplayOnRoi(cageFound.getRoi());
			}
			int cageNumber;
			try {
				cageNumber = Integer.parseInt(CageString.getCageNumberFromCageRoiName(cageFound.getRoi().getName()));
			} catch (NumberFormatException ex) {
				graphStatusLabel.setText("Could not parse cage id from ROI name.");
				return;
			}
			int first = cageNumber;
			int last = cageNumber;
			options = ResultsOptionsBuilder.forChart().withResultType(measure).withBuildExcelStepMs(stepMs)
					.withCageRange(first, last).withKymoFractionTraceMode(KymoFractionTraceMode.FINAL).build();
		}
		options.relativeToMaximum = false;
		applyKymoAggregateChartOptions(exp, options);

		ChartInteractionHandlerFactory handlerFactory = new ChartInteractionHandlerFactory() {
			@Override
			public ChartInteractionHandler createHandler(Experiment exp2, ResultsOptions options2,
					ChartCagePair[][] charts) {
				return new SpotChartInteractionHandler(exp2, options2, charts,
						spot -> parent0.dlgSpots.onMeasureChartSpotClicked(spot));
			}
		};
		chartCagesFrame = new ChartCagesFrame(new CageSpotSeriesBuilder(), handlerFactory, new GridLayoutStrategy(),
				createKymoChartUIControlsFactory());
		chartCagesFrame.createMainChartPanel("Kymograph", exp, options);
		chartCagesFrame.setChartUpperLeftLocation(getInitialUpperLeftPosition(exp));
		chartCagesFrame.displayData(exp, options);
		if (chartCagesFrame.getMainChartFrame() != null) {
			chartCagesFrame.getMainChartFrame().toFront();
			chartCagesFrame.getMainChartFrame().requestFocus();
		}
		graphStatusLabel.setText(" ");
	}

	private void plotSpotsOverlay(Experiment exp, ResultsOptions options) {
		List<Spot> selectedSpots = SpotSequenceRois.selectedSpotsFromSequence(exp);
		if (selectedSpots.isEmpty()) {
			graphStatusLabel.setText("Select one or more spot ROIs on the camera sequence.");
			return;
		}
		overlayFrame = new KymoOverlayFrame();
		overlayFrame.setMeasurementTypes(KYMO_MEASURES);
		overlayFrame.setParentComboBox(measureComboBox);
		overlayFrame.setSelectedSpotsProvider(
				() -> ChartSpotsOverlayFrame.dedupeSpots(SpotSequenceRois.selectedSpotsFromSequence(exp)));
		overlayFrame.setOnSpotChartClicked(parent0.dlgSpots::onMeasureChartSpotClicked);
		overlayFrame.createMainChartPanel("Kymograph (selected)", options);
		overlayFrame.setChartUpperLeftLocation(getInitialUpperLeftPosition(exp));
		overlayFrame.displayData(exp, options);
		graphStatusLabel.setText(" ");
	}

	private void closeCharts() {
		if (chartCagesFrame != null && chartCagesFrame.getMainChartFrame() != null) {
			chartCagesFrame.getMainChartFrame().dispose();
		}
		chartCagesFrame = null;
		if (overlayFrame != null) {
			overlayFrame.dispose();
		}
		overlayFrame = null;
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

	private ComboBoxUIControlsFactory createKymoChartUIControlsFactory() {
		ComboBoxUIControlsFactory ui = new ComboBoxUIControlsFactory();
		ui.setMeasurementTypes(KYMO_MEASURES);
		ui.setParentComboBox(measureComboBox);
		return ui;
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
}
