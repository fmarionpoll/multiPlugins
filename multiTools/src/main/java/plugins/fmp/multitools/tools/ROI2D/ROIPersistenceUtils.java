package plugins.fmp.multitools.tools.ROI2D;

import java.awt.Rectangle;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import icy.roi.ROI2D;
import icy.type.geom.Polygon2D;
import icy.type.geom.Polyline2D;
import plugins.kernel.roi.roi2d.ROI2DEllipse;
import plugins.kernel.roi.roi2d.ROI2DLine;
import plugins.kernel.roi.roi2d.ROI2DPolygon;
import plugins.kernel.roi.roi2d.ROI2DPolyLine;
import plugins.kernel.roi.roi2d.ROI2DRectangle;

/**
 * Utility class for persisting ROIs to/from CSV format.
 * 
 * <p>Provides common methods for exporting and importing ROIs across different entity types
 * (Spot, Capillary, Cage). Supports efficient storage using geometric parameters for simple
 * shapes and full coordinate storage for complex shapes.
 * 
 * <p>CSV Format:
 * <pre>
 * ELLIPSE: roiType;centerX;centerY;radiusX;radiusY
 * LINE: roiType;x1;y1;x2;y2
 * POLYLINE: roiType;npoints;x1;y1;x2;y2;...
 * POLYGON: roiType;npoints;x1;y1;x2;y2;...
 * RECTANGLE: roiType;x;y;width;height
 * </pre>
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 * @since 2.3.3
 */
public class ROIPersistenceUtils {
	
	private ROIPersistenceUtils() {
		// Utility class - prevent instantiation
	}
	
	// ========================================================================
	// CSV EXPORT METHODS
	// ========================================================================
	
	/**
	 * Exports ROI type and data to CSV format.
	 * 
	 * <p>Returns two CSV fields: roiType and roiData separated by the separator.
	 * For example: "ellipse;150;200;10;10" for an ellipse at (150,200) with radii 10.
	 * 
	 * @param roi the ROI to export
	 * @param separator the CSV field separator
	 * @return CSV string with roiType and roiData, or empty strings if roi is null
	 */
	public static String exportROITypeAndData(ROI2D roi, String separator) {
		if (roi == null) {
			return separator; // roiType empty, roiData empty
		}
		
		ROIType type = ROIType.fromROI2D(roi);
		String typeStr = type.toCsvString();
		String dataStr = exportROIData(roi, type, separator);
		
		return typeStr + separator + dataStr;
	}
	
	/**
	 * Exports ROI data based on its type.
	 * 
	 * @param roi the ROI to export
	 * @param type the ROI type
	 * @param separator the CSV field separator
	 * @return CSV string with ROI data
	 */
	private static String exportROIData(ROI2D roi, ROIType type, String separator) {
		switch (type) {
		case ELLIPSE:
			return extractEllipseParams(roi, separator);
		case LINE:
			return extractLineParams(roi, separator);
		case POLYLINE:
			return extractPolylineParams(roi, separator);
		case POLYGON:
			return extractPolygonParams(roi, separator);
		case RECTANGLE:
			return extractRectangleParams(roi, separator);
		default:
			return "";
		}
	}
	
	/**
	 * Extracts ellipse parameters: centerX, centerY, radiusX, radiusY.
	 * 
	 * @param roi the ellipse ROI
	 * @param separator the CSV field separator
	 * @return CSV string with ellipse parameters
	 */
	public static String extractEllipseParams(ROI2D roi, String separator) {
		if (!(roi instanceof ROI2DEllipse)) {
			return "";
		}
		
		Rectangle bounds = roi.getBounds();
		double centerX = bounds.getCenterX();
		double centerY = bounds.getCenterY();
		double radiusX = bounds.getWidth() / 2.0;
		double radiusY = bounds.getHeight() / 2.0;
		
		return String.format("%.1f%s%.1f%s%.1f%s%.1f", 
			centerX, separator, centerY, separator, radiusX, separator, radiusY);
	}
	
	/**
	 * Extracts line parameters: x1, y1, x2, y2.
	 * 
	 * @param roi the line ROI
	 * @param separator the CSV field separator
	 * @return CSV string with line parameters
	 */
	public static String extractLineParams(ROI2D roi, String separator) {
		if (!(roi instanceof ROI2DLine)) {
			return "";
		}
		
		ROI2DLine lineROI = (ROI2DLine) roi;
		Line2D line = lineROI.getLine();
		
		return String.format("%.1f%s%.1f%s%.1f%s%.1f",
			line.getX1(), separator, line.getY1(), separator,
			line.getX2(), separator, line.getY2());
	}
	
	/**
	 * Extracts polyline parameters: npoints, x1, y1, x2, y2, ...
	 * 
	 * @param roi the polyline ROI
	 * @param separator the CSV field separator
	 * @return CSV string with polyline parameters
	 */
	public static String extractPolylineParams(ROI2D roi, String separator) {
		if (!(roi instanceof ROI2DPolyLine)) {
			return "";
		}
		
		ROI2DPolyLine polylineROI = (ROI2DPolyLine) roi;
		Polyline2D polyline = polylineROI.getPolyline2D();
		
		StringBuilder sb = new StringBuilder();
		sb.append(polyline.npoints);
		
		for (int i = 0; i < polyline.npoints; i++) {
			sb.append(separator).append(String.format("%.1f", polyline.xpoints[i]));
			sb.append(separator).append(String.format("%.1f", polyline.ypoints[i]));
		}
		
		return sb.toString();
	}
	
	/**
	 * Extracts polygon parameters: npoints, x1, y1, x2, y2, ...
	 * 
	 * @param roi the polygon ROI
	 * @param separator the CSV field separator
	 * @return CSV string with polygon parameters
	 */
	public static String extractPolygonParams(ROI2D roi, String separator) {
		if (!(roi instanceof ROI2DPolygon)) {
			return "";
		}
		
		ROI2DPolygon polygonROI = (ROI2DPolygon) roi;
		Polygon2D polygon = polygonROI.getPolygon2D();
		
		StringBuilder sb = new StringBuilder();
		sb.append(polygon.npoints);
		
		for (int i = 0; i < polygon.npoints; i++) {
			sb.append(separator).append(String.format("%.1f", polygon.xpoints[i]));
			sb.append(separator).append(String.format("%.1f", polygon.ypoints[i]));
		}
		
		return sb.toString();
	}
	
	/**
	 * Extracts rectangle parameters: x, y, width, height.
	 * 
	 * @param roi the rectangle ROI
	 * @param separator the CSV field separator
	 * @return CSV string with rectangle parameters
	 */
	public static String extractRectangleParams(ROI2D roi, String separator) {
		if (!(roi instanceof ROI2DRectangle)) {
			return "";
		}
		
		Rectangle bounds = roi.getBounds();
		
		return String.format("%.1f%s%.1f%s%.1f%s%.1f",
			(double) bounds.x, separator, (double) bounds.y, separator,
			(double) bounds.width, separator, (double) bounds.height);
	}
	
	// ========================================================================
	// CSV IMPORT METHODS
	// ========================================================================
	
	/**
	 * Imports ROI from CSV format.
	 * 
	 * @param roiTypeStr the ROI type string from CSV
	 * @param roiDataStr the ROI data string from CSV (may contain separators)
	 * @param roiName the name to assign to the reconstructed ROI
	 * @return the reconstructed ROI, or null if reconstruction fails
	 */
	public static ROI2D importROIFromCSV(String roiTypeStr, String roiDataStr, String roiName) {
		if (roiTypeStr == null || roiTypeStr.trim().isEmpty()) {
			return null;
		}
		
		if (roiDataStr == null || roiDataStr.trim().isEmpty()) {
			return null;
		}
		
		ROIType type = ROIType.fromString(roiTypeStr);
		
		// Split data by common separators (handle both ; and ,)
		String[] params = roiDataStr.split("[;,]");
		
		ROI2D roi = reconstructROI(type, params, roiName);
		return roi;
	}
	
	/**
	 * Reconstructs ROI from parameters based on type.
	 * 
	 * @param type the ROI type
	 * @param params the parameter array
	 * @param name the ROI name
	 * @return the reconstructed ROI, or null if reconstruction fails
	 */
	private static ROI2D reconstructROI(ROIType type, String[] params, String name) {
		try {
			switch (type) {
			case ELLIPSE:
				return reconstructEllipse(params, name);
			case LINE:
				return reconstructLine(params, name);
			case POLYLINE:
				return reconstructPolyline(params, name);
			case POLYGON:
				return reconstructPolygon(params, name);
			case RECTANGLE:
				return reconstructRectangle(params, name);
			default:
				return null;
			}
		} catch (Exception e) {
			System.err.println("Error reconstructing ROI of type " + type + ": " + e.getMessage());
			return null;
		}
	}
	
	/**
	 * Reconstructs ellipse ROI from parameters: centerX, centerY, radiusX, radiusY.
	 * 
	 * @param params the parameter array
	 * @param name the ROI name
	 * @return the reconstructed ellipse ROI
	 */
	public static ROI2D reconstructEllipse(String[] params, String name) {
		if (params.length < 4) {
			throw new IllegalArgumentException("Ellipse requires 4 parameters: centerX, centerY, radiusX, radiusY");
		}
		
		double centerX = Double.parseDouble(params[0].trim());
		double centerY = Double.parseDouble(params[1].trim());
		double radiusX = Double.parseDouble(params[2].trim());
		double radiusY = Double.parseDouble(params[3].trim());
		
		double x = centerX - radiusX;
		double y = centerY - radiusY;
		double width = 2 * radiusX;
		double height = 2 * radiusY;
		
		Ellipse2D ellipse = new Ellipse2D.Double(x, y, width, height);
		ROI2DEllipse roi = new ROI2DEllipse(ellipse);
		
		if (name != null && !name.isEmpty()) {
			roi.setName(name);
		}
		
		return roi;
	}
	
	/**
	 * Reconstructs line ROI from parameters: x1, y1, x2, y2.
	 * 
	 * @param params the parameter array
	 * @param name the ROI name
	 * @return the reconstructed line ROI
	 */
	public static ROI2D reconstructLine(String[] params, String name) {
		if (params.length < 4) {
			throw new IllegalArgumentException("Line requires 4 parameters: x1, y1, x2, y2");
		}
		
		double x1 = Double.parseDouble(params[0].trim());
		double y1 = Double.parseDouble(params[1].trim());
		double x2 = Double.parseDouble(params[2].trim());
		double y2 = Double.parseDouble(params[3].trim());
		
		Line2D line = new Line2D.Double(x1, y1, x2, y2);
		ROI2DLine roi = new ROI2DLine(line);
		
		if (name != null && !name.isEmpty()) {
			roi.setName(name);
		}
		
		return roi;
	}
	
	/**
	 * Reconstructs polyline ROI from parameters: npoints, x1, y1, x2, y2, ...
	 * 
	 * @param params the parameter array
	 * @param name the ROI name
	 * @return the reconstructed polyline ROI
	 */
	public static ROI2D reconstructPolyline(String[] params, String name) {
		if (params.length < 1) {
			throw new IllegalArgumentException("Polyline requires at least npoints parameter");
		}
		
		int npoints = Integer.parseInt(params[0].trim());
		
		if (params.length < 1 + npoints * 2) {
			throw new IllegalArgumentException("Polyline requires " + (1 + npoints * 2) + 
				" parameters (npoints + x,y coordinates)");
		}
		
		double[] xpoints = new double[npoints];
		double[] ypoints = new double[npoints];
		
		for (int i = 0; i < npoints; i++) {
			xpoints[i] = Double.parseDouble(params[1 + i * 2].trim());
			ypoints[i] = Double.parseDouble(params[1 + i * 2 + 1].trim());
		}
		
		Polyline2D polyline = new Polyline2D(xpoints, ypoints, npoints);
		ROI2DPolyLine roi = new ROI2DPolyLine(polyline);
		
		if (name != null && !name.isEmpty()) {
			roi.setName(name);
		}
		
		return roi;
	}
	
	/**
	 * Reconstructs polygon ROI from parameters: npoints, x1, y1, x2, y2, ...
	 * 
	 * @param params the parameter array
	 * @param name the ROI name
	 * @return the reconstructed polygon ROI
	 */
	public static ROI2D reconstructPolygon(String[] params, String name) {
		if (params.length < 1) {
			throw new IllegalArgumentException("Polygon requires at least npoints parameter");
		}
		
		int npoints = Integer.parseInt(params[0].trim());
		
		if (params.length < 1 + npoints * 2) {
			throw new IllegalArgumentException("Polygon requires " + (1 + npoints * 2) + 
				" parameters (npoints + x,y coordinates)");
		}
		
		List<Point2D> points = new ArrayList<>();
		
		for (int i = 0; i < npoints; i++) {
			double x = Double.parseDouble(params[1 + i * 2].trim());
			double y = Double.parseDouble(params[1 + i * 2 + 1].trim());
			points.add(new Point2D.Double(x, y));
		}
		
		Polygon2D polygon = new Polygon2D(points);
		ROI2DPolygon roi = new ROI2DPolygon(polygon);
		
		if (name != null && !name.isEmpty()) {
			roi.setName(name);
		}
		
		return roi;
	}
	
	/**
	 * Reconstructs rectangle ROI from parameters: x, y, width, height.
	 * 
	 * @param params the parameter array
	 * @param name the ROI name
	 * @return the reconstructed rectangle ROI
	 */
	public static ROI2D reconstructRectangle(String[] params, String name) {
		if (params.length < 4) {
			throw new IllegalArgumentException("Rectangle requires 4 parameters: x, y, width, height");
		}
		
		double x = Double.parseDouble(params[0].trim());
		double y = Double.parseDouble(params[1].trim());
		double width = Double.parseDouble(params[2].trim());
		double height = Double.parseDouble(params[3].trim());
		
		ROI2DRectangle roi = new ROI2DRectangle(x, y, width, height);
		
		if (name != null && !name.isEmpty()) {
			roi.setName(name);
		}
		
		return roi;
	}
	
	// ========================================================================
	// UTILITY METHODS
	// ========================================================================
	
	/**
	 * Detects the ROI type from an ROI2D instance.
	 * 
	 * @param roi the ROI to analyze
	 * @return the detected ROI type
	 */
	public static ROIType detectROIType(ROI2D roi) {
		return ROIType.fromROI2D(roi);
	}
	
	/**
	 * Checks if an ellipse ROI has been modified from its stored parameters.
	 * 
	 * <p>This is useful for Spots to determine if they should save geometric parameters
	 * (efficient) or full coordinates (preserves user edits).
	 * 
	 * @param currentROI the current ROI
	 * @param storedCenterX the stored center X coordinate
	 * @param storedCenterY the stored center Y coordinate
	 * @param storedRadius the stored radius
	 * @return true if the ROI has been modified from the stored parameters
	 */
	public static boolean isModifiedEllipseROI(ROI2D currentROI, int storedCenterX, 
			int storedCenterY, int storedRadius) {
		if (!(currentROI instanceof ROI2DEllipse)) {
			return true; // Different type = modified
		}
		
		Rectangle bounds = currentROI.getBounds();
		double currentCenterX = bounds.getCenterX();
		double currentCenterY = bounds.getCenterY();
		double currentRadiusX = bounds.getWidth() / 2.0;
		double currentRadiusY = bounds.getHeight() / 2.0;
		
		// Allow small tolerance for floating point comparison
		double tolerance = 0.5;
		
		boolean centerXMatches = Math.abs(currentCenterX - storedCenterX) < tolerance;
		boolean centerYMatches = Math.abs(currentCenterY - storedCenterY) < tolerance;
		boolean radiusMatches = Math.abs(currentRadiusX - storedRadius) < tolerance &&
		                        Math.abs(currentRadiusY - storedRadius) < tolerance;
		
		return !(centerXMatches && centerYMatches && radiusMatches);
	}
}
