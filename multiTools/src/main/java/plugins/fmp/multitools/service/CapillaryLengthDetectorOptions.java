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
	 * Fraction of the contrast range (between background and capillary) above which
	 * a position is considered to be on the capillary.
	 */
	public double thresholdFraction = 0.35;

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
