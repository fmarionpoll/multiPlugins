package plugins.fmp.multitools.experiment.ui.host;

import icy.preferences.XMLPreferences;
import plugins.fmp.multitools.tools.DescriptorIndex;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;

/**
 * Base interface through which shared dialog panels access their host
 * plugin (multiCAFE or multiSPOTS96) without taking a direct compile-time
 * dependency on the plugin root class. Per-dialog subtypes extend this
 * interface and add panel-specific methods.
 */
public interface DialogHost {

	/** Shared experiment combo box managed by the plugin. */
	JComboBoxExperimentLazy getExperimentsCombo();

	/** Shared descriptor index managed by the plugin. */
	DescriptorIndex getDescriptorIndex();

	/**
	 * Returns the plugin's {@link XMLPreferences} subtree for the given
	 * node name. Mirrors {@code PluginActionable#getPreferences(String)}
	 * without exposing the Icy plugin base class.
	 */
	XMLPreferences getPluginPreferences(String node);
}
