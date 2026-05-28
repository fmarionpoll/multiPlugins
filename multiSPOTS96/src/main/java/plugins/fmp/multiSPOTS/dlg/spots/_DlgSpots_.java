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
import plugins.fmp.multitools.experiment.spot.Spot;

public class _DlgSpots_ extends JPanel implements PropertyChangeListener, ChangeListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 853047648249832145L;
	public PopupPanel capPopupPanel = null;
	JTabbedPane tabbedPane = new JTabbedPane();

//			ThresholdColors colorsThreshold = new ThresholdColors();
	CreateCagesPanel createCagesPanel = new CreateCagesPanel();
	CreateBlobsPanel createBlobsPanel = new CreateBlobsPanel();
	EditSpotsPanel editSpotsPanel = new EditSpotsPanel();
	public InfosPanel infosPanel = new InfosPanel();

	public LoadSaveSpotsPanel loadSaveSpotsPanel = new LoadSaveSpotsPanel();

//	private int id_shape = 1;
	private int id_infos = 3;
	private int id_createCages = 0;
//	private int id_spots = 1;
	private int id_editSpots = 2;
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

		createCagesPanel.init(gridLayout, parent0);
		createCagesPanel.addPropertyChangeListener(this);
		tabbedPane.addTab("Cages", null, createCagesPanel, "Create cages");
		id_createCages = order;
		order++;

		createBlobsPanel.init(gridLayout, parent0);
		createBlobsPanel.addPropertyChangeListener(this);
		tabbedPane.addTab("Detect blobs", null, createBlobsPanel, "Detect blobs thresholding image");
//		id_spots = order;
		order++;

		editSpotsPanel.init(gridLayout, parent0);
		editSpotsPanel.addPropertyChangeListener(this);
		tabbedPane.addTab("Edit", null, editSpotsPanel, "Edit spots position");
		id_editSpots = order;
		order++;
//
//		tabShape.init(gridLayout, parent0);
//		tabShape.addPropertyChangeListener(this);
//		tabbedPane.addTab("Shape", null, tabShape, "Edit spots shape");
//		id_shape = order;
//		order++;

		infosPanel.init(gridLayout, parent0.expListComboLazy);
		infosPanel.addPropertyChangeListener(this);
		tabbedPane.addTab("Infos", null, infosPanel, "Edit infos");
		id_infos = order;
		order++;

		loadSaveSpotsPanel.init(gridLayout, parent0);
		loadSaveSpotsPanel.addPropertyChangeListener(this);
		tabbedPane.addTab("Load/Save", null, loadSaveSpotsPanel, "Load/Save cage & spots descriptors (xml file)");
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
				parent0.dlgExperiment.intervalsPanel.getExptParms(exp);
				createCagesPanel.updateNColumnsFieldFromSequence();
			}
		} else if (event.getPropertyName().equals("CAP_ROIS_SAVE")) {
			tabbedPane.setSelectedIndex(id_editSpots);
		}
	}

	public void displaySpotsInformation(Experiment exp) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				updateDialogs(exp);
				parent0.dlgExperiment.optionsPanel.viewSpotsCheckBox.setSelected(true);
			}
		});
	}

	public void updateDialogs(Experiment exp) {
		if (exp != null) {
			ExperimentUtils.transferSpotsToCamDataSequence(exp);
		}
	}

	/**
	 * Called when the user picks a trace on a spot-measures chart: sync the Spots →
	 * Infos table row if that table has been opened in this session.
	 */
	public void onMeasureChartSpotClicked(Spot spot) {
		if (spot == null) {
			return;
		}
		infosPanel.highlightSpotInInfosTableIfOpen(spot);
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		JTabbedPane tabbedPane = (JTabbedPane) e.getSource();
		int selectedIndex = tabbedPane.getSelectedIndex();
		if (selectedIndex != id_editSpots)
			editSpotsPanel.clearTemporaryROIs();
		if (selectedIndex != id_createCages)
			createCagesPanel.clearTemporaryROIs();
//		exp.getSeqCamData().displaySpecificROIs(true, "spots");
	}

}
