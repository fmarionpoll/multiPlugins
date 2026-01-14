package plugins.fmp.multiSPOTS96.experiment.spots;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import icy.roi.ROI2D;
import icy.type.geom.Polyline2D;
import plugins.fmp.multiSPOTS96.tools.polyline.Level2D;
import plugins.kernel.roi.roi2d.ROI2DPolyLine;

public class SpotLevel2D {

	// === CONSTANTS ===
	private static final double DEFAULT_FACTOR = 1.0;

	// === CORE FIELDS ===
	private Level2D level2D;
	private Level2D level2DOld;
	private ROI2DPolyLine roi;
	private double factor;
	private String name;

	// === CONSTRUCTORS ===

	/**
	 * Creates a new SpotMeasure with the specified name.
	 * 
	 * @param name the measure name
	 * @throws IllegalArgumentException if name is null
	 */
	public SpotLevel2D(String name) {
		this.name = Objects.requireNonNull(name, "Name cannot be null");
		this.factor = DEFAULT_FACTOR;
		this.level2D = new Level2D();
		this.level2DOld = new Level2D();
	}

	/**
	 * Creates a new SpotMeasure with the specified name and limit points.
	 * 
	 * @param name  the measure name
	 * @param limit the limit points
	 * @throws IllegalArgumentException if name is null
	 */
	public SpotLevel2D(String name, List<Point2D> limit) {
		this(name);
		if (limit != null && !limit.isEmpty()) {
			setLevel2D(new Level2D(limit));
		}
	}

	// === CORE OPERATIONS ===

	public void copyLevel2D(SpotLevel2D source) {
		if (source != null && source.getLevel2D() != null) {
			setLevel2D(source.getLevel2D().clone());
		}
	}

	// === LEVEL2D MANAGEMENT ===

	public void clearLevel2D() {
		setLevel2D(new Level2D());
	}

	public void transferValues(double[] values) {
		if (values == null || values.length == 0) {
			return;
		}
		int npoints = values.length;
		double[] xpoints = new double[npoints];
		double[] ypoints = new double[npoints];
		for (int i = 0; i < npoints; i++) {
			xpoints[i] = i;
			ypoints[i] = values[i];
		}
		setLevel2D(new Level2D(xpoints, ypoints, npoints));
	}

	public double[] transferLevel2DToValues() {
		double[] values = new double[level2D.npoints];
		for (int i = 0; i < level2D.npoints; i++) {
			values[i] = level2D.ypoints[i];
		}
		return values;
	}

	public void transferIsPresent(int[] isPresent) {
		if (isPresent == null || isPresent.length == 0) {
			return;
		}
		int npoints = isPresent.length;
		double[] xpoints = new double[npoints];
		double[] ypoints = new double[npoints];
		for (int i = 0; i < npoints; i++) {
			xpoints[i] = i;
			ypoints[i] = isPresent[i] > 0 ? 1.0 : 0.0;
		}
		setLevel2D(new Level2D(xpoints, ypoints, npoints));
	}

	public int getLevel2DNPoints() {
		return level2D != null ? level2D.npoints : 0;
	}

	public Level2D getLevel2D() {
		return level2D;
	}

	public void setLevel2D(Level2D level2D) {
		this.level2D = level2D;
	}

	// === NAME MANAGEMENT ===

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = Objects.requireNonNull(name, "Name cannot be null");
	}

	// === ROI MANAGEMENT ===

	public ROI2DPolyLine getRoi() {
		return roi;
	}

	public void setRoi(ROI2DPolyLine roi) {
		this.roi = roi;
	}

	// === VALIDATION ===

	public boolean isThereAnyMeasuresDone() {
		return level2D != null && level2D.npoints > 0;
	}

	// === LEVEL2D DATA PROCESSING ===

	/**
	 * Gets level2D Y data subsampled.
	 * 
	 * @param seriesBinMs the series bin in milliseconds
	 * @param outputBinMs the output bin in milliseconds
	 * @return the subsampled data
	 */

	/**
	 * Gets level2D Y data.
	 * 
	 * @return the Y data
	 */
	public List<Double> getLevel2DYAsList() {
		if (level2D == null || level2D.ypoints == null) {
			return new ArrayList<>();
		}

		List<Double> result = new ArrayList<>();
		for (double value : level2D.ypoints) {
			result.add(value);
		}
		return result;
	}

	/**
	 * Adjusts level2D to image width.
	 * 
	 * @param imageWidth the image width
	 */
	public void adjustLevel2DToImageWidth(int imageWidth) {
		if (level2D == null) {
			return;
		}

		if (level2DOld == null || level2DOld.npoints == 0) {
			level2DOld = level2D.clone();
		}

		if (level2D.npoints > imageWidth) {
			level2D.cropPolylineToNewWidth(imageWidth);
		}
	}

	/**
	 * Crops level2D to specified number of points.
	 * 
	 * @param npoints the number of points
	 */
	public void cropLevel2DToNPoints(int npoints) {
		if (level2D != null) {
			level2D.cropPolylineToNewWidth(npoints);
		}
	}

	/**
	 * Restores cropped level2D.
	 * 
	 * @return the number of points restored
	 */
	public int restoreCroppedLevel2D() {
		if (level2DOld != null && level2DOld.npoints > 0) {
			level2D = level2DOld.clone();
			return level2DOld.npoints;
		}
		return 0;
	}

	// === ROI PROCESSING ===

	/**
	 * Gets ROI for image.
	 * 
	 * @param name        the ROI name
	 * @param time        the time
	 * @param imageHeight the image height
	 * @return the ROI
	 */
	public ROI2DPolyLine getROIForImage(String name, int time, int imageHeight) {
		if (level2D == null || level2D.npoints == 0) {
			return null;
		}

		Polyline2D polyline = getPolyline2DFromLevel2D(level2D, imageHeight);
		if (polyline == null) {
			return null;
		}

		ROI2DPolyLine result = new ROI2DPolyLine(polyline);
		result.setName(name);
		setROI2DColorAndStrokeFromName(result);
		return result;
	}

	/**
	 * Gets polyline2D from level2D.
	 * 
	 * @param level2D     the level2D
	 * @param imageHeight the image height
	 * @return the polyline2D
	 */
	public Polyline2D getPolyline2DFromLevel2D(Level2D level2D, int imageHeight) {
		if (level2D == null || level2D.npoints == 0) {
			return null;
		}
		return level2D.expandPolylineToNewWidth(imageHeight);
	}

	/**
	 * Sets ROI2D color and stroke from name.
	 * 
	 * @param roi the ROI to configure
	 */
	private void setROI2DColorAndStrokeFromName(ROI2DPolyLine roi) {
		if (roi == null) {
			return;
		}

		if (name.contains("sum")) {
			roi.setColor(Color.RED);
			roi.setStroke(2);
		} else if (name.contains("clean")) {
			roi.setColor(Color.GREEN);
			roi.setStroke(1);
		} else if (name.contains("flyPresent")) {
			roi.setColor(Color.BLUE);
			roi.setStroke(1);
		} else {
			roi.setColor(Color.YELLOW);
			roi.setStroke(1);
		}
	}

	/**
	 * Transfers ROI to level2D.
	 */
	public void transferROItoLevel2D() {
		if (roi == null) {
			return;
		}

		Polyline2D polyline = roi.getPolyline2D();
		if (polyline != null) {
			level2D = new Level2D(polyline);
		}
	}

	// === OFFSET COMPENSATION ===

	/**
	 * Compensates offset using selected ROI.
	 * 
	 * @param roi the ROI to use for compensation
	 * @param add whether to add or subtract the offset
	 */
	public void compensateOffsetUsingSelectedRoi(ROI2D roi, boolean add) {
		if (roi == null || level2D == null) {
			return;
		}

		// Implementation would depend on specific offset compensation logic
		// This is a placeholder for the actual implementation
	}

	/**
	 * Cuts and interpolates points enclosed in selected ROI.
	 * 
	 * @param roi the ROI
	 */
	public void cutAndInterpolatePointsEnclosedInSelectedRoi(ROI2D roi) {
		if (roi == null || level2D == null) {
			return;
		}

		// Implementation would depend on specific interpolation logic
		// This is a placeholder for the actual implementation
	}

	// === UTILITY METHODS ===

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null || getClass() != obj.getClass())
			return false;
		SpotLevel2D other = (SpotLevel2D) obj;
		return Objects.equals(name, other.name) && Objects.equals(level2D, other.level2D);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, factor, level2D);
	}

	@Override
	public String toString() {
		return String.format("SpotMeasure{name='%s', factor=%.2f, level2DPoints=%d}", name, factor,
				getLevel2DNPoints());
	}

}
