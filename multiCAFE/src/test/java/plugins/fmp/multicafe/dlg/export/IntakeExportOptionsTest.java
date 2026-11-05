package plugins.fmp.multicafe.dlg.export;

import static org.junit.Assert.*;

import java.awt.GridLayout;
import java.lang.reflect.Field;
import javax.swing.SwingUtilities;
import org.junit.Test;
import plugins.fmp.multitools.tools.results.ResultsOptions;

public class IntakeExportOptionsTest {
    @Test public void intakeSelectionReachesExportWithoutOrdinaryGulps() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                MCExport_ dialog = new MCExport_();
                Field field = MCExport_.class.getDeclaredField("tabGulps");
                field.setAccessible(true);
                Gulps gulps = (Gulps) field.get(dialog);
                gulps.init(new GridLayout(4, 2));
                assertEquals("intake", gulps.intakeCheckBox.getText());
                assertTrue(SwingUtilities.isDescendingFrom(gulps.intakeCheckBox, gulps));
                assertTrue(dialog.getGulpsMeasurementOptions().intake);
                assertFalse(dialog.getGulpsMeasurementOptions().lrPI);
                gulps.intakeCheckBox.setSelected(false);
                assertFalse(dialog.getGulpsMeasurementOptions().intake);
                gulps.nbGulpsCheckBox.setSelected(false);
                gulps.amplitudeGulpsCheckBox.setSelected(false);
                gulps.intakeCheckBox.setSelected(true);
                ResultsOptions options = dialog.getGulpsMeasurementOptions();
                assertTrue(options.intake);
                assertFalse(options.nbGulps);
                assertFalse(options.amplitudeGulps);
                gulps.nbGulpsCheckBox.setSelected(true);
                assertTrue(dialog.getGulpsMeasurementOptions().intake);
                assertTrue(dialog.getGulpsMeasurementOptions().nbGulps);
                gulps.sumCheckBox.setSelected(true);
                assertTrue(dialog.getGulpsMeasurementOptions().lrPI);
                gulps.intakeCheckBox.setSelected(false);
                assertFalse(dialog.getGulpsMeasurementOptions().intake);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
    }
}
