package plugins.fmp.multitools.transfer;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TransferReport {
	public static final class Failure {
		public final Path src;
		public final Path dst;
		public final String message;

		public Failure(Path src, Path dst, String message) {
			this.src = src;
			this.dst = dst;
			this.message = message;
		}
	}

	public TransferDirection direction;
	public TransferMode mode;
	public Path localCommonRoot;
	public Path otherRoot;

	public int totalItems = 0;
	public int copied = 0;
	public int skippedExisting = 0;
	public int skippedNotNewer = 0;
	public int missingSource = 0; // import only
	public int failed = 0;

	public Duration elapsed = Duration.ZERO;
	private final List<Failure> failures = new ArrayList<>();

	public void addFailure(Path src, Path dst, Exception e) {
		failed++;
		String msg = (e != null && e.getMessage() != null) ? e.getMessage() : String.valueOf(e);
		failures.add(new Failure(src, dst, msg));
	}

	public List<Failure> getFailures() {
		return Collections.unmodifiableList(failures);
	}
}

