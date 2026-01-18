package plugins.fmp.multitools.experiment;

import java.util.Iterator;
import java.util.List;

import icy.roi.ROI;
import icy.roi.ROI2D;
import plugins.fmp.multitools.experiment.cages.cage.Cage;
import plugins.fmp.multitools.experiment.cages.cages.Cages;
import plugins.fmp.multitools.experiment.ids.SpotID;
import plugins.fmp.multitools.experiment.spots.spot.Spot;
import plugins.fmp.multitools.experiment.spots.spot.SpotString;
import plugins.fmp.multitools.experiment.spots.spots.Spots;
import plugins.kernel.roi.roi2d.ROI2DPolygon;

public class ExperimentUtils {

	public static void transferCamDataROI2DsToSpots(Experiment exp) {
		if (exp.getCages() == null)
			exp.setCages(new Cages());

		Spots allSpots = exp.getSpots();
		List<ROI2D> listROIsSpots = exp.getSeqCamData().getROIsContainingString("spot");
		for (ROI2D roi : listROIsSpots) {
			boolean found = false;
			for (Cage cage : exp.getCages().cagesList) {
				List<Spot> spots = cage.getSpotList(allSpots);
				for (Spot spot : spots) {
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
					Spot newSpot = new Spot(roi_new);
					int uniqueSpotID = allSpots.getNextUniqueSpotID();
					SpotID spotUniqueID = new SpotID(uniqueSpotID);
					newSpot.getProperties().setCageID(cageID);
					newSpot.setSpotUniqueID(spotUniqueID);
					newSpot.getProperties().setCagePosition(cagePosition);
					allSpots.addSpot(newSpot);
					cage.getSpotIDs().add(spotUniqueID);
				}
			}
		}
	}

	public void removeSpotsWithNoCamDataROI(Experiment exp) {
		if (exp.getCages() == null)
			exp.setCages(new Cages());

		Spots allSpots = exp.getSpots();
		List<ROI2D> listROIsSpots = exp.getSeqCamData().getROIsContainingString("spot");

		// spot with no corresponding roi? remove
		for (Cage cage : exp.getCages().cagesList) {
			List<Spot> spots = cage.getSpotList(allSpots);
			Iterator<Spot> iterator = spots.iterator();
			while (iterator.hasNext()) {
				Spot spot = iterator.next();
				boolean found = false;
				for (ROI roi : listROIsSpots) {
					if (roi.getName().equals(spot.getRoi().getName())) {
						found = true;
						break;
					}
				}
				if (!found) {
					// Remove spot ID and spot from global array
					SpotID spotID = spot.getSpotUniqueID();
					cage.getSpotIDs().remove(spotID);
					allSpots.getSpotList().remove(spot);
					iterator.remove();
				}
			}
		}
	}

	public static void transferSpotsToCamDataSequence(Experiment exp) {
		if (exp.getCages() == null)
			return;

		Spots allSpots = exp.getSpots();
		List<ROI2D> listROISSpots = exp.getSeqCamData().getROIsContainingString("spot");
		// roi with no corresponding cap? add ROI
		for (Cage cage : exp.getCages().cagesList) {
			List<Spot> spotList = cage.getSpotList(allSpots);
			for (Spot spot : spotList) {
				boolean found = false;
				for (ROI roi : listROISSpots) {
					if (spot.getRoi() == null)
						continue;
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
