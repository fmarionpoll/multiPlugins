package plugins.fmp.multiSPOTS96.dlg.f_excel;

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
import plugins.fmp.multitools.tools.JComponents.Dialog;
import plugins.fmp.multitools.tools.JComponents.exceptions.FileDialogException;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.toExcel.XLSExportMeasuresFromSpot;
import plugins.fmp.multitools.tools.toExcel.exceptions.ExcelExportException;
import plugins.fmp.multitools.tools.toExcel.query.XLSExportMeasuresCagesAsQuery;

public class _DlgExcel_ extends JPanel implements PropertyChangeListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = -4296207607692017074L;
	public PopupPanel capPopupPanel = null;
	private JTabbedPane tabsPane = new JTabbedPane();
	public Options tabCommonOptions = new Options();
	private SpotsAreas spotsAreas = new SpotsAreas();
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

		tabCommonOptions.init(capLayout);
		tabsPane.addTab("Common options", null, tabCommonOptions, "Define common options");
		tabCommonOptions.addPropertyChangeListener(this);

		spotsAreas.init(capLayout);
		tabsPane.addTab("Spots", null, spotsAreas, "Export measures made on spots to file");
		spotsAreas.addPropertyChangeListener(this);

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
					XLSExportMeasuresFromSpot xlsExport = new XLSExportMeasuresFromSpot();
					try {
						Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
						xlsExport.exportToFile(file, getSpotsOptions(exp));
					} catch (ExcelExportException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			});

		} else if (evt.getPropertyName().equals("EXPORT_SPOTSMEASURES_AS_Q")) {
			String file = defineXlsFileName(exp, "_asQ.xlsx");
			if (file == null)
				return;
			updateParametersCurrentExperiment(exp);
			ThreadUtil.bgRun(new Runnable() {
				@Override
				public void run() {
					XLSExportMeasuresCagesAsQuery xlsExport = new XLSExportMeasuresCagesAsQuery();
					try {
						Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
						xlsExport.exportQToFile(file, getSpotsOptions(exp));
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
			return null;
		}
	}

	private void updateParametersCurrentExperiment(Experiment exp) {
		parent0.dlgExperiment.tabInfos.getExperimentInfosFromDialog(exp);
	}

	private ResultsOptions getSpotsOptions(Experiment exp) {
		ResultsOptions resultsOptions = new ResultsOptions();

		resultsOptions.spotAreas = true;
		resultsOptions.sum = spotsAreas.areaCheckBox.isSelected();
		resultsOptions.nPixels = spotsAreas.nPixelsCheckBox.isSelected();
		resultsOptions.relativeToMaximum = spotsAreas.t0CheckBox.isSelected();
		resultsOptions.onlyalive = spotsAreas.discardNoFlyCageCheckBox.isSelected();

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
