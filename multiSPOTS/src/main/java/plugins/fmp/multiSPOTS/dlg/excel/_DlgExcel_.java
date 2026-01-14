package plugins.fmp.multiSPOTS.dlg.excel;

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
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multiSPOTS.experiment.Experiment;
import plugins.fmp.multiSPOTS.tools.JComponents.Dialog;
import plugins.fmp.multiSPOTS.tools.toExcel.XLSExportOptions;
import plugins.fmp.multiSPOTS.tools.toExcel.XLSExportSpotMeasures;

public class _DlgExcel_ extends JPanel implements PropertyChangeListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = -4296207607692017074L;
	public PopupPanel capPopupPanel = null;
	private JTabbedPane tabsPane = new JTabbedPane();
	public Options tabCommonOptions = new Options();
	private SpotsAreas tabAreas = new SpotsAreas();
	// TODO _CAGES private Move tabMove = new Move();
	private MultiSPOTS parent0 = null;

	public void init(JPanel mainPanel, String string, MultiSPOTS parent0) {
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

		tabAreas.init(capLayout);
		tabsPane.addTab("Spots", null, tabAreas, "Export measures made on spots to file");
		tabAreas.addPropertyChangeListener(this);

		// TODO _CAGES tabMove.init(capLayout);
		// TODO _CAGES tabsPane.addTab("Move", null, tabMove, "Export fly positions to
		// file");
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
		Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
		if (exp == null)
			return;

		// TODO _CAGES if (evt.getPropertyName().equals("EXPORT_MOVEDATA")) {
		// TODO _CAGES String file = defineXlsFileName(exp, "_move.xlsx");
		// TODO _CAGES if (file == null)
		// TODO _CAGES return;
		// TODO _CAGES updateParametersCurrentExperiment(exp);
		// TODO _CAGES ThreadUtil.bgRun(new Runnable() {
		// TODO _CAGES @Override
		// TODO _CAGES public void run() {
		// TODO _CAGES XLSExportMoveResults xlsExport = new XLSExportMoveResults();
		// TODO _CAGES xlsExport.exportToFile(file, getMoveOptions());
		// TODO _CAGES }
		// TODO _CAGES });
		// TODO _CAGES } else

		if (evt.getPropertyName().equals("EXPORT_SPOTSMEASURES")) {
			String file = defineXlsFileName(exp, "_spotsareas.xlsx");
			if (file == null)
				return;
			updateParametersCurrentExperiment(exp);
			ThreadUtil.bgRun(new Runnable() {
				@Override
				public void run() {
					XLSExportSpotMeasures xlsExport2 = new XLSExportSpotMeasures();
					xlsExport2.exportToFile(file, getLevelsOptions());
				}
			});
		}
	}

	private String defineXlsFileName(Experiment exp, String pattern) {
		String filename0 = exp.seqCamData.getFileNameFromImageList(0);
		Path directory = Paths.get(filename0).getParent();
		Path subpath = directory.getName(directory.getNameCount() - 1);
		String tentativeName = subpath.toString() + pattern;
		return Dialog.saveFileAs(tentativeName, directory.getParent().toString(), "xlsx");
	}

	private void updateParametersCurrentExperiment(Experiment exp) {
		parent0.dlgExperiment.tabInfos.getExperimentInfosFromDialog(exp);
	}

	// TODO _CAGES private XLSExportOptions getMoveOptions() {
	// TODO _CAGES XLSExportOptions options = new XLSExportOptions();
	// TODO _CAGES options.xyImage = tabMove.xyCenterCheckBox.isSelected();
	// TODO _CAGES options.xyCage = tabMove.xyCageCheckBox.isSelected();
	// TODO _CAGES options.xyCapillaries = tabMove.xyTipCapsCheckBox.isSelected();
	// TODO _CAGES options.distance = tabMove.distanceCheckBox.isSelected();
	// TODO _CAGES options.alive = tabMove.aliveCheckBox.isSelected();
	// TODO _CAGES options.onlyalive = tabMove.deadEmptyCheckBox.isSelected();
	// TODO _CAGES options.sleep = tabMove.sleepCheckBox.isSelected();
	// TODO _CAGES options.ellipseAxes = tabMove.rectSizeCheckBox.isSelected();
	// TODO _CAGES getCommonOptions(options);
	// TODO _CAGES return options;
	// TODO _CAGES }

	private XLSExportOptions getLevelsOptions() {
		XLSExportOptions options = new XLSExportOptions();
		options.spotAreas = true;
		options.sum = tabAreas.sumCheckBox.isSelected();
		options.nPixels = tabAreas.nPixelsCheckBox.isSelected();
		options.lrPI = tabAreas.lrPICheckBox.isSelected();
		options.lrPIThreshold = (double) tabAreas.lrPIThresholdJSpinner.getValue();
		options.sumPerCage = tabAreas.sumPerCageCheckBox.isSelected();
		options.relativeToT0 = tabAreas.t0CheckBox.isSelected();
		options.subtractEvaporation = false;
		getCommonOptions(options);
		return options;
	}

	private void getCommonOptions(XLSExportOptions options) {
		options.transpose = tabCommonOptions.transposeCheckBox.isSelected();
		options.buildExcelStepMs = tabCommonOptions.getExcelBuildStep();
		options.buildExcelUnitMs = tabCommonOptions.binUnit.getMsUnitValue();
		options.fixedIntervals = tabCommonOptions.isFixedFrameButton.isSelected();
		options.startAll_Ms = tabCommonOptions.getStartAllMs();
		options.endAll_Ms = tabCommonOptions.getEndAllMs();
		options.collateSeries = tabCommonOptions.collateSeriesCheckBox.isSelected();
		options.padIntervals = tabCommonOptions.padIntervalsCheckBox.isSelected();
		options.absoluteTime = false; // tabCommonOptions.absoluteTimeCheckBox.isSelected();
		options.onlyalive = tabCommonOptions.onlyAliveCheckBox.isSelected();
		options.exportAllFiles = tabCommonOptions.exportAllFilesCheckBox.isSelected();

		options.expList = parent0.expListCombo;
		if (tabCommonOptions.exportAllFilesCheckBox.isSelected()) {
			options.experimentIndexFirst = 0;
			options.experimentIndexLast = options.expList.getItemCount() - 1;
		} else {
			options.experimentIndexFirst = parent0.expListCombo.getSelectedIndex();
			options.experimentIndexLast = parent0.expListCombo.getSelectedIndex();
		}
	}
}
