package plugins.fmp.multitools.service;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import icy.file.Saver;
import icy.gui.frame.progress.ProgressFrame;
import icy.image.IcyBufferedImage;
import icy.sequence.Sequence;
import icy.system.SystemUtil;
import icy.system.thread.Processor;
import icy.type.DataType;
import icy.type.collection.array.Array1DUtil;
import loci.formats.FormatException;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.Comparators;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.ROI2D.AlongT;
import plugins.fmp.multitools.tools.ROI2D.ROI2DUtilities;
import plugins.fmp.multitools.tools.polyline.Bresenham;

public class KymographBuilder {

	private Map<Capillary, ArrayList<int[]>> capIntegerArrays = new HashMap<>();

	public static final class LockProbeReport {
		public final Path directory;
		public final int total;
		public final int ok;
		public final int locked;
		public final List<String> lockedFiles;

		LockProbeReport(Path directory, int total, int ok, int locked, List<String> lockedFiles) {
			this.directory = directory;
			this.total = total;
			this.ok = ok;
			this.locked = locked;
			this.lockedFiles = lockedFiles;
		}
	}

	/**
	 * Non-destructive probe: for each file matching {@code fileNameFilter} in {@code dir},
	 * tries to rename it to a temporary name and back. If the move fails, the file is
	 * considered locked by another process (typical Windows handle lock).
	 */
	public static LockProbeReport probeFileLocks(Path dir, Predicate<String> fileNameFilter) {
		if (dir == null || !Files.isDirectory(dir)) {
			return new LockProbeReport(dir, 0, 0, 0, new ArrayList<>());
		}
		Predicate<String> pred = fileNameFilter != null ? fileNameFilter : (s -> true);
		List<Path> files;
		try (Stream<Path> stream = Files.list(dir)) {
			files = stream.filter(Files::isRegularFile).filter(p -> {
				String n = p.getFileName() != null ? p.getFileName().toString() : "";
				return pred.test(n);
			}).collect(Collectors.toList());
		} catch (IOException e) {
			Logger.warn("KymographBuilder: probeFileLocks list failed " + dir + " : " + e.getMessage());
			return new LockProbeReport(dir, 0, 0, 0, new ArrayList<>());
		}

		int ok = 0;
		ArrayList<String> lockedFiles = new ArrayList<>();
		for (Path p : files) {
			Path probe = p.resolveSibling(p.getFileName().toString() + ".probe_locktest");
			try {
				Files.move(p, probe, StandardCopyOption.REPLACE_EXISTING);
				Files.move(probe, p, StandardCopyOption.REPLACE_EXISTING);
				ok++;
			} catch (IOException e) {
				lockedFiles.add(p.getFileName().toString() + " : " + e.getMessage());
				try {
					// Best-effort cleanup if first move succeeded but second failed.
					if (Files.exists(probe) && !Files.exists(p)) {
						Files.move(probe, p, StandardCopyOption.REPLACE_EXISTING);
					}
				} catch (IOException ignored) {
				}
			}
		}
		int total = files.size();
		int locked = Math.max(0, total - ok);
		return new LockProbeReport(dir, total, ok, locked, lockedFiles);
	}

	/** Convenience wrapper for kymograph TIFFs: {@code line*.tif/.tiff}. */
	public static LockProbeReport probeKymographFileLocks(Path dir) {
		return probeFileLocks(dir, (name) -> {
			if (name == null)
				return false;
			String n = name.toLowerCase();
			return n.startsWith("line") && (n.endsWith(".tif") || n.endsWith(".tiff"));
		});
	}

	public static final class PreArchiveResult {
		public final int renamed;
		public final int failed;
		public final Path directory;

		PreArchiveResult(int renamed, int failed, Path directory) {
			this.renamed = renamed;
			this.failed = failed;
			this.directory = directory;
		}

		public boolean ok() {
			return failed == 0;
		}
	}

	/**
	 * Pre-flight for the active kymograph bin: delete every file whose name starts with
	 * {@code old_}, then rename {@code line*.tif/.tiff} to {@code old_line*...}. If either
	 * step fails, {@link PreArchiveResult#failed} is non-zero so the caller can warn and
	 * the builder can fall back to flip-flop bins.
	 */
	public static PreArchiveResult preArchiveExistingKymographsInCurrentBin(Experiment exp) {
		if (exp == null) {
			return new PreArchiveResult(0, 0, null);
		}
		String binDir = exp.getKymosBinFullDirectory();
		if (binDir == null) {
			return new PreArchiveResult(0, 0, null);
		}
		return prepareKymographBinDeleteOldThenRenameLine(Paths.get(binDir));
	}

	/**
	 * (1) Deletes all regular files in {@code dir} whose name starts with {@code old_}
	 * (case-insensitive). (2) Renames each {@code line*.tif/.tiff} to {@code old_line*...}
	 * with rollback if any rename fails. Returns {@code failed > 0} if any delete failed or
	 * any line file could not be renamed.
	 */
	public static PreArchiveResult prepareKymographBinDeleteOldThenRenameLine(Path dir) {
		if (dir == null || !Files.isDirectory(dir)) {
			return new PreArchiveResult(0, 0, dir);
		}
		int delFail = deleteAllOldPrefixedFiles(dir);
		if (delFail > 0) {
			return new PreArchiveResult(0, delFail, dir);
		}
		return archiveLineKymographsInDirectory(dir, -1L, true, false, true, true);
	}

	private static int deleteAllOldPrefixedFiles(Path dir) {
		if (dir == null || !Files.isDirectory(dir)) {
			return 0;
		}
		List<Path> toDelete;
		try (Stream<Path> stream = Files.list(dir)) {
			toDelete = stream.filter(Files::isRegularFile).filter(p -> {
				String n = p.getFileName() != null ? p.getFileName().toString() : "";
				return n.regionMatches(true, 0, "old_", 0, 4);
			}).collect(Collectors.toList());
		} catch (IOException e) {
			Logger.warn("KymographBuilder: deleteAllOldPrefixedFiles list failed " + dir + " : " + e.getMessage());
			return 1;
		}
		int failures = 0;
		for (Path p : toDelete) {
			deletePathWithRetries(p);
			if (Files.exists(p)) {
				failures++;
				Logger.warn("KymographBuilder: could not delete " + p);
			}
		}
		return failures;
	}

	public boolean buildKymograph(Experiment exp, BuildSeriesOptions options) {
		if (exp.getCapillaries().getList().size() < 1) {
			Logger.warn("KymographBuilder:buildKymo Abort (1): nbcapillaries = 0");
			return false;
		}

		getCapillariesToProcess(exp, options);
		clearAllAlongTMasks(exp);

		SequenceLoaderService loader = new SequenceLoaderService();
		// Canonical behavior: time origin for analyses is the first valid (visible)
		// frame, i.e. frame 0 of the clipped image list.
		long camReferenceMs = exp.getSeqCamData().getFirstValidFrameEpochMs();
		if (camReferenceMs < 0) {
			// Fallback to persisted experiment-level timing if file timestamps aren't available.
			camReferenceMs = exp.getCamImageFirst_ms();
		}
		if (camReferenceMs < 0) {
			camReferenceMs = 0;
		}
		exp.build_MsTimeIntervalsArray_From_SeqCamData_FileNamesList(camReferenceMs);

		long first_ms = 0;
		long last_ms = exp.getSeqCamData().getTimeManager().getBinLast_ms();
		if (last_ms <= first_ms && exp.getKymoLast_ms() > exp.getKymoFirst_ms()) {
			first_ms = exp.getKymoFirst_ms();
			last_ms = exp.getKymoLast_ms();
		}

		long step_ms = exp.getKymoBin_ms();

		int nTotalFrames = exp.getSeqCamData().getImageLoader().getNTotalFrames();
		int lowIndex = 0;
		int highIndex = (nTotalFrames > 0) ? (nTotalFrames - 1) : 0;
		if (highIndex < lowIndex)
			highIndex = lowIndex;
		long[] camImages_ms = exp.getCamImages_ms();
		if (camImages_ms != null && highIndex < camImages_ms.length)
			last_ms = Math.min(last_ms, camImages_ms[highIndex]);

		int expectedWidth = (step_ms > 0) ? Math.max(1, 1 + (int) Math.ceil((last_ms - first_ms) / (double) step_ms))
				: 1;
		initArraysToBuildKymographImages(exp, options, expectedWidth);

		int sourceLastImageIndex = nTotalFrames;
		final int refSizex = exp.getSeqCamData().getSequence().getSizeX();
		final int refSizey = exp.getSeqCamData().getSequence().getSizeY();

		ProgressFrame progress = new ProgressFrame("Analyze series");

		final Processor processor = new Processor(SystemUtil.getNumberOfCPUs());
		processor.setThreadName("buildKymograph");
		processor.setPriority(Processor.NORM_PRIORITY);
		int ntasks = exp.getCapillaries().getList().size();
		ArrayList<Future<?>> tasks = new ArrayList<Future<?>>(ntasks);
		tasks.clear();

		for (int iToColumn = 0; iToColumn < expectedWidth; iToColumn++) {
			long ii_ms = first_ms + iToColumn * step_ms;
			int sourceImageIndex = exp.findNearestIntervalWithBinarySearch(ii_ms, lowIndex, highIndex);
			if (sourceImageIndex < 0)
				continue;

			final int fromSourceImageIndex = sourceImageIndex;

			final int viewT = fromSourceImageIndex;
			final int kymographColumn = iToColumn;
			progress.setMessage("Processing file: " + (sourceImageIndex + 1) + "//" + sourceLastImageIndex);

			final IcyBufferedImage sourceImage = loader
					.imageIORead(exp.getSeqCamData().getFileNameFromImageList(fromSourceImageIndex));

			tasks.add(processor.submit(() -> {
				for (Capillary capi : exp.getCapillaries().getList()) {
					if (!capi.getKymographBuild())
						continue;
					analyzeImageUnderCapillary(sourceImage, capi, viewT, kymographColumn, refSizex, refSizey, options);
				}
			}));
		}

		progress.close();
		waitFuturesCompletion(processor, tasks);

		SequenceCamData seqCamData = exp.getSeqCamData();
		int sizeC = seqCamData.getSequence().getSizeC();
		if (options.doCreateBinDir) {
			String previousBinDir = exp.getBinSubDirectory();
			String binDir = chooseWritableBinSubDirectory(exp, exp.getBinNameFromKymoFrameStep(), options);
			exp.setBinSubDirectory(binDir);
			exp.setGenerationMode(plugins.fmp.multitools.experiment.GenerationMode.KYMOGRAPH);
			exp.saveBinDescription(binDir);
			if (previousBinDir != null && !previousBinDir.equals(binDir)) {
				copyMeasuresBetweenBins(exp, previousBinDir, binDir);
			}
		}

		archiveExistingKymographTiffsBeforeExport(exp);
		exportCapillaryKymographs(exp, sizeC);
		return true;
	}

	private static String chooseWritableBinSubDirectory(Experiment exp, String preferredBinDir, BuildSeriesOptions options) {
		if (exp == null || preferredBinDir == null) {
			return preferredBinDir;
		}
		String resultsDir = exp.getResultsDirectory();
		if (resultsDir == null) {
			return preferredBinDir;
		}

		Path preferredFull = Paths.get(resultsDir, preferredBinDir);
		if (!Files.isDirectory(preferredFull)) {
			return preferredBinDir;
		}

		// Once flip-flop bins exist, keep all subsequent rebuilds confined to the two slots.
		// This avoids re-touching bin_XX (which may remain locked) and prevents the "3 dirs
		// with TIFFs" pattern from growing further across sessions.
		String slot1 = preferredBinDir + "_r1";
		String slot2 = preferredBinDir + "_r2";
		Path slot1Full = Paths.get(resultsDir, slot1);
		Path slot2Full = Paths.get(resultsDir, slot2);
		boolean flipAlreadyExists = Files.isDirectory(slot1Full) || Files.isDirectory(slot2Full);
		if (flipAlreadyExists) {
			String current = exp.getBinSubDirectory();
			String firstChoice = (current != null && current.endsWith("_r1")) ? slot2 : slot1;
			String secondChoice = firstChoice.equals(slot1) ? slot2 : slot1;
			String chosen = tryPrepareFlipFlopSlot(resultsDir, firstChoice);
			if (chosen != null) {
				return chosen;
			}
			chosen = tryPrepareFlipFlopSlot(resultsDir, secondChoice);
			if (chosen != null) {
				return chosen;
			}
			// If neither slot is usable (e.g. both locked), fall back to preferredBinDir.
			return preferredBinDir;
		}

		// Canonical bin: delete old_* backups, then rename line*.tif -> old_line*. If that fails
		// (locks) or line TIFFs remain, write to flip-flop slots instead.
		if (!optionsPreflightSaysSkipCanonicalPrep(options)) {
			PreArchiveResult prep = prepareKymographBinDeleteOldThenRenameLine(preferredFull);
			if (prep.failed == 0 && !hasAnyLineTiff(preferredFull) && canTemporarilyRenameOneTiff(preferredFull)) {
				return preferredBinDir;
			}
		}

		if (hasAnyLineTiff(preferredFull) || !canTemporarilyRenameOneTiff(preferredFull)) {
			String current = exp.getBinSubDirectory();
			String firstChoice = (current != null && current.endsWith("_r1")) ? slot2 : slot1;
			String secondChoice = firstChoice.equals(slot1) ? slot2 : slot1;

			String chosen = tryPrepareFlipFlopSlot(resultsDir, firstChoice);
			if (chosen != null) {
				return chosen;
			}
			chosen = tryPrepareFlipFlopSlot(resultsDir, secondChoice);
			if (chosen != null) {
				return chosen;
			}
		}

		return preferredBinDir;
	}

	private static boolean optionsPreflightSaysSkipCanonicalPrep(BuildSeriesOptions options) {
		return options != null && options.kymoPreflightDetectedLockedFiles;
	}

	private static String tryPrepareFlipFlopSlot(String resultsDir, String binSubDir) {
		if (resultsDir == null || binSubDir == null) {
			return null;
		}
		Path dir = Paths.get(resultsDir, binSubDir);
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			return null;
		}

		PreArchiveResult prep = prepareKymographBinDeleteOldThenRenameLine(dir);
		if (prep.failed > 0 || hasAnyLineTiff(dir) || !canTemporarilyRenameOneTiff(dir)) {
			return null;
		}
		Logger.warn("KymographBuilder: writing rebuilt kymographs to " + dir);
		return binSubDir;
	}

	private static boolean hasAnyLineTiff(Path binDir) {
		if (binDir == null || !Files.isDirectory(binDir)) {
			return false;
		}
		try (java.util.stream.Stream<Path> stream = Files.list(binDir)) {
			return stream.filter(Files::isRegularFile).anyMatch(p -> {
				String n = p.getFileName() != null ? p.getFileName().toString().toLowerCase() : "";
				return n.startsWith("line") && (n.endsWith(".tif") || n.endsWith(".tiff"));
			});
		} catch (IOException e) {
			return false;
		}
	}

	private static boolean canTemporarilyRenameOneTiff(Path binDir) {
		if (binDir == null || !Files.isDirectory(binDir)) {
			return true;
		}
		try (java.util.stream.Stream<Path> stream = Files.list(binDir)) {
			Path anyTiff = stream.filter(Files::isRegularFile).filter(p -> {
				String n = p.getFileName() != null ? p.getFileName().toString().toLowerCase() : "";
				return n.startsWith("line") && (n.endsWith(".tif") || n.endsWith(".tiff"));
			}).findFirst().orElse(null);

			if (anyTiff == null) {
				return true;
			}

			Path probe = anyTiff.resolveSibling(anyTiff.getFileName().toString() + ".probe_rename");
			// Try rename away and back. If file is locked, Windows will throw FileSystemException.
			Files.move(anyTiff, probe, StandardCopyOption.REPLACE_EXISTING);
			Files.move(probe, anyTiff, StandardCopyOption.REPLACE_EXISTING);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	public void saveComputation(Experiment exp, BuildSeriesOptions options) {
		exp.saveExperimentDescriptors();
	}

	private void getCapillariesToProcess(Experiment exp, BuildSeriesOptions options) {
		Collections.sort(exp.getCapillaries().getList(), new Comparators.Capillary_ROIName());
		int index = 0;

		for (Capillary cap : exp.getCapillaries().getList()) {
			// Derive kymograph name from current ROI name (not from potentially stale
			// metadata)
			String roiName = cap.getRoiName();
			if (roiName != null) {
				String kymographName = Capillary.replace_LR_with_12(roiName);
				cap.setKymographName(kymographName);
				cap.setKymographFileName(kymographName + ".tiff");
			}

			// Assign sequential index for building (will be corrected later when matching
			// to files)
			int i = cap.getKymographIndex();
			if (i < 0) {
				i = index;
				cap.setKymographIndex(i);
			}

			index++;
			cap.setKymographBuild(true);
		}
	}

	private void waitFuturesCompletion(Processor processor, ArrayList<Future<?>> futuresArray) {
		while (!futuresArray.isEmpty()) {
			final Future<?> f = futuresArray.get(futuresArray.size() - 1);
			try {
				f.get();
			} catch (ExecutionException e) {
				Logger.error("KymographBuilder:waitFuturesCompletion - Execution exception", e);
			} catch (InterruptedException e) {
				Logger.warn("KymographBuilder:waitFuturesCompletion - Interrupted exception: " + e.getMessage());
			}
			futuresArray.remove(f);
		}
	}

	void analyzeImageUnderCapillary(IcyBufferedImage sourceImage, Capillary cap, int t, int kymographColumn,
			int refSizex, int refSizey, BuildSeriesOptions options) {
		AlongT alongT = cap.getAlongTAtT(t);
		if (alongT == null) {
			Logger.warn("KymographBuilder:analyzeImageUnderCapillary - no AlongT for t=" + t + " cap="
					+ (cap.getRoiName() != null ? cap.getRoiName() : cap.getKymographName()) + ", skipping column "
					+ kymographColumn);
			return;
		}
		int imgW = sourceImage.getWidth();
		int imgH = sourceImage.getHeight();
		if (imgW != refSizex || imgH != refSizey)
			Logger.warn("KymographBuilder:analyzeImageUnderCapillary - source image size " + imgW + "x" + imgH
					+ " differs from reference " + refSizex + "x" + refSizey + " (t=" + t
					+ "), mask indices may be wrong");

		if (alongT.getMasksList() == null || alongT.getMasksList().isEmpty())
			buildMasks(alongT, refSizex, refSizey, options);
		ArrayList<ArrayList<int[]>> masksList = alongT.getMasksList();
		if (masksList == null) {
			Logger.warn("KymographBuilder:analyzeImageUnderCapillary - masksList still null after build for t=" + t
					+ " cap=" + (cap.getRoiName() != null ? cap.getRoiName() : cap.getKymographName())
					+ ", skipping column " + kymographColumn);
			return;
		}
		if (masksList.isEmpty()) {
			// A degenerate ROI (e.g. tracking failure at this frame) can produce zero
			// masks.
			// Leaving the column uninitialized results in a black column; instead, copy
			// previous column if available so the kymograph remains visually consistent.
			IcyBufferedImage capImage = cap.getCap_Image();
			int kymoImageWidth = capImage.getWidth();
			ArrayList<int[]> capInteger = capIntegerArrays.get(cap);
			if (capInteger != null && kymographColumn > 0) {
				for (int chan = 0; chan < capInteger.size(); chan++) {
					int[] capImageChannel = capInteger.get(chan);
					for (int row = 0; row < capImage.getHeight(); row++) {
						int dst = row * kymoImageWidth + kymographColumn;
						int src = row * kymoImageWidth + (kymographColumn - 1);
						capImageChannel[dst] = capImageChannel[src];
					}
				}
			}
			Logger.warn("KymographBuilder:analyzeImageUnderCapillary - empty masksList (degenerate ROI?) t=" + t
					+ " cap=" + (cap.getRoiName() != null ? cap.getRoiName() : cap.getKymographName()) + " column="
					+ kymographColumn);
			return;
		}

		IcyBufferedImage capImage = cap.getCap_Image();
		int kymoImageWidth = capImage.getWidth();
		ArrayList<int[]> capInteger = capIntegerArrays.get(cap);
		if (capInteger == null || capInteger.isEmpty()) {
			Logger.warn("KymographBuilder:analyzeImageUnderCapillary - capInteger missing for cap="
					+ (cap.getRoiName() != null ? cap.getRoiName() : cap.getKymographName()));
			return;
		}
		final int kymoSizeC = Math.max(1, capImage.getSizeC());
		if (capInteger.size() < kymoSizeC) {
			Logger.warn("KymographBuilder:analyzeImageUnderCapillary - capInteger.size=" + capInteger.size()
					+ " < kymoSizeC=" + kymoSizeC + " for cap="
					+ (cap.getRoiName() != null ? cap.getRoiName() : cap.getKymographName()));
			return;
		}

		int sourceImageWidth = sourceImage.getWidth();
		final int srcSizeC = Math.max(1, sourceImage.getSizeC());
		int[] src0 = Array1DUtil.arrayToIntArray(sourceImage.getDataXY(0), sourceImage.isSignedDataType());
		int[] src1 = (srcSizeC > 1)
				? Array1DUtil.arrayToIntArray(sourceImage.getDataXY(1), sourceImage.isSignedDataType())
				: null;
		int[] src2 = (srcSizeC > 2)
				? Array1DUtil.arrayToIntArray(sourceImage.getDataXY(2), sourceImage.isSignedDataType())
				: null;

		int cnt = 0;
		for (ArrayList<int[]> mask : masksList) {
			long sum0 = 0;
			long sum1 = 0;
			long sum2 = 0;
			for (int[] m : mask) {
				int idx = m[0] + m[1] * sourceImageWidth;
				sum0 += src0[idx];
				if (src1 != null)
					sum1 += src1[idx];
				if (src2 != null)
					sum2 += src2[idx];
			}
			if (!mask.isEmpty()) {
				int dst = cnt * kymoImageWidth + kymographColumn;
				capInteger.get(0)[dst] = (int) (sum0 / mask.size());
				if (kymoSizeC > 1 && src1 != null)
					capInteger.get(1)[dst] = (int) (sum1 / mask.size());
				if (kymoSizeC > 2 && src2 != null)
					capInteger.get(2)[dst] = (int) (sum2 / mask.size());
			}
			cnt++;
		}
	}

	/**
	 * Renames every {@code line*.tiff} in {@code dir} to {@code old_line*.tiff} on the
	 * caller thread (before parallel export or before flip-flop delete). Archived files
	 * are registered for deletion on normal JVM exit.
	 */
	private static void archiveAllLineKymographTiffsInDirectory(Path dir) {
		archiveLineKymographsInDirectory(dir, -1L, true, true, false, true);
	}

	private static PreArchiveResult archiveLineKymographsInDirectory(Path dir, long timestampMs,
			boolean bestEffortDeleteOldName, boolean registerDeleteOnExit, boolean rollbackOnAnyFailure,
			boolean useMoveRetries) {
		if (dir == null || !Files.isDirectory(dir)) {
			return new PreArchiveResult(0, 0, dir);
		}
		List<Path> lineTiffs;
		try (Stream<Path> stream = Files.list(dir)) {
			lineTiffs = stream.filter(Files::isRegularFile).filter(p -> {
				String n = p.getFileName() != null ? p.getFileName().toString().toLowerCase() : "";
				if (!n.startsWith("line"))
					return false;
				return n.endsWith(".tif") || n.endsWith(".tiff");
			}).collect(Collectors.toList());
		} catch (IOException e) {
			Logger.warn("KymographBuilder: archiveLineKymographsInDirectory list failed " + dir + " : "
					+ e.getMessage());
			return new PreArchiveResult(0, 0, dir);
		}

		int renamed = 0;
		int failed = 0;
		// For rollback if any move fails: keep a list of successful (from -> to) renames.
		ArrayList<Path[]> moved = rollbackOnAnyFailure ? new ArrayList<>() : null;
		for (Path target : lineTiffs) {
			String fn = target.getFileName().toString();
			Path archived = buildArchivedName(dir, fn, timestampMs);
			if (bestEffortDeleteOldName) {
				deletePathWithRetries(archived);
			}
			try {
				if (useMoveRetries) {
					moveWithRetries(target, archived);
				} else {
					Files.move(target, archived, StandardCopyOption.REPLACE_EXISTING);
				}
				if (registerDeleteOnExit) {
					archived.toFile().deleteOnExit();
				}
				if (moved != null) {
					moved.add(new Path[] { target, archived });
				}
				renamed++;
			} catch (IOException e) {
				failed++;
				Logger.warn("KymographBuilder: could not archive " + target + " -> " + archived + " : " + e.getMessage());
			}
		}
		if (rollbackOnAnyFailure && failed > 0 && moved != null && !moved.isEmpty()) {
			// Roll back best-effort: restore original names to avoid leaving a partially
			// renamed directory when we will abort computation anyway.
			for (int i = moved.size() - 1; i >= 0; i--) {
				Path[] pair = moved.get(i);
				Path original = pair[0];
				Path archived = pair[1];
				try {
					if (useMoveRetries) {
						moveWithRetries(archived, original);
					} else {
						Files.move(archived, original, StandardCopyOption.REPLACE_EXISTING);
					}
				} catch (IOException e) {
					Logger.warn("KymographBuilder: rollback failed " + archived + " -> " + original + " : " + e.getMessage());
				}
			}
			renamed = 0;
		}
		return new PreArchiveResult(renamed, failed, dir);
	}

	private static Path buildArchivedName(Path dir, String originalFileName, long timestampMs) {
		String fn = originalFileName != null ? originalFileName : "";
		int dot = fn.lastIndexOf('.');
		String base = (dot > 0) ? fn.substring(0, dot) : fn;
		String ext = (dot > 0) ? fn.substring(dot) : "";
		String suffix = (timestampMs > 0) ? ("_" + timestampMs) : "";
		return dir.resolve("old_" + base + suffix + ext);
	}

	private void archiveExistingKymographTiffsBeforeExport(Experiment exp) {
		String directory = exp.getDirectoryToSaveResults();
		if (directory == null) {
			return;
		}
		Path dir = Paths.get(directory);
		archiveAllLineKymographTiffsInDirectory(dir);
	}

	private static void deletePathWithRetries(Path path) {
		if (path == null || !Files.exists(path)) {
			return;
		}
		IOException last = null;
		final boolean isWindows = SystemUtil.isWindows();
		final int maxAttempts = isWindows ? 15 : 3;
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				Files.deleteIfExists(path);
				return;
			} catch (IOException e) {
				last = e;
				if (!isWindows) {
					break;
				}
				try {
					Thread.sleep(Math.min(500L, 30L * attempt));
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}
		if (last != null) {
			Logger.warn("KymographBuilder: could not delete stale archive " + path + " : " + last.getMessage());
		}
	}

	/**
	 * When flip-flop creates {@code bin_XX_r1/r2}, measures (CSV/XML) may already be
	 * present in the previously-selected bin folder. Copy them forward so creating
	 * a new revision doesn't "lose" measures from the user's perspective.
	 */
	private static void copyMeasuresBetweenBins(Experiment exp, String fromBinDir, String toBinDir) {
		if (exp == null || fromBinDir == null || toBinDir == null || fromBinDir.equals(toBinDir)) {
			return;
		}
		String resultsDir = exp.getResultsDirectory();
		if (resultsDir == null) {
			return;
		}
		Path from = Paths.get(resultsDir, fromBinDir);
		Path to = Paths.get(resultsDir, toBinDir);
		if (!Files.isDirectory(from) || !Files.isDirectory(to)) {
			return;
		}
		try (Stream<Path> stream = Files.list(from)) {
			for (Path p : stream.collect(Collectors.toList())) {
				if (!Files.isRegularFile(p)) {
					continue;
				}
				String name = p.getFileName().toString();
				String lower = name.toLowerCase();
				// Keep measures and descriptors, but never propagate image files.
				if (lower.endsWith(".tif") || lower.endsWith(".tiff")) {
					continue;
				}
				if (!(lower.endsWith(".csv") || lower.endsWith(".xml"))) {
					continue;
				}
				// Avoid carrying over "deleted_*" helpers.
				if (lower.startsWith("deleted_")) {
					continue;
				}

				Path dest = to.resolve(name);
				if (Files.exists(dest)) {
					continue;
				}
				try {
					Files.copy(p, dest);
				} catch (IOException e) {
					Logger.warn("KymographBuilder: could not copy measure " + p + " -> " + dest + " : " + e.getMessage());
				}
			}
		} catch (IOException e) {
			Logger.warn("KymographBuilder: copyMeasuresBetweenBins list failed " + from + " : " + e.getMessage());
		}
	}

	private void exportCapillaryKymographs(Experiment exp, final int sizeC) {
		final Processor processor = new Processor(SystemUtil.getNumberOfCPUs());
		processor.setThreadName("buildKymograph");
		processor.setPriority(Processor.NORM_PRIORITY);
		int nbcapillaries = exp.getCapillaries().getList().size();
		ArrayList<Future<?>> tasks = new ArrayList<Future<?>>(nbcapillaries);
		tasks.clear();

		String directory = exp.getDirectoryToSaveResults();
		if (directory == null)
			return;

		for (int icap = 0; icap < nbcapillaries; icap++) {
			final Capillary cap = exp.getCapillaries().getList().get(icap);
			if (!cap.getKymographBuild())
				continue;

			tasks.add(processor.submit(new Runnable() {
				@Override
				public void run() {
					IcyBufferedImage capImage = cap.getCap_Image();
					ArrayList<int[]> capInteger = capIntegerArrays.get(cap);
					if (capInteger != null) {
						int kymoSizeC = capImage.getSizeC();
						if (capInteger.size() != kymoSizeC) {
							Logger.warn("KymographBuilder:exportCapillaryKymographs - channel count mismatch for cap="
									+ (cap.getRoiName() != null ? cap.getRoiName() : cap.getKymographName()) + " kymoSizeC="
									+ kymoSizeC + " capInteger.size=" + capInteger.size() + " (input sizeC=" + sizeC + ")");
						}
						boolean isSignedDataType = capImage.isSignedDataType();
						int nch = Math.min(kymoSizeC, capInteger.size());
						for (int chan = 0; chan < nch; chan++) {
							int[] tabValues = capInteger.get(chan);
							Object destArray = capImage.getDataXY(chan);
							Array1DUtil.intArrayToSafeArray(tabValues, 0, destArray, 0, -1, isSignedDataType,
									isSignedDataType);
							capImage.setDataXY(chan, destArray);
						}
					}

					String filename = directory + File.separator + cap.getKymographFileName();
					File file = new File(filename);
					Logger.debug("file saved= " + filename);
					try {
						saveImageSafely(capImage, file);
						cap.setCap_Image(null);
						capIntegerArrays.remove(cap);
					} catch (FormatException e) {
						Logger.error("KymographBuilder: Failed to save kymograph (format error): " + filename, e);
					} catch (IOException e) {
						Logger.error("KymographBuilder: Failed to save kymograph (IO error): " + filename, e);
					}
				}
			}));
		}

		waitFuturesCompletion(processor, tasks);
	}

	/**
	 * Avoid in-place overwrite of existing TIFFs: write to a temporary file, then
	 * replace the target. This prevents rare corruption/size blow-ups observed
	 * when overwriting kymographs.
	 */
	private static void saveImageSafely(IcyBufferedImage image, File target) throws FormatException, IOException {
		if (image == null || target == null) {
			throw new IOException("saveImageSafely: null image or target");
		}
		Path targetPath = target.toPath();
		Path parent = targetPath.getParent();
		if (parent == null) {
			throw new IOException("saveImageSafely: target has no parent directory: " + target);
		}
		Files.createDirectories(parent);

		String baseName = targetPath.getFileName().toString();
		String tmpName = baseName + ".tmp";
		Path tmpPath = parent.resolve(tmpName);
		int n = 1;
		while (Files.exists(tmpPath) && n < 100) {
			tmpPath = parent.resolve(tmpName + "." + n);
			n++;
		}

		File tmpFile = tmpPath.toFile();
		try {
			// Always overwrite the tmp file if it already exists.
			Saver.saveImage(image, tmpFile, true);
			moveWithRetries(tmpPath, targetPath);
		} finally {
			try {
				Files.deleteIfExists(tmpPath);
			} catch (IOException ignored) {
				// best-effort cleanup
			}
		}
	}

	private static void moveWithRetries(Path tmpPath, Path targetPath) throws IOException {
		IOException last = null;
		final boolean isWindows = SystemUtil.isWindows();
		final int maxAttempts = isWindows ? 25 : 3;

		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				try {
					Files.move(tmpPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				} catch (IOException atomicNotSupported) {
					Files.move(tmpPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
				}
				return;
			} catch (IOException e) {
				last = e;
				if (!isWindows) {
					break;
				}
				// On Windows, the target TIFF can remain locked briefly after viewers/sequences are closed.
				// Retry a few times with backoff rather than failing the entire rebuild.
				try {
					long sleepMs = Math.min(1000L, 40L * attempt);
					Thread.sleep(sleepMs);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}
		throw last != null ? last : new IOException("Failed to move " + tmpPath + " -> " + targetPath);
	}

	private void clearAllAlongTMasks(Experiment exp) {
		for (Capillary cap : exp.getCapillaries().getList()) {
			if (!cap.getKymographBuild())
				continue;
			for (AlongT capT : cap.getAlongTList())
				capT.setMasksList(null);
		}
	}

	private void initArraysToBuildKymographImages(Experiment exp, BuildSeriesOptions options, int kymoImageWidthHint) {
		SequenceCamData seqCamData = exp.getSeqCamData();
		if (seqCamData.getSequence() == null)
			seqCamData.setSequence(exp.getSeqCamData().getImageLoader()
					.initSequenceFromFirstImage(exp.getSeqCamData().getImagesList(true)));
		// Keep kymographs aligned with camera channel count (typically RGB = 3).
		// This preserves the historical behaviour (color kymographs) for multiCAFE.
		final int kymoSizeC = Math.max(1, seqCamData.getSequence().getSizeC());
		int sizex = seqCamData.getSequence().getSizeX();
		int sizey = seqCamData.getSequence().getSizeY();

		long firstMs = seqCamData.getTimeManager().getBinFirst_ms();
		long lastMs = seqCamData.getTimeManager().getBinLast_ms();
		if (lastMs <= firstMs)
			lastMs = exp.getKymoLast_ms();
		if (lastMs <= firstMs)
			firstMs = exp.getKymoFirst_ms();
		long stepMs = exp.getKymoBin_ms();
		int kymoImageWidth = (kymoImageWidthHint > 0) ? kymoImageWidthHint
				: ((stepMs > 0) ? (int) ((lastMs - firstMs) / stepMs + 1) : 1);
		if (kymoImageWidth <= 0)
			kymoImageWidth = (int) ((exp.getKymoLast_ms() - exp.getKymoFirst_ms()) / stepMs + 1);

		int imageHeight = 0;
		for (Capillary cap : exp.getCapillaries().getList()) {
			if (!cap.getKymographBuild())
				continue;
			for (AlongT capT : cap.getAlongTList()) {
				int imageHeight_i = buildMasksCount(capT, sizex, sizey, options);
				if (imageHeight_i > imageHeight)
					imageHeight = imageHeight_i;
			}
			buildCapInteger(cap, exp.getSeqCamData().getSequence(), kymoImageWidth, imageHeight, kymoSizeC);
		}
	}

	private int buildMasksCount(AlongT capT, int sizex, int sizey, BuildSeriesOptions options) {
		ArrayList<ArrayList<int[]>> masks = new ArrayList<ArrayList<int[]>>();
		ArrayList<Point2D> capPoints = ROI2DUtilities.getCapillaryPoints(capT.getRoi());
		getPointsfromROIPolyLineUsingBresenham(capPoints, masks, options.diskRadius, sizex, sizey);
		return masks.size();
	}

	private int buildMasks(AlongT capT, int sizex, int sizey, BuildSeriesOptions options) {
		ArrayList<Point2D> capPoints = ROI2DUtilities.getCapillaryPoints(capT.getRoi());
		ArrayList<ArrayList<int[]>> masks = new ArrayList<ArrayList<int[]>>();
		getPointsfromROIPolyLineUsingBresenham(capPoints, masks, options.diskRadius, sizex, sizey);
		capT.setMasksList(masks);
		return masks.size();
	}

	private void buildCapInteger(Capillary cap, Sequence seq, int imageWidth, int imageHeight, int sizeC) {
		int kymoSizeC = Math.max(1, sizeC);
		cap.setCap_Image(new IcyBufferedImage(imageWidth, imageHeight, kymoSizeC, DataType.UBYTE));

		int len = imageWidth * imageHeight;
		ArrayList<int[]> capInteger = new ArrayList<int[]>(kymoSizeC);
		for (int chan = 0; chan < kymoSizeC; chan++) {
			capInteger.add(new int[len]);
		}
		capIntegerArrays.put(cap, capInteger);
	}

	private void getPointsfromROIPolyLineUsingBresenham(ArrayList<Point2D> pointsList, List<ArrayList<int[]>> masks,
			double diskRadius, int sizex, int sizey) {
		ArrayList<int[]> pixels = Bresenham.getPixelsAlongLineFromROI2D(pointsList);
		int idiskRadius = (int) diskRadius;
		for (int[] pixel : pixels)
			masks.add(getAllPixelsAroundPixel(pixel, idiskRadius, sizex, sizey));
	}

	private ArrayList<int[]> getAllPixelsAroundPixel(int[] pixel, int diskRadius, int sizex, int sizey) {
		ArrayList<int[]> maskAroundPixel = new ArrayList<int[]>();
		double m1 = pixel[0];
		double m2 = pixel[1];
		double radiusSquared = diskRadius * diskRadius;
		int minX = clipValueToLimits(pixel[0] - diskRadius, 0, sizex - 1);
		int maxX = clipValueToLimits(pixel[0] + diskRadius, minX, sizex - 1);
		int minY = pixel[1];
		int maxY = pixel[1];

		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				double dx = x - m1;
				double dy = y - m2;
				double distanceSquared = dx * dx + dy * dy;
				if (distanceSquared <= radiusSquared) {
					maskAroundPixel.add(new int[] { x, y });
				}
			}
		}
		return maskAroundPixel;
	}

	private int clipValueToLimits(int x, int min, int max) {
		if (x < min)
			x = min;
		if (x > max)
			x = max;
		return x;
	}
}
