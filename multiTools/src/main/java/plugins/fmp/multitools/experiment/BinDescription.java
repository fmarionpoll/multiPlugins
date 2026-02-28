package plugins.fmp.multitools.experiment;

public class BinDescription {
	private long firstKymoColMs = 0;
	private long lastKymoColMs = 0;
	private long binKymoColMs = 60000; // Default 60 seconds
	private String binDirectory = null;
	/** User-confirmed nominal interval in seconds for bin directory naming; -1 if not set (derive from binKymoColMs). */
	private int nominalIntervalSec = -1;

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

	public void copyFrom(BinDescription other) {
		if (other != null) {
			this.firstKymoColMs = other.firstKymoColMs;
			this.lastKymoColMs = other.lastKymoColMs;
			this.binKymoColMs = other.binKymoColMs;
			this.binDirectory = other.binDirectory;
			this.nominalIntervalSec = other.nominalIntervalSec;
		}
	}

	public boolean isValid() {
		return binKymoColMs > 0 && lastKymoColMs > firstKymoColMs;
	}
}
