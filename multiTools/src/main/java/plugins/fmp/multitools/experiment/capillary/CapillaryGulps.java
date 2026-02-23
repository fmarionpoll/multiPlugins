package plugins.fmp.multitools.experiment.capillary;

import java.awt.Rectangle;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.w3c.dom.Node;

import icy.roi.ROI;
import icy.roi.ROI2D;
import icy.type.geom.Polyline2D;
import icy.util.StringUtil;
import icy.util.XMLUtil;
import plugins.fmp.multitools.tools.polyline.Level2D;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.kernel.roi.roi2d.ROI2DLine;
import plugins.kernel.roi.roi2d.ROI2DPolyLine;

public class CapillaryGulps {
	private final String ID_GULPS = "gulpsMC";
	/**
	 * Dense series of gulp heights (vertical extent yTop−yBottom) per kymograph X bin.
	 * Index = X position; value = height at that X. 0 = no gulp; &gt;0 = feeding extent; &lt;0 = negative event (excluded from metrics).
	 */
	private Level2D gulpHeights = new Level2D();

	// -------------------------------

	public void copy(CapillaryGulps capG) {
		if (capG == null)
			return;
		this.gulpHeights = capG.gulpHeights != null ? capG.gulpHeights.clone() : new Level2D();
	}

	/** npoints: kymograph width when known (>0); otherwise inferred from ROIs. */
	public boolean loadGulpsFromXML(Node node, int npoints) {
		boolean flag = false;
		ArrayList<ROI2D> rois = new ArrayList<ROI2D>();
		final Node nodeROIs = XMLUtil.getElement(node, ID_GULPS);
		if (nodeROIs != null) {
			flag = true;
			List<ROI> roislocal = ROI.loadROIsFromXML(nodeROIs);
			for (ROI roislocal_i : roislocal) {
				ROI2D roi = (ROI2D) roislocal_i;
				rois.add(roi);
			}
		}
		buildGulpsFromROIs(rois, npoints);
		return flag;
	}

	// -------------------------------
	public void clear() {
		gulpHeights = new Level2D();
	}

	public void ensureSize(int npoints) {
		if (npoints <= 0) {
			gulpHeights = new Level2D();
			return;
		}
		if (gulpHeights == null || gulpHeights.npoints != npoints) {
			double[] x = new double[npoints];
			double[] y = new double[npoints];
			for (int i = 0; i < npoints; i++)
				x[i] = i;
			gulpHeights = new Level2D(x, y, npoints);
		}
	}

	public void setAmplitudeAt(int index, double value) {
		if (gulpHeights == null || index < 0 || index >= gulpHeights.npoints)
			return;
		gulpHeights.ypoints[index] = value;
	}

	public Level2D getHeightSeries() {
		return gulpHeights;
	}

	public void addNewGulpFromPoints(ArrayList<Point2D> gulpPoints) {
		if (gulpPoints == null || gulpPoints.isEmpty())
			return;
		double minX = gulpPoints.get(0).getX(), maxX = minX;
		double minY = gulpPoints.get(0).getY(), maxY = minY;
		for (Point2D p : gulpPoints) {
			minX = Math.min(minX, p.getX());
			maxX = Math.max(maxX, p.getX());
			minY = Math.min(minY, p.getY());
			maxY = Math.max(maxY, p.getY());
		}
		int x = (int) Math.round(gulpPoints.get(0).getX());
		double height = maxY - minY;
		double width = maxX - minX;
		double amplitude = height >= width ? height : 1.0;
		if (amplitude <= 0)
			amplitude = 1.0;
		int size = Math.max((int) Math.ceil(maxX), x) + 1;
		ensureSize(size);
		if (x >= 0 && x < gulpHeights.npoints)
			gulpHeights.ypoints[x] = amplitude;
	}

	/**
	 * Adds one gulp from a vertical segment at xPixel with given y bounds. Ensures
	 * gulpHeights size and sets height at xPixel.
	 */
	public void addGulpFromVerticalSegment(int xPixel, double yBottom, double yTop, int npoints) {
		if (npoints <= 0 || xPixel < 0 || xPixel >= npoints)
			return;
		ensureSize(npoints);
		double amplitude = Math.abs(yTop - yBottom);
		gulpHeights.ypoints[xPixel] = amplitude;
	}

	boolean isThereAnyMeasuresDone() {
		return (gulpHeights != null && gulpHeights.npoints > 0);
	}

	private void convertPositiveAmplitudesIntoEvent(ArrayList<Integer> data_in) {
		if (data_in == null)
			return;

		int npoints = data_in.size();
		for (int i = 0; i < npoints; i++)
			data_in.set(i, data_in.get(i) > 0 ? 1 : 0);
	}

	private ArrayList<Integer> stretchArrayToOutputBins(ArrayList<Integer> data_in, long seriesBinMs,
			long outputBinMs) {
		if (data_in == null)
			return null;

		long npoints_out = data_in.size() * seriesBinMs / outputBinMs + 1;
		double time_last = data_in.size() * seriesBinMs;
		ArrayList<Integer> data_out = new ArrayList<Integer>((int) npoints_out);
		for (double time_out = 0; time_out <= time_last; time_out += outputBinMs) {
			int index_in = (int) (time_out / seriesBinMs);
			if (index_in >= data_in.size())
				index_in = data_in.size() - 1;
			data_out.add(data_in.get(index_in));
		}
		return data_out;
	}

	public ArrayList<Integer> getMeasuresFromGulps(EnumResults resultType, int npoints, long seriesBinMs,
			long outputBinMs) {
		ArrayList<Integer> data_in = null;
		switch (resultType) {
		case SUMGULPS:
		case SUMGULPS_LR:
			data_in = getCumSumFromGulps(npoints);
			data_in = stretchArrayToOutputBins(data_in, seriesBinMs, outputBinMs);
			break;
		case NBGULPS:
			data_in = getIsGulpsFromHeightSeries(npoints);
			data_in = stretchArrayToOutputBins(data_in, seriesBinMs, outputBinMs);
			break;
		case AMPLITUDEGULPS:
			data_in = getAmplitudeGulpsFromHeightSeries(npoints);
			data_in = stretchArrayToOutputBins(data_in, seriesBinMs, outputBinMs);
			break;
		case TTOGULP:
		case TTOGULP_LR:
			List<Integer> datag = getIsGulpsFromHeightSeries(npoints);
			data_in = getTToNextGulp(datag, npoints);
			data_in = stretchArrayToOutputBins(data_in, seriesBinMs, outputBinMs);
			break;

		case AUTOCORREL:
		case AUTOCORREL_LR:
		case CROSSCORREL:
		case CROSSCORREL_LR:
		case MARKOV_CHAIN:
			data_in = getAmplitudeGulpsFromHeightSeries(npoints);
			convertPositiveAmplitudesIntoEvent(data_in);
			data_in = stretchArrayToOutputBins(data_in, seriesBinMs, outputBinMs);
			break;

		default:
			break;
		}
		return data_in;
	}

	ArrayList<Integer> getTToNextGulp(List<Integer> datai, int npoints) {
		if (datai == null || datai.isEmpty()) {
			return new ArrayList<Integer>(Collections.nCopies(npoints, 0));
		}

		int size = datai.size();
		ArrayList<Integer> data_out = new ArrayList<Integer>(Collections.nCopies(size, 0));

		boolean foundGulp = false;
		int distanceToNextGulp = 0;

		for (int index = size - 1; index >= 0; index--) {
			if (datai.get(index) == 1) {
				distanceToNextGulp = 0;
				foundGulp = true;
				data_out.set(index, distanceToNextGulp);
			} else if (foundGulp) {
				distanceToNextGulp++;
				data_out.set(index, distanceToNextGulp);
			}
		}
		return data_out;
	}

	public void removeGulpsWithinInterval(int startPixel, int endPixel) {
		if (gulpHeights != null && gulpHeights.npoints > 0) {
			int start = Math.max(0, startPixel);
			int end = Math.min(endPixel, gulpHeights.npoints - 1);
			for (int i = start; i <= end; i++)
				gulpHeights.ypoints[i] = 0;
		}
	}

	// -------------------------------

	public boolean csvExportDataFlatToRow(StringBuffer sbf, String sep) {
		Level2D level2D = transferGulpsToLevel2D();
		if (level2D == null)
			level2D = new Level2D();
		int npoints = level2D.npoints;
		sbf.append(Integer.toString(npoints) + sep);
		for (int i = 0; i < npoints; i++) {
			sbf.append(StringUtil.toString((double) level2D.ypoints[i]));
			sbf.append(sep);
		}
		return true;
	}

	private Level2D transferGulpsToLevel2D() {
		return gulpHeights != null ? gulpHeights : new Level2D();
	}

	public boolean csvExportDataToRow(StringBuffer sbf, String sep, int npoints) {
		ArrayList<Integer> amplitudeGulps = getAmplitudeGulpsFromHeightSeries(npoints);
		sbf.append(Integer.toString(npoints) + sep);
		if (amplitudeGulps != null) {
			for (int i = 0; i < npoints; i++) {
				sbf.append(Integer.toString(amplitudeGulps.get(i)));
				sbf.append(sep);
			}
		}
		return true;
	}

	public void csvImportDataFromRow(String[] data, int startAt) {
		// Need at least one value at index startAt (npoints / ngulps)
		if (data == null || data.length <= startAt)
			return;

		clear();
		int firstValue = (int) Double.parseDouble(data[startAt]);
		int offset = startAt + 1;

		// Check for legacy sparse format: "ngulps", "g0", ...
		if (offset < data.length && data[offset].trim().startsWith("g")) {
			int ngulps = firstValue;
			for (int i = 0; i < ngulps; i++) {
				offset = csvImportOneGulp(data, offset);
			}
		} else {
			// New dense format: "npoints", val0, val1, ...
			int npoints = firstValue;
			ensureSize(npoints);
			for (int i = 0; i < npoints; i++) {
				if (offset >= data.length)
					break;
				double val = Double.parseDouble(data[offset]);
				offset++;
				gulpHeights.ypoints[i] = val;
			}
		}
	}

	private int csvImportOneGulp(String[] data, int offset) {
		offset++;
		int npoints = (int) Double.parseDouble(data[offset]);
		offset++;

		int[] x = new int[npoints];
		int[] y = new int[npoints];
		for (int i = 0; i < npoints; i++) {
			x[i] = (int) Double.parseDouble(data[offset]);
			offset++;
			y[i] = (int) Double.parseDouble(data[offset]);
			offset++;
		}
		int xPixel = npoints > 0 ? x[0] : 0;
		double yMin = npoints > 0 ? y[0] : 0;
		double yMax = yMin;
		for (int i = 0; i < npoints; i++) {
			yMin = Math.min(yMin, y[i]);
			yMax = Math.max(yMax, y[i]);
		}
		double amplitude = Math.max(1.0, yMax - yMin);
		int maxX = xPixel;
		for (int i = 0; i < npoints; i++)
			maxX = Math.max(maxX, x[i]);
		ensureSize(maxX + 1);
		if (xPixel >= 0 && xPixel < gulpHeights.npoints)
			gulpHeights.ypoints[xPixel] = amplitude;
		return offset;
	}

	// -------------------------------

	/**
	 * Builds gulp heights from ROIs. When npoints > 0 (kymograph width), uses that size
	 * and skips inferring extent from ROIs; otherwise infers from ROIs for backward compatibility.
	 */
	void buildGulpsFromROIs(ArrayList<ROI2D> rois, int npoints) {
		clear();
		// make sure that array is large enough
		if (npoints > 0) {
			ensureSize(npoints);
		} else {
			int maxX = 0;
			for (ROI2D roi : rois) {
				if (roi instanceof ROI2DLine) {
					Line2D line = ((ROI2DLine) roi).getLine();
					int xPixel = (int) Math.round((line.getX1() + line.getX2()) / 2);
					if (xPixel + 1 > maxX)
						maxX = xPixel + 1;
				} else {
					Rectangle rect = roi.getBounds();
					if (!rect.isEmpty()) {
						int endX = rect.x + rect.width;
						if (endX > maxX)
							maxX = endX;
					}
				}
			}
			if (maxX > 0)
				ensureSize(maxX);
		}
		// transfer roi to measure
		for (ROI2D roi : rois) {
			if (roi instanceof ROI2DLine)
				addGulpFromROI2DLine((ROI2DLine) roi);
			else if (roi instanceof ROI2DPolyLine)
				addGulpFromROI2DPolyLine((ROI2DPolyLine) roi);
		}
	}

	private void addGulpFromROI2DPolyLine(ROI2DPolyLine roi) {
		Polyline2D gulpPolyline = roi.getPolyline2D();
		if (gulpPolyline.npoints < 1 || gulpHeights == null || gulpHeights.npoints <= 0)
			return;
		int x;
		double amplitude;
		if (gulpPolyline.npoints == 2) {
			double x0 = gulpPolyline.xpoints[0], x1 = gulpPolyline.xpoints[1];
			double y0 = gulpPolyline.ypoints[0], y1 = gulpPolyline.ypoints[1];
			double height = Math.abs(y1 - y0);
			double width = Math.abs(x1 - x0);
			if (height >= width) {
				x = (int) Math.round(x0);
				amplitude = height;
			} else {
				x = (int) Math.round((x0 + x1) / 2);
				amplitude = 1.0;
			}
		} else {
			x = (int) gulpPolyline.xpoints[0];
			amplitude = 1.0;
		}
		if (x >= 0 && x < gulpHeights.npoints)
			gulpHeights.ypoints[x] = amplitude;
	}

	private void addGulpFromROI2DLine(ROI2DLine roi) {
		Line2D line = roi.getLine();
		double x1 = line.getX1(), y1 = line.getY1(), x2 = line.getX2(), y2 = line.getY2();
		int x = (int) Math.round((x1 + x2) / 2);
		double yBottom = Math.min(y1, y2);
		double yTop = Math.max(y1, y2);
		double amplitude = Math.abs(yTop - yBottom);
		if (amplitude <= 0 || gulpHeights == null || gulpHeights.npoints <= 0 || x < 0
				|| x >= gulpHeights.npoints)
			return;
		gulpHeights.ypoints[x] = amplitude;
	}

	/**
	 * Replaces gulp measures with heights derived from the given ROIs (gulp ROIs only).
	 * npoints: kymograph width when known (>0); otherwise inferred from ROIs.
	 */
	public void transferROIsToMeasures(List<? extends ROI> listRois, int npoints) {
		ArrayList<ROI2D> rois = new ArrayList<ROI2D>();
		for (ROI roi : listRois) {
			String roiname = roi.getName();
			if (roiname == null || !roiname.contains("gulp"))
				continue;

			if (roi instanceof ROI2DLine) {
				rois.add((ROI2DLine) roi);
			} else if (roi instanceof ROI2DPolyLine) {
				rois.add((ROI2DPolyLine) roi);
			}
		}
		buildGulpsFromROIs(rois, npoints);
	}

	ArrayList<Integer> getCumSumFromGulps(int npoints) {
		ArrayList<Integer> sum = new ArrayList<Integer>(Collections.nCopies(npoints, 0));
		ArrayList<Integer> amp = getAmplitudeGulpsFromHeightSeries(npoints);
		if (amp == null)
			return sum;
		int running = 0;
		for (int i = 0; i < npoints; i++) {
			int v = amp.get(i);
			running += v;
			sum.set(i, running);
		}
		return sum;
	}

	private ArrayList<Integer> getAmplitudeGulpsFromHeightSeries(int npoints) {
		if (gulpHeights == null || gulpHeights.npoints == 0)
			return null;
		int n = Math.min(npoints, gulpHeights.npoints);
		ArrayList<Integer> out = new ArrayList<Integer>(Collections.nCopies(npoints, 0));
		for (int i = 0; i < n; i++)
			out.set(i, (int) gulpHeights.ypoints[i]);
		return out;
	}

	private ArrayList<Integer> getIsGulpsFromHeightSeries(int npoints) {
		ArrayList<Integer> amp = getAmplitudeGulpsFromHeightSeries(npoints);
		if (amp == null)
			return null;
		for (int i = 0; i < amp.size(); i++) {
			// gulp is an interval with feeding: amplitude > 0
			amp.set(i, amp.get(i) > 0 ? 1 : 0);
		}
		return amp;
	}

}