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

	public static Cage resolveCageFromKymographPath(String filePath, Cages cages) {
		if (filePath == null || cages == null) {
			return null;
		}
		String base = new File(filePath).getName();
		int dot = base.lastIndexOf('.');
		if (dot > 0) {
			base = base.substring(0, dot);
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
}
