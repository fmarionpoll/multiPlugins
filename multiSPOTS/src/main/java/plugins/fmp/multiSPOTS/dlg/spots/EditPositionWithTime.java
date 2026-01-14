package plugins.fmp.multiSPOTS.dlg.spots;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import icy.gui.frame.IcyFrame;
import icy.gui.viewer.Viewer;
import icy.roi.ROI2D;
import icy.sequence.Sequence;
import icy.type.geom.Polygon2D;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multiSPOTS.experiment.Experiment;
import plugins.fmp.multiSPOTS.experiment.spots.Spot;
import plugins.fmp.multiSPOTS.tools.JComponents.TableModelSpotWithTime;
import plugins.fmp.multiSPOTS.tools.ROI2D.ROI2DAlongT;
import plugins.fmp.multiSPOTS.tools.ROI2D.Utilities;
import plugins.kernel.roi.roi2d.ROI2DPolygon;

public class EditPositionWithTime extends JPanel implements ListSelectionListener {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	IcyFrame dialogFrame = null;

	private JButton addItemButton = new JButton("Add");
	private JButton deleteItemButton = new JButton("Delete");
	private JButton saveSpotsButton = new JButton("Save spots");
	private JCheckBox showFrameButton = new JCheckBox("Show frame");
	private JButton fitToFrameButton = new JButton("Fit spots to frame");
	private JTable tableView = new JTable();

	private final String dummyname = "perimeter_enclosing";
	private ROI2DPolygon envelopeRoi = null;
	private ROI2DPolygon envelopeRoi_initial = null;
	private MultiSPOTS parent0 = null;

	private TableModelSpotWithTime spotsWithTimeTablemodel = null;

	public void initialize(MultiSPOTS parent0, Point pt) {
		this.parent0 = parent0;
		spotsWithTimeTablemodel = new TableModelSpotWithTime(parent0.expListCombo);

		JPanel topPanel = new JPanel(new GridLayout(3, 1));
		FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);

		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(new JLabel("Viewer frame T:"));
		panel1.add(addItemButton);
		panel1.add(deleteItemButton);
		topPanel.add(panel1);

		JPanel panel2 = new JPanel(flowLayout);
		panel2.add(showFrameButton);
		panel2.add(fitToFrameButton);
		panel2.add(saveSpotsButton);
		topPanel.add(panel2);

		JPanel panel3 = new JPanel(flowLayout);
		panel3.add(saveSpotsButton);
		topPanel.add(panel3);

		tableView.setModel(spotsWithTimeTablemodel);
		tableView.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		tableView.setPreferredScrollableViewportSize(new Dimension(180, 300));
		tableView.setFillsViewportHeight(true);
		JScrollPane scrollPane = new JScrollPane(tableView);
		JPanel tablePanel = new JPanel();
		tablePanel.add(scrollPane);

		dialogFrame = new IcyFrame("Edit spots position", true, true);
		dialogFrame.add(topPanel, BorderLayout.NORTH);
		dialogFrame.add(tablePanel, BorderLayout.CENTER);
		dialogFrame.setLocation(pt);

		dialogFrame.pack();
		dialogFrame.addToDesktopPane();
		dialogFrame.requestFocus();
		dialogFrame.setVisible(true);

		defineActionListeners();
		defineSelectionListener();

		fitToFrameButton.setEnabled(false);
	}

	private void defineActionListeners() {

		fitToFrameButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				moveAllSpots();
			}
		});

		showFrameButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				fitToFrameButton.setEnabled(showFrameButton.isSelected());
				showFrame(showFrameButton.isSelected());
			}
		});

		addItemButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				addTableItem();
			}
		});

		deleteItemButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				int selectedRow = tableView.getSelectedRow();
				deleteTableItem(selectedRow);
			}
		});

		saveSpotsButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				int selectedRow = tableView.getSelectedRow();
				saveSpots(selectedRow);
			}
		});
	}

	private void defineSelectionListener() {
		tableView.getSelectionModel().addListSelectionListener(this);
	}

	void close() {
		dialogFrame.close();
	}

	private void moveAllSpots() {
		if (envelopeRoi == null)
			return;
		Point2D pt0 = envelopeRoi_initial.getPosition2D();
		Point2D pt1 = envelopeRoi.getPosition2D();
		envelopeRoi_initial = new ROI2DPolygon(envelopeRoi.getPolygon2D());
		double deltaX = pt1.getX() - pt0.getX();
		double deltaY = pt1.getY() - pt0.getY();
		shiftPositionOfSpots(deltaX, deltaY);
	}

	private void shiftPositionOfSpots(double deltaX, double deltaY) {
		Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
		if (exp == null)
			return;
		Sequence seq = exp.seqCamData.seq;
		ArrayList<ROI2D> listRois = seq.getROI2Ds();
		for (ROI2D roi : listRois) {
			if (!roi.getName().contains("spot"))
				continue;
			Point2D point2d = roi.getPosition2D();
			roi.setPosition2D(new Point2D.Double(point2d.getX() + deltaX, point2d.getY() + deltaY));
		}
	}

	private void showFrame(boolean show) {
		Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
		if (exp == null)
			return;

		if (show) {
			int t = exp.seqCamData.seq.getFirstViewer().getPositionT();
			// TODO select current interval and return only rois2D from that interval
			addFrameAroundSpots(t, exp);
		} else
			removeFrameAroundSpots(exp.seqCamData.seq);
	}

	private void addFrameAroundSpots(int t, Experiment exp) {
		ArrayList<ROI2D> listRoisAtT = new ArrayList<ROI2D>();
		for (Spot spot : exp.spotsArray.spotsList) {
			ROI2DAlongT kymoROI2D = spot.getROIAtT(t);
			listRoisAtT.add(kymoROI2D.getRoi_in());
		}
		Polygon2D polygon = Utilities.getPolygonEnclosingROI2Ds(listRoisAtT);

		removeFrameAroundSpots(exp.seqCamData.seq);
		envelopeRoi_initial = new ROI2DPolygon(polygon);
		envelopeRoi = new ROI2DPolygon(polygon);
		envelopeRoi.setName(dummyname);
		envelopeRoi.setColor(Color.YELLOW);

		exp.seqCamData.seq.addROI(envelopeRoi);
		exp.seqCamData.seq.setSelectedROI(envelopeRoi);
	}

	private void removeFrameAroundSpots(Sequence seq) {
		seq.removeROI(envelopeRoi);
		seq.removeROI(envelopeRoi_initial);
	}

	private void addTableItem() {
		Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
		if (exp == null)
			return;

		Viewer v = exp.seqCamData.seq.getFirstViewer();
		long intervalT = v.getPositionT();

		if (exp.spotsArray.findKymoROI2DIntervalStart(intervalT) < 0) {
			exp.spotsArray.addKymoROI2DInterval(intervalT);
		}
	}

	private void deleteTableItem(int selectedRow) {
		Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
		if (exp == null)
			return;

		Viewer v = exp.seqCamData.seq.getFirstViewer();
		long intervalT = v.getPositionT();

		if (exp.spotsArray.findKymoROI2DIntervalStart(intervalT) >= 0) {
			exp.spotsArray.deleteKymoROI2DInterval(intervalT);
		}
	}

	private void displaySpotsForSelectedInterval(int selectedRow) {
		Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
		if (exp == null)
			return;
		Sequence seq = exp.seqCamData.seq;

		int intervalT = (int) exp.spotsArray.getKymoROI2DIntervalsStartAt(selectedRow);
		seq.removeAllROI();
		List<ROI2D> listRois = new ArrayList<ROI2D>();
		for (Spot spot : exp.spotsArray.spotsList)
			listRois.add(spot.getROIAtT((int) intervalT).getRoi_in());
		seq.addROIs(listRois, false);

		Viewer v = seq.getFirstViewer();
		v.setPositionT((int) intervalT);
	}

	private void saveSpots(int selectedRow) {
		Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
		if (exp == null)
			return;
		Sequence seq = exp.seqCamData.seq;

		int intervalT = (int) exp.spotsArray.getKymoROI2DIntervalsStartAt(selectedRow);
		List<ROI2D> listRois = seq.getROI2Ds();
		for (ROI2D roi : listRois) {
			if (!roi.getName().contains("line"))
				continue;
			Spot spot = exp.spotsArray.getSpotFromName(roi.getName());
			if (spot != null) {
				ROI2D roilocal = (ROI2D) roi.getCopy();
				spot.getROIAtT(intervalT).setRoi_in(roilocal);
			}
		}
	}

	@Override
	public void valueChanged(ListSelectionEvent e) {
		if (e.getValueIsAdjusting())
			return;

		int selectedRow = tableView.getSelectedRow();
		if (selectedRow < 0) {
			tableView.setRowSelectionInterval(0, 0);
			selectedRow = 0;
		}
		displaySpotsForSelectedInterval(selectedRow);
		showFrame(showFrameButton.isSelected());
	}

}
