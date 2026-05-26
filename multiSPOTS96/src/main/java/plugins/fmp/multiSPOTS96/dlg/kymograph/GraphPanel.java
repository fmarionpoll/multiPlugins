package plugins.fmp.multiSPOTS96.dlg.kymograph;

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
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multiSPOTS96.dlg.spotsMeasures.SpotSequenceRois;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.CageString;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.service.KymoAnalysisResult;
import plugins.fmp.multitools.service.KymoAnalysisResult.SpotKymoSeries;
import plugins.fmp.multitools.tools.chart.ChartCagesFrame;
import plugins.fmp.multitools.tools.chart.ChartSpotsOverlayFrame;
import plugins.fmp.multitools.tools.chart.builders.CageKymoSeriesBuilder;
import plugins.fmp.multitools.tools.chart.strategies.GridLayoutStrategy;
import plugins.fmp.multitools.tools.chart.strategies.NoOpChartUIControlsFactory;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.KymoFractionTraceMode;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.results.ResultsOptionsBuilder;

/**
 * Kymograph metric charts: measure, display mode, and chart windows (cages or spot overlay).
 */
public class GraphPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private static final EnumResults[] KYMO_MEASURES = { EnumResults.KYMO_FRACT, EnumResults.KYMO_ABS_DELTA,
			EnumResults.KYMO_CAGE_MEAN_FRACT, EnumResults.KYMO_CAGE_MEAN_ABS_DELTA };

	private final MultiSPOTS96 parent0;
	private final AnalysisPanel analysisPanel;

	private final JComboBox<EnumResults> measureComboBox = new JComboBox<>(KYMO_MEASURES);
	private final JRadioButton displayAllButton = new JRadioButton("all cages", true);
	private final JRadioButton displaySelectedCageButton = new JRadioButton("cage selected", false);
	private final JRadioButton displaySelectedSpotsButton = new JRadioButton("spot(s) selected", false);
	private final JButton diagnosticsButton = new JButton("Diagnostics…");
	private final JButton displayChartsButton = new JButton("Display charts");
	private final JComboBox<KymoFractionTraceMode> fractionTraceCombo = new JComboBox<>(KymoFractionTraceMode.values());
	private final JLabel graphStatusLabel = new JLabel(" ", SwingConstants.LEFT);

	private ChartCagesFrame chartCagesFrame;
	private KymoOverlayFrame overlayFrame;

	public GraphPanel(MultiSPOTS96 parent0, AnalysisPanel analysisPanel) {
		super(new GridLayout(4, 1));
		this.parent0 = parent0;
		this.analysisPanel = analysisPanel;
		FlowLayout left = new FlowLayout(FlowLayout.LEFT);
		left.setVgap(0);

		JPanel p0 = new JPanel(left);
		p0.add(diagnosticsButton);
		p0.add(displayChartsButton);
		p0.add(new JLabel("Measure"));
		p0.add(measureComboBox);
		add(p0);

		JPanel pFrac = new JPanel(left);
		pFrac.add(new JLabel("Fraction trace"));
		pFrac.add(fractionTraceCombo);
		add(pFrac);

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

		measureComboBox.setSelectedItem(EnumResults.KYMO_FRACT);
		fractionTraceCombo.setSelectedItem(KymoFractionTraceMode.FINAL);
		syncFractionTraceComboEnabled();

		analysisPanel.addKymoResultListener(e -> maybeRefreshVisibleCharts());

		diagnosticsButton.addActionListener(e -> KymoDiagnosticsDialog.show(this,
				parent0.kymoDiagnosticsOptions, this::maybeRefreshVisibleCharts));
		displayChartsButton.addActionListener(e -> onDisplayCharts());
		measureComboBox.addActionListener(e -> {
			syncFractionTraceComboEnabled();
			maybeRefreshVisibleCharts();
		});
		fractionTraceCombo.addActionListener(e -> maybeRefreshVisibleCharts());
		displayAllButton.addActionListener(e -> maybeRefreshVisibleCharts());
		displaySelectedCageButton.addActionListener(e -> maybeRefreshVisibleCharts());
		displaySelectedSpotsButton.addActionListener(e -> maybeRefreshVisibleCharts());
	}

	void maybeRefreshVisibleCharts() {
		KymoAnalysisResult lastResult = analysisPanel.getLastResult();
		if (lastResult == null || lastResult.byCageId.isEmpty()) {
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

	private KymoFractionTraceMode selectedKymoFractionTraceMode() {
		Object o = fractionTraceCombo.getSelectedItem();
		return o instanceof KymoFractionTraceMode ? (KymoFractionTraceMode) o : KymoFractionTraceMode.FINAL;
	}

	private void syncFractionTraceComboEnabled() {
		EnumResults m = selectedMeasure();
		boolean fract = m == EnumResults.KYMO_FRACT || m == EnumResults.KYMO_CAGE_MEAN_FRACT;
		fractionTraceCombo.setEnabled(fract);
		if (!fract) {
			fractionTraceCombo.setSelectedItem(KymoFractionTraceMode.FINAL);
		}
	}

	private static boolean hasGapFillDiagnostics(KymoAnalysisResult r) {
		if (r == null) {
			return false;
		}
		for (List<SpotKymoSeries> list : r.byCageId.values()) {
			if (list == null) {
				continue;
			}
			for (SpotKymoSeries row : list) {
				if (row != null && row.fractionBeforeGapFill != null) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean fractionTraceNeedsStoredSeries(KymoFractionTraceMode mode) {
		return mode == KymoFractionTraceMode.BEFORE_GAP_FILL || mode == KymoFractionTraceMode.DELTA_GAP_FILL;
	}

	private void updateGraphStatusAfterDisplay(KymoAnalysisResult lastResult) {
		EnumResults meas = selectedMeasure();
		if ((meas == EnumResults.KYMO_FRACT || meas == EnumResults.KYMO_CAGE_MEAN_FRACT)
				&& fractionTraceNeedsStoredSeries(selectedKymoFractionTraceMode())
				&& !hasGapFillDiagnostics(lastResult)) {
			graphStatusLabel.setText(
					"Before/delta traces: open Diagnostics, enable storage of pre-gap-fill fractions, then Analyze.");
			return;
		}
		graphStatusLabel.setText(" ");
	}

	private void onDisplayCharts() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		KymoAnalysisResult lastResult = analysisPanel.getLastResult();
		if (exp == null || lastResult == null) {
			graphStatusLabel.setText("Run Analyze on the Kymo analysis tab first.");
			return;
		}
		if (lastResult.byCageId.isEmpty()) {
			graphStatusLabel.setText("Nothing to display.");
			return;
		}
		closeCharts();

		int stepMs = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, exp.getKymoBin_ms()));
		EnumResults measure = selectedMeasure();
		ResultsOptions options;

		if (displaySelectedSpotsButton.isSelected()) {
			options = ResultsOptionsBuilder.forChart().withResultType(measure).withBuildExcelStepMs(stepMs)
					.withCageRange(-1, -1).withKymoFractionTraceMode(selectedKymoFractionTraceMode()).build();
			options.relativeToMaximum = false;
			plotSpotsOverlay(exp, options, lastResult);
			return;
		}

		if (displayAllButton.isSelected()) {
			options = ResultsOptionsBuilder.forChart().withResultType(measure).withBuildExcelStepMs(stepMs)
					.withCageRange(-1, -1).withKymoFractionTraceMode(selectedKymoFractionTraceMode()).build();
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
					.withCageRange(first, last).withKymoFractionTraceMode(selectedKymoFractionTraceMode()).build();
		}
		options.relativeToMaximum = false;

		CageKymoSeriesBuilder dataBuilder = new CageKymoSeriesBuilder(lastResult);
		chartCagesFrame = new ChartCagesFrame(dataBuilder, null, new GridLayoutStrategy(),
				new NoOpChartUIControlsFactory());
		chartCagesFrame.createMainChartPanel("Kymograph", exp, options);
		chartCagesFrame.setChartUpperLeftLocation(getInitialUpperLeftPosition(exp));
		chartCagesFrame.displayData(exp, options);
		if (chartCagesFrame.getMainChartFrame() != null) {
			chartCagesFrame.getMainChartFrame().toFront();
			chartCagesFrame.getMainChartFrame().requestFocus();
		}
		updateGraphStatusAfterDisplay(lastResult);
	}

	private void plotSpotsOverlay(Experiment exp, ResultsOptions options, KymoAnalysisResult lastResult) {
		List<Spot> selectedSpots = SpotSequenceRois.selectedSpotsFromSequence(exp);
		if (selectedSpots.isEmpty()) {
			graphStatusLabel.setText("Select one or more spot ROIs on the camera sequence.");
			return;
		}
		overlayFrame = new KymoOverlayFrame();
		overlayFrame.setSelectedSpotsProvider(
				() -> ChartSpotsOverlayFrame.dedupeSpots(SpotSequenceRois.selectedSpotsFromSequence(exp)));
		overlayFrame.createMainChartPanel("Kymograph (selected)", options);
		overlayFrame.setChartUpperLeftLocation(getInitialUpperLeftPosition(exp));
		overlayFrame.displayData(exp, options, lastResult);
		updateGraphStatusAfterDisplay(lastResult);
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
