package plugins.fmp.multitools.series;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import icy.canvas.IcyCanvas;
import icy.gui.viewer.Viewer;
import icy.sequence.Sequence;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cages.CagesSequenceMapper;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.experiment.sequence.SequenceKymos;
import plugins.fmp.multitools.service.CageSpotKymographBuilder;
import plugins.fmp.multitools.service.KymographBuilder;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.ViewerFMP;

/**
 * Background worker for multiSPOTS96 cage vertical-line kymographs (experimental).
 */
public class BuildKymosFromCageSpots extends BuildSeries {

	@Override
	void analyzeExperiment(Experiment exp) {
		exp.releaseKymographSequence();
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
			displayCageKymographsOnEdt(exp);
		}
	}

	/**
	 * Loads freshly written {@code kymocage_*} TIFFs into {@code seqKymos} and opens (or revives) an
	 * Icy viewer on the EDT — same idea as {@link BuildKymosFromCapillaries} calling
	 * {@link Experiment#loadKymographs()} after export, plus visible {@link ViewerFMP}.
	 */
	private void displayCageKymographsOnEdt(Experiment exp) {
		try {
			SwingUtilities.invokeAndWait(new Runnable() {
				@Override
				public void run() {
					if (!exp.loadCageSpotKymographs()) {
						Logger.warn("BuildKymosFromCageSpots: loadCageSpotKymographs() after build returned false");
						return;
					}
					SequenceKymos sk = exp.getSeqKymos();
					if (sk == null || sk.getSequence() == null) {
						return;
					}
					Sequence seq = sk.getSequence();
					if (seq.isUpdating()) {
						seq.endUpdate();
					}
					if (seq.getSizeT() < 1) {
						Logger.warn("BuildKymosFromCageSpots: kymograph sequence has no frames to display");
						return;
					}
					ArrayList<Viewer> vList = seq.getViewers();
					if (vList == null || vList.isEmpty()) {
						ViewerFMP v = new ViewerFMP(seq, false, true);
						List<String> list = IcyCanvas.getCanvasPluginNames();
						String pluginName = list.stream().filter(s -> s.contains("Canvas2D_3Transforms")).findFirst()
								.orElse(null);
						if (pluginName != null) {
							v.setCanvas(pluginName);
						}
						v.setRepeat(false);
						v.setTitle("Cage kymographs");
						v.setVisible(true);
					} else {
						Viewer existing = vList.get(0);
						existing.setVisible(true);
						existing.toFront();
					}
				}
			});
		} catch (InvocationTargetException | InterruptedException e) {
			Logger.error("BuildKymosFromCageSpots: display cage kymographs on EDT failed", e);
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private boolean loadExperimentDataToBuildCageKymos(Experiment exp) {
		// Do not use loadCamDataSpots() as the gate: it requires getSequence() != null, but
		// lazy-loaded experiments (and fresh image stacks) often have imagesList set while
		// sequence is still null until loadImages() — same order as BuildKymosFromCapillaries.
		exp.load_cages_description_and_measures();
		SequenceCamData seqData = exp.getSeqCamData();
		if (seqData == null) {
			Logger.warn("BuildKymosFromCageSpots: seqCamData is null after load_cages_description_and_measures");
			return false;
		}
		if (!seqData.loadImages()) {
			Logger.warn("BuildKymosFromCageSpots: seqCamData.loadImages() failed (no images list or load error?)");
			return false;
		}
		if (seqData.getSequence() == null) {
			Logger.warn("BuildKymosFromCageSpots: sequence still null after loadImages");
			return false;
		}
		CagesSequenceMapper.transferROIsToSequence(exp.getCages(), seqData);
		seqData.removeROIsContainingString("spot");
		exp.getSpots().applyPreConsumedRoiStyles();
		exp.getCages().transferCageSpotsToSequenceAsROIs(seqData, exp.getSpots());

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
