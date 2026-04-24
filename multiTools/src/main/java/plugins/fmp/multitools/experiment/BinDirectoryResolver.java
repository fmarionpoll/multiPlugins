package plugins.fmp.multitools.experiment;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Single source of truth for choosing which {@code results/bin_xxx}
 * subdirectory an experiment should read from or write into.
 *
 * <h3>Policy</h3>
 * <p>
 * Each candidate {@code bin_xxx} directory is annotated with a compression
 * <i>equivalence class</i> derived from its {@link BinDescription}
 * (loaded from {@code BinDescription.xml}, or inferred from the detected
 * camera interval and on-disk content if absent). The equivalence key is
 * the triplet {@code (roundedCameraSec, subsampleFactor, generationMode)}.
 * Near-duplicate directories (e.g. {@code bin_59} and {@code bin_60} both
 * coming from a 60 s raw full-resolution kymograph run) share the same key
 * and are treated together.
 * </p>
 * <ol>
 * <li>Restrict to the class matching the current recording (camera interval
 * + declared generation mode, if any).</li>
 * <li>Within that class: if exactly one directory contains measures, adopt
 * it and rename the empty peers in place to
 * {@code deleted_<name>} (see {@link BinDirectoryScanUtils#DELETED_PREFIX}).</li>
 * <li>If several directories in that class contain measures, prompt via
 * {@link ChooseAnalysisIntervalDialog}.</li>
 * <li>If no directory in that class contains measures, pick the newest.</li>
 * <li>Classes outside the matching one remain on disk and are still
 * discoverable via the dialog.</li>
 * </ol>
 *
 * <p>
 * This class keeps only a session remember-me for series-of-experiments UX
 * and a user-preference "last choice" for tie-breaking. It does not mutate
 * on-disk state except for the opt-in cleanup rename.
 * </p>
 */
public final class BinDirectoryResolver {

	private static final Preferences PREFS = Preferences.userNodeForPackage(BinDirectoryResolver.class);
	private static final String PREF_LAST_BIN_SUBDIR = "lastSelectedBinSubDirectory";
	/** Controls the empty-equivalent rename cleanup: {@code rename} (default) or {@code off}. */
	public static final String PREF_CLEANUP_MODE = "binAutoCleanupMode";
	/** First-run info dialog gate. */
	public static final String PREF_CLEANUP_INFO_SHOWN = "binAutoCleanupInfoShown";

	/** Tolerance (seconds) for considering two camera intervals to be the same class. */
	private static final int CAM_TOLERANCE_SEC = 1;

	private static String rememberedBinForSession = null;

	/** Kymograph flip-flop folders: {@code bin_60_r1}, {@code bin_60_r2}, … */
	private static final Pattern BIN_WITH_FLIP_REVISION = Pattern.compile("^bin_(\\d+)_r(\\d+)$");

	private BinDirectoryResolver() {
	}

	public static void clearSessionRemembered() {
		rememberedBinForSession = null;
	}

	public static final class Context {
		public String resultsDirectory;
		public long detectedIntervalMs = -1;
		public int nominalIntervalSec = -1;
		/** Declared generation mode of the current run (optional). UNKNOWN = ignore when matching classes. */
		public GenerationMode generationMode = GenerationMode.UNKNOWN;
		public String previouslySelected;
		public boolean allowPrompt = true;
		public Component parentForDialog;
		public boolean useSessionRemembered = true;
		/** Whether the resolver is allowed to rename empty equivalents. */
		public boolean allowCleanup = true;
	}

	/**
	 * Resolves the bin subdirectory to use. Returns {@code null} if nothing
	 * suitable exists and no target can be derived.
	 */
	public static String resolve(Context ctx) {
		if (ctx == null || ctx.resultsDirectory == null)
			return null;
		Path resultsPath = Paths.get(ctx.resultsDirectory);
		if (!Files.isDirectory(resultsPath))
			return deriveNameFromInterval(ctx);

		List<BinCandidate> candidates = scanCandidates(resultsPath, ctx.detectedIntervalMs);

		if (ctx.previouslySelected != null) {
			Path prevPath = resultsPath.resolve(ctx.previouslySelected);
			if (Files.isDirectory(prevPath)) {
				return pickNewestKymographBinInFamily(resultsPath, ctx.previouslySelected);
			}
			if (containsName(candidates, ctx.previouslySelected)) {
				return pickNewestKymographBinInFamily(resultsPath, ctx.previouslySelected);
			}
		}

		if (ctx.useSessionRemembered && rememberedBinForSession != null) {
			Path memPath = resultsPath.resolve(rememberedBinForSession);
			if (Files.isDirectory(memPath)) {
				return pickNewestKymographBinInFamily(resultsPath, rememberedBinForSession);
			}
			if (containsName(candidates, rememberedBinForSession)) {
				return pickNewestKymographBinInFamily(resultsPath, rememberedBinForSession);
			}
		}

		if (candidates.isEmpty()) {
			return deriveNameFromInterval(ctx);
		}

		// Safeguard for legacy results with multiple bin_xxx siblings when interval
		// detection fails: if exactly one directory contains real content (images or
		// measures), adopt it without prompting.
		BinCandidate soleWithData = soleCandidateWithData(candidates);
		if (soleWithData != null) {
			return soleWithData.name;
		}

		EquivKey targetKey = classifyExpected(ctx);
		Map<EquivKey, List<BinCandidate>> byClass = groupByEquivalenceClass(candidates);

		List<BinCandidate> matching = pickMatchingClass(byClass, targetKey);

		if (matching != null && !matching.isEmpty()) {
			List<BinCandidate> withData = new ArrayList<>();
			for (BinCandidate c : matching) {
				if (c.hasMeasures || c.hasImages)
					withData.add(c);
			}
			if (withData.size() == 1) {
				BinCandidate adopted = withData.get(0);
				maybeUpgradeMetadata(ctx, adopted);
				maybeCleanupEmptyPeers(ctx, matching, adopted);
				return adopted.name;
			}
			if (withData.isEmpty()) {
				BinCandidate chosen = pickBest(matching);
				maybeUpgradeMetadata(ctx, chosen);
				return chosen.name;
			}
			return promptFromClass(ctx, withData, matching);
		}

		// No candidate matches the detected class. Fallback: behave as before.
		if (candidates.size() == 1) {
			return candidates.get(0).name;
		}
		if (ctx.allowPrompt) {
			return promptAcrossClasses(ctx, candidates);
		}
		return pickBest(candidates).name;
	}

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
	 * Scans {@code results/} for immediate {@code bin_<integer>} and flip-flop
	 * {@code bin_<integer>_r<integer>} children, skipping any directory whose name
	 * begins with
	 * {@link BinDirectoryScanUtils#DELETED_PREFIX}. Each candidate is
	 * annotated with its compression-class metadata, either loaded from its
	 * {@code BinDescription.xml} or inferred from content and
	 * {@code detectedCameraMs}.
	 */
	public static List<BinCandidate> scanCandidates(Path resultsPath, long detectedCameraMs) {
		List<BinCandidate> out = new ArrayList<>();
		if (resultsPath == null || !Files.isDirectory(resultsPath))
			return out;
		BinDescriptionPersistence persistence = new BinDescriptionPersistence();
		try {
			for (Path p : Files.newDirectoryStream(resultsPath)) {
				if (!Files.isDirectory(p))
					continue;
				String name = p.getFileName().toString();
				if (BinDirectoryScanUtils.isIgnoredDirectoryName(name))
					continue;
				if (!name.startsWith(Experiment.BIN))
					continue;
				int sec;
				try {
					sec = Integer.parseInt(name.substring(Experiment.BIN.length()));
				} catch (NumberFormatException e) {
					Matcher flip = BIN_WITH_FLIP_REVISION.matcher(name);
					if (!flip.matches())
						continue;
					sec = Integer.parseInt(flip.group(1));
				}
				if (sec <= 0)
					continue;
				out.add(buildCandidate(p, name, sec, persistence, detectedCameraMs));
			}
		} catch (IOException e) {
			// ignore
		}
		out.sort(Comparator.comparingInt((BinCandidate c) -> c.seconds));
		return out;
	}

	/** Back-compat overload: scan without a known camera interval. */
	public static List<BinCandidate> scanCandidates(Path resultsPath) {
		return scanCandidates(resultsPath, -1);
	}

	private static BinCandidate buildCandidate(Path dir, String name, int sec,
			BinDescriptionPersistence persistence, long detectedCameraMs) {
		BinCandidate c = new BinCandidate();
		c.name = name;
		c.seconds = sec;
		c.path = dir;
		c.hasMeasures = BinDirectoryScanUtils.hasMeasureContent(dir);
		c.hasImages = BinDirectoryScanUtils.hasImageContent(dir);
		c.hasBinDescription = Files.exists(dir.resolve(BinDescriptionPersistence.ID_V2_BINDESCRIPTION_XML));
		try {
			c.lastModifiedMs = Files.getLastModifiedTime(dir).toMillis();
		} catch (IOException e) {
			c.lastModifiedMs = 0;
		}

		BinDescription desc = new BinDescription();
		boolean metadataLoaded = c.hasBinDescription && persistence.load(desc, dir.toString());
		if (metadataLoaded) {
			c.cameraIntervalMs = desc.getCameraIntervalMs();
			c.subsampleFactor = desc.getSubsampleFactor();
			c.generationMode = desc.getGenerationMode();
			c.binKymoColMs = desc.getBinKymoColMs();
			c.nominalIntervalSec = desc.getNominalIntervalSec();
		} else {
			c.binKymoColMs = ((long) sec) * 1000L;
			c.nominalIntervalSec = sec;
		}
		if (c.cameraIntervalMs <= 0 && detectedCameraMs > 0) {
			c.cameraIntervalMs = detectedCameraMs;
			c.metadataInferred = true;
		}
		if (!metadataLoaded || c.subsampleFactor <= 0) {
			if (c.cameraIntervalMs > 0 && c.binKymoColMs > 0) {
				c.subsampleFactor = (int) Math.max(1L, Math.round(c.binKymoColMs / (double) c.cameraIntervalMs));
			} else {
				c.subsampleFactor = 1;
			}
			c.metadataInferred = c.metadataInferred || !metadataLoaded;
		}
		if (c.generationMode == null || c.generationMode == GenerationMode.UNKNOWN) {
			if (BinDirectoryScanUtils.hasKymographFiles(dir)) {
				c.generationMode = GenerationMode.KYMOGRAPH;
			} else if (c.hasMeasures) {
				c.generationMode = GenerationMode.DIRECT_FROM_STACK;
			} else {
				c.generationMode = GenerationMode.UNKNOWN;
			}
			if (!metadataLoaded)
				c.metadataInferred = true;
		}
		c.latestLineKymographTiffMs = BinDirectoryScanUtils.newestLineKymographTiffTimestampMs(dir);
		return c;
	}

	/**
	 * Canonical folder plus flip revisions share one "stem" (e.g. {@code bin_60} for
	 * {@code bin_60_r1}). Among those siblings under {@code resultsPath}, returns the
	 * directory name whose {@code line*.tiff} files are newest by last-modified time.
	 */
	public static String pickNewestKymographBinInFamily(Path resultsPath, String binDirName) {
		if (resultsPath == null || binDirName == null || !Files.isDirectory(resultsPath)) {
			return binDirName;
		}
		String stem = stemBinFolderForFlipFamily(binDirName);
		String bestName = binDirName;
		long bestTs = -1L;
		boolean anyLineKymoTimestamp = false;
		Pattern siblingPat = Pattern.compile("^" + Pattern.quote(stem) + "_r\\d+$");
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(resultsPath)) {
			for (Path p : ds) {
				if (!Files.isDirectory(p)) {
					continue;
				}
				String n = p.getFileName().toString();
				if (BinDirectoryScanUtils.isIgnoredDirectoryName(n)) {
					continue;
				}
				if (!n.equals(stem) && !siblingPat.matcher(n).matches()) {
					continue;
				}
				long ts = BinDirectoryScanUtils.newestLineKymographTiffTimestampMs(p);
				if (ts > 0L) {
					anyLineKymoTimestamp = true;
				}
				if (ts > bestTs) {
					bestTs = ts;
					bestName = n;
				} else if (ts == bestTs && ts > 0L) {
					if (flipRevisionSuffix(n) > flipRevisionSuffix(bestName)) {
						bestName = n;
					}
				}
			}
		} catch (IOException e) {
			return binDirName;
		}
		if (!anyLineKymoTimestamp) {
			return binDirName;
		}
		return bestName;
	}

	private static String stemBinFolderForFlipFamily(String dirName) {
		if (dirName == null) {
			return null;
		}
		return dirName.replaceFirst("_r\\d+$", "");
	}

	private static int flipRevisionSuffix(String dirName) {
		if (dirName == null) {
			return 0;
		}
		Matcher m = Pattern.compile("_r(\\d+)$").matcher(dirName);
		return m.find() ? Integer.parseInt(m.group(1)) : 0;
	}

	// -------------- equivalence classes --------------

	/** Compact key for "same compression class". */
	public static final class EquivKey {
		public final int cameraSec;
		public final int subsampleFactor;
		public final GenerationMode mode;

		public EquivKey(int cameraSec, int subsampleFactor, GenerationMode mode) {
			this.cameraSec = cameraSec;
			this.subsampleFactor = subsampleFactor;
			this.mode = mode == null ? GenerationMode.UNKNOWN : mode;
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof EquivKey))
				return false;
			EquivKey other = (EquivKey) o;
			return cameraSec == other.cameraSec && subsampleFactor == other.subsampleFactor && mode == other.mode;
		}

		@Override
		public int hashCode() {
			return ((cameraSec * 31) + subsampleFactor) * 31 + mode.hashCode();
		}

		@Override
		public String toString() {
			return String.format(Locale.ROOT, "cam%ds_x%d_%s", cameraSec, subsampleFactor, mode);
		}
	}

	public static EquivKey equivalenceKey(BinCandidate c) {
		int camSec = c.cameraIntervalMs > 0 ? (int) Math.round(c.cameraIntervalMs / 1000.0) : -1;
		int factor = Math.max(1, c.subsampleFactor);
		return new EquivKey(camSec, factor, c.generationMode == null ? GenerationMode.UNKNOWN : c.generationMode);
	}

	public static EquivKey classifyExpected(Context ctx) {
		if (ctx == null)
			return new EquivKey(-1, 1, GenerationMode.UNKNOWN);
		int camSec = ctx.detectedIntervalMs > 0 ? (int) Math.round(ctx.detectedIntervalMs / 1000.0) : -1;
		int factor = 1; // by default we look for full-resolution output of the current recording
		return new EquivKey(camSec, factor, ctx.generationMode == null ? GenerationMode.UNKNOWN : ctx.generationMode);
	}

	public static Map<EquivKey, List<BinCandidate>> groupByEquivalenceClass(List<BinCandidate> candidates) {
		Map<EquivKey, List<BinCandidate>> map = new LinkedHashMap<>();
		for (BinCandidate c : candidates) {
			EquivKey k = equivalenceKey(c);
			map.computeIfAbsent(k, kk -> new ArrayList<>()).add(c);
		}
		return map;
	}

	/**
	 * Returns the list of candidates whose equivalence class matches {@code target},
	 * or a merged class when {@code target.mode == UNKNOWN} (i.e. the caller did
	 * not declare a generation mode; match on cameraSec + factor regardless).
	 */
	private static List<BinCandidate> pickMatchingClass(Map<EquivKey, List<BinCandidate>> byClass, EquivKey target) {
		if (target == null || target.cameraSec <= 0)
			return null;
		List<BinCandidate> merged = null;
		for (Map.Entry<EquivKey, List<BinCandidate>> e : byClass.entrySet()) {
			EquivKey k = e.getKey();
			if (Math.abs(k.cameraSec - target.cameraSec) > CAM_TOLERANCE_SEC)
				continue;
			if (k.subsampleFactor != target.subsampleFactor)
				continue;
			if (target.mode != GenerationMode.UNKNOWN && k.mode != GenerationMode.UNKNOWN
					&& k.mode != target.mode)
				continue;
			if (merged == null)
				merged = new ArrayList<>();
			merged.addAll(e.getValue());
		}
		return merged;
	}

	// -------------- metadata upgrade --------------

	/**
	 * When the adopted candidate's metadata was inferred rather than loaded
	 * (pre-2.1.0 directory, or XML missing altogether), write a best-effort
	 * upgraded {@code BinDescription.xml} so that subsequent sessions do not
	 * have to re-infer. Silent best-effort: failures (read-only mount, etc.)
	 * are ignored.
	 */
	private static void maybeUpgradeMetadata(Context ctx, BinCandidate c) {
		if (ctx == null || !ctx.allowCleanup)
			return;
		if (c == null || c.path == null || !c.metadataInferred)
			return;
		if (c.cameraIntervalMs <= 0 || c.binKymoColMs <= 0)
			return;
		BinDescription desc = new BinDescription();
		desc.setBinKymoColMs(c.binKymoColMs);
		desc.setBinDirectory(c.name);
		if (c.nominalIntervalSec > 0)
			desc.setNominalIntervalSec(c.nominalIntervalSec);
		desc.setCameraIntervalMs(c.cameraIntervalMs);
		desc.setSubsampleFactor(Math.max(1, c.subsampleFactor));
		desc.setGenerationMode(c.generationMode == null ? GenerationMode.UNKNOWN : c.generationMode);
		desc.setMeasuresPresent(c.hasMeasures);
		new BinDescriptionPersistence().save(desc, c.path.toString());
	}

	// -------------- cleanup --------------

	private static void maybeCleanupEmptyPeers(Context ctx, List<BinCandidate> matching, BinCandidate adopted) {
		if (ctx == null || !ctx.allowCleanup)
			return;
		String mode = PREFS.get(PREF_CLEANUP_MODE, "rename");
		if (!"rename".equalsIgnoreCase(mode))
			return;
		List<BinCandidate> toRename = new ArrayList<>();
		for (BinCandidate c : matching) {
			if (c == adopted)
				continue;
			if (c.hasMeasures || c.hasImages)
				continue;
			toRename.add(c);
		}
		if (toRename.isEmpty())
			return;
		maybeShowFirstRunInfo(ctx, toRename);
		for (BinCandidate c : toRename) {
			renameToDeleted(c.path);
		}
	}

	private static void maybeShowFirstRunInfo(Context ctx, List<BinCandidate> toRename) {
		boolean shown = PREFS.getBoolean(PREF_CLEANUP_INFO_SHOWN, false);
		if (shown || ctx == null || !ctx.allowPrompt || ctx.parentForDialog == null)
			return;
		StringBuilder sb = new StringBuilder();
		sb.append("Some empty sibling directories looked like duplicates of the one just selected ");
		sb.append("and will be renamed (not deleted):\n\n");
		for (BinCandidate c : toRename)
			sb.append("  ").append(c.name).append("  \u2192  ")
					.append(BinDirectoryScanUtils.DELETED_PREFIX).append(c.name).append('\n');
		sb.append("\nThey stay next to the data; search for \"")
				.append(BinDirectoryScanUtils.DELETED_PREFIX)
				.append("*\" to purge them manually later.\n");
		sb.append("You can turn this off by setting preference \"" + PREF_CLEANUP_MODE + "\" to \"off\".");
		javax.swing.JOptionPane.showMessageDialog(ctx.parentForDialog, sb.toString(),
				"Cleanup of near-duplicate bin directories", javax.swing.JOptionPane.INFORMATION_MESSAGE);
		PREFS.putBoolean(PREF_CLEANUP_INFO_SHOWN, true);
	}

	private static void renameToDeleted(Path dir) {
		if (dir == null)
			return;
		String original = dir.getFileName().toString();
		String target = BinDirectoryScanUtils.DELETED_PREFIX + original;
		Path parent = dir.getParent();
		Path targetPath = parent.resolve(target);
		if (Files.exists(targetPath)) {
			SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd'T'HHmm");
			target = target + "_" + fmt.format(new Date());
			targetPath = parent.resolve(target);
			int n = 1;
			while (Files.exists(targetPath) && n < 100) {
				targetPath = parent.resolve(target + "_" + n);
				n++;
			}
		}
		try {
			Files.move(dir, targetPath);
		} catch (IOException ignored) {
			// best-effort; a locked directory simply stays as-is
		}
	}

	// -------------- prompting --------------

	private static String promptFromClass(Context ctx, List<BinCandidate> withData, List<BinCandidate> sameClass) {
		if (!ctx.allowPrompt)
			return pickBest(withData).name;
		Integer detectedForDialog = ctx.detectedIntervalMs > 0 ? Integer.valueOf((int) Math.round(
				ctx.detectedIntervalMs / 1000.0)) : null;
		ChooseAnalysisIntervalDialog.Result r = ChooseAnalysisIntervalDialog.ask(ctx.parentForDialog, withData,
				detectedForDialog, lastPreference());
		if (r != null && r.chosenBinName != null) {
			String chosen = r.chosenBinName;
			BinCandidate chosenCandidate = findByName(sameClass, chosen);
			if (chosenCandidate != null && !hasAnyData(chosenCandidate)) {
				BinCandidate fallback = pickFallbackWithData(sameClass, chosenCandidate);
				if (fallback != null) {
					showAutoSwitchMessage(ctx, chosenCandidate, fallback);
					chosen = fallback.name;
				}
			}
			PREFS.put(PREF_LAST_BIN_SUBDIR, chosen);
			if (r.rememberForSession) {
				rememberedBinForSession = chosen;
			}
			return chosen;
		}
		return pickBest(withData).name;
	}

	private static String promptAcrossClasses(Context ctx, List<BinCandidate> candidates) {
		Integer detectedForDialog = ctx.detectedIntervalMs > 0 ? Integer.valueOf((int) Math.round(
				ctx.detectedIntervalMs / 1000.0)) : null;
		ChooseAnalysisIntervalDialog.Result r = ChooseAnalysisIntervalDialog.ask(ctx.parentForDialog, candidates,
				detectedForDialog, lastPreference());
		if (r != null && r.chosenBinName != null) {
			String chosen = r.chosenBinName;
			BinCandidate chosenCandidate = findByName(candidates, chosen);
			if (chosenCandidate != null && !hasAnyData(chosenCandidate)) {
				BinCandidate fallback = pickFallbackWithData(candidates, chosenCandidate);
				if (fallback != null) {
					showAutoSwitchMessage(ctx, chosenCandidate, fallback);
					chosen = fallback.name;
				}
			}
			PREFS.put(PREF_LAST_BIN_SUBDIR, chosen);
			if (r.rememberForSession) {
				rememberedBinForSession = chosen;
			}
			return chosen;
		}
		return pickBest(candidates).name;
	}

	private static boolean hasAnyData(BinCandidate c) {
		return c != null && (c.hasMeasures || c.hasImages);
	}

	private static BinCandidate soleCandidateWithData(List<BinCandidate> candidates) {
		if (candidates == null || candidates.isEmpty())
			return null;
		BinCandidate sole = null;
		for (BinCandidate c : candidates) {
			if (!hasAnyData(c))
				continue;
			if (sole != null)
				return null;
			sole = c;
		}
		return sole;
	}

	private static BinCandidate findByName(List<BinCandidate> candidates, String name) {
		if (candidates == null || name == null)
			return null;
		for (BinCandidate c : candidates) {
			if (name.equals(c.name))
				return c;
		}
		return null;
	}

	private static BinCandidate pickFallbackWithData(List<BinCandidate> candidates, BinCandidate chosenEmpty) {
		if (candidates == null || chosenEmpty == null)
			return null;
		BinCandidate best = null;
		int bestDelta = Integer.MAX_VALUE;
		for (BinCandidate c : candidates) {
			if (!hasAnyData(c))
				continue;
			int d = Math.abs(c.seconds - chosenEmpty.seconds);
			if (best == null || d < bestDelta || (d == bestDelta && c.lastModifiedMs > best.lastModifiedMs)) {
				best = c;
				bestDelta = d;
			}
		}
		return best;
	}

	private static void showAutoSwitchMessage(Context ctx, BinCandidate chosenEmpty, BinCandidate fallback) {
		if (ctx == null || !ctx.allowPrompt || ctx.parentForDialog == null)
			return;
		String msg = "The selected directory \"" + chosenEmpty.name + "\" appears to be empty (no images/measures).\n"
				+ "Using \"" + fallback.name + "\" instead because it contains data.";
		javax.swing.JOptionPane.showMessageDialog(ctx.parentForDialog, msg, "Empty bin directory",
				javax.swing.JOptionPane.INFORMATION_MESSAGE);
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
				} else if (c.hasBinDescription == best.hasBinDescription) {
					if (c.latestLineKymographTiffMs > best.latestLineKymographTiffMs) {
						best = c;
					} else if (c.latestLineKymographTiffMs == best.latestLineKymographTiffMs
							&& c.lastModifiedMs > best.lastModifiedMs) {
						best = c;
					}
				} else if (!c.hasBinDescription && !best.hasBinDescription) {
					if (c.latestLineKymographTiffMs > best.latestLineKymographTiffMs) {
						best = c;
					} else if (c.latestLineKymographTiffMs == best.latestLineKymographTiffMs
							&& c.lastModifiedMs > best.lastModifiedMs) {
						best = c;
					}
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

	/** Descriptor of a {@code results/bin_xxx} candidate annotated with class metadata. */
	public static final class BinCandidate {
		public String name;
		public int seconds;
		public Path path;
		public boolean hasMeasures;
		public boolean hasImages;
		public boolean hasBinDescription;
		public long lastModifiedMs;
		public long cameraIntervalMs = -1;
		public int subsampleFactor = 1;
		public GenerationMode generationMode = GenerationMode.UNKNOWN;
		public long binKymoColMs = -1;
		public int nominalIntervalSec = -1;
		/** True when class metadata was not loaded from XML but inferred from content. */
		public boolean metadataInferred = false;
		/** Max last-modified of {@code line*.tiff} in this bin (0 if none). */
		public long latestLineKymographTiffMs;

		public File toFile() {
			return path != null ? path.toFile() : null;
		}
	}
}
