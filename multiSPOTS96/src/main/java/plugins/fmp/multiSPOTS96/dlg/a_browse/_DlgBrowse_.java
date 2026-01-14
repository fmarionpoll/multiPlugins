package plugins.fmp.multiSPOTS96.dlg.a_browse;

import java.awt.BorderLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import javax.swing.JPanel;

import plugins.fmp.multiSPOTS96.MultiSPOTS96;

public class _DlgBrowse_ extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -6826269677524125173L;
	public LoadSaveExperiment loadSaveExperiment = new LoadSaveExperiment();

	public void init(JPanel mainPanel, String string, MultiSPOTS96 parent0) {
		JPanel filesPanel = loadSaveExperiment.initPanel(parent0);
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
