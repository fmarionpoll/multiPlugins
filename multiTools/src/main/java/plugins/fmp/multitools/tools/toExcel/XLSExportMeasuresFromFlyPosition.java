package plugins.fmp.multitools.tools.toExcel;

import java.awt.Point;

import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ExperimentProperties;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.FlyPosition;
import plugins.fmp.multitools.experiment.cage.FlyPositions;
import plugins.fmp.multitools.experiment.sequence.ImageLoader;
import plugins.fmp.multitools.experiment.sequence.TimeManager;
import plugins.fmp.multitools.tools.chart.builders.CageFlyPositionSeriesBuilder;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.Results;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.toExcel.config.ExcelExportConstants;
import plugins.fmp.multitools.tools.toExcel.enums.EnumXLSColumnHeader;
import plugins.fmp.multitools.tools.toExcel.exceptions.ExcelExportException;
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

		OptionToResultsMapping[] mappings = {
			new OptionToResultsMapping(() -> options.xyImage, EnumResults.XYIMAGE),
			new OptionToResultsMapping(() -> options.xyCage, EnumResults.XYTOPCAGE),
			new OptionToResultsMapping(() -> options.xyCapillaries, EnumResults.XYTIPCAPS),
			new OptionToResultsMapping(() -> options.ellipseAxes, EnumResults.ELLIPSEAXES),
			new OptionToResultsMapping(() -> options.distance, EnumResults.DISTANCE),
			new OptionToResultsMapping(() -> options.alive, EnumResults.ISALIVE),
			new OptionToResultsMapping(() -> options.sleep, EnumResults.SLEEP)
		};

		int colmax = 0;
		for (OptionToResultsMapping mapping : mappings) {
			if (mapping.isEnabled()) {
				for (EnumResults resultType : mapping.getResults()) {
					int col = exportResultType(exp, startColumn, charSeries, resultType, "fly position");
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
				writeXLSResult(sheet, pt, results);
				pt.x++;
			}
		}
		return pt.x;
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
		Results results = new Results("Cage_" + cage.getProperties().getCageID(), cage.getProperties().getCageNFlies(),
				cage.getProperties().getCageID(), 0, resultType);
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
			return pos.distance;
		case ISALIVE:
			return pos.bAlive ? 1.0 : 0.0;
		case SLEEP:
			return pos.bSleep ? 1.0 : 0.0;
		case ELLIPSEAXES:
			return pos.axis1;
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
