package plugins.fmp.multitools.experiment.timebase;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import plugins.fmp.multitools.experiment.sequence.ImageLoader;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.tools.Logger;

/**
 * Per-frame analysis timescale: relative timestamps \(t[i]\) and aligned image
 * basenames for the experiment analysis interval. Persisted under
 * {@code results/FrameTimeScale.csv} so charts/export work without an open cam stack.
 */
public final class FrameTimeScale {

	public static final String FILENAME = "FrameTimeScale.csv";
	private static final String CSV_VERSION = "1.0";

	private long t0EpochMs = -1;
	private long absFirst = 0;
	private long absEndExclusive = -1;
	private long[] relativeMs = new long[0];
	private String[] imageFiles = new String[0];

	public int size() {
		return relativeMs != null ? relativeMs.length : 0;
	}

	public long getT0EpochMs() {
		return t0EpochMs;
	}

	public long getRelativeMs(int i) {
		return relativeMs[i];
	}

	public long[] getRelativeMsCopy() {
		return relativeMs != null ? Arrays.copyOf(relativeMs, relativeMs.length) : new long[0];
	}

	/** Unmodifiable view for callers that already own the Experiment lifetime. */
	public long[] getRelativeMs() {
		return relativeMs;
	}

	public String getImageRelativePath(int i) {
		return imageFiles[i];
	}

	public long getAbsoluteFirstIndex() {
		return absFirst;
	}

	public long getAbsoluteEndExclusive() {
		return absEndExclusive;
	}

	public boolean isEmpty() {
		return size() == 0;
	}

	public double[] toMinutes() {
		int n = size();
		double[] minutes = new double[n];
		for (int i = 0; i < n; i++) {
			minutes[i] = relativeMs[i] / 60000.0;
		}
		return minutes;
	}

	/**
	 * Median of consecutive \(\Delta t\) in ms, or -1 if unavailable.
	 */
	public long medianDeltaMs() {
		int n = size();
		if (n < 2) {
			return -1;
		}
		long[] deltas = new long[n - 1];
		for (int i = 1; i < n; i++) {
			deltas[i - 1] = relativeMs[i] - relativeMs[i - 1];
		}
		Arrays.sort(deltas);
		int mid = deltas.length / 2;
		return (deltas.length % 2 == 1) ? deltas[mid] : (deltas[mid - 1] + deltas[mid]) / 2;
	}

	public static FrameTimeScale fromSeqCamData(SequenceCamData seqCamData, long t0EpochMs) {
		FrameTimeScale scale = new FrameTimeScale();
		if (seqCamData == null || seqCamData.getImageLoader() == null) {
			return scale;
		}
		ImageLoader loader = seqCamData.getImageLoader();
		int n = loader.getNTotalFrames();
		if (n <= 0) {
			return scale;
		}
		scale.t0EpochMs = t0EpochMs;
		scale.absFirst = loader.getAbsoluteIndexFirstImage();
		long fixed = loader.getFixedNumberOfImages();
		scale.absEndExclusive = fixed > 0 ? fixed : (scale.absFirst + n);
		scale.relativeMs = new long[n];
		scale.imageFiles = new String[n];
		for (int i = 0; i < n; i++) {
			java.nio.file.attribute.FileTime ft = seqCamData.getFileTimeFromStructuredName(i);
			long absMs = ft != null ? ft.toMillis() : (t0EpochMs + i * 60000L);
			scale.relativeMs[i] = absMs - t0EpochMs;
			String path = loader.getFileNameFromImageList(i);
			scale.imageFiles[i] = basename(path);
		}
		return scale;
	}

	public static FrameTimeScale fromArrays(long t0EpochMs, long[] relativeMs, String[] imageFiles, long absFirst,
			long absEndExclusive) {
		FrameTimeScale scale = new FrameTimeScale();
		scale.t0EpochMs = t0EpochMs;
		scale.relativeMs = relativeMs != null ? Arrays.copyOf(relativeMs, relativeMs.length) : new long[0];
		if (imageFiles != null && imageFiles.length == scale.relativeMs.length) {
			scale.imageFiles = Arrays.copyOf(imageFiles, imageFiles.length);
		} else {
			scale.imageFiles = new String[scale.relativeMs.length];
			Arrays.fill(scale.imageFiles, "");
		}
		scale.absFirst = absFirst;
		scale.absEndExclusive = absEndExclusive;
		return scale;
	}

	/**
	 * True if stored basenames match the current analysis-interval files (order-sensitive).
	 */
	public boolean matchesSeqCamData(SequenceCamData seqCamData) {
		if (seqCamData == null || isEmpty()) {
			return false;
		}
		ImageLoader loader = seqCamData.getImageLoader();
		int n = loader.getNTotalFrames();
		if (n != size()) {
			return false;
		}
		for (int i = 0; i < n; i++) {
			String path = loader.getFileNameFromImageList(i);
			String base = basename(path);
			String stored = imageFiles[i] != null ? imageFiles[i] : "";
			if (!stored.equals(base)) {
				return false;
			}
		}
		return true;
	}

	public boolean save(String resultsDirectory) {
		if (resultsDirectory == null || resultsDirectory.isEmpty() || isEmpty()) {
			return false;
		}
		File dir = new File(resultsDirectory);
		if (!dir.exists() && !dir.mkdirs()) {
			Logger.warn("FrameTimeScale: cannot create results directory " + resultsDirectory);
			return false;
		}
		File out = new File(dir, FILENAME);
		try (FileWriter writer = new FileWriter(out)) {
			writer.write("#;version;" + CSV_VERSION + "\n");
			writer.write("#;t0EpochMs;" + t0EpochMs + "\n");
			writer.write("#;absFirst;" + absFirst + "\n");
			writer.write("#;absEnd;" + absEndExclusive + "\n");
			writer.write("#;index;relative_ms;image_file\n");
			for (int i = 0; i < relativeMs.length; i++) {
				String file = imageFiles[i] != null ? imageFiles[i] : "";
				writer.write(i + ";" + relativeMs[i] + ";" + file + "\n");
			}
			return true;
		} catch (IOException e) {
			Logger.warn("FrameTimeScale: save failed: " + e.getMessage());
			return false;
		}
	}

	public static FrameTimeScale load(String resultsDirectory) {
		if (resultsDirectory == null || resultsDirectory.isEmpty()) {
			return null;
		}
		File in = new File(resultsDirectory, FILENAME);
		if (!in.isFile()) {
			return null;
		}
		try (BufferedReader reader = new BufferedReader(new FileReader(in))) {
			String line;
			long t0 = -1;
			long absFirst = 0;
			long absEnd = -1;
			List<Long> times = new ArrayList<>();
			List<String> files = new ArrayList<>();
			boolean inData = false;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}
				if (line.startsWith("#")) {
					String[] parts = line.split(";");
					if (parts.length >= 3) {
						String key = parts[1];
						if ("t0EpochMs".equals(key)) {
							t0 = Long.parseLong(parts[2]);
						} else if ("absFirst".equals(key)) {
							absFirst = Long.parseLong(parts[2]);
						} else if ("absEnd".equals(key)) {
							absEnd = Long.parseLong(parts[2]);
						} else if ("index".equals(key)) {
							inData = true;
						}
					}
					continue;
				}
				if (!inData) {
					inData = true;
				}
				String[] parts = line.split(";", -1);
				if (parts.length < 2) {
					continue;
				}
				times.add(Long.parseLong(parts[1]));
				files.add(parts.length >= 3 ? parts[2] : "");
			}
			if (times.isEmpty()) {
				return null;
			}
			long[] rel = new long[times.size()];
			String[] names = new String[files.size()];
			for (int i = 0; i < times.size(); i++) {
				rel[i] = times.get(i);
				names[i] = files.get(i);
			}
			return fromArrays(t0, rel, names, absFirst, absEnd);
		} catch (Exception e) {
			Logger.warn("FrameTimeScale: load failed: " + e.getMessage());
			return null;
		}
	}

	private static String basename(String path) {
		if (path == null || path.isEmpty()) {
			return "";
		}
		try {
			return Paths.get(path).getFileName().toString();
		} catch (Exception e) {
			int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
			return slash >= 0 ? path.substring(slash + 1) : path;
		}
	}
}
