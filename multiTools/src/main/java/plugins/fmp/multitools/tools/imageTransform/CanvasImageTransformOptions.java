package plugins.fmp.multitools.tools.imageTransform;

import java.awt.Color;
import java.util.ArrayList;

import icy.image.IcyBufferedImage;

public class CanvasImageTransformOptions {
	public ImageTransformEnums transformOption;
	public IcyBufferedImage backgroundImage = null;
	public IcyBufferedImage secondImage = null;
	public int npixels_changed = 0;
	public boolean copyResultsToThe3planes = true;

	public int xfirst;
	public int xlast;
	public int yfirst;
	public int ylast;
	public int channel0;
	public int channel1;
	public int channel2;
	public int w0 = 1;
	public int w1 = 1;
	public int w2 = 1;
	public int spanDiff = 3;
	public int simplethreshold = 255;
	public int background_delta = 50;
	public int background_jitter = 1;

	public int colorthreshold = 0;
	public int colordistanceType = 0;
	public boolean ifGreater = true;

	/** Pixel shift applied to the source before reference subtraction (drift preview; sub-pixel OK). */
	public double translateDx = 0;
	/** Pixel shift applied to the source before reference subtraction (drift preview; sub-pixel OK). */
	public double translateDy = 0;

	/**
	 * Rotate source about ({@link #rotatePivotX}, {@link #rotatePivotY}) by this
	 * angle (radians, CCW in image coords) before translation and reference
	 * subtraction.
	 */
	public double rotationRadians = 0;
	public double rotatePivotX = 0;
	public double rotatePivotY = 0;

	/**
	 * When true, the shift+t-ref preview applies per-channel 1st–99th percentile
	 * contrast stretch after the legacy {@code 255-|a-b|} difference (preview only).
	 */
	public boolean differencePreviewAutoContrast = false;

	public final byte byteFALSE = 0;
	public final byte byteTRUE = (byte) 0xFF;
	public ArrayList<Color> colorarray = null;

	/** Used when {@link #transformOption} is {@link ImageTransformEnums#THRESHOLD_COLORS}. */
	public SpotThresholdColorSpace thresholdColorSpace = SpotThresholdColorSpace.RGB;

	/**
	 * Optional second reference set: pixels matching {@link #colorarray} but also within
	 * {@link #colorExcludeThreshold} of any exclude color are treated as non-match (background guard).
	 */
	public ArrayList<Color> colorExcludeArray = null;
	public int colorExcludeThreshold = 0;

	public void setSingleThreshold(int simplethreshold, boolean ifGreater) {
		this.simplethreshold = simplethreshold;
		this.ifGreater = ifGreater;
	}

	public void setColorArrayThreshold(int colordistanceType, int colorthreshold, ArrayList<Color> colorarray) {
		setColorArrayThreshold(colordistanceType, colorthreshold, colorarray, SpotThresholdColorSpace.RGB);
	}

	public void setColorArrayThreshold(int colordistanceType, int colorthreshold, ArrayList<Color> colorarray,
			SpotThresholdColorSpace colorSpace) {
		transformOption = ImageTransformEnums.THRESHOLD_COLORS;
		this.colordistanceType = colordistanceType;
		this.colorthreshold = colorthreshold;
		this.colorarray = colorarray;
		this.thresholdColorSpace = colorSpace != null ? colorSpace : SpotThresholdColorSpace.RGB;
	}

	public void setColorExcludeThreshold(ArrayList<Color> excludeColors, int excludeThreshold) {
		if (excludeColors == null || excludeColors.isEmpty() || excludeThreshold <= 0) {
			this.colorExcludeArray = null;
			this.colorExcludeThreshold = 0;
		} else {
			this.colorExcludeArray = new ArrayList<>(excludeColors);
			this.colorExcludeThreshold = excludeThreshold;
		}
	}
}
