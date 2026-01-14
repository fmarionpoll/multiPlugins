package plugins.fmp.multiSPOTS.tools.chart;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.List;

import javax.swing.JPanel;

import org.jfree.chart.ChartColor;
import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.entity.ChartEntity;
import org.jfree.chart.entity.XYItemEntity;
import org.jfree.chart.plot.CombinedRangeXYPlot;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import icy.gui.frame.IcyFrame;
import icy.gui.util.GuiUtil;
import icy.gui.viewer.Viewer;
import plugins.fmp.multiSPOTS.experiment.Experiment;
import plugins.fmp.multiSPOTS.experiment.spots.Spot;
import plugins.fmp.multiSPOTS.tools.toExcel.EnumXLSExportType;
import plugins.fmp.multiSPOTS.tools.toExcel.XLSExport;
import plugins.fmp.multiSPOTS.tools.toExcel.XLSExportOptions;
import plugins.fmp.multiSPOTS.tools.toExcel.XLSResults;
import plugins.fmp.multiSPOTS.tools.toExcel.XLSResultsArray;

public class ChartSpots extends IcyFrame {
	public JPanel mainChartPanel = null;
	public IcyFrame mainChartFrame = null;

	private Point pt = new Point(0, 0);
	private boolean flagMaxMinSet = false;
	private double globalYMax = 0;
	private double globalYMin = 0;
	private double globalXMax = 0;

	private double ymax = 0;
	private double ymin = 0;
	private double xmax = 0;

	int nCagesAlongX = 1;
	int nCagesAlongY = 1;

	ChartPanel[][] panelHolder = null;
	Experiment exp = null;

	// ----------------------------------------

	public void createSpotsChartPanel2(String title, Experiment exp) {
		mainChartPanel = new JPanel();
		mainChartFrame = GuiUtil.generateTitleFrame(title, new JPanel(), new Dimension(300, 70), true, true, true,
				true);
		mainChartFrame.add(mainChartPanel);

		nCagesAlongX = exp.spotsArray.nColumnsPerPlate / exp.spotsArray.nColumnsPerCage;
		nCagesAlongY = exp.spotsArray.nRowsPerPlate / exp.spotsArray.nRowsPerCage;
		panelHolder = new ChartPanel[nCagesAlongY][nCagesAlongX];
		mainChartPanel.setLayout(new GridLayout(nCagesAlongY, nCagesAlongX));
	}

	private NumberAxis setYaxis(int row, int col, XLSExportOptions xlsExportOptions) {
		NumberAxis yAxis = new NumberAxis();
		row = row * exp.spotsArray.nRowsPerCage;
		col = col * exp.spotsArray.nColumnsPerCage;
		String yLegend = String.valueOf((char) (row + 'A')) + "_" + Integer.toString(col);
		yAxis.setLabel(yLegend);

		if (xlsExportOptions.relativeToT0 || xlsExportOptions.relativeToMedianT0) {
//			yAxis.setLabel("ratio"); // (t-t0)/t0 of " + yAxis.getLabel());
			yAxis.setAutoRange(false);
			yAxis.setRange(-0.2, 1.2);
		} else {
//			yAxis.setLabel("grey lvl");
			yAxis.setAutoRange(true);
			yAxis.setAutoRangeIncludesZero(false);
		}
		return yAxis;
	}

	public void displayData(Experiment exp, XLSExportOptions xlsExportOptions) {
		this.exp = exp;
		ymax = 0;
		ymin = 0;
		flagMaxMinSet = false;
		Paint[] chartColor = ChartColor.createDefaultPaintArray();

		XLSResultsArray xlsResultsArray = getDataAsResultsArray(exp, xlsExportOptions);
		XLSResultsArray xlsResultsArray2 = null;
		if (xlsExportOptions.exportType == EnumXLSExportType.AREA_SUMCLEAN) {
			xlsExportOptions.exportType = EnumXLSExportType.AREA_SUM;
			xlsResultsArray2 = getDataAsResultsArray(exp, xlsExportOptions);
			xlsExportOptions.exportType = EnumXLSExportType.AREA_SUMCLEAN;
		}

		// ---------------------------
		int cageID = 0;
		for (int row = 0; row < nCagesAlongY; row++) {
			for (int col = 0; col < nCagesAlongX; col++) {
				if (xlsExportOptions.cageIndexFirst >= 0
						&& (cageID < xlsExportOptions.cageIndexFirst || cageID > xlsExportOptions.cageIndexLast)) {
					cageID++;
					continue;
				}
				NumberAxis yAxis = setYaxis(row, col, xlsExportOptions);
				XYPlot subplot = getXYPlotOfOneCage(cageID, yAxis, chartColor, xlsResultsArray, xlsResultsArray2);
				CombinedRangeXYPlot combinedXYPlot = new CombinedRangeXYPlot(yAxis);
				combinedXYPlot.add(subplot);

				JFreeChart chart = new JFreeChart(null, // xlsExportOptions.exportType.toTitle()
						null, // titleFont
						combinedXYPlot, // plot
						false); // true);
				// create legend
//				Font font = chart.getTitle().getFont().deriveFont(Font.BOLD, (float) 14.);
//				chart.getTitle().setFont(font);
				chart.setID("row:" + row + ":col:" + col + ":cageID:" + cageID);

				ChartPanel panel = new ChartPanel(chart, // chart
						200, 100, // preferred width and height of panel
						50, 25, // min width and height of panel
						1200, 600, // max width and height of panel
						true, // use memory buffer to improve performance
						true, // chart property editor available via popup menu
						true, // copy option available via popup menu
						true, // print option available via popup menu
						false, // zoom options added to the popup menu
						true); // tooltips enabled for the chart

				panel.addChartMouseListener(new ChartMouseListener() {
					public void chartMouseClicked(ChartMouseEvent e) {
						Spot clikedSpot = getClickedSpot(e);
						selectT(exp, xlsExportOptions, clikedSpot);
						selectKymograph(exp, clikedSpot);
					}

					public void chartMouseMoved(ChartMouseEvent e) {
					}
				});

				panelHolder[row][col] = panel;
				mainChartPanel.add(panel);
				cageID++;
			}
		}

		// -----------------------------------
		mainChartFrame.pack();
		mainChartFrame.setLocation(pt);
		mainChartFrame.addToDesktopPane();
		mainChartFrame.setVisible(true);
	}

	public void setLocationRelativeToRectangle(Rectangle rectv, Point deltapt) {
		pt = new Point(rectv.x + deltapt.x, rectv.y + deltapt.y);
	}

	public void setUpperLeftLocation(Rectangle rectv) {
		pt = new Point(rectv.x, rectv.y);
	}

	private XYLineAndShapeRenderer getSubPlotRenderer(XYSeriesCollection xySeriesCollection, Paint[] chartColor) {
		XYLineAndShapeRenderer subPlotRenderer = new XYLineAndShapeRenderer(true, false);

		int maxcolor = chartColor.length;
		Stroke stroke = new BasicStroke(0.5f, // width = width of the stroke
				BasicStroke.CAP_ROUND, // cap = decoration of the ends of the stroke
				BasicStroke.JOIN_ROUND, // join = decoration applied where paths segments meet
				1.0f, // miterlimit = limit to trim the miter join (>= 1)
				new float[] { 2.0f, 4.0f }, // dash = array representing dashing pattern
				0.0f); // dash phase = offset to start dashing pattern

		for (int i = 0; i < xySeriesCollection.getSeriesCount(); i++) {
			String[] description = xySeriesCollection.getSeries(i).getDescription().split(":");
			int icolor = Integer.valueOf(description[3]);
			String key = (String) xySeriesCollection.getSeriesKey(i);
			// get description to get
			if (key.contains("*"))
				subPlotRenderer.setSeriesStroke(i, stroke);
			icolor = icolor % maxcolor;
			subPlotRenderer.setSeriesPaint(i, chartColor[icolor]);
		}
		return subPlotRenderer;
	}

	private XYPlot buildSubPlot(XYSeriesCollection xySeriesCollection, Paint[] chartColor) {
		String[] description = xySeriesCollection.getSeries(0).getDescription().split(":");
		XYLineAndShapeRenderer subPlotRenderer = getSubPlotRenderer(xySeriesCollection, chartColor);
		NumberAxis xAxis = new NumberAxis(); // description[1]);
		final XYPlot subplot = new XYPlot(xySeriesCollection, xAxis, null, subPlotRenderer);

		int nflies = Integer.valueOf(description[5]);
		if (nflies > 0) {
			subplot.setBackgroundPaint(Color.WHITE);
			subplot.setDomainGridlinePaint(Color.GRAY);
			subplot.setRangeGridlinePaint(Color.GRAY);
		} else {
			subplot.setBackgroundPaint(Color.LIGHT_GRAY);
			subplot.setDomainGridlinePaint(Color.WHITE);
			subplot.setRangeGridlinePaint(Color.WHITE);
		}
		return subplot;
	}

	private XLSResultsArray getDataAsResultsArray(Experiment exp, XLSExportOptions xlsExportOptions) {
		XLSExport xlsExport = new XLSExport();
		return xlsExport.getSpotsDataFromOneExperiment(exp, xlsExportOptions);
	}

	private void updateGlobalMaxMin() {
		if (!flagMaxMinSet) {
			globalYMax = ymax;
			globalYMin = ymin;
			globalXMax = xmax;
			flagMaxMinSet = true;
		} else {
			if (globalYMax < ymax)
				globalYMax = ymax;
			if (globalYMin >= ymin)
				globalYMin = ymin;
			if (globalXMax < xmax)
				globalXMax = xmax;
		}
	}

	private XYSeries getXYSeries(XLSResults results, String name) {
		XYSeries seriesXY = new XYSeries(name, false);
		if (results.valuesOut != null && results.valuesOut.length > 0) {
			xmax = results.valuesOut.length;
			ymax = results.valuesOut[0];
			ymin = ymax;
			addPointsAndUpdateExtrema(seriesXY, results, 0);
		}
		return seriesXY;
	}

	private void addPointsAndUpdateExtrema(XYSeries seriesXY, XLSResults results, int startFrame) {
		int x = 0;
		int npoints = results.valuesOut.length;
		for (int j = 0; j < npoints; j++) {
			double y = results.valuesOut[j];
			seriesXY.add(x + startFrame, y);
			if (ymax < y)
				ymax = y;
			if (ymin > y)
				ymin = y;
			x++;
		}
	}

	private XYPlot getXYPlotOfOneCage(int cageID, NumberAxis yAxis, Paint[] chartColor, XLSResultsArray xlsResultsArray,
			XLSResultsArray xlsResultsArray2) {

		XYSeriesCollection xyDataSetList = getDataOfOneCage(xlsResultsArray, cageID, "");
		if (xlsResultsArray2 != null)
			addXYSeriesCollection(xyDataSetList, getDataOfOneCage(xlsResultsArray2, cageID, "*"));

		final XYPlot subplot = buildSubPlot(xyDataSetList, chartColor);
		return subplot;
	}

	private XYSeriesCollection getDataOfOneCage(XLSResultsArray xlsResultsArray, int cageID, String token) {
		XYSeriesCollection xySeriesCollection = null;
		for (int i = 0; i < xlsResultsArray.size(); i++) {
			XLSResults xlsResults = xlsResultsArray.getRow(i);
			if (cageID != xlsResults.cageID)
				continue;
			if (xySeriesCollection == null) {
				xySeriesCollection = new XYSeriesCollection();
			}
			XYSeries seriesXY = getXYSeries(xlsResults, xlsResults.name + token);
			seriesXY.setDescription(
					"ID:" + xlsResults.cageID + ":Pos:" + xlsResults.cagePosition + ":nflies:" + xlsResults.nflies);
			xySeriesCollection.addSeries(seriesXY);
			updateGlobalMaxMin();
		}
		return xySeriesCollection;
	}

	private void addXYSeriesCollection(XYSeriesCollection destination, XYSeriesCollection source) {

		for (int j = 0; j < source.getSeriesCount(); j++) {
			XYSeries xySeries = source.getSeries(j);
			destination.addSeries(xySeries);
		}

	}

	private Spot getClickedSpot(ChartMouseEvent e) {
		final MouseEvent trigger = e.getTrigger();
		if (trigger.getButton() != MouseEvent.BUTTON1)
			return null;

		JFreeChart chart = e.getChart();
		MouseEvent mouseEvent = e.getTrigger();
		String[] chartID = chart.getID().split(":");
		int row = Integer.valueOf(chartID[1]);
		int col = Integer.valueOf(chartID[3]);

		ChartPanel panel = panelHolder[row][col];
		PlotRenderingInfo plotInfo = panel.getChartRenderingInfo().getPlotInfo();
		Point2D pointClicked = panel.translateScreenToJava2D(mouseEvent.getPoint());

		// get chart
		int subplotindex = plotInfo.getSubplotIndex(pointClicked);
		CombinedRangeXYPlot combinedXYPlot = (CombinedRangeXYPlot) chart.getPlot();
		@SuppressWarnings("unchecked")
		List<XYPlot> subplots = combinedXYPlot.getSubplots();

		// get item in the chart
		Spot spotFound = null;
		String description = null;
		ChartEntity chartEntity = e.getEntity();

		if (chartEntity != null && chartEntity instanceof XYItemEntity) {
			XYItemEntity xyItemEntity = (XYItemEntity) chartEntity;
			int isel = xyItemEntity.getSeriesIndex();
			XYDataset xyDataset = xyItemEntity.getDataset();
			description = (String) xyDataset.getSeriesKey(isel); // TODO check
			spotFound = exp.spotsArray.getSpotContainingName(description.substring(0, 5));
			spotFound.spot_CamData_T = xyItemEntity.getItem();

		} else if (subplotindex >= 0) {
			XYDataset xyDataset = subplots.get(subplotindex).getDataset(0);
			description = (String) xyDataset.getSeriesKey(0); // TODO check
			spotFound = exp.spotsArray.getSpotContainingName(description.substring(0, 5));

		} else {
			System.out.println("Graph clicked but source not found");
			return null;
		}

		String[] roiDescription = description.split("_");
		int index = exp.spotsArray.getSpotIndexFromPlateCoordinates(roiDescription[1], roiDescription[2]);
		spotFound.spot_Kymograph_T = index;
		return spotFound;
	}

	private void selectT(Experiment exp, XLSExportOptions xlsExportOptions, Spot spot) {
		Viewer v = exp.seqCamData.seq.getFirstViewer();
		if (v != null && spot != null && spot.spot_CamData_T > 0) {
			int ii = (int) (spot.spot_CamData_T * xlsExportOptions.buildExcelStepMs / exp.seqCamData.binDuration_ms);
			v.setPositionT(ii);
		}
	}

	private void selectKymograph(Experiment exp, Spot spot) {
		if (exp.seqSpotKymos != null) {
			Viewer v = exp.seqSpotKymos.seq.getFirstViewer();
			if (v != null && spot != null) {
				v.setPositionT(spot.spot_Kymograph_T);
			}
		}
	}

}
