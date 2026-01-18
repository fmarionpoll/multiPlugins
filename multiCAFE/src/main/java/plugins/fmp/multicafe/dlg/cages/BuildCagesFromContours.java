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
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;
import icy.roi.ROI2D;
import icy.type.DataType;
import icy.type.geom.Polygon2D;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillaries.capillary.Capillary;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.tools.ROI2D.ROI2DUtilities;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.overlay.OverlayThreshold;
import plugins.fmp.multitools.tools.polyline.Blobs;
import plugins.kernel.roi.roi2d.ROI2DPolygon;

public class BuildCagesFromContours extends JPanel implements ChangeListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = -121724000730795396L;
	private JButton drawPolygon2DButton = new JButton("Draw Polygon2D");
	private JButton createCagesButton = new JButton("Create cages");
	private JSpinner thresholdSpinner = new JSpinner(new SpinnerNumberModel(60, 0, 10000, 1));
	public JCheckBox overlayCheckBox = new JCheckBox("Overlay ", false);
	private JButton deleteButton = new JButton("Cut points within selected polygon");
	private JComboBox<ImageTransformEnums> transformForLevelsComboBox = new JComboBox<ImageTransformEnums>(
			new ImageTransformEnums[] { ImageTransformEnums.R_RGB, ImageTransformEnums.G_RGB, ImageTransformEnums.B_RGB,
					ImageTransformEnums.R2MINUS_GB, ImageTransformEnums.G2MINUS_RB, ImageTransformEnums.B2MINUS_RG,
					ImageTransformEnums.RGB, ImageTransformEnums.GBMINUS_2R, ImageTransformEnums.RBMINUS_2G,
					ImageTransformEnums.RGMINUS_2B, ImageTransformEnums.H_HSB, ImageTransformEnums.S_HSB,
					ImageTransformEnums.B_HSB });
	private OverlayThreshold overlayThreshold = null;
	private MultiCAFE parent0 = null;
	private ROI2DPolygon userPolygon = null;

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);
		flowLayout.setVgap(0);

		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(drawPolygon2DButton);
		panel1.add(createCagesButton);
		add(panel1);

		JLabel videochannel = new JLabel("detect from ");
		videochannel.setHorizontalAlignment(SwingConstants.RIGHT);
		transformForLevelsComboBox.setSelectedIndex(2);
		JPanel panel2 = new JPanel(flowLayout);
		panel2.add(videochannel);
		panel2.add(transformForLevelsComboBox);
		panel2.add(overlayCheckBox);
		panel2.add(thresholdSpinner);
		add(panel2);

		JPanel panel3 = new JPanel(flowLayout);
		panel3.add(deleteButton);
		add(panel3);

		defineActionListeners();
		thresholdSpinner.addChangeListener(this);
		overlayCheckBox.addChangeListener(this);
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

		createCagesButton.addActionListener(new ActionListener() {
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

		transformForLevelsComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					updateOverlay(exp);
			}
		});

		deleteButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					try {
						deletePointsIncluded(exp);
					} catch (InterruptedException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
			}
		});
	}

	public void updateOverlay(Experiment exp) {
		SequenceCamData seqCamData = exp.getSeqCamData();
		if (seqCamData == null)
			return;
		if (overlayThreshold == null) {
			overlayThreshold = new OverlayThreshold(seqCamData.getSequence());
			seqCamData.getSequence().addOverlay(overlayThreshold);
		} else {
			seqCamData.getSequence().removeOverlay(overlayThreshold);
			overlayThreshold.setSequence(seqCamData.getSequence());
			seqCamData.getSequence().addOverlay(overlayThreshold);
		}
		exp.getCages().setDetect_threshold((int) thresholdSpinner.getValue());
		overlayThreshold.setThresholdTransform(exp.getCages().getDetect_threshold(),
				(ImageTransformEnums) transformForLevelsComboBox.getSelectedItem(), false);
		seqCamData.getSequence().overlayChanged(overlayThreshold);
		seqCamData.getSequence().dataChanged();
	}

	public void removeOverlay(Experiment exp) {
		if (exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null)
			exp.getSeqCamData().getSequence().removeOverlay(overlayThreshold);
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		if (e.getSource() == thresholdSpinner) {
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null)
				updateOverlay(exp);
		} else if (e.getSource() == overlayCheckBox) {
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null) {
				if (overlayCheckBox.isSelected()) {
					if (overlayThreshold == null)
						overlayThreshold = new OverlayThreshold(exp.getSeqCamData().getSequence());
					exp.getSeqCamData().getSequence().addOverlay(overlayThreshold);
					updateOverlay(exp);
				} else
					removeOverlay(exp);
			}
		}
	}

	private void createROIsFromSelectedPolygon(Experiment exp) {
		ROI2DUtilities.removeRoisContainingStringAtT(-1, "cage", exp.getSeqCamData().getSequence());
//		exp.getCages().getCageList().clear();

		// Note: This method only creates cages for existing capillaries.
		// It uses capillaries as seed points for blob detection, so cages cannot
		// be detected for missing capillaries. If some capillaries are missing,
		// use BuildCagesAsArray instead, which creates all cages in a grid pattern.
		// The graph layout has been updated to handle missing cages properly.

		int t = exp.getSeqCamData().getCurrentFrame();
		IcyBufferedImage img0 = IcyBufferedImageUtil.convertToType(overlayThreshold.getTransformedImage(t),
				DataType.INT, false);

		Rectangle rectGrid = new Rectangle(0, 0, img0.getSizeX(), img0.getSizeY());
		if (userPolygon != null) {
			rectGrid = userPolygon.getBounds();
			exp.getSeqCamData().getSequence().removeROI(userPolygon);
		}
		IcyBufferedImage subImg0 = IcyBufferedImageUtil.getSubImage(img0, rectGrid);

		Blobs blobs = new Blobs(subImg0);
		blobs.getPixelsConnected();
		blobs.getBlobsConnected();
		blobs.fillBlanksPixelsWithinBlobs();

		List<Integer> blobsfound = new ArrayList<Integer>();
		for (Capillary cap : exp.getCapillaries().getList()) {
			Point2D pt = cap.getCapillaryROILowestPoint();
			if (pt != null) {
				int ix = (int) (pt.getX() - rectGrid.x);
				int iy = (int) (pt.getY() - rectGrid.y);
				int blobi = blobs.getBlobAt(ix, iy);
				boolean found = false;
				for (int i : blobsfound) {
					if (i == blobi) {
						found = true;
						break;
					}
				}
				if (!found) {
					blobsfound.add(blobi);
					ROI2DPolygon roiP = new ROI2DPolygon(blobs.getBlobPolygon2D(blobi));
					roiP.translate(rectGrid.x, rectGrid.y);
					int cagenb = cap.getCageIndexFromRoiName();
					roiP.setName("cage" + String.format("%03d", cagenb));
					roiP.setColor(Color.MAGENTA);
					cap.setCageID(cagenb);
					exp.getSeqCamData().getSequence().addROI(roiP);
				}
			}
		}
	}

	void deletePointsIncluded(Experiment exp) throws InterruptedException {
		SequenceCamData seqCamData = exp.getSeqCamData();
		ROI2D roiSnip = seqCamData.getSequence().getSelectedROI2D();
		if (roiSnip == null)
			return;

		List<ROI2D> roiList = seqCamData.findROIsMatchingNamePattern("cage");
		for (ROI2D cageRoi : roiList) {
			if (roiSnip.intersects(cageRoi) && cageRoi instanceof ROI2DPolygon) {
				Polygon2D oldPolygon = ((ROI2DPolygon) cageRoi).getPolygon2D();
				if (oldPolygon == null)
					continue;
				Polygon2D newPolygon = new Polygon2D();
				for (int i = 0; i < oldPolygon.npoints; i++) {
					if (roiSnip.contains(oldPolygon.xpoints[i], oldPolygon.ypoints[i]))
						continue;
					newPolygon.addPoint(oldPolygon.xpoints[i], oldPolygon.ypoints[i]);
				}
				((ROI2DPolygon) cageRoi).setPolygon2D(newPolygon);
			}
		}
	}

	private void create2DPolygon(Experiment exp) {
		final String dummyname = "perimeter_enclosing";
		if (userPolygon == null) {
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
			userPolygon = new ROI2DPolygon(points);
			userPolygon.setName(dummyname);
		}
		exp.getSeqCamData().getSequence().addROI(userPolygon);
		exp.getSeqCamData().getSequence().setSelectedROI(userPolygon);
	}

}
