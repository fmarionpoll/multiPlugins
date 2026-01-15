package plugins.fmp.multiSPOTS96.tools.chart.strategies;

import java.awt.LayoutManager;

import javax.swing.JPanel;

import plugins.fmp.multitools.tools.chart.ChartCagePair;

/**
 * Strategy interface for arranging chart panels in different layouts.
 * Different implementations can provide grid layouts, horizontal layouts, etc.
 */
public interface ChartLayoutStrategy {
	
	/**
	 * Arranges chart panels in the main panel according to the strategy.
	 * 
	 * @param mainPanel the main panel to arrange panels in
	 * @param chartArray the array of chart panel pairs
	 * @param nPanelsX number of panels along X axis
	 * @param nPanelsY number of panels along Y axis
	 * @param singleCageMode if true, only display the first cage (for single cage view)
	 */
	void arrangePanels(JPanel mainPanel, ChartCagePair[][] chartArray, 
	                   int nPanelsX, int nPanelsY, boolean singleCageMode);
	
	/**
	 * Creates a layout manager for the given dimensions.
	 * 
	 * @param nPanelsX number of panels along X axis
	 * @param nPanelsY number of panels along Y axis
	 * @return the layout manager to use, or null if layout should be set directly on panel
	 */
	LayoutManager createLayout(int nPanelsX, int nPanelsY);
	
	/**
	 * Sets the layout directly on the panel. Some layouts (like BoxLayout) require
	 * the container reference, so they cannot be created independently.
	 * 
	 * @param panel the panel to set the layout on
	 * @param nPanelsX number of panels along X axis
	 * @param nPanelsY number of panels along Y axis
	 */
	default void setLayoutOnPanel(JPanel panel, int nPanelsX, int nPanelsY) {
		LayoutManager layout = createLayout(nPanelsX, nPanelsY);
		if (layout != null) {
			panel.setLayout(layout);
		}
	}
}

