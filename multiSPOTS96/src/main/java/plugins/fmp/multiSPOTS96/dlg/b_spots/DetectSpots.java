package plugins.fmp.multiSPOTS96.dlg.b_spots;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

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

import icy.roi.ROI2D;
import icy.util.StringUtil;
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multiSPOTS96.canvas2D.Canvas2D_3Transforms;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cages.Cage;
import plugins.fmp.multitools.experiment.spots.Spot;
import plugins.fmp.multitools.series.DetectSpotsOutline;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.Comparators;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.overlay.OverlayThreshold;
import plugins.kernel.roi.roi2d.ROI2DEllipse;

public class DetectSpots extends JPanel implements ChangeListener, PropertyChangeListener {
	private static final long serialVersionUID = -5257698990389571518L;
	private MultiSPOTS96 parent0 = null;

	private String detectString = "Detect blobs...";
	private JButton startComputationButton = new JButton(detectString);
	private JComboBox<String> allCellsComboBox = new JComboBox<String>(new String[] { "all cages", "selected cages" });
	private JCheckBox allCheckBox = new JCheckBox("ALL (current to last)", false);

	private JLabel spotsFilterLabel = new JLabel("Filter");
	ImageTransformEnums[] transforms = new ImageTransformEnums[] { ImageTransformEnums.R_RGB, ImageTransformEnums.G_RGB,
			ImageTransformEnums.B_RGB, ImageTransformEnums.R2MINUS_GB, ImageTransformEnums.G2MINUS_RB,
			ImageTransformEnums.B2MINUS_RG, ImageTransformEnums.RGB, ImageTransformEnums.GBMINUS_2R,
			ImageTransformEnums.RBMINUS_2G, ImageTransformEnums.RGMINUS_2B, ImageTransformEnums.RGB_DIFFS,
			ImageTransformEnums.H_HSB, ImageTransformEnums.S_HSB, ImageTransformEnums.B_HSB };
	private JComboBox<ImageTransformEnums> spotsTransformsComboBox = new JComboBox<ImageTransformEnums>(transforms);

	private String[] directions = new String[] { "threshold >", "threshold <" };
	private JComboBox<String> spotsDirectionComboBox = new JComboBox<String>(directions);
	private JSpinner spotsThresholdSpinner = new JSpinner(new SpinnerNumberModel(35, 0, 255, 1));
	private JToggleButton spotsViewButton = new JToggleButton("View");
	private JCheckBox spotsOverlayCheckBox = new JCheckBox("overlay");

	private JButton convertSpotToEllipseButton = new JButton("Convert blobs to spots");
	private JSpinner spotDiameterSpinner = new JSpinner(new SpinnerNumberModel(22, 1, 1200, 1));

	private JButton deleteSelectedSpotsButton = new JButton("Remove selected spots");
	private JButton duplicateSelectedSpotButton = new JButton("Duplicate selected spot");
	private JButton cleanUpNamesButton = new JButton("Clean up spot names");

	private DetectSpotsOutline detectSpots = null;
	private OverlayThreshold overlayThreshold = null;

	// ----------------------------------------------------

	void init(GridLayout capLayout, MultiSPOTS96 parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);
		layoutLeft.setVgap(0);

		JPanel panel0 = new JPanel(layoutLeft);
		panel0.add(startComputationButton);
		panel0.add(allCellsComboBox);
		panel0.add(allCheckBox);
		add(panel0);

		JPanel panel1 = new JPanel(layoutLeft);
		panel1.add(spotsFilterLabel);
		panel1.add(spotsTransformsComboBox);
		panel1.add(spotsDirectionComboBox);
		panel1.add(spotsThresholdSpinner);
		panel1.add(spotsViewButton);
		panel1.add(spotsOverlayCheckBox);
		add(panel1);
		add(panel1);

		JPanel panel2 = new JPanel(layoutLeft);
		panel2.add(convertSpotToEllipseButton);
		panel2.add(new JLabel("size (pixels="));
		panel2.add(spotDiameterSpinner);
		add(panel2);

		JPanel panel3 = new JPanel(layoutLeft);
		panel3.add(deleteSelectedSpotsButton);
		panel3.add(duplicateSelectedSpotButton);
		panel3.add(cleanUpNamesButton);
		add(panel3);

		spotsTransformsComboBox.setSelectedItem(ImageTransformEnums.RGB_DIFFS);
		spotsDirectionComboBox.setSelectedIndex(1);

		defineActionListeners();
		defineItemListeners();
	}

	private void defineItemListeners() {
		spotsThresholdSpinner.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				updateOverlayThreshold();
			}
		});
	}

	private void defineActionListeners() {
		startComputationButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				if (startComputationButton.getText().equals(detectString))
					startComputation();
				else
					stopComputation();
			}
		});

		allCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Color color = Color.BLACK;
				if (allCheckBox.isSelected())
					color = Color.RED;
				allCheckBox.setForeground(color);
				startComputationButton.setForeground(color);
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

		deleteSelectedSpotsButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					deleteSelectedSpot(exp);
			}
		});

		duplicateSelectedSpotButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					duplicateSelectedSpot(exp);
			}
		});

		convertSpotToEllipseButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					int diameter = (int) spotDiameterSpinner.getValue();
					convertBlobsToCircularSpots(exp, diameter);
				}
			}
		});

		spotDiameterSpinner.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					changeSpotsDiameter(exp);
			}
		});

		cleanUpNamesButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					cleanUpSpotNames(exp);
				}
			}
		});
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		if (e.getSource() == spotsThresholdSpinner) {
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null)
				exp.getCages().detect_threshold = (int) spotsThresholdSpinner.getValue();
		}
	}

	public void updateOverlay(Experiment exp) {
		if (overlayThreshold == null) {
			overlayThreshold = new OverlayThreshold(exp.getSeqCamData().getSequence());
		} else {
			exp.getSeqCamData().getSequence().removeOverlay(overlayThreshold);
			overlayThreshold.setSequence(exp.getSeqCamData().getSequence());
		}
		// overlayThreshold.setReferenceImage(exp.getSeqCamData().getReferenceImage());
		exp.getSeqCamData().getSequence().addOverlay(overlayThreshold);
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

	private BuildSeriesOptions initTrackParameters(Experiment exp) {
		BuildSeriesOptions options = detectSpots.options;
		options.expList = parent0.expListComboLazy;
		options.expList.index0 = parent0.expListComboLazy.getSelectedIndex();
		if (allCheckBox.isSelected())
			options.expList.index1 = options.expList.getItemCount() - 1;
		else
			options.expList.index1 = parent0.expListComboLazy.getSelectedIndex();

		options.btrackWhite = (spotsDirectionComboBox.getSelectedIndex() == 1);
		options.threshold = (int) spotsThresholdSpinner.getValue();
		options.detectFlies = false;

		options.parent0Rect = parent0.mainFrame.getBoundsInternal();
//		options.binSubDirectory = exp.getBinSubDirectory();

		options.fromFrame = exp.getSeqCamData().getCurrentFrame();

		options.transformop = (ImageTransformEnums) spotsTransformsComboBox.getSelectedItem();
		int iselected = allCellsComboBox.getSelectedIndex() - 1;
		options.selectedIndexes = new ArrayList<Integer>(exp.getCages().cagesList.size());
		options.selectedIndexes.addAll(getSelectedCages(exp, iselected));
		options.detectCage = iselected;
		return options;
	}

	ArrayList<Integer> getSelectedCages(Experiment exp, int iSelectedOption) {
		ArrayList<Integer> indexes = new ArrayList<Integer>(exp.getCages().cagesList.size());
		for (Cage cage : exp.getCages().cagesList) {
			boolean bselected = true;
			if (iSelectedOption == 0)
				bselected = cage.getRoi().isSelected();
			if (bselected)
				indexes.add(cage.getProperties().getCageID());
		}
		return indexes;
	}

	void startComputation() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;
		parent0.dlgBrowse.loadSaveExperiment.closeViewsForCurrentExperiment(exp);

		detectSpots = new DetectSpotsOutline();
		detectSpots.options = initTrackParameters(exp);
		detectSpots.stopFlag = false;
		detectSpots.addPropertyChangeListener(this);
		detectSpots.execute();
		startComputationButton.setText("STOP");
	}

	private void stopComputation() {
		if (detectSpots != null && !detectSpots.stopFlag)
			detectSpots.stopFlag = true;
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (StringUtil.equals("thread_ended", evt.getPropertyName())) {
			startComputationButton.setText(detectString);
			selectCagesAccordingToOptions(detectSpots.options.selectedIndexes);
		}
	}

	void selectCagesAccordingToOptions(ArrayList<Integer> selectedCagesList) {
		if (allCellsComboBox.getSelectedIndex() == 0 || selectedCagesList == null || selectedCagesList.size() < 1)
			return;
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;
		for (Cage cage : exp.getCages().cagesList) {
			if (!selectedCagesList.contains(cage.getProperties().getCageID()))
				continue;
			cage.getRoi().setSelected(true);
		}
	}

	void removeOverlay(Experiment exp) {
		if (exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null)
			exp.getSeqCamData().getSequence().removeOverlay(overlayThreshold);
	}

	void deleteSelectedSpot(Experiment exp) {
		if (exp.getSeqCamData().getSequence() != null) {
			ArrayList<ROI2D> listROIs = exp.getSeqCamData().getSequence().getSelectedROI2Ds();
			for (ROI2D roi : listROIs) {
				String name = roi.getName();
				if (!name.contains("spot"))
					continue;
				Cage cage = exp.getCages().getCageFromSpotName(name);
				Iterator<Spot> iterator = cage.spotsArray.getSpotsList().iterator();
				while (iterator.hasNext()) {
					Spot spot = iterator.next();
					if (name.equals(spot.getRoi().getName())) {
						iterator.remove();
						break;
					}
				}
			}
			cleanUpSpotNames(exp);
		}
		exp.saveSpotsArray_file();
	}

	void duplicateSelectedSpot(Experiment exp) {
		if (exp.getSeqCamData().getSequence() != null) {
			ArrayList<ROI2D> listROIs = exp.getSeqCamData().getSequence().getSelectedROI2Ds();
			for (ROI2D roi : listROIs) {
				String name = roi.getName();
				if (!name.contains("spot"))
					continue;
				Cage cage = exp.getCages().getCageFromSpotName(name);
				ArrayList<Spot> spotsToDuplicate = new ArrayList<Spot>();
				Iterator<Spot> iterator = cage.spotsArray.getSpotsList().iterator();
				while (iterator.hasNext()) {
					Spot spot = iterator.next();
					if (name.equals(spot.getRoi().getName())) {
						spotsToDuplicate.add(spot);
						break;
					}
				}
				if (spotsToDuplicate.size() > 0) {
					for (Spot spot : spotsToDuplicate) {
						Point2D.Double pos = (Double) spot.getRoi().getPosition2D();
						Rectangle rect = spot.getRoi().getBounds();
						int radius = rect.width / 2;
						pos.setLocation(pos.getX() + 5, pos.getY() + 5);
						cage.addEllipseSpot(pos, radius);
						Spot newSpot = cage.spotsArray.getSpotsList().get(cage.spotsArray.getSpotsList().size() - 1);
						exp.getSeqCamData().getSequence().addROI(newSpot.getRoi());
					}
				}
			}
			cleanUpSpotNames(exp);
		}
		exp.saveSpotsArray_file();
	}

	void convertBlobsToCircularSpots(Experiment exp, int diameter) {
		boolean bOnlySelectedCages = (allCellsComboBox.getSelectedIndex() == 1);
		for (Cage cage : exp.getCages().cagesList) {
			if (bOnlySelectedCages && !cage.getRoi().isSelected())
				continue;
			for (Spot spot : cage.spotsArray.getSpotsList()) {
				ROI2D roiP = spot.getRoi();
				Point center = roiP.getPosition();
				Rectangle rect = roiP.getBounds();
				center.x += rect.getWidth() / 2;
				center.y += rect.getHeight() / 2;

				String name = spot.getRoi().getName();
				Ellipse2D ellipse = new Ellipse2D.Double(center.x - diameter / 2, center.y - diameter / 2, diameter,
						diameter);
				ROI2DEllipse roiEllipse = new ROI2DEllipse(ellipse);
				roiEllipse.setName(name);
				spot.setRoi(roiEllipse);
			}
		}
		exp.getSeqCamData().removeROIsContainingString("spot");
		exp.getCages().transferCageSpotsToSequenceAsROIs(exp.getSeqCamData());
		exp.saveSpotsArray_file();
	}

	void changeSpotsDiameter(Experiment exp) {
		int diameter = (int) spotDiameterSpinner.getValue();
		convertBlobsToCircularSpots(exp, diameter);
	}

	private void cleanUpSpotNames(Experiment exp) {
		for (Cage cage : exp.getCages().cagesList) {
			cage.mapSpotsToCageColumnRow();
			Collections.sort(cage.spotsArray.getSpotsList(), new Comparators.Spot_cagePosition());
			cage.cleanUpSpotNames();
		}
		exp.getSeqCamData().removeROIsContainingString("spot");
		exp.getCages().transferCageSpotsToSequenceAsROIs(exp.getSeqCamData());
		exp.saveSpotsArray_file();
	}

}
