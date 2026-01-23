package plugins.fmp.multitools.tools.toExcel;

import java.awt.Point;
import java.util.List;

import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ExperimentProperties;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.sequence.ImageLoader;
import plugins.fmp.multitools.tools.chart.builders.CageCapillarySeriesBuilder;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.Results;
import plugins.fmp.multitools.tools.results.ResultsCapillaries;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.toExcel.config.ExcelExportConstants;
import plugins.fmp.multitools.tools.toExcel.enums.EnumXLSColumnHeader;
import plugins.fmp.multitools.tools.toExcel.exceptions.ExcelExportException;
import plugins.fmp.multitools.tools.toExcel.utils.XLSUtils;

/**
 * Excel export implementation for gulp measurements. Uses the Template Method
 * pattern for structured export operations, following the same pattern as spot
 * and capillary exports.
 * 
 * <p>
 * This class exports gulp data including:
 * <ul>
 * <li>SUMGULPS - Cumulated volume of gulps</li>
 * <li>SUMGULPS_LR - Cumulated volume of gulps per cage (L+R)</li>
 * <li>NBGULPS - Number of gulps</li>
 * <li>AMPLITUDEGULPS - Amplitude of gulps</li>
 * <li>TTOGULP - Time to previous gulp</li>
 * <li>TTOGULP_LR - Time to previous gulp of either capillary</li>
 * <li>MARKOV_CHAIN - Markov chain transitions</li>
 * <li>AUTOCORREL - Autocorrelation</li>
 * <li>CROSSCORREL - Cross-correlation</li>
 * </ul>
 * </p>
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 */
public class XLSExportMeasuresFromGulp extends XLSExport {

	/**
	 * Exports gulp data for a single experiment.
	 * 
	 * @param exp         The experiment to export
	 * @param startColumn The starting column for export
	 * @param charSeries  The series identifier
	 * @return The next available column
	 * @throws ExcelExportException If export fails
	 */
	@Override
	protected int exportExperimentData(Experiment exp, ResultsOptions xlsExportOptions, int startColumn,
			String charSeries) throws ExcelExportException {

		OptionToResultsMapping[] mappings = {
			new OptionToResultsMapping(() -> options.sumGulps, EnumResults.SUMGULPS),
			new OptionToResultsMapping(() -> options.lrPI && options.sumGulps, EnumResults.SUMGULPS_LR),
			new OptionToResultsMapping(() -> options.nbGulps, EnumResults.NBGULPS),
			new OptionToResultsMapping(() -> options.amplitudeGulps, EnumResults.AMPLITUDEGULPS),
			new OptionToResultsMapping(() -> options.tToNextGulp, EnumResults.TTOGULP),
			new OptionToResultsMapping(() -> options.tToNextGulp_LR, EnumResults.TTOGULP_LR),
			new OptionToResultsMapping(() -> options.markovChain, EnumResults.MARKOV_CHAIN),
			new OptionToResultsMapping(() -> options.autocorrelation, EnumResults.AUTOCORREL),
			new OptionToResultsMapping(() -> options.crosscorrelation, EnumResults.CROSSCORREL),
			new OptionToResultsMapping(() -> options.crosscorrelationLR, EnumResults.CROSSCORREL_LR)
		};

		int colmax = 0;
		for (OptionToResultsMapping mapping : mappings) {
			if (mapping.isEnabled()) {
				for (EnumResults resultType : mapping.getResults()) {
					int col = exportResultType(exp, startColumn, charSeries, resultType, "gulp");
					if (col > colmax)
						colmax = col;
				}
			}
		}

		return colmax;
	}

	@Override
	protected int exportResultTypeToSheet(Experiment exp, SXSSFSheet sheet, EnumResults resultType, int col0,
			String charSeries) {
		Point pt = new Point(col0, 0);
		pt = writeExperimentSeparator(sheet, pt);

		// All gulp measures now use the unified computation path via
		// getCapillaryMeasuresForXLSPass1
		// which supports both computed and direct-access measures
		return xlsExportExperimentGulpDataToSheetUsingBuilder(exp, sheet, resultType, pt, charSeries);
	}

//	/**
//	 * Checks if a gulp result type is supported by the chart builder
//	 * (CageCapillarySeriesBuilder).
//	 * 
//	 * @param resultType The result type to check
//	 * @return true if supported, false otherwise
//	 */
//	private boolean isSupportedByChartBuilder(EnumResults resultType) {
//		return resultType == EnumResults.SUMGULPS || resultType == EnumResults.SUMGULPS_LR
//				|| resultType == EnumResults.NBGULPS || resultType == EnumResults.AMPLITUDEGULPS;
//	}

	/**
	 * Exports gulp data using the chart builder approach (for supported types).
	 * 
	 * @param exp        The experiment to export
	 * @param sheet      The sheet to write to
	 * @param resultType The export type
	 * @param pt         The starting point (after separator)
	 * @param charSeries The series identifier
	 * @return The next available column
	 */
	private int xlsExportExperimentGulpDataToSheetUsingBuilder(Experiment exp, SXSSFSheet sheet, EnumResults resultType,
			Point pt, String charSeries) {

		ResultsOptions resultsOptions = new ResultsOptions();
		long kymoBin_ms = exp.getKymoBin_ms();
		if (kymoBin_ms <= 0) {
			kymoBin_ms = 60000;
		}
		resultsOptions.buildExcelStepMs = (int) kymoBin_ms;
		resultsOptions.relativeToMaximum = false;
		resultsOptions.subtractT0 = false;
		resultsOptions.correctEvaporation = false; // Gulps don't use evaporation correction
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
					double scalingFactorToPhysicalUnits = exp.getCapillaries()
							.getScalingFactorToPhysicalUnits(resultType);
					results.transferDataValuesToValuesOut(scalingFactorToPhysicalUnits, resultType);

					pt.y = 0;
					pt = writeExperimentGulpInfos(sheet, pt, exp, charSeries, cage, cap, resultType);
					writeXLSResult(sheet, pt, results);
					pt.x++;
				}
			}
		}
		return pt.x;
	}

//	/**
//	 * Exports gulp data using the legacy approach (for unsupported types).
//	 * 
//	 * @param exp        The experiment to export
//	 * @param sheet      The sheet to write to
//	 * @param resultType The export type
//	 * @param col0       The starting column
//	 * @param charSeries The series identifier
//	 * @return The next available column
//	 */
//	private int xlsExportExperimentGulpDataToSheetLegacy(Experiment exp, SXSSFSheet sheet, EnumResults resultType,
//			int col0, String charSeries) {
//		Point pt = new Point(col0, 0);
//
//		double scalingFactorToPhysicalUnits = exp.getCapillaries().getScalingFactorToPhysicalUnits(resultType);
//
//		for (Capillary capillary : exp.getCapillaries().getList()) {
//			pt.y = 0;
//			pt = writeExperimentGulpInfos(sheet, pt, exp, charSeries, capillary, resultType);
//			Results results = getResultsDataValuesFromGulpMeasures(exp, capillary, options);
//			results.transferDataValuesToValuesOut(scalingFactorToPhysicalUnits, resultType);
//			writeXLSResult(sheet, pt, results);
//			pt.x++;
//		}
//		return pt.x;
//	}

	/**
	 * Gets the results for a gulp.
	 * 
	 * @param exp            The experiment
	 * @param capillary      The capillary
	 * @param resultsOptions The export options
	 * @return The XLS results
	 */
	public Results getResultsDataValuesFromGulpMeasures(Experiment exp, Capillary capillary,
			ResultsOptions resultsOptions) {
		int nOutputFrames = getNOutputFrames(exp, resultsOptions);

		// Create XLSResults with capillary properties
		Results results = new Results(capillary.getRoiName(), capillary.getProperties().getNFlies(),
				capillary.getCageID(), 0, resultsOptions.resultType);
		results.setStimulus(capillary.getStimulus());
		results.setConcentration(capillary.getConcentration());
		results.initValuesOutArray(nOutputFrames, Double.NaN);

		// Get bin durations
		long binData = exp.getKymoBin_ms();
		long binExcel = resultsOptions.buildExcelStepMs;

		// Get data from capillary (gulps are extracted via
		// getCapillaryMeasuresForXLSPass1)
		results.getDataFromCapillary(exp, capillary, binData, binExcel, resultsOptions, false);

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
		// For gulps, use kymograph timing (same as capillaries)
		long kymoFirst_ms = exp.getKymoFirst_ms();
		long kymoLast_ms = exp.getKymoLast_ms();

		if (kymoLast_ms <= kymoFirst_ms) {
			// Try to get from kymograph sequence
			if (exp.getSeqKymos() != null) {
				ImageLoader imgLoader = exp.getSeqKymos().getImageLoader();
				if (imgLoader != null) {
					long kymoBin_ms = exp.getKymoBin_ms();
					if (kymoBin_ms > 0) {
						kymoLast_ms = kymoFirst_ms + imgLoader.getNTotalFrames() * kymoBin_ms;
						exp.setKymoLast_ms(kymoLast_ms);
					}
				}
			}
		}

		long durationMs = kymoLast_ms - kymoFirst_ms;
		int nOutputFrames = (int) (durationMs / resultsOptions.buildExcelStepMs + 1);

		if (nOutputFrames <= 1) {
			handleExportError(exp, -1);
			// Fallback to a reasonable default
			nOutputFrames = 1000;
		}

		return nOutputFrames;
	}

	/**
	 * Writes experiment gulp information to the sheet.
	 * 
	 * @param sheet      The sheet to write to
	 * @param pt         The starting point
	 * @param exp        The experiment
	 * @param charSeries The series identifier
	 * @param cage       The cage
	 * @param capillary  The capillary
	 * @param resultType The export type
	 * @return The updated point
	 */
	protected Point writeExperimentGulpInfos(SXSSFSheet sheet, Point pt, Experiment exp, String charSeries,
			Cage cage, Capillary capillary, EnumResults resultType) {
		int x = pt.x;
		int y = pt.y;
		boolean transpose = options.transpose;

		// Write basic file information
		writeFileInformationForGulp(sheet, x, y, transpose, exp);

		// Write experiment properties
		writeExperimentPropertiesForGulp(sheet, x, y, transpose, exp, charSeries);

		// Write cage properties (same as capillary export)
		writeCageProperties(sheet, pt, transpose, cage);

		// Write capillary properties (same as capillary export)
		writeCapillaryProperties(sheet, x, y, transpose, capillary, charSeries, resultType);

		pt.y = y + getDescriptorRowCount();
		return pt;
	}

	/**
	 * Writes basic file information to the sheet (for gulps).
	 */
	private void writeFileInformationForGulp(SXSSFSheet sheet, int x, int y, boolean transpose, Experiment exp) {
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
	 * Writes experiment properties to the sheet (for gulps).
	 */
	private void writeExperimentPropertiesForGulp(SXSSFSheet sheet, int x, int y, boolean transpose, Experiment exp,
			String charSeries) {
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
	 * Writes capillary properties to the sheet.
	 */
	private void writeCapillaryProperties(SXSSFSheet sheet, int x, int y, boolean transpose, Capillary capillary,
			String charSeries, EnumResults resultType) {
		XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.CAP.getValue(), transpose,
				capillary.getSideDescriptor(resultType));
		XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.CAP_INDEX.getValue(), transpose,
				charSeries + "_" + capillary.getLast2ofCapillaryName());
		XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.CAP_VOLUME.getValue(), transpose, capillary.getVolume());
		XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.CAP_PIXELS.getValue(), transpose, capillary.getPixels());
		XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.CAP_STIM.getValue(), transpose, capillary.getStimulus());
		XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.CAP_CONC.getValue(), transpose,
				capillary.getConcentration());
		XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.CAP_NFLIES.getValue(), transpose, capillary.getNFlies());
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
		boolean isLRType = (resultType == EnumResults.SUMGULPS_LR);

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

		// Calculate the number of output bins
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
	 * @param timesMs  Array of time values in milliseconds (must be sorted)
	 * @param values   Array of corresponding values
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
	 * Handles export errors by logging them.
	 * 
	 * @param exp           The experiment
	 * @param nOutputFrames The number of output frames
	 */
	protected void handleExportError(Experiment exp, int nOutputFrames) {
		String error = String.format(
				"XLSExport:ExportError() ERROR in %s\n nOutputFrames=%d kymoFirstCol_Ms=%d kymoLastCol_Ms=%d",
				exp.getExperimentDirectory(), nOutputFrames, exp.getKymoFirst_ms(), exp.getKymoLast_ms());
		System.err.println(error);
	}
}
