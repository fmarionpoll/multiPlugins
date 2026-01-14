package plugins.fmp.multiSPOTS96.tools.ROI2D;

import java.awt.Point;
import java.util.logging.Logger;

import org.w3c.dom.Node;

import icy.file.xml.XMLPersistent;
import icy.roi.BooleanMask2D;
import icy.roi.ROI2D;
import icy.util.XMLUtil;

/**
 * Enhanced ROI2D along time with proper encapsulation and validation.
 * Represents a ROI that exists at a specific time point with associated mask
 * data.
 * 
 * @author MultiSPOTS96 Team
 * @version 2.0
 */
public class ROI2DWithMask implements XMLPersistent {

	private static final Logger logger = Logger.getLogger(ROI2DWithMask.class.getName());

	// Private fields with proper encapsulation

	private ROI2D inputRoi = null;
//	private ArrayList<ArrayList<int[]>> masksList = null;
	private BooleanMask2D inputMask = null;
	private BooleanMask2D inputMaskNoFly = null;
	private Point[] maskPoints = null;
	// Memory-efficient alternative using primitive arrays
	private int[] maskX = null;
	private int[] maskY = null;
	private int yMax = 0;
	private int yMin = 0;

	/**
	 * Creates a new ROI2DAlongT with specified time and ROI.
	 * 
	 * @param timePoint The time point for this ROI
	 * @param roi       The ROI at this time point
	 * @throws ValidationException If parameters are invalid
	 */
	public ROI2DWithMask(ROI2D roi) throws ValidationException {
		setInputRoi(roi);
	}

	/**
	 * Creates a new ROI2DAlongT with default values.
	 */
	public ROI2DWithMask() {
		// Default constructor
	}

	/**
	 * Gets the input ROI.
	 * 
	 * @return The input ROI, or null if not set
	 */
	public ROI2D getInputRoi() {
		return inputRoi;
	}

	/**
	 * Sets the input ROI.
	 * 
	 * @param roi The input ROI to set
	 * @throws ValidationException If the ROI is invalid
	 */
	public void setInputRoi(ROI2D roi) throws ValidationException {
		if (roi != null) {
			ROI2DValidator.validateROI2D(roi, "roi");
			this.inputRoi = (ROI2D) roi.getCopy();
		} else {
			this.inputRoi = null;
		}

		// Clear dependent data when ROI changes
		clearMaskData();
	}

	/**
	 * Gets the input mask.
	 * 
	 * @return The input mask, or null if not built
	 */
	public BooleanMask2D getInputMask() {
		return inputMask;
	}

	/**
	 * Gets the input mask without fly.
	 * 
	 * @return The input mask without fly, or null if not set
	 */
	public BooleanMask2D getInputMaskNoFly() {
		return inputMaskNoFly;
	}

	/**
	 * Sets the input mask without fly.
	 * 
	 * @param inputMaskNoFly The mask to set
	 */
	public void setInputMaskNoFly(BooleanMask2D inputMaskNoFly) {
		this.inputMaskNoFly = inputMaskNoFly;
	}

	/**
	 * Gets the mask points.
	 * 
	 * @return The mask points array, or null if not built
	 */
	public Point[] getMaskPoints() {
		return maskPoints != null ? maskPoints.clone() : null;
	}

	/**
	 * Gets the mask points as primitive arrays for memory efficiency.
	 * 
	 * @return Array containing [xCoords, yCoords] or null if no mask
	 */
	public int[][] getMaskPointsAsArrays() {
		if (maskPoints == null || maskPoints.length == 0) {
			return null;
		}
		
		if (maskX == null || maskY == null) {
			// Convert Point[] to primitive arrays
			maskX = new int[maskPoints.length];
			maskY = new int[maskPoints.length];
			for (int i = 0; i < maskPoints.length; i++) {
				maskX[i] = maskPoints[i].x;
				maskY[i] = maskPoints[i].y;
			}
		}
		
		return new int[][]{maskX, maskY};
	}

	/**
	 * Gets the number of mask points.
	 * 
	 * @return Number of mask points
	 */
	public int getMaskPointCount() {
		return maskPoints != null ? maskPoints.length : 0;
	}

	/**
	 * Gets a single mask point by index using primitive coordinates.
	 * 
	 * @param index The point index
	 * @return Array [x, y] or null if invalid index
	 */
	public int[] getMaskPointAt(int index) {
		if (maskPoints == null || index < 0 || index >= maskPoints.length) {
			return null;
		}
		
		if (maskX == null || maskY == null) {
			// Convert Point[] to primitive arrays
			maskX = new int[maskPoints.length];
			maskY = new int[maskPoints.length];
			for (int i = 0; i < maskPoints.length; i++) {
				maskX[i] = maskPoints[i].x;
				maskY[i] = maskPoints[i].y;
			}
		}
		
		return new int[]{maskX[index], maskY[index]};
	}

	/**
	 * Gets the maximum Y coordinate.
	 * 
	 * @return The maximum Y coordinate
	 */
	public int getYMax() {
		return yMax;
	}

	/**
	 * Gets the minimum Y coordinate.
	 * 
	 * @return The minimum Y coordinate
	 */
	public int getYMin() {
		return yMin;
	}

	/**
	 * Builds the 2D mask from the input ROI.
	 * 
	 * @throws ProcessingException If mask building fails
	 */
	public void buildMask2DFromInputRoi() throws ProcessingException {
		if (inputRoi == null) {
			throw new ProcessingException("buildMask2D", "Input ROI is not set");
		}

		try {
			inputMask = inputRoi.getBooleanMask2D(0, 0, 1, true); // z, t, c, inclusive
			maskPoints = inputMask.getPoints();
			calculateYBounds();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ProcessingException("buildMask2D", "Operation was interrupted", e);
		} catch (Exception e) {
			throw new ProcessingException("buildMask2D", "Failed to build mask from ROI", e);
		}
	}

	/**
	 * Calculates the height of the 2D mask.
	 * 
	 * @return The height of the mask (yMax - yMin + 1)
	 * @throws ProcessingException If mask points are not available
	 */
	public int getMask2DHeight() throws ProcessingException {
		if (maskPoints == null || maskPoints.length == 0) {
			throw new ProcessingException("getMask2DHeight",
					"Mask points are not available. Call buildMask2DFromInputRoi() first");
		}

		calculateYBounds();
		return (yMax - yMin + 1);
	}

	/**
	 * Calculates the Y bounds (min and max) from mask points.
	 */
	private void calculateYBounds() {
		if (maskPoints == null || maskPoints.length == 0) {
			yMax = 0;
			yMin = 0;
			return;
		}

		yMax = maskPoints[0].y;
		yMin = yMax;

		for (int i = 1; i < maskPoints.length; i++) {
			if (maskPoints[i].y < yMin) {
				yMin = maskPoints[i].y;
			}
			if (maskPoints[i].y > yMax) {
				yMax = maskPoints[i].y;
			}
		}
	}

	/**
	 * Clears all mask-related data.
	 */
	private void clearMaskData() {
		inputMask = null;
		inputMaskNoFly = null;
		maskPoints = null;
		maskX = null;
		maskY = null;
		yMax = 0;
		yMin = 0;
	}

	/**
	 * Checks if mask data is available.
	 * 
	 * @return true if mask data is built, false otherwise
	 */
	public boolean hasMaskData() {
		return inputMask != null && maskPoints != null;
	}

	/**
	 * Creates a summary string of this ROI along time.
	 * 
	 * @return A formatted summary string
	 */
	public String getSummary() {
		return String.format("ROI2DWithMask[ hasMask=%s, points=%d]", hasMaskData(),
				maskPoints != null ? maskPoints.length : 0);
	}

	@Override
	public boolean loadFromXML(Node node) {
		try {
			final Node nodeMeta = XMLUtil.getElement(node, Constants.XML.ID_META);
			if (nodeMeta == null) {
				logger.warning("No metadata node found in XML");
				return false;
			}

			inputRoi = Utilities.loadFromXML_ROI(nodeMeta);

			// Clear dependent data since we loaded a new ROI
			clearMaskData();

			return true;
		} catch (Exception e) {
			logger.severe("Failed to load ROI2DAlongT from XML: " + e.getMessage());
			return false;
		}
	}

	@Override
	public boolean saveToXML(Node node) {
		try {
			final Node nodeMeta = XMLUtil.setElement(node, Constants.XML.ID_META);
			if (nodeMeta == null) {
				logger.warning("Failed to create metadata node in XML");
				return false;
			}

			if (inputRoi != null) {
				Utilities.saveToXML_ROI(nodeMeta, inputRoi);
			}

			return true;
		} catch (Exception e) {
			logger.severe("Failed to save ROI2DAlongT to XML: " + e.getMessage());
			return false;
		}
	}

	@Override
	public String toString() {
		return getSummary();
	}
}
