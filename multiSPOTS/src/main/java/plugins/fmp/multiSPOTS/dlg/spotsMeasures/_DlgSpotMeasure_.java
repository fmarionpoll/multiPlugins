package plugins.fmp.multiSPOTS.dlg.spotsMeasures;

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
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.results.ResultsOptions;

public class _DlgSpotMeasure_ extends JPanel implements PropertyChangeListener, ChangeListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 853047648249832145L;
	public PopupPanel capPopupPanel = null;
	JTabbedPane tabsPane = new JTabbedPane();
	ThresholdLightPanel thresholdLightPanel = new ThresholdLightPanel();
//	ThresholdLightV2 tabThresholdLightV2 = new ThresholdLightV2();
//	ThresholdSimple tabSimpleThresholdAdvanced = new ThresholdSimple();
//	ThresholdColors colorsThreshold = new ThresholdColors();
	CleanGapsSpotsPanel cleanGapsSpotsPanel = new CleanGapsSpotsPanel();
	EditSpotMeasuresPanel editSpotsPanel = new EditSpotMeasuresPanel();
	ConsumptionV3Panel consumptionV3Panel = new ConsumptionV3Panel();
	ConsumptionAggV4Panel consumptionAggV4Panel = new ConsumptionAggV4Panel();
	public ChartsPanel chartsPanel = new ChartsPanel();
	public LoadSavePanel loadSavePanel = new LoadSavePanel();

	private int id_threshold = 1;
	private MultiSPOTS parent0 = null;

	public void init(JPanel mainPanel, String string, MultiSPOTS parent0) {
		this.parent0 = parent0;
		capPopupPanel = new PopupPanel(string);
		JPanel capPanel = capPopupPanel.getMainPanel();
		capPanel.setLayout(new BorderLayout());
		capPopupPanel.collapse();
		mainPanel.add(capPopupPanel);

		GridLayout gridLayout = new GridLayout(4, 1);
		int order = 0;

		thresholdLightPanel.init(gridLayout, parent0);
		thresholdLightPanel.addPropertyChangeListener(this);
		tabsPane.addTab("Threshold", null, thresholdLightPanel, "Spots measures from camera");
		id_threshold = order;
		order++;

//		tabThresholdLightV2.init(gridLayout, parent0);
//		tabThresholdLightV2.addPropertyChangeListener(this);
//		tabsPane.addTab("Threshold B2", null, tabThresholdLightV2, "Spots measures V2 (comparison channel)");
//		order++;

//		tabSimpleThresholdAdvanced.init(gridLayout, parent0);
//		tabSimpleThresholdAdvanced.addPropertyChangeListener(this);
//		tabsPane.addTab("Threshold", null, tabSimpleThresholdAdvanced,
//				"Measure area using a simple transform and threshold");
//		id_threshold = order;
//		order++;

//		colorsThreshold.init(gridLayout, parent0);
//		colorsThreshold.addPropertyChangeListener(this);
//		tabsPane.addTab("Colors threshold", null, colorsThreshold, "Measure area using colors defined by user");
//		order++;

		cleanGapsSpotsPanel.init(gridLayout, parent0);
		tabsPane.addTab("Night / clean", null, cleanGapsSpotsPanel, "Detect dark frames and clean spot measures");
		order++;

		editSpotsPanel.init(gridLayout, parent0);
		editSpotsPanel.addPropertyChangeListener(this);
		tabsPane.addTab("Edit", null, editSpotsPanel, "Edit measures");
		order++;

		consumptionV3Panel.init(gridLayout, parent0);
		consumptionV3Panel.addPropertyChangeListener(this);
		tabsPane.addTab("V3", null, consumptionV3Panel, "Consumption V3 (experiment-median residual)");
		order++;

		consumptionAggV4Panel.init(gridLayout, parent0);
		consumptionAggV4Panel.addPropertyChangeListener(this);
		tabsPane.addTab("V4 (AGG)", null, consumptionAggV4Panel, "AGG_SUMCLEAN evaluation policies");
		order++;

		chartsPanel.init(gridLayout, parent0);
		chartsPanel.addPropertyChangeListener(this);
		tabsPane.addTab("Charts", null, chartsPanel, "Display results as charts");
		order++;

		loadSavePanel.init(gridLayout, parent0);
		loadSavePanel.addPropertyChangeListener(this);
		tabsPane.addTab("Load/Save", null, loadSavePanel, "Load/Save xml file with spots descriptors");
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

	/** Copies AGG V4 policy from the V4 tab into chart/export {@link ResultsOptions}. */
	public void applyAggV4PolicyInto(ResultsOptions o) {
		consumptionAggV4Panel.applyPolicyInto(o);
	}

	@Override
	public void propertyChange(PropertyChangeEvent event) {
		if (event.getPropertyName().equals("SPOTS_ROIS_OPEN")) {
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null) {
				displaySpotsInformation(exp);
				tabsPane.setSelectedIndex(id_threshold);
				parent0.dlgExperiment.intervalsPanel.getExptParms(exp);
			}
		} else if (event.getPropertyName().equals("SPOTS_ROIS_SAVE")) {
			tabsPane.setSelectedIndex(id_threshold);
		}
	}

	public void displaySpotsInformation(Experiment exp) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				// ExperimentUtils.transferSpotsToCamDataSequence(exp); //TODO ??
				parent0.dlgExperiment.optionsPanel.viewSpotsCheckBox.setSelected(true);
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
			if (displayCapillaries) // && exp.getSpots().getSpotsList().size() < 1)
				exp.loadCamDataSpots();
			exp.getSeqCamData().displaySpecificROIs(true, "spots");
		}
	}

}
