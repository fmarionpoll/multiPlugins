package plugins.fmp.multitools.series;

import java.lang.reflect.InvocationTargetException;

import javax.swing.JOptionPane;
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
		KymographBuilder.PreArchiveResult pre = KymographBuilder.preArchiveExistingKymographsInCurrentBin(exp);
		if (pre.failed > 0) {
			try {
				SwingUtilities.invokeAndWait(new Runnable() {
					@Override
					public void run() {
						JOptionPane.showMessageDialog(null,
								"Cannot rename existing kymograph TIFF(s) in:\n" + pre.directory + "\n\n"
										+ "Some files appear to be locked by Windows or another process.\n"
										+ "Close any viewer/file browser preview using these kymographs and retry.",
								"Kymograph files locked", JOptionPane.WARNING_MESSAGE);
					}
				});
			} catch (InvocationTargetException | InterruptedException e) {
				Logger.error("BuildKymosFromCapillaries: failed to show lock warning dialog", e);
			}
			return;
		}

		loadExperimentDataToBuildKymos(exp);
		openDataViewer(exp);
		getTimeLimitsOfSequence(exp);

		KymographBuilder builder = new KymographBuilder();
		if (builder.buildKymograph(exp, options)) {
			exp.saveExperimentDescriptors();
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

		// Use loadImages() like LoadSaveExperiment does to properly initialize the sequence
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
		exp.setKymoBin_ms(options.t_Ms_BinDuration);
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
