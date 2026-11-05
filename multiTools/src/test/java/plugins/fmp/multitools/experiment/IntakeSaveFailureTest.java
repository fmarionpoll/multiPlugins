package plugins.fmp.multitools.experiment;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import plugins.fmp.multitools.experiment.capillary.Capillary;

public class IntakeSaveFailureTest {
    @Rule public TemporaryFolder files = new TemporaryFolder();
    @org.junit.BeforeClass public static void initializeIcy() { icy.preferences.IcyPreferences.init(); }

    @Test(timeout = 10000) public void measureWriteFailureIsReportedEvenWhenDescriptionsAreSaved() throws Exception {
        Path directory = files.getRoot().toPath();
        Experiment experiment = new Experiment() {
            @Override public boolean saveCapillariesMeasures(String path) { return false; }
        };
        experiment.setResultsDirectory(directory.toString());
        plugins.kernel.roi.roi2d.ROI2DLine roi = new plugins.kernel.roi.roi2d.ROI2DLine(0, 0, 0, 100);
        roi.setName("line0L");
        Capillary capillary = new Capillary(roi);
        capillary.getIntakes().set(new double[] {0, 2, 0}, 0, 0, .5, false);
        experiment.getCapillaries().getList().add(capillary);
        assertFalse(experiment.save_capillaries_description_and_measures());
        assertTrue(Files.isRegularFile(directory.resolve("CapillariesDescription.csv")));
        assertEquals(1, capillary.getIntakes().countEvents());
    }
}
