package plugins.fmp.multiSPOTS.tools.toExcel;

import java.util.ArrayList;
import java.util.Arrays;

import plugins.fmp.multiSPOTS.experiment.spots.Spot;
import plugins.fmp.multiSPOTS.experiment.spots.SpotsArray;

public class XLSResultsArray {
	ArrayList<XLSResults> resultsList = null;
	XLSResults evapL = null;
	XLSResults evapR = null;
	boolean sameLR = true;
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

	public XLSResults getNextRow(int irow) {
		XLSResults rowL = resultsList.get(irow);
		int cageL = getCageFromKymoFileName(rowL.name);
		XLSResults rowR = null;
		if (irow + 1 < resultsList.size()) {
			rowR = resultsList.get(irow + 1);
			int cageR = getCageFromKymoFileName(rowR.name);
			if (cageR != cageL)
				rowR = null;
		}
		return rowR;
	}

	protected int getCageFromKymoFileName(String name) {
		if (!name.contains("line"))
			return -1;
		return Integer.valueOf(name.substring(4, 5));
	}

	public void checkIfSameStimulusAndConcentration(Spot spot) {
		if (!sameLR)
			return;
		if (stim == null)
			stim = spot.spotStim;
		if (conc == null)
			conc = spot.spotConc;
		sameLR &= stim.equals(spot.spotStim);
		sameLR &= conc.equals(spot.spotConc);
	}

	public void subtractEvaporation() {
		int dimension = 0;
		for (XLSResults result : resultsList) {
			if (result.valuesOut == null)
				continue;
			if (result.valuesOut.length > dimension)
				dimension = result.valuesOut.length;
		}
		if (dimension == 0)
			return;

		computeEvaporationFromResultsWithZeroFlies(dimension);
		subtractEvaporationLocal();
	}

	private void computeEvaporationFromResultsWithZeroFlies(int dimension) {
		evapL = new XLSResults("L", 0, 0, 0, null);
		evapR = new XLSResults("R", 0, 0, 0, null);
		evapL.initValuesOutArray(dimension, 0.);
		evapR.initValuesOutArray(dimension, 0.);

		for (XLSResults result : resultsList) {
			if (result.valuesOut == null || result.nflies != 0)
				continue;
			String side = result.name.substring(result.name.length() - 1);
			if (sameLR || side.contains("L"))
				evapL.addDataToValOutEvap(result);
			else
				evapR.addDataToValOutEvap(result);
		}
		evapL.averageEvaporation();
		evapR.averageEvaporation();
	}

	private void subtractEvaporationLocal() {
		for (XLSResults result : resultsList) {
			String side = result.name.substring(result.name.length() - 1);
			if (sameLR || side.contains("L"))
				result.subtractEvap(evapL);
			else
				result.subtractEvap(evapR);
		}
	}

	public void subtractDeltaT(int i, int j) {
		for (XLSResults row : resultsList)
			row.subtractDeltaT(1, 1); // options.buildExcelStepMs);
	}

	private int getLen(XLSResults rowL, XLSResults rowR) {
		int lenL = rowL.valuesOut.length;
		int lenR = rowR.valuesOut.length;
		return Math.min(lenL, lenR);
	}

	public void getPI_LR(XLSResults rowL, XLSResults rowR, double threshold) {
		int len = getLen(rowL, rowR);
		for (int index = 0; index < len; index++) {
			double dataL = rowL.valuesOut[index];
			double dataR = rowR.valuesOut[index];
			double delta = 0.;
			if (dataL < 0)
				delta = dataL;
			if (dataR < delta)
				delta = dataR;
			dataL -= delta;
			dataR -= delta;
			double sum = dataL + dataR;
			double pi = 0.;
			if (sum != 0. && !Double.isNaN(sum) && sum >= threshold)
				pi = (dataL - dataR) / sum;
			if (pi > highestPiAllowed)
				pi = highestPiAllowed;
			if (pi < lowestPiAllowed)
				pi = lowestPiAllowed;
			rowL.valuesOut[index] = sum;
			rowR.valuesOut[index] = pi;
		}
	}

	// ---------------------------------------------------

	public void getSpotsArrayResults_T0(SpotsArray spotsArray, EnumXLSExportType exportType, int nOutputFrames,
			long kymoBinCol_Ms, XLSExportOptions xlsExportOptions) {
		xlsExportOptions.exportType = exportType;
		buildSpotsDataForPass1(spotsArray, nOutputFrames, kymoBinCol_Ms, xlsExportOptions);
		buildDataForPass2(xlsExportOptions);
	}

	public void getSpotsArrayResults1(SpotsArray spotsArray, int nOutputFrames, long kymoBinCol_Ms,
			XLSExportOptions xlsExportOptions) {
		buildSpotsDataForPass1(spotsArray, nOutputFrames, kymoBinCol_Ms, xlsExportOptions);
		buildDataForPass2(xlsExportOptions);
	}

	private void buildSpotsDataForPass1(SpotsArray spotsArray, int nOutputFrames, long kymoBinCol_Ms,
			XLSExportOptions xlsExportOptions) {
		double scalingFactorToPhysicalUnits = spotsArray.getScalingFactorToPhysicalUnits(xlsExportOptions.exportType);
		for (Spot spot : spotsArray.spotsList) {
			checkIfSameStimulusAndConcentration(spot);
			XLSResults results = new XLSResults(spot.getRoi().getName(), spot.spotNFlies, spot.cageID,
					spot.cagePosition, xlsExportOptions.exportType, nOutputFrames);

			results.dataValues = spot.getSpotMeasuresForXLSPass1(xlsExportOptions.exportType, kymoBinCol_Ms,
					xlsExportOptions.buildExcelStepMs);
			if (xlsExportOptions.relativeToT0 && xlsExportOptions.exportType != EnumXLSExportType.AREA_FLYPRESENT)
				results.relativeToT0();
//			if (xlsExportOptions.relativeToMedianT0 && xlsExportOptions.exportType != EnumXLSExportType.AREA_FLYPRESENT)
//				results.relativeToMedianT(0, xlsExportOptions.medianT0FromNPoints);
			results.transferMeasuresToValuesOut(scalingFactorToPhysicalUnits, xlsExportOptions.exportType);
			resultsList.add(results);
		}
	}

	public void buildDataForPass2(XLSExportOptions xlsExportOptions) {
		switch (xlsExportOptions.exportType) {
		case TOPLEVEL_LR:
		case TOPLEVELDELTA_LR:
		case AREA_SUM_LR:
		case AREA_SUMCLEAN_LR:
			buildLR(xlsExportOptions.lrPIThreshold);
			break;
		case AUTOCORREL:
			buildAutocorrel(xlsExportOptions);
			break;
		case AUTOCORREL_LR:
			buildAutocorrelLR(xlsExportOptions);
			break;
		case CROSSCORREL:
			buildCrosscorrel(xlsExportOptions);
			break;
		case CROSSCORREL_LR:
			buildCrosscorrelLR(xlsExportOptions);
			break;
		default:
			break;
		}
	}

	private void buildLR(double threshold) {
		for (int irow = 0; irow < resultsList.size(); irow++) {
			XLSResults rowL = getRow(irow);
			XLSResults rowR = getNextRow(irow);
			if (rowR != null) {
				irow++;
				getPI_LR(rowL, rowR, threshold);
			}
		}
	}

	private void buildAutocorrel(XLSExportOptions xlsExportOptions) {
		for (int irow = 0; irow < resultsList.size(); irow++) {
			XLSResults rowL = getRow(irow);
			correl(rowL, rowL, rowL, xlsExportOptions.nBinsCorrelation);
		}
	}

	private void buildCrosscorrel(XLSExportOptions xlsExportOptions) {
		for (int irow = 0; irow < resultsList.size(); irow++) {
			XLSResults rowL = getRow(irow);
			XLSResults rowR = getNextRow(irow);
			if (rowR != null) {
				irow++;
				XLSResults rowLtoR = new XLSResults("LtoR", 0, 0, 0, null);
				rowLtoR.initValuesOutArray(rowL.dimension, 0.);
				correl(rowL, rowR, rowLtoR, xlsExportOptions.nBinsCorrelation);
				XLSResults rowRtoL = new XLSResults("RtoL", 0, 0, 0, null);
				rowRtoL.initValuesOutArray(rowL.dimension, 0.);
				correl(rowR, rowL, rowRtoL, xlsExportOptions.nBinsCorrelation);
				rowL.copyValuesOut(rowLtoR);
				rowR.copyValuesOut(rowRtoL);
			}
		}
	}

	private void buildCrosscorrelLR(XLSExportOptions xlsExportOptions) {
		for (int irow = 0; irow < resultsList.size(); irow++) {
			XLSResults rowL = getRow(irow);
			XLSResults rowR = getNextRow(irow);
			if (rowR != null) {
				irow++;
				XLSResults rowLR = new XLSResults("LR", 0, 0, 0, null);
				rowLR.initValuesOutArray(rowL.dimension, 0.);
				combineIntervals(rowL, rowR, rowLR);
				correl(rowL, rowLR, rowL, xlsExportOptions.nBinsCorrelation);
				correl(rowR, rowLR, rowR, xlsExportOptions.nBinsCorrelation);
			}
		}
	}

	private void correl(XLSResults row1, XLSResults row2, XLSResults rowOut, int nbins) {
		double[] sumBins = new double[2 * nbins + 1];
		Arrays.fill(sumBins, 0);
		double nitems = 0;
		for (int i1 = 0; i1 < row1.valuesOut.length; i1++) {
			if (row1.valuesOut[i1] == 0.)
				continue;
			nitems++;
			for (int i2 = 0; i2 < row2.valuesOut.length; i2++) {
				int ibin = i2 - i1;
				if (ibin < -nbins || ibin > nbins)
					continue;
				if (row2.valuesOut[i2] != 0.)
					sumBins[ibin + nbins]++;
			}
		}
		Arrays.fill(rowOut.valuesOut, Double.NaN);
		for (int i = 0; i < 2 * nbins; i++)
			rowOut.valuesOut[i] = sumBins[i] / nitems;
	}

	private void combineIntervals(XLSResults row1, XLSResults row2, XLSResults rowOut) {
		for (int i = 0; i < rowOut.valuesOut.length; i++) {
			if ((row2.valuesOut[i] + row1.valuesOut[i]) > 0.)
				rowOut.valuesOut[i] = 1.;
		}
	}

	private void buildAutocorrelLR(XLSExportOptions xlsExportOptions) {
		for (int irow = 0; irow < resultsList.size(); irow++) {
			XLSResults rowL = getRow(irow);
			XLSResults rowR = getNextRow(irow);
			if (rowR != null) {
				irow++;
				XLSResults rowLR = new XLSResults("LR", 0, 0, 0, null);
				rowLR.initValuesOutArray(rowL.dimension, 0.);
				combineIntervals(rowL, rowR, rowLR);
				correl(rowLR, rowLR, rowL, xlsExportOptions.nBinsCorrelation);
				correl(rowLR, rowLR, rowR, xlsExportOptions.nBinsCorrelation);
			}
		}
	}
}
