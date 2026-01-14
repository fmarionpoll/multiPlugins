package plugins.fmp.multiSPOTS.dlg.spots;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import icy.gui.frame.progress.AnnounceFrame;
import icy.roi.ROI2D;
import icy.type.geom.Polygon2D;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multiSPOTS.experiment.Experiment;
import plugins.fmp.multiSPOTS.experiment.ExperimentUtils;
import plugins.fmp.multiSPOTS.experiment.SequenceCamData;
import plugins.fmp.multiSPOTS.experiment.spots.Spot;
import plugins.fmp.multiSPOTS.experiment.spots.SpotsArray;
import plugins.fmp.multiSPOTS.tools.ROI2D.ROIUtilities;
import plugins.fmp.multiSPOTS.tools.polyline.PolygonUtilities;
import plugins.kernel.roi.roi2d.ROI2DEllipse;
import plugins.kernel.roi.roi2d.ROI2DPolygon;

public class CreateSpots extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -5257698990389571518L;

	private JButton displayFrameDButton = new JButton("(1) Display frame");
	private JButton createCirclesButton = new JButton("(2) Create circles");

	private JSpinner nColsPerCageJSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 500, 1));
	private JSpinner nRowsPerCageJSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 500, 1));
	private JSpinner nFliesPerCageJSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 500, 1));
	private JSpinner pixelRadiusSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 1000, 1));

	private JSpinner nRowsJSpinner = new JSpinner(new SpinnerNumberModel(8, 2, 100, 1));
	private JSpinner nColumnsJSpinner = new JSpinner(new SpinnerNumberModel(12, 2, 100, 1));

	private Polygon2D polygon2D = null;
	private String[] flyString = new String[] { "fly", "flies" };
	private JLabel flyLabel = new JLabel(flyString[0]);

	private MultiSPOTS parent0 = null;
	private boolean silent = false;

	// ----------------------------------------------------------

	void init(GridLayout capLayout, MultiSPOTS parent0) {
		setLayout(capLayout);
		FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);
		flowLayout.setVgap(0);

		JPanel panel0 = new JPanel(flowLayout);
		panel0.add(displayFrameDButton);
		panel0.add(createCirclesButton);
		panel0.add(pixelRadiusSpinner);
		pixelRadiusSpinner.setPreferredSize(new Dimension(40, 20));
		panel0.add(new JLabel("pixels"));

		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(new JLabel("Spots:"));
		panel1.add(new JLabel("cols"));
		panel1.add(nColumnsJSpinner);
		nColumnsJSpinner.setPreferredSize(new Dimension(40, 20));
		panel1.add(new JLabel("rows"));
		panel1.add(nRowsJSpinner);
		nRowsJSpinner.setPreferredSize(new Dimension(40, 20));

		JPanel panel2 = new JPanel(flowLayout);
		panel2.add(new JLabel("Grouped within n cols"));
		panel2.add(nColsPerCageJSpinner);
		nColsPerCageJSpinner.setPreferredSize(new Dimension(40, 20));
		panel2.add(new JLabel("& n rows"));
		panel2.add(nRowsPerCageJSpinner);
		nRowsPerCageJSpinner.setPreferredSize(new Dimension(40, 20));
		panel2.add(new JLabel("with"));

		panel2.add(nFliesPerCageJSpinner);
		nFliesPerCageJSpinner.setPreferredSize(new Dimension(40, 20));
		panel2.add(flyLabel);

		add(panel0);
		add(panel1);
		add(panel2);

		defineActionListeners();
		this.parent0 = parent0;
	}

	private void defineActionListeners() {
		displayFrameDButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				create2DPolygon();
			}
		});

		createCirclesButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
				if (exp != null) {
					polygon2D = getPolygonEnclosingSpotsFromSelectedRoi(exp);
					if (polygon2D != null) {
						createSpotsFromPolygon(exp, polygon2D);
						ExperimentUtils.transferSpotsToCamDataSequence(exp);
						int nbFliesPerCage = (int) nFliesPerCageJSpinner.getValue();
						exp.spotsArray.initSpotsWithNFlies(nbFliesPerCage);
					}
				}
			}
		});

		nFliesPerCageJSpinner.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				int i = (int) nFliesPerCageJSpinner.getValue() > 1 ? 1 : 0;
				flyLabel.setText(flyString[i]);
				nFliesPerCageJSpinner.requestFocus();
			}
		});

		nColsPerCageJSpinner.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
				if (exp != null)
					updateCageDescriptorsOfSpots(exp);
			}
		});

		nRowsPerCageJSpinner.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
				if (exp != null)
					updateCageDescriptorsOfSpots(exp);
			}
		});
	}

	// ---------------------------------

	private void create2DPolygon() {
		Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
		if (exp == null)
			return;
		SequenceCamData seqCamData = exp.seqCamData;
		final String dummyname = "perimeter_enclosing_spots";
		if (isRoiPresent(seqCamData, dummyname))
			return;

		ROI2DPolygon roi = new ROI2DPolygon(getSpotsPolygon(exp));
		roi.setName(dummyname);
		seqCamData.seq.addROI(roi);
		seqCamData.seq.setSelectedROI(roi);
	}

	private boolean isRoiPresent(SequenceCamData seqCamData, String dummyname) {
		ArrayList<ROI2D> listRois = seqCamData.seq.getROI2Ds();
		for (ROI2D roi : listRois) {
			if (roi.getName().equals(dummyname))
				return true;
		}
		return false;
	}

	private Polygon2D getSpotsPolygon(Experiment exp) {
		if (polygon2D == null) {
			if (exp.spotsArray.spotsList.size() > 0) {
				polygon2D = exp.spotsArray.getPolygon2DEnclosingAllSpots();
			} else {
				Rectangle rect = exp.seqCamData.seq.getBounds2D();
				List<Point2D> points = new ArrayList<Point2D>();
				points.add(new Point2D.Double(rect.x + rect.width / 5, rect.y + rect.height / 5));
				points.add(new Point2D.Double(rect.x + rect.width * 4 / 5, rect.y + rect.height / 5));
				points.add(new Point2D.Double(rect.x + rect.width * 4 / 5, rect.y + rect.height * 2 / 3));
				points.add(new Point2D.Double(rect.x + rect.width / 5, rect.y + rect.height * 2 / 3));
				polygon2D = new Polygon2D(points);
			}
		}
		return polygon2D;
	}

	private Polygon2D getPolygonEnclosingSpotsFromSelectedRoi(Experiment exp) {
		SequenceCamData seqCamData = exp.seqCamData;
		ROI2D roi = seqCamData.seq.getSelectedROI2D();
		if (!(roi instanceof ROI2DPolygon)) {
			new AnnounceFrame("The frame must be a ROI2D Polygon");
			return null;
		}
		polygon2D = PolygonUtilities.orderVerticesOf4CornersPolygon(((ROI2DPolygon) roi).getPolygon());
		seqCamData.seq.removeROI(roi);
		return polygon2D;
	}

	private void createSpotsFromPolygon(Experiment exp, Polygon2D polygon2D) {
		int n_columns = 10;
		int n_rows = 1;
		int radius = 3;
		try {
			n_columns = (int) nColumnsJSpinner.getValue();
			n_rows = (int) nRowsJSpinner.getValue();
			radius = (int) pixelRadiusSpinner.getValue();
		} catch (Exception e) {
			new AnnounceFrame("Can't interpret one of the ROI parameters value");
		}
		// erase existing spots
		exp.seqCamData.seq.removeROIs(ROIUtilities.getROIsContainingString("spot", exp.seqCamData.seq), false);
		exp.spotsArray.spotsList.clear();
		exp.spotsArray = new SpotsArray();
		Point2D.Double[][] arrayPoints = PolygonUtilities.createArrayOfPointsFromPolygon(polygon2D, n_columns, n_rows);
		convertPoint2DArrayToSpots(exp, arrayPoints, n_columns, n_rows, radius);
		updateCageDescriptorsOfSpots(exp);
	}

	private void convertPoint2DArrayToSpots(Experiment exp, Point2D.Double[][] arrayPoints, int nbcols, int nbrows,
			int radius) {
		exp.spotsArray.nColumnsPerPlate = nbcols;
		exp.spotsArray.nRowsPerPlate = nbrows;
		int spotIndex = 0;
		for (int row = 0; row < nbrows; row++) {
			for (int column = 0; column < nbcols; column++) {
				Point2D point = arrayPoints[column][row];
				double x = point.getX() - radius;
				double y = point.getY() - radius;
				Ellipse2D ellipse = new Ellipse2D.Double(x, y, 2 * radius, 2 * radius);
				ROI2DEllipse roiEllipse = new ROI2DEllipse(ellipse);
				roiEllipse.setName("spot_" + String.format("%03d", row) + String.format("%03d", column));

				Spot spot = new Spot(roiEllipse);
				spot.plateIndex = spotIndex;
				spot.plateColumn = column;
				spot.plateRow = row;
				spot.spotRadius = radius;
				spot.spotXCoord = (int) point.getX();
				spot.spotYCoord = (int) point.getY();
				try {
					spot.spotNPixels = (int) roiEllipse.getNumberOfPoints();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				exp.spotsArray.spotsList.add(spot);
				spotIndex++;
			}
		}
	}

	private void updateCageDescriptorsOfSpots(Experiment exp) {
		if (silent)
			return;

		int nColsPerCage = (int) nColsPerCageJSpinner.getValue();
		int nRowsPerCage = (int) nRowsPerCageJSpinner.getValue();
		exp.spotsArray.updatePlateIndexToCageIndexes(nColsPerCage, nRowsPerCage);
	}

	public void updateDialog(Experiment exp) {
		if (exp != null) {
			silent = true;
			nColsPerCageJSpinner.setValue(exp.spotsArray.nColumnsPerCage);
			nRowsPerCageJSpinner.setValue(exp.spotsArray.nRowsPerCage);
			silent = false;
		}
	}

}
