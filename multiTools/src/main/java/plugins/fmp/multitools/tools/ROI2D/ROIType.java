package plugins.fmp.multitools.tools.ROI2D;

import icy.roi.ROI2D;
import plugins.kernel.roi.roi2d.ROI2DEllipse;
import plugins.kernel.roi.roi2d.ROI2DLine;
import plugins.kernel.roi.roi2d.ROI2DPolygon;
import plugins.kernel.roi.roi2d.ROI2DPolyLine;
import plugins.kernel.roi.roi2d.ROI2DRectangle;

/**
 * Enumeration of ROI types for CSV persistence.
 * 
 * <p>This enum provides a standardized way to identify and persist different ROI types
 * across Spot, Capillary, and Cage entities. Each type defines its storage format:
 * 
 * <ul>
 *   <li><b>ELLIPSE</b>: centerX, centerY, radiusX, radiusY (or radiusX for circle)</li>
 *   <li><b>LINE</b>: x1, y1, x2, y2</li>
 *   <li><b>POLYLINE</b>: npoints, x1, y1, x2, y2, ...</li>
 *   <li><b>POLYGON</b>: npoints, x1, y1, x2, y2, ...</li>
 *   <li><b>RECTANGLE</b>: x, y, width, height</li>
 *   <li><b>UNKNOWN</b>: fallback for unrecognized types</li>
 * </ul>
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 * @since 2.3.3
 */
public enum ROIType {
	
	/** Ellipse or circle ROI defined by center and radii */
	ELLIPSE("ellipse"),
	
	/** Line ROI defined by two endpoints */
	LINE("line"),
	
	/** Polyline ROI defined by sequence of connected points */
	POLYLINE("polyline"),
	
	/** Polygon ROI defined by closed sequence of points */
	POLYGON("polygon"),
	
	/** Rectangle ROI defined by position and dimensions */
	RECTANGLE("rectangle"),
	
	/** Unknown or unsupported ROI type */
	UNKNOWN("unknown");
	
	private final String csvName;
	
	/**
	 * Creates a ROIType with the specified CSV name.
	 * 
	 * @param csvName the name used in CSV files
	 */
	private ROIType(String csvName) {
		this.csvName = csvName;
	}
	
	/**
	 * Gets the CSV representation of this ROI type.
	 * 
	 * @return the CSV name
	 */
	public String toCsvString() {
		return csvName;
	}
	
	/**
	 * Parses a CSV string to a ROIType.
	 * 
	 * @param csvString the CSV string to parse
	 * @return the corresponding ROIType, or UNKNOWN if not recognized
	 */
	public static ROIType fromString(String csvString) {
		if (csvString == null || csvString.trim().isEmpty()) {
			return UNKNOWN;
		}
		
		String normalized = csvString.trim().toLowerCase();
		for (ROIType type : values()) {
			if (type.csvName.equals(normalized)) {
				return type;
			}
		}
		return UNKNOWN;
	}
	
	/**
	 * Detects the ROI type from an ROI2D instance.
	 * 
	 * @param roi the ROI to analyze
	 * @return the detected ROI type, or UNKNOWN if roi is null
	 */
	public static ROIType fromROI2D(ROI2D roi) {
		if (roi == null) {
			return UNKNOWN;
		}
		
		if (roi instanceof ROI2DEllipse) {
			return ELLIPSE;
		} else if (roi instanceof ROI2DLine) {
			return LINE;
		} else if (roi instanceof ROI2DPolyLine) {
			return POLYLINE;
		} else if (roi instanceof ROI2DPolygon) {
			return POLYGON;
		} else if (roi instanceof ROI2DRectangle) {
			return RECTANGLE;
		}
		
		return UNKNOWN;
	}
	
	/**
	 * Checks if this type stores explicit point coordinates.
	 * 
	 * @return true if this type uses point-based storage (POLYLINE, POLYGON)
	 */
	public boolean isPointBased() {
		return this == POLYLINE || this == POLYGON;
	}
	
	/**
	 * Checks if this type stores geometric parameters.
	 * 
	 * @return true if this type uses parameter-based storage (ELLIPSE, LINE, RECTANGLE)
	 */
	public boolean isParametric() {
		return this == ELLIPSE || this == LINE || this == RECTANGLE;
	}
	
	@Override
	public String toString() {
		return csvName;
	}
}
