package plugins.fmp.multiSPOTS96.experiment.cages;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import icy.roi.ROI2D;
import plugins.fmp.multiSPOTS96.experiment.sequence.SequenceCamData;

/**
 * Modern, thread-safe implementation of cages array management with clean code
 * practices.
 * 
 * <p>
 * This class provides comprehensive functionality for managing collections of
 * cages:
 * <ul>
 * <li>Thread-safe operations with optimized performance</li>
 * <li>O(1) lookups using indexed access</li>
 * <li>Structured error handling and detailed results</li>
 * <li>Configuration-driven behavior</li>
 * <li>Proper resource management</li>
 * <li>Rich information access and validation</li>
 * </ul>
 * 
 * <p>
 * Usage example:
 * 
 * <pre>{@code
 * ModernCagesArray cagesArray = ModernCagesArray.builder().withConfiguration(CagesArrayConfiguration.highPerformance())
 * 		.build();
 * 
 * CageOperationResult result = cagesArray.addCage(cage);
 * if (result.isSuccess()) {
 * 	System.out.println("Added cage successfully");
 * }
 * 
 * // Fast O(1) lookups
 * Optional<ModernCage> cage = cagesArray.findCageById(42);
 * }</pre>
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 * @since 2.3.3
 */
public final class CagesArrayModern implements AutoCloseable {
	private static final Logger LOGGER = Logger.getLogger(CagesArrayModern.class.getName());

	// === THREAD-SAFE COLLECTIONS ===
	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	private final List<CageModern> cages = new CopyOnWriteArrayList<>();
	private final Map<Integer, CageModern> cagesByID = new ConcurrentHashMap<>();
	private final Map<String, CageModern> cagesByName = new ConcurrentHashMap<>();

	// === CONFIGURATION ===
	private final CagesArrayConfiguration configuration;
	private volatile boolean closed = false;

	// === CONSTRUCTORS ===

	/**
	 * Creates a new ModernCagesArray with the specified configuration.
	 * 
	 * @param configuration the configuration to use
	 * @throws IllegalArgumentException if configuration is null
	 */
	public CagesArrayModern(CagesArrayConfiguration configuration) {
		this.configuration = Objects.requireNonNull(configuration, "Configuration cannot be null");
	}

	/**
	 * Creates a builder for constructing ModernCagesArray instances.
	 * 
	 * @return a new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	// === INFORMATION ACCESS ===

	/**
	 * Gets comprehensive information about this cages array.
	 * 
	 * @return detailed cages array information
	 * @throws IllegalStateException if the array is closed
	 */
	public CagesArrayProperties getArrayProperties() {
		ensureNotClosed();
		lock.readLock().lock();
		try {
			List<String> cageNames = cages.stream().map(cage -> cage.getData().getName()).collect(Collectors.toList());
			List<String> cageSex = cages.stream().map(cage -> cage.getData().getProperties().getFlySex())
					.collect(Collectors.toList());
			List<String> cageStrain = cages.stream().map(cage -> cage.getData().getProperties().getFlyStrain())
					.collect(Collectors.toList());
//			List<Integer> cageAge = cages.stream().map(cage -> cage.getData().getProperties().getFlyAge())
//					.collect(Collectors.toList());
//			List<String> spotStimulus = cages.stream().map(cage -> cage.getSpotsArray().getSpotStimulus())
//					.collect(Collectors.toList());
//			List<String> spotConcentration = cages.stream().map(cage -> cage.getData().geSpotConcentration())
//					.collect(Collectors.toList());
//			List<String> spotVolume = cages.stream().map(cage -> cage.getData().getSpotVolume())
//					.collect(Collectors.toList());

			long validCages = cages.stream().filter(cage -> cage.getData().isValid()).count();

			long activeCages = cages.stream().filter(cage -> cage.getData().isDetectEnabled()).count();

			long cagesWithSpots = cages.stream().filter(cage -> cage.getSpotsArray().getSpotsList().size() > 0).count();

			return CagesArrayProperties.builder() //
					.totalCages(cages.size()) //
					.validCages((int) validCages) //
					.activeCages((int) activeCages) //
					.cagesWithSpots((int) cagesWithSpots) //
					.gridSize(configuration.getNTotalCages()) //
					.cageNames(cageNames) //
					.cageSex(cageSex) //
					.cageStrain(cageStrain) //
//					.cageAge(cageAge) //
					.build();
		} finally {
			lock.readLock().unlock();
		}
	}

	public List<CageModern> getCages() {
		ensureNotClosed();
		return Collections.unmodifiableList(new ArrayList<>(cages));
	}

	// === CAGE MANAGEMENT ===

	/**
	 * Adds a cage to the array with validation and duplicate checking.
	 * 
	 * @param cage the cage to add
	 * @return detailed operation result
	 * @throws IllegalStateException if the array is closed
	 */
	public CageOperationResult addCage(CageModern cage) {
		if (cage == null) {
			return CageOperationResult.failure("ADD_CAGE", new IllegalArgumentException("Cage cannot be null"),
					"Cannot add null cage");
		}

		ensureNotClosed();
		lock.writeLock().lock();
		try {
			long startTime = System.currentTimeMillis();

			// Validate cage if configured
			if (configuration.isValidateInputs()) {
				CageOperationResult validation = cage.validateCage();
				if (!validation.isSuccess()) {
					return CageOperationResult.failure("ADD_CAGE",
							validation.getError().orElse(new IllegalArgumentException("Invalid cage")),
							"Cage validation failed: " + validation.getMessage());
				}
			}

			String cageName = cage.getData().getName();
			int cageID = cage.getData().getProperties().getCageID();

			// Check for duplicates
			if (cagesByID.containsKey(cageID)) {
				return CageOperationResult.failure("ADD_CAGE", new IllegalStateException("Duplicate cage ID"),
						"Cage with ID " + cageID + " already exists");
			}

			if (cagesByName.containsKey(cageName)) {
				return CageOperationResult.failure("ADD_CAGE", new IllegalStateException("Duplicate cage name"),
						"Cage with name '" + cageName + "' already exists");
			}

			// Add cage
			cages.add(cage);
			cagesByID.put(cageID, cage);
			cagesByName.put(cageName, cage);

			long processingTime = System.currentTimeMillis() - startTime;

			return CageOperationResult.success("ADD_CAGE", "Successfully added cage: " + cageName).toBuilder()
					.processingTimeMs(processingTime).addMetadata("totalCages", cages.size())
					.addMetadata("cageID", cageID).addMetadata("cageName", cageName).build();

		} finally {
			lock.writeLock().unlock();
		}
	}

	/**
	 * Removes a cage by ID with proper cleanup.
	 * 
	 * @param cageID the ID of the cage to remove
	 * @return operation result
	 * @throws IllegalStateException if the array is closed
	 */
	public CageOperationResult removeCageById(int cageID) {
		ensureNotClosed();
		lock.writeLock().lock();
		try {
			long startTime = System.currentTimeMillis();

			CageModern cage = cagesByID.remove(cageID);
			if (cage != null) {
				cages.remove(cage);
				cagesByName.remove(cage.getData().getName());

				// Close the cage to free resources
				try {
					cage.close();
				} catch (Exception e) {
					LOGGER.warning("Error closing cage during removal: " + e.getMessage());
				}

				long processingTime = System.currentTimeMillis() - startTime;

				return CageOperationResult
						.success("REMOVE_CAGE", "Successfully removed cage: " + cage.getData().getName()).toBuilder()
						.processingTimeMs(processingTime).addMetadata("totalCages", cages.size()).build();
			} else {
				return CageOperationResult.failure("REMOVE_CAGE", new IllegalArgumentException("Cage not found"),
						"No cage found with ID: " + cageID);
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	// === OPTIMIZED LOOKUPS ===

	/**
	 * Finds a cage by ID with O(1) performance.
	 * 
	 * @param cageID the cage ID to search for
	 * @return optional containing the cage if found
	 * @throws IllegalStateException if the array is closed
	 */
	public Optional<CageModern> findCageById(int cageID) {
		ensureNotClosed();
		return Optional.ofNullable(cagesByID.get(cageID));
	}

	/**
	 * Finds a cage by name with O(1) performance.
	 * 
	 * @param name the cage name to search for
	 * @return optional containing the cage if found
	 * @throws IllegalArgumentException if name is null or empty
	 * @throws IllegalStateException    if the array is closed
	 */
	public Optional<CageModern> findCageByName(String name) {
		if (name == null || name.trim().isEmpty()) {
			throw new IllegalArgumentException("Name cannot be null or empty");
		}
		ensureNotClosed();
		return Optional.ofNullable(cagesByName.get(name));
	}

	/**
	 * Finds cages within a specified rectangular region.
	 * 
	 * @param region the region to search within
	 * @return list of cages within the region
	 * @throws IllegalArgumentException if region is null
	 * @throws IllegalStateException    if the array is closed
	 */
	public List<CageModern> findCagesInRegion(Rectangle2D region) {
		if (region == null) {
			throw new IllegalArgumentException("Region cannot be null");
		}

		ensureNotClosed();
		lock.readLock().lock();
		try {
			return cages.stream().filter(cage -> region.intersects(cage.getData().getBounds()))
					.collect(Collectors.toList());
		} finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * Finds all valid cages.
	 * 
	 * @return list of valid cages
	 * @throws IllegalStateException if the array is closed
	 */
	public List<CageModern> findValidCages() {
		ensureNotClosed();
		lock.readLock().lock();
		try {
			return cages.stream().filter(cage -> cage.getData().isValid()).collect(Collectors.toList());
		} finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * Finds all active cages (detection enabled).
	 * 
	 * @return list of active cages
	 * @throws IllegalStateException if the array is closed
	 */
	public List<CageModern> findActiveCages() {
		ensureNotClosed();
		lock.readLock().lock();
		try {
			return cages.stream().filter(cage -> cage.getData().isDetectEnabled()).collect(Collectors.toList());
		} finally {
			lock.readLock().unlock();
		}
	}

	// === BULK OPERATIONS ===

	/**
	 * Merges cages from another array with duplicate handling.
	 * 
	 * @param otherArray the array to merge from
	 * @return detailed operation result
	 * @throws IllegalArgumentException if otherArray is null
	 * @throws IllegalStateException    if the array is closed
	 */
	public CageOperationResult mergeCages(CagesArrayModern otherArray) {
		if (otherArray == null) {
			return CageOperationResult.failure("MERGE_CAGES",
					new IllegalArgumentException("Other array cannot be null"), "Cannot merge with null array");
		}

		ensureNotClosed();
		lock.writeLock().lock();
		try {
			long startTime = System.currentTimeMillis();

			int addedCount = 0;
			int skippedCount = 0;
			List<String> addedCages = new ArrayList<>();
			List<String> skippedCages = new ArrayList<>();

			for (CageModern cage : otherArray.getCages()) {
				String cageName = cage.getData().getName();
				int cageID = cage.getData().getProperties().getCageID();

				if (!cagesByID.containsKey(cageID) && !cagesByName.containsKey(cageName)) {
					cages.add(cage);
					cagesByID.put(cageID, cage);
					cagesByName.put(cageName, cage);
					addedCount++;
					addedCages.add(cageName);
				} else {
					skippedCount++;
					skippedCages.add(cageName + " (duplicate)");
				}
			}

			long processingTime = System.currentTimeMillis() - startTime;

			return CageOperationResult.builder().success(addedCount > 0).operationType("MERGE_CAGES")
					.message(String.format("Merged %d cages, skipped %d duplicates", addedCount, skippedCount))
					.processingTimeMs(processingTime).addMetadata("addedCount", addedCount)
					.addMetadata("skippedCount", skippedCount).addMetadata("addedCages", addedCages)
					.addMetadata("skippedCages", skippedCages).build();

		} finally {
			lock.writeLock().unlock();
		}
	}

	/**
	 * Clears all cages with proper resource cleanup.
	 * 
	 * @return operation result
	 * @throws IllegalStateException if the array is closed
	 */
	public CageOperationResult clearAllCages() {
		ensureNotClosed();
		lock.writeLock().lock();
		try {
			long startTime = System.currentTimeMillis();
			int removedCount = cages.size();

			// Close all cages to free resources
			for (CageModern cage : cages) {
				try {
					cage.close();
				} catch (Exception e) {
					LOGGER.warning("Error closing cage during clear: " + e.getMessage());
				}
			}

			cages.clear();
			cagesByID.clear();
			cagesByName.clear();

			long processingTime = System.currentTimeMillis() - startTime;

			return CageOperationResult.success("CLEAR_CAGES", "Successfully cleared all cages").toBuilder()
					.processingTimeMs(processingTime).addMetadata("removedCount", removedCount).build();

		} finally {
			lock.writeLock().unlock();
		}
	}

	// === INTEGRATION METHODS ===

	/**
	 * Transfers cages to a sequence as ROIs.
	 * 
	 * @param seqCamData the sequence to transfer to
	 * @return operation result
	 * @throws IllegalArgumentException if seqCamData is null
	 * @throws IllegalStateException    if the array is closed
	 */
	public CageOperationResult transferCagesToSequence(SequenceCamData seqCamData) {
		if (seqCamData == null) {
			return CageOperationResult.failure("TRANSFER_TO_SEQUENCE",
					new IllegalArgumentException("SequenceCamData cannot be null"), "Cannot transfer to null sequence");
		}

		ensureNotClosed();
		lock.readLock().lock();
		try {
			long startTime = System.currentTimeMillis();

			List<ROI2D> cageROIList = new ArrayList<>(cages.size());
			for (CageModern cage : cages) {
				cageROIList.add(cage.getData().getRoi());
			}

			seqCamData.getSequence().addROIs(cageROIList, true);

			long processingTime = System.currentTimeMillis() - startTime;

			return CageOperationResult
					.success("TRANSFER_TO_SEQUENCE",
							"Successfully transferred " + cageROIList.size() + " cages to sequence")
					.toBuilder().processingTimeMs(processingTime).addMetadata("transferredCount", cageROIList.size())
					.build();

		} finally {
			lock.readLock().unlock();
		}
	}

	// === CONFIGURATION ACCESS ===

	/**
	 * Gets the current configuration.
	 * 
	 * @return the configuration
	 */
	public CagesArrayConfiguration getConfiguration() {
		return configuration;
	}

	// === LIFECYCLE ===

	/**
	 * Closes this cages array and releases all resources.
	 */
	@Override
	public void close() {
		if (!closed) {
			lock.writeLock().lock();
			try {
				if (!closed) {
					// Close all cages
					for (CageModern cage : cages) {
						try {
							cage.close();
						} catch (Exception e) {
							LOGGER.warning("Error closing cage: " + cage.getData().getName() + " - " + e.getMessage());
						}
					}

					cages.clear();
					cagesByID.clear();
					cagesByName.clear();
					closed = true;

//					LOGGER.info("ModernCagesArray closed successfully");
				}
			} finally {
				lock.writeLock().unlock();
			}
		}
	}

	@Override
	public String toString() {
		return String.format("ModernCagesArray{cages=%d, valid=%d, active=%d}", cages.size(), findValidCages().size(),
				findActiveCages().size());
	}

	// === PRIVATE HELPER METHODS ===

	/**
	 * Ensures this cages array is not closed.
	 * 
	 * @throws IllegalStateException if the array is closed
	 */
	private void ensureNotClosed() {
		if (closed) {
			throw new IllegalStateException("CagesArray is closed");
		}
	}

	// === BUILDER PATTERN ===

	/**
	 * Builder for creating ModernCagesArray instances.
	 */
	public static class Builder {
		private CagesArrayConfiguration configuration = CagesArrayConfiguration.defaultConfiguration();

		/**
		 * Sets the configuration.
		 * 
		 * @param configuration the configuration to use
		 * @return this builder
		 */
		public Builder withConfiguration(CagesArrayConfiguration configuration) {
			this.configuration = configuration;
			return this;
		}

		/**
		 * Builds the ModernCagesArray instance.
		 * 
		 * @return the created cages array
		 */
		public CagesArrayModern build() {
			return new CagesArrayModern(configuration);
		}
	}
}