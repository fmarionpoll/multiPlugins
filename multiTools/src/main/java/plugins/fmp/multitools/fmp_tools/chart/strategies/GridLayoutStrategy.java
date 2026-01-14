package plugins.fmp.multitools.fmp_tools.chart.strategies;

import java.awt.GridLayout;
import java.awt.LayoutManager;

import javax.swing.JPanel;

import org.jfree.chart.ChartPanel;

import plugins.fmp.multitools.fmp_tools.chart.ChartCagePair;

/**
 * Layout strategy that arranges charts in a grid layout (one chart per cage).
 * This is the default strategy for displaying cage-based measurements.
 */
public class GridLayoutStrategy implements ChartLayoutStrategy {

	@Override
	public void arrangePanels(JPanel mainPanel, ChartCagePair[][] chartArray, int nPanelsX, int nPanelsY,
			boolean singleCageMode) {
		mainPanel.removeAll();

		if (singleCageMode) {
			// When displaying a single cage, the array is 1x1 and the panel is always at
			// [0][0]
			if (chartArray != null && chartArray.length > 0 && chartArray[0].length > 0) {
				ChartCagePair pair = chartArray[0][0];
				if (pair != null && pair.getChartPanel() != null) {
					mainPanel.add(pair.getChartPanel());
				} else {
					mainPanel.add(new JPanel());
				}
			} else {
				mainPanel.add(new JPanel());
			}
		} else {
			// Grid layout: iterate through all positions
			for (int row = 0; row < nPanelsY; row++) {
				for (int col = 0; col < nPanelsX; col++) {
					ChartPanel chartPanel = null;
					if (row < chartArray.length && col < chartArray[0].length) {
						ChartCagePair pair = chartArray[row][col];
						if (pair != null) {
							chartPanel = pair.getChartPanel();
						}
					}

					if (chartPanel == null) {
						mainPanel.add(new JPanel());
					} else {
						mainPanel.add(chartPanel);
					}
				}
			}
		}

		mainPanel.revalidate();
		mainPanel.repaint();
	}

	@Override
	public LayoutManager createLayout(int nPanelsX, int nPanelsY) {
		return new GridLayout(nPanelsY, nPanelsX);
	}
}
