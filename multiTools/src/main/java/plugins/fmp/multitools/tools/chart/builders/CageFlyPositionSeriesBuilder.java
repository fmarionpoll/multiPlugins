package plugins.fmp.multitools.tools.chart.builders;

import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.Map;

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

		EnumResults resultType = options.resultType;
		boolean multiFly = flyPositions.getNflies() > 1;

		// For multi-fly pseudo-id, derived measures are not meaningful.
		if (multiFly) {
			switch (resultType) {
			case DISTANCE:
			case ISALIVE:
			case SLEEP:
				return new XYSeriesCollection();
			default:
				break;
			}
		}

		// Ensure required computations are performed based on result type
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

		String name = cage.getRoi() != null ? cage.getRoi().getName()
				: (cage.getCageRoi2D() != null ? cage.getCageRoi2D().getName() : "Cage " + cage.getProperties().getCageID());

		if (!multiFly) {
			XYSeriesCollection dataset = new XYSeriesCollection();
			XYSeries seriesXY = new XYSeries(name, false);
			seriesXY.setDescription(name);
			addPointsToXYSeries(cage, flyPositions, resultType, seriesXY);
			if (seriesXY.getItemCount() > 0) {
				dataset.addSeries(seriesXY);
			}
			return dataset;
		}

		// Multi-fly: create one series per pseudo flyId (rank-by-area per frame).
		int maxFlyId = Math.max(0, flyPositions.getNflies() - 1);
		for (FlyPosition p : flyPositions.flyPositionList) {
			if (p != null && p.flyId >= 0) {
				maxFlyId = Math.max(maxFlyId, p.flyId);
			}
		}
		int nSeries = maxFlyId + 1;

		XYSeriesCollection dataset = new XYSeriesCollection();
		Map<Integer, XYSeries> byId = new HashMap<>();
		for (int flyId = 0; flyId < nSeries; flyId++) {
			XYSeries s = new XYSeries(name + "_fly" + flyId, false);
			s.setDescription(name + "_fly" + flyId);
			byId.put(flyId, s);
		}

		// Precompute ROI bounds and scale factors once per cage.
		Rectangle2D rect1 = null;
		if (cage.getRoi() != null) {
			rect1 = cage.getRoi().getBounds();
		} else if (cage.getCageRoi2D() != null) {
			rect1 = cage.getCageRoi2D().getBounds();
		}
		double sx = flyPositions.getMmPerPixelX();
		double sy = flyPositions.getMmPerPixelY();

		double yOrigin = 0;
		double yTop = 0;
		double xLeft = 0;
		if (rect1 != null) {
			yOrigin = rect1.getY() + rect1.getHeight();
			yTop = rect1.getY();
			xLeft = rect1.getX();
		}

		for (FlyPosition pos : flyPositions.flyPositionList) {
			if (pos == null)
				continue;
			if (pos.flyId < 0)
				continue;
			if (pos.rectPosition == null)
				continue;
			if (Double.isNaN(pos.rectPosition.getX()) || Double.isNaN(pos.rectPosition.getY()))
				continue;

			XYSeries s = byId.get(pos.flyId);
			if (s == null)
				continue;

			double timeMinutes = pos.tMs / (60.0 * 1000.0);

			double yOrX;
			switch (resultType) {
			case XYIMAGE:
				// Legacy semantics: Y from bottom of cage.
				if (rect1 == null)
					continue;
				yOrX = (yOrigin - pos.rectPosition.getY()) * sy;
				s.add(timeMinutes, yOrX);
				break;
			case XYTOPCAGE:
			case YTOPCAGE:
				if (rect1 == null)
					continue;
				yOrX = (pos.rectPosition.getY() - yTop) * sy;
				s.add(timeMinutes, yOrX);
				break;
			case XTOPCAGE:
				if (rect1 == null)
					continue;
				yOrX = (pos.rectPosition.getX() - xLeft) * sx;
				s.add(timeMinutes, yOrX);
				break;
			case XYTIPCAPS:
				yOrX = pos.getCenterRectangle().getX() * sx;
				s.add(timeMinutes, yOrX);
				break;
			case ELLIPSEAXES:
				yOrX = pos.axis1Mm;
				s.add(timeMinutes, yOrX);
				break;
			default:
				// Fallback to legacy bottom-of-cage Y.
				if (rect1 == null)
					continue;
				yOrX = (yOrigin - pos.rectPosition.getY()) * sy;
				s.add(timeMinutes, yOrX);
				break;
			}
		}

		for (int flyId = 0; flyId < nSeries; flyId++) {
			XYSeries s = byId.get(flyId);
			if (s != null && s.getItemCount() > 0) {
				dataset.addSeries(s);
			}
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

		 case XTOPCAGE:
		        processXTopCageData(cage, flyPositions, seriesXY, itmax);
		        break;
		    case YTOPCAGE:
		        processYTopCageData(cage, flyPositions, seriesXY, itmax);
		        break;
		        
		case XYIMAGE:
			// Y position measured from cage bottom (legacy behavior)
			processPositionDataFromBottom(flyPositions, seriesXY, itmax, cage);
			break;
		case XYTOPCAGE:
			// Y position measured from cage top (requested semantics)
			processPositionDataFromTop(flyPositions, seriesXY, itmax, cage);
			break;

		case XYTIPCAPS:
			processXPositionData(flyPositions, seriesXY, itmax);
			break;

		default:
			processPositionDataFromBottom(flyPositions, seriesXY, itmax, cage);
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
			double distance = pos.distanceMm;
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
	 * Processes Y position data for a cage, measured from the bottom edge
	 * (legacy behavior used for XYIMAGE and as default).
	 */
	private void processPositionDataFromBottom(FlyPositions results, XYSeries seriesXY, int itmax, Cage cage) {
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
		double sy = results.getMmPerPixelY();

		for (int it = 0; it < itmax; it++) {
			FlyPosition pos = results.flyPositionList.get(it);
			Rectangle2D itRect = pos.rectPosition;
			double ypos = (yOrigin - itRect.getY()) * sy;
			addxyPos(seriesXY, pos, ypos);
		}
	}

	/**
	 * Processes Y position data for a cage, measured from the top edge (distance
	 * from top of cage). Used for XYTOPCAGE.
	 */
	private void processPositionDataFromTop(FlyPositions results, XYSeries seriesXY, int itmax, Cage cage) {
		Rectangle rect1 = null;
		if (cage.getRoi() != null) {
			rect1 = cage.getRoi().getBounds();
		} else if (cage.getCageRoi2D() != null) {
			rect1 = cage.getCageRoi2D().getBounds();
		}

		if (rect1 == null) {
			Logger.warn("Cannot process position data from top: cage ROI is null");
			return;
		}

		double yTop = rect1.getY();
		double sy = results.getMmPerPixelY();

		for (int it = 0; it < itmax; it++) {
			FlyPosition pos = results.flyPositionList.get(it);
			Rectangle2D itRect = pos.rectPosition;
			double ypos = (itRect.getY() - yTop) * sy;
			addxyPos(seriesXY, pos, ypos);
		}
	}

	/**
	 * Processes X position data for a cage (XYTIPCAPS).
	 */
	private void processXPositionData(FlyPositions results, XYSeries seriesXY, int itmax) {
		double sx = results.getMmPerPixelX();
		for (int it = 0; it < itmax; it++) {
			FlyPosition pos = results.flyPositionList.get(it);
			double xpos = pos.getCenterRectangle().getX() * sx;
			addxyPos(seriesXY, pos, xpos);
		}
	}

	/**
	 * Processes ellipse axes data for a cage.
	 */
	private void processEllipseAxesData(FlyPositions results, XYSeries seriesXY, int itmax) {
		for (int it = 0; it < itmax; it++) {
			FlyPosition pos = results.flyPositionList.get(it);
			double axis1 = pos.axis1Mm;
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
	
	/**
	 * Processes Y position relative to the top of the cage (YTOPCAGE).
	 * This mirrors the existing behavior used for XYTOPCAGE charts.
	 */
	private void processYTopCageData(Cage cage, FlyPositions results, XYSeries seriesXY, int itmax) {
	    Rectangle rect1 = null;
	    if (cage.getRoi() != null) {
	        rect1 = cage.getRoi().getBounds();
	    } else if (cage.getCageRoi2D() != null) {
	        rect1 = cage.getCageRoi2D().getBounds();
	    }
	    if (rect1 == null) {
	        Logger.warn("Cannot process YTOPCAGE: cage ROI is null");
	        return;
	    }
	    double yTop = rect1.getY();
	    double sy = results.getMmPerPixelY();
	    for (int it = 0; it < itmax; it++) {
	        FlyPosition pos = results.flyPositionList.get(it);
	        Rectangle2D itRect = pos.rectPosition;
	        double ypos = (itRect.getY() - yTop) * sy;  // distance from top edge of cage
	        addxyPos(seriesXY, pos, ypos);
	    }
	}
	/**
	 * Processes X position relative to the left edge of the cage (XTOPCAGE).
	 */
	private void processXTopCageData(Cage cage, FlyPositions results, XYSeries seriesXY, int itmax) {
	    Rectangle rect1 = null;
	    if (cage.getRoi() != null) {
	        rect1 = cage.getRoi().getBounds();
	    } else if (cage.getCageRoi2D() != null) {
	        rect1 = cage.getCageRoi2D().getBounds();
	    }
	    if (rect1 == null) {
	        Logger.warn("Cannot process XTOPCAGE: cage ROI is null");
	        return;
	    }
	    double xLeft = rect1.getX();
	    double sx = results.getMmPerPixelX();
	    for (int it = 0; it < itmax; it++) {
	        FlyPosition pos = results.flyPositionList.get(it);
	        Rectangle2D itRect = pos.rectPosition;
	        double xpos = (itRect.getX() - xLeft) * sx;  // distance from left edge of cage
	        addxyPos(seriesXY, pos, xpos);
	    }
	}
}
