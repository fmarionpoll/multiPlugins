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
import plugins.fmp.multitools.experiment.cage.CageSpotStimulusAggregation.StimulusConcKey;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.service.CageKymoGreenHeightAggregation;
import plugins.fmp.multitools.service.CageKymoGreenHeightAggregation.SumSeries;
import plugins.fmp.multitools.service.KymoAnalysisResult;
import plugins.fmp.multitools.service.KymoAnalysisResult.SpotKymoSeries;
import plugins.fmp.multitools.tools.chart.ChartCageBuild;
import plugins.fmp.multitools.tools.chart.style.SeriesStyleCodec;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

/**
 * Builds {@link XYSeriesCollection} from a cached {@link KymoAnalysisResult}.
 * Measure is chosen from {@link ResultsOptions#resultType} (per-spot fraction, |Δf|, or cage mean).
 */
public final class CageKymoSeriesBuilder implements CageSeriesBuilder {

	private final KymoAnalysisResult result;

	public CageKymoSeriesBuilder(KymoAnalysisResult result) {
		this.result = result;
	}

	public static boolean isKymoMetricMeasure(EnumResults rt) {
		return rt == EnumResults.KYMO_FRACT || rt == EnumResults.KYMO_ABS_DELTA || rt == EnumResults.KYMO_CAGE_MEAN_FRACT
				|| rt == EnumResults.KYMO_CAGE_MEAN_ABS_DELTA || rt == EnumResults.KYMO_GREEN_HEIGHT
				|| rt == EnumResults.KYMO_GREEN_HEIGHT_RATIO || rt == EnumResults.KYMO_CAGE_MEAN_GREEN_HEIGHT_RATIO
				|| rt == EnumResults.AGG_GREENHEIGHT_RATIO;
	}

	private static boolean useKymoAggGreenHeightSum(EnumResults rt) {
		return rt == EnumResults.AGG_GREENHEIGHT_RATIO;
	}

	private static boolean useAbsDelta(EnumResults rt) {
		return rt == EnumResults.KYMO_ABS_DELTA || rt == EnumResults.KYMO_CAGE_MEAN_ABS_DELTA;
	}

	private static boolean useCageMean(EnumResults rt) {
		return rt == EnumResults.KYMO_CAGE_MEAN_FRACT || rt == EnumResults.KYMO_CAGE_MEAN_ABS_DELTA
				|| rt == EnumResults.KYMO_CAGE_MEAN_GREEN_HEIGHT_RATIO;
	}

	private static boolean useGreenHeight(EnumResults rt) {
		return rt == EnumResults.KYMO_GREEN_HEIGHT;
	}

	private static boolean useGreenHeightRatio(EnumResults rt) {
		return rt == EnumResults.KYMO_GREEN_HEIGHT_RATIO || rt == EnumResults.KYMO_CAGE_MEAN_GREEN_HEIGHT_RATIO;
	}

	/**
	 * Y-axis unit label for kymograph fraction charts.
	 */
	public static String kymoChartRangeAxisLabel(ResultsOptions opts) {
		if (opts == null || opts.resultType == null) {
			return null;
		}
		if (!isKymoMetricMeasure(opts.resultType)) {
			return null;
		}
		return opts.resultType.toUnit();
	}

	private static int traceColumnCount(SpotKymoSeries row, EnumResults rt, ResultsOptions options) {
		if (row == null) {
			return 0;
		}
		if (useAbsDelta(rt)) {
			return row.absDeltaFraction.length;
		}
		if (useGreenHeight(rt)) {
			return row.greenHeight.length;
		}
		if (useGreenHeightRatio(rt)) {
			return row.greenHeightRatio.length;
		}
		return row.fraction.length;
	}

	/**
	 * Y value for one bin from a row; measure-specific arrays on {@link SpotKymoSeries}.
	 */
	static double traceY(SpotKymoSeries row, EnumResults rt, ResultsOptions opts, int j) {
		if (row == null || rt == null) {
			return Double.NaN;
		}
		if (useAbsDelta(rt)) {
			return j < row.absDeltaFraction.length ? row.absDeltaFraction[j] : Double.NaN;
		}
		if (useGreenHeight(rt)) {
			return j < row.greenHeight.length ? row.greenHeight[j] : Double.NaN;
		}
		if (useGreenHeightRatio(rt)) {
			return j < row.greenHeightRatio.length ? row.greenHeightRatio[j] : Double.NaN;
		}
		return j < row.fraction.length ? row.fraction[j] : Double.NaN;
	}

	@Override
	public XYSeriesCollection build(Experiment exp, Cage cage, ResultsOptions options) {
		XYSeriesCollection dataset = new XYSeriesCollection();
		if (result == null || cage == null || cage.getProperties() == null) {
			return dataset;
		}
		int cageId = cage.getProperties().getCageID();
		double[] x = result.xAxisMinutes;
		if (x.length == 0) {
			return dataset;
		}
		EnumResults rt = options != null && options.resultType != null ? options.resultType : EnumResults.KYMO_FRACT;
		List<SpotKymoSeries> rows = result.curvesForCage(cageId);
		if (rows.isEmpty()) {
			return dataset;
		}
		if (useKymoAggGreenHeightSum(rt)) {
			addGreenHeightRatioSumAggregates(dataset, cage, x, rows, options);
			ChartCageBuild.updateGlobalExtremaFromDataset(dataset);
			return dataset;
		}
		if (useCageMean(rt)) {
			addCageMeanSeries(dataset, cage, x, rows, rt, options);
			ChartCageBuild.updateGlobalExtremaFromDataset(dataset);
			return dataset;
		}
		for (SpotKymoSeries row : rows) {
			Spot spot = row.spot;
			if (spot == null) {
				continue;
			}
			String key = SpotChartSeriesKeys.key(spot, row.indexInCage);
			XYSeries series = buildSeriesForRow(x, row, key, rt, options);
			if (series.getItemCount() == 0) {
				continue;
			}
			CageProperties cageProp = cage.getProperties();
			Color color = spot.getProperties().getColor();
			series.setDescription(SeriesStyleCodec.buildDescription(cageProp.getCageID(), cageProp.getCageID(),
					cageProp.getCageNFlies(), color));
			dataset.addSeries(series);
		}
		ChartCageBuild.updateGlobalExtremaFromDataset(dataset);
		return dataset;
	}

	private static void addGreenHeightRatioSumAggregates(XYSeriesCollection dataset, Cage cage, double[] x,
			List<SpotKymoSeries> rows, ResultsOptions options) {
		int n = x.length;
		List<SumSeries> sums = CageKymoGreenHeightAggregation.buildSumRatioByStimulusConc(rows, n);
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
			int len = Math.min(n, agg.values.length);
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

	private static Color aggregatePaletteColor(int index) {
		Paint[] paints = ChartColor.createDefaultPaintArray();
		if (paints == null || paints.length == 0) {
			return Color.BLACK;
		}
		Paint p = paints[Math.max(0, index) % paints.length];
		return p instanceof Color ? (Color) p : Color.BLACK;
	}

	private static void addCageMeanSeries(XYSeriesCollection dataset, Cage cage, double[] x, List<SpotKymoSeries> rows,
			EnumResults rt, ResultsOptions options) {
		int n = x.length;
		double[] mean = new double[n];
		for (int j = 0; j < n; j++) {
			mean[j] = Double.NaN;
		}
		for (int j = 0; j < n; j++) {
			double sum = 0;
			int cnt = 0;
			for (SpotKymoSeries row : rows) {
				double v = traceY(row, rt, options, j);
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
		CageProperties cageProp = cage.getProperties();
		Color color = Color.DARK_GRAY;
		series.setDescription(SeriesStyleCodec.buildDescription(cageProp.getCageID(), cageProp.getCageID(),
				cageProp.getCageNFlies(), color));
		dataset.addSeries(series);
	}

	private static XYSeries buildSeriesForRow(double[] x, SpotKymoSeries row, String key, EnumResults rt,
			ResultsOptions options) {
		XYSeries series = new XYSeries(key, false);
		int rowLen = traceColumnCount(row, rt, options);
		int n = Math.min(x.length, rowLen);
		for (int j = 0; j < n; j++) {
			double y = traceY(row, rt, options, j);
			if (!Double.isFinite(y)) {
				continue;
			}
			series.add(x[j], y);
		}
		return series;
	}

	/**
	 * Overlay chart: one series per selected spot, or cage-mean computed over the selected spots only
	 * (grouped per cage when measure is a cage-mean type).
	 */
	public static XYSeriesCollection buildOverlayForSpots(Experiment exp, KymoAnalysisResult result, ResultsOptions options,
			List<Spot> spots) {
		XYSeriesCollection dataset = new XYSeriesCollection();
		if (result == null || spots == null || spots.isEmpty()) {
			return dataset;
		}
		double[] x = result.xAxisMinutes;
		if (x.length == 0) {
			return dataset;
		}
		EnumResults rt = options != null && options.resultType != null ? options.resultType : EnumResults.KYMO_FRACT;
		if (useKymoAggGreenHeightSum(rt)) {
			java.util.HashMap<Integer, java.util.ArrayList<Spot>> byCage = new java.util.HashMap<>();
			for (Spot spot : spots) {
				if (spot == null || spot.getProperties() == null) {
					continue;
				}
				int cid = spot.getProperties().getCageID();
				byCage.computeIfAbsent(cid, k -> new java.util.ArrayList<>()).add(spot);
			}
			for (java.util.Map.Entry<Integer, java.util.ArrayList<Spot>> e : byCage.entrySet()) {
				int cageId = e.getKey();
				java.util.ArrayList<SpotKymoSeries> subset = new java.util.ArrayList<>();
				for (Spot s : e.getValue()) {
					SpotKymoSeries row = findRowForSpot(result, cageId, s);
					if (row != null) {
						subset.add(row);
					}
				}
				if (subset.isEmpty()) {
					continue;
				}
				Cage cage = findCage(exp, cageId);
				if (cage == null) {
					continue;
				}
				addGreenHeightRatioSumAggregates(dataset, cage, x, subset, options);
			}
			ChartCageBuild.updateGlobalExtremaFromDataset(dataset);
			return dataset;
		}
		if (useCageMean(rt)) {
			java.util.HashMap<Integer, java.util.ArrayList<Spot>> byCage = new java.util.HashMap<>();
			for (Spot spot : spots) {
				if (spot == null || spot.getProperties() == null) {
					continue;
				}
				int cid = spot.getProperties().getCageID();
				byCage.computeIfAbsent(cid, k -> new java.util.ArrayList<>()).add(spot);
			}
			for (java.util.Map.Entry<Integer, java.util.ArrayList<Spot>> e : byCage.entrySet()) {
				int cageId = e.getKey();
				java.util.ArrayList<SpotKymoSeries> subset = new java.util.ArrayList<>();
				for (Spot s : e.getValue()) {
					SpotKymoSeries row = findRowForSpot(result, cageId, s);
					if (row != null) {
						subset.add(row);
					}
				}
				if (subset.isEmpty()) {
					continue;
				}
				Cage cage = findCage(exp, cageId);
				if (cage == null) {
					continue;
				}
				String key = "mean (selected)" + SpotChartSeriesKeys.SEP + "cage" + cageId;
				XYSeries meanSeries = meanSeriesForRows(x, subset, rt, options, key);
				if (meanSeries.getItemCount() == 0) {
					continue;
				}
				CageProperties cageProp = cage.getProperties();
				meanSeries.setDescription(SeriesStyleCodec.buildDescription(cageProp.getCageID(), cageProp.getCageID(),
						cageProp.getCageNFlies(), Color.DARK_GRAY));
				dataset.addSeries(meanSeries);
			}
			ChartCageBuild.updateGlobalExtremaFromDataset(dataset);
			return dataset;
		}
		for (Spot spot : spots) {
			if (spot == null || spot.getProperties() == null) {
				continue;
			}
			int cageId = spot.getProperties().getCageID();
			SpotKymoSeries row = findRowForSpot(result, cageId, spot);
			if (row == null) {
				continue;
			}
			String key = SpotChartSeriesKeys.key(spot, row.indexInCage);
			XYSeries series = buildSeriesForRow(x, row, key, rt, options);
			if (series.getItemCount() == 0) {
				continue;
			}
			Cage cage = exp != null && exp.getCages() != null ? exp.getCages().getCageFromSpotName(spot.getName())
					: null;
			int cid = spot.getProperties().getCageID();
			int nFlies = cage != null && cage.getProperties() != null ? cage.getProperties().getCageNFlies() : -1;
			Color color = spot.getProperties().getColor();
			series.setDescription(SeriesStyleCodec.buildDescription(cid, cid, nFlies, color));
			dataset.addSeries(series);
		}
		ChartCageBuild.updateGlobalExtremaFromDataset(dataset);
		return dataset;
	}

	private static Cage findCage(Experiment exp, int cageId) {
		if (exp == null || exp.getCages() == null) {
			return null;
		}
		for (Cage c : exp.getCages().cagesList) {
			if (c != null && c.getProperties() != null && c.getProperties().getCageID() == cageId) {
				return c;
			}
		}
		return null;
	}

	private static XYSeries meanSeriesForRows(double[] x, List<SpotKymoSeries> rows, EnumResults rt,
			ResultsOptions options, String key) {
		int n = x.length;
		double[] mean = new double[n];
		for (int j = 0; j < n; j++) {
			mean[j] = Double.NaN;
		}
		for (int j = 0; j < n; j++) {
			double sum = 0;
			int cnt = 0;
			for (SpotKymoSeries row : rows) {
				double v = traceY(row, rt, options, j);
				if (Double.isFinite(v)) {
					sum += v;
					cnt++;
				}
			}
			if (cnt > 0) {
				mean[j] = sum / cnt;
			}
		}
		XYSeries series = new XYSeries(key, false);
		for (int j = 0; j < n; j++) {
			if (Double.isFinite(mean[j])) {
				series.add(x[j], mean[j]);
			}
		}
		return series;
	}

	private static SpotKymoSeries findRowForSpot(KymoAnalysisResult result, int cageId, Spot spot) {
		for (SpotKymoSeries row : result.curvesForCage(cageId)) {
			if (row.spot == spot) {
				return row;
			}
			if (spot.getName() != null && row.spot != null && spot.getName().equals(row.spot.getName())) {
				return row;
			}
		}
		return null;
	}
}
