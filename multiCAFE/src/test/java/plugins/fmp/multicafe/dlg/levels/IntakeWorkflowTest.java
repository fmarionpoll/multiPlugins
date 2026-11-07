package plugins.fmp.multicafe.dlg.levels;

import static org.junit.Assert.*;

import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import icy.image.IcyBufferedImage;
import icy.sequence.Sequence;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryIntakes;
import plugins.fmp.multitools.experiment.sequence.SequenceKymos;
import plugins.fmp.multitools.experiment.timebase.FrameTimeScale;
import plugins.fmp.multitools.service.CorrelationGulpPreview;
import plugins.fmp.multitools.service.ManualIntakeEdits;
import plugins.fmp.multitools.tools.polyline.Level2D;

public class IntakeWorkflowTest {
    private Experiment experiment;
    private Sequence sequence;

    @BeforeClass public static void initializeIcy() { icy.preferences.IcyPreferences.init(); }

    @Before public void createExperiment() {
        experiment = new Experiment();
        long[] times = new long[50];
        for (int i = 0; i < times.length; i++) times[i] = i * 60000L;
        experiment.setFrameTimeScale(FrameTimeScale.fromArrays(0, times, null, 0, times.length));
        sequence = new Sequence();
        for (int c = 0; c < 3; c++) {
            Capillary cap = new Capillary();
            cap.setKymographIndex(c);
            cap.setNFlies(c == 0 ? 1 : 0);
            double[] x = new double[50], y = new double[50];
            for (int i = 0; i < y.length; i++) {
                x[i] = i;
                y[i] = 20 + .1 * i + .02 * Math.sin(i + c) + (c == 0 && i >= 20 ? 5 : 0);
            }
            cap.getTopRaw().polylineLevel = new Level2D(x, y, y.length);
            experiment.getCapillaries().getList().add(cap);
            sequence.setImage(c, 0, IcyBufferedImage.createFrom(new BufferedImage(50, 100, BufferedImage.TYPE_INT_RGB)));
        }
        SequenceKymos kymos = new SequenceKymos();
        kymos.setSequence(sequence);
        experiment.setSeqKymos(kymos);
    }

    @After public void closeImages() { sequence.close(); }

    @Test public void explicitlyChosenThresholdKeepsProvenanceEvenWhenEqualToAutomatic() {
        Capillary cap = experiment.getCapillaries().getList().get(0);
        CorrelationGulpPreview preview = CorrelationGulpPreview.computeAccepted(experiment, cap, false);
        new AutomaticIntakeResult(preview, true, false, preview.noise.automatic).install(cap);
        assertTrue(cap.getIntakes().isManualThreshold());
        assertEquals(preview.noise.automatic, cap.getIntakes().getThresholdPxPerMin(), 0);
        new AutomaticIntakeResult(preview, true, false).install(cap);
        assertFalse(cap.getIntakes().isManualThreshold());
    }

    @Test public void preparedEditsMatchCsvAndSavedGeometryWithoutRecomputingOnInstall() throws Exception {
        Capillary cap = experiment.getCapillaries().getList().get(0);
        cap.getGulps().ensureSize(50);
        cap.getGulps().setAmplitudeAt(20, 7.5);
        cap.setGulpMeasuresDirty(false);
        CorrelationGulpPreview preview = CorrelationGulpPreview.computeAccepted(experiment, cap, false);
        ManualIntakeEdits edits = new ManualIntakeEdits();
        edits.replace(30, 30, 4.5, 40);
        Map<Capillary, Double> thresholds = new IdentityHashMap<>();
        thresholds.put(cap, .75);
        Map<Capillary, Boolean> manual = new IdentityHashMap<>();
        manual.put(cap, true);
        Map<Capillary, String> overrides = new IdentityHashMap<>();
        overrides.put(cap, edits.options());
        EditedIntakeSnapshot snapshot = new EditedIntakeSnapshot(experiment,
                new ArrayList<>(experiment.getCapillaries().getList()), true, false,
                thresholds, manual, overrides, new IdentityHashMap<>());
        thresholds.put(cap, 99.0); // captured settings must be independent of later edits
        snapshot.prepare(message -> assertFalse(SwingUtilities.isEventDispatchThread()));
        assertFalse(cap.getIntakes().hasMeasures());
        assertEquals(4.5, snapshot.results.get(cap).amplitudes[30], 0);
        SwingUtilities.invokeAndWait(() -> assertTrue(snapshot.install(IntakePersistence.Destination.SAVE_INTAKE)));
        assertEquals(7.5, cap.getGulps().getHeightSeries().ypoints[20], 0);
        assertFalse("Saving separate intake must not mark ordinary gulps as edited", cap.isGulpMeasuresDirty());
        assertEquals(.75, cap.getIntakes().getThresholdPxPerMin(), 0);
        assertEquals(40, cap.getIntakes().bottomAt(30, 0), 0);
        assertTrue(cap.getIntakes().getMethodOptions().contains(edits.options()));
        StringWriter csv = new StringWriter();
        BufferedWriter writer = new BufferedWriter(csv);
        IntakeReviewExport.writeReviewCsv(writer, experiment, cap, preview, .75, true, true, false, edits);
        writer.flush();
        try (org.apache.commons.csv.CSVParser parser = org.apache.commons.csv.CSVParser.parse(csv.toString(),
                org.apache.commons.csv.CSVFormat.DEFAULT.withDelimiter(';').withCommentMarker('#').withFirstRecordAsHeader())) {
            org.apache.commons.csv.CSVRecord row = parser.getRecords().get(30);
            assertEquals("30", row.get("frame"));
            assertEquals(4.5, Double.parseDouble(row.get("candidate_amplitude_px")), 0);
            assertEquals(40, Double.parseDouble(row.get("fitted_meniscus_px")), 0);
            assertEquals("true", row.get("manual_override"));
        }
    }

    @Test public void sourceChangeRejectsEntirePreparedExperiment() throws Exception {
        EditedIntakeSnapshot snapshot = new EditedIntakeSnapshot(experiment,
                new ArrayList<>(experiment.getCapillaries().getList()), true, false,
                new IdentityHashMap<>(), new IdentityHashMap<>(), new IdentityHashMap<>(), new IdentityHashMap<>());
        snapshot.prepare(message -> { });
        experiment.getCapillaries().getList().get(1).getTopRaw().polylineLevel.ypoints[10] += 1;
        SwingUtilities.invokeAndWait(() -> assertFalse(snapshot.install(IntakePersistence.Destination.REPLACE_GULPS_AND_SAVE)));
        for (Capillary cap : experiment.getCapillaries().getList())
            assertFalse(cap.getIntakes().hasMeasures());
    }

    @Test public void toprawShiftAbsorbsStoredOffsetAndPreservesHandPlacedBars() {
        CapillaryIntakes original = new CapillaryIntakes();
        original.set(new double[] {0, 2, 3}, 0, 0, .75, true);
        original.setBottomPositions(new double[] {Double.NaN, 21.5, 24});
        ManualIntakeEdits edits = new ManualIntakeEdits();
        edits.shiftPositions(1.5, false);
        edits.replace(2, 2, 3, 24);
        original.setMethodOptions("v6.2,source=accepted_topraw" + edits.options() + ",scale=1.0");

        CapillaryIntakes rebased = IntakePersistence.rebaseStoredIntake(original, new double[] {18, 20, 23}, 2, 0);
        assertEquals(21.5, original.bottomAt(1, -1), 0);
        assertEquals(22, rebased.bottomAt(1, -1), 0);
        assertEquals(24, rebased.bottomAt(2, -1), 0);
        assertArrayEquals(new double[] {0, 2, 3}, rebased.getEvents().getHeightSeries().ypoints, 0);
        assertEquals(.75, rebased.getThresholdPxPerMin(), 0);
        assertEquals("v6.2,source=accepted_topraw,manual_intakes=2:2:3.0:24.0,scale=1.0", rebased.getMethodOptions());
    }
}
