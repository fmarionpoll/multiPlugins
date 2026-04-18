package plugins.fmp.multiSPOTS96.dlg.hosts;

import icy.preferences.XMLPreferences;
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multitools.experiment.ui.host.DialogHost;
import plugins.fmp.multitools.tools.DescriptorIndex;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;

/**
 * Adapts {@link MultiSPOTS96} to the shared {@link DialogHost} contract
 * used by panels living in multiTools. A single instance is created per
 * plugin session and shared by all dialog hosts.
 */
public class MultiSpots96DialogHost implements DialogHost {

	private final MultiSPOTS96 plugin;

	public MultiSpots96DialogHost(MultiSPOTS96 plugin) {
		this.plugin = plugin;
	}

	protected MultiSPOTS96 getPlugin() {
		return plugin;
	}

	@Override
	public JComboBoxExperimentLazy getExperimentsCombo() {
		return plugin.expListComboLazy;
	}

	@Override
	public DescriptorIndex getDescriptorIndex() {
		return plugin.descriptorIndex;
	}

	@Override
	public XMLPreferences getPluginPreferences(String node) {
		return plugin.getPreferences(node);
	}
}
