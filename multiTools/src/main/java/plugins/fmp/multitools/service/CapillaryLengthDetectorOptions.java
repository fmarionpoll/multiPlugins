package plugins.fmp.multitools.service;

/**
 * Parameters of the ROI-guided capillary length measurement performed by
 * {@link CapillaryLengthDetector}.
 */
public class CapillaryLengthDetectorOptions {

	/** First cam frame used for the measurement. */
	public int frameIndex = 0;

	/** Number of consecutive frames averaged before detection (noise reduction). */
	public int nFramesAveraged = 3;

	/** Half length, in pixels, of the cross-section sampled across the capillary axis. */
	public int perpendicularHalfLength = 12;

	/** Half width, in pixels, of the band considered to be inside the capillary. */
	public int capillaryHalfWidth = 4;

	/** Pixels skipped between the inner band and the background flanks. */
	public int flankGap = 2;

	/** Number of axis samples used to estimate the local capillary direction. */
	public int tangentWindow = 8;

	/**
	 * Closest and farthest distance from the axis, in pixels, where the two glass
	 * walls are looked for. The wall pair marks the tube even where it holds no
	 * liquid, which is what keeps an empty upper section from being cut off.
	 */
	public int wallSearchMin = 2;
	public int wallSearchMax = 8;

	/**
	 * Grey levels a wall must stand out from the background to be believed. Without
	 * this floor, plain noise in a tube-free area would look like a faint wall pair.
	 */
	public double wallMinContrast = 3.;

	/** Same floor for the liquid clue: below it the tube is simply not visible. */
	public double liquidMinContrast = 4.;

	/**
	 * Fraction of the contrast range (between background and capillary) above which
	 * a position is considered to be on the capillary.
	 */
	public double thresholdFraction = 0.35;

	/**
	 * Longest interruption, in pixels, crossed while following the capillary. The
	 * rack holding the tubes shows up as a dark horizontal bar that hides every
	 * capillary over a few tens of pixels; without this the measure would stop
	 * there and report only the upper or the lower half of the tube.
	 */
	public int maxGapPixels = 50;

	/**
	 * Pixels the search runs past each end of the ROI. Users draw the ROI a little
	 * longer than the tube, but not always, and a tip falling just outside would
	 * otherwise be missed.
	 */
	public int axisExtensionPixels = 10;

	/**
	 * Fraction of the ROI trimmed at each end before looking for the capillary. The
	 * detected run is afterwards extended back into the trimmed zone, so this only
	 * prevents ROI border artefacts from seeding the search.
	 */
	public double searchMarginFraction = 0.02;

	/** Minimum detected length, as a fraction of the ROI length, to accept a measure. */
	public double minLengthFraction = 0.30;

	/** Robust deviation beyond which a capillary is flagged as an outlier. */
	public double outlierMadFactor = 3.0;

	/** Physical length of the calibrated capillaries, used for reporting mm/pixel. */
	public double physicalLengthMm = 32.0;
}
