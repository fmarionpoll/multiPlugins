package plugins.fmp.multitools.tools.chart;

import static org.junit.Assert.*;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.junit.Test;

import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.tools.chart.SpotChartMeasures.Family;
import plugins.fmp.multitools.tools.chart.builders.KymoSpotChartSupport;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

public class SpotChartUiTest {
	@org.junit.ClassRule public static final plugins.fmp.multitools.test.InMemoryImageRule imageMemory =
			new plugins.fmp.multitools.test.InMemoryImageRule();
	@Test public void everyCageSummaryIsExcludedFromSpotRequestsIncludingNonAggNames() {
		for (Family family : Family.values()) {
			for (EnumResults result : family.measures(false)) assertFalse(result.isCageSpotSummary());
			for (EnumResults result : family.measures(true)) {
				assertTrue(result.isCageSpotSummary());
				assertFalse(SpotChartMeasures.sourceOf(result).isCageSpotSummary());
				ResultsOptions options = new ResultsOptions(); options.resultType = result;
				try {
					KymoSpotChartSupport.buildOverlayForSpots(null, options, Collections.emptyList());
					fail("Accepted a cage population as a spot result: " + result);
				} catch (IllegalArgumentException expected) { }
			}
		}
		assertTrue(EnumResults.CAGE_GREY_MEDIAN.isCageSpotSummary());
		assertTrue(EnumResults.KYMO_CAGE_MEAN_FRACT.isCageSpotSummary());
	}

	@Test public void choosingCageFiltersSpotsForBothImageFamiliesAndLines() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			for (ChartSpotsOverlayFrame overlay : Arrays.asList(new FilterControls(), new ColorControls(), new LineControls())) {
				List<Spot> available = Arrays.asList(spot(2, "spot2_a"), spot(2, "spot2_b"), spot(7, "spot7_a"));
				overlay.setAvailableSpotsProvider(() -> available);
				ResultsOptions options = new ResultsOptions();
				options.resultType = overlay instanceof LineControls ? EnumResults.KYMO_GREEN_HEIGHT_RATIO : EnumResults.FILTER_GREY;
				JPanel controls = overlay.createTopControlsPanel(options);
				List<JComboBox<?>> combos = descendants(controls, JComboBox.class);
				assertEquals(2, combos.size());
				JComboBox<?> cages = combos.get(0);
				JComboBox<?> spots = combos.get(1);
				assertEquals(4, spots.getItemCount()); // Selected ROI(s) + all three spots
				cages.setSelectedItem(7);
				assertEquals(2, spots.getItemCount());
				assertEquals("spot7_a", spots.getSelectedItem().toString());
				cages.setSelectedItem(2);
				assertEquals(3, spots.getItemCount());
				assertEquals("spot2_a", spots.getSelectedItem().toString());
			}
		});
	}

	@Test public void multipleSpotsUseTheMeasureJustChosenRatherThanOldPriority() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			FilterControls filter = new FilterControls();
			ResultsOptions options = new ResultsOptions(); options.resultType = EnumResults.FILTER_GREY_SMOOTHED;
			JPanel controls = filter.createTopControlsPanel(options);
			filter.lastSelectedSpots = Arrays.asList(spot(1, "a"), spot(1, "b"));
			findCheck(controls, EnumResults.FILTER_GREY.name()).doClick();
			assertEquals(Collections.singletonList(EnumResults.FILTER_GREY), filter.getEnabledMeasures(options));
			assertEquals(1, filter.countSelectedMeasures());
		});
	}

	@Test public void linesOverlaySeveralMeasuresForOneSpotAndOneMeasureForSeveralSpots() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			LineControls lines = new LineControls();
			ResultsOptions options = new ResultsOptions(); options.resultType = EnumResults.KYMO_GREEN_HEIGHT_RATIO;
			JPanel controls = lines.createTopControlsPanel(options);
			lines.lastSelectedSpots = Collections.singletonList(spot(1, "a"));
			findCheck(controls, EnumResults.X_LINE_RATIO.name()).doClick();
			assertEquals(2, lines.getEnabledMeasures(options).size());
			lines.lastSelectedSpots = Arrays.asList(spot(1, "a"), spot(1, "b"));
			findCheck(controls, EnumResults.RIM_LINE_RATIO.name()).doClick();
			assertEquals(Collections.singletonList(EnumResults.RIM_LINE_RATIO), lines.getEnabledMeasures(options));
		});
	}

	@Test public void renderedLineOverlayKeepsPixelHeightAndRatiosOnDifferentAxes() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			plugins.fmp.multitools.experiment.Experiment exp = new plugins.fmp.multitools.experiment.Experiment();
			exp.setCamImages_ms(new long[] { 0, 60000 });
			exp.setKymoFirst_ms(0); exp.setKymoLast_ms(60000); exp.setKymoBin_ms(60000);
			plugins.fmp.multitools.experiment.cage.Cage cage = new plugins.fmp.multitools.experiment.cage.Cage();
			cage.getProperties().setCageID(1); exp.getCages().cagesList.add(cage);
			Spot spot = spot(1, "spot1_a"); exp.getSpots().getSpotList().add(spot);
			spot.getMeasurements(EnumResults.KYMO_GREEN_HEIGHT_RATIO).setValues(new double[] { 1, 0.5 });
			spot.getMeasurements(EnumResults.KYMO_GREEN_HEIGHT).setValues(new double[] { 20, 10 });
			ResultsOptions options = new ResultsOptions(); options.resultType = EnumResults.KYMO_GREEN_HEIGHT_RATIO;
			RenderedLine lines = new RenderedLine();
			JPanel controls = lines.start(exp, spot, options);
			findCheck(controls, EnumResults.KYMO_GREEN_HEIGHT.name()).doClick();
			org.jfree.chart.plot.XYPlot plot = lines.getChartPanel().getChart().getXYPlot();
			assertEquals(2, plot.getRangeAxisCount());
			assertEquals(0.5, plot.getDataset(0).getYValue(0, 1), 1e-9);
			assertEquals(10.0, plot.getDataset(1).getYValue(0, 1), 1e-9);
			assertNotEquals(plot.getRangeAxis(0).getLabel(), plot.getRangeAxis(1).getLabel());
		});
	}

	private static Spot spot(int cage, String name) {
		Spot spot = new Spot(); spot.getProperties().setCageID(cage); spot.getProperties().setName(name); return spot;
	}

	private static JCheckBox findCheck(Container root, String label) {
		for (JCheckBox check : SpotChartUiTest.<JCheckBox>descendants(root, JCheckBox.class)) if (label.equals(check.getText())) {
			assertNotNull(check.getToolTipText());
			return check;
		}
		throw new AssertionError("Checkbox not found: " + label);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static <T> List<T> descendants(Container root, Class type) {
		List<T> result = new ArrayList<>();
		for (Component component : root.getComponents()) {
			if (type.isInstance(component)) result.add((T) component);
			else if (component instanceof Container) result.addAll(descendants((Container) component, type));
		}
		return result;
	}

	// Exercise real controls without opening native Icy chart windows.
	private static class FilterControls extends ChartSpotsOverlayFrame { @Override protected void refreshChart() { } }
	private static class ColorControls extends ChartColorSpotsOverlayFrame { @Override protected void refreshChart() { } }
	private static class LineControls extends ChartLineSpotsOverlayFrame { @Override protected void refreshChart() { } }
	private static class RenderedLine extends ChartLineSpotsOverlayFrame {
		JPanel start(plugins.fmp.multitools.experiment.Experiment exp, Spot spot, ResultsOptions options) {
			mainChartPanel = new JPanel(new java.awt.BorderLayout());
			lastExperiment = exp; lastOptions = options; lastSelectedSpots = Collections.singletonList(spot);
			setAvailableSpotsProvider(() -> Collections.singletonList(spot));
			setSelectedSpotsProvider(() -> Collections.singletonList(spot));
			JPanel controls = createTopControlsPanel(options);
			refreshChart();
			return controls;
		}
	}
}
