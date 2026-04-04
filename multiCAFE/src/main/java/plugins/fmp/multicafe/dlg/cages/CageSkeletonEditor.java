package plugins.fmp.multicafe.dlg.cages;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import icy.gui.dialog.MessageDialog;
import icy.gui.frame.IcyFrame;
import icy.roi.ROI2D;
import icy.sequence.Sequence;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.Comparators;
import plugins.kernel.roi.roi2d.ROI2DLine;
import plugins.kernel.roi.roi2d.ROI2DPolygon;

public class CageSkeletonEditor {
	private static final String SKELETON_PREFIX = "skelCageEditor_";

	private CageSkeletonEditor() {
	}

	public static void open(MultiCAFE parent0, Experiment exp) {
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null) {
			return;
		}

		Sequence sequence = exp.getSeqCamData().getSequence();
		List<ROI2D> cageRois = getSortedCageRois(sequence);
		if (cageRois.isEmpty()) {
			MessageDialog.showDialog("No cage ROI found to edit", MessageDialog.INFORMATION_MESSAGE);
			return;
		}

		int initialWidth = estimateInitialCageWidth(cageRois);
		final EditorState state = new EditorState(exp, sequence, initialWidth);
		state.originalCageRois = copyRoiList(cageRois);
		addSkeletonsFromCages(state, cageRois);

		IcyFrame frame = new IcyFrame("Edit cages from skeletons", true, true, true, true);
		state.frame = frame;
		frame.setLayout(new GridLayout(2, 1));

		JPanel controlsPanel = buildControlsPanel(state);
		JPanel actionsPanel = buildActionsPanel(state);
		frame.add(controlsPanel);
		frame.add(actionsPanel);

		frame.addToDesktopPane();
		frame.pack();
		frame.center();
		frame.setVisible(true);
	}

	private static JPanel buildControlsPanel(EditorState state) {
		FlowLayout flow = new FlowLayout(FlowLayout.LEFT);
		flow.setVgap(0);
		JPanel panel = new JPanel(flow);
		panel.add(new JLabel("cage width"));
		panel.add(state.cageWidthSpinner);
		return panel;
	}

	private static JPanel buildActionsPanel(EditorState state) {
		FlowLayout flow = new FlowLayout(FlowLayout.LEFT);
		flow.setVgap(0);
		JPanel panel = new JPanel(flow);

		JButton updateButton = new JButton("Update");
		JButton okButton = new JButton("OK");
		JButton cancelButton = new JButton("Cancel");

		updateButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				applyFromSkeletons(state);
			}
		});

		okButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				applyFromSkeletons(state);
				closeEditor(state);
			}
		});

		cancelButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				restoreOriginalCages(state);
				closeEditor(state);
			}
		});

		panel.add(updateButton);
		panel.add(okButton);
		panel.add(cancelButton);
		return panel;
	}

	private static void addSkeletonsFromCages(EditorState state, List<ROI2D> cageRois) {
		int index = 0;
		for (ROI2D cageRoi : cageRois) {
			Line2D axis = computeLongAxis(cageRoi);
			if (axis == null) {
				continue;
			}
			ROI2DLine skeleton = new ROI2DLine(axis);
			String cageName = cageRoi.getName();
			String skeletonName = SKELETON_PREFIX + String.format("%03d", index);
			skeleton.setName(skeletonName);
			skeleton.setColor(Color.GREEN);
			state.skeletonToCageName.put(skeletonName, cageName);
			state.sequence.addROI(skeleton);
			index++;
		}
	}

	private static Line2D computeLongAxis(ROI2D roi) {
		if (roi instanceof ROI2DPolygon) {
			List<Point2D> points = ((ROI2DPolygon) roi).getPoints();
			Line2D axis = longAxisFromPolygon(points);
			if (axis != null) {
				return axis;
			}
		}

		Rectangle2D b = roi.getBounds2D();
		double cx = b.getCenterX();
		double cy = b.getCenterY();
		if (b.getHeight() >= b.getWidth()) {
			return new Line2D.Double(cx, b.getMinY(), cx, b.getMaxY());
		}
		return new Line2D.Double(b.getMinX(), cy, b.getMaxX(), cy);
	}

	private static Line2D computeShortAxis(ROI2D roi) {
		if (roi instanceof ROI2DPolygon) {
			List<Point2D> points = ((ROI2DPolygon) roi).getPoints();
			Line2D axis = shortAxisFromPolygon(points);
			if (axis != null) {
				return axis;
			}
		}

		Rectangle2D b = roi.getBounds2D();
		double cx = b.getCenterX();
		double cy = b.getCenterY();
		if (b.getHeight() >= b.getWidth()) {
			return new Line2D.Double(b.getMinX(), cy, b.getMaxX(), cy);
		}
		return new Line2D.Double(cx, b.getMinY(), cx, b.getMaxY());
	}

	private static int estimateInitialCageWidth(List<ROI2D> cageRois) {
		double sum = 0;
		int n = 0;
		for (ROI2D cageRoi : cageRois) {
			Line2D shortAxis = computeShortAxis(cageRoi);
			if (shortAxis == null) {
				continue;
			}
			double len = shortAxis.getP1().distance(shortAxis.getP2());
			if (len >= 1e-6) {
				sum += len;
				n++;
			}
		}
		if (n == 0) {
			return 8;
		}
		int rounded = (int) Math.round(sum / n);
		return Math.max(1, Math.min(500, rounded));
	}

	private static Line2D longAxisFromPolygon(List<Point2D> points) {
		if (points == null || points.size() < 4) {
			return null;
		}

		int n = points.size();
		double bestLen2 = Double.POSITIVE_INFINITY;
		int bestEdge = -1;
		for (int i = 0; i < n; i++) {
			Point2D a0 = points.get(i);
			Point2D a1 = points.get((i + 1) % n);
			double len2 = a0.distanceSq(a1);
			if (len2 < bestLen2) {
				bestLen2 = len2;
				bestEdge = i;
			}
		}
		if (bestEdge < 0) {
			return null;
		}

		Point2D a0 = points.get(bestEdge);
		Point2D a1 = points.get((bestEdge + 1) % n);
		Point2D midA = midpoint(a0, a1);

		Point2D midB = null;
		if ((n % 2) == 0) {
			int opp = (bestEdge + (n / 2)) % n;
			Point2D b0 = points.get(opp);
			Point2D b1 = points.get((opp + 1) % n);
			midB = midpoint(b0, b1);
		} else {
			double bestD2 = -1;
			for (int i = 0; i < n; i++) {
				Point2D b0 = points.get(i);
				Point2D b1 = points.get((i + 1) % n);
				Point2D mid = midpoint(b0, b1);
				double d2 = mid.distanceSq(midA);
				if (d2 > bestD2) {
					bestD2 = d2;
					midB = mid;
				}
			}
		}
		if (midB == null) {
			return null;
		}
		return new Line2D.Double(midA, midB);
	}

	private static Line2D shortAxisFromPolygon(List<Point2D> points) {
		if (points == null || points.size() < 4) {
			return null;
		}

		int n = points.size();
		double bestLen2 = -1;
		int bestEdge = -1;
		for (int i = 0; i < n; i++) {
			Point2D a0 = points.get(i);
			Point2D a1 = points.get((i + 1) % n);
			double len2 = a0.distanceSq(a1);
			if (len2 > bestLen2) {
				bestLen2 = len2;
				bestEdge = i;
			}
		}
		if (bestEdge < 0) {
			return null;
		}

		Point2D a0 = points.get(bestEdge);
		Point2D a1 = points.get((bestEdge + 1) % n);
		Point2D midA = midpoint(a0, a1);

		Point2D midB = null;
		if ((n % 2) == 0) {
			int opp = (bestEdge + (n / 2)) % n;
			Point2D b0 = points.get(opp);
			Point2D b1 = points.get((opp + 1) % n);
			midB = midpoint(b0, b1);
		} else {
			double bestD2 = -1;
			for (int i = 0; i < n; i++) {
				Point2D b0 = points.get(i);
				Point2D b1 = points.get((i + 1) % n);
				Point2D mid = midpoint(b0, b1);
				double d2 = mid.distanceSq(midA);
				if (d2 > bestD2) {
					bestD2 = d2;
					midB = mid;
				}
			}
		}
		if (midB == null) {
			return null;
		}
		return new Line2D.Double(midA, midB);
	}

	private static Point2D midpoint(Point2D a, Point2D b) {
		return new Point2D.Double((a.getX() + b.getX()) * 0.5, (a.getY() + b.getY()) * 0.5);
	}

	private static void applyFromSkeletons(EditorState state) {
		List<ROI2D> skeletons = getSkeletonRois(state.sequence);
		Collections.sort(skeletons, new Comparators.ROI2D_Name());
		if (skeletons.isEmpty()) {
			return;
		}

		int cageWidth = ((Number) state.cageWidthSpinner.getValue()).intValue();
		List<ROI2D> newCages = new ArrayList<ROI2D>(skeletons.size());
		for (ROI2D roi : skeletons) {
			if (!(roi instanceof ROI2DLine)) {
				continue;
			}
			ROI2DLine lineRoi = (ROI2DLine) roi;
			ROI2DPolygon cageRoi = buildCageFromSkeleton(lineRoi, cageWidth);
			if (cageRoi == null) {
				continue;
			}
			String cageName = state.skeletonToCageName.get(roi.getName());
			if (cageName != null) {
				cageRoi.setName(cageName);
			}
			cageRoi.setColor(Color.MAGENTA);
			newCages.add(cageRoi);
		}

		removeCurrentCages(state.sequence);
		for (ROI2D cage : newCages) {
			state.sequence.addROI(cage);
		}

		state.exp.getCages().transferROIsFromSequence(state.exp.getSeqCamData());
		if (state.exp.getCapillaries().getList().size() > 0) {
			state.exp.getCages().transferNFliesFromCapillariesToCageBox(state.exp.getCapillaries().getList());
		}
	}

	private static ROI2DPolygon buildCageFromSkeleton(ROI2DLine lineRoi, int width) {
		Line2D line = lineRoi.getLine();
		double x1 = line.getX1();
		double y1 = line.getY1();
		double x2 = line.getX2();
		double y2 = line.getY2();

		double dx = x2 - x1;
		double dy = y2 - y1;
		double len = Math.hypot(dx, dy);
		if (len < 1e-6) {
			return null;
		}

		double halfWidth = width * 0.5;
		double nx = -dy / len;
		double ny = dx / len;

		List<Point2D> points = new ArrayList<Point2D>(4);
		points.add(new Point2D.Double(x1 + nx * halfWidth, y1 + ny * halfWidth));
		points.add(new Point2D.Double(x1 - nx * halfWidth, y1 - ny * halfWidth));
		points.add(new Point2D.Double(x2 - nx * halfWidth, y2 - ny * halfWidth));
		points.add(new Point2D.Double(x2 + nx * halfWidth, y2 + ny * halfWidth));
		return new ROI2DPolygon(points);
	}

	private static void restoreOriginalCages(EditorState state) {
		removeCurrentCages(state.sequence);
		for (ROI2D cage : state.originalCageRois) {
			state.sequence.addROI(cage);
		}
		state.exp.getCages().transferROIsFromSequence(state.exp.getSeqCamData());
		if (state.exp.getCapillaries().getList().size() > 0) {
			state.exp.getCages().transferNFliesFromCapillariesToCageBox(state.exp.getCapillaries().getList());
		}
	}

	private static void closeEditor(EditorState state) {
		removeSkeletons(state.sequence);
		try {
			if (state.frame != null) {
				state.frame.close();
			}
		} catch (Exception e) {
			// ignore
		}
	}

	private static List<ROI2D> getSortedCageRois(Sequence sequence) {
		List<ROI2D> out = new ArrayList<ROI2D>();
		for (ROI2D roi : sequence.getROI2Ds()) {
			String name = roi.getName();
			if (name != null && name.startsWith("cage")) {
				out.add(roi);
			}
		}
		Collections.sort(out, new Comparators.ROI2D_Name());
		return out;
	}

	private static List<ROI2D> getSkeletonRois(Sequence sequence) {
		List<ROI2D> out = new ArrayList<ROI2D>();
		for (ROI2D roi : sequence.getROI2Ds()) {
			String name = roi.getName();
			if (name != null && name.startsWith(SKELETON_PREFIX)) {
				out.add(roi);
			}
		}
		return out;
	}

	private static List<ROI2D> copyRoiList(List<ROI2D> source) {
		List<ROI2D> out = new ArrayList<ROI2D>(source.size());
		for (ROI2D roi : source) {
			out.add((ROI2D) roi.getCopy());
		}
		return out;
	}

	private static void removeCurrentCages(Sequence sequence) {
		List<ROI2D> rois = new ArrayList<ROI2D>(sequence.getROI2Ds());
		for (ROI2D roi : rois) {
			String name = roi.getName();
			if (name != null && name.startsWith("cage")) {
				sequence.removeROI(roi);
			}
		}
	}

	private static void removeSkeletons(Sequence sequence) {
		List<ROI2D> rois = new ArrayList<ROI2D>(sequence.getROI2Ds());
		for (ROI2D roi : rois) {
			String name = roi.getName();
			if (name != null && name.startsWith(SKELETON_PREFIX)) {
				sequence.removeROI(roi);
			}
		}
	}

	private static final class EditorState {
		final Experiment exp;
		final Sequence sequence;
		final JSpinner cageWidthSpinner;
		final HashMap<String, String> skeletonToCageName = new HashMap<String, String>();
		List<ROI2D> originalCageRois = new ArrayList<ROI2D>();
		IcyFrame frame = null;

		EditorState(Experiment exp, Sequence sequence, int initialCageWidth) {
			this.exp = exp;
			this.sequence = sequence;
			this.cageWidthSpinner = new JSpinner(new SpinnerNumberModel(initialCageWidth, 1, 500, 1));
		}
	}
}
