package plugins.fmp.multiSPOTS.dlg.fractions;

import static org.junit.Assert.*;
import java.awt.Component;
import java.awt.Container;
import java.util.*;
import javax.swing.*;
import org.jfree.data.xy.XYSeriesCollection;
import org.junit.Test;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.fraction.*;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.*;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionCalculator.*;
import plugins.fmp.multitools.experiment.spot.measurement.*;
import plugins.fmp.multitools.experiment.spot.measurement.SpotProcessingSettings.GapPolicy;
import plugins.fmp.multitools.experiment.spot.measurement.SpotProcessingSettings.TailPolicy;

public class CalculateSpotFractionsPanelTest {
    private final Key key = new Key(SpotMeasurementMethod.FILTER, "signal", "");
    private Row row(double[] raw, int[] flags, Settings settings) {
        Spot spot = new Spot(); spot.getProperties().setName("A"); spot.getProperties().setCageID(1);
        double[] times = new double[raw.length], fly = new double[raw.length];
        for (int i = 0; i < times.length; i++) { times[i] = i * 60; fly[i] = flags[i] == 0 ? 0 : 1; }
        spot.getMethodMeasurements().put(new SpotMethodResult(key.method, times, fly, "fraction", Collections.emptyMap(),
                Collections.singletonList(new SpotMeasuredQuantity("signal", "intensity", raw, flags))));
        SpotFractionPlan plan = new SpotFractionPlan(); plan.settings.put(key, settings);
        return SpotFractionCalculator.calculate(Collections.singletonList(new Input(spot, key.method)), key, plan).get(0);
    }
    @Test public void commonWorkspaceSeparatesPreviewSaveRangeAndReferenceActions() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            _DlgCalculateFractions_ panel = new _DlgCalculateFractions_();
            Set<String> buttons = new HashSet<>(); collect(panel, buttons);
            assertTrue(buttons.containsAll(Arrays.asList("Preview current", "Calculate & save current", "Apply & save range",
                    "Saved review list", "Approve reference", "Borrow initial reference", "Exclude failed deposit")));
            assertEquals(Illumination.NONE, panel.controls().illumination); assertEquals(TailPolicy.KEEP_MISSING, panel.controls().tail);
            assertFalse(panel.spotOverride.isSelected()); assertEquals(Tolerance.PERCENT_REFERENCE, panel.controls().toleranceMode);
            panel.toleranceMode.setSelectedItem(Tolerance.ROBUST_NOISE); assertFalse(panel.tolerance.isEnabled());
            panel.method.setSelectedItem(SpotMeasurementMethod.VERTICAL_LINE);
            assertEquals(2, ((DefaultComboBoxModel<String>) panel.quantity.getModel()).getIndexOf("color_integral"));
            assertEquals(2, panel.view.getItemCount());
            assertEquals(SpotFractionChart.View.FRACTION_REMAINING, panel.view.getItemAt(0));
            assertEquals(SpotFractionChart.View.FRACTION_CONSUMED, panel.view.getItemAt(1));
            assertEquals(2, panel.diagnosticView.getItemCount());
            assertEquals(4, panel.aggregateMeasure.getItemCount());
        });
    }
    @Test public void chartNeverClampsNegativeConsumptionAndMarksFilledSegmentsSeparately() {
        Row row = row(new double[] {100, 900, 50, 120}, new int[] {0, 1, 0, 0}, Settings.DEFAULT);
        XYSeriesCollection data = SpotFractionChart.data(row, SpotFractionChart.View.FRACTION_CONSUMED, false);
        assertNull(data.getY(0, 1)); assertEquals(.25, data.getYValue(1, 1), 1e-12);
        assertEquals(-.2, data.getYValue(0, 3), 1e-12); assertEquals(1, data.getXValue(1, 1), 0);
        assertEquals(SpotSampleFlags.OCCLUDED, row.fractions.getObservationFlags()[1]);
    }
    @Test public void optionalVisualConnectionDoesNotChangeUnderlyingMissingValues() {
        Settings settings = new Settings(GapPolicy.NONE, GapPolicy.NONE, TailPolicy.KEEP_MISSING, 600,
                Tolerance.PERCENT_REFERENCE, 8, 0, Illumination.NONE);
        Row row = row(new double[] {100, 900, 100}, new int[] {0, 1, 0}, settings);
        XYSeriesCollection connected = SpotFractionChart.data(row, SpotFractionChart.View.FRACTION_CONSUMED, true);
        assertEquals(0, connected.getYValue(1, 1), 0); assertNull(connected.getY(0, 1));
        assertTrue(Double.isNaN(row.remaining()[1])); assertFalse(row.fractions.getEstimatedSamples()[1]);
        assertNull(SpotFractionChart.data(row, SpotFractionChart.View.FRACTION_CONSUMED, false).getY(1, 1));
    }
    @Test public void savingOneSelectionRetainsOtherMethodDecisionsAndSupportsRemovingAnOverride() {
        SpotFractionPlan saved = new SpotFractionPlan(), preview = new SpotFractionPlan();
        Key other = new Key(SpotMeasurementMethod.COLOR, "grey_signal", "");
        saved.settings.put(other, Settings.DEFAULT); saved.settings.put(key.forSpot("A"), Settings.DEFAULT);
        saved.review.add(new Review(other.forSpot("B"), "Other method still needs review"));
        preview.settings.put(key, Settings.DEFAULT); preview.review.add(new Review(key.forSpot("A"), "Initial occlusion"));
        SpotFractionPlan merged = _DlgCalculateFractions_.mergeSelection(saved, preview, key);
        assertTrue(merged.settings.containsKey(other)); assertFalse(merged.settings.containsKey(key.forSpot("A")));
        assertEquals(2, merged.review.size());
    }
    @Test public void aggregatePlotsDoNotTreatVisualConnectionsAsCalculatedContributions() {
        Settings settings = new Settings(GapPolicy.NONE, GapPolicy.NONE, TailPolicy.KEEP_MISSING, 600,
                Tolerance.PERCENT_REFERENCE, 8, 0, Illumination.NONE);
        Row row = row(new double[] {100, 900, 100}, new int[] {0, 1, 0}, settings);
        plugins.fmp.multitools.experiment.spot.fraction.SpotFractionAggregation.Group group =
                plugins.fmp.multitools.experiment.spot.fraction.SpotFractionAggregation.build(Collections.singletonList(row.input),
                        Collections.singletonList(row), key, new SpotFractionPlan()).get(0);
        XYSeriesCollection data = SpotFractionAggregateChart.data(group,
                plugins.fmp.multitools.experiment.spot.fraction.SpotFractionAggregation.Measure.SUM_CONSUMED);
        assertNull(data.getY(0, 1)); assertNull(data.getY(1, 1)); assertEquals(0, group.availableSpots()[1]);
    }
    private void collect(Container container, Set<String> buttons) {
        for (Component child : container.getComponents()) {
            if (child instanceof JButton) buttons.add(((JButton) child).getText());
            if (child instanceof Container) collect((Container) child, buttons);
        }
    }
}
