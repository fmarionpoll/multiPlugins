package plugins.fmp.multitools.transfer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

import plugins.fmp.multitools.series.ProgressReporter;

public final class TransferRunner {
	private TransferRunner() {
		throw new UnsupportedOperationException("Utility class");
	}

	public static TransferReport run(TransferPlan plan, Path otherRoot, TransferDirection direction, TransferMode mode,
			ProgressReporter progress) {
		if (progress == null)
			progress = ProgressReporter.NO_OP;

		TransferReport report = new TransferReport();
		report.direction = direction;
		report.mode = mode;
		report.localCommonRoot = (plan != null) ? plan.localCommonRoot : null;
		report.otherRoot = otherRoot;
		report.totalItems = (plan != null && plan.items != null) ? plan.items.size() : 0;

		if (plan == null || plan.localCommonRoot == null || otherRoot == null || plan.items == null) {
			report.elapsed = Duration.ZERO;
			return report;
		}

		Instant t0 = Instant.now();
		int total = plan.items.size();
		for (int i = 0; i < total; i++) {
			if (progress.isCancelled())
				break;

			TransferItem it = plan.items.get(i);
			Path src;
			Path dst;
			if (direction == TransferDirection.EXPORT) {
				src = it.src;
				dst = otherRoot.resolve(it.rel);
			} else {
				src = otherRoot.resolve(it.rel);
				dst = plan.localCommonRoot.resolve(it.rel);
			}

			progress.updateProgress(String.format("%s (%d/%d)", it.rel.toString(), i + 1, total), i + 1, total);

			// Import: missing on source is not an error; report it separately.
			if (direction == TransferDirection.IMPORT && !Files.exists(src)) {
				report.missingSource++;
				continue;
			}

			try {
				if (!Files.exists(src)) {
					throw new IOException("Source file missing: " + src);
				}

				Decision decision = decideCopy(src, dst, mode);
				if (decision == Decision.SKIP_EXISTING) {
					report.skippedExisting++;
					continue;
				}
				if (decision == Decision.SKIP_NOT_NEWER) {
					report.skippedNotNewer++;
					continue;
				}

				Files.createDirectories(dst.getParent());
				Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
				report.copied++;
			} catch (Exception e) {
				report.addFailure(src, dst, (e instanceof Exception) ? (Exception) e : new Exception(e));
			}
		}

		report.elapsed = Duration.between(t0, Instant.now());
		if (report.failed > 0) {
			progress.failed(String.format("Transfer completed with %d failures", report.failed));
		} else {
			progress.completed();
		}
		return report;
	}

	private enum Decision {
		COPY,
		SKIP_EXISTING,
		SKIP_NOT_NEWER
	}

	private static Decision decideCopy(Path src, Path dst, TransferMode mode) throws IOException {
		if (mode == TransferMode.OVERWRITE) {
			return Decision.COPY;
		}

		if (mode == TransferMode.IF_MISSING) {
			return Files.exists(dst) ? Decision.SKIP_EXISTING : Decision.COPY;
		}

		// IF_NEWER
		if (!Files.exists(dst)) {
			return Decision.COPY;
		}

		FileTime srcTime = Files.getLastModifiedTime(src);
		FileTime dstTime = Files.getLastModifiedTime(dst);

		int cmp = srcTime.compareTo(dstTime);
		if (cmp > 0) {
			return Decision.COPY;
		}
		if (cmp < 0) {
			return Decision.SKIP_NOT_NEWER;
		}
		// Same mtime: compare size as a cheap tie-breaker
		long srcSize = Files.size(src);
		long dstSize = Files.size(dst);
		return (srcSize != dstSize) ? Decision.COPY : Decision.SKIP_NOT_NEWER;
	}
}

