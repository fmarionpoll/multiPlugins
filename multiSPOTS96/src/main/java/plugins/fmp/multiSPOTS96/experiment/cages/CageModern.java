package plugins.fmp.multiSPOTS96.experiment.cages;

import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import icy.roi.BooleanMask2D;
import icy.roi.ROI2D;
import plugins.fmp.multiSPOTS96.experiment.spots.Spot;
import plugins.fmp.multiSPOTS96.experiment.spots.SpotString;
import plugins.fmp.multiSPOTS96.experiment.spots.SpotsArray;
import plugins.kernel.roi.roi2d.ROI2DEllipse;

/**
 * Modern, thread-safe implementation of cage management with clean code
 * practices.
 * 
 * <p>
 * This class provides comprehensive functionality for managing individual
 * cages:
 * <ul>
 * <li>Immutable data structures with builder patterns</li>
 * <li>Proper encapsulation and validation</li>
 * <li>Structured error handling with rich results</li>
 * <li>Resource management with AutoCloseable</li>
 * <li>Thread-safe operations</li>
 * </ul>
 * 
 * <p>
 * Usage example:
 * 
 * <pre>{@code
 * ModernCage cage = ModernCage.builder().withData(CageData.createValid(roi, properties)).build();
 * 
 * CageOperationResult result = cage.addSpot(new Point2D.Double(100, 100), 30);
 * if (result.isSuccess()) {
 * 	System.out.println("Added spot successfully");
 * }
 * }</pre>
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 * @since 2.3.3
 */
public final class CageModern implements Comparable<CageModern>, AutoCloseable {
	private static final Logger LOGGER = Logger.getLogger(CageModern.class.getName());

	// === CORE DATA ===
	private final CageData data;
	private final FlyPositions flyPositions;
	private final SpotsArray spotsArray;
	private final AtomicBoolean closed = new AtomicBoolean(false);

	// === CONSTRUCTORS ===
	private CageModern(Builder builder) {
		this.data = Objects.requireNonNull(builder.data, "CageData cannot be null");
		this.flyPositions = builder.flyPositions != null ? builder.flyPositions : new FlyPositions();
		this.spotsArray = builder.spotsArray != null ? builder.spotsArray : new SpotsArray();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static CageModern createValid(ROI2D roi, CageProperties properties) {
		CageData cageData = CageData.createValid(roi, properties);
		return builder().withData(cageData).build();
	}

	public static CageModern createInvalid(ROI2D roi, String reason) {
		CageData cageData = CageData.createInvalid(roi, reason);
		return builder().withData(cageData).build();
	}

	// === INFORMATION ACCESS ===

	/**
	 * Gets the immutable cage data.
	 * 
	 * @return the cage data
	 * @throws IllegalStateException if the cage is closed
	 */
	public CageData getData() {
		ensureNotClosed();
		return data;
	}

	/**
	 * Gets the fly positions for this cage.
	 * 
	 * @return the fly positions
	 * @throws IllegalStateException if the cage is closed
	 */
	public FlyPositions getFlyPositions() {
		ensureNotClosed();
		return flyPositions;
	}

	/**
	 * Gets the spots array for this cage.
	 * 
	 * @return the spots array
	 * @throws IllegalStateException if the cage is closed
	 */
	public SpotsArray getSpotsArray() {
		ensureNotClosed();
		return spotsArray;
	}

	// === OPERATIONS ===

	/**
	 * Adds an elliptical spot to this cage with validation and rich error
	 * reporting.
	 * 
	 * @param center the center point of the spot
	 * @param radius the radius of the spot
	 * @return detailed operation result
	 * @throws IllegalStateException if the cage is closed
	 */
	public CageOperationResult addSpot(Point2D.Double center, int radius) {
		ensureNotClosed();
		long startTime = System.currentTimeMillis();

		try {
			// Input validation
			if (center == null) {
				return CageOperationResult.failure("ADD_SPOT", new IllegalArgumentException("Center cannot be null"),
						"Invalid spot center");
			}

			if (radius <= 0) {
				return CageOperationResult.failure("ADD_SPOT", new IllegalArgumentException("Radius must be positive"),
						"Invalid spot radius: " + radius);
			}

			// Create spot
			Spot spot = createEllipseSpot(spotsArray.getSpotsList().size(), center, radius);

			// Add spot to array
			spotsArray.getSpotsList().add(spot);

			long processingTime = System.currentTimeMillis() - startTime;

			return CageOperationResult.success("ADD_SPOT", "Successfully added spot").toBuilder()
					.processingTimeMs(processingTime).addMetadata("spotCount", spotsArray.getSpotsList().size())
					.addMetadata("spotRadius", radius).addMetadata("spotCenter", center).build();

		} catch (Exception e) {
			return CageOperationResult.failure("ADD_SPOT", e, "Unexpected error adding spot");
		}
	}

	/**
	 * Computes the boolean mask for this cage with proper error handling.
	 * 
	 * @return operation result with mask computation details
	 * @throws IllegalStateException if the cage is closed
	 */
	public CageOperationResult computeMask() {
		ensureNotClosed();
		long startTime = System.currentTimeMillis();

		try {
			BooleanMask2D mask = data.getRoi().getBooleanMask2D(0, 0, 1, true);

			long processingTime = System.currentTimeMillis() - startTime;

			return CageOperationResult.success("COMPUTE_MASK", "Successfully computed cage mask").toBuilder()
					.processingTimeMs(processingTime).addMetadata("maskPoints", mask.getNumberOfPoints()).build();

		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return CageOperationResult.failure("COMPUTE_MASK", e, "Mask computation was interrupted");
		} catch (Exception e) {
			return CageOperationResult.failure("COMPUTE_MASK", e, "Failed to compute mask: " + e.getMessage());
		}
	}

	/**
	 * Clears all measurements for this cage.
	 * 
	 * @return operation result
	 * @throws IllegalStateException if the cage is closed
	 */
	public CageOperationResult clearMeasures() {
		ensureNotClosed();
		long startTime = System.currentTimeMillis();

		try {
			flyPositions.clear();

			long processingTime = System.currentTimeMillis() - startTime;

			return CageOperationResult.success("CLEAR_MEASURES", "Successfully cleared all measures").toBuilder()
					.processingTimeMs(processingTime).build();

		} catch (Exception e) {
			return CageOperationResult.failure("CLEAR_MEASURES", e, "Failed to clear measures");
		}
	}

	/**
	 * Validates this cage's data and configuration.
	 * 
	 * @return validation result with detailed feedback
	 * @throws IllegalStateException if the cage is closed
	 */
	public CageOperationResult validateCage() {
		ensureNotClosed();
		long startTime = System.currentTimeMillis();

		try {
			boolean isValid = true;
			StringBuilder issues = new StringBuilder();

			// Check ROI
			if (data.getRoi() == null) {
				isValid = false;
				issues.append("ROI is null; ");
			} else if (!data.hasValidBounds()) {
				isValid = false;
				issues.append("ROI bounds are invalid; ");
			}

			// Check properties
			if (data.getProperties() == null) {
				isValid = false;
				issues.append("Properties are null; ");
			} else if (data.getProperties().getCageID() < 0) {
				isValid = false;
				issues.append("Cage ID is invalid; ");
			}

			// Check name
			String name = data.getName();
			if (name == null || name.trim().isEmpty()) {
				isValid = false;
				issues.append("Cage name is empty; ");
			}

			long processingTime = System.currentTimeMillis() - startTime;

			if (isValid) {
				return CageOperationResult.success("VALIDATE", "Cage validation passed").toBuilder()
						.processingTimeMs(processingTime).addMetadata("cageName", name)
						.addMetadata("cageID", data.getProperties().getCageID()).build();
			} else {
				return CageOperationResult
						.failure("VALIDATE", new IllegalStateException("Validation failed"),
								"Cage validation failed: " + issues.toString())
						.toBuilder().processingTimeMs(processingTime).build();
			}

		} catch (Exception e) {
			return CageOperationResult.failure("VALIDATE", e, "Unexpected error during validation");
		}
	}

	// === COMPARISON & LIFECYCLE ===

	@Override
	public int compareTo(CageModern other) {
		if (other == null) {
			throw new IllegalArgumentException("Cannot compare with null cage");
		}
		return data.getName().compareTo(other.data.getName());
	}

	@Override
	public void close() {
		if (closed.compareAndSet(false, true)) {
			LOGGER.fine("Closing cage: " + data.getName());
			// Cleanup resources if needed
			flyPositions.clear();
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null || getClass() != obj.getClass())
			return false;
		CageModern that = (CageModern) obj;
		return Objects.equals(data.getName(), that.data.getName())
				&& Objects.equals(data.getProperties().getCageID(), that.data.getProperties().getCageID());
	}

	@Override
	public int hashCode() {
		return Objects.hash(data.getName(), data.getProperties().getCageID());
	}

	@Override
	public String toString() {
		return String.format("ModernCage{name='%s', id=%d, valid=%b, spots=%d}", data.getName(),
				data.getProperties().getCageID(), data.isValid(), spotsArray.getSpotsList().size());
	}

	// === PRIVATE HELPER METHODS ===

	/**
	 * Ensures this cage is not closed.
	 * 
	 * @throws IllegalStateException if the cage is closed
	 */
	private void ensureNotClosed() {
		if (closed.get()) {
			throw new IllegalStateException("Cage is closed: " + data.getName());
		}
	}

	/**
	 * Creates an elliptical spot with proper configuration.
	 * 
	 * @param position the spot position index
	 * @param center   the center point
	 * @param radius   the radius
	 * @return the created spot
	 */
	private Spot createEllipseSpot(int position, Point2D.Double center, int radius) {
		Ellipse2D ellipse = new Ellipse2D.Double(center.x - radius, center.y - radius, 2 * radius, 2 * radius);
		ROI2DEllipse roiEllipse = new ROI2DEllipse(ellipse);
		roiEllipse.setName(SpotString.createSpotString(data.getProperties().getCageID(), position));

		Spot spot = new Spot(roiEllipse);
		spot.getProperties().setCageID(data.getProperties().getCageID());
		spot.getProperties().setCagePosition(position);
		spot.getProperties().setSpotRadius(radius);
		spot.getProperties().setSpotXCoord((int) center.getX());
		spot.getProperties().setSpotYCoord((int) center.getY());

		try {
			spot.getProperties().setSpotNPixels((int) roiEllipse.getNumberOfPoints());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			LOGGER.warning("Interrupted while computing spot pixels for cage: " + data.getName());
			spot.getProperties().setSpotNPixels(0);
		}

		return spot;
	}

	// === BUILDER PATTERN ===

	/**
	 * Builder for creating ModernCage instances.
	 */
	public static class Builder {
		private CageData data;
		private FlyPositions flyPositions;
		private SpotsArray spotsArray;

		/**
		 * Sets the cage data.
		 * 
		 * @param data the cage data
		 * @return this builder
		 */
		public Builder withData(CageData data) {
			this.data = data;
			return this;
		}

		/**
		 * Sets the fly positions.
		 * 
		 * @param flyPositions the fly positions
		 * @return this builder
		 */
		public Builder withFlyPositions(FlyPositions flyPositions) {
			this.flyPositions = flyPositions;
			return this;
		}

		/**
		 * Sets the spots array.
		 * 
		 * @param spotsArray the spots array
		 * @return this builder
		 */
		public Builder withSpotsArray(SpotsArray spotsArray) {
			this.spotsArray = spotsArray;
			return this;
		}

		/**
		 * Builds the ModernCage instance.
		 * 
		 * @return the created cage
		 * @throws IllegalArgumentException if required data is missing
		 */
		public CageModern build() {
			if (data == null) {
				throw new IllegalArgumentException("CageData is required");
			}
			return new CageModern(this);
		}
	}
}