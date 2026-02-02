package plugins.fmp.multiSPOTS.dlg.spots;

import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

import icy.gui.dialog.ConfirmDialog;
import icy.roi.ROI2D;
import icy.type.geom.Polygon2D;
import icy.type.geom.Polyline2D;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.kernel.roi.roi2d.ROI2DPolyLine;
import plugins.kernel.roi.roi2d.ROI2DPolygon;
import plugins.kernel.roi.roi2d.ROI2DShape;

public class Edit extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7582410775062671523L;

	private JButton editSpotsButton = new JButton("Change spots position with time");
	private JCheckBox showFrameButton = new JCheckBox("Select spots within frame");
	private JToggleButton displaySnakeButton = new JToggleButton("Display snake over spots");
	private JButton updateSpotsFromSnakeButton = new JButton("Center spots to snake");
	private MultiSPOTS parent0 = null;
	private EditPositionWithTime editSpotsTable = null;

	private final String dummyname = "perimeter_enclosing";
	private ROI2DPolygon spotsFrame = null;
	private ArrayList<Spot> enclosedSpots = null;
	private ROI2DPolyLine snakeRoi = null;

	private JButton erodeButton = new JButton("Contract spots");
	private JButton dilateButton = new JButton("Dilate spots");

	void init(GridLayout capLayout, MultiSPOTS parent0) {
		this.parent0 = parent0;
		setLayout(capLayout);
		FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);
		flowLayout.setVgap(0);

		JPanel panel0 = new JPanel(flowLayout);
		panel0.add(showFrameButton);
		panel0.add(displaySnakeButton);
		panel0.add(updateSpotsFromSnakeButton);
		add(panel0);

		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(dilateButton);
		panel1.add(erodeButton);
		add(panel1);

		JPanel panel2 = new JPanel(flowLayout);
		panel2.add(editSpotsButton);
		add(panel2);

		defineActionListeners();
		updateButtonsState(false);
	}

	private void updateButtonsState(boolean isFrameSelected) {
		displaySnakeButton.setEnabled(isFrameSelected);
		updateSpotsFromSnakeButton.setEnabled((displaySnakeButton.isSelected()) ? isFrameSelected : false);
	}

	private void defineActionListeners() {
		editSpotsButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
				if (exp == null)
					return;
				openDialog();
			}
		});

		showFrameButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
				if (exp == null)
					return;
				updateButtonsState(showFrameButton.isSelected());
				showFrame(showFrameButton.isSelected());
			}
		});

		displaySnakeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
				if (exp == null)
					return;
				if (displaySnakeButton.isSelected())
					displaySnake(exp);
				else
					removeSnake(exp);
				updateButtonsState(showFrameButton.isSelected());
			}
		});

		updateSpotsFromSnakeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
				if (exp == null)
					return;
				updateSpotsFromSnake(exp);
			}
		});

		dilateButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
				if (exp == null)
					return;
				resizeSpots(exp, +1);
			}
		});

		erodeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
				if (exp == null)
					return;
				resizeSpots(exp, -1);
			}
		});
	}

	private Point getFramePosition() {
		Point point = new Point();
		Component currComponent = (Component) editSpotsButton;
		int index = 0;
		int indexMax = 12; // TODO : why 12??
		while (currComponent != null && index < indexMax) {
			Point relativeLocation = currComponent.getLocation();
			point.translate(relativeLocation.x, relativeLocation.y);
			currComponent = currComponent.getParent();
			index++;
		}
		return point;
	}

	public void openDialog() {
		Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
		if (exp != null) {
//			exp.spotsArray.transferDescriptionToSpots();
			if (editSpotsTable == null)
				editSpotsTable = new EditPositionWithTime();
			editSpotsTable.initialize(parent0, getFramePosition());
		}
	}

	public void closeDialog() {
		editSpotsTable.close();
	}

	// --------------------------------------

	private void showFrame(boolean show) {
		Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
		if (exp == null)
			return;

		if (show) {
			int t = exp.getSeqCamData().getSequence().getFirstViewer().getPositionT();
			addFrameAroundSpots(t, exp);
		} else {
			removeSpotsFrame(exp);
			removeSnake(exp);
		}
	}

	private void addFrameAroundSpots(int t, Experiment exp) {
		if (spotsFrame == null) {
			ArrayList<ROI2D> listRoisAtT = new ArrayList<ROI2D>();
			for (Spot spot : exp.spotsArray.spotsList) {
				ROI2DAlongT kymoROI2D = spot.getROIAtT(t);
				listRoisAtT.add(kymoROI2D.getRoi_in());
			}
			Polygon2D polygon = Utilities.getPolygonEnclosingROI2Ds(listRoisAtT);

			spotsFrame = new ROI2DPolygon(polygon);
			spotsFrame.setName(dummyname);
			spotsFrame.setColor(Color.YELLOW);
		}

		exp.getSeqCamData().getSequence().removeROI(spotsFrame);
		exp.getSeqCamData().getSequence().addROI(spotsFrame);
		exp.getSeqCamData().getSequence().setSelectedROI(spotsFrame);
		removeSnake(exp);
	}

	private void displaySnake(Experiment exp) {
		enclosedSpots = exp.spotsArray.getSpotsEnclosed(spotsFrame);
		if (enclosedSpots.size() > 0) {
			ArrayList<Point2D> listPoint = new ArrayList<Point2D>();
			for (Spot spot : enclosedSpots) {
				listPoint.add(new Point2D.Double(spot.spotXCoord, spot.spotYCoord));
			}
			snakeRoi = new ROI2DPolyLine(listPoint);
			exp.getSeqCamData().getSequence().addROI(snakeRoi);
			exp.getSeqCamData().getSequence().setSelectedROI(snakeRoi);
		}
	}

	private void removeSnake(Experiment exp) {
		if (snakeRoi != null)
			exp.getSeqCamData().getSequence().removeROI(snakeRoi);
		snakeRoi = null;
	}

	private void removeSpotsFrame(Experiment exp) {
		if (spotsFrame != null)
			exp.getSeqCamData().getSequence().removeROI(spotsFrame);
		spotsFrame = null;
	}

	private void updateSpotsFromSnake(Experiment exp) {
		if (enclosedSpots != null && enclosedSpots.size() > 0 && snakeRoi != null) {
			Polyline2D snake = snakeRoi.getPolyline2D();
			int i = 0;
			for (Spot spot : enclosedSpots) {
				double deltax = snake.xpoints[i] - spot.spotXCoord;
				double deltay = snake.ypoints[i] - spot.spotYCoord;
				spot.spotXCoord = (int) snake.xpoints[i];
				spot.spotYCoord = (int) snake.ypoints[i];
				spot.getRoi().translate(deltax, deltay);
				i++;
			}
		}
	}

	private void resizeSpots(Experiment exp, int delta) {
		enclosedSpots = exp.spotsArray.getSpotsEnclosed(spotsFrame);
		if (enclosedSpots.size() > 0) {
			for (Spot spot : enclosedSpots) {
				ROI2DShape roi = (ROI2DShape) spot.getRoi();
				exp.getSeqCamData().getSequence().removeROI(roi);
				roi = (ROI2DShape) Utilities.resizeROI(roi, delta);
				spot.setRoi(roi);
				exp.getSeqCamData().getSequence().addROI(roi);
			}
		} else {
			ConfirmDialog.confirm("At least one spot must be selected");
		}
	}

}
