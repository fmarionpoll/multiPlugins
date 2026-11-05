package plugins.fmp.multicafe.dlg.levels;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import icy.roi.ROI;
import icy.roi.ROI2D;
import icy.sequence.Sequence;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryIntakes;
import plugins.fmp.multitools.experiment.capillary.IntakeEvaporationOverlay;
import plugins.fmp.multitools.service.CorrelationGulpPreview;

/** Owns temporary intake overlays and restores stored overlays when a session is hidden or closed. */
final class IntakeOverlayController {
    private Capillary roiCapillary;
    private boolean[] roiCandidates;
    private double[] roiAmplitudes, roiBottoms;
    private boolean roiShowGaps, roiStored, roiCompensated;
    private CorrelationGulpPreview roiPreview;
    void invalidate() { roiCandidates = null; }
    void forget() { roiCapillary = null; roiPreview = null; roiCandidates = null; }
    void update(Experiment experiment, Capillary selected, CorrelationGulpPreview preview,
            boolean inactive, boolean show, boolean compensated, boolean showDiagnostics,
            double[] amplitudes, double[] positions) {
        Sequence seq = experiment.getSeqKymos() == null ? null : experiment.getSeqKymos().getSequence();
        if (seq == null) return;
        Capillary cap = inactive ? null : selected;
        double[] heights = !inactive && show && preview != null ? amplitudes : null;
        double[] bottoms = heights == null ? null : positions;
        boolean[] chosen = heights == null ? null : new boolean[heights.length];
        if (chosen != null) for (int i = 1; i < chosen.length; i++) chosen[i] = heights[i] > 0;
        boolean stored = inactive || (show && chosen == null);
		if (!inactive && cap == roiCapillary && stored == roiStored
				&& roiCompensated == compensated && Arrays.equals(chosen, roiCandidates)
				&& roiShowGaps == showDiagnostics && Arrays.equals(bottoms, roiBottoms)
				&& Arrays.equals(heights, roiAmplitudes) && (chosen == null || roiPreview == preview))
			return;
		List<Integer> frames = new ArrayList<>();
		if (roiCapillary != null)
			frames.add(roiCapillary.getKymographIndex());
		if (cap != null)
			frames.add(cap.getKymographIndex());
		List<ROI> old = new ArrayList<>();
		for (ROI2D roi : seq.getROI2Ds())
			if (CapillaryIntakes.isOverlay(roi) && frames.contains(roi.getT()) && !"true"
					.equals(roi.getProperty(plugins.fmp.multitools.service.MeniscusRefinementPreview.ROI_PROPERTY)))
				old.add(roi);
		List<ROI2D> rois = new ArrayList<>();
		if (chosen != null) {
			int t = cap.getKymographIndex();
			for (int i = 1; i < chosen.length; i++)
				if (chosen[i] && Double.isFinite(bottoms[i]))
					rois.add(CapillaryIntakes.createRoi(cap.getKymographPrefix(), i, bottoms[i], heights[i],
							t));
			if (preview.hasEvaporationReference)
				rois.addAll(IntakeEvaporationOverlay.createRois(cap.getKymographPrefix(), heights,
						preview.raw, preview.evaporation, t, compensated));
		} else if (stored) {
			Capillary owner = inactive ? roiCapillary : cap;
			if (owner != null)
				rois.addAll(owner.getCorrelationGulpROIsForCapillaryAtT(owner.getKymographIndex()));
		}
		seq.beginUpdate();
		try {
			if (!old.isEmpty())
				seq.removeROIs(old, false);
			if (!rois.isEmpty())
				seq.addROIs(rois, false);
		} finally {
			seq.endUpdate();
		}
		roiCapillary = cap;
		roiCandidates = chosen;
		roiAmplitudes = heights;
		roiBottoms = bottoms;
		roiShowGaps = showDiagnostics;
		roiStored = stored;
		roiCompensated = compensated;
		roiPreview = chosen == null ? null : preview;
	}

}
