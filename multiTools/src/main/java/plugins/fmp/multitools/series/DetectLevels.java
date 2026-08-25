package plugins.fmp.multitools.series;

import java.awt.Rectangle;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.service.LevelDetectorFromCam;
import plugins.fmp.multitools.service.LevelDetectorFromKymo;
import plugins.fmp.multitools.tools.Logger;

public class DetectLevels extends BuildSeries {
	void analyzeExperiment(Experiment exp) {
		if (options.sourceCamDirect) {
			exp.xmlLoad_MCExperiment();
			exp.load_capillaries_description_and_measures();
			// Direct-from-cam detection now writes standard TOPLEVEL/BOTTOMLEVEL measures.
			// Clear existing kymograph-based measures so results are fully replaced.
			exp.getCapillaries().clearKymoMeasuresOnly(-1, -1, options.detectL, options.detectR, options.detectTop,
					options.detectBottom);
			exp.getSeqCamData().loadImages();
			exp.getFileIntervalsFromSeqCamData();
			exp.build_MsTimeIntervalsArray_From_SeqCamData_FileNamesList(exp.getSeqCamData().getFirstImageMs());
			getTimeLimitsOfSequence(exp);
			new LevelDetectorFromCam().detectLevels(exp, options);

		} else if (loadExperimentDataToDetectLevels(exp)) {
			refreshKymoOptionsForExperiment(exp);
			exp.getSeqKymos().displayViewerAtRectangle(options.parent0Rect);
			new LevelDetectorFromKymo().detectLevels(exp, options);
		}
		exp.closeSequences();
	}

	/**
	 * Batch mode reuses one {@code BuildSeriesOptions} built from the first
	 * experiment. Refresh kymo T-range and full-frame search area for this
	 * experiment so bottom search starts at this kymo's actual height.
	 */
	private void refreshKymoOptionsForExperiment(Experiment exp) {
		if (options == null || exp.getSeqKymos() == null || exp.getSeqKymos().getSequence() == null) {
			return;
		}
		int sizeT = exp.getSeqKymos().getSequence().getSizeT();
		if (!options.detectSelectedKymo) {
			options.kymoFirst = 0;
			options.kymoLast = Math.max(0, sizeT - 1);
		} else {
			if (options.kymoFirst < 0 || options.kymoFirst >= sizeT) {
				options.kymoFirst = 0;
			}
			options.kymoLast = options.kymoFirst;
		}
		if (!options.analyzePartOnly) {
			Rectangle bounds = exp.getSeqKymos().getSequence().getBounds2D();
			int w = Math.max(0, bounds.width);
			int h = Math.max(0, bounds.height);
			options.searchArea = new Rectangle(0, 0, w, h);
			Logger.debug("DetectLevels: refreshed searchArea " + w + "x" + h + " for " + exp.getResultsDirectory());
			exp.getCapillaries().clearKymoMeasuresOnly(options.kymoFirst, options.kymoLast, options.detectL,
					options.detectR, options.detectTop, options.detectBottom);
		}
	}

	private boolean loadExperimentDataToDetectLevels(Experiment exp) {
		exp.xmlLoad_MCExperiment();
		exp.load_capillaries_description_and_measures();
		return exp.loadKymographs();
	}
}
