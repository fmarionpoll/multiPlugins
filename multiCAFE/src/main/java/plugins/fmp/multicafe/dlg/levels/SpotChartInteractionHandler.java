package plugins.fmp.multicafe.dlg.levels;

import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.List;

import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.entity.ChartEntity;
import org.jfree.chart.entity.XYItemEntity;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYDataset;

import icy.gui.viewer.Viewer;
import icy.roi.ROI2D;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cages.cage.Cage;
import plugins.fmp.multitools.experiment.spots.Spot;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.chart.ChartCagePair;
import plugins.fmp.multitools.tools.chart.ChartCagePanel;
import plugins.fmp.multitools.tools.chart.ChartInteractionHandler;
import plugins.fmp.multitools.tools.results.ResultsOptions;

/**
 * Handler for spot-related chart interactions. Manages user clicks on spot
 * charts to select spots, navigate to images, and display kymographs.
 * 
 * @author MultiCAFE
 */
public class SpotChartInteractionHandler implements ChartInteractionHandler {

	private static final String CHART_ID_DELIMITER = ":";
	private static final int MAX_DESCRIPTION_LENGTH = 16;
	private static final int LEFT_MOUSE_BUTTON = MouseEvent.BUTTON1;

	private final Experiment experiment;
	private final ResultsOptions resultsOptions;
	private final ChartCagePair[][] chartPanelArray;

	/**
	 * Creates a new spot chart interaction handler.
	 * 
	 * @param experiment      the experiment containing the data
	 * @param resultsOptions  the export options
	 * @param chartPanelArray the array of chart panel pairs
	 */
	public SpotChartInteractionHandler(Experiment experiment, ResultsOptions resultsOptions,
			ChartCagePair[][] chartPanelArray) {
		this.experiment = experiment;
		this.resultsOptions = resultsOptions;
		this.chartPanelArray = chartPanelArray;
	}

	@Override
	public ChartMouseListener createMouseListener() {
		return new SpotChartMouseListener();
	}

	/**
	 * Gets the spot from a clicked chart.
	 * 
	 * @param e the chart mouse event
	 * @return the selected spot or null if not found
	 */
	private Spot getSpotFromClickedChart(ChartMouseEvent e) {
		if (e == null) {
			Logger.warn("Chart mouse event is null");
			return null;
		}

		final MouseEvent trigger = e.getTrigger();
		if (trigger.getButton() != LEFT_MOUSE_BUTTON) {
			return null;
		}

		JFreeChart chart = e.getChart();
		if (chart == null) {
			Logger.warn("Chart is null");
			return null;
		}

		// Get panel from event source
		Object source = e.getTrigger().getSource();
		ChartPanel panel = null;
		Cage cage = null;

		// Try to get cage directly from ChartCagePanel if available
		if (source instanceof ChartCagePanel) {
			panel = (ChartCagePanel) source;
			cage = ((ChartCagePanel) panel).getCage();
		} else if (source instanceof ChartPanel) {
			panel = (ChartPanel) source;
		} else {
			Logger.warn("Event source is not a ChartPanel: " + source);
			return null;
		}

		// Fall back to array lookup if cage not found directly
		if (cage == null) {
			if (chart.getID() == null) {
				Logger.warn("Chart ID is null and cannot get cage from ChartCagePanel");
				return null;
			}

			String[] chartID = chart.getID().split(CHART_ID_DELIMITER);
			if (chartID.length < 4) {
				Logger.warn("Invalid chart ID format: " + chart.getID());
				return null;
			}

			try {
				int row = Integer.parseInt(chartID[1]);
				int col = Integer.parseInt(chartID[3]);

				if (row < 0 || row >= chartPanelArray.length || col < 0 || col >= chartPanelArray[0].length) {
					Logger.warn("Invalid chart coordinates: row=" + row + ", col=" + col);
					return null;
				}

				cage = chartPanelArray[row][col].getCage();
				if (cage == null) {
					Logger.warn("Clicked chart has no associated cage");
					return null;
				}
			} catch (NumberFormatException ex) {
				Logger.warn("Could not parse chart coordinates: " + ex.getMessage());
				return null;
			}
		}

		try {

			PlotRenderingInfo plotInfo = panel.getChartRenderingInfo().getPlotInfo();
			Point2D pointClicked = panel.translateScreenToJava2D(trigger.getPoint());

			int subplotIndex = plotInfo.getSubplotIndex(pointClicked);
			XYPlot xyPlot = (XYPlot) chart.getPlot();

			Spot spotFound = null;
			ChartEntity chartEntity = e.getEntity();

			if (chartEntity != null && chartEntity instanceof XYItemEntity) {
				spotFound = getSpotFromXYItemEntity((XYItemEntity) chartEntity);
			} else if (subplotIndex >= 0) {
				XYDataset xyDataset = xyPlot.getDataset(0);
				if (xyDataset != null && xyDataset.getSeriesCount() > 0) {
					String description = (String) xyDataset.getSeriesKey(0);
					spotFound = experiment.getCages().getSpotFromROIName(description, experiment.getSpots());
				}
			} else {
				List<Spot> spots = cage.getSpotList(experiment.getSpots());
				if (spots.size() > 0) {
					spotFound = spots.get(0);
				}
			}

			if (spotFound == null) {
				Logger.warn("Failed to find spot from clicked chart");
				return null;
			}

			int index = experiment.getCages().getSpotGlobalPosition(spotFound, experiment.getSpots());
			spotFound.setSpotKymographT(index);
			return spotFound;
		} catch (Exception ex) {
			Logger.warn("Error processing spot from clicked chart: " + ex.getMessage());
			return null;
		}
	}

	/**
	 * Gets the spot from an XY item entity.
	 * 
	 * @param xyItemEntity the XY item entity
	 * @return the selected spot or null if not found
	 */
	private Spot getSpotFromXYItemEntity(XYItemEntity xyItemEntity) {
		if (xyItemEntity == null) {
			Logger.warn("XY item entity is null");
			return null;
		}

		int seriesIndex = xyItemEntity.getSeriesIndex();
		XYDataset xyDataset = xyItemEntity.getDataset();

		if (xyDataset == null) {
			Logger.warn("XY dataset is null");
			return null;
		}

		String description = (String) xyDataset.getSeriesKey(seriesIndex);
		if (description == null) {
			Logger.warn("Series description is null");
			return null;
		}

		description = description.substring(0, Math.min(description.length(), MAX_DESCRIPTION_LENGTH));

		Spot spotFound = experiment.getCages().getSpotFromROIName(description, experiment.getSpots());
		if (spotFound == null) {
			Logger.warn("Graph clicked but source not found - description (roiName)=" + description);
			return null;
		}

		spotFound.setSpotCamDataT(xyItemEntity.getItem());
		return spotFound;
	}

	/**
	 * Selects a spot in the experiment.
	 * 
	 * @param exp  the experiment
	 * @param spot the spot to select
	 */
	private void chartSelectSpot(Experiment exp, Spot spot) {
		if (exp == null || spot == null) {
			Logger.warn("Cannot select spot: experiment or spot is null");
			return;
		}

		ROI2D roi = spot.getRoi();
		if (roi != null) {
			exp.getSeqCamData().getSequence().setFocusedROI(roi);
			exp.getSeqCamData().centerDisplayOnRoi(roi);
		}
	}

	/**
	 * Selects the time position for a spot.
	 * 
	 * @param exp            the experiment
	 * @param resultsOptions the export options
	 * @param spot           the spot to select time for
	 */
	private void selectT(Experiment exp, ResultsOptions resultsOptions, Spot spot) {
		if (exp == null || spot == null) {
			Logger.warn("Cannot select time: experiment or spot is null");
			return;
		}

		Viewer v = exp.getSeqCamData().getSequence().getFirstViewer();
		if (v != null && spot.getSpotCamDataT() > 0) {
			int frameIndex = (int) (spot.getSpotCamDataT() * resultsOptions.buildExcelStepMs
					/ exp.getSeqCamData().getTimeManager().getBinDurationMs());
			v.setPositionT(frameIndex);
		}
	}

	/**
	 * Selects the kymograph for a spot.
	 * 
	 * @param exp  the experiment
	 * @param spot the spot to select kymograph for
	 */
	private void chartSelectKymograph(Experiment exp, Spot spot) {
		if (exp == null || spot == null) {
			Logger.warn("Cannot select kymograph: experiment or spot is null");
			return;
		}

		// Kymograph selection for spots is currently not implemented
		// if (exp.seqKymos != null && exp.seqKymos.getSequence() != null) {
		// Viewer v = exp.seqKymos.getSequence().getFirstViewer();
		// if (v != null) {
		// v.setPositionT(spot.getSpotKymographT());
		// }
		// }
	}

	/**
	 * Handles the selection of a clicked spot.
	 * 
	 * @param exp            the experiment
	 * @param resultsOptions the export options
	 * @param clickedSpot    the clicked spot
	 */
	private void chartSelectClickedSpot(Experiment exp, ResultsOptions resultsOptions, Spot clickedSpot) {
		if (clickedSpot == null) {
			Logger.warn("Clicked spot is null");
			return;
		}

		chartSelectSpot(exp, clickedSpot);
		selectT(exp, resultsOptions, clickedSpot);
		chartSelectKymograph(exp, clickedSpot);

		ROI2D roi = clickedSpot.getRoi();
		if (roi != null) {
			exp.getSeqCamData().getSequence().setSelectedROI(roi);
		}

		String spotName = clickedSpot.getRoi().getName();
		Cage cage = exp.getCages().getCageFromSpotROIName(spotName, exp.getSpots());
		if (cage != null) {
			ROI2D cageRoi = cage.getRoi();
			exp.getSeqCamData().centerDisplayOnRoi(cageRoi);
		} else {
			Logger.warn("Could not find cage for spot: " + spotName);
		}
	}

	/**
	 * Inner class for handling chart mouse events for spots.
	 */
	private class SpotChartMouseListener implements ChartMouseListener {
		@Override
		public void chartMouseClicked(ChartMouseEvent e) {
			Spot clickedSpot = getSpotFromClickedChart(e);
			if (clickedSpot != null) {
				chartSelectClickedSpot(experiment, resultsOptions, clickedSpot);
			}
		}

		@Override
		public void chartMouseMoved(ChartMouseEvent e) {
			// No action needed for mouse movement
		}
	}
}
