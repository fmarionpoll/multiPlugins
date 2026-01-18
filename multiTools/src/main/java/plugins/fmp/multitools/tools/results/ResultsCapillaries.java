package plugins.fmp.multitools.tools.results;

import java.util.ArrayList;
import java.util.List;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cages.CageCapillariesComputation;
import plugins.fmp.multitools.experiment.capillaries.Capillary;
import plugins.fmp.multitools.experiment.capillaries.CapillaryMeasure;
import plugins.fmp.multitools.tools.Level2D;

public class ResultsCapillaries extends Results {

	public ResultsCapillaries(String name, int nflies, int cageID, int cagePos, EnumResults exportType) {
		super(name, nflies, cageID, cagePos, exportType);
		// TODO Auto-generated constructor stub
	}

	/**
	 * Gets L+R data (SUM or PI) from CageCapillariesComputation for TOPLEVEL_LR
	 * export. For L capillaries: exports SUM measure For R capillaries: exports PI
	 * measure
	 * 
	 * @param exp        The experiment
	 * @param capillary  The capillary
	 * @param results    The XLS results to populate
	 * @param binData    The bin duration for the data
	 * @param binExcel   The bin duration for Excel output
	 * @param subtractT0 Whether to subtract T0 value
	 */
	void getLRDataFromCage(Experiment exp, Capillary capillary, long binData, long binExcel, boolean subtractT0) {

		int cageID = capillary.getCageID();
		CageCapillariesComputation cageComp = exp.getCages().getCageComputation(cageID);

		if (cageComp == null) {
			// No computation available, fall back to raw
			ResultsOptions fallbackOptions = new ResultsOptions();
			fallbackOptions.resultType = EnumResults.TOPRAW;
			getDataFromCapillary(exp, capillary, binData, binExcel, fallbackOptions, subtractT0);
			return;
		}

		// Determine which measure to use based on capillary side
		String side = getCapillarySide(capillary);
		CapillaryMeasure measure = null;

		if (side != null && (side.contains("L") || side.contains("1"))) {
			// L capillary: use SUM
			measure = cageComp.getSumMeasure();
		} else if (side != null && (side.contains("R") || side.contains("2"))) {
			// R capillary: use PI
			measure = cageComp.getPIMeasure();
		} else {
			// Side unclear, try first capillary as L, second as R
			List<Capillary> caps = exp.getCages().getCageList().stream().filter(c -> c.getCageID() == cageID)
					.findFirst().map(c -> c.getCapillaries(exp.getCapillaries()))
					.orElse(java.util.Collections.emptyList());

			if (!caps.isEmpty() && caps.get(0) == capillary) {
				measure = cageComp.getSumMeasure();
			} else if (caps.size() >= 2 && caps.get(1) == capillary) {
				measure = cageComp.getPIMeasure();
			}
		}

		if (measure != null && measure.polylineLevel != null && measure.polylineLevel.npoints > 0) {
			// Get measures by binning polyline data (similar to getMeasures implementation)
			if (binData <= 0 || binExcel <= 0) {
				// Invalid bin sizes, fall back to raw
				ResultsOptions fallbackOptions = new ResultsOptions();
				fallbackOptions.resultType = EnumResults.TOPRAW;
				getDataFromCapillary(exp, capillary, binData, binExcel, fallbackOptions, subtractT0);
				return;
			}
			Level2D polyline = measure.polylineLevel;
			long maxMs = (polyline.npoints - 1) * binData;
			int nOutputFrames = (int) (maxMs / binExcel) + 1;

			ArrayList<Integer> intData = new ArrayList<>(nOutputFrames);
			for (int i = 0; i < nOutputFrames; i++) {
				long timeMs = i * binExcel;
				int index = (int) (timeMs / binData);
				if (index >= 0 && index < polyline.npoints) {
					intData.add((int) polyline.ypoints[index]);
				} else {
					intData.add(0);
				}
			}

			if (intData != null && !intData.isEmpty()) {
				// Convert Integer to Double
				ArrayList<Double> dataValues = new ArrayList<>(intData.size());
				int t0Value = 0;

				if (subtractT0 && intData.size() > 0) {
					t0Value = intData.get(0);
				}

				for (Integer intValue : intData) {
					if (subtractT0) {
						dataValues.add((double) (intValue - t0Value));
					} else {
						dataValues.add(intValue.doubleValue());
					}
				}

				setDataValues(dataValues);
				return;
			}
		}

		// Fallback to raw if computation failed
		ResultsOptions fallbackOptions = new ResultsOptions();
		fallbackOptions.resultType = EnumResults.TOPRAW;
		getDataFromCapillary(exp, capillary, binData, binExcel, fallbackOptions, subtractT0);
	}

	/**
	 * Helper method to determine capillary side from capSide or name.
	 */
	private String getCapillarySide(Capillary cap) {
		if (cap.getProperties().getSide() != null && !cap.getProperties().getSide().equals("."))
			return cap.getProperties().getSide();
		// Try to get from name
		String name = cap.getRoiName();
		if (name != null) {
			name = name.toUpperCase();
			if (name.contains("L") || name.contains("1"))
				return "L";
			if (name.contains("R") || name.contains("2"))
				return "R";
		}
		return "";
	}
}
