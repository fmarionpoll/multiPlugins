package plugins.fmp.multicafe.dlg.levels;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import icy.gui.component.PopupPanel;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.fmp_tools.imageTransform.ImageTransformEnums;

public class MCLevels_ extends JPanel implements PropertyChangeListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7339633966002954720L;
	PopupPanel capPopupPanel = null;
	private JTabbedPane tabsPane = new JTabbedPane();
	public LoadSaveLevels tabFileLevels = new LoadSaveLevels();
	DetectLevelsDlg tabDetectLevels2 = new DetectLevelsDlg();

	// DetectLevelsKMeans tabDetectLevelsK = new DetectLevelsKMeans();
	DetectGulpsDlg tabDetectGulps = new DetectGulpsDlg();
	EditLevels tabEdit = new EditLevels();
	Adjust tabAdjust = new Adjust();
	public Chart tabGraphs = new Chart();
	MultiCAFE parent0 = null;

	public void init(JPanel mainPanel, String string, MultiCAFE parent0) {
		this.parent0 = parent0;
		capPopupPanel = new PopupPanel(string);
		JPanel capPanel = capPopupPanel.getMainPanel();
		capPanel.setLayout(new BorderLayout());
		capPopupPanel.collapse();
		mainPanel.add(capPopupPanel);

		GridLayout capLayout = new GridLayout(4, 1);

		tabDetectLevels2.init(capLayout, parent0);
		tabDetectLevels2.addPropertyChangeListener(this);
		tabsPane.addTab("Levels", null, tabDetectLevels2, "Find limits of the columns of liquid");

		tabDetectGulps.init(capLayout, parent0);
		tabsPane.addTab("Gulps", null, tabDetectGulps, "Detect gulps");
		tabDetectGulps.addPropertyChangeListener(this);

		tabEdit.init(capLayout, parent0);
		tabEdit.addPropertyChangeListener(this);
		tabsPane.addTab("Edit", null, tabEdit, "Edit Rois / measures");

		tabGraphs.init(capLayout, parent0);
		tabGraphs.addPropertyChangeListener(this);
		tabsPane.addTab("Graphs", null, tabGraphs, "Display results as a graph");

		tabFileLevels.init(capLayout, parent0);
		tabFileLevels.addPropertyChangeListener(this);
		tabsPane.addTab("Load/Save", null, tabFileLevels, "Load/Save kymographs");

		capPanel.add(tabsPane);
		tabDetectLevels2.transformPass1ComboBox.setSelectedItem(ImageTransformEnums.RGB_DIFFS);
		tabsPane.setSelectedIndex(0);

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
	public void propertyChange(PropertyChangeEvent arg0) {
		if (arg0.getPropertyName().equals("MEASURES_SAVE")) {
			tabsPane.setSelectedIndex(0);
		}
	}

}
