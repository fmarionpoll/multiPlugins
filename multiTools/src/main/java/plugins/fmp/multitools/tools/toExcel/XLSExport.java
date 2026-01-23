package plugins.fmp.multitools.tools.toExcel;

import java.awt.Point;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import icy.gui.frame.progress.ProgressFrame;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ExperimentProperties;
import plugins.fmp.multitools.experiment.LazyExperiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.CageProperties;
import plugins.fmp.multitools.tools.Directories;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.Results;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.toExcel.config.ExcelExportConstants;
import plugins.fmp.multitools.tools.toExcel.enums.EnumXLSColumnHeader;
import plugins.fmp.multitools.tools.toExcel.exceptions.ExcelDataException;
import plugins.fmp.multitools.tools.toExcel.exceptions.ExcelExportException;
import plugins.fmp.multitools.tools.toExcel.exceptions.ExcelResourceException;
import plugins.fmp.multitools.tools.toExcel.utils.ExcelResourceManager;
import plugins.fmp.multitools.tools.toExcel.utils.XLSUtils;

/**
 * Template Method pattern base class for Excel export operations. Provides
 * common functionality and structure for all Excel export types.
 * 
 * <p>
 * This class defines the overall algorithm for Excel export while allowing
 * subclasses to customize specific steps through protected methods.
 */
public abstract class XLSExport {

	protected ResultsOptions options = null;
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
	 * @param filename       The target Excel file path
	 * @param resultsOptions The export options
	 * @throws ExcelExportException If export fails
	 */
	public final void exportToFile(String filename, ResultsOptions resultsOptions) throws ExcelExportException {
		System.out.println("XLSExportBase:exportToFile() - " + ExcelExportConstants.EXPORT_START_MESSAGE);

		this.options = resultsOptions;
		this.expList = resultsOptions.expList;

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
				if (exp instanceof LazyExperiment) {
					((LazyExperiment) exp).loadIfNeeded();
				}

				// Ensure properties are loaded (reload to ensure they're up to date)
				exp.loadExperimentDescriptors();
				exp.load_spots_description_and_measures();

				// Ensure bin directory is set before loading capillaries
				// This is critical for finding the CapillariesMeasures.csv file
				ensureBinDirectoryIsDefined(exp);
				exp.load_capillaries_description_and_measures();
				exp.loadCagesMeasures();
				if (shouldSkipChainedExperiment(exp)) {
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

	protected void ensureBinDirectoryIsDefined(Experiment exp) {
		if (exp.getBinSubDirectory() == null) {
			// First, try to use shared bin directory from experiment list
			if (expList.expListBinSubDirectory != null) {
				exp.setBinSubDirectory(expList.expListBinSubDirectory);
			} else {
				// Auto-detect bin directory by finding subdirectories with TIFF files
				List<String> binDirs = Directories.getSortedListOfSubDirectoriesWithTIFF(exp.getResultsDirectory());
				if (binDirs != null && !binDirs.isEmpty()) {
					// Find first directory containing "bin" (case-insensitive)
					for (String dir : binDirs) {
						if (dir.toLowerCase().contains("bin")) {
							exp.setBinSubDirectory(dir);
							break;
						}
					}
					// If no "bin" directory found, use the first one
					if (exp.getBinSubDirectory() == null) {
						exp.setBinSubDirectory(binDirs.get(0));
					}
				}
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
	protected boolean shouldSkipChainedExperiment(Experiment exp) {
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
	protected abstract int exportExperimentData(Experiment exp, ResultsOptions resultsOptions, int startColumn,
			String charSeries) throws ExcelExportException;

	/**
	 * Template method for exporting a specific result type to sheets. Handles common
	 * logic like sheet creation, "onlyalive" sheet handling, and error management.
	 * Subclasses provide the specific export implementation via
	 * exportResultTypeToSheet.
	 * 
	 * @param exp         The experiment to export
	 * @param col0        The starting column
	 * @param charSeries  The series identifier
	 * @param resultType  The result type to export
	 * @param errorContext The context string for error messages (e.g., "capillary", "gulp", "fly position")
	 * @return The next available column
	 * @throws ExcelExportException If export fails
	 */
	protected int exportResultType(Experiment exp, int col0, String charSeries, EnumResults resultType,
			String errorContext) throws ExcelExportException {
		try {
			options.resultType = resultType;
			SXSSFSheet sheet = getSheet(resultType.toString(), resultType);
			int colmax = exportResultTypeToSheet(exp, sheet, resultType, col0, charSeries);

			if (options.onlyalive) {
				sheet = getSheet(resultType.toString() + ExcelExportConstants.ALIVE_SHEET_SUFFIX, resultType);
				exportResultTypeToSheet(exp, sheet, resultType, col0, charSeries);
			}

			return colmax;
		} catch (ExcelResourceException e) {
			throw new ExcelExportException("Failed to export " + errorContext + " data", "export_result_type",
					resultType.toString(), e);
		}
	}

	/**
	 * Exports a specific result type to a sheet. Must be implemented by subclasses
	 * to provide the specific export logic.
	 * 
	 * @param exp        The experiment to export
	 * @param sheet      The sheet to write to
	 * @param resultType The result type being exported
	 * @param col0       The starting column
	 * @param charSeries The series identifier
	 * @return The next available column
	 */
	protected abstract int exportResultTypeToSheet(Experiment exp, SXSSFSheet sheet, EnumResults resultType, int col0,
			String charSeries);

	/**
	 * Helper class for mapping export options to their corresponding result types.
	 * Used to declaratively define which result types should be exported based on
	 * option flags.
	 */
	protected static class OptionToResultsMapping {
		private final Supplier<Boolean> optionCheck;
		private final List<EnumResults> results;

		/**
		 * Creates a mapping from an option check to one or more result types.
		 * 
		 * @param optionCheck A supplier that returns true if the option is enabled
		 * @param results     The result types to export when the option is enabled
		 */
		OptionToResultsMapping(Supplier<Boolean> optionCheck, EnumResults... results) {
			this.optionCheck = optionCheck;
			this.results = Arrays.asList(results);
		}

		/**
		 * Checks if the option is enabled.
		 * 
		 * @return true if the option is enabled
		 */
		boolean isEnabled() {
			return optionCheck.get();
		}

		/**
		 * Gets the list of result types associated with this option.
		 * 
		 * @return The list of result types
		 */
		List<EnumResults> getResults() {
			return results;
		}
	}

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
	 * @param title      The sheet title
	 * @param resultType The export type
	 * @return The sheet instance
	 * @throws ExcelResourceException If sheet creation fails
	 */
	protected SXSSFSheet getSheet(String title, EnumResults resultType) throws ExcelResourceException {
		SXSSFWorkbook workbook = resourceManager.getWorkbook();
		SXSSFSheet sheet = workbook.getSheet(title);

		if (sheet == null) {
			sheet = workbook.createSheet(title);
			writeTopRowDescriptors(sheet);
			writeTopRowTimeIntervals(sheet, getDescriptorRowCount(), resultType);
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
	 * @param sheet      The sheet to write to
	 * @param row        The starting row
	 * @param resultType The export type
	 */
	protected void writeTopRowTimeIntervals(SXSSFSheet sheet, int row, EnumResults resultType) {
		boolean transpose = options.transpose;
		Point pt = new Point(0, row);

		long firstImageMs = expAll.getSeqCamData().getFirstImageMs();
		long lastImageMs = expAll.getSeqCamData().getLastImageMs();
		long duration = lastImageMs - firstImageMs;
		long interval = 0;

		while (interval < duration) {
			long absoluteTime = firstImageMs + interval;
			int i = (int) (absoluteTime / options.buildExcelUnitMs);
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
		return EnumXLSColumnHeader.DUM4.getValue() + 1;
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
	 * Writes basic file information to the sheet.
	 */
	protected void writeFileInformation(SXSSFSheet sheet, Point pt, boolean transpose, Experiment exp) {
		String filename = exp.getResultsDirectory();
		if (filename == null) {
			filename = exp.getSeqCamData().getImagesDirectory();
		}

		Path path = Paths.get(filename);
		SimpleDateFormat df = new SimpleDateFormat(ExcelExportConstants.DEFAULT_DATE_FORMAT);
		String date = df.format(exp.chainImageFirst_ms);
		String name0 = path.toString();
		String cam = extractCameraInfo(name0);

		XLSUtils.setValueAtColumn(sheet, pt, EnumXLSColumnHeader.PATH, transpose, name0);
		XLSUtils.setValueAtColumn(sheet, pt, EnumXLSColumnHeader.DATE, transpose, date);
		XLSUtils.setValueAtColumn(sheet, pt, EnumXLSColumnHeader.CAM, transpose, cam);
	}

	/**
	 * Extracts camera information from the filename.
	 */
	public String extractCameraInfo(String filename) {
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
	protected void writeExperimentProperties(SXSSFSheet sheet, Point pt, boolean transpose, Experiment exp,
			String charSeries) {
		// Ensure experiment properties are loaded
		exp.loadExperimentDescriptors();
		ExperimentProperties props = exp.getProperties();

		XLSUtils.setFieldValueAtColumn(sheet, pt, transpose, props, EnumXLSColumnHeader.EXP_BOXID, charSeries);
		XLSUtils.setFieldValueAtColumn(sheet, pt, transpose, props, EnumXLSColumnHeader.EXP_EXPT);
		XLSUtils.setFieldValueAtColumn(sheet, pt, transpose, props, EnumXLSColumnHeader.EXP_STIM1);
		XLSUtils.setFieldValueAtColumn(sheet, pt, transpose, props, EnumXLSColumnHeader.EXP_CONC1);
		XLSUtils.setFieldValueAtColumn(sheet, pt, transpose, props, EnumXLSColumnHeader.EXP_STRAIN);
		XLSUtils.setFieldValueAtColumn(sheet, pt, transpose, props, EnumXLSColumnHeader.EXP_SEX);
		XLSUtils.setFieldValueAtColumn(sheet, pt, transpose, props, EnumXLSColumnHeader.EXP_STIM2);
		XLSUtils.setFieldValueAtColumn(sheet, pt, transpose, props, EnumXLSColumnHeader.EXP_CONC2);
	}

	/**
	 * Writes cage properties to the sheet.
	 */
	protected void writeCageProperties(SXSSFSheet sheet, Point pt, boolean transpose, Cage cage) {
		CageProperties props = cage.getProperties();
		XLSUtils.setValueAtColumn(sheet, pt, EnumXLSColumnHeader.CAGEID, transpose, props.getCageID());
		XLSUtils.setValueAtColumn(sheet, pt, EnumXLSColumnHeader.CAGEPOS, transpose, props.getCagePosition());
		XLSUtils.setValueAtColumn(sheet, pt, EnumXLSColumnHeader.CAGE_NFLIES, transpose, props.getCageNFlies());
		XLSUtils.setValueAtColumn(sheet, pt, EnumXLSColumnHeader.CAGE_STRAIN, transpose, props.getFlyStrain());
		XLSUtils.setValueAtColumn(sheet, pt, EnumXLSColumnHeader.CAGE_SEX, transpose, props.getFlySex());
		XLSUtils.setValueAtColumn(sheet, pt, EnumXLSColumnHeader.CAGE_AGE, transpose, props.getFlyAge());
		XLSUtils.setValueAtColumn(sheet, pt, EnumXLSColumnHeader.CAGE_COMMENT, transpose, props.getComment());
	}

	/**
	 * Handles export errors by logging them.
	 * 
	 * @param exp           The experiment
	 * @param nOutputFrames The number of output frames
	 */
	protected void handleExportError(Experiment exp, int nOutputFrames) {
		String error = String.format(ExcelExportConstants.ErrorMessages.EXPORT_ERROR_FORMAT, exp.getResultsDirectory(),
				nOutputFrames, exp.getSeqCamData().getTimeManager().getBinFirst_ms(),
				exp.getSeqCamData().getTimeManager().getBinLast_ms());
		System.err.println(error);
	}

	/**
	 * Writes XLS results to the sheet.
	 * 
	 * @param sheet     The sheet to write to
	 * @param pt        The starting point
	 * @param result The results to write
	 */
	protected void writeXLSResult(SXSSFSheet sheet, Point pt, Results result) {
		boolean transpose = options.transpose;

		if (result.getValuesOutLength() < 1) {
			return;
		}

		for (long coltime = expAll.getSeqCamData().getFirstImageMs(); coltime < expAll.getSeqCamData()
				.getLastImageMs(); coltime += options.buildExcelStepMs, pt.y++) {

			int i_from = (int) ((coltime - expAll.getSeqCamData().getFirstImageMs()) / options.buildExcelStepMs);

			if (i_from >= result.getValuesOutLength()) {
				break;
			}

			double value = result.getValuesOut()[i_from];

			if (!Double.isNaN(value)) {
				XLSUtils.setValue(sheet, pt, transpose, value);

//				if (i_from < xlsResult.padded_out.length && xlsResult.padded_out[i_from]) {
//					XLSUtils.getCell(sheet, pt, transpose).setCellStyle(redCellStyle);
//				}
			}
		}
	}
}