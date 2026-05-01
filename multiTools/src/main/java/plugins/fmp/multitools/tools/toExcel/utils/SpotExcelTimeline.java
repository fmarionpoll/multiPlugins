package plugins.fmp.multitools.tools.toExcel.utils;

import java.util.Locale;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.sequence.ImageLoader;
import plugins.fmp.multitools.experiment.sequence.TimeManager;
import plugins.fmp.multitools.experiment.spot.SpotMeasure;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.toExcel.config.ExcelExportConstants;

/**
 * Spot Excel export uses
 * {@link plugins.fmp.multitools.experiment.spot.SpotMeasure#getValuesAsSubsampledList(long, long)}
 * with native spacing from {@link #resolveNativeSeriesBinMs(Experiment)} and
 * output spacing {@link ResultsOptions#buildExcelStepMs}.
 */
public final class SpotExcelTimeline {

	private SpotExcelTimeline() {
	}

	/**
	 * Native spacing between stored samples (camera / sequence bin). Falls back to
	 * average spacing from time span / frame count when TimeManager bins are unset.
	 */
	public static long resolveNativeSeriesBinMs(Experiment exp) {
		TimeManager tm = exp.getSeqCamData().getTimeManager();
		ImageLoader il = exp.getSeqCamData().getImageLoader();
		long bd = tm.getBinImage_ms() > 0 ? tm.getBinImage_ms() : tm.getBinDurationMs();
		if (bd > 0) {
			return bd;
		}
		int nf = il.getNTotalFrames();
		if (nf > 1) {
			long span = tm.getBinLast_ms() - tm.getBinFirst_ms();
			if (span > 0) {
				return span / (nf - 1);
			}
		}
		return 0;
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
		TimeManager tm = exp.getSeqCamData().getTimeManager();
		ImageLoader il = exp.getSeqCamData().getImageLoader();
		long step = opt.buildExcelStepMs > 0 ? opt.buildExcelStepMs : 1;
		int nFrames = Math.max(1, il.getNTotalFrames());

		long nativeStep = resolveNativeSeriesBinMs(exp);
		if (nativeStep > 0 && nFrames > 0) {
			return Math.max(1, SpotMeasure.countSubsampledOutputPoints(nativeStep, step, nFrames));
		}

		long durationMs = tm.getBinLast_ms() - tm.getBinFirst_ms();
		int nOutputFrames = (int) (durationMs / step + 1);

		if (nOutputFrames <= 1) {
			long binDuration = tm.getBinDurationMs();
			long binLastMs = tm.getBinFirst_ms() + (long) il.getNTotalFrames() * binDuration;
			tm.setBinLast_ms(binLastMs);
			nOutputFrames = (int) ((binLastMs - tm.getBinFirst_ms()) / step + 1);
			if (nOutputFrames <= 1) {
				nOutputFrames = Math.max(1, il.getNTotalFrames());
			}
		}

		return Math.max(1, nOutputFrames);
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
}
