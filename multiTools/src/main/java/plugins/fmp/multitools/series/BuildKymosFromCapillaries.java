package plugins.fmp.multitools.series;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Paths;

import javax.swing.SwingUtilities;

import icy.gui.viewer.Viewer;
import icy.sequence.Sequence;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.NominalIntervalConfirmer;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.service.KymographBuilder;
import plugins.fmp.multitools.tools.Logger;

public class BuildKymosFromCapillaries extends BuildSeries {
	public Sequence seqDataForKymos = new Sequence();
	private Viewer vData = null;

	void analyzeExperiment(Experiment exp) {
		exp.releaseKymographSequence();

		String kymoDir = exp.getKymosBinFullDirectory();
		if (options != null) {
			options.kymoPreflightDetectedLockedFiles = false;
		}
		if (kymoDir != null && options != null) {
			KymographBuilder.LockProbeReport lockProbe = KymographBuilder.probeKymographFileLocks(Paths.get(kymoDir));
			options.kymoPreflightDetectedLockedFiles = lockProbe.locked > 0;
			if (lockProbe.locked > 0) {
				Logger.warn("BuildKymosFromCapillaries: kymograph TIFF(s) appear locked in " + lockProbe.directory
						+ " (locked=" + lockProbe.locked + "/" + lockProbe.total + "). Rename-based bin prep will be skipped.");
				for (String s : lockProbe.lockedFiles) {
					Logger.warn("BuildKymosFromCapillaries: locked: " + s);
				}
			}
		}

		loadExperimentDataToBuildKymos(exp);
		openDataViewer(exp);
		getTimeLimitsOfSequence(exp);

		KymographBuilder builder = new KymographBuilder();
		if (builder.buildKymograph(exp, options)) {
			exp.saveExperimentDescriptors();
			// Session bin hint must follow the directory we just wrote (e.g. bin_300),
			// not a legacy bin_60 left from before the rebuild.
			if (options.expList != null && exp.getBinSubDirectory() != null) {
				options.expList.expListBinSubDirectory = exp.getBinSubDirectory();
				plugins.fmp.multitools.experiment.BinDirectoryResolver.clearSessionRemembered();
			}
		}

		try {
			SwingUtilities.invokeAndWait(new Runnable() {
				@Override
				public void run() {
					if (!exp.loadKymographs()) {
						Logger.warn("BuildKymosFromCapillaries: loadKymographs() after rebuild returned false");
					}
				}
			});
		} catch (InvocationTargetException | InterruptedException e) {
			Logger.error("BuildKymosFromCapillaries: loadKymographs on EDT failed", e);
		}

		closeDataViewerAndSequence();
	}

	private boolean loadExperimentDataToBuildKymos(Experiment exp) {
		boolean flag = exp.loadMCCapillaries_Only();
		// exp.getCapillaries().transferCapillaryRoiToSequence(exp.getSeqCamData().getSequence());
		SequenceCamData seqData = exp.getSeqCamData();

		// Use loadImages() like BrowsePanel does to properly initialize the sequence
		seqData.loadImages();
		// Initialize time parameters for this experiment BEFORE building time intervals array
		exp.getFileIntervalsFromSeqCamData();
		long firstValidEpochMs = seqData.getFirstValidFrameEpochMs();
		if (firstValidEpochMs < 0) {
			firstValidEpochMs = exp.getCamImageFirst_ms();
		}
		exp.build_MsTimeIntervalsArray_From_SeqCamData_FileNamesList(firstValidEpochMs);
		return flag;
	}

	protected void getTimeLimitsOfSequence(Experiment exp) {
		exp.getFileIntervalsFromSeqCamData();
		long requested = options.t_Ms_BinDuration;
		long medianMs = exp.getCamImageBin_ms();
		if (medianMs <= 0 && exp.getFrameTimeScale() != null && !exp.getFrameTimeScale().isEmpty()) {
			medianMs = exp.getFrameTimeScale().medianDeltaMs();
		}
		// Batch-safe: console warn only; never abort. Native width is still 1 col/frame.
		long binMs = NominalIntervalConfirmer.clampBinMsToCameraSampling(requested, medianMs);
		options.t_Ms_BinDuration = binMs;
		exp.setKymoBin_ms(binMs);
		// Keep nominal in sync — getBinNameFromKymoFrameStep() prefers nominal and would
		// otherwise keep writing into legacy bin_60 while kymoBin is already 300 s.
		int nominalSec = (int) Math.max(1, Math.round(binMs / 1000.0));
		exp.setNominalIntervalSec(nominalSec);
		options.binSubDirectory = exp.getBinNameFromKymoFrameStep();
		if (options.isFrameFixed) {
			exp.setKymoFirst_ms(options.t_Ms_First);
			exp.setKymoLast_ms(options.t_Ms_Last);
			if (exp.getKymoLast_ms() > exp.getCamImageLast_ms())
				exp.setKymoLast_ms(exp.getCamImageLast_ms());
		} else {
			exp.setKymoFirst_ms(0);
			exp.setKymoLast_ms(exp.getCamImageLast_ms() - exp.getCamImageFirst_ms());
		}
	}

	private void openDataViewer(Experiment exp) {
		try {
			SwingUtilities.invokeAndWait(new Runnable() {
				public void run() {
					seqDataForKymos = newSequence("analyze stack starting with file " + exp.getSeqCamData().getSequence().getName(),
							exp.getSeqCamData().getSeqImage(0, 0));
					vData = new Viewer(seqDataForKymos, true);
				}
			});
		} catch (InvocationTargetException | InterruptedException e) {
			Logger.error("BuildKymographs:openKymoViewers() Failed to open kymograph viewers", e);
		}
	}
	
	private void closeDataViewerAndSequence() {
		closeViewer(vData);
		closeSequence(seqDataForKymos);
	}
}
