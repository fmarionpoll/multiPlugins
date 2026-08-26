package plugins.fmp.multitools.series;

import java.awt.Rectangle;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.series.options.LevelDetectV2Options;
import plugins.fmp.multitools.service.LevelDetectorFromKymoV2;
import plugins.fmp.multitools.tools.Logger;

/**
 * Series worker for kymograph top-level detection v2. Does not overwrite legacy
 * pass1/pass2 recipe defaults on capillaries.
 */
public class DetectLevelsV2 extends BuildSeries {

	public LevelDetectV2Options v2Options = new LevelDetectV2Options();

	@Override
	void analyzeExperiment(Experiment exp) {
		if (loadExperimentData(exp)) {
			refreshKymoOptionsForExperiment(exp);
			exp.getSeqKymos().displayViewerAtRectangle(options.parent0Rect);
			new LevelDetectorFromKymoV2().detectLevels(exp, options, v2Options);
		}
		exp.closeSequences();
	}

	private void refreshKymoOptionsForExperiment(Experiment exp) {
		if (options == null || exp.getSeqKymos() == null || exp.getSeqKymos().getSequence() == null)
			return;
		int sizeT = exp.getSeqKymos().getSequence().getSizeT();
		if (!options.detectSelectedKymo) {
			options.kymoFirst = 0;
			options.kymoLast = Math.max(0, sizeT - 1);
		} else {
			if (options.kymoFirst < 0 || options.kymoFirst >= sizeT)
				options.kymoFirst = 0;
			options.kymoLast = options.kymoFirst;
		}
		if (!options.analyzePartOnly) {
			Rectangle bounds = exp.getSeqKymos().getSequence().getBounds2D();
			int w = Math.max(0, bounds.width);
			int h = Math.max(0, bounds.height);
			options.searchArea = new Rectangle(0, 0, w, h);
			Logger.debug("DetectLevelsV2: refreshed searchArea " + w + "x" + h + " for " + exp.getResultsDirectory());
			exp.getCapillaries().clearKymoMeasuresOnly(options.kymoFirst, options.kymoLast, options.detectL,
					options.detectR, true, false);
		}
	}

	private boolean loadExperimentData(Experiment exp) {
		exp.xmlLoad_MCExperiment();
		exp.load_capillaries_description_and_measures();
		return exp.loadKymographs();
	}
}