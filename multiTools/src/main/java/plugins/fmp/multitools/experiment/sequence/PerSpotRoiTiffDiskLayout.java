package plugins.fmp.multitools.experiment.sequence;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cages.Cages;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.tools.Logger;

/**
 * One TIFF per spot ROI name under {@code baseDirectory} (legacy SPOTS-style layout).
 */
public final class PerSpotRoiTiffDiskLayout implements KymographDiskLayout {

	public static final PerSpotRoiTiffDiskLayout INSTANCE = new PerSpotRoiTiffDiskLayout();

	private PerSpotRoiTiffDiskLayout() {
	}

	@Override
	public List<ImageFileData> listImageDescriptors(String baseDirectory, Cages cages, Spots allSpots) {
		if (baseDirectory == null || baseDirectory.trim().isEmpty()) {
			throw new IllegalArgumentException("Base directory cannot be null or empty");
		}
		if (cages == null) {
			throw new IllegalArgumentException("Cages array cannot be null");
		}
		if (allSpots == null) {
			return new ArrayList<>();
		}

		String fullDirectory = baseDirectory + File.separator;

		if (cages.cagesList.isEmpty()) {
			Logger.warn("No cages found in cages array");
			return new ArrayList<>();
		}

		Cage firstCage = cages.cagesList.get(0);
		List<Spot> firstCageSpots = firstCage.getSpotList(allSpots);
		if (firstCageSpots.isEmpty()) {
			Logger.warn("No spots found in first cage");
			return new ArrayList<>();
		}

		int totalExpectedFiles = cages.cagesList.size() * firstCageSpots.size();
		List<ImageFileData> fileList = new ArrayList<>(totalExpectedFiles);

		for (Cage cage : cages.cagesList) {
			List<Spot> spots = cage.getSpotList(allSpots);
			if (spots.isEmpty()) {
				continue;
			}

			for (Spot spot : spots) {
				ImageFileData descriptor = new ImageFileData();
				descriptor.fileName = fullDirectory + spot.getRoi().getName() + ".tiff";
				descriptor.exists = new File(descriptor.fileName).exists();
				fileList.add(descriptor);
			}
		}
		return fileList;
	}
}
