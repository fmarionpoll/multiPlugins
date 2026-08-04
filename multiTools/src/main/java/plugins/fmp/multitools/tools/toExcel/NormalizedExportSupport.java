package plugins.fmp.multitools.tools.toExcel;

import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ExperimentProperties;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.CageProperties;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.toExcel.enums.EnumColumnType;
import plugins.fmp.multitools.tools.toExcel.enums.EnumXLSColumnHeader;
import plugins.fmp.multitools.tools.toExcel.utils.XLSUtils;

/**
 * Normalized Excel export: SERIES (descriptors once) + DATA_measure (series_id,
 * t_min, value). Always row-oriented; ignores the wide-export transpose flag.
 */
public final class NormalizedExportSupport {

	public static final String SERIES_SHEET = "SERIES";
	public static final String DATA_PREFIX = "DATA_";

	private NormalizedExportSupport() {
	}

	public static String dataSheetName(EnumResults resultType) {
		String name = DATA_PREFIX + resultType.toString();
		return name.length() > 31 ? name.substring(0, 31) : name;
	}

	public static SXSSFSheet getOrCreateSeriesSheet(SXSSFWorkbook workbook) {
		SXSSFSheet sheet = workbook.getSheet(SERIES_SHEET);
		if (sheet != null) {
			return sheet;
		}
		sheet = workbook.createSheet(SERIES_SHEET);
		setCellString(sheet, 0, 0, "series_id");
		for (EnumXLSColumnHeader header : EnumXLSColumnHeader.values()) {
			EnumColumnType type = header.toType();
			if (type != EnumColumnType.COMMON && type != EnumColumnType.CAP) {
				continue;
			}
			setCellString(sheet, 0, header.getValue() + 1, header.getName());
		}
		return sheet;
	}

	public static SXSSFSheet getOrCreateDataSheet(SXSSFWorkbook workbook, EnumResults resultType) {
		String title = dataSheetName(resultType);
		SXSSFSheet sheet = workbook.getSheet(title);
		if (sheet != null) {
			return sheet;
		}
		sheet = workbook.createSheet(title);
		setCellString(sheet, 0, 0, "series_id");
		setCellString(sheet, 0, 1, "t_min");
		setCellString(sheet, 0, 2, "value");
		return sheet;
	}

	public static int nextEmptyRow(SXSSFSheet sheet) {
		return sheet.getLastRowNum() + 1;
	}

	public static int writeSeriesDescriptors(SXSSFSheet seriesSheet, Experiment exp, String charSeries, Cage cage,
			Capillary capillary, EnumResults resultType) {
		int row = nextEmptyRow(seriesSheet);
		setCellInt(seriesSheet, row, 0, row);

		if (exp != null) {
			setCellString(seriesSheet, row, EnumXLSColumnHeader.PATH.getValue() + 1,
					exp.getExperimentField(EnumXLSColumnHeader.PATH));
			setCellString(seriesSheet, row, EnumXLSColumnHeader.DATE.getValue() + 1,
					exp.getExperimentField(EnumXLSColumnHeader.DATE));
			setCellString(seriesSheet, row, EnumXLSColumnHeader.CAM.getValue() + 1,
					exp.getExperimentField(EnumXLSColumnHeader.CAM));
			double camSec = exp.getCameraSampleIntervalSec();
			double analysisSec = exp.getAnalysisBinSec();
			if (camSec > 0) {
				setCellDouble(seriesSheet, row, EnumXLSColumnHeader.CAM_SAMPLE_S.getValue() + 1, camSec);
			}
			if (analysisSec > 0) {
				setCellDouble(seriesSheet, row, EnumXLSColumnHeader.ANALYSIS_BIN_S.getValue() + 1, analysisSec);
			}
			exp.loadExperimentDescriptors();
			ExperimentProperties props = exp.getProperties();
			if (props != null) {
				setProp(seriesSheet, row, props, EnumXLSColumnHeader.EXP_ID, charSeries);
				setProp(seriesSheet, row, props, EnumXLSColumnHeader.EXP_EXPT, null);
				setProp(seriesSheet, row, props, EnumXLSColumnHeader.EXP_STIM1, null);
				setProp(seriesSheet, row, props, EnumXLSColumnHeader.EXP_CONC1, null);
				setProp(seriesSheet, row, props, EnumXLSColumnHeader.EXP_STIM2, null);
				setProp(seriesSheet, row, props, EnumXLSColumnHeader.EXP_CONC2, null);
				setProp(seriesSheet, row, props, EnumXLSColumnHeader.EXP_STRAIN, null);
				setProp(seriesSheet, row, props, EnumXLSColumnHeader.EXP_SEX, null);
			}
		}
		if (cage != null) {
			CageProperties cp = cage.getProperties();
			setCellInt(seriesSheet, row, EnumXLSColumnHeader.CAGEID.getValue() + 1, cp.getCageID());
			setCellInt(seriesSheet, row, EnumXLSColumnHeader.CAGEPOS.getValue() + 1, cp.getCageID());
			setCellInt(seriesSheet, row, EnumXLSColumnHeader.CAGE_NFLIES.getValue() + 1, cp.getCageNFlies());
			setCellString(seriesSheet, row, EnumXLSColumnHeader.CAGE_STRAIN.getValue() + 1, cp.getFlyStrain());
			setCellString(seriesSheet, row, EnumXLSColumnHeader.CAGE_SEX.getValue() + 1, cp.getFlySex());
			setCellInt(seriesSheet, row, EnumXLSColumnHeader.CAGE_AGE.getValue() + 1, cp.getFlyAge());
			setCellString(seriesSheet, row, EnumXLSColumnHeader.CAGE_COMMENT.getValue() + 1, cp.getComment());
		}
		if (capillary != null) {
			setCellString(seriesSheet, row, EnumXLSColumnHeader.CAP.getValue() + 1,
					capillary.getSideDescriptor(resultType));
			setCellString(seriesSheet, row, EnumXLSColumnHeader.CAP_INDEX.getValue() + 1,
					charSeries + "_" + capillary.getLast2ofCapillaryName());
			setCellDouble(seriesSheet, row, EnumXLSColumnHeader.CAP_VOLUME.getValue() + 1, capillary.getVolume());
			setCellInt(seriesSheet, row, EnumXLSColumnHeader.CAP_PIXELS.getValue() + 1, capillary.getPixels());
			setCellString(seriesSheet, row, EnumXLSColumnHeader.CAP_STIM.getValue() + 1, capillary.getStimulus());
			setCellString(seriesSheet, row, EnumXLSColumnHeader.CAP_CONC.getValue() + 1, capillary.getConcentration());
			setCellInt(seriesSheet, row, EnumXLSColumnHeader.CAP_NFLIES.getValue() + 1, capillary.getNFlies());
			setCellString(seriesSheet, row, EnumXLSColumnHeader.DUM4.getValue() + 1, resultType.toString());
		}
		return row;
	}

	public static void writeDataPoints(SXSSFSheet dataSheet, int seriesId, long[] timesMs, double[] values,
			boolean sparseSkipEmpty) {
		int row = nextEmptyRow(dataSheet);
		int n = Math.min(timesMs != null ? timesMs.length : 0, values != null ? values.length : 0);
		for (int i = 0; i < n; i++) {
			double v = values[i];
			if (Double.isNaN(v)) {
				continue;
			}
			if (sparseSkipEmpty && v == 0.0) {
				continue;
			}
			setCellInt(dataSheet, row, 0, seriesId);
			setCellDouble(dataSheet, row, 1, timesMs[i] / 60000.0);
			setCellDouble(dataSheet, row, 2, v);
			row++;
		}
	}

	private static void setProp(SXSSFSheet sheet, int row, ExperimentProperties props, EnumXLSColumnHeader field,
			String charSeries) {
		String text = props.getField(field);
		if (charSeries != null && !charSeries.isEmpty()) {
			text = text + "_" + charSeries;
		}
		setCellString(sheet, row, field.getValue() + 1, text);
	}

	private static void setCellString(SXSSFSheet sheet, int row, int col, String value) {
		if (value == null) {
			return;
		}
		XLSUtils.getCell(sheet, row, col).setCellValue(value);
	}

	private static void setCellInt(SXSSFSheet sheet, int row, int col, int value) {
		XLSUtils.getCell(sheet, row, col).setCellValue(value);
	}

	private static void setCellDouble(SXSSFSheet sheet, int row, int col, double value) {
		XLSUtils.getCell(sheet, row, col).setCellValue(value);
	}
}
