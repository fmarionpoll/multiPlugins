package plugins.fmp.multitools.tools.chart.builders;

import java.util.List;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.capillary.Capillary;

/**
 * Stable unique keys for capillary traces in {@link org.jfree.data.xy.XYSeriesCollection}
 * (JFreeChart requires distinct series keys). Display/semantics stay in the
 * {@code cageId_side} prefix; disambiguation is encoded after {@value #SEP}.
 */
public final class CapillaryChartSeriesKeys {

	public static final String SEP = "::";

	private CapillaryChartSeriesKeys() {
	}

	/**
	 * Unique key for a capillary measure series. Format:
	 * {@code <cageId>_<side>::k<kymographIndex>} (falls back to kymograph name).
	 */
	public static String key(Capillary cap) {
		if (cap == null) {
			return "null";
		}
		String side = cap.getCapillarySide();
		if (side == null || side.isEmpty()) {
			side = "?";
		}
		String base = cap.getCageID() + "_" + side;
		int idx = cap.getKymographIndex();
		if (idx >= 0) {
			return base + SEP + "k" + idx;
		}
		String name = cap.getKymographName();
		if (name != null && !name.isEmpty()) {
			return base + SEP + name;
		}
		return base + SEP + "u" + System.identityHashCode(cap);
	}

	/** Series key for t00 overlay curves (contains {@code *} so renderers dash them). */
	public static String keyT00(Capillary cap) {
		return key(cap) + "*00";
	}

	/** Portion before {@value #SEP}, or the whole key if no separator (legacy). */
	public static String displayBase(String seriesKey) {
		if (seriesKey == null) {
			return null;
		}
		int sep = seriesKey.lastIndexOf(SEP);
		if (sep > 0) {
			return seriesKey.substring(0, sep);
		}
		return seriesKey;
	}

	/**
	 * Side or synthetic type token from a series key ({@code L}/{@code R},
	 * {@code Sum}/{@code PI}, {@code threshold}/{@code evaporation}, ...).
	 */
	public static String sideOrTypeFromKey(String seriesKey) {
		String base = displayBase(seriesKey);
		if (base == null) {
			return null;
		}
		int u = base.lastIndexOf('_');
		if (u < 0 || u + 1 >= base.length()) {
			return base;
		}
		return base.substring(u + 1);
	}

	public static boolean isLeftSideKey(String seriesKey) {
		String side = sideOrTypeFromKey(seriesKey);
		return "L".equals(side) || "1".equals(side);
	}

	public static boolean isRightSideKey(String seriesKey) {
		String side = sideOrTypeFromKey(seriesKey);
		return "R".equals(side) || "2".equals(side);
	}

	public static boolean isAuxiliarySeriesKey(String seriesKey) {
		String side = sideOrTypeFromKey(seriesKey);
		return "threshold".equals(side) || "evaporation".equals(side) || "Sum".equals(side) || "PI".equals(side);
	}

	/**
	 * Resolves the capillary backing a cage chart series key. Prefer kymograph
	 * index / name after {@value #SEP}; fall back to side match for legacy keys.
	 */
	public static Capillary resolve(Experiment experiment, Cage cage, String seriesKey) {
		if (experiment == null || cage == null || seriesKey == null) {
			return null;
		}
		List<Capillary> capillaries = cage.getCapillaries(experiment.getCapillaries());
		if (capillaries == null || capillaries.isEmpty()) {
			return null;
		}

		String sideOrType = sideOrTypeFromKey(seriesKey);
		if (sideOrType == null || isAuxiliarySeriesKey(seriesKey)) {
			if ("Sum".equals(sideOrType) || "PI".equals(sideOrType)) {
				for (Capillary cap : capillaries) {
					String capSide = cap.getCapillarySide();
					if ("Sum".equals(sideOrType) && ("L".equals(capSide) || "1".equals(capSide))) {
						return cap;
					}
					if ("PI".equals(sideOrType) && ("R".equals(capSide) || "2".equals(capSide))) {
						return cap;
					}
				}
				return capillaries.get(0);
			}
			return null;
		}

		int sep = seriesKey.lastIndexOf(SEP);
		if (sep > 0 && sep + SEP.length() < seriesKey.length()) {
			String suffix = seriesKey.substring(sep + SEP.length());
			if (suffix.startsWith("k")) {
				try {
					int kymoIndex = Integer.parseInt(suffix.substring(1));
					for (Capillary cap : capillaries) {
						if (cap.getKymographIndex() == kymoIndex) {
							return cap;
						}
					}
				} catch (NumberFormatException ignored) {
				}
			}
			for (Capillary cap : capillaries) {
				if (suffix.equals(cap.getKymographName())) {
					return cap;
				}
			}
		}

		for (Capillary cap : capillaries) {
			String capSide = cap.getCapillarySide();
			if (sideOrType.equals(capSide)
					|| (sideOrType.equals("1") && ("L".equals(capSide) || "1".equals(capSide)))
					|| (sideOrType.equals("2") && ("R".equals(capSide) || "2".equals(capSide)))) {
				return cap;
			}
		}
		return null;
	}
}
