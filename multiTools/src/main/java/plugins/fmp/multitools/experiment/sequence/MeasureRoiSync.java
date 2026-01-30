package plugins.fmp.multitools.experiment.sequence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import icy.roi.ROI2D;
import icy.sequence.Sequence;

/**
 * Generic "update measure ROIs at frame t" for a sequence: remove existing
 * measure ROIs (by name filter), then add ROIs for frame t. Mirrors the
 * pattern used for seqCamData fly positions (Experiment.updateROIsAt) and
 * seqKymos capillary measures (SequenceKymos.syncROIsForCurrentFrame).
 */
public final class MeasureRoiSync {

	private MeasureRoiSync() {
	}

	/**
	 * Descriptor linking a measure type to its ROI name filter: optional required
	 * fragment (e.g. "_" for capillary measures) and tokens that identify ROIs to
	 * remove (e.g. "det" for fly positions, or "toplevel", "bottomlevel", etc.).
	 */
	public static final class MeasureRoiFilter {
		public final String nameRequired;
		public final List<String> removeTokens;

		public MeasureRoiFilter(String nameRequired, List<String> removeTokens) {
			this.nameRequired = nameRequired;
			this.removeTokens = removeTokens == null ? Collections.emptyList() : removeTokens;
		}

		public static final MeasureRoiFilter FLY_POSITION = new MeasureRoiFilter(null,
				Collections.singletonList("det"));
		public static final MeasureRoiFilter CAPILLARY_MEASURES = new MeasureRoiFilter("_",
				Arrays.asList("toplevel", "bottomlevel", "derivative", "gulps"));
	}

	/**
	 * Updates the sequence so only measure ROIs for frame t are present: removes
	 * ROIs matching the filter, then adds roisForT (caller must set T on each ROI).
	 *
	 * @param t           frame index
	 * @param seq         sequence to update
	 * @param filter      which ROIs to remove (required fragment + tokens)
	 * @param roisForT    ROIs for frame t (can be null or empty to only remove)
	 */
	public static void updateMeasureROIsAt(int t, Sequence seq, MeasureRoiFilter filter, List<ROI2D> roisForT) {
		boolean isCapillaryMeasures = (filter == MeasureRoiFilter.CAPILLARY_MEASURES);

			System.out.println("capMeasures:"+ isCapillaryMeasures + " - display rois for "+seq.getName());
			for (ROI2D roi: roisForT)
				System.out.println(roi.getName());
		
		if (seq == null || filter == null)
			return;
		seq.beginUpdate();
		try {
			List<ROI2D> all = seq.getROI2Ds();
			List<ROI2D> toRemove = new ArrayList<>();
			for (ROI2D roi : all) {
				if (roi.getName() == null)
					continue;
				String name = roi.getName();
				if (filter.nameRequired != null && !filter.nameRequired.isEmpty() && !name.contains(filter.nameRequired))
					continue;
				for (String token : filter.removeTokens) {
					if (name.contains(token)) {
						toRemove.add(roi);
						break;
					}
				}
			}
			if (!toRemove.isEmpty())
				seq.removeROIs(toRemove, false);
			if (roisForT != null && !roisForT.isEmpty())
				seq.addROIs(roisForT, false);
		} finally {
			seq.endUpdate();
		}
	}
}
