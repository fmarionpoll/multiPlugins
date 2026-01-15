package plugins.fmp.multiSPOTS96.tools.chart;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;

import icy.util.StringUtil;
import plugins.fmp.multitools.experiment.cages.Cage;
import plugins.fmp.multitools.tools.chart.plot.CageChartPlotFactory;

public class ChartCagePanel extends ChartPanel implements PropertyChangeListener, AutoCloseable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private Cage cageListened = null;

	public ChartCagePanel(JFreeChart chart, int width, int height, int minimumDrawWidth, int minimumDrawHeight,
			int maximumDrawWidth, int maximumDrawHeight, boolean useBuffer, boolean properties, boolean copy,
			boolean save, boolean print, boolean zoom) {
		super(chart, width, height, minimumDrawWidth, minimumDrawHeight, maximumDrawWidth, maximumDrawHeight, useBuffer,
				properties, copy, save, print, zoom);
		// TODO Auto-generated constructor stub
	}

	private void updateFlyCountDisplay(int flyCount) {
		XYPlot xyPlot = getChart().getXYPlot();
		CageChartPlotFactory.setXYPlotBackGroundAccordingToNFlies(xyPlot, flyCount);
	}

	public void subscribeToCagePropertiesUpdates(Cage cage) {
		this.cageListened = cage;
		this.cageListened.getProperties().addPropertyChangeListener(this);
	}

	/**
	 * Gets the cage associated with this chart panel.
	 * 
	 * @return the cage, or null if not set
	 */
	public Cage getCage() {
		return cageListened;
	}

	@Override
	public void close() throws Exception {
		this.cageListened.getProperties().removePropertyChangeListener(this);
		this.cageListened = null;
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (StringUtil.equals("cageNFlies", evt.getPropertyName())) {
			int flyCount = (int) evt.getNewValue();
			updateFlyCountDisplay(flyCount);
		}

	}

}
