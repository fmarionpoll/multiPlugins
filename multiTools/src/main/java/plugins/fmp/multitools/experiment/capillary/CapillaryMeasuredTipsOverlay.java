package plugins.fmp.multitools.experiment.capillary;

import java.awt.Color;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import icy.roi.ROI2D;
import icy.roi.ROIEvent.ROIEventType;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillary.geometry.CapillaryPhaseGeometry;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.experiment.sequence.MeasureRoiSync;
import plugins.fmp.multitools.experiment.sequence.MeasureRoiSync.MeasureRoiFilter;
import plugins.kernel.roi.roi2d.ROI2DLine;
import plugins.kernel.roi.roi2d.ROI2DPolygon;

/**
 * CODEX Light-blue tip overlays ({@code caplength_xx}) for measured capillary
 * extents. Named so they never match the capillary ROI pattern {@code line},
 * and can be edited by the user then written back to
 * {@link CapillaryProperties} measured endpoints (and thus
 * CapillariesDescription.csv).
 */
public final class CapillaryMeasuredTipsOverlay {

	public static final String ROI_PREFIX = "caplength_";
	public static final Color ROI_COLOR = new Color(120, 200, 255);
	public static final String OUTLINE_PREFIX = "capshape_";
	private static final Map<SequenceCamData, Boolean> outlinesVisible =
			Collections.synchronizedMap(new WeakHashMap<SequenceCamData, Boolean>());

	public static void setOutlinesVisible(SequenceCamData sequence, boolean visible) {
		if (sequence != null)
			outlinesVisible.put(sequence, visible);
	}

	private CapillaryMeasuredTipsOverlay() {
	}

	/**
	 * Rebuilds tip ROIs from stored measured endpoints. Editable so the user can
	 * adjust tips like green {@code line*} capillaries.
	 *
	 * @return number of tip ROIs added
	 */
	public static int transferTipsToSequence(Capillaries capillaries, SequenceCamData seqCamData) {
		int t = 0;
		if (seqCamData != null && seqCamData.getSequence() != null && seqCamData.getSequence().getFirstViewer() != null)
			t = seqCamData.getSequence().getFirstViewer().getPositionT();
		return transferTipsToSequence(capillaries, seqCamData, t);
	}

	/**
	 * Rebuilds the physical-capillary overlay for the phase containing {@code t}.
	 */
	public static int transferTipsToSequence(Capillaries capillaries, SequenceCamData seqCamData, int t) {
		if (capillaries == null || seqCamData == null || seqCamData.getSequence() == null)
			return 0;
		List<ROI2D> rois = getROIsAtT(capillaries, seqCamData, t);
		seqCamData.getSequence().beginUpdate();
		try {
			MeasureRoiSync.updateMeasureROIsAt(t, seqCamData.getSequence(), MeasureRoiFilter.CAPILLARY_TIPS, rois);
		} finally {
			seqCamData.getSequence().endUpdate();
		}
		int shown = 0;
		for (ROI2D roi : rois)
			if (roi.getName().startsWith(ROI_PREFIX))
				shown++;
		return shown;
	}

	/** Creates detached display ROIs without changing the sequence or stored geometry. */
	public static List<ROI2D> getROIsAtT(Capillaries capillaries, SequenceCamData seqCamData, int t) {
		List<ROI2D> rois = new ArrayList<>();
		if (capillaries == null)
			return rois;
		int shown = 0;
		for (Capillary cap : capillaries.getList()) {
			CapillaryProperties props = cap.getProperties();
			Line2D physical = cap.getPhaseGeometry().getBlueAt(t);
			if (physical == null) {
				if (props.hasMeasuredEndpoints()) {
					Point2D start = props.getMeasuredStart();
					Point2D end = props.getMeasuredEnd();
					physical = new Line2D.Double(start, end);
				} else {
					plugins.fmp.multitools.tools.ROI2D.AlongT phase = cap.getAlongTAtT(t);
					if (phase != null && phase.getRoi() instanceof ROI2DLine)
						physical = ((ROI2DLine) phase.getRoi()).getLine();
				}
				if (physical == null)
					continue;
			}
			ROI2DLine roi = new ROI2DLine(physical);
			roi.setName(ROI_PREFIX + tipSuffix(cap, shown));
			roi.setColor(ROI_COLOR);
			roi.setStroke(3);
			roi.setReadOnly(false);
			roi.setT(-1);
			rois.add(roi);
			double width = cap.getPhaseGeometry().getWidthAt(t);
			if (Boolean.TRUE.equals(outlinesVisible.get(seqCamData)) && Double.isFinite(width)) {
				Point2D[] corners = new CapillaryPhaseGeometry(t, null, physical, width).getPhysicalCorners();
				if (corners != null) {
					ROI2DPolygon outline = new ROI2DPolygon(Arrays.asList(corners));
					outline.setName(OUTLINE_PREFIX + tipSuffix(cap, shown));
					outline.setColor(new Color(80, 220, 120));
					outline.setStroke(1);
					outline.setOpacity(0.f);
					outline.setReadOnly(true);
					outline.setT(-1);
					// The outline is diagnostic; edits still belong to the blue line.
					roi.addListener(event -> {
						if (event.getType() == ROIEventType.ROI_CHANGED) {
							Point2D[] updated = new CapillaryPhaseGeometry(t, null, roi.getLine(), width)
									.getPhysicalCorners();
							if (updated != null)
								outline.setPoints(Arrays.asList(updated));
						}
					});
					rois.add(outline);
				}
			}
			shown++;
		}
		return rois;
	}

	public static void removeTipsFromSequence(SequenceCamData seqCamData) {
		if (seqCamData != null && seqCamData.getSequence() != null) {
			seqCamData.removeROIsContainingString(ROI_PREFIX);
			seqCamData.removeROIsContainingString(OUTLINE_PREFIX);
		}
	}

	/**
	 * Copies tip ROI geometry back into capillary properties (measured endpoints
	 * and pixel length). Call before saving CapillariesDescription.csv.
	 *
	 * @return number of capillaries updated from tip ROIs
	 */
	public static int transferTipsFromSequence(Capillaries capillaries, SequenceCamData seqCamData) {
		int t = 0;
		if (seqCamData != null && seqCamData.getSequence() != null && seqCamData.getSequence().getFirstViewer() != null)
			t = seqCamData.getSequence().getFirstViewer().getPositionT();
		return transferTipsFromSequence(capillaries, seqCamData, t);
	}

	/** Saves edited blue lines as keyframes at the active green phase start. */
	public static int transferTipsFromSequence(Capillaries capillaries, SequenceCamData seqCamData, int t) {
		return transferTipsFromSequence(capillaries, seqCamData, t, false, false);
	}

	/** Explicit manual editing: add at t, or update only the active blue phase. */
	public static int editBluePositionFromSequence(Capillaries capillaries, SequenceCamData seqCamData, int t,
			boolean addAtCurrentFrame) {
		return transferTipsFromSequence(capillaries, seqCamData, t, addAtCurrentFrame, true);
	}

	private static int transferTipsFromSequence(Capillaries capillaries, SequenceCamData seqCamData, int t,
			boolean addAtCurrentFrame, boolean bluePhaseOnly) {
		if (capillaries == null || seqCamData == null || seqCamData.getSequence() == null)
			return 0;

		List<ROI2D> tipRois = seqCamData.findROIsMatchingNamePattern(ROI_PREFIX);
		int updated = 0;
		for (Capillary cap : capillaries.getList()) {
			String suffix = tipSuffix(cap, -1);
			if (suffix.isEmpty())
				continue;
			String want = ROI_PREFIX + suffix;
			ROI2D match = null;
			for (ROI2D roi : tipRois) {
				if (roi.getName() != null && roi.getName().equals(want)) {
					match = roi;
					break;
				}
			}
			if (!(match instanceof ROI2DLine))
				continue;

			Line2D line = ((ROI2DLine) match).getLine();
			if (line == null)
				continue;
			Point2D p1 = line.getP1();
			Point2D p2 = line.getP2();
			if (!(p1.distance(p2) > 0) || !Double.isFinite(p1.getX() + p1.getY() + p2.getX() + p2.getY()))
				continue;
			plugins.fmp.multitools.tools.ROI2D.AlongT phase = cap.getAlongTAtT(t);
			long phaseStart = phase == null ? t : phase.getStart();
			Long bluePhase = cap.getPhaseGeometry().getBluePhaseStartAt(t);
			if (bluePhase != null)
				phaseStart = Math.max(phaseStart, bluePhase);
			if (bluePhaseOnly) {
				// Legacy measurements can be displayed without a blue keyframe map.
				// Preserve that original pose before adding the first later position.
				if (addAtCurrentFrame && t > 0 && !cap.getPhaseGeometry().isInitialized()) {
					ROI2D reference = cap.getRoiAtFrameT(0);
					if (!(reference instanceof ROI2DLine))
						continue;
					Line2D initial = ((ROI2DLine) reference).getLine();
					if (cap.getProperties().hasMeasuredEndpoints())
						initial = new Line2D.Double(cap.getProperties().getMeasuredStart(), cap.getProperties().getMeasuredEnd());
					cap.getPhaseGeometry().initialize(0, ((ROI2DLine) reference).getLine(), initial);
				}
				if (!addAtCurrentFrame && bluePhase == null)
					continue;
				phaseStart = addAtCurrentFrame ? t : bluePhase;
			}
			if (cap.getPhaseGeometry().isInitialized())
				cap.getPhaseGeometry().putBlue(phaseStart, line);
			else if (phase != null && phase.getRoi() instanceof ROI2DLine)
				cap.getPhaseGeometry().initialize(phaseStart, ((ROI2DLine) phase.getRoi()).getLine(), line);
			else
				continue;

			CapillaryProperties props = cap.getProperties();
			// Keep the old single reference synchronized for old readers and old kymos.
			if (phaseStart == 0 || !props.hasMeasuredEndpoints())
				props.setMeasuredEndpoints(p1, p2);
			int pixels = (int) Math.round(p1.distance(p2));
			if (pixels > 0)
				cap.setPixels(pixels);
			props.setPixelsAutoMeasured(true);
			updated++;
		}
		return updated;
	}

	/**
	 * Suffix after {@link #ROI_PREFIX}: kymograph prefix (e.g. {@code 0L}), never
	 * including {@code line}, so tip names are never mistaken for capillary ROIs.
	 */
	public static String tipSuffix(Capillary cap, int fallbackIndex) {
		String prefix = cap.getKymographPrefix();
		if (prefix == null || prefix.isEmpty()) {
			String roiName = cap.getRoiName();
			if (roiName != null && roiName.startsWith("line") && roiName.length() > 4)
				prefix = roiName.substring(4);
		}
		if (prefix != null && !prefix.isEmpty())
			return prefix;
		return fallbackIndex >= 0 ? Integer.toString(fallbackIndex) : "";
	}
}
