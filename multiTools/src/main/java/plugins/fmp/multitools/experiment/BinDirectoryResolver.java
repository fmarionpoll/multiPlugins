package plugins.fmp.multitools.experiment;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Single source of truth for choosing which {@code results/bin_xxx}
 * subdirectory an experiment should read from or write into.
 * <p>
 * Replaces the historical patchwork of three independent policies in
 * {@code ExperimentDirectories.getBinSubDirectory...},
 * {@code Experiment.ensureBinDirectoryForLoading} and
 * {@code XLSExport.ensureBinDirectoryIsDefined}.
 *
 * <h3>Policy</h3>
 * <ol>
 * <li>If the detected camera interval and any existing {@code bin_xxx}
 * directory round to the same integer of seconds within a small tolerance
 * (&plusmn;1 s) <b>and</b> only one such match exists, auto-select it.</li>
 * <li>If several {@code bin_xxx} directories match the detected interval,
 * prefer the one that actually contains persisted measures
 * ({@code SpotsMeasures.csv} / {@code CagesMeasures.csv} /
 * {@code CapillariesMeasures.csv}) or a {@code BinDescription.xml};
 * tie-break by last-modified time.</li>
 * <li>If several {@code bin_xxx} directories exist but none matches, prompt
 * the user through a shared {@link ChooseAnalysisIntervalDialog} (with the
 * detected value pre-selected when possible).</li>
 * <li>If no {@code bin_xxx} directory exists yet, return the <b>logical</b>
 * name that would be used on save (derived from the detected interval if
 * known, else {@code null}). The directory is <b>not</b> created on disk.</li>
 * </ol>
 *
 * <p>
 * This class is stateless; the only persistent state is a user-preference
 * cache of the last chosen bin (used as a weak tie-break and for
 * remember-for-session semantics).
 */
public final class BinDirectoryResolver {

	private static final Preferences PREFS = Preferences.userNodeForPackage(BinDirectoryResolver.class);
	private static final String PREF_LAST_BIN_SUBDIR = "lastSelectedBinSubDirectory";

	/** Tolerance (seconds) for considering a bin_xxx directory a "match" of the detected interval. */
	private static final int MATCH_TOLERANCE_SEC = 1;

	/** Session cache for "Remember for this session" on the chooser dialog. */
	private static String rememberedBinForSession = null;

	private BinDirectoryResolver() {
	}

	/**
	 * Call when closing a series of experiments or when the user closes the
	 * application; clears the "remember for session" cache.
	 */
	public static void clearSessionRemembered() {
		rememberedBinForSession = null;
	}

	/**
	 * Context for a resolution. Fill in what's known; missing values are fine.
	 */
	public static final class Context {
		public String resultsDirectory;
		public long detectedIntervalMs = -1;
		public int nominalIntervalSec = -1;
		/** Optional previously selected bin name (e.g. from expListCombo.expListBinSubDirectory). */
		public String previouslySelected;
		/** Whether prompting the user is allowed (UI available, not running headless export loop). */
		public boolean allowPrompt = true;
		/** Optional parent component for dialogs. */
		public Component parentForDialog;
		/** When loading a series, skip prompting after the first decision. */
		public boolean useSessionRemembered = true;
	}

	/**
	 * Resolves the bin subdirectory to use. Returns {@code null} if nothing
	 * suitable exists and no target can be derived (e.g. empty results, no
	 * detection).
	 */
	public static String resolve(Context ctx) {
		if (ctx == null || ctx.resultsDirectory == null)
			return null;
		Path resultsPath = Paths.get(ctx.resultsDirectory);
		if (!Files.isDirectory(resultsPath))
			return deriveNameFromInterval(ctx);

		List<BinCandidate> candidates = scanCandidates(resultsPath);

		// Honor an explicit previously selected bin if it still exists.
		if (ctx.previouslySelected != null && containsName(candidates, ctx.previouslySelected)) {
			return ctx.previouslySelected;
		}

		// Honor session-remembered selection when asked (series of experiments).
		if (ctx.useSessionRemembered && rememberedBinForSession != null
				&& containsName(candidates, rememberedBinForSession)) {
			return rememberedBinForSession;
		}

		if (candidates.isEmpty()) {
			return deriveNameFromInterval(ctx);
		}

		int detectedSec = ctx.detectedIntervalMs > 0 ? (int) Math.round(ctx.detectedIntervalMs / 1000.0)
				: ctx.nominalIntervalSec;

		// Narrow to matches within tolerance of detected interval.
		List<BinCandidate> matches = new ArrayList<>();
		if (detectedSec > 0) {
			for (BinCandidate c : candidates) {
				if (Math.abs(c.seconds - detectedSec) <= MATCH_TOLERANCE_SEC) {
					matches.add(c);
				}
			}
		}

		if (matches.size() == 1) {
			return matches.get(0).name;
		}

		if (matches.size() > 1) {
			return pickBest(matches).name;
		}

		// No match within tolerance.
		if (candidates.size() == 1) {
			return candidates.get(0).name;
		}

		// Multiple non-matching bins: prompt if possible, else fall back to best.
		if (ctx.allowPrompt) {
			Integer detectedForDialog = detectedSec > 0 ? Integer.valueOf(detectedSec) : null;
			ChooseAnalysisIntervalDialog.Result r = ChooseAnalysisIntervalDialog.ask(ctx.parentForDialog, candidates,
					detectedForDialog, lastPreference());
			if (r != null && r.chosenBinName != null) {
				PREFS.put(PREF_LAST_BIN_SUBDIR, r.chosenBinName);
				if (r.rememberForSession) {
					rememberedBinForSession = r.chosenBinName;
				}
				return r.chosenBinName;
			}
		}

		// Headless / cancel: prefer the one with measures, else most recent.
		return pickBest(candidates).name;
	}

	/**
	 * Returns the directory name that would be used when saving, without
	 * creating anything on disk. Returns {@code null} when no interval is known
	 * (caller should defer until detection has run).
	 */
	public static String deriveNameFromInterval(Context ctx) {
		if (ctx == null)
			return null;
		if (ctx.nominalIntervalSec > 0)
			return Experiment.binDirectoryNameFromNominal(ctx.nominalIntervalSec);
		if (ctx.detectedIntervalMs > 0)
			return Experiment.binDirectoryNameFromMs(ctx.detectedIntervalMs);
		return null;
	}

	/**
	 * Scans {@code results/} for immediate children whose name matches
	 * {@code bin_<integer>}. Returns them sorted by numeric suffix (ascending).
	 */
	public static List<BinCandidate> scanCandidates(Path resultsPath) {
		List<BinCandidate> out = new ArrayList<>();
		if (resultsPath == null || !Files.isDirectory(resultsPath))
			return out;
		try {
			for (Path p : Files.newDirectoryStream(resultsPath)) {
				if (!Files.isDirectory(p))
					continue;
				String name = p.getFileName().toString();
				if (!name.startsWith(Experiment.BIN))
					continue;
				int sec;
				try {
					sec = Integer.parseInt(name.substring(Experiment.BIN.length()));
				} catch (NumberFormatException e) {
					continue;
				}
				if (sec <= 0)
					continue;
				BinCandidate c = new BinCandidate();
				c.name = name;
				c.seconds = sec;
				c.path = p;
				c.hasMeasures = hasAnyMeasuresFile(p);
				c.hasBinDescription = Files.exists(p.resolve(BinDescriptionPersistence.ID_V2_BINDESCRIPTION_XML));
				try {
					c.lastModifiedMs = Files.getLastModifiedTime(p).toMillis();
				} catch (IOException e) {
					c.lastModifiedMs = 0;
				}
				out.add(c);
			}
		} catch (IOException e) {
			// ignore
		}
		out.sort(Comparator.comparingInt((BinCandidate c) -> c.seconds));
		return out;
	}

	private static boolean hasAnyMeasuresFile(Path p) {
		return Files.exists(p.resolve("SpotsMeasures.csv")) || Files.exists(p.resolve("SpotsArrayMeasures.csv"))
				|| Files.exists(p.resolve("CagesMeasures.csv")) || Files.exists(p.resolve("CapillariesMeasures.csv"))
				|| Files.exists(p.resolve("CapillariesArrayMeasures.csv"));
	}

	private static BinCandidate pickBest(List<BinCandidate> list) {
		BinCandidate best = null;
		for (BinCandidate c : list) {
			if (best == null) {
				best = c;
				continue;
			}
			if (c.hasMeasures && !best.hasMeasures) {
				best = c;
			} else if (c.hasMeasures == best.hasMeasures) {
				if (c.hasBinDescription && !best.hasBinDescription) {
					best = c;
				} else if (c.hasBinDescription == best.hasBinDescription && c.lastModifiedMs > best.lastModifiedMs) {
					best = c;
				}
			}
		}
		return best;
	}

	private static boolean containsName(List<BinCandidate> list, String name) {
		if (name == null)
			return false;
		for (BinCandidate c : list) {
			if (name.equals(c.name))
				return true;
		}
		return false;
	}

	private static String lastPreference() {
		return PREFS.get(PREF_LAST_BIN_SUBDIR, null);
	}

	/** Simple descriptor of a {@code results/bin_xxx} candidate directory. */
	public static final class BinCandidate {
		public String name;
		public int seconds;
		public Path path;
		public boolean hasMeasures;
		public boolean hasBinDescription;
		public long lastModifiedMs;

		public File toFile() {
			return path != null ? path.toFile() : null;
		}
	}
}
