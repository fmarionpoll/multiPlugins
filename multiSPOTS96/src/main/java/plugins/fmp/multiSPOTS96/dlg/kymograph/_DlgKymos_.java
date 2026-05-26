package plugins.fmp.multiSPOTS96.dlg.kymograph;

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
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multitools.experiment.Experiment;

public class _DlgKymos_ extends JPanel implements PropertyChangeListener, ChangeListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1122367183829360097L;
	PopupPanel capPopupPanel = null;
	JTabbedPane tabsPane = new JTabbedPane();
	public CageKymographsPanel tabCreate = new CageKymographsPanel();
	public CageKymographLoadSavePanel tabLoadSave = new CageKymographLoadSavePanel();

	private MultiSPOTS96 parent0 = null;

	public void init(JPanel mainPanel, String string, MultiSPOTS96 parent0) {
		this.parent0 = parent0;
		capPopupPanel = new PopupPanel(string);
		JPanel capPanel = capPopupPanel.getMainPanel();
		capPanel.setLayout(new BorderLayout());
		capPopupPanel.collapse();
		mainPanel.add(capPopupPanel);
		GridLayout capLayout = new GridLayout(3, 1);

		tabCreate.init(capLayout, parent0);
		tabCreate.addPropertyChangeListener(this);
		tabsPane.addTab("Build kymos", null, tabCreate, "Build stacked cage kymographs from spot ROIs");

		tabLoadSave.init(capLayout, parent0);
		tabLoadSave.addPropertyChangeListener(this);
		tabsPane.addTab("Load/Save", null, tabLoadSave, "Load or export cage kymograph TIFF files");

		tabsPane.addChangeListener(this);
		tabsPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
		capPanel.add(tabsPane);

		capPopupPanel.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				parent0.mainFrame.revalidate();
				parent0.mainFrame.pack();
				parent0.mainFrame.repaint();
				tabbedCapillariesAndKymosSelected();
			}
		});
	}

	@Override
	public void propertyChange(PropertyChangeEvent event) {
		if (event.getPropertyName().equals("KYMOS_OPEN")) {
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null) {
				parent0.dlgExperiment.updateViewerForSequenceCam(exp);
			}
			tabsPane.setSelectedIndex(0);
		} else if (event.getPropertyName().equals("KYMOS_SAVE")) {
			tabsPane.setSelectedIndex(0);
		}
	}

	public void updateDialogs(Experiment exp) {
//		if (exp != null) {
//			tabCreate.syncFromExperiment(exp);
//			if (exp.getSeqKymos() != null)
//				tabBinsize.displayDlgKymoIntervals(exp);
//		}
	}

	void tabbedCapillariesAndKymosSelected() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null) {
			return;
		}
		int iselected = tabsPane.getSelectedIndex();
		if (iselected == 0) {
			Viewer v = exp.getSeqCamData().getSequence().getFirstViewer();
			if (v != null) {
				v.toFront();
			}
		}
	}

	@Override
	public void stateChanged(ChangeEvent event) {
		if (event.getSource() == tabsPane)
			tabbedCapillariesAndKymosSelected();
	}

}
