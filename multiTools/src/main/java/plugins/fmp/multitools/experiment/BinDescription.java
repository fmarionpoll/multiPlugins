package plugins.fmp.multitools.experiment;

import plugins.fmp.multitools.experiment.timebase.MeasureTimebase;

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

	private MeasureTimebase primaryTimebase = MeasureTimebase.UNKNOWN;

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

	public MeasureTimebase getPrimaryTimebase() {
		return primaryTimebase == null ? MeasureTimebase.UNKNOWN : primaryTimebase;
	}

	public void setPrimaryTimebase(MeasureTimebase primaryTimebase) {
		this.primaryTimebase = primaryTimebase == null ? MeasureTimebase.UNKNOWN : primaryTimebase;
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
			this.primaryTimebase = other.primaryTimebase;
		}
	}

	public boolean isValid() {
		return binKymoColMs > 0 && lastKymoColMs > firstKymoColMs;
	}

	/**
	 * If {@code binKymoColMs} was incorrectly stored as the camera interval while
	 * {@code nominalIntervalSec} or a {@code bin_N} directory name indicates a
	 * different analysis bin, repair {@code binKymoColMs} to the analysis bin.
	 *
	 * @return true if a repair was applied
	 */
	public boolean repairBinKymoIfConflatedWithCamera() {
		long expectedAnalysisMs = resolveExpectedAnalysisBinMs();
		if (expectedAnalysisMs <= 0 || binKymoColMs <= 0) {
			return false;
		}
		long cam = cameraIntervalMs;
		boolean binEqualsCamera = cam > 0 && Math.abs(binKymoColMs - cam) <= Math.max(1000L, cam / 100L);
		boolean binDiffersFromExpected = Math.abs(binKymoColMs - expectedAnalysisMs) > Math.max(1000L,
				expectedAnalysisMs / 100L);
		if (binEqualsCamera && binDiffersFromExpected) {
			binKymoColMs = expectedAnalysisMs;
			if (cam > 0) {
				subsampleFactor = (int) Math.max(1L, Math.round(binKymoColMs / (double) cam));
			}
			return true;
		}
		return false;
	}

	/**
	 * Analysis bin in ms from nominal seconds or {@code bin_N} directory name.
	 */
	public long resolveExpectedAnalysisBinMs() {
		if (nominalIntervalSec > 0) {
			return nominalIntervalSec * 1000L;
		}
		int fromDir = parseBinDirectorySeconds(binDirectory);
		if (fromDir > 0) {
			return fromDir * 1000L;
		}
		return -1L;
	}

	static int parseBinDirectorySeconds(String binDirectory) {
		if (binDirectory == null || binDirectory.isEmpty()) {
			return -1;
		}
		String name = binDirectory;
		int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
		if (slash >= 0 && slash + 1 < name.length()) {
			name = name.substring(slash + 1);
		}
		if (!name.startsWith("bin_")) {
			return -1;
		}
		String num = name.substring(4).trim();
		try {
			int sec = Integer.parseInt(num);
			return sec > 0 ? sec : -1;
		} catch (NumberFormatException e) {
			return -1;
		}
	}
}
