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
import plugins.fmp.multitools.service.KymoAnalysisResult.SpotKymoSeries;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.Logger;

/**
 * Computes per-spot vertical metric-fraction time series from loaded cage kymograph TIFFs.
 */
public final class CageKymoAnalyzer {

	public static final class Params {
		public ImageTransformEnums metricTransform;
		public int metricThreshold;
		/** Pixel is valid only if R+G+B is at least this (occluded/black pixels skipped). */
		public int minSumRgbForValidPixel;
		public int minValidRowsPerColumn;
		public boolean useGpuTransforms;

		/**
		 * When true, each column uses the longest contiguous vertical run of "signal" rows inside
		 * the ROI band instead of the full band height (falls back to full band if no run is long enough).
		 */
		public boolean restrictSignalBandPerColumn;
		/** Minimum length (rows) of that run to adopt it; otherwise use full {@code [y0,y1)}. */
		public int effectiveBandMinRunLength;
		/**
		 * If &gt; 0, a row counts toward the signal run only if max(R,G,B) is at least this (reduces faint
		 * background). If 0, only {@link #minSumRgbForValidPixel} applies for signal rows.
		 */
		public int signalMinMaxRgb;

		/**
		 * When true, fill NaN fraction columns that lie strictly between two finite neighbors (occlusions in
		 * time): linear interpolation from the left to the right finite value. Leading and trailing NaN runs
		 * are unchanged.
		 */
		public boolean fillOcclusionsLocf;
		/**
		 * Max length of a NaN run to fill when it is bounded by finite values on both sides; 0 = unlimited.
		 */
		public int locfMaxGapColumns;

		/**
		 * When true, each {@link SpotKymoSeries} retains {@link SpotKymoSeries#fractionBeforeGapFill} for charts
		 * and overlays (extra memory per strip).
		 */
		public boolean includeDiagnostics;

		public Params(ImageTransformEnums metricTransform, int metricThreshold, int minSumRgbForValidPixel,
				int minValidRowsPerColumn, boolean useGpuTransforms, boolean restrictSignalBandPerColumn,
				int effectiveBandMinRunLength, int signalMinMaxRgb, boolean fillOcclusionsLocf,
				int locfMaxGapColumns) {
			this(metricTransform, metricThreshold, minSumRgbForValidPixel, minValidRowsPerColumn, useGpuTransforms,
					restrictSignalBandPerColumn, effectiveBandMinRunLength, signalMinMaxRgb, fillOcclusionsLocf,
					locfMaxGapColumns, false);
		}

		public Params(ImageTransformEnums metricTransform, int metricThreshold, int minSumRgbForValidPixel,
				int minValidRowsPerColumn, boolean useGpuTransforms, boolean restrictSignalBandPerColumn,
				int effectiveBandMinRunLength, int signalMinMaxRgb, boolean fillOcclusionsLocf,
				int locfMaxGapColumns, boolean includeDiagnostics) {
			this.metricTransform = metricTransform != null ? metricTransform : ImageTransformEnums.RGB_DIFFS;
			this.metricThreshold = metricThreshold;
			this.minSumRgbForValidPixel = minSumRgbForValidPixel;
			this.minValidRowsPerColumn = minValidRowsPerColumn;
			this.useGpuTransforms = useGpuTransforms;
			this.restrictSignalBandPerColumn = restrictSignalBandPerColumn;
			this.effectiveBandMinRunLength = Math.max(1, effectiveBandMinRunLength);
			this.signalMinMaxRgb = Math.max(0, signalMinMaxRgb);
			this.fillOcclusionsLocf = fillOcclusionsLocf;
			this.locfMaxGapColumns = Math.max(0, locfMaxGapColumns);
			this.includeDiagnostics = includeDiagnostics;
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

		Map<Integer, List<SpotKymoSeries>> out = new LinkedHashMap<>();
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
			List<SpotKymoSeries> series = computeForImage(img, bands, params, useW);
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

	private static List<SpotKymoSeries> computeForImage(IcyBufferedImage img, List<CageKymographSpotBands> bands,
			Params params, int imgW) {
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

		List<SpotKymoSeries> list = new ArrayList<>();
		int si = 0;
		for (CageKymographSpotBands band : bands) {
			Spot spot = band.spot;
			if (band.geometryMissing) {
				double[] f = new double[imgW];
				double[] df = new double[imgW];
				Arrays.fill(f, Double.NaN);
				double[] fractionBeforeGapFill = null;
				if (params.includeDiagnostics) {
					fractionBeforeGapFill = Arrays.copyOf(f, f.length);
				}
				applyOcclusionBridgeFill(f, params);
				recomputeDfFromF(f, df);
				list.add(new SpotKymoSeries(spot, si, f, df, fractionBeforeGapFill));
				si++;
				continue;
			}
			int y0 = Math.max(0, band.y0);
			int y1 = Math.min(imgH, band.y1Exclusive);
			double[] f = new double[imgW];
			double[] df = new double[imgW];
			for (int x = 0; x < imgW; x++) {
				int[] yRange = columnSignalYRange(y0, y1, x, imgW, r, g, b, nC, params);
				int yStart = yRange[0];
				int yEnd = yRange[1];
				int aboveThresholdRows = 0;
				int valid = 0;
				for (int y = yStart; y < yEnd; y++) {
					int idx = y * imgW + x;
					int rv = sampleChan(r, idx);
					int gv = nC > 1 ? sampleChan(g, idx) : rv;
					int bv = nC > 2 ? sampleChan(b, idx) : rv;
					int sum = rv + gv + bv;
					if (sum < params.minSumRgbForValidPixel) {
						continue;
					}
					valid++;
					boolean pass = false;
					if (metric != null && idx >= 0 && idx < metricLen) {
						double m = metric[idx];
						pass = Double.isFinite(m) && m > thr;
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
			double[] fractionBeforeGapFill = null;
			if (params.includeDiagnostics) {
				fractionBeforeGapFill = Arrays.copyOf(f, f.length);
			}
			applyOcclusionBridgeFill(f, params);
			recomputeDfFromF(f, df);
			list.add(new SpotKymoSeries(spot, si, f, df, fractionBeforeGapFill));
			si++;
		}
		return list;
	}

	/**
	 * Effective vertical integration range for one time column, matching analysis and metric overlay preview.
	 */
	public static int[] columnSignalYRange(int y0, int y1, int x, int imgW, int[] r, int[] g, int[] b, int nC,
			Params params) {
		if (!params.restrictSignalBandPerColumn || y1 <= y0) {
			return new int[] { y0, y1 };
		}
		int bandH = y1 - y0;
		boolean[] sig = new boolean[bandH];
		for (int yi = 0; yi < bandH; yi++) {
			int y = y0 + yi;
			int idx = y * imgW + x;
			int rv = sampleChan(r, idx);
			int gv = nC > 1 ? sampleChan(g, idx) : rv;
			int bv = nC > 2 ? sampleChan(b, idx) : rv;
			int sum = rv + gv + bv;
			if (sum < params.minSumRgbForValidPixel) {
				continue;
			}
			if (params.signalMinMaxRgb > 0) {
				int mx = Math.max(rv, Math.max(gv, bv));
				if (mx < params.signalMinMaxRgb) {
					continue;
				}
			}
			sig[yi] = true;
		}
		int bestLen = 0;
		int bestStart = 0;
		int curStart = -1;
		int curLen = 0;
		for (int i = 0; i < bandH; i++) {
			if (sig[i]) {
				if (curStart < 0) {
					curStart = i;
					curLen = 1;
				} else {
					curLen++;
				}
			} else {
				if (curStart >= 0 && curLen > bestLen) {
					bestLen = curLen;
					bestStart = curStart;
				}
				curStart = -1;
				curLen = 0;
			}
		}
		if (curStart >= 0 && curLen > bestLen) {
			bestLen = curLen;
			bestStart = curStart;
		}
		if (bestLen >= params.effectiveBandMinRunLength) {
			int yLo = y0 + bestStart;
			return new int[] { yLo, yLo + bestLen };
		}
		return new int[] { y0, y1 };
	}

	/**
	 * Fills NaN segments that have a finite value immediately to the left and right (time runs along x).
	 * Values inside the gap are set by linear interpolation between those two finites.
	 */
	private static void applyOcclusionBridgeFill(double[] f, Params params) {
		if (!params.fillOcclusionsLocf) {
			return;
		}
		int n = f.length;
		int x = 0;
		while (x < n) {
			while (x < n && Double.isFinite(f[x])) {
				x++;
			}
			if (x >= n) {
				break;
			}
			int gapStart = x;
			while (x < n && !Double.isFinite(f[x])) {
				x++;
			}
			int gapEnd = x;
			int gapLen = gapEnd - gapStart;
			int left = gapStart - 1;
			int right = gapEnd;
			if (left < 0 || right >= n) {
				continue;
			}
			if (!Double.isFinite(f[left]) || !Double.isFinite(f[right])) {
				continue;
			}
			if (params.locfMaxGapColumns > 0 && gapLen > params.locfMaxGapColumns) {
				continue;
			}
			double vLeft = f[left];
			double vRight = f[right];
			for (int i = 0; i < gapLen; i++) {
				double t = (double) (i + 1) / (double) (gapLen + 1);
				f[gapStart + i] = vLeft + t * (vRight - vLeft);
			}
		}
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
