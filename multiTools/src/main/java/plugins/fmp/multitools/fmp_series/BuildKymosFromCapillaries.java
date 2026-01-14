package plugins.fmp.multitools.fmp_series;

import java.lang.reflect.InvocationTargetException;

import javax.swing.SwingUtilities;

import icy.gui.viewer.Viewer;
import icy.sequence.Sequence;
import plugins.fmp.multitools.fmp_experiment.Experiment;
import plugins.fmp.multitools.fmp_experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.fmp_service.KymographBuilder;
import plugins.fmp.multitools.fmp_tools.Logger;

public class BuildKymosFromCapillaries extends BuildSeries {
	public Sequence seqDataForKymos = new Sequence();
	private Viewer vData = null;

	void analyzeExperiment(Experiment exp) {
		loadExperimentDataToBuildKymos(exp);
		openDataViewer(exp);
		getTimeLimitsOfSequence(exp);

		KymographBuilder builder = new KymographBuilder();
		if (builder.buildKymograph(exp, options)) {
//			builder.saveComputation(exp, options);
			exp.saveExperimentDescriptors();
		}

		// Don't close seqKymos sequence - it will be needed when loading the experiment later
		// The sequence will be properly managed by the experiment lifecycle
		
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
		exp.build_MsTimeIntervalsArray_From_SeqCamData_FileNamesList(exp.getCamImageFirst_ms());
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
