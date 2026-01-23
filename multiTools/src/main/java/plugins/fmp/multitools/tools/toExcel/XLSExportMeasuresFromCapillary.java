package plugins.fmp.multitools.tools.toExcel;

import java.awt.Point;
import java.util.List;

import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.tools.chart.builders.CageCapillarySeriesBuilder;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.Results;
import plugins.fmp.multitools.tools.results.ResultsCapillaries;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.toExcel.config.ExcelExportConstants;
import plugins.fmp.multitools.tools.toExcel.enums.EnumXLSColumnHeader;
import plugins.fmp.multitools.tools.toExcel.exceptions.ExcelExportException;
import plugins.fmp.multitools.tools.toExcel.exceptions.ExcelResourceException;
import plugins.fmp.multitools.tools.toExcel.utils.XLSUtils;

/**
 * Excel export implementation for capillary measurements. Uses the Template
 * Method pattern for structured export operations, following the same pattern
 * as spot exports.
 * 
 * <p>
 * This class exports capillary data including:
 * <ul>
 * <li>TOPRAW - Raw top liquid level</li>
 * <li>TOPLEVEL - Top liquid level compensated for evaporation</li>
 * <li>TOPLEVEL_LR - Volume consumed in capillaries per cage</li>
 * <li>DERIVEDVALUES - Derived top liquid level</li>
 * <li>BOTTOMLEVEL - Bottom liquid level</li>
 * <li>TOPLEVELDELTA - Top liquid consumed (t - t-1)</li>
 * </ul>
 * </p>
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 */
public class XLSExportMeasuresFromCapillary extends XLSExport {

	/**
	 * Exports capillary data for a single experiment.
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

		OptionToResultsMapping[] mappings = {
			new OptionToResultsMapping(() -> options.topLevel, EnumResults.TOPRAW, EnumResults.TOPLEVEL),
			new OptionToResultsMapping(() -> options.lrPI, EnumResults.TOPLEVEL_LR),
			new OptionToResultsMapping(() -> options.bottomLevel, EnumResults.BOTTOMLEVEL),
			new OptionToResultsMapping(() -> options.derivative, EnumResults.DERIVEDVALUES)
		};

		exp.dispatchCapillariesToCages();
		int colmax = 0;
		for (OptionToResultsMapping mapping : mappings) {
			if (mapping.isEnabled()) {
				for (EnumResults resultType : mapping.getResults()) {
					int col = getCapDataAndExport(exp, startColumn, charSeries, resultType);
					if (col > colmax)
						colmax = col;
				}
			}
		}

		return colmax;
	}

	private static class OptionToResultsMapping {
		private final java.util.function.Supplier<Boolean> optionCheck;
		private final List<EnumResults> results;

		OptionToResultsMapping(java.util.function.Supplier<Boolean> optionCheck, EnumResults... results) {
			this.optionCheck = optionCheck;
			this.results = java.util.Arrays.asList(results);
		}

		boolean isEnabled() {
			return optionCheck.get();
		}

		List<EnumResults> getResults() {
			return results;
		}
	}

	protected int getCapDataAndExport(Experiment exp, int col0, String charSeries, EnumResults resultType)
			throws ExcelExportException {
		try {
			options.resultType = resultType;
			SXSSFSheet sheet = getSheet(resultType.toString(), resultType);
			int colmax = xlsExportExperimentCapDataToSheet(exp, sheet, resultType, col0, charSeries);
			if (options.onlyalive) {
				sheet = getSheet(resultType.toString() + ExcelExportConstants.ALIVE_SHEET_SUFFIX, resultType);
				xlsExportExperimentCapDataToSheet(exp, sheet, resultType, col0, charSeries);
			}

			return colmax;
		} catch (ExcelResourceException e) {
			throw new ExcelExportException("Failed to export spot data", "get_spot_data_and_export",
					resultType.toString(), e);
		}
	}

	protected int xlsExportExperimentCapDataToSheet(Experiment exp, SXSSFSheet sheet, EnumResults resultType, int col0,
			String charSeries) {
		Point pt = new Point(col0, 0);
		pt = writeExperimentSeparator(sheet, pt);

		ResultsOptions resultsOptions = new ResultsOptions();
		long kymoBin_ms = exp.getKymoBin_ms();
		if (kymoBin_ms <= 0) {
			kymoBin_ms = 60000;
		}
		resultsOptions.buildExcelStepMs = (int) kymoBin_ms;
		resultsOptions.relativeToMaximum = false;
		resultsOptions.subtractT0 = false;
		resultsOptions.correctEvaporation = resultType == EnumResults.TOPLEVEL ? true : false;
		resultsOptions.resultType = resultType;

		exp.dispatchCapillariesToCages();
		exp.getCages().prepareComputations(exp, resultsOptions);

		CageCapillarySeriesBuilder builder = new CageCapillarySeriesBuilder();

		for (Cage cage : exp.getCages().getCageList()) {
			if (cage == null) {
				continue;
			}

			List<Capillary> capillaries = cage.getCapillaries(exp.getCapillaries());
			if (capillaries == null || capillaries.isEmpty()) {
				continue;
			}

			XYSeriesCollection dataset = builder.build(exp, cage, resultsOptions);
			if (dataset == null || dataset.getSeriesCount() == 0) {
				continue;
			}
			for (Capillary cap : capillaries) {
				XYSeries series = findSeriesForCapillary(dataset, exp, cage, cap, resultType);
				if (series == null) {
					continue;
				}

				Results results = convertXYSeriesToResults(exp, cap, series, resultsOptions, resultType);
				if (results != null) {
					pt.y = 0;
					pt = writeExperimentCapInfos(sheet, pt, exp, charSeries, cage, cap, resultType);
					writeXLSResult(sheet, pt, results);
					pt.x++;
				}
			}
		}
		return pt.x;
	}

	/**
	 * Finds the appropriate XYSeries for a given capillary based on the result
	 * type. For LR types, maps L capillary to Sum series and R capillary to PI
	 * series. For regular types, finds series matching the capillary side.
	 * 
	 * @param dataset    The XYSeriesCollection from the builder
	 * @param exp        The experiment
	 * @param cage       The cage containing the capillary
	 * @param cap        The capillary to find series for
	 * @param resultType The export result type
	 * @return The matching XYSeries or null if not found
	 */
	private XYSeries findSeriesForCapillary(XYSeriesCollection dataset, Experiment exp, Cage cage, Capillary cap,
			EnumResults resultType) {
		if (dataset == null || cage == null || cap == null) {
			return null;
		}

		String capSide = cap.getCapillarySide();
		boolean isLRType = (resultType == EnumResults.TOPLEVEL_LR || resultType == EnumResults.TOPLEVELDELTA_LR
				|| resultType == EnumResults.SUMGULPS_LR);

		if (isLRType) {
			// For LR types: L capillary -> Sum, R capillary -> PI
			if (capSide != null && (capSide.contains("L") || capSide.contains("1"))) {
				return findSeriesByKey(dataset, cage.getCageID() + "_Sum");
			} else if (capSide != null && (capSide.contains("R") || capSide.contains("2"))) {
				return findSeriesByKey(dataset, cage.getCageID() + "_PI");
			}
			// Fallback: first capillary is L (Sum), second is R (PI)
			List<Capillary> caps = cage.getCapillaries(exp.getCapillaries());
			int capIndex = caps.indexOf(cap);
			if (capIndex == 0) {
				return findSeriesByKey(dataset, cage.getCageID() + "_Sum");
			} else if (capIndex == 1) {
				return findSeriesByKey(dataset, cage.getCageID() + "_PI");
			}
		} else {
			// For regular types: find series matching capillary side
			String expectedKey = cage.getCageID() + "_" + capSide;
			return findSeriesByKey(dataset, expectedKey);
		}

		return null;
	}

	/**
	 * Finds a series in the collection by its key.
	 * 
	 * @param dataset The XYSeriesCollection to search
	 * @param key     The series key to find
	 * @return The matching XYSeries or null if not found
	 */
	private XYSeries findSeriesByKey(XYSeriesCollection dataset, String key) {
		if (dataset == null || key == null) {
			return null;
		}

		for (int i = 0; i < dataset.getSeriesCount(); i++) {
			XYSeries series = dataset.getSeries(i);
			if (key.equals(series.getKey())) {
				return series;
			}
		}
		return null;
	}

	/**
	 * Converts an XYSeries (time in minutes from start, value) to a Results object
	 * with binned values indexed by time bin.
	 * 
	 * @param exp            The experiment
	 * @param cap            The capillary
	 * @param series         The XYSeries to convert (X values in minutes from
	 *                       start)
	 * @param resultsOptions The export options
	 * @param resultType     The result type
	 * @return A Results object with valuesOut array populated
	 */
	private Results convertXYSeriesToResults(Experiment exp, Capillary cap, XYSeries series,
			ResultsOptions resultsOptions, EnumResults resultType) {
		if (exp == null || cap == null || series == null || resultsOptions == null) {
			return null;
		}

		// Calculate the number of output bins based on expAll (combined time range)
		long firstImageMs = expAll.getSeqCamData().getFirstImageMs();
		long lastImageMs = expAll.getSeqCamData().getLastImageMs();
		long buildExcelStepMs = resultsOptions.buildExcelStepMs;

		if (lastImageMs <= firstImageMs || buildExcelStepMs <= 0) {
			return null;
		}

		long durationMs = lastImageMs - firstImageMs;
		int nBins = (int) (durationMs / buildExcelStepMs) + 1;

		// Create Results object
		ResultsCapillaries results = new ResultsCapillaries(cap.getKymographName(), cap.getProperties().getNFlies(),
				cap.getCageID(), 0, resultType);
		results.setStimulus(cap.getStimulus());
		results.setConcentration(cap.getConcentration());
		results.setCapSide(cap.getCageID() + "_" + cap.getCapillarySide());
		results.initValuesOutArray(nBins, Double.NaN);

		// Get experiment's actual duration for validation
		// XYSeries time values are in minutes from experiment start (time 0)
		// Calculate the actual experiment duration from image count and bin duration
		long binMs = exp.getSeqCamData().getTimeManager().getBinImage_ms();
		int nFrames = exp.getSeqCamData().getImageLoader().getNTotalFrames();
		long expDurationMs = 0;
		if (binMs > 0 && nFrames > 0) {
			// Duration = (nFrames - 1) * binMs (time from first to last image)
			expDurationMs = (nFrames - 1) * binMs;
		} else {
			// Fallback: use binLast_ms if available
			long expLastMs = exp.getSeqCamData().getTimeManager().getBinLast_ms();
			long expFirstMs = exp.getSeqCamData().getTimeManager().getBinFirst_ms();
			if (expLastMs > expFirstMs) {
				expDurationMs = expLastMs - expFirstMs;
			}
		}

		// Build array of data points with times in milliseconds for interpolation
		// XYSeries X values are in minutes from experiment start (time 0)
		int nDataPoints = series.getItemCount();
		if (nDataPoints == 0) {
			return results;
		}

		double[] dataTimesMs = new double[nDataPoints];
		double[] dataValues = new double[nDataPoints];
		for (int i = 0; i < nDataPoints; i++) {
			double timeMinutes = series.getX(i).doubleValue();
			dataTimesMs[i] = timeMinutes * 60.0 * 1000.0; // Convert to milliseconds
			dataValues[i] = series.getY(i).doubleValue();
		}

		// Use linear interpolation to fill all output bins
		// For each output bin, find the interpolated value from the nearest data points
		for (int binIndex = 0; binIndex < nBins; binIndex++) {
			long binTimeMs = firstImageMs + binIndex * buildExcelStepMs;

			// Check if bin time is within experiment's actual duration
			if (expDurationMs > 0 && binTimeMs > expDurationMs) {
				continue; // Skip bins beyond experiment duration
			}

			// Find interpolated value at binTimeMs
			double interpolatedValue = linearInterpolate(dataTimesMs, dataValues, binTimeMs);
			if (!Double.isNaN(interpolatedValue)) {
				results.getValuesOut()[binIndex] = interpolatedValue;
			}
		}

		return results;
	}

	/**
	 * Performs linear interpolation to find the value at the given time.
	 * 
	 * @param timesMs      Array of time values in milliseconds (must be sorted)
	 * @param values       Array of corresponding values
	 * @param targetTimeMs Target time in milliseconds
	 * @return Interpolated value, or NaN if target is outside the data range
	 */
	private double linearInterpolate(double[] timesMs, double[] values, long targetTimeMs) {
		if (timesMs == null || values == null || timesMs.length == 0 || timesMs.length != values.length) {
			return Double.NaN;
		}

		double targetTime = (double) targetTimeMs;

		// Check if target is before first data point
		if (targetTime <= timesMs[0]) {
			return values[0];
		}

		// Check if target is after last data point
		if (targetTime >= timesMs[timesMs.length - 1]) {
			return values[values.length - 1];
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
	 * Writes experiment capillary information to the sheet.
	 * 
	 * @param sheet      The sheet to write to
	 * @param pt         The starting point
	 * @param exp        The experiment
	 * @param charSeries The series identifier
	 * @param capillary  The capillary
	 * @param resultType The export type
	 * @return The updated point
	 */
	protected Point writeExperimentCapInfos(SXSSFSheet sheet, Point pt, Experiment exp, String charSeries, Cage cage,
			Capillary capillary, EnumResults resultType) {

		boolean transpose = options.transpose;

		writeFileInformation(sheet, pt, transpose, exp);
		writeExperimentProperties(sheet, pt, transpose, exp, charSeries);
		writeCageProperties(sheet, pt, transpose, cage);
		writeCapProperties(sheet, pt, transpose, capillary, charSeries, resultType);

		pt.y += getDescriptorRowCount();
		return pt;
	}

	private void writeCapProperties(SXSSFSheet sheet, Point pt, boolean transpose, Capillary capillary,
			String charSeries, EnumResults resultType) {
		XLSUtils.setValueAtColumn(sheet, pt, EnumXLSColumnHeader.CAP, transpose,
				capillary.getSideDescriptor(resultType));
		XLSUtils.setValueAtColumn(sheet, pt, EnumXLSColumnHeader.CAP_INDEX, transpose,
				charSeries + "_" + capillary.getLast2ofCapillaryName());
		XLSUtils.setValueAtColumn(sheet, pt, EnumXLSColumnHeader.CAP_VOLUME, transpose, capillary.getVolume());
		XLSUtils.setValueAtColumn(sheet, pt, EnumXLSColumnHeader.CAP_PIXELS, transpose, capillary.getPixels());
		XLSUtils.setValueAtColumn(sheet, pt, EnumXLSColumnHeader.CAP_STIM, transpose, capillary.getStimulus());
		XLSUtils.setValueAtColumn(sheet, pt, EnumXLSColumnHeader.CAP_CONC, transpose, capillary.getConcentration());
		XLSUtils.setValueAtColumn(sheet, pt, EnumXLSColumnHeader.CAP_NFLIES, transpose, capillary.getNFlies());
		XLSUtils.setValueAtColumn(sheet, pt, EnumXLSColumnHeader.DUM4, transpose, resultType.toString());
	}

}
