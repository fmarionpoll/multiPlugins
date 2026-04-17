package plugins.fmp.multitools.experiment;

public class BinDescription {
	private long firstKymoColMs = 0;
	private long lastKymoColMs = 0;
	private long binKymoColMs = 60000; // Default 60 seconds
	private String binDirectory = null;
	/** User-confirmed nominal interval in seconds for bin directory naming; -1 if not set (derive from binKymoColMs). */
	private int nominalIntervalSec = -1;

	/**
	 * Median raw camera inter-frame interval in ms (i.e. the acquisition rate).
	 * -1 if unknown / not yet measured. Kept separate from {@link #binKymoColMs}
	 * so that subsampling can be distinguished from pure raw storage.
	 */
	private long cameraIntervalMs = -1;

	/**
	 * Integer subsample factor: 1 = every frame kept, 3 = every 3rd frame kept, etc.
	 * Defined as max(1, round(binKymoColMs / cameraIntervalMs)). 1 when the camera
	 * interval is unknown.
	 */
	private int subsampleFactor = 1;

	/** How the measures in the directory were produced. */
	private GenerationMode generationMode = GenerationMode.UNKNOWN;

	/**
	 * Cheap sanity flag: whether measure files were present last time the directory
	 * was scanned or saved. Not authoritative - the resolver rescans when deciding.
	 */
	private boolean measuresPresent = false;

	public BinDescription() {
	}

	public BinDescription(long firstKymoColMs, long lastKymoColMs, long binKymoColMs, String binDirectory) {
		this.firstKymoColMs = firstKymoColMs;
		this.lastKymoColMs = lastKymoColMs;
		this.binKymoColMs = binKymoColMs;
		this.binDirectory = binDirectory;
	}

	public long getFirstKymoColMs() {
		return firstKymoColMs;
	}

	public void setFirstKymoColMs(long firstKymoColMs) {
		this.firstKymoColMs = firstKymoColMs;
	}

	public long getLastKymoColMs() {
		return lastKymoColMs;
	}

	public void setLastKymoColMs(long lastKymoColMs) {
		this.lastKymoColMs = lastKymoColMs;
	}

	public long getBinKymoColMs() {
		return binKymoColMs;
	}

	public void setBinKymoColMs(long binKymoColMs) {
		this.binKymoColMs = binKymoColMs;
	}

	public String getBinDirectory() {
		return binDirectory;
	}

	public void setBinDirectory(String binDirectory) {
		this.binDirectory = binDirectory;
	}

	/**
	 * Returns the user-confirmed nominal interval in seconds, or -1 if not set.
	 * When -1, callers should derive from {@link #getBinKymoColMs()} (e.g. round to nearest second).
	 */
	public int getNominalIntervalSec() {
		return nominalIntervalSec;
	}

	public void setNominalIntervalSec(int nominalIntervalSec) {
		this.nominalIntervalSec = nominalIntervalSec;
	}

	public long getCameraIntervalMs() {
		return cameraIntervalMs;
	}

	public void setCameraIntervalMs(long cameraIntervalMs) {
		this.cameraIntervalMs = cameraIntervalMs;
	}

	public int getSubsampleFactor() {
		return subsampleFactor;
	}

	public void setSubsampleFactor(int subsampleFactor) {
		this.subsampleFactor = Math.max(1, subsampleFactor);
	}

	public GenerationMode getGenerationMode() {
		return generationMode == null ? GenerationMode.UNKNOWN : generationMode;
	}

	public void setGenerationMode(GenerationMode generationMode) {
		this.generationMode = generationMode == null ? GenerationMode.UNKNOWN : generationMode;
	}

	public boolean isMeasuresPresent() {
		return measuresPresent;
	}

	public void setMeasuresPresent(boolean measuresPresent) {
		this.measuresPresent = measuresPresent;
	}

	/**
	 * Effective interval per sample in ms, i.e. {@code cameraIntervalMs * subsampleFactor}
	 * when both are known, otherwise falls back to {@link #binKymoColMs}.
	 */
	public long getEffectiveIntervalMs() {
		if (cameraIntervalMs > 0 && subsampleFactor > 0)
			return cameraIntervalMs * subsampleFactor;
		return binKymoColMs;
	}

	public void copyFrom(BinDescription other) {
		if (other != null) {
			this.firstKymoColMs = other.firstKymoColMs;
			this.lastKymoColMs = other.lastKymoColMs;
			this.binKymoColMs = other.binKymoColMs;
			this.binDirectory = other.binDirectory;
			this.nominalIntervalSec = other.nominalIntervalSec;
			this.cameraIntervalMs = other.cameraIntervalMs;
			this.subsampleFactor = other.subsampleFactor;
			this.generationMode = other.generationMode;
			this.measuresPresent = other.measuresPresent;
		}
	}

	public boolean isValid() {
		return binKymoColMs > 0 && lastKymoColMs > firstKymoColMs;
	}
}
