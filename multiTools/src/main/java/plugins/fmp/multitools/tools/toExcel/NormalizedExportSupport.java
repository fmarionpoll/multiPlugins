package plugins.fmp.multitools.tools.toExcel;

import java.util.Set;

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
 * Normalized Excel export: one {@code SERIES} row per entity (capillary/spot or
 * cage for LR), joined from {@code DATA_<measure>} via {@code entity_id}.
 * <p>
 * {@code entity_id} = {@code exp_key|cage_id|cap_id}. For cage-LR aggregates,
 * {@code cap_id} is {@link #CAP_ID_LR}. Always row-oriented; ignores the
 * wide-export transpose flag.
 */
public final class NormalizedExportSupport {

	public static final String SERIES_SHEET = "SERIES";
	public static final String DATA_PREFIX = "DATA_";
	public static final String ENTITY_SEP = "|";
	/** Capillary segment for cage-level L/R aggregate series. */
	public static final String CAP_ID_LR = "LR";

	private static final int COL_ENTITY_ID = 0;
	private static final int COL_EXP_KEY = 1;
	private static final int COL_CAGE_ID = 2;
	private static final int COL_CAP_ID = 3;
	private static final int DESCRIPTOR_COL0 = 4;

	private NormalizedExportSupport() {
	}

	public static String dataSheetName(EnumResults resultType) {
		String name = DATA_PREFIX + resultType.toString();
		return name.length() > 31 ? name.substring(0, 31) : name;
	}

	public static String buildExpKey(Experiment exp, String charSeries) {
		String base = "";
		if (exp != null) {
			exp.loadExperimentDescriptors();
			ExperimentProperties props = exp.getProperties();
			if (props != null) {
				String id = props.getField(EnumXLSColumnHeader.EXP_ID);
				if (id != null && !id.isEmpty()) {
					base = id;
				}
			}
			if (base.isEmpty()) {
				String path = exp.getExperimentField(EnumXLSColumnHeader.PATH);
				String date = exp.getExperimentField(EnumXLSColumnHeader.DATE);
				String cam = exp.getExperimentField(EnumXLSColumnHeader.CAM);
				base = nullToEmpty(path) + ENTITY_SEP + nullToEmpty(date) + ENTITY_SEP + nullToEmpty(cam);
			}
		}
		if (charSeries != null && !charSeries.isEmpty()) {
			return base + "_" + charSeries;
		}
		return base;
	}

	public static String buildCapId(Capillary capillary) {
		if (capillary == null) {
			return CAP_ID_LR;
		}
		String id = capillary.getLast2ofCapillaryName();
		return id != null ? id : "";
	}

	public static String buildEntityId(String expKey, int cageId, String capId) {
		return nullToEmpty(expKey) + ENTITY_SEP + cageId + ENTITY_SEP + nullToEmpty(capId);
	}

	public static String buildEntityId(Experiment exp, String charSeries, Cage cage, Capillary capillary) {
		String expKey = buildExpKey(exp, charSeries);
		int cageId = cage != null ? cage.getProperties().getCageID() : -1;
		return buildEntityId(expKey, cageId, buildCapId(capillary));
	}

	public static SXSSFSheet getOrCreateSeriesSheet(SXSSFWorkbook workbook) {
		SXSSFSheet sheet = workbook.getSheet(SERIES_SHEET);
		if (sheet != null) {
			return sheet;
		}
		sheet = workbook.createSheet(SERIES_SHEET);
		setCellString(sheet, 0, COL_ENTITY_ID, "entity_id");
		setCellString(sheet, 0, COL_EXP_KEY, "exp_key");
		setCellString(sheet, 0, COL_CAGE_ID, "cage_id");
		setCellString(sheet, 0, COL_CAP_ID, "cap_id");
		for (EnumXLSColumnHeader header : EnumXLSColumnHeader.values()) {
			EnumColumnType type = header.toType();
			if (type != EnumColumnType.COMMON && type != EnumColumnType.CAP) {
				continue;
			}
			setCellString(sheet, 0, header.getValue() + DESCRIPTOR_COL0, header.getName());
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
		setCellString(sheet, 0, 0, "entity_id");
		setCellString(sheet, 0, 1, "t_minutes");
		if (isCageLrMeasure(resultType)) {
			setCellString(sheet, 0, 2, "sum");
			setCellString(sheet, 0, 3, "pi");
		} else {
			setCellString(sheet, 0, 2, "value");
		}
		return sheet;
	}

	public static boolean isCageLrMeasure(EnumResults resultType) {
		return resultType == EnumResults.TOPLEVEL_LR || resultType == EnumResults.TOPLEVELDELTA_LR
				|| resultType == EnumResults.SUMGULPS_LR;
	}

	public static int nextEmptyRow(SXSSFSheet sheet) {
		return sheet.getLastRowNum() + 1;
	}

	/**
	 * Writes a SERIES row once per {@code entity_id}. Returns the join key for DATA
	 * sheets. Measure name is implied by the DATA sheet, not stored on SERIES.
	 */
	public static String ensureSeriesRow(SXSSFSheet seriesSheet, Set<String> writtenEntityIds, Experiment exp,
			String charSeries, Cage cage, Capillary capillary) {
		String entityId = buildEntityId(exp, charSeries, cage, capillary);
		if (writtenEntityIds != null && writtenEntityIds.contains(entityId)) {
			return entityId;
		}
		if (writtenEntityIds != null) {
			writtenEntityIds.add(entityId);
		}

		String expKey = buildExpKey(exp, charSeries);
		int cageId = cage != null ? cage.getProperties().getCageID() : -1;
		String capId = buildCapId(capillary);
		int row = nextEmptyRow(seriesSheet);

		setCellString(seriesSheet, row, COL_ENTITY_ID, entityId);
		setCellString(seriesSheet, row, COL_EXP_KEY, expKey);
		setCellInt(seriesSheet, row, COL_CAGE_ID, cageId);
		setCellString(seriesSheet, row, COL_CAP_ID, capId);

		if (exp != null) {
			setCellString(seriesSheet, row, EnumXLSColumnHeader.PATH.getValue() + DESCRIPTOR_COL0,
					exp.getExperimentField(EnumXLSColumnHeader.PATH));
			setCellString(seriesSheet, row, EnumXLSColumnHeader.DATE.getValue() + DESCRIPTOR_COL0,
					exp.getExperimentField(EnumXLSColumnHeader.DATE));
			setCellString(seriesSheet, row, EnumXLSColumnHeader.CAM.getValue() + DESCRIPTOR_COL0,
					exp.getExperimentField(EnumXLSColumnHeader.CAM));
			double camSec = exp.getCameraSampleIntervalSec();
			double analysisSec = exp.getAnalysisBinSec();
			if (camSec > 0) {
				setCellDouble(seriesSheet, row, EnumXLSColumnHeader.CAM_SAMPLE_S.getValue() + DESCRIPTOR_COL0, camSec);
			}
			if (analysisSec > 0) {
				setCellDouble(seriesSheet, row, EnumXLSColumnHeader.ANALYSIS_BIN_S.getValue() + DESCRIPTOR_COL0,
						analysisSec);
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
			setCellInt(seriesSheet, row, EnumXLSColumnHeader.CAGEID.getValue() + DESCRIPTOR_COL0, cp.getCageID());
			setCellInt(seriesSheet, row, EnumXLSColumnHeader.CAGEPOS.getValue() + DESCRIPTOR_COL0, cp.getCageID());
			setCellInt(seriesSheet, row, EnumXLSColumnHeader.CAGE_NFLIES.getValue() + DESCRIPTOR_COL0, cp.getCageNFlies());
			setCellString(seriesSheet, row, EnumXLSColumnHeader.CAGE_STRAIN.getValue() + DESCRIPTOR_COL0,
					cp.getFlyStrain());
			setCellString(seriesSheet, row, EnumXLSColumnHeader.CAGE_SEX.getValue() + DESCRIPTOR_COL0, cp.getFlySex());
			setCellInt(seriesSheet, row, EnumXLSColumnHeader.CAGE_AGE.getValue() + DESCRIPTOR_COL0, cp.getFlyAge());
			setCellString(seriesSheet, row, EnumXLSColumnHeader.CAGE_COMMENT.getValue() + DESCRIPTOR_COL0,
					cp.getComment());
		}
		if (capillary != null) {
			String name = capillary.getKymographName();
			if (name == null || name.isEmpty()) {
				name = capId;
			}
			setCellString(seriesSheet, row, EnumXLSColumnHeader.CAP.getValue() + DESCRIPTOR_COL0, name);
			setCellString(seriesSheet, row, EnumXLSColumnHeader.CAP_INDEX.getValue() + DESCRIPTOR_COL0,
					expKey + "_" + capId);
			setCellDouble(seriesSheet, row, EnumXLSColumnHeader.CAP_VOLUME.getValue() + DESCRIPTOR_COL0,
					capillary.getVolume());
			setCellInt(seriesSheet, row, EnumXLSColumnHeader.CAP_PIXELS.getValue() + DESCRIPTOR_COL0,
					capillary.getPixels());
			setCellString(seriesSheet, row, EnumXLSColumnHeader.CAP_STIM.getValue() + DESCRIPTOR_COL0,
					capillary.getStimulus());
			setCellString(seriesSheet, row, EnumXLSColumnHeader.CAP_CONC.getValue() + DESCRIPTOR_COL0,
					capillary.getConcentration());
			setCellInt(seriesSheet, row, EnumXLSColumnHeader.CAP_NFLIES.getValue() + DESCRIPTOR_COL0,
					capillary.getNFlies());
		} else {
			setCellString(seriesSheet, row, EnumXLSColumnHeader.CAP.getValue() + DESCRIPTOR_COL0, "cage_LR");
		}
		return entityId;
	}

	public static void writeDataPoints(SXSSFSheet dataSheet, String entityId, long[] timesMs, double[] values,
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
			setCellString(dataSheet, row, 0, entityId);
			setCellDouble(dataSheet, row, 1, timesMs[i] / 60000.0);
			setCellDouble(dataSheet, row, 2, v);
			row++;
		}
	}

	/**
	 * Cage LR export: one DATA row per time with both {@code sum} and {@code pi}.
	 */
	public static void writeDataPointsSumPi(SXSSFSheet dataSheet, String entityId, long[] timesMs, double[] sumValues,
			double[] piValues) {
		int row = nextEmptyRow(dataSheet);
		int n = Math.min(timesMs != null ? timesMs.length : 0,
				Math.min(sumValues != null ? sumValues.length : 0, piValues != null ? piValues.length : 0));
		for (int i = 0; i < n; i++) {
			double sum = sumValues[i];
			double pi = piValues[i];
			if (Double.isNaN(sum) && Double.isNaN(pi)) {
				continue;
			}
			setCellString(dataSheet, row, 0, entityId);
			setCellDouble(dataSheet, row, 1, timesMs[i] / 60000.0);
			if (!Double.isNaN(sum)) {
				setCellDouble(dataSheet, row, 2, sum);
			}
			if (!Double.isNaN(pi)) {
				setCellDouble(dataSheet, row, 3, pi);
			}
			row++;
		}
	}

	private static String nullToEmpty(String s) {
		return s != null ? s : "";
	}

	private static void setProp(SXSSFSheet sheet, int row, ExperimentProperties props, EnumXLSColumnHeader field,
			String charSeries) {
		String text = props.getField(field);
		if (charSeries != null && !charSeries.isEmpty()) {
			text = text + "_" + charSeries;
		}
		setCellString(sheet, row, field.getValue() + DESCRIPTOR_COL0, text);
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
