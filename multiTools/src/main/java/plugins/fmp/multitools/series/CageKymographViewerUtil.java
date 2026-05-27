package plugins.fmp.multitools.series;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JToolBar;

import icy.canvas.IcyCanvas;
import icy.gui.viewer.Viewer;
import icy.gui.viewer.ViewerEvent;
import icy.gui.viewer.ViewerListener;
import icy.sequence.DimensionId;
import icy.sequence.Sequence;
import plugins.fmp.multitools.canvas2D.Canvas2D_3Transforms;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.sequence.SequenceKymos;
import plugins.fmp.multitools.service.CageKymographPickSupport;
import plugins.fmp.multitools.service.KymocageCageResolver;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.ViewerFMP;
import plugins.fmp.multitools.tools.overlay.CageKymographSpotPickOverlay;

/**
 * Opens (or revives) an Icy viewer for a cage kymograph {@link SequenceKymos} stack. Must run on
 * the Swing EDT.
 * <p>
 * Viewer titles follow the multiCAFE kymograph pattern ({@code Intervals#viewerChanged}): show the
 * capillary/cage display name for the current frame T and the analysis bin duration.
 */
public final class CageKymographViewerUtil {

	private static CageKymographSpotPickOverlay spotPickOverlay;
	private static Sequence spotPickHostSequence;
	private static final KymographViewerTitleListener titleListener = new KymographViewerTitleListener();
	private static Experiment titleListenerExperiment;

	private CageKymographViewerUtil() {
	}

	public static void openIfPresent(Experiment exp) {
		if (exp == null) {
			return;
		}
		if (exp.isCageKymographDiskRewriteInProgress()) {
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
			Logger.warn("CageKymographViewerUtil: no kymograph frames to display");
			return;
		}
		Viewer v;
		ArrayList<Viewer> vList = seq.getViewers();
		if (vList == null || vList.isEmpty()) {
			v = new ViewerFMP(seq, false, true);
			List<String> list = IcyCanvas.getCanvasPluginNames();
			String pluginName = list.stream().filter(s -> s.contains("Canvas2D_3Transforms")).findFirst().orElse(null);
			if (pluginName != null) {
				v.setCanvas(pluginName);
			}
			v.setRepeat(false);
			if (v.getCanvas() instanceof Canvas2D_3Transforms) {
				JToolBar toolBar = v.getToolBar();
				((Canvas2D_3Transforms) v.getCanvas()).customizeToolbarStep2(toolBar);
			}
			v.setVisible(true);
		} else {
			v = vList.get(0);
			v.setVisible(true);
			v.toFront();
		}
		attachSpotPickOverlay(exp, seq);
		attachViewerTitleListener(exp, v);
	}

	/**
	 * Display name for kymograph frame {@code t}: cage ROI name when available, else
	 * {@code kymocage_*} file stem.
	 */
	public static String cageDisplayNameForKymographFrame(Experiment exp, int t) {
		if (exp == null || t < 0) {
			return null;
		}
		Cage cage = CageKymographPickSupport.cageForKymographFrame(exp, t);
		if (cage != null && cage.getRoi() != null) {
			String roiName = cage.getRoi().getName();
			if (roiName != null && !roiName.isEmpty()) {
				return roiName;
			}
		}
		SequenceKymos sk = exp.getSeqKymos();
		if (sk != null) {
			String path = sk.getFileNameFromImageList(t);
			String base = KymocageCageResolver.fileBaseFromKymographPath(path);
			if (base != null) {
				return base;
			}
		}
		return "t=" + t;
	}

	/**
	 * Title for the kymograph viewer at frame {@code t} (multiCAFE:
	 * {@code capillaryName + "  :" + intervalSec + " s"}).
	 */
	public static String buildKymographViewerTitle(Experiment exp, int t) {
		String cageName = cageDisplayNameForKymographFrame(exp, t);
		if (cageName == null || cageName.isEmpty()) {
			cageName = "Cage kymographs";
		}
		long binMs = exp != null ? exp.getKymoBin_ms() : 0;
		if (binMs <= 0 && exp != null) {
			binMs = exp.getCamImageBin_ms();
		}
		if (binMs > 0) {
			long sec = Math.max(1L, binMs / 1000L);
			return cageName + "  :" + sec + " s";
		}
		return cageName;
	}

	/** Select the cage ROI matching kymograph frame {@code t}; deselect other cages (multiCAFE capillary sync). */
	public static void selectCageForKymographFrame(Experiment exp, int t) {
		if (exp == null || exp.getCages() == null || exp.getCages().cagesList == null) {
			return;
		}
		Cage cage = CageKymographPickSupport.cageForKymographFrame(exp, t);
		for (Cage c : exp.getCages().cagesList) {
			if (c == null || c.getRoi() == null) {
				continue;
			}
			c.getRoi().setSelected(c == cage);
		}
	}

	private static void onKymographFrameSelected(Experiment exp, Viewer v, int t) {
		if (exp == null || v == null || t < 0) {
			return;
		}
		selectCageForKymographFrame(exp, t);
		v.setTitle(buildKymographViewerTitle(exp, t));
	}

	private static void attachViewerTitleListener(Experiment exp, Viewer v) {
		if (exp == null || v == null) {
			return;
		}
		titleListenerExperiment = exp;
		titleListener.setExperiment(exp);
		v.removeListener(titleListener);
		v.addListener(titleListener);
		int t = v.getPositionT();
		if (t < 0) {
			t = 0;
		}
		onKymographFrameSelected(exp, v, t);
	}

	private static synchronized void attachSpotPickOverlay(Experiment exp, Sequence seq) {
		if (spotPickOverlay != null && spotPickHostSequence != null) {
			spotPickHostSequence.removeOverlay(spotPickOverlay);
			spotPickOverlay = null;
			spotPickHostSequence = null;
		}
		spotPickOverlay = new CageKymographSpotPickOverlay(exp);
		seq.addOverlay(spotPickOverlay);
		spotPickHostSequence = seq;
	}

	private static final class KymographViewerTitleListener implements ViewerListener {

		private Experiment experiment;

		void setExperiment(Experiment exp) {
			this.experiment = exp;
		}

		@Override
		public void viewerChanged(ViewerEvent event) {
			if (event.getType() != ViewerEvent.ViewerEventType.POSITION_CHANGED
					|| event.getDim() != DimensionId.T) {
				return;
			}
			Viewer v = event.getSource();
			Experiment exp = experiment;
			if (exp == null) {
				exp = titleListenerExperiment;
			}
			if (exp == null || exp.getSeqKymos() == null || exp.getSeqKymos().getSequence() == null) {
				return;
			}
			if (v.getSequence() == null
					|| v.getSequence().getId() != exp.getSeqKymos().getSequence().getId()) {
				return;
			}
			int t = v.getPositionT();
			if (t < 0) {
				return;
			}
			onKymographFrameSelected(exp, v, t);
		}

		@Override
		public void viewerClosed(Viewer viewer) {
			if (viewer != null) {
				viewer.removeListener(this);
			}
		}
	}
}
