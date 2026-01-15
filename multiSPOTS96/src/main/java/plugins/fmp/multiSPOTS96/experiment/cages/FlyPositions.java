package plugins.fmp.multitools.experiment.cages;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import icy.util.XMLUtil;
import plugins.fmp.multitools.tools.Comparators;
import plugins.fmp.multitools.tools.ROI2D.Measures;
import plugins.fmp.multitools.tools.toExcel.EnumXLSExport;
import plugins.kernel.roi.roi2d.ROI2DArea;

public class FlyPositions {
	public Double moveThreshold = 50.;
	public int sleepThreshold = 5;
	public int lastTimeAlive = 0;
	public int lastIntervalAlive = 0;
	public ArrayList<FlyPosition> flyPositionList = new ArrayList<FlyPosition>();

	public String name = null;
	public EnumXLSExport exportType = null;
	public int binsize = 1;
	public Point2D origin = new Point2D.Double(0, 0);
	public double pixelsize = 1.;
	public int nflies = 1;

	private String ID_NBITEMS = "nb_items";
	private String ID_POSITIONSLIST = "PositionsList";
	private String ID_LASTIMEITMOVED = "lastTimeItMoved";
	private String ID_TLAST = "tlast";
	private String ID_ILAST = "ilast";

	public FlyPositions() {
	}

	public FlyPositions(String name, EnumXLSExport exportType, int nFrames, int binsize) {
		this.name = name;
		this.exportType = exportType;
		this.binsize = binsize;
		flyPositionList = new ArrayList<FlyPosition>(nFrames);
		for (int i = 0; i < nFrames; i++)
			flyPositionList.add(new FlyPosition(i));
	}

	public void clear() {
		flyPositionList.clear();
	}

	public void ensureCapacity(int nFrames) {
		flyPositionList.ensureCapacity(nFrames);
//		initArray(nFrames);
	}

	void initArray(int nFrames) {
		for (int i = 0; i < nFrames; i++) {
			FlyPosition value = new FlyPosition(i);
			flyPositionList.add(value);
		}
	}

	public Rectangle2D getRectangle(int i) {
		return flyPositionList.get(i).rectPosition;
	}

	public Rectangle2D getValidPointAtOrBefore(int index) {
		Rectangle2D rect = new Rectangle2D.Double(-1, -1, Double.NaN, Double.NaN);
		for (int i = index; i >= 0; i--) {
			FlyPosition xyVal = flyPositionList.get(i);
			if (xyVal.rectPosition.getX() >= 0 && xyVal.rectPosition.getY() >= 0) {
				rect = xyVal.rectPosition;
				break;
			}
		}
		return rect;
	}

	public int getTime(int i) {
		return flyPositionList.get(i).flyIndexT;
	}

	public void addPositionWithoutRoiArea(int t, Rectangle2D rectangle) {
		FlyPosition pos = new FlyPosition(t, rectangle);
		flyPositionList.add(pos);
	}

	public void addPositionWithRoiArea(int t, Rectangle2D rectangle, ROI2DArea roiArea) {
		FlyPosition pos = new FlyPosition(t, rectangle, roiArea);
		flyPositionList.add(pos);
	}

	public void copyXYTaSeries(FlyPositions xySeriesFrom) {
		moveThreshold = xySeriesFrom.moveThreshold;
		sleepThreshold = xySeriesFrom.sleepThreshold;
		lastTimeAlive = xySeriesFrom.lastIntervalAlive;
		flyPositionList = new ArrayList<FlyPosition>(xySeriesFrom.flyPositionList.size());
		flyPositionList.addAll(flyPositionList);
		name = xySeriesFrom.name;
		exportType = xySeriesFrom.exportType;
		binsize = xySeriesFrom.binsize;
	}

	public void pasteXYTaSeries(FlyPositions xySeriesTo) {
		xySeriesTo.moveThreshold = moveThreshold;
		xySeriesTo.sleepThreshold = sleepThreshold;
		xySeriesTo.lastTimeAlive = lastIntervalAlive;
		xySeriesTo.flyPositionList = new ArrayList<FlyPosition>(flyPositionList.size());
		xySeriesTo.flyPositionList.addAll(flyPositionList);
		xySeriesTo.name = name;
		xySeriesTo.exportType = exportType;
		xySeriesTo.binsize = binsize;
	}

	// -----------------------------------------------

	public boolean loadXYTseriesFromXML(Node node) {
		if (node == null)
			return false;

		Element node_lastime = XMLUtil.getElement(node, ID_LASTIMEITMOVED);
		lastTimeAlive = XMLUtil.getAttributeIntValue(node_lastime, ID_TLAST, -1);
		lastIntervalAlive = XMLUtil.getAttributeIntValue(node_lastime, ID_ILAST, -1);

		Element node_position_list = XMLUtil.getElement(node, ID_POSITIONSLIST);
		if (node_position_list == null)
			return false;

		flyPositionList.clear();
		int nb_items = XMLUtil.getAttributeIntValue(node_position_list, ID_NBITEMS, 0);
		flyPositionList.ensureCapacity(nb_items);
		for (int i = 0; i < nb_items; i++)
			flyPositionList.add(new FlyPosition(i));
		boolean bAdded = false;

		for (int i = 0; i < nb_items; i++) {
			String elementi = "i" + i;
			Element node_position_i = XMLUtil.getElement(node_position_list, elementi);
			FlyPosition pos = new FlyPosition();
			pos.loadXYTvaluesFromXML(node_position_i);
			if (pos.flyIndexT < nb_items)
				flyPositionList.set(pos.flyIndexT, pos);
			else {
				flyPositionList.add(pos);
				bAdded = true;
			}
		}

		if (bAdded)
			Collections.sort(flyPositionList, new Comparators.XYTaValue_Tindex());
		return true;
	}

	public boolean saveXYTseriesToXML(Node node) {
		if (node == null)
			return false;

		Element node_lastime = XMLUtil.addElement(node, ID_LASTIMEITMOVED);
		XMLUtil.setAttributeIntValue(node_lastime, ID_TLAST, lastTimeAlive);
		lastIntervalAlive = getLastIntervalAlive();
		XMLUtil.setAttributeIntValue(node_lastime, ID_ILAST, lastIntervalAlive);

		Element node_position_list = XMLUtil.addElement(node, ID_POSITIONSLIST);
		XMLUtil.setAttributeIntValue(node_position_list, ID_NBITEMS, flyPositionList.size());

		int i = 0;
		for (FlyPosition pos : flyPositionList) {
			String elementi = "i" + i;
			Element node_position_i = XMLUtil.addElement(node_position_list, elementi);
			pos.saveXYTvaluesToXML(node_position_i);
			i++;
		}
		return true;
	}

	// -----------------------------------------------

	public int computeLastIntervalAlive() {
		computeIsAlive();
		return lastIntervalAlive;
	}

	public void computeIsAlive() {
		computeDistanceBetweenConsecutivePoints();
		lastIntervalAlive = 0;
		boolean isalive = false;
		for (int i = flyPositionList.size() - 1; i >= 0; i--) {
			FlyPosition pos = flyPositionList.get(i);
			if (pos.distance > moveThreshold && !isalive) {
				lastIntervalAlive = i;
				lastTimeAlive = pos.flyIndexT;
				isalive = true;
			}
			pos.bAlive = isalive;
		}
	}

	public void checkIsAliveFromAliveArray() {
		lastIntervalAlive = 0;
		boolean isalive = false;
		for (int i = flyPositionList.size() - 1; i >= 0; i--) {
			FlyPosition pos = flyPositionList.get(i);
			if (!isalive && pos.bAlive) {
				lastIntervalAlive = i;
				lastTimeAlive = pos.flyIndexT;
				isalive = true;
			}
			pos.bAlive = isalive;
		}
	}

	public void computeDistanceBetweenConsecutivePoints() {
		if (flyPositionList.size() <= 0)
			return;

		// assume ordered points
		Point2D previousPoint = flyPositionList.get(0).getCenterRectangle();
		for (FlyPosition pos : flyPositionList) {
			Point2D currentPoint = pos.getCenterRectangle();
			pos.distance = currentPoint.distance(previousPoint);
			if (previousPoint.getX() < 0 || currentPoint.getX() < 0)
				pos.distance = Double.NaN;
			previousPoint = currentPoint;
		}
	}

	public void computeCumulatedDistance() {
		if (flyPositionList.size() <= 0)
			return;

		// assume ordered points
		double sum = 0.;
		for (FlyPosition pos : flyPositionList) {
			sum += pos.distance;
			pos.sumDistance = sum;
		}
	}

	// -----------------------------------------------------------

	public void excelComputeDistanceBetweenPoints(FlyPositions flyPositions, int dataStepMs, int excelStepMs) {
		if (flyPositions.flyPositionList.size() <= 0)
			return;

		flyPositions.computeDistanceBetweenConsecutivePoints();
		flyPositions.computeCumulatedDistance();

		int excel_startMs = 0;
		int n_excel_intervals = flyPositionList.size();
		int excel_endMs = n_excel_intervals * excelStepMs;
		int n_data_intervals = flyPositions.flyPositionList.size();

		double sumDistance_previous = 0.;

		for (int excel_Ms = excel_startMs; excel_Ms < excel_endMs; excel_Ms += excelStepMs) {
			int excel_bin = excel_Ms / excelStepMs;
			FlyPosition excel_pos = flyPositionList.get(excel_bin);

			int data_bin = excel_Ms / dataStepMs;
			int data_bin_remainder = excel_Ms % dataStepMs;
			FlyPosition data_pos = flyPositions.flyPositionList.get(data_bin);

			double delta = 0.;
			if (data_bin_remainder != 0 && (data_bin + 1 < n_data_intervals)) {
				delta = flyPositions.flyPositionList.get(data_bin + 1).distance * data_bin_remainder / dataStepMs;
			}
			excel_pos.distance = data_pos.sumDistance - sumDistance_previous + delta;
			sumDistance_previous = data_pos.sumDistance;
		}
	}

	public void excelComputeIsAlive(FlyPositions flyPositions, int stepMs, int buildExcelStepMs) {
		flyPositions.computeIsAlive();
		int it_start = 0;
		int it_end = flyPositions.flyPositionList.size() * stepMs;
		int it_out = 0;
		for (int it = it_start; it < it_end && it_out < flyPositionList.size(); it += buildExcelStepMs, it_out++) {
			int index = it / stepMs;
			FlyPosition pos = flyPositionList.get(it_out);
			pos.bAlive = flyPositions.flyPositionList.get(index).bAlive;
		}
	}

	public void excelComputeSleep(FlyPositions flyPositions, int stepMs, int buildExcelStepMs) {
		flyPositions.computeSleep();
		int it_start = 0;
		int it_end = flyPositions.flyPositionList.size() * stepMs;
		int it_out = 0;
		for (int it = it_start; it < it_end && it_out < flyPositionList.size(); it += buildExcelStepMs, it_out++) {
			int index = it / stepMs;
			FlyPosition pos = flyPositionList.get(it_out);
			pos.bSleep = flyPositions.flyPositionList.get(index).bSleep;
		}
	}

	public void excelComputeNewPointsOrigin(Point2D newOrigin, FlyPositions flyPositions, int stepMs,
			int buildExcelStepMs) {
		newOrigin.setLocation(newOrigin.getX() * pixelsize, newOrigin.getY() * pixelsize);
		double deltaX = newOrigin.getX() - origin.getX();
		double deltaY = newOrigin.getY() - origin.getY();
		if (deltaX == 0 && deltaY == 0)
			return;
		int it_start = 0;
		int it_end = flyPositions.flyPositionList.size() * stepMs;
		int it_out = 0;
		for (int it = it_start; it < it_end && it_out < flyPositionList.size(); it += buildExcelStepMs, it_out++) {
			int index = it / stepMs;
			FlyPosition pos_from = flyPositions.flyPositionList.get(index);
			FlyPosition pos_to = flyPositionList.get(it_out);
			pos_to.copy(pos_from);
			pos_to.rectPosition.setRect(pos_to.rectPosition.getX() - deltaX, pos_to.rectPosition.getY() - deltaY,
					pos_to.rectPosition.getWidth(), pos_to.rectPosition.getHeight());
		}
	}

	public void excelComputeEllipse(FlyPositions flyPositions, int dataStepMs, int excelStepMs) {
		if (flyPositions.flyPositionList.size() <= 0)
			return;

		flyPositions.computeEllipseAxes();
		int excel_startMs = 0;
		int n_excel_intervals = flyPositionList.size();
		int excel_endMs = (n_excel_intervals - 1) * excelStepMs;

		for (int excel_Ms = excel_startMs; excel_Ms < excel_endMs; excel_Ms += excelStepMs) {
			int excel_bin = excel_Ms / excelStepMs;
			FlyPosition excel_pos = flyPositionList.get(excel_bin);

			int data_bin = excel_Ms / dataStepMs;
			FlyPosition data_pos = flyPositions.flyPositionList.get(data_bin);

			excel_pos.axis1 = data_pos.axis1;
			excel_pos.axis2 = data_pos.axis2;
		}
	}

	// ------------------------------------------------------------

	public List<Double> getIsAliveAsDoubleArray() {
		ArrayList<Double> dataArray = new ArrayList<Double>();
		dataArray.ensureCapacity(flyPositionList.size());
		for (FlyPosition pos : flyPositionList)
			dataArray.add(pos.bAlive ? 1.0 : 0.0);
		return dataArray;
	}

	public List<Integer> getIsAliveAsIntegerArray() {
		ArrayList<Integer> dataArray = new ArrayList<Integer>();
		dataArray.ensureCapacity(flyPositionList.size());
		for (FlyPosition pos : flyPositionList) {
			dataArray.add(pos.bAlive ? 1 : 0);
		}
		return dataArray;
	}

	public int getLastIntervalAlive() {
		if (lastIntervalAlive >= 0)
			return lastIntervalAlive;
		return computeLastIntervalAlive();
	}

	private int getDeltaT() {
		return flyPositionList.get(1).flyIndexT - flyPositionList.get(0).flyIndexT;
	}

	public Double getDistanceBetween2Points(int firstTimeIndex, int secondTimeIndex) {
		if (flyPositionList.size() < 2)
			return Double.NaN;
		int firstIndex = firstTimeIndex / getDeltaT();
		int secondIndex = secondTimeIndex / getDeltaT();
		if (firstIndex < 0 || secondIndex < 0 || firstIndex >= flyPositionList.size()
				|| secondIndex >= flyPositionList.size())
			return Double.NaN;
		FlyPosition pos1 = flyPositionList.get(firstIndex);
		FlyPosition pos2 = flyPositionList.get(secondIndex);
		if (pos1.rectPosition.getX() < 0 || pos2.rectPosition.getX() < 0)
			return Double.NaN;

		Point2D point2 = pos2.getCenterRectangle();
		Double distance = point2.distance(pos1.getCenterRectangle());
		return distance;
	}

	public int isAliveAtTimeIndex(int timeIndex) {
		if (flyPositionList.size() < 2)
			return 0;
		getLastIntervalAlive();
		int index = timeIndex / getDeltaT();
		FlyPosition pos = flyPositionList.get(index);
		return (pos.bAlive ? 1 : 0);
	}

	private List<Integer> getDistanceAsMoveOrNot() {
		computeDistanceBetweenConsecutivePoints();
		ArrayList<Integer> dataArray = new ArrayList<Integer>();
		dataArray.ensureCapacity(flyPositionList.size());
		for (int i = 0; i < flyPositionList.size(); i++)
			dataArray.add(flyPositionList.get(i).distance < moveThreshold ? 1 : 0);
		return dataArray;
	}

	public void computeSleep() {
		if (flyPositionList.size() < 1)
			return;
		List<Integer> datai = getDistanceAsMoveOrNot();
		int timeBinSize = getDeltaT();
		int j = 0;
		for (FlyPosition pos : flyPositionList) {
			int isleep = 1;
			int k = 0;
			for (int i = 0; i < sleepThreshold; i += timeBinSize) {
				if ((k + j) >= datai.size())
					break;
				isleep = datai.get(k + j) * isleep;
				if (isleep == 0)
					break;
				k++;
			}
			pos.bSleep = (isleep == 1);
			j++;
		}
	}

	public List<Double> getSleepAsDoubleArray() {
		ArrayList<Double> dataArray = new ArrayList<Double>();
		dataArray.ensureCapacity(flyPositionList.size());
		for (FlyPosition pos : flyPositionList)
			dataArray.add(pos.bSleep ? 1.0 : 0.0);
		return dataArray;
	}

	public int isAsleepAtTimeIndex(int timeIndex) {
		if (flyPositionList.size() < 2)
			return -1;
		int index = timeIndex / getDeltaT();
		if (index >= flyPositionList.size())
			return -1;
		return (flyPositionList.get(index).bSleep ? 1 : 0);
	}

	public void computeNewPointsOrigin(Point2D newOrigin) {
		newOrigin.setLocation(newOrigin.getX() * pixelsize, newOrigin.getY() * pixelsize);
		double deltaX = newOrigin.getX() - origin.getX();
		double deltaY = newOrigin.getY() - origin.getY();
		if (deltaX == 0 && deltaY == 0)
			return;
		for (FlyPosition pos : flyPositionList) {
			pos.rectPosition.setRect(pos.rectPosition.getX() - deltaX, pos.rectPosition.getY() - deltaY,
					pos.rectPosition.getWidth(), pos.rectPosition.getHeight());
		}
	}

	public void computeEllipseAxes() {
		if (flyPositionList.size() < 1)
			return;

		for (FlyPosition pos : flyPositionList) {
			if (pos.flyRoi != null) {
				double[] ellipsoidValues = null;
				try {
					ellipsoidValues = Measures.computeOrientation(pos.flyRoi, null);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				pos.axis1 = ellipsoidValues[0];
				pos.axis2 = ellipsoidValues[1];
			} else if (pos.rectPosition != null) {
				pos.axis1 = pos.rectPosition.getHeight();
				pos.axis2 = pos.rectPosition.getWidth();
				if (pos.axis2 > pos.axis1) {
					double x = pos.axis1;
					pos.axis1 = pos.axis2;
					pos.axis2 = x;
				}
			}
		}
	}

	public void setPixelSize(double newpixelSize) {
		pixelsize = newpixelSize;
	}

	public void convertPixelsToPhysicalValues() {
		for (FlyPosition pos : flyPositionList) {
			pos.rectPosition.setRect(pos.rectPosition.getX() * pixelsize, pos.rectPosition.getY() * pixelsize,
					pos.rectPosition.getWidth() * pixelsize, pos.rectPosition.getHeight() * pixelsize);

			pos.axis1 = pos.axis1 * pixelsize;
			pos.axis2 = pos.axis2 * pixelsize;
		}

		origin.setLocation(origin.getX() * pixelsize, origin.getY() * pixelsize);
	}

	public void clearValues(int fromIndex) {
		int toIndex = flyPositionList.size();
		if (fromIndex > 0 && fromIndex < toIndex)
			flyPositionList.subList(fromIndex, toIndex).clear();
	}

	// --------------------------------------------------------

	public boolean cvsExport_XYwh_ToRow(StringBuffer sbf, String sep) {
		int npoints = 0;
		if (flyPositionList != null && flyPositionList.size() > 0)
			npoints = flyPositionList.size();

		sbf.append(Integer.toString(npoints) + sep);
		if (npoints > 0) {
			for (int i = 0; i < npoints; i++) {
				flyPositionList.get(i).cvsExportXYWHData(sbf, sep);
			}
		}
		return true;
	}

	public boolean cvsExport_XY_ToRow(StringBuffer sbf, String sep) {
		int npoints = 0;
		if (flyPositionList != null && flyPositionList.size() > 0)
			npoints = flyPositionList.size();

		sbf.append(Integer.toString(npoints) + sep);
		if (npoints > 0) {
			for (int i = 0; i < npoints; i++) {
				flyPositionList.get(i).cvsExportXYData(sbf, sep);
			}
		}
		return true;
	}

	public boolean csvImportXYWHDataFromRow(String[] data, int startAt) {
		if (data.length < startAt)
			return false;

		int npoints = Integer.valueOf(data[startAt]);
		if (npoints > 0) {
			flyPositionList = new ArrayList<FlyPosition>(npoints);
			int offset = startAt + 1;
			for (int i = 0; i < npoints; i++) {
				FlyPosition flyPosition = new FlyPosition();
				flyPosition.csvImportXYWHData(data, offset);
				flyPositionList.add(flyPosition);
				offset += 5;
			}
		}
		return true;
	}

	public boolean csvImportXYDataFromRow(String[] data, int startAt) {
		if (data.length < startAt)
			return false;

		int npoints = Integer.valueOf(data[startAt]);
		if (npoints > 0) {
			flyPositionList = new ArrayList<FlyPosition>(npoints);
			int offset = startAt + 1;
			for (int i = 0; i < npoints; i++) {
				FlyPosition flyPosition = new FlyPosition();
				flyPosition.csvImportXYData(data, offset);
				flyPositionList.add(flyPosition);
				offset += 3;
			}
		}
		return true;
	}

}
