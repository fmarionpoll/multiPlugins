package plugins.fmp.multiSPOTS.dlg.measure;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.SpinnerNumberModel;

import icy.gui.viewer.Viewer;
import icy.roi.ROI2D;
import icy.sequence.Sequence;
import icy.sequence.SequenceEvent;
import icy.sequence.SequenceListener;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multiSPOTS.dlg.measure_imageFilters.AxisOptions;
import plugins.fmp.multiSPOTS.dlg.measure_imageFilters.SpotsMeasuresUi;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.tools.chart.ChartCagesFrame;
import plugins.fmp.multitools.tools.chart.ChartColorSpotsOverlayFrame;
import plugins.fmp.multitools.tools.chart.ChartLineSpotsOverlayFrame;
import plugins.fmp.multitools.tools.chart.ChartSpotsOverlayFrame;
import plugins.fmp.multitools.tools.chart.SpotChartMeasures;
import plugins.fmp.multitools.tools.chart.SpotChartMeasures.Family;
import plugins.fmp.multitools.tools.chart.builders.CageSpotSeriesBuilder;
import plugins.fmp.multitools.tools.chart.interaction.SpotChartInteractionHandler;
import plugins.fmp.multitools.tools.chart.strategies.ComboBoxUIControlsFactory;
import plugins.fmp.multitools.tools.chart.strategies.GridLayoutStrategy;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.KymoFractionTraceMode;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.results.ResultsOptionsBuilder;

/**
 * Shared chart workflow for all three measurement families. Spot and summary
 * views own independent options and windows; only the summary view accepts
 * cage-population results. Calculations remain in the shared series builders.
 */
public class MeasureChartsPanel extends JPanel implements SequenceListener {
	private static final long serialVersionUID = 1L;
	private final MultiSPOTS owner;
	private final Family family;
	private final JTabbedPane tabs = new JTabbedPane();
	private final ChartView spots;
	private final ChartView summaries;
	private Sequence listenedSequence;

	public MeasureChartsPanel(MultiSPOTS owner, Family family) {
		super(new BorderLayout());
		this.owner = owner;
		this.family = family;
		spots = new ChartView(false);
		summaries = new ChartView(true);
		tabs.addTab("Spot measurements", spots);
		tabs.addTab("Cage summaries", summaries);
		add(tabs, BorderLayout.CENTER);
	}

	private Experiment selectedExperiment() {
		return (Experiment) owner.expListComboLazy.getSelectedItem();
	}

	/** Explicit display also refreshes any independently open companion window. */
	public void displayChartPanels(Experiment exp) {
		ChartView selected = tabs.getSelectedIndex() == 0 ? spots : summaries;
		ChartView other = selected == spots ? summaries : spots;
		selected.display(exp);
		if (other.isVisibleChart()) other.display(exp);
	}

	public void refreshIfDisplayed(Experiment exp) {
		if (spots.isVisibleChart()) spots.display(exp);
		if (summaries.isVisibleChart()) summaries.display(exp);
	}

	public void refreshVisibleCharts() {
		refreshIfDisplayed(selectedExperiment());
	}

	public void closeAllCharts() {
		spots.close();
		summaries.close();
		if (listenedSequence != null) listenedSequence.removeListener(this);
		listenedSequence = null;
	}

	private void listenTo(Experiment exp) {
		// Line charts follow the experiment-open pipeline even when its camera closes.
		if (family == Family.LINES) return;
		Sequence next = exp.getSeqCamData() != null ? exp.getSeqCamData().getSequence() : null;
		if (next == listenedSequence) return;
		if (listenedSequence != null) listenedSequence.removeListener(this);
		listenedSequence = next;
		if (next != null) next.addListener(this);
	}

	@Override public void sequenceChanged(SequenceEvent event) { }
	@Override public void sequenceClosed(Sequence sequence) {
		if (sequence == listenedSequence) closeAllCharts();
	}

	private final class ChartView extends JPanel {
		private static final long serialVersionUID = 1L;
		private final boolean summary;
		private final JComboBox<EnumResults> measure;
		private final JRadioButton experimentView = new JRadioButton("Experiment (all cages)", true);
		private final JRadioButton cageView = new JRadioButton("Cage");
		private final JRadioButton spotView = new JRadioButton("Spot(s)");
		private final JComboBox<CageChoice> cage = new JComboBox<>();
		private final JCheckBox relative = new JCheckBox("Relative to baseline maximum");
		private final JSpinner baseline = new JSpinner(new SpinnerNumberModel(2, 0, 120, 1));
		private final JCheckBox stopStable = new JCheckBox("Stop when maximum is stable");
		private final JSpinner stableBins = new JSpinner(new SpinnerNumberModel(3, 1, 60, 1));
		private final JSpinner smoothBins = new JSpinner(new SpinnerNumberModel(5, 1, 99, 2));
		private final JPanel baselineRow = row();
		private final JPanel medianRow = row();
		private final JLabel status = new JLabel(" ");
		private Experiment currentExperiment;
		private boolean updating;
		private ChartCagesFrame cageFrame;
		private ChartSpotsOverlayFrame spotFrame;
		private AxisOptions axes;

		ChartView(boolean summary) {
			this.summary = summary;
			measure = new JComboBox<>(family.measures(summary));
			SpotsMeasuresUi.showMeasurementNames(measure);
			if (!summary && family == Family.FILTER) measure.setSelectedItem(EnumResults.FILTER_GREY_FLY_MASKED);
			if (!summary && family == Family.COLORS) measure.setSelectedItem(EnumResults.COLOR_GREY);
			JPanel scope = row();
			scope.add(new JLabel("View"));
			scope.add(experimentView);
			scope.add(cageView);
			ButtonGroup group = new ButtonGroup();
			group.add(experimentView);
			group.add(cageView);
			if (!summary) { scope.add(spotView); group.add(spotView); }
			scope.add(cage);
			JPanel metric = row();
			metric.add(new JLabel(summary ? "Summary" : "Measure"));
			metric.add(measure);
			if (!summary && family != Family.LINES) metric.add(relative);
			baselineRow.add(new JLabel("Baseline (min)"));
			baselineRow.add(baseline);
			baselineRow.add(stopStable);
			baselineRow.add(new JLabel("Stable samples"));
			baselineRow.add(stableBins);
			medianRow.add(new JLabel("Median reference smoothing (samples)"));
			medianRow.add(smoothBins);
			JButton display = new JButton("Display chart");
			JButton refresh = new JButton("Refresh");
			JButton axis = new JButton("Axis options");
			JPanel actions = row();
			actions.add(display); actions.add(refresh); actions.add(axis);
			JPanel message = row(); message.add(status);
			SpotsMeasuresUi.layoutStackedRows(this, scope, metric, baselineRow, medianRow, actions, message);
			display.addActionListener(e -> display(selectedExperiment()));
			refresh.addActionListener(e -> display(selectedExperiment()));
			axis.addActionListener(e -> openAxes());
			measure.addActionListener(e -> { updateControls(); refresh(); });
			relative.addActionListener(e -> { updateControls(); refresh(); });
			experimentView.addActionListener(e -> scopeChanged());
			cageView.addActionListener(e -> scopeChanged());
			spotView.addActionListener(e -> scopeChanged());
			cage.addActionListener(e -> { if (!updating) refresh(); });
			baseline.addChangeListener(e -> refresh());
			stopStable.addActionListener(e -> { updateControls(); refresh(); });
			stableBins.addChangeListener(e -> refresh());
			smoothBins.addChangeListener(e -> refresh());
			updateControls();
		}

		private void updateControls() {
			EnumResults result = (EnumResults) measure.getSelectedItem();
			boolean normalized = result != EnumResults.FLY_PIXEL_COUNT;
			relative.setEnabled(normalized);
			if (!normalized) relative.setSelected(false);
			boolean depletion = result == EnumResults.AGG_FILTER_DEPLETION || result == EnumResults.AGG_COLOR_DEPLETION;
			baselineRow.setVisible(depletion || (!summary && family != Family.LINES && relative.isSelected()));
			stableBins.setEnabled(stopStable.isSelected());
			medianRow.setVisible(result == EnumResults.CAGE_GREY_MEDIAN || result == EnumResults.AGG_FILTER_DEPLETION);
			cage.setVisible(cageView.isSelected());
			revalidate();
		}

		private void refresh() {
			if (!updating && isVisibleChart()) display(selectedExperiment());
		}

		private void scopeChanged() {
			Experiment exp = owner != null ? selectedExperiment() : null;
			if (exp != null) loadCages(exp);
			updateControls();
			refresh();
		}

		private boolean isVisibleChart() {
			return cageFrame != null && cageFrame.getMainChartFrame() != null && cageFrame.getMainChartFrame().isVisible()
					|| spotFrame != null && spotFrame.getMainChartFrame() != null && spotFrame.getMainChartFrame().isVisible();
		}

		private void loadCages(Experiment exp) {
			CageChoice previous = currentExperiment == exp ? (CageChoice) cage.getSelectedItem() : null;
			updating = true;
			try {
				cage.removeAllItems();
				List<Cage> list = new ArrayList<>(exp.getCages().getCageList());
				list.removeIf(c -> c == null || c.getProperties() == null);
				list.sort(Comparator.comparingInt(Cage::getCageID));
				Cage preferred = previous != null ? previous.value : exp.getCages().findFirstSelectedCage();
				if (preferred == null) preferred = exp.getCages().findFirstCageWithSelectedSpot(exp.getSpots());
				for (Cage value : list) {
					CageChoice choice = new CageChoice(value);
					cage.addItem(choice);
					if (value == preferred) cage.setSelectedItem(choice);
				}
			} finally { updating = false; }
		}

		private ResultsOptions options(Experiment exp) {
			EnumResults result = (EnumResults) measure.getSelectedItem();
			if (result == null || result.isCageSpotSummary() != summary)
				throw new IllegalArgumentException("Measurement does not belong to this chart panel");
			CageChoice chosen = (CageChoice) cage.getSelectedItem();
			int id = cageView.isSelected() && chosen != null ? chosen.value.getCageID() : -1;
			long bin = family == Family.LINES ? exp.getKymoBin_ms() : exp.getCamImageBin_ms();
			if (bin <= 0) bin = 60000;
			ResultsOptions options = ResultsOptionsBuilder.forChart().withResultType(result)
					.withCageRange(id, id).withBuildExcelStepMs((int) Math.min(Integer.MAX_VALUE, Math.max(1L, bin)))
					.withKymoFractionTraceMode(KymoFractionTraceMode.FINAL).build();
			options.relativeToMaximum = !summary && family != Family.LINES && relative.isSelected();
			options.spotAggregateByStimulusConc = false;
			options.spotBaselineWindowMinutes = ((Number) baseline.getValue()).intValue();
			options.spotBaselineStopWhenStable = stopStable.isSelected();
			options.spotBaselineStableBins = ((Number) stableBins.getValue()).intValue();
			options.aggMedianRefSmoothWindowBins = ((Number) smoothBins.getValue()).intValue();
			return options;
		}

		private void display(Experiment exp) {
			if (exp == null || exp.getCages() == null || exp.getSpots() == null) {
				close(); status.setText("No experiment selected."); return;
			}
			boolean changed = currentExperiment != exp;
			loadCages(exp);
			if (changed) close();
			currentExperiment = exp;
			listenTo(exp);
			if (cage.getItemCount() == 0) { close(); status.setText("No cages available."); return; }
			ResultsOptions options = options(exp);
			if (family != Family.LINES) {
				EnumResults source = SpotChartMeasures.sourceOf(options.resultType);
				boolean data = false;
				for (Cage value : exp.getCages().getCageList()) {
					if (cageView.isSelected() && value.getCageID() != options.cageIndexFirst) continue;
					for (Spot spot : value.getSpotList(exp.getSpots()))
						if (spot != null && spot.getMeasurements(source) != null && spot.getMeasurements(source).getCount() > 0) data = true;
				}
				if (!data) { close(); status.setText("No data for this measurement and view. Run analysis or load saved measures."); return; }
			}
			if (!summary && spotView.isSelected()) {
				status.setText(" ");
				displaySpots(exp, options);
				return;
			}
			displayCages(exp, options);
			status.setText(" ");
		}

		private void displayCages(Experiment exp, ResultsOptions options) {
			if (spotFrame != null) { close(); }
			if (cageFrame == null || cageFrame.getMainChartFrame() == null || !cageFrame.getMainChartFrame().isVisible()) {
				close();
				ComboBoxUIControlsFactory controls = new ComboBoxUIControlsFactory();
				controls.setMeasurementTypes(family.measures(summary));
				controls.showMeasureNamesWithTitles();
				controls.setMeasurementSelectionListener(result -> measure.setSelectedItem(result));
				controls.setSingleCageSelectionListener((experiment, selected) -> {
					updating = true;
					try {
						for (int i = 0; i < cage.getItemCount(); i++)
							if (cage.getItemAt(i).value.getCageID() == selected.getCageID()) cage.setSelectedIndex(i);
					} finally { updating = false; }
					owner.dlgSpots.onChartSingleCageSelected(experiment, selected);
				});
				cageFrame = new ChartCagesFrame(new CageSpotSeriesBuilder(),
						(experiment, settings, charts) -> new SpotChartInteractionHandler(experiment, settings, charts,
								spot -> owner.dlgSpots.onMeasureChartSpotClicked(spot)),
						new GridLayoutStrategy(), controls);
				cageFrame.createMainChartPanel(title(), exp, options);
				cageFrame.setChartUpperLeftLocation(position(exp));
			}
			cageFrame.displayData(exp, options);
		}

		private void displaySpots(Experiment exp, ResultsOptions options) {
			if (cageFrame != null) close();
			List<Spot> selected = initialSpots(exp);
			if (selected.isEmpty()) { close(); status.setText("No spots available."); return; }
			if (spotFrame == null || spotFrame.getMainChartFrame() == null || !spotFrame.getMainChartFrame().isVisible()) {
				close();
				spotFrame = family == Family.COLORS ? new ChartColorSpotsOverlayFrame()
						: family == Family.LINES ? new ChartLineSpotsOverlayFrame() : new ChartSpotsOverlayFrame();
				spotFrame.setSelectedSpotsProvider(() -> selectedSpots(exp));
				spotFrame.setAvailableSpotsProvider(() -> availableSpots(exp));
				spotFrame.setSpotExclusiveSelectionController(spot -> selectSpot(exp, spot));
				spotFrame.createMainChartPanel(title(), options);
				spotFrame.setChartUpperLeftLocation(position(exp));
			}
			spotFrame.displayData(exp, options, selected);
		}

		private String title() { return family.label + (summary ? " - Cage summaries" : " - Spot measurements"); }

		private void openAxes() {
			if (!isVisibleChart()) { status.setText("Display a chart first."); return; }
			if (axes != null) axes.close();
			axes = new AxisOptions();
			if (spotFrame != null) axes.initialize(owner, spotFrame::getChartPanel);
			else axes.initialize(owner, cageFrame);
			axes.requestFocus();
		}

		private void close() {
			if (axes != null) axes.close();
			axes = null;
			if (cageFrame != null && cageFrame.getMainChartFrame() != null) cageFrame.getMainChartFrame().dispose();
			cageFrame = null;
			if (spotFrame != null) spotFrame.dispose();
			spotFrame = null;
		}
	}

	private static JPanel row() { return new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0)); }

	/** Initial chart target; later "Selected ROI(s)" refreshes use the actual ROI selection. */
	static List<Spot> initialSpots(Experiment exp) {
		// Deduplication returns an immutable empty list when no ROI is selected.
		List<Spot> selected = new ArrayList<>(selectedSpots(exp));
		if (selected.isEmpty()) {
			List<Spot> available = availableSpots(exp);
			if (!available.isEmpty()) selected.add(available.get(0));
		}
		return selected;
	}

	private static List<Spot> availableSpots(Experiment exp) {
		List<Spot> spots = new ArrayList<>();
		for (Cage cage : exp.getCages().getCageList()) spots.addAll(cage.getSpotList(exp.getSpots()));
		return ChartSpotsOverlayFrame.dedupeSpots(spots);
	}

	/** No fallback: the "Selected ROI(s)" choice must reflect the actual selection. */
	private static List<Spot> selectedSpots(Experiment exp) {
		List<Spot> selected = new ArrayList<>();
		if (exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null) return selected;
		for (ROI2D roi : exp.getSeqCamData().getSequence().getROI2Ds()) {
			if (roi == null || !roi.isSelected() || roi.getName() == null
					|| !roi.getName().toLowerCase(java.util.Locale.ROOT).startsWith("spot")) continue;
			Spot spot = exp.getCages().getSpotFromROIName(roi.getName(), exp.getSpots());
			if (spot != null) selected.add(spot);
		}
		return ChartSpotsOverlayFrame.dedupeSpots(selected);
	}

	private void selectSpot(Experiment exp, Spot spot) {
		if (exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null) {
			Sequence sequence = exp.getSeqCamData().getSequence();
			ROI2D target = null;
			for (ROI2D roi : sequence.getROI2Ds()) {
				if (roi.getName() == null || !roi.getName().toLowerCase(java.util.Locale.ROOT).startsWith("spot")) continue;
				boolean match = roi.getName().equals(spot.getName());
				roi.setSelected(match);
				if (match) target = roi;
			}
			if (target != null) {
				sequence.setSelectedROI(target);
				exp.getSeqCamData().centerDisplayOnRoi(target);
			}
		}
		owner.dlgSpots.onMeasureChartSpotClicked(spot);
	}

	private Rectangle position(Experiment exp) {
		Rectangle rect = owner.mainFrame.getBounds();
		if (exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null) {
			Viewer viewer = exp.getSeqCamData().getSequence().getFirstViewer();
			if (viewer != null) rect = viewer.getBounds();
		}
		rect.translate(5, rect.height + 10);
		return rect;
	}

	private static final class CageChoice {
		final Cage value;
		CageChoice(Cage value) { this.value = value; }
		@Override public String toString() { return "Cage " + value.getCageID(); }
	}
}
