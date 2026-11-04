package plugins.fmp.multicafe.dlg.levels;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.service.CorrelationGulpPreview;
import plugins.fmp.multitools.service.IntakeDiagnostics;
import plugins.fmp.multitools.service.IntakeEpisodes;
import plugins.fmp.multitools.service.ManualIntakeEdits;

/** Review ZIP and CSV. Captured settings are written without changing measures. */
public final class IntakeReviewExport {
	final Experiment exp;
	final List<Capillary> caps;
	final boolean episodes, local;
	final Map<Capillary, Double> thresholds;
	final Map<Capillary, String> edits;
	final Map<Capillary, CorrelationGulpPreview> previews;

	IntakeReviewExport(Experiment exp, List<Capillary> caps, boolean episodes, boolean local,
			Map<Capillary, Double> thresholds, Map<Capillary, String> edits,
			Map<Capillary, CorrelationGulpPreview> previews) {
		this.exp = exp;
		this.caps = caps;
		this.episodes = episodes;
		this.local = local;
		this.thresholds = thresholds;
		this.edits = edits;
		this.previews = new IdentityHashMap<>(previews);
	}

	void write(Path destination) throws IOException {
		Path temp = Files.createTempFile(destination.toAbsolutePath().getParent(), "intake-review-", ".zip");
		try {
			try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(temp))) {
				StringBuilder summary = new StringBuilder("Intake review: " + exp.getResultsDirectory()
						+ "\nAll capillaries; current detector settings; includes unsaved manual event edits."
						+ "\nEach CSV compares automatic, chosen-threshold and edited intake. PNGs are original kymographs."
						+ "\nUnchanged bins are not assumed reviewed. No experiment measures were saved by this export.\n\n");
				for (Capillary cap : caps) {
					CorrelationGulpPreview p = previews.get(cap);
					if (p == null || p.localEvaporation != local || !p.matchesSources(exp))
						p = CorrelationGulpPreview.computeAccepted(exp, cap, local);
					previews.put(cap, p);
					double setting = thresholds.containsKey(cap) ? thresholds.get(cap)
							: Double.isFinite(p.noise.automatic) ? p.noise.automatic : 1;
					ManualIntakeEdits manual = ManualIntakeEdits.fromOptions(edits.get(cap), p.raw.length);
					String id = cap.getLast2ofCapillaryName().replaceAll("[^A-Za-z0-9_-]", "_");
					java.io.StringWriter text = new java.io.StringWriter();
					java.io.BufferedWriter writer = new java.io.BufferedWriter(text);
					writeReviewCsv(writer, exp, cap, p, setting, thresholds.containsKey(cap), episodes, local, manual);
					writer.flush();
					zip.putNextEntry(new java.util.zip.ZipEntry("correlation-" + id + ".csv"));
					zip.write(text.toString().getBytes(StandardCharsets.UTF_8));
					zip.closeEntry();
					zip.putNextEntry(new java.util.zip.ZipEntry("kymograph-" + id + ".png"));
					javax.imageio.ImageIO.write(p.image, "png", zip);
					zip.closeEntry();
					summary.append(id).append(cap.getNFlies() == 0 ? " (no-fly control)" : "")
							.append(manual.isEmpty() ? ": no manual event overrides\n"
									: ": manual event overrides included\n");
				}
				for (CorrelationGulpPreview p : previews.values())
					if (!p.matchesSources(exp))
						throw new IOException("Levels changed during export; export the review again.");
				zip.putNextEntry(new java.util.zip.ZipEntry("README.txt"));
				zip.write(summary.toString().getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
			}
			Files.move(temp, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(temp);
		}
	}

	static void writeReviewCsv(BufferedWriter writer, Experiment experiment, Capillary computedCapillary,
			CorrelationGulpPreview preview, double threshold, boolean manual, boolean episodes, boolean local,
			ManualIntakeEdits edits) throws IOException {
		AutomaticIntakeResult detected = new AutomaticIntakeResult(preview, episodes, local, threshold);
		double[] beforeEdits = detected.amplitudes;
		double[] amplitudes = beforeEdits.clone();
		double[] bottoms = detected.bottoms == null ? preview.raw.clone() : detected.bottoms.clone();
		edits.apply(amplitudes, bottoms);
		boolean[] selected = new boolean[amplitudes.length];
		for (int i = 1; i < selected.length; i++)
			selected[i] = amplitudes[i] > 0;
		List<IntakeDiagnostics.Warning> warnings = episodes ? IntakeDiagnostics.analyze(preview.raw,
				preview.correctedLevels, preview.evaporation, preview.minutes, preview.motion.reliable,
				IntakeEpisodes.detect(preview.residualRate, preview.minutes, preview.motion.reliable,
						preview.controlRates, preview.noise.automatic, threshold, preview.correctedLevels,
						preview::supportsIntakeStep),
				preview.controlRates, threshold, computedCapillary.getNFlies() == 0)
				: java.util.Collections.emptyList();
		double[] automaticAmplitudes = Double.isFinite(preview.noise.automatic)
				? new AutomaticIntakeResult(preview, episodes, local).amplitudes
				: null;
		writer.write("# prototype=accepted_level_intake;capillary=" + computedCapillary.getLast2ofCapillaryName()
				+ ";source=accepted_topraw\n");
		writer.write("# experiment=" + experiment.getResultsDirectory() + ";reference=" + preview.referenceDescription
				+ ";method_options=" + IntakeEpisodes.METHOD_VERSION + ",source=accepted_topraw,persistent=" + episodes
				+ ",local=" + local + edits.options() + "\n");
		writer.write("# threshold_source=" + preview.noise.source + ";manual=" + manual
				+ ";automatic_threshold_px_per_min=" + preview.noise.automatic + "\n");
		writer.write("# comparison=detector recomputed from current accepted levels and method settings;"
				+ "edit_action compares chosen threshold before/after event edits;"
				+ "automatic_net_intake_px uses automatic threshold (NaN if unavailable);"
				+ "unchanged does not imply user reviewed\n");
		writer.write(
				"frame;elapsed_min;raw_px;refined_px;shift_px;evaporation_increment_px;excess_px_per_min;correlation;reliable;reason;automatic_threshold_px_per_min;chosen_threshold_px_per_min;candidate;candidate_amplitude_px;searched_range_px;fitted_meniscus_px;intake_top_px;intake_bottom_px;diagnostic_excess_px;diagnostic_reliable;diagnostic_types;image_profile;topraw_fallback;automatic_net_intake_px;detector_net_before_edits_px;edited_net_intake_px;manual_override;edit_action\n");
		for (int i = 0; i < preview.raw.length; i++) {
			double evap = i == 0 ? Double.NaN : preview.evaporation[i] - preview.evaporation[i - 1];
			String change = !edits.covers(i) ? "unchanged"
					: !(beforeEdits[i] > 0) && amplitudes[i] > 0 ? "added"
							: beforeEdits[i] > 0 && !(amplitudes[i] > 0) ? "removed"
									: Math.abs(amplitudes[i] - beforeEdits[i]) > 1e-4 ? "resized" : "manual_override";
			String gapAmount = "", gapReliable = "", diagnosticTypes = "";
			if (episodes)
				for (IntakeDiagnostics.Warning gap : warnings)
					if (gap.last == i) {
						String separator = diagnosticTypes.isEmpty() ? "" : "|";
						gapAmount += separator + gap.excess;
						gapReliable += separator + gap.reliable;
						diagnosticTypes += separator + (gap.noFly ? "CONTROL_" : "") + gap.kind.name();
					}
			writer.write(i + ";" + preview.minutes[i] + ";" + preview.raw[i] + ";" + preview.motion.refined[i] + ";"
					+ preview.motion.shift[i] + ";" + evap + ";" + preview.residualRate[i] + ";"
					+ preview.motion.correlation[i] + ";" + preview.motion.reliable[i] + ";" + "\""
					+ preview.motion.reason[i].replace("\"", "\"\"") + "\";" + preview.noise.automatic + ";" + threshold
					+ ";" + selected[i] + ";" + amplitudes[i] + ";" + preview.motion.searchRange[i] + ";" + bottoms[i]
					+ ";" + (selected[i] ? bottoms[i] - amplitudes[i] : Double.NaN) + ";"
					+ (selected[i] ? bottoms[i] : Double.NaN) + ";" + gapAmount + ";" + gapReliable + ";"
					+ diagnosticTypes + ";" + preview.motion.profile[i] + ";" + preview.motion.toprawFallback[i] + ";"
					+ (automaticAmplitudes == null ? Double.NaN : netIntake(automaticAmplitudes[i], evap)) + ";"
					+ netIntake(beforeEdits[i], evap) + ";" + netIntake(amplitudes[i], evap) + ";" + edits.covers(i)
					+ ";" + change + "\n");
		}
	}

	private static double netIntake(double amplitude, double evaporation) {
		return amplitude > 0 ? Math.max(0, amplitude - evaporation) : 0;
	}
}
