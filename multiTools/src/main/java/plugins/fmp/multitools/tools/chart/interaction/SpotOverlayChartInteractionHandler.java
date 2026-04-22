package plugins.fmp.multitools.tools.chart.interaction;

import java.awt.event.MouseEvent;

import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.entity.ChartEntity;
import org.jfree.chart.entity.XYItemEntity;
import org.jfree.data.xy.XYDataset;

import icy.gui.viewer.Viewer;
import icy.roi.ROI2D;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.results.ResultsOptions;

public class SpotOverlayChartInteractionHandler {
	private static final int LEFT_MOUSE_BUTTON = MouseEvent.BUTTON1;

	private final Experiment experiment;
	private final ResultsOptions resultsOptions;

	public SpotOverlayChartInteractionHandler(Experiment experiment, ResultsOptions resultsOptions) {
		this.experiment = experiment;
		this.resultsOptions = resultsOptions;
	}

	public ChartMouseListener createMouseListener() {
		return new MouseListener();
	}

	private Spot getSpotFromClickedChart(ChartMouseEvent e) {
		if (e == null)
			return null;
		if (experiment == null || experiment.getCages() == null || experiment.getSpots() == null)
			return null;

		final MouseEvent trigger = e.getTrigger();
		if (trigger == null || trigger.getButton() != LEFT_MOUSE_BUTTON)
			return null;

		ChartEntity chartEntity = e.getEntity();
		if (!(chartEntity instanceof XYItemEntity))
			return null;

		XYItemEntity item = (XYItemEntity) chartEntity;
		XYDataset dataset = item.getDataset();
		if (dataset == null)
			return null;

		int seriesIndex = item.getSeriesIndex();
		String seriesKey = (String) dataset.getSeriesKey(seriesIndex);
		if (seriesKey == null)
			return null;

		String spotKey = seriesKey;
		int sep = seriesKey.indexOf("::");
		if (sep >= 0)
			spotKey = seriesKey.substring(0, sep);

		Spot spot = experiment.getCages().getSpotFromROIName(spotKey, experiment.getSpots());
		if (spot == null) {
			Logger.warn("Spot not found from seriesKey=" + seriesKey);
			return null;
		}

		spot.setSpotCamDataT(item.getItem());
		return spot;
	}

	private void selectSpotAndMoveT(Spot spot) {
		if (spot == null || experiment == null || experiment.getSeqCamData() == null
				|| experiment.getSeqCamData().getSequence() == null) {
			return;
		}

		ROI2D roi = spot.getRoi();
		if (roi != null) {
			experiment.getSeqCamData().getSequence().setFocusedROI(roi);
			experiment.getSeqCamData().getSequence().setSelectedROI(roi);
			experiment.getSeqCamData().centerDisplayOnRoi(roi);
		}

		if (resultsOptions != null) {
			Viewer v = experiment.getSeqCamData().getSequence().getFirstViewer();
			if (v != null && spot.getSpotCamDataT() > 0) {
				int frameIndex = (int) (spot.getSpotCamDataT() * resultsOptions.buildExcelStepMs
						/ experiment.getSeqCamData().getTimeManager().getBinDurationMs());
				v.setPositionT(frameIndex);
			}
		}

		if (roi != null) {
			Cage cage = experiment.getCages().getCageFromSpotROIName(roi.getName(), experiment.getSpots());
			if (cage != null && cage.getRoi() != null) {
				experiment.getSeqCamData().centerDisplayOnRoi(cage.getRoi());
			}
		}
	}

	private class MouseListener implements ChartMouseListener {
		@Override
		public void chartMouseClicked(ChartMouseEvent e) {
			Spot spot = getSpotFromClickedChart(e);
			if (spot == null)
				return;
			selectSpotAndMoveT(spot);
		}

		@Override
		public void chartMouseMoved(ChartMouseEvent e) {
		}
	}
}

