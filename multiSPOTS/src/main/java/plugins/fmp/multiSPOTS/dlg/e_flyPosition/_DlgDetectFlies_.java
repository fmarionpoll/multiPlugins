package plugins.fmp.multiSPOTS.dlg.e_flyPosition;

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

public class _DlgDetectFlies_ extends JPanel implements PropertyChangeListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3457738144388946607L;

	Detect1Panel detect1Panel = new Detect1Panel();
	Detect2BackgroundPanel detect2BackgroundPanel = new Detect2BackgroundPanel();
	Detect2FliesPanel detect2FliesPanel = new Detect2FliesPanel();
	EditPanel editPanel = new EditPanel();
	public LoadSavePositions tabFile = new LoadSavePositions();
	public PlotFliesPositions tabGraphics = new PlotFliesPositions();
	public PopupPanel capPopupPanel = null;
	JTabbedPane tabsPane = new JTabbedPane();
	int previouslySelected = -1;
	public boolean bTrapROIsEdit = false;

	int iTAB_DETECT1 = 0;
	int iTAB_DETECT2BCKGND = 1;
	int iTAB_DETECT2FLIES = 2;
	int iTAB_EDIT = 3;

	MultiSPOTS parent0 = null;

	public void init(JPanel mainPanel, String string, MultiSPOTS parent0) {
		this.parent0 = parent0;

		capPopupPanel = new PopupPanel(string);
		JPanel capPanel = capPopupPanel.getMainPanel();
		capPanel.setLayout(new BorderLayout());
		capPopupPanel.collapse();

		mainPanel.add(capPopupPanel);
		GridLayout capLayout = new GridLayout(4, 1);
		createTabs(capLayout);

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

	void createTabs(GridLayout capLayout) {
		int iTab = 0;

		iTab++;
		iTAB_DETECT1 = iTab;
		detect1Panel.init(capLayout, parent0);
		detect1Panel.addPropertyChangeListener(this);
		tabsPane.addTab("Detect (option 1)", null, detect1Panel,
				"Detect flies position using thresholding on image overlay");

		iTab++;
		iTAB_DETECT2BCKGND = iTab;
		detect2BackgroundPanel.init(capLayout, parent0);
		detect2BackgroundPanel.addPropertyChangeListener(this);
		tabsPane.addTab("Background", null, detect2BackgroundPanel, "Build background image");

		iTab++;
		iTAB_DETECT2FLIES = iTab;
		detect2FliesPanel.init(capLayout, parent0);
		detect2FliesPanel.addPropertyChangeListener(this);
		tabsPane.addTab("Detect (option 2)", null, detect2FliesPanel, "Detect flies position from subtracted background");

		iTab++;
		iTAB_EDIT = iTab;
		editPanel.init(capLayout, parent0);
		editPanel.addPropertyChangeListener(this);
		tabsPane.addTab("Edit", null, editPanel, "Edit flies detection");

		iTab++;
		tabGraphics.init(capLayout, parent0);
		tabGraphics.addPropertyChangeListener(this);
		tabsPane.addTab("Graphs", null, tabGraphics, "Display results as graphics");

		iTab++;
		tabFile.init(capLayout, parent0);
		tabFile.addPropertyChangeListener(this);
		tabsPane.addTab("Load/Save", null, tabFile, "Load/save flies position");
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
//		if (evt.getPropertyName().equals("LOAD_DATA"))
//			tabBuildCagesAsArray.updateNColumnsFieldFromSequence();
	}

}
