package plugins.fmp.multitools.experiment;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Shared helpers that inspect bin_xxx directory content without depending on
 * the XML schema. Used by both {@link Experiment#saveBinDescription(String)}
 * and {@link BinDirectoryResolver} so that the notions of "has measures" and
 * "is a kymograph" stay consistent across the code base.
 */
public final class BinDirectoryScanUtils {

	/** Reserved prefix used to hide directories from the plugin. */
	public static final String DELETED_PREFIX = "deleted_";

	private BinDirectoryScanUtils() {
	}

	/**
	 * Returns true if the given directory name should be ignored by any
	 * plugin-side scan (hidden / renamed-for-trash).
	 */
	public static boolean isIgnoredDirectoryName(String dirName) {
		return dirName != null && dirName.startsWith(DELETED_PREFIX);
	}

	/**
	 * Returns true if the given bin directory contains actual measure output:
	 * any XML line file, any measures CSV, or any kymograph TIFF.
	 */
	public static boolean hasMeasureContent(String binDirectoryFullPath) {
		if (binDirectoryFullPath == null)
			return false;
		File dir = new File(binDirectoryFullPath);
		if (!dir.isDirectory())
			return false;
		return hasMeasureContent(dir.toPath());
	}

	public static boolean hasMeasureContent(Path dir) {
		if (dir == null || !Files.isDirectory(dir))
			return false;
		try (Stream<Path> stream = Files.list(dir)) {
			return stream.filter(Files::isRegularFile).anyMatch(BinDirectoryScanUtils::isMeasureFile);
		} catch (IOException e) {
			return false;
		}
	}

	/**
	 * Returns true if the given bin directory contains kymograph TIFFs
	 * (heuristic used when inferring generation mode for legacy dirs).
	 */
	public static boolean hasKymographFiles(Path dir) {
		if (dir == null || !Files.isDirectory(dir))
			return false;
		try (Stream<Path> stream = Files.list(dir)) {
			return stream.filter(Files::isRegularFile).anyMatch(p -> isKymographFile(p.getFileName().toString()));
		} catch (IOException e) {
			return false;
		}
	}

	private static boolean isMeasureFile(Path path) {
		String name = path.getFileName().toString().toLowerCase();
		if (isKymographFile(name))
			return true;
		if (name.startsWith("line") && (name.endsWith(".xml") || name.endsWith(".csv")))
			return true;
		if (name.startsWith("measures") && (name.endsWith(".csv") || name.endsWith(".xml")))
			return true;
		if (name.startsWith("spotsmeasures") && (name.endsWith(".csv") || name.endsWith(".xml")))
			return true;
		return false;
	}

	private static boolean isKymographFile(String lowerName) {
		if (lowerName == null)
			return false;
		if (!(lowerName.endsWith(".tif") || lowerName.endsWith(".tiff")))
			return false;
		return lowerName.startsWith("kymo") || lowerName.startsWith("line");
	}
}
