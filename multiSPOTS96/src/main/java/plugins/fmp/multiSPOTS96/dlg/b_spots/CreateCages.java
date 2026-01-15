package plugins.fmp.multiSPOTS96.dlg.b_spots;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;
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
import icy.sequence.Sequence;
import icy.type.geom.Polygon2D;
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ExperimentUtils;
import plugins.fmp.multitools.experiment.cages.Cage;
import plugins.fmp.multitools.experiment.cages.Cages;
import plugins.fmp.multitools.tools.ROI2D.GeometryException;
import plugins.fmp.multitools.tools.ROI2D.ROI2DGrid;
import plugins.fmp.multitools.tools.ROI2D.ValidationException;
import plugins.fmp.multitools.tools.polyline.PolygonUtilities;
import plugins.kernel.roi.roi2d.ROI2DPolygon;

public class CreateCages extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -5257698990389571518L;
	private JButton createFrameButton = new JButton("(1) Create frame over all cages");
	private JButton createGridButton = new JButton("(2) Grid");
	private JButton createCagesButton = new JButton("(3) Create cages");
//	private JButton editCagesButton = new JButton("Edit cages infos...");

	private JSpinner nCagesPerPlateAlongXJSpinner = new JSpinner(new SpinnerNumberModel(6, 0, 10000, 1));
	private JSpinner nCagesPerPlateAlongYJSpinner = new JSpinner(new SpinnerNumberModel(8, 0, 10000, 1));
	private JSpinner width_intervalTextField = new JSpinner(new SpinnerNumberModel(4, 0, 10000, 1));

	private int width_interval = 1;
	private int height_interval = 1;
	private Polygon2D polygon2D = null;
	ROI2DPolygon roiCagesPerimeter = null;
	private ROI2DGrid roiGrid = null;
	private MultiSPOTS96 parent0;

	// -------------------------------------------------

	void init(GridLayout capLayout, MultiSPOTS96 parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);
		flowLayout.setVgap(0);

		JPanel panel0 = new JPanel(flowLayout);
		panel0.add(createFrameButton);
		createFrameButton.setHorizontalAlignment(SwingConstants.LEFT);
		add(panel0);

		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(createGridButton);
		createGridButton.setHorizontalAlignment(SwingConstants.LEFT);
		panel1.add(new JLabel("with"));
		panel1.add(nCagesPerPlateAlongXJSpinner);
		nCagesPerPlateAlongXJSpinner.setPreferredSize(new Dimension(40, 20));
		panel1.add(new JLabel("cols  x "));
		panel1.add(nCagesPerPlateAlongYJSpinner);
		nCagesPerPlateAlongYJSpinner.setPreferredSize(new Dimension(40, 20));
		panel1.add(new JLabel("rows"));
		add(panel1);

		JPanel panel2 = new JPanel(flowLayout);
		panel2.add(createCagesButton);
		createCagesButton.setHorizontalAlignment(SwingConstants.LEFT);
		panel2.add(new JLabel("with"));
		panel2.add(width_intervalTextField);
		width_intervalTextField.setPreferredSize(new Dimension(40, 20));
		panel2.add(new JLabel("pixels spacing"));
		add(panel2);

		JPanel panel3 = new JPanel(flowLayout);
//		panel3.add(editCagesButton);
		add(panel3);

		defineActionListeners();
	}

	private void defineActionListeners() {
		createFrameButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					selectRoiPerimeterEnclosingCages(exp);
					exp.getSeqCamData().removeROIsContainingString("cage");
					removeGrid(exp);
				}
			}
		});

		createGridButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					createGrid(exp);
				}
			}
		});

		createCagesButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					createCages(exp);
					removeGrid(exp);
					exp.saveCages_File();
				}
			}
		});

	}

	private void removeGrid(Experiment exp) {
		if (roiGrid == null)
			return;
		exp.getSeqCamData().getSequence().removeROIs(roiGrid.getHorizontalRois(), false);
		exp.getSeqCamData().getSequence().removeROIs(roiGrid.getVerticalRois(), false);
	}

	private void createCages(Experiment exp) {
		polygon2D = getPolygonFromCagesPerimeterRoi(exp);
		if (polygon2D != null) {
			if (roiGrid == null)
				createGrid(exp);
			createCagesFromGrid(exp, roiGrid);
			ExperimentUtils.transferCagesToCamDataSequence(exp);
		}
	}

	private Polygon2D getPolygonFromCagesPerimeterRoi(Experiment exp) {
		selectRoiPerimeterEnclosingCages(exp);
		ROI2D roi = (ROI2D) exp.getSeqCamData().getSequence().getSelectedROI();
		polygon2D = PolygonUtilities.orderVerticesOf4CornersPolygon(((ROI2DPolygon) roi).getPolygon());
		exp.getSeqCamData().getSequence().removeROI(roi);
		return polygon2D;
	}

	void updateNColumnsFieldFromSequence() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null) {
			int nrois = exp.getCages().cagesList.size();
			if (nrois > 0) {
				nCagesPerPlateAlongXJSpinner.setValue(exp.getCages().nCagesAlongX);
				nCagesPerPlateAlongYJSpinner.setValue(exp.getCages().nCagesAlongY);
			}
		}
	}

	private void selectRoiPerimeterEnclosingCages(Experiment exp) {
		Sequence seq = exp.getSeqCamData().getSequence();
		if (roiCagesPerimeter == null) {
			roiCagesPerimeter = new ROI2DPolygon(getPolygonEnclosingAllCages(exp));
			roiCagesPerimeter.setName("perimeter");
		}
		if (!seq.contains(roiCagesPerimeter))
			seq.addROI(roiCagesPerimeter);
		roiCagesPerimeter.setColor(Color.orange);
		seq.setSelectedROI(roiCagesPerimeter);
	}

	private Polygon2D getPolygonEnclosingAllCages(Experiment exp) {
		if (exp.getCages().cagesList.size() > 0) {
			polygon2D = exp.getCages().getPolygon2DEnclosingAllCages();
		} else {
			// Rectangle rect = exp.getSeqCamData().getSequence().getBounds2D();
			Rectangle rect = new Rectangle(318, 125, 1260, 836);
			List<Point2D> points = new ArrayList<Point2D>();
			points.add(new Point2D.Double(rect.x, rect.y));
			points.add(new Point2D.Double(rect.x, rect.y + rect.height));
			points.add(new Point2D.Double(rect.x + rect.width, rect.y + rect.height));
			points.add(new Point2D.Double(rect.x + rect.width, rect.y));

			polygon2D = new Polygon2D(points);
		}
		return polygon2D;
	}

	private void createCagesFromGrid(Experiment exp, ROI2DGrid roiGrid) {
		int n_columns = 10;
		int n_rows = 1;
		try {
			n_columns = (int) nCagesPerPlateAlongXJSpinner.getValue();
			n_rows = (int) nCagesPerPlateAlongYJSpinner.getValue();
			width_interval = (int) width_intervalTextField.getValue();
			height_interval = width_interval;
		} catch (Exception e) {
			new AnnounceFrame("Can't interpret ROI parameters value");
		}

		// erase existing cages
		exp.getSeqCamData().removeROIsContainingString("cage");
		exp.getCages().cagesList.clear();
		exp.setCages(new Cages(n_columns, n_rows));
		createCagesArrayFromGrid(exp, roiGrid, n_columns, n_rows, width_interval, height_interval);
	}

	private void createCagesArrayFromGrid(Experiment exp, ROI2DGrid roiGrid, int ncolumns, int nrows,
			int width_interval, int height_interval) {
		Point2D.Double[][] grid = roiGrid.getGridPoints();
		// test if dimensions are ok
		if (grid.length != (ncolumns + 1) || grid[0].length != (nrows + 1)) {
			System.out.println("error in the dimensions of grid");
		}
		// generate cage frames
		String cageRoot = "cage";
		int index = 0;

		for (int row = 0; row < nrows; row++) {
			for (int column = 0; column < ncolumns; column++) {
				ROI2DPolygon roiP = createRoiPolygon(grid, column, row, width_interval, height_interval);
				roiP.setName(cageRoot + String.format("%03d", index));
				roiP.setColor(Color.magenta);

				Cage cage = new Cage(roiP);
				cage.getProperties().setCageID(index);
				cage.getProperties().setArrayIndex(index);
				cage.getProperties().setArrayColumn(column);
				cage.getProperties().setArrayRow(row);

				index++;
				exp.getSeqCamData().getSequence().addROI(roiP);
				exp.getCages().cagesList.add(cage);
			}
		}
	}

	private ROI2DPolygon createRoiPolygon(Point2D.Double[][] grid, int icol, int irow, int width, int height) {
		List<Point2D> points = new ArrayList<>();
		Point2D.Double pt = (Double) grid[icol][irow].clone();
		pt.x += width;
		pt.y += height;
		points.add(pt);

		pt = (Double) grid[icol][irow + 1].clone();
		pt.x += width;
		pt.y -= height;
		points.add(pt);

		pt = (Double) grid[icol + 1][irow + 1].clone();
		pt.x -= width;
		pt.y -= height;
		points.add(pt);

		pt = (Double) grid[icol + 1][irow].clone();
		pt.x -= width;
		pt.y += height;
		points.add(pt);

		ROI2DPolygon roiP = new ROI2DPolygon(points);
		return roiP;
	}

	private void createGrid(Experiment exp) {
		exp.getSeqCamData().removeROIsContainingString("cage");
		exp.getSeqCamData().removeROIsContainingString("row");
		exp.getSeqCamData().removeROIsContainingString("col");
		int n_columns = 6;
		int n_rows = 8;
		try {
			n_columns = (int) nCagesPerPlateAlongXJSpinner.getValue();
			n_rows = (int) nCagesPerPlateAlongYJSpinner.getValue();
		} catch (Exception e) {
			new AnnounceFrame("Can't interpret one of the ROI parameters value");
		}

		Polygon2D polyGon = getPolygonFromCagesPerimeterRoi(exp);
		roiGrid = new ROI2DGrid();
		try {
			roiGrid.createGridFromFrame(polyGon, n_columns, n_rows);
			exp.getSeqCamData().getSequence().addROIs(roiGrid.getHorizontalRois(), false);
			exp.getSeqCamData().getSequence().addROIs(roiGrid.getVerticalRois(), false);
		} catch (ValidationException | GeometryException e) {
			System.err.println("Error creating grid from frame: " + e.getMessage());
			e.printStackTrace();
			new AnnounceFrame("Error creating grid: " + e.getMessage());
		}
	}

	public void clearTemporaryROIs() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null) {
			removeGrid(exp);
			exp.getSeqCamData().getSequence().removeROI(roiCagesPerimeter);
			roiGrid = null;
			polygon2D = null;
			roiCagesPerimeter = null;
		}
	}
}
