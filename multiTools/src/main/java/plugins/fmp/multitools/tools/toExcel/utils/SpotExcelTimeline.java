package plugins.fmp.multitools.tools.toExcel.utils;

import java.util.Arrays;
import java.util.Locale;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.sequence.ImageLoader;
import plugins.fmp.multitools.experiment.sequence.TimeManager;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.toExcel.config.ExcelExportConstants;

/**
 * Per-frame elapsed times for spot Excel. Prefer user nominal interval, then persisted bin CameraIntervalMs from
 * the bin descriptor; timestamps must agree (median consecutive Δ within ±25%) else spacing is uniform on that axis.
 * If neither is known, falls back to file-detected cam step, cameras, synthesize.
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

	private static int countBinsInWindow(long clipStartMs, long clipEndMs, long excelDeltaMs) {
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

		long nominalMs = nominalStepMsFromExperiment(exp);
		long persistedCamMs = exp.getPersistedBinCameraIntervalMs();
		long authoritativeStepMs = nominalMs > 0L ? nominalMs : (persistedCamMs > 0L ? persistedCamMs : 0L);
		long[] fromCam = tryBuildCamRelativeTimes(exp, nf);

		if (authoritativeStepMs > 0L) {
			if (fromCam != null && medianMatchesReference(fromCam, nf, authoritativeStepMs)) {
				return fromCam;
			}
			return uniformElapsedGrid(nf, authoritativeStepMs);
		}

		long detectedStep = exp.getCamImageBin_ms();
		if (detectedStep > 0L) {
			if (fromCam != null && medianMatchesReference(fromCam, nf, detectedStep)) {
				return fromCam;
			}
			return uniformElapsedGrid(nf, detectedStep);
		}

		if (fromCam != null) {
			return fromCam;
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

	private static boolean medianMatchesReference(long[] tRelative, int nf, long referenceStepMs) {
		if (nf < 2 || referenceStepMs <= 0L) {
			return true;
		}
		long med = medianConsecutiveDeltaMs(tRelative, nf);
		if (med <= 0L) {
			return false;
		}
		double ratio = med / (double) referenceStepMs;
		return ratio >= 0.75 && ratio <= 1.25;
	}

	private static long medianConsecutiveDeltaMs(long[] tRelative, int nf) {
		if (nf < 2) {
			return 0L;
		}
		long[] deltas = new long[nf - 1];
		for (int i = 0; i < nf - 1; i++) {
			deltas[i] = tRelative[i + 1] - tRelative[i];
		}
		Arrays.sort(deltas);
		int mid = deltas.length / 2;
		return (deltas.length % 2 == 1) ? deltas[mid] : (deltas[mid - 1] + deltas[mid]) / 2;
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
		long nominalMs = nominalStepMsFromExperiment(exp);
		if (nominalMs > 0L) {
			return uniformElapsedGrid(nf, nominalMs);
		}
		long persistedCamMs = exp.getPersistedBinCameraIntervalMs();
		if (persistedCamMs > 0L) {
			return uniformElapsedGrid(nf, persistedCamMs);
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
