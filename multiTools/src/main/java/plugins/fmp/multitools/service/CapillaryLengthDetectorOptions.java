package plugins.fmp.multitools.service;

/**
 * Parameters of the ROI-end change-point measurement performed by
 * {@link CapillaryLengthDetector}.
 */
public class CapillaryLengthDetectorOptions {

	/** First cam frame used for the measurement. */
	public int frameIndex = 0;

	/**
	 * Number of frames combined before detection. Image 0 is the annotation and
	 * calibration reference, so one frame is the safe default when a recording may
	 * drift. Users can still request a temporal median for stable recordings with
	 * transient fly occlusions.
	 */
	public int nFramesAveraged = 1;

	/**
	 * Spacing between those frames. A fly sitting for a couple of frames is
	 * then outvoted by the median; the glass walls are in every frame.
	 */
	public int frameStride = 3;

	/**
	 * Half length, in pixels, of the transverse strip sampled across the capillary.
	 */
	public int perpendicularHalfLength = 12;

	/** Number of axis samples used to estimate the local capillary direction. */
	public int tangentWindow = 8;

	/**
	 * Pixels searched inward from each ROI end. Covers the usual 5-10 px overhang
	 * plus a little extra if the tip sits slightly further in.
	 */
	public int inwardSearchPixels = 30;

	/**
	 * Upper bound when the first window is still outside the tube (a longer
	 * overhang into the cage). Past this the ROI is treated as not containing
	 * the tip.
	 */
	public int inwardSearchMaxPixels = 120;

	/** Samples at the ROI end used as the local outside / background reference. */
	public int outsidePixels = 5;

	/**
	 * Consecutive capillary-like samples required after a candidate tip, so a fly
	 * or a dust speck in the overhang is not taken as the glass end.
	 */
	public int persistencePixels = 4;

	/**
	 * Stretch of wall-like scores that must follow a candidate tip before it is
	 * accepted. A lid edge or a dust line is a few pixels; real glass continues.
	 */
	public int confirmationPixels = 12;

	/**
	 * Minimum rise of the wall score above the overhang baseline to treat a
	 * sample as glass. Empty glass is only a little darker than air, so this
	 * must stay low; liquid is not used as the inside reference.
	 */
	public double capillaryScoreThreshold = 0.8;

	/**
	 * Optional inward correction from the first persistent side-wall signal,
	 * expressed as a fraction of the measured capillary half-width. Ground-truth
	 * validation at both 640- and 1280-pixel resolution showed that the detected
	 * onset already matches the visible tip, so correction is disabled by default.
	 */
	public double tipInsetHalfWidthScale = 0.;

	/** Lower and upper safety bounds for the optical tip correction, in pixels. */
	public double tipInsetMinPixels = 2.0;
	public double tipInsetMaxPixels = 6.0;

	/** Minimum detected length, as a fraction of the ROI length, to accept a measure. */
	public double minLengthFraction = 0.30;

	/** Robust deviation beyond which a capillary is flagged as an outlier. */
	public double outlierMadFactor = 3.0;

	/**
	 * Smallest departure from the trend, as a fraction of the median length, that
	 * may be called an outlier.
	 */
	public double outlierMinTolerance = 0.03;

	/**
	 * Median physical capillary length expressed in cage pitches. This robust
	 * calibration was obtained from the manually checked experiments and is
	 * independent of image resolution.
	 */
	public double capillaryLengthPerCagePitch = 4.34;

	/**
	 * Weight of the frame-derived absolute length relative to detected tips. A soft
	 * prior tolerates perspective changes between horizontal cage pitch and the
	 * vertical capillary scale.
	 */
	public double frameScalePriorWeight = 0.75;

	/** Use the detected ten-cage frame to anchor the absolute length trend. */
	public boolean useFrameScalePrior = true;

	/** Physical length of the calibrated capillaries, used for reporting mm/pixel. */
	public double physicalLengthMm = 32.0;
}
