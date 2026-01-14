package plugins.fmp.multiSPOTS.tools;

import java.util.Comparator;

import icy.roi.ROI;
import icy.roi.ROI2D;
import icy.sequence.Sequence;
import plugins.fmp.multiSPOTS.experiment.Experiment;
import plugins.fmp.multiSPOTS.experiment.cages.Cage;
import plugins.fmp.multiSPOTS.experiment.cages.FlyPosition;
import plugins.fmp.multiSPOTS.experiment.cages.FlyPositions;
import plugins.fmp.multiSPOTS.tools.toExcel.XLSResults;

public class Comparators {
	public static class ROI_Name_Comparator implements Comparator<ROI> {
		@Override
		public int compare(ROI o1, ROI o2) {
			return o1.getName().compareTo(o2.getName());
		}
	}

	public static class ROI2D_Name_Comparator implements Comparator<ROI2D> {
		@Override
		public int compare(ROI2D o1, ROI2D o2) {
			return o1.getName().compareTo(o2.getName());
		}
	}

	public static class ROI2D_T_Comparator implements Comparator<ROI2D> {
		@Override
		public int compare(ROI2D o1, ROI2D o2) {
			return o1.getT() - o2.getT();
		}
	}

	public static class Sequence_Name_Comparator implements Comparator<Sequence> {
		@Override
		public int compare(Sequence o1, Sequence o2) {
			return o1.getName().compareTo(o2.getName());
		}
	}

	public static class XLSResults_Name_Comparator implements Comparator<XLSResults> {
		@Override
		public int compare(XLSResults o1, XLSResults o2) {
			return o1.name.compareTo(o2.name);
		}
	}

	public static class XYTaSeries_Name_Comparator implements Comparator<FlyPositions> {
		@Override
		public int compare(FlyPositions o1, FlyPositions o2) {
			return o1.name.compareTo(o2.name);
		}
	}

	public static class Cage_Name_Comparator implements Comparator<Cage> {
		@Override
		public int compare(Cage o1, Cage o2) {
			return o1.getRoi().getName().compareTo(o2.getRoi().getName());
		}
	}

	public static class XYTaValue_Tindex_Comparator implements Comparator<FlyPosition> {
		@Override
		public int compare(FlyPosition o1, FlyPosition o2) {
			return o1.flyIndexT - o2.flyIndexT;
		}
	}

	public static class Experiment_Start_Comparator implements Comparator<Experiment> {
		@Override
		public int compare(Experiment exp1, Experiment exp2) {
			return Long.compare(exp1.seqCamData.firstImage_ms + exp1.seqCamData.binFirst_ms,
					exp2.seqCamData.firstImage_ms + exp2.seqCamData.binFirst_ms);
		}
	}
}
