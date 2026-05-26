package plugins.fmp.multitools.service;

import java.util.ArrayList;
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
import plugins.fmp.multitools.service.KymographChromaAnalysisResult.SpotChromaKymoSeries;
import plugins.fmp.multitools.tools.Logger;

/**
 * Computes per-spot vertical chroma fraction time series from loaded cage kymograph TIFFs.
 */
public final class CageKymographChromaAnalyzer {

	public static final class Params {
		public int chromaThreshold;
		/** Pixel is valid only if R+G+B is at least this (occluded/black pixels skipped). */
		public int minSumRgbForValidPixel;
		public int minValidRowsPerColumn;

		public Params(int chromaThreshold, int minSumRgbForValidPixel, int minValidRowsPerColumn) {
			this.chromaThreshold = chromaThreshold;
			this.minSumRgbForValidPixel = minSumRgbForValidPixel;
			this.minValidRowsPerColumn = minValidRowsPerColumn;
		}
	}

	private CageKymographChromaAnalyzer() {
	}

	public static KymographChromaAnalysisResult analyze(Experiment exp, Params params) {
		if (exp == null || params == null) {
			return new KymographChromaAnalysisResult(Map.of(), new double[0], 0);
		}
		SequenceKymos sk = exp.getSeqKymos();
		if (sk == null || sk.getSequence() == null) {
			Logger.warn("CageKymographChromaAnalyzer: no kymograph sequence loaded");
			return new KymographChromaAnalysisResult(Map.of(), new double[0], 0);
		}
		Spots spots = exp.getSpots();
		if (spots == null || exp.getCages() == null) {
			return new KymographChromaAnalysisResult(Map.of(), new double[0], 0);
		}
		int refW = 0;
		int refH = 0;
		if (exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null) {
			refW = exp.getSeqCamData().getSequence().getSizeX();
			refH = exp.getSeqCamData().getSequence().getSizeY();
		}
		int nT = sk.getImageLoader().getNTotalFrames();
		if (nT <= 0) {
			return new KymographChromaAnalysisResult(Map.of(), new double[0], 0);
		}

		Map<Integer, List<SpotChromaKymoSeries>> out = new LinkedHashMap<>();
		int widthBins = 0;
		double[] xAxis = new double[0];

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
			List<CageKymographSpotBands> bands = CageKymographSpotBands.layout(cage, spots, refW, refH);
			if (bands.isEmpty()) {
				continue;
			}
			if (widthBins == 0) {
				widthBins = imgW;
				xAxis = buildXAxisMinutes(exp, widthBins);
			} else if (imgW != widthBins) {
				Logger.warn("CageKymographChromaAnalyzer: image width " + imgW + " differs from first (" + widthBins
						+ "); series will clip to shorter width");
			}

			int cageId = cage.getProperties() != null ? cage.getProperties().getCageID() : -1;
			int useW = widthBins > 0 ? Math.min(imgW, widthBins) : imgW;
			List<SpotChromaKymoSeries> series = computeForImage(img, bands, params, useW);
			out.put(cageId, series);
		}

		return new KymographChromaAnalysisResult(out, xAxis, widthBins);
	}

	private static double[] buildXAxisMinutes(Experiment exp, int widthBins) {
		double[] x = new double[widthBins];
		long step = Math.max(1L, exp.getKymoBin_ms());
		long first = exp.getKymoFirst_ms();
		for (int col = 0; col < widthBins; col++) {
			x[col] = (first + (long) col * step) / 60000.0;
		}
		return x;
	}

	private static List<SpotChromaKymoSeries> computeForImage(IcyBufferedImage img, List<CageKymographSpotBands> bands,
			Params params, int imgW) {
		int imgH = img.getHeight();
		int nC = Math.max(1, img.getSizeC());
		int[] r = nC > 0 ? Array1DUtil.arrayToIntArray(img.getDataXY(0), img.isSignedDataType()) : null;
		int[] g = nC > 1 ? Array1DUtil.arrayToIntArray(img.getDataXY(1), img.isSignedDataType()) : null;
		int[] b = nC > 2 ? Array1DUtil.arrayToIntArray(img.getDataXY(2), img.isSignedDataType()) : null;

		List<SpotChromaKymoSeries> list = new ArrayList<>();
		int si = 0;
		for (CageKymographSpotBands band : bands) {
			Spot spot = band.spot;
			int y0 = Math.max(0, band.y0);
			int y1 = Math.min(imgH, band.y1Exclusive);
			double[] f = new double[imgW];
			double[] df = new double[imgW];
			for (int x = 0; x < imgW; x++) {
				int chromaRows = 0;
				int valid = 0;
				for (int y = y0; y < y1; y++) {
					int idx = y * imgW + x;
					int rv = sampleChan(r, idx);
					int gv = nC > 1 ? sampleChan(g, idx) : rv;
					int bv = nC > 2 ? sampleChan(b, idx) : rv;
					int sum = rv + gv + bv;
					if (sum < params.minSumRgbForValidPixel) {
						continue;
					}
					valid++;
					int sdiff = Math.abs(rv - gv) + Math.abs(rv - bv) + Math.abs(gv - bv);
					if (sdiff > params.chromaThreshold) {
						chromaRows++;
					}
				}
				if (valid < params.minValidRowsPerColumn) {
					f[x] = Double.NaN;
				} else {
					f[x] = (double) chromaRows / (double) valid;
				}
			}
			for (int x = 0; x < imgW; x++) {
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
			list.add(new SpotChromaKymoSeries(spot, si, f, df));
			si++;
		}
		return list;
	}

	private static int sampleChan(int[] ch, int idx) {
		if (ch == null || idx < 0 || idx >= ch.length) {
			return 0;
		}
		return ch[idx];
	}
}
