package plugins.fmp.multitools.tools.toExcel;

import java.awt.Point;

import org.apache.poi.xssf.streaming.SXSSFSheet;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cages.Cage;
import plugins.fmp.multitools.experiment.sequence.ImageLoader;
import plugins.fmp.multitools.experiment.sequence.TimeManager;
import plugins.fmp.multitools.experiment.spots.Spot;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.Results;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.toExcel.config.ExcelExportConstants;
import plugins.fmp.multitools.tools.toExcel.exceptions.ExcelExportException;
import plugins.fmp.multitools.tools.toExcel.exceptions.ExcelResourceException;

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
		int column = startColumn;

		if (options.spotAreas) {
			column = getSpotDataAndExport(exp, column, charSeries, EnumResults.AREA_SUM);
			getSpotDataAndExport(exp, column, charSeries, EnumResults.AREA_FLYPRESENT);
			getSpotDataAndExport(exp, column, charSeries, EnumResults.AREA_SUMCLEAN);
		}

		return column;
	}

	/**
	 * Exports spot data for a specific export type.
	 * 
	 * @param exp        The experiment to export
	 * @param col0       The starting column
	 * @param charSeries The series identifier
	 * @param resultType The export type
	 * @return The next available column
	 * @throws ExcelExportException If export fails
	 */
	protected int getSpotDataAndExport(Experiment exp, int col0, String charSeries, EnumResults resultType)
			throws ExcelExportException {
		try {
			options.resultType = resultType;
			SXSSFSheet sheet = getSheet(resultType.toString(), resultType);
			int colmax = xlsExportExperimentSpotDataToSheet(exp, sheet, resultType, col0, charSeries);

			if (options.onlyalive) {
				sheet = getSheet(resultType.toString() + ExcelExportConstants.ALIVE_SHEET_SUFFIX, resultType);
				xlsExportExperimentSpotDataToSheet(exp, sheet, resultType, col0, charSeries);
			}

			return colmax;
		} catch (ExcelResourceException e) {
			throw new ExcelExportException("Failed to export spot data", "get_spot_data_and_export",
					resultType.toString(), e);
		}
	}

	/**
	 * Exports spot data to a specific sheet.
	 * 
	 * @param exp           The experiment to export
	 * @param sheet         The sheet to write to
	 * @param resultType The export type
	 * @param col0          The starting column
	 * @param charSeries    The series identifier
	 * @return The next available column
	 */
	protected int xlsExportExperimentSpotDataToSheet(Experiment exp, SXSSFSheet sheet, EnumResults resultType,
			int col0, String charSeries) {
		Point pt = new Point(col0, 0);
		pt = writeExperimentSeparator(sheet, pt);

		Spots allSpots = exp.getSpots();
		for (Cage cage : exp.getCages().cagesList) {
			double scalingFactorToPhysicalUnits = allSpots.getScalingFactorToPhysicalUnits(resultType);
			cage.updateSpotsStimulus_i(allSpots);

			for (Spot spot : cage.getSpotList(allSpots)) {
				pt.y = 0;
				pt = writeExperimentSpotInfos(sheet, pt, exp, charSeries, cage, spot, resultType);
				Results results = getResultsDataValuesFromSpotMeasures(exp, cage, spot, options);
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
	public Results getResultsDataValuesFromSpotMeasures(Experiment exp, Cage cage, Spot spot,
			ResultsOptions xlsExportOptions) {
		/*
		 * 1) get n input frames for signal between timefirst and time last; locate
		 * binfirst and bin last in the array of long in seqcamdata 2) given excelBinms,
		 * calculate n output bins
		 */
		int nOutputFrames = getNOutputFrames(exp, xlsExportOptions);

		Results results = new Results(cage.getProperties(), spot.getProperties(), nOutputFrames);

		long binData = exp.getSeqCamData().getTimeManager().getBinDurationMs();
		long binExcel = xlsExportOptions.buildExcelStepMs;
		results.getDataFromSpot(spot, binData, binExcel, xlsExportOptions);
		return results;
	}

	/**
	 * Gets the number of output frames for the experiment.
	 * 
	 * @param exp The experiment
	 * @return The number of output frames
	 */
	protected int getNOutputFrames(Experiment exp, ResultsOptions resultsOptions) {
		TimeManager timeManager = exp.getSeqCamData().getTimeManager();
		ImageLoader imgLoader = exp.getSeqCamData().getImageLoader();
		long durationMs = timeManager.getBinLast_ms() - timeManager.getBinFirst_ms();
		int nOutputFrames = (int) (durationMs / resultsOptions.buildExcelStepMs + 1);

		if (nOutputFrames <= 1) {
			long binLastMs = timeManager.getBinFirst_ms()
					+ imgLoader.getNTotalFrames() * timeManager.getBinDurationMs();
			timeManager.setBinLast_ms(binLastMs);

			if (binLastMs <= 0) {
				handleExportError(exp, -1);
			}

			nOutputFrames = (int) ((binLastMs - timeManager.getBinFirst_ms()) / resultsOptions.buildExcelStepMs + 1);

			if (nOutputFrames <= 1) {
				nOutputFrames = imgLoader.getNTotalFrames();
				handleExportError(exp, nOutputFrames);
			}
		}

		return nOutputFrames;
	}

}
