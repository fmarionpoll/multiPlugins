package plugins.fmp.multitools.fmp_tools.chart.strategies;

import java.awt.FlowLayout;
import java.awt.LayoutManager;

import javax.swing.JPanel;

import plugins.fmp.multitools.fmp_tools.chart.ChartCagePair;

/**
 * Layout strategy that arranges charts using FlowLayout (wraps naturally).
 * This allows charts to wrap to new lines automatically without fixed grid constraints,
 * and only displays charts for existing cages (skips empty positions).
 */
public class FlowLayoutStrategy implements ChartLayoutStrategy {

	@Override
	public void arrangePanels(JPanel mainPanel, ChartCagePair[][] chartArray, 
	                          int nPanelsX, int nPanelsY, boolean singleCageMode) {
		mainPanel.removeAll();
		
		if (singleCageMode) {
			// When displaying a single cage, the array is 1x1 and the panel is always at [0][0]
			if (chartArray != null && chartArray.length > 0 && chartArray[0].length > 0) {
				ChartCagePair pair = chartArray[0][0];
				if (pair != null && pair.getChartPanel() != null) {
					mainPanel.add(pair.getChartPanel());
				}
			}
		} else {
			// FlowLayout: only add panels for existing cages, skip nulls
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
		}

		mainPanel.revalidate();
		mainPanel.repaint();
	}

	@Override
	public LayoutManager createLayout(int nPanelsX, int nPanelsY) {
		return new FlowLayout(FlowLayout.LEFT, 5, 5);
	}
}
