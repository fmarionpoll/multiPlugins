package plugins.fmp.multitools.experiment.capillary.geometry;

import java.awt.geom.Line2D;

import plugins.fmp.multitools.experiment.BinDescription;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.GenerationMode;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.tools.results.EnumResults;

/**
 * Native-pixel to microlitre scale. Measures stay in source pixels; this helper
 * is applied at chart/Excel time.
 */
public final class CapillaryPixelScale {
	public enum MeasurePixelSource {
		CAM_DIRECT, GREEN_KYMO, BLUE_NORMED_KYMO
	}

	private CapillaryPixelScale() {
	}

	public static MeasurePixelSource sourceOf(Experiment exp) {
		if (exp == null)
			return MeasurePixelSource.GREEN_KYMO;
		BinDescription bin = exp.getActiveBinDescription();
		if (bin != null && bin.getGenerationMode() == GenerationMode.DIRECT_FROM_STACK)
			return MeasurePixelSource.CAM_DIRECT;
		if (bin != null && bin.isKymoFromNormedBlue())
			return MeasurePixelSource.BLUE_NORMED_KYMO;
		return MeasurePixelSource.GREEN_KYMO;
	}

	public static double expansionRatioOf(Experiment exp) {
		if (exp == null || exp.getActiveBinDescription() == null)
			return 0.10;
		double e = exp.getActiveBinDescription().getKymoBlueExpansionRatio();
		if (!Double.isFinite(e) || e < 0)
			return 0.10;
		return e;
	}

	public static double blueLengthPx(Capillary cap, long t) {
		if (cap == null)
			return 0;
		if (cap.getPhaseGeometry() != null && cap.getPhaseGeometry().isInitialized()) {
			Line2D blue = cap.getPhaseGeometry().getBlueAt(t);
			if (blue != null) {
				double length = blue.getP1().distance(blue.getP2());
				if (length > 0)
					return length;
			}
		}
		return Math.max(0, cap.getPixels());
	}

	public static double maxBlueLengthPx(Capillary cap) {
		if (cap == null)
			return 0;
		if (cap.getPhaseGeometry() != null && cap.getPhaseGeometry().isInitialized()) {
			double max = 0;
			for (Line2D blue : cap.getPhaseGeometry().getBlueKeyframes().values()) {
				if (blue == null)
					continue;
				double length = blue.getP1().distance(blue.getP2());
				if (length > max)
					max = length;
			}
			if (max > 0)
				return max;
		}
		return Math.max(0, cap.getPixels());
	}

	public static double pixelsFor32mm(Capillary cap, long t, MeasurePixelSource source) {
		if (source == MeasurePixelSource.BLUE_NORMED_KYMO)
			return maxBlueLengthPx(cap);
		return blueLengthPx(cap, t);
	}

	public static double ulPerNativePixel(Capillary cap, long t, MeasurePixelSource source, double expansionRatio) {
		if (cap == null || cap.getVolume() <= 0)
			return 1.0;
		if (source == MeasurePixelSource.BLUE_NORMED_KYMO) {
			double e = finiteExpansion(expansionRatio);
			int h = NormedBlueKymoGeometry.kymoHeight(cap, e);
			if (h < 2)
				return fallbackUlPerPixel(cap);
			return cap.getVolume() * (1.0 + e) / h;
		}
		double scale = pixelsFor32mm(cap, t, source);
		if (scale <= 0)
			return fallbackUlPerPixel(cap);
		return cap.getVolume() / scale;
	}

	public static double absoluteRowOffset(Capillary cap, MeasurePixelSource source, double expansionRatio) {
		if (source != MeasurePixelSource.BLUE_NORMED_KYMO)
			return 0;
		double e = finiteExpansion(expansionRatio);
		if (e <= 0)
			return 0;
		int h = NormedBlueKymoGeometry.kymoHeight(cap, e);
		return h * e / (2.0 * (1.0 + e));
	}

	public static double toUl(double yNative, Capillary cap, long t, MeasurePixelSource source, double expansionRatio,
			boolean absoluteLevel) {
		double y = yNative;
		if (absoluteLevel)
			y -= absoluteRowOffset(cap, source, expansionRatio);
		return y * ulPerNativePixel(cap, t, source, expansionRatio);
	}

	public static boolean isVolumeUnit(EnumResults resultType) {
		return resultType != null && "volume (ul)".equals(resultType.toUnit());
	}

	public static boolean isCountType(EnumResults resultType) {
		if (resultType == null)
			return false;
		switch (resultType) {
		case NBGULPS:
		case TTOGULP:
		case TTOGULP_LR:
		case AUTOCORREL:
		case CROSSCORREL:
		case CROSSCORREL_LR:
			return true;
		default:
			return false;
		}
	}

	public static boolean isAbsoluteLevel(EnumResults resultType) {
		if (resultType == null)
			return false;
		switch (resultType) {
		case TOPRAW:
		case TOPLEVEL:
		case BOTTOMLEVEL:
		case TOPRAW00:
		case TOPLEVEL00:
			return true;
		default:
			return false;
		}
	}

	public static long frameOfColumn(int column, int subsampleFactor) {
		int factor = Math.max(1, subsampleFactor);
		return (long) column * (long) factor;
	}

	public static void applyToUl(java.util.List<Double> nativeValues, double[] dest, int destLen, Capillary cap,
			Experiment exp, EnumResults resultType) {
		if (nativeValues == null || dest == null || destLen <= 0)
			return;
		int len = Math.min(destLen, Math.min(dest.length, nativeValues.size()));
		if (isCountType(resultType) || !isVolumeUnit(resultType)) {
			for (int i = 0; i < len; i++)
				dest[i] = nativeValues.get(i);
			return;
		}
		MeasurePixelSource source = sourceOf(exp);
		double e = expansionRatioOf(exp);
		int factor = exp != null ? Math.max(1, exp.getKymoSubsampleFactor()) : 1;
		boolean abs = isAbsoluteLevel(resultType);
		for (int i = 0; i < len; i++)
			dest[i] = toUl(nativeValues.get(i), cap, frameOfColumn(i, factor), source, e, abs);
	}

	private static double finiteExpansion(double expansionRatio) {
		if (!Double.isFinite(expansionRatio) || expansionRatio < 0)
			return 0;
		return expansionRatio;
	}

	private static double fallbackUlPerPixel(Capillary cap) {
		if (cap.getPixels() > 0 && cap.getVolume() > 0)
			return cap.getVolume() / cap.getPixels();
		return 1.0;
	}
}
