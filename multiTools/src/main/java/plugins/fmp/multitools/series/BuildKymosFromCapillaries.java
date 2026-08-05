package plugins.fmp.multitools.series;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Paths;

import javax.swing.SwingUtilities;

import icy.gui.viewer.Viewer;
import icy.sequence.Sequence;
import plugins.fmp.multitools.experiment.Experiment;
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
		int factor = options.kymoDownsampleFactor > 0 ? options.kymoDownsampleFactor : 1;
		long medianMs = exp.getCamImageBin_ms();
		if (medianMs <= 0 && exp.getFrameTimeScale() != null && !exp.getFrameTimeScale().isEmpty()) {
			medianMs = exp.getFrameTimeScale().medianDeltaMs();
		}
		if (medianMs <= 0) {
			medianMs = Math.max(1L, options.t_Ms_BinDuration);
		}
		// Folder = native camera seconds; ×N is subsampleFactor + effective kymoBin_ms.
		long effectiveBinMs = medianMs * (long) factor;
		options.t_Ms_BinDuration = effectiveBinMs;
		options.kymoDownsampleFactor = factor;
		exp.setKymoBin_ms(effectiveBinMs);
		exp.setKymoSubsampleFactor(factor);
		if (exp.getActiveBinDescription() != null && medianMs > 0) {
			exp.getActiveBinDescription().setCameraIntervalMs(medianMs);
			exp.getActiveBinDescription().setSubsampleFactor(factor);
		}
		int cameraSec = (int) Math.max(1, Math.round(medianMs / 1000.0));
		exp.setNominalIntervalSec(cameraSec);
		exp.setGenerationMode(plugins.fmp.multitools.experiment.GenerationMode.KYMOGRAPH);
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
