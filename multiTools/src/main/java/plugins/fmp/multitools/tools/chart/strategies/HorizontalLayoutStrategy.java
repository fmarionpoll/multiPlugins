package plugins.fmp.multitools.tools.chart.strategies;

import java.awt.LayoutManager;

import javax.swing.BoxLayout;
import javax.swing.JPanel;

import plugins.fmp.multitools.tools.chart.ChartCagePair;

/**
 * Layout strategy that arranges charts horizontally in a single row.
 * This is useful for fly position displays where one chart per cage is shown in sequence.
 */
public class HorizontalLayoutStrategy implements ChartLayoutStrategy {

	@Override
	public void arrangePanels(JPanel mainPanel, ChartCagePair[][] chartArray, 
	                          int nPanelsX, int nPanelsY, boolean singleCageMode) {
		mainPanel.removeAll();
		
		// For horizontal layout, we iterate through all cages and add them in order
		// We flatten the 2D array into a 1D sequence
		if (chartArray != null) {
			for (int row = 0; row < chartArray.length; row++) {
				if (chartArray[row] != null) {
					for (int col = 0; col < chartArray[row].length; col++) {
						ChartCagePair pair = chartArray[row][col];
						if (pair != null && pair.getChartPanel() != null) {
							mainPanel.add(pair.getChartPanel());
						}
					}
				}
			}
		}

		mainPanel.revalidate();
		mainPanel.repaint();
	}

	@Override
	public LayoutManager createLayout(int nPanelsX, int nPanelsY) {
		// Note: BoxLayout requires the container, so we'll set it on the panel directly
		// This method returns null to indicate the layout should be set directly
		return null;
	}
	
	@Override
	public void setLayoutOnPanel(JPanel panel, int nPanelsX, int nPanelsY) {
		// BoxLayout requires the container reference, so we set it directly
		panel.setLayout(new BoxLayout(panel, BoxLayout.LINE_AXIS));
	}
}

