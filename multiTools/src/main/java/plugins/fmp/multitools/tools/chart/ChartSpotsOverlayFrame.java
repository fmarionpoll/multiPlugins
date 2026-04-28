package plugins.fmp.multitools.tools.chart;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.data.xy.XYDataItem;

import icy.gui.frame.IcyFrame;
import icy.gui.util.GuiUtil;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotMeasure;
import plugins.fmp.multitools.tools.chart.interaction.SpotOverlayChartInteractionHandler;
import plugins.fmp.multitools.tools.chart.style.SeriesStyleCodec;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

public class ChartSpotsOverlayFrame {
	public interface SelectedSpotsProvider {
		List<Spot> getSelectedSpots();
	}

	public interface AvailableSpotsProvider {
		List<Spot> getAvailableSpots();
	}

	public interface SpotExclusiveSelectionController {
		void selectExclusiveSpot(Spot spot);
	}

	private enum SpotChoiceMode {
		SEQUENCE_SELECTION, FIXED_SPOT
	}

	private static final class SpotChoice {
		final SpotChoiceMode mode;
		final Spot spot;

		private SpotChoice(SpotChoiceMode mode, Spot spot) {
			this.mode = Objects.requireNonNull(mode, "mode");
			this.spot = spot;
		}

		static SpotChoice sequenceSelection() {
			return new SpotChoice(SpotChoiceMode.SEQUENCE_SELECTION, null);
		}

		static SpotChoice fixedSpot(Spot spot) {
			return new SpotChoice(SpotChoiceMode.FIXED_SPOT, spot);
		}

		String getLabel() {
			switch (mode) {
			case SEQUENCE_SELECTION:
				return "Selected ROI(s)";
			case FIXED_SPOT:
				return spot != null && spot.getName() != null ? spot.getName() : "(spot)";
			default:
				return "";
			}
		}

		@Override
		public String toString() {
			return getLabel();
		}

		String getKey() {
			return mode == SpotChoiceMode.SEQUENCE_SELECTION ? "Selected ROI(s)"
					: (spot != null && spot.getName() != null ? "spot:" + spot.getName() : "spot:(null)");
		}
	}

	private IcyFrame mainChartFrame = null;
	private JPanel mainChartPanel = null;
	private ChartPanel chartPanel = null;

	private JPanel topControlsPanel = null;
	private JCheckBox cbSum = new JCheckBox("sum");
	private JCheckBox cbNoFly = new JCheckBox("sumNoFly");
	private JCheckBox cbClean = new JCheckBox("clean");
	private JCheckBox cbFlyPresent = new JCheckBox("flyPresent");
	private JComboBox<SpotChoice> spotSelectorCombo = null;
	private JButton updateButton = new JButton("Update");

	private String baseTitle = null;
	private Experiment lastExperiment = null;
	private ResultsOptions lastOptions = null;
	private List<Spot> lastSelectedSpots = null;
	private SelectedSpotsProvider selectedSpotsProvider = null;
	private AvailableSpotsProvider availableSpotsProvider = null;
	private SpotExclusiveSelectionController spotExclusiveSelectionController = null;
	private boolean isUpdatingSpotComboModel = false;
	private boolean isUpdatingMeasureCheckboxes = false;

	public void setSelectedSpotsProvider(SelectedSpotsProvider provider) {
		this.selectedSpotsProvider = provider;
	}

	public void setAvailableSpotsProvider(AvailableSpotsProvider provider) {
		this.availableSpotsProvider = provider;
	}

	public void setSpotExclusiveSelectionController(SpotExclusiveSelectionController controller) {
		this.spotExclusiveSelectionController = controller;
	}

	public void createMainChartPanel(String title, ResultsOptions options) {
		if (title == null || title.trim().isEmpty())
			throw new IllegalArgumentException("Title cannot be null or empty");
		if (options == null)
			throw new IllegalArgumentException("ResultsOptions cannot be null");

		this.baseTitle = title;

		mainChartPanel = new JPanel(new BorderLayout());
		String finalTitle = title + ": " + (options.resultType != null ? options.resultType.toString() : "");
		if (mainChartFrame != null && (mainChartFrame.getParent() != null || mainChartFrame.isVisible())) {
			mainChartFrame.setTitle(finalTitle);
			mainChartFrame.removeAll();
		} else {
			mainChartFrame = GuiUtil.generateTitleFrame(finalTitle, new JPanel(), new Dimension(500, 200), true, true,
					true, true);
		}
		mainChartFrame.setLayout(new BorderLayout());
		topControlsPanel = createTopControlsPanel(options);
		mainChartFrame.add(topControlsPanel, BorderLayout.NORTH);
		mainChartFrame.add(new JScrollPane(mainChartPanel), BorderLayout.CENTER);
	}

	public void displayData(Experiment exp, ResultsOptions options, List<Spot> selectedSpots) {
		if (mainChartPanel == null || mainChartFrame == null)
			throw new IllegalStateException("createMainChartPanel must be called first");
		if (exp == null || options == null || selectedSpots == null || selectedSpots.isEmpty())
			return;

		this.lastExperiment = exp;
		this.lastOptions = options;
		this.lastSelectedSpots = selectedSpots;

		refreshChart();

		mainChartFrame.pack();
		if (mainChartFrame.getParent() == null) {
			mainChartFrame.addToDesktopPane();
		}
		mainChartFrame.setVisible(true);
		mainChartFrame.toFront();
		mainChartFrame.requestFocus();
	}

	public void setChartUpperLeftLocation(Rectangle rect) {
		if (rect == null)
			return;
		if (mainChartFrame != null) {
			mainChartFrame.setLocation(rect.getLocation());
		}
	}

	public IcyFrame getMainChartFrame() {
		return mainChartFrame;
	}

	public void dispose() {
		if (mainChartFrame != null) {
			mainChartFrame.dispose();
		}
		mainChartFrame = null;
		mainChartPanel = null;
		chartPanel = null;
	}

	private JPanel createTopControlsPanel(ResultsOptions options) {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));

		setCheckboxDefaults(options);

		spotSelectorCombo = new JComboBox<>();
		refreshSpotSelectorModel(false);
		spotSelectorCombo.addActionListener(e -> onSpotSelectorChanged());

		panel.add(cbSum);
		panel.add(cbNoFly);
		panel.add(cbClean);
		panel.add(cbFlyPresent);
		panel.add(spotSelectorCombo);
		panel.add(updateButton);

		updateButton.addActionListener(e -> refreshChart());

		cbSum.addActionListener(e -> onMeasureCheckboxChanged());
		cbNoFly.addActionListener(e -> onMeasureCheckboxChanged());
		cbClean.addActionListener(e -> onMeasureCheckboxChanged());
		cbFlyPresent.addActionListener(e -> onMeasureCheckboxChanged());

		return panel;
	}

	private void onMeasureCheckboxChanged() {
		if (isUpdatingMeasureCheckboxes) {
			return;
		}
		isUpdatingMeasureCheckboxes = true;
		try {
			maybeEnforceSingleMeasureSelection();
		} finally {
			isUpdatingMeasureCheckboxes = false;
		}
		refreshChart();
	}

	private void setCheckboxDefaults(ResultsOptions options) {
		EnumResults rt = options != null ? options.resultType : null;
		cbSum.setSelected(resultLabelEquals(rt, "AREA_SUM"));
		cbNoFly.setSelected(resultLabelEquals(rt, "AREA_SUMNOFLY"));
		cbClean.setSelected(resultLabelEquals(rt, "AREA_SUMCLEAN"));
		cbFlyPresent.setSelected(resultLabelEquals(rt, "AREA_FLYPRESENT"));
		if (!cbSum.isSelected() && !cbNoFly.isSelected() && !cbClean.isSelected() && !cbFlyPresent.isSelected()) {
			cbClean.setSelected(true);
		}
	}

	private static boolean resultLabelEquals(EnumResults r, String label) {
		return r != null && label.equals(r.toString());
	}

	private static boolean isAreaFlyPresent(EnumResults r) {
		return r != null && "AREA_FLYPRESENT".equals(r.toString());
	}

	private static EnumResults findResultByLabel(String label) {
		return EnumResults.findByText(label);
	}

	private void maybeEnforceSingleMeasureSelection() {
		if (lastSelectedSpots == null || lastSelectedSpots.size() <= 1) {
			return;
		}
		// Several spots selected => only one measure can be selected (overlay spots).
		int n = countSelectedMeasures();
		if (n <= 1)
			return;

		// Keep the first checked in a stable priority order.
		JCheckBox keep = cbClean.isSelected() ? cbClean
				: cbNoFly.isSelected() ? cbNoFly : cbSum.isSelected() ? cbSum : cbFlyPresent;
		cbSum.setSelected(keep == cbSum);
		cbNoFly.setSelected(keep == cbNoFly);
		cbClean.setSelected(keep == cbClean);
		cbFlyPresent.setSelected(keep == cbFlyPresent);
	}

	private int countSelectedMeasures() {
		int n = 0;
		if (cbSum.isSelected())
			n++;
		if (cbNoFly.isSelected())
			n++;
		if (cbClean.isSelected())
			n++;
		if (cbFlyPresent.isSelected())
			n++;
		return n;
	}

	private void onSpotSelectorChanged() {
		if (isUpdatingSpotComboModel)
			return;
		SpotChoice choice = getCurrentSpotChoice();
		if (choice == null)
			return;
		if (choice.mode == SpotChoiceMode.FIXED_SPOT) {
			if (choice.spot != null) {
				if (spotExclusiveSelectionController != null) {
					try {
						spotExclusiveSelectionController.selectExclusiveSpot(choice.spot);
					} catch (Exception e) {
						// ignore controller errors; chart can still refresh from internal selection
					}
				}
				List<Spot> fixed = new ArrayList<>();
				fixed.add(choice.spot);
				lastSelectedSpots = fixed;
			}
		} else {
			// Switch back to follow-selection mode.
			List<Spot> refreshed = getSelectedSpotsFromProvider();
			if (refreshed != null && !refreshed.isEmpty()) {
				lastSelectedSpots = refreshed;
			}
		}
		refreshChart();
	}

	private SpotChoice getCurrentSpotChoice() {
		return spotSelectorCombo != null ? (SpotChoice) spotSelectorCombo.getSelectedItem() : null;
	}

	private List<Spot> getSelectedSpotsFromProvider() {
		if (selectedSpotsProvider == null)
			return null;
		try {
			return selectedSpotsProvider.getSelectedSpots();
		} catch (Exception e) {
			return null;
		}
	}

	private List<Spot> getAvailableSpotsFromProvider() {
		if (availableSpotsProvider == null)
			return null;
		try {
			return availableSpotsProvider.getAvailableSpots();
		} catch (Exception e) {
			return null;
		}
	}

	private void refreshSpotSelectorModel(boolean keepSelection) {
		if (spotSelectorCombo == null)
			return;
		isUpdatingSpotComboModel = true;
		try {
			String previousKey = null;
			if (keepSelection) {
				SpotChoice previous = getCurrentSpotChoice();
				previousKey = previous != null ? previous.getKey() : null;
			}

			spotSelectorCombo.removeAllItems();
			spotSelectorCombo.addItem(SpotChoice.sequenceSelection());

			List<Spot> spots = dedupeSpots(getAvailableSpotsFromProvider());
			List<Spot> sorted = new ArrayList<>(spots);
			Collections.sort(sorted, Comparator.comparing(s -> s != null ? safeLower(s.getName()) : "", String::compareTo));
			for (Spot s : sorted) {
				if (s == null)
					continue;
				spotSelectorCombo.addItem(SpotChoice.fixedSpot(s));
			}

			if (previousKey != null) {
				for (int i = 0; i < spotSelectorCombo.getItemCount(); i++) {
					SpotChoice sc = spotSelectorCombo.getItemAt(i);
					if (sc != null && previousKey.equals(sc.getKey())) {
						spotSelectorCombo.setSelectedIndex(i);
						return;
					}
				}
			}
			spotSelectorCombo.setSelectedIndex(0);
		} finally {
			isUpdatingSpotComboModel = false;
		}
	}

	private void refreshChart() {
		if (mainChartPanel == null || mainChartFrame == null || lastExperiment == null || lastOptions == null
				|| lastSelectedSpots == null || lastSelectedSpots.isEmpty()) {
			return;
		}

		refreshSpotSelectorModel(true);

		SpotChoice choice = getCurrentSpotChoice();
		boolean followSelection = (choice == null || choice.mode == SpotChoiceMode.SEQUENCE_SELECTION);

		if (followSelection) {
			List<Spot> refreshed = getSelectedSpotsFromProvider();
			if (refreshed != null && !refreshed.isEmpty()) {
				lastSelectedSpots = refreshed;
			}
		} else if (choice != null && choice.mode == SpotChoiceMode.FIXED_SPOT && choice.spot != null) {
			List<Spot> fixed = new ArrayList<>();
			fixed.add(choice.spot);
			lastSelectedSpots = fixed;
		}

		maybeEnforceSingleMeasureSelection();

		mainChartPanel.removeAll();

		List<EnumResults> enabledMeasures = getEnabledMeasures(lastOptions);
		if (enabledMeasures.isEmpty() && mainChartPanel != null) {
			mainChartPanel.revalidate();
			mainChartPanel.repaint();
			return;
		}

		boolean overlaySpots = lastSelectedSpots.size() > 1;
		boolean overlayMeasures = !overlaySpots && enabledMeasures.size() > 1;

		// Build datasets. When FLYPRESENT is shown together with continuous measures,
		// put it on its own Y axis.
		boolean flyEnabled = enabledMeasures.stream().anyMatch(ChartSpotsOverlayFrame::isAreaFlyPresent);
		boolean hasContinuous = enabledMeasures.stream().anyMatch(t -> !isAreaFlyPresent(t));
		boolean splitFlyAxis = flyEnabled && hasContinuous;

		NumberAxis xAxis = new NumberAxis("time (min)");
		xAxis.setAutoRangeIncludesZero(false);

		NumberAxis yAxis0 = new NumberAxis("");
		yAxis0.setAutoRangeIncludesZero(false);

		XYPlot plot = new XYPlot(null, xAxis, yAxis0, null);

		if (splitFlyAxis) {
			XYSeriesCollection ds0 = buildDatasetForMeasures(lastExperiment, lastOptions, lastSelectedSpots,
					enabledMeasures, false, overlaySpots, overlayMeasures);
			XYLineAndShapeRenderer r0 = createRenderer(ds0);
			plot.setDataset(0, ds0);
			plot.setRenderer(0, r0);

			yAxis0.setAutoRange(false);
			yAxis0.setRange(computePaddedRangeFromZero(ds0, 1.0, 0.05, 0.05));

			XYSeriesCollection ds1 = buildDatasetForMeasures(lastExperiment, lastOptions, lastSelectedSpots,
					enabledMeasures, true, overlaySpots, overlayMeasures);
			XYLineAndShapeRenderer r1 = createRenderer(ds1);
			EnumResults flyPresentType = findResultByLabel("AREA_FLYPRESENT");
			NumberAxis yAxis1 = new NumberAxis(flyPresentType != null ? flyPresentType.toUnit() : "");
			yAxis1.setInverted(true);
			yAxis1.setAutoRange(false);
			yAxis1.setRange(computePaddedRange(ds1, 0.0, 1.2, 0.05, 0.05));
			plot.setRangeAxis(1, yAxis1);
			plot.setDataset(1, ds1);
			plot.setRenderer(1, r1);
			plot.mapDatasetToRangeAxis(1, 1);

			yAxis0.setLabel("area" + lastOptions.resultType.toUnit());
		} else {
			XYSeriesCollection ds = buildDatasetForMeasures(lastExperiment, lastOptions, lastSelectedSpots,
					enabledMeasures, flyEnabled && !hasContinuous, overlaySpots, overlayMeasures);
			plot.setDataset(0, ds);
			plot.setRenderer(0, createRenderer(ds));

			EnumResults labelType = flyEnabled && !hasContinuous ? findResultByLabel("AREA_FLYPRESENT")
					: lastOptions.resultType;
			yAxis0.setLabel(labelType != null ? labelType.toUnit() : "");
			if (flyEnabled && !hasContinuous) {
				yAxis0.setAutoRange(false);
				yAxis0.setRange(computePaddedRange(ds, 0.0, 1.2, 0.05, 0.05));
			} else {
				yAxis0.setAutoRange(false);
				yAxis0.setRange(computePaddedRangeFromZero(ds, 1.0, 0.05, 0.05));
			}
		}

		JFreeChart chart = new JFreeChart(plot);
		if (baseTitle != null) {
			mainChartFrame.setTitle(baseTitle);
		}

		chartPanel = new ChartPanel(chart, 900, 500, 300, 200, 2000, 2000, true, true, true, true, false, true);
		chartPanel.addChartMouseListener(
				new SpotOverlayChartInteractionHandler(lastExperiment, lastOptions).createMouseListener());
		mainChartPanel.add(chartPanel, BorderLayout.CENTER);

		mainChartPanel.revalidate();
		mainChartPanel.repaint();
	}

	private List<EnumResults> getEnabledMeasures(ResultsOptions options) {
		List<EnumResults> out = new ArrayList<>();
		if (cbSum.isSelected())
			addResultIfDefined(out, "AREA_SUM");
		if (cbNoFly.isSelected())
			addResultIfDefined(out, "AREA_SUMNOFLY");
		if (cbClean.isSelected())
			addResultIfDefined(out, "AREA_SUMCLEAN");
		if (cbFlyPresent.isSelected())
			addResultIfDefined(out, "AREA_FLYPRESENT");
		return out;
	}

	private static void addResultIfDefined(List<EnumResults> out, String label) {
		EnumResults v = findResultByLabel(label);
		if (v != null) {
			out.add(v);
		}
	}

	private XYSeriesCollection buildDatasetForMeasures(Experiment exp, ResultsOptions options, List<Spot> selectedSpots,
			List<EnumResults> enabledMeasures, boolean flyOnly, boolean overlaySpots, boolean overlayMeasures) {
		XYSeriesCollection dataset = new XYSeriesCollection();
		if (overlaySpots) {
			EnumResults chosen = enabledMeasures.isEmpty() ? options.resultType : enabledMeasures.get(0);
			if (chosen == null)
				return dataset;
			if (flyOnly && !isAreaFlyPresent(chosen))
				return dataset;
			if (!flyOnly && isAreaFlyPresent(chosen))
				return dataset;
			for (int i = 0; i < selectedSpots.size(); i++) {
				Spot spot = selectedSpots.get(i);
				XYSeries series = createXYSeriesFromSpotMeasure(exp, spot, options, chosen, spot.getName());
				if (series == null)
					continue;
				applySpotStyle(exp, spot, series, pickSpotColor(i));
				dataset.addSeries(series);
			}
			return dataset;
		}

		// Single spot
		Spot spot = selectedSpots.get(0);
		if (spot == null)
			return dataset;

		// If only one measure is enabled, show a single curve for that measure.
		// If several measures are enabled, overlay measures (different colors).
		for (EnumResults resultType : enabledMeasures) {
			if (resultType == null)
				continue;
			if (flyOnly && !isAreaFlyPresent(resultType))
				continue;
			if (!flyOnly && isAreaFlyPresent(resultType))
				continue;

			Color color = pickMeasureColor(resultType);
			XYSeries series = createXYSeriesFromSpotMeasure(exp, spot, options, resultType,
					spot.getName() + "::" + resultType.name());
			if (series == null)
				continue;
			applySpotStyle(exp, spot, series, color);
			dataset.addSeries(series);
		}
		return dataset;
	}

	private Color pickSpotColor(int index) {
		Color[] palette = new Color[] { new Color(0, 0, 0), new Color(0, 102, 204), new Color(0, 153, 0),
				new Color(204, 102, 0), new Color(153, 0, 153), new Color(0, 153, 153), new Color(153, 102, 0),
				new Color(102, 102, 102) };
		if (index < 0)
			index = 0;
		return palette[index % palette.length];
	}

	private Color pickMeasureColor(EnumResults resultType) {
		if (resultType == null)
			return Color.BLACK;
		switch (resultType.name()) {
		case "AREA_SUM":
			return new Color(0, 0, 0);
		case "AREA_SUMNOFLY":
			return new Color(0, 102, 204);
		case "AREA_SUMCLEAN":
			return new Color(0, 153, 0);
		case "AREA_FLYPRESENT":
			return new Color(153, 0, 153);
		default:
			return Color.BLACK;
		}
	}

	private XYLineAndShapeRenderer createRenderer(XYSeriesCollection dataset) {
		XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
		SeriesStyleCodec.applySeriesPaintsFromDescription(dataset, renderer);
		return renderer;
	}

	private static void applySpotStyle(Experiment exp, Spot spot, XYSeries series, Color color) {
		if (exp == null || exp.getCages() == null || exp.getSpots() == null || spot == null || series == null)
			return;
		Cage cage = exp.getCages().getCageFromSpotROIName(spot.getName(), exp.getSpots());
		if (cage == null || cage.getProperties() == null || spot.getProperties() == null)
			return;
		series.setDescription(SeriesStyleCodec.buildDescription(cage.getProperties().getCageID(),
				cage.getProperties().getCageID(), cage.getProperties().getCageNFlies(), color));
	}

	private static XYSeries createXYSeriesFromSpotMeasure(Experiment exp, Spot spot, ResultsOptions baseOptions,
			EnumResults resultType, String seriesKey) {
		if (exp == null || spot == null || baseOptions == null || resultType == null || seriesKey == null)
			return null;

		XYSeries seriesXY = new XYSeries(seriesKey, false);

		if (exp.getSeqCamData().getTimeManager().getCamImagesTime_Ms() == null)
			exp.getSeqCamData().build_MsTimesArray_From_FileNamesList();
		double[] camImages_time_min = exp.getSeqCamData().getTimeManager().getCamImagesTime_Minutes();

		SpotMeasure spotMeasure = spot.getMeasurements(resultType);
		if (spotMeasure == null)
			return null;

		double divider = 1.0;
		if (baseOptions.relativeToMaximum && !isAreaFlyPresent(resultType)) {
			divider = spotMeasure.getMaximumValue();
			if (divider == 0)
				divider = 1.0;
		}

		int npoints = spotMeasure.getCount();
		if (camImages_time_min != null && npoints > camImages_time_min.length)
			npoints = camImages_time_min.length;

		for (int j = 0; j < npoints; j++) {
			double x = camImages_time_min != null ? camImages_time_min[j] : j;
			double y = spotMeasure.getValueAt(j) / divider;
			seriesXY.add(x, y);
		}
		return seriesXY;
	}

	public static List<Spot> dedupeSpots(List<Spot> spots) {
		if (spots == null || spots.isEmpty())
			return Collections.emptyList();
		List<Spot> out = new ArrayList<>();
		for (Spot s : spots) {
			if (s == null)
				continue;
			boolean seen = false;
			for (Spot o : out) {
				if (o != null && o.getName() != null && o.getName().equals(s.getName())) {
					seen = true;
					break;
				}
			}
			if (!seen)
				out.add(s);
		}
		return out;
	}

	private static String safeLower(String s) {
		return s != null ? s.toLowerCase() : "";
	}

	private static org.jfree.data.Range computePaddedRange(XYSeriesCollection dataset, double defaultLower,
			double defaultUpper, double padFraction, double padMinAbs) {
		if (dataset == null || dataset.getSeriesCount() == 0) {
			return new org.jfree.data.Range(defaultLower, defaultUpper);
		}
		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
		boolean hasValue = false;

		for (int s = 0; s < dataset.getSeriesCount(); s++) {
			XYSeries series = dataset.getSeries(s);
			if (series == null) {
				continue;
			}
			for (int i = 0; i < series.getItemCount(); i++) {
				XYDataItem item = series.getDataItem(i);
				if (item == null || item.getY() == null) {
					continue;
				}
				double y = item.getY().doubleValue();
				if (!Double.isFinite(y)) {
					continue;
				}
				hasValue = true;
				if (y < min)
					min = y;
				if (y > max)
					max = y;
			}
		}

		if (!hasValue) {
			return new org.jfree.data.Range(defaultLower, defaultUpper);
		}

		if (min == max) {
			double pad = Math.max(padMinAbs, Math.abs(min) * padFraction);
			if (pad == 0) {
				pad = padMinAbs > 0 ? padMinAbs : 0.1;
			}
			return new org.jfree.data.Range(min - pad, max + pad);
		}

		double span = max - min;
		double pad = Math.max(padMinAbs, span * padFraction);
		return new org.jfree.data.Range(min - pad, max + pad);
	}

	private static org.jfree.data.Range computePaddedRangeFromZero(XYSeriesCollection dataset, double defaultUpper,
			double padFraction, double padMinAbs) {
		if (dataset == null || dataset.getSeriesCount() == 0) {
			return new org.jfree.data.Range(0.0, defaultUpper);
		}
		double max = Double.NEGATIVE_INFINITY;
		boolean hasValue = false;

		for (int s = 0; s < dataset.getSeriesCount(); s++) {
			XYSeries series = dataset.getSeries(s);
			if (series == null) {
				continue;
			}
			for (int i = 0; i < series.getItemCount(); i++) {
				XYDataItem item = series.getDataItem(i);
				if (item == null || item.getY() == null) {
					continue;
				}
				double y = item.getY().doubleValue();
				if (!Double.isFinite(y)) {
					continue;
				}
				hasValue = true;
				if (y > max)
					max = y;
			}
		}

		if (!hasValue) {
			return new org.jfree.data.Range(0.0, defaultUpper);
		}

		if (max <= 0) {
			return new org.jfree.data.Range(0.0, Math.max(defaultUpper, 1.0));
		}

		double pad = Math.max(padMinAbs, Math.abs(max) * padFraction);
		return new org.jfree.data.Range(0.0, max + pad);
	}
}
