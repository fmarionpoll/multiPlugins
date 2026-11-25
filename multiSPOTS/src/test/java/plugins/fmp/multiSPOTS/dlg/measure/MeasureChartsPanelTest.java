package plugins.fmp.multiSPOTS.dlg.measure;

import static org.junit.Assert.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
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
    private static Spot spot(Experiment exp, Sequence sequence, String name) {
        ROI2DEllipse roi = new ROI2DEllipse(0, 0, 5, 5); roi.setName(name); roi.setSelected(false);
        Spot spot = new Spot(roi); spot.getProperties().setCageID(1); spot.getProperties().setName(name);
        exp.getSpots().getSpotList().add(spot); sequence.addROI(roi); return spot;
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
