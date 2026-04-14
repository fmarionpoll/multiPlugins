package plugins.fmp.multitools.experiment.cage;

import java.awt.Color;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import icy.roi.BooleanMask2D;
import icy.roi.ROI2D;
import icy.type.geom.Polygon2D;
import plugins.fmp.multitools.experiment.cages.EnumCageMeasures;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.ids.CapillaryID;
import plugins.fmp.multitools.experiment.ids.SpotID;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotString;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.tools.Comparators;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.ROI2D.ROIType;
import plugins.fmp.multitools.tools.toExcel.enums.EnumXLSColumnHeader;
import plugins.kernel.roi.roi2d.ROI2DEllipse;
import plugins.kernel.roi.roi2d.ROI2DPolygon;
import plugins.kernel.roi.roi2d.ROI2DRectangle;
import plugins.kernel.roi.roi2d.ROI2DShape;

public class Cage implements Comparable<Cage>, AutoCloseable {
	private static final Color FLY_POSITION_ROI_COLOR = Color.YELLOW;

	/** Fly rectangle ROI names: {@code detR<3-digit cage>_<t>_<idx>}. */
	public static final Pattern DETR_FLY_ROI_NAME_PATTERN = Pattern.compile("^detR(\\d{3})_(\\d+)_(\\d+)$");

	private ROI2D cageROI2D = null;
	public BooleanMask2D cageMask2D = null;
	public CageProperties prop = new CageProperties();
	public CageMeasures measures = new CageMeasures();

	public FlyPositions flyPositions = new FlyPositions();

	// ID-based references (new approach)
	private List<SpotID> spotIDs = new ArrayList<>();
	private List<CapillaryID> capillaryIDs = new ArrayList<>();

	private final AtomicBoolean closed = new AtomicBoolean(false);

	public boolean valid = false;
	public boolean bDetect = true;
	public boolean initialflyRemoved = false;

	public Cage(ROI2DShape roi) {
		this.cageROI2D = roi;
	}

	public Cage() {
	}

	@Override
	public int compareTo(Cage o) {
		if (o == null)
			return 1;
		// Handle null ROIs by comparing cage IDs as fallback
		if (this.cageROI2D == null && o.cageROI2D == null) {
			return Integer.compare(this.prop.getCageID(), o.prop.getCageID());
		}
		if (this.cageROI2D == null)
			return -1;
		if (o.cageROI2D == null)
			return 1;
		String thisName = this.cageROI2D.getName();
		String otherName = o.cageROI2D.getName();
		if (thisName == null && otherName == null)
			return 0;
		if (thisName == null)
			return -1;
		if (otherName == null)
			return 1;
		return thisName.compareTo(otherName);
	}

	// ------------------------------------
	// ID-based access methods

	public List<SpotID> getSpotIDs() {
		return spotIDs;
	}

	public void setSpotIDs(List<SpotID> spotIDs) {
		this.spotIDs = spotIDs != null ? new ArrayList<>(spotIDs) : new ArrayList<>();
	}

	public List<CapillaryID> getCapillaryIDs() {
		return capillaryIDs;
	}

	public void setCapillaryIDs(List<CapillaryID> capillaryIDs) {
		this.capillaryIDs = capillaryIDs != null ? new ArrayList<>(capillaryIDs) : new ArrayList<>();
	}

	/**
	 * Resolves SpotIDs to actual Spot objects from the global SpotsArray.
	 * 
	 * @param allSpots the global SpotsArray containing all spots
	 * @return list of Spot objects for this cage
	 */
	public List<Spot> getSpotList(Spots allSpots) {
		List<Spot> result = new ArrayList<>();
		if (allSpots == null) {
			return result;
		}
		if (!spotIDs.isEmpty()) {
			for (SpotID spotID : spotIDs) {
				for (Spot spot : allSpots.getSpotList()) {
					if (spot.getSpotUniqueID() != null && spot.getSpotUniqueID().equals(spotID)) {
						result.add(spot);
						break;
					}
				}
			}
			return result;
		}
		// Fallback for legacy: cage has no spotIDs, filter by cageID
		int cageID = prop.getCageID();
		for (Spot spot : allSpots.getSpotList()) {
			if (spot.getProperties().getCageID() == cageID) {
				result.add(spot);
			}
		}
		return result;
	}

	/**
	 * Resolves CapillaryIDs to actual Capillary objects from the global
	 * Capillaries.
	 * 
	 * @param allCapillaries the global Capillaries containing all capillaries
	 * @return list of Capillary objects for this cage
	 */
	public List<Capillary> getCapillaries(Capillaries allCapillaries) {
		List<Capillary> result = new ArrayList<>();
		if (allCapillaries == null) {
			return result;
		}
		for (CapillaryID capID : capillaryIDs) {
			// Find capillary by kymographIndex
			for (Capillary cap : allCapillaries.getList()) {
				if (cap.getKymographIndex() == capID.getKymographIndex()) {
					result.add(cap);
					break;
				}
			}
		}
		return result;
	}

//	// Backward-compatible getters (deprecated, use ID-based methods)
//	@Deprecated
//	public SpotsArray getSpotsArray() {
//		return null; // Deprecated - use getSpots(SpotsArray allSpots) instead
//	}

	public CageProperties getProperties() {
		return prop;
	}

	public ROI2D getRoi() {
		return cageROI2D;
	}

	public ROI2D getCageRoi2D() {
		return cageROI2D;
	}

	public void setCageRoi(ROI2D roi) {
		cageROI2D = roi;
		setCageID(getCageIDFromRoiName());
	}

	public String getCageNumberFromRoiName() {
		if (cageROI2D == null || cageROI2D.getName() == null) {
			// Fallback to cage ID or existing strCageNumber if ROI is not available
			String fallback = prop.getStrCageNumber();
			if (fallback == null || fallback.isEmpty() || fallback.equals("0")) {
				fallback = formatCageNumberToString(prop.getCageID());
			}
			prop.setStrCageNumber(fallback);
			return prop.getStrCageNumber();
		}

		String roiName = cageROI2D.getName();
		if (roiName.length() >= 3) {
			prop.setStrCageNumber(roiName.substring(roiName.length() - 3));
		} else {
			// ROI name too short, use cage ID as fallback
			String fallback = String.format("%03d", prop.getCageID());
			prop.setStrCageNumber(fallback);
		}
		return prop.getStrCageNumber();
	}

	public String formatCageNumberToString(int number) {
		return String.format("%03d", number);
	}

	public int getCageIDFromRoiName() {
		int cageID = -1;
		if (cageROI2D != null && cageROI2D.getName() != null) {
			String roiName = cageROI2D.getName();
			if (roiName.length() >= 3)
				cageID = Integer.parseInt(roiName.substring(roiName.length() - 3));
		}
		return cageID;
	}

	public int getCageID() {
		return prop.getCageID();
	}

	public void setCageID(int ID) {
		prop.setCageID(ID);
	}

	public BooleanMask2D getCageMask2D() {
		return cageMask2D;
	}

	public void setMask2D(BooleanMask2D mask) {
		cageMask2D = mask;
	}

	public int getCageNFlies() {
		return prop.getCageNFlies();
	}

	public void setCageNFlies(int nFlies) {
		prop.setCageNFlies(nFlies);
	}

	// ------------------------------------------

	public FlyPositions getFlyPositions() {
		return flyPositions;
	}

	public void clearMeasures() {
		flyPositions.clear();
		measures.clear();
	}

	public Point2D getCenterTopCage() {
		Rectangle2D rect = cageROI2D.getBounds2D();
		Point2D pt = new Point2D.Double(rect.getX() + rect.getWidth() / 2, rect.getY());
		return pt;
	}

	public Point2D getCenterTipCapillaries(Capillaries capList) {
		List<Point2D> listpts = new ArrayList<Point2D>();
		for (Capillary cap : capList.getList()) {
			Point2D pt = cap.getCapillaryTipWithinROI2D(cageROI2D);
			if (pt != null)
				listpts.add(pt);
		}
		double x = 0;
		double y = 0;
		int n = listpts.size();
		for (Point2D pt : listpts) {
			x += pt.getX();
			y += pt.getY();
		}
		Point2D pt = new Point2D.Double(x / n, y / n);
		return pt;
	}

	public void addCapillaryIfUnique(Capillary cap) {
		if (cap == null) {
			return;
		}
		CapillaryID capID = new CapillaryID(cap.getKymographIndex());
		if (!capillaryIDs.contains(capID)) {
			capillaryIDs.add(capID);
		}
	}

	public void addCapillaryIDIfUnique(CapillaryID capID) {
		if (capID != null && !capillaryIDs.contains(capID)) {
			capillaryIDs.add(capID);
		}
	}

	public void addCapillaryIfUniqueBulkFilteredOnCageID(List<Capillary> capillaryList) {
		for (Capillary cap : capillaryList) {
			if (cap.getCageID() == prop.getCageID())
				addCapillaryIfUnique(cap);
		}
	}

	public void clearCapillaryList() {
		capillaryIDs.clear();
	}

	@Deprecated
	public List<Capillary> getCapillaryList() {
		return new ArrayList<>(); // Deprecated - use getCapillaries(Capillaries allCapillaries) instead
	}

	// -------------------------------------------------

	public void copyCageInfo(Cage cageFrom) {
		copyCage(cageFrom, false);
	}

	public void copyCage(Cage cageFrom, boolean bMeasures) {
		prop.copy(cageFrom.prop);
		cageROI2D = (ROI2D) cageFrom.cageROI2D.getCopy();
		valid = false;
		if (bMeasures)
			flyPositions.copyXYTaSeries(cageFrom.flyPositions);
		// Copy ID lists
		spotIDs = new ArrayList<>(cageFrom.spotIDs);
		capillaryIDs = new ArrayList<>(cageFrom.capillaryIDs);
	}

	public void pasteCageInfo(Cage cageTo) {
		prop.paste(cageTo.prop);
		cageTo.cageROI2D = (ROI2D) cageROI2D.getCopy();
		// Paste ID lists
		cageTo.spotIDs = new ArrayList<>(spotIDs);
		cageTo.capillaryIDs = new ArrayList<>(capillaryIDs);
	}

	public void pasteCage(Cage cageTo, boolean bMeasures) {
		prop.paste(cageTo.prop);
		cageTo.cageROI2D = (ROI2D) cageROI2D.getCopy();
		// Paste ID lists
		cageTo.spotIDs = new ArrayList<>(spotIDs);
		cageTo.capillaryIDs = new ArrayList<>(capillaryIDs);
		if (bMeasures)
			flyPositions.copyXYTaSeries(cageTo.flyPositions);
	}

	public String getField(EnumXLSColumnHeader fieldEnumCode) {
		String stringValue = null;
		switch (fieldEnumCode) {
		case CAGE_SEX:
			stringValue = prop.getFlySex();
			break;
		case CAGE_AGE:
			stringValue = String.valueOf(prop.getFlyAge());
			break;
		case CAGE_STRAIN:
			stringValue = prop.getFlyStrain();
			break;
		case CAGE_NFLIES:
			stringValue = Integer.toString(prop.getCageNFlies());
			break;
		default:
			break;
		}
		return stringValue;
	}

	public void setField(EnumXLSColumnHeader fieldEnumCode, String stringValue) {
		switch (fieldEnumCode) {
		case CAGE_SEX:
			prop.setFlySex(stringValue);
			break;
		case CAGE_AGE:
			int ageValue = Integer.valueOf(stringValue);
			prop.setFlyAge(ageValue);
			break;
		case CAGE_STRAIN:
			prop.setFlyStrain(stringValue);
			break;
		default:
			break;
		}
	}

	/**
	 * All fly rectangles stored for camera frame index {@code t} ({@link FlyPosition#flyIndexT}).
	 */
	public List<ROI2DRectangle> collectRoiRectanglesAtFrameIndexT(int t) {
		List<ROI2DRectangle> out = new ArrayList<>();
		if (cageROI2D == null)
			return out;
		int idx = 0;
		for (FlyPosition fp : flyPositions.flyPositionList) {
			if (fp.flyIndexT != t)
				continue;
			if (fp.rectPosition == null || Double.isNaN(fp.rectPosition.getX()))
				continue;
			ROI2DRectangle flyRoiR = new ROI2DRectangle(fp.rectPosition);
			flyRoiR.setName("detR" + getCageNumberFromRoiName() + "_" + t + "_" + idx);
			flyRoiR.setT(t);
			flyRoiR.setColor(FLY_POSITION_ROI_COLOR);
			out.add(flyRoiR);
			idx++;
		}
		return out;
	}

	/** First rectangle at frame {@code t}, or null. Prefer {@link #collectRoiRectanglesAtFrameIndexT(int)} if multiple flies per frame. */
	public ROI2DRectangle getRoiRectangleFromPositionAtT(int t) {
		List<ROI2DRectangle> list = collectRoiRectanglesAtFrameIndexT(t);
		return list.isEmpty() ? null : list.get(0);
	}

	/**
	 * Whether {@code fp} is an unused / missed slot (same notion as {@code Edit#findFirst}).
	 */
	public static boolean isEmptyFlyPositionSlot(FlyPosition fp) {
		if (fp == null || fp.rectPosition == null)
			return true;
		if (Double.isNaN(fp.rectPosition.getX()) || Double.isNaN(fp.rectPosition.getY()))
			return true;
		return fp.rectPosition.getX() == -1 && fp.rectPosition.getY() == -1;
	}

	/**
	 * True if {@code (x,y)} lies inside this cage ROI (polygon, rectangle, or bounds fallback).
	 */
	public boolean containsImagePoint(double x, double y) {
		if (cageROI2D == null)
			return false;
		if (cageROI2D instanceof ROI2DPolygon) {
			Polygon2D poly = ((ROI2DPolygon) cageROI2D).getPolygon2D();
			if (poly == null || poly.npoints < 3)
				return false;
			Polygon awt = new Polygon();
			for (int i = 0; i < poly.npoints; i++)
				awt.addPoint((int) Math.round(poly.xpoints[i]), (int) Math.round(poly.ypoints[i]));
			return awt.contains(x, y);
		}
		if (cageROI2D instanceof ROI2DRectangle)
			return ((ROI2DRectangle) cageROI2D).getRectangle().contains(x, y);
		Rectangle b = cageROI2D.getBounds();
		return b.contains(x, y);
	}

	/**
	 * Adds or fills a fly rectangle at frame {@code t}, centered on {@code (cx, cy)}. Uses 10×5 px if
	 * no valid fly exists at {@code t} in this cage; otherwise copies width/height from the first valid
	 * fly at {@code t} (same order as {@link #collectRoiRectanglesAtFrameIndexT(int)}).
	 */
	public void addManualFlyAtImageCoordinates(int t, double cx, double cy, long[] camImagesMs) {
		double w = 10;
		double h = 5;
		FlyPosition firstValidAtT = null;
		for (FlyPosition fp : flyPositions.flyPositionList) {
			if (fp.flyIndexT != t)
				continue;
			if (!isEmptyFlyPositionSlot(fp)) {
				firstValidAtT = fp;
				break;
			}
		}
		if (firstValidAtT != null) {
			w = firstValidAtT.rectPosition.getWidth();
			h = firstValidAtT.rectPosition.getHeight();
			if (w <= 0 || h <= 0) {
				w = 10;
				h = 5;
			}
		}
		Rectangle2D rect = new Rectangle2D.Double(cx - w / 2, cy - h / 2, w, h);
		FlyPosition target = null;
		for (FlyPosition fp : flyPositions.flyPositionList) {
			if (fp.flyIndexT != t)
				continue;
			if (isEmptyFlyPositionSlot(fp)) {
				target = fp;
				break;
			}
		}
		if (target == null) {
			target = new FlyPosition(t);
			flyPositions.flyPositionList.add(target);
		}
		target.rectPosition.setRect(rect);
		if (camImagesMs != null && t >= 0 && t < camImagesMs.length)
			target.tMs = camImagesMs[t];
		flyPositions.recomputeNfliesFromEntries();
		Collections.sort(flyPositions.flyPositionList, new Comparators.XYTaValue_Tindex());
	}

	/**
	 * Removes the {@code collectIdx}-th <em>valid</em> fly at {@code flyIndexT} (same ordering as
	 * {@link #collectRoiRectanglesAtFrameIndexT(int)}).
	 *
	 * @return true if a row was removed
	 */
	public boolean removeFlyAtFrameCollectIndex(int flyIndexT, int collectIdx) {
		int i = 0;
		for (Iterator<FlyPosition> it = flyPositions.flyPositionList.iterator(); it.hasNext();) {
			FlyPosition fp = it.next();
			if (fp.flyIndexT != flyIndexT)
				continue;
			if (isEmptyFlyPositionSlot(fp))
				continue;
			if (i == collectIdx) {
				it.remove();
				flyPositions.recomputeNfliesFromEntries();
				return true;
			}
			i++;
		}
		return false;
	}

	/**
	 * Moves the {@code collectIdx}-th <em>valid</em> fly at frame {@code flyIndexT} to be centered on
	 * {@code (cx, cy)} and forces its size to {@code (w, h)}.
	 *
	 * @return true if a fly was found and moved
	 */
	public boolean moveFlyAtFrameCollectIndexTo(int flyIndexT, int collectIdx, double cx, double cy, double w, double h,
			long[] camImagesMs) {
		int i = 0;
		for (FlyPosition fp : flyPositions.flyPositionList) {
			if (fp.flyIndexT != flyIndexT)
				continue;
			if (isEmptyFlyPositionSlot(fp))
				continue;
			if (i == collectIdx) {
				double ww = (w > 0) ? w : 10;
				double hh = (h > 0) ? h : 5;
				Rectangle2D rect = new Rectangle2D.Double(cx - ww / 2, cy - hh / 2, ww, hh);
				fp.rectPosition.setRect(rect);
				if (camImagesMs != null && flyIndexT >= 0 && flyIndexT < camImagesMs.length)
					fp.tMs = camImagesMs[flyIndexT];
				return true;
			}
			i++;
		}
		return false;
	}

	/**
	 * Adds or fills a fly rectangle at frame {@code t}, centered on {@code (cx, cy)} with the provided
	 * size {@code (w, h)}. Uses the first empty slot at {@code t} if available; otherwise appends a
	 * new entry.
	 */
	public void addManualFlyAtImageCoordinatesWithSize(int t, double cx, double cy, double w, double h, long[] camImagesMs) {
		double ww = (w > 0) ? w : 10;
		double hh = (h > 0) ? h : 5;
		Rectangle2D rect = new Rectangle2D.Double(cx - ww / 2, cy - hh / 2, ww, hh);
		FlyPosition target = null;
		for (FlyPosition fp : flyPositions.flyPositionList) {
			if (fp.flyIndexT != t)
				continue;
			if (isEmptyFlyPositionSlot(fp)) {
				target = fp;
				break;
			}
		}
		if (target == null) {
			target = new FlyPosition(t);
			flyPositions.flyPositionList.add(target);
		}
		target.rectPosition.setRect(rect);
		if (camImagesMs != null && t >= 0 && t < camImagesMs.length)
			target.tMs = camImagesMs[t];
		flyPositions.recomputeNfliesFromEntries();
		Collections.sort(flyPositions.flyPositionList, new Comparators.XYTaValue_Tindex());
	}

	public void transferRoisToPositions(List<ROI2D> detectedROIsList) {
		if (cageROI2D == null) {
			return;
		}
		String filter = "detR" + getCageNumberFromRoiName();
		for (ROI2D roi : detectedROIsList) {
			String name = roi.getName();
			if (name == null || !name.contains(filter))
				continue;
			if (!(roi instanceof ROI2DRectangle))
				continue;
			Rectangle2D rect = ((ROI2DRectangle) roi).getRectangle();
			Matcher m = DETR_FLY_ROI_NAME_PATTERN.matcher(name);
			if (m.matches()) {
				if (!m.group(1).equals(getCageNumberFromRoiName()))
					continue;
				int flyT = Integer.parseInt(m.group(2));
				int idx = Integer.parseInt(m.group(3));
				applyDetRectangleToFlyAtFrameCollectIndex(flyT, idx, rect);
			} else {
				int t = (int) roi.getT();
				if (t >= 0 && t < flyPositions.flyPositionList.size())
					flyPositions.flyPositionList.get(t).rectPosition.setRect(rect);
			}
		}
	}

	private void applyDetRectangleToFlyAtFrameCollectIndex(int flyIndexT, int collectIdx, Rectangle2D rect) {
		int i = 0;
		for (FlyPosition fp : flyPositions.flyPositionList) {
			if (fp.flyIndexT != flyIndexT)
				continue;
			if (isEmptyFlyPositionSlot(fp))
				continue;
			if (i == collectIdx) {
				fp.rectPosition.setRect(rect);
				return;
			}
			i++;
		}
	}

	public void computeCageBooleanMask2D() throws InterruptedException {
		cageMask2D = cageROI2D.getBooleanMask2D(0, 0, 1, true);
	}

	/**
	 * Adds a SpotID to this cage if it's not already present.
	 */
	public void addSpotIDIfUnique(SpotID spotID) {
		if (spotID != null && !spotIDs.contains(spotID)) {
			spotIDs.add(spotID);
		}
	}

	// -----------------------------------------

	public String csvExportCageDescription(String sep) {
		StringBuffer sbf = new StringBuffer();
		List<String> row = new ArrayList<String>();
		row.add(prop.getStrCageNumber());
		// Handle null ROI name safely
		String roiName = (cageROI2D != null && cageROI2D.getName() != null) ? cageROI2D.getName()
				: "cage" + String.format("%03d", prop.getCageID());
		row.add(roiName);
		row.add(Integer.toString(prop.getCageNFlies()));
		row.add(Integer.toString(prop.getFlyAge()));
		row.add(prop.getComment());
		row.add(prop.getFlyStrain());
		row.add(prop.getFlySex());

		if (cageROI2D != null) {
			Polygon2D polygon = ((ROI2DPolygon) cageROI2D).getPolygon2D();
			row.add(Integer.toString(polygon.npoints));
			for (int i = 0; i < polygon.npoints; i++) {
				row.add(Integer.toString((int) polygon.xpoints[i]));
				row.add(Integer.toString((int) polygon.ypoints[i]));
			}
		} else
			row.add("0");
		sbf.append(String.join(sep, row));
		sbf.append("\n");
		return sbf.toString();
	}

	// --------------------------------------------------------

	public int addEllipseSpot(Point2D.Double center, int radius, Spots allSpots) {
		int position = spotIDs.size();
		// Add spot to global SpotsArray first to get unique ID
		if (allSpots != null) {
			int uniqueSpotID = allSpots.getNextUniqueSpotID();
			SpotID spotUniqueID = new SpotID(uniqueSpotID);
			Spot spot = createEllipseSpot(position, center, radius);
			spot.setSpotUniqueID(spotUniqueID);
			allSpots.addSpot(spot);
			spotIDs.add(spotUniqueID);
		}
		return spotIDs.size();
	}

	private Spot createEllipseSpot(int cagePosition, Point2D.Double center, int radius) {
		Ellipse2D ellipse = new Ellipse2D.Double(center.x, center.y, 2 * radius, 2 * radius);
		ROI2DEllipse roiEllipse = new ROI2DEllipse(ellipse);
		roiEllipse.setName(SpotString.createSpotString(prop.getCageID(), cagePosition));
		Spot spot = new Spot(roiEllipse);
		spot.getProperties().setCageID(prop.getCageID());
		spot.getProperties().setCagePosition(cagePosition);
		spot.getProperties().setSpotRadius(radius);
		spot.getProperties().setSpotXCoord((int) center.getX());
		spot.getProperties().setSpotYCoord((int) center.getY());
		try {
			spot.getProperties().setSpotNPixels((int) roiEllipse.getNumberOfPoints());
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return spot;
	}

	public Spot getSpotFromRoiName(String name, Spots allSpots) {
		if (allSpots == null) {
			return null;
		}
		int cagePosition = SpotString.getSpotCagePositionFromSpotName(name);
		List<Spot> spots = getSpotList(allSpots);
		for (Spot spot : spots) {
			if (spot.getProperties().getCagePosition() == cagePosition)
				return spot;
		}
		return null;
	}

	public void mapSpotsToCageColumnRow(Spots allSpots) {
		if (allSpots == null || cageROI2D == null) {
			return;
		}
		Rectangle rect = cageROI2D.getBounds();
		if (rect == null || rect.width <= 0 || rect.height <= 0) {
			return;
		}
		final int nCols = 8;
		final int nRows = 4;
		double deltaX = rect.getWidth() / nCols;
		double deltaY = rect.getHeight() / nRows;
		if (deltaX <= 0 || deltaY <= 0) {
			return;
		}
		List<Spot> spots = getSpotList(allSpots);
		for (Spot spot : spots) {
			ROI2D spotRoi = spot.getRoi();
			if (spotRoi == null) {
				continue;
			}
			Rectangle rectSpot = spotRoi.getBounds();
			if (rectSpot == null) {
				continue;
			}
			double x = rectSpot.getCenterX() - rect.getX();
			double y = rectSpot.getCenterY() - rect.getY();
			int col = (int) Math.floor(x / deltaX);
			int row = (int) Math.floor(y / deltaY);
			col = Math.max(0, Math.min(nCols - 1, col));
			row = Math.max(0, Math.min(nRows - 1, row));
			spot.getProperties().setCageColumn(col);
			spot.getProperties().setCageRow(row);
		}
	}

	public void cleanUpSpotNames(Spots allSpots) {
		if (allSpots == null) {
			return;
		}
		List<Spot> spots = getSpotList(allSpots);
		for (int i = 0; i < spots.size(); i++) {
			Spot spot = spots.get(i);
			spot.setName(prop.getCageID(), i);
			spot.getProperties().setCageID(prop.getCageID());
			spot.getProperties().setCagePosition(i);
		}
	}

	public void updateSpotsStimulus_i(Spots allSpots) {
		if (allSpots == null) {
			return;
		}
		ArrayList<String> stimulusArray = new ArrayList<String>(8);
		List<Spot> spots = getSpotList(allSpots);
		for (Spot spot : spots) {
			String test = spot.getProperties().getStimulus();
			stimulusArray.add(test);
			spot.getProperties().setStimulusI(test + "_" + findNumberOfIdenticalItems(test, stimulusArray));
		}
	}

	private int findNumberOfIdenticalItems(String test, ArrayList<String> array) {
		int items = 0;
		for (String element : array)
			if (element.equals(test))
				items++;
		return items;
	}

	public Spot combineSpotsWithSameStimConc(String stim, String conc, Spots allSpots) {
		if (allSpots == null) {
			return null;
		}
		Spot spotCombined = null;
		List<Spot> spots = getSpotList(allSpots);
		for (Spot spotSource : spots) {
			if (stim.equals(spotSource.getProperties().getStimulus().trim())
					&& conc.equals(spotSource.getProperties().getConcentration().trim())) {
				if (spotCombined == null) {
					spotCombined = new Spot(spotSource, true);
				} else {
					spotCombined.addMeasurements(spotSource);
				}
			}
		}
		return spotCombined;
	}

	public void normalizeSpotMeasures(Spots allSpots) {
		if (allSpots == null) {
			return;
		}
		List<Spot> spots = getSpotList(allSpots);
		for (Spot spot : spots) {
			spot.normalizeMeasures();
		}
	}

	public Spot createSpotPI(Spot spot1, Spot spot2) {
		if (spot1 == null || spot2 == null)
			return null;
		Spot spotPI = new Spot();
		spotPI.getProperties().setCageID(spot1.getProperties().getCageID());
		spotPI.getProperties().setName("PI");
		spotPI.getProperties().setStimulus("PI");
		spotPI.getProperties().setConcentration(
				spot1.getCombinedStimulusConcentration() + " / " + spot2.getCombinedStimulusConcentration());
		spotPI.computePI(spot1, spot2);
		return spotPI;
	}

	public Spot createSpotSUM(Spot spot1, Spot spot2) {
		if (spot1 == null || spot2 == null)
			return null;
		Spot spotSUM = new Spot();
		spotSUM.getProperties().setCageID(spot1.getProperties().getCageID());
		spotSUM.getProperties().setName("SUM");
		spotSUM.getProperties().setStimulus("SUM");
		spotSUM.getProperties().setConcentration(
				spot1.getCombinedStimulusConcentration() + " / " + spot2.getCombinedStimulusConcentration());
		spotSUM.computeSUM(spot1, spot2);
		return spotSUM;
	}

	// ----------------------------------------
	// CSV Import methods

	public void csvImport_CAGE_Header(String[] data) {
		if (data == null || data.length < 3) {
			System.err.println(
					"Cage:csvImport_CAGE_Header() Invalid data array length: " + (data == null ? "null" : data.length));
			return;
		}

		int i = 0;
		// Parse cageID (as String first, then convert to int)
		String cageIDStr = data[i];
		i++;
		try {
			int cageIDInt = Integer.valueOf(cageIDStr);
			prop.setCageID(cageIDInt);
			prop.setStrCageNumber(cageIDStr);
		} catch (NumberFormatException e) {
			System.err.println("Cage:csvImport_CAGE_Header() Invalid cageID: " + cageIDStr);
		}

		// Parse nFlies
		if (i < data.length) {
			try {
				int nFlies = Integer.valueOf(data[i]);
				prop.setCageNFlies(nFlies);
				i++;
			} catch (NumberFormatException e) {
				System.err.println("Cage:csvImport_CAGE_Header() Invalid nFlies: " + data[i]);
				i++;
			}
		}

		// Parse age
		if (i < data.length) {
			try {
				int age = Integer.valueOf(data[i]);
				prop.setFlyAge(age);
				i++;
			} catch (NumberFormatException e) {
				System.err.println("Cage:csvImport_CAGE_Header() Invalid age: " + data[i]);
				i++;
			}
		}

		// Parse comment
		if (i < data.length) {
			prop.setComment(data[i]);
			i++;
		}

		// Parse strain
		if (i < data.length) {
			prop.setFlyStrain(data[i]);
			i++;
		}

		// Parse sex (may be labeled as "sect" in legacy format)
		if (i < data.length) {
			prop.setFlySex(data[i]);
			i++;
		}

		// Optional colorR, colorG, colorB (v2 format). Only consume if all 3 look
		// numeric.
		if (i + 2 < data.length && isNumeric(data[i]) && isNumeric(data[i + 1]) && isNumeric(data[i + 2])) {
			try {
				int r = Integer.parseInt(data[i]);
				int g = Integer.parseInt(data[i + 1]);
				int b = Integer.parseInt(data[i + 2]);
				prop.setColor(new Color(r, g, b));
				i += 3;
			} catch (NumberFormatException e) {
				// Ignore malformed color triplet and leave current color unchanged
			}
		}

		if (i < data.length && isFoodSideToken(data[i])) {
			try {
				prop.setFoodSide(FoodSide.valueOf(data[i].trim().toUpperCase()));
			} catch (IllegalArgumentException e) {
				prop.setFoodSide(FoodSide.TOP);
			}
			i++;
		}

		// Parse ROI name (if present in CAGES section format)
		String cageROI_name = "";
		if (i < data.length) {
			cageROI_name = data[i];
			i++;
		}

		// Parse ROI type (v2.1 format) - check if next field is a string (not a number)
		String roiTypeStr = "";
		if (i < data.length && !isNumeric(data[i])) {
			roiTypeStr = data[i];
			i++;
		}

		// Parse npoints (if present in CAGES section format)
		int npoints = 0;
		if (i < data.length) {
			try {
				npoints = Integer.valueOf(data[i]);
				i++;
			} catch (NumberFormatException e) {
				// npoints not present or invalid - that's OK, ROI might be loaded from XML
			}
		}

		// Parse ROI data based on type
		if (npoints > 0 && i + (npoints * 2) <= data.length) {
			// If roiType is specified, use it; otherwise infer from npoints
			ROIType roiType = ROIType.fromString(roiTypeStr);

			if (roiType == ROIType.RECTANGLE && npoints == 4) {
				// Rectangle format: x, y, width, height
				try {
					int x = Integer.valueOf(data[i++]);
					int y = Integer.valueOf(data[i++]);
					int width = Integer.valueOf(data[i++]);
					int height = Integer.valueOf(data[i++]);

					cageROI2D = new ROI2DRectangle(x, y, width, height);
					if (cageROI_name != null && !cageROI_name.isEmpty()) {
						cageROI2D.setName(cageROI_name);
					}
					Color color = prop.getColor();
					if (color != null) {
						cageROI2D.setColor(color);
					}
				} catch (NumberFormatException e) {
					System.err.println("Cage:csvImport_CAGE_Header() Invalid rectangle parameters");
				}
			} else {
				// Polygon format: x,y coordinate pairs
				double[] x = new double[npoints];
				double[] y = new double[npoints];
				for (int j = 0; j < npoints; j++) {
					try {
						x[j] = Double.valueOf(data[i]);
						i++;
						y[j] = Double.valueOf(data[i]);
						i++;
					} catch (NumberFormatException e) {
						System.err.println("Cage:csvImport_CAGE_Header() Invalid coordinate at index " + j);
						break;
					}
				}
				Polygon2D polygon = new Polygon2D(x, y, npoints);
				cageROI2D = new ROI2DPolygon(polygon);
				if (cageROI_name != null && !cageROI_name.isEmpty()) {
					cageROI2D.setName(cageROI_name);
				}
				Color color = prop.getColor();
				if (color != null) {
					cageROI2D.setColor(color);
				}
			}
		}
	}

	/**
	 * Checks if a string represents a numeric value.
	 * 
	 * @param str the string to check
	 * @return true if the string is numeric
	 */
	private boolean isNumeric(String str) {
		if (str == null || str.trim().isEmpty()) {
			return false;
		}
		try {
			Integer.parseInt(str.trim());
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private static boolean isFoodSideToken(String str) {
		if (str == null || str.trim().isEmpty()) {
			return false;
		}
		String t = str.trim().toUpperCase();
		for (FoodSide v : FoodSide.values()) {
			if (v.name().equals(t)) {
				return true;
			}
		}
		return false;
	}

	public void csvImport_MEASURE_Data_v0(EnumCageMeasures measureType, String[] data, boolean complete) {
		switch (measureType) {
		case POSITION:
			if (complete) {
				flyPositions.csvImport_Rectangle_FromRow(data, 1);
			} else {
				flyPositions.csvImport_XY_FromRow(data, 1);
			}
			break;
		default:
			break;
		}
	}

	public void csvImport_MEASURE_Data_Parameters(String[] data) {
		flyPositions.cvsImport_Parameter_FromRow(data);
	}

	// ----------------------------------------

	@Override
	public void close() throws Exception {
		if (closed.compareAndSet(false, true)) {
			Logger.debug("Closing cage: "); // + data.getName());
			// Cleanup resources if needed
			flyPositions.clear();
		}
	}
}
