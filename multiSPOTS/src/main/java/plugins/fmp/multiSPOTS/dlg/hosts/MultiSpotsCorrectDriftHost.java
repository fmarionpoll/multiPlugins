package plugins.fmp.multiSPOTS.dlg.hosts;

import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multitools.experiment.ui.host.CorrectDriftHost;

/**
 * Adapter that exposes multiSPOTS to the shared
 * {@link plugins.fmp.multitools.experiment.ui.CorrectDriftPanel}. All methods
 * are inherited from {@link MultiSpotsDialogHost}; the subclass exists only
 * to declare the {@link CorrectDriftHost} marker interface and to keep room for
 * future drift-specific hooks.
 */
public class MultiSpotsCorrectDriftHost extends MultiSpotsDialogHost implements CorrectDriftHost {

	public MultiSpotsCorrectDriftHost(MultiSPOTS plugin) {
		super(plugin);
	}
}
