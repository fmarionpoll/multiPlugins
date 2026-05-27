package plugins.fmp.multitools.tools.toExcel.utils;

import java.util.Arrays;
import java.util.Locale;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.sequence.ImageLoader;
import plugins.fmp.multitools.experiment.sequence.TimeManager;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.toExcel.config.ExcelExportConstants;

/**
 * Per-frame elapsed times for spot Excel export and resampling.
 * <p>
 * When camera timestamps are available and weakly increasing, they define the acquisition span (one sample per
 * frame). Otherwise a uniform grid is synthesized using persisted camera interval, detected cam step, then
 * nominal interval. Nominal interval is not used to override conflicting camera timing.
 */
public final class SpotExcelTimeline {

	private SpotExcelTimeline() {
	}

	public static final class SpotExcelGrid {
		private final long[] frameElapsedMsRelative;
		private final long excelDeltaMs;
		private final long clipStartMs;
		private final int nBins;

		public SpotExcelGrid(long[] frameElapsedMsRelative, long excelDeltaMs, long clipStartMs, int nBins) {
			this.frameElapsedMsRelative = frameElapsedMsRelative;
			this.excelDeltaMs = excelDeltaMs;
			this.clipStartMs = clipStartMs;
			this.nBins = Math.max(1, nBins);
		}

		public long[] getFrameElapsedMsRelative() {
			return frameElapsedMsRelative;
		}

		public long getExcelDeltaMs() {
			return excelDeltaMs;
		}

		public long getClipStartMs() {
			return clipStartMs;
		}

		public int getNBins() {
			return nBins;
		}

		public long getHeaderElapsedMs(int k) {
			return clipStartMs + (long) k * excelDeltaMs;
		}
	}

	/**
	 * Relative elapsed ms from the first camera frame to the last, same authoritative spacing as
	 * {@link #buildForSpotExport}. Zero if there is no usable stack ({@code seqCamData}, or fewer than two frames).
	 */
	public static long relativeCameraAcquisitionSpanMs(Experiment exp) {
		if (exp == null || exp.getSeqCamData() == null) {
			return 0L;
		}
		ImageLoader il = exp.getSeqCamData().getImageLoader();
		if (il == null) {
			return 0L;
		}
		int nf = il.getNTotalFrames();
		if (nf <= 1) {
			return 0L;
		}
		long[] t = resolveFrameElapsedMsArray(exp);
		return t.length > 0 ? t[t.length - 1] : 0L;
	}

	public static SpotExcelGrid buildForSpotExport(Experiment exp, ResultsOptions opt) {
		if (opt != null && opt.resultType != null && opt.resultType.isPersistedKymographSpotMeasure()) {
			return buildForKymoSpotExport(exp, opt);
		}
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getImageLoader() == null) {
			return new SpotExcelGrid(new long[] { 0L }, 1L, 0L, 1);
		}
		ImageLoader il = exp.getSeqCamData().getImageLoader();
		int nf = il.getNTotalFrames();

		long[] t = nf > 0 ? resolveFrameElapsedMsArray(exp) : new long[0];
		long tLast = t.length > 0 ? t[t.length - 1] : 0L;

		long delta = opt != null && opt.buildExcelStepMs > 0 ? opt.buildExcelStepMs : 1L;

		long lo = 0L;
		long hi = tLast;

		if (opt != null && opt.fixedIntervals) {
			lo = clampMs(opt.startAll_Ms, 0L, tLast);
			hi = clampMs(opt.endAll_Ms, lo, Math.max(lo, tLast));
		}

		int nBins = countBinsInWindow(lo, hi, delta);

		return new SpotExcelGrid(t.length > 0 ? t : new long[] { 0L }, delta, lo, nBins);
	}

	private static long clampMs(long v, long lo, long hi) {
		if (v < lo) {
			return lo;
		}
		if (v > hi) {
			return hi;
		}
		return v;
	}

	/**
	 * Bins covering {@code [clipStartMs, clipEndMs]} at {@code excelDeltaMs} spacing (inclusive endpoints).
	 * E.g. 365 frames at 20 s → span 7_280_000 ms; export step 60 s → 122 bins, not 365.
	 */
	static int countBinsInWindow(long clipStartMs, long clipEndMs, long excelDeltaMs) {
		if (excelDeltaMs <= 0L) {
			return 1;
		}
		if (clipEndMs < clipStartMs) {
			return 1;
		}
		long span = clipEndMs - clipStartMs;
		long npts = span / excelDeltaMs + 1L;
		return (int) Math.min(npts, (long) Integer.MAX_VALUE);
	}

	public static String formatElapsedColumnHeader(long elapsedMs, int unitMs) {
		int u = unitMs > 0 ? unitMs : 1;
		double units = elapsedMs / (double) u;
		if (Double.isFinite(units) && Math.abs(units - Math.rint(units)) < 1e-9) {
			return ExcelExportConstants.TIME_COLUMN_PREFIX + (long) Math.rint(units);
		}
		String s = String.format(Locale.US, "%.12f", units).replaceAll("0*$", "").replaceAll("\\.$", "");
		return ExcelExportConstants.TIME_COLUMN_PREFIX + s;
	}

	private static SpotExcelGrid buildForKymoSpotExport(Experiment exp, ResultsOptions opt) {
		long delta = opt != null && opt.buildExcelStepMs > 0 ? opt.buildExcelStepMs
				: (exp != null ? exp.getKymoBin_ms() : 0L);
		if (delta <= 0L) {
			delta = 60_000L;
		}
		int nativeBins = 0;
		if (exp != null && exp.getSpots() != null) {
			EnumResults rt = opt != null ? opt.resultType : EnumResults.KYMO_GREEN_HEIGHT_RATIO;
			for (Spot spot : exp.getSpots().getSpotList()) {
				if (spot == null) {
					continue;
				}
				EnumResults measureType = rt != null ? rt : EnumResults.KYMO_GREEN_HEIGHT_RATIO;
				if (spot.getMeasurements(measureType) != null) {
					nativeBins = Math.max(nativeBins, spot.getMeasurements(measureType).getCount());
				}
			}
		}
		if (nativeBins < 1) {
			nativeBins = 1;
		}
		long[] t = new long[nativeBins];
		for (int j = 0; j < nativeBins; j++) {
			t[j] = (long) j * delta;
		}
		long tLast = t[nativeBins - 1];
		long lo = 0L;
		long hi = tLast;
		if (opt != null && opt.fixedIntervals) {
			lo = clampMs(opt.startAll_Ms, 0L, tLast);
			hi = clampMs(opt.endAll_Ms, lo, Math.max(lo, tLast));
		}
		int nBins = countBinsInWindow(lo, hi, delta);
		return new SpotExcelGrid(t, delta, lo, nBins);
	}

	public static int computeSpotExcelBinCount(Experiment exp, ResultsOptions opt) {
		return buildForSpotExport(exp, opt).getNBins();
	}

	public static int maxSpotExcelBinCountAcrossExportRange(JComboBoxExperimentLazy expList, ResultsOptions opt) {
		if (expList == null || opt == null) {
			return 1;
		}
		int n = expList.getItemCount();
		if (n <= 0) {
			return 1;
		}
		int[] bounds = expList.getExportExperimentIndexBounds(opt);
		int startIdx = bounds[0];
		int endIdx = bounds[1];
		if (endIdx < startIdx) {
			return 1;
		}
		int max = 1;
		for (int i = startIdx; i <= endIdx && i < n; i++) {
			Experiment exp = expList.getItemAt(i);
			max = Math.max(max, computeSpotExcelBinCount(exp, opt));
		}
		return max;
	}

	static long[] resolveFrameElapsedMsArray(Experiment exp) {
		exp.loadFileIntervalsFromSeqCamData();

		ImageLoader il = exp.getSeqCamData().getImageLoader();
		int nf = il.getNTotalFrames();
		if (nf < 1) {
			return new long[0];
		}

		long[] fromCam = tryBuildCamRelativeTimes(exp, nf);
		if (fromCam != null) {
			return fromCam;
		}

		long persistedCamMs = exp.getPersistedBinCameraIntervalMs();
		if (persistedCamMs > 0L) {
			return uniformElapsedGrid(nf, persistedCamMs);
		}

		long detectedStep = exp.getCamImageBin_ms();
		if (detectedStep > 0L) {
			return uniformElapsedGrid(nf, detectedStep);
		}

		long nominalMs = nominalStepMsFromExperiment(exp);
		if (nominalMs > 0L) {
			return uniformElapsedGrid(nf, nominalMs);
		}

		return synthesizeUniformFrameTimes(exp, nf);
	}

	private static long nominalStepMsFromExperiment(Experiment exp) {
		int sec = exp.getNominalIntervalSec();
		return sec > 0 ? (long) sec * 1000L : 0L;
	}

	private static long[] tryBuildCamRelativeTimes(Experiment exp, int nf) {
		TimeManager tm = exp.getSeqCamData().getTimeManager();
		long[] cam = tm.getCamImagesTime_Ms();
		if (cam == null || cam.length != nf) {
			exp.getSeqCamData().build_MsTimesArray_From_FileNamesList();
			cam = tm.getCamImagesTime_Ms();
		}
		if (cam == null || cam.length != nf || !isWeaklyIncreasing(cam)) {
			return null;
		}
		long[] t = Arrays.copyOf(cam, nf);
		long origin = t[0];
		for (int i = 0; i < nf; i++) {
			t[i] -= origin;
		}
		return t;
	}

	private static long[] uniformElapsedGrid(int nf, long stepMs) {
		long[] t = new long[nf];
		for (int i = 0; i < nf; i++) {
			t[i] = (long) i * stepMs;
		}
		return t;
	}

	private static boolean isWeaklyIncreasing(long[] cam) {
		for (int i = 1; i < cam.length; i++) {
			if (cam[i] < cam[i - 1]) {
				return false;
			}
		}
		return true;
	}

	private static long[] synthesizeUniformFrameTimes(Experiment exp, int nf) {
		long persistedCamMs = exp.getPersistedBinCameraIntervalMs();
		if (persistedCamMs > 0L) {
			return uniformElapsedGrid(nf, persistedCamMs);
		}
		long nominalMs = nominalStepMsFromExperiment(exp);
		if (nominalMs > 0L) {
			return uniformElapsedGrid(nf, nominalMs);
		}

		TimeManager tm = exp.getSeqCamData().getTimeManager();
		long detected = exp.getCamImageBin_ms();
		long step;
		if (detected > 0L) {
			step = detected;
		} else {
			long span = tm.getBinLast_ms() - tm.getBinFirst_ms();
			if (nf > 1 && span > 0L) {
				step = span / (nf - 1);
				if (step <= 0L) {
					step = 1L;
				}
			} else {
				long bd = tm.getBinImage_ms() > 0 ? tm.getBinImage_ms() : tm.getBinDurationMs();
				step = bd > 0L ? bd : 1L;
			}
		}
		return uniformElapsedGrid(nf, step);
	}
}
