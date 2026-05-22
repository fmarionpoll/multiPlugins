package plugins.fmp.multiSPOTS96.dlg.export;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import icy.gui.component.PopupPanel;
import icy.system.thread.ThreadUtil;
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ui.ExcelOptionsPanel;
import plugins.fmp.multitools.experiment.ui.TransferResultsHost;
import plugins.fmp.multitools.experiment.ui.TransferResultsPanel;
import plugins.fmp.multitools.tools.JComponents.Dialog;
import plugins.fmp.multitools.tools.JComponents.exceptions.FileDialogException;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.toExcel.XLSExportMeasuresFromSpot;
import plugins.fmp.multitools.tools.toExcel.XLSExportMeasuresFromSpotAggregatedByStimulusConc;
import plugins.fmp.multitools.tools.toExcel.exceptions.ExcelExportException;

public class _DlgExport_ extends JPanel implements PropertyChangeListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = -4296207607692017074L;
	public PopupPanel capPopupPanel = null;
	private JTabbedPane tabsPane = new JTabbedPane();
	public ExcelOptionsPanel excelOptionsPanel = new ExcelOptionsPanel(ExcelOptionsPanel.Features.spots96Defaults());
	private SpotsAreasPanel spotsAreasPanel = new SpotsAreasPanel();
	private AggregatedSpotsAreasPanel aggregatedSpotsAreasPanel = new AggregatedSpotsAreasPanel();
	private TransferResultsPanel transferResultsPanel = null;
	// private CagesAreas cagesAreas = new CagesAreas();
	// TODO _CAGES private Move tabMove = new Move();
	private MultiSPOTS96 parent0 = null;

	public void init(JPanel mainPanel, String string, MultiSPOTS96 parent0) {
		this.parent0 = parent0;

		capPopupPanel = new PopupPanel(string);
		JPanel capPanel = capPopupPanel.getMainPanel();
		capPanel.setLayout(new BorderLayout());
		capPopupPanel.collapse();
		mainPanel.add(capPopupPanel);
		GridLayout capLayout = new GridLayout(3, 2);

		excelOptionsPanel.init(capLayout);
		tabsPane.addTab("Common options", null, excelOptionsPanel, "Define common options");
		excelOptionsPanel.addPropertyChangeListener(this);

		spotsAreasPanel.init(capLayout);
		tabsPane.addTab("Spots", null, spotsAreasPanel, "Export measures made on spots to file");
		spotsAreasPanel.addPropertyChangeListener(this);

		aggregatedSpotsAreasPanel.init(capLayout);
		tabsPane.addTab("Aggregated spots", null, aggregatedSpotsAreasPanel,
				"Export AGG_SUMCLEAN (cage \u00d7 stimulus/concentration groups)");
		aggregatedSpotsAreasPanel.addPropertyChangeListener(this);

		transferResultsPanel = new TransferResultsPanel(parent0.expListComboLazy, new TransferResultsHost() {
			@Override
			public void closeAllExperimentsForTransfer() {
				parent0.dlgBrowse.loadSaveExperiment.closeAllExperimentsForTransfer();
			}

			@Override
			public void reloadExperimentsFromExperimentXml(java.util.List<String> experimentXmlPaths) {
				parent0.dlgBrowse.loadSaveExperiment.reloadExperimentsFromExperimentXml(experimentXmlPaths);
			}

			@Override
			public void openExperimentAtIndex(int index) {
				parent0.dlgBrowse.loadSaveExperiment.openExperimentAtIndex(index);
			}
		});
		tabsPane.addTab("Transfer results", null, transferResultsPanel, "Export/Import results to/from another location");

//		cagesAreas.init(capLayout);
//		tabsPane.addTab("Cages", null, cagesAreas, "Export measures made on cages to file");
//		cagesAreas.addPropertyChangeListener(this);

// TODO _CAGES tabMove.init(capLayout);
// TODO _CAGES tabsPane.addTab("Move", null, tabMove, "Export fly positions to file");
// TODO _CAGES tabMove.addPropertyChangeListener(this);

		capPanel.add(tabsPane);
		tabsPane.setSelectedIndex(0);

		capPopupPanel.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				parent0.mainFrame.revalidate();
				parent0.mainFrame.pack();
				parent0.mainFrame.repaint();
			}
		});
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;

		if (evt.getPropertyName().equals("EXPORT_SPOTSMEASURES")) {
			String file = defineXlsFileName(exp, "_spotsareas.xlsx");
			if (file == null)
				return;
			updateParametersCurrentExperiment(exp);
			ThreadUtil.bgRun(new Runnable() {
				@Override
				public void run() {
					try {
						Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
						ResultsOptions o = getSpotsOptions(exp);
						new XLSExportMeasuresFromSpot().exportToFile(file, o);
					} catch (ExcelExportException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			});
		} else if (evt.getPropertyName().equals("EXPORT_AGGREGATED_SPOTSMEASURES")) {
			String file = defineXlsFileName(exp, "_spots_aggregate.xlsx");
			if (file == null)
				return;
			updateParametersCurrentExperiment(exp);
			ThreadUtil.bgRun(new Runnable() {
				@Override
				public void run() {
					try {
						Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
						ResultsOptions o = getAggregatedSpotsOptions(exp);
						new XLSExportMeasuresFromSpotAggregatedByStimulusConc().exportToFile(file, o);
					} catch (ExcelExportException e) {
						e.printStackTrace();
					}
				}
			});
		}
	}

	private String defineXlsFileName(Experiment exp, String pattern) {
		String filename0 = exp.getSeqCamData().getFileNameFromImageList(0);
		Path directory = Paths.get(filename0).getParent();
		Path subpath = directory.getName(directory.getNameCount() - 1);
		String tentativeName = subpath.toString() + pattern;
		try {
			return Dialog.saveFileAs(tentativeName, directory.getParent().toString(), "xlsx");
		} catch (FileDialogException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

	private void updateParametersCurrentExperiment(Experiment exp) {
		parent0.dlgExperiment.infosPanel.getExperimentInfosFromDialog(exp);
	}

	private ResultsOptions getSpotsOptions(Experiment exp) {
		ResultsOptions resultsOptions = new ResultsOptions();

		resultsOptions.spotAreas = true;
		resultsOptions.sum = spotsAreasPanel.areaCheckBox.isSelected();
		resultsOptions.spotSumNoFly = spotsAreasPanel.sumNoFlyCheckBox.isSelected();
		resultsOptions.spotSumClean = spotsAreasPanel.sumCleanCheckBox.isSelected();
		resultsOptions.spotAreaCountV5 = spotsAreasPanel.areaCountV5CheckBox.isSelected();
		resultsOptions.spotGreySumV5 = spotsAreasPanel.greySumV5CheckBox.isSelected();
//		resultsOptions.sumV2 = spotsAreas.areaV2CheckBox.isSelected();
//		resultsOptions.spotSumNoFlyV2 = spotsAreas.sumNoFlyV2CheckBox.isSelected();
//		resultsOptions.spotSumCleanV2 = spotsAreas.sumCleanV2CheckBox.isSelected();
		resultsOptions.relativeToMaximum = spotsAreasPanel.t0CheckBox.isSelected();
		resultsOptions.onlyalive = spotsAreasPanel.discardNoFlyCageCheckBox.isSelected();
		resultsOptions.spotAggregateByStimulusConc = false;
		resultsOptions.resultType = null;
		resultsOptions.spotBaselineWindowMinutes = 2;
		resultsOptions.spotBaselineStopWhenStable = false;
		resultsOptions.spotBaselineStableBins = 3;

		getCommonOptions(resultsOptions, exp);

		return resultsOptions;
	}

	private ResultsOptions getAggregatedSpotsOptions(Experiment exp) {
		ResultsOptions resultsOptions = new ResultsOptions();
		resultsOptions.spotAreas = true;
		resultsOptions.sum = false;
		resultsOptions.spotSumNoFly = false;
		resultsOptions.spotSumClean = true;
		resultsOptions.relativeToMaximum = false;
		resultsOptions.onlyalive = aggregatedSpotsAreasPanel.discardNoFlyCageCheckBox.isSelected();
		resultsOptions.spotAggregateByStimulusConc = true;
		resultsOptions.resultType = EnumResults.AGG_SUMCLEAN;
		resultsOptions.spotBaselineWindowMinutes = ((Number) aggregatedSpotsAreasPanel.baselineMinutesSpinner.getValue())
				.intValue();
		resultsOptions.spotBaselineStopWhenStable = aggregatedSpotsAreasPanel.stopWhenStableCheckBox.isSelected();
		resultsOptions.spotBaselineStableBins = ((Number) aggregatedSpotsAreasPanel.stableBinsSpinner.getValue()).intValue();
		getCommonOptions(resultsOptions, exp);
		return resultsOptions;
	}

	private void getCommonOptions(ResultsOptions resultsOptions, Experiment exp) {
		resultsOptions.transpose = excelOptionsPanel.isTranspose();
		resultsOptions.buildExcelStepMs = excelOptionsPanel.getExcelBuildStep();
		resultsOptions.buildExcelUnitMs = excelOptionsPanel.getBinUnitMs();
		resultsOptions.fixedIntervals = excelOptionsPanel.getIsFixedFrame();
		resultsOptions.startAll_Ms = excelOptionsPanel.getStartAllMs();
		resultsOptions.endAll_Ms = excelOptionsPanel.getEndAllMs();
		resultsOptions.exportAllFiles = excelOptionsPanel.isExportAllFiles();

		resultsOptions.expList = parent0.expListComboLazy;
		resultsOptions.expList.expListBinSubDirectory = exp.getBinSubDirectory();
		if (excelOptionsPanel.isExportAllFiles()) {
			resultsOptions.firstExp = 0;
			int itemCount = resultsOptions.expList.getItemCount();
			resultsOptions.lastExp = (itemCount > 0) ? itemCount - 1 : 0;
		} else {
			int selectedIndex = parent0.expListComboLazy.getSelectedIndex();
			resultsOptions.firstExp = (selectedIndex >= 0) ? selectedIndex : 0;
			resultsOptions.lastExp = resultsOptions.firstExp;
		}
		resultsOptions.experimentIndexFirst = resultsOptions.firstExp;
		resultsOptions.experimentIndexLast = resultsOptions.lastExp;
	}
}
