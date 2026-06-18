package plugins.fmp.multitools.tools;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Resolves {@code .tiff} / {@code .tif} pairs written to the same logical base name (Windows-safe
 * flip when one extension path stays locked).
 */
public final class TiffTifSiblingPaths {

	private TiffTifSiblingPaths() {
	}

	/**
	 * If both exist, returns the file with the newer last-modified time; otherwise whichever exists;
	 * if neither exists, returns null.
	 */
	public static File pickExistingPreferNewerMtime(File tiffFile, File tifFile) {
		if (tiffFile == null || tifFile == null) {
			return null;
		}
		boolean eTiff = tiffFile.isFile();
		boolean eTif = tifFile.isFile();
		if (eTiff && eTif) {
			return tiffFile.lastModified() >= tifFile.lastModified() ? tiffFile : tifFile;
		}
		if (eTiff) {
			return tiffFile;
		}
		if (eTif) {
			return tifFile;
		}
		return null;
	}

	/**
	 * Picks the on-disk file for {@code baseName} under {@code directoryWithSeparator} (trailing
	 * separator). When both {@code .tiff} and {@code .tif} exist, keeps the newer file and deletes
	 * the older sibling so a previous build cannot be loaded by mistake.
	 */
	public static File pickForKymographDescriptor(String directoryWithSeparator, String baseName) {
		File tiff = new File(directoryWithSeparator + baseName + ".tiff");
		File tif = new File(directoryWithSeparator + baseName + ".tif");
		File picked = pickExistingPreferNewerMtime(tiff, tif);
		if (picked == null) {
			return tiff;
		}
		if (tiff.isFile() && tif.isFile() && tiff.lastModified() != tif.lastModified()) {
			File stale = picked == tiff ? tif : tiff;
			deleteIfPresentWithRetries(stale.toPath(), 8, 80);
		}
		return picked;
	}

	/**
	 * Returns the sibling path swapping {@code .tiff} ↔ {@code .tif}, or null if the file name has
	 * neither suffix (case-insensitive).
	 */
	public static Path alternateTiffExtensionPath(Path targetPath) {
		if (targetPath == null) {
			return null;
		}
		Path parent = targetPath.getParent();
		if (parent == null) {
			return null;
		}
		String fn = targetPath.getFileName().toString();
		String lower = fn.toLowerCase(Locale.ROOT);
		if (lower.endsWith(".tiff")) {
			return parent.resolve(fn.substring(0, fn.length() - 5) + ".tif");
		}
		if (lower.endsWith(".tif")) {
			return parent.resolve(fn.substring(0, fn.length() - 4) + ".tiff");
		}
		return null;
	}

	/**
	 * After a successful kymograph save to {@code savedPath}, remove the {@code .tif}/{@code .tiff}
	 * sibling so loaders cannot pick a stale copy from a previous build (Windows lock recovery).
	 */
	public static void deleteAlternateTiffSiblingIfPresent(Path savedPath) {
		Path alt = alternateTiffExtensionPath(savedPath);
		if (alt == null) {
			return;
		}
		deleteIfPresentWithRetries(alt, 12, 120);
	}

	private static void deleteIfPresentWithRetries(Path path, int maxAttempts, long maxSleepMs) {
		if (path == null || !Files.exists(path)) {
			return;
		}
		IOException last = null;
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				Files.deleteIfExists(path);
				return;
			} catch (IOException e) {
				last = e;
				try {
					Thread.sleep(Math.min(maxSleepMs, Math.max(5L, 15L * attempt)));
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}
		if (last != null) {
			Logger.warn("TiffTifSiblingPaths: could not delete stale sibling " + path + " : " + last.getMessage());
		}
	}
}
