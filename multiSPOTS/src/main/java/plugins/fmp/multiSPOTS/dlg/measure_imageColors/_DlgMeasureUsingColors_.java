package plugins.fmp.multiSPOTS.dlg.measure_imageColors;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

import icy.gui.component.PopupPanel;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multiSPOTS.dlg.measure_imageFilters.LoadSavePanel;
import plugins.fmp.multitools.experiment.Experiment;

/**
 * Parallel UI for V5 and color-distance spot measures (detection + charts
 * only).
 */
public class _DlgMeasureUsingColors_ extends JPanel {

	private static final long serialVersionUID = 1L;

	public PopupPanel capPopupPanel = null;
	JTabbedPane tabsPane = new JTabbedPane();

	public DetectColorPanel detectColorPanel = new DetectColorPanel();
	public ChartsColorPanel chartsColorPanel = new ChartsColorPanel();
	public LoadSavePanel loadSavePanel = new LoadSavePanel();

	/**
	 * Kymographs tab index when enabled; {@code -1} disables load-cam on that
	 * branch.
	 */

	private MultiSPOTS parent0 = null;

	public void init(JPanel mainPanel, String string, MultiSPOTS parent0) {
		this.parent0 = parent0;
		capPopupPanel = new PopupPanel(string);
		JPanel capPanel = capPopupPanel.getMainPanel();
		capPanel.setLayout(new BorderLayout());
		capPopupPanel.collapse();
		mainPanel.add(capPopupPanel);

		GridLayout gridLayout = new GridLayout(4, 1);
//		int order = 0;

		detectColorPanel.init(gridLayout, parent0);
//		detectColorPanel.addPropertyChangeListener(this);
		tabsPane.addTab("Color detect", null, detectColorPanel, "Color-distance spot detection from camera");
//		order++;

		chartsColorPanel.init(gridLayout, parent0);
//		chartsColorPanel.addPropertyChangeListener(this);
		tabsPane.addTab("Color charts", null, chartsColorPanel, "Display color-distance spot results");
//		order++;

		loadSavePanel.init(gridLayout, parent0);
//		loadSavePanel.addPropertyChangeListener(this);
		tabsPane.addTab("Load/Save", null, loadSavePanel, "Load/Save xml file with spots descriptors");

		tabsPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
		capPanel.add(tabsPane);

		capPopupPanel.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				parent0.mainFrame.revalidate();
				parent0.mainFrame.pack();
				parent0.mainFrame.repaint();
			}
		});
	}

	public void displaySpotsInformation(Experiment exp) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				parent0.dlgExperiment.optionsPanel.viewSpotsCheckBox.setSelected(true);
			}
		});
	}

}
