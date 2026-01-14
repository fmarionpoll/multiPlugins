package plugins.fmp.multiSPOTS.experiment.spots;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import icy.roi.ROI2D;
import icy.type.geom.Polyline2D;
import icy.util.StringUtil;
import plugins.fmp.multiSPOTS.tools.polyline.Level2D;
import plugins.kernel.roi.roi2d.ROI2DPolyLine;

public class SpotMeasure {
	private Level2D level2D = new Level2D();
	private Level2D leve2D_old = new Level2D();
	public double[] values = null;
	public int[] isPresent = null;
	private ROI2DPolyLine roi = null;
	private double factor = 1.;

	private String name = "noname";

	// -------------------------

	SpotMeasure(String name) {
		this.setName(name);
	}

	public SpotMeasure(String name, List<Point2D> limit) {
		this.setName(name);
		setLevel2D(new Level2D(limit));
	}

	void copyLevel2D(SpotMeasure sourceSpotMeasure) {
		if (sourceSpotMeasure.getLevel2D() != null)
			setLevel2D(sourceSpotMeasure.getLevel2D().clone());
	}

	void clearLevel2D() {
		setLevel2D(new Level2D());
	}

	void initLevel2D_fromMeasureValues(String name) {
		this.setName(name);
		int ii_start = 0;
		if (values == null)
			return;
		int ii_end = values.length - 1;
		int npoints = values.length;

		double[] xpoints = new double[npoints];
		double[] ypoints = new double[npoints];
		int j = 0;
		for (int i = ii_start; i < ii_end; i++, j++) {
			xpoints[j] = i;
			ypoints[j] = values[j];
		}
		setLevel2D(new Level2D(xpoints, ypoints, npoints));
	}

	void initLevel2D_fromBooleans(String name) {
		this.setName(name);
		int xStart = 0;
		if (isPresent == null)
			return;
		int xEnd = isPresent.length - 1;
		int npoints = isPresent.length;
		double[] xpoints = new double[npoints];
		double[] ypoints = new double[npoints];
		int j = 0;
		for (int i = xStart; i < xEnd; i++, j++) {
			xpoints[j] = i;
			ypoints[j] = isPresent[j] > 0 ? 1d : 0d;
		}
		setLevel2D(new Level2D(xpoints, ypoints, npoints));
	}

	int getLevel2DNPoints() {
		if (getLevel2D() == null)
			return 0;
		return getLevel2D().npoints;
	}

	public Level2D getLevel2D() {
		return level2D;
	}

	public void setLevel2D(Level2D level2d) {
		level2D = level2d;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public ROI2DPolyLine getRoi() {
		return roi;
	}

	public void setRoi(ROI2DPolyLine roi) {
		this.roi = roi;
	}

	boolean isThereAnyMeasuresDone() {
		return (getLevel2D() != null && getLevel2D().npoints > 0);
	}

	ArrayList<Double> getLevel2D_Y_subsampled(long seriesBinMs, long outputBinMs) {
		if (getLevel2D() == null || getLevel2D().npoints == 0)
			return null;

		long maxMs = (getLevel2D().ypoints.length - 1) * seriesBinMs;
		long npoints = (maxMs / outputBinMs) + 1;
		ArrayList<Double> arrayDouble = new ArrayList<Double>((int) npoints);
		for (double iMs = 0; iMs <= maxMs; iMs += outputBinMs) {
			int index = (int) (iMs / seriesBinMs);
			arrayDouble.add(getLevel2D().ypoints[index]);
		}
		return arrayDouble;
	}

	List<Double> getLevel2D_Y() {
		if (getLevel2D() == null || getLevel2D().npoints == 0)
			return null;
		List<Double> arrayDouble = new ArrayList<Double>(getLevel2D().ypoints.length);
		for (double i : getLevel2D().ypoints)
			arrayDouble.add(i);
		return arrayDouble;
	}

	// ----------------------------------------------------------------------

	void adjustLevel2DToImageWidth(int imageWidth) {
		if (getLevel2D() == null || getLevel2D().npoints == 0)
			return;
		int npoints = getLevel2D().npoints;
		int npoints_old = 0;
		if (leve2D_old != null && leve2D_old.npoints > npoints)
			npoints_old = leve2D_old.npoints;
		if (npoints == imageWidth || npoints_old == imageWidth)
			return;

		// reduce polyline npoints to imageSize
		if (npoints > imageWidth) {
			int newSize = imageWidth;
			if (npoints < npoints_old)
				newSize = 1 + imageWidth * npoints / npoints_old;
			setLevel2D(getLevel2D().contractPolylineToNewWidth(newSize));
			if (npoints_old != 0)
				leve2D_old = leve2D_old.contractPolylineToNewWidth(imageWidth);
		}
		// expand polyline npoints to imageSize
		else {
			int newSize = imageWidth;
			if (npoints < npoints_old)
				newSize = imageWidth * npoints / npoints_old;
			setLevel2D(getLevel2D().expandPolylineToNewWidth(newSize));
			if (npoints_old != 0)
				leve2D_old = leve2D_old.expandPolylineToNewWidth(imageWidth);
		}
	}

	void cropLevel2DToNPoints(int npoints) {
		if (npoints >= getLevel2D().npoints)
			return;

		if (leve2D_old == null)
			leve2D_old = getLevel2D().clone();

		setLevel2D(getLevel2D().cropPolylineToNewWidth(npoints));
	}

	int restoreCroppedLevel2D() {
		if (leve2D_old != null)
			setLevel2D(leve2D_old.clone());
		return getLevel2D().npoints;
	}

	// ----------------------------------------------------------------------

	public ROI2DPolyLine getROIForImage(String name, int t, int imageHeight) {
		roi = getROI2DFromLevel2D(getLevel2D(), imageHeight);
		String roiname = name + "_" + getName();
		roi.setName(roiname);
		roi.setT(t);
		setROI2DColorAndStrokeFromName();
//		roi.setStroke(.5);  
		return roi;
	}

	private ROI2DPolyLine getROI2DFromLevel2D(Level2D level2D, int imageHeight) {
		Polyline2D polyline = new Polyline2D(level2D.xpoints, level2D.ypoints, level2D.npoints);

		factor = (double) imageHeight / level2D.getBounds().getMaxY();
		for (int i = 0; i < level2D.npoints; i++) {
			polyline.xpoints[i] = level2D.xpoints[i];
			polyline.ypoints[i] = level2D.ypoints[i] * factor;
		}
		return new ROI2DPolyLine(polyline);
	}

	public Polyline2D getPolyline2DFromLevel2D(Level2D level2D, int imageHeight) {
		Polyline2D polyline = new Polyline2D(level2D.xpoints, level2D.ypoints, level2D.npoints);
		factor = (double) imageHeight / level2D.getBounds().getMaxY();
		for (int i = 0; i < level2D.npoints; i++) {
			polyline.xpoints[i] = level2D.xpoints[i];
			polyline.ypoints[i] = level2D.ypoints[i] * factor;
		}
		return polyline;
	}

	private void setROI2DColorAndStrokeFromName() {
		Color color = null;
		Double stroke = 1.;
		switch (name) {
		case "sum":
			color = Color.red;
			stroke = .5;
			break;
		case "clean":
			color = Color.green;
			break;
		default:
			color = Color.blue;
			break;
		}
		roi.setColor(color);
		roi.setStroke(stroke);
	}

	public void transferROItoLevel2D() {
		Polyline2D polyline = roi.getPolyline2D();
		if (polyline.npoints != level2D.npoints) {
			level2D = new Level2D(polyline.npoints);
		}
		for (int i = 0; i < polyline.npoints; i++) {
			level2D.xpoints[i] = polyline.xpoints[i];
			level2D.ypoints[i] = polyline.ypoints[i] / factor;
		}
	}

	// ----------------------------------------------------------------------

	public void buildRunningMedian(int span, double[] yvalues) {

		int nbspan = span / 2;
		int sizeTempArray = nbspan * 2 + 1;
		double[] tempArraySorted = new double[sizeTempArray];
		double[] tempArrayCircular = new double[sizeTempArray];

		int npoints = yvalues.length;
		values = new double[npoints];
		int t2 = npoints - 1;
		for (int t1 = 0; t1 < nbspan; t1++, t2--) {
			values[t1] = yvalues[t1];
			values[t2] = yvalues[t2];
		}

		for (int t = 0; t < sizeTempArray; t++) {
			double value = yvalues[t];
			tempArrayCircular[t] = value;
			values[t] = value;
		}

		int iarraycircular = sizeTempArray - 1;
		for (int t = nbspan; t < npoints - nbspan; t++) {
			int bin = t + nbspan;
			double newvalue = yvalues[t];
			tempArrayCircular[iarraycircular] = newvalue;
			tempArraySorted = tempArrayCircular.clone();
			Arrays.sort(tempArraySorted);
			double median = tempArraySorted[nbspan];
			bin = t;
			values[bin] = median;

			iarraycircular++;
			if (iarraycircular >= sizeTempArray)
				iarraycircular = 0;
		}
	}

	public void compensateOffetUsingSelectedRoi(ROI2D roi, boolean bAdd) {
		int offset = (int) roi.getBounds().getHeight();
		if (!bAdd)
			offset = -offset;
		int left = (int) roi.getBounds().getX();

		Polyline2D polyline = getRoi().getPolyline2D();
		for (int i = left; i < polyline.npoints; i++) {
			polyline.ypoints[i] -= offset;
		}
		getRoi().setPolyline2D(polyline);
	}

	public void cutAndInterpolatePointsEnclosedInSelectedRoi(ROI2D roi) {
		Polyline2D polyline = getRoi().getPolyline2D();
		int first_pt_inside = -1;
		int last_pt_inside = -1;
		for (int i = 0; i < polyline.npoints; i++) {
			boolean isInside = roi.contains(polyline.xpoints[i], polyline.ypoints[i]);
			if (first_pt_inside < 0) {
				if (isInside)
					first_pt_inside = i;
				continue;
			}

			if (isInside) {
				last_pt_inside = i;
				continue;
			} else
				last_pt_inside = i - 1;

			if (first_pt_inside >= 0 && last_pt_inside >= 0) {
				extrapolateBetweenLimits(polyline, first_pt_inside, last_pt_inside);
				first_pt_inside = -1;
				last_pt_inside = -1;
			}
		}

		if (first_pt_inside >= 0 && last_pt_inside < 0) {
			extrapolateBetweenLimits(polyline, first_pt_inside, last_pt_inside);
		}
		getRoi().setPolyline2D(polyline);
	}

	void extrapolateBetweenLimits(Polyline2D polyline, int first_pt_inside, int last_pt_inside) {
		int first = first_pt_inside - 1;
		if (first <= 0)
			first = 0;
		int last = last_pt_inside + 1;
		if (last >= polyline.npoints)
			last = polyline.npoints - 1;
		if (last == 0)
			last = first;
		double startY = polyline.ypoints[first];
		if (first == 0)
			startY = 512.;
		double startX = polyline.xpoints[first];
		int npoints = last_pt_inside - first_pt_inside + 1;
		double deltaX = (polyline.xpoints[last] - polyline.xpoints[first]) / npoints;
		double deltaY = (polyline.ypoints[last] - startY) / npoints;

		int k = 0;
		for (int j = first_pt_inside; j < last_pt_inside + 1; j++, k++) {
			polyline.xpoints[j] = startX + deltaX * k;
			polyline.ypoints[j] = startY + deltaY * k;
		}
	}

	// ----------------------------------------------------------------------

	public boolean cvsExportXYDataToRow(StringBuffer sbf, String sep) {
		int npoints = 0;
		if (getLevel2D() != null && getLevel2D().npoints > 0)
			npoints = getLevel2D().npoints;

		sbf.append(Integer.toString(npoints) + sep);
		if (npoints > 0) {
			for (int i = 0; i < getLevel2D().npoints; i++) {
				sbf.append(StringUtil.toString((double) getLevel2D().xpoints[i]));
				sbf.append(sep);
				sbf.append(StringUtil.toString((double) getLevel2D().ypoints[i]));
				sbf.append(sep);
			}
		}
		return true;
	}

	public boolean cvsExportYDataToRow(StringBuffer sbf, String sep) {
		int npoints = 0;
		if (getLevel2D() != null && getLevel2D().npoints > 0)
			npoints = getLevel2D().npoints;

		sbf.append(Integer.toString(npoints) + sep);
		if (npoints > 0) {
			for (int i = 0; i < getLevel2D().npoints; i++) {
				sbf.append(StringUtil.toString((double) getLevel2D().ypoints[i]));
				sbf.append(sep);
			}
		}
		return true;
	}

	public boolean csvImportXYDataFromRow(String[] data, int startAt) {
		if (data.length < startAt)
			return false;

		int npoints = Integer.valueOf(data[startAt]);
		if (npoints > 0) {
			double[] x = new double[npoints];
			double[] y = new double[npoints];
			int offset = startAt + 1;
			for (int i = 0; i < npoints; i++) {
				x[i] = Double.valueOf(data[offset]);
				offset++;
				y[i] = Double.valueOf(data[offset]);
				offset++;
			}
			setLevel2D(new Level2D(x, y, npoints));
		}
		return true;
	}

	public boolean csvImportYDataFromRow(String[] data, int startAt) {
		if (data.length < startAt)
			return false;

		int npoints = Integer.valueOf(data[startAt]);
		if (npoints > 0) {
			double[] x = new double[npoints];
			double[] y = new double[npoints];
			int offset = startAt + 1;
			for (int i = 0; i < npoints; i++) {
				x[i] = i;
				y[i] = Double.valueOf(data[offset]);
				offset++;
			}
			setLevel2D(new Level2D(x, y, npoints));
		}
		return true;
	}

}
