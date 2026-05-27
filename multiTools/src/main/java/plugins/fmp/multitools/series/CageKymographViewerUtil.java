package plugins.fmp.multitools.series;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JToolBar;

import icy.canvas.IcyCanvas;
import icy.gui.viewer.Viewer;
import icy.sequence.Sequence;
import plugins.fmp.multitools.canvas2D.Canvas2D_3Transforms;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.sequence.SequenceKymos;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.ViewerFMP;
import plugins.fmp.multitools.tools.overlay.CageKymographSpotPickOverlay;

/**
 * Opens (or revives) an Icy viewer for a cage kymograph {@link SequenceKymos} stack. Must run on
 * the Swing EDT.
 */
public final class CageKymographViewerUtil {

	private static CageKymographSpotPickOverlay spotPickOverlay;
	private static Sequence spotPickHostSequence;

	private CageKymographViewerUtil() {
	}

	public static void openIfPresent(Experiment exp) {
		if (exp == null) {
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
		ArrayList<Viewer> vList = seq.getViewers();
		if (vList == null || vList.isEmpty()) {
			ViewerFMP v = new ViewerFMP(seq, false, true);
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
			v.setTitle("Cage kymographs");
			v.setVisible(true);
		} else {
			Viewer existing = vList.get(0);
			existing.setVisible(true);
			existing.toFront();
		}
		attachSpotPickOverlay(exp, seq);
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
}
