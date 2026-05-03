package plugins.fmp.multitools.experiment.timebase;

/**
 * Result of {@link TimestepResolver#resolve}.
 */
public final class TimestepResolutionResult {

	private final long stepMs;
	private final MeasureTimebase source;
	private final boolean preferPhysicalKymoFrameCount;
	private final boolean usedFallback60s;

	public TimestepResolutionResult(long stepMs, MeasureTimebase source, boolean preferPhysicalKymoFrameCount,
			boolean usedFallback60s) {
		this.stepMs = stepMs;
		this.source = source != null ? source : MeasureTimebase.UNKNOWN;
		this.preferPhysicalKymoFrameCount = preferPhysicalKymoFrameCount;
		this.usedFallback60s = usedFallback60s;
	}

	public long getStepMs() {
		return stepMs;
	}

	public MeasureTimebase getSource() {
		return source;
	}

	public boolean isPreferPhysicalKymoFrameCount() {
		return preferPhysicalKymoFrameCount;
	}

	public boolean isUsedFallback60s() {
		return usedFallback60s;
	}
}
