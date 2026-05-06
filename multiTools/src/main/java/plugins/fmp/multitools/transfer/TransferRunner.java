package plugins.fmp.multitools.transfer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import icy.system.SystemUtil;
import plugins.fmp.multitools.series.ProgressReporter;

public final class TransferRunner {
	private TransferRunner() {
		throw new UnsupportedOperationException("Utility class");
	}

	public static TransferReport run(TransferPlan plan, Path otherRoot, TransferDirection direction, TransferMode mode,
			ProgressReporter progress) {
		return run(plan, otherRoot, direction, mode, false, progress);
	}

	public static TransferReport run(TransferPlan plan, Path otherRoot, TransferDirection direction, TransferMode mode,
			boolean importScanSourceResultsTrees, ProgressReporter progress) {
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
		int maxRetries = SystemUtil.isWindows() ? 6 : 2;
		long retrySleepMs = SystemUtil.isWindows() ? 250L : 80L;

		if (direction == TransferDirection.IMPORT && importScanSourceResultsTrees) {
			TransferReport r = runImportByScanningSourceTrees(plan, otherRoot, mode, maxRetries, retrySleepMs, progress);
			r.elapsed = Duration.between(t0, Instant.now());
			if (r.failed > 0) {
				progress.failed(String.format("Transfer completed with %d failures", r.failed));
			} else {
				progress.completed();
			}
			return r;
		}

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
				try {
					copySafelyWithRetries(src, dst, maxRetries, retrySleepMs);
					report.copied++;
				} catch (IOException io) {
					// Common Windows case: destination file is held by the current Java process (or another app).
					if (looksLikeLockedDestination(io)) {
						report.lockedDestination++;
						continue;
					}
					throw io;
				}
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

	private static void copySafelyWithRetries(Path src, Path dst, int maxRetries, long sleepMs) throws IOException {
		IOException last = null;
		for (int i = 0; i < Math.max(1, maxRetries); i++) {
			try {
				copySafely(src, dst);
				return;
			} catch (IOException e) {
				last = e;
				if (i + 1 >= maxRetries) {
					throw e;
				}
				try {
					Thread.sleep(Math.max(1L, sleepMs));
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					throw e;
				}
			}
		}
		if (last != null)
			throw last;
	}

	/**
	 * Write destination via temp file then move, to avoid partial writes and to reduce
	 * issues with replace-in-place semantics on Windows shares.
	 */
	private static void copySafely(Path src, Path dst) throws IOException {
		Path dir = dst.getParent();
		if (dir == null) {
			throw new IOException("Destination has no parent directory: " + dst);
		}

		String base = dst.getFileName() != null ? dst.getFileName().toString() : "file";
		Path tmp = dir.resolve(base + ".transfer_tmp");
		// Ensure we don't collide with a previous crashed run
		int n = 0;
		while (Files.exists(tmp) && n < 50) {
			n++;
			tmp = dir.resolve(base + ".transfer_tmp" + n);
		}

		try {
			Files.copy(src, tmp, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
			try {
				Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			// Best-effort cleanup if temp still exists (e.g. move failed)
			try {
				if (Files.exists(tmp)) {
					Files.delete(tmp);
				}
			} catch (IOException ignored) {
			}
		}
	}

	private static boolean looksLikeLockedDestination(IOException e) {
		if (e == null)
			return false;
		String m = e.getMessage();
		if (m == null)
			return false;
		String s = m.toLowerCase();
		return s.contains("used by another process") || s.contains("being used") || s.contains("the process cannot access")
				|| s.contains("access is denied");
	}

	private static TransferReport runImportByScanningSourceTrees(TransferPlan plan, Path otherRoot, TransferMode mode,
			int maxRetries, long retrySleepMs, ProgressReporter progress) {
		TransferReport report = new TransferReport();
		report.direction = TransferDirection.IMPORT;
		report.mode = mode;
		report.localCommonRoot = plan.localCommonRoot;
		report.otherRoot = otherRoot;

		if (plan.resultsRoots == null || plan.resultsRoots.isEmpty()) {
			return report;
		}

		int totalRoots = plan.resultsRoots.size();
		int rootIndex = 0;
		for (Path localResultsRoot : plan.resultsRoots) {
			if (progress.isCancelled())
				break;
			rootIndex++;

			Path relRoot = plan.localCommonRoot.relativize(localResultsRoot.toAbsolutePath().normalize());
			Path sourceRoot = otherRoot.resolve(relRoot);
			if (!Files.isDirectory(sourceRoot)) {
				report.missingSourceRoots++;
				continue;
			}

			progress.updateProgress(String.format("Scanning source: %s (%d/%d)", relRoot.toString(), rootIndex, totalRoots),
					rootIndex, totalRoots);

			try (java.util.stream.Stream<Path> stream = Files.walk(sourceRoot)) {
				List<Path> sourceFiles = stream.filter(Files::isRegularFile).collect(Collectors.toList());
				report.totalItems += sourceFiles.size();
				for (Path src : sourceFiles) {
					if (progress.isCancelled())
						break;
					Path relFile = sourceRoot.relativize(src);
					Path dst = localResultsRoot.resolve(relFile);
					try {
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
						try {
							copySafelyWithRetries(src, dst, maxRetries, retrySleepMs);
							report.copied++;
						} catch (IOException io) {
							if (looksLikeLockedDestination(io)) {
								report.lockedDestination++;
								continue;
							}
							throw io;
						}
					} catch (Exception e) {
						report.addFailure(src, dst, (e instanceof Exception) ? (Exception) e : new Exception(e));
					}
				}
			} catch (IOException e) {
				// Can't scan this root: report as missing source root to keep it non-fatal.
				report.missingSourceRoots++;
			}
		}
		return report;
	}
}

