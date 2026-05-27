package plugins.fmp.multitools.service;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cages.Cages;

/**
 * Maps a {@code kymocage_*.tif*} file path to a {@link Cage}, matching
 * {@link CageSpotKymographBuilder} naming ({@code kymocage_<cageID>} or {@code kymocage_i<listIndex>}).
 */
public final class KymocageCageResolver {

	private static final Pattern PAT_INDEX = Pattern.compile("kymocage_i(\\d+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern PAT_ID = Pattern.compile("kymocage_(\\d+)", Pattern.CASE_INSENSITIVE);

	private KymocageCageResolver() {
	}

	/**
	 * File name stem (no extension), e.g. {@code kymocage_0} or {@code kymocage_i3}, or null if the path
	 * does not look like a cage kymograph TIFF.
	 */
	public static String fileBaseFromKymographPath(String filePath) {
		if (filePath == null) {
			return null;
		}
		String base = new File(filePath).getName();
		int dot = base.lastIndexOf('.');
		if (dot > 0) {
			base = base.substring(0, dot);
		}
		if (PAT_INDEX.matcher(base).find() || PAT_ID.matcher(base).find()) {
			return base;
		}
		return null;
	}

	public static Cage resolveCageFromKymographPath(String filePath, Cages cages) {
		if (filePath == null || cages == null) {
			return null;
		}
		String base = fileBaseFromKymographPath(filePath);
		if (base == null) {
			return null;
		}
		Matcher mi = PAT_INDEX.matcher(base);
		if (mi.find()) {
			int idx = Integer.parseInt(mi.group(1));
			if (idx >= 0 && idx < cages.cagesList.size()) {
				return cages.cagesList.get(idx);
			}
			return null;
		}
		Matcher mid = PAT_ID.matcher(base);
		if (mid.find()) {
			int cageId = Integer.parseInt(mid.group(1));
			for (Cage c : cages.cagesList) {
				if (c != null && c.getProperties() != null && c.getProperties().getCageID() == cageId) {
					return c;
				}
			}
		}
		return null;
	}

	/** Same stem as {@link CageSpotKymographBuilder} output files for this cage. */
	public static String kymocageFileBaseForCage(Cage cage, Cages cages) {
		if (cage == null || cages == null || cages.cagesList == null) {
			return null;
		}
		int idx = cages.cagesList.indexOf(cage);
		if (idx < 0) {
			return null;
		}
		int cageId = cage.getProperties() != null ? cage.getProperties().getCageID() : -1;
		return "kymocage_" + (cageId >= 0 ? String.valueOf(cageId) : "i" + idx);
	}
}
