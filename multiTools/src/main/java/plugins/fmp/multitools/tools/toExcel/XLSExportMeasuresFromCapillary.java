package plugins.fmp.multiSPOTS96.tools.toExcel;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cages.Cage;
import plugins.fmp.multitools.experiment.capillaries.Capillary;
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

		List<EnumResults> resultsToExport = new ArrayList<EnumResults>();

		if (options.topLevel) {
			resultsToExport.add(EnumResults.TOPRAW);
			resultsToExport.add(EnumResults.TOPLEVEL);
		}

		if (options.lrPI) {
			resultsToExport.add(EnumResults.TOPLEVEL_LR);
		}

		if (options.bottomLevel) {
			resultsToExport.add(EnumResults.BOTTOMLEVEL);
		}
		if (options.derivative) {
			resultsToExport.add(EnumResults.DERIVEDVALUES);
		}

		int colmax = 0;
		exp.dispatchCapillariesToCages();
		for (EnumResults resultType : resultsToExport) {
			int col = getCapDataAndExport(exp, startColumn, charSeries, resultType);
			if (col > colmax)
				colmax = col;
		}

		return colmax;
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
	 * Finds the appropriate XYSeries for a given capillary based on the result type.
	 * For LR types, maps L capillary to Sum series and R capillary to PI series.
	 * For regular types, finds series matching the capillary side.
	 * 
	 * @param dataset    The XYSeriesCollection from the builder
	 * @param exp       The experiment
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
	 * @param series         The XYSeries to convert (X values in minutes from start)
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
		ResultsCapillaries results = new ResultsCapillaries(cap.getKymographName(), cap.getProperties().nFlies,
				cap.getCageID(), 0, resultType);
		results.setStimulus(cap.getStimulus());
		results.setConcentration(cap.getConcentration());
		results.setCapSide(cap.getCageID() + "_" + cap.getCapillarySide());
		results.initValuesOutArray(nBins, Double.NaN);

		// Convert XYSeries data points to binned array
		// XYSeries X values are in minutes from experiment start (time 0)
		// Need to convert to milliseconds and map to bins
		for (int i = 0; i < series.getItemCount(); i++) {
			double timeMinutes = series.getX(i).doubleValue();
			double value = series.getY(i).doubleValue();

			// Convert minutes to milliseconds (time from start)
			long timeMsFromStart = (long) (timeMinutes * 60.0 * 1000.0);

			// Calculate bin index (bins are also relative to firstImageMs)
			// Since both are relative to start, we can directly calculate the bin
			if (timeMsFromStart < 0) {
				continue; // Skip data points before experiment start
			}

			int binIndex = (int) (timeMsFromStart / buildExcelStepMs);
			if (binIndex >= 0 && binIndex < nBins) {
				results.getValuesOut()[binIndex] = value;
			}
		}

		return results;
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
