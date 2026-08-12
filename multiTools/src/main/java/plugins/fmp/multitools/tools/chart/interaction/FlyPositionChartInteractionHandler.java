package plugins.fmp.multitools.tools.chart.interaction;

import java.awt.event.MouseEvent;
import java.util.ArrayList;

import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
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
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.chart.interaction.ChartCamFrameNavigation;

/**
 * Fly-position chart interactions (click-to-select cage ROI, jump to nearest T).
 */
public class FlyPositionChartInteractionHandler implements ChartInteractionHandler {

	private static final int LEFT_MOUSE_BUTTON = MouseEvent.BUTTON1;

	private final Experiment experiment;

	public FlyPositionChartInteractionHandler(Experiment experiment) {
		this.experiment = experiment;
	}

	public FlyPositionChartInteractionHandler(Experiment experiment, ResultsOptions resultsOptions) {
		this(experiment);
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

	private double getDomainValueFromEvent(ChartMouseEvent e, ChartPanel panel, XYPlot plot) {
		return ChartCamFrameNavigation.getTimeMinutesFromEvent(e, panel, plot);
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
			double domainValue = getDomainValueFromEvent(e, (ChartPanel) source, plot);
			int frameIndex = (int) Math.round(domainValue);

			selectCageAndMoveT(cage, frameIndex);
		}

		@Override
		public void chartMouseMoved(ChartMouseEvent e) {
		}
	}
}

