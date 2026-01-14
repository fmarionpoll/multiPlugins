package plugins.fmp.multiSPOTS96.series.options;

/**
 * Configuration for time-related parameters. Replaces time-related fields from
 * BuildSeriesOptions.
 */
public class TimeOptions {
	private final boolean isFrameFixed;
	private final long firstMs;
	private final long lastMs;
	private final long binDurationMs;
	private final long fromFrame;
	private final int referenceFrame;
	private final int seriesFirst;
	private final int seriesLast;
	private final int backgroundNFrames;
	private final int backgroundFirst;
	private final boolean runBackwards;
	private final boolean analyzePartOnly;

	private TimeOptions(Builder builder) {
		this.isFrameFixed = builder.isFrameFixed;
		this.firstMs = validateTimeMs(builder.firstMs, "firstMs");
		this.lastMs = validateTimeMs(builder.lastMs, "lastMs");
		this.binDurationMs = validateBinDurationMs(builder.binDurationMs);
		this.fromFrame = validateFrameIndex(builder.fromFrame, "fromFrame");
		this.referenceFrame = (int) validateFrameIndex(builder.referenceFrame, "referenceFrame");
		this.seriesFirst = (int) validateFrameIndex(builder.seriesFirst, "seriesFirst");
		this.seriesLast = (int) validateFrameIndex(builder.seriesLast, "seriesLast");
		this.backgroundNFrames = validateBackgroundNFrames(builder.backgroundNFrames);
		this.backgroundFirst = (int) validateFrameIndex(builder.backgroundFirst, "backgroundFirst");
		this.runBackwards = builder.runBackwards;
		this.analyzePartOnly = builder.analyzePartOnly;

		// Validate relationships
		validateTimeRelationships();
	}

	// Getters
	public boolean isFrameFixed() {
		return isFrameFixed;
	}

	public long getFirstMs() {
		return firstMs;
	}

	public long getLastMs() {
		return lastMs;
	}

	public long getBinDurationMs() {
		return binDurationMs;
	}

	public long getFromFrame() {
		return fromFrame;
	}

	public int getReferenceFrame() {
		return referenceFrame;
	}

	public int getSeriesFirst() {
		return seriesFirst;
	}

	public int getSeriesLast() {
		return seriesLast;
	}

	public int getBackgroundNFrames() {
		return backgroundNFrames;
	}

	public int getBackgroundFirst() {
		return backgroundFirst;
	}

	public boolean isRunBackwards() {
		return runBackwards;
	}

	public boolean isAnalyzePartOnly() {
		return analyzePartOnly;
	}

	// Validation methods
	private long validateTimeMs(long timeMs, String fieldName) {
		if (timeMs < 0) {
			throw new IllegalArgumentException(fieldName + " must be non-negative, got: " + timeMs);
		}
		return timeMs;
	}

	private long validateBinDurationMs(long binDurationMs) {
		if (binDurationMs < 1) {
			throw new IllegalArgumentException("Bin duration must be positive, got: " + binDurationMs);
		}
		return binDurationMs;
	}

	private long validateFrameIndex(long frameIndex, String fieldName) {
		if (frameIndex < 0) {
			throw new IllegalArgumentException(fieldName + " must be non-negative, got: " + frameIndex);
		}
		return frameIndex;
	}

	private int validateBackgroundNFrames(int backgroundNFrames) {
		if (backgroundNFrames < 1) {
			throw new IllegalArgumentException(
					"Background number of frames must be positive, got: " + backgroundNFrames);
		}
		return backgroundNFrames;
	}

	private void validateTimeRelationships() {
		if (isFrameFixed && firstMs > lastMs) {
			throw new IllegalArgumentException(
					"First time (" + firstMs + ") cannot be greater than last time (" + lastMs + ")");
		}
		if (seriesFirst > seriesLast && seriesLast > 0) {
			throw new IllegalArgumentException(
					"Series first (" + seriesFirst + ") cannot be greater than series last (" + seriesLast + ")");
		}
	}

	// Builder pattern
	public static class Builder {
		private boolean isFrameFixed = false;
		private long firstMs = 0;
		private long lastMs = 0;
		private long binDurationMs = 1;
		private long fromFrame = 0;
		private int referenceFrame = 0;
		private int seriesFirst = 0;
		private int seriesLast = 0;
		private int backgroundNFrames = 60;
		private int backgroundFirst = 0;
		private boolean runBackwards = false;
		private boolean analyzePartOnly = false;

		public Builder frameFixed(boolean isFrameFixed) {
			this.isFrameFixed = isFrameFixed;
			return this;
		}

		public Builder firstMs(long firstMs) {
			this.firstMs = firstMs;
			return this;
		}

		public Builder lastMs(long lastMs) {
			this.lastMs = lastMs;
			return this;
		}

		public Builder binDurationMs(long binDurationMs) {
			this.binDurationMs = binDurationMs;
			return this;
		}

		public Builder fromFrame(long fromFrame) {
			this.fromFrame = fromFrame;
			return this;
		}

		public Builder referenceFrame(int referenceFrame) {
			this.referenceFrame = referenceFrame;
			return this;
		}

		public Builder seriesFirst(int seriesFirst) {
			this.seriesFirst = seriesFirst;
			return this;
		}

		public Builder seriesLast(int seriesLast) {
			this.seriesLast = seriesLast;
			return this;
		}

		public Builder backgroundNFrames(int backgroundNFrames) {
			this.backgroundNFrames = backgroundNFrames;
			return this;
		}

		public Builder backgroundFirst(int backgroundFirst) {
			this.backgroundFirst = backgroundFirst;
			return this;
		}

		public Builder runBackwards(boolean runBackwards) {
			this.runBackwards = runBackwards;
			return this;
		}

		public Builder analyzePartOnly(boolean analyzePartOnly) {
			this.analyzePartOnly = analyzePartOnly;
			return this;
		}

		public TimeOptions build() {
			return new TimeOptions(this);
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	// Create from legacy BuildSeriesOptions
	public static TimeOptions fromLegacyOptions(plugins.fmp.multiSPOTS96.series.BuildSeriesOptions legacy) {
		return builder().frameFixed(legacy.isFrameFixed).firstMs(legacy.t_Ms_First).lastMs(legacy.t_Ms_Last)
				.binDurationMs(legacy.t_Ms_BinDuration).fromFrame(legacy.fromFrame)
				.referenceFrame(legacy.referenceFrame).seriesFirst(legacy.seriesFirst).seriesLast(legacy.seriesLast)
				.backgroundNFrames(legacy.backgroundNFrames).backgroundFirst(legacy.backgroundFirst)
				.runBackwards(legacy.runBackwards).analyzePartOnly(legacy.analyzePartOnly).build();
	}
}