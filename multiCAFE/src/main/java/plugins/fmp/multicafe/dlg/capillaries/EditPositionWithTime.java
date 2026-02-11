package plugins.fmp.multicafe.dlg.capillaries;

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
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.tools.JComponents.CapillariesWithTimeTableModel;
import plugins.fmp.multitools.tools.ROI2D.AlongT;
import plugins.fmp.multitools.tools.ROI2D.ROI2DUtilities;
import plugins.kernel.roi.roi2d.ROI2DPolygon;

public class EditPositionWithTime extends JPanel implements ListSelectionListener {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	IcyFrame dialogFrame = null;

	private JButton addItemButton = new JButton("Add");
	private JButton deleteItemButton = new JButton("Delete current");
	private JButton deleteAllButton = new JButton("Delete all");
	private JButton saveCapillariesButton = new JButton("Update capillaries from ROIs");
	private JCheckBox showFrameButton = new JCheckBox("Show frame");
	private JButton fitToFrameButton = new JButton("Fit capillaries to frame");
	private JTable tableView = new JTable();

	private final String dummyname = "perimeter_enclosing_capillaries";
	private ROI2DPolygon envelopeRoi = null;
	private ROI2DPolygon envelopeRoi_initial = null;
	private MultiCAFE parent0 = null;

	private CapillariesWithTimeTableModel capillariesWithTimeTablemodel = null;

	public void initialize(MultiCAFE parent0, Point pt) {
		this.parent0 = parent0;
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null)
			exp.getCapillaries().unifyAlongTIntervalsForDialog();
		capillariesWithTimeTablemodel = new CapillariesWithTimeTableModel(parent0.expListComboLazy);

		JPanel topPanel = new JPanel(new GridLayout(3, 1));
		FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);

		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(new JLabel("Viewer frame T:"));
		panel1.add(addItemButton);
		panel1.add(deleteItemButton);
		panel1.add(deleteAllButton);
		topPanel.add(panel1);

		JPanel panel2 = new JPanel(flowLayout);
		panel2.add(showFrameButton);
		panel2.add(fitToFrameButton);
		panel2.add(saveCapillariesButton);
		topPanel.add(panel2);

		JPanel panel3 = new JPanel(flowLayout);
		panel3.add(saveCapillariesButton);
		topPanel.add(panel3);

		tableView.setModel(capillariesWithTimeTablemodel);
		tableView.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		tableView.setPreferredScrollableViewportSize(new Dimension(180, 300));
		tableView.setFillsViewportHeight(true);
		JScrollPane scrollPane = new JScrollPane(tableView);
		JPanel tablePanel = new JPanel();
		tablePanel.add(scrollPane);

		dialogFrame = new IcyFrame("Edit capillaries position", true, true);
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
				moveAllCapillaries();
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

		deleteAllButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				deleteAllRows();
			}
		});

		saveCapillariesButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				int selectedRow = tableView.getSelectedRow();
				saveCapillaries(selectedRow);
			}
		});
	}

	private void defineSelectionListener() {
		tableView.getSelectionModel().addListSelectionListener(this);
	}

	private void deleteAllRows() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;
		for (Capillary cap : exp.getCapillaries().getList()) {
			cap.retainFirstAlongT();
		}
		exp.save_capillaries_description_and_measures();
	}

	void close() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null) {
			exp.getCapillaries().compressRedundantAlongTPerCapillary();
			exp.save_capillaries_description_and_measures();
		}
		dialogFrame.close();
	}

	private void moveAllCapillaries() {
		if (envelopeRoi == null)
			return;
		Point2D pt0 = envelopeRoi_initial.getPosition2D();
		Point2D pt1 = envelopeRoi.getPosition2D();
		envelopeRoi_initial = new ROI2DPolygon(envelopeRoi.getPolygon2D());
		double deltaX = pt1.getX() - pt0.getX();
		double deltaY = pt1.getY() - pt0.getY();
		shiftPositionOfCapillaries(deltaX, deltaY);
	}

	private void shiftPositionOfCapillaries(double deltaX, double deltaY) {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		;
		if (exp == null)
			return;
		Sequence seq = exp.getSeqCamData().getSequence();
		ArrayList<ROI2D> listRois = seq.getROI2Ds();
		for (ROI2D roi : listRois) {
			if (!roi.getName().contains("line"))
				continue;
			Point2D point2d = roi.getPosition2D();
			roi.setPosition2D(new Point2D.Double(point2d.getX() + deltaX, point2d.getY() + deltaY));
		}
	}

	private void showFrame(boolean show) {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;

		if (show) {
			int t = exp.getSeqCamData().getSequence().getFirstViewer().getPositionT();
			// TODO select current interval and return only rois2D from that interval
			addFrameAroundCapillaries(t, exp);
		} else
			removeFrameAroundCapillaries(exp.getSeqCamData().getSequence());
	}

	private void addFrameAroundCapillaries(int t, Experiment exp) {
		ArrayList<ROI2D> listRoisAtT = new ArrayList<ROI2D>();
		for (Capillary cap : exp.getCapillaries().getList()) {
			AlongT kymoROI2D = cap.getAlongTAtT(t);
			listRoisAtT.add(kymoROI2D.getRoi());
		}
		Polygon2D polygon = ROI2DUtilities.getPolygonEnclosingCapillaries(listRoisAtT);

		removeFrameAroundCapillaries(exp.getSeqCamData().getSequence());
		envelopeRoi_initial = new ROI2DPolygon(polygon);
		envelopeRoi = new ROI2DPolygon(polygon);
		envelopeRoi.setName(dummyname);
		envelopeRoi.setColor(Color.YELLOW);

		exp.getSeqCamData().getSequence().addROI(envelopeRoi);
		exp.getSeqCamData().getSequence().setSelectedROI(envelopeRoi);
	}

	private void removeFrameAroundCapillaries(Sequence seq) {
		seq.removeROI(envelopeRoi);
		seq.removeROI(envelopeRoi_initial);
	}

	private void addTableItem() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;

		Viewer v = exp.getSeqCamData().getSequence().getFirstViewer();
		long intervalT = v.getPositionT();

		if (exp.getCapillaries().findKymoROI2DIntervalStart(intervalT) < 0) {
			exp.getCapillaries().addKymoROI2DInterval(intervalT);
			exp.save_capillaries_description_and_measures();
		}
	}

	private void deleteTableItem(int selectedRow) {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;

		Viewer v = exp.getSeqCamData().getSequence().getFirstViewer();
		long intervalT = v.getPositionT();

		if (exp.getCapillaries().findKymoROI2DIntervalStart(intervalT) >= 0) {
			exp.getCapillaries().deleteKymoROI2DInterval(intervalT);
			exp.save_capillaries_description_and_measures();
		}
	}

	private void displayCapillariesForSelectedInterval(int selectedRow) {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;
		Sequence seq = exp.getSeqCamData().getSequence();

		int intervalT = (int) exp.getCapillaries().getKymoROI2DIntervalsStartAt(selectedRow);
		seq.removeAllROI();
		List<ROI2D> listRois = new ArrayList<ROI2D>();
		for (Capillary cap : exp.getCapillaries().getList()) {
			listRois.add(cap.getAlongTAtT((int) intervalT).getRoi());
		}
		seq.addROIs(listRois, false);

		Viewer v = seq.getFirstViewer();
		v.setPositionT((int) intervalT);
	}

	private void saveCapillaries(int selectedRow) {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;
		Sequence seq = exp.getSeqCamData().getSequence();

		int intervalT = (int) exp.getCapillaries().getKymoROI2DIntervalsStartAt(selectedRow);
		List<ROI2D> listRois = seq.getROI2Ds();
		for (ROI2D roi : listRois) {
			if (!roi.getName().contains("line"))
				continue;
			Capillary cap = exp.getCapillaries().getCapillaryFromRoiName(roi.getName());
			if (cap != null) {
				ROI2D roilocal = (ROI2D) roi.getCopy();
				cap.getAlongTAtT(intervalT).setRoi(roilocal);
			}
		}
		exp.save_capillaries_description_and_measures();
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
		displayCapillariesForSelectedInterval(selectedRow);
		showFrame(showFrameButton.isSelected());
	}

}
