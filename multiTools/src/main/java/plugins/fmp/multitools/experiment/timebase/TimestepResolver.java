package plugins.fmp.multitools.experiment.timebase;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.GenerationMode;
import plugins.fmp.multitools.experiment.sequence.ImageLoader;
import plugins.fmp.multitools.tools.Logger;

/**
 * Resolves a timestep in milliseconds from experiment metadata, optional user
 * Excel step, and {@link TimestepResolutionContext}.
 */
public final class TimestepResolver {

	public static final long FALLBACK_MS = 60_000L;

	private TimestepResolver() {
	}

	public static TimestepResolutionResult resolve(Experiment exp, int userExcelStepMs,
			TimestepResolutionContext ctx) {
		if (ctx == null)
			ctx = TimestepResolutionContext.FOR_CHART_NATIVE_MEASURE;
		if (exp == null) {
			Logger.warn("TimestepResolver: experiment null, using " + FALLBACK_MS + " ms");
			return new TimestepResolutionResult(FALLBACK_MS, MeasureTimebase.UNKNOWN, false, true);
		}

		switch (ctx) {
		case FOR_SERIES_BOUNDS_BUILD:
			if (userExcelStepMs > 0) {
				return new TimestepResolutionResult(userExcelStepMs, MeasureTimebase.UNKNOWN, false, false);
			}
			return resolveMeasureNative(exp);
		case FOR_CHART_NATIVE_MEASURE:
			return resolveMeasureNative(exp);
		case FOR_EXCEL_EXPORT:
			if (userExcelStepMs > 0) {
				return new TimestepResolutionResult(userExcelStepMs, MeasureTimebase.EXPORT_RESAMPLE_STEP,
						shouldPreferPhysicalKymoCount(exp, userExcelStepMs), false);
			}
			return resolveMeasureNative(exp);
		default:
			return resolveMeasureNative(exp);
		}
	}

	private static TimestepResolutionResult resolveMeasureNative(Experiment exp) {
		GenerationMode mode = exp.getGenerationMode();
		long kymo = exp.getKymoBin_ms();

		if (mode == GenerationMode.KYMOGRAPH && kymo > 0) {
			return new TimestepResolutionResult(kymo, MeasureTimebase.KYMO_COLUMN_STEP,
					shouldPreferPhysicalKymoCount(exp, boundedInt(kymo)), false);
		}
		if (mode == GenerationMode.DIRECT_FROM_STACK) {
			long cam = resolveCameraStepMs(exp);
			if (cam > 0)
				return new TimestepResolutionResult(cam, MeasureTimebase.CAMERA_FRAME_STEP, false, false);
		}
		if (mode == GenerationMode.UNKNOWN) {
			if (kymo > 0) {
				return new TimestepResolutionResult(kymo, MeasureTimebase.KYMO_COLUMN_STEP,
						shouldPreferPhysicalKymoCount(exp, boundedInt(kymo)), false);
			}
			long cam = resolveCameraStepMs(exp);
			if (cam > 0)
				return new TimestepResolutionResult(cam, MeasureTimebase.CAMERA_FRAME_STEP, false, false);
		} else if (mode == GenerationMode.KYMOGRAPH) {
			long cam = resolveCameraStepMs(exp);
			if (cam > 0)
				return new TimestepResolutionResult(cam, MeasureTimebase.CAMERA_FRAME_STEP, false, false);
		}

		if (kymo > 0) {
			return new TimestepResolutionResult(kymo, MeasureTimebase.KYMO_COLUMN_STEP,
					shouldPreferPhysicalKymoCount(exp, boundedInt(kymo)), false);
		}
		long cam = resolveCameraStepMs(exp);
		if (cam > 0)
			return new TimestepResolutionResult(cam, MeasureTimebase.CAMERA_FRAME_STEP, false, false);

		Logger.warn("TimestepResolver: no interval for experiment " + safeDir(exp) + " — using " + FALLBACK_MS
				+ " ms fallback");
		return new TimestepResolutionResult(FALLBACK_MS, MeasureTimebase.UNKNOWN, false, true);
	}

	private static int boundedInt(long kymo) {
		return (int) Math.min(kymo, Integer.MAX_VALUE);
	}

	private static String safeDir(Experiment exp) {
		try {
			return exp.getResultsDirectory();
		} catch (Exception e) {
			return "(unknown)";
		}
	}

	private static long resolveCameraStepMs(Experiment exp) {
		long p = exp.getPersistedBinCameraIntervalMs();
		if (p > 0)
			return p;
		long cam = exp.getCamImageBin_ms();
		if (cam > 0)
			return cam;
		if (exp.getSeqCamData() != null && exp.getSeqCamData().getTimeManager() != null) {
			long b = exp.getSeqCamData().getTimeManager().getBinImage_ms();
			if (b > 0)
				return b;
		}
		return -1L;
	}

	static boolean shouldPreferPhysicalKymoCount(Experiment exp, int resolvedStepMs) {
		if (exp == null || resolvedStepMs <= 0)
			return false;
		long kymoBin = exp.getKymoBin_ms();
		if (kymoBin <= 0 || resolvedStepMs != kymoBin)
			return false;
		if (exp.getSeqKymos() == null)
			return false;
		ImageLoader il = exp.getSeqKymos().getImageLoader();
		return il != null && il.getNTotalFrames() > 0;
	}
}
