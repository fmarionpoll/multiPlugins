package plugins.fmp.multitools.experiment.capillaries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.series.options.BuildSeriesOptions.GulpDetectionMethod;
import plugins.fmp.multitools.series.options.GulpThresholdMethod;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;

public final class DetectionProvenanceSupport {

	public static final String FLY_METHOD_DETECT1 = "detect1";
	public static final String FLY_METHOD_DETECT2 = "detect2";
	public static final String COL_LEVEL_PASS1 = "level_pass1";
	public static final String COL_LEVEL_TRANSFORM1 = "level_transform1";
	public static final String COL_LEVEL_THRESHOLD1 = "level_threshold1";
	public static final String COL_LEVEL_DIRECTION_UP1 = "level_direction_up1";
	public static final String COL_LEVEL_PASS2 = "level_pass2";
	public static final String COL_LEVEL_TRANSFORM2 = "level_transform2";
	public static final String COL_LEVEL_THRESHOLD2 = "level_threshold2";
	public static final String COL_LEVEL_DIRECTION_UP2 = "level_direction_up2";
	public static final String COL_LEVEL_JITTER2 = "level_jitter2";
	public static final String COL_LEVEL_SOURCE = "level_source";
	public static final String COL_GULP_METHOD = "gulp_method";
	public static final String COL_GULP_TRANSFORM = "gulp_transform";
	public static final String COL_GULP_SPAN_DIFF = "gulp_span_diff";
	public static final String COL_GULP_THRESHOLD_METHOD = "gulp_threshold_method";
	public static final String COL_GULP_THRESHOLD_K = "gulp_threshold_k";
	public static final String COL_GULP_THRESHOLD_UL = "gulp_threshold_uL";
	public static final String COL_MULTICAFE_VERSION = "multicafe_version";
	public static final String COL_MULTITOOLS_VERSION = "multitools_version";
	public static final String COL_FLY_DETECT_METHOD = "fly_detect_method";
	public static final String COL_FLY1_SOURCE_TRANSFORM = "fly1_source_transform";
	public static final String COL_FLY1_BACKGROUND_TRANSFORM = "fly1_background_transform";
	public static final String COL_FLY1_THRESHOLD = "fly1_threshold";
	public static final String COL_FLY1_TRACK_WHITE = "fly1_track_white";
	public static final String COL_FLY2_THRESHOLD_DIFF = "fly2_threshold_diff";
	public static final String COL_FLY2_DUAL_BACKGROUND = "fly2_dual_background";
	public static final String COL_FLY2_REDNESS_THRESHOLD = "fly2_redness_threshold";
	public static final String COL_FLY2_BACKGROUND_N_FRAMES = "fly2_background_n_frames";
	public static final String COL_FLY2_BACKGROUND_FIRST = "fly2_background_first";
	public static final String COL_FLY2_BACKGROUND_THRESHOLD = "fly2_background_threshold";

	public static final List<String> CAPILLARY_PROVENANCE_COLUMNS = Arrays.asList(COL_LEVEL_PASS1,
			COL_LEVEL_TRANSFORM1, COL_LEVEL_THRESHOLD1, COL_LEVEL_DIRECTION_UP1, COL_LEVEL_PASS2,
			COL_LEVEL_TRANSFORM2, COL_LEVEL_THRESHOLD2, COL_LEVEL_DIRECTION_UP2, COL_LEVEL_JITTER2, COL_LEVEL_SOURCE,
			COL_GULP_METHOD, COL_GULP_TRANSFORM, COL_GULP_SPAN_DIFF, COL_GULP_THRESHOLD_METHOD, COL_GULP_THRESHOLD_K,
			COL_GULP_THRESHOLD_UL);

	public static final List<String> IDEXPT_PROVENANCE_COLUMNS = buildIdexptColumns();

	private DetectionProvenanceSupport() {
	}

	private static List<String> buildIdexptColumns() {
		List<String> cols = new ArrayList<>();
		cols.add(COL_MULTICAFE_VERSION);
		cols.add(COL_MULTITOOLS_VERSION);
		cols.addAll(CAPILLARY_PROVENANCE_COLUMNS);
		cols.add(COL_FLY_DETECT_METHOD);
		cols.add(COL_FLY1_SOURCE_TRANSFORM);
		cols.add(COL_FLY1_BACKGROUND_TRANSFORM);
		cols.add(COL_FLY1_THRESHOLD);
		cols.add(COL_FLY1_TRACK_WHITE);
		cols.add(COL_FLY2_THRESHOLD_DIFF);
		cols.add(COL_FLY2_DUAL_BACKGROUND);
		cols.add(COL_FLY2_REDNESS_THRESHOLD);
		cols.add(COL_FLY2_BACKGROUND_N_FRAMES);
		cols.add(COL_FLY2_BACKGROUND_FIRST);
		cols.add(COL_FLY2_BACKGROUND_THRESHOLD);
		return cols;
	}

	public static boolean isFullBatchLevelDetection(BuildSeriesOptions options) {
		return options != null && !options.detectSelectedKymo && !options.analyzePartOnly;
	}

	public static boolean isFullBatchGulpDetection(BuildSeriesOptions options) {
		return options != null && !options.detectSelectedKymo;
	}

	public static void copyLevelRecipeTo(BuildSeriesOptions dest, BuildSeriesOptions src) {
		if (dest == null || src == null) {
			return;
		}
		dest.pass1 = src.pass1;
		dest.pass2 = src.pass2;
		dest.transform01 = src.transform01;
		dest.transform02 = src.transform02;
		dest.directionUp1 = src.directionUp1;
		dest.directionUp2 = src.directionUp2;
		dest.detectLevel1Threshold = src.detectLevel1Threshold;
		dest.detectLevel2Threshold = src.detectLevel2Threshold;
		dest.jitter2 = src.jitter2;
		dest.sourceCamDirect = src.sourceCamDirect;
		dest.detectTop = src.detectTop;
		dest.detectBottom = src.detectBottom;
		dest.detectL = src.detectL;
		dest.detectR = src.detectR;
		dest.transformBottom = src.transformBottom;
		dest.directionUpBottom = src.directionUpBottom;
		dest.detectLevelBottomThreshold = src.detectLevelBottomThreshold;
		dest.bottomSearchFromBottomPx = src.bottomSearchFromBottomPx;
	}

	public static void copyGulpRecipeTo(BuildSeriesOptions dest, BuildSeriesOptions src) {
		if (dest == null || src == null) {
			return;
		}
		dest.gulpDetectionMethod = src.gulpDetectionMethod;
		dest.transformForGulps = src.transformForGulps;
		dest.spanDiffForGulps = src.spanDiffForGulps;
		dest.thresholdMethod = src.thresholdMethod;
		dest.thresholdSdMultiplier = src.thresholdSdMultiplier;
		dest.detectGulpsThreshold_uL = src.detectGulpsThreshold_uL;
		dest.buildDerivative = src.buildDerivative;
		dest.buildGulps = src.buildGulps;
	}

	public static void copyFlyDetect1RecipeTo(BuildSeriesOptions dest, BuildSeriesOptions src) {
		if (dest == null || src == null) {
			return;
		}
		dest.flyDetectSourceTransform = src.flyDetectSourceTransform;
		dest.flyDetectBackgroundTransform = src.flyDetectBackgroundTransform;
		dest.threshold = src.threshold;
		dest.btrackWhite = src.btrackWhite;
		dest.blimitLow = src.blimitLow;
		dest.blimitUp = src.blimitUp;
		dest.blimitRatio = src.blimitRatio;
		dest.bjitter = src.bjitter;
		dest.limitLow = src.limitLow;
		dest.limitUp = src.limitUp;
		dest.limitRatio = src.limitRatio;
		dest.jitter = src.jitter;
		dest.nFliesPresent = src.nFliesPresent;
		dest.blimitMaxBlobsPerCage = src.blimitMaxBlobsPerCage;
	}

	public static void copyFlyDetect2RecipeTo(BuildSeriesOptions dest, BuildSeriesOptions src) {
		if (dest == null || src == null) {
			return;
		}
		dest.thresholdDiff = src.thresholdDiff;
		dest.dualBackground = src.dualBackground;
		dest.rednessThreshold = src.rednessThreshold;
		dest.backgroundNFrames = src.backgroundNFrames;
		dest.backgroundFirst = src.backgroundFirst;
		dest.backgroundThreshold = src.backgroundThreshold;
		dest.detectFlies = src.detectFlies;
		dest.detectIllumPhase = src.detectIllumPhase;
		copyFlyDetect1RecipeTo(dest, src);
	}

	public static void appendCapillaryProvenanceColumns(List<String> row, BuildSeriesOptions opts) {
		if (row == null || opts == null) {
			return;
		}
		row.add(Boolean.toString(opts.pass1));
		row.add(transformName(opts.transform01));
		row.add(Integer.toString(opts.detectLevel1Threshold));
		row.add(Boolean.toString(opts.directionUp1));
		row.add(Boolean.toString(opts.pass2));
		row.add(transformName(opts.transform02));
		row.add(Integer.toString(opts.detectLevel2Threshold));
		row.add(Boolean.toString(opts.directionUp2));
		row.add(Integer.toString(opts.jitter2));
		row.add(opts.sourceCamDirect ? "cam" : "kymo");
		row.add(opts.gulpDetectionMethod != null ? opts.gulpDetectionMethod.name() : "");
		row.add(transformName(opts.transformForGulps));
		row.add(Integer.toString(opts.spanDiffForGulps));
		row.add(opts.thresholdMethod != null ? opts.thresholdMethod.name() : "");
		row.add(Double.toString(opts.thresholdSdMultiplier));
		row.add(Double.toString(opts.detectGulpsThreshold_uL));
	}

	public static void importCapillaryProvenance(Capillary cap, String[] data, int startIndex) {
		if (cap == null || data == null || startIndex < 0 || startIndex >= data.length) {
			return;
		}
		BuildSeriesOptions opts = cap.getProperties().getLimitsOptions();
		int i = startIndex;
		opts.pass1 = parseBoolean(data, i++, opts.pass1);
		opts.transform01 = parseTransform(data, i++, opts.transform01);
		opts.detectLevel1Threshold = parseInt(data, i++, opts.detectLevel1Threshold);
		opts.directionUp1 = parseBoolean(data, i++, opts.directionUp1);
		opts.pass2 = parseBoolean(data, i++, opts.pass2);
		opts.transform02 = parseTransform(data, i++, opts.transform02);
		opts.detectLevel2Threshold = parseInt(data, i++, opts.detectLevel2Threshold);
		opts.directionUp2 = parseBoolean(data, i++, opts.directionUp2);
		opts.jitter2 = parseInt(data, i++, opts.jitter2);
		if (i < data.length) {
			String source = data[i++];
			if (source != null && !source.isEmpty()) {
				opts.sourceCamDirect = "cam".equalsIgnoreCase(source.trim());
			}
		}
		if (i < data.length) {
			opts.gulpDetectionMethod = GulpDetectionMethod.fromXml(data[i++]);
		}
		opts.transformForGulps = parseTransform(data, i++, opts.transformForGulps);
		opts.spanDiffForGulps = parseInt(data, i++, opts.spanDiffForGulps);
		if (i < data.length && data[i] != null && !data[i].isEmpty()) {
			opts.thresholdMethod = parseGulpThresholdMethod(data[i++]);
		} else if (i < data.length) {
			i++;
		}
		opts.thresholdSdMultiplier = parseDouble(data, i++, opts.thresholdSdMultiplier);
		opts.detectGulpsThreshold_uL = parseDouble(data, i++, opts.detectGulpsThreshold_uL);
	}

	public static List<Object> idexptProvenanceValues(Experiment exp) {
		List<Object> values = new ArrayList<>();
		for (String col : IDEXPT_PROVENANCE_COLUMNS) {
			values.add(idexptValue(exp, col));
		}
		return values;
	}

	private static Object idexptValue(Experiment exp, String col) {
		if (exp == null) {
			return "";
		}
		BuildSeriesOptions level = exp.getLevelDetectionDefaults();
		BuildSeriesOptions gulp = exp.getGulpDetectionDefaults();
		BuildSeriesOptions fly1 = exp.getFlyDetect1Defaults();
		BuildSeriesOptions fly2 = exp.getFlyDetect2Defaults();
		switch (col) {
		case COL_MULTICAFE_VERSION:
			return nullToEmpty(Experiment.multiCafeVersionForExport());
		case COL_MULTITOOLS_VERSION:
			return nullToEmpty(Experiment.multiToolsVersion());
		case COL_LEVEL_PASS1:
			return level.pass1;
		case COL_LEVEL_TRANSFORM1:
			return transformName(level.transform01);
		case COL_LEVEL_THRESHOLD1:
			return level.detectLevel1Threshold;
		case COL_LEVEL_DIRECTION_UP1:
			return level.directionUp1;
		case COL_LEVEL_PASS2:
			return level.pass2;
		case COL_LEVEL_TRANSFORM2:
			return transformName(level.transform02);
		case COL_LEVEL_THRESHOLD2:
			return level.detectLevel2Threshold;
		case COL_LEVEL_DIRECTION_UP2:
			return level.directionUp2;
		case COL_LEVEL_JITTER2:
			return level.jitter2;
		case COL_LEVEL_SOURCE:
			return level.sourceCamDirect ? "cam" : "kymo";
		case COL_GULP_METHOD:
			return gulp.gulpDetectionMethod != null ? gulp.gulpDetectionMethod.name() : "";
		case COL_GULP_TRANSFORM:
			return transformName(gulp.transformForGulps);
		case COL_GULP_SPAN_DIFF:
			return gulp.spanDiffForGulps;
		case COL_GULP_THRESHOLD_METHOD:
			return gulp.thresholdMethod != null ? gulp.thresholdMethod.name() : "";
		case COL_GULP_THRESHOLD_K:
			return gulp.thresholdSdMultiplier;
		case COL_GULP_THRESHOLD_UL:
			return gulp.detectGulpsThreshold_uL;
		case COL_FLY_DETECT_METHOD:
			return nullToEmpty(exp.getLastFlyDetectMethod());
		case COL_FLY1_SOURCE_TRANSFORM:
			return transformName(fly1.flyDetectSourceTransform);
		case COL_FLY1_BACKGROUND_TRANSFORM:
			return transformName(fly1.flyDetectBackgroundTransform);
		case COL_FLY1_THRESHOLD:
			return fly1.threshold;
		case COL_FLY1_TRACK_WHITE:
			return fly1.btrackWhite;
		case COL_FLY2_THRESHOLD_DIFF:
			return fly2.thresholdDiff;
		case COL_FLY2_DUAL_BACKGROUND:
			return fly2.dualBackground;
		case COL_FLY2_REDNESS_THRESHOLD:
			return fly2.rednessThreshold;
		case COL_FLY2_BACKGROUND_N_FRAMES:
			return fly2.backgroundNFrames;
		case COL_FLY2_BACKGROUND_FIRST:
			return fly2.backgroundFirst;
		case COL_FLY2_BACKGROUND_THRESHOLD:
			return fly2.backgroundThreshold;
		default:
			return "";
		}
	}

	private static String transformName(ImageTransformEnums t) {
		return t != null ? t.toString() : "";
	}

	private static String nullToEmpty(String s) {
		return s != null ? s : "";
	}

	private static boolean parseBoolean(String[] data, int index, boolean defaultValue) {
		if (index >= data.length || data[index] == null || data[index].isEmpty()) {
			return defaultValue;
		}
		return Boolean.parseBoolean(data[index].trim());
	}

	private static int parseInt(String[] data, int index, int defaultValue) {
		if (index >= data.length || data[index] == null || data[index].isEmpty()) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(data[index].trim());
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	private static double parseDouble(String[] data, int index, double defaultValue) {
		if (index >= data.length || data[index] == null || data[index].isEmpty()) {
			return defaultValue;
		}
		try {
			return Double.parseDouble(data[index].trim());
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	private static ImageTransformEnums parseTransform(String[] data, int index, ImageTransformEnums defaultValue) {
		if (index >= data.length || data[index] == null || data[index].isEmpty()) {
			return defaultValue;
		}
		ImageTransformEnums t = ImageTransformEnums.findByText(data[index].trim());
		return t != null ? t : defaultValue;
	}

	private static GulpThresholdMethod parseGulpThresholdMethod(String value) {
		if (value == null || value.isEmpty()) {
			return GulpThresholdMethod.MEAN_PLUS_SD;
		}
		for (GulpThresholdMethod m : GulpThresholdMethod.values()) {
			if (m.name().equals(value.trim())) {
				return m;
			}
		}
		return GulpThresholdMethod.MEAN_PLUS_SD;
	}
}
