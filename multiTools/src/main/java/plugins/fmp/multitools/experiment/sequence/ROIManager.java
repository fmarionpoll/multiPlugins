package plugins.fmp.multitools.experiment.sequence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import icy.canvas.Canvas2D;
import icy.canvas.IcyCanvas;
import icy.canvas.Layer;
import icy.gui.viewer.Viewer;
import icy.roi.ROI;
import icy.roi.ROI2D;
import icy.sequence.Sequence;
import plugins.fmp.multitools.tools.Comparators;
import plugins.fmp.multitools.tools.Logger;

public class ROIManager {

	public ROIManager() {
	}

	public void displaySpecificROIs(Sequence seq, boolean isVisible, String pattern) {
		if (seq == null) {
			Logger.warn("Cannot display ROIs: sequence is null");
			return;
		}

		Viewer v = seq.getFirstViewer();
		if (v == null) {
			Logger.warn("Cannot display ROIs: viewer is null");
			return;
		}

		IcyCanvas canvas = v.getCanvas();
		List<Layer> layers = canvas.getLayers(false);
		if (layers == null) {
			return;
		}

		for (Layer layer : layers) {
			ROI roi = layer.getAttachedROI();
			if (roi == null) {
				continue;
			}
			String name = roi.getName();
			if (name != null && name.contains(pattern)) {
				layer.setVisible(isVisible);
			}
		}
	}

	public ArrayList<ROI2D> getROIsContainingString(Sequence seq, String pattern) {
		if (seq == null) {
			Logger.warn("Cannot get ROIs: sequence is null");
			return new ArrayList<>();
		}

		ArrayList<ROI2D> roiList = seq.getROI2Ds();
		Collections.sort(roiList, new Comparators.ROI_Name());

		ArrayList<ROI2D> matchingROIs = new ArrayList<>();
		for (ROI2D roi : roiList) {
			if (roi.getName() != null && roi.getName().contains(pattern)) {
				matchingROIs.add(roi);
			}
		}
		return matchingROIs;
	}

	public ArrayList<ROI2D> getROIsMissingString(Sequence seq, String pattern) {
		if (seq == null) {
			Logger.warn("Cannot get ROIs: sequence is null");
			return new ArrayList<>();
		}

		ArrayList<ROI2D> roiList = seq.getROI2Ds();
		Collections.sort(roiList, new Comparators.ROI_Name());

		ArrayList<ROI2D> matchingROIs = new ArrayList<>();
		for (ROI2D roi : roiList) {
			if (roi.getName() != null && !roi.getName().contains(pattern)) {
				matchingROIs.add(roi);
			}
		}
		return matchingROIs;
	}

	public void removeROIsContainingString(Sequence seq, String pattern) {
		if (seq == null) {
			Logger.warn("Cannot remove ROIs: sequence is null");
			return;
		}

		List<ROI2D> matchingROIs = getROIsContainingString(seq, pattern);

		if (!matchingROIs.isEmpty()) {
			seq.removeROIs(matchingROIs, false);
		}
	}

	public void removeROIsMissingString(Sequence seq, String pattern) {
		if (seq == null) {
			Logger.warn("Cannot remove ROIs: sequence is null");
			return;
		}

		List<ROI2D> matchingROIs = getROIsMissingString(seq, pattern);
		if (!matchingROIs.isEmpty()) {
			seq.removeROIs(matchingROIs, false);
		}
	}

	public void centerOnRoi(Sequence seq, ROI2D roi) {
		if (seq == null || roi == null) {
			Logger.warn("Cannot center on ROI: sequence or ROI is null");
			return;
		}

		Viewer v = seq.getFirstViewer();
		if (v == null) {
			Logger.warn("Cannot center on ROI: viewer is null");
			return;
		}

		try {
			Canvas2D canvas = (Canvas2D) v.getCanvas();
			canvas.centerOn(roi.getBounds());
		} catch (ClassCastException e) {
			Logger.warn("Cannot center on ROI: canvas is not Canvas2D");
		}
	}

	public void selectRoi(Sequence seq, ROI2D roi, boolean select) {
		if (seq == null || roi == null) {
			Logger.warn("Cannot select ROI: sequence or ROI is null");
			return;
		}

		if (select) {
			seq.setSelectedROI(roi);
		} else {
			seq.setSelectedROI(null);
		}
	}

	public void clearAllROIs(Sequence seq) {
		if (seq == null) {
			Logger.warn("Cannot clear ROIs: sequence is null");
			return;
		}
		seq.removeAllROI();
	}

	public void addROI(Sequence seq, ROI roi) {
		if (seq == null || roi == null) {
			Logger.warn("Cannot add ROI: sequence or ROI is null");
			return;
		}
		seq.addROI(roi);
	}
}