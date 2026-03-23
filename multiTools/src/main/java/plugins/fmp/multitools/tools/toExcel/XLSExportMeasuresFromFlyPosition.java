package plugins.fmp.multitools.tools.toExcel;

import java.awt.Color;
import java.awt.Point;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import org.apache.poi.xssf.streaming.SXSSFRow;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import icy.type.geom.Polygon2D;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ExperimentProperties;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.FlyPosition;
import plugins.fmp.multitools.experiment.cage.FlyPositions;
import plugins.fmp.multitools.experiment.sequence.ImageLoader;
import plugins.fmp.multitools.experiment.sequence.TimeManager;
import plugins.fmp.multitools.tools.ROI2D.ROIType;
import plugins.fmp.multitools.tools.ROI2D.ROIPersistenceUtils;
import plugins.fmp.multitools.tools.chart.builders.CageFlyPositionSeriesBuilder;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.Results;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.toExcel.config.ExcelExportConstants;
import plugins.fmp.multitools.tools.toExcel.enums.EnumColumnType;
import plugins.fmp.multitools.tools.toExcel.enums.EnumXLSColumnHeader;
import plugins.fmp.multitools.tools.toExcel.exceptions.ExcelExportException;
import plugins.fmp.multitools.tools.toExcel.exceptions.ExcelResourceException;
import plugins.fmp.multitools.tools.toExcel.utils.XLSUtils;
import plugins.kernel.roi.roi2d.ROI2DPolygon;
import plugins.kernel.roi.roi2d.ROI2DRectangle;

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
	 * Fly-position export can produce different "widths" per worksheet (notably
	 * XYIMAGE writes 4 components). If we advance the outer-loop startColumn by the
	 * widest sheet, narrower sheets appear to have large empty blocks between
	 * experiments. Track per-sheet next column instead.
	 */
	private final Map<String, Integer> nextColumnBySheet = new HashMap<>();

	private int getNextColumnForSheet(String sheetTitle, int defaultStart) {
		Integer v = nextColumnBySheet.get(sheetTitle);
		return v != null ? v.intValue() : defaultStart;
	}

	private void setNextColumnForSheet(String sheetTitle, int nextCol) {
		nextColumnBySheet.put(sheetTitle, nextCol);
	}

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

		// Fly-position exports need a valid camera timeline to map FlyPosition.flyIndexT -> FlyPosition.tMs.
		// In multi-experiment exports, measures can be loaded from bin directories without opening the
		// camera sequence, leaving tMs at 0 for all points (which results in data only at t0).
		// Ensure camera timing is available before building datasets.
		exp.openSequenceCamData();
		if (exp.getSeqCamData() != null) {
			exp.initTmsForFlyPositions(exp.getSeqCamData().getFirstImageMs());
		}

		// Always export static cage limits; Cage_ID matches XYIMAGE / XYTOPCAGE exports.
		exportCageLimitsForExperiment(exp, charSeries);
		exportFlyScaleForExperiment(exp, charSeries);

		OptionToResultsMapping[] mappings = {
			new OptionToResultsMapping(() -> options.xyImage, EnumResults.XYIMAGE),
			new OptionToResultsMapping(() -> options.xyCage, EnumResults.XYTOPCAGE),
			new OptionToResultsMapping(() -> options.xyCapillaries, EnumResults.XYTIPCAPS),
			new OptionToResultsMapping(() -> options.ellipseAxes, EnumResults.ELLIPSEAXES),
			new OptionToResultsMapping(() -> options.distance, EnumResults.DISTANCE),
			new OptionToResultsMapping(() -> options.alive, EnumResults.ISALIVE),
			new OptionToResultsMapping(() -> options.sleep, EnumResults.SLEEP)
		};

		// Keep the outer-loop column stable; each worksheet manages its own "next"
		// column via nextColumnBySheet.
		int outerLoopColumn = startColumn;
		for (OptionToResultsMapping mapping : mappings) {
			if (mapping.isEnabled()) {
				for (EnumResults resultType : mapping.getResults()) {
					outerLoopColumn = exportResultTypePerSheet(exp, outerLoopColumn, charSeries, resultType);
				}
			}
		}

		return outerLoopColumn;
	}

	/**
	 * Exports a result type using a per-worksheet start column to avoid empty
	 * blocks between experiments when different worksheets have different widths.
	 */
	private int exportResultTypePerSheet(Experiment exp, int defaultStartColumn, String charSeries, EnumResults resultType)
			throws ExcelExportException {
		String title = resultType.toString();
		int col0 = getNextColumnForSheet(title, defaultStartColumn);
		int colmax = exportResultType(exp, col0, charSeries, resultType, "fly position");
		setNextColumnForSheet(title, colmax);

		if (options.onlyalive) {
			String aliveTitle = title + ExcelExportConstants.ALIVE_SHEET_SUFFIX;
			int aliveCol0 = getNextColumnForSheet(aliveTitle, defaultStartColumn);
			int aliveMax = exportResultType(exp, aliveCol0, charSeries, resultType, "fly position");
			setNextColumnForSheet(aliveTitle, aliveMax);
		}

		// Return the default start column unchanged so the outer loop doesn't inflate
		// based on the widest sheet.
		return defaultStartColumn;
	}

	@Override
	protected int exportResultTypeToSheet(Experiment exp, SXSSFSheet sheet, EnumResults resultType, int col0,
			String charSeries) {
		// Special case: XYIMAGE now exports full rectangle components (x, y, w, h)
		// instead of a single Y-based series.
		if (resultType == EnumResults.XYIMAGE) {
			return exportXYImageRectComponents(exp, sheet, col0, charSeries);
		}

		ensureFlyPositionTimesInitialized(exp);

		// Align with other exporters: introduce a small separator block (2 cells)
		// between experiments, but compute the starting index per sheet so we don't
		// accumulate large empty regions.
		Point pt = new Point(computeNextSeriesIndex(sheet), 0);
		pt = writeExperimentSeparator(sheet, pt);

		ResultsOptions resultsOptions = new ResultsOptions();
		long kymoBin_ms = exp.getKymoBin_ms();
		if (kymoBin_ms <= 0) {
			kymoBin_ms = 60000;
		}
		resultsOptions.buildExcelStepMs = (int) kymoBin_ms;
		resultsOptions.relativeToMaximum = false;
		resultsOptions.subtractT0 = false;
		resultsOptions.correctEvaporation = false;
		resultsOptions.resultType = resultType;

		CageFlyPositionSeriesBuilder builder = new CageFlyPositionSeriesBuilder();

		for (Cage cage : exp.getCages().getCageList()) {
			if (cage == null) {
				continue;
			}

			XYSeriesCollection dataset = builder.build(exp, cage, resultsOptions);
			if (dataset == null || dataset.getSeriesCount() == 0) {
				continue;
			}

			XYSeries series = dataset.getSeries(0);
			if (series == null) {
				continue;
			}

			Results results = convertXYSeriesToResults(exp, cage, series, resultsOptions, resultType);
			if (results != null) {
				pt.y = 0;
				pt = writeExperimentFlyPositionInfos(sheet, pt, exp, charSeries, cage, resultType);
				XLSUtils.setValueAtColumn(sheet, new Point(pt.x, 0), EnumXLSColumnHeader.DUM4, options.transpose,
						resultType.toString());
				writeXLSResult(sheet, pt, results);
				pt.x++;
			}
		}
		return pt.x;
	}

	/**
	 * Writes only the COMMON and CAP descriptors for fly-position exports, which
	 * are conceptually cage/capillary-level rather than spot-level.
	 */
	@Override
	protected int writeTopRowDescriptors(SXSSFSheet sheet) {
		int nextcol = -1;

		for (EnumXLSColumnHeader header : EnumXLSColumnHeader.values()) {
			EnumColumnType type = header.toType();
			if (type != EnumColumnType.COMMON && type != EnumColumnType.CAP) {
				continue;
			}
			XLSUtils.setValue(sheet, 0, header.getValue(), options.transpose, header.getName());
			if (nextcol < header.getValue()) {
				nextcol = header.getValue();
			}
		}

		return nextcol + 1;
	}

	/**
	 * Converts an XYSeries (time in minutes from start, value) to a Results object
	 * with binned values indexed by time bin.
	 * 
	 * @param exp            The experiment
	 * @param cage           The cage
	 * @param series         The XYSeries to convert (X values in minutes from
	 *                       start)
	 * @param resultsOptions The export options
	 * @param resultType     The result type
	 * @return A Results object with valuesOut array populated
	 */
	private Results convertXYSeriesToResults(Experiment exp, Cage cage, XYSeries series,
			ResultsOptions resultsOptions, EnumResults resultType) {
		if (exp == null || cage == null || series == null || resultsOptions == null) {
			return null;
		}

		// Build results on the global timeline defined by expAll
		final long firstAllMs = expAll.getSeqCamData().getFirstImageMs();
		final long lastAllMs = expAll.getSeqCamData().getLastImageMs();
		final long buildExcelStepMs = resultsOptions.buildExcelStepMs;

		if (lastAllMs <= firstAllMs || buildExcelStepMs <= 0) {
			return null;
		}

		final int nBins = (int) ((lastAllMs - firstAllMs) / buildExcelStepMs) + 1;

		// Create Results object
		Results results = new Results("Cage_" + cage.getProperties().getCageID(), cage.getProperties().getCageNFlies(),
				cage.getProperties().getCageID(), 0, resultType);
		results.initValuesOutArray(nBins, Double.NaN);

		// XYSeries X values are in minutes from experiment start (time 0).
		// For "relative time" exports (absoluteTime=false), the sheet time axis is also relative,
		// so no offset must be applied.
		// For "absolute time" exports, shift local times onto the expAll absolute timeline.
		final long expOffsetToAllMs = options.absoluteTime ? (exp.chainImageFirst_ms - firstAllMs) : 0L;

		int nDataPoints = series.getItemCount();
		if (nDataPoints == 0) {
			return results;
		}

		double[] dataTimesMs = new double[nDataPoints];
		double[] dataValues = new double[nDataPoints];
		for (int i = 0; i < nDataPoints; i++) {
			double timeMinutes = series.getX(i).doubleValue();
			dataTimesMs[i] = expOffsetToAllMs + (timeMinutes * 60.0 * 1000.0);
			dataValues[i] = series.getY(i).doubleValue();
		}

		// Interpolation assumes time is monotonic. FlyPosition series can be out of order
		// (e.g. when loaded from legacy sources), so we enforce sorting here.
		sortByTime(dataTimesMs, dataValues);

		// Interpolate onto the global expAll bins. Do not extrapolate outside [first,last] data points.
		for (int binIndex = 0; binIndex < nBins; binIndex++) {
			long binTimeGlobalMs = firstAllMs + (long) binIndex * buildExcelStepMs;
			double interpolatedValue = linearInterpolateNoExtrapolation(dataTimesMs, dataValues, binTimeGlobalMs);
			if (!Double.isNaN(interpolatedValue)) {
				results.getValuesOut()[binIndex] = interpolatedValue;
			}
		}

		return results;
	}

	/**
	 * Exports cage limits (geometry) to a dedicated worksheet, reusing the same
	 * encoding as CagesDescription.csv (one row per cage, npoints then coordinates),
	 * with a leading Cage_ID column matching other fly-position exports.
	 */
	private void exportCageLimitsForExperiment(Experiment exp, String charSeries) throws ExcelExportException {
		if (exp == null || exp.getCages() == null || exp.getCages().getCageList().isEmpty()) {
			return;
		}

		try {
			SXSSFWorkbook workbook = resourceManager.getWorkbook();
			final String title = "CageLimits";
			SXSSFSheet sheet = workbook.getSheet(title);
			boolean transpose = options.transpose;

			final String[] fields = new String[] { "Cage_ID", "cageID", "nFlies", "age", "Comment", "strain", "sex",
					"colorR", "colorG", "colorB", "ROIname", "roiType", "npoints" };

			// Use the same convention as other exporters:
			// - logical x: entity index (cage instance)
			// - logical y: field index within the record
			// `transpose` only swaps these logical coordinates at write-time.
			int nextEntityIndex;
			if (sheet == null) {
				sheet = workbook.createSheet(title);
				for (int i = 0; i < fields.length; i++) {
					XLSUtils.setValue(sheet, 0, i, transpose, fields[i]);
				}
				nextEntityIndex = 1;
			} else {
				if (transpose) {
					// When transposed, entities are written as physical rows.
					nextEntityIndex = sheet.getLastRowNum() + 1;
				} else {
					// When not transposed, entities are written as physical columns.
					org.apache.poi.xssf.streaming.SXSSFRow firstRow = sheet.getRow(0);
					short lastCell = (firstRow != null) ? firstRow.getLastCellNum() : 1;
					nextEntityIndex = (lastCell >= 0) ? lastCell : 1;
					if (nextEntityIndex < 1) {
						nextEntityIndex = 1;
					}
				}
			}

			for (Cage cage : exp.getCages().getCageList()) {
				if (cage == null) {
					continue;
				}

				Color color = cage.getProperties().getColor();
				if (color == null && cage.getRoi() != null) {
					color = cage.getRoi().getColor();
				}
				if (color == null) {
					color = Color.MAGENTA;
				}

				String comment = cage.getProperties().getComment() != null ? cage.getProperties().getComment() : "";
				String strain = cage.getProperties().getFlyStrain() != null ? cage.getProperties().getFlyStrain() : "";
				String sex = cage.getProperties().getFlySex() != null ? cage.getProperties().getFlySex() : "";

				int x = nextEntityIndex;
				int y = 0;
				String cageIdLabel = (charSeries != null ? charSeries : "") + cage.getProperties().getCageID();
				XLSUtils.setValue(sheet, x, y++, transpose, cageIdLabel);
				XLSUtils.setValue(sheet, x, y++, transpose, cage.getProperties().getCageID());
				XLSUtils.setValue(sheet, x, y++, transpose, cage.getProperties().getCageNFlies());
				XLSUtils.setValue(sheet, x, y++, transpose, cage.getProperties().getFlyAge());
				XLSUtils.setValue(sheet, x, y++, transpose, comment);
				XLSUtils.setValue(sheet, x, y++, transpose, strain);
				XLSUtils.setValue(sheet, x, y++, transpose, sex);
				XLSUtils.setValue(sheet, x, y++, transpose, color.getRed());
				XLSUtils.setValue(sheet, x, y++, transpose, color.getGreen());
				XLSUtils.setValue(sheet, x, y++, transpose, color.getBlue());

				String roiName = (cage.getRoi() != null && cage.getRoi().getName() != null) ? cage.getRoi().getName()
						: "cage" + String.format("%03d", cage.getProperties().getCageID());
				XLSUtils.setValue(sheet, x, y++, transpose, roiName);

				ROIType roiType = ROIPersistenceUtils.detectROIType(cage.getRoi());
				XLSUtils.setValue(sheet, x, y++, transpose, roiType.toCsvString());

				// Geometry encoding: polygon or rectangle, as in CagesPersistenceLegacy
				if (cage.getRoi() != null && cage.getRoi() instanceof ROI2DPolygon) {
					ROI2DPolygon polyRoi = (ROI2DPolygon) cage.getRoi();
					Polygon2D polygon = polyRoi.getPolygon2D();
					XLSUtils.setValue(sheet, x, y++, transpose, polygon.npoints);
					for (int i = 0; i < polygon.npoints; i++) {
						XLSUtils.setValue(sheet, x, y++, transpose, (int) polygon.xpoints[i]);
						XLSUtils.setValue(sheet, x, y++, transpose, (int) polygon.ypoints[i]);
					}
				} else if (cage.getRoi() != null && cage.getRoi() instanceof ROI2DRectangle) {
					ROI2DRectangle rectRoi = (ROI2DRectangle) cage.getRoi();
					java.awt.Rectangle rect = rectRoi.getBounds();
					XLSUtils.setValue(sheet, x, y++, transpose, 4); // rectangle
					XLSUtils.setValue(sheet, x, y++, transpose, rect.x);
					XLSUtils.setValue(sheet, x, y++, transpose, rect.y);
					XLSUtils.setValue(sheet, x, y++, transpose, rect.width);
					XLSUtils.setValue(sheet, x, y++, transpose, rect.height);
				} else {
					XLSUtils.setValue(sheet, x, y++, transpose, 0);
				}

				nextEntityIndex++;
			}
		} catch (ExcelResourceException e) {
			throw new ExcelExportException("Failed to export cage limits worksheet", "export_cage_limits",
					exp != null ? exp.getResultsDirectory() : "unknown", e);
		}
	}

	private void exportFlyScaleForExperiment(Experiment exp, String charSeries) throws ExcelExportException {
		if (exp == null) {
			return;
		}
		try {
			SXSSFWorkbook workbook = resourceManager.getWorkbook();
			final String title = "XYScale";
			SXSSFSheet sheet = workbook.getSheet(title);
			boolean transpose = options.transpose;
			final String[] fields = new String[] { "Series", "mmPerPixelX", "mmPerPixelY" };
			int nextEntityIndex;
			if (sheet == null) {
				sheet = workbook.createSheet(title);
				for (int i = 0; i < fields.length; i++) {
					XLSUtils.setValue(sheet, 0, i, transpose, fields[i]);
				}
				nextEntityIndex = 1;
			} else {
				if (transpose) {
					nextEntityIndex = sheet.getLastRowNum() + 1;
				} else {
					org.apache.poi.xssf.streaming.SXSSFRow firstRow = sheet.getRow(0);
					short lastCell = (firstRow != null) ? firstRow.getLastCellNum() : 1;
					nextEntityIndex = (lastCell >= 0) ? lastCell : 1;
					if (nextEntityIndex < 1) {
						nextEntityIndex = 1;
					}
				}
			}
			int x = nextEntityIndex;
			int y = 0;
			String series = (charSeries != null && !charSeries.isEmpty()) ? charSeries : "exp";
			XLSUtils.setValue(sheet, x, y++, transpose, series);
			XLSUtils.setValue(sheet, x, y++, transpose, exp.getFlyMmPerPixelX());
			XLSUtils.setValue(sheet, x, y++, transpose, exp.getFlyMmPerPixelY());
		} catch (ExcelResourceException e) {
			throw new ExcelExportException("Failed to export XY scale worksheet", "export_xy_scale",
					exp != null ? exp.getResultsDirectory() : "unknown", e);
		}
	}

	/**
	 * Builds and exports four XYIMAGE series per cage for rectPosition components:
	 * x, y, w, h. Each component is written on a separate column, with DUM4
	 * indicating which parameter (\"x\",\"y\",\"w\",\"h\") is exported on that
	 * column.
	 */
	private int exportXYImageRectComponents(Experiment exp, SXSSFSheet sheet, int col0, String charSeries) {
		ensureFlyPositionTimesInitialized(exp);

		// Same separator convention as other exporters, using per-sheet start index.
		Point pt = new Point(computeNextSeriesIndex(sheet), 0);
		pt = writeExperimentSeparator(sheet, pt);

		ResultsOptions resultsOptions = new ResultsOptions();
		long kymoBin_ms = exp.getKymoBin_ms();
		if (kymoBin_ms <= 0) {
			kymoBin_ms = 60000;
		}
		resultsOptions.buildExcelStepMs = (int) kymoBin_ms;
		resultsOptions.relativeToMaximum = false;
		resultsOptions.subtractT0 = false;
		resultsOptions.correctEvaporation = false;
		resultsOptions.resultType = EnumResults.XYIMAGE;

		boolean transpose = options.transpose;

		for (Cage cage : exp.getCages().getCageList()) {
			if (cage == null) {
				continue;
			}
			FlyPositions flyPositions = cage.getFlyPositions();
			if (flyPositions == null || flyPositions.flyPositionList == null
					|| flyPositions.flyPositionList.isEmpty()) {
				continue;
			}

			Results[] componentResults = buildRectComponentResultsForCage(exp, cage, flyPositions, resultsOptions);
			String[] labels = { "x", "y", "w", "h" };

			for (int i = 0; i < componentResults.length; i++) {
				Results res = componentResults[i];
				if (res == null) {
					continue;
				}

				pt.y = 0;
				pt = writeExperimentFlyPositionInfos(sheet, pt, exp, charSeries, cage, EnumResults.XYIMAGE);
				// DUM4 row: label which rect component (x, y, w, h) is in this column
				XLSUtils.setValueAtColumn(sheet, new Point(pt.x, 0), EnumXLSColumnHeader.DUM4, transpose, labels[i]);

				writeXLSResult(sheet, pt, res);
				pt.x++;
			}
		}
		return pt.x;
	}

	/**
	 * Builds Results arrays for x, y, w, h components of rectPosition, binned over
	 * time using the same binning scheme as other fly exports.
	 */
	private Results[] buildRectComponentResultsForCage(Experiment exp, Cage cage, FlyPositions flyPositions,
			ResultsOptions resultsOptions) {
		Results[] resultsArray = new Results[4];

		if (flyPositions == null || flyPositions.flyPositionList == null || flyPositions.flyPositionList.isEmpty()) {
			return resultsArray;
		}

		final long firstAllMs = expAll.getSeqCamData().getFirstImageMs();
		final long lastAllMs = expAll.getSeqCamData().getLastImageMs();
		final long buildExcelStepMs = resultsOptions.buildExcelStepMs;

		if (lastAllMs <= firstAllMs || buildExcelStepMs <= 0) {
			return resultsArray;
		}

		final int nBins = (int) ((lastAllMs - firstAllMs) / buildExcelStepMs) + 1;
		final long expOffsetToAllMs = options.absoluteTime ? (exp.chainImageFirst_ms - firstAllMs) : 0L;
		final double sx = flyPositions.getMmPerPixelX();
		final double sy = flyPositions.getMmPerPixelY();

		for (int i = 0; i < resultsArray.length; i++) {
			resultsArray[i] = new Results("Cage_" + cage.getProperties().getCageID(),
					cage.getProperties().getCageNFlies(), cage.getProperties().getCageID(), 0, EnumResults.XYIMAGE);
			resultsArray[i].initValuesOutArray(nBins, Double.NaN);
		}

		// Build arrays of observed points (global time) and interpolate to bins.
		final int nPts = flyPositions.flyPositionList.size();
		double[] timesGlobalMs = new double[nPts];
		double[] xs = new double[nPts];
		double[] ys = new double[nPts];
		double[] ws = new double[nPts];
		double[] hs = new double[nPts];

		int kept = 0;
		for (FlyPosition pos : flyPositions.flyPositionList) {
			if (pos == null || pos.getRectangle2D() == null) {
				continue;
			}
			double x = pos.getRectangle2D().getX();
			double y = pos.getRectangle2D().getY();
			double w = pos.getRectangle2D().getWidth();
			double h = pos.getRectangle2D().getHeight();
			if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(w) || Double.isNaN(h)) {
				continue;
			}

			timesGlobalMs[kept] = expOffsetToAllMs + (double) pos.tMs;
			xs[kept] = x * sx;
			ys[kept] = y * sy;
			ws[kept] = w * sx;
			hs[kept] = h * sy;
			kept++;
		}

		if (kept <= 0) {
			return resultsArray;
		}

		double[] times = java.util.Arrays.copyOf(timesGlobalMs, kept);
		double[] xvals = java.util.Arrays.copyOf(xs, kept);
		double[] yvals = java.util.Arrays.copyOf(ys, kept);
		double[] wvals = java.util.Arrays.copyOf(ws, kept);
		double[] hvals = java.util.Arrays.copyOf(hs, kept);

		// Ensure interpolation arrays are monotonic in time.
		sortByTime(times, xvals, yvals, wvals, hvals);

		for (int binIndex = 0; binIndex < nBins; binIndex++) {
			long binTimeGlobalMs = firstAllMs + (long) binIndex * buildExcelStepMs;

			double xi = linearInterpolateNoExtrapolation(times, xvals, binTimeGlobalMs);
			double yi = linearInterpolateNoExtrapolation(times, yvals, binTimeGlobalMs);
			double wi = linearInterpolateNoExtrapolation(times, wvals, binTimeGlobalMs);
			double hi = linearInterpolateNoExtrapolation(times, hvals, binTimeGlobalMs);

			if (!Double.isNaN(xi)) {
				resultsArray[0].getValuesOut()[binIndex] = xi;
			}
			if (!Double.isNaN(yi)) {
				resultsArray[1].getValuesOut()[binIndex] = yi;
			}
			if (!Double.isNaN(wi)) {
				resultsArray[2].getValuesOut()[binIndex] = wi;
			}
			if (!Double.isNaN(hi)) {
				resultsArray[3].getValuesOut()[binIndex] = hi;
			}
		}

		return resultsArray;
	}

	/**
	 * Performs linear interpolation to find the value at the given time.
	 * 
	 * @param timesMs      Array of time values in milliseconds (must be sorted)
	 * @param values       Array of corresponding values
	 * @param targetTimeMs Target time in milliseconds
	 * @return Interpolated value, or NaN if target is outside the data range
	 */
	private double linearInterpolateNoExtrapolation(double[] timesMs, double[] values, long targetTimeMs) {
		if (timesMs == null || values == null || timesMs.length == 0 || timesMs.length != values.length) {
			return Double.NaN;
		}

		double targetTime = (double) targetTimeMs;

		// Do not extrapolate outside the data range
		if (targetTime < timesMs[0] || targetTime > timesMs[timesMs.length - 1]) {
			return Double.NaN;
		}

		// Find the two data points to interpolate between
		for (int i = 0; i < timesMs.length - 1; i++) {
			if (targetTime >= timesMs[i] && targetTime <= timesMs[i + 1]) {
				// Linear interpolation: y = y0 + (y1 - y0) * (x - x0) / (x1 - x0)
				double x0 = timesMs[i];
				double x1 = timesMs[i + 1];
				double y0 = values[i];
				double y1 = values[i + 1];

				if (x1 == x0) {
					return y0; // Avoid division by zero
				}

				double interpolated = y0 + (y1 - y0) * (targetTime - x0) / (x1 - x0);
				return interpolated;
			}
		}

		return Double.NaN;
	}

	/**
	 * Sorts the given time array in ascending order, applying the same permutation
	 * to each values array. All arrays must have the same length.
	 */
	private void sortByTime(double[] times, double[]... values) {
		if (times == null || times.length <= 1) {
			return;
		}
		if (values == null) {
			return;
		}
		final int n = times.length;
		for (double[] v : values) {
			if (v == null || v.length != n) {
				return;
			}
		}

		Integer[] order = new Integer[n];
		for (int i = 0; i < n; i++) {
			order[i] = i;
		}
		Arrays.sort(order, Comparator.comparingDouble(i -> times[i]));

		double[] tSorted = new double[n];
		for (int i = 0; i < n; i++) {
			tSorted[i] = times[order[i]];
		}
		System.arraycopy(tSorted, 0, times, 0, n);

		for (double[] v : values) {
			double[] vSorted = new double[n];
			for (int i = 0; i < n; i++) {
				vSorted[i] = v[order[i]];
			}
			System.arraycopy(vSorted, 0, v, 0, n);
		}
	}

	/**
	 * In multi-experiment exports we must append each experiment to the end of the
	 * current sheet. Using the global "startColumn" across all result types causes
	 * gaps on sheets that export fewer series (e.g. XYTOPCAGE vs XYIMAGE which
	 * exports 4 components).
	 */
	private int computeNextSeriesIndex(SXSSFSheet sheet) {
		if (sheet == null) {
			return 1;
		}
		if (options != null && options.transpose) {
			return Math.max(1, sheet.getLastRowNum() + 1);
		}
		SXSSFRow row0 = sheet.getRow(0);
		short lastCellNum = (row0 != null) ? row0.getLastCellNum() : -1;
		int next = (lastCellNum >= 0) ? lastCellNum : 1;
		return Math.max(1, next);
	}

	/**
	 * Ensures {@link FlyPosition#tMs} is populated for this experiment.
	 * <p>
	 * When cages measures are loaded from disk, fly positions often contain only the
	 * frame index ({@code flyIndexT}). The corresponding timestamps ({@code tMs})
	 * must be rebuilt from the camera image list; otherwise all points appear at
	 * t0 and exports beyond the first time column are blank.
	 */
	private void ensureFlyPositionTimesInitialized(Experiment exp) {
		if (exp == null || exp.getCages() == null || exp.getCages().getCageList() == null) {
			return;
		}

		boolean hasNonZeroTime = false;
		for (Cage cage : exp.getCages().getCageList()) {
			if (cage == null || cage.getFlyPositions() == null || cage.getFlyPositions().flyPositionList == null
					|| cage.getFlyPositions().flyPositionList.isEmpty()) {
				continue;
			}
			for (FlyPosition p : cage.getFlyPositions().flyPositionList) {
				if (p != null && p.tMs != 0) {
					hasNonZeroTime = true;
					break;
				}
			}
			if (hasNonZeroTime) {
				break;
			}
		}
		if (hasNonZeroTime) {
			return;
		}

		try {
			// Build camImages_ms[] as RELATIVE times from the first image of this experiment.
			// This makes FlyPosition.tMs directly comparable to the sheet's time bins when absoluteTime=false.
			long firstImageMs = (exp.getFirstImage_FileTime() != null) ? exp.getFirstImage_FileTime().toMillis() : -1L;
			if (firstImageMs <= 0 && exp.getSeqCamData() != null) {
				java.nio.file.attribute.FileTime ft = exp.getSeqCamData().getFileTimeFromStructuredName(0);
				if (ft != null) {
					firstImageMs = ft.toMillis();
				}
			}
			if (firstImageMs > 0) {
				exp.initTmsForFlyPositions(firstImageMs);
			}
		} catch (Exception e) {
			// Keep export resilient; if timing can't be initialized, callers will still
			// get descriptors and any pre-existing points.
		}
	}

	/**
	 * Gets the results for fly positions (legacy method, kept for backward compatibility).
	 * 
	 * @param exp            The experiment
	 * @param cage           The cage
	 * @param flyPositions   The fly positions
	 * @param resultsOptions The export options
	 * @return The XLS results
	 */
	public Results getResultsDataValuesFromFlyPositionMeasures(Experiment exp, Cage cage, FlyPositions flyPositions,
			ResultsOptions resultsOptions) {
		long firstImageMs = expAll.getSeqCamData().getFirstImageMs();
		long lastImageMs = expAll.getSeqCamData().getLastImageMs();
		long buildExcelStepMs = resultsOptions.buildExcelStepMs;

		if (lastImageMs <= firstImageMs || buildExcelStepMs <= 0) {
			return null;
		}

		long durationMs = lastImageMs - firstImageMs;
		int nBins = (int) (durationMs / buildExcelStepMs) + 1;

		Results results = new Results("Cage_" + cage.getProperties().getCageID(), cage.getProperties().getCageNFlies(),
				cage.getProperties().getCageID(), 0, resultsOptions.resultType);
		results.initValuesOutArray(nBins, Double.NaN);

		if (flyPositions == null || flyPositions.flyPositionList == null || flyPositions.flyPositionList.isEmpty()) {
			return results;
		}

		EnumResults resultType = resultsOptions.resultType;

		switch (resultType) {
		case DISTANCE:
			flyPositions.computeDistanceBetweenConsecutivePoints();
			break;
		case ISALIVE:
			flyPositions.computeIsAlive();
			break;
		case SLEEP:
			flyPositions.computeSleep();
			break;
		case ELLIPSEAXES:
			flyPositions.computeEllipseAxes();
			break;
		case XYIMAGE:
		case XYTOPCAGE:
		case XYTIPCAPS:
			// These types use direct coordinate values, no computation needed
			break;
		default:
			// Other result types not applicable to fly position measurements
			break;
		}

		for (int binIndex = 0; binIndex < nBins; binIndex++) {
			long binTimeMs = firstImageMs + binIndex * buildExcelStepMs;
			long binEndTime = binTimeMs + buildExcelStepMs;

			java.util.List<Double> binValues = new java.util.ArrayList<>();
			for (FlyPosition pos : flyPositions.flyPositionList) {
				if (pos.tMs >= binTimeMs && pos.tMs < binEndTime) {
					Double value = extractValueFromFlyPosition(pos, resultType);
					if (value != null && !Double.isNaN(value)) {
						binValues.add(value);
					}
				}
			}

			if (!binValues.isEmpty()) {
				double sum = 0.0;
				for (Double val : binValues) {
					sum += val;
				}
				results.getValuesOut()[binIndex] = sum / binValues.size();
			}
		}

		return results;
	}

	private Double extractValueFromFlyPosition(FlyPosition pos, EnumResults resultType) {
		switch (resultType) {
		case XYIMAGE:
		case XYTOPCAGE:
			return pos.getCenterRectangle().getY();
		case XYTIPCAPS:
			return pos.getCenterRectangle().getX();
		case DISTANCE:
			return pos.distanceMm;
		case ISALIVE:
			return pos.bAlive ? 1.0 : 0.0;
		case SLEEP:
			return pos.bSleep ? 1.0 : 0.0;
		case ELLIPSEAXES:
			return pos.axis1Mm;
		default:
			return pos.getCenterRectangle().getY();
		}
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
