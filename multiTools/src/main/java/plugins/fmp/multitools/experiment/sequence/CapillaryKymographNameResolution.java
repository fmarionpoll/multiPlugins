package plugins.fmp.multitools.experiment.sequence;

import java.io.File;
import java.util.List;

import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillary.Capillary;

/**
 * Resolves a {@link Capillary} from a kymograph frame file path and naming conventions (L/R vs 1/2,
 * {@code line…} prefixes). Extracted from {@link SequenceKymos#getCapillaryForFrame} for reuse
 * and to separate filename logic from ROI polyline editing.
 */
public final class CapillaryKymographNameResolution {

	private CapillaryKymographNameResolution() {
	}

	/**
	 * @param path absolute or relative path for frame {@code t}, or null if unknown
	 * @param t    frame index (used for list-position fallbacks)
	 */
	public static Capillary resolve(String path, int t, Capillaries capillaries) {
		if (capillaries == null || t < 0) {
			return null;
		}
		if (path != null) {
			String baseName = new File(path).getName();
			int lastDot = baseName.lastIndexOf('.');
			if (lastDot > 0) {
				baseName = baseName.substring(0, lastDot);
			}
			Capillary cap = capillaries.getCapillaryFromKymographName(baseName);
			if (cap != null) {
				return cap;
			}
			String displayName = baseName.replaceAll("1$", "L").replaceAll("2$", "R");
			cap = capillaries.getCapillaryFromKymographName(displayName);
			if (cap != null) {
				return cap;
			}
			String numericName = Capillary.replace_LR_with_12(baseName);
			if (!numericName.equals(baseName)) {
				cap = capillaries.getCapillaryFromKymographName(numericName);
				if (cap != null) {
					return cap;
				}
			}
			String prefix = prefixFromKymographBaseName(baseName);
			if (prefix != null) {
				cap = capillaries.getCapillaryFromRoiNamePrefix(prefix);
				if (cap != null) {
					return cap;
				}
			}
		}
		List<Capillary> list = capillaries.getList();
		if (t < list.size()) {
			return list.get(t);
		}
		return capillaries.getCapillaryAtT(t);
	}

	private static String prefixFromKymographBaseName(String baseName) {
		if (baseName == null || baseName.length() < 6 || !baseName.startsWith("line")) {
			return null;
		}
		String number = baseName.substring(4, baseName.length() - 1);
		String last = baseName.substring(baseName.length() - 1);
		if ("1".equals(last)) {
			return number + "L";
		}
		if ("2".equals(last)) {
			return number + "R";
		}
		return baseName.substring(baseName.length() - 2);
	}
}
