package plugins.fmp.multiSPOTS96;

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
import plugins.fmp.multiSPOTS96.dlg.browse._DlgBrowse_;
import plugins.fmp.multiSPOTS96.dlg.excel._DlgExcel_;
import plugins.fmp.multiSPOTS96.dlg.experiment._DlgExperiment_;
import plugins.fmp.multiSPOTS96.dlg.spots._DlgSpots_;
import plugins.fmp.multiSPOTS96.dlg.spotsMeasures._DlgSpotMeasure_;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.DescriptorIndex;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;

public class MultiSPOTS96 extends PluginActionable {

	public IcyFrame mainFrame = new IcyFrame("multiSPOTS96 April 30, 2026", true, true, true, true);
	public JComboBoxExperimentLazy expListComboLazy = new JComboBoxExperimentLazy();
	public DescriptorIndex descriptorIndex = new DescriptorIndex();
	public ViewOptionsHolder viewOptions = new ViewOptionsHolder();

	public _DlgBrowse_ dlgBrowse = new _DlgBrowse_();
	public _DlgExperiment_ dlgExperiment = new _DlgExperiment_();
	public _DlgSpots_ dlgSpots = new _DlgSpots_();
	public _DlgSpotMeasure_ dlgMeasure = new _DlgSpotMeasure_();
	public _DlgExcel_ dlgExcel = new _DlgExcel_();

	public JTabbedPane tabsPane = new JTabbedPane();

	// -------------------------------------------------------------------

	@Override
	public void run() {
		String revision = MultiSPOTS96.class.getPackage() != null
				? MultiSPOTS96.class.getPackage().getImplementationVersion()
				: null;
		Experiment.setProgramContext("multiSPOTS96", revision);

		viewOptions.migrateLegacyPreferencesIfNeeded(getPreferences("viewOptions"),
				getPreferences("multiSPOTS96Intervals"));
		viewOptions.load(getPreferences("viewOptions"));

		JPanel mainPanel = GuiUtil.generatePanelWithoutBorder();

		dlgBrowse.init(mainPanel, "Browse", this);
		dlgExperiment.init(mainPanel, "Experiment", this);
		dlgSpots.init(mainPanel, "Spots", this);
		dlgMeasure.init(mainPanel, "Measure spots", this);
		dlgExcel.init(mainPanel, "Export", this);

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
		PluginLauncher.start(PluginLoader.getPlugin(MultiSPOTS96.class.getName()));
	}

}
