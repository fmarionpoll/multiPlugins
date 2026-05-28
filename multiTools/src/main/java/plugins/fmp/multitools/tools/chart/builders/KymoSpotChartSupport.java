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
import plugins.fmp.multitools.experiment.cage.CageSpotStimulusAggregation.StimulusConcKey;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotMeasure;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.service.CageKymoGreenHeightAggregation;
import plugins.fmp.multitools.service.CageKymoGreenHeightAggregation.SumSeries;
import plugins.fmp.multitools.service.CageKymographStripLayoutCsv;
import plugins.fmp.multitools.service.CageKymographStripLayoutCsv.PersistedKymoGrid;
import plugins.fmp.multitools.tools.chart.ChartCageBuild;
import plugins.fmp.multitools.tools.chart.style.SeriesStyleCodec;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

/**
 * Chart helpers for kymograph measures stored on {@link Spot}.
 */
public final class KymoSpotChartSupport {

	private KymoSpotChartSupport() {
	}

	public static boolean experimentHasKymoSpotMeasures(Experiment exp) {
		if (exp == null || exp.getSpots() == null) {
			return false;
		}
		for (Spot spot : exp.getSpots().getSpotList()) {
			if (spot != null && spot.getMeasurementsKymo().hasAnyData()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * X-axis in minutes for kymograph strip columns. Prefers {@link CageKymographStripLayoutCsv} when
	 * present; otherwise {@link Experiment#getKymoFirst_ms()} and {@link Experiment#getKymoBin_ms()}.
	 */
	public static double[] buildKymoXAxisMinutes(Experiment exp, int nBins) {
		if (nBins <= 0) {
			return new double[0];
		}
		long stepMs;
		long firstMs;
		String binDir = exp != null ? exp.getKymosBinFullDirectory() : null;
		PersistedKymoGrid grid = binDir != null ? CageKymographStripLayoutCsv.readPersistedKymoGridOrNull(binDir, nBins)
				: null;
		if (grid != null && grid.columnCount == nBins) {
			stepMs = Math.max(1L, grid.stepMs);
			firstMs = Math.max(0L, grid.firstMs);
		} else {
			stepMs = exp != null ? exp.getKymoBin_ms() : 0;
			if (stepMs <= 0) {
				stepMs = 60_000L;
			}
			firstMs = exp != null ? exp.getKymoFirst_ms() : 0;
			if (firstMs < 0) {
				firstMs = 0;
			}
		}
		double[] x = new double[nBins];
		for (int j = 0; j < nBins; j++) {
			x[j] = (firstMs + (long) j * stepMs) / 60000.0;
		}
		return x;
	}

	public static int maxKymoBinsForSpots(List<Spot> spots, EnumResults rt) {
		int n = 0;
		if (spots == null || rt == null) {
			return n;
		}
		for (Spot spot : spots) {
			if (spot == null) {
				continue;
			}
			SpotMeasure m = spot.getMeasurements(rt);
			if (m != null) {
				n = Math.max(n, m.getCount());
			}
		}
		return n;
	}

	public static XYSeriesCollection buildCageDataset(Experiment exp, Cage cage, Spots allSpots,
			ResultsOptions options) {
		XYSeriesCollection dataset = new XYSeriesCollection();
		if (exp == null || cage == null || allSpots == null || options == null || options.resultType == null) {
			return dataset;
		}
		List<Spot> spots = cage.getSpotList(allSpots);
		if (spots.isEmpty()) {
			return dataset;
		}
		EnumResults rt = options.resultType;
		if (rt == EnumResults.AGG_GREENHEIGHT_CONSO) {
			addGreenHeightConsoAggregates(exp, cage, spots, options, dataset);
			ChartCageBuild.updateGlobalExtremaFromDataset(dataset);
			return dataset;
		}
		if (isCageMean(rt)) {
			int nBins = maxKymoBinsForSpots(spots, underlyingSpotType(rt));
			double[] x = buildKymoXAxisMinutes(exp, nBins);
			addCageMeanSeries(cage, x, spots, rt, options, dataset);
			ChartCageBuild.updateGlobalExtremaFromDataset(dataset);
			return dataset;
		}
		int nBins = maxKymoBinsForSpots(spots, rt);
		double[] x = buildKymoXAxisMinutes(exp, nBins);
		for (int si = 0; si < spots.size(); si++) {
			Spot spot = spots.get(si);
			String key = SpotChartSeriesKeys.key(spot, si);
			XYSeries series = seriesFromSpot(x, spot, rt, options, key);
			if (series == null || series.getItemCount() == 0) {
				continue;
			}
			series.setDescription(SeriesStyleCodec.buildDescription(cage.getProperties().getCageID(),
					cage.getProperties().getCageID(), cage.getProperties().getCageNFlies(),
					spot.getProperties().getColor()));
			dataset.addSeries(series);
		}
		ChartCageBuild.updateGlobalExtremaFromDataset(dataset);
		return dataset;
	}

	public static XYSeriesCollection buildOverlayForSpots(Experiment exp, ResultsOptions options, List<Spot> spots) {
		XYSeriesCollection dataset = new XYSeriesCollection();
		if (exp == null || options == null || spots == null || spots.isEmpty()) {
			return dataset;
		}
		EnumResults rt = options.resultType != null ? options.resultType : EnumResults.KYMO_FRACT;
		java.util.HashMap<Integer, java.util.ArrayList<Spot>> byCage = new java.util.HashMap<>();
		for (Spot spot : spots) {
			if (spot == null || spot.getProperties() == null) {
				continue;
			}
			byCage.computeIfAbsent(spot.getProperties().getCageID(), k -> new java.util.ArrayList<>()).add(spot);
		}
		for (java.util.Map.Entry<Integer, java.util.ArrayList<Spot>> e : byCage.entrySet()) {
			Cage cage = exp.getCages() != null ? exp.getCages().getCageFromID(e.getKey()) : null;
			if (cage == null) {
				continue;
			}
			List<Spot> cageSpots = e.getValue();
			if (rt == EnumResults.AGG_GREENHEIGHT_CONSO) {
				addGreenHeightConsoAggregates(exp, cage, cageSpots, options, dataset);
				continue;
			}
			if (isCageMean(rt)) {
				int nBins = maxKymoBinsForSpots(cageSpots, underlyingSpotType(rt));
				double[] x = buildKymoXAxisMinutes(exp, nBins);
				addCageMeanSeries(cage, x, cageSpots, rt, options, dataset);
				continue;
			}
			int nBins = maxKymoBinsForSpots(cageSpots, rt);
			double[] x = buildKymoXAxisMinutes(exp, nBins);
			for (int si = 0; si < cageSpots.size(); si++) {
				Spot spot = cageSpots.get(si);
				String key = SpotChartSeriesKeys.key(spot, si);
				XYSeries series = seriesFromSpot(x, spot, rt, options, key);
				if (series == null || series.getItemCount() == 0) {
					continue;
				}
				series.setDescription(SeriesStyleCodec.buildDescription(cage.getProperties().getCageID(),
						cage.getProperties().getCageID(), cage.getProperties().getCageNFlies(),
						spot.getProperties().getColor()));
				dataset.addSeries(series);
			}
		}
		ChartCageBuild.updateGlobalExtremaFromDataset(dataset);
		return dataset;
	}

	private static void addGreenHeightConsoAggregates(Experiment exp, Cage cage, List<Spot> spots,
			ResultsOptions options, XYSeriesCollection dataset) {
		int nBins = maxKymoBinsForSpots(spots, EnumResults.KYMO_GREEN_HEIGHT_RATIO);
		if (nBins <= 0) {
			return;
		}
		double[] x = buildKymoXAxisMinutes(exp, nBins);
		List<CageSpotAggregateSeries> cached = cage.getSpotAggregates().getEntries();
		if (cached != null && !cached.isEmpty()) {
			addCachedAggregates(cage, x, cached, options, dataset);
			return;
		}
		List<SumSeries> sums = CageKymoGreenHeightAggregation.buildSumConsoByStimulusConcFromSpots(spots, nBins);
		List<StimulusConcKey> globalOrder = options != null ? options.spotAggregateGlobalKeyOrder : null;
		int ai = 0;
		CageProperties cageProp = cage.getProperties();
		for (SumSeries agg : sums) {
			if (agg == null || agg.key == null || agg.values == null) {
				continue;
			}
			String seriesKey = SpotChartSeriesKeys.keyAggregate(cageProp.getCageID(), agg.key.stimulus,
					agg.key.concentration, ai++);
			XYSeries series = new XYSeries(seriesKey, false);
			int len = Math.min(nBins, agg.values.length);
			for (int j = 0; j < len; j++) {
				double y = agg.values[j];
				if (Double.isFinite(y)) {
					series.add(x[j], y);
				}
			}
			if (series.getItemCount() == 0) {
				continue;
			}
			int paletteIndex = ai - 1;
			if (globalOrder != null && !globalOrder.isEmpty()) {
				int idx = globalOrder.indexOf(new StimulusConcKey(agg.key.stimulus, agg.key.concentration));
				if (idx >= 0) {
					paletteIndex = idx;
				}
			}
			Color color = aggregatePaletteColor(paletteIndex);
			series.setDescription(SeriesStyleCodec.buildDescription(cageProp.getCageID(), cageProp.getCageID(),
					cageProp.getCageNFlies(), color));
			dataset.addSeries(series);
		}
	}

	private static void addCachedAggregates(Cage cage, double[] x, List<CageSpotAggregateSeries> cached,
			ResultsOptions options, XYSeriesCollection dataset) {
		List<StimulusConcKey> globalOrder = options != null ? options.spotAggregateGlobalKeyOrder : null;
		int ai = 0;
		CageProperties cageProp = cage.getProperties();
		for (CageSpotAggregateSeries row : cached) {
			if (row == null || row.getKey() == null || row.getMeasure() == null) {
				continue;
			}
			StimulusConcKey key = row.getKey();
			String seriesKey = SpotChartSeriesKeys.keyAggregate(cageProp.getCageID(), key.stimulus, key.concentration,
					ai++);
			XYSeries series = new XYSeries(seriesKey, false);
			int n = Math.min(x.length, row.getMeasure().getCount());
			for (int j = 0; j < n; j++) {
				double y = row.getMeasure().getValueAt(j);
				if (Double.isFinite(y)) {
					series.add(x[j], y);
				}
			}
			if (series.getItemCount() == 0) {
				continue;
			}
			int paletteIndex = ai - 1;
			if (globalOrder != null && !globalOrder.isEmpty()) {
				int idx = globalOrder.indexOf(new StimulusConcKey(key.stimulus, key.concentration));
				if (idx >= 0) {
					paletteIndex = idx;
				}
			}
			Color color = aggregatePaletteColor(paletteIndex);
			series.setDescription(SeriesStyleCodec.buildDescription(cageProp.getCageID(), cageProp.getCageID(),
					cageProp.getCageNFlies(), color));
			dataset.addSeries(series);
		}
	}

	private static void addCageMeanSeries(Cage cage, double[] x, List<Spot> spots, EnumResults rt,
			ResultsOptions options, XYSeriesCollection dataset) {
		int n = x.length;
		if (n <= 0) {
			return;
		}
		EnumResults spotRt = underlyingSpotType(rt);
		double[] mean = new double[n];
		for (int j = 0; j < n; j++) {
			mean[j] = Double.NaN;
		}
		for (int j = 0; j < n; j++) {
			double sum = 0;
			int cnt = 0;
			for (Spot spot : spots) {
				double v = valueAt(spot, spotRt, options, j);
				if (Double.isFinite(v)) {
					sum += v;
					cnt++;
				}
			}
			if (cnt > 0) {
				mean[j] = sum / cnt;
			}
		}
		String key = "cage mean" + SpotChartSeriesKeys.SEP + "cage" + cage.getProperties().getCageID();
		XYSeries series = new XYSeries(key, false);
		for (int j = 0; j < n; j++) {
			if (Double.isFinite(mean[j])) {
				series.add(x[j], mean[j]);
			}
		}
		if (series.getItemCount() == 0) {
			return;
		}
		series.setDescription(SeriesStyleCodec.buildDescription(cage.getProperties().getCageID(),
				cage.getProperties().getCageID(), cage.getProperties().getCageNFlies(), Color.DARK_GRAY));
		dataset.addSeries(series);
	}

	private static XYSeries seriesFromSpot(double[] x, Spot spot, EnumResults rt, ResultsOptions options,
			String seriesKey) {
		if (spot == null || x == null || x.length == 0) {
			return null;
		}
		XYSeries series = new XYSeries(seriesKey, false);
		SpotMeasure measure = spot.getMeasurements(rt);
		if (measure == null) {
			return series;
		}
		int n = Math.min(x.length, measure.getCount());
		for (int j = 0; j < n; j++) {
			double y = valueAt(spot, rt, options, j);
			if (Double.isFinite(y)) {
				series.add(x[j], y);
			}
		}
		return series;
	}

	private static double valueAt(Spot spot, EnumResults rt, ResultsOptions options, int j) {
		SpotMeasure measure = spot.getMeasurements(rt);
		if (measure == null || j < 0 || j >= measure.getCount()) {
			return Double.NaN;
		}
		return measure.getValueAt(j);
	}

	private static boolean isCageMean(EnumResults rt) {
		return rt == EnumResults.KYMO_CAGE_MEAN_FRACT || rt == EnumResults.KYMO_CAGE_MEAN_ABS_DELTA
				|| rt == EnumResults.KYMO_CAGE_MEAN_GREEN_HEIGHT_RATIO;
	}

	private static EnumResults underlyingSpotType(EnumResults cageMeanRt) {
		if (cageMeanRt == EnumResults.KYMO_CAGE_MEAN_ABS_DELTA) {
			return EnumResults.KYMO_ABS_DELTA;
		}
		if (cageMeanRt == EnumResults.KYMO_CAGE_MEAN_GREEN_HEIGHT_RATIO) {
			return EnumResults.KYMO_GREEN_HEIGHT_RATIO;
		}
		return EnumResults.KYMO_FRACT;
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
