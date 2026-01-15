package plugins.fmp.multiSPOTS.dlg.cages;

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
import icy.roi.ROI;
import icy.roi.ROI2D;
import icy.type.DataType;
import icy.type.geom.Polygon2D;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multiSPOTS.experiment.Experiment;
import plugins.fmp.multiSPOTS.experiment.SequenceCamData;
import plugins.fmp.multiSPOTS.experiment.spots.Spot;
import plugins.fmp.multiSPOTS.tools.ROI2D.ROIUtilities;
import plugins.fmp.multiSPOTS.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multiSPOTS.tools.overlay.OverlayThreshold;
import plugins.fmp.multiSPOTS.tools.polyline.Blobs;
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
	JComboBox<ImageTransformEnums> transformForLevelsComboBox = new JComboBox<ImageTransformEnums>(
			new ImageTransformEnums[] { ImageTransformEnums.R_RGB, ImageTransformEnums.G_RGB, ImageTransformEnums.B_RGB,
					ImageTransformEnums.R2MINUS_GB, ImageTransformEnums.G2MINUS_RB, ImageTransformEnums.B2MINUS_RG,
					ImageTransformEnums.RGB, ImageTransformEnums.GBMINUS_2R, ImageTransformEnums.RBMINUS_2G,
					ImageTransformEnums.RGMINUS_2B, ImageTransformEnums.H_HSB, ImageTransformEnums.S_HSB,
					ImageTransformEnums.B_HSB });
	private OverlayThreshold overlayThreshold = null;
	private MultiSPOTS parent0 = null;
	private ROI2DPolygon userPolygon = null;

	void init(GridLayout capLayout, MultiSPOTS parent0) {
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
				Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
				if (exp != null)
					create2DPolygon(exp);
			}
		});

		createCagesButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
				if (exp != null) {
					exp.seqCamData.seq.removeROIs(ROIUtilities.getROIsContainingString("cage", exp.seqCamData.seq),
							false);
					exp.getCages().removeCages();
					createROIsFromSelectedPolygonAndSpots(exp);
					exp.getCages().transferROIsFromSequenceToCages(exp.seqCamData.seq);
					if (exp.spotsArray.spotsList.size() > 0)
						exp.getCages().transferNFliesFromSpotsToCages(exp.spotsArray);
				}
			}
		});

		transformForLevelsComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
				if (exp != null)
					updateOverlay(exp);
			}
		});

		deleteButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
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
		SequenceCamData seqCamData = exp.seqCamData;
		if (seqCamData == null)
			return;
		if (overlayThreshold == null) {
			overlayThreshold = new OverlayThreshold(seqCamData.seq);
			seqCamData.seq.addOverlay(overlayThreshold);
		} else {
			seqCamData.seq.removeOverlay(overlayThreshold);
			overlayThreshold.setSequence(seqCamData.seq);
			seqCamData.seq.addOverlay(overlayThreshold);
		}
		exp.getCages().detect_threshold = (int) thresholdSpinner.getValue();
		overlayThreshold.setThresholdTransform(exp.getCages().detect_threshold,
				(ImageTransformEnums) transformForLevelsComboBox.getSelectedItem(), false);
		seqCamData.seq.overlayChanged(overlayThreshold);
		seqCamData.seq.dataChanged();
	}

	public void removeOverlay(Experiment exp) {
		if (exp.seqCamData != null && exp.seqCamData.seq != null)
			exp.seqCamData.seq.removeOverlay(overlayThreshold);
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		if (e.getSource() == thresholdSpinner) {
			Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
			if (exp != null)
				updateOverlay(exp);
		} else if (e.getSource() == overlayCheckBox) {
			Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
			if (exp != null) {
				if (overlayCheckBox.isSelected()) {
					if (overlayThreshold == null)
						overlayThreshold = new OverlayThreshold(exp.seqCamData.seq);
					exp.seqCamData.seq.addOverlay(overlayThreshold);
					updateOverlay(exp);
				} else
					removeOverlay(exp);
			}
		}
	}

	private void createROIsFromSelectedPolygonAndSpots(Experiment exp) {
		exp.seqCamData.seq.removeROIs(ROIUtilities.getROIsContainingString("cage", exp.seqCamData.seq), false);
		exp.getCages().removeCages();

		int t = exp.seqCamData.currentFrame;
		IcyBufferedImage img0 = IcyBufferedImageUtil.convertToType(overlayThreshold.getTransformedImage(t),
				DataType.INT, false);

		Rectangle rectGrid = new Rectangle(0, 0, img0.getSizeX(), img0.getSizeY());
		if (userPolygon != null) {
			rectGrid = userPolygon.getBounds();
			exp.seqCamData.seq.removeROI(userPolygon);
		}
		IcyBufferedImage subImg0 = IcyBufferedImageUtil.getSubImage(img0, rectGrid);

		Blobs blobs = new Blobs(subImg0);
		blobs.getPixelsConnected();
		blobs.getBlobsConnected();
		blobs.fillBlanksPixelsWithinBlobs();

		List<Integer> blobsfound = new ArrayList<Integer>();
		for (Spot spot : exp.spotsArray.spotsList) {
			Point2D pt = spot.getSpotCenter();
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
					int cagenb = spot.cageID;
					roiP.setName("cage" + String.format("%03d", cagenb));
					spot.cageID = cagenb;
					exp.seqCamData.seq.addROI(roiP);
				}
			}
		}
	}

	void deletePointsIncluded(Experiment exp) throws InterruptedException {
		SequenceCamData seqCamData = exp.seqCamData;
		ROI2D roiSnip = seqCamData.seq.getSelectedROI2D();
		if (roiSnip == null)
			return;

		List<ROI> roiList = ROIUtilities.getROIsContainingString("cage", seqCamData.seq);
		for (ROI cageRoi : roiList) {
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

	// TODO: same routine in BuildCagesAsArray
	private void create2DPolygon(Experiment exp) {
		final String dummyname = "perimeter_enclosing";
		ArrayList<ROI2D> listRois = exp.seqCamData.seq.getROI2Ds();
		for (ROI2D roi : listRois) {
			if (roi.getName().equals(dummyname))
				return;
		}

		Polygon2D polygon = null;
		if (exp.spotsArray.spotsList.size() > 0) {
			polygon = exp.spotsArray.get2DPolygonEnclosingSpots();
		} else {
			Rectangle rect = exp.seqCamData.seq.getBounds2D();
			List<Point2D> points = new ArrayList<Point2D>();
			int rectleft = rect.x + rect.width / 6;
			int rectright = rect.x + rect.width * 5 / 6;
			int recttop = rect.y + rect.height * 2 / 3;
			points.add(new Point2D.Double(rectleft, recttop));
			points.add(new Point2D.Double(rectright, recttop));
			points.add(new Point2D.Double(rectright, rect.y + rect.height - 4));
			points.add(new Point2D.Double(rectleft, rect.y + rect.height - 4));
			polygon = new Polygon2D(points);
		}
		ROI2DPolygon roi = new ROI2DPolygon(polygon);
		roi.setName(dummyname);
		exp.seqCamData.seq.addROI(roi);
		exp.seqCamData.seq.setSelectedROI(roi);
	}

}
