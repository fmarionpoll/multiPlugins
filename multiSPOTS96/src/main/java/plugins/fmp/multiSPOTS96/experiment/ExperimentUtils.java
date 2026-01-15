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
		if (exp.getCages() == null)
			exp.getCages() = new CagesArray();

		List<ROI2D> listROIsSpots = exp.getSeqCamData().getROIsContainingString("spot");
		for (ROI2D roi : listROIsSpots) {
			boolean found = false;
			for (Cage cage : exp.getCages().cagesList) {
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
					Cage cage = exp.getCages().getCageFromID(cageID);
					cage.spotsArray.getSpotsList().add(new Spot(roi_new));
				}
			}
		}
	}

	public void removeSpotsWithNoCamDataROI(Experiment exp) {
		if (exp.getCages() == null)
			exp.getCages() = new CagesArray();

		List<ROI2D> listROIsSpots = exp.getSeqCamData().getROIsContainingString("spot");

		// spot with no corresponding roi? remove
		for (Cage cage : exp.getCages().cagesList) {
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
		if (exp.getCages() == null)
			return;

		List<ROI2D> listROISSpots = exp.getSeqCamData().getROIsContainingString("spot");
		// roi with no corresponding cap? add ROI
		for (Cage cage : exp.getCages().cagesList) {
			for (Spot spot : cage.spotsArray.getSpotsList()) {
				boolean found = false;
				for (ROI roi : listROISSpots) {
					if (roi.getName().equals(spot.getRoi().getName())) {
						found = true;
						break;
					}
				}
				if (!found)
					exp.getSeqCamData().getSequence().addROI(spot.getRoi());
			}
		}
	}

	public static void transferCagesToCamDataSequence(Experiment exp) {
		if (exp.getCages() == null)
			return;

		List<ROI2D> roisAlreadyTransferred = exp.getSeqCamData().getROIsContainingString("cage");
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
				exp.getSeqCamData().getSequence().addROI(cage.getRoi());
		}
	}

	public static void removeCageAndSpotROISFromCamDataSequence(Experiment exp) {
		if (exp.getCages() == null)
			return;

		List<ROI2D> roisCages = exp.getSeqCamData().getROIsContainingString("cage");
		exp.getSeqCamData().getSequence().removeROIs(roisCages, false);

		List<ROI2D> roisSpots = exp.getSeqCamData().getROIsContainingString("spot");
		exp.getSeqCamData().getSequence().removeROIs(roisSpots, false);

	}
}
