package plugins.fmp.multiSPOTS96.dlg.spotsMeasures2;

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
import plugins.fmp.multitools.experiment.Experiment;

/**
 * Parallel UI for V5 and color-distance spot measures (detection + charts only).
 */
public class _DlgSpotMeasure2_ extends JPanel implements PropertyChangeListener, ChangeListener {

	private static final long serialVersionUID = 1L;

	public PopupPanel capPopupPanel = null;
	JTabbedPane tabsPane = new JTabbedPane();
	public ThresholdV5Panel thresholdV5Panel = new ThresholdV5Panel();
	public ChartsV5Panel chartsV5Panel = new ChartsV5Panel();
	public ThresholdColorsPanel thresholdColorsPanel = new ThresholdColorsPanel();
	public ChartsColorPanel chartsColorPanel = new ChartsColorPanel();

	private int idThresholdTabV5 = 0;
	private int idThresholdTabColor = 2;
	private MultiSPOTS96 parent0 = null;

	public void init(JPanel mainPanel, String string, MultiSPOTS96 parent0) {
		this.parent0 = parent0;
		capPopupPanel = new PopupPanel(string);
		JPanel capPanel = capPopupPanel.getMainPanel();
		capPanel.setLayout(new BorderLayout());
		capPopupPanel.collapse();
		mainPanel.add(capPopupPanel);

		GridLayout gridLayout = new GridLayout(4, 1);
		int order = 0;

		thresholdV5Panel.init(gridLayout, parent0);
		thresholdV5Panel.addPropertyChangeListener(this);
		tabsPane.addTab("V5 threshold", null, thresholdV5Panel, "V5 spot measures from camera");
		idThresholdTabV5 = order;
		order++;

		chartsV5Panel.init(gridLayout, parent0);
		chartsV5Panel.addPropertyChangeListener(this);
		tabsPane.addTab("V5 charts", null, chartsV5Panel, "Display V5 spot results");
		order++;

		thresholdColorsPanel.init(gridLayout, parent0);
		thresholdColorsPanel.addPropertyChangeListener(this);
		tabsPane.addTab("Color threshold", null, thresholdColorsPanel, "Color-distance spot measures from camera");
		idThresholdTabColor = order;
		order++;

		chartsColorPanel.init(gridLayout, parent0);
		chartsColorPanel.addPropertyChangeListener(this);
		tabsPane.addTab("Color charts", null, chartsColorPanel, "Display color-distance spot results");
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
				tabsPane.setSelectedIndex(idThresholdTabV5);
				parent0.dlgExperiment.intervalsPanel.getExptParms(exp);
			}
		} else if (event.getPropertyName().equals("SPOTS_ROIS_SAVE")) {
			tabsPane.setSelectedIndex(idThresholdTabV5);
		}
	}

	public void displaySpotsInformation(Experiment exp) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
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
			boolean loadCam = (selectedIndex == idThresholdTabV5 || selectedIndex == idThresholdTabColor);
			if (loadCam) {
				exp.loadCamDataSpots();
			}
			exp.getSeqCamData().displaySpecificROIs(true, "spots");
		}
	}
}
