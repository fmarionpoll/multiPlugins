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
import plugins.fmp.multitools.tools.toExcel.XLSExportMeasuresCagesAsQuery;
import plugins.fmp.multitools.tools.toExcel.XLSExportMeasuresFromSpot;
import plugins.fmp.multitools.tools.toExcel.XLSExportOptions;
import plugins.fmp.multitools.tools.toExcel.exceptions.ExcelExportException;

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
			updateExperrimentsParameters(exp);
			ThreadUtil.bgRun(new Runnable() {
				@Override
				public void run() {
					XLSExportMeasuresFromSpot xlsExport = new XLSExportMeasuresFromSpot();
					try {
						xlsExport.exportToFile(file, getSpotsOptions());
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
			updateExperrimentsParameters(exp);
			ThreadUtil.bgRun(new Runnable() {
				@Override
				public void run() {
					XLSExportMeasuresCagesAsQuery xlsExport = new XLSExportMeasuresCagesAsQuery();
					try {
						xlsExport.exportQToFile(file, getSpotsOptions());
					} catch (ExcelExportException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			});
		}
	}

	private String defineXlsFileName(Experiment exp, String pattern) {
		String filename0 = exp.seqCamData.getFileNameFromImageList(0);
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

	private void updateExperrimentsParameters(Experiment exp) {
		parent0.dlgExperiment.tabInfos.getExperimentInfosFromDialog(exp.getProperties());
	}

	private XLSExportOptions getSpotsOptions() {
		int first = 0;
		int last = parent0.expListComboLazy.getItemCount() - 1;
		if (!tabCommonOptions.exportAllFilesCheckBox.isSelected()) {
			first = parent0.expListComboLazy.getSelectedIndex();
			last = parent0.expListComboLazy.getSelectedIndex();
		}

		XLSExportOptions options = new XLSExportOptions();
		options.spotAreas = true;
		options.sum = spotsAreas.areaCheckBox.isSelected();
		options.nPixels = spotsAreas.nPixelsCheckBox.isSelected();
		options.relativeToT0 = spotsAreas.t0CheckBox.isSelected();

		options.transpose = tabCommonOptions.transposeCheckBox.isSelected();
		options.buildExcelStepMs = tabCommonOptions.getExcelBuildStep();
		options.buildExcelUnitMs = tabCommonOptions.binUnit.getMsUnitValue();
		boolean fixedIntervals = tabCommonOptions.isFixedFrameButton.isSelected();
		options.fixedIntervals = fixedIntervals;
		options.startAll_Ms = tabCommonOptions.getStartAllMs();
		options.endAll_Ms = tabCommonOptions.getEndAllMs();
		options.collateSeries = false;
		options.padIntervals = false;
		options.absoluteTime = false;
		options.onlyalive = spotsAreas.discardNoFlyCageCheckBox.isSelected();
		options.exportAllFiles = tabCommonOptions.exportAllFilesCheckBox.isSelected();
		options.expList = parent0.expListComboLazy;
		options.experimentIndexFirst = first;
		options.experimentIndexLast = last;

		return options;
	}
}
