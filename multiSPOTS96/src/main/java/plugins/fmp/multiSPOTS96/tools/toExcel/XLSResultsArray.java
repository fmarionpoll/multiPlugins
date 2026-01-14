package plugins.fmp.multiSPOTS96.tools.toExcel;

import java.util.ArrayList;

import plugins.fmp.multiSPOTS96.experiment.cages.Cage;
import plugins.fmp.multiSPOTS96.experiment.cages.CagesArray;
import plugins.fmp.multiSPOTS96.experiment.spots.Spot;

public class XLSResultsArray {
	ArrayList<XLSResults> resultsList = null;
	String stim = null;
	String conc = null;
	double lowestPiAllowed = -1.2;
	double highestPiAllowed = 1.2;

	public XLSResultsArray(int size) {
		resultsList = new ArrayList<XLSResults>(size);
	}

	public XLSResultsArray() {
		resultsList = new ArrayList<XLSResults>();
	}

	public int size() {
		return resultsList.size();
	}

	public XLSResults getRow(int index) {
		if (index >= resultsList.size())
			return null;
		return resultsList.get(index);
	}

	public void subtractDeltaT(int i, int j) {
		for (XLSResults row : resultsList)
			row.subtractDeltaT(1, 1); // options.buildExcelStepMs);
	}

	// ---------------------------------------------------

	public void getSpotsArrayResults_T0(CagesArray cagesArray, EnumXLSExport exportType, int nOutputFrames,
			long kymoBinCol_Ms, XLSExportOptions xlsExportOptions) {
		xlsExportOptions.exportType = exportType;
		buildSpotsDataForPass1(cagesArray, nOutputFrames, kymoBinCol_Ms, xlsExportOptions);
	}

	public void getSpotsArrayResults1(CagesArray cagesArray, int nOutputFrames, long kymoBinCol_Ms,
			XLSExportOptions xlsExportOptions) {
		buildSpotsDataForPass1(cagesArray, nOutputFrames, kymoBinCol_Ms, xlsExportOptions);
	}

	private void buildSpotsDataForPass1(CagesArray cagesArray, int nOutputFrames, long kymoBinCol_Ms,
			XLSExportOptions xlsExportOptions) {
		for (Cage cage : cagesArray.cagesList) {
			double scalingFactorToPhysicalUnits = cage.spotsArray
					.getScalingFactorToPhysicalUnits(xlsExportOptions.exportType);
			for (Spot spot : cage.spotsArray.getSpotsList()) {
				XLSResults results = new XLSResults(cage.getProperties(), spot.getProperties(), nOutputFrames);
				results.setDataValues((ArrayList<Double>) spot.getMeasuresForExcelPass1(xlsExportOptions.exportType,
						kymoBinCol_Ms, xlsExportOptions.buildExcelStepMs));
				if (xlsExportOptions.relativeToT0 && xlsExportOptions.exportType != EnumXLSExport.AREA_FLYPRESENT)
					results.relativeToMaximum(); // relativeToT0();
				results.transferDataValuesToValuesOut(scalingFactorToPhysicalUnits, xlsExportOptions.exportType);
				resultsList.add(results);
			}
		}
	}

}
