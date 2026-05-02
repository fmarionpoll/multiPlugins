package plugins.fmp.multitools.tools.toExcel.query;

import java.awt.Point;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import icy.gui.frame.progress.ProgressFrame;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.Results;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.toExcel.XLSExportMeasuresFromSpot;
import plugins.fmp.multitools.tools.toExcel.config.ExcelExportConstants;
import plugins.fmp.multitools.tools.toExcel.enums.EnumColumnType;
import plugins.fmp.multitools.tools.toExcel.enums.EnumXLSQueryColumnHeader;
import plugins.fmp.multitools.tools.toExcel.exceptions.ExcelDataException;
import plugins.fmp.multitools.tools.toExcel.exceptions.ExcelExportException;
import plugins.fmp.multitools.tools.toExcel.exceptions.ExcelResourceException;
import plugins.fmp.multitools.tools.toExcel.utils.ExcelResourceManager;
import plugins.fmp.multitools.tools.toExcel.utils.SpotExcelTimeline;
import plugins.fmp.multitools.tools.toExcel.utils.XLSUtils;

public class XLSExportMeasuresCagesAsQuery extends XLSExportMeasuresFromSpot {
	ArrayList<EnumXLSQueryColumnHeader> headers = new ArrayList<EnumXLSQueryColumnHeader>();

	public void exportQToFile(String filename, ResultsOptions resultsOptions) throws ExcelExportException {
		Logger.debug("XLSExportBase:exportQToFile() - " + ExcelExportConstants.EXPORT_START_MESSAGE);

		this.options = resultsOptions;
		this.expList = resultsOptions.expList;

		try (ExcelResourceManager resourceManager = new ExcelResourceManager(filename)) {
			this.resourceManager = resourceManager;
			this.redCellStyle = resourceManager.getRedCellStyle();
			this.blueCellStyle = resourceManager.getBlueCellStyle();

			// Execute method steps
			prepareQExperiments();
			validateExportParameters();
			executeExportQ();

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
	 * Executes the export process with progress tracking.
	 * 
	 * @throws ExcelExportException If export execution fails
	 */
	protected void executeExportQ() throws ExcelExportException {
		int nbexpts = expList.getItemCount();
		initHeadersArray();
		ProgressFrame progress = new ProgressFrame(ExcelExportConstants.DEFAULT_PROGRESS_TITLE);

		try {
			progress.setLength(nbexpts);
			int column = 1;
			int iSeries = 0;

			for (int index = options.experimentIndexFirst; index <= options.experimentIndexLast; index++) {
				Experiment exp = expList.getItemAt(index);
				exp.load_spots_description_and_measures();
				progress.setMessage("Export experiment " + (index + 1) + " of " + nbexpts);
				Logger.debug("Export experiment " + (index + 1) + " of " + nbexpts);
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
	 * Prepares experiments for export by loading data and setting up chains.
	 * 
	 * @throws ExcelDataException If experiment preparation fails
	 */
	protected void prepareQExperiments() throws ExcelDataException {
		try {
			int[] ix = expList.getExportExperimentIndexBounds(options);
			expList.loadExperimentMeasuresForExportRange(true, options.onlyalive, ix[0], ix[1]);
//			expList.chainExperimentsUsingKymoIndexes(options.collateSeries);
//			expList.setFirstImageForAllExperiments(options.collateSeries);
//			expAll = expList.get_MsTime_of_StartAndEnd_AllExperiments(options);
		} catch (Exception e) {
			throw new ExcelDataException("Failed to prepare experiments for export", "prepare_experiments",
					"experiment_loading", e);
		}
	}

	private void initHeadersArray() {
		headers.add(EnumXLSQueryColumnHeader.QDATE);
		headers.add(EnumXLSQueryColumnHeader.QEXP_ID);
		headers.add(EnumXLSQueryColumnHeader.QEXP_EXPT);
		headers.add(EnumXLSQueryColumnHeader.QEXP_STIM1);
		headers.add(EnumXLSQueryColumnHeader.QEXP_CONC1);
		headers.add(EnumXLSQueryColumnHeader.QEXP_STIM2);
		headers.add(EnumXLSQueryColumnHeader.QEXP_CONC2);
		headers.add(EnumXLSQueryColumnHeader.QEXP_STRAIN);
		headers.add(EnumXLSQueryColumnHeader.QCAGE_NFLIES);
		headers.add(EnumXLSQueryColumnHeader.QCAGE_POS);
		headers.add(EnumXLSQueryColumnHeader.QVAL_TIME);
		headers.add(EnumXLSQueryColumnHeader.QVAL_STIM1);
		headers.add(EnumXLSQueryColumnHeader.QN_STIM1);
		headers.add(EnumXLSQueryColumnHeader.QVAL_STIM2);
		headers.add(EnumXLSQueryColumnHeader.QN_STIM2);
		headers.add(EnumXLSQueryColumnHeader.QVAL_SUM);
		headers.add(EnumXLSQueryColumnHeader.QVAL_PI);
		for (int i = 0; i < headers.size(); i++)
			headers.get(i).setValue(i);
	}

	@Override
	protected int exportExperimentData(Experiment exp, ResultsOptions resultsOptions, int startColumn,
			String charSeries) throws ExcelExportException {
		int column = getCageDataAndExport(exp, startColumn, charSeries, resultsOptions, EnumResults.AREA_SUMCLEAN);
		column = getCageDataAndExport(exp, startColumn, charSeries, resultsOptions, EnumResults.AREA_SUM);
		return column;
	}

	protected int getCageDataAndExport(Experiment exp, int col0, String charSeries, ResultsOptions resultsOptions,
			EnumResults resultType) throws ExcelDataException {
		options.resultType = resultType;
		int colmax = 0;
		try {
			SXSSFSheet sheet = xlsGetQSheet(resultType.toString(), resultType);
			colmax = xlsExportExperimentCageDataToSheet(sheet, exp, resultsOptions, resultType, col0, charSeries);
		} catch (Exception e) {
			throw new ExcelDataException("Failed to get access to sheet or to export", "getCageDataAndExport",
					"experiment_export", e);
		}
		return colmax;
	}

	SXSSFSheet xlsGetQSheet(String title, EnumResults resultType) throws ExcelResourceException {
		SXSSFWorkbook workbook = resourceManager.getWorkbook();
		SXSSFSheet sheet = workbook.getSheet(title);

		if (sheet == null) {
			sheet = resourceManager.getWorkbook().createSheet(title);
			writeTopRow_Qdescriptors(sheet);
		}
		return sheet;
	}

	int writeTopRow_Qdescriptors(SXSSFSheet sheet) {
		Point pt = new Point(0, 0);
		int x = 0;
		boolean transpose = options.transpose;
		int nextcol = -1;
		for (EnumXLSQueryColumnHeader dumb : headers) {
			XLSUtils.setValue(sheet, x, dumb.getValue(), transpose, dumb.getName());
			if (nextcol < dumb.getValue())
				nextcol = dumb.getValue();
		}
		pt.y = nextcol + 1;
		return pt.y;
	}

	int xlsExportExperimentCageDataToSheet(SXSSFSheet sheet, Experiment exp, ResultsOptions resultsOptions,
			EnumResults resultType, int col0, String charSeries) {
		Point pt = new Point(col0, 0);
		String stim1 = exp.getProperties().getField_stim1().trim();
		String conc1 = exp.getProperties().getField_conc1().trim();
		String stim2 = exp.getProperties().getField_stim2().trim();
		String conc2 = exp.getProperties().getField_conc2().trim();

		Spots allSpots = exp.getSpots();
		for (Cage cage : exp.getCages().cagesList) {

			if (cage.getSpotList(allSpots).size() == 0)
				continue;

			if (resultsOptions.onlyalive && cage.getProperties().getCageNFlies() < 1)
				continue;

			double scalingFactorToPhysicalUnits = allSpots.getScalingFactorToPhysicalUnits(resultType);

			Spot spot1 = cage.combineSpotsWithSameStimConc(stim1, conc1, allSpots);
			Results xlsStim1 = getResultForCage(exp, cage, spot1, scalingFactorToPhysicalUnits, resultsOptions,
					resultType);
			if (spot1 != null)
				cage.getProperties().setCountStim1(spot1.getProperties().getCountAggregatedSpots());

			Spot spot2 = cage.combineSpotsWithSameStimConc(stim2, conc2, allSpots);
			Results xlsStim2 = getResultForCage(exp, cage, spot2, scalingFactorToPhysicalUnits, resultsOptions,
					resultType);
			if (spot2 != null)
				cage.getProperties().setCountStim2(spot2.getProperties().getCountAggregatedSpots());

			Spot spotSUM = cage.createSpotSUM(spot1, spot2);
			Results xlsSUM = getResultForCage(exp, cage, spotSUM, scalingFactorToPhysicalUnits, resultsOptions,
					resultType);

			Spot spotPI = cage.createSpotPI(spot1, spot2);
			Results xlsPI = getResultForCage(exp, cage, spotPI, scalingFactorToPhysicalUnits, resultsOptions,
					resultType);

			int tStart = 0;
			int tEnd = 0;
			if (resultsOptions.fixedIntervals) {
				tStart = (int) resultsOptions.startAll_Ms / resultsOptions.buildExcelStepMs;
				tEnd = (int) resultsOptions.endAll_Ms / resultsOptions.buildExcelStepMs;
			} else {
				if (xlsStim1 != null)
					tEnd = xlsStim1.getValuesOutLength() - 1;
				else if (xlsStim2 != null)
					tEnd = xlsStim2.getValuesOutLength() - 1;
			}

			for (int t = tStart; t <= tEnd; t++) {
				pt.y = 0;
				writeCageProperties(sheet, pt, exp, charSeries, cage, resultType);
				pt.y -= 4;
				writeCageMeasuresAtT(sheet, pt, t, xlsStim1, xlsStim2, xlsPI, xlsSUM, resultType);
				pt.x++;
			}
		}
//		pt.x++;
		return pt.x;
	}

	Results getResultForCage(Experiment exp, Cage cage, Spot spot, double scaling, ResultsOptions resultsOptions,
			EnumResults resultType) {
		Results xlsResults = null;
		if (spot != null) {
			SpotExcelTimeline.SpotExcelGrid grid = SpotExcelTimeline.buildForSpotExport(exp, resultsOptions);
			xlsResults = getResultsDataValuesFromSpotMeasures(cage, spot, grid, resultsOptions);
			xlsResults.transferDataValuesToValuesOut(scaling, resultType);
		}
		return xlsResults;
	}

	void writeCageProperties(SXSSFSheet sheet, Point pt, Experiment exp, String charSeries, Cage cage,
			EnumResults resultType) {
		boolean transpose = options.transpose;
		for (int i = 0; i < headers.size(); i++) {
			if (headers.get(i).toType() == EnumColumnType.DESCRIPTOR_STR) {
				String dummy = getDescriptorStr(exp, cage, headers.get(i));
				pt.y = headers.get(i).getValue();
				XLSUtils.setValue(sheet, pt, transpose, dummy);
			}

			if (headers.get(i).toType() == EnumColumnType.DESCRIPTOR_INT) {
				int value = getDescriptorInt(exp, cage, headers.get(i));
				pt.y = headers.get(i).getValue();
				XLSUtils.setValue(sheet, pt, transpose, value);
			}
		}
	}

	void writeCageMeasuresAtT(SXSSFSheet sheet, Point pt, int t, Results xlsStim1, Results xlsStim2, Results xlsPI,
			Results xlsSUM, EnumResults resultType) {
		pt.y = EnumXLSQueryColumnHeader.QVAL_TIME.getValue();
		XLSUtils.setValue(sheet, pt, options.transpose, t);

		pt.y = EnumXLSQueryColumnHeader.QVAL_STIM1.getValue();
		writeDataToXLS(sheet, pt, t, xlsStim1);
		pt.y = EnumXLSQueryColumnHeader.QVAL_STIM2.getValue();
		writeDataToXLS(sheet, pt, t, xlsStim2);
		pt.y = EnumXLSQueryColumnHeader.QVAL_SUM.getValue();
		writeDataToXLS(sheet, pt, t, xlsSUM);
		pt.y = EnumXLSQueryColumnHeader.QVAL_PI.getValue();
		writeDataToXLS(sheet, pt, t, xlsPI);

		pt.y++;
	}

	void writeDataToXLS(SXSSFSheet sheet, Point pt, int t, Results xlsResult) {
		if (xlsResult == null)
			return;
		double value = xlsResult.getValuesOut()[t];
		boolean transpose = options.transpose;
		if (!Double.isNaN(value)) {
			XLSUtils.setValue(sheet, pt, transpose, value);
		}
	}

	String getDescriptorStr(Experiment exp, Cage cage, EnumXLSQueryColumnHeader col) {
		String dummy = null;
		switch (col) {
		case QDATE:
			SimpleDateFormat df = new SimpleDateFormat("MM/dd/yyyy");
			return df.format(exp.getSeqCamData().getTimeManager().getFirstImageMs());
		case QEXP_ID:
			return exp.getProperties().getFfield_expID();
		case QCAGEID:
			return Integer.toString(cage.getProperties().getCageID());
		case QCAGE_POS:
			return Integer.toString(cage.getProperties().getCageID());
		case QEXP_EXPT:
			return exp.getProperties().getFfield_experiment();
		case QEXP_STRAIN:
			return exp.getProperties().getField_strain();
		case QEXP_SEX:
			return exp.getProperties().getField_sex();
		case QEXP_STIM1:
			return exp.getProperties().getField_stim1();
		case QEXP_CONC1:
			return exp.getProperties().getField_conc1();
		case QEXP_STIM2:
			return exp.getProperties().getField_stim2();
		case QEXP_CONC2:
			return exp.getProperties().getField_conc2();

		case QCAGE_STRAIN:
			return cage.getProperties().getFlyStrain();
		case QCAGE_SEX:
			return cage.getProperties().getFlySex();
		case QCAGE_COMMENT:
			return cage.getProperties().getComment();

		default:
			break;
		}
		return dummy;
	}

	int getDescriptorInt(Experiment exp, Cage cage, EnumXLSQueryColumnHeader col) {
		int dummy = -1;
		switch (col) {
		case QCAGE_POS:
			return cage.getProperties().getArrayIndex();
		case QCAGE_NFLIES:
			return cage.getProperties().getCageNFlies();
		case QCAGE_AGE:
			return cage.getProperties().getFlyAge();
		case QN_STIM1:
			return cage.getProperties().getCountStim1();
		case QN_STIM2:
			return cage.getProperties().getCountStim2();

		default:
			break;
		}
		return dummy;
	}

}
