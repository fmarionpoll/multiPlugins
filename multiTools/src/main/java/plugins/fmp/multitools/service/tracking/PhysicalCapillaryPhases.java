package plugins.fmp.multitools.service.tracking;

import java.awt.geom.Line2D;
import java.util.SortedSet;
import java.util.TreeSet;
import icy.roi.ROI2D;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.geometry.CapillaryPhaseGeometryModel;
import plugins.kernel.roi.roi2d.ROI2DLine;

/** Actual blue phase starts, independent of green AlongT and legacy boundary markers. */
public final class PhysicalCapillaryPhases {
    private PhysicalCapillaryPhases() { }

    public static SortedSet<Long> starts(Capillaries caps) {
        SortedSet<Long> starts = new TreeSet<>();
        for (Capillary cap : caps.getList()) {
            starts.addAll(cap.getPhaseGeometry().getBlueKeyframes().keySet());
            if (cap.getProperties().hasMeasuredEndpoints()) starts.add(0L);
        }
        return starts;
    }

    /** Materialize old measured endpoints without substituting green corridors for physical data. */
    public static boolean ensureReference(Capillary cap) {
        CapillaryPhaseGeometryModel model = cap.getPhaseGeometry();
        if (model.isInitialized()) return true;
        if (!cap.getProperties().hasMeasuredEndpoints()) return false;
        ROI2D green = cap.getRoiAtFrameT(0);
        if (!(green instanceof ROI2DLine)) return false;
        model.initialize(0, ((ROI2DLine) green).getLine(), new Line2D.Double(
                cap.getProperties().getMeasuredStart(), cap.getProperties().getMeasuredEnd()));
        return true;
    }

    /** A proposal creates editable copies of preceding positions, never estimated displacements. */
    public static int addFromPrevious(Capillaries caps, int frame) {
        if (frame < 1) return 0;
        int added = 0;
        for (Capillary cap : caps.getList()) {
            if (!ensureReference(cap)) continue;
            CapillaryPhaseGeometryModel model = cap.getPhaseGeometry();
            if (model.getBlueStartingAt(frame) != null || model.getBluePhaseStartAt(frame - 1) == null) continue;
            model.putBlue(frame, model.getBlueAt(frame - 1), model.getWidthAt(frame - 1));
            added++;
        }
        return added;
    }

    public static int remove(Capillaries caps, long frame) {
        if (frame <= 0) return 0;
        int removed = 0;
        for (Capillary cap : caps.getList())
            if (cap.getPhaseGeometry().removeBluePhase(frame)) removed++;
        if (removed > 0 && frame <= Integer.MAX_VALUE) caps.getTrackingTimeline().remove((int) frame);
        return removed;
    }
}
