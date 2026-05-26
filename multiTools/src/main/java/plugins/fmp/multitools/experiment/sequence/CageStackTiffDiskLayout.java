package plugins.fmp.multitools.experiment.sequence;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cages.Cages;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.tools.Logger;

/**
 * One stacked TIFF per cage ({@code kymocage_<id>.tiff} or {@code .tif}) as produced by
 * {@link plugins.fmp.multitools.service.CageSpotKymographBuilder}.
 */
public final class CageStackTiffDiskLayout implements KymographDiskLayout {

	public static final CageStackTiffDiskLayout INSTANCE = new CageStackTiffDiskLayout();

	private CageStackTiffDiskLayout() {
	}

	@Override
	public List<ImageFileData> listImageDescriptors(String baseDirectory, Cages cages, Spots allSpots) {
		if (baseDirectory == null || baseDirectory.trim().isEmpty()) {
			throw new IllegalArgumentException("Base directory cannot be null or empty");
		}
		if (cages == null) {
			throw new IllegalArgumentException("Cages array cannot be null");
		}

		String fullDirectory = baseDirectory + File.separator;

		if (cages.cagesList.isEmpty()) {
			Logger.warn("No cages found for cage kymograph file list");
			return new ArrayList<>();
		}

		List<ImageFileData> fileList = new ArrayList<>(cages.cagesList.size());
		int idx = 0;
		for (Cage cage : cages.cagesList) {
			int cid = cage.prop.getCageID();
			String base = "kymocage_" + (cid >= 0 ? String.valueOf(cid) : "i" + idx);
			String pathTiff = fullDirectory + base + ".tiff";
			String pathTif = fullDirectory + base + ".tif";
			File fTiff = new File(pathTiff);
			File fTif = new File(pathTif);

			ImageFileData descriptor = new ImageFileData();
			if (fTiff.exists()) {
				descriptor.fileName = pathTiff;
				descriptor.exists = true;
			} else if (fTif.exists()) {
				descriptor.fileName = pathTif;
				descriptor.exists = true;
			} else {
				descriptor.fileName = pathTiff;
				descriptor.exists = false;
			}
			fileList.add(descriptor);
			idx++;
		}
		return fileList;
	}
}
