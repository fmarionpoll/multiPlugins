package plugins.fmp.multiSPOTS.dlg.hosts;

import icy.preferences.XMLPreferences;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multitools.experiment.ui.host.DialogHost;
import plugins.fmp.multitools.tools.DescriptorIndex;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;

/**
 * Adapts {@link MultiSPOTS} to the shared {@link DialogHost} contract
 * used by panels living in multiTools. A single instance is created per
 * plugin session and shared by all dialog hosts.
 */
public class MultiSpots96DialogHost implements DialogHost {

	private final MultiSPOTS plugin;

	public MultiSpots96DialogHost(MultiSPOTS plugin) {
		this.plugin = plugin;
	}

	protected MultiSPOTS getPlugin() {
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
