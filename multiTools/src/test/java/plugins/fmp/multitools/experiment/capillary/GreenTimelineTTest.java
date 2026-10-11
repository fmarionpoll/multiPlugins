package plugins.fmp.multitools.experiment.capillary;

import static org.junit.Assert.*;
import java.awt.geom.Line2D;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import icy.roi.ROI2D;
import plugins.fmp.multitools.tools.ROI2D.AlongT;
import plugins.kernel.roi.roi2d.ROI2DLine;

public class GreenTimelineTTest {
    @BeforeClass public static void initializeIcy() { icy.preferences.IcyPreferences.init(); }

    private ROI2DLine line(double x) {
        ROI2DLine line = new ROI2DLine(new Line2D.Double(x, 0, x, 100));
        line.setName("line0L");
        return line;
    }

    @Test public void legacyEditsRemainLiveIncludingListReplacementAndStartChanges() {
        Capillary cap = new Capillary(line(10));
        AlongT first = new AlongT(0, line(10)), later = new AlongT(25, line(20));
        cap.setAlongTList(Arrays.asList(first, later));
        assertSame(later, cap.getAlongTAtT(50));
        later.setStart(60);
        assertSame(first, cap.getAlongTAtT(50));
        assertSame(later, cap.getAlongTAtT(60));
        cap.getAlongTList().remove(first);
        assertNull(cap.getAlongTAtT(0));
        cap.setAlongTList(Arrays.asList(first));
        assertSame(first, cap.getAlongTAtT(100));
        cap.addAlongTAtStartIfMissing(25);
        assertEquals(2, cap.getAlongTList().size());
        assertNotSame(first.getRoi(), cap.getAlongTAtT(25).getRoi());
        cap.removeROI2DIntervalStartingAt(25);
        assertSame(first, cap.getAlongTAtT(25));
    }

    @Test public void importsAndTrackingInjectionShareOrderedPhaseEditing() {
        Capillary cap = new Capillary(line(10));
        cap.addAlongTFromImport(new AlongT(80, line(30)));
        cap.addAlongTFromImport(new AlongT(0, line(10)));
        assertEquals(0, cap.getIntervalStart(0));
        assertEquals(80, cap.getIntervalEnd(0));
        Map<Long, ROI2D> tracked = new LinkedHashMap<>();
        tracked.put(50L, line(22));
        tracked.put(25L, line(20));
        cap.injectTrackedRoisIntoAlongT(25, 50, tracked);
        assertEquals(4, cap.getAlongTList().size());
        assertEquals(20, ((ROI2DLine) cap.getRoiAtFrameT(49)).getLine().getX1(), 0);
        assertEquals(22, ((ROI2DLine) cap.getRoiAtFrameT(50)).getLine().getX1(), 0);
        assertEquals(10, ((ROI2DLine) cap.getRoiAtFrameT(0)).getLine().getX1(), 0);
        assertEquals(30, ((ROI2DLine) cap.getRoiAtFrameT(80)).getLine().getX1(), 0);
    }
}
