package plugins.fmp.multitools.fmp_experiment.persistence;

/**
 * Common interface for all persistence operations in multiCAFE. Provides a
 * normalized pattern for loading and saving descriptions and measures.
 * 
 * <p>
 * All persistence classes should implement this interface to ensure consistent
 * behavior across Experiment, Cages, Capillaries, and Spots.
 * </p>
 * 
 * <p>
 * The strategy follows a transparent migration pattern: - Try to load from v2_
 * format first - Fallback to legacy formats if v2_ format not found - Always
 * save to v2_ format
 * </p>
 * 
 * @param <T> The entity type being persisted (e.g., Experiment, Cages,
 *            Capillaries, SpotsArray)
 */
public interface PersistenceStrategy<T> {

	/**
	 * Loads entity descriptions (metadata, ROIs, properties) from the results
	 * directory. Tries v2_ format first, then falls back to legacy formats.
	 * 
	 * @param entity           the entity to populate
	 * @param resultsDirectory the results directory containing description files
	 * @return true if successful
	 */
	boolean loadDescription(T entity, String resultsDirectory);

	/**
	 * Loads entity measures (time-series data) from the bin directory. Tries v2_
	 * format first, then falls back to legacy formats.
	 * 
	 * @param entity       the entity to populate
	 * @param binDirectory the bin directory (e.g., results/bin_60) containing
	 *                     measure files
	 * @return true if successful
	 */
	boolean loadMeasures(T entity, String binDirectory);

	/**
	 * Saves entity descriptions (metadata, ROIs, properties) to the results
	 * directory. Always saves to v2_ format.
	 * 
	 * @param entity           the entity to save
	 * @param resultsDirectory the results directory where description files should
	 *                         be saved
	 * @return true if successful
	 */
	boolean saveDescription(T entity, String resultsDirectory);

	/**
	 * Saves entity measures (time-series data) to the bin directory. Always saves
	 * to v2_ format.
	 * 
	 * @param entity       the entity to save
	 * @param binDirectory the bin directory (e.g., results/bin_60) where measure
	 *                     files should be saved
	 * @return true if successful
	 */
	boolean saveMeasures(T entity, String binDirectory);

	/**
	 * Migrates entity data from legacy format to v2_ format. This is called
	 * automatically when legacy data is detected.
	 * 
	 * @param entity           the entity to migrate
	 * @param resultsDirectory the results directory
	 * @param binDirectory     the bin directory (may be null if measures don't
	 *                         exist)
	 * @return true if migration was successful
	 */
	boolean migrateFromLegacy(T entity, String resultsDirectory, String binDirectory);
}
