package plugins.fmp.multitools.series;

import java.nio.file.Paths;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.service.CageSpotKymographBuilder;
import plugins.fmp.multitools.service.KymographBuilder;
import plugins.fmp.multitools.tools.Logger;

/**
 * Background worker for multiSPOTS96 cage vertical-line kymographs (experimental).
 */
public class BuildKymosFromCageSpots extends BuildSeries {

	@Override
	void analyzeExperiment(Experiment exp) {
		Logger.info("BuildKymosFromCageSpots: start cage kymographs for " + exp.getResultsDirectory());
		String kymoDir = exp.getKymosBinFullDirectory();
		if (options != null) {
			options.kymoPreflightDetectedLockedFiles = false;
		}
		if (kymoDir != null && options != null) {
			KymographBuilder.LockProbeReport lockProbe = KymographBuilder.probeFileLocks(Paths.get(kymoDir), (name) -> {
				if (name == null) {
					return false;
				}
				String n = name.toLowerCase();
				return n.startsWith("kymocage_") && (n.endsWith(".tif") || n.endsWith(".tiff"));
			});
			options.kymoPreflightDetectedLockedFiles = lockProbe.locked > 0;
			if (lockProbe.locked > 0) {
				Logger.warn("BuildKymosFromCageSpots: kymocage TIFF(s) appear locked in " + lockProbe.directory
						+ " (locked=" + lockProbe.locked + "/" + lockProbe.total + "). Build will try flip-flop if needed.");
				for (String s : lockProbe.lockedFiles) {
					Logger.warn("BuildKymosFromCageSpots: locked: " + s);
				}
			}
		}

		if (!loadExperimentDataToBuildCageKymos(exp)) {
			Logger.warn("BuildKymosFromCageSpots: could not load camera / cages / spots");
			return;
		}

		getTimeLimitsOfSequence(exp);

		CageSpotKymographBuilder builder = new CageSpotKymographBuilder();
		if (builder.buildCageSpotKymographs(exp, options)) {
			exp.saveExperimentDescriptors();
		}
	}

	private boolean loadExperimentDataToBuildCageKymos(Experiment exp) {
		if (!exp.loadCamDataSpots()) {
			return false;
		}
		SequenceCamData seqData = exp.getSeqCamData();
		if (seqData == null) {
			return false;
		}
		seqData.loadImages();
		exp.getFileIntervalsFromSeqCamData();
		long firstValidEpochMs = seqData.getFirstValidFrameEpochMs();
		if (firstValidEpochMs < 0) {
			firstValidEpochMs = exp.getCamImageFirst_ms();
		}
		exp.build_MsTimeIntervalsArray_From_SeqCamData_FileNamesList(firstValidEpochMs);
		exp.ensureResultsDirectoryFromImagesFolder();
		return true;
	}

	protected void getTimeLimitsOfSequence(Experiment exp) {
		exp.getFileIntervalsFromSeqCamData();
		exp.setKymoBin_ms(options.t_Ms_BinDuration);
		if (options.isFrameFixed) {
			exp.setKymoFirst_ms(options.t_Ms_First);
			exp.setKymoLast_ms(options.t_Ms_Last);
			if (exp.getKymoLast_ms() > exp.getCamImageLast_ms()) {
				exp.setKymoLast_ms(exp.getCamImageLast_ms());
			}
		} else {
			exp.setKymoFirst_ms(0);
			exp.setKymoLast_ms(exp.getCamImageLast_ms() - exp.getCamImageFirst_ms());
		}
	}
}
