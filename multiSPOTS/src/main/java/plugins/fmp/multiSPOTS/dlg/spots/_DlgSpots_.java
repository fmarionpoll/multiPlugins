package plugins.fmp.multiSPOTS.dlg.spots;

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
	CreateSpots tabCreateSpots = new CreateSpots();
	Infos tabInfos = new Infos();
	DetectContours tabShape = new DetectContours();
	Edit tabEdit = new Edit();
	public LoadSaveSpots tabFile = new LoadSaveSpots();

	private int id_shape = 1;
	private int id_infos = 1;
	private int id_create = 0;
	private MultiSPOTS parent0 = null;

	public void init(JPanel mainPanel, String string, MultiSPOTS parent0) {
		this.parent0 = parent0;
		capPopupPanel = new PopupPanel(string);
		JPanel capPanel = capPopupPanel.getMainPanel();
		capPanel.setLayout(new BorderLayout());
		capPopupPanel.collapse();
		mainPanel.add(capPopupPanel);

		GridLayout gridLayout = new GridLayout(3, 1);
		int order = 0;

		tabCreateCages.init(gridLayout, parent0);
		tabCreateCages.addPropertyChangeListener(this);
		tabbedPane.addTab("Cages", null, tabCreateCages, "Create cages");
		id_create = order;
		order++;

		tabCreateSpots.init(gridLayout, parent0);
		tabCreateSpots.addPropertyChangeListener(this);
		tabbedPane.addTab("(old spots)", null, tabCreateSpots,
				"Create spots defining liquid drops without reference to cages");
		id_create = order;
		order++;

		tabInfos.init(gridLayout, parent0);
		tabInfos.addPropertyChangeListener(this);
		tabbedPane.addTab("Infos", null, tabInfos, "Edit infos");
		id_infos = order;
		order++;

		tabShape.init(gridLayout, parent0);
		tabShape.addPropertyChangeListener(this);
		tabbedPane.addTab("Shape", null, tabShape, "Edit spots shape");
		id_shape = order;
		order++;

		tabEdit.init(gridLayout, parent0);
		tabEdit.addPropertyChangeListener(this);
		tabbedPane.addTab("Edit", null, tabEdit, "Edit spots position with time");
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
			tabCreateSpots.updateDialog(exp);
			tabCreateCages.updateNColumnsFieldFromSequence();
		}
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		JTabbedPane tabbedPane = (JTabbedPane) e.getSource();
		int selectedIndex = tabbedPane.getSelectedIndex();
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null) {
			boolean displayCapillaries = (selectedIndex == id_create);
			exp.getSeqCamData().displaySpecificROIs(displayCapillaries, "line");
			exp.getSeqCamData().displaySpecificROIs(true, "spots");
		}
	}

}
