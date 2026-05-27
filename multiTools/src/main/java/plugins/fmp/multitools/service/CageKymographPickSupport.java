package plugins.fmp.multitools.service;

import java.util.List;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spots.Spots;

/**
 * Maps a click on a stacked cage kymograph image (x = kymograph column, y = stacked spot band) to a
 * camera frame index and {@link Spot}, matching {@link CageSpotKymographBuilder} time mapping.
 */
public final class CageKymographPickSupport {

	private CageKymographPickSupport() {
	}

	public static boolean isCageKymographFrame(Experiment exp, int kymographFrameT) {
		if (exp == null || exp.getSeqKymos() == null) {
			return false;
		}
		String path = exp.getSeqKymos().getFileNameFromImageList(kymographFrameT);
		return KymocageCageResolver.resolveCageFromKymographPath(path, exp.getCages()) != null;
	}

	public static Cage cageForKymographFrame(Experiment exp, int kymographFrameT) {
		if (exp == null || exp.getSeqKymos() == null) {
			return null;
		}
		String path = exp.getSeqKymos().getFileNameFromImageList(kymographFrameT);
		return KymocageCageResolver.resolveCageFromKymographPath(path, exp.getCages());
	}

	public static Spot spotAtImageY(Cage cage, Spots spots, int imageWidth, int imageHeight, double imageY) {
		if (cage == null || spots == null || imageWidth <= 0 || imageHeight <= 0) {
			return null;
		}
		List<CageKymographSpotBands> bands = CageKymographSpotBands.layout(cage, spots, imageWidth, imageHeight);
		int y = (int) Math.floor(imageY);
		if (y < 0) {
			y = 0;
		}
		if (y >= imageHeight) {
			y = imageHeight - 1;
		}
		for (CageKymographSpotBands b : bands) {
			if (y >= b.y0 && y < b.y1Exclusive) {
				return b.spot;
			}
		}
		return null;
	}

	/**
	 * Camera frame index (0..N-1) for kymograph column {@code column}, or null if intervals are missing.
	 */
	public static Integer cameraFrameForKymographColumn(Experiment exp, int column) {
		if (exp == null) {
			return null;
		}
		ensureCamTimeIntervals(exp);
		if (exp.getCamImages_ms() == null) {
			return null;
		}
		SequenceCamData seqCam = exp.getSeqCamData();
		if (seqCam == null || seqCam.getImageLoader() == null) {
			return null;
		}
		int nTotalFrames = seqCam.getImageLoader().getNTotalFrames();
		int lowIndex = 0;
		int highIndex = (nTotalFrames > 0) ? (nTotalFrames - 1) : 0;
		if (highIndex < lowIndex) {
			highIndex = lowIndex;
		}

		long first_ms = exp.getKymoFirst_ms();
		long last_ms = exp.getKymoLast_ms();
		long step_ms = exp.getKymoBin_ms();
		if (step_ms <= 0) {
			return null;
		}

		if (last_ms <= first_ms) {
			last_ms = first_ms + 1;
		}
		long[] camImages_ms = exp.getCamImages_ms();
		if (camImages_ms != null && highIndex < camImages_ms.length) {
			last_ms = Math.min(last_ms, camImages_ms[highIndex]);
		}

		long ii_ms = first_ms + (long) column * step_ms;
		int idx = exp.findNearestIntervalWithBinarySearch(ii_ms, lowIndex, highIndex);
		return idx >= 0 ? idx : null;
	}

	private static void ensureCamTimeIntervals(Experiment exp) {
		if (exp.getCamImages_ms() != null) {
			return;
		}
		SequenceCamData scd = exp.getSeqCamData();
		if (scd == null) {
			return;
		}
		long camReferenceMs = scd.getFirstValidFrameEpochMs();
		if (camReferenceMs < 0) {
			camReferenceMs = exp.getCamImageFirst_ms();
		}
		if (camReferenceMs < 0) {
			camReferenceMs = 0;
		}
		exp.build_MsTimeIntervalsArray_From_SeqCamData_FileNamesList(camReferenceMs);
	}

	public static int clampColumn(int column, int imageWidth) {
		if (imageWidth <= 0) {
			return 0;
		}
		if (column < 0) {
			return 0;
		}
		if (column >= imageWidth) {
			return imageWidth - 1;
		}
		return column;
	}
}
