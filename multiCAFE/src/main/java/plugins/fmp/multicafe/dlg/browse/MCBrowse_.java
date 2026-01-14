package plugins.fmp.multicafe.dlg.browse;

import java.awt.BorderLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import javax.swing.JPanel;

import plugins.fmp.multicafe.MultiCAFE;

public class MCBrowse_ extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -6826269677524125173L;
	public LoadSaveExperiment panelLoadSave = new LoadSaveExperiment();

	public void init(JPanel mainPanel, String string, MultiCAFE parent0) {
		JPanel filesPanel = panelLoadSave.initPanel(parent0);
		mainPanel.add(filesPanel, BorderLayout.CENTER);
		mainPanel.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				parent0.mainFrame.revalidate();
				parent0.mainFrame.pack();
				parent0.mainFrame.repaint();
			}
		});
	}

}
