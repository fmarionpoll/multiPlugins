package plugins.fmp.multitools.experiment.timebase;

/**
 * Identifies which physical or logical grid a timestep (ms between samples)
 * refers to. See {@link TimestepResolver}.
 */
public enum MeasureTimebase {
	/** Per camera frame index; nominal step from acquisition / {@code TimeManager#binImage_ms}. */
	CAMERA_FRAME_STEP,
	/** Per kymograph column; {@code Experiment#getKymoBin_ms()} / bin description. */
	KYMO_COLUMN_STEP,
	/**
	 * Excel or user export resampling grid;
	 * {@link plugins.fmp.multitools.tools.results.ResultsOptions#buildExcelStepMs}.
	 */
	EXPORT_RESAMPLE_STEP,
	UNKNOWN
}
