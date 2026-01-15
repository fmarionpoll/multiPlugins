package plugins.fmp.multitools.tools.ROI2D;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import icy.roi.ROI;
import icy.roi.ROIEvent;
import icy.roi.ROIEvent.ROIEventType;
import icy.roi.ROIListener;
import icy.sequence.Sequence;
import icy.type.geom.Polygon2D;
import icy.type.geom.Polyline2D;
import plugins.fmp.multitools.tools.Logger;
import plugins.kernel.roi.roi2d.ROI2DPolyLine;

/**
 * Enhanced ROI2D grid implementation with thread safety, validation, and
 * improved architecture. Manages a grid of ROIs with proper encapsulation and
 * error handling.
 * 
 * @author MultiSPOTS96 Team
 * @version 2.0
 */
public class ROI2DGrid implements ROIListener {


	// Thread safety
	private final ReadWriteLock lock = new ReentrantReadWriteLock();

	// Grid data
	private Point2D.Double[][] grid;
	private ArrayList<ROI2DPolyLine> columnRois;
	private ArrayList<ROI2DPolyLine> rowRois;
	private ArrayList<ROI2DPolygonPlus> areaRois;

	// Grid dimensions
	private int gridRows = 0;
	private int gridColumns = 0;

	// State management
	private volatile boolean updateEnabled = true;
	private boolean gridInitialized = false;

	/**
	 * Creates a new ROI2DGrid.
	 */
	public ROI2DGrid() {
		// Default constructor
	}

	/**
	 * Creates a grid from the specified polygon frame with validation.
	 * 
	 * @param polygon The polygon defining the grid bounds
	 * @param columns Number of columns in the grid
	 * @param rows    Number of rows in the grid
	 * @throws ValidationException If parameters are invalid
	 * @throws GeometryException   If grid creation fails
	 */
	public void createGridFromFrame(Polygon2D polygon, int columns, int rows)
			throws ValidationException, GeometryException {

		// Validate inputs
		ROI2DValidator.validateNotNull(polygon, "polygon");
		ROI2DValidator.validatePolygonSides(polygon, Constants.Geometry.REQUIRED_POLYGON_SIDES, "polygon");
		ROI2DValidator.validateGridDimensions(columns, rows);

		lock.writeLock().lock();
		try {
			this.gridRows = rows + 1;
			this.gridColumns = columns + 1;

			// Create grid points
			grid = createGridWithPolygon(polygon, columns, rows);

			// Initialize ROI collections
			columnRois = new ArrayList<>(gridColumns);
			rowRois = new ArrayList<>(gridRows);

			// Create column ROIs
			createColumnRois();

			// Create row ROIs
			createRowRois();

			gridInitialized = true;

		} catch (Exception e) {
			gridInitialized = false;
			throw new GeometryException("createGridFromFrame", "Failed to create grid", e);
		} finally {
			lock.writeLock().unlock();
		}
	}

	/**
	 * Creates column ROIs with proper naming and listeners.
	 */
	private void createColumnRois() {
		for (int column = 0; column < gridColumns; column++) {
			ROI2DPolyLine roi = getVerticalROI(column);
			roi.setName(Constants.Grid.COLUMN_PREFIX + column);
			columnRois.add(roi);
			roi.addListener(this);
		}
	}

	/**
	 * Creates row ROIs with proper naming and listeners.
	 */
	private void createRowRois() {
		for (int row = 0; row < gridRows; row++) {
			ROI2DPolyLine roi = getHorizontalROI(row);
			roi.setName(Constants.Grid.ROW_PREFIX + row);
			rowRois.add(roi);
			roi.addListener(this);
		}
	}

	/**
	 * Gets the horizontal (row) ROIs.
	 * 
	 * @return A copy of the row ROIs list
	 */
	public ArrayList<ROI2DPolyLine> getHorizontalRois() {
		lock.readLock().lock();
		try {
			return rowRois != null ? new ArrayList<>(rowRois) : new ArrayList<>();
		} finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * Gets the vertical (column) ROIs.
	 * 
	 * @return A copy of the column ROIs list
	 */
	public ArrayList<ROI2DPolyLine> getVerticalRois() {
		lock.readLock().lock();
		try {
			return columnRois != null ? new ArrayList<>(columnRois) : new ArrayList<>();
		} finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * Gets all area ROIs.
	 * 
	 * @return A copy of the area ROIs list
	 */
	public ArrayList<ROI2DPolygonPlus> getAreaRois() {
		lock.readLock().lock();
		try {
			return areaRois != null ? new ArrayList<>(areaRois) : new ArrayList<>();
		} finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * Gets only the selected area ROIs.
	 * 
	 * @return A list of selected area ROIs
	 */
	public ArrayList<ROI2DPolygonPlus> getSelectedAreaRois() {
		lock.readLock().lock();
		try {
			ArrayList<ROI2DPolygonPlus> selectedRois = new ArrayList<>();
			if (areaRois != null) {
				for (ROI2DPolygonPlus roiP : areaRois) {
					if (roiP.isSelected()) {
						selectedRois.add(roiP);
					}
				}
			}
			return selectedRois;
		} finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * Gets the area ROI at the specified position with validation.
	 * 
	 * @param position The position index
	 * @return The ROI at the specified position, or null if not found
	 * @throws ValidationException If position is invalid
	 */
	public ROI2DPolygonPlus getAreaAt(int position) throws ValidationException {
		lock.readLock().lock();
		try {
			if (areaRois == null) {
				return null;
			}

			ROI2DValidator.validateArrayIndex(position, areaRois.size(), "position");

			ROI2DPolygonPlus roiP = areaRois.get(position);
			int actualPosition = roiP.getCagePosition();

			if (actualPosition != position) {
				// Search for the correct ROI
				for (ROI2DPolygonPlus roi : areaRois) {
					if (roi.getCagePosition() == position) {
						return roi;
					}
				}
				return null;
			}

			return roiP;
		} finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * Gets a copy of the grid points.
	 * 
	 * @return A copy of the grid points array
	 */
	public Point2D.Double[][] getGridPoints() {
		lock.readLock().lock();
		try {
			if (grid == null) {
				return null;
			}

			// Create a deep copy
			Point2D.Double[][] copy = new Point2D.Double[grid.length][];
			for (int i = 0; i < grid.length; i++) {
				if (grid[i] != null) {
					copy[i] = new Point2D.Double[grid[i].length];
					for (int j = 0; j < grid[i].length; j++) {
						if (grid[i][j] != null) {
							copy[i][j] = (Point2D.Double) grid[i][j].clone();
						}
					}
				}
			}
			return copy;
		} finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * Clears all grid ROIs from the sequence.
	 * 
	 * @param sequence The sequence to clear ROIs from
	 * @throws ValidationException If sequence is null
	 */
	public void clearGridRois(Sequence sequence) throws ValidationException {
		ROI2DValidator.validateNotNull(sequence, "sequence");

		lock.writeLock().lock();
		try {
			if (rowRois != null && !rowRois.isEmpty()) {
				sequence.removeROIs(rowRois, false);
			}
			if (columnRois != null && !columnRois.isEmpty()) {
				sequence.removeROIs(columnRois, false);
			}
			if (areaRois != null && !areaRois.isEmpty()) {
				sequence.removeROIs(areaRois, false);
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	/**
	 * Converts the grid to area ROIs with validation.
	 * 
	 * @param cageRoot       The root name for cages
	 * @param color          The color for the ROIs
	 * @param widthInterval  The width interval
	 * @param heightInterval The height interval
	 * @throws ValidationException If parameters are invalid
	 * @throws ProcessingException If grid conversion fails
	 */
	public void gridToRois(String cageRoot, Color color, int widthInterval, int heightInterval)
			throws ValidationException, ProcessingException {

		if (cageRoot == null) {
			cageRoot = Constants.Grid.DEFAULT_CAGE_ROOT_NAME;
		}

		if (!gridInitialized) {
			throw new ProcessingException("gridToRois",
					"Grid has not been initialized. Call createGridFromFrame() first");
		}

		lock.writeLock().lock();
		try {
			int totalAreas = (gridColumns - 1) * (gridRows - 1);
			areaRois = new ArrayList<>(totalAreas);

			int index = 0;
			for (int row = 0; row < (gridRows - 1); row++) {
				for (int column = 0; column < (gridColumns - 1); column++) {
					ROI2DPolygonPlus roiP = createRoiPolygon(column, row, widthInterval, heightInterval);
					roiP.setName(cageRoot + String.format(Constants.XML.CAGE_NAME_FORMAT, index));

					if (color != null) {
						roiP.setColor(color);
					}

					try {
						roiP.setCageRow(row);
						roiP.setCageColumn(column);
						roiP.setCagePosition(index);
					} catch (ValidationException e) {
						throw new ProcessingException("gridToRois", "Failed to set cage properties", e);
					}

					areaRois.add(roiP);
					index++;
				}
			}
		} catch (ValidationException e) {
			throw e;
		} catch (Exception e) {
			throw new ProcessingException("gridToRois", "Failed to convert grid to ROIs", e);
		} finally {
			lock.writeLock().unlock();
		}
	}

	/**
	 * Creates a polygon ROI for the specified grid cage with validation.
	 */
	private ROI2DPolygonPlus createRoiPolygon(int column, int row, int width, int height) throws ValidationException {

		ROI2DValidator.validateArrayIndex(column, gridColumns - 1, "column");
		ROI2DValidator.validateArrayIndex(row, gridRows - 1, "row");

		List<Point2D> points = new ArrayList<>(4);

		// Create the four corners of the polygon
		Point2D.Double pt = (Double) grid[column][row].clone();
		pt.x += width;
		pt.y += height;
		points.add(pt);

		pt = (Double) grid[column][row + 1].clone();
		pt.x += width;
		pt.y -= height;
		points.add(pt);

		pt = (Double) grid[column + 1][row + 1].clone();
		pt.x -= width;
		pt.y -= height;
		points.add(pt);

		pt = (Double) grid[column + 1][row].clone();
		pt.x -= width;
		pt.y += height;
		points.add(pt);

		return new ROI2DPolygonPlus(points, row, column);
	}

	/**
	 * Creates a grid with the specified polygon and dimensions.
	 */
	private Point2D.Double[][] createGridWithPolygon(Polygon2D polygon, int columns, int rows)
			throws GeometryException {

		try {
			Point2D.Double[][] arrayPoints = new Point2D.Double[columns + 1][rows + 1];

			for (int col = 0; col <= columns; col++) {
				double ratioX = col / (double) columns;

				double x = polygon.xpoints[0] + (polygon.xpoints[3] - polygon.xpoints[0]) * ratioX;
				double y = polygon.ypoints[0] + (polygon.ypoints[3] - polygon.ypoints[0]) * ratioX;
				Point2D.Double point0 = new Point2D.Double(x, y);

				x = polygon.xpoints[1] + (polygon.xpoints[2] - polygon.xpoints[1]) * ratioX;
				y = polygon.ypoints[1] + (polygon.ypoints[2] - polygon.ypoints[1]) * ratioX;
				Point2D.Double point1 = new Point2D.Double(x, y);

				for (int row = 0; row <= rows; row++) {
					double ratioY = row / (double) rows;
					x = point0.x + (point1.x - point0.x) * ratioY;
					y = point0.y + (point1.y - point0.y) * ratioY;

					arrayPoints[col][row] = new Point2D.Double(x, y);
				}
			}
			return arrayPoints;
		} catch (Exception e) {
			throw new GeometryException("createGridWithPolygon", "Failed to create grid points", e);
		}
	}

	/**
	 * Creates a vertical ROI for the specified column.
	 */
	private ROI2DPolyLine getVerticalROI(int column) {
		List<Point2D> points = new ArrayList<>(gridRows);
		for (int row = 0; row < gridRows; row++) {
			points.add(grid[column][row]);
		}
		return new ROI2DPolyLine(points);
	}

	/**
	 * Creates a horizontal ROI for the specified row.
	 */
	private ROI2DPolyLine getHorizontalROI(int row) {
		List<Point2D> points = new ArrayList<>(gridColumns);
		for (int column = 0; column < gridColumns; column++) {
			points.add(grid[column][row]);
		}
		return new ROI2DPolyLine(points);
	}

	/**
	 * Creates a vertical polyline for the specified column.
	 */
	private Polyline2D getVerticalLine(int column) {
		double[] xpoints = new double[gridRows];
		double[] ypoints = new double[gridRows];
		for (int row = 0; row < gridRows; row++) {
			xpoints[row] = grid[column][row].x;
			ypoints[row] = grid[column][row].y;
		}
		return new Polyline2D(xpoints, ypoints, gridRows);
	}

	/**
	 * Creates a horizontal polyline for the specified row.
	 */
	private Polyline2D getHorizontalLine(int row) {
		double[] xpoints = new double[gridColumns];
		double[] ypoints = new double[gridColumns];
		for (int column = 0; column < gridColumns; column++) {
			xpoints[column] = grid[column][row].x;
			ypoints[column] = grid[column][row].y;
		}
		return new Polyline2D(xpoints, ypoints, gridColumns);
	}

	/**
	 * Updates the grid from a vertical ROI.
	 */
	private void updateGridFromVerticalROI(int column, ROI2DPolyLine roi) {
		Polyline2D line = roi.getPolyline2D();
		for (int row = 0; row < gridRows; row++) {
			if (row < line.npoints) {
				grid[column][row].x = line.xpoints[row];
				grid[column][row].y = line.ypoints[row];
			}
		}
	}

	/**
	 * Updates the grid from a horizontal ROI.
	 */
	private void updateGridFromHorizontalROI(int row, ROI2DPolyLine roi) {
		Polyline2D line = roi.getPolyline2D();
		for (int column = 0; column < gridColumns; column++) {
			if (column < line.npoints) {
				grid[column][row].x = line.xpoints[column];
				grid[column][row].y = line.ypoints[column];
			}
		}
	}

	/**
	 * Updates a horizontal ROI from grid values.
	 */
	private void updateHorizontalROIFromGridValues(int row) {
		if (row >= 0 && row < rowRois.size()) {
			ROI2DPolyLine roi = rowRois.get(row);
			roi.setPolyline2D(getHorizontalLine(row));
		}
	}

	/**
	 * Updates a vertical ROI from grid values.
	 */
	private void updateVerticalROIFromGridValues(int column) {
		if (column >= 0 && column < columnRois.size()) {
			ROI2DPolyLine roi = columnRois.get(column);
			roi.setPolyline2D(getVerticalLine(column));
		}
	}

	/**
	 * Checks if the grid is initialized.
	 * 
	 * @return true if the grid is initialized, false otherwise
	 */
	public boolean isGridInitialized() {
		lock.readLock().lock();
		try {
			return gridInitialized;
		} finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * Gets the number of grid rows.
	 * 
	 * @return The number of grid rows
	 */
	public int getGridRows() {
		lock.readLock().lock();
		try {
			return gridRows;
		} finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * Gets the number of grid columns.
	 * 
	 * @return The number of grid columns
	 */
	public int getGridColumns() {
		lock.readLock().lock();
		try {
			return gridColumns;
		} finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * Enables or disables grid updates.
	 * 
	 * @param enabled true to enable updates, false to disable
	 */
	public void setUpdateEnabled(boolean enabled) {
		this.updateEnabled = enabled;
	}

	/**
	 * Checks if grid updates are enabled.
	 * 
	 * @return true if updates are enabled, false otherwise
	 */
	public boolean isUpdateEnabled() {
		return updateEnabled;
	}

	@Override
	public void roiChanged(ROIEvent event) {
		if (!updateEnabled || !gridInitialized) {
			return;
		}

		if (event.getType() == ROIEventType.ROI_CHANGED) {
			lock.writeLock().lock();
			try {
				ROI roi = event.getSource();
				String name = roi.getName();

				if (name == null) {
					return;
				}

				int index = extractIndexFromName(name);
				if (index < 0) {
					return;
				}

				updateEnabled = false;
				try {
					if (name.contains(Constants.Grid.ROW_IDENTIFIER)) {
						handleRowUpdate(index, (ROI2DPolyLine) roi);
					} else if (name.contains(Constants.Grid.COLUMN_IDENTIFIER)) {
						handleColumnUpdate(index, (ROI2DPolyLine) roi);
					}
				} finally {
					updateEnabled = true;
				}

			} catch (Exception e) {
				Logger.warn("Failed to update grid from ROI change: " + e.getMessage(), e);
			} finally {
				lock.writeLock().unlock();
			}
		}
	}

	/**
	 * Handles row update operations.
	 */
	private void handleRowUpdate(int index, ROI2DPolyLine roi) {
		if (index >= 0 && index < gridRows) {
			updateGridFromHorizontalROI(index, roi);
			for (int i = 0; i < gridColumns; i++) {
				updateVerticalROIFromGridValues(i);
			}
		}
	}

	/**
	 * Handles column update operations.
	 */
	private void handleColumnUpdate(int index, ROI2DPolyLine roi) {
		if (index >= 0 && index < gridColumns) {
			updateGridFromVerticalROI(index, roi);
			for (int i = 0; i < gridRows; i++) {
				updateHorizontalROIFromGridValues(i);
			}
		}
	}

	/**
	 * Extracts index from ROI name.
	 */
	private int extractIndexFromName(String name) {
		try {
			int lastSeparator = name.lastIndexOf(Constants.Grid.NAME_INDEX_SEPARATOR);
			if (lastSeparator >= 0 && lastSeparator < name.length() - 1) {
				return Integer.parseInt(name.substring(lastSeparator + 1));
			}
		} catch (NumberFormatException e) {
			Logger.warn("Failed to extract index from ROI name: " + name);
		}
		return -1;
	}

	@Override
	public String toString() {
		lock.readLock().lock();
		try {
			return String.format("ROI2DGrid[rows=%d, columns=%d, initialized=%s, areas=%d]", gridRows, gridColumns,
					gridInitialized, areaRois != null ? areaRois.size() : 0);
		} finally {
			lock.readLock().unlock();
		}
	}
}
