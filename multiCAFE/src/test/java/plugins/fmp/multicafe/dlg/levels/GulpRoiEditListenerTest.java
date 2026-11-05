package plugins.fmp.multicafe.dlg.levels;

import static org.junit.Assert.*;

import java.awt.Color;
import java.awt.geom.Line2D;
import javax.swing.SwingUtilities;
import org.junit.Test;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryIntakes;
import plugins.kernel.roi.roi2d.ROI2DLine;

public class GulpRoiEditListenerTest {
    @org.junit.BeforeClass public static void initializeIcy() { icy.preferences.IcyPreferences.init(); }

    @Test public void viewingGulpDoesNotDirtyMeasuresButMovingItDoes() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Capillary cap = new Capillary();
            ROI2DLine roi = new ROI2DLine(10, 20, 10, 15);
            roi.setName("gulp0000010");
            roi.addListener(EditLevels.gulpRoiEditListener(cap));
            roi.setSelected(true);
            roi.setFocused(true);
            roi.setColor(Color.BLUE);
            roi.setStroke(3);
            roi.setSelected(false);
            roi.setFocused(false);
            assertFalse("Viewing and styling a gulp must not trigger the save prompt", cap.isGulpMeasuresDirty());
            roi.setLine(new Line2D.Double(10, 20, 10, 12));
            assertTrue("Editing ordinary gulp geometry must retain the save prompt", cap.isGulpMeasuresDirty());
        });
    }

    @Test public void intakeOverlayAndLevelEditsDoNotDirtyGulps() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Capillary cap = new Capillary();
            ROI2DLine intake = new ROI2DLine(10, 20, 10, 15);
            intake.setName("before_gulp0000010");
            CapillaryIntakes.markOverlay(intake);
            intake.addListener(EditLevels.gulpRoiEditListener(cap));
            intake.setLine(new Line2D.Double(10, 20, 10, 12));
            ROI2DLine level = new ROI2DLine(0, 20, 10, 20);
            level.setName("toplevel");
            level.addListener(EditLevels.gulpRoiEditListener(cap));
            level.setLine(new Line2D.Double(0, 21, 10, 21));
            assertFalse(cap.isGulpMeasuresDirty());
        });
    }
}
