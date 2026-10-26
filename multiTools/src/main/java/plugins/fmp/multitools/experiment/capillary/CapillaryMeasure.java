package plugins.fmp.multitools.experiment.capillary;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import icy.roi.ROI;
import icy.type.geom.Polyline2D;
import icy.util.StringUtil;
import icy.util.XMLUtil;
import plugins.fmp.multitools.tools.polyline.Level2D;
import plugins.kernel.roi.roi2d.ROI2DPolyLine;

public class CapillaryMeasure {
	public Level2D polylineLevel = new Level2D();
	public Level2D polyline_old = new Level2D();
	public int[] limit = null;

	public String capName = "noname";
	public int capIndexKymo = -1;
	public String header = null;

    private boolean[] refinementQuality;
    private double[] qualityX, qualityY;

    /** Quality belongs to this exact trajectory; manual edits invalidate it. */
    public boolean[] getRefinementQuality() {
        if(refinementQuality==null || polylineLevel==null || polylineLevel.npoints!=refinementQuality.length
                || !Arrays.equals(qualityX,Arrays.copyOf(polylineLevel.xpoints,polylineLevel.npoints))
                || !Arrays.equals(qualityY,Arrays.copyOf(polylineLevel.ypoints,polylineLevel.npoints))) return null;
        return refinementQuality.clone();
    }
    public void setRefinementQuality(boolean[] quality) {
        refinementQuality=null;qualityX=null;qualityY=null;
        if(quality==null)return;
        if(polylineLevel==null || quality.length!=polylineLevel.npoints)
            throw new IllegalArgumentException("Refinement quality must match the level trajectory");
        refinementQuality=quality.clone();qualityX=Arrays.copyOf(polylineLevel.xpoints,polylineLevel.npoints);
        qualityY=Arrays.copyOf(polylineLevel.ypoints,polylineLevel.npoints);
    }
    private String qualityText() {
        boolean[] quality=getRefinementQuality();if(quality==null)return "";
        StringBuilder text=new StringBuilder();for(boolean valid:quality)text.append(valid?'1':'0');return text.toString();
    }
    private void readQuality(String text) {
        setRefinementQuality(null);
        if(polylineLevel==null || text==null || text.length()!=polylineLevel.npoints || !text.matches("[01]+"))return;
        boolean[] quality=new boolean[text.length()];for(int i=0;i<quality.length;i++)quality[i]=text.charAt(i)=='1';
        setRefinementQuality(quality);
    }
    private void appendQuality(StringBuffer row,String separator) {
        String text=qualityText();if(!text.isEmpty())row.append("refinement_quality=").append(text).append(separator);
    }
    private void readQualityTrailer(String[] data,int start) {
        setRefinementQuality(null);
        for(int i=start;i<data.length;i++)if(data[i].startsWith("refinement_quality="))readQuality(data[i].substring(19));
    }

	private final String ID_NPOINTS = "npoints";
	private final String ID_NAME = "name";
	private final String ID_N = "n";
	private final String ID_X = "x";
	private final String ID_Y = "y";

	// -------------------------

	public CapillaryMeasure(String capName) {
		this.capName = capName;
	}

	public CapillaryMeasure(String name, int indexImage, List<Point2D> limit) {
		this.capName = name;
		this.capIndexKymo = indexImage;
		polylineLevel = new Level2D(limit);
	}

	public void clear() {
        setRefinementQuality(null);
		polylineLevel = new Level2D();
	}

	/**
	 * Marks the measure at the given time index as invalid (no data), e.g. when
	 * that frame was dark. Sets ypoints[index] to Double.NaN so export/analysis can
	 * treat it as missing.
	 */
	public void setInvalidAt(int index) {
		if (polylineLevel == null || polylineLevel.npoints == 0)
			return;
		if (index < 0 || index >= polylineLevel.npoints)
			return;
		polylineLevel.ypoints[index] = Double.NaN;
	}

	/**
	 * Fills NaN y-values with linear interpolation between neighboring valid
	 * samples (edge NaNs copy the nearest valid value). Used after ROI cuts so
	 * gaps can stay empty until Validate/Save.
	 */
	public void fillInvalidYWithLinearInterpolation() {
		if (polylineLevel == null || polylineLevel.npoints == 0)
			return;
		double[] y = polylineLevel.ypoints;
		int n = polylineLevel.npoints;
		int firstValid = -1;
		for (int i = 0; i < n; i++) {
			if (!Double.isNaN(y[i])) {
				firstValid = i;
				break;
			}
		}
		if (firstValid < 0)
			return;
		for (int i = 0; i < firstValid; i++) {
			y[i] = y[firstValid];
		}
		int prevValid = firstValid;
		for (int i = firstValid + 1; i < n; i++) {
			if (!Double.isNaN(y[i])) {
				if (i > prevValid + 1) {
					int start = prevValid + 1;
					int end = i - 1;
					int len = end - start + 1;
					double y0 = y[prevValid];
					double y1 = y[i];
					for (int k = 0; k < len; k++) {
						double alpha = (double) (k + 1) / (len + 1);
						y[start + k] = y0 + alpha * (y1 - y0);
					}
				}
				prevValid = i;
			}
		}
		if (prevValid < n - 1) {
			for (int i = prevValid + 1; i < n; i++) {
				y[i] = y[prevValid];
			}
		}
	}

	public void setPolylineLevelFromTempData(String name, int indexImage, int xStart, int xEnd) {
		this.capName = name;
		this.capIndexKymo = indexImage;
		if (limit == null || limit.length == 0)
			return;

		if (xEnd < xStart)
			return;

		// Clamp xStart/xEnd so that we never read past the end of limit[]
		int maxIndex = limit.length - 1;
		if (xStart < 0)
			xStart = 0;
		if (xStart > maxIndex)
			xStart = maxIndex;
		if (xEnd < 0)
			xEnd = 0;
		if (xEnd > maxIndex)
			xEnd = maxIndex;
		if (xEnd < xStart)
			return;

		int npoints = xEnd - xStart + 1;
		double[] xpoints = new double[npoints];
		double[] ypoints = new double[npoints];
		int j = 0;
		for (int i = xStart; i <= xEnd; i++, j++) {
			xpoints[j] = i;
			ypoints[j] = limit[j];
		}
		polylineLevel = new Level2D(xpoints, ypoints, npoints);
	}

	public void setTempDataFromPolylineLevel() {
		int npoints = polylineLevel.npoints;
		limit = new int[npoints];
		for (int j = 0; j < npoints; j++) {
			limit[j] = (int) polylineLevel.ypoints[j];
		}
	}

	public int getNPoints() {
		if (polylineLevel == null)
			return 0;
		return polylineLevel.npoints;
	}

	public Level2D getPolylineLevel() {
		return polylineLevel;
	}

	public double getValueAt(int j) {
		// TODO Auto-generated method stub
		return polylineLevel.ypoints[j];
	}

	int restoreNPoints() {
		if (polyline_old != null)
			polylineLevel = polyline_old.clone();
		return polylineLevel.npoints;
	}

	void cropToNPoints(int npoints) {
		if (npoints > polylineLevel.npoints)
			return;

		if (polyline_old == null)
			polyline_old = polylineLevel.clone();

		Polyline2D pol = new Polyline2D();
		for (int i = 0; i < npoints; i++)
			pol.addPoint(polylineLevel.xpoints[i], polylineLevel.ypoints[i]);

		polylineLevel = new Level2D(pol);
	}

	public void copy(CapillaryMeasure cap) {
		if (cap.polylineLevel != null)
			polylineLevel = cap.polylineLevel.clone();
        setRefinementQuality(cap.getRefinementQuality());
	}

	public boolean isThereAnyMeasuresDone() {
		return (polylineLevel != null && polylineLevel.npoints > 0);
	}

	ArrayList<Integer> getMeasures(long seriesBinMs, long outputBinMs) {
		if (polylineLevel == null || polylineLevel.npoints == 0)
			return null;
		if (seriesBinMs <= 0 || outputBinMs <= 0)
			return null;
		long maxMs = (polylineLevel.ypoints.length - 1) * seriesBinMs;
		long npoints = (maxMs / outputBinMs) + 1;
		ArrayList<Integer> arrayInt = new ArrayList<Integer>((int) npoints);
		for (double iMs = 0; iMs <= maxMs; iMs += outputBinMs) {
			int index = (int) (iMs / seriesBinMs);
			if (index >= 0 && index < polylineLevel.npoints) {
				arrayInt.add((int) polylineLevel.ypoints[index]);
			}
		}
		return arrayInt;
	}

	List<Integer> getMeasures() {
		return getIntegerArrayFromPolyline2D();
	}

	int getLastMeasure() {
		if (polylineLevel == null || polylineLevel.npoints == 0)
			return 0;
		int lastitem = polylineLevel.ypoints.length - 1;
		int ivalue = (int) polylineLevel.ypoints[lastitem];
		return ivalue;
	}

	int getT0Measure() {
		if (polylineLevel == null || polylineLevel.npoints == 0)
			return 0;
		return (int) polylineLevel.ypoints[0];
	}

	int getLastDeltaMeasure() {
		if (polylineLevel == null || polylineLevel.npoints == 0)
			return 0;
		int lastitem = polylineLevel.ypoints.length - 1;
		return (int) (polylineLevel.ypoints[lastitem] - polylineLevel.ypoints[lastitem - 1]);
	}

	/**
	 * Sets this measure's polyline from the given ROI. Caller is responsible for
	 * having decided that this ROI belongs to this measure (e.g. via MeasureEditTarget).
	 */
	public void setFromROI(ROI2DPolyLine roi) {
		if (roi != null) {
			Polyline2D line = roi.getPolyline2D();
			if (line != null && line.npoints > 0)
				polylineLevel = new Level2D(line);
		}
	}

	boolean transferROIsToMeasures(List<ROI> listRois) {
		for (ROI roi : listRois) {
			String roiname = roi.getName();
			if (roi instanceof ROI2DPolyLine && roiname.contains(capName)) {
				polylineLevel = new Level2D(((ROI2DPolyLine) roi).getPolyline2D());
				return true;
			}
		}
		return false;
	}

	boolean transferROIToMeasure(ROI roi) {
		boolean flag = false;
		if (roi instanceof ROI2DPolyLine && roi.getName().contains(capName)) {
			polylineLevel = new Level2D(((ROI2DPolyLine) roi).getPolyline2D());
			flag = true;
		}
		return flag;
	}

	List<Integer> getIntegerArrayFromPolyline2D() {
		if (polylineLevel == null || polylineLevel.npoints == 0)
			return null;
		List<Integer> arrayInt = new ArrayList<Integer>(polylineLevel.ypoints.length);
		for (double i : polylineLevel.ypoints)
			arrayInt.add((int) i);
		return arrayInt;
	}

	// ----------------------------------------------------------------------

	public int loadCapillaryLimitFromXML(Node node, String nodename, String header) {
		setRefinementQuality(null);
        final Node nodeMeta = XMLUtil.getElement(node, nodename);
		int npoints = 0;
		polylineLevel = null;
		if (nodeMeta != null) {
			capName = XMLUtil.getElementValue(nodeMeta, ID_NAME, nodename);
			if (!capName.contains("_")) {
				this.header = header;
				capName = header + capName;
			}
			polylineLevel = loadPolyline2DFromXML(nodeMeta);
            readQuality(XMLUtil.getElementValue(nodeMeta,"refinement_quality",""));
			if (polylineLevel != null)
				npoints = polylineLevel.npoints;
		}
		final Node nodeMeta_old = XMLUtil.getElement(node, nodename + "old");
		if (nodeMeta_old != null)
			polyline_old = loadPolyline2DFromXML(nodeMeta_old);
		return npoints;
	}

	Level2D loadPolyline2DFromXML(Node nodeMeta) {
		Level2D line = null;
		int npoints1 = XMLUtil.getElementIntValue(nodeMeta, ID_NPOINTS, 0);
		if (npoints1 > 0) {
			double[] xpoints = new double[npoints1];
			double[] ypoints = new double[npoints1];
			for (int i = 0; i < npoints1; i++) {
				Element elmt = XMLUtil.getElement(nodeMeta, ID_N + i);
				if (i == 0)
					xpoints[i] = XMLUtil.getAttributeDoubleValue(elmt, ID_X, 0);
				else
					xpoints[i] = i + xpoints[0];
				ypoints[i] = XMLUtil.getAttributeDoubleValue(elmt, ID_Y, 0);
			}
			line = new Level2D(xpoints, ypoints, npoints1);
		}
		return line;
	}

	public void saveCapillaryLimit2XML(Node node, String nodename) {
		if (polylineLevel == null || polylineLevel.npoints == 0)
			return;
		final Node nodeMeta = XMLUtil.setElement(node, nodename);
		if (nodeMeta != null) {
			XMLUtil.setElementValue(nodeMeta, ID_NAME, capName);
			saveLevel2XML(nodeMeta, polylineLevel);
            XMLUtil.setElementValue(nodeMeta,"refinement_quality",qualityText());
			final Node nodeMeta_old = XMLUtil.setElement(node, nodename + "old");
			if (polyline_old != null && polyline_old.npoints != polylineLevel.npoints)
				saveLevel2XML(nodeMeta_old, polyline_old);
		}
	}

	void saveLevel2XML(Node nodeMeta, Polyline2D polyline) {
		XMLUtil.setElementIntValue(nodeMeta, ID_NPOINTS, polyline.npoints);
		for (int i = 0; i < polyline.npoints; i++) {
			Element elmt = XMLUtil.setElement(nodeMeta, ID_N + i);
			if (i == 0)
				XMLUtil.setAttributeDoubleValue(elmt, ID_X, polyline.xpoints[i]);
			XMLUtil.setAttributeDoubleValue(elmt, ID_Y, polyline.ypoints[i]);
		}
	}

	public void adjustToImageWidth(int imageSize) {
		if (polylineLevel == null || polylineLevel.npoints == 0)
			return;
		int npoints = polylineLevel.npoints;
		int npoints_old = 0;
		if (polyline_old != null && polyline_old.npoints > npoints)
			npoints_old = polyline_old.npoints;
		if (npoints == imageSize || npoints_old == imageSize)
			return;

		// reduce polyline npoints to imageSize
		if (npoints > imageSize) {
			int newSize = imageSize;
			if (npoints < npoints_old)
				newSize = 1 + imageSize * npoints / npoints_old;
			polylineLevel = polylineLevel.contractPolylineToNewSize(newSize);
			if (npoints_old != 0)
				polyline_old = polyline_old.contractPolylineToNewSize(imageSize);
		}
		// expand polyline npoints to imageSize
		else {
			int newSize = imageSize;
			if (npoints < npoints_old)
				newSize = imageSize * npoints / npoints_old;
			polylineLevel = polylineLevel.expandPolylineToNewWidth(newSize);
			if (npoints_old != 0)
				polyline_old = polyline_old.expandPolylineToNewWidth(imageSize);
		}
	}

	public void cropToImageWidth(int imageSize) {
		if (polylineLevel == null || polylineLevel.npoints == 0)
			return;
		int npoints = polylineLevel.npoints;
		if (npoints == imageSize)
			return;

		int npoints_old = 0;
		if (polyline_old != null && polyline_old.npoints > npoints)
			npoints_old = polyline_old.npoints;
		if (npoints == imageSize || npoints_old == imageSize)
			return;

		// reduce polyline npoints to imageSize
		int newSize = imageSize;
		polylineLevel = polylineLevel.cropPolylineToNewWidth(newSize);
	}

	// ----------------------------------------------------------------------

	public boolean cvsExportXYDataToRow(StringBuffer sbf, String sep) {
        boolean refined=getRefinementQuality()!=null;
		int npoints = 0;
		if (polylineLevel != null && polylineLevel.npoints > 0)
			npoints = polylineLevel.npoints;

		sbf.append(Integer.toString(npoints) + sep);
		if (npoints > 0) {
			for (int i = 0; i < polylineLevel.npoints; i++) {
				sbf.append((!refined ? StringUtil.toString(polylineLevel.xpoints[i]) : Double.toString(polylineLevel.xpoints[i])));
				sbf.append(sep);
				sbf.append((!refined ? StringUtil.toString(polylineLevel.ypoints[i]) : Double.toString(polylineLevel.ypoints[i])));
				sbf.append(sep);
			}
		}
		appendQuality(sbf,sep);
		return true;
	}

	public boolean cvsExportYDataToRow(StringBuffer sbf, String sep) {
        boolean refined=getRefinementQuality()!=null;
		int npoints = 0;
		if (polylineLevel != null && polylineLevel.npoints > 0)
			npoints = polylineLevel.npoints;

		sbf.append(Integer.toString(npoints) + sep);
		if (npoints > 0) {
			for (int i = 0; i < polylineLevel.npoints; i++) {
				sbf.append((!refined ? StringUtil.toString(polylineLevel.ypoints[i]) : Double.toString(polylineLevel.ypoints[i])));
				sbf.append(sep);
			}
		}

		appendQuality(sbf,sep);
		return true;
	}

	public boolean csvImportXYDataFromRow(String[] data, int startAt) {
        setRefinementQuality(null);
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
			polylineLevel = new Level2D(x, y, npoints);
            readQualityTrailer(data,offset);
		}
		return true;
	}

	public boolean csvImportYDataFromRow(String[] data, int startAt) {
        setRefinementQuality(null);
		int len = data.length;
		if (len <= startAt)
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
			polylineLevel = new Level2D(x, y, npoints);
            readQualityTrailer(data,offset);
		}
		return true;
	}

}
