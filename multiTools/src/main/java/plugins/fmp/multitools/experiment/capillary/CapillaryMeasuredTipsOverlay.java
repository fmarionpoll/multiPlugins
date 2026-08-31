package plugins.fmp.multitools.experiment.capillary;

import java.awt.Color;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.List;

import icy.roi.ROI2D;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.kernel.roi.roi2d.ROI2DLine;

/**
 * Light-blue tip overlays ({@code caplength_xx}) for measured capillary extents.
 * Named so they never match the capillary ROI pattern {@code line}, and can be
 * edited by the user then written back to {@link CapillaryProperties} measured
 * endpoints (and thus CapillariesDescription.csv).
 */
public final class CapillaryMeasuredTipsOverlay {

	public static final String ROI_PREFIX = "caplength_";
	public static final Color ROI_COLOR = new Color(120, 200, 255);

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
		if (seqCamData != null && seqCamData.getSequence() != null
				&& seqCamData.getSequence().getFirstViewer() != null)
			t = seqCamData.getSequence().getFirstViewer().getPositionT();
		return transferTipsToSequence(capillaries, seqCamData, t);
	}

	/** Rebuilds the physical-capillary overlay for the phase containing {@code t}. */
	public static int transferTipsToSequence(Capillaries capillaries, SequenceCamData seqCamData, int t) {
		if (capillaries == null || seqCamData == null || seqCamData.getSequence() == null)
			return 0;
		removeTipsFromSequence(seqCamData);

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
			seqCamData.getSequence().addROI(roi);
			shown++;
		}
		return shown;
	}

	public static void removeTipsFromSequence(SequenceCamData seqCamData) {
		if (seqCamData != null && seqCamData.getSequence() != null)
			seqCamData.removeROIsContainingString(ROI_PREFIX);
	}

	/**
	 * Copies tip ROI geometry back into capillary properties (measured endpoints
	 * and pixel length). Call before saving CapillariesDescription.csv.
	 *
	 * @return number of capillaries updated from tip ROIs
	 */
	public static int transferTipsFromSequence(Capillaries capillaries, SequenceCamData seqCamData) {
		int t = 0;
		if (seqCamData != null && seqCamData.getSequence() != null
				&& seqCamData.getSequence().getFirstViewer() != null)
			t = seqCamData.getSequence().getFirstViewer().getPositionT();
		return transferTipsFromSequence(capillaries, seqCamData, t);
	}

	/** Saves edited blue lines as keyframes at the active green phase start. */
	public static int transferTipsFromSequence(Capillaries capillaries, SequenceCamData seqCamData, int t) {
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
			plugins.fmp.multitools.tools.ROI2D.AlongT phase = cap.getAlongTAtT(t);
			long phaseStart = phase == null ? t : phase.getStart();
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
