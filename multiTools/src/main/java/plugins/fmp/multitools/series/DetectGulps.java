package plugins.fmp.multitools.series;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.series.options.BuildSeriesOptions.GulpDetectionMethod;
import plugins.fmp.multitools.service.GulpDetector;
import plugins.fmp.multitools.service.KymographService;
import plugins.fmp.multitools.tools.Logger;

public class DetectGulps extends BuildSeries {

	void analyzeExperiment(Experiment exp) {
		boolean classic = options.gulpDetectionMethod == GulpDetectionMethod.XDIFFN_REF;
		boolean wantKymo = classic || options.buildDerivative;

		if (!loadExperimentDataToDetectGulps(exp, wantKymo && classic)) {
			closeKymos(exp);
			return;
		}
		if (!classic && wantKymo) {
			exp.loadKymographs();
		}
		syncNFliesFromCages(exp);
		exp.ensureFrameTimeScale();

		if (classic) {
			if (!exp.isNativeFrameIndexedKymo()) {
				int nFrames = exp.getAnalysisFrameCount();
				int kymoWidth = exp.getKymoColumnCount();
				String expName = experimentLabel(exp);
				Logger.warn("DetectGulps: gulp detection skipped (non-native / downsampled kymo) for experiment="
						+ expName + " (kymo width=" + kymoWidth + ", analysis frames=" + nFrames
						+ "). Use downsample x1 for gulp detection.");
				closeKymos(exp);
				return;
			}
			buildFilteredImage(exp);
		} else if (options.buildDerivative && exp.getSeqKymos() != null && exp.getSeqKymos().getSequence() != null
				&& exp.isNativeFrameIndexedKymo()) {
			buildFilteredImage(exp);
		}

		new GulpDetector().detectGulps(exp, options);
		closeKymos(exp);
	}

	private static void closeKymos(Experiment exp) {
		if (exp != null && exp.getSeqKymos() != null) {
			exp.getSeqKymos().closeSequence();
		}
	}

	private static void syncNFliesFromCages(Experiment exp) {
		if (exp == null) {
			return;
		}
		exp.load_cages_description_and_measures();
		if (exp.getCages() != null && exp.getCapillaries() != null) {
			exp.getCages().transferNFliesFromCagesToCapillaries(exp.getCapillaries().getList());
		}
	}

	private static String experimentLabel(Experiment exp) {
		if (exp == null) {
			return "(null)";
		}
		String dir = exp.getExperimentDirectory();
		if (dir != null && !dir.isEmpty()) {
			return dir;
		}
		String results = exp.getResultsDirectory();
		return results != null ? results : exp.toString();
	}

	private boolean loadExperimentDataToDetectGulps(Experiment exp, boolean requireKymos) {
		exp.xmlLoad_MCExperiment();

		boolean flag = exp.loadMCCapillaries_Only();
		flag &= exp.load_capillaries_description_and_measures();
		if (requireKymos) {
			flag &= exp.loadKymographs();
		}
		return flag;
	}

	private void buildFilteredImage(Experiment exp) {
		if (exp.getSeqKymos() == null)
			return;
		new KymographService().buildFiltered(exp, 0, BuildSeriesOptions.Z_INDEX_FILTERED_FOR_GULPS,
				options.transformForGulps, options.spanDiffForGulps);
	}

}
