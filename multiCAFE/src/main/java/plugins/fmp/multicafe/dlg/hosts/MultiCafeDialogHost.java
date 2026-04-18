package plugins.fmp.multicafe.dlg.hosts;

import icy.preferences.XMLPreferences;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.ui.host.DialogHost;
import plugins.fmp.multitools.tools.DescriptorIndex;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;

/**
 * Adapts {@link MultiCAFE} to the shared {@link DialogHost} contract used
 * by panels living in multiTools. A single instance is created per plugin
 * session and shared by all dialog hosts.
 */
public class MultiCafeDialogHost implements DialogHost {

	private final MultiCAFE plugin;

	public MultiCafeDialogHost(MultiCAFE plugin) {
		this.plugin = plugin;
	}

	protected MultiCAFE getPlugin() {
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
