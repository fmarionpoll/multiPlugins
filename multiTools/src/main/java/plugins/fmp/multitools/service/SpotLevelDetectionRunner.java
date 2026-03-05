package plugins.fmp.multitools.service;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;

/**
 * Strategy interface for running spot-level detection from camera data.
 * Implementations may use single-threaded, CPU-parallel, or GPU-backed backends.
 */
public interface SpotLevelDetectionRunner {

	void detectSpots(Experiment exp, BuildSeriesOptions options);
}

