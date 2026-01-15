package plugins.fmp.multicafe.dlg.levels;

import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import icy.gui.util.GuiUtil;
import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.sequence.SequenceKymos;

public class Filter extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -4413321640245046423L;
	private MultiCAFE parent0;
	private JButton startButton = new JButton("Start");
	private JTextField spanText = new JTextField("10");

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;
		add(GuiUtil.besidesPanel(new JLabel("use N pixels="), spanText, new JLabel(" ")));
		add(GuiUtil.besidesPanel(new JLabel(" "), new JLabel(" "), startButton));
		defineActionListeners();
	}

	private void defineActionListeners() {
		startButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					SequenceKymos seqKymos = exp.getSeqKymos();
					int span = getSpan();
					int c = 1;
					for (int t = 0; t < seqKymos.getSequence().getSizeT(); t++)
						crossCorrelatePixels(seqKymos, t, span, c);
				}
			}
		});
	}

	int getSpan() {
		return Integer.valueOf(spanText.getText());
	}

	private void crossCorrelatePixels(SequenceKymos kymo, int span, int t, int c) {
		IcyBufferedImage image = null;
		image = kymo.getSequence().getImage(t, 0, c);
		double[] tabValues = Array1DUtil.arrayToDoubleArray(image.getDataXY(0), image.isSignedDataType());
		int xwidth = image.getSizeX();
		int yheight = image.getSizeY();
		double[] col0 = new double[yheight];
		double[] col1 = new double[yheight];
		int npoints = 2 * span;
		int len = yheight - npoints;
		int startx = span;
		double[] correl = new double[npoints];
		int[] ishift = new int[xwidth];
		ishift[0] = 0;
		for (int iy = 0; iy < yheight; iy++) {
			col0[iy] = tabValues[iy * xwidth];
		}

		for (int ix = 1; ix < xwidth; ix++) {
			for (int iy = 0; iy < yheight; iy++) {
				col1[iy] = tabValues[ix + iy * xwidth];
			}
			for (int starty = 0; starty < npoints; starty++) {
				correl[starty] = correlationBetween2Arrays(col0, col1, startx, starty, len);
			}
			int imax = 0;
			double vmax = correl[0];
			for (int i = 1; i < npoints; i++) {
				if (correl[i] > vmax) {
					vmax = correl[i];
					imax = i;
				}
			}
			ishift[ix] = span - imax;
		}
		shiftColumnsOfPixels(ishift, kymo);
	}

	private void shiftColumnsOfPixels(int[] shift, SequenceKymos kymographSeq) {
		IcyBufferedImage image0 = kymographSeq.getSequence().getFirstImage();
		IcyBufferedImage image1 = IcyBufferedImageUtil.getCopy(image0);
		int xwidth = image0.getSizeX();
		int yheight = image0.getSizeY();

		for (int chan = 0; chan < kymographSeq.getSequence().getSizeC(); chan++) {
			Object dataObject0 = image0.getDataXY(chan);
			double[] dataArray0 = Array1DUtil.arrayToDoubleArray(dataObject0, image0.isSignedDataType());
			Object dataObject1 = image1.getDataXY(chan);
			double[] dataArray1 = Array1DUtil.arrayToDoubleArray(dataObject1, image1.isSignedDataType());
			for (int ix = 0; ix < xwidth; ix++) {
				int iydest = shift[ix];
				for (int iy = 0; iy < yheight; iy++, iydest++) {
					if (iydest >= 0 && iydest < yheight)
						dataArray0[ix + iydest * xwidth] = dataArray1[ix + iy * xwidth];
				}
			}
			Array1DUtil.doubleArrayToSafeArray(dataArray0, dataObject0, image0.isSignedDataType());
			image0.setDataXY(chan, dataObject0);
		}
	}

	private double correlationBetween2Arrays(double[] xs, double[] ys, int startx, int starty, int len) {
		double sx = 0.0;
		double sy = 0.0;
		double sxx = 0.0;
		double syy = 0.0;
		double sxy = 0.0;

		for (int i = 0; i < len; ++i, startx++, starty++) {
			double x = xs[startx];
			double y = ys[starty];

			sx += x;
			sy += y;
			sxx += x * x;
			syy += y * y;
			sxy += x * y;
		}

		// covariation
		double cov = sxy / len - sx * sy / len / len;
		// standard error of x
		double sigmax = Math.sqrt(sxx / len - sx * sx / len / len);
		// standard error of y
		double sigmay = Math.sqrt(syy / len - sy * sy / len / len);

		// correlation is just a normalized covariation
		return cov / sigmax / sigmay;
	}
}
