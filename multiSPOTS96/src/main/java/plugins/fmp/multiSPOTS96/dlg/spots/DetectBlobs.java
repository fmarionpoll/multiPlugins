package plugins.fmp.multiSPOTS96.dlg.spots;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
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

import icy.canvas.IcyCanvas;
import icy.roi.ROI2D;
import icy.util.StringUtil;
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multitools.canvas2D.Canvas2D_3Transforms;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.ids.SpotID;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.series.DetectSpotsOutline;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.overlay.OverlayThreshold;
import plugins.kernel.roi.roi2d.ROI2DEllipse;

public class DetectBlobs extends JPanel implements ChangeListener, PropertyChangeListener {
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

	private DetectSpotsOutline detectSpots = null;
	private OverlayThreshold overlayThreshold = null;

	// ----------------------------------------------------

	void init(GridLayout capLayout, MultiSPOTS96 parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);
		layoutLeft.setVgap(0);

		JPanel panel1 = new JPanel(layoutLeft);
		panel1.add(spotsFilterLabel);
		panel1.add(spotsTransformsComboBox);
		panel1.add(spotsDirectionComboBox);
		panel1.add(spotsThresholdSpinner);
		panel1.add(spotsViewButton);
		panel1.add(spotsOverlayCheckBox);
		add(panel1);

		JPanel panel0 = new JPanel(layoutLeft);
		panel0.add(startComputationButton);
		panel0.add(allCellsComboBox);
		panel0.add(allCheckBox);
		add(panel0);

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
			IcyCanvas canvas = exp.getSeqCamData().getSequence().getFirstViewer().getCanvas();
			if (canvas instanceof Canvas2D_3Transforms)
				((Canvas2D_3Transforms) canvas).setTransformStep1Index(0);
		}
		spotsOverlayCheckBox.setEnabled(displayCheckOverlay);
	}

	private void updateTransformFunctionsOfCanvas(Experiment exp) {
		IcyCanvas canvas = exp.getSeqCamData().getSequence().getFirstViewer().getCanvas();
		if (!(canvas instanceof Canvas2D_3Transforms))
			return;
		Canvas2D_3Transforms c3 = (Canvas2D_3Transforms) canvas;
		if (c3.getTransformStep1ItemCount() < (spotsTransformsComboBox.getItemCount() + 1))
			c3.updateTransformsStep1(transforms);
		int index = spotsTransformsComboBox.getSelectedIndex();
		c3.setTransformStep1(index + 1, null);
	}

	void updateOverlayThreshold() {
		if (!spotsOverlayCheckBox.isSelected())
			return;

		if (overlayThreshold == null) {
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null)
				updateOverlay(exp);
		}

		// Align overlay condition with detection (options.btrackWhite logic):
		// index 0: "threshold >", index 1: "threshold <"
		// Detection uses ">" when btrackWhite is true (index 1), "<" otherwise.
		// So overlay must use > when index==1 to highlight exactly what will be
		// detected.
		boolean ifGreater = (spotsDirectionComboBox.getSelectedIndex() == 1);
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
			Spots allSpots = exp.getSpots();
			ArrayList<ROI2D> listROIs = exp.getSeqCamData().getSequence().getSelectedROI2Ds();
			for (ROI2D roi : listROIs) {
				String name = roi.getName();
				if (name == null || !name.contains("spot"))
					continue;

				Spot spotToDelete = allSpots.findSpotByName(name);
				if (spotToDelete == null) {
					Logger.error("spot to delete not found; " + name);
					continue;
				}

				Cage cage = exp.getCages().getCageFromID(spotToDelete.getProperties().getCageID());
				if (cage == null) {
					cage = exp.getCages().getCageFromSpotName(name);
				}

				SpotID spotID = spotToDelete.getSpotUniqueID();
				allSpots.removeSpot(spotToDelete);
				if (spotID != null) {
					for (Cage c : exp.getCages().cagesList) {
						c.getSpotIDs().remove(spotID);
					}
				} else if (cage != null) {
					List<Spot> cageSpots = cage.getSpotList(allSpots);
					for (Spot spot : cageSpots) {
						ROI2D spotRoi = spot.getRoi();
						if (spotRoi != null && name.equals(spotRoi.getName())) {
							allSpots.removeSpot(spot);
							break;
						}
					}
				}
			}
			cleanUpSpotNames(exp);
		}
		exp.saveSpots_File();
	}

	void duplicateSelectedSpot(Experiment exp) {
		if (exp.getSeqCamData().getSequence() != null) {
			Spots allSpots = exp.getSpots();
			ArrayList<ROI2D> listROIs = exp.getSeqCamData().getSequence().getSelectedROI2Ds();
			for (ROI2D roi : listROIs) {
				String name = roi.getName();
				if (!name.contains("spot"))
					continue;

				Spot spotToDuplicate = allSpots.findSpotByName(name);
				if (spotToDuplicate == null) {
					Logger.error("spot to duplicate not found; " + roi.getName());
					continue;
				}

				int cageID = spotToDuplicate.getProperties().getCageID();
				Cage cage = exp.getCages().getCageFromID(cageID);
				if (cage == null) {
					// legacy fallback based on ROI naming convention
					cage = exp.getCages().getCageFromSpotName(name);
				}
				if (cage == null) {
					Logger.error("cage not found for selected spot; " + roi.getName());
					continue;
				}

				ROI2D sourceRoi = spotToDuplicate.getRoi();
				if (sourceRoi == null) {
					Logger.error("selected spot has no ROI; " + roi.getName());
					continue;
				}
				Point2D sourcePos = sourceRoi.getPosition2D();
				Rectangle rect = sourceRoi.getBounds();
				if (sourcePos == null || rect == null) {
					Logger.error("selected spot has invalid ROI geometry; " + roi.getName());
					continue;
				}
				int radius = Math.max(1, Math.min(rect.width, rect.height) / 2);
				Point2D.Double pos = new Double(sourcePos.getX() + 5, sourcePos.getY() + 5);

				// create new spot
				cage.addEllipseSpot(pos, radius, allSpots);
				List<SpotID> spotIDs = cage.getSpotIDs();
				if (!spotIDs.isEmpty()) {
					SpotID lastID = spotIDs.get(spotIDs.size() - 1);
					Spot newSpot = allSpots.findSpotwithID(lastID);
					if (newSpot != null) {
						newSpot.getProperties().setCageID(cage.getCageID());
						exp.getSeqCamData().getSequence().addROI(newSpot.getRoi());
					}
				}
			}
			cleanUpSpotNames(exp);
		}
		exp.saveSpots_File();
	}

	void convertBlobsToCircles(Experiment exp, int diameter) {
		boolean bOnlySelectedCages = (allCellsComboBox.getSelectedIndex() == 1);
		Spots allSpots = exp.getSpots();
//		int nspots = allSpots.getSpotListCount();

		for (Cage cage : exp.getCages().cagesList) {
			if (bOnlySelectedCages && !cage.getRoi().isSelected())
				continue;
			List<SpotID> spotIDList = cage.getSpotIDs();
			for (SpotID spotID : spotIDList) {
				Spot spot = allSpots.findSpotwithID(spotID);
				if (spot == null) {
					continue;
				}

				ROI2D roiP = spot.getRoi();
				if (roiP == null) {
					continue;
				}
				Rectangle rect = roiP.getBounds();
				if (rect == null) {
					continue;
				}

				double centerX = rect.getCenterX();
				double centerY = rect.getCenterY();
				double radius = diameter / 2.0;

				String name = spot.getRoi().getName();
				Ellipse2D ellipse = new Ellipse2D.Double(centerX - radius, centerY - radius, diameter, diameter);
				ROI2DEllipse roiEllipse = new ROI2DEllipse(ellipse);
				roiEllipse.setName(name);
				spot.setRoi(roiEllipse);
			}
		}
		exp.getSeqCamData().removeROIsContainingString("spot");
		exp.getSpots().transferSpotsToSequenceAsROIs(exp.getSeqCamData().getSequence());
		exp.saveSpots_File();
	}

	private void cleanUpSpotNames(Experiment exp) {
		Spots allSpots = exp.getSpots();
		for (Cage cage : exp.getCages().cagesList) {
			cage.reorderSpotsReadingOrderAndAssignRowCol(allSpots);
			cage.cleanUpSpotNames(allSpots);
		}
		exp.getSeqCamData().removeROIsContainingString("spot");
		exp.getCages().transferCageSpotsToSequenceAsROIs(exp.getSeqCamData(), allSpots);
		exp.saveSpots_File();
	}

}
