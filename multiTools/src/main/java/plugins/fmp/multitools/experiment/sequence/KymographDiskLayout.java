package plugins.fmp.multitools.experiment.sequence;

import java.util.List;

import plugins.fmp.multitools.experiment.cages.Cages;
import plugins.fmp.multitools.experiment.spots.Spots;

/**
 * Discovers on-disk kymograph frame files for a given experiment layout (per-spot TIFF names vs
 * one stacked TIFF per cage). Implementations are stateless; callers own synchronization if needed.
 */
public interface KymographDiskLayout {

	/**
	 * @param baseDirectory directory ending with or without a trailing separator (implementations normalize)
	 * @param cages         non-null cages collection
	 * @param allSpots      may be null for layouts that do not use spots (e.g. cage stack list)
	 */
	List<ImageFileData> listImageDescriptors(String baseDirectory, Cages cages, Spots allSpots);
}
