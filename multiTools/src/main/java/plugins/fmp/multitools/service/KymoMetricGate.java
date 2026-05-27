package plugins.fmp.multitools.service;

/**
 * Threshold tests for kymograph metrics (same convention as fly vs spot in spot-level detection).
 */
public final class KymoMetricGate {

	private KymoMetricGate() {
	}

	/**
	 * @param passWhenAbove when {@code true}, passes when {@code m > thr}; when {@code false}, passes when
	 *          {@code m <= thr} (same shape as {@code flyThresholdUp} in {@code BuildSeriesOptions}).
	 */
	public static boolean directedFinite(double m, int thr, boolean passWhenAbove) {
		if (!Double.isFinite(m)) {
			return false;
		}
		boolean above = m > thr;
		return passWhenAbove ? above : !above;
	}
}
