package plugins.fmp.multiSPOTS96.dlg.spots;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Polygon;
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
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import icy.canvas.Canvas2D;
import icy.gui.dialog.ConfirmDialog;
import icy.gui.viewer.Viewer;
import icy.roi.ROI2D;
import icy.type.geom.Polygon2D;
import icy.type.geom.Polyline2D;
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.ids.SpotID;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.ROI2D.ROI2DUtilities;
import plugins.kernel.roi.roi2d.ROI2DPolyLine;
import plugins.kernel.roi.roi2d.ROI2DPolygon;

public class EditSpots extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7582410775062671523L;

	private JButton deleteSelectedSpotsButton = new JButton("Delete spots");
	private JButton duplicateSelectedSpotButton = new JButton("Duplicate spot");
	private JButton cleanUpNamesButton = new JButton("Clean up spot names");

	private JComboBox<String> typeCombo = new JComboBox<String>(new String[] { "spot", "cage" });

	private JCheckBox selectRoisCheckBox = new JCheckBox("Select");
	private JCheckBox displaySnakeCheckBox = new JCheckBox("Display snake");
	private JButton centerRoisToSnakeButton = new JButton("Center rois to snake");
	private MultiSPOTS96 parent0 = null;

	public ROI2DPolygon roiPerimeter = null;
	public ROI2DPolyLine roiSnake = null;
	private ArrayList<ROI2D> enclosedRois = null;

	private JButton erodeButton = new JButton("Contract rois");
	private JButton dilateButton = new JButton("Dilate rois");

	void init(GridLayout capLayout, MultiSPOTS96 parent0) {
		this.parent0 = parent0;
		setLayout(capLayout);
		FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);
		flowLayout.setVgap(0);

		JPanel panel0 = new JPanel(flowLayout);
		panel0.add(deleteSelectedSpotsButton);
		panel0.add(duplicateSelectedSpotButton);
		panel0.add(cleanUpNamesButton);
		add(panel0);

		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(new JLabel("select type of rois"));
		panel1.add(typeCombo);
		add(panel1);

		JPanel panel2 = new JPanel(flowLayout);
		panel2.add(selectRoisCheckBox);
		panel2.add(displaySnakeCheckBox);
		panel2.add(centerRoisToSnakeButton);
		add(panel2);

		JPanel panel3 = new JPanel(flowLayout);
		panel3.add(dilateButton);
		panel3.add(erodeButton);
//		panel2.add(editSpotsWithTimeButton);
		add(panel3);

		defineActionListeners();
		updateButtonsStateAccordingToSelectRois(false, false);
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

		cleanUpNamesButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					cleanUpSpotNames(exp);
				}
			}
		});

		selectRoisCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp == null)
					return;
				boolean isSelected = selectRoisCheckBox.isSelected();
				updateButtonsStateAccordingToSelectRois(isSelected, false);
				showFrameEnclosingRois(exp, isSelected);
			}
		});

		displaySnakeCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp == null)
					return;
				boolean isSelected = displaySnakeCheckBox.isSelected();
				showSnake(exp, isSelected);
				showFrameEnclosingRois(exp, !isSelected);
				updateButtonsStateAccordingToSelectRois(true, isSelected);
			}
		});

		centerRoisToSnakeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp == null)
					return;
				updateRoisFromSnake(exp);
			}
		});

		dilateButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp == null)
					return;
				resizeRois(exp, +1);
			}
		});

		erodeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp == null)
					return;
				resizeRois(exp, -1);
			}
		});
	}

	// --------------------------------------

	private void updateButtonsStateAccordingToSelectRois(boolean enableSnake, boolean enableCenter) {
		displaySnakeCheckBox.setEnabled(enableSnake);
		centerRoisToSnakeButton.setEnabled(enableCenter);
	}

	private void showFrameEnclosingRois(Experiment exp, boolean show) {
		if (show) {
			setEnclosingFrame(exp);
		} else {
			exp.getSeqCamData().getSequence().removeROI(roiPerimeter);
			roiPerimeter = null;
		}
	}

	private void setEnclosingFrame(Experiment exp) {
		exp.getSeqCamData().getSequence().removeROI(roiPerimeter);
		showSnake(exp, false);
		createPerimeterEnclosingRois(exp);
		exp.getSeqCamData().getSequence().addROI(roiPerimeter);
		exp.getSeqCamData().getSequence().setSelectedROI(roiPerimeter);

		makeSureRectangleIsVisible(exp, roiPerimeter.getBounds());
	}

	private void makeSureRectangleIsVisible(Experiment exp, Rectangle rect) {
		Viewer v = exp.getSeqCamData().getSequence().getFirstViewer();
		Canvas2D canvas = (Canvas2D) v.getCanvas();
		canvas.centerOn(rect);
	}

	private void showSnake(Experiment exp, boolean show) {
		exp.getSeqCamData().getSequence().removeROI(roiSnake);
		String selectedRoiType = (String) typeCombo.getSelectedItem();

		if (show) {
			exp.getSeqCamData().getSequence().removeROI(roiPerimeter);
			ArrayList<ROI2D> snakeRois = buildOrderedRoisInsideCurrentPerimeter(exp, selectedRoiType);
			if (snakeRois.isEmpty()) {
				displaySnakeCheckBox.setSelected(false);
				updateButtonsStateAccordingToSelectRois(true, false);
				if (roiPerimeter != null) {
					exp.getSeqCamData().getSequence().addROI(roiPerimeter);
				}
				JOptionPane.showMessageDialog(this,
						"No " + selectedRoiType + " ROIs lie inside the yellow perimeter.\n"
								+ "Resize the perimeter so it covers the spots or cages you want in the snake.",
						"Snake", JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			enclosedRois = snakeRois;
			ArrayList<Point2D> listPoint = new ArrayList<Point2D>();
			for (ROI2D roi : enclosedRois) {
				listPoint.add(anchorPointForSnake(roi, selectedRoiType));
			}
			roiSnake = new ROI2DPolyLine(listPoint);
			roiSnake.setName("snake");
			exp.getSeqCamData().getSequence().addROI(roiSnake);
			exp.getSeqCamData().getSequence().setSelectedROI(roiSnake);

			exp.getSeqCamData().displaySpecificROIs(false, selectedRoiType);
			makeSureRectangleIsVisible(exp, roiSnake.getBounds());
		} else {
			roiSnake = null;
			if (roiPerimeter != null) {
				ArrayList<ROI2D> stillInside = buildOrderedRoisInsideCurrentPerimeter(exp, selectedRoiType);
				if (!stillInside.isEmpty()) {
					enclosedRois = stillInside;
				}
				exp.getSeqCamData().getSequence().addROI(roiPerimeter);
			}
			exp.getSeqCamData().displaySpecificROIs(true, selectedRoiType);
		}
	}

	private static Point2D.Double anchorPointForSnake(ROI2D roi, String selectedRoiType) {
		Rectangle rect = roi.getBounds();
		if (selectedRoiType.contains("cage")) {
			return new Point2D.Double(rect.getX(), rect.getY());
		}
		return new Point2D.Double(rect.getCenterX(), rect.getCenterY());
	}

	private static boolean pointInsidePolygon(Polygon2D poly, double x, double y) {
		if (poly == null || poly.npoints < 3) {
			return false;
		}
		Polygon awt = new Polygon();
		for (int i = 0; i < poly.npoints; i++) {
			awt.addPoint((int) Math.round(poly.xpoints[i]), (int) Math.round(poly.ypoints[i]));
		}
		return awt.contains(x, y);
	}

	private ArrayList<ROI2D> buildOrderedRoisInsideCurrentPerimeter(Experiment exp, String selectedRoiType) {
		ArrayList<ROI2D> inside = new ArrayList<>();
		if (roiPerimeter == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null) {
			return inside;
		}
		Polygon2D poly = roiPerimeter.getPolygon2D();
		ArrayList<ROI2D> listRoisPresent = exp.getSeqCamData().getROIsContainingString(selectedRoiType);
		for (ROI2D roi : listRoisPresent) {
			if (roi == null || roi == roiPerimeter || roi == roiSnake) {
				continue;
			}
			String name = roi.getName();
			if (name != null && (name.equals("perimeter") || name.equals("snake"))) {
				continue;
			}
			Point2D.Double anchor = anchorPointForSnake(roi, selectedRoiType);
			if (pointInsidePolygon(poly, anchor.x, anchor.y)) {
				inside.add(roi);
			}
		}
		inside.sort((a, b) -> {
			Point2D pa = anchorPointForSnake(a, selectedRoiType);
			Point2D pb = anchorPointForSnake(b, selectedRoiType);
			int cy = Double.compare(pa.getY(), pb.getY());
			if (cy != 0) {
				return cy;
			}
			return Double.compare(pa.getX(), pb.getX());
		});
		return inside;
	}

	private void createPerimeterEnclosingRois(Experiment exp) {
		String selectedRoiType = (String) typeCombo.getSelectedItem();
		ArrayList<ROI2D> listRoisPresent = exp.getSeqCamData().getROIsContainingString(selectedRoiType);
		ArrayList<ROI2D> listRoisSelected = new ArrayList<ROI2D>();
		for (ROI2D roi : listRoisPresent) {
			if (roi.isSelected())
				listRoisSelected.add(roi);
		}
		enclosedRois = listRoisSelected.size() > 0 ? listRoisSelected : listRoisPresent;

		Polygon2D polygon = ROI2DUtilities.getPolygonEnclosingROI2Ds(enclosedRois, selectedRoiType);
		roiPerimeter = new ROI2DPolygon(polygon);
		roiPerimeter.setName("perimeter");
		roiPerimeter.setColor(Color.YELLOW);
	}

	private void updateRoisFromSnake(Experiment exp) {
		if (enclosedRois == null || enclosedRois.size() < 1 || roiSnake == null)
			return;

		String selectedRoiType = (String) typeCombo.getSelectedItem();
		Polyline2D snake = roiSnake.getPolyline2D();
		int i = 0;
		for (ROI2D roi : enclosedRois) {
			double x = snake.xpoints[i];
			double y = snake.ypoints[i];
			i++;

			Rectangle rect = roi.getBounds();
			Point2D.Double point = null;
			if (selectedRoiType.contains("cage"))
				point = new Point2D.Double(x, y);
			else
				point = new Point2D.Double(x - rect.width / 2, y - rect.height / 2);
			roi.setPosition2D(point);
		}

		exp.getSeqCamData().displaySpecificROIs(true, selectedRoiType);
	}

	private void resizeRois(Experiment exp, int delta) {
		if (enclosedRois != null && enclosedRois.size() > 0) {
			for (ROI2D roi : enclosedRois) {
				exp.getSeqCamData().getSequence().removeROI(roi);
				roi = ROI2DUtilities.resizeROI(roi, delta);
				exp.getSeqCamData().getSequence().addROI(roi);
			}
		} else {
			ConfirmDialog.confirm("At least one spot must be selected");
		}
	}

	public void clearTemporaryROIs() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null) {
			exp.getSeqCamData().getSequence().removeROI(roiSnake);
			exp.getSeqCamData().getSequence().removeROI(roiPerimeter);
			roiSnake = null;
			roiPerimeter = null;
			displaySnakeCheckBox.setSelected(false);
			selectRoisCheckBox.setSelected(false);
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
		// exp.saveSpots_File();
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
				Point2D.Double pos = new Point2D.Double(sourcePos.getX() + 5, sourcePos.getY() + 5);

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
			// cleanUpSpotNames(exp);
		}
		// exp.saveSpots_File();
	}

	private void cleanUpSpotNames(Experiment exp) {
		Spots allSpots = exp.getSpots();
		int duplicateResolvedCount = 0;
		for (Cage cage : exp.getCages().cagesList) {
			cage.reorderSpotsReadingOrderAndAssignRowCol(allSpots);
			duplicateResolvedCount += cage.cleanUpSpotNames(allSpots);
		}
		exp.getSeqCamData().removeROIsContainingString("spot");
		exp.getCages().transferCageSpotsToSequenceAsROIs(exp.getSeqCamData(), allSpots);
		exp.saveSpots_File();
		if (duplicateResolvedCount > 0) {
			JOptionPane.showMessageDialog(this,
					"Several spots shared the same cage row/column label.\n"
							+ "Names were made unique by adjusting column indices.\n"
							+ "Those spots are highlighted in red (" + duplicateResolvedCount + " spot(s)).",
					"Duplicate spot positions", JOptionPane.WARNING_MESSAGE);
		}
	}

}
