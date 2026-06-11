package plugins.fmp.multitools.tools;

import java.io.File;
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
	 * separator). When neither exists, returns the default {@code .tiff} path (may not exist yet).
	 */
	public static File pickForKymographDescriptor(String directoryWithSeparator, String baseName) {
		File tiff = new File(directoryWithSeparator + baseName + ".tiff");
		File tif = new File(directoryWithSeparator + baseName + ".tif");
		File picked = pickExistingPreferNewerMtime(tiff, tif);
		return picked != null ? picked : tiff;
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
}
