package plugins.fmp.multitools.tools.chart.interaction;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;

import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.XYPlot;

import icy.gui.viewer.Viewer;
import icy.roi.ROI2D;
import icy.sequence.Sequence;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.ViewerFMP;
import plugins.fmp.multitools.tools.chart.ChartCagePanel;
import plugins.fmp.multitools.tools.chart.ChartInteractionHandler;

/**
 * Fly-position chart interactions (click-to-select cage ROI, jump to nearest T).
 */
public class FlyPositionChartInteractionHandler implements ChartInteractionHandler {

	private static final int LEFT_MOUSE_BUTTON = MouseEvent.BUTTON1;

	private final Experiment experiment;

	public FlyPositionChartInteractionHandler(Experiment experiment) {
		this.experiment = experiment;
	}

	@Override
	public ChartMouseListener createMouseListener() {
		return new FlyPositionChartMouseListener();
	}

	private static ROI2D resolveRoiOnSequence(Sequence seq, ROI2D fromModel) {
		if (seq == null || fromModel == null) {
			return null;
		}
		ArrayList<ROI2D> onSeq = seq.getROI2Ds();
		for (ROI2D r : onSeq) {
			if (r == fromModel) {
				return r;
			}
		}
		String name = fromModel.getName();
		if (name != null) {
			for (ROI2D r : onSeq) {
				if (name.equals(r.getName())) {
					return r;
				}
			}
		}
		return null;
	}

	private Cage getCageFromEvent(ChartMouseEvent e) {
		if (e == null) {
			return null;
		}
		MouseEvent trigger = e.getTrigger();
		if (trigger == null || trigger.getButton() != LEFT_MOUSE_BUTTON) {
			return null;
		}
		Object source = trigger.getSource();
		if (source instanceof ChartCagePanel) {
			return ((ChartCagePanel) source).getCage();
		}
		return null;
	}

	private double getTimeMinutesFromEvent(ChartMouseEvent e, ChartPanel panel, XYPlot plot) {
		if (e == null || panel == null || plot == null) {
			return -1;
		}
		Point screenPoint = e.getTrigger().getPoint();
		Point2D java2DPoint = panel.translateScreenToJava2D(screenPoint);
		Rectangle2D dataArea = panel.getScreenDataArea();
		ValueAxis domainAxis = plot.getDomainAxis();
		return domainAxis.java2DToValue(java2DPoint.getX(), dataArea, plot.getDomainAxisEdge());
	}

	private int getFrameIndexFromTimeMinutes(Experiment exp, double timeMinutes) {
		if (exp == null || exp.getSeqCamData() == null || timeMinutes < 0) {
			return -1;
		}
		int nTotalFrames = exp.getSeqCamData().getImageLoader().getNTotalFrames();
		if (nTotalFrames <= 0) {
			Sequence seq = exp.getSeqCamData().getSequence();
			if (seq != null) {
				nTotalFrames = seq.getSizeT();
			}
		}
		if (nTotalFrames <= 0) {
			return -1;
		}

		long[] timeArray = exp.getSeqCamData().getTimeManager().getCamImagesTime_Ms();
		if (timeArray != null && timeArray.length == nTotalFrames) {
			// TimeManager.findNearestIntervalWithBinarySearch expects 'high' to be a valid index.
			return exp.getSeqCamData().getTimeManager().findNearestIntervalWithBinarySearch((long) (timeMinutes * 60000),
					0, nTotalFrames - 1);
		}

		// Fallback: approximate frame index from bin duration.
		long binMs = exp.getSeqCamData().getTimeManager().getBinDurationMs();
		if (binMs > 0) {
			long tMs = (long) (timeMinutes * 60000.0);
			int frame = (int) Math.round((double) tMs / (double) binMs);
			if (frame < 0)
				frame = 0;
			if (frame >= nTotalFrames)
				frame = nTotalFrames - 1;
			return frame;
		}

		return -1;
	}

	private void selectCageAndMoveT(Cage cage, int frameIndex) {
		if (experiment == null || cage == null || experiment.getSeqCamData() == null
				|| experiment.getSeqCamData().getSequence() == null) {
			return;
		}

		Sequence seq = experiment.getSeqCamData().getSequence();
		Viewer v = seq.getFirstViewer();
		if (v == null) {
			v = new ViewerFMP(seq, true, true);
		}
		v.toFront();
		if (frameIndex >= 0) {
			v.setPositionT(frameIndex);
		}

		ROI2D cageRoi = cage.getRoi() != null ? cage.getRoi() : cage.getCageRoi2D();
		if (cageRoi != null) {
			ROI2D seqRoi = resolveRoiOnSequence(seq, cageRoi);
			if (seqRoi == null) {
				Logger.warn("Cage ROI is not attached to the camera sequence (no instance/name match): "
						+ cageRoi.getName());
			} else {
				seq.setFocusedROI(seqRoi);
				seq.setSelectedROI(seqRoi);
				experiment.getSeqCamData().centerDisplayOnRoi(seqRoi);
			}
		}
	}

	private class FlyPositionChartMouseListener implements ChartMouseListener {
		@Override
		public void chartMouseClicked(ChartMouseEvent e) {
			Cage cage = getCageFromEvent(e);
			if (cage == null) {
				return;
			}
			if (experiment == null) {
				return;
			}
			Object source = e.getTrigger().getSource();
			if (!(source instanceof ChartPanel)) {
				return;
			}

			JFreeChart chart = e.getChart();
			if (chart == null) {
				return;
			}
			XYPlot plot = (XYPlot) chart.getPlot();
			double timeMinutes = getTimeMinutesFromEvent(e, (ChartPanel) source, plot);
			int frameIndex = getFrameIndexFromTimeMinutes(experiment, timeMinutes);

			selectCageAndMoveT(cage, frameIndex);
		}

		@Override
		public void chartMouseMoved(ChartMouseEvent e) {
		}
	}
}

