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

		public Params(ImageTransformEnums metricTransform, int metricThreshold, int minSumRgbForValidPixel,
				int minValidRowsPerColumn, boolean useGpuTransforms, int maxRowOcclusionGapColumns,
				boolean rowwiseOcclusionFill, boolean previewLiftedBands) {
			this.metricTransform = metricTransform != null ? metricTransform : ImageTransformEnums.RGB_DIFFS;
			this.metricThreshold = metricThreshold;
			this.minSumRgbForValidPixel = minSumRgbForValidPixel;
			this.minValidRowsPerColumn = minValidRowsPerColumn;
			this.useGpuTransforms = useGpuTransforms;
			this.maxRowOcclusionGapColumns = Math.max(0, maxRowOcclusionGapColumns);
			this.rowwiseOcclusionFill = rowwiseOcclusionFill;
			this.previewLiftedBands = previewLiftedBands;
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

	/**
	 * Per-column fraction inside the spot band: rows counted as ON use the post-lift cleaned mask when row lift is on
	 * (temporal gap fill, vertical bridge, left-anchored trace); otherwise raw metric &gt; threshold. Rows are included
	 * in the denominator if sum RGB passes the valid gate, or if the cleaned mask marks the pixel ON (bridged trace).
	 */
	public static double[] computeColumnMetricFractions(IcyBufferedImage img, CageKymographSpotBands band,
			Params params, int imgW) {
		double[] f = new double[imgW];
		Arrays.fill(f, Double.NaN);
		if (band.geometryMissing) {
			return f;
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

		int y0 = Math.max(0, band.y0);
		int y1 = Math.min(imgH, band.y1Exclusive);
		boolean[] cleanedMask = params.rowwiseOcclusionFill
				? KymoStripPostLiftMetricMask.buildWithMetric(img, band, params, imgW, imgH, metric, r, g, b, nC)
				: null;
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
					if (cleanedMask == null || !cleanedMask[idx]) {
						continue;
					}
				}
				valid++;
				boolean pass;
				if (cleanedMask != null) {
					pass = cleanedMask[idx];
				} else if (metric != null && idx >= 0 && idx < metricLen) {
					double m = metric[idx];
					pass = Double.isFinite(m) && m > thr;
				} else {
					pass = false;
				}
				if (pass) {
					aboveThresholdRows++;
				}
			}
			if (valid < params.minValidRowsPerColumn) {
				f[x] = Double.NaN;
			} else {
				f[x] = (double) aboveThresholdRows / (double) valid;
			}
		}
		return f;
	}

	private static List<KymoAnalysisResult.SpotKymoSeries> computeForImage(IcyBufferedImage img,
			List<CageKymographSpotBands> bands, Params params, int imgW) {
		IcyBufferedImage data = params.rowwiseOcclusionFill ? KymoStripRowwiseOcclusionFill.apply(img, bands, params)
				: img;
		List<KymoAnalysisResult.SpotKymoSeries> list = new ArrayList<>();
		int si = 0;
		for (CageKymographSpotBands band : bands) {
			Spot spot = band.spot;
			double[] f = computeColumnMetricFractions(data, band, params, imgW);
			double[] df = new double[imgW];
			recomputeDfFromF(f, df);
			list.add(new KymoAnalysisResult.SpotKymoSeries(spot, si, f, df));
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
}
