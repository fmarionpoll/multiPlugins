package plugins.fmp.multiSPOTS96.tools.toExcel;

import java.awt.Point;

import org.apache.poi.xssf.streaming.SXSSFSheet;

import plugins.fmp.multiSPOTS96.experiment.Experiment;
import plugins.fmp.multiSPOTS96.experiment.cages.Cage;
import plugins.fmp.multiSPOTS96.experiment.sequence.ImageLoader;
import plugins.fmp.multiSPOTS96.experiment.sequence.TimeManager;
import plugins.fmp.multiSPOTS96.experiment.spots.Spot;
import plugins.fmp.multiSPOTS96.tools.toExcel.exceptions.ExcelExportException;
import plugins.fmp.multiSPOTS96.tools.toExcel.exceptions.ExcelResourceException;

/**
 * Excel export implementation for spot measurements. Uses the Template Method
 * pattern for structured export operations.
 */
public class XLSExportMeasuresFromSpot extends XLSExport {

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
	protected int exportExperimentData(Experiment exp, XLSExportOptions xlsExportOptions, int startColumn,
			String charSeries) throws ExcelExportException {
		int column = startColumn;

		if (options.spotAreas) {
			column = getSpotDataAndExport(exp, column, charSeries, EnumXLSExport.AREA_SUM);
			getSpotDataAndExport(exp, column, charSeries, EnumXLSExport.AREA_FLYPRESENT);
			getSpotDataAndExport(exp, column, charSeries, EnumXLSExport.AREA_SUMCLEAN);
		}

		return column;
	}

	/**
	 * Exports spot data for a specific export type.
	 * 
	 * @param exp        The experiment to export
	 * @param col0       The starting column
	 * @param charSeries The series identifier
	 * @param exportType The export type
	 * @return The next available column
	 * @throws ExcelExportException If export fails
	 */
	protected int getSpotDataAndExport(Experiment exp, int col0, String charSeries, EnumXLSExport exportType)
			throws ExcelExportException {
		try {
			options.exportType = exportType;
			SXSSFSheet sheet = getSheet(exportType.toString(), exportType);
			int colmax = xlsExportExperimentSpotDataToSheet(exp, sheet, exportType, col0, charSeries);

			if (options.onlyalive) {
				sheet = getSheet(exportType.toString() + ExcelExportConstants.ALIVE_SHEET_SUFFIX, exportType);
				xlsExportExperimentSpotDataToSheet(exp, sheet, exportType, col0, charSeries);
			}

			return colmax;
		} catch (ExcelResourceException e) {
			throw new ExcelExportException("Failed to export spot data", "get_spot_data_and_export",
					exportType.toString(), e);
		}
	}

	/**
	 * Exports spot data to a specific sheet.
	 * 
	 * @param exp           The experiment to export
	 * @param sheet         The sheet to write to
	 * @param xlsExportType The export type
	 * @param col0          The starting column
	 * @param charSeries    The series identifier
	 * @return The next available column
	 */
	protected int xlsExportExperimentSpotDataToSheet(Experiment exp, SXSSFSheet sheet, EnumXLSExport xlsExportType,
			int col0, String charSeries) {
		Point pt = new Point(col0, 0);
		pt = writeExperimentSeparator(sheet, pt);

		for (Cage cage : exp.cagesArray.cagesList) {
			double scalingFactorToPhysicalUnits = cage.spotsArray.getScalingFactorToPhysicalUnits(xlsExportType);
			cage.updateSpotsStimulus_i();

			for (Spot spot : cage.spotsArray.getSpotsList()) {
				pt.y = 0;
				pt = writeExperimentSpotInfos(sheet, pt, exp, charSeries, cage, spot, xlsExportType);
				XLSResults xlsResults = getXLSResultsDataValuesFromSpotMeasures(exp, cage, spot, options);
				xlsResults.transferDataValuesToValuesOut(scalingFactorToPhysicalUnits, xlsExportType);
				writeXLSResult(sheet, pt, xlsResults);
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
	public XLSResults getXLSResultsDataValuesFromSpotMeasures(Experiment exp, Cage cage, Spot spot, XLSExportOptions xlsExportOptions) {
		/*
		 * 1) get n input frames for signal between timefirst and time last; locate
		 * binfirst and bin last in the array of long in seqcamdata 2) given excelBinms,
		 * calculate n output bins
		 */
		int nOutputFrames = getNOutputFrames(exp, xlsExportOptions);

		XLSResults xlsResults = new XLSResults(cage.getProperties(), spot.getProperties(), nOutputFrames);

		long binData = exp.seqCamData.getTimeManager().getBinDurationMs();
		long binExcel = xlsExportOptions.buildExcelStepMs;
		xlsResults.getDataFromSpot(spot, binData, binExcel, xlsExportOptions);
		return xlsResults;
	}

	/**
	 * Gets the number of output frames for the experiment.
	 * 
	 * @param exp The experiment
	 * @return The number of output frames
	 */
	protected int getNOutputFrames(Experiment exp, XLSExportOptions options) {
		TimeManager timeManager = exp.seqCamData.getTimeManager();
		ImageLoader imgLoader = exp.seqCamData.getImageLoader();
		long durationMs = timeManager.getBinLast_ms() - timeManager.getBinFirst_ms();
		int nOutputFrames = (int) (durationMs / options.buildExcelStepMs + 1);

		if (nOutputFrames <= 1) {
			long binLastMs = timeManager.getBinFirst_ms()
					+ imgLoader.getNTotalFrames() * timeManager.getBinDurationMs();
			timeManager.setBinLast_ms(binLastMs);

			if (binLastMs <= 0) {
				handleExportError(exp, -1);
			}

			nOutputFrames = (int) ((binLastMs - timeManager.getBinFirst_ms()) / options.buildExcelStepMs + 1);

			if (nOutputFrames <= 1) {
				nOutputFrames = imgLoader.getNTotalFrames();
				handleExportError(exp, nOutputFrames);
			}
		}

		return nOutputFrames;
	}

}
