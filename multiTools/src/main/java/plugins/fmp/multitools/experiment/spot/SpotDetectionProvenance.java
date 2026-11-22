package plugins.fmp.multitools.experiment.spot;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.results.EnumResults;

/** Settings captured when the current measurements for a spot family are saved. */
public final class SpotDetectionProvenance {
	public enum Family {
		FILTER("image_filter", false, EnumResults.FILTER_GREY, EnumResults.FILTER_GREY_FLY_MASKED,
				EnumResults.FILTER_GREY_SMOOTHED),
		FLY_MASK("fly_mask", false, EnumResults.FLY_PIXEL_COUNT),
		COLOR("color_distance", false, EnumResults.COLOR_GREY, EnumResults.COLOR_PIXEL_COUNT,
				EnumResults.COLOR_GREY_PRE_GATE, EnumResults.COLOR_GREY_SMOOTHED),
		KYMO_STRIP("kymograph_strip", true, EnumResults.KYMO_ROW_FRACTION, EnumResults.KYMO_ROW_FRACTION_CHANGE,
				EnumResults.KYMO_GREEN_HEIGHT, EnumResults.KYMO_GREEN_HEIGHT_RATIO),
		KYMO_CROSS("kymograph_cross", true, EnumResults.KYMO_CROSS_RATIO),
		KYMO_RIM("kymograph_rim", true, EnumResults.KYMO_RIM_RATIO);

		public final String id;
		private final EnumResults[] measures;
		private final boolean kymograph;

		Family(String id, boolean kymograph, EnumResults... measures) {
			this.id = id;
			this.measures = measures;
			this.kymograph = kymograph;
		}

		public boolean hasData(Spot spot) {
			if (spot == null) {
				return false;
			}
			for (EnumResults type : measures) {
				SpotMeasure measure = spot.getMeasurements(type);
				if (measure != null && measure.getCount() > 0) {
					return true;
				}
			}
			return false;
		}

		Path file(Experiment experiment) {
			String dir = kymograph ? experiment.getKymosBinFullDirectory() : experiment.getResultsDirectory();
			return dir == null ? null : Paths.get(dir).resolve("SpotDetection-" + id + ".properties");
		}
	}

	private SpotDetectionProvenance() {
	}

	public static void recordCamera(Experiment experiment, Family family, String method, BuildSeriesOptions options) {
		Properties settings = new Properties();
		put(settings, "first_ms", options.t_Ms_First, "last_ms", options.t_Ms_Last,
				"bin_ms", options.t_Ms_BinDuration, "spot_threshold", options.spotThreshold,
				"spot_threshold_above", options.spotThresholdUp, "spot_transform", options.transform01,
				"fly_threshold", options.flyThreshold, "fly_threshold_above", options.flyThresholdUp,
				"fly_transform", options.transform02, "fly_occupancy_percent", options.flyOccupancyPercentForSpotSumNoFly,
				"detection_mode", options.spotDetectionMode);
		if (family == Family.COLOR) {
			put(settings, "color_reference", options.spotColorReferenceList,
					"color_space", options.spotColorSpace, "color_distance_type", options.spotColorDistanceType,
					"color_distance_max", options.spotColorDistanceMax,
					"color_excluded", options.spotColorExcludeList,
					"color_exclude_distance_max", options.spotColorExcludeDistanceMax);
		}
		if (family == Family.COLOR) {
			put(settings, "fly_nan_dilation_bins", options.colorFlyNaNDilationBins,
					"fly_border_median_half_width_bins", options.colorFlyNaNBorderMedianHalfWidth,
					"fly_border_spike_ratio", options.colorFlyNaNBorderSpikeRatio,
					"clean_spike_median_half_width_bins", options.colorGreySumCleanSpikeMedianHalfWidth,
					"clean_spike_ratio", options.colorGreySumCleanSpikeRatio,
					"clean_spike_passes", options.colorGreySumCleanSpikePasses);
		}
		addRunMetadata(settings, method);
		save(experiment, family, settings);
		save(experiment, Family.FLY_MASK, settings);
	}

	public static void recordKymograph(Experiment experiment, Family family, String method, Object... parameters) {
		Properties settings = new Properties();
		put(settings, "first_ms", experiment.getKymoFirst_ms(), "last_ms", experiment.getKymoLast_ms(),
				"bin_ms", experiment.getKymoBin_ms(), "subsample_factor", experiment.getKymoSubsampleFactor());
		put(settings, parameters);
		addRunMetadata(settings, method);
		save(experiment, family, settings);
	}

	public static void recordCameraLine(Experiment experiment, Family family, String method, Object... parameters) {
		Properties settings = new Properties();
		put(settings, "sampling", "camera_frames", "first_ms", 0, "last_ms",
				Math.max(0, experiment.getCamImageLast_ms() - experiment.getCamImageFirst_ms()),
				"bin_ms", experiment.getCamImageBin_ms(), "subsample_factor", 1);
		put(settings, parameters);
		addRunMetadata(settings, method);
		save(experiment, family, settings);
	}

	private static void put(Properties settings, Object... parameters) {
		for (int i = 0; i < parameters.length; i += 2) {
			Object value = parameters[i + 1];
			if (value != null) {
				settings.setProperty("parameter." + parameters[i], value.toString());
			}
		}
	}

	private static void addRunMetadata(Properties settings, String method) {
		settings.setProperty("run_id", UUID.randomUUID().toString());
		settings.setProperty("recorded_at_utc", Instant.now().toString());
		settings.setProperty("method", method);
	}

	private static void save(Experiment experiment, Family family, Properties settings) {
		Path file = family.file(experiment);
		if (file == null) {
			return;
		}
		Path temp = null;
		try {
			temp = Files.createTempFile(file.getParent(), "spot-detection-", ".tmp");
			try (OutputStream out = Files.newOutputStream(temp)) {
				settings.store(out, null);
			}
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			Logger.warn("Could not record spot detection settings in " + file + ": " + e.getMessage());
		} finally {
			if (temp != null) {
				try {
					Files.deleteIfExists(temp);
				} catch (IOException e) {
					Logger.warn("Could not clean up temporary spot detection settings: " + e.getMessage());
				}
			}
		}
	}

	public static Properties load(Experiment experiment, Family family) throws IOException {
		Properties settings = new Properties();
		Path file = family.file(experiment);
		if (file != null && Files.isRegularFile(file)) {
			try (InputStream in = Files.newInputStream(file)) {
				settings.load(in);
			}
		}
		return settings;
	}
}
