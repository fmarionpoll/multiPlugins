package plugins.fmp.multitools.experiment.sequence;

import java.util.Iterator;
import java.util.List;

import icy.roi.ROI2D;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.tools.Logger;
import plugins.kernel.roi.roi2d.ROI2DShape;

public class SequenceKymosUtils {
	public static void transferCamDataROIStoKymo(Experiment exp) {
		if (exp.getSeqKymos() == null) {
			Logger.warn("SequenceKymosUtils:transferCamDataROIstoKymo seqkymos null - return");
			return;
		}
		if (exp.getCapillaries() == null) {
			exp.setCapillaries(new Capillaries());
			Logger.error("SequenceKymosUtils:transferCamDataROIstoKymo error: seqkymos.capillaries was null");
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
			if (!found)
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
