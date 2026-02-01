package plugins.fmp.multiSPOTS96.dlg.b_spots;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import icy.image.IcyBufferedImage;
import icy.roi.ROI;
import icy.roi.ROI2D;
import icy.sequence.Sequence;
import plugins.adufour.quickhull.QuickHull2D;
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multiSPOTS96.canvas2D.Canvas2D_3Transforms;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.ROI2D.Measures;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions;
import plugins.fmp.multitools.tools.overlay.OverlayThreshold;
import plugins.kernel.roi.roi2d.ROI2DEllipse;
import plugins.kernel.roi.roi2d.ROI2DPolygon;
import plugins.kernel.roi.roi2d.ROI2DShape;

public class ShapeSpots extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 4950182090521600937L;

	private JButton detectContoursButton = new JButton("Detect spots contours");
	private JButton restoreContoursButton = new JButton("Restore contours");
	private JCheckBox selectedSpotCheckBox = new JCheckBox("selected spots", false);

	private JButton cutAndInterpolateButton = new JButton("Cut");

	private JLabel spotsFilterLabel = new JLabel("Filter");
	ImageTransformEnums[] transforms = new ImageTransformEnums[] { ImageTransformEnums.R_RGB, ImageTransformEnums.G_RGB,
			ImageTransformEnums.B_RGB, ImageTransformEnums.R2MINUS_GB, ImageTransformEnums.G2MINUS_RB,
			ImageTransformEnums.B2MINUS_RG, ImageTransformEnums.RGB, ImageTransformEnums.GBMINUS_2R,
			ImageTransformEnums.RBMINUS_2G, ImageTransformEnums.RGMINUS_2B, ImageTransformEnums.RGB_DIFFS,
			ImageTransformEnums.H_HSB, ImageTransformEnums.S_HSB, ImageTransformEnums.B_HSB };
	private JComboBox<ImageTransformEnums> spotsTransformsComboBox = new JComboBox<ImageTransformEnums>(transforms);
	private String[] directions = new String[] { " threshold >", " threshold <" };
	private JComboBox<String> spotsDirectionComboBox = new JComboBox<String>(directions);
	private JSpinner spotsThresholdSpinner = new JSpinner(new SpinnerNumberModel(35, 0, 255, 1));
	private JToggleButton spotsViewButton = new JToggleButton("View");
	private JCheckBox spotsOverlayCheckBox = new JCheckBox("overlay");

	private OverlayThreshold overlayThreshold = null;
	private MultiSPOTS96 parent0 = null;

	void init(GridLayout gridLayout, MultiSPOTS96 parent0) {
		setLayout(gridLayout);
		this.parent0 = parent0;

		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);
		layoutLeft.setVgap(0);

		JPanel panel0 = new JPanel(layoutLeft);
		panel0.add(detectContoursButton);
		panel0.add(restoreContoursButton);
		panel0.add(selectedSpotCheckBox);
		add(panel0);

		JPanel panel1 = new JPanel(layoutLeft);
		panel1.add(spotsFilterLabel);
		panel1.add(spotsTransformsComboBox);
		panel1.add(spotsDirectionComboBox);
		panel1.add(spotsThresholdSpinner);
		panel1.add(spotsViewButton);
		panel1.add(spotsOverlayCheckBox);
		add(panel1);

		JPanel panel2 = new JPanel(layoutLeft);
		panel2.add(cutAndInterpolateButton);
		add(panel2);

		spotsTransformsComboBox.setSelectedItem(ImageTransformEnums.RGB_DIFFS);
		spotsDirectionComboBox.setSelectedIndex(1);
		declareListeners();
	}

	private void declareListeners() {
		spotsOverlayCheckBox.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					if (spotsOverlayCheckBox.isSelected()) {
						updateOverlay(exp);
						updateOverlayThreshold();
					} else {
						removeOverlay(exp);
						overlayThreshold = null;
					}
				}
			}
		});

		spotsTransformsComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					int index = spotsTransformsComboBox.getSelectedIndex();
					Canvas2D_3Transforms canvas = (Canvas2D_3Transforms) exp.getSeqCamData().getSequence()
							.getFirstViewer().getCanvas();
					updateTransformFunctionsOfCanvas(exp);
					if (!spotsViewButton.isSelected()) {
						spotsViewButton.setSelected(true);
					}
					canvas.setTransformStep1Index(index + 1);
					updateOverlayThreshold();
				}
			}
		});

		spotsDirectionComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				updateOverlayThreshold();
			}
		});

		spotsThresholdSpinner.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				updateOverlayThreshold();
			}
		});

		spotsViewButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					displayTransform(exp);
			}
		});

		detectContoursButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					exp.getSeqCamData().removeROIsContainingString("_mask");
					detectContours(exp);
					exp.saveSpots_File();
				}
			}
		});

		restoreContoursButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					restoreContours(exp);
				}
			}
		});

		cutAndInterpolateButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					cutAndInterpolate(exp);
					exp.saveSpots_File();
				}
			}
		});
	}

	void updateOverlay(Experiment exp) {
		if (overlayThreshold == null)
			overlayThreshold = new OverlayThreshold(exp.getSeqCamData().getSequence());
		else {
			exp.getSeqCamData().getSequence().removeOverlay(overlayThreshold);
			overlayThreshold.setSequence(exp.getSeqCamData().getSequence());
		}
		exp.getSeqCamData().getSequence().addOverlay(overlayThreshold);
	}

	void removeOverlay(Experiment exp) {
		if (exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null)
			exp.getSeqCamData().getSequence().removeOverlay(overlayThreshold);
	}

	void updateOverlayThreshold() {
		if (!spotsOverlayCheckBox.isSelected())
			return;

		if (overlayThreshold == null) {
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null)
				updateOverlay(exp);
		}

		boolean ifGreater = (spotsDirectionComboBox.getSelectedIndex() == 0);
		int threshold = (int) spotsThresholdSpinner.getValue();
		ImageTransformEnums transform = (ImageTransformEnums) spotsTransformsComboBox.getSelectedItem();
		overlayThreshold.setThresholdSingle(threshold, transform, ifGreater);
		overlayThreshold.painterChanged();
	}

	private BuildSeriesOptions initDetectOptions(Experiment exp) {
		BuildSeriesOptions options = new BuildSeriesOptions();

		// list of stack experiments
		options.expList = parent0.expListComboLazy;
		options.expList.index0 = parent0.expListComboLazy.getSelectedIndex();
		options.expList.index1 = parent0.expListComboLazy.getSelectedIndex();
		options.detectAllSeries = false;
		options.seriesFirst = 0;

		// other parameters
		options.transform01 = (ImageTransformEnums) spotsTransformsComboBox.getSelectedItem();
		options.spotThresholdUp = (spotsDirectionComboBox.getSelectedIndex() == 0);
		options.spotThreshold = (int) spotsThresholdSpinner.getValue();
		options.analyzePartOnly = false; // fromCheckBox.isSelected();

		options.overlayTransform = (ImageTransformEnums) spotsTransformsComboBox.getSelectedItem();
		options.overlayIfGreater = (spotsDirectionComboBox.getSelectedIndex() == 0);
		options.overlayThreshold = (int) spotsThresholdSpinner.getValue();

		options.detectSelectedROIs = selectedSpotCheckBox.isSelected();

		return options;
	}

	private void displayTransform(Experiment exp) {
		boolean displayCheckOverlay = false;
		if (spotsViewButton.isSelected()) {
			updateTransformFunctionsOfCanvas(exp);
			displayCheckOverlay = true;
		} else {
			removeOverlay(exp);
			spotsOverlayCheckBox.setSelected(false);
			Canvas2D_3Transforms canvas = (Canvas2D_3Transforms) exp.getSeqCamData().getSequence().getFirstViewer()
					.getCanvas();
			canvas.setTransformStep1Index(0);
		}
		spotsOverlayCheckBox.setEnabled(displayCheckOverlay);
	}

	private void updateTransformFunctionsOfCanvas(Experiment exp) {
		Canvas2D_3Transforms canvas = (Canvas2D_3Transforms) exp.getSeqCamData().getSequence().getFirstViewer()
				.getCanvas();
		if (canvas.getTransformStep1ItemCount() < (spotsTransformsComboBox.getItemCount() + 1)) {
			canvas.updateTransformsStep1(transforms);
		}
		int index = spotsTransformsComboBox.getSelectedIndex();
		canvas.setTransformStep1(index + 1, null);
	}

	private void detectContours(Experiment exp) {
		BuildSeriesOptions options = initDetectOptions(exp);
		CanvasImageTransformOptions transformOptions = new CanvasImageTransformOptions();
		transformOptions.transformOption = options.transform01;
		transformOptions.setSingleThreshold(options.spotThreshold, options.spotThresholdUp);
		ImageTransformInterface transformFunction = options.transform01.getFunction();

		Sequence seq = exp.getSeqCamData().getSequence();
		int t = seq.getFirstViewer().getPositionT();

		IcyBufferedImage sourceImage = seq.getImage(t, 0);
		IcyBufferedImage workImage = transformFunction.getTransformedImage(sourceImage, transformOptions);
		boolean detectSelectedROIs = selectedSpotCheckBox.isSelected();
		Spots allSpots = exp.getSpots();

		for (Cage cage : exp.getCages().cagesList) {
			for (Spot spot : cage.getSpotList(allSpots)) {
				ROI2D roi_in = spot.getRoi();
				if (detectSelectedROIs && !roi_in.isSelected())
					continue;

				exp.getSeqCamData().getSequence().removeROI(roi_in);
				try {
					spot.setMask2DSpot(spot.getRoi().getBooleanMask2D(0, 0, 1, true));
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				ROI2DPolygon roi0 = Measures.getContourOfDetectedSpot(workImage, spot, options);
				if (roi0 != null) {
					List<Point2D> listPoints = QuickHull2D.computeConvexEnvelope(((ROI2DShape) roi0).getPoints());
					ROI2DPolygon roi_new = new ROI2DPolygon(listPoints);

					roi_new.setName(spot.getRoi().getName());
					roi_new.setColor(spot.getRoi().getColor());
					spot.setRoi(roi_new);
				}
				exp.getSeqCamData().getSequence().addROI(spot.getRoi());
			}
		}
	}

	private void restoreContours(Experiment exp) {
		boolean detectSelectedROIs = selectedSpotCheckBox.isSelected();
		Spots allSpots = exp.getSpots();

		for (Cage cage : exp.getCages().cagesList) {
			for (Spot spot : cage.getSpotList(allSpots)) {
				ROI2D roi_in = spot.getRoi();
				if (detectSelectedROIs && !roi_in.isSelected())
					continue;

				String roiName = roi_in.getName();
				exp.getSeqCamData().getSequence().removeROI(roi_in);
				Point2D point = new Point2D.Double(spot.getProperties().getSpotXCoord(),
						spot.getProperties().getSpotYCoord());
				double x = point.getX() - spot.getProperties().getSpotRadius();
				double y = point.getY() - spot.getProperties().getSpotRadius();
				Ellipse2D ellipse = new Ellipse2D.Double(x, y, 2 * spot.getProperties().getSpotRadius(),
						2 * spot.getProperties().getSpotRadius());
				ROI2DEllipse roiEllipse = new ROI2DEllipse(ellipse);
				roiEllipse.setName(roiName);
				spot.setRoi(roiEllipse);
				exp.getSeqCamData().getSequence().addROI(spot.getRoi());
			}
		}
	}

	private void replaceRoi(Experiment exp, Spot spot, ROI2D roi_old, ROI2D roi_new) {
		exp.getSeqCamData().getSequence().removeROI(roi_new);
		exp.getSeqCamData().getSequence().removeROI(roi_old);
		roi_new.setName(roi_old.getName());
		roi_new.setColor(roi_old.getColor());
		spot.setRoi((ROI2DShape) roi_new);
		try {
			spot.getProperties().setSpotNPixels((int) roi_new.getNumberOfPoints());
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		exp.getSeqCamData().getSequence().addROI(roi_new);
	}

	void cutAndInterpolate(Experiment exp) {
		ROI2D roi = exp.getSeqCamData().getSequence().getSelectedROI2D();
		if (roi == null)
			return;

		Spots allSpots = exp.getSpots();
		for (Cage cage : exp.getCages().cagesList) {
			for (Spot spot : cage.getSpotList(allSpots)) {
				ROI2D spotRoi = spot.getRoi();
				try {
					if (!spotRoi.intersects(roi))
						continue;

					ROI newRoi = spotRoi.getSubtraction(roi);
					replaceRoi(exp, spot, spotRoi, (ROI2D) newRoi);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				break;
			}
		}
	}

}
