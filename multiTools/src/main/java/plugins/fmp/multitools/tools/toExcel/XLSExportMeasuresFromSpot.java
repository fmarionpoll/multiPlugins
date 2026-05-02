package plugins.fmp.multitools.tools.toExcel;

import java.awt.Point;
import java.util.ArrayList;

import org.apache.poi.xssf.streaming.SXSSFSheet;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.Results;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.toExcel.exceptions.ExcelExportException;
import plugins.fmp.multitools.tools.toExcel.utils.SpotExcelTimeline;

/**
 * Excel export implementation for spot measurements. Uses the Template Method
 * pattern for structured export operations.
 */
public class XLSExportMeasuresFromSpot extends XLSExportSpots {

	/**
	 * Exports spot data for a single experiment.
	 * 
	 * @param exp         The experiment to export
	 * @param startColumn The starting column for export
	 * @param charSeries  The series identifier
	 * @return The next available column
	 * @throws ExcelExportException If export fails
	 **/
	@Override
	protected int exportExperimentData(Experiment exp, ResultsOptions resultsOptions, int startColumn,
			String charSeries) throws ExcelExportException {

		if (!hasSpotMeasuresSelectedForExport(resultsOptions)) {
			return startColumn;
		}

		int colmax = 0;
		for (EnumResults resultType : enabledSpotMeasureTypesForExport(resultsOptions)) {
			int col = exportResultType(exp, startColumn, charSeries, resultType, "spot");
			if (col > colmax) {
				colmax = col;
			}
		}

		return colmax;
	}

	@Override
	protected int exportResultTypeToSheet(Experiment exp, SXSSFSheet sheet, EnumResults resultType, int col0,
			String charSeries) {
		Point pt = new Point(col0, 0);
		pt = writeExperimentSeparator(sheet, pt);

		SpotExcelTimeline.SpotExcelGrid spotGrid = SpotExcelTimeline.buildForSpotExport(exp, options);

		Spots allSpots = exp.getSpots();
		for (Cage cage : exp.getCages().cagesList) {
			double scalingFactorToPhysicalUnits = allSpots.getScalingFactorToPhysicalUnits(resultType);
			cage.updateSpotsStimulus_i(allSpots);

			for (Spot spot : cage.getSpotList(allSpots)) {
				pt.y = 0;
				pt = writeExperimentSpotInfos(sheet, pt, exp, charSeries, cage, spot, resultType);
				Results results = getResultsDataValuesFromSpotMeasures(cage, spot, spotGrid, options);
				results.transferDataValuesToValuesOut(scalingFactorToPhysicalUnits, resultType);
				writeXLSResult(sheet, pt, results);
				pt.x++;
			}
		}
		return pt.x;
	}

	/**
	 * Gets the results for a spot.
	 * 
	 * @param exp           The experiment
	 * @param cage          The cage
	 * @param spot          The spot
	 * @param xlsExportType The export type
	 * @return The XLS results
	 */
	public Results getResultsDataValuesFromSpotMeasures(Cage cage, Spot spot,
			SpotExcelTimeline.SpotExcelGrid grid, ResultsOptions xlsExportOptions) {
		Results results = new Results(cage.getProperties(), spot.getProperties(), 1);
		results.getDataFromSpot(spot, grid, xlsExportOptions);
		ArrayList<Double> dv = results.getDataValues();
		int nOut = dv != null && !dv.isEmpty() ? dv.size() : 1;
		results.initValuesOutArray(nOut, Double.NaN);
		return results;
	}

}
