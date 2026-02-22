package plugins.fmp.multicafe;

import java.awt.BorderLayout;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import icy.gui.frame.IcyFrame;
import icy.gui.util.GuiUtil;
import icy.main.Icy;
import icy.plugin.PluginLauncher;
import icy.plugin.PluginLoader;
import icy.plugin.abstract_.PluginActionable;
import icy.preferences.GeneralPreferences;
import plugins.fmp.multicafe.dlg.browse.MCBrowse_;
import plugins.fmp.multicafe.dlg.cages.MCCages_;
import plugins.fmp.multicafe.dlg.capillaries.MCCapillaries_;
import plugins.fmp.multicafe.dlg.excel.MCExcel_;
import plugins.fmp.multicafe.dlg.experiment.MCExperiment_;
import plugins.fmp.multicafe.dlg.kymos.MCKymos_;
import plugins.fmp.multicafe.dlg.levels.MCLevels_;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.DescriptorIndex;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;

public class MultiCAFE extends PluginActionable {
	public IcyFrame mainFrame = new IcyFrame("MultiCAFE February 22, 2026", true, true, true, true);

	public JComboBoxExperimentLazy expListComboLazy = new JComboBoxExperimentLazy();
	public DescriptorIndex descriptorIndex = new DescriptorIndex();
	public ViewOptionsHolder viewOptions = new ViewOptionsHolder();

	public MCBrowse_ paneBrowse = new MCBrowse_();
	public MCExperiment_ paneExperiment = new MCExperiment_();
	public MCCapillaries_ paneCapillaries = new MCCapillaries_();
	public MCKymos_ paneKymos = new MCKymos_();
	public MCLevels_ paneLevels = new MCLevels_();
	public MCCages_ paneCages = new MCCages_();
	public MCExcel_ paneExcel = new MCExcel_();

	public JTabbedPane tabsPane = new JTabbedPane();

	// -------------------------------------------------------------------

	@Override
	public void run() {
		// Set the program context so it can be saved in MCExperiment.xml
		Experiment.setProgramContext("multiCAFE");

		viewOptions.load(getPreferences("viewOptions"));

		JPanel mainPanel = GuiUtil.generatePanelWithoutBorder();
		paneBrowse.init(mainPanel, "Browse", this);
		paneExperiment.init(mainPanel, "Experiment", this);
		paneCapillaries.init(mainPanel, "Capillaries", this);
		paneKymos.init(mainPanel, "Kymographs", this);
		paneLevels.init(mainPanel, "Capillary levels", this);
		paneCages.init(mainPanel, "Fly positions", this);
		paneExcel.init(mainPanel, "Export", this);

		mainFrame.setLayout(new BorderLayout());
		mainFrame.add(mainPanel, BorderLayout.WEST);
		mainFrame.pack();
		mainFrame.center();
		mainFrame.setVisible(true);
		mainFrame.addToDesktopPane();
	}

	public static void main(String[] args) {
		Icy.main(args);
		GeneralPreferences.setSequencePersistence(false);
		PluginLauncher.start(PluginLoader.getPlugin(MultiCAFE.class.getName()));
	}

}
