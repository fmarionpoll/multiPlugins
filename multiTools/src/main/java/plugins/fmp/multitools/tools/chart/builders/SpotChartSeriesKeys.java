package plugins.fmp.multitools.tools.chart.builders;

import java.util.List;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.ids.SpotID;
import plugins.fmp.multitools.experiment.spot.Spot;

/**
 * Stable unique keys for spot traces in {@link org.jfree.data.xy.XYSeriesCollection}
 * (JFreeChart requires distinct series keys). The display name may repeat; disambiguation
 * is encoded after {@value #SEP}.
 */
public final class SpotChartSeriesKeys {

	public static final String SEP = "::";

	private SpotChartSeriesKeys() {
	}

	public static String key(Spot spot, int indexInCage) {
		String base = spot.getName();
		if (base == null || base.isEmpty()) {
			base = "unnamed_spot";
		}
		if (spot.getSpotUniqueID() != null) {
			return base + SEP + spot.getSpotUniqueID().getId();
		}
		return base + SEP + "s" + indexInCage;
	}

	/**
	 * Resolves the spot backing a cage chart series key. {@code cage} is required only when
	 * the key uses the {@code ::s&lt;index&gt;} fallback (no {@link SpotID}).
	 */
	public static Spot resolveSpot(Experiment experiment, Cage cage, String seriesKey) {
		if (experiment == null || experiment.getSpots() == null || experiment.getCages() == null
				|| seriesKey == null) {
			return null;
		}
		int sep = seriesKey.lastIndexOf(SEP);
		if (sep <= 0 || sep + SEP.length() >= seriesKey.length()) {
			return experiment.getCages().getSpotFromROIName(seriesKey, experiment.getSpots());
		}
		String suffix = seriesKey.substring(sep + SEP.length());
		try {
			int id = Integer.parseInt(suffix);
			Spot byId = experiment.getSpots().findSpotwithID(new SpotID(id));
			if (byId != null) {
				return byId;
			}
		} catch (NumberFormatException ignored) {
			// multi-measure overlay keys use suffix like AREA_SUM
		}
		if (suffix.startsWith("s")) {
			try {
				int idx = Integer.parseInt(suffix.substring(1));
				if (cage != null) {
					List<Spot> cageSpots = cage.getSpotList(experiment.getSpots());
					if (idx >= 0 && idx < cageSpots.size()) {
						return cageSpots.get(idx);
					}
				}
			} catch (NumberFormatException ignored) {
			}
		}
		String baseName = seriesKey.substring(0, sep);
		return experiment.getCages().getSpotFromROIName(baseName, experiment.getSpots());
	}
}
