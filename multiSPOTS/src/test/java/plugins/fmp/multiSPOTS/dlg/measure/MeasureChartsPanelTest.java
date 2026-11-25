package plugins.fmp.multiSPOTS.dlg.measure;

import static org.junit.Assert.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.junit.Test;
import plugins.fmp.multitools.tools.chart.SpotChartMeasures.Family;
import plugins.fmp.multitools.tools.chart.SpotAcquisitionCharts.Measure;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.kernel.roi.roi2d.ROI2DEllipse;
import icy.sequence.Sequence;

public class MeasureChartsPanelTest {
    @org.junit.BeforeClass public static void initializeIcyPreferences() { icy.preferences.IcyPreferences.init(); }
    @Test public void spotDisplayFallsBackAndUsesDeterministicRoiSelectionOrder() {
        Experiment exp = new Experiment(); Sequence sequence = new Sequence(); exp.getSeqCamData().attachSequence(sequence);
        try {
            Cage cage = new Cage(); cage.getProperties().setCageID(1); exp.getCages().cagesList.add(cage);
            Spot first = spot(exp, sequence, "spot1_a"), second = spot(exp, sequence, "spot1_b");
            assertEquals(Collections.singletonList(first), MeasureChartsPanel.initialSpots(exp));
            assertFalse(first.getRoi().isSelected()); assertFalse(second.getRoi().isSelected());
            second.getRoi().setSelected(true); assertEquals(Collections.singletonList(second), MeasureChartsPanel.initialSpots(exp));
            first.getRoi().setSelected(true); assertEquals(Arrays.asList(first, second), MeasureChartsPanel.initialSpots(exp));
        } finally { sequence.close(); }
    }
    @Test public void emptyExperimentHasNoFallbackSpot() { assertTrue(MeasureChartsPanel.initialSpots(new Experiment()).isEmpty()); }
    @Test public void viewerFrameMapsOntoTheChartTimeAxis() {
        Experiment exp = new Experiment();
        exp.setCamImages_ms(new long[] { 0, 20000, 45000, 60000 });
        assertEquals(Double.valueOf(0.75), MeasureChartsPanel.minutesAtFrame(exp, 2));
        assertEquals(Double.valueOf(0), MeasureChartsPanel.minutesAtFrame(exp, 0));
        assertNull(MeasureChartsPanel.minutesAtFrame(exp, 4));
        assertNull(MeasureChartsPanel.minutesAtFrame(null, 0));
    }
    @Test public void draggedCursorSelectsTheCameraFrameUnderThePointer() {
        Experiment exp = new Experiment();
        exp.setCamImages_ms(new long[] { 0, 20000, 45000, 60000 });
        assertEquals(2, MeasureChartsPanel.frameAtChartMinutes(exp, 0.75));
        assertEquals(1, MeasureChartsPanel.frameAtChartMinutes(exp, 0.3));
        assertEquals(0, MeasureChartsPanel.frameAtChartMinutes(exp, 0));
        assertEquals(3, MeasureChartsPanel.frameAtChartMinutes(exp, 5));
        assertEquals(-1, MeasureChartsPanel.frameAtChartMinutes(exp, Double.NaN));
        assertEquals(-1, MeasureChartsPanel.frameAtChartMinutes(null, 1));
        for (int frame = 0; frame < 4; frame++)
            assertEquals(frame, MeasureChartsPanel.frameAtChartMinutes(exp, MeasureChartsPanel.minutesAtFrame(exp, frame)));
        assertTrue(MeasureChartsPanel.nearVerticalCursor(100, 100 + MeasureChartsPanel.CURSOR_GRAB_PX));
        assertFalse(MeasureChartsPanel.nearVerticalCursor(100, 100 + MeasureChartsPanel.CURSOR_GRAB_PX + 1));
        assertFalse(MeasureChartsPanel.nearVerticalCursor(100, Double.NaN));
    }
    @Test public void chartMinuteUsesTheRecordedFrameIntervalRatherThanOneMinuteBins() {
        double[] seconds = new double[120];
        for (int i = 0; i < seconds.length; i++) seconds[i] = i * 20.0;
        assertEquals(90, MeasureChartsPanel.frameAtMinutes(seconds, null, 30));
        assertEquals(30, MeasureChartsPanel.frameAtMinutes(seconds, null, 10));
        long[] camera = new long[240];
        for (int i = 0; i < camera.length; i++) camera[i] = i * 20000L;
        double[] subset = new double[] { 0, 40, 600 };
        assertEquals(30, MeasureChartsPanel.frameAtMinutes(subset, camera, 10));
    }
    @Test public void curveClickStoresTheCameraFrameAndFocusesTheSpot() {
        Experiment exp = new Experiment();
        exp.getSeqCamData().setImagesList(Arrays.asList("frame0.jpg", "frame1.jpg", "frame2.jpg", "frame3.jpg"));
        exp.getSeqCamData().getTimeManager().setCamImagesTime_Ms(new long[] { 0, 20000, 45000, 60000 });
        Spot spot = new Spot(); spot.getProperties().setName("spotA");
        MeasureChartsPanel.focusClickedSample(exp, spot, 0.75);
        assertEquals(2, spot.getSpotCamDataT());
    }
    @Test public void parameterBandNamesTheMeasureAndChartsShrinkToThirtyPercent() {
        assertEquals("Cage 4", MeasureChartsPanel.cageTitle(4));
        assertEquals("Filtered signal (mean intensity)", MeasureChartsPanel.parameterBand("Filtered signal", "mean intensity"));
        assertEquals("Fly presence", MeasureChartsPanel.parameterBand("Fly presence", " "));
        assertEquals(new Dimension(500, 320), MeasureChartsPanel.chartSize(100));
        assertEquals(new Dimension(150, 96), MeasureChartsPanel.chartSize(30));
        assertEquals(MeasureChartsPanel.chartSize(MeasureChartsPanel.CHART_SCALE_MIN), MeasureChartsPanel.chartSize(1));
    }
    @Test public void tracesShareTheSpotColorAndTheLegendListsEachStimulusOnce() {
        Experiment exp = new Experiment(); Sequence sequence = new Sequence();
        try {
            Spot sucroseA = colored(exp, sequence, "a", "sucrose", Color.RED);
            Spot sucroseB = colored(exp, sequence, "b", "sucrose", Color.RED);
            Spot yeast = colored(exp, sequence, "c", "yeast", Color.BLUE);
            XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
            MeasureChartsPanel.paintByStimulus(renderer, Arrays.asList(sucroseA, sucroseB, yeast), 3, true);
            assertEquals(Color.RED, renderer.getSeriesPaint(0));
            assertEquals(Color.RED, renderer.getSeriesPaint(1));
            assertEquals(Color.BLUE, renderer.getSeriesPaint(2));
            assertEquals(Boolean.TRUE, renderer.getSeriesVisibleInLegend(0));
            assertEquals(Boolean.FALSE, renderer.getSeriesVisibleInLegend(1));
            assertEquals(Boolean.TRUE, renderer.getSeriesVisibleInLegend(2));
            assertEquals("sucrose", renderer.getLegendItemLabelGenerator().generateLabel(null, 0));
            assertEquals("yeast", renderer.getLegendItemLabelGenerator().generateLabel(null, 2));
            XYLineAndShapeRenderer flies = new XYLineAndShapeRenderer(true, false);
            MeasureChartsPanel.paintFlyPresence(flies, 2);
            assertEquals(MeasureChartsPanel.FLY_TRACE, flies.getSeriesPaint(0));
            assertEquals(MeasureChartsPanel.FLY_TRACE, flies.getSeriesPaint(1));
            assertEquals(Boolean.FALSE, flies.getSeriesVisibleInLegend(0));
        } finally { sequence.close(); }
    }
    @Test public void unsetStimulusFallsBackToOneLegendEntryPerSpotColor() {
        Experiment exp = new Experiment(); Sequence sequence = new Sequence();
        try {
            Spot red = colored(exp, sequence, "a", "..", Color.RED);
            Spot redAgain = colored(exp, sequence, "b", "..", new Color(255, 0, 0));
            Spot blue = colored(exp, sequence, "c", "  ", Color.BLUE);
            XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
            MeasureChartsPanel.paintByStimulus(renderer, Arrays.asList(red, redAgain, blue), 3, true);
            assertEquals(Boolean.TRUE, renderer.getSeriesVisibleInLegend(0));
            assertEquals(Boolean.FALSE, renderer.getSeriesVisibleInLegend(1));
            assertEquals(Boolean.TRUE, renderer.getSeriesVisibleInLegend(2));
            assertEquals("red", renderer.getLegendItemLabelGenerator().generateLabel(null, 0));
            assertEquals("blue", renderer.getLegendItemLabelGenerator().generateLabel(null, 2));
        } finally { sequence.close(); }
    }
    private static Spot colored(Experiment exp, Sequence sequence, String name, String stimulus, Color color) {
        Spot spot = spot(exp, sequence, name);
        spot.getProperties().setStimulus(stimulus);
        spot.getProperties().setColor(color);
        return spot;
    }
    private static Spot spot(Experiment exp, Sequence sequence, String name) {
        ROI2DEllipse roi = new ROI2DEllipse(0, 0, 5, 5); roi.setName(name); roi.setSelected(false);
        Spot spot = new Spot(roi); spot.getProperties().setCageID(1); spot.getProperties().setName(name);
        exp.getSpots().getSpotList().add(spot); sequence.addROI(roi); return spot;
    }
    @Test public void singleSpotChoiceFollowsTheCageAndKeepsTheChosenSpot() throws Exception {
        Experiment exp = new Experiment(); Sequence sequence = new Sequence();
        try {
            Spot later = named(exp, sequence, "spot_b", 1, "yeast", "10 mM");
            Spot first = named(exp, sequence, "spot_a", 1, "sucrose", "70 mM");
            Spot other = named(exp, sequence, "spot_c", 3, "sucrose", "70 mM");
            assertEquals("spot_a sucrose 70 mM", MeasureChartsPanel.spotMenuLabel(first));
            assertEquals("spot_d", MeasureChartsPanel.spotMenuLabel(named(exp, sequence, "spot_d", 3, "..", "..")));
            assertEquals(Arrays.asList(first, later), MeasureChartsPanel.spotsInCage(exp.getSpots().getSpotList(), 1));
            SwingUtilities.invokeAndWait(() -> {
                MeasureChartsPanel panel = new MeasureChartsPanel(null, Family.FILTER);
                assertFalse(panel.spotChoice.isVisible());
                panel.scope.setSelectedIndex(2);
                assertTrue(panel.cage.isVisible());
                assertTrue(panel.spotChoice.isVisible());
                panel.bindExperiment(exp);
                panel.selectSpotForCurrentView(exp);
                assertEquals(Integer.valueOf(1), panel.cage.getSelectedItem());
                assertSame(first, panel.spotChoice.getSelectedItem());
                panel.cage.setSelectedItem(Integer.valueOf(3));
                assertSame(other, panel.spotChoice.getSelectedItem());
                panel.cage.setSelectedItem(Integer.valueOf(1));
                assertSame(first, panel.spotChoice.getSelectedItem());
                panel.spotChoice.setSelectedItem(later);
                panel.selectSpotForCurrentView(exp);
                assertSame(later, panel.spotChoice.getSelectedItem());
                panel.closeAllCharts();
            });
        } finally { sequence.close(); }
    }
    private static Spot named(Experiment exp, Sequence sequence, String name, int cageId, String stimulus, String concentration) {
        Spot spot = spot(exp, sequence, name);
        spot.getProperties().setCageID(cageId);
        spot.getProperties().setStimulus(stimulus);
        spot.getProperties().setConcentration(concentration);
        return spot;
    }
    @Test public void collectionUiOffersOnlyMeasurementsAndFlyPresenceWithoutDerivedControls() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (Family family : Family.values()) {
                MeasureChartsPanel panel = new MeasureChartsPanel(null, family);
                assertEquals(3, panel.scope.getItemCount()); assertTrue(panel.flies.isSelected());
                for (int i = 0; i < panel.measure.getItemCount(); i++) {
                    Measure measure = panel.measure.getItemAt(i);
                    assertFalse(measure.quantity.contains("ratio")); assertFalse(measure.quantity.contains("smooth"));
                    assertFalse(measure.quantity.contains("clean"));
                    if (measure.flyOnly) { panel.measure.setSelectedIndex(i); assertFalse(panel.flies.isEnabled()); }
                }
                List<String> labels = new ArrayList<>(); labels(panel, labels);
                assertFalse(labels.contains("Relative to baseline maximum")); assertFalse(labels.contains("Cage summaries"));
                assertFalse(labels.contains("Baseline (min)")); panel.closeAllCharts();
            }
        });
    }
    private static void labels(Container container, List<String> labels) {
        for (Component component : container.getComponents()) {
            if (component instanceof AbstractButton) labels.add(((AbstractButton) component).getText());
            if (component instanceof JLabel) labels.add(((JLabel) component).getText());
            if (component instanceof JTabbedPane) for (int i = 0; i < ((JTabbedPane) component).getTabCount(); i++) labels.add(((JTabbedPane) component).getTitleAt(i));
            if (component instanceof Container) labels((Container) component, labels);
        }
    }
}
