package plugins.fmp.multitools.experiment;

/**
 * Describes how the measures stored in a given bin_xxx directory were produced.
 *
 * <p>
 * KYMOGRAPH: measures were computed by first building kymograph TIFF files
 * (typical multiCAFE workflow). The bin directory contains kymograph images
 * alongside XML/CSV measures.
 * </p>
 * <p>
 * DIRECT_FROM_STACK: measures were computed directly from the camera stack
 * without producing intermediate kymograph images (multiSPOTS96 workflow and
 * the lighter-weight multiCAFE path). The bin directory contains only
 * XML/CSV measures.
 * </p>
 * <p>
 * UNKNOWN: mode could not be determined (e.g. legacy directory with no
 * metadata and ambiguous content).
 * </p>
 */
public enum GenerationMode {
	KYMOGRAPH, DIRECT_FROM_STACK, UNKNOWN;

	public static GenerationMode fromString(String name) {
		if (name == null)
			return UNKNOWN;
		try {
			return GenerationMode.valueOf(name.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			return UNKNOWN;
		}
	}
}
