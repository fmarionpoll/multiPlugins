package plugins.fmp.multiSPOTS96.dlg.spotsMeasures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import icy.roi.ROI2D;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.spot.Spot;

/**
 * Spot ROIs on the camera sequence whose name starts with {@code "spot"} (case
 * insensitive).
 */
public final class SpotSequenceRois {

	private SpotSequenceRois() {
	}

	public static boolean nameLooksLikeSpotRoi(String name) {
		return name != null && name.toLowerCase().startsWith("spot");
	}

	/**
	 * Distinct ROI names on the sequence that look like spot ROIs, sorted
	 * alphabetically (case-insensitive).
	 */
	public static List<String> spotRoiNamesFromSequence(Experiment exp) {
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null) {
			return Collections.emptyList();
		}
		List<ROI2D> roiList = exp.getSeqCamData().getSequence().getROI2Ds();
		if (roiList == null || roiList.isEmpty()) {
			return Collections.emptyList();
		}
		Set<String> seen = new LinkedHashSet<>();
		for (ROI2D roi : roiList) {
			if (roi == null)
				continue;
			String name = roi.getName();
			if (!nameLooksLikeSpotRoi(name))
				continue;
			seen.add(name);
		}
		List<String> out = new ArrayList<>(seen);
		Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
		return out;
	}

	public static List<Spot> allSpotsFromSequence(Experiment exp) {
		List<Spot> out = new ArrayList<>();
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null)
			return out;
		List<ROI2D> roiList = exp.getSeqCamData().getSequence().getROI2Ds();
		if (roiList == null || roiList.isEmpty())
			return out;
		for (ROI2D roi : roiList) {
			if (roi == null)
				continue;
			String name = roi.getName();
			if (!nameLooksLikeSpotRoi(name))
				continue;
			Spot spot = exp.getCages().getSpotFromROIName(name, exp.getSpots());
			if (spot != null)
				out.add(spot);
		}
		return out;
	}

	public static List<Spot> selectedSpotsFromSequence(Experiment exp) {
		List<Spot> out = new ArrayList<>();
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null)
			return out;
		List<ROI2D> roiList = exp.getSeqCamData().getSequence().getROI2Ds();
		if (roiList == null || roiList.isEmpty())
			return out;
		ROI2D firstSpotRoi = null;
		for (ROI2D roi : roiList) {
			if (roi == null)
				continue;
			String name = roi.getName();
			if (!nameLooksLikeSpotRoi(name))
				continue;
			if (firstSpotRoi == null)
				firstSpotRoi = roi;
			if (!roi.isSelected())
				continue;
			Spot spot = exp.getCages().getSpotFromROIName(name, exp.getSpots());
			if (spot != null)
				out.add(spot);
		}
		if (out.isEmpty() && firstSpotRoi != null) {
			String name = firstSpotRoi.getName();
			Spot spot = exp.getCages().getSpotFromROIName(name, exp.getSpots());
			if (spot != null)
				out.add(spot);
		}
		return out;
	}
}
