package plugins.fmp.multitools.experiment.cage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Transient cage-level spot aggregates (e.g. AGG_SUMCLEAN by stimulus/concentration),
 * computed from per-spot measures on the native sample index. Refreshed by
 * {@link plugins.fmp.multitools.experiment.cages.Cages#prepareSpotAggregates}.
 */
public final class CageSpotAggregates {

	private List<CageSpotAggregateSeries> entries = Collections.emptyList();

	public void clear() {
		entries = Collections.emptyList();
	}

	public void setEntries(List<CageSpotAggregateSeries> list) {
		if (list == null || list.isEmpty()) {
			clear();
			return;
		}
		this.entries = new ArrayList<>(list);
	}

	public List<CageSpotAggregateSeries> getEntries() {
		return entries;
	}

	public boolean isEmpty() {
		return entries.isEmpty();
	}
}
