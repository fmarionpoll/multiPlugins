package plugins.fmp.multitools.tools.toExcel;

import java.awt.Point;
import java.util.List;

import org.apache.poi.xssf.streaming.SXSSFSheet;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.CageSpotStimulusAggregation;
import plugins.fmp.multitools.experiment.cage.CageSpotStimulusAggregation.AggregateSeries;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotProperties;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.Results;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.toExcel.utils.SpotExcelTimeline;

/**
 * Excel export for spot measures aggregated at cage level by (stimulus, concentration).
 *
 * For AREA_SUMCLEAN, exports the sum of per-spot normalized consumption series:
 *   (maxBaseline - v(t)) / maxBaseline
 * where maxBaseline is computed from the beginning of the series (see {@link ResultsOptions} parameters).
 */
public class XLSExportMeasuresFromSpotAggregatedByStimulusConc extends XLSExportSpots {

	@Override
	protected int exportExperimentData(Experiment exp, ResultsOptions resultsOptions, int startColumn, String charSeries)
			throws plugins.fmp.multitools.tools.toExcel.exceptions.ExcelExportException {
		if (resultsOptions == null || exp == null) {
			return startColumn;
		}
		boolean aggregateExport = resultsOptions.spotAggregateByStimulusConc
				|| resultsOptions.resultType == EnumResults.AGG_SUMCLEAN;
		if (!resultsOptions.spotAreas || !aggregateExport) {
			return startColumn;
		}
		int colmax = exportResultType(exp, startColumn, charSeries, EnumResults.AREA_SUMCLEAN, "spot_aggregate");
		return colmax;
	}

	@Override
	protected int exportResultTypeToSheet(Experiment exp, SXSSFSheet sheet, EnumResults resultType, int col0,
			String charSeries) {
		if (resultType != EnumResults.AREA_SUMCLEAN && resultType != EnumResults.AGG_SUMCLEAN) {
			return col0;
		}
		Point pt = new Point(col0, 0);
		pt = writeExperimentSeparator(sheet, pt);

		SpotExcelTimeline.SpotExcelGrid grid = SpotExcelTimeline.buildForSpotExport(exp, options);
		Spots allSpots = exp.getSpots();
		EnumResults savedRt = options.resultType;
		EnumResults buildRt = EnumResults.AGG_SUMCLEAN.equals(savedRt) ? EnumResults.AGG_SUMCLEAN
				: EnumResults.AREA_SUMCLEAN;
		for (Cage cage : exp.getCages().cagesList) {
			if (options.onlyalive && cage.getProperties().getCageNFlies() < 1) {
				continue;
			}
			if (cage.getSpotList(allSpots).isEmpty()) {
				continue;
			}

			options.resultType = buildRt;
			List<AggregateSeries> series = CageSpotStimulusAggregation.buildAggregates(cage, allSpots, options, grid);
			for (AggregateSeries s : series) {
				if (s == null || s.values == null || s.values.isEmpty()) {
					continue;
				}
				pt.y = 0;

				Spot synthetic = buildSyntheticSpotForAggregate(cage, s);
				pt = writeExperimentSpotInfos(sheet, pt, exp, charSeries, cage, synthetic, resultType);

				Results r = new Results(cage.getProperties(), synthetic.getProperties(), s.values.size());
				r.setDataValues(s.values);
				r.initValuesOutArray(s.values.size(), Double.NaN);
				// Keep scaling consistent with existing spot exports.
				double scaling = allSpots.getScalingFactorToPhysicalUnits(resultType);
				r.transferDataValuesToValuesOut(scaling, resultType);
				writeXLSResult(sheet, pt, r);
				pt.x++;
			}
		}
		options.resultType = savedRt;

		Logger.info("XLSExportMeasuresFromSpotAggregatedByStimulusConc: exported columns up to " + pt.x);
		return pt.x;
	}

	private Spot buildSyntheticSpotForAggregate(Cage cage, AggregateSeries s) {
		Spot spot = new Spot();
		SpotProperties p = spot.getProperties();
		p.setCageID(cage.getProperties().getCageID());
		p.setCagePosition(-1);
		p.setCageRow(-1);
		p.setCageColumn(-1);
		p.setStimulus(s.key.stimulus);
		p.setConcentration(s.key.concentration);
		p.setSpotVolume(Double.NaN);
		// Reuse the pixels column to store how many spots contributed.
		p.setSpotNPixels(Math.max(0, s.nSpotsExposed));
		p.setName("AGG");
		p.setStimulusI("N=" + s.nSpotsExposed);
		return spot;
	}
}

