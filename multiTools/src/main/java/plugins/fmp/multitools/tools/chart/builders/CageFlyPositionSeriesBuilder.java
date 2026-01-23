package plugins.fmp.multitools.tools.chart.builders;

import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.FlyPosition;
import plugins.fmp.multitools.experiment.cage.FlyPositions;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * Builds cage datasets from FlyPositions measurements.
 * Supports various position-related measurements: distance, alive status, sleep status, and Y positions.
 */
public class CageFlyPositionSeriesBuilder implements CageSeriesBuilder {
	
	
	/** Value representing alive status */
	private static final double ALIVE_VALUE = 1.0;
	
	/** Value representing dead status */
	private static final double DEAD_VALUE = 0.0;
	
	/** Value representing sleep status */
	private static final double SLEEP_VALUE = 1.0;
	
	/** Value representing awake status */
	private static final double AWAKE_VALUE = 0.0;

	@Override
	public XYSeriesCollection build(Experiment exp, Cage cage, ResultsOptions options) {
		if (cage == null) {
			return new XYSeriesCollection();
		}
		
		// Get flyPositions (try both field and getter for compatibility)
		FlyPositions flyPositions = cage.flyPositions != null ? cage.flyPositions : cage.getFlyPositions();
		
		if (flyPositions == null) {
			return new XYSeriesCollection();
		}
		
		if (flyPositions.flyPositionList == null) {
			return new XYSeriesCollection();
		}
		
		if (flyPositions.flyPositionList.isEmpty()) {
			return new XYSeriesCollection();
		}
		
		if (options == null || options.resultType == null) {
			return new XYSeriesCollection();
		}

		// Ensure required computations are performed based on result type
		EnumResults resultType = options.resultType;
		switch (resultType) {
		case DISTANCE:
			flyPositions.computeDistanceBetweenConsecutivePoints();
			break;
		case ISALIVE:
			flyPositions.computeIsAlive();
			break;
		case SLEEP:
			flyPositions.computeSleep();
			break;
		case ELLIPSEAXES:
			flyPositions.computeEllipseAxes();
			break;
		default:
			// No computation needed for position types
			break;
		}
		
		XYSeriesCollection dataset = new XYSeriesCollection();
		String name = cage.getRoi() != null ? cage.getRoi().getName() : 
		             (cage.getCageRoi2D() != null ? cage.getCageRoi2D().getName() : 
		              "Cage " + cage.getProperties().getCageID());
		XYSeries seriesXY = new XYSeries(name, false);
		seriesXY.setDescription(name);
		
		addPointsToXYSeries(cage, flyPositions, resultType, seriesXY);
		
		if (seriesXY.getItemCount() > 0) {
			dataset.addSeries(seriesXY);
		}
		
		return dataset;
	}
	
	/**
	 * Adds data points to an XY series based on the export option.
	 * 
	 * @param cage       the cage containing the data
	 * @param flyPositions the fly positions data
	 * @param resultType the type of data to extract
	 * @param seriesXY   the series to add points to
	 */
	private void addPointsToXYSeries(Cage cage, FlyPositions flyPositions, EnumResults resultType, XYSeries seriesXY) {
		if (cage == null || seriesXY == null || flyPositions == null) {
			Logger.warn("Cannot add points: cage, series, or flyPositions is null");
			return;
		}

		if (flyPositions.flyPositionList == null || flyPositions.flyPositionList.isEmpty()) {
			Logger.warn("No fly positions data for cage ID: " + cage.getProperties().getCageID());
			return;
		}

		int itmax = flyPositions.flyPositionList.size();

		switch (resultType) {
		case DISTANCE:
			processDistanceData(flyPositions, seriesXY, itmax, cage);
			break;

		case ISALIVE:
			processAliveData(flyPositions, seriesXY, itmax);
			break;

		case SLEEP:
			processSleepData(flyPositions, seriesXY, itmax);
			break;

		case ELLIPSEAXES:
			processEllipseAxesData(flyPositions, seriesXY, itmax);
			break;

		case XYIMAGE:
		case XYTOPCAGE:
			processPositionData(flyPositions, seriesXY, itmax, cage);
			break;

		case XYTIPCAPS:
			processXPositionData(flyPositions, seriesXY, itmax);
			break;

		default:
			processPositionData(flyPositions, seriesXY, itmax, cage);
			break;
		}
	}

	/**
	 * Processes distance data for a cage.
	 * Uses the precomputed distance value from flyPositions.computeDistanceBetweenConsecutivePoints().
	 */
	private void processDistanceData(FlyPositions results, XYSeries seriesXY, int itmax, Cage cage) {
		for (int it = 0; it < itmax; it++) {
			FlyPosition pos = results.flyPositionList.get(it);
			double distance = pos.distance;
			addxyPos(seriesXY, pos, distance);
		}
	}

	/**
	 * Processes alive status data for a cage.
	 */
	private void processAliveData(FlyPositions results, XYSeries seriesXY, int itmax) {
		for (int it = 0; it < itmax; it++) {
			FlyPosition pos = results.flyPositionList.get(it);
			boolean alive = pos.bAlive;
			double ypos = alive ? ALIVE_VALUE : DEAD_VALUE;
			addxyPos(seriesXY, pos, ypos);
		}
	}

	/**
	 * Processes sleep status data for a cage.
	 */
	private void processSleepData(FlyPositions results, XYSeries seriesXY, int itmax) {
		for (int it = 0; it < itmax; it++) {
			FlyPosition pos = results.flyPositionList.get(it);
			boolean asleep = pos.bSleep;
			double ypos = asleep ? SLEEP_VALUE : AWAKE_VALUE;
			addxyPos(seriesXY, pos, ypos);
		}
	}

	/**
	 * Processes Y position data for a cage (XYIMAGE, XYTOPCAGE).
	 */
	private void processPositionData(FlyPositions results, XYSeries seriesXY, int itmax, Cage cage) {
		Rectangle rect1 = null;
		if (cage.getRoi() != null) {
			rect1 = cage.getRoi().getBounds();
		} else if (cage.getCageRoi2D() != null) {
			rect1 = cage.getCageRoi2D().getBounds();
		}
		
		if (rect1 == null) {
			Logger.warn("Cannot process position data: cage ROI is null");
			return;
		}
		
		double yOrigin = rect1.getY() + rect1.getHeight();

		for (int it = 0; it < itmax; it++) {
			FlyPosition pos = results.flyPositionList.get(it);
			Rectangle2D itRect = pos.rectPosition;
			double ypos = yOrigin - itRect.getY();
			addxyPos(seriesXY, pos, ypos);
		}
	}

	/**
	 * Processes X position data for a cage (XYTIPCAPS).
	 */
	private void processXPositionData(FlyPositions results, XYSeries seriesXY, int itmax) {
		for (int it = 0; it < itmax; it++) {
			FlyPosition pos = results.flyPositionList.get(it);
			double xpos = pos.getCenterRectangle().getX();
			addxyPos(seriesXY, pos, xpos);
		}
	}

	/**
	 * Processes ellipse axes data for a cage.
	 */
	private void processEllipseAxesData(FlyPositions results, XYSeries seriesXY, int itmax) {
		for (int it = 0; it < itmax; it++) {
			FlyPosition pos = results.flyPositionList.get(it);
			double axis1 = pos.axis1;
			addxyPos(seriesXY, pos, axis1);
		}
	}

	/**
	 * Adds a single data point to the series.
	 * Converts time from milliseconds to minutes to match the pattern used by
	 * other builders (Capillary, Gulp).
	 */
	private void addxyPos(XYSeries seriesXY, FlyPosition pos, Double ypos) {
		if (seriesXY == null || pos == null) {
			Logger.warn("Cannot add position: series or position is null");
			return;
		}

		// Convert time from milliseconds to minutes (matching Capillary/Gulp pattern)
		double timeMinutes = pos.tMs / (60.0 * 1000.0);
		seriesXY.add(timeMinutes, ypos);
	}
}
