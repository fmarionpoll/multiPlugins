package plugins.fmp.multicafe.dlg.cages;

import java.awt.GridLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import plugins.fmp.multicafe.MultiCAFE;

public class Detect2_ extends JPanel implements PropertyChangeListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	Detect2Background tabDetectBackground = new Detect2Background();
	Detect2Flies tabDetect2Flies = new Detect2Flies();
	JTabbedPane tabsPane = new JTabbedPane(JTabbedPane.LEFT);
	int previouslySelected = -1;
	int iTAB_BACKGND = 0;
	int iTAB_DETECT2 = 1;
	MultiCAFE parent0 = null;

	public void init(GridLayout capLayout, MultiCAFE parent0) {
		this.parent0 = parent0;

		createTabs(capLayout);
		tabsPane.setSelectedIndex(0);
		add(tabsPane);

		tabsPane.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				int selectedIndex = tabsPane.getSelectedIndex();
				previouslySelected = selectedIndex;
			}
		});
	}

	void createTabs(GridLayout capLayout) {
//		GridLayout capLayout = new GridLayout(4, 1);
//		tabsPane.setTabPlacement(JTabbedPane.LEFT);
		tabsPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

		int iTab = 0;
		iTAB_BACKGND = iTab;
		tabDetectBackground.init(capLayout, parent0);
		tabDetectBackground.addPropertyChangeListener(this);
		tabsPane.addTab("Bkgnd" + "", null, tabDetectBackground, "Build background without flies");

		iTab++;
		iTAB_DETECT2 = iTab;
		tabDetect2Flies.init(capLayout, parent0);
		tabDetect2Flies.addPropertyChangeListener(this);
		tabsPane.addTab("Flies", null, tabDetect2Flies, "Detect flies position using background subtraction");
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		// TODO Auto-generated method stub

	}

}
