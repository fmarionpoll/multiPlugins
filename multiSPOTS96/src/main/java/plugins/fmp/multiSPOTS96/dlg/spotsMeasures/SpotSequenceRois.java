package plugins.fmp.multiSPOTS96.dlg.spotsMeasures;

import java.util.ArrayList;
import java.util.List;

import icy.roi.ROI2D;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.spot.Spot;

/**
 * Spot ROIs on the camera sequence whose name starts with {@code "spot"}.
 */
public final class SpotSequenceRois {

	private SpotSequenceRois() {
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
			if (name == null || !name.startsWith("spot"))
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
			if (name == null || !name.startsWith("spot"))
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
