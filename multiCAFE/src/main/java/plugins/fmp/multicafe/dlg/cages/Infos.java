package plugins.fmp.multicafe.dlg.cages;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import icy.roi.ROI2D;
import icy.type.geom.Polygon2D;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.tools.polyline.PolygonUtilities;
import plugins.kernel.roi.roi2d.ROI2DPolygon;

public class Infos extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3325915033686366985L;
	private JButton editCageButton = new JButton("Edit cage infos...");
	private MultiCAFE parent0 = null;
	private InfosCagesTable dialog = null;
	private List<Cage> cagesCopy = new ArrayList<Cage>();

	JRadioButton useCapillaries = new JRadioButton("capillary");
	JRadioButton useROI = new JRadioButton("dimensions of ROI");
	ButtonGroup useGroup = new ButtonGroup();

	private JSpinner lengthSpinner = new JSpinner(new SpinnerNumberModel(78., 0., 100., 1.));
	private JSpinner pixelsSpinner = new JSpinner(new SpinnerNumberModel(5, 0, 1000, 1));
	private JButton measureButton = new JButton("get 1rst capillary");
	private JSpinner xTopMmSpinner = new JSpinner(new SpinnerNumberModel(100., 0., 500., 1.));
	private JSpinner yLeftMmSpinner = new JSpinner(new SpinnerNumberModel(81., 0., 500., 1.));
	private JButton measureROIButton = new JButton("use model 4-corner ROI");
	private static final String DEFAULT_SCALE_MODEL_ROI_NAME = "flyScaleModel_4corners";

	private final JButton generateScaleModelRoiButton = new JButton("Generate 4-corner ROI");
	private ROI2DPolygon scaleModelRoi4Corners = null;
	private String scaleModelRoiName = DEFAULT_SCALE_MODEL_ROI_NAME;
	private boolean tabCleanupListenerRegistered = false;
	private JPanel anisotropicPanel = null;
	private JPanel anisotropicPanel2 = null;
	private final JLabel flyScaleXValueLabel = new JLabel("-");
	private final JLabel flyScaleYValueLabel = new JLabel("-");

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);
		flowLayout.setVgap(0);

		JPanel panelScale = new JPanel(flowLayout);
		panelScale.add(editCageButton);
		panelScale.add(new JLabel("Scale (mm/pixel): X="));
		panelScale.add(flyScaleXValueLabel);
		panelScale.add(new JLabel("  Y="));
		panelScale.add(flyScaleYValueLabel);
		add(panelScale);

		JPanel panel0a = new JPanel(flowLayout);
		panel0a.add(new JLabel("Use as reference: "));
		panel0a.add(useROI);
		panel0a.add(useCapillaries);
		panel0a.add(measureButton);
		useGroup.add(useROI);
		useGroup.add(useCapillaries);
		useROI.setSelected(true);
		add(panel0a);

		anisotropicPanel = new JPanel(flowLayout);
		anisotropicPanel.add(new JLabel("x width (mm):", SwingConstants.RIGHT));
		anisotropicPanel.add(xTopMmSpinner);
		anisotropicPanel.add(new JLabel("y height (mm):", SwingConstants.RIGHT));
		anisotropicPanel.add(yLeftMmSpinner);
		add(anisotropicPanel);

		anisotropicPanel2 = new JPanel(flowLayout);
		anisotropicPanel2.add(generateScaleModelRoiButton);
		anisotropicPanel2.add(measureROIButton);
		add(anisotropicPanel2);

		defineActionListeners();
		registerTabCleanupListener();
		refreshFromCurrentExperiment();
	}

	public void refreshFromCurrentExperiment() {
		Experiment exp = parent0 != null ? (Experiment) parent0.expListComboLazy.getSelectedItem() : null;
		refreshDisplayedScale(exp);
	}

	private void refreshDisplayedScale(Experiment exp) {
		if (exp == null) {
			flyScaleXValueLabel.setText("-");
			flyScaleYValueLabel.setText("-");
			return;
		}
		flyScaleXValueLabel.setText(String.format("%.6f", exp.getFlyMmPerPixelX()));
		flyScaleYValueLabel.setText(String.format("%.6f", exp.getFlyMmPerPixelY()));
	}

	private void defineActionListeners() {
		editCageButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					if (exp.getCages().getCageList().size() < 1)
						exp.getCages().createEmptyCagesFromCapillaries(exp.getCapillaries());
					exp.getCapillaries().transferDescriptionToCapillaries();
					exp.getCages().transferNFliesFromCapillariesToCageBox(exp.getCapillaries().getList());
					dialog = new InfosCagesTable();
					dialog.initialize(parent0, cagesCopy);
				}
			}
		});

		useCapillaries.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				lengthSpinner.setValue(23.);
				measureButton.setText("get length 1rst capillary");
				updateModeVisibility();
				applyFlyScaleToExperiment();
			}
		});

		useROI.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				updateModeVisibility();
				measureROI();
			}
		});

		measureButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				if (useCapillaries.isSelected()) {
					measureFirstCapillary();
				} else if (useROI.isSelected()) {
					measureROI();
				}
			}
		});
		measureROIButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				measureROIUsingScaleModelRoi();
			}
		});

		generateScaleModelRoiButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				toggleScaleModelRoiPolygon();
			}
		});

		ChangeListener scaleListener = new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				if (useCapillaries.isSelected())
					applyFlyScaleToExperiment();
			}
		};
		lengthSpinner.addChangeListener(scaleListener);
		pixelsSpinner.addChangeListener(scaleListener);
		updateModeVisibility();
	}

	private void registerTabCleanupListener() {
		if (tabCleanupListenerRegistered) {
			return;
		}
		if (parent0 == null || parent0.paneCages == null || parent0.paneCages.tabsPane == null) {
			return;
		}

		parent0.paneCages.tabsPane.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				// Remove the generated model ROI when leaving the "Infos" tab.
				if (parent0.paneCages.tabsPane.getSelectedComponent() != Infos.this) {
					removeScaleModelRoiPolygon();
				}
			}
		});

		tabCleanupListenerRegistered = true;
	}

	private String getScaleModelRoiName() {
		return DEFAULT_SCALE_MODEL_ROI_NAME;
	}

	private ROI2DPolygon findScaleModelRoiPolygon(Experiment exp) {
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null) {
			return null;
		}
		List<ROI2D> rois = exp.getSeqCamData().getSequence().getROI2Ds();
		if (rois == null) {
			return null;
		}
		for (ROI2D roi : rois) {
			if (roi instanceof ROI2DPolygon) {
				String name = roi.getName();
				if (name != null && name.equals(scaleModelRoiName)) {
					return (ROI2DPolygon) roi;
				}
			}
		}
		return null;
	}

	private void toggleScaleModelRoiPolygon() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null) {
			return;
		}

		// Toggle "off" always removes the currently active generated model ROI,
		// even if the user changed the name field since generation.
		if (scaleModelRoi4Corners != null) {
			removeScaleModelRoiPolygon();
			return;
		}

		ROI2DPolygon existing = findScaleModelRoiPolygon(exp);
		if (existing != null) {
			removeScaleModelRoiPolygon();
			return;
		}

		// Toggle "on": create a new model ROI using the name field.
		scaleModelRoiName = getScaleModelRoiName();
		removeScaleModelRoiPolygon();
		generateScaleModelRoiPolygon(exp);
	}

	private void removeScaleModelRoiPolygon() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null) {
			return;
		}

		if (scaleModelRoi4Corners != null) {
			exp.getSeqCamData().getSequence().removeROI(scaleModelRoi4Corners);
			scaleModelRoi4Corners = null;
		}

		List<ROI2D> rois = exp.getSeqCamData().getSequence().getROI2Ds();
		if (rois == null) {
			return;
		}

		List<ROI2D> toRemove = new ArrayList<>();
		for (ROI2D roi : rois) {
			if (roi instanceof ROI2DPolygon) {
				String name = roi.getName();
				if (name != null && name.equals(scaleModelRoiName)) {
					toRemove.add(roi);
				}
			}
		}
		for (ROI2D roi : toRemove) {
			exp.getSeqCamData().getSequence().removeROI(roi);
		}
	}

	private void generateScaleModelRoiPolygon(Experiment exp) {
		Rectangle rect = exp.getSeqCamData().getSequence().getBounds2D();
		if (rect == null || rect.width <= 0 || rect.height <= 0) {
			JOptionPane.showMessageDialog(this, "Could not determine camera bounds to generate the 4-corner ROI.",
					"ROI generation failed", JOptionPane.WARNING_MESSAGE);
			return;
		}

		int marginX = Math.max(1, rect.width / 20);
		int marginY = Math.max(1, rect.height / 20);

		double rectleft = rect.x + marginX;
		double rectright = rect.x + rect.width - marginX;
		double recttop = rect.y + marginY;
		double rectbottom = rect.y + rect.height - marginY;

		// If capillaries exist, place the top edge closer to the capillary area
		// (better starting point for manual vertex adjustment).
		if (exp.getCapillaries() != null && exp.getCapillaries().getList().size() > 0) {
			Rectangle bound0 = exp.getCapillaries().getList().get(0).getRoi().getBounds();
			rectleft = bound0.x;
			rectright = bound0.x + bound0.width;
			if (exp.getCapillaries().getList().size() > 1) {
				Rectangle bound1 = exp.getCapillaries().getList().get(exp.getCapillaries().getList().size() - 1)
						.getRoi().getBounds();
				rectright = bound1.x + bound1.width;
			}
			int diff = (int) ((rectright - rectleft) * 2 / 60.0);
			rectleft -= diff;
			rectright += diff;
			recttop = bound0.y + bound0.height - (bound0.height / 8);
			rectbottom = rect.y + rect.height - 4;
		}

		List<Point2D> points = new ArrayList<>();
		points.add(new Point2D.Double(rectleft, recttop)); // UL-ish
		points.add(new Point2D.Double(rectright, recttop)); // UR-ish
		points.add(new Point2D.Double(rectright, rectbottom)); // LR-ish
		points.add(new Point2D.Double(rectleft, rectbottom)); // LL-ish

		ROI2DPolygon roiP = new ROI2DPolygon(points);
		roiP.setName(scaleModelRoiName);

		exp.getSeqCamData().getSequence().addROI(roiP);
		exp.getSeqCamData().getSequence().setSelectedROI(roiP);
		scaleModelRoi4Corners = roiP;
	}

	private void measureROIUsingScaleModelRoi() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null) {
			return;
		}

		ROI2DPolygon model = scaleModelRoi4Corners != null ? scaleModelRoi4Corners : findScaleModelRoiPolygon(exp);
		if (model == null) {
			JOptionPane.showMessageDialog(this, "Model 4-corner ROI not found.\nClick \"Generate 4-corner ROI\" first.",
					"Scale not applied", JOptionPane.WARNING_MESSAGE);
			return;
		}

		exp.getSeqCamData().getSequence().setSelectedROI(model);
		measureROI();
	}

	void measureFirstCapillary() {
		int npixels = parent0.paneCapillaries.tabInfos.getLengthFirstCapillaryROI();
		if (npixels > 0) {
			pixelsSpinner.setValue(npixels);
			applyFlyScaleToExperiment();
		} else {
			JOptionPane.showMessageDialog(this,
					"Could not measure the first capillary length in pixels.\n"
							+ "Make sure capillary ROIs are present and loaded.",
					"Fly scale not applied", JOptionPane.WARNING_MESSAGE);
		}
	}

	void measureROI() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null)
			return;

		ROI2D selectedRoi = exp.getSeqCamData().getSequence().getSelectedROI2D();
		if (!(selectedRoi instanceof ROI2DPolygon)) {
			JOptionPane.showMessageDialog(this, "Select a 4-corner polygon ROI on the camera sequence.");
			return;
		}

		Polygon2D orderedPolygon = PolygonUtilities
				.orderVerticesOf4CornersPolygon(((ROI2DPolygon) selectedRoi).getPolygon());
		if (orderedPolygon == null || orderedPolygon.npoints < 4) {
			JOptionPane.showMessageDialog(this, "Could not read a valid 4-corner polygon ROI.");
			return;
		}

		Point2D.Double pUL = new Point2D.Double(orderedPolygon.xpoints[0], orderedPolygon.ypoints[0]);
		Point2D.Double pLL = new Point2D.Double(orderedPolygon.xpoints[1], orderedPolygon.ypoints[1]);
		Point2D.Double pLR = new Point2D.Double(orderedPolygon.xpoints[2], orderedPolygon.ypoints[2]);
		Point2D.Double pUR = new Point2D.Double(orderedPolygon.xpoints[3], orderedPolygon.ypoints[3]);

		double topPx = pUL.distance(pUR);
		double bottomPx = pLL.distance(pLR);
		double leftPx = pUL.distance(pLL);
		double rightPx = pUR.distance(pLR);

		if (topPx <= 0 || bottomPx <= 0 || leftPx <= 0 || rightPx <= 0) {
			JOptionPane.showMessageDialog(this, "Polygon edges are invalid (zero length).");
			return;
		}

		double xTopMm = ((Number) xTopMmSpinner.getValue()).doubleValue();
		double yLeftMm = ((Number) yLeftMmSpinner.getValue()).doubleValue();
		if (xTopMm <= 0 || yLeftMm <= 0) {
			JOptionPane.showMessageDialog(this, "All ROI dimensions in mm must be > 0.");
			return;
		}

		double sx = xTopMm / ((topPx + bottomPx) / 2.0);
		double sy = yLeftMm / ((leftPx + rightPx) / 2.0);
		exp.setFlyMmPerPixelX(sx);
		exp.setFlyMmPerPixelY(sy);
		refreshDisplayedScale(exp);
		if (!exp.saveExperimentDescriptors()) {
			JOptionPane.showMessageDialog(this,
					"Scale values were computed, but saving experiment descriptors failed.\n"
							+ "Excel export may still use the previous (default) scale.",
					"Scale not persisted", JOptionPane.WARNING_MESSAGE);
		}

		JOptionPane.showMessageDialog(this,
				String.format("Applied fly scale from ROI:%nX=%.6f mm/pixel%nY=%.6f mm/pixel", sx, sy));
	}

	private void applyFlyScaleToExperiment() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;
		double lengthMm = ((Number) lengthSpinner.getValue()).doubleValue();
		double lengthPx = ((Number) pixelsSpinner.getValue()).doubleValue();
		if (!(lengthMm > 0.0 && lengthPx > 0.0)) {
			JOptionPane.showMessageDialog(this,
					"Invalid inputs for fly scale.\nlengthMm and lengthPx must both be > 0.", "Fly scale not applied",
					JOptionPane.WARNING_MESSAGE);
			return;
		}
		double scale = lengthMm / lengthPx;
		exp.setFlyMmPerPixelX(scale);
		exp.setFlyMmPerPixelY(scale);
		refreshDisplayedScale(exp);
		if (!exp.saveExperimentDescriptors()) {
			JOptionPane.showMessageDialog(this,
					"Scale values were computed, but saving experiment descriptors failed.\n"
							+ "Excel export may still use the previous (default) scale.",
					"Scale not persisted", JOptionPane.WARNING_MESSAGE);
		}
	}

	private void updateModeVisibility() {
		boolean capillaryMode = useCapillaries.isSelected();
		measureButton.setVisible(capillaryMode);
		anisotropicPanel.setVisible(!capillaryMode);
		anisotropicPanel2.setVisible(!capillaryMode);
	}

}
