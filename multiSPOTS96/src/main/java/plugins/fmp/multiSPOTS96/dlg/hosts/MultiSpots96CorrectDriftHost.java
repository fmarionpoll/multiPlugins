package plugins.fmp.multiSPOTS96.dlg.hosts;

import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multitools.experiment.ui.host.CorrectDriftHost;

/**
 * Adapter that exposes multiSPOTS96 to the shared
 * {@link plugins.fmp.multitools.experiment.ui.CorrectDriftPanel}. All
 * methods are inherited from {@link MultiSpots96DialogHost}; the subclass
 * exists only to declare the {@link CorrectDriftHost} marker interface
 * and to keep room for future drift-specific hooks.
 */
public class MultiSpots96CorrectDriftHost extends MultiSpots96DialogHost implements CorrectDriftHost {

	public MultiSpots96CorrectDriftHost(MultiSPOTS96 plugin) {
		super(plugin);
	}
}
