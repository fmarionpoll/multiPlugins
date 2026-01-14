package plugins.fmp.multiSPOTS.tools.chart;

import org.jfree.data.xy.XYSeriesCollection;

import plugins.fmp.multiSPOTS.tools.MaxMinDouble;

public class ChartData {
	public MaxMinDouble yMaxMin = null;
	public MaxMinDouble xMaxMin = null;
	public XYSeriesCollection xyDataset = null;

	// ----------------------------------------

	public ChartData() {
	}

	public ChartData(MaxMinDouble xMaxMin, MaxMinDouble yMaxMin, XYSeriesCollection xyDataset) {
		this.xMaxMin = xMaxMin;
		this.yMaxMin = yMaxMin;
		this.xyDataset = xyDataset;
	}
}
