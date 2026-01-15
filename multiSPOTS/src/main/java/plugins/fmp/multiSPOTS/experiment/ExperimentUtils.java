package plugins.fmp.multiSPOTS.experiment;

import java.util.Iterator;
import java.util.List;

import icy.roi.ROI;
import plugins.fmp.multiSPOTS.experiment.cages.Cage;
import plugins.fmp.multiSPOTS.experiment.spots.Spot;
import plugins.fmp.multiSPOTS.experiment.spots.SpotsArray;
import plugins.fmp.multiSPOTS.tools.ROI2D.ROIUtilities;
import plugins.kernel.roi.roi2d.ROI2DPolygon;

public class ExperimentUtils {

	public static void transferCamDataROIStoSpots(Experiment exp) {
		if (exp.spotsArray == null)
			exp.spotsArray = new SpotsArray();

		List<ROI> listROISCap = ROIUtilities.getROIsContainingString("spot", exp.seqCamData.seq);
		for (ROI roi : listROISCap) {
			boolean found = false;
			for (Spot spot : exp.spotsArray.spotsList) {
				if (spot.getRoi() != null && roi.getName().equals(spot.getRoi().getName())) {
					found = true;
					break;
				}
			}
			if (!found) {
				ROI2DPolygon roi_new = new ROI2DPolygon();
				exp.spotsArray.spotsList.add(new Spot(roi_new));
			}
		}

		// cap with no corresponding roi? remove
		Iterator<Spot> iterator = exp.spotsArray.spotsList.iterator();
		while (iterator.hasNext()) {
			Spot spot = iterator.next();
			boolean found = false;
			for (ROI roi : listROISCap) {
				if (roi.getName().equals(spot.getRoi().getName())) {
					found = true;
					break;
				}
			}
			if (!found)
				iterator.remove();
		}
	}

	public static void transferSpotsToCamDataSequence(Experiment exp) {
		if (exp.spotsArray == null)
			return;

		List<ROI> listROISSpots = ROIUtilities.getROIsContainingString("spot", exp.seqCamData.seq);
		// roi with no corresponding cap? add ROI
		for (Spot spot : exp.spotsArray.spotsList) {
			boolean found = false;
			for (ROI roi : listROISSpots) {
				if (roi.getName().equals(spot.getRoi().getName())) {
					found = true;
					break;
				}
			}
			if (!found)
				exp.seqCamData.seq.addROI(spot.getRoi());
		}
	}

	public static void transferCagesToCamDataSequence(Experiment exp) {
		if (exp.spotsArray == null)
			return;

		List<ROI> roisAlreadyTransferred = ROIUtilities.getROIsContainingString("cage", exp.seqCamData.seq);
		// roi with no corresponding cap? add ROI
		for (Cage cage : exp.getCages().cagesList) {
			boolean found = false;
			for (ROI roi : roisAlreadyTransferred) {
				if (roi.getName().equals(cage.getRoi().getName())) {
					found = true;
					break;
				}
			}
			if (!found)
				exp.seqCamData.seq.addROI(cage.getRoi());
		}
	}
}
