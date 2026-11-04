package plugins.fmp.multicafe.dlg.export;

import static org.junit.Assert.*;

import java.awt.GridLayout;
import java.lang.reflect.Field;
import javax.swing.SwingUtilities;
import org.junit.Test;
import plugins.fmp.multitools.tools.results.ResultsOptions;

public class SavedIntakeExportOptionsTest {
    @Test public void savedIntakeSelectionReachesExportWithoutOrdinaryGulps() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                MCExport_ dialog = new MCExport_();
                Field field = MCExport_.class.getDeclaredField("tabGulps");
                field.setAccessible(true);
                Gulps gulps = (Gulps) field.get(dialog);
                gulps.init(new GridLayout(4, 2));
                assertEquals("saved intake", gulps.savedIntakeCheckBox.getText());
                assertTrue(SwingUtilities.isDescendingFrom(gulps.savedIntakeCheckBox, gulps));
                assertTrue(dialog.getGulpsMeasurementOptions().savedIntake);
                assertFalse(dialog.getGulpsMeasurementOptions().lrPI);
                gulps.savedIntakeCheckBox.setSelected(false);
                assertFalse(dialog.getGulpsMeasurementOptions().savedIntake);
                gulps.nbGulpsCheckBox.setSelected(false);
                gulps.amplitudeGulpsCheckBox.setSelected(false);
                gulps.savedIntakeCheckBox.setSelected(true);
                ResultsOptions options = dialog.getGulpsMeasurementOptions();
                assertTrue(options.savedIntake);
                assertFalse(options.nbGulps);
                assertFalse(options.amplitudeGulps);
                gulps.nbGulpsCheckBox.setSelected(true);
                assertTrue(dialog.getGulpsMeasurementOptions().savedIntake);
                assertTrue(dialog.getGulpsMeasurementOptions().nbGulps);
                gulps.sumCheckBox.setSelected(true);
                assertTrue(dialog.getGulpsMeasurementOptions().lrPI);
                gulps.savedIntakeCheckBox.setSelected(false);
                assertFalse(dialog.getGulpsMeasurementOptions().savedIntake);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
    }
}
