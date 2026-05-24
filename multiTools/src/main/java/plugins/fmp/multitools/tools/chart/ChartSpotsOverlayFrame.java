package plugins.fmp.multitools.tools.chart;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.prefs.Preferences;

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
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import icy.gui.frame.IcyFrame;
import icy.gui.util.GuiUtil;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotMeasure;
import plugins.fmp.multitools.experiment.spot.SpotPreConsumedSupport;
import plugins.fmp.multitools.tools.chart.builders.SpotChartSeriesKeys;
import plugins.fmp.multitools.tools.chart.interaction.SpotOverlayChartInteractionHandler;
import plugins.fmp.multitools.tools.chart.style.SeriesStyleCodec;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

public class ChartSpotsOverlayFrame {

	private static final int DEFAULT_FRAME_WIDTH = 500;
	private static final int DEFAULT_FRAME_HEIGHT = 200;

	public interface SelectedSpotsProvider {
		List<Spot> getSelectedSpots();
	}

	public interface AvailableSpotsProvider {
		List<Spot> getAvailableSpots();
	}

	public interface SpotExclusiveSelectionController {
		void selectExclusiveSpot(Spot spot);
	}

	protected enum SpotChoiceMode {
		SEQUENCE_SELECTION, FIXED_SPOT
	}

	protected static final class SpotChoice {
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

	private Point graphLocation = new Point(0, 0);
	private ChartPanel chartPanel = null;

	private JPanel topControlsPanel = null;
	protected JCheckBox cbSum = new JCheckBox("sum");
	protected JCheckBox cbNoFly = new JCheckBox("sumNoFly");
	protected JCheckBox cbClean = new JCheckBox("clean");
	protected JCheckBox cbCleanV3 = new JCheckBox("cleanV3");
//	private JCheckBox cbSumV2 = new JCheckBox("sumV2");
//	private JCheckBox cbNoFlyV2 = new JCheckBox("sumNoFlyV2");
//	private JCheckBox cbCleanV2 = new JCheckBox("cleanV2");
	protected JCheckBox cbFlyPresent = new JCheckBox("flyPresent");
	protected JCheckBox cbDepletion = new JCheckBox("(max-v)/max");
	protected JComboBox<SpotChoice> spotSelectorCombo = null;
	protected JButton updateButton = new JButton("Update");

	private String baseTitle = null;
	protected Experiment lastExperiment = null;
	protected ResultsOptions lastOptions = null;
	protected List<Spot> lastSelectedSpots = null;
	protected SelectedSpotsProvider selectedSpotsProvider = null;
	protected AvailableSpotsProvider availableSpotsProvider = null;
	protected SpotExclusiveSelectionController spotExclusiveSelectionController = null;
	protected boolean isUpdatingSpotComboModel = false;
	protected boolean isUpdatingMeasureCheckboxes = false;

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
			mainChartFrame = GuiUtil.generateTitleFrame(finalTitle, new JPanel(),
					new Dimension(DEFAULT_FRAME_WIDTH, DEFAULT_FRAME_HEIGHT), true, true, true, true);
		}
		mainChartFrame.setLayout(new BorderLayout());
		topControlsPanel = createTopControlsPanel(options);
		mainChartFrame.add(topControlsPanel, BorderLayout.NORTH);
		mainChartFrame.add(new JScrollPane(mainChartPanel), BorderLayout.CENTER);

		mainChartFrame.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				saveOverlayWindowPreferences();
			}

			@Override
			public void componentMoved(ComponentEvent e) {
				saveOverlayWindowPreferences();
			}
		});
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
		loadOverlayWindowPreferences();
		if (mainChartFrame.getParent() == null) {
			mainChartFrame.addToDesktopPane();
		}
		mainChartFrame.setVisible(true);
		mainChartFrame.toFront();
		mainChartFrame.requestFocus();
	}

	public void setChartUpperLeftLocation(Rectangle rect) {
		if (rect == null) {
			return;
		}
		graphLocation = new Point(rect.x, rect.y);
		if (mainChartFrame != null) {
			mainChartFrame.setLocation(graphLocation);
		}
	}

	private void loadOverlayWindowPreferences() {
		if (mainChartFrame == null) {
			return;
		}
		Preferences prefs = Preferences.userNodeForPackage(getClass());
		int x = prefs.getInt("window_x", graphLocation.x);
		int y = prefs.getInt("window_y", graphLocation.y);
		int w = prefs.getInt("window_w", DEFAULT_FRAME_WIDTH);
		int h = prefs.getInt("window_h", DEFAULT_FRAME_HEIGHT);
		mainChartFrame.setBounds(new Rectangle(x, y, w, h));
	}

	private void saveOverlayWindowPreferences() {
		if (mainChartFrame == null) {
			return;
		}
		Preferences prefs = Preferences.userNodeForPackage(getClass());
		Rectangle r = mainChartFrame.getBounds();
		prefs.putInt("window_x", r.x);
		prefs.putInt("window_y", r.y);
		prefs.putInt("window_w", r.width);
		prefs.putInt("window_h", r.height);
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

	protected JPanel createTopControlsPanel(ResultsOptions options) {
		JPanel panel = new JPanel(new BorderLayout());
		JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));

		setCheckboxDefaults(options);

		spotSelectorCombo = new JComboBox<>();
		refreshSpotSelectorModel(false);
		spotSelectorCombo.addActionListener(e -> onSpotSelectorChanged());

		row1.add(cbSum);
		row1.add(cbNoFly);
		row1.add(cbClean);
		row1.add(cbCleanV3);
		row1.add(cbFlyPresent);
		row1.add(cbDepletion);
		row1.add(spotSelectorCombo);
		row1.add(updateButton);

		panel.add(row1, BorderLayout.NORTH);
		panel.add(row2, BorderLayout.SOUTH);

		updateButton.addActionListener(e -> refreshChart());

		cbSum.addActionListener(e -> onMeasureCheckboxChanged());
		cbNoFly.addActionListener(e -> onMeasureCheckboxChanged());
		cbClean.addActionListener(e -> onMeasureCheckboxChanged());
		cbCleanV3.addActionListener(e -> onMeasureCheckboxChanged());
		cbFlyPresent.addActionListener(e -> onMeasureCheckboxChanged());
		cbDepletion.addActionListener(e -> refreshChart());

		return panel;
	}

	protected void onMeasureCheckboxChanged() {
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

	protected void setCheckboxDefaults(ResultsOptions options) {
		EnumResults rt = options != null ? options.resultType : null;
		if (isV5NativeSpotChartMeasure(rt)) {
			cbSum.setSelected(false);
			cbNoFly.setSelected(false);
			cbClean.setSelected(false);
			cbCleanV3.setSelected(false);
			cbFlyPresent.setSelected(false);
			return;
		}
		cbSum.setSelected(resultLabelEquals(rt, "AREA_SUM"));
		cbNoFly.setSelected(resultLabelEquals(rt, "AREA_SUMNOFLY"));
		cbClean.setSelected(resultLabelEquals(rt, "AREA_SUMCLEAN"));
		cbCleanV3.setSelected(resultLabelEquals(rt, "AREA_SUMCLEAN_V3"));
//		cbSumV2.setSelected(resultLabelEquals(rt, "AREA_SUM_V2"));
//		cbNoFlyV2.setSelected(resultLabelEquals(rt, "AREA_SUMNOFLY_V2"));
//		cbCleanV2.setSelected(resultLabelEquals(rt, "AREA_SUMCLEAN_V2"));
		cbFlyPresent.setSelected(resultLabelEquals(rt, "AREA_FLYPRESENT"));
		if (!cbSum.isSelected() && !cbNoFly.isSelected() && !cbClean.isSelected() && !cbCleanV3.isSelected() //
//				&& !cbSumV2.isSelected()&& !cbNoFlyV2.isSelected() && !cbCleanV2.isSelected() //
				&& !cbFlyPresent.isSelected()) {
			cbClean.setSelected(true);
		}
	}

	/** V5/V6 per-spot scalars: chart uses {@link ResultsOptions#resultType}. */
	protected static boolean isV5NativeSpotChartMeasure(EnumResults rt) {
		return rt == EnumResults.AREA_COUNT_V5 || rt == EnumResults.GREY_SUM_V5 || rt == EnumResults.GREY_SUM_V5_PREFLY
				|| rt == EnumResults.GREY_SUM_CLEAN_V5 || rt == EnumResults.AREA_COUNT_V6
				|| rt == EnumResults.GREY_SUM_V6 || rt == EnumResults.GREY_SUM_V6_PREFLY
				|| rt == EnumResults.GREY_SUM_CLEAN_V6;
	}

	protected static boolean resultLabelEquals(EnumResults r, String label) {
		return r != null && label.equals(r.toString());
	}

	private static boolean isAreaFlyPresent(EnumResults r) {
		return r != null && "AREA_FLYPRESENT".equals(r.toString());
	}

	private static EnumResults findResultByLabel(String label) {
		return EnumResults.findByText(label);
	}

	protected void maybeEnforceSingleMeasureSelection() {
		if (lastSelectedSpots == null || lastSelectedSpots.size() <= 1) {
			return;
		}
		// Several spots selected => only one measure can be selected (overlay spots).
		int n = countSelectedMeasures();
		if (n <= 1)
			return;

		// Keep the first checked in a stable priority order.
		JCheckBox keep = // cbCleanV2.isSelected() ? cbCleanV2
				// : cbNoFlyV2.isSelected() ? cbNoFlyV2
				// : cbSumV2.isSelected() ? cbSumV2
				// : //
				cbCleanV3.isSelected() ? cbCleanV3
						: cbClean.isSelected() ? cbClean
								: cbNoFly.isSelected() ? cbNoFly : cbSum.isSelected() ? cbSum : cbFlyPresent;
		cbSum.setSelected(keep == cbSum);
		cbNoFly.setSelected(keep == cbNoFly);
		cbClean.setSelected(keep == cbClean);
		cbCleanV3.setSelected(keep == cbCleanV3);
//		cbSumV2.setSelected(keep == cbSumV2);
//		cbNoFlyV2.setSelected(keep == cbNoFlyV2);
//		cbCleanV2.setSelected(keep == cbCleanV2);
		cbFlyPresent.setSelected(keep == cbFlyPresent);
	}

	protected int countSelectedMeasures() {
		int n = 0;
		if (cbSum.isSelected())
			n++;
		if (cbNoFly.isSelected())
			n++;
		if (cbClean.isSelected())
			n++;
		if (cbCleanV3.isSelected())
			n++;
//		if (cbSumV2.isSelected())
//			n++;
//		if (cbNoFlyV2.isSelected())
//			n++;
//		if (cbCleanV2.isSelected())
//			n++;
		if (cbFlyPresent.isSelected())
			n++;
		return n;
	}

	protected void onSpotSelectorChanged() {
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

	protected SpotChoice getCurrentSpotChoice() {
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

	protected void refreshSpotSelectorModel(boolean keepSelection) {
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
			Collections.sort(sorted,
					Comparator.comparing(s -> s != null ? safeLower(s.getName()) : "", String::compareTo));
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

	protected void refreshChart() {
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
			// UX: when the sequence selection yields a single spot, select that spot
			// explicitly in the combo
			// so users can immediately see which spot's curve they're looking at.
			maybeSelectSingleSpotInCombo(lastSelectedSpots);
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
			XYLineAndShapeRenderer r0 = buildPlotRenderer(ds0, overlaySpots, enabledMeasures, false);
			plot.setDataset(0, ds0);
			plot.setRenderer(0, r0);

			yAxis0.setAutoRange(false);
			yAxis0.setRange(computePaddedRangeFromZero(ds0, 1.0, 0.05, 0.05));

			XYSeriesCollection ds1 = buildDatasetForMeasures(lastExperiment, lastOptions, lastSelectedSpots,
					enabledMeasures, true, overlaySpots, overlayMeasures);
			XYLineAndShapeRenderer r1 = buildPlotRenderer(ds1, overlaySpots, enabledMeasures, true);
			EnumResults flyPresentType = findResultByLabel("AREA_FLYPRESENT");
			NumberAxis yAxis1 = new NumberAxis(flyPresentType != null ? flyPresentType.toUnit() : "");
			yAxis1.setInverted(true);
			yAxis1.setAutoRange(false);
			yAxis1.setRange(new org.jfree.data.Range(0.0, 100.0));
			plot.setRangeAxis(1, yAxis1);
			plot.setDataset(1, ds1);
			plot.setRenderer(1, r1);
			plot.mapDatasetToRangeAxis(1, 1);

			EnumResults y0 = firstContinuousMeasure(enabledMeasures);
			if (y0 == null && lastOptions.resultType != null) {
				y0 = lastOptions.resultType;
			}
			yAxis0.setLabel(y0 != null ? y0.toUnit() : "");
		} else {
			XYSeriesCollection ds = buildDatasetForMeasures(lastExperiment, lastOptions, lastSelectedSpots,
					enabledMeasures, flyEnabled && !hasContinuous, overlaySpots, overlayMeasures);
			plot.setDataset(0, ds);
			plot.setRenderer(0, buildPlotRenderer(ds, overlaySpots, enabledMeasures, flyEnabled && !hasContinuous));

			EnumResults labelType = flyEnabled && !hasContinuous ? findResultByLabel("AREA_FLYPRESENT")
					: firstContinuousMeasure(enabledMeasures);
			if (labelType == null) {
				labelType = lastOptions.resultType;
			}
			yAxis0.setLabel(labelType != null ? labelType.toUnit() : "");
			if (flyEnabled && !hasContinuous) {
				yAxis0.setAutoRange(false);
				yAxis0.setRange(new org.jfree.data.Range(0.0, 100.0));
			} else {
				yAxis0.setAutoRange(false);
				yAxis0.setRange(computePaddedRangeFromZero(ds, 1.0, 0.05, 0.05));
			}
		}

		JFreeChart chart = new JFreeChart(plot);
		updateFrameTitleWithSelection(enabledMeasures, lastSelectedSpots);

		chartPanel = new ChartPanel(chart, 900, 500, 300, 200, 2000, 2000, true, true, true, true, false, true);
		chartPanel.addChartMouseListener(
				new SpotOverlayChartInteractionHandler(lastExperiment, lastOptions).createMouseListener());
		mainChartPanel.add(chartPanel, BorderLayout.CENTER);

		mainChartPanel.revalidate();
		mainChartPanel.repaint();
	}

	private void updateFrameTitleWithSelection(List<EnumResults> enabledMeasures, List<Spot> selectedSpots) {
		if (mainChartFrame == null) {
			return;
		}
		String title = baseTitle != null ? baseTitle : "Spots measures";
		String spotLabel = "";
		if (selectedSpots != null) {
			if (selectedSpots.size() == 1 && selectedSpots.get(0) != null) {
				spotLabel = selectedSpots.get(0).getName();
			} else if (selectedSpots.size() > 1) {
				spotLabel = selectedSpots.size() + " spots";
			}
		}
		String measuresLabel = "";
		if (enabledMeasures != null && !enabledMeasures.isEmpty()) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < enabledMeasures.size(); i++) {
				EnumResults r = enabledMeasures.get(i);
				if (r == null)
					continue;
				if (sb.length() > 0)
					sb.append(", ");
				sb.append(r.toString());
			}
			measuresLabel = sb.toString();
		}
		if (!spotLabel.isEmpty() || !measuresLabel.isEmpty()) {
			title = title + " [" + (spotLabel.isEmpty() ? "spot" : spotLabel) + " | "
					+ (measuresLabel.isEmpty() ? "measure" : measuresLabel) + "]";
		}
		mainChartFrame.setTitle(title);
	}

	private void maybeSelectSingleSpotInCombo(List<Spot> selectedSpots) {
		if (spotSelectorCombo == null || isUpdatingSpotComboModel) {
			return;
		}
		if (selectedSpots == null || selectedSpots.size() != 1) {
			return;
		}
		Spot only = selectedSpots.get(0);
		if (only == null) {
			return;
		}
		isUpdatingSpotComboModel = true;
		try {
			for (int i = 0; i < spotSelectorCombo.getItemCount(); i++) {
				SpotChoice sc = spotSelectorCombo.getItemAt(i);
				if (sc != null && sc.mode == SpotChoiceMode.FIXED_SPOT && sc.spot == only) {
					spotSelectorCombo.setSelectedIndex(i);
					return;
				}
			}
			// Fallback to name match (when Spots instances differ)
			String name = only.getName();
			if (name != null) {
				for (int i = 0; i < spotSelectorCombo.getItemCount(); i++) {
					SpotChoice sc = spotSelectorCombo.getItemAt(i);
					if (sc != null && sc.mode == SpotChoiceMode.FIXED_SPOT && sc.spot != null
							&& name.equals(sc.spot.getName())) {
						spotSelectorCombo.setSelectedIndex(i);
						return;
					}
				}
			}
		} finally {
			isUpdatingSpotComboModel = false;
		}
	}

	protected List<EnumResults> getEnabledMeasures(ResultsOptions options) {
		List<EnumResults> out = new ArrayList<>();
		if (cbSum.isSelected())
			addResultIfDefined(out, "AREA_SUM");
		if (cbNoFly.isSelected())
			addResultIfDefined(out, "AREA_SUMNOFLY");
		if (cbClean.isSelected())
			addResultIfDefined(out, "AREA_SUMCLEAN");
		if (cbCleanV3.isSelected())
			addResultIfDefined(out, "AREA_SUMCLEAN_V3");
//		if (cbSumV2.isSelected())
//			addResultIfDefined(out, "AREA_SUM_V2");
//		if (cbNoFlyV2.isSelected())
//			addResultIfDefined(out, "AREA_SUMNOFLY_V2");
//		if (cbCleanV2.isSelected())
//			addResultIfDefined(out, "AREA_SUMCLEAN_V2");
		if (cbFlyPresent.isSelected())
			addResultIfDefined(out, "AREA_FLYPRESENT");
		if (out.isEmpty() && options != null && isV5NativeSpotChartMeasure(options.resultType)) {
			out.add(options.resultType);
		}
		return out;
	}

	protected static void addResultIfDefined(List<EnumResults> out, String label) {
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
				XYSeries series = createXYSeriesFromSpotMeasure(exp, spot, options, chosen, cbDepletion.isSelected(),
						SpotChartSeriesKeys.key(spot, i));
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
			XYSeries series = createXYSeriesFromSpotMeasure(exp, spot, options, resultType, cbDepletion.isSelected(),
					spot.getName() + SpotChartSeriesKeys.SEP + resultType.name());
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

	protected Color pickMeasureColor(EnumResults resultType) {
		if (resultType == null)
			return Color.BLACK;
		switch (resultType.name()) {
		case "AREA_SUM":
			return new Color(0, 0, 0);
		case "AREA_SUMNOFLY":
			return new Color(0, 102, 204);
		case "AREA_SUMCLEAN":
			return new Color(0, 153, 0);
		case "AREA_SUMCLEAN_V3":
			return new Color(90, 170, 130);
		case "AREA_SUM_V2":
			return new Color(80, 80, 80);
		case "AREA_SUMNOFLY_V2":
			return new Color(60, 140, 230);
		case "AREA_SUMCLEAN_V2":
			return new Color(60, 180, 90);
		case "AREA_FLYPRESENT":
			return new Color(153, 0, 153);
		default:
			return Color.BLACK;
		}
	}

	protected XYLineAndShapeRenderer buildPlotRenderer(XYSeriesCollection dataset, boolean overlaySpots,
			List<EnumResults> enabledMeasures, boolean flyDatasetSlice) {
		XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
		SeriesStyleCodec.applySeriesPaintsFromDescription(dataset, renderer);
		applyV2StrokeHints(dataset, renderer);
		customizeSpotOverlayRendererStrokes(dataset, renderer, overlaySpots, enabledMeasures, flyDatasetSlice);
		return renderer;
	}

	/**
	 * Optional per-subclass styling after {@link #applyV2StrokeHints}. {@code flyDatasetSlice} is true for the
	 * fly-only range axis dataset when split axes are used.
	 */
	protected void customizeSpotOverlayRendererStrokes(XYSeriesCollection dataset, XYLineAndShapeRenderer renderer,
			boolean overlaySpots, List<EnumResults> enabledMeasures, boolean flyDatasetSlice) {
	}

	private static void applyV2StrokeHints(XYSeriesCollection dataset, XYLineAndShapeRenderer renderer) {
		if (dataset == null || renderer == null) {
			return;
		}
		java.awt.Stroke solid = new java.awt.BasicStroke(1.6f);
		java.awt.Stroke dashed = new java.awt.BasicStroke(1.6f, java.awt.BasicStroke.CAP_BUTT,
				java.awt.BasicStroke.JOIN_BEVEL, 0f, new float[] { 6f, 5f }, 0f);
		for (int i = 0; i < dataset.getSeriesCount(); i++) {
			String key = String.valueOf(dataset.getSeriesKey(i));
			boolean v2 = key.contains("AREA_SUM_V2") || key.contains("AREA_SUMNOFLY_V2")
					|| key.contains("AREA_SUMCLEAN_V2") || key.contains("_V2");
			boolean v3 = key.contains("AREA_SUMCLEAN_V3");
			renderer.setSeriesStroke(i, (v2 || v3) ? dashed : solid);
		}
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
			EnumResults resultType, boolean depletionMode, String seriesKey) {
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
			divider = SpotPreConsumedSupport.computeBaselineMaxValue(exp, spot, spotMeasure, camImages_time_min,
					baseOptions);
			if (divider == 0)
				divider = 1.0;
		}

		double flyPresentToPercent = 1.0;
		if (isAreaFlyPresent(resultType)) {
			flyPresentToPercent = 100.0 / (double) spot.getFlyPresentDenomPixelCount();
		}

		int npoints = spotMeasure.getCount();
		if (camImages_time_min != null && npoints > camImages_time_min.length)
			npoints = camImages_time_min.length;

		for (int j = 0; j < npoints; j++) {
			double x = camImages_time_min != null ? camImages_time_min[j] : j;
			double raw = spotMeasure.getValueAt(j);
			double y;
			if (isAreaFlyPresent(resultType)) {
				y = raw * flyPresentToPercent;
			} else if (baseOptions.relativeToMaximum && depletionMode) {
				y = SpotPreConsumedSupport.computeDepletionValue(spot, exp, j, raw, divider);
			} else if (baseOptions.relativeToMaximum) {
				y = raw / divider;
			} else {
				y = raw;
			}
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

	private static EnumResults firstContinuousMeasure(List<EnumResults> enabledMeasures) {
		if (enabledMeasures == null) {
			return null;
		}
		for (EnumResults m : enabledMeasures) {
			if (m != null && !isAreaFlyPresent(m)) {
				return m;
			}
		}
		return null;
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
