package plugins.fmp.multiSPOTS96.dlg.spots;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import icy.roi.ROI2D;
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.ids.SpotID;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.overlay.OverlayThreshold;
import plugins.kernel.roi.roi2d.ROI2DEllipse;

public class BlobsToSpots extends JPanel implements ChangeListener, PropertyChangeListener {
	private static final long serialVersionUID = -5257698990389571518L;
	private MultiSPOTS96 parent0 = null;

//	private String detectString = "Detect blobs...";

	private JButton convertBlobsToSpotButton = new JButton("Convert blobs to spots");
	private JSpinner spotDiameterSpinner = new JSpinner(new SpinnerNumberModel(22, 1, 1200, 1));

	private JButton deleteSelectedSpotsButton = new JButton("Delete spots");
	private JButton duplicateSelectedSpotButton = new JButton("Duplicate spot");
	private JButton cleanUpNamesButton = new JButton("Clean up spot names");

//	private DetectSpotsOutline detectSpots = null;
	private OverlayThreshold overlayThreshold = null;

	// ----------------------------------------------------

	void init(GridLayout capLayout, MultiSPOTS96 parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);
		layoutLeft.setVgap(0);

		JPanel panel2 = new JPanel(layoutLeft);
		panel2.add(convertBlobsToSpotButton);
		panel2.add(new JLabel("size (pixels="));
		panel2.add(spotDiameterSpinner);
		add(panel2);

		JPanel panel3 = new JPanel(layoutLeft);
		panel3.add(deleteSelectedSpotsButton);
		panel3.add(duplicateSelectedSpotButton);
		panel3.add(cleanUpNamesButton);
		add(panel3);

		defineActionListeners();
		defineItemListeners();
	}

	private void defineItemListeners() {
	}

	private void defineActionListeners() {
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

		convertBlobsToSpotButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					int diameter = (int) spotDiameterSpinner.getValue();
					convertBlobsToCircles(exp, diameter);
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
//		if (e.getSource() == spotsThresholdSpinner) {
//			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
//			if (exp != null)
//				exp.getCages().detect_threshold = (int) spotsThresholdSpinner.getValue();
//		}
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

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
//		if (StringUtil.equals("thread_ended", evt.getPropertyName())) {
//			startComputationButton.setText(detectString);
//			selectCagesAccordingToOptions(detectSpots.options.selectedIndexes);
//		}
	}

	void selectCagesAccordingToOptions(ArrayList<Integer> selectedCagesList) {
//		if (allCellsComboBox.getSelectedIndex() == 0 || selectedCagesList == null || selectedCagesList.size() < 1)
//			return;
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;
		for (Cage cage : exp.getCages().cagesList) {
			if (!selectedCagesList.contains(cage.getProperties().getCageID()))
				continue;
			cage.getRoi().setSelected(true);
		}
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
//		boolean bOnlySelectedCages = (allCellsComboBox.getSelectedIndex() == 1);
		Spots allSpots = exp.getSpots();
//		int nspots = allSpots.getSpotListCount();

		for (Cage cage : exp.getCages().cagesList) {
//			if (bOnlySelectedCages && !cage.getRoi().isSelected())
//				continue;
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

	void changeSpotsDiameter(Experiment exp) {
		int diameter = (int) spotDiameterSpinner.getValue();
		convertBlobsToCircles(exp, diameter);
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
