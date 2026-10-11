package plugins.fmp.multitools.experiment.sequence;

import java.util.List;

import icy.roi.ROI2D;

/**
 * Provides ROIs to display on a sequence at a given frame T. Used when the
 * viewer T position changes so the sequence shows the correct ROIs for that
 * frame (e.g. capillary lines, fly positions).
 */
public interface ROIsAtTProvider {

	/** Whether this family currently participates in frame refreshes. */
	default boolean isEnabled() {
		return true;
	}

	MeasureRoiSync.MeasureRoiFilter getFilter();

	List<ROI2D> getROIsAtT(int t);
}
