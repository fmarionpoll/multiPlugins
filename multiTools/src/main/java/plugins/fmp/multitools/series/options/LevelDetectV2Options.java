package plugins.fmp.multitools.series.options;

import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;

/**
 * Algorithm knobs for kymograph top-level detection v2 (temporal tracking, edge
 * peak, tape prepass, post-smoothing). Kept separate from legacy
 * {@link BuildSeriesOptions} pass1/pass2 recipes.
 */
public class LevelDetectV2Options {

	public ImageTransformEnums transform = ImageTransformEnums.RGB_DIFFS;
	public boolean directionUp = true;
	public int threshold = 35;

	/** Apply {@code MINUSHORIZAVG} on the raw kymo before the color transform. */
	public boolean removeHorizontalAverage = false;

	/** When true, localize by vertical edge peak; otherwise first threshold crossing. */
	public boolean edgePeak = true;

	/** Max pixels the level may rise between consecutive columns (plateau noise). */
	public int trackUp = 3;
	/** Max pixels the level may drop between consecutive columns (gulps). */
	public int trackDown = 25;

	/** Odd median window on Y(t); 1 or less disables. */
	public int medianWindow = 5;
	/** Replace points that deviate from neighbors by more than this many pixels. */
	public int maxSpikePx = 4;

	public static final int TOP_SEARCH_OFFSET_PIXELS = 5;

	public LevelDetectV2Options copy() {
		LevelDetectV2Options o = new LevelDetectV2Options();
		o.transform = transform;
		o.directionUp = directionUp;
		o.threshold = threshold;
		o.removeHorizontalAverage = removeHorizontalAverage;
		o.edgePeak = edgePeak;
		o.trackUp = trackUp;
		o.trackDown = trackDown;
		o.medianWindow = medianWindow;
		o.maxSpikePx = maxSpikePx;
		return o;
	}
}
