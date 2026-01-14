package plugins.fmp.multicafe.dlg.cages;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;

import icy.gui.frame.progress.AnnounceFrame;
import icy.roi.ROI2D;
import icy.type.geom.Polygon2D;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.fmp_experiment.Experiment;
import plugins.fmp.multitools.fmp_experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.fmp_tools.ROI2D.ROI2DUtilities;
import plugins.kernel.roi.roi2d.ROI2DPolygon;

public class BuildCagesAsArray extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -5257698990389571518L;
	private JButton drawPolygon2DButton = new JButton("Draw Polygon2D");
	private JButton createCellsButton = new JButton("Create cages");
	private JSpinner nColumnsTextField = new JSpinner(new SpinnerNumberModel(10, 0, 10000, 1));
	private JSpinner width_cageTextField = new JSpinner(new SpinnerNumberModel(20, 0, 10000, 1));
	private JSpinner width_intervalTextField = new JSpinner(new SpinnerNumberModel(3, 0, 10000, 1));
	private JSpinner nRowsTextField = new JSpinner(new SpinnerNumberModel(1, 0, 10000, 1));
	private int ncolumns = 10;
	private int nrows = 1;
	private int width_cage = 10;
	private int width_interval = 2;
	private MultiCAFE parent0 = null;
	private ROI2DPolygon roiUserPolygon = null;

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);
		flowLayout.setVgap(0);

		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(drawPolygon2DButton);
		panel1.add(createCellsButton);
		add(panel1);

		JLabel nColumnsLabel = new JLabel("N columns ");
		JLabel nRowsLabel = new JLabel("N rows ");
		JLabel cageWidthLabel = new JLabel("cage width ");
		JLabel betweenCellsLabel = new JLabel("between cages ");
		nColumnsLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		cageWidthLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		betweenCellsLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		nRowsLabel.setHorizontalAlignment(SwingConstants.RIGHT);

		JPanel panel2 = new JPanel(flowLayout);
		panel2.add(cageWidthLabel);
		panel2.add(width_cageTextField);
		panel2.add(nColumnsLabel);
		panel2.add(nColumnsTextField);
		add(panel2);

		JPanel panel3 = new JPanel(flowLayout);
		panel3.add(betweenCellsLabel);
		panel3.add(width_intervalTextField);
		panel3.add(nRowsLabel);
		panel3.add(nRowsTextField);
		add(panel3);

		defineActionListeners();
	}

	private void defineActionListeners() {
		drawPolygon2DButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					create2DPolygon(exp);
			}
		});

		createCellsButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					createROIsFromSelectedPolygon(exp);
					exp.getCages().updateCagesFromSequence(exp.getSeqCamData());
					if (exp.getCapillaries().getList().size() > 0)
						exp.getCages().transferNFliesFromCapillariesToCageBox(exp.getCapillaries().getList());
				}
			}
		});
	}

	void updateNColumnsFieldFromSequence() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null) {
			int nrois = exp.getCages().getCageList().size();
			if (nrois > 0) {
				nColumnsTextField.setValue(nrois);
				ncolumns = nrois;
			}
		}
	}

	private void create2DPolygon(Experiment exp) {
		final String dummyname = "perimeter_enclosing";
		if (roiUserPolygon == null) {
			ArrayList<ROI2D> listRois = exp.getSeqCamData().getSequence().getROI2Ds();
			for (ROI2D roi : listRois) {
				if (roi.getName().equals(dummyname))
					return;
			}

			Rectangle rect = exp.getSeqCamData().getSequence().getBounds2D();
			List<Point2D> points = new ArrayList<Point2D>();
			int rectleft = rect.x + rect.width / 6;
			int rectright = rect.x + rect.width * 5 / 6;
			int recttop = rect.y + rect.height * 2 / 3;
			if (exp.getCapillaries().getList().size() > 0) {
				Rectangle bound0 = exp.getCapillaries().getList().get(0).getRoi().getBounds();
				int last = exp.getCapillaries().getList().size() - 1;
				Rectangle bound1 = exp.getCapillaries().getList().get(last).getRoi().getBounds();
				rectleft = bound0.x;
				rectright = bound1.x + bound1.width;
				int diff = (rectright - rectleft) * 2 / 60;
				rectleft -= diff;
				rectright += diff;
				recttop = bound0.y + bound0.height - (bound0.height / 8);
			}

			points.add(new Point2D.Double(rectleft, recttop));
			points.add(new Point2D.Double(rectright, recttop));
			points.add(new Point2D.Double(rectright, rect.y + rect.height - 4));
			points.add(new Point2D.Double(rectleft, rect.y + rect.height - 4));
			roiUserPolygon = new ROI2DPolygon(points);
			roiUserPolygon.setName(dummyname);
		}
		exp.getSeqCamData().getSequence().addROI(roiUserPolygon);
		exp.getSeqCamData().getSequence().setSelectedROI(roiUserPolygon);
	}

	private void createROIsFromSelectedPolygon(Experiment exp) {
		ROI2DUtilities.removeRoisContainingStringAtT(-1, "cage", exp.getSeqCamData().getSequence());
//		exp.getCages().getCageList().clear();

		// read values from text boxes
		try {
			ncolumns = (int) nColumnsTextField.getValue();
			nrows = (int) nRowsTextField.getValue();
			width_cage = (int) width_cageTextField.getValue();
			width_interval = (int) width_intervalTextField.getValue();
		} catch (Exception e) {
			new AnnounceFrame("Can't interpret one of the ROI parameters value");
		}

		SequenceCamData seqCamData = exp.getSeqCamData();
		Polygon2D roiPolygonMin = ROI2DUtilities.orderVerticesofPolygon(roiUserPolygon.getPolygon());
		seqCamData.getSequence().removeROI(roiUserPolygon);

		// generate cage frames
		ROI2DUtilities.removeRoisContainingStringAtT(-1, "cage", exp.getSeqCamData().getSequence());
		exp.getCages().getCageList().clear();
		String cageRoot = "cage";
		int iRoot = 0;

		Polygon2D roiPolygon = ROI2DUtilities.inflate(roiPolygonMin, ncolumns, nrows, width_cage, width_interval);

		double deltax_top = (roiPolygon.xpoints[3] - roiPolygon.xpoints[0]) / ncolumns;
		double deltax_bottom = (roiPolygon.xpoints[2] - roiPolygon.xpoints[1]) / ncolumns;
		double deltay_top = (roiPolygon.ypoints[3] - roiPolygon.ypoints[0]) / ncolumns;
		double deltay_bottom = (roiPolygon.ypoints[2] - roiPolygon.ypoints[1]) / ncolumns;

		for (int i = 0; i < ncolumns; i++) {
			double x0i = roiPolygon.xpoints[0] + deltax_top * i;
			double x1i = roiPolygon.xpoints[1] + deltax_bottom * i;
			double x3i = x0i + deltax_top;
			double x2i = x1i + deltax_bottom;

			double y0i = roiPolygon.ypoints[0] + deltay_top * i;
			double y1i = roiPolygon.ypoints[1] + deltay_bottom * i;
			double y3i = y0i + deltay_top;
			double y2i = y1i + deltay_bottom;

			for (int j = 0; j < nrows; j++) {
				double deltax_left = (x1i - x0i) / nrows;
				double deltax_right = (x2i - x3i) / nrows;
				double deltay_left = (y1i - y0i) / nrows;
				double deltay_right = (y2i - y3i) / nrows;

				double x0ij = x0i + deltax_left * j;
				double x1ij = x0ij + deltax_left;
				double x3ij = x3i + deltax_right * j;
				double x2ij = x3ij + deltax_right;

				double y0ij = y0i + deltay_left * j;
				double y1ij = y0ij + deltay_left;
				double y3ij = y3i + deltay_right * j;
				double y2ij = y3ij + deltay_right;

				// shrink by
				double xspacer_top = (x3ij - x0ij) * width_interval / (width_cage + 2 * width_interval);
				double xspacer_bottom = (x2ij - x1ij) * width_interval / (width_cage + 2 * width_interval);
				double yspacer_left = (y1ij - y0ij) * width_interval / (width_cage + 2 * width_interval);
				double yspacer_right = (y2ij - y3ij) * width_interval / (width_cage + 2 * width_interval);

				// define intersection
				List<Point2D> points = new ArrayList<>();

				Point2D point0 = ROI2DUtilities.lineIntersect(x0ij + xspacer_top, y0ij, x1ij + xspacer_bottom, y1ij,
						x0ij, y0ij + yspacer_left, x3ij, y3ij + yspacer_right);
				points.add(point0);

				Point2D point1 = ROI2DUtilities.lineIntersect(x1ij, y1ij - yspacer_left, x2ij, y2ij - yspacer_right,
						x0ij + xspacer_top, y0ij, x1ij + xspacer_bottom, y1ij);
				points.add(point1);

				Point2D point2 = ROI2DUtilities.lineIntersect(x1ij, y1ij - yspacer_left, x2ij, y2ij - yspacer_right,
						x3ij - xspacer_top, y3ij, x2ij - xspacer_bottom, y2ij);
				points.add(point2);

				Point2D point3 = ROI2DUtilities.lineIntersect(x0ij, y0ij + yspacer_left, x3ij, y3ij + yspacer_right,
						x3ij - xspacer_top, y3ij, x2ij - xspacer_bottom, y2ij);
				points.add(point3);

				ROI2DPolygon roiP = new ROI2DPolygon(points);
				roiP.setName(cageRoot + String.format("%03d", iRoot));
				roiP.setColor(Color.MAGENTA);
				iRoot++;
				seqCamData.getSequence().addROI(roiP);
			}
		}
	}

}
