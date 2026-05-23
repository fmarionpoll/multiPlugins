package plugins.fmp.multitools.tools.chart.interaction;

import java.util.List;

import icy.gui.viewer.Viewer;
import icy.roi.ROI2D;
import icy.sequence.Sequence;

import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.experiment.spot.Spot;

/**
 * Moves the camera viewer to a spot time index and selects the spot ROI on the sequence using the
 * ROI instance attached to the sequence (required for correct ICY selection rendering).
 */
public final class SpotChartRoiFocus {

	private SpotChartRoiFocus() {
	}

	public static void moveViewerToSpotTAndSelectRoi(SequenceCamData seqCam, Spot spot) {
		if (seqCam == null || spot == null) {
			return;
		}
		Sequence seq = seqCam.getSequence();
		if (seq == null) {
			return;
		}

		Viewer v = seq.getFirstViewer();
		if (v != null && spot.getSpotCamDataT() >= 0) {
			v.setPositionT(spot.getSpotCamDataT());
		}

		ROI2D seqRoi = findSpotRoiOnSequence(seq, spot);
		if (seqRoi == null) {
			return;
		}

		clearSpotRoiSelectionFlags(seq);
		seqRoi.setSelected(true);
		seq.setFocusedROI(seqRoi);
		seq.setSelectedROI(seqRoi);
		seqCam.centerDisplayOnRoi(seqRoi);
	}

	private static void clearSpotRoiSelectionFlags(Sequence seq) {
		List<ROI2D> rois = seq.getROI2Ds();
		if (rois == null) {
			return;
		}
		for (ROI2D r : rois) {
			if (r == null) {
				continue;
			}
			String n = r.getName();
			if (n != null && n.startsWith("spot")) {
				r.setSelected(false);
			}
		}
	}

	private static ROI2D findSpotRoiOnSequence(Sequence seq, Spot spot) {
		if (seq == null || spot == null) {
			return null;
		}
		String spotName = spot.getName();
		List<ROI2D> rois = seq.getROI2Ds();
		if (rois == null || rois.isEmpty()) {
			return spot.getRoi();
		}

		if (spotName != null) {
			for (ROI2D r : rois) {
				if (r == null) {
					continue;
				}
				String n = r.getName();
				if (n != null && n.startsWith("spot") && spotName.equals(n)) {
					return r;
				}
			}
		}

		ROI2D fromSpot = spot.getRoi();
		if (fromSpot != null) {
			String roiName = fromSpot.getName();
			if (roiName != null) {
				for (ROI2D r : rois) {
					if (r != null && roiName.equals(r.getName())) {
						return r;
					}
				}
			}
		}
		return fromSpot;
	}
}
