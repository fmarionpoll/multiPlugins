package plugins.fmp.multiSPOTS.dlg.cages;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import icy.gui.component.PopupPanel;
import plugins.fmp.multiSPOTS.MultiSPOTS;

public class _DlgCages_ extends JPanel implements PropertyChangeListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3457738144388946607L;

	BuildCagesAsArray tabBuildCagesAsArray = new BuildCagesAsArray();
	BuildCagesFromContours tabBuildCagesAsContours = new BuildCagesFromContours();
	Infos tabInfos = new Infos();
	public LoadSaveCages tabFile = new LoadSaveCages();
	public PopupPanel capPopupPanel = null;
	JTabbedPane tabsPane = new JTabbedPane();
	int previouslySelected = -1;
	public boolean bTrapROIsEdit = false;

	int iTAB_CAGES1 = 0;
	int iTAB_CAGES2 = 1;
	int iTAB_INFOS = 2;
	int iTAB_EDIT = 3;

	MultiSPOTS parent0 = null;

	public void init(JPanel mainPanel, String string, MultiSPOTS parent0) {
		this.parent0 = parent0;

		capPopupPanel = new PopupPanel(string);
		JPanel capPanel = capPopupPanel.getMainPanel();
		capPanel.setLayout(new BorderLayout());
		capPopupPanel.collapse();

		mainPanel.add(capPopupPanel);
		GridLayout capLayout = new GridLayout(3, 1);

		int iTab = 0;
		iTAB_CAGES1 = iTab;
		tabBuildCagesAsArray.init(capLayout, parent0);
		tabBuildCagesAsArray.addPropertyChangeListener(this);
		tabsPane.addTab("Define array cols/rows", null, tabBuildCagesAsArray, "Build cages as an array of cells");

		iTab++;
		iTAB_CAGES2 = iTab;
		tabBuildCagesAsContours.init(capLayout, parent0);
		tabBuildCagesAsContours.addPropertyChangeListener(this);
		tabsPane.addTab("Detect contours of cages", null, tabBuildCagesAsContours, "Detect contours to build cages");

		iTab++;
		iTAB_INFOS = iTab;
		tabInfos.init(capLayout, parent0);
		tabInfos.addPropertyChangeListener(this);
		tabsPane.addTab("Infos", null, tabInfos, "Display infos about cages and flies positions");

		iTab++;
		tabFile.init(capLayout, parent0);
		tabFile.addPropertyChangeListener(this);
		tabsPane.addTab("Load/Save", null, tabFile, "Load/save cages and flies position");

		tabsPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
		capPanel.add(tabsPane);
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
	public void propertyChange(PropertyChangeEvent evt) {
		if (evt.getPropertyName().equals("LOAD_DATA"))
			tabBuildCagesAsArray.updateNColumnsFieldFromSequence();
	}

}
