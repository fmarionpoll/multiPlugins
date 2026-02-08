package plugins.fmp.multiSPOTS.dlg.spotsMeasures;

import java.awt.Color;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;

import icy.gui.util.GuiUtil;
import icy.image.IcyBufferedImage;
import icy.roi.ROI;
import icy.roi.ROI2D;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.tools.polyline.Line2DPlus;
import plugins.kernel.roi.roi2d.ROI2DLine;

public class Adjust extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1756354919434057560L;

	JSpinner jitterJSpinner = new JSpinner(new SpinnerNumberModel(10, 0, 500, 1));
	private JButton adjustButton = new JButton("Align");
	private MultiSPOTS parent0 = null;
	private Line2D refLineUpper = null;
	private Line2D refLineLower = null;
	private ROI2DLine roiRefLineUpper = new ROI2DLine();
	private ROI2DLine roiRefLineLower = new ROI2DLine();

	void init(GridLayout capLayout, MultiSPOTS parent0) {
		setLayout(capLayout);
		add(GuiUtil.besidesPanel(new JLabel(" "), new JLabel("jitter ", SwingConstants.RIGHT), jitterJSpinner,
				adjustButton));

		this.parent0 = parent0;
		defineActionListeners();
	}

	private void defineActionListeners() {
		adjustButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Object o = e.getSource();
				if (o == adjustButton)
					roisCenterLinestoAllCapillaries();
			}
		});
	}

	// -------------------------------------------------------
	private void roisCenterLinestoAllCapillaries() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;
		SequenceCamData seqCamData = exp.getSeqCamData();
		refLineUpper = roiRefLineUpper.getLine();
		refLineLower = roiRefLineLower.getLine();

		int chan = 0;
		int jitter = (int) jitterJSpinner.getValue();
		int t = seqCamData.currentFrame;
		seqCamData.getSequence().setPositionT(t);
		IcyBufferedImage vinputImage = seqCamData.getSequence().getImage(t, 0, chan);
		if (vinputImage == null) {
			System.out.println("Adjust:roisCenterLinestoAllCapillaries() An error occurred while reading image: " + t);
			return;
		}
		int xwidth = vinputImage.getSizeX();
		double[] sourceValues = Array1DUtil.arrayToDoubleArray(vinputImage.getDataXY(0),
				vinputImage.isSignedDataType());

		// loop through all lines
		List<ROI> capillaryRois = ROIUtilities.getROIsContainingString("line", seqCamData.seq);
		for (ROI roi : capillaryRois) {
			if (roi instanceof ROI2DLine) {
				Line2D line = roisCenterLinetoCapillary(sourceValues, xwidth, (ROI2DLine) roi, jitter);
				((ROI2DLine) roi).setLine(line);
			}
		}
	}

	private Line2D roisCenterLinetoCapillary(double[] sourceValues, int xwidth, ROI2DLine roi, int jitter) {

		Line2DPlus line = new Line2DPlus();
		line.setLine(roi.getLine());

		// ----------------------------------------------------------
		// upper position (according to refBar)
		if (!refLineUpper.intersectsLine(line))
			return null;

		Point2D.Double pti = line.getIntersection(refLineUpper);
		double y = pti.getY();
		double x = pti.getX();

		int lowx = (int) x - jitter;
		if (lowx < 0)
			lowx = 0;
		int ixa = (int) x;
		int iya = (int) y;
		double sumVala = 0;
		double[] arrayVala = new double[2 * jitter + 1];
		int iarray = 0;
		for (int ix = lowx; ix <= (lowx + 2 * jitter); ix++, iarray++) {
			arrayVala[iarray] = sourceValues[iya * xwidth + ix];
			sumVala += arrayVala[iarray];
		}
		double avgVala = sumVala / (double) (2 * jitter + 1);

		// find first left < avg
		int ilefta = 0;
		for (int i = 0; i < 2 * jitter; i++) {
			if (arrayVala[i] < avgVala) {
				ilefta = i;
				break;
			}
		}

		// find first right < avg
		int irighta = 2 * jitter;
		for (int i = irighta; i >= 0; i--) {
			if (arrayVala[i] < avgVala) {
				irighta = i;
				break;
			}
		}
		if (ilefta > irighta)
			return null;
		int index = (ilefta + irighta) / 2;
		ixa = lowx + index;

		// find lower position
		if (!refLineLower.intersectsLine(line))
			return null;
		pti = line.getIntersection(refLineLower);
		y = pti.getY();
		x = pti.getX();

		lowx = (int) x - jitter;
		if (lowx < 0)
			lowx = 0;
		int ixb = (int) x;
		int iyb = (int) y;

		double sumValb = 0;
		double[] arrayValb = new double[2 * jitter + 1];
		iarray = 0;
		for (int ix = lowx; ix <= (lowx + 2 * jitter); ix++, iarray++) {
			arrayValb[iarray] = sourceValues[iyb * xwidth + ix];
			sumValb += arrayValb[iarray];
		}
		double avgValb = sumValb / (double) (2 * jitter + 1);

		// find first left < avg
		int ileftb = 0;
		for (int i = 0; i < 2 * jitter; i++) {
			if (arrayValb[i] < avgValb) {
				ileftb = i;
				break;
			}
		}
		// find first right < avg
		int irightb = 2 * jitter;
		for (int i = irightb; i >= 0; i--) {
			if (arrayValb[i] < avgValb) {
				irightb = i;
				break;
			}
		}
		if (ileftb > irightb)
			return null;

		index = (ileftb + irightb) / 2;
		ixb = lowx + index;

		// store result
		double y1 = line.getY1();
		double y2 = line.getY2();
		line.x1 = (double) ixa;
		line.y1 = (double) iya;
		line.x2 = (double) ixb;
		line.y2 = (double) iyb;
		double x1 = line.getXfromY(y1);
		double x2 = line.getXfromY(y2);
		Line2D line_out = new Line2D.Double(x1, y1, x2, y2);

		return line_out;
	}

	void roisDisplayrefBar(boolean display) {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;
		SequenceCamData seqCamData = exp.getSeqCamData();
		if (seqCamData == null)
			return;

		if (display) {
			// take as ref the whole image otherwise, we won't see the lines if the use has
			// not defined any capillaries
			int seqheight = seqCamData.getSequence().getHeight();
			int seqwidth = seqCamData.getSequence().getWidth();
			refLineUpper = new Line2D.Double(0, seqheight / 3, seqwidth, seqheight / 3);
			refLineLower = new Line2D.Double(0, 2 * seqheight / 3, seqwidth, 2 * seqheight / 3);

			List<ROI> capillaryRois = ROIUtilities.getROIsContainingString("line", seqCamData.seq);
			Rectangle extRect = new Rectangle(((ROI2D) capillaryRois.get(0)).getBounds());
			for (ROI roi : capillaryRois) {
				Rectangle rect = ((ROI2D) roi).getBounds();
				extRect.add(rect);
			}
			extRect.grow(extRect.width * 1 / 10, -extRect.height * 2 / 10);
			refLineUpper.setLine(extRect.getX(), extRect.getY(), extRect.getX() + extRect.getWidth(), extRect.getY());
			refLineLower.setLine(extRect.getX(), extRect.getY() + extRect.getHeight(),
					extRect.getX() + extRect.getWidth(), extRect.getY() + extRect.getHeight());

			roiRefLineUpper.setLine(refLineUpper);
			roiRefLineLower.setLine(refLineLower);

			roiRefLineUpper.setName("refBarUpper");
			roiRefLineUpper.setColor(Color.YELLOW);
			roiRefLineLower.setName("refBarLower");
			roiRefLineLower.setColor(Color.YELLOW);

			seqCamData.getSequence().addROI(roiRefLineUpper);
			seqCamData.getSequence().addROI(roiRefLineLower);
		} else {
			seqCamData.getSequence().removeROI(roiRefLineUpper);
			seqCamData.getSequence().removeROI(roiRefLineLower);
		}
	}
}
