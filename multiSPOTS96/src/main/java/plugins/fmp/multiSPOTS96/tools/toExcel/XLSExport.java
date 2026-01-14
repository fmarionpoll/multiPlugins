package plugins.fmp.multiSPOTS96.tools.toExcel;

import java.awt.Point;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import icy.gui.frame.progress.ProgressFrame;
import plugins.fmp.multiSPOTS96.experiment.Experiment;
import plugins.fmp.multiSPOTS96.experiment.ExperimentProperties;
import plugins.fmp.multiSPOTS96.experiment.cages.Cage;
import plugins.fmp.multiSPOTS96.experiment.spots.Spot;
import plugins.fmp.multiSPOTS96.tools.JComponents.JComboBoxExperimentLazy;
import plugins.fmp.multiSPOTS96.tools.toExcel.exceptions.ExcelDataException;
import plugins.fmp.multiSPOTS96.tools.toExcel.exceptions.ExcelExportException;
import plugins.fmp.multiSPOTS96.tools.toExcel.exceptions.ExcelResourceException;

/**
 * Template Method pattern base class for Excel export operations. Provides
 * common functionality and structure for all Excel export types.
 * 
 * <p>
 * This class defines the overall algorithm for Excel export while allowing
 * subclasses to customize specific steps through protected methods.
 */
public abstract class XLSExport {

	protected XLSExportOptions options = null;
	protected Experiment expAll = null;
	protected JComboBoxExperimentLazy expList = null;

	// Resource management
	protected ExcelResourceManager resourceManager = null;

	// Style references
	protected CellStyle redCellStyle = null;
	protected CellStyle blueCellStyle = null;

	/**
	 * Template method that defines the overall export algorithm. This method should
	 * not be overridden by subclasses.
	 * 
	 * @param filename The target Excel file path
	 * @param options  The export options
	 * @throws ExcelExportException If export fails
	 */
	public final void exportToFile(String filename, XLSExportOptions options) throws ExcelExportException {
		System.out.println("XLSExportBase:exportToFile() - " + ExcelExportConstants.EXPORT_START_MESSAGE);

		this.options = options;
		this.expList = options.expList;

		try (ExcelResourceManager resourceManager = new ExcelResourceManager(filename)) {
			this.resourceManager = resourceManager;
			this.redCellStyle = resourceManager.getRedCellStyle();
			this.blueCellStyle = resourceManager.getBlueCellStyle();

			// Execute method steps
			prepareExperiments();
			validateExportParameters();
			executeExport();

			// Save and close
			resourceManager.saveAndClose();

		} catch (ExcelResourceException e) {
			throw new ExcelExportException("Resource management failed during export", "export_to_file", filename, e);
		} catch (Exception e) {
			throw new ExcelExportException("Unexpected error during export", "export_to_file", filename, e);
		} finally {
			cleanup();
		}

		System.out.println("XLSExport:exportToFile() - " + ExcelExportConstants.EXPORT_FINISH_MESSAGE);
	}

	/**
	 * Prepares experiments for export by loading data and setting up chains.
	 * 
	 * @throws ExcelDataException If experiment preparation fails
	 */
	protected void prepareExperiments() throws ExcelDataException {
		try {
			expList.loadListOfMeasuresFromAllExperiments(true, options.onlyalive);
			expList.chainExperimentsUsingKymoIndexes(options.collateSeries);
			expList.setFirstImageForAllExperiments(options.collateSeries);
			expAll = expList.get_MsTime_of_StartAndEnd_AllExperiments(options);
		} catch (Exception e) {
			throw new ExcelDataException("Failed to prepare experiments for export", "prepare_experiments",
					"experiment_loading", e);
		}
	}

	/**
	 * Validates export parameters before proceeding. Subclasses can override to add
	 * specific validation.
	 * 
	 * @throws ExcelDataException If validation fails
	 */
	protected void validateExportParameters() throws ExcelDataException {
		if (options == null) {
			throw new ExcelDataException("Export options cannot be null", "validate_parameters", "options_validation");
		}

		if (expList == null) {
			throw new ExcelDataException("Experiment list cannot be null", "validate_parameters", "expList_validation");
		}

		if (options.experimentIndexFirst < 0 || options.experimentIndexLast < 0) {
			throw new ExcelDataException("Invalid experiment index range", "validate_parameters", "index_validation");
		}

		if (options.experimentIndexFirst > options.experimentIndexLast) {
			throw new ExcelDataException("First experiment index cannot be greater than last", "validate_parameters",
					"index_validation");
		}
	}

	/**
	 * Executes the export process with progress tracking.
	 * 
	 * @throws ExcelExportException If export execution fails
	 */
	protected void executeExport() throws ExcelExportException {
		int nbexpts = expList.getItemCount();
		ProgressFrame progress = new ProgressFrame(ExcelExportConstants.DEFAULT_PROGRESS_TITLE);

		try {
			progress.setLength(nbexpts);
			int column = 1;
			int iSeries = 0;

			for (int index = options.experimentIndexFirst; index <= options.experimentIndexLast; index++) {
				Experiment exp = expList.getItemAt(index);
				exp.load_MS96_spotsMeasures();
				if (shouldSkipExperiment(exp)) {
					continue;
				}
				progress.setMessage("Export experiment " + (index + 1) + " of " + nbexpts);

				String seriesIdentifier = CellReference.convertNumToColString(iSeries);
				column = exportExperimentData(exp, options, column, seriesIdentifier);

				iSeries++;
				progress.incPosition();
			}

			progress.setMessage(ExcelExportConstants.SAVE_PROGRESS_MESSAGE);

		} catch (Exception e) {
			throw new ExcelExportException("Export execution failed", "execute_export", "export_loop", e);
		} finally {
			// Ensure progress frame is properly closed
			if (progress != null) {
				progress.close();
			}
		}
	}

	/**
	 * Determines whether to skip an experiment during export. Default
	 * implementation skips chained experiments.
	 * 
	 * @param exp The experiment to check
	 * @return true if the experiment should be skipped
	 */
	protected boolean shouldSkipExperiment(Experiment exp) {
		return exp.chainToPreviousExperiment != null;
	}

	/**
	 * Exports data for a single experiment. This method must be implemented by
	 * subclasses.
	 * 
	 * @param exp         The experiment to export
	 * @param startColumn The starting column for export
	 * @param charSeries  The series identifier
	 * @return The next available column
	 * @throws ExcelExportException If export fails
	 */
	protected abstract int exportExperimentData(Experiment exp, XLSExportOptions xlsExportOptions, int startColumn,
			String charSeries) throws ExcelExportException;

	/**
	 * Cleanup method called after export completion. Subclasses can override to add
	 * specific cleanup logic.
	 */
	protected void cleanup() {
		// Default implementation does nothing
		// Subclasses can override for specific cleanup
	}

	// Common utility methods

	/**
	 * Gets a sheet from the workbook, creating it if necessary.
	 * 
	 * @param title     The sheet title
	 * @param xlsExport The export type
	 * @return The sheet instance
	 * @throws ExcelResourceException If sheet creation fails
	 */
	protected SXSSFSheet getSheet(String title, EnumXLSExport xlsExport) throws ExcelResourceException {
		SXSSFWorkbook workbook = resourceManager.getWorkbook();
		SXSSFSheet sheet = workbook.getSheet(title);

		if (sheet == null) {
			sheet = workbook.createSheet(title);
			writeTopRowDescriptors(sheet);
			writeTopRowTimeIntervals(sheet, getDescriptorRowCount(), xlsExport);
		}

		return sheet;
	}

	/**
	 * Writes the top row descriptors to the sheet.
	 * 
	 * @param sheet The sheet to write to
	 * @return The number of descriptor rows written
	 */
	protected int writeTopRowDescriptors(SXSSFSheet sheet) {
//        Point pt = new Point(0, 0);
		int nextcol = -1;

		for (EnumXLSColumnHeader header : EnumXLSColumnHeader.values()) {
			XLSUtils.setValue(sheet, 0, header.getValue(), options.transpose, header.getName());
			if (nextcol < header.getValue()) {
				nextcol = header.getValue();
			}
		}

		return nextcol + 1;
	}

	/**
	 * Writes the time interval headers to the sheet.
	 * 
	 * @param sheet     The sheet to write to
	 * @param row       The starting row
	 * @param xlsExport The export type
	 */
	protected void writeTopRowTimeIntervals(SXSSFSheet sheet, int row, EnumXLSExport xlsExport) {
		boolean transpose = options.transpose;
		Point pt = new Point(0, row);

		long duration = expAll.seqCamData.getLastImageMs() - expAll.seqCamData.getFirstImageMs();
		long interval = 0;

		while (interval < duration) {
			int i = (int) (interval / options.buildExcelUnitMs);
			XLSUtils.setValue(sheet, pt, transpose, ExcelExportConstants.TIME_COLUMN_PREFIX + i);
			pt.y++;
			interval += options.buildExcelStepMs;
		}
	}

	/**
	 * Gets the number of descriptor rows.
	 * 
	 * @return The descriptor row count
	 */
	protected int getDescriptorRowCount() {
		return EnumXLSColumnHeader.values().length;
	}

	/**
	 * Writes a separator between experiments.
	 * 
	 * @param sheet The sheet to write to
	 * @param pt    The current point
	 * @return The updated point
	 */
	protected Point writeExperimentSeparator(SXSSFSheet sheet, Point pt) {
		boolean transpose = options.transpose;
		XLSUtils.setValue(sheet, pt, transpose, ExcelExportConstants.SHEET_SEPARATOR);
		pt.x++;
		XLSUtils.setValue(sheet, pt, transpose, ExcelExportConstants.SHEET_SEPARATOR);
		pt.x++;
		return pt;
	}

	/**
	 * Writes experiment spot information to the sheet.
	 * 
	 * @param sheet         The sheet to write to
	 * @param pt            The starting point
	 * @param exp           The experiment
	 * @param charSeries    The series identifier
	 * @param cage          The cage
	 * @param spot          The spot
	 * @param xlsExportType The export type
	 * @return The updated point
	 */
	protected Point writeExperimentSpotInfos(SXSSFSheet sheet, Point pt, Experiment exp, String charSeries, Cage cage,
			Spot spot, EnumXLSExport xlsExportType) {
		int x = pt.x;
		int y = pt.y;
		boolean transpose = options.transpose;

		// Write basic file information
		writeFileInformation(sheet, x, y, transpose, exp);

		// Write experiment properties
		writeExperimentProperties(sheet, x, y, transpose, exp);

		// Write spot properties
		writeSpotProperties(sheet, x, y, transpose, spot, cage, charSeries, xlsExportType);

		// Write cage properties
		writeCageProperties(sheet, x, y, transpose, cage);

		pt.y = y + ExcelExportConstants.ColumnPositions.DUM4 + 1;
		return pt;
	}

	/**
	 * Writes basic file information to the sheet.
	 */
	private void writeFileInformation(SXSSFSheet sheet, int x, int y, boolean transpose, Experiment exp) {
		String filename = exp.getResultsDirectory();
		if (filename == null) {
			filename = exp.seqCamData.getImagesDirectory();
		}

		Path path = Paths.get(filename);
		SimpleDateFormat df = new SimpleDateFormat(ExcelExportConstants.DEFAULT_DATE_FORMAT);
		String date = df.format(exp.chainImageFirst_ms);
		String name0 = path.toString();
		String cam = extractCameraInfo(name0);

		XLSUtils.setValue(sheet, x, y + ExcelExportConstants.ColumnPositions.PATH, transpose, name0);
		XLSUtils.setValue(sheet, x, y + ExcelExportConstants.ColumnPositions.DATE, transpose, date);
		XLSUtils.setValue(sheet, x, y + ExcelExportConstants.ColumnPositions.CAM, transpose, cam);
	}

	/**
	 * Extracts camera information from the filename.
	 */
	private String extractCameraInfo(String filename) {
		int pos = filename.indexOf(ExcelExportConstants.CAMERA_IDENTIFIER);
		if (pos > 0) {
			int pos5 = pos + ExcelExportConstants.CAMERA_IDENTIFIER_LENGTH;
			if (pos5 >= filename.length()) {
				pos5 = filename.length() - 1;
			}
			return filename.substring(pos, pos5);
		}
		return ExcelExportConstants.CAMERA_DEFAULT_VALUE;
	}

	/**
	 * Writes experiment properties to the sheet.
	 */
	private void writeExperimentProperties(SXSSFSheet sheet, int x, int y, boolean transpose, Experiment exp) {
		ExperimentProperties props = exp.getProperties();

		XLSUtils.setFieldValue(sheet, x, y, transpose, props, EnumXLSColumnHeader.EXP_BOXID);
		XLSUtils.setFieldValue(sheet, x, y, transpose, props, EnumXLSColumnHeader.EXP_EXPT);
		XLSUtils.setFieldValue(sheet, x, y, transpose, props, EnumXLSColumnHeader.EXP_STIM1);
		XLSUtils.setFieldValue(sheet, x, y, transpose, props, EnumXLSColumnHeader.EXP_CONC1);
		XLSUtils.setFieldValue(sheet, x, y, transpose, props, EnumXLSColumnHeader.EXP_STRAIN);
		XLSUtils.setFieldValue(sheet, x, y, transpose, props, EnumXLSColumnHeader.EXP_SEX);
		XLSUtils.setFieldValue(sheet, x, y, transpose, props, EnumXLSColumnHeader.EXP_STIM2);
		XLSUtils.setFieldValue(sheet, x, y, transpose, props, EnumXLSColumnHeader.EXP_CONC2);
	}

	/**
	 * Writes spot properties to the sheet.
	 */
	private void writeSpotProperties(SXSSFSheet sheet, int x, int y, boolean transpose, Spot spot, Cage cage,
			String charSeries, EnumXLSExport xlsExportType) {
		XLSUtils.setValue(sheet, x, y + ExcelExportConstants.ColumnPositions.SPOT_VOLUME, transpose,
				spot.getProperties().getSpotVolume());
		XLSUtils.setValue(sheet, x, y + ExcelExportConstants.ColumnPositions.SPOT_PIXELS, transpose,
				spot.getProperties().getSpotNPixels());
		XLSUtils.setValue(sheet, x, y + ExcelExportConstants.ColumnPositions.CAGEPOS, transpose,
				spot.getCagePosition(xlsExportType));
		XLSUtils.setValue(sheet, x, y + ExcelExportConstants.ColumnPositions.SPOT_STIM, transpose,
				spot.getProperties().getStimulus());
		XLSUtils.setValue(sheet, x, y + ExcelExportConstants.ColumnPositions.SPOT_CONC, transpose,
				spot.getProperties().getConcentration());
		XLSUtils.setValue(sheet, x, y + ExcelExportConstants.ColumnPositions.SPOT_CAGEID, transpose,
				spot.getProperties().getCageID());
		XLSUtils.setValue(sheet, x, y + ExcelExportConstants.ColumnPositions.SPOT_CAGEROW, transpose,
				spot.getProperties().getCageRow());
		XLSUtils.setValue(sheet, x, y + ExcelExportConstants.ColumnPositions.SPOT_CAGECOL, transpose,
				spot.getProperties().getCageColumn());
		XLSUtils.setValue(sheet, x, y + ExcelExportConstants.ColumnPositions.CAGEID, transpose,
				charSeries + spot.getProperties().getCageID());
		XLSUtils.setValue(sheet, x, y + ExcelExportConstants.ColumnPositions.SPOT_NFLIES, transpose,
				cage.getProperties().getCageNFlies());
		XLSUtils.setValue(sheet, x, y + ExcelExportConstants.ColumnPositions.CHOICE_NOCHOICE, transpose,
				ExcelExportConstants.CHOICE_NOCHOICE_DEFAULT);
		XLSUtils.setValue(sheet, x, y + ExcelExportConstants.ColumnPositions.DUM4, transpose,
				spot.getProperties().getStimulusI());
	}

	/**
	 * Writes cage properties to the sheet.
	 */
	private void writeCageProperties(SXSSFSheet sheet, int x, int y, boolean transpose, Cage cage) {
		XLSUtils.setValue(sheet, x, y + ExcelExportConstants.ColumnPositions.CAGE_STRAIN, transpose,
				cage.getProperties().getFlyStrain());
		XLSUtils.setValue(sheet, x, y + ExcelExportConstants.ColumnPositions.CAGE_SEX, transpose,
				cage.getProperties().getFlySex());
		XLSUtils.setValue(sheet, x, y + ExcelExportConstants.ColumnPositions.CAGE_AGE, transpose,
				cage.getProperties().getFlyAge());
		XLSUtils.setValue(sheet, x, y + ExcelExportConstants.ColumnPositions.CAGE_COMMENT, transpose,
				cage.getProperties().getComment());
	}

	/**
	 * Handles export errors by logging them.
	 * 
	 * @param exp           The experiment
	 * @param nOutputFrames The number of output frames
	 */
	protected void handleExportError(Experiment exp, int nOutputFrames) {
		String error = String.format(ExcelExportConstants.ErrorMessages.EXPORT_ERROR_FORMAT, exp.getResultsDirectory(),
				nOutputFrames, exp.seqCamData.getTimeManager().getBinFirst_ms(),
				exp.seqCamData.getTimeManager().getBinLast_ms());
		System.err.println(error);
	}

	/**
	 * Writes XLS results to the sheet.
	 * 
	 * @param sheet     The sheet to write to
	 * @param pt        The starting point
	 * @param xlsResult The results to write
	 */
	protected void writeXLSResult(SXSSFSheet sheet, Point pt, XLSResults xlsResult) {
		boolean transpose = options.transpose;

		if (xlsResult.getValuesOutLength() < 1) {
			return;
		}

		for (long coltime = expAll.seqCamData.getFirstImageMs(); coltime < expAll.seqCamData
				.getLastImageMs(); coltime += options.buildExcelStepMs, pt.y++) {

			int i_from = (int) ((coltime - expAll.seqCamData.getFirstImageMs()) / options.buildExcelStepMs);

			if (i_from >= xlsResult.getValuesOutLength()) {
				break;
			}

			double value = xlsResult.getValuesOut()[i_from];

			if (!Double.isNaN(value)) {
				XLSUtils.setValue(sheet, pt, transpose, value);

//				if (i_from < xlsResult.padded_out.length && xlsResult.padded_out[i_from]) {
//					XLSUtils.getCell(sheet, pt, transpose).setCellStyle(redCellStyle);
//				}
			}
		}
	}
}