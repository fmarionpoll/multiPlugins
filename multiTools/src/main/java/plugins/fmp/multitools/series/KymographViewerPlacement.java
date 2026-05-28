package plugins.fmp.multitools.series;

import java.awt.Rectangle;

import icy.gui.frame.IcyFrame;
import icy.gui.viewer.Viewer;
import icy.main.Icy;
import icy.sequence.Sequence;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ui.FrameGeometryPreferences;

/**
 * Default placement for cage kymograph viewers: to the right of the camera viewer when present,
 * otherwise to the right of the plugin main window.
 */
public final class KymographViewerPlacement {

	private KymographViewerPlacement() {
	}

	public static Rectangle computeDefaultBounds(Experiment exp, IcyFrame pluginMainFrame) {
		Rectangle anchor = anchorBounds(exp, pluginMainFrame);
		if (anchor == null) {
			return FrameGeometryPreferences.clampToUsableScreen(new Rectangle(100, 100, 800, 400));
		}

		int width = 800;
		int height = 400;
		if (exp != null && exp.getSeqKymos() != null && exp.getSeqKymos().getSequence() != null) {
			Rectangle img = exp.getSeqKymos().getSequence().getBounds2D();
			if (img != null) {
				if (img.width > 0) {
					width = (int) img.getWidth();
				}
				if (img.height > 0) {
					height = (int) img.getHeight();
				}
			}
		}

		Rectangle r = new Rectangle(anchor.x + anchor.width + 5, anchor.y, width, height);
		int desktopWidth = Icy.getMainInterface().getMainFrame().getDesktopWidth();
		if (r.x + r.width > desktopWidth) {
			r.x = anchor.x;
			r.y = anchor.y + anchor.height + 5;
			r.width = Math.min(desktopWidth, Math.max(width, 400));
		}
		return FrameGeometryPreferences.clampToUsableScreen(r);
	}

	private static Rectangle anchorBounds(Experiment exp, IcyFrame pluginMainFrame) {
		if (exp != null && exp.getSeqCamData() != null) {
			Sequence seqCam = exp.getSeqCamData().getSequence();
			if (seqCam != null) {
				Viewer camViewer = seqCam.getFirstViewer();
				if (camViewer != null) {
					Rectangle b = camViewer.getBounds();
					if (b != null && b.width > 0 && b.height > 0) {
						return b;
					}
				}
			}
		}
		if (pluginMainFrame != null) {
			Rectangle b = pluginMainFrame.getBoundsInternal();
			if (b != null && b.width > 0 && b.height > 0) {
				return b;
			}
		}
		return null;
	}
}
