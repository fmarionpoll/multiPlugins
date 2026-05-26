package plugins.fmp.multitools.experiment;

/**
 * How kymograph frame files are laid out under the experiment's kymographs bin. Used to pick the
 * correct listing strategy without each plugin probing the filesystem. Additional kinds can be
 * added when new on-disk formats appear (e.g. HDF5-backed stacks).
 */
public enum KymographKind {

	/**
	 * One TIFF per capillary; paths come from {@link plugins.fmp.multitools.experiment.capillary.Capillary}
	 * kymograph names (multiCAFE-style, {@link plugins.fmp.multitools.service.KymographService}).
	 */
	CAPILLARY_MODEL_TIFF,

	/**
	 * One stacked TIFF per cage ({@code kymocage_<id>.tif*}, SPOTS96 / {@code CageSpotKymographBuilder}).
	 */
	CAGE_STACKED_TIFF,

	/**
	 * One TIFF per spot ROI name (legacy per-spot layout under the bin).
	 */
	SPOT_ROI_NAME_TIFF
}
