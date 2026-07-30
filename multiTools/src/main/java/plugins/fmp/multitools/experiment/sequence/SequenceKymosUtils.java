package plugins.fmp.multitools.experiment.sequence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import icy.roi.ROI2D;
import icy.sequence.Sequence;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.tools.Comparators;
import plugins.fmp.multitools.tools.Logger;
import plugins.kernel.roi.roi2d.ROI2DShape;

public class SequenceKymosUtils {
	/**
	 * Syncs capillaries with cam-sequence {@code line*} ROIs by merge: add missing,
	 * remove orphans. Keeps existing Capillary objects when names match (preserves
	 * measures). Used by edit/load paths.
	 */
	public static void transferCamDataROIStoKymo(Experiment exp) {
		if (exp == null || exp.getSeqCamData() == null) {
			Logger.warn("SequenceKymosUtils:transferCamDataROIstoKymo seqCamData null - return");
			return;
		}
		if (exp.getCapillaries() == null) {
			exp.setCapillaries(new Capillaries());
			Logger.error("SequenceKymosUtils:transferCamDataROIstoKymo error: capillaries was null");
		}

		// rois not in cap? add
		List<ROI2D> listROISCap = exp.getSeqCamData().findROIsMatchingNamePattern("line");
		for (ROI2D roi : listROISCap) {
			boolean found = false;
			for (Capillary cap : exp.getCapillaries().getList()) {
				if (cap.getRoi() != null && roi.getName().equals(cap.getRoiName())) {
					found = true;
					break;
				}
			}
			if (!found && roi instanceof ROI2DShape)
				exp.getCapillaries().getList().add(new Capillary((ROI2DShape) roi));
		}

		// cap with no corresponding roi? remove
		Iterator<Capillary> iterator = exp.getCapillaries().getList().iterator();
		while (iterator.hasNext()) {
			Capillary cap = iterator.next();
			boolean found = false;
			for (ROI2D roi : listROISCap) {
				if (roi.getName().equals(cap.getRoiName())) {
					found = true;
					break;
				}
			}
			if (!found)
				iterator.remove();
		}
	}

	/**
	 * Removes all cam-sequence ROIs whose names contain {@code line}.
	 */
	public static void removeCamLineRois(Experiment exp) {
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null)
			return;
		Sequence seq = exp.getSeqCamData().getSequence();
		List<ROI2D> lineRois = new ArrayList<>(exp.getSeqCamData().findROIsMatchingNamePattern("line"));
		for (ROI2D roi : lineRois) {
			seq.removeROI(roi);
		}
	}

	/**
	 * Replaces the capillary list with fresh Capillary objects built from current
	 * cam-sequence {@code line*} ROIs. Does not preserve measures or kymograph
	 * indices from any previous capillaries. Used when regenerating capillaries.
	 */
	public static void replaceCapillariesFromCamLineRois(Experiment exp) {
		if (exp == null || exp.getSeqCamData() == null) {
			Logger.warn("SequenceKymosUtils:replaceCapillariesFromCamLineRois seqCamData null - return");
			return;
		}
		if (exp.getCapillaries() == null) {
			exp.setCapillaries(new Capillaries());
		}
		exp.getCapillaries().deleteAllCapillaries();

		List<ROI2D> listROISCap = exp.getSeqCamData().findROIsMatchingNamePattern("line");
		Collections.sort(listROISCap, new Comparators.ROI2D_Name());
		for (ROI2D roi : listROISCap) {
			if (roi instanceof ROI2DShape)
				exp.getCapillaries().getList().add(new Capillary((ROI2DShape) roi));
		}
	}

	public static void transferKymoCapillariesToCamData(Experiment exp) {
		if (exp.getCapillaries() == null)
			return;
		List<ROI2D> listROISCap = exp.getSeqCamData().findROIsMatchingNamePattern("line");
		// roi with no corresponding cap? add ROI
		for (Capillary cap : exp.getCapillaries().getList()) {
			boolean found = false;
			for (ROI2D roi : listROISCap) {
				if (roi.getName().equals(cap.getRoiName())) {
					found = true;
					break;
				}
			}
			if (!found)
				exp.getSeqCamData().getSequence().addROI(cap.getRoi());
		}
	}

}
