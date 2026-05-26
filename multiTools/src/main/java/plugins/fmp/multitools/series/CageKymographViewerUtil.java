package plugins.fmp.multitools.series;

import java.util.ArrayList;
import java.util.List;

import icy.canvas.IcyCanvas;
import icy.gui.viewer.Viewer;
import icy.sequence.Sequence;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.sequence.SequenceKymos;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.ViewerFMP;

/**
 * Opens (or revives) an Icy viewer for a cage kymograph {@link SequenceKymos} stack. Must run on
 * the Swing EDT.
 */
public final class CageKymographViewerUtil {

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
			v.setTitle("Cage kymographs");
			v.setVisible(true);
		} else {
			Viewer existing = vList.get(0);
			existing.setVisible(true);
			existing.toFront();
		}
	}
}
