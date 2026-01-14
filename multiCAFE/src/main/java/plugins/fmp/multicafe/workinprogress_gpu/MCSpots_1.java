package plugins.fmp.multicafe.workinprogress_gpu;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import java.util.ArrayList;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import icy.gui.frame.IcyFrame;
import icy.gui.util.GuiUtil;
import icy.gui.viewer.Viewer;
import icy.image.IcyBufferedImage;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.fmp_experiment.Experiment;
import plugins.fmp.multitools.fmp_experiment.sequence.SequenceKymos;
import plugins.fmp.multitools.fmp_service.KymographService;
import plugins.fmp.multitools.fmp_tools.imageTransform.ImageTransformEnums;

public class MCSpots_1 extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7614659720134775871L;
	private MultiCAFE parent0 = null;
	private JButton subtractButton = new JButton("Subtract first column");
	private JButton buildHistogramButton = new JButton("Build histogram");
	private JButton removeBackGroundButton = new JButton("Remove background");
	private double[][] avgX = null;
	private double[][] avgY = null;
	IcyFrame mainChartFrame = null;

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;
		add(GuiUtil.besidesPanel(subtractButton, buildHistogramButton));
		add(GuiUtil.besidesPanel(removeBackGroundButton, new JLabel(" ")));
		defineActionListeners();
	}

	private void defineActionListeners() {
		subtractButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					subtractFirstColumn(exp);
			}
		});

		buildHistogramButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					buildHistogram(exp);
			}
		});

		removeBackGroundButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					System.out.println("MCSpots_1:removeBackgroundButton should call removeBackGround()");
//					removeBackGround(exp);
			}
		});
	}

	// -------------------------------------------------

	void subtractFirstColumn(Experiment exp) {
		SequenceKymos seqKymos = exp.getSeqKymos();
		if (seqKymos == null)
			return;
		ImageTransformEnums transform = ImageTransformEnums.SUBTRACT_1RSTCOL;
		int zChannelDestination = 1;
		new KymographService().buildFiltered(exp, 0, zChannelDestination, transform, 0);
		seqKymos.getSequence().getFirstViewer().getCanvas().setPositionZ(zChannelDestination);
	}

	void buildHistogram(Experiment exp) {
		SequenceKymos seqKymos = exp.getSeqKymos();
		if (seqKymos == null)
			return;

		int imageIndex = exp.getSeqKymos().getSequence().getFirstViewer().getPositionT();
		getAverageXandYProfile(seqKymos, imageIndex);
		graphDisplay2Panels(exp, avgX, avgY);
	}

	private void getAverageXandYProfile(SequenceKymos seqKymos, int imageIndex) {
		int height = seqKymos.getSequence().getSizeY();
		int width = seqKymos.getSequence().getSizeX();
		Rectangle rect = new Rectangle(0, 0, width, height);

		Point2D.Double[] refpoint = new Point2D.Double[4];
		refpoint[0] = new Point2D.Double(rect.x, rect.y);
		refpoint[1] = new Point2D.Double(rect.x, rect.y + rect.height - 1);
		refpoint[2] = new Point2D.Double(rect.x + rect.width - 1, rect.y + rect.height - 1);
		refpoint[3] = new Point2D.Double(rect.x + rect.width - 1, rect.y);

		int nYpoints = (int) (refpoint[1].y - refpoint[0].y + 1);
		int nXpoints = (int) (refpoint[3].x - refpoint[0].x + 1);
		double[][] sumXArray = new double[nXpoints][3];
		double[][] countXArray = new double[nXpoints][3];
		avgX = new double[nXpoints][4];
		double[][] sumYArray = new double[nYpoints][3];
		double[][] countYArray = new double[nYpoints][3];
		avgY = new double[nYpoints][4];

		int z = 0;
		IcyBufferedImage virtualImage = seqKymos.getSeqImage(imageIndex, z);

		for (int chan = 0; chan < 3; chan++) {
			if (virtualImage == null) {
				System.out.println("MCSpots_1:getAverageXandYProfile() An error occurred while reading image: "
						+ seqKymos.getCurrentFrame());
				return;
			}
			int widthImage = virtualImage.getSizeX();
			double[] image1DArray = Array1DUtil.arrayToDoubleArray(virtualImage.getDataXY(chan),
					virtualImage.isSignedDataType());
			double deltaXUp = (refpoint[3].x - refpoint[0].x + 1);
			double deltaXDown = (refpoint[2].x - refpoint[1].x + 1);
			double deltaYUp = (refpoint[3].y - refpoint[0].y + 1);
			double deltaYDown = (refpoint[2].y - refpoint[1].y + 1);

			for (int ix = 0; ix < nXpoints; ix++) {
				double xUp = refpoint[0].x + deltaXUp * ix / nXpoints;
				double yUp = refpoint[0].y + deltaYUp * ix / nXpoints;
				double xDown = refpoint[1].x + deltaXDown * ix / nXpoints;
				double yDown = refpoint[1].y + deltaYDown * ix / nXpoints;

				for (int iy = 0; iy < nYpoints; iy++) {
					double x = xUp + (xDown - xUp + 1) * iy / nYpoints;
					double y = yUp + (yDown - yUp + 1) * iy / nYpoints;

					int index = (int) x + ((int) y * widthImage);
					double value = image1DArray[index];

					sumXArray[ix][chan] = sumXArray[ix][chan] + value;
					countXArray[ix][chan] = countXArray[ix][chan] + 1;

					sumYArray[iy][chan] = sumYArray[iy][chan] + value;
					countYArray[iy][chan] = countYArray[iy][chan] + 1;
				}
			}
		}

		// compute average
		for (int chan = 0; chan < 3; chan++) {
			for (int ix = 0; ix < nXpoints; ix++) {
				double n = countXArray[ix][chan];
				avgX[ix][chan] = sumXArray[ix][chan] / n;
			}

			for (int iy = 0; iy < nYpoints; iy++) {
				double n = countYArray[iy][chan];
				avgY[iy][chan] = sumYArray[iy][chan] / n;
			}
		}
	}

//	private void removeBackGround(Experiment exp) 
//	{
//		double [] avgColor = new double [3];
//		int width = exp.getSeqKymos().getSeq().getSizeX();
//		for (int chan = 0; chan <3; chan++) 
//		{
//			double sum = 0;
//			for (int ix = 0; ix < width; ix++) 
//				sum += avgX[ix][chan]; 
//			avgColor[chan] = sum/width;
//		}
//		// now either create a reference image with this color or write a new transform
//		EnumImageTransformations transform = EnumImageTransformations.SUBTRACT_REF;
//		int zChannelDestination = 1;
//		exp.setReferenceImageWithConstant(avgColor);
//		exp.kymosBuildFiltered01(0, zChannelDestination, transform, 0);
//		exp.getSeqKymos().getSeq().getFirstViewer().getCanvas().setPositionZ(zChannelDestination);
//	}
//	
//	public void setReferenceImageWithConstant (double [] pixel) 
//	{
//		if (tImg == null) 
//			tImg = new ImageTransform();
//		tImg.setSpanDiff(0);
//		Sequence seq = seqKymos.getSeq();
//		tImg.referenceImage = new IcyBufferedImage(seq.getSizeX(), seq.getSizeY(), seq.getSizeC(), seq.getDataType_());
//		IcyBufferedImage result = tImg.referenceImage;
//		for (int c=0; c < seq.getSizeC(); c++) 
//		{
//			double [] doubleArray = Array1DUtil.arrayToDoubleArray(result.getDataXY(c), result.isSignedDataType());
//			Array1DUtil.fill(doubleArray, 0, doubleArray.length, pixel[c]);
//			Array1DUtil.doubleArrayToArray(doubleArray, result.getDataXY(c));
//		}
//		result.dataChanged();
//	}

	private void graphDisplay2Panels(Experiment exp, double[][] arrayX, double[][] arrayY) {
		Point pt = null;
		if (mainChartFrame != null) {
			pt = mainChartFrame.getLocation();
			mainChartFrame.removeAll();
			mainChartFrame.close();
		}

		final JPanel mainPanel = new JPanel();
		mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.LINE_AXIS));
		String localtitle = "Average along X and Y";
		mainChartFrame = GuiUtil.generateTitleFrame(localtitle, new JPanel(), new Dimension(1400, 800), true, true,
				true, true);

		int totalpoints = 0;
		ArrayList<XYSeriesCollection> xyDataSetList = new ArrayList<XYSeriesCollection>();
		XYSeriesCollection xyDataset = graphCreateXYDataSet(arrayX, "X chan ");
		xyDataSetList.add(xyDataset);
		totalpoints += xyDataset.getSeries(0).getItemCount();
		xyDataset = graphCreateXYDataSet(arrayY, "Y chan ");
		xyDataSetList.add(xyDataset);
		totalpoints += xyDataset.getSeries(0).getItemCount();

		for (int i = 0; i < xyDataSetList.size(); i++) {
			xyDataset = xyDataSetList.get(i);
			int npoints = xyDataset.getSeries(0).getItemCount();
			JFreeChart xyChart = ChartFactory.createXYLineChart(null, null, null, xyDataset, PlotOrientation.VERTICAL,
					true, true, true);
			xyChart.setAntiAlias(true);
			xyChart.setTextAntiAlias(true);
			int drawWidth = npoints * 800 / totalpoints;
			int drawHeight = 400;
			ChartPanel xyChartPanel = new ChartPanel(xyChart, drawWidth, drawHeight, drawWidth, drawHeight, drawWidth,
					drawHeight, false, false, true, true, true, true);
			mainPanel.add(xyChartPanel);
		}

		mainChartFrame.add(mainPanel);
		mainChartFrame.pack();
		if (pt == null) {
			SequenceKymos seqKymos = exp.getSeqKymos();
			Viewer v = seqKymos.getSequence().getFirstViewer();
			Rectangle rectv = v.getBounds();
			pt = new Point((int) rectv.getX(), (int) rectv.getY() + 30);
		}
		mainChartFrame.setLocation(pt);

		mainChartFrame.setVisible(true);
		mainChartFrame.addToDesktopPane();
		mainChartFrame.requestFocus();
	}

	private XYSeriesCollection graphCreateXYDataSet(double[][] array, String rootName) {
		XYSeriesCollection xyDataset = new XYSeriesCollection();
		for (int chan = 0; chan < 4; chan++) {
			XYSeries seriesXY = new XYSeries(rootName + chan);
			if (chan == 3)
				seriesXY.setDescription("1-2 + 3-2");
			int len = array.length;
			for (int i = 0; i < len; i++) {
				double value = array[i][chan];
				seriesXY.add(i, value);
			}
			xyDataset.addSeries(seriesXY);
		}
		return xyDataset;
	}
}
