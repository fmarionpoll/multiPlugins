package plugins.fmp.multiSPOTS96.dlg.b_spots;

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
import plugins.fmp.multitools.experiment.ExperimentUtils;

public class _DlgSpots_ extends JPanel implements PropertyChangeListener, ChangeListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 853047648249832145L;
	public PopupPanel capPopupPanel = null;
	JTabbedPane tabbedPane = new JTabbedPane();

//			ThresholdColors colorsThreshold = new ThresholdColors();
	CreateCages tabCreateCages = new CreateCages();
//	CreateSpots tabCreateSpots = new CreateSpots();
	DetectSpots tabDetectSpots = new DetectSpots();
	public Infos tabInfos = new Infos();
//	ShapeSpots tabShape = new ShapeSpots();
	EditSpots tabEditSpots = new EditSpots();
	public LoadSaveSpots tabFile = new LoadSaveSpots();

	private int id_shape = 1;
	private int id_infos = 1;
	private int id_createCages = 0;
//	private int id_spots = 1;
	private int id_editSpots = 2;
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

		tabCreateCages.init(gridLayout, parent0);
		tabCreateCages.addPropertyChangeListener(this);
		tabbedPane.addTab("Cages", null, tabCreateCages, "Create cages");
		id_createCages = order;
		order++;

//		tabCreateSpots.init(gridLayout, parent0);
//		tabCreateSpots.addPropertyChangeListener(this);
//		tabbedPane.addTab("Spots", null, tabCreateSpots, "Create spots defining drops with reference to cages");
//		order++;

		tabDetectSpots.init(gridLayout, parent0);
		tabDetectSpots.addPropertyChangeListener(this);
		tabbedPane.addTab("Detect spots", null, tabDetectSpots, "Detect spots after threshold");
//		id_spots = order;
		order++;

		tabEditSpots.init(gridLayout, parent0);
		tabEditSpots.addPropertyChangeListener(this);
		tabbedPane.addTab("Edit", null, tabEditSpots, "Edit spots position");
		id_editSpots = order;
		order++;
//
//		tabShape.init(gridLayout, parent0);
//		tabShape.addPropertyChangeListener(this);
//		tabbedPane.addTab("Shape", null, tabShape, "Edit spots shape");
//		id_shape = order;
//		order++;

		tabInfos.init(gridLayout, parent0.expListComboLazy);
		tabInfos.addPropertyChangeListener(this);
		tabbedPane.addTab("Infos", null, tabInfos, "Edit infos");
		id_infos = order;
		order++;

		tabFile.init(gridLayout, parent0);
		tabFile.addPropertyChangeListener(this);
		tabbedPane.addTab("Load/Save", null, tabFile, "Load/Save cage & spots descriptors (xml file)");
		order++;

		tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
		capPanel.add(tabbedPane);
		tabbedPane.addChangeListener(this);

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
				tabbedPane.setSelectedIndex(id_infos);
				parent0.dlgExperiment.tabIntervals.getExptParms(exp);
				tabCreateCages.updateNColumnsFieldFromSequence();
			}
		} else if (event.getPropertyName().equals("CAP_ROIS_SAVE")) {
			tabbedPane.setSelectedIndex(id_shape);
		}
	}

	public void displaySpotsInformation(Experiment exp) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				updateDialogs(exp);
				parent0.dlgExperiment.tabOptions.viewSpotsCheckBox.setSelected(true);
			}
		});
	}

	public void updateDialogs(Experiment exp) {
		if (exp != null) {
			ExperimentUtils.transferSpotsToCamDataSequence(exp);
			tabCreateCages.updateNColumnsFieldFromSequence();
		}
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		JTabbedPane tabbedPane = (JTabbedPane) e.getSource();
		int selectedIndex = tabbedPane.getSelectedIndex();
		if (selectedIndex != id_editSpots)
			tabEditSpots.clearTemporaryROIs();
		if (selectedIndex != id_createCages)
			tabCreateCages.clearTemporaryROIs();
//		exp.getSeqCamData().displaySpecificROIs(true, "spots");
	}

}
