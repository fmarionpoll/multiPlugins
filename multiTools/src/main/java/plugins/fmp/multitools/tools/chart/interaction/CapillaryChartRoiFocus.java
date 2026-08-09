package plugins.fmp.multitools.tools.chart.interaction;

import java.util.ArrayList;
import java.util.List;

import icy.gui.viewer.Viewer;
import icy.roi.ROI2D;
import icy.sequence.Sequence;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.ViewerFMP;

/**
 * Shared capillary selection from chart clicks: focus ROI on
 * {@code seqCamData} and jump the kymograph viewer.
 */
public final class CapillaryChartRoiFocus {

	private CapillaryChartRoiFocus() {
	}

	public static void selectClickedCapillary(Experiment exp, Capillary capillary, double timeMinutes) {
		if (capillary == null) {
			Logger.warn("Clicked capillary is null");
			return;
		}
		int frameIndex = ChartCamFrameNavigation.getFrameIndexFromTimeMinutes(exp, timeMinutes);
		selectKymographForCapillary(exp, capillary);
		selectCapillaryAtT(exp, capillary, frameIndex);
	}

	public static void selectKymographForCapillary(Experiment exp, Capillary capillary) {
		if (exp == null || capillary == null) {
			Logger.warn("Cannot select kymograph: experiment or capillary is null");
			return;
		}
		if (exp.getSeqKymos() == null || exp.getSeqKymos().getSequence() == null) {
			return;
		}

		Viewer v = exp.getSeqKymos().getSequence().getFirstViewer();
		if (v == null) {
			v = new ViewerFMP(exp.getSeqKymos().getSequence(), true, true);
		}

		List<String> kymographImagesList = exp.getSeqKymos().getImagesList();
		int kymographIndex = capillary.deriveKymographIndexFromImageList(kymographImagesList);
		if (kymographIndex >= 0 && kymographIndex < exp.getSeqKymos().getSequence().getSizeT()) {
			v.setPositionT(kymographIndex);
			v.toFront();
		}
	}

	public static void selectCapillaryAtT(Experiment exp, Capillary capillary, int frameIndex) {
		if (exp == null || capillary == null) {
			Logger.warn("Cannot select capillary: experiment or capillary is null");
			return;
		}
		if (exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null) {
			return;
		}

		Sequence seq = exp.getSeqCamData().getSequence();
		Viewer v = seq.getFirstViewer();
		if (v == null) {
			v = new ViewerFMP(seq, true, true);
		}
		v.toFront();
		if (frameIndex >= 0) {
			v.setPositionT(frameIndex);
		}

		ROI2D roi = capillary.getRoiAtFrameT(frameIndex);
		if (roi == null) {
			return;
		}
		ROI2D seqRoi = resolveRoiOnSequence(seq, roi);
		if (seqRoi == null) {
			Logger.warn("Capillary ROI is not attached to the camera sequence (no instance/name match): "
					+ roi.getName());
			return;
		}
		seq.setFocusedROI(seqRoi);
		seq.setSelectedROI(seqRoi);
	}

	private static ROI2D resolveRoiOnSequence(Sequence seq, ROI2D fromCapillary) {
		if (seq == null || fromCapillary == null) {
			return null;
		}
		ArrayList<ROI2D> onSeq = seq.getROI2Ds();
		for (ROI2D r : onSeq) {
			if (r == fromCapillary) {
				return r;
			}
		}
		String name = fromCapillary.getName();
		if (name != null) {
			for (ROI2D r : onSeq) {
				if (name.equals(r.getName())) {
					return r;
				}
			}
		}
		return null;
	}
}
