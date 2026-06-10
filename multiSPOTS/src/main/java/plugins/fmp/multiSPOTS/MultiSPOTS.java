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
import icy.preferences.XMLPreferences;
import plugins.fmp.multiSPOTS.dlg.browse._DlgBrowse_;
import plugins.fmp.multiSPOTS.dlg.experiment._DlgExperiment_;
import plugins.fmp.multiSPOTS.dlg.export._DlgExport_;
import plugins.fmp.multiSPOTS.dlg.kymograph._DlgKymos_;
import plugins.fmp.multiSPOTS.dlg.spots._DlgSpots_;
import plugins.fmp.multiSPOTS.dlg.spotsMeasures._DlgSpotMeasure_;
import plugins.fmp.multiSPOTS.dlg.spotsMeasures2._DlgSpotMeasure2_;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ui.FrameGeometryPreferences;
import plugins.fmp.multitools.series.CageKymographViewerUtil;
import plugins.fmp.multitools.tools.DescriptorIndex;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;

public class MultiSPOTS extends PluginActionable {

	public IcyFrame mainFrame = new IcyFrame("multiSPOTS June 10, 2026", true, true, true, true);
	public JComboBoxExperimentLazy expListComboLazy = new JComboBoxExperimentLazy();
	public DescriptorIndex descriptorIndex = new DescriptorIndex();
	public ViewOptionsHolder viewOptions = new ViewOptionsHolder();

	private volatile boolean suppressExperimentOpenOnComboProgrammaticChange = false;

	public boolean isSuppressExperimentOpenOnComboProgrammaticChange() {
		return suppressExperimentOpenOnComboProgrammaticChange;
	}

	public void setSuppressExperimentOpenOnComboProgrammaticChange(boolean suppress) {
		this.suppressExperimentOpenOnComboProgrammaticChange = suppress;
	}

	public _DlgBrowse_ dlgBrowse = new _DlgBrowse_();
	public _DlgExperiment_ dlgExperiment = new _DlgExperiment_();
	public _DlgSpots_ dlgSpots = new _DlgSpots_();
	public _DlgSpotMeasure_ dlgMeasure = new _DlgSpotMeasure_();
	public _DlgSpotMeasure2_ dlgMeasureV5 = new _DlgSpotMeasure2_();
	public _DlgKymos_ dlgKymos = new _DlgKymos_();
	public _DlgExport_ dlgExcel = new _DlgExport_();

	public JTabbedPane tabsPane = new JTabbedPane();

	/**
	 * Opens the cage kymograph viewer with persisted position/size (gui
	 * preferences).
	 */
	public void openCageKymographViewer(Experiment exp) {
		CageKymographViewerUtil.openIfPresent(exp, getPreferences("gui"), mainFrame);
	}

	// -------------------------------------------------------------------

	@Override
	public void run() {
		String revision = MultiSPOTS.class.getPackage() != null
				? MultiSPOTS.class.getPackage().getImplementationVersion()
				: null;
		Experiment.setProgramContext("multiSPOTS", revision);

		viewOptions.migrateLegacyPreferencesIfNeeded(getPreferences("viewOptions"),
				getPreferences("multiSPOTSIntervals"));
		viewOptions.load(getPreferences("viewOptions"));

		JPanel mainPanel = GuiUtil.generatePanelWithoutBorder();

		dlgBrowse.init(mainPanel, "Browse", this);
		dlgExperiment.init(mainPanel, "Experiment", this);
		dlgSpots.init(mainPanel, "Spots", this);
		dlgMeasure.init(mainPanel, "Measure spots", this);
		dlgMeasureV5.init(mainPanel, "Measure spots - experimental", this);
		dlgKymos.init(mainPanel, "Kymographs", this);
		dlgExcel.init(mainPanel, "Export", this);

		mainFrame.setLayout(new BorderLayout());
		mainFrame.add(mainPanel, BorderLayout.WEST);

		mainFrame.pack();
		XMLPreferences guiFramePrefs = getPreferences("gui");
		if (!FrameGeometryPreferences.restore(mainFrame, guiFramePrefs, "pluginWindow.", 420, 280)) {
			mainFrame.center();
		}
		FrameGeometryPreferences.installAutoSave(mainFrame, guiFramePrefs, "pluginWindow.");
		mainFrame.setVisible(true);
		mainFrame.addToDesktopPane();
	}

	public static void main(String[] args) {
		Icy.main(args);
		GeneralPreferences.setSequencePersistence(false);
		PluginLauncher.start(PluginLoader.getPlugin(MultiSPOTS.class.getName()));
	}

}
