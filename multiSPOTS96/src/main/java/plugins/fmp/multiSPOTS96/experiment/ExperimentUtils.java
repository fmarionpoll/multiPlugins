package plugins.fmp.multiSPOTS96.experiment;

import java.util.Iterator;
import java.util.List;

import icy.roi.ROI;
import icy.roi.ROI2D;
import plugins.fmp.multitools.experiment.cages.Cage;
import plugins.fmp.multitools.experiment.cages.CagesArray;
import plugins.fmp.multitools.experiment.spots.Spot;
import plugins.fmp.multitools.experiment.spots.SpotString;
import plugins.kernel.roi.roi2d.ROI2DPolygon;

public class ExperimentUtils {

	public static void transferCamDataROI2DsToSpots(Experiment exp) {
		if (exp.cagesArray == null)
			exp.cagesArray = new CagesArray();

		List<ROI2D> listROIsSpots = exp.seqCamData.getROIsContainingString("spot");
		for (ROI2D roi : listROIsSpots) {
			boolean found = false;
			for (Cage cage : exp.cagesArray.cagesList) {
				for (Spot spot : cage.spotsArray.getSpotsList()) {
					if (spot.getRoi() != null && roi.getName().equals(spot.getRoi().getName())) {
						found = true;
						break;
					}
				}
			}
			if (!found) {
				String name = roi.getName();
				ROI2DPolygon roi_new = new ROI2DPolygon();
				int cageID = SpotString.getCageIDFromSpotName(name);
				int cagePosition = SpotString.getSpotCagePositionFromSpotName(name);
				if (cageID >= 0 && cagePosition >= 0) {
					Cage cage = exp.cagesArray.getCageFromID(cageID);
					cage.spotsArray.getSpotsList().add(new Spot(roi_new));
				}
			}
		}
	}

	public void removeSpotsWithNoCamDataROI(Experiment exp) {
		if (exp.cagesArray == null)
			exp.cagesArray = new CagesArray();

		List<ROI2D> listROIsSpots = exp.seqCamData.getROIsContainingString("spot");

		// spot with no corresponding roi? remove
		for (Cage cage : exp.cagesArray.cagesList) {
			Iterator<Spot> iterator = cage.spotsArray.getSpotsList().iterator();
			while (iterator.hasNext()) {
				Spot spot = iterator.next();
				boolean found = false;
				for (ROI roi : listROIsSpots) {
					if (roi.getName().equals(spot.getRoi().getName())) {
						found = true;
						break;
					}
				}
				if (!found)
					iterator.remove();
			}
		}
	}

	public static void transferSpotsToCamDataSequence(Experiment exp) {
		if (exp.cagesArray == null)
			return;

		List<ROI2D> listROISSpots = exp.seqCamData.getROIsContainingString("spot");
		// roi with no corresponding cap? add ROI
		for (Cage cage : exp.cagesArray.cagesList) {
			for (Spot spot : cage.spotsArray.getSpotsList()) {
				boolean found = false;
				for (ROI roi : listROISSpots) {
					if (roi.getName().equals(spot.getRoi().getName())) {
						found = true;
						break;
					}
				}
				if (!found)
					exp.seqCamData.getSequence().addROI(spot.getRoi());
			}
		}
	}

	public static void transferCagesToCamDataSequence(Experiment exp) {
		if (exp.cagesArray == null)
			return;

		List<ROI2D> roisAlreadyTransferred = exp.seqCamData.getROIsContainingString("cage");
		// roi with no corresponding cap? add ROI
		for (Cage cage : exp.cagesArray.cagesList) {
			boolean found = false;
			for (ROI roi : roisAlreadyTransferred) {
				if (roi.getName().equals(cage.getRoi().getName())) {
					found = true;
					break;
				}
			}
			if (!found)
				exp.seqCamData.getSequence().addROI(cage.getRoi());
		}
	}

	public static void removeCageAndSpotROISFromCamDataSequence(Experiment exp) {
		if (exp.cagesArray == null)
			return;

		List<ROI2D> roisCages = exp.seqCamData.getROIsContainingString("cage");
		exp.seqCamData.getSequence().removeROIs(roisCages, false);

		List<ROI2D> roisSpots = exp.seqCamData.getROIsContainingString("spot");
		exp.seqCamData.getSequence().removeROIs(roisSpots, false);

	}
}
