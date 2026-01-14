package plugins.fmp.multiSPOTS;

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
import plugins.fmp.multiSPOTS.dlg.browse._DlgBrowse_;
import plugins.fmp.multiSPOTS.dlg.excel._DlgExcel_;
import plugins.fmp.multiSPOTS.dlg.experiment._DlgExperiment_;
import plugins.fmp.multiSPOTS.dlg.kymos._DlgKymos_;
import plugins.fmp.multiSPOTS.dlg.spots._DlgSpots_;
import plugins.fmp.multiSPOTS.dlg.spotsMeasures._DlgSpotMeasure_;
import plugins.fmp.multiSPOTS.tools.JComponents.JComboBoxExperiment;

public class MultiSPOTS extends PluginActionable {
	public IcyFrame mainFrame = new IcyFrame("multiSPOTS ov 14, 2025", true, true, true, true);
	public JComboBoxExperiment expListCombo = new JComboBoxExperiment();

	public _DlgBrowse_ dlgBrowse = new _DlgBrowse_();
	public _DlgExperiment_ dlgExperiment = new _DlgExperiment_();
	public _DlgSpots_ dlgSpots = new _DlgSpots_();
	public _DlgKymos_ dlgKymos = new _DlgKymos_();
	public _DlgSpotMeasure_ dlgMeasure = new _DlgSpotMeasure_();
	// public _DlgCages_ dlgCages = new _DlgCages_(); //TODO _CAGES
	// TODO _CAGES public _DlgDetectFlies_ dlgDetectFlies = new _DlgDetectFlies_();
	public _DlgExcel_ dlgExcel = new _DlgExcel_();

	public JTabbedPane tabsPane = new JTabbedPane();

	// -------------------------------------------------------------------

	@Override
	public void run() {
		JPanel mainPanel = GuiUtil.generatePanelWithoutBorder();

		dlgBrowse.init(mainPanel, "Browse", this);
		dlgExperiment.init(mainPanel, "Experiment", this);
		dlgSpots.init(mainPanel, "Spots", this);
		dlgKymos.init(mainPanel, "Kymographs", this);
		dlgMeasure.init(mainPanel, "Measure spots", this);
		// dlgCages.init(mainPanel, "Cages", this); //TODO _CAGES
		// TODO _CAGES dlgDetectFlies.init(mainPanel, "Detect flies", this);
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
		PluginLauncher.start(PluginLoader.getPlugin(MultiSPOTS.class.getName()));
	}

}
