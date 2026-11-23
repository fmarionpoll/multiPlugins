package plugins.fmp.multitools.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.service.CageKymographStripLayoutCsv.PersistedKymoGrid;
import plugins.fmp.multitools.tools.Logger;

/** Source camera timestamps for the columns of cage kymographs. */
public final class CageKymographTimeline {

	public static final String FILENAME = "CageKymographColumnTimes.csv";

	private static final Set<String> REPORTED_FILE_MISMATCHES = Collections.newSetFromMap(new ConcurrentHashMap<>());

	private CageKymographTimeline() { }

	/**
	 * How elapsed times were assigned to kymograph columns.
	 * {@link Basis#COLUMN_FILE} and {@link Basis#CAMERA_FRAME} are acquisition times.
	 * {@link Basis#NOMINAL_BIN} is the historical uniform interval.
	 * {@link Basis#COLUMN_FILE_REJECTED} means a column-time file was present but did not
	 * describe every column, so no substitute clock was invented.
	 */
	public enum Basis {
		COLUMN_FILE("column_file"),
		CAMERA_FRAME("camera_frame"),
		NOMINAL_BIN("nominal_bin"),
		COLUMN_FILE_REJECTED("column_file_rejected");

		public final String exportId;

		Basis(String exportId) {
			this.exportId = exportId;
		}
	}

	/** Elapsed milliseconds for each kymograph column, and the rule that produced them. */
	public static final class Clock {
		public final Basis basis;
		/** Null when {@link Basis#COLUMN_FILE_REJECTED}. */
		public final long[] elapsedMs;

		Clock(Basis basis, long[] elapsedMs) {
			this.basis = basis;
			this.elapsedMs = elapsedMs;
		}
	}

	public static void write(String directory, long[] cameraMs, List<Integer> frames) {
		List<String> lines = new ArrayList<>();
		lines.add("column;elapsed_ms");
		for (int col = 0; col < frames.size(); col++) {
			int frame = frames.get(col);
			if (cameraMs == null || frame < 0 || frame >= cameraMs.length) {
				throw new IllegalArgumentException("Missing camera timestamp for kymograph column " + col);
			}
			lines.add(col + ";" + cameraMs[frame]);
		}
		try {
			Files.write(Paths.get(directory, FILENAME), lines, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException("Cannot save kymograph column timestamps", e);
		}
	}

	/**
	 * Elapsed time of each kymograph column.
	 * A column-time file that exists but does not list {@code columnCount} valid rows is rejected:
	 * camera timestamps and the nominal interval are not substituted for it.
	 *
	 * @param cameraElapsedMs relative acquisition times, or null to read them from {@code exp}
	 */
	public static Clock resolve(Experiment exp, int columnCount, long[] cameraElapsedMs) {
		if (columnCount < 1) {
			return new Clock(Basis.NOMINAL_BIN, new long[0]);
		}
		String directory = exp != null ? exp.getKymosBinFullDirectory() : null;
		FileLoad file = read(directory, columnCount);
		if (file.status == FileStatus.INVALID) {
			reportFileMismatch(directory, columnCount, file.reason);
			return new Clock(Basis.COLUMN_FILE_REJECTED, null);
		}
		if (file.status == FileStatus.VALID) {
			return new Clock(Basis.COLUMN_FILE, file.times);
		}
		long[] camera = cameraElapsedMs != null ? cameraElapsedMs : cameraElapsed(exp);
		PersistedKymoGrid grid = CageKymographStripLayoutCsv.readPersistedKymoGridOrNull(directory, columnCount);
		long first = grid != null ? grid.firstMs : exp != null ? Math.max(0L, exp.getKymoFirst_ms()) : 0L;
		long last = grid != null ? grid.lastMs : exp != null ? exp.getKymoLast_ms() : 0L;
		int factor = exp != null ? exp.getKymoSubsampleFactor() : 1;
		int[] frames = sourceFrames(columnCount, camera, first, last, factor);
		if (frames != null) {
			long[] times = new long[columnCount];
			for (int i = 0; i < columnCount; i++) {
				times[i] = camera[frames[i]];
			}
			return new Clock(Basis.CAMERA_FRAME, times);
		}
		long step = grid != null ? grid.stepMs : exp != null ? exp.getKymoBin_ms() : 60_000L;
		if (step <= 0L) {
			step = 60_000L;
		}
		long[] nominal = new long[columnCount];
		for (int i = 0; i < columnCount; i++) {
			nominal[i] = first + (long) i * step;
		}
		return new Clock(Basis.NOMINAL_BIN, nominal);
	}

	/**
	 * Source camera frame of each kymograph column, or null when the series width does not match
	 * the camera window one-to-one or by the saved subsample factor.
	 */
	public static int[] sourceFrames(int width, long[] cameraTimes, long startMs, long endMs, int factor) {
		if (width < 1 || cameraTimes == null || cameraTimes.length == 0) {
			return null;
		}
		int start = 0;
		int end = cameraTimes.length;
		if (endMs > startMs) {
			while (start < end && cameraTimes[start] < startMs) {
				start++;
			}
			while (end > start && cameraTimes[end - 1] > endMs) {
				end--;
			}
		}
		int firstFrame = end > start ? start : 0;
		int frameCount = end > start ? end - start : cameraTimes.length;
		int step = Math.max(1, factor);
		boolean direct = width == frameCount;
		boolean subsampled = frameCount > 0 && width == 1 + (frameCount - 1) / step;
		if (!direct && !subsampled) {
			return null;
		}
		int[] frames = new int[width];
		for (int column = 0; column < width; column++) {
			int frame = direct ? firstFrame + column : firstFrame + column * step;
			if (frame < 0 || frame >= cameraTimes.length) {
				return null;
			}
			frames[column] = frame;
		}
		return frames;
	}

	private static long[] cameraElapsed(Experiment exp) {
		if (exp == null) {
			return null;
		}
		long[] camera = exp.getCamImages_ms();
		if ((camera == null || camera.length == 0) && exp.getSeqCamData() != null) {
			camera = exp.getSeqCamData().getTimeManager().getCamImagesTime_Ms();
		}
		if (camera == null || camera.length == 0) {
			exp.ensureFrameTimeScale();
			camera = exp.getCamImages_ms();
		}
		return camera;
	}

	private static void reportFileMismatch(String directory, int columnCount, String reason) {
		String key = String.valueOf(directory) + "|" + columnCount + "|" + reason;
		if (!REPORTED_FILE_MISMATCHES.add(key)) {
			return;
		}
		Logger.error("CageKymographColumnTimes.csv in " + directory + " does not match " + columnCount
				+ " kymograph columns (" + reason
				+ "). Acquisition timestamps were not replaced with a nominal interval.");
	}

	private enum FileStatus {
		ABSENT, VALID, INVALID
	}

	private static final class FileLoad {
		final FileStatus status;
		final long[] times;
		final String reason;

		FileLoad(FileStatus status, long[] times, String reason) {
			this.status = status;
			this.times = times;
			this.reason = reason;
		}
	}

	private static FileLoad read(String directory, int count) {
		if (directory == null) {
			return new FileLoad(FileStatus.ABSENT, null, null);
		}
		Path path = Paths.get(directory, FILENAME);
		if (!Files.isRegularFile(path)) {
			return new FileLoad(FileStatus.ABSENT, null, null);
		}
		try {
			List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
			if (lines.isEmpty() || !"column;elapsed_ms".equals(lines.get(0))) {
				return new FileLoad(FileStatus.INVALID, null, "missing column;elapsed_ms header");
			}
			int found = lines.size() - 1;
			if (found != count) {
				return new FileLoad(FileStatus.INVALID, null, "found " + found + " rows");
			}
			long[] times = new long[count];
			for (int i = 0; i < count; i++) {
				String[] fields = lines.get(i + 1).split(";");
				if (fields.length != 2 || Integer.parseInt(fields[0]) != i) {
					return new FileLoad(FileStatus.INVALID, null, "row " + i + " is not column " + i);
				}
				times[i] = Long.parseLong(fields[1]);
				if (times[i] < 0 || (i > 0 && times[i] < times[i - 1])) {
					return new FileLoad(FileStatus.INVALID, null, "timestamps are not increasing");
				}
			}
			return new FileLoad(FileStatus.VALID, times, null);
		} catch (IOException | NumberFormatException e) {
			Logger.error("Cannot read kymograph column timestamps " + path, e);
			return new FileLoad(FileStatus.INVALID, null, "unreadable");
		}
	}

	public static double[] minutes(Experiment exp, int count) {
		if (count <= 0) {
			return new double[0];
		}
		Clock clock = resolve(exp, count, null);
		double[] minutes = new double[count];
		if (clock.elapsedMs == null) {
			java.util.Arrays.fill(minutes, Double.NaN);
			return minutes;
		}
		for (int i = 0; i < count; i++) {
			minutes[i] = clock.elapsedMs[i] / 60_000.0;
		}
		return minutes;
	}
}
