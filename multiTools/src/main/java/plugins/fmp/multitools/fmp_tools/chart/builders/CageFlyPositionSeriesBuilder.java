package plugins.fmp.multitools.fmp_tools.chart.builders;

import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import plugins.fmp.multitools.fmp_tools.Logger;

import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import plugins.fmp.multitools.fmp_experiment.Experiment;
import plugins.fmp.multitools.fmp_experiment.cages.Cage;
import plugins.fmp.multitools.fmp_experiment.cages.FlyPositions;
import plugins.fmp.multitools.fmp_experiment.cages.FlyPosition;
import plugins.fmp.multitools.fmp_tools.results.EnumResults;
import plugins.fmp.multitools.fmp_tools.results.ResultsOptions;

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
		
		XYSeriesCollection dataset = new XYSeriesCollection();
		String name = cage.getRoi() != null ? cage.getRoi().getName() : 
		             (cage.getCageRoi2D() != null ? cage.getCageRoi2D().getName() : 
		              "Cage " + cage.getProperties().getCageID());
		XYSeries seriesXY = new XYSeries(name, false);
		seriesXY.setDescription(name);
		
		addPointsToXYSeries(cage, flyPositions, options.resultType, seriesXY);
		
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

		case XYTOPCAGE:
		default:
			processPositionData(flyPositions, seriesXY, itmax, cage);
			break;
		}
	}

	/**
	 * Processes distance data for a cage.
	 */
	private void processDistanceData(FlyPositions results, XYSeries seriesXY, int itmax, Cage cage) {
		if (itmax == 0) {
			return;
		}
		
		FlyPosition firstPos = results.flyPositionList.get(0);
		double previousY = firstPos.rectPosition.getY() + firstPos.rectPosition.getHeight() / 2;

		for (int it = 0; it < itmax; it++) {
			FlyPosition currentPos = results.flyPositionList.get(it);
			double currentY = currentPos.rectPosition.getY() + currentPos.rectPosition.getHeight() / 2;
			double ypos = currentY - previousY;
			addxyPos(seriesXY, currentPos, ypos);
			previousY = currentY;
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
	 * Processes position data for a cage.
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
	 * Adds a single data point to the series.
	 */
	private void addxyPos(XYSeries seriesXY, FlyPosition pos, Double ypos) {
		if (seriesXY == null || pos == null) {
			Logger.warn("Cannot add position: series or position is null");
			return;
		}

		double indexT = pos.flyIndexT;
		seriesXY.add(indexT, ypos);
	}
}
