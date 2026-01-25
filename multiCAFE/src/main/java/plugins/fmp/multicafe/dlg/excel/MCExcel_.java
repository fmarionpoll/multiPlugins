package plugins.fmp.multicafe.dlg.excel;

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
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.JComponents.Dialog;
import plugins.fmp.multitools.tools.JComponents.exceptions.FileDialogException;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.toExcel.XLSExportMeasuresFromCapillary;
import plugins.fmp.multitools.tools.toExcel.XLSExportMeasuresFromFlyPosition;
import plugins.fmp.multitools.tools.toExcel.XLSExportMeasuresFromGulp;
import plugins.fmp.multitools.tools.toExcel.exceptions.ExcelExportException;

public class MCExcel_ extends JPanel implements PropertyChangeListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = -4296207607692017074L;
	PopupPanel capPopupPanel = null;
	private JTabbedPane tabsPane = new JTabbedPane();
	public Options tabCommonOptions = new Options();
	private Levels tabLevels = new Levels();
	private Gulps tabGulps = new Gulps();
	private Move tabMove = new Move();
	private MultiCAFE parent0 = null;

	public void init(JPanel mainPanel, String string, MultiCAFE parent0) {
		this.parent0 = parent0;

		capPopupPanel = new PopupPanel(string);
		JPanel capPanel = capPopupPanel.getMainPanel();
		capPanel.setLayout(new BorderLayout());
		capPopupPanel.collapse();
		mainPanel.add(capPopupPanel);
		GridLayout capLayout = new GridLayout(3, 2);

		tabCommonOptions.init(capLayout);
		tabsPane.addTab("Common options", null, tabCommonOptions, "Define common options");
		tabCommonOptions.addPropertyChangeListener(this);

		tabLevels.init(capLayout);
		tabsPane.addTab("Levels", null, tabLevels, "Export capillary levels to file");
		tabLevels.addPropertyChangeListener(this);

		tabGulps.init(capLayout);
		tabsPane.addTab("Gulps", null, tabGulps, "Export gulps to file");
		tabGulps.addPropertyChangeListener(this);

		tabMove.init(capLayout);
		tabsPane.addTab("Fly positions", null, tabMove, "Export fly positions to file");
		tabMove.addPropertyChangeListener(this);

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

		if (evt.getPropertyName().equals("EXPORT_MOVEDATA")) {
			String file = defineXlsFileName(exp, "_move.xlsx");
			if (file == null)
				return;
			updateParametersCurrentExperiment(exp);
			ThreadUtil.bgRun(new Runnable() {
				@Override
				public void run() {
					XLSExportMeasuresFromFlyPosition xlsExport = new XLSExportMeasuresFromFlyPosition();
					try {
						xlsExport.exportToFile(file, getMoveOptions(exp));
					} catch (ExcelExportException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			});
		} else if (evt.getPropertyName().equals("EXPORT_KYMOSDATA")) {
			String file = defineXlsFileName(exp, "_feeding.xlsx");
			if (file == null)
				return;
			updateParametersCurrentExperiment(exp);
			ThreadUtil.bgRun(new Runnable() {
				@Override
				public void run() {
					XLSExportMeasuresFromCapillary xlsExport2 = new XLSExportMeasuresFromCapillary();
					try {
						xlsExport2.exportToFile(file, getLevelsOptions(exp));
					} catch (ExcelExportException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			});
		} else if (evt.getPropertyName().equals("EXPORT_GULPSDATA")) {
			String file = defineXlsFileName(exp, "_gulps.xlsx");
			if (file == null)
				return;
			updateParametersCurrentExperiment(exp);
			ThreadUtil.bgRun(new Runnable() {
				@Override
				public void run() {
					XLSExportMeasuresFromGulp xlsExport2 = new XLSExportMeasuresFromGulp();
					try {
						xlsExport2.exportToFile(file, getGulpsOptions(exp));
					} catch (ExcelExportException e) {
						// TODO Auto-generated catch block
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
		}
		return tentativeName;
	}

	private void updateParametersCurrentExperiment(Experiment exp) {
		parent0.paneCapillaries.getDialogCapillariesInfos(exp);
		parent0.paneExperiment.tabInfos.getExperimentInfosFromDialog(exp);
	}

	private ResultsOptions getMoveOptions(Experiment exp) {
		ResultsOptions options = new ResultsOptions();
		options.xyImage = tabMove.xyCenterCheckBox.isSelected();
		options.xyCage = tabMove.xyCageCheckBox.isSelected();
		options.xyCapillaries = tabMove.xyTipCapsCheckBox.isSelected();
		options.distance = tabMove.distanceCheckBox.isSelected();
		options.alive = tabMove.aliveCheckBox.isSelected();
		options.onlyalive = tabMove.deadEmptyCheckBox.isSelected();
		options.sleep = tabMove.sleepCheckBox.isSelected();
		options.ellipseAxes = tabMove.rectSizeCheckBox.isSelected();
		getCommonOptions(options, exp);
		return options;
	}

	private ResultsOptions getLevelsOptions(Experiment exp) {
		ResultsOptions resultsOptions = new ResultsOptions();
		resultsOptions.sumGulps = false;
		resultsOptions.nbGulps = false;
		resultsOptions.topLevel = tabLevels.topLevelCheckBox.isSelected();
		resultsOptions.bottomLevel = tabLevels.bottomLevelCheckBox.isSelected();
		resultsOptions.sumGulps = false;
		resultsOptions.lrPI = tabLevels.lrPICheckBox.isSelected();
		resultsOptions.lrPIThreshold = (double) tabLevels.lrPIThresholdJSpinner.getValue();
		resultsOptions.sumPerCage = tabLevels.sumPerCageCheckBox.isSelected();
		resultsOptions.correctEvaporation = tabLevels.subtractEvaporationCheckBox.isSelected();
		getCommonOptions(resultsOptions, exp);
		return resultsOptions;
	}

	private ResultsOptions getGulpsOptions(Experiment exp) {
		ResultsOptions resultsOptions = new ResultsOptions();
		resultsOptions.topLevel = false;
		resultsOptions.topLevelDelta = false;
		resultsOptions.bottomLevel = false;
		resultsOptions.derivative = tabGulps.derivativeCheckBox.isSelected();
		resultsOptions.sumPerCage = false;

		resultsOptions.sumGulps = tabGulps.sumGulpsCheckBox.isSelected();
		resultsOptions.lrPI = tabGulps.sumCheckBox.isSelected();
		resultsOptions.nbGulps = tabGulps.nbGulpsCheckBox.isSelected();
		resultsOptions.amplitudeGulps = tabGulps.amplitudeGulpsCheckBox.isSelected();

		resultsOptions.markovChain = tabGulps.markovChainCheckBox.isSelected();
//		resultsOptions.autocorrelation = tabGulps.autocorrelationCheckBox.isSelected();
//		resultsOptions.crosscorrelation = tabGulps.crosscorrelationCheckBox.isSelected();
//		resultsOptions.nBinsCorrelation = (int) tabGulps.nbinsJSpinner.getValue();

		resultsOptions.correctEvaporation = false;
		getCommonOptions(resultsOptions, exp);
		return resultsOptions;
	}

	private void getCommonOptions(ResultsOptions resultsOptions, Experiment exp) {
		resultsOptions.transpose = tabCommonOptions.transposeCheckBox.isSelected();
		resultsOptions.buildExcelStepMs = tabCommonOptions.getExcelBuildStep();
		resultsOptions.buildExcelUnitMs = tabCommonOptions.binUnit.getMsUnitValue();
		resultsOptions.fixedIntervals = tabCommonOptions.isFixedFrameButton.isSelected();
		resultsOptions.startAll_Ms = tabCommonOptions.getStartAllMs();
		resultsOptions.endAll_Ms = tabCommonOptions.getEndAllMs();

		resultsOptions.collateSeries = tabCommonOptions.collateSeriesCheckBox.isSelected();
		resultsOptions.padIntervals = tabCommonOptions.padIntervalsCheckBox.isSelected();
		resultsOptions.absoluteTime = false; // tabCommonOptions.absoluteTimeCheckBox.isSelected();
		resultsOptions.onlyalive = tabCommonOptions.onlyAliveCheckBox.isSelected();
		resultsOptions.exportAllFiles = tabCommonOptions.exportAllFilesCheckBox.isSelected();

		resultsOptions.expList = parent0.expListComboLazy;
		resultsOptions.expList.expListBinSubDirectory = exp.getBinSubDirectory();
		if (tabCommonOptions.exportAllFilesCheckBox.isSelected()) {
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
