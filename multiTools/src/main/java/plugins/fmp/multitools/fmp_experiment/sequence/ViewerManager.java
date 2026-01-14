package plugins.fmp.multitools.fmp_experiment.sequence;

import java.awt.Rectangle;
import java.lang.reflect.InvocationTargetException;
import plugins.fmp.multitools.fmp_tools.Logger;

import javax.swing.SwingUtilities;

import icy.sequence.Sequence;
import plugins.fmp.multitools.fmp_tools.ViewerFMP;
import plugins.fmp.multitools.fmp_tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.fmp_tools.overlay.OverlayThreshold;

public class ViewerManager {

	private OverlayThreshold overlayThresholdCam = null;

	public ViewerManager() {
	}

	public void displayViewerAtRectangle(Sequence seq, Rectangle parentRect) {
		if (seq == null) {
			Logger.warn("Cannot display viewer: sequence is null");
			return;
		}

		if (parentRect == null) {
			Logger.warn("Cannot display viewer: parent rectangle is null");
			return;
		}

		try {
			SwingUtilities.invokeAndWait(new Runnable() {
				public void run() {
					ViewerFMP v = (ViewerFMP) seq.getFirstViewer();
					if (v == null) {
						v = new ViewerFMP(seq, true, true);
					}
					Rectangle viewerBounds = v.getBoundsInternal();
					viewerBounds.setLocation(parentRect.x + parentRect.width, parentRect.y);
					v.setBounds(viewerBounds);
				}
			});
		} catch (InvocationTargetException | InterruptedException e) {
			Logger.error("Error displaying viewer: " + e.getMessage());
		}
	}

	public void updateOverlay(Sequence seq) {
		if (seq == null) {
			Logger.warn("Cannot update overlay: sequence is null");
			return;
		}

		if (overlayThresholdCam == null) {
			overlayThresholdCam = new OverlayThreshold(seq);
		} else {
			seq.removeOverlay(overlayThresholdCam);
			overlayThresholdCam.setSequence(seq);
		}
		seq.addOverlay(overlayThresholdCam);
	}

	public void removeOverlay(Sequence seq) {
		if (seq == null || overlayThresholdCam == null) {
			return;
		}
		seq.removeOverlay(overlayThresholdCam);
	}

	public void updateOverlayThreshold(int threshold, ImageTransformEnums transform, boolean ifGreater) {
		if (overlayThresholdCam == null) {
			return;
		}
		overlayThresholdCam.setThresholdSingle(threshold, transform, ifGreater);
		overlayThresholdCam.painterChanged();
	}

	public OverlayThreshold getOverlayThresholdCam() {
		return overlayThresholdCam;
	}

	public void setOverlayThresholdCam(OverlayThreshold overlay) {
		this.overlayThresholdCam = overlay;
	}
}