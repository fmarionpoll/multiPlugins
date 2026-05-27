package plugins.fmp.multitools.service;

import java.util.Collections;
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

	/** Time window and column count for kymograph ↔ camera mapping (aligned with {@link CageSpotKymographBuilder}). */
	public static final class KymoPickWindow {
		public final long firstMs;
		public final long lastMs;
		public final long stepMs;
		public final int columnCount;

		KymoPickWindow(long firstMs, long lastMs, long stepMs, int columnCount) {
			this.firstMs = firstMs;
			this.lastMs = lastMs;
			this.stepMs = stepMs;
			this.columnCount = columnCount;
		}
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

	/**
	 * Camera sequence size used to clip spot ROIs when computing stacked bands — same convention as
	 * {@link CageSpotKymographBuilder} and {@link CageKymoAnalyzer}. Picking assumes the camera
	 * sequence is loaded (required for canvas-linked workflows); if size is unavailable, callers
	 * should skip mapping.
	 */
	public static int[] cameraReferenceSize(Experiment exp) {
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null) {
			return new int[] { 0, 0 };
		}
		icy.sequence.Sequence seq = exp.getSeqCamData().getSequence();
		return new int[] { seq.getSizeX(), seq.getSizeY() };
	}

	public static KymoPickWindow resolveKymoPickWindow(Experiment exp) {
		if (exp == null) {
			return null;
		}
		ensureCamTimeIntervals(exp);
		if (exp.getCamImages_ms() == null) {
			return null;
		}
		SequenceCamData seqCamData = exp.getSeqCamData();
		if (seqCamData == null || seqCamData.getImageLoader() == null) {
			return null;
		}

		long first_ms = exp.getKymoFirst_ms();
		long last_ms = exp.getKymoLast_ms();
		if (last_ms <= first_ms) {
			first_ms = 0;
			last_ms = seqCamData.getTimeManager().getBinLast_ms();
			if (last_ms <= first_ms && exp.getCamImageLast_ms() > exp.getCamImageFirst_ms()) {
				last_ms = exp.getCamImageLast_ms() - exp.getCamImageFirst_ms();
			}
			if (last_ms <= first_ms) {
				last_ms = first_ms + 1;
			}
		}
		long step_ms = exp.getKymoBin_ms();
		if (step_ms <= 0) {
			return null;
		}

		int nTotalFrames = seqCamData.getImageLoader().getNTotalFrames();
		int highIndex = (nTotalFrames > 0) ? (nTotalFrames - 1) : 0;
		long[] camImages_ms = exp.getCamImages_ms();
		if (camImages_ms != null && highIndex < camImages_ms.length) {
			last_ms = Math.min(last_ms, camImages_ms[highIndex]);
		}

		int columnCount = (int) Math.max(1, 1 + (int) Math.ceil((last_ms - first_ms) / (double) step_ms));
		return new KymoPickWindow(first_ms, last_ms, step_ms, columnCount);
	}

	/**
	 * For cage kymograph sequences: uses the time grid stored in {@link CageKymographStripLayoutCsv}
	 * when it matches the opened image width (same grid as at build time, including nearest-frame
	 * sampling vs. camera timestamps). Otherwise falls back to {@link #resolveKymoPickWindow}.
	 */
	public static KymoPickWindow resolveKymoPickWindowForCageKymograph(Experiment exp, int kymographImageWidth) {
		if (exp == null || kymographImageWidth <= 0) {
			return resolveKymoPickWindow(exp);
		}
		CageKymographStripLayoutCsv.PersistedKymoGrid g = CageKymographStripLayoutCsv
				.readPersistedKymoGridOrNull(exp.getKymosBinFullDirectory(), kymographImageWidth);
		if (g != null) {
			return new KymoPickWindow(g.firstMs, g.lastMs, g.stepMs, g.columnCount);
		}
		return resolveKymoPickWindow(exp);
	}

	/**
	 * Vertical bands for the stacked cage kymograph: prefers {@link CageKymographStripLayoutCsv} in
	 * the experiment's kymograph bin when meta matches, otherwise {@link CageKymographSpotBands#layout}.
	 */
	public static List<CageKymographSpotBands> stackedSpotBands(Experiment exp, Cage cage, Spots spots, int refSizex,
			int refSizey, int kymographColumnCount) {
		if (cage == null || spots == null || kymographColumnCount <= 0) {
			return Collections.emptyList();
		}
		String binDir = exp != null ? exp.getKymosBinFullDirectory() : null;
		String fileBase = exp != null && exp.getCages() != null ? KymocageCageResolver.kymocageFileBaseForCage(cage,
				exp.getCages()) : null;
		if (binDir != null && fileBase != null) {
			List<CageKymographSpotBands> fromFile = CageKymographStripLayoutCsv.readBandsOrNull(binDir, fileBase, cage,
					spots, refSizex, refSizey, kymographColumnCount);
			if (fromFile != null && !fromFile.isEmpty()) {
				return fromFile;
			}
		}
		if (refSizex <= 0 || refSizey <= 0) {
			return Collections.emptyList();
		}
		return CageKymographSpotBands.layout(cage, spots, refSizex, refSizey);
	}

	/**
	 * Sum of stacked spot band heights in kymograph row coordinates (excludes bottom padding on a
	 * max-height sequence canvas). Uses persisted strip layout from the bin when available
	 * ({@link CageKymographStripLayoutCsv}); otherwise clipped spot ROI heights on the camera image.
	 */
	public static int stackedContentHeightPixels(Experiment exp, Cage cage, Spots spots, int refSizex, int refSizey,
			int kymographColumnCount) {
		List<CageKymographSpotBands> bands = stackedSpotBands(exp, cage, spots, refSizex, refSizey, kymographColumnCount);
		if (bands.isEmpty()) {
			return 0;
		}
		return bands.get(bands.size() - 1).y1Exclusive;
	}

	public static Spot spotAtStackedY(Experiment exp, Cage cage, Spots spots, int refSizex, int refSizey,
			int kymographColumnCount, int stackedY) {
		List<CageKymographSpotBands> bands = stackedSpotBands(exp, cage, spots, refSizex, refSizey, kymographColumnCount);
		int y = stackedY;
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
	public static Integer cameraFrameForKymographColumn(Experiment exp, int column, KymoPickWindow win) {
		if (exp == null || win == null) {
			return null;
		}
		if (column < 0 || column >= win.columnCount) {
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

		long ii_ms = win.firstMs + (long) column * win.stepMs;
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
