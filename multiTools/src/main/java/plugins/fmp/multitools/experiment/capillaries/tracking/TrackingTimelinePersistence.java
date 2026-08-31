package plugins.fmp.multitools.experiment.capillaries.tracking;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import plugins.fmp.multitools.tools.Logger;

/** Sidecar persistence for editable tracking boundaries. */
public final class TrackingTimelinePersistence {
	public static final String FILE_NAME = "CapillaryTrackingBoundaries.csv";
	private static final String HEADER = "# multiCAFE capillary tracking boundaries v1";

	private TrackingTimelinePersistence() {}

	public static boolean load(TrackingTimeline timeline, String resultsDirectory) {
		if (timeline == null || resultsDirectory == null)
			return false;
		Path path = Paths.get(resultsDirectory, FILE_NAME);
		if (!Files.isRegularFile(path)) {
			timeline.clear();
			return true;
		}
		List<TrackingBoundary> loaded = new ArrayList<TrackingBoundary>();
		try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.trim().isEmpty() || line.startsWith("#") || line.startsWith("frame;"))
					continue;
				String[] fields = line.split(";", -1);
				if (fields.length < 5)
					continue;
				loaded.add(new TrackingBoundary(Integer.parseInt(fields[0]),
						TrackingBoundary.Origin.valueOf(fields[1]), TrackingBoundary.Status.valueOf(fields[2]),
						decode(fields[4]), Double.parseDouble(fields[3])));
			}
			timeline.replaceAll(loaded);
			return true;
		} catch (Exception e) {
			Logger.warn("Cannot load tracking boundaries from " + path + ": " + e.getMessage());
			return false;
		}
	}

	public static boolean save(TrackingTimeline timeline, String resultsDirectory) {
		if (timeline == null || resultsDirectory == null)
			return false;
		Path path = Paths.get(resultsDirectory, FILE_NAME);
		try {
			Files.createDirectories(path.getParent());
			try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				writer.write(HEADER);
				writer.newLine();
				writer.write("frame;origin;status;score;reason_base64");
				writer.newLine();
				for (TrackingBoundary boundary : timeline.getBoundaries()) {
					writer.write(Integer.toString(boundary.getFrame()));
					writer.write(';');
					writer.write(boundary.getOrigin().name());
					writer.write(';');
					writer.write(boundary.getStatus().name());
					writer.write(';');
					writer.write(Double.toString(boundary.getScore()));
					writer.write(';');
					writer.write(encode(boundary.getReason()));
					writer.newLine();
				}
			}
			return true;
		} catch (IOException e) {
			Logger.warn("Cannot save tracking boundaries to " + path + ": " + e.getMessage());
			return false;
		}
	}

	private static String encode(String text) {
		return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
	}

	private static String decode(String text) {
		return new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
	}
}
