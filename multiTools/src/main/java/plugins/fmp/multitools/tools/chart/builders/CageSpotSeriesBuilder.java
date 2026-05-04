package plugins.fmp.multitools.tools.chart.builders;

import java.awt.Color;
import java.awt.Paint;
import java.util.List;

import org.jfree.chart.ChartColor;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.CageProperties;
import plugins.fmp.multitools.experiment.cage.CageSpotStimulusAggregation;
import plugins.fmp.multitools.experiment.cage.CageSpotStimulusAggregation.AggregateSeries;
import plugins.fmp.multitools.experiment.cage.CageSpotStimulusAggregation.StimulusConcKey;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotMeasure;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.chart.ChartCageBuild;
import plugins.fmp.multitools.tools.chart.style.SeriesStyleCodec;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.toExcel.utils.SpotExcelTimeline;

/**
 * Builds cage datasets from Spot measurements.
 */
public class CageSpotSeriesBuilder implements CageSeriesBuilder {

	@Override
	public XYSeriesCollection build(Experiment exp, Cage cage, ResultsOptions options) {
		Spots allSpots = exp.getSpots();
		if (cage == null || allSpots == null) {
			Logger.debug("No spot data for cage");
			return new XYSeriesCollection();
		}
		List<Spot> spots = cage.getSpotList(allSpots);
		if (spots.isEmpty()) {
			Logger.debug("No spot data for cage");
			return new XYSeriesCollection();
		}

		if (options != null && options.resultType == EnumResults.AGG_SUMCLEAN) {
			XYSeriesCollection dataset = new XYSeriesCollection();
			addAggregateSeries(exp, cage, allSpots, options, dataset);
			ChartCageBuild.updateGlobalExtremaFromDataset(dataset);
			return dataset;
		}

		XYSeriesCollection dataset = new XYSeriesCollection();
		for (int si = 0; si < spots.size(); si++) {
			Spot spot = spots.get(si);
			String seriesKey = SpotChartSeriesKeys.key(spot, si);
			XYSeries series = createXYSeriesFromSpotMeasure(exp, spot, options, seriesKey);
			if (series == null)
				continue;
			series.setDescription(buildSeriesDescription(cage, spot));
			dataset.addSeries(series);
		}

		ChartCageBuild.updateGlobalExtremaFromDataset(dataset);
		return dataset;
	}

	private static String buildSeriesDescription(Cage cage, Spot spot) {
		CageProperties cageProp = cage.getProperties();
		Color color = spot.getProperties().getColor();
		return SeriesStyleCodec.buildDescription(cageProp.getCageID(), cageProp.getCageID(), cageProp.getCageNFlies(),
				color);
	}

	private static XYSeries createXYSeriesFromSpotMeasure(Experiment exp, Spot spot, ResultsOptions resultOptions,
			String seriesKey) {
		if (exp == null || spot == null || resultOptions == null || seriesKey == null)
			return null;
		XYSeries seriesXY = new XYSeries(seriesKey, false);

		if (exp.getSeqCamData().getTimeManager().getCamImagesTime_Ms() == null)
			exp.getSeqCamData().build_MsTimesArray_From_FileNamesList();
		double[] camImages_time_min = exp.getSeqCamData().getTimeManager().getCamImagesTime_Minutes();

		SpotMeasure spotMeasure = spot.getMeasurements(resultOptions.resultType);
		if (spotMeasure == null)
			return null;

		double divider = 1.0;
		if (resultOptions.relativeToMaximum && resultOptions.resultType != EnumResults.AREA_FLYPRESENT) {
			divider = spotMeasure.getMaximumValue();
			if (divider == 0)
				divider = 1.0;
		}

		double flyPresentToPercent = 1.0;
		if (resultOptions.resultType == EnumResults.AREA_FLYPRESENT) {
			flyPresentToPercent = 100.0 / (double) spot.getFlyPresentDenomPixelCount();
		}

		int npoints = spotMeasure.getCount();
		if (camImages_time_min != null && npoints > camImages_time_min.length)
			npoints = camImages_time_min.length;

		for (int j = 0; j < npoints; j++) {
			double x = camImages_time_min != null ? camImages_time_min[j] : j;
			double raw = spotMeasure.getValueAt(j);
			double y = resultOptions.resultType == EnumResults.AREA_FLYPRESENT ? raw * flyPresentToPercent
					: raw / divider;
			seriesXY.add(x, y);
		}
		return seriesXY;
	}

	private static void addAggregateSeries(Experiment exp, Cage cage, Spots allSpots, ResultsOptions options,
			XYSeriesCollection dataset) {
		if (exp == null || cage == null || allSpots == null || options == null || dataset == null) {
			return;
		}
		if (exp.getSeqCamData() == null || exp.getSeqCamData().getTimeManager() == null) {
			return;
		}

		SpotExcelTimeline.SpotExcelGrid grid = SpotExcelTimeline.buildForSpotExport(exp, options);
		List<AggregateSeries> aggregates = CageSpotStimulusAggregation.buildAggregates(cage, allSpots, options, grid);
		if (aggregates == null || aggregates.isEmpty()) {
			return;
		}

		List<StimulusConcKey> globalOrder = options.spotAggregateGlobalKeyOrder;
		int ai = 0;
		for (AggregateSeries agg : aggregates) {
			if (agg == null || agg.values == null || agg.values.isEmpty() || agg.key == null) {
				continue;
			}
			String seriesKey = SpotChartSeriesKeys.keyAggregate(cage.getProperties().getCageID(), agg.key.stimulus,
					agg.key.concentration, ai++);
			XYSeries seriesXY = new XYSeries(seriesKey, false);

			for (int k = 0; k < agg.values.size(); k++) {
				long queryMs = grid.getClipStartMs() + (long) k * grid.getExcelDeltaMs();
				double x = minutesAtElapsedMs(exp, queryMs);
				double y = agg.values.get(k) != null ? agg.values.get(k) : Double.NaN;
				if (!Double.isFinite(y)) {
					continue;
				}
				seriesXY.add(x, y);
			}

			int paletteIndex = ai - 1;
			if (globalOrder != null && !globalOrder.isEmpty()) {
				int idx = globalOrder.indexOf(new StimulusConcKey(agg.key.stimulus, agg.key.concentration));
				if (idx >= 0) {
					paletteIndex = idx;
				}
			}
			Color color = aggregatePaletteColor(paletteIndex);
			seriesXY.setDescription(SeriesStyleCodec.buildDescription(cage.getProperties().getCageID(),
					cage.getProperties().getCageID(), cage.getProperties().getCageNFlies(), color));
			dataset.addSeries(seriesXY);
		}
	}

	private static Color aggregatePaletteColor(int index) {
		Paint[] paints = ChartColor.createDefaultPaintArray();
		if (paints == null || paints.length == 0) {
			return Color.BLACK;
		}
		Paint p = paints[Math.max(0, index) % paints.length];
		return p instanceof Color ? (Color) p : Color.BLACK;
	}

	private static double minutesAtElapsedMs(Experiment exp, long elapsedMsFromFirstFrame) {
		if (exp == null || exp.getSeqCamData() == null) {
			return elapsedMsFromFirstFrame / 60000.0;
		}
		if (exp.getSeqCamData().getTimeManager().getCamImagesTime_Ms() == null) {
			exp.getSeqCamData().build_MsTimesArray_From_FileNamesList();
		}
		long[] cam = exp.getSeqCamData().getTimeManager().getCamImagesTime_Ms();
		if (cam == null || cam.length < 1) {
			return elapsedMsFromFirstFrame / 60000.0;
		}
		long origin = cam[0];
		long queryAbs = origin + elapsedMsFromFirstFrame;

		if (cam.length == 1) {
			return (queryAbs - origin) / 60000.0;
		}

		if (queryAbs <= cam[0]) {
			return 0.0;
		}
		if (queryAbs >= cam[cam.length - 1]) {
			return (cam[cam.length - 1] - origin) / 60000.0;
		}

		for (int i = 1; i < cam.length; i++) {
			long hi = cam[i];
			if (queryAbs > hi) {
				continue;
			}
			long lo = cam[i - 1];
			long dt = hi - lo;
			if (dt <= 0L) {
				return (hi - origin) / 60000.0;
			}
			double alpha = (queryAbs - lo) / (double) dt;
			double m0 = (lo - origin) / 60000.0;
			double m1 = (hi - origin) / 60000.0;
			return m0 + alpha * (m1 - m0);
		}

		return (cam[cam.length - 1] - origin) / 60000.0;
	}
}
