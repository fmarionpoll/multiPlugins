package plugins.fmp.multiSPOTS96.dlg.b_spots;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import icy.canvas.Canvas2D;
import icy.gui.dialog.ConfirmDialog;
import icy.gui.viewer.Viewer;
import icy.roi.ROI2D;
import icy.type.geom.Polygon2D;
import icy.type.geom.Polyline2D;
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.ROI2D.Utilities;
import plugins.kernel.roi.roi2d.ROI2DPolyLine;
import plugins.kernel.roi.roi2d.ROI2DPolygon;

public class EditSpots extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7582410775062671523L;

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
		panel0.add(new JLabel("select type of rois"));
		panel0.add(typeCombo);
		add(panel0);

		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(selectRoisCheckBox);
		panel1.add(displaySnakeCheckBox);
		panel1.add(centerRoisToSnakeButton);
		add(panel1);

		JPanel panel2 = new JPanel(flowLayout);
		panel2.add(dilateButton);
		panel2.add(erodeButton);
//		panel2.add(editSpotsWithTimeButton);
		add(panel2);

		defineActionListeners();
		updateButtonsStateAccordingToSelectRois(false, false);
	}

	private void defineActionListeners() {
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
			exp.seqCamData.getSequence().removeROI(roiPerimeter);
			roiPerimeter = null;
		}
	}

	private void setEnclosingFrame(Experiment exp) {
		exp.seqCamData.getSequence().removeROI(roiPerimeter);
		showSnake(exp, false);
		createPerimeterEnclosingRois(exp);
		exp.seqCamData.getSequence().addROI(roiPerimeter);
		exp.seqCamData.getSequence().setSelectedROI(roiPerimeter);

		makeSureRectangleIsVisible(exp, roiPerimeter.getBounds());
	}

	private void makeSureRectangleIsVisible(Experiment exp, Rectangle rect) {
		Viewer v = exp.seqCamData.getSequence().getFirstViewer();
		Canvas2D canvas = (Canvas2D) v.getCanvas();
		canvas.centerOn(rect);
	}

	private void showSnake(Experiment exp, boolean show) {
		exp.seqCamData.getSequence().removeROI(roiSnake);
		String selectedRoiType = (String) typeCombo.getSelectedItem();

		if (show) {
			exp.seqCamData.getSequence().removeROI(roiPerimeter);
			if (enclosedRois.size() > 0) {
				ArrayList<Point2D> listPoint = new ArrayList<Point2D>();
				for (ROI2D roi : enclosedRois) {
					Rectangle rect = roi.getBounds();
					Point2D.Double point = null;
					if (selectedRoiType.contains("cage"))
						point = new Point2D.Double(rect.getX(), rect.getY());
					else
						point = new Point2D.Double(rect.getCenterX(), rect.getCenterY());
					listPoint.add(point);
				}
				roiSnake = new ROI2DPolyLine(listPoint);
				roiSnake.setName("snake");
				exp.seqCamData.getSequence().addROI(roiSnake);
				exp.seqCamData.getSequence().setSelectedROI(roiSnake);

				exp.seqCamData.displaySpecificROIs(false, selectedRoiType);
				makeSureRectangleIsVisible(exp, roiSnake.getBounds());
			}
		} else {
			roiSnake = null;
			exp.seqCamData.getSequence().addROI(roiPerimeter);
			exp.seqCamData.displaySpecificROIs(true, selectedRoiType);
		}
	}

	private void createPerimeterEnclosingRois(Experiment exp) {
		String selectedRoiType = (String) typeCombo.getSelectedItem();
		ArrayList<ROI2D> listRoisPresent = exp.seqCamData.getROIsContainingString(selectedRoiType);
		ArrayList<ROI2D> listRoisSelected = new ArrayList<ROI2D>();
		for (ROI2D roi : listRoisPresent) {
			if (roi.isSelected())
				listRoisSelected.add(roi);
		}
		enclosedRois = listRoisSelected.size() > 0 ? listRoisSelected : listRoisPresent;

		Polygon2D polygon = Utilities.getPolygonEnclosingROI2Ds(enclosedRois, selectedRoiType);
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

		exp.seqCamData.displaySpecificROIs(true, selectedRoiType);
	}

	private void resizeRois(Experiment exp, int delta) {
		if (enclosedRois.size() > 0) {
			for (ROI2D roi : enclosedRois) {
				exp.seqCamData.getSequence().removeROI(roi);
				roi = Utilities.resizeROI(roi, delta);
				exp.seqCamData.getSequence().addROI(roi);
			}
		} else {
			ConfirmDialog.confirm("At least one spot must be selected");
		}
	}

	public void clearTemporaryROIs() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null) {
			exp.seqCamData.getSequence().removeROI(roiSnake);
			exp.seqCamData.getSequence().removeROI(roiPerimeter);
			roiSnake = null;
			roiPerimeter = null;
			displaySnakeCheckBox.setSelected(false);
			selectRoisCheckBox.setSelected(false);
		}
	}

}
