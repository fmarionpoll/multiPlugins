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
	CleanGapsSpotsPanel cleanGapsSpotsPanel = new CleanGapsSpotsPanel();
	EditSpotMeasuresPanel editSpotsPanel = new EditSpotMeasuresPanel();
	public ChartsPanel chartsPanel = new ChartsPanel();
	public LoadSavePanel loadSavePanel = new LoadSavePanel();

	ConsumptionV3Panel consumptionV3Panel = new ConsumptionV3Panel();
	ConsumptionAggV4Panel consumptionAggV4Panel = new ConsumptionAggV4Panel();
	public ThresholdV5Panel thresholdV5Panel = new ThresholdV5Panel();
	public ChartsV5Panel chartsV5Panel = new ChartsV5Panel();

	private int id_threshold = 1;
	private int idThresholdTabV5 = 0;
	private int idKymographsTab = -1;
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

		cleanGapsSpotsPanel.init(gridLayout, parent0);
		tabsPane.addTab("Night / clean", null, cleanGapsSpotsPanel, "Detect dark frames and clean spot measures");
		order++;

		editSpotsPanel.init(gridLayout, parent0);
		editSpotsPanel.addPropertyChangeListener(this);
		tabsPane.addTab("Edit", null, editSpotsPanel, "Edit measures");
		order++;

		chartsPanel.init(gridLayout, parent0);
		chartsPanel.addPropertyChangeListener(this);
		tabsPane.addTab("Charts", null, chartsPanel, "Display results as charts");
		order++;

		loadSavePanel.init(gridLayout, parent0);
		loadSavePanel.addPropertyChangeListener(this);
		tabsPane.addTab("Load/Save", null, loadSavePanel, "Load/Save xml file with spots descriptors");
		order++;

//		consumptionV3Panel.init(gridLayout, parent0);
//		consumptionV3Panel.addPropertyChangeListener(this);
//		tabsPane.addTab("V3", null, consumptionV3Panel, "Consumption V3 (experiment-median residual)");
//		order++;
//
//		consumptionAggV4Panel.init(gridLayout, parent0);
//		consumptionAggV4Panel.addPropertyChangeListener(this);
//		tabsPane.addTab("V4 (AGG)", null, consumptionAggV4Panel, "AGG_SUMCLEAN evaluation policies");
//		order++;
//
//		thresholdV5Panel.init(gridLayout, parent0);
//		thresholdV5Panel.addPropertyChangeListener(this);
//		tabsPane.addTab("V5 threshold", null, thresholdV5Panel, "V5 spot measures from camera");
//		idThresholdTabV5 = order;
//		order++;
//
//		chartsV5Panel.init(gridLayout, parent0);
//		chartsV5Panel.addPropertyChangeListener(this);
//		tabsPane.addTab("V5 charts", null, chartsV5Panel, "Display V5 spot results");
//		order++;

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

	/**
	 * Copies AGG V4 policy from the V4 tab into chart/export
	 * {@link ResultsOptions}.
	 */
	public void applyAggV4PolicyInto(ResultsOptions o) {
		consumptionAggV4Panel.applyPolicyInto(o);
	}

	@Override
	public void propertyChange(PropertyChangeEvent event) {
		String n = event.getPropertyName();
		if (n.equals("SPOTS_ROIS_OPEN")) {
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null) {
				displaySpotsInformation(exp);
				tabsPane.setSelectedIndex(id_threshold);
				parent0.dlgExperiment.intervalsPanel.getExptParms(exp);
			}
		} else if (n.equals("SPOTS_ROIS_SAVE")) {
			tabsPane.setSelectedIndex(id_threshold);
		} else if ("SPOTS_ROIS_OPEN".equals(n) || "CAP_ROIS_OPEN".equals(n)) {
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null) {
				displaySpotsInformation(exp);
				tabsPane.setSelectedIndex(idThresholdTabV5);
				parent0.dlgExperiment.intervalsPanel.getExptParms(exp);
			}
		} else if ("SPOTS_ROIS_SAVE".equals(n) || "CAP_ROIS_SAVE".equals(n)) {
			tabsPane.setSelectedIndex(idThresholdTabV5);
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
			else {
				boolean loadCam = (selectedIndex == idThresholdTabV5
						|| (idKymographsTab >= 0 && selectedIndex == idKymographsTab));
				if (loadCam) {
					exp.loadCamDataSpots();
				}
			}
			exp.getSeqCamData().displaySpecificROIs(true, "spots");
		}
	}

}
