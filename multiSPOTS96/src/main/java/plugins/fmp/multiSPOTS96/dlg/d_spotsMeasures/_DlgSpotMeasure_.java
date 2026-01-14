package plugins.fmp.multiSPOTS96.dlg.d_spotsMeasures;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import icy.gui.component.PopupPanel;
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multiSPOTS96.experiment.Experiment;

public class _DlgSpotMeasure_ extends JPanel implements PropertyChangeListener, ChangeListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 853047648249832145L;
	public PopupPanel capPopupPanel = null;
	JTabbedPane tabsPane = new JTabbedPane();
//	ThresholdSimple tabSimpleThreshold = new ThresholdSimple();
	ThresholdSimple tabSimpleThresholdAdvanced = new ThresholdSimple();
//	ThresholdColors colorsThreshold = new ThresholdColors();
	Edit tabEdit = new Edit();
	public Charts tabCharts = new Charts();
	public LoadSave tabFile = new LoadSave();

	private int id_threshold = 1;
	private MultiSPOTS96 parent0 = null;

	public void init(JPanel mainPanel, String string, MultiSPOTS96 parent0) {
		this.parent0 = parent0;
		capPopupPanel = new PopupPanel(string);
		JPanel capPanel = capPopupPanel.getMainPanel();
		capPanel.setLayout(new BorderLayout());
		capPopupPanel.collapse();
		mainPanel.add(capPopupPanel);

		GridLayout gridLayout = new GridLayout(3, 1);
		int order = 0;

//		tabSimpleThreshold.init(gridLayout, parent0);
//		tabSimpleThreshold.addPropertyChangeListener(this);
//		tabsPane.addTab("Simple threshold", null, tabSimpleThreshold,
//				"Measure area using a simple transform and threshold");
//		id_threshold = order;
//		order++;

		tabSimpleThresholdAdvanced.init(gridLayout, parent0);
		tabSimpleThresholdAdvanced.addPropertyChangeListener(this);
		tabsPane.addTab("Threshold", null, tabSimpleThresholdAdvanced,
				"Measure area using a simple transform and threshold");
		id_threshold = order;
		order++;

//		colorsThreshold.init(gridLayout, parent0);
//		colorsThreshold.addPropertyChangeListener(this);
//		tabsPane.addTab("Colors threshold", null, colorsThreshold, "Measure area using colors defined by user");
//		order++;

		tabEdit.init(gridLayout, parent0);
		tabEdit.addPropertyChangeListener(this);
		tabsPane.addTab("Edit", null, tabEdit, "Edit measures (move/cut/extrapolate)");
		order++;

		tabCharts.init(gridLayout, parent0);
		tabCharts.addPropertyChangeListener(this);
		tabsPane.addTab("Charts", null, tabCharts, "Display results as charts");
		order++;

		tabFile.init(gridLayout, parent0);
		tabFile.addPropertyChangeListener(this);
		tabsPane.addTab("Load/Save", null, tabFile, "Load/Save xml file with spots descriptors");
		order++;

		tabsPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
		capPanel.add(tabsPane);
		tabsPane.addChangeListener(this);

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
	public void propertyChange(PropertyChangeEvent event) {
		if (event.getPropertyName().equals("SPOTS_ROIS_OPEN")) {
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null) {
				displaySpotsInformation(exp);
				tabsPane.setSelectedIndex(id_threshold);
				parent0.dlgExperiment.tabIntervals.getExptParms(exp);
			}
		} else if (event.getPropertyName().equals("SPOTS_ROIS_SAVE")) {
			tabsPane.setSelectedIndex(id_threshold);
		}
	}

	public void displaySpotsInformation(Experiment exp) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				// ExperimentUtils.transferSpotsToCamDataSequence(exp); //TODO ??
				parent0.dlgExperiment.tabOptions.viewSpotsCheckBox.setSelected(true);
			}
		});
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		JTabbedPane tabbedPane = (JTabbedPane) e.getSource();
		int selectedIndex = tabbedPane.getSelectedIndex();
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null) {
			boolean displayCapillaries = (selectedIndex == id_threshold);
			if (displayCapillaries) // && exp.spotsArray.getSpotsList().size() < 1)
				exp.loadCamDataSpots();
			exp.seqCamData.displaySpecificROIs(true, "spots");
		}
	}

}
