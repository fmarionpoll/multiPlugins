package plugins.fmp.multitools.experiment.capillary;

import java.awt.Rectangle;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.w3c.dom.Node;

import icy.roi.ROI;
import icy.roi.ROI2D;
import icy.type.geom.Polyline2D;
import icy.util.StringUtil;
import icy.util.XMLUtil;
import plugins.fmp.multitools.tools.polyline.Level2D;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.kernel.roi.roi2d.ROI2DArea;
import plugins.kernel.roi.roi2d.ROI2DLine;
import plugins.kernel.roi.roi2d.ROI2DPolyLine;

public class CapillaryGulps {
	private final String ID_GULPS = "gulpsMC";
	/**
	 * Canonical gulp storage: dense amplitude series aligned to kymograph X bins.
	 * <ul>
	 * <li>0: no feeding detected</li>
	 * <li>&gt;0: feeding amount during this bin</li>
	 * <li>&lt;0: negative event (kept in raw series; excluded from counting
	 * metrics)</li>
	 * </ul>
	 */
	private Level2D gulpAmplitude = new Level2D();

	/**
	 * Legacy representation (no longer the primary storage). Kept for minimal
	 * compatibility with older ROI-based code paths.
	 */
	@Deprecated
	public ArrayList<Polyline2D> gulps = new ArrayList<Polyline2D>();

	// -------------------------------

	public void copy(CapillaryGulps capG) {
		if (capG == null)
			return;
		this.gulpAmplitude = capG.gulpAmplitude != null ? capG.gulpAmplitude.clone() : new Level2D();
		// legacy
		this.gulps = new ArrayList<Polyline2D>(capG.gulps.size());
		this.gulps.addAll(capG.gulps);
	}

	public boolean loadGulpsFromXML(Node node) {
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

		buildGulpsFromROIs(rois);
		return flag;
	}

	// -------------------------------
	public void clear() {
		gulpAmplitude = new Level2D();
	}

	public void ensureSize(int npoints) {
		if (npoints <= 0) {
			gulpAmplitude = new Level2D();
			return;
		}
		if (gulpAmplitude == null || gulpAmplitude.npoints != npoints) {
			double[] x = new double[npoints];
			double[] y = new double[npoints];
			for (int i = 0; i < npoints; i++)
				x[i] = i;
			gulpAmplitude = new Level2D(x, y, npoints);
		}
	}

	public void setAmplitudeAt(int index, double value) {
		if (gulpAmplitude == null || index < 0 || index >= gulpAmplitude.npoints)
			return;
		gulpAmplitude.ypoints[index] = value;
	}

	public Level2D getAmplitudeSeries() {
		return gulpAmplitude;
	}

	public void addNewGulpFromPoints(ArrayList<Point2D> gulpPoints) {
		int npoints = gulpPoints.size();
		if (npoints < 1)
			return;

		double[] xpoints = new double[npoints];
		double[] ypoints = new double[npoints];
		for (int i = 0; i < npoints; i++) {
			xpoints[i] = gulpPoints.get(i).getX();
			ypoints[i] = gulpPoints.get(i).getY();
		}
		Polyline2D gulpLine = new Polyline2D(xpoints, ypoints, npoints);
		gulps.add(gulpLine);
	}

	/**
	 * Adds one gulp from a vertical segment at xPixel with given y bounds.
	 * Ensures gulpAmplitude size, appends to legacy gulps, and sets amplitude (overwrite at xPixel).
	 */
	public void addGulpFromVerticalSegment(int xPixel, double yBottom, double yTop, int npoints) {
		if (npoints <= 0 || xPixel < 0 || xPixel >= npoints)
			return;
		ensureSize(npoints);
		double amplitude = Math.abs(yTop - yBottom);
		double[] xpoints = new double[] { xPixel, xPixel };
		double[] ypoints = new double[] { yBottom, yTop };
		gulps.add(new Polyline2D(xpoints, ypoints, 2));
		gulpAmplitude.ypoints[xPixel] = amplitude;
	}

	boolean isThereAnyMeasuresDone() {
		return (gulpAmplitude != null && gulpAmplitude.npoints > 0);
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
			data_in = getIsGulpsFromAmplitudeSeries(npoints);
			data_in = stretchArrayToOutputBins(data_in, seriesBinMs, outputBinMs);
			break;
		case AMPLITUDEGULPS:
			data_in = getAmplitudeGulpsFromAmplitudeSeries(npoints);
			data_in = stretchArrayToOutputBins(data_in, seriesBinMs, outputBinMs);
			break;
		case TTOGULP:
		case TTOGULP_LR:
			List<Integer> datag = getIsGulpsFromAmplitudeSeries(npoints);
			data_in = getTToNextGulp(datag, npoints);
			data_in = stretchArrayToOutputBins(data_in, seriesBinMs, outputBinMs);
			break;

		case AUTOCORREL:
		case AUTOCORREL_LR:
		case CROSSCORREL:
		case CROSSCORREL_LR:
		case MARKOV_CHAIN:
			data_in = getAmplitudeGulpsFromAmplitudeSeries(npoints);
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
		// Dense series: zero-out samples in the interval
		if (gulpAmplitude != null && gulpAmplitude.npoints > 0) {
			int start = Math.max(0, startPixel);
			int end = Math.min(endPixel, gulpAmplitude.npoints - 1);
			for (int i = start; i <= end; i++)
				gulpAmplitude.ypoints[i] = 0;
		}
		// Legacy list
		Iterator<Polyline2D> iterator = gulps.iterator();
		while (iterator.hasNext()) {
			Polyline2D gulp = iterator.next();
			Rectangle rect = ((Polyline2D) gulp).getBounds();
			if (rect.x >= startPixel && rect.x <= endPixel)
				iterator.remove();
		}
	}

	// -------------------------------

	public boolean csvExportDataFlatToRow(StringBuffer sbf, String sep) {
		Level2D polylineLevel = transferGulpsToLevel2D();
		if (polylineLevel == null)
			polylineLevel = new Level2D();
		int npoints = polylineLevel.npoints;
		sbf.append(Integer.toString(npoints) + sep);
		for (int i = 0; i < npoints; i++) {
			sbf.append(StringUtil.toString((double) polylineLevel.ypoints[i]));
			sbf.append(sep);
		}
		return true;
	}

	private Level2D transferGulpsToLevel2D() {
		// Canonical representation
		if (gulpAmplitude != null && gulpAmplitude.npoints > 0)
			return gulpAmplitude;

		// Fallback to legacy conversion if needed (should be rare)
		int npoints = 0;
		if (gulps != null) {
			for (Polyline2D gulpPolyline : gulps) {
				if (gulpPolyline.npoints > 0) {
					for (int i = 0; i < gulpPolyline.npoints; i++)
						npoints = Math.max(npoints, (int) gulpPolyline.xpoints[i]);
				}
			}
		}
		npoints++;
		ensureSize(npoints);
		ArrayList<Integer> amp = getAmplitudeGulpsFromROIsArray(npoints);
		if (amp != null) {
			for (int i = 0; i < npoints; i++)
				gulpAmplitude.ypoints[i] = amp.get(i);
		}
		return gulpAmplitude;
	}

	public boolean csvExportDataToRow(StringBuffer sbf, String sep, int npoints) {
		// Get the amplitude array (dense format: one value per bin)
		ArrayList<Integer> amplitudeGulps = getAmplitudeGulpsFromROIsArray(npoints);

		// Export: npoints, val0, val1, ... valN
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
			// Convert legacy ROIs into dense series as canonical representation
			transferGulpsToLevel2D();
		} else {
			// New dense format: "npoints", val0, val1, ...
			int npoints = firstValue;
			ensureSize(npoints);
			for (int i = 0; i < npoints; i++) {
				if (offset >= data.length)
					break;
				double val = Double.parseDouble(data[offset]);
				offset++;
				gulpAmplitude.ypoints[i] = val;
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
		Polyline2D gulpLine = new Polyline2D(x, y, npoints);
		gulps.add(gulpLine);

		return offset;
	}

	// -------------------------------

	public void buildGulpsFromROIs(ArrayList<ROI2D> rois) {
		// Clear existing gulp data
		gulps = new ArrayList<Polyline2D>(rois.size());

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

		for (ROI2D roi : rois) {
			if (roi instanceof ROI2DPolyLine) {
				Polyline2D gulpPolyline = ((ROI2DPolyLine) roi).getPolyline2D();
				gulps.add(gulpPolyline);

				// Extract x position and amplitude from gulp ROI (vertical or horizontal)
				if (gulpPolyline.npoints >= 1 && gulpAmplitude != null && gulpAmplitude.npoints > 0) {
					int x;
					double amplitude;
					if (gulpPolyline.npoints == 2) {
						double x0 = gulpPolyline.xpoints[0], x1 = gulpPolyline.xpoints[1];
						double y0 = gulpPolyline.ypoints[0], y1 = gulpPolyline.ypoints[1];
						double height = Math.abs(y1 - y0);
						double width = Math.abs(x1 - x0);
						// Vertical segment (along toplevel): use x from first point, amplitude = height
						if (height >= width) {
							x = (int) Math.round(x0);
							amplitude = height;
						} else {
							// Horizontal segment (across toplevel): use center x, amplitude = 1
							x = (int) Math.round((x0 + x1) / 2);
							amplitude = 1.0;
						}
					} else {
						x = (int) gulpPolyline.xpoints[0];
						amplitude = 1.0;
					}
					if (x >= 0 && x < gulpAmplitude.npoints)
						gulpAmplitude.ypoints[x] = amplitude;
				}
			} else if (roi instanceof ROI2DArea) {
				// Handle ROI2DArea (legacy compacted gulps format)
				Rectangle rect = roi.getBounds();
				if (rect.isEmpty())
					continue;

				// Iterate over the bounding box and check containment
				// Group points by X (time) to reconstruct gulp events
				for (int x = rect.x; x < rect.x + rect.width; x++) {
					ArrayList<Point2D> pts = new ArrayList<>();
					for (int y = rect.y; y < rect.y + rect.height; y++) {
						if (roi.contains(x, y)) {
							pts.add(new Point2D.Double(x, y));
						}
					}
					if (!pts.isEmpty()) {
						addNewGulpFromPoints(pts);
						// Mark this x position as having a gulp in the amplitude series
						if (x >= 0 && x < gulpAmplitude.npoints) {
							gulpAmplitude.ypoints[x] = 1.0;
						}
					}
				}
			} else if (roi instanceof ROI2DLine) {
				// Same conversion as EditLevels.addGulpFromLine: middle x, y range, amplitude
				Line2D line = ((ROI2DLine) roi).getLine();
				double x1 = line.getX1(), y1 = line.getY1(), x2 = line.getX2(), y2 = line.getY2();
				int xPixel = (int) Math.round((x1 + x2) / 2);
				double yBottom = Math.min(y1, y2);
				double yTop = Math.max(y1, y2);
				double amplitude = Math.abs(yTop - yBottom);
				if (amplitude > 0 && gulpAmplitude != null && gulpAmplitude.npoints > 0 && xPixel >= 0 && xPixel < gulpAmplitude.npoints) {
					gulps.add(new Polyline2D(new double[] { xPixel, xPixel }, new double[] { yBottom, yTop }, 2));
					gulpAmplitude.ypoints[xPixel] = amplitude;
				}
			}
		}
	}

	public void transferROIsToMeasures(List<ROI> listRois) {
		ArrayList<ROI2D> rois = new ArrayList<ROI2D>();
		for (ROI roi : listRois) {
			String roiname = roi.getName();
			if (roi instanceof ROI2DPolyLine) {
				if (roiname != null && roiname.contains("gulp"))
					rois.add((ROI2DPolyLine) roi);
			} else if (roi instanceof ROI2DArea) {
				if (roiname != null && roiname.contains("gulp"))
					rois.add((ROI2DArea) roi);
			} else if (roi instanceof ROI2DLine) {
				if (roiname != null && roiname.contains("gulp"))
					rois.add((ROI2DLine) roi);
			}
		}
		buildGulpsFromROIs(rois);
	}

	ArrayList<Integer> getCumSumFromGulps(int npoints) {
		ArrayList<Integer> sum = new ArrayList<Integer>(Collections.nCopies(npoints, 0));
		ArrayList<Integer> amp = getAmplitudeGulpsFromAmplitudeSeries(npoints);
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

	private ArrayList<Integer> getAmplitudeGulpsFromROIsArray(int npoints) {
		if (gulps == null || gulps.size() == 0)
			return null;

		ArrayList<Integer> amplitudeGulpsArray = new ArrayList<Integer>(Collections.nCopies(npoints, 0));
		for (Polyline2D gulpLine : gulps)
			addROItoAmplitudeGulpsArray(gulpLine, amplitudeGulpsArray);
		return amplitudeGulpsArray;
	}

	private void addROItoAmplitudeGulpsArray(Polyline2D polyline2D, ArrayList<Integer> amplitudeGulpsArray) {
		double yvalue = polyline2D.ypoints[0];
		int npoints = polyline2D.npoints;
		for (int j = 0; j < npoints; j++) {
			int timeIndex = (int) polyline2D.xpoints[j];
			int delta = (int) (polyline2D.ypoints[j] - yvalue);
			amplitudeGulpsArray.set(timeIndex, delta);
			yvalue = polyline2D.ypoints[j];
		}
	}

	private ArrayList<Integer> getAmplitudeGulpsFromAmplitudeSeries(int npoints) {
		if (gulpAmplitude == null || gulpAmplitude.npoints == 0)
			return null;
		int n = Math.min(npoints, gulpAmplitude.npoints);
		ArrayList<Integer> out = new ArrayList<Integer>(Collections.nCopies(npoints, 0));
		for (int i = 0; i < n; i++)
			out.set(i, (int) gulpAmplitude.ypoints[i]);
		return out;
	}

	private ArrayList<Integer> getIsGulpsFromAmplitudeSeries(int npoints) {
		ArrayList<Integer> amp = getAmplitudeGulpsFromAmplitudeSeries(npoints);
		if (amp == null)
			return null;
		for (int i = 0; i < amp.size(); i++) {
			// gulp is an interval with feeding: amplitude > 0
			amp.set(i, amp.get(i) > 0 ? 1 : 0);
		}
		return amp;
	}

}