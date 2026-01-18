package plugins.fmp.multitools.tools.toExcel;

import java.awt.Point;

import org.apache.poi.xssf.streaming.SXSSFSheet;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ExperimentProperties;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.FlyPositions;
import plugins.fmp.multitools.experiment.sequence.ImageLoader;
import plugins.fmp.multitools.experiment.sequence.TimeManager;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.Results;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.toExcel.config.ExcelExportConstants;
import plugins.fmp.multitools.tools.toExcel.enums.EnumXLSColumnHeader;
import plugins.fmp.multitools.tools.toExcel.exceptions.ExcelExportException;
import plugins.fmp.multitools.tools.toExcel.exceptions.ExcelResourceException;
import plugins.fmp.multitools.tools.toExcel.utils.XLSUtils;

/**
 * Excel export implementation for fly position measurements. Uses the Template
 * Method pattern for structured export operations, following the same pattern
 * as spot and capillary exports.
 * 
 * <p>
 * This class exports fly position data including:
 * <ul>
 * <li>XYIMAGE - XY coordinates in image space</li>
 * <li>XYTOPCAGE - XY coordinates relative to top of cage</li>
 * <li>XYTIPCAPS - XY coordinates relative to tip of capillaries</li>
 * <li>ELLIPSEAXES - Ellipse axes (major and minor)</li>
 * <li>DISTANCE - Distance between consecutive points</li>
 * <li>ISALIVE - Fly alive status (1=alive, 0=dead)</li>
 * <li>SLEEP - Fly sleep status (1=sleeping, 0=awake)</li>
 * </ul>
 * </p>
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 */
public class XLSExportMeasuresFromFlyPosition extends XLSExport {

	/**
	 * Exports fly position data for a single experiment.
	 * 
	 * @param exp         The experiment to export
	 * @param startColumn The starting column for export
	 * @param charSeries  The series identifier
	 * @return The next available column
	 * @throws ExcelExportException If export fails
	 */
	@Override
	protected int exportExperimentData(Experiment exp, ResultsOptions resultsOptions, int startColumn,
			String charSeries) throws ExcelExportException {
		int column = startColumn;

		if (options.xyImage) {
			column = getFlyPositionDataAndExport(exp, column, charSeries, EnumResults.XYIMAGE);
		}
		if (options.xyCage) {
			getFlyPositionDataAndExport(exp, column, charSeries, EnumResults.XYTOPCAGE);
		}
		if (options.xyCapillaries) {
			getFlyPositionDataAndExport(exp, column, charSeries, EnumResults.XYTIPCAPS);
		}
		if (options.ellipseAxes) {
			getFlyPositionDataAndExport(exp, column, charSeries, EnumResults.ELLIPSEAXES);
		}
		if (options.distance) {
			getFlyPositionDataAndExport(exp, column, charSeries, EnumResults.DISTANCE);
		}
		if (options.alive) {
			getFlyPositionDataAndExport(exp, column, charSeries, EnumResults.ISALIVE);
		}
		if (options.sleep) {
			getFlyPositionDataAndExport(exp, column, charSeries, EnumResults.SLEEP);
		}

		return column;
	}

	/**
	 * Exports fly position data for a specific export type.
	 * 
	 * @param exp        The experiment to export
	 * @param col0       The starting column
	 * @param charSeries The series identifier
	 * @param resultType The export type
	 * @return The next available column
	 * @throws ExcelExportException If export fails
	 */
	protected int getFlyPositionDataAndExport(Experiment exp, int col0, String charSeries, EnumResults resultType)
			throws ExcelExportException {
		try {
			options.resultType = resultType;
			SXSSFSheet sheet = getSheet(resultType.toString(), resultType);
			int colmax = xlsExportExperimentFlyPositionDataToSheet(exp, sheet, resultType, col0, charSeries);

			if (options.onlyalive) {
				sheet = getSheet(resultType.toString() + ExcelExportConstants.ALIVE_SHEET_SUFFIX, resultType);
				xlsExportExperimentFlyPositionDataToSheet(exp, sheet, resultType, col0, charSeries);
			}

			return colmax;
		} catch (ExcelResourceException e) {
			throw new ExcelExportException("Failed to export fly position data", "get_fly_position_data_and_export",
					resultType.toString(), e);
		}
	}

	/**
	 * Exports fly position data to a specific sheet.
	 * 
	 * @param exp        The experiment to export
	 * @param sheet      The sheet to write to
	 * @param resultType The export type
	 * @param col0       The starting column
	 * @param charSeries The series identifier
	 * @return The next available column
	 */
	protected int xlsExportExperimentFlyPositionDataToSheet(Experiment exp, SXSSFSheet sheet, EnumResults resultType,
			int col0, String charSeries) {
		Point pt = new Point(col0, 0);
		pt = writeExperimentSeparator(sheet, pt);

		// For fly positions, scaling is typically 1.0 (already in physical units)
		double scalingFactorToPhysicalUnits = 1.0;

		for (Cage cage : exp.getCages().cagesList) {
			FlyPositions flyPositions = cage.flyPositions;
			if (flyPositions == null || flyPositions.flyPositionList == null
					|| flyPositions.flyPositionList.isEmpty()) {
				continue;
			}

			pt.y = 0;
			pt = writeExperimentFlyPositionInfos(sheet, pt, exp, charSeries, cage, resultType);
			Results results = getResultsDataValuesFromFlyPositionMeasures(exp, cage, flyPositions, options);
			results.transferDataValuesToValuesOut(scalingFactorToPhysicalUnits, resultType);
			writeXLSResult(sheet, pt, results);
			pt.x++;
		}
		return pt.x;
	}

	/**
	 * Gets the results for fly positions.
	 * 
	 * @param exp            The experiment
	 * @param cage           The cage
	 * @param flyPositions   The fly positions
	 * @param resultsOptions The export options
	 * @return The XLS results
	 */
	public Results getResultsDataValuesFromFlyPositionMeasures(Experiment exp, Cage cage, FlyPositions flyPositions,
			ResultsOptions resultsOptions) {
		int nOutputFrames = getNOutputFrames(exp, resultsOptions);

		// Create XLSResults with cage properties
		Results results = new Results("Cage_" + cage.getProperties().getCageID(), cage.getProperties().getCageNFlies(),
				cage.getProperties().getCageID(), 0, resultsOptions.resultType);
		results.initValuesOutArray(nOutputFrames, Double.NaN);

		// Get bin durations
		long binData = exp.getSeqCamData().getTimeManager().getBinDurationMs();
		long binExcel = resultsOptions.buildExcelStepMs;

		// Get data from fly positions
		results.getDataFromFlyPositions(flyPositions, binData, binExcel, resultsOptions);

		return results;
	}

	/**
	 * Gets the number of output frames for the experiment.
	 * 
	 * @param exp            The experiment
	 * @param resultsOptions The export options
	 * @return The number of output frames
	 */
	protected int getNOutputFrames(Experiment exp, ResultsOptions resultsOptions) {
		// For fly positions, use camera sequence timing
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

	/**
	 * Writes experiment fly position information to the sheet.
	 * 
	 * @param sheet      The sheet to write to
	 * @param pt         The starting point
	 * @param exp        The experiment
	 * @param charSeries The series identifier
	 * @param cage       The cage
	 * @param resultType The export type
	 * @return The updated point
	 */
	protected Point writeExperimentFlyPositionInfos(SXSSFSheet sheet, Point pt, Experiment exp, String charSeries,
			Cage cage, EnumResults resultType) {
		int x = pt.x;
		int y = pt.y;
		boolean transpose = options.transpose;

		// Write basic file information
		writeFileInformationForFlyPosition(sheet, x, y, transpose, exp);

		// Write experiment properties
		writeExperimentPropertiesForFlyPosition(sheet, x, y, transpose, exp, charSeries);

		// Write cage properties
		writeCagePropertiesForFlyPosition(sheet, x, y, transpose, cage, charSeries);

		pt.y = y + getDescriptorRowCount();
		return pt;
	}

	/**
	 * Writes basic file information to the sheet (for fly positions).
	 */
	private void writeFileInformationForFlyPosition(SXSSFSheet sheet, int x, int y, boolean transpose, Experiment exp) {
		String filename = exp.getResultsDirectory();
		if (filename == null) {
			filename = exp.getSeqCamData().getImagesDirectory();
		}

		java.nio.file.Path path = java.nio.file.Paths.get(filename);
		java.text.SimpleDateFormat df = new java.text.SimpleDateFormat(ExcelExportConstants.DEFAULT_DATE_FORMAT);
		String date = df.format(exp.chainImageFirst_ms);
		String name0 = path.toString();
		String cam = extractCameraInfo(name0);

		XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.PATH.getValue(), transpose, name0);
		XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.DATE.getValue(), transpose, date);
		XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.CAM.getValue(), transpose, cam);
	}

	/**
	 * Writes experiment properties to the sheet (for fly positions).
	 */
	private void writeExperimentPropertiesForFlyPosition(SXSSFSheet sheet, int x, int y, boolean transpose,
			Experiment exp, String charSeries) {
		ExperimentProperties props = exp.getProperties();

		XLSUtils.setFieldValue(sheet, x, y, transpose, props, EnumXLSColumnHeader.EXP_BOXID);
		XLSUtils.setFieldValue(sheet, x, y, transpose, props, EnumXLSColumnHeader.EXP_EXPT);
		XLSUtils.setFieldValue(sheet, x, y, transpose, props, EnumXLSColumnHeader.EXP_STIM1);
		XLSUtils.setFieldValue(sheet, x, y, transpose, props, EnumXLSColumnHeader.EXP_CONC1);
		XLSUtils.setFieldValue(sheet, x, y, transpose, props, EnumXLSColumnHeader.EXP_STRAIN);
		XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.EXP_BOXID.getValue(), transpose, charSeries);
		XLSUtils.setFieldValue(sheet, x, y, transpose, props, EnumXLSColumnHeader.EXP_SEX);
		XLSUtils.setFieldValue(sheet, x, y, transpose, props, EnumXLSColumnHeader.EXP_STIM2);
		XLSUtils.setFieldValue(sheet, x, y, transpose, props, EnumXLSColumnHeader.EXP_CONC2);
	}

	/**
	 * Writes cage properties to the sheet (for fly positions).
	 */
	private void writeCagePropertiesForFlyPosition(SXSSFSheet sheet, int x, int y, boolean transpose, Cage cage,
			String charSeries) {
		XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.CAGEID.getValue(), transpose,
				charSeries + cage.getProperties().getCageID());
		XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.CAGE_STRAIN.getValue(), transpose,
				cage.getProperties().getFlyStrain());
		XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.CAGE_SEX.getValue(), transpose,
				cage.getProperties().getFlySex());
		XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.CAGE_AGE.getValue(), transpose,
				cage.getProperties().getFlyAge());
		XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.SPOT_NFLIES.getValue(), transpose,
				cage.getProperties().getCageNFlies());
		XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.CAGE_COMMENT.getValue(), transpose,
				cage.getProperties().getComment());
	}

	/**
	 * Handles export errors by logging them.
	 * 
	 * @param exp           The experiment
	 * @param nOutputFrames The number of output frames
	 */
	protected void handleExportError(Experiment exp, int nOutputFrames) {
		String error = String.format(
				"XLSExport:ExportError() ERROR in %s\n nOutputFrames=%d binFirst_Ms=%d binLast_Ms=%d",
				exp.getExperimentDirectory(), nOutputFrames, exp.getSeqCamData().getTimeManager().getBinFirst_ms(),
				exp.getSeqCamData().getTimeManager().getBinLast_ms());
		System.err.println(error);
	}
}
