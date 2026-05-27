package plugins.fmp.multitools.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import icy.image.IcyBufferedImage;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.sequence.SequenceKymos;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.Logger;

/**
 * Computes per-spot vertical metric-fraction time series from loaded cage kymograph TIFFs.
 */
public final class CageKymoAnalyzer {

	public static final class Params {
		public ImageTransformEnums metricTransform;
		public int metricThreshold;
		/** Pixel is valid only if R+G+B is at least this (very dark pixels skipped). */
		public int minSumRgbForValidPixel;
		public int minValidRowsPerColumn;
		public boolean useGpuTransforms;
		/**
		 * Max length (columns) of a bad run along a row that may be filled when bracketed by good pixels (row-wise
		 * occlusion lift only). 0 disables row lift fills.
		 */
		public int maxRowOcclusionGapColumns;
		/**
		 * When true, copy the frame and lift short temporal occlusions inside each spot band row-wise (see
		 * {@link KymoStripRowwiseOcclusionFill}) before metric fractions and overlays.
		 */
		public boolean rowwiseOcclusionFill;
		/**
		 * When true (and row lift is on), kymograph overlay blends the lifted RGB inside spot bands so the corrected
		 * picture is visible; ignored by {@link CageKymoAnalyzer#analyze}.
		 */
		public boolean previewLiftedBands;
		/**
		 * When true, pixels where the insect metric passes the insect threshold are excluded from spot-on detection
		 * (parallel to a separate flies filter on camera frames).
		 */
		public boolean insectMetricGateEnabled;
		public ImageTransformEnums insectMetricTransform;
		public int insectMetricThreshold;
		/** When true, insect is "on" when metric {@code >} threshold; when false, when metric {@code <=} threshold. */
		public boolean insectMetricThresholdUp;

		public Params(ImageTransformEnums metricTransform, int metricThreshold, int minSumRgbForValidPixel,
				int minValidRowsPerColumn, boolean useGpuTransforms, int maxRowOcclusionGapColumns,
				boolean rowwiseOcclusionFill, boolean previewLiftedBands, boolean insectMetricGateEnabled,
				ImageTransformEnums insectMetricTransform, int insectMetricThreshold, boolean insectMetricThresholdUp) {
			this.metricTransform = metricTransform != null ? metricTransform : ImageTransformEnums.RGB_DIFFS;
			this.metricThreshold = metricThreshold;
			this.minSumRgbForValidPixel = minSumRgbForValidPixel;
			this.minValidRowsPerColumn = minValidRowsPerColumn;
			this.useGpuTransforms = useGpuTransforms;
			this.maxRowOcclusionGapColumns = Math.max(0, maxRowOcclusionGapColumns);
			this.rowwiseOcclusionFill = rowwiseOcclusionFill;
			this.previewLiftedBands = previewLiftedBands;
			this.insectMetricGateEnabled = insectMetricGateEnabled;
			this.insectMetricTransform = insectMetricTransform != null ? insectMetricTransform
					: ImageTransformEnums.B_RGB;
			this.insectMetricThreshold = insectMetricThreshold;
			this.insectMetricThresholdUp = insectMetricThresholdUp;
		}
	}

	private CageKymoAnalyzer() {
	}

	public static KymoAnalysisResult analyze(Experiment exp, Params params) {
		if (exp == null || params == null) {
			return new KymoAnalysisResult(Map.of(), new double[0], 0);
		}
		SequenceKymos sk = exp.getSeqKymos();
		if (sk == null || sk.getSequence() == null) {
			Logger.warn("CageKymoAnalyzer: no kymograph sequence loaded");
			return new KymoAnalysisResult(Map.of(), new double[0], 0);
		}
		Spots spots = exp.getSpots();
		if (spots == null || exp.getCages() == null) {
			return new KymoAnalysisResult(Map.of(), new double[0], 0);
		}
		int refW = 0;
		int refH = 0;
		if (exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null) {
			refW = exp.getSeqCamData().getSequence().getSizeX();
			refH = exp.getSeqCamData().getSequence().getSizeY();
		}
		int nT = sk.getImageLoader().getNTotalFrames();
		if (nT <= 0) {
			return new KymoAnalysisResult(Map.of(), new double[0], 0);
		}

		Map<Integer, List<KymoAnalysisResult.SpotKymoSeries>> out = new LinkedHashMap<>();
		int widthBins = 0;
		double[] xAxis = new double[0];
		CageKymographStripLayoutCsv.PersistedKymoGrid persistedKymoGrid = null;

		for (int t = 0; t < nT; t++) {
			String fn = sk.getFileNameFromImageList(t);
			Cage cage = KymocageCageResolver.resolveCageFromKymographPath(fn, exp.getCages());
			if (cage == null) {
				continue;
			}
			IcyBufferedImage img = sk.getSeqImage(t, 0);
			if (img == null) {
				continue;
			}
			int imgW = img.getWidth();
			int imgH = img.getHeight();
			if (refW <= 0 || refH <= 0) {
				refW = imgW;
				refH = imgH;
			}
			List<CageKymographSpotBands> bands = CageKymographPickSupport.stackedSpotBands(exp, cage, spots, refW,
					refH, imgW);
			if (bands.isEmpty()) {
				continue;
			}
			if (widthBins == 0) {
				widthBins = imgW;
				persistedKymoGrid = CageKymographStripLayoutCsv.readPersistedKymoGridOrNull(
						exp.getKymosBinFullDirectory(), widthBins);
				xAxis = buildXAxisMinutes(exp, widthBins, persistedKymoGrid);
			} else if (imgW != widthBins) {
				Logger.warn("CageKymoAnalyzer: image width " + imgW + " differs from first (" + widthBins
						+ "); series will clip to shorter width");
			}

			int cageId = cage.getProperties() != null ? cage.getProperties().getCageID() : -1;
			int useW = widthBins > 0 ? Math.min(imgW, widthBins) : imgW;
			List<KymoAnalysisResult.SpotKymoSeries> series = computeForImage(img, bands, params, useW);
			out.put(cageId, series);
		}

		return new KymoAnalysisResult(out, xAxis, widthBins);
	}

	private static double[] buildXAxisMinutes(Experiment exp, int widthBins,
			CageKymographStripLayoutCsv.PersistedKymoGrid persistedGrid) {
		double[] x = new double[widthBins];
		long step;
		long first;
		if (persistedGrid != null && persistedGrid.columnCount == widthBins) {
			step = Math.max(1L, persistedGrid.stepMs);
			first = persistedGrid.firstMs;
		} else {
			step = Math.max(1L, exp.getKymoBin_ms());
			first = exp.getKymoFirst_ms();
		}
		for (int col = 0; col < widthBins; col++) {
			x[col] = (first + (long) col * step) / 60000.0;
		}
		return x;
	}

	/** Per-column green mask height (row count ON in band) and ratio to the first column with height &gt; 0. */
	public static double[] greenHeightRatioFromHeights(int[] greenHeight) {
		int n = greenHeight != null ? greenHeight.length : 0;
		double[] ratio = new double[n];
		Arrays.fill(ratio, Double.NaN);
		if (n == 0) {
			return ratio;
		}
		int baseline = -1;
		for (int x = 0; x < n; x++) {
			if (greenHeight[x] > 0) {
				baseline = x;
				break;
			}
		}
		if (baseline < 0) {
			return ratio;
		}
		double h0 = greenHeight[baseline];
		if (h0 <= 0) {
			return ratio;
		}
		for (int x = 0; x < n; x++) {
			int h = greenHeight[x];
			ratio[x] = h > 0 ? h / h0 : 0.0;
		}
		return ratio;
	}

	public static int[] computeColumnGreenHeights(boolean[] spotOnMask, CageKymographSpotBands band, int imgW) {
		int[] h = new int[imgW];
		if (spotOnMask == null || band == null || band.geometryMissing) {
			return h;
		}
		int y0 = Math.max(0, band.y0);
		int y1 = Math.max(y0, band.y1Exclusive);
		for (int x = 0; x < imgW; x++) {
			int cnt = 0;
			for (int y = y0; y < y1; y++) {
				int idx = y * imgW + x;
				if (idx >= 0 && idx < spotOnMask.length && spotOnMask[idx]) {
					cnt++;
				}
			}
			h[x] = cnt;
		}
		return h;
	}

	public static double[] computeColumnMetricFractions(IcyBufferedImage img, CageKymographSpotBands band,
			Params params, int imgW) {
		return computeColumnMetrics(img, band, params, imgW).fraction;
	}

	public static final class ColumnMetrics {
		public final double[] fraction;
		public final int[] greenHeight;
		public final double[] greenHeightRatio;

		ColumnMetrics(double[] fraction, int[] greenHeight, double[] greenHeightRatio) {
			this.fraction = fraction;
			this.greenHeight = greenHeight;
			this.greenHeightRatio = greenHeightRatio;
		}
	}

	/**
	 * Fraction, green height (rows), and h/h₀ for one spot band on one kymograph frame. Rows counted as ON use the
	 * post-lift cleaned mask when row lift is on; otherwise raw spot metric &gt; threshold (with optional insect
	 * exclusion).
	 */
	public static ColumnMetrics computeColumnMetrics(IcyBufferedImage img, CageKymographSpotBands band, Params params,
			int imgW) {
		double[] f = new double[imgW];
		int[] gh = new int[imgW];
		double[] ghr = new double[imgW];
		Arrays.fill(f, Double.NaN);
		Arrays.fill(ghr, Double.NaN);
		if (band.geometryMissing) {
			return new ColumnMetrics(f, gh, ghr);
		}
		int imgH = img.getHeight();
		int nC = Math.max(1, img.getSizeC());
		int[] r = nC > 0 ? Array1DUtil.arrayToIntArray(img.getDataXY(0), img.isSignedDataType()) : null;
		int[] g = nC > 1 ? Array1DUtil.arrayToIntArray(img.getDataXY(1), img.isSignedDataType()) : null;
		int[] b = nC > 2 ? Array1DUtil.arrayToIntArray(img.getDataXY(2), img.isSignedDataType()) : null;

		IcyBufferedImage metricImg = KymoImageTransforms.applyMetricTransform(img, params.metricTransform,
				params.useGpuTransforms);
		double[] metric = KymoImageTransforms.channel0AsDouble(metricImg);
		int metricLen = metric != null ? metric.length : 0;
		double thr = params.metricThreshold;
		double[] insectMetric = null;
		if (params.insectMetricGateEnabled) {
			IcyBufferedImage ins = KymoImageTransforms.applyMetricTransform(img, params.insectMetricTransform,
					params.useGpuTransforms);
			insectMetric = ins != null ? KymoImageTransforms.channel0AsDouble(ins) : null;
		}

		int y0 = Math.max(0, band.y0);
		int y1 = Math.min(imgH, band.y1Exclusive);
		boolean[] spotOnMask = params.rowwiseOcclusionFill
				? KymoStripPostLiftMetricMask.buildWithMetric(img, band, params, imgW, imgH, metric, r, g, b, nC)
				: buildRawSpotOnMask(imgW, imgH, y0, y1, metric, metricLen, thr, insectMetric, params, r, g, b, nC);
		gh = computeColumnGreenHeights(spotOnMask, band, imgW);
		ghr = greenHeightRatioFromHeights(gh);
		for (int x = 0; x < imgW; x++) {
			int aboveThresholdRows = 0;
			int valid = 0;
			for (int y = y0; y < y1; y++) {
				int idx = y * imgW + x;
				int rv = sampleChan(r, idx);
				int gv = nC > 1 ? sampleChan(g, idx) : rv;
				int bv = nC > 2 ? sampleChan(b, idx) : rv;
				int sum = rv + gv + bv;
				if (sum < params.minSumRgbForValidPixel) {
					if (!spotOnMask[idx]) {
						continue;
					}
				}
				valid++;
				if (spotOnMask[idx]) {
					aboveThresholdRows++;
				}
			}
			if (valid < params.minValidRowsPerColumn) {
				f[x] = Double.NaN;
			} else {
				f[x] = (double) aboveThresholdRows / (double) valid;
			}
		}
		return new ColumnMetrics(f, gh, ghr);
	}

	private static boolean[] buildRawSpotOnMask(int imgW, int imgH, int y0, int y1, double[] metric, int metricLen,
			double thr, double[] insectMetric, Params params, int[] r, int[] g, int[] b, int nC) {
		boolean[] m = new boolean[imgW * imgH];
		for (int y = y0; y < y1; y++) {
			for (int x = 0; x < imgW; x++) {
				int idx = y * imgW + x;
				int rv = sampleChan(r, idx);
				int gv = nC > 1 ? sampleChan(g, idx) : rv;
				int bv = nC > 2 ? sampleChan(b, idx) : rv;
				if (rv + gv + bv < params.minSumRgbForValidPixel) {
					continue;
				}
				if (metric != null && idx >= 0 && idx < metricLen) {
					double mv = metric[idx];
					boolean spotP = Double.isFinite(mv) && mv > thr;
					boolean insectP = insectPixelOn(insectMetric, idx, params);
					m[idx] = spotP && !insectP;
				}
			}
		}
		return m;
	}

	private static List<KymoAnalysisResult.SpotKymoSeries> computeForImage(IcyBufferedImage img,
			List<CageKymographSpotBands> bands, Params params, int imgW) {
		IcyBufferedImage data = params.rowwiseOcclusionFill ? KymoStripRowwiseOcclusionFill.apply(img, bands, params)
				: img;
		List<KymoAnalysisResult.SpotKymoSeries> list = new ArrayList<>();
		int si = 0;
		for (CageKymographSpotBands band : bands) {
			Spot spot = band.spot;
			ColumnMetrics cols = computeColumnMetrics(data, band, params, imgW);
			double[] df = new double[imgW];
			recomputeDfFromF(cols.fraction, df);
			list.add(new KymoAnalysisResult.SpotKymoSeries(spot, si, cols.fraction, df, cols.greenHeight,
					cols.greenHeightRatio));
			si++;
		}
		return list;
	}

	private static void recomputeDfFromF(double[] f, double[] df) {
		for (int x = 0; x < f.length; x++) {
			if (x == 0) {
				df[x] = 0.0;
			} else {
				double a = f[x - 1];
				double c = f[x];
				if (Double.isFinite(a) && Double.isFinite(c)) {
					df[x] = Math.abs(c - a);
				} else {
					df[x] = Double.NaN;
				}
			}
		}
	}

	private static int sampleChan(int[] ch, int idx) {
		if (ch == null || idx < 0 || idx >= ch.length) {
			return 0;
		}
		return ch[idx];
	}

	private static boolean insectPixelOn(double[] insectMetric, int idx, Params params) {
		if (!params.insectMetricGateEnabled || insectMetric == null || idx < 0 || idx >= insectMetric.length) {
			return false;
		}
		return KymoMetricGate.directedFinite(insectMetric[idx], params.insectMetricThreshold,
				params.insectMetricThresholdUp);
	}
}
