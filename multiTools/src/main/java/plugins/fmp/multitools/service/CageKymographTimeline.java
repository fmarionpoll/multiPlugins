package plugins.fmp.multitools.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.service.CageKymographStripLayoutCsv.PersistedKymoGrid;
import plugins.fmp.multitools.tools.Logger;

/** Source camera timestamps for the columns of cage kymographs. */
public final class CageKymographTimeline {

	public static final String FILENAME = "CageKymographColumnTimes.csv";

	private CageKymographTimeline() { }

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

	private static long[] read(String directory, int count) {
		if (directory == null) return null;
		Path path = Paths.get(directory, FILENAME);
		if (!Files.isRegularFile(path)) return null;
		try {
			List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
			if (lines.size() != count + 1 || !"column;elapsed_ms".equals(lines.get(0))) return null;
			long[] times = new long[count];
			for (int i = 0; i < count; i++) {
				String[] fields = lines.get(i + 1).split(";");
				if (fields.length != 2 || Integer.parseInt(fields[0]) != i) return null;
				times[i] = Long.parseLong(fields[1]);
				if (times[i] < 0 || (i > 0 && times[i] < times[i - 1])) return null;
			}
			return times;
		} catch (IOException | NumberFormatException e) {
			Logger.warn("Cannot read kymograph column timestamps " + path, e);
			return null;
		}
	}

	public static double[] minutes(Experiment exp, int count) {
		if (count <= 0) return new double[0];
		String directory = exp != null ? exp.getKymosBinFullDirectory() : null;
		long[] times = read(directory, count);
		PersistedKymoGrid grid = CageKymographStripLayoutCsv.readPersistedKymoGridOrNull(directory, count);
		long first = grid != null ? grid.firstMs : exp != null ? Math.max(0L, exp.getKymoFirst_ms()) : 0L;
		long step = grid != null ? grid.stepMs : exp != null ? exp.getKymoBin_ms() : 60_000L;
		// Older native-frame kymographs saved the nominal bin step by mistake.
		// Recover their timestamps only when the camera window matches every column.
		if (times == null && exp != null) {
			long[] camera = exp.getCamImages_ms();
			if ((camera == null || camera.length == 0) && exp.getSeqCamData() != null) {
				camera = exp.getSeqCamData().getTimeManager().getCamImagesTime_Ms();
			}
			if (camera == null || camera.length == 0) {
				exp.ensureFrameTimeScale();
				camera = exp.getCamImages_ms();
			}
			long last = grid != null ? grid.lastMs : exp.getKymoLast_ms();
			List<Long> selected = new ArrayList<>();
			if (camera != null) {
				for (long time : camera) {
					if (time >= first && (last <= first || time <= last)) selected.add(time);
				}
			}
			if (selected.size() == count) {
				times = new long[count];
				for (int i = 0; i < count; i++) times[i] = selected.get(i);
			}
		}
		double[] minutes = new double[count];
		for (int i = 0; i < count; i++) {
			minutes[i] = (times != null ? times[i] : first + (long) i * Math.max(1L, step)) / 60_000.0;
		}
		return minutes;
	}
}
