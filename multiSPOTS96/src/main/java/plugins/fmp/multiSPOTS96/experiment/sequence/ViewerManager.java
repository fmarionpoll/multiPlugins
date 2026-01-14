package plugins.fmp.multiSPOTS96.experiment.sequence;

import java.awt.Rectangle;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;

import icy.sequence.Sequence;
import plugins.fmp.multiSPOTS96.tools.ViewerFMP;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multiSPOTS96.tools.overlay.OverlayThreshold;

public class ViewerManager {
	private static final Logger LOGGER = Logger.getLogger(ViewerManager.class.getName());

	private OverlayThreshold overlayThresholdCam = null;

	public ViewerManager() {
	}

	public void displayViewerAtRectangle(Sequence seq, Rectangle parentRect) {
		if (seq == null) {
			LOGGER.warning("Cannot display viewer: sequence is null");
			return;
		}

		if (parentRect == null) {
			LOGGER.warning("Cannot display viewer: parent rectangle is null");
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
			LOGGER.severe("Error displaying viewer: " + e.getMessage());
		}
	}

	public void updateOverlay(Sequence seq) {
		if (seq == null) {
			LOGGER.warning("Cannot update overlay: sequence is null");
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