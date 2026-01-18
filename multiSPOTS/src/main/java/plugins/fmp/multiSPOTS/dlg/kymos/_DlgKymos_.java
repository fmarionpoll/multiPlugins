package plugins.fmp.multiSPOTS.dlg.kymos;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import icy.gui.component.PopupPanel;
import icy.gui.viewer.Viewer;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multitools.experiment.Experiment;

public class _DlgKymos_ extends JPanel implements PropertyChangeListener, ChangeListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1122367183829360097L;
	public PopupPanel capPopupPanel = null;
	JTabbedPane tabsPane = new JTabbedPane();
	public Create tabCreate = new Create();
	public Display tabDisplay = new Display();
	public LoadSave tabLoadSave = new LoadSave();

	private MultiSPOTS parent0 = null;

	public void init(JPanel mainPanel, String string, MultiSPOTS parent0) {
		this.parent0 = parent0;
		capPopupPanel = new PopupPanel(string);
		JPanel capPanel = capPopupPanel.getMainPanel();
		capPanel.setLayout(new BorderLayout());
		capPopupPanel.collapse();
		mainPanel.add(capPopupPanel);
		GridLayout capLayout = new GridLayout(3, 1);

		tabCreate.init(capLayout, this.parent0);
		tabCreate.addPropertyChangeListener(this);
		tabsPane.addTab("Build kymos", null, tabCreate, "Build pseudo-kymographs from ROIs");

		tabDisplay.init(capLayout, parent0);
		tabDisplay.addPropertyChangeListener(this);
		tabsPane.addTab("Display", null, tabDisplay, "Display options of data & kymographs");

		tabsPane.addChangeListener(this);
		tabsPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
		capPanel.add(tabsPane);

		tabLoadSave.init(capLayout, parent0);
		tabLoadSave.addPropertyChangeListener(this);
		tabsPane.addTab("Load/Save", null, tabLoadSave, "Load/Save  kymographs");

		capPopupPanel.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				parent0.mainFrame.revalidate();
				parent0.mainFrame.pack();
				parent0.mainFrame.repaint();
				tabsPaneSelected();
			}
		});
	}

	@Override
	public void propertyChange(PropertyChangeEvent event) {
		if (event.getPropertyName().equals("KYMOS_OPEN")) {
			tabsPane.setSelectedIndex(2);
		} else if (event.getPropertyName().equals("KYMOS_SAVE")) {
			tabsPane.setSelectedIndex(1);
		}
	}

	public void updateDialogs(Experiment exp) {
//		tabIntervals.displayDlgKymoIntervals (exp);
	}

	void tabsPaneSelected() {
		Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
		if (exp == null || exp.seqCamData == null)
			return;
		int iselected = tabsPane.getSelectedIndex();
		if (iselected == 0) {
			Viewer v = exp.seqCamData.seq.getFirstViewer();
			if (v != null)
				v.toFront();
			parent0.dlgExperiment.tabsPane.setSelectedIndex(0);
			tabCreate.getExptParms(exp);
		} else if (iselected == 1) {
			parent0.dlgKymos.tabDisplay.displayUpdateOnSwingThread();
		}
	}

	@Override
	public void stateChanged(ChangeEvent event) {
		if (event.getSource() == tabsPane)
			tabsPaneSelected();
	}

}