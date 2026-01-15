package plugins.fmp.multicafe.dlg.capillaries;

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
import icy.gui.viewer.Viewer;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.sequence.SequenceKymosUtils;

public class MCCapillaries_ extends JPanel implements PropertyChangeListener, ChangeListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 853047648249832145L;
	PopupPanel capPopupPanel = null;
	JTabbedPane tabsPane = new JTabbedPane();
	public Create tabCreate = new Create();
	Edit tabEdit = new Edit();
	public LoadSaveCapillaries tabFile = new LoadSaveCapillaries();
	Adjust tabAdjust = new Adjust();
	public Infos tabInfos = new Infos();
	private int ID_INFOS = 1;
	private int ID_ADJUST = 3;
	private int ID_EDIT = 2;
	// private int ID_FILTER = 4;
	private boolean editSelected = false;
	private MultiCAFE parent0 = null;

	public void init(JPanel mainPanel, String string, MultiCAFE parent0) {
		this.parent0 = parent0;
		capPopupPanel = new PopupPanel(string);
		JPanel capPanel = capPopupPanel.getMainPanel();
		capPanel.setLayout(new BorderLayout());
		capPopupPanel.collapse();
		mainPanel.add(capPopupPanel);

		GridLayout capLayout = new GridLayout(3, 1);
		int order = 0;
		tabCreate.init(capLayout, parent0);
		tabCreate.addPropertyChangeListener(this);
		tabsPane.addTab("Create caps", null, tabCreate, "Create lines defining capillaries");
		order++;

		ID_INFOS = order;
		tabInfos.init(capLayout, parent0);
		tabInfos.addPropertyChangeListener(this);
		tabsPane.addTab("Infos", null, tabInfos, "Define pixel conversion unit of images and capillaries content");
		order++;

		ID_EDIT = order;
		tabEdit.init(capLayout, parent0);
		tabEdit.addPropertyChangeListener(this);
		tabsPane.addTab("Edit", null, tabEdit, "Edit capillaries position and size");
		order++;

		ID_ADJUST = order;
		tabAdjust.init(capLayout, parent0);
		tabAdjust.addPropertyChangeListener(this);
		tabsPane.addTab("Adjust", null, tabAdjust, "Adjust ROIS position to the capillaries");
		order++;

		tabFile.init(capLayout, parent0);
		tabFile.addPropertyChangeListener(this);
		tabsPane.addTab("Load/Save", null, tabFile, "Load/Save xml file with capillaries descriptors");

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
		if (event.getPropertyName().equals("CAP_ROIS_OPEN")) {
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null) {
				displayCapillariesInformation(exp);
				tabsPane.setSelectedIndex(ID_INFOS);
				parent0.paneExperiment.tabIntervals.displayCamDataIntervals(exp);
			}
		} else if (event.getPropertyName().equals("CAP_ROIS_SAVE")) {
			tabsPane.setSelectedIndex(ID_INFOS);
		} else if (event.getPropertyName().equals("CAPILLARIES_NEW")) {
			tabsPane.setSelectedIndex(ID_INFOS);
		}

	}

	public void displayCapillariesInformation(Experiment exp) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				updateDialogs(exp);
				parent0.paneExperiment.tabOptions.viewCapillariesCheckBox.setSelected(true);
			}
		});
	}

	public void updateDialogs(Experiment exp) {
		if (exp != null) {
			SequenceKymosUtils.transferCamDataROIStoKymo(exp);
			exp.getCapillaries().getDesc_old().copy(exp.getCapillaries().getCapillariesDescription());
			tabInfos.setDlgInfosCapillaryDescriptors(exp.getCapillaries());
			tabCreate.setGroupingAndNumber(exp.getCapillaries());
		}
	}

	public void getDialogCapillariesInfos(Experiment exp) {
		tabInfos.getCapillaryDescriptorsFromDlgInfos(exp.getCapillaries());
		tabCreate.setCapillariesGrouping(exp.getCapillaries());
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		JTabbedPane tabbedPane = (JTabbedPane) e.getSource();
		int selectedIndex = tabbedPane.getSelectedIndex();
		tabAdjust.roisDisplayrefBar(selectedIndex == ID_ADJUST);
//		boolean disableCapillaries = (selectedIndex != ID_FILTER);
//		parent0.paneExperiment.tabOptions.displayROIsCategory(disableCapillaries, "line");

		parent0.paneExperiment.tabOptions.viewCapillariesCheckBox.setSelected(selectedIndex == ID_INFOS);
		if (selectedIndex == ID_EDIT) {
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null) {
				Viewer v = exp.getSeqCamData().getSequence().getFirstViewer();
				if (v != null) {
					v.toFront();
					v.requestFocus();
				}
			}
			tabEdit.openDialog();
			editSelected = true;
		} else if (editSelected) {
			tabEdit.closeDialog();
			editSelected = false;
		}

	}

}
