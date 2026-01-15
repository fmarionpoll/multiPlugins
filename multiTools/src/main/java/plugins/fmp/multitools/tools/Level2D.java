package plugins.fmp.multiSPOTS96.tools;

import java.awt.geom.Point2D;
import java.util.List;

import icy.type.geom.Polyline2D;

public class Level2D extends Polyline2D {

	public Level2D() {
		super();
	}

	public Level2D(Polyline2D polyline) {
		super(polyline.xpoints, polyline.ypoints, polyline.npoints);
	}

	public Level2D(double[] xpoints, double[] ypoints, int npoints) {
		super(xpoints, ypoints, npoints);
	}

	public Level2D(int[] xpoints, int[] ypoints, int npoints) {
		super(xpoints, ypoints, npoints);
	}

	public Level2D(List<Point2D> limit) {
		super();
		npoints = limit.size();
		xpoints = new double[npoints];
		ypoints = new double[npoints];
		int index = 0;
		for (Point2D pt : limit) {
			xpoints[index] = pt.getX();
			ypoints[index] = pt.getY();
			index++;
		}
	}

	public boolean insertSeriesofYPoints(List<Point2D> points, int start, int end) {
		if (start < 0 || end > (this.npoints - 1))
			return false;
		int i_list = 0;
		for (int i_array = start; i_array < end; i_array++, i_list++)
			ypoints[i_array] = points.get(i_list).getY();
		return true;
	}

	public boolean insertYPoints(int[] points, int start, int end) {
		if (start < 0 || end > (this.npoints - 1))
			return false;
		int i_list = 0;
		for (int i_array = start; i_array <= end; i_array++, i_list++)
			this.ypoints[i_array] = points[i_list];
		return true;
	}

	@Override
	public Level2D clone() {
		Level2D pol = new Level2D();
		for (int i = 0; i < npoints; i++)
			pol.addPoint(xpoints[i], ypoints[i]);
		return pol;
	}

	public Level2D expandPolylineToNewSize(int imageSize) {
		double[] nxpoints = new double[imageSize];
		double[] nypoints = new double[imageSize];
		for (int j = 0; j < npoints; j++) {
			int i0 = j * imageSize / npoints;
			int i1 = (j + 1) * imageSize / npoints;
			double y0 = ypoints[j];
			double y1 = y0;
			if ((j + 1) < npoints)
				y1 = ypoints[j + 1];
			for (int i = i0; i < i1; i++) {
				nxpoints[i] = i;
				nypoints[i] = y0 + (y1 - y0) * (i - i0) / (i1 - i0);
			}
		}
		return new Level2D(nxpoints, nypoints, imageSize);
	}

	public Level2D contractPolylineToNewSize(int imageSize) {
		double[] nxpoints = new double[imageSize];
		double[] nypoints = new double[imageSize];
		for (int i = 0; i < imageSize; i++) {
			int j = i * npoints / imageSize;
			nxpoints[i] = i;
			nypoints[i] = ypoints[j];
		}
		return new Level2D(nxpoints, nypoints, imageSize);
	}

	public Level2D cropPolylineToNewSize(int imageSize) {
		double[] nxpoints = new double[imageSize];
		double[] nypoints = new double[imageSize];
		for (int i = 0; i < imageSize; i++) {
			int j = i;
			if (j < npoints)
				nypoints[i] = ypoints[j];
			else
				nypoints[i] = ypoints[npoints - 1];
			nxpoints[i] = i;
		}
		return new Level2D(nxpoints, nypoints, imageSize);
	}

	public void offsetToStartWithZeroAmplitude() {
		if (npoints == 0 || ypoints == null)
			return;
		double value0 = ypoints[0];
		for (int i = 0; i < npoints; i++) {
			ypoints[i] -= value0;
		}
	}
}
