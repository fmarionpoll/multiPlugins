package plugins.fmp.multitools.tools.chart.interaction;

import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.entity.ChartEntity;
import org.jfree.chart.entity.XYItemEntity;
import org.jfree.data.xy.XYDataset;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.chart.ChartCagePair;
import plugins.fmp.multitools.tools.chart.ChartCagePanel;
import plugins.fmp.multitools.tools.chart.ChartInteractionHandler;
import plugins.fmp.multitools.tools.chart.builders.SpotChartSeriesKeys;
import plugins.fmp.multitools.tools.results.ResultsOptions;

/**
 * Spot-related chart interactions for cage grid charts (click-to-select ROI and
 * move sequence T).
 *
 * <p>
 * This is intended for multiSPOTS96 spot measures charts.
 * </p>
 */
public class SpotChartInteractionHandler implements ChartInteractionHandler {

	private static final int LEFT_MOUSE_BUTTON = MouseEvent.BUTTON1;

	private final Experiment experiment;
	private final Consumer<Spot> onSpotSelectedFromChart;

	public SpotChartInteractionHandler(Experiment experiment, ResultsOptions resultsOptions,
			@SuppressWarnings("unused") ChartCagePair[][] chartArray) {
		this(experiment, resultsOptions, chartArray, null);
	}

	public SpotChartInteractionHandler(Experiment experiment, ResultsOptions resultsOptions,
			@SuppressWarnings("unused") ChartCagePair[][] chartArray, Consumer<Spot> onSpotSelectedFromChart) {
		this.experiment = experiment;
		this.onSpotSelectedFromChart = onSpotSelectedFromChart;
	}

	@Override
	public ChartMouseListener createMouseListener() {
		return new SpotChartMouseListener();
	}

	private Spot getSpotFromClickedChart(ChartMouseEvent e, int frameIndex) {
		if (e == null) {
			return null;
		}
		if (experiment == null || experiment.getCages() == null || experiment.getSpots() == null) {
			return null;
		}

		final MouseEvent trigger = e.getTrigger();
		if (trigger == null || trigger.getButton() != LEFT_MOUSE_BUTTON) {
			return null;
		}

		Object source = trigger.getSource();
		Cage cage = null;
		if (source instanceof ChartCagePanel) {
			cage = ((ChartCagePanel) source).getCage();
		}

		ChartEntity chartEntity = e.getEntity();
		if (!(chartEntity instanceof XYItemEntity)) {
			return null;
		}

		XYItemEntity item = (XYItemEntity) chartEntity;
		XYDataset dataset = item.getDataset();
		if (dataset == null) {
			return null;
		}

		int seriesIndex = item.getSeriesIndex();
		String seriesKey = (String) dataset.getSeriesKey(seriesIndex);
		if (seriesKey == null) {
			return null;
		}
		if (SpotChartSeriesKeys.isAggregateSeriesKey(seriesKey)) {
			return null;
		}

		Spot spot = SpotChartSeriesKeys.resolveSpot(experiment, cage, seriesKey);
		if (spot == null) {
			Logger.warn("Spot not found from seriesKey=" + seriesKey);
			return null;
		}

		if (frameIndex >= 0) {
			spot.setSpotCamDataT(frameIndex);
		}
		return spot;
	}

	private void selectSpotAndMoveT(Spot spot) {
		if (spot == null || experiment == null || experiment.getSeqCamData() == null) {
			return;
		}
		SpotChartRoiFocus.moveViewerToSpotTAndSelectRoi(experiment.getSeqCamData(), spot);
	}

	private static void applyExclusiveCageRoiSelection(Experiment exp, Cage cageToSelect) {
		if (exp == null || exp.getCages() == null || cageToSelect == null) {
			return;
		}
		for (Cage cage : exp.getCages().cagesList) {
			if (cage == null || cage.getRoi() == null) {
				continue;
			}
			cage.getRoi().setSelected(cage == cageToSelect);
		}
	}

	/**
	 * Plot background (not on a series point): first spot of the chart's cage at camera T=0.
	 */
	private Spot resolveSpotFromCageBackgroundClick(ChartMouseEvent e) {
		if (e == null || experiment == null || experiment.getCages() == null || experiment.getSpots() == null) {
			return null;
		}
		MouseEvent trigger = e.getTrigger();
		if (trigger == null || trigger.getButton() != LEFT_MOUSE_BUTTON) {
			return null;
		}
		if (e.getEntity() instanceof XYItemEntity) {
			return null;
		}
		Object source = trigger.getSource();
		if (!(source instanceof ChartCagePanel)) {
			return null;
		}
		Cage cage = ((ChartCagePanel) source).getCage();
		if (cage == null) {
			return null;
		}
		List<Spot> spots = cage.getSpotList(experiment.getSpots());
		if (spots == null) {
			return null;
		}
		for (Spot s : spots) {
			if (s != null) {
				s.setSpotCamDataT(0);
				return s;
			}
		}
		return null;
	}

	private class SpotChartMouseListener implements ChartMouseListener {
		@Override
		public void chartMouseClicked(ChartMouseEvent e) {
			int frameIndex = -1;
			if (e.getEntity() instanceof XYItemEntity) {
				double timeMinutes = ChartCamFrameNavigation.getTimeMinutesFromXYItem((XYItemEntity) e.getEntity());
				frameIndex = ChartCamFrameNavigation.getFrameIndexFromTimeMinutes(experiment, timeMinutes);
			}

			Spot spot = getSpotFromClickedChart(e, frameIndex);
			Cage backgroundCage = null;
			if (spot == null) {
				spot = resolveSpotFromCageBackgroundClick(e);
				if (spot != null) {
					MouseEvent trigger = e.getTrigger();
					if (trigger != null && trigger.getSource() instanceof ChartCagePanel) {
						backgroundCage = ((ChartCagePanel) trigger.getSource()).getCage();
					}
				}
			}
			if (spot == null) {
				return;
			}

			if (backgroundCage != null) {
				applyExclusiveCageRoiSelection(experiment, backgroundCage);
			}
			selectSpotAndMoveT(spot);
			if (onSpotSelectedFromChart != null) {
				onSpotSelectedFromChart.accept(spot);
			}
		}

		@Override
		public void chartMouseMoved(ChartMouseEvent e) {
		}
	}
}
