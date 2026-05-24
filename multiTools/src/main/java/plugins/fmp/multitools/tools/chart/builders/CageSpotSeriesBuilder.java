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
import plugins.fmp.multitools.experiment.cage.CageSpotAggregateSeries;
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

		if (options != null && (options.resultType == EnumResults.AGG_SUMCLEAN
				|| options.resultType == EnumResults.AGG_SUMCLEAN_V5 || options.resultType == EnumResults.AGG_AREA_COUNT_V5
				|| options.resultType == EnumResults.AGG_SUMCLEAN_V6 || options.resultType == EnumResults.AGG_AREA_COUNT_V6
				|| options.resultType == EnumResults.AGG_MEDIANREF)) {
			XYSeriesCollection dataset = new XYSeriesCollection();
			addAggregateSeries(exp, cage, allSpots, options, dataset);
			updateGlobalExtremaExcludingMedianRef(dataset);
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

	private static void updateGlobalExtremaExcludingMedianRef(XYSeriesCollection full) {
		if (full == null || full.getSeriesCount() == 0) {
			return;
		}
		XYSeriesCollection primary = new XYSeriesCollection();
		for (int i = 0; i < full.getSeriesCount(); i++) {
			String key = (String) full.getSeriesKey(i);
			if (SpotChartSeriesKeys.isMedianRefSeriesKey(key)) {
				continue;
			}
			primary.addSeries(full.getSeries(i));
		}
		if (primary.getSeriesCount() > 0) {
			ChartCageBuild.updateGlobalExtremaFromDataset(primary);
		} else {
			ChartCageBuild.updateGlobalExtremaFromDataset(full);
		}
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

		double[] camImagesTimeMin = exp.getSeqCamData().getTimeManager().getCamImagesTime_Minutes();
		List<CageSpotAggregateSeries> cached = cage.getSpotAggregates().getEntries();
		List<CageSpotAggregateSeries> cachedRows = (cached != null && !cached.isEmpty()) ? cached : null;
		List<AggregateSeries> aggregates = cachedRows != null ? null
				: CageSpotStimulusAggregation.buildAggregatesOnNativeSamples(exp, cage, allSpots, options);

		List<StimulusConcKey> globalOrder = options.spotAggregateGlobalKeyOrder;
		int ai = 0;
		if (cachedRows != null) {
			for (CageSpotAggregateSeries row : cachedRows) {
				if (row == null || row.getKey() == null || row.getMeasure() == null) {
					continue;
				}
				StimulusConcKey key = row.getKey();
				String seriesKey = SpotChartSeriesKeys.keyAggregate(cage.getProperties().getCageID(), key.stimulus,
						key.concentration, ai++);
				XYSeries seriesXY = new XYSeries(seriesKey, false);
				int npoints = row.getMeasure().getCount();
				if (camImagesTimeMin != null && npoints > camImagesTimeMin.length) {
					npoints = camImagesTimeMin.length;
				}
				for (int k = 0; k < npoints; k++) {
					double x = camImagesTimeMin != null ? camImagesTimeMin[k] : k;
					double y = row.getMeasure().getValueAt(k);
					if (!Double.isFinite(y)) {
						continue;
					}
					seriesXY.add(x, y);
				}
				int paletteIndex = ai - 1;
				if (globalOrder != null && !globalOrder.isEmpty()) {
					int idx = globalOrder.indexOf(new StimulusConcKey(key.stimulus, key.concentration));
					if (idx >= 0) {
						paletteIndex = idx;
					}
				}
				Color color = aggregatePaletteColor(paletteIndex);
				seriesXY.setDescription(SeriesStyleCodec.buildDescription(cage.getProperties().getCageID(),
						cage.getProperties().getCageID(), cage.getProperties().getCageNFlies(), color));
				dataset.addSeries(seriesXY);
			}
			appendMedianRefSeries(exp, cage, dataset);
			return;
		}

		if (aggregates == null || aggregates.isEmpty()) {
			appendMedianRefSeries(exp, cage, dataset);
			return;
		}

		for (AggregateSeries agg : aggregates) {
			if (agg == null || agg.values == null || agg.values.isEmpty() || agg.key == null) {
				continue;
			}
			String seriesKey = SpotChartSeriesKeys.keyAggregate(cage.getProperties().getCageID(), agg.key.stimulus,
					agg.key.concentration, ai++);
			XYSeries seriesXY = new XYSeries(seriesKey, false);

			for (int k = 0; k < agg.values.size(); k++) {
				double x = camImagesTimeMin != null && k < camImagesTimeMin.length ? camImagesTimeMin[k] : k;
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
		appendMedianRefSeries(exp, cage, dataset);
	}

	private static void appendMedianRefSeries(Experiment exp, Cage cage, XYSeriesCollection dataset) {
		if (exp == null || cage == null || dataset == null) {
			return;
		}
		if (exp.getSeqCamData() == null || exp.getSeqCamData().getTimeManager() == null) {
			return;
		}
		CageSpotAggregateSeries median = cage.getSpotAggregates().getMedianRefSeries();
		if (median == null || median.getMeasure() == null) {
			return;
		}
		double[] camImagesTimeMin = exp.getSeqCamData().getTimeManager().getCamImagesTime_Minutes();
		String seriesKey = SpotChartSeriesKeys.keyMedianRef(cage.getProperties().getCageID());
		XYSeries seriesXY = new XYSeries(seriesKey, false);
		SpotMeasure m = median.getMeasure();
		int npoints = m.getCount();
		if (camImagesTimeMin != null && npoints > camImagesTimeMin.length) {
			npoints = camImagesTimeMin.length;
		}
		for (int k = 0; k < npoints; k++) {
			double x = camImagesTimeMin != null ? camImagesTimeMin[k] : k;
			double y = m.getValueAt(k);
			if (!Double.isFinite(y)) {
				continue;
			}
			seriesXY.add(x, y);
		}
		seriesXY.setDescription(SeriesStyleCodec.buildDescription(cage.getProperties().getCageID(),
				cage.getProperties().getCageID(), cage.getProperties().getCageNFlies(), Color.DARK_GRAY));
		dataset.addSeries(seriesXY);
	}

	private static Color aggregatePaletteColor(int index) {
		Paint[] paints = ChartColor.createDefaultPaintArray();
		if (paints == null || paints.length == 0) {
			return Color.BLACK;
		}
		Paint p = paints[Math.max(0, index) % paints.length];
		return p instanceof Color ? (Color) p : Color.BLACK;
	}
}
