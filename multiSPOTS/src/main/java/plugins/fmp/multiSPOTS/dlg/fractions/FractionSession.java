package plugins.fmp.multiSPOTS.dlg.fractions;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionCalculator.Row;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.Key;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.Reference;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.Review;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.Settings;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMeasuredQuantity;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMeasurementMethod;

/**
 * Shared working-state operations for the fraction workspace. Calculation and
 * file formats stay in multiTools; this class only compares decisions, picks
 * raw chart samples, and chooses the primary directory.
 */
final class FractionSession {
	private FractionSession() {
	}

	/** Image methods stay in results. Only vertical measurements follow the kymograph bin. */
	static Path measurementsDirectory(Path results, SpotMeasurementMethod method, String bin) {
		if (results == null)
			throw new IllegalArgumentException("Experiment has no results directory");
		if (method == null || !method.isKymographDerived() || bin == null || bin.isEmpty())
			return results;
		return results.resolve(bin);
	}

	static SpotFractionPlan mergeSelection(SpotFractionPlan saved, SpotFractionPlan preview, Key key) {
		saved.settings.keySet().removeIf(k -> k.method == key.method && k.quantity.equals(key.quantity));
		saved.references.keySet().removeIf(k -> k.method == key.method && k.quantity.equals(key.quantity));
		saved.review.removeIf(r -> r.key.method == key.method && r.key.quantity.equals(key.quantity));
		preview.settings.forEach((k, v) -> {
			if (k.method == key.method && k.quantity.equals(key.quantity))
				saved.settings.put(k, v);
		});
		preview.references.forEach((k, v) -> {
			if (k.method == key.method && k.quantity.equals(key.quantity))
				saved.references.put(k, v);
		});
		for (Review review : preview.review)
			if (review.key.method == key.method && review.key.quantity.equals(key.quantity))
				saved.review.add(review);
		return saved;
	}

	static boolean sameSettings(Settings left, Settings right) {
		if (left == right)
			return true;
		if (left == null || right == null)
			return false;
		return left.similarGap == right.similarGap && left.changingGap == right.changingGap && left.tail == right.tail
				&& Double.compare(left.maximumGapSeconds, right.maximumGapSeconds) == 0
				&& left.toleranceMode == right.toleranceMode && Double.compare(left.tolerance, right.tolerance) == 0
				&& left.medianRadius == right.medianRadius && left.illumination == right.illumination
				&& left.referenceEstimation == right.referenceEstimation
				&& Double.compare(left.baselineStartSeconds, right.baselineStartSeconds) == 0
				&& Double.compare(left.baselineEndSeconds, right.baselineEndSeconds) == 0;
	}

	/** Settings, references and review items for one method and quantity. */
	static boolean sameSlice(SpotFractionPlan left, SpotFractionPlan right, Key selection) {
		if (left == null || right == null || selection == null)
			return false;
		return slice(left, selection).equals(slice(right, selection));
	}

	private static List<String> slice(SpotFractionPlan plan, Key selection) {
		List<String> lines = new ArrayList<>();
		for (Map.Entry<Key, Settings> entry : plan.settings.entrySet())
			if (matches(entry.getKey(), selection))
				lines.add("S\t" + entry.getKey().spot + "\t" + describe(entry.getValue()));
		for (Map.Entry<Key, Reference> entry : plan.references.entrySet())
			if (matches(entry.getKey(), selection))
				lines.add("R\t" + entry.getKey().spot + "\t" + describe(entry.getValue()));
		for (Review review : plan.review)
			if (matches(review.key, selection))
				lines.add("V\t" + review.key.spot + "\t" + review.reason);
		Collections.sort(lines);
		return lines;
	}

	private static boolean matches(Key key, Key selection) {
		return key.method == selection.method && key.quantity.equals(selection.quantity);
	}

	private static String describe(Settings settings) {
		return settings.similarGap + "|" + settings.changingGap + "|" + settings.tail + "|"
				+ settings.maximumGapSeconds + "|" + settings.toleranceMode + "|" + settings.tolerance + "|"
				+ settings.medianRadius + "|" + settings.illumination + "|" + settings.referenceEstimation + "|"
				+ settings.baselineStartSeconds + "|" + settings.baselineEndSeconds;
	}

	private static String describe(Reference reference) {
		return reference.kind + "|" + reference.value + "|" + reference.timeSeconds + "|" + reference.source + "|"
				+ reference.approved + "|" + reference.fingerprint + "|" + reference.donors;
	}

	static final class ChartPick {
		final double raw;
		final double timeSeconds;
		final String source;
		final int sample;

		ChartPick(double raw, double timeSeconds, String source, int sample) {
			this.raw = raw;
			this.timeSeconds = timeSeconds;
			this.source = source;
			this.sample = sample;
		}
	}

	/**
	 * A chart click supplies the original measurement. Plotted remaining,
	 * consumed, corrected and visually connected values are not references.
	 */
	static ChartPick chartPick(Row row, int sample) {
		if (row == null || row.input == null || row.input.result == null || sample < 0)
			return null;
		SpotMeasuredQuantity quantity = row.input.result.getQuantity(row.key.quantity);
		double[] raw = quantity.getRawValues();
		int[] flags = quantity.getEffectiveFlags();
		if (sample >= raw.length || flags[sample] != 0 || !Double.isFinite(raw[sample]) || raw[sample] <= 0)
			return null;
		double[] times = row.input.result.getTimesSeconds();
		return new ChartPick(raw[sample], times[sample], "User-selected valid observation at sample " + sample, sample);
	}

	static boolean occlusionIssue(String reason) {
		if (reason == null)
			return false;
		String text = reason.toLowerCase(java.util.Locale.ROOT);
		return text.contains("occlusion") || text.contains("gap") || text.contains("illumination")
				|| text.contains("noise") || text.contains("final") || text.contains("smoothing");
	}
}
