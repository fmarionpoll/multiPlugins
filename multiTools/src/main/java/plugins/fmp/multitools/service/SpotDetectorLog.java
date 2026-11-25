package plugins.fmp.multitools.service;

import java.io.File;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.series.MeasurementProgress;

/** Identifies the experiment in detector warnings. The batch index is present only during a series run. */
final class SpotDetectorLog {
	private SpotDetectorLog() {
	}

	static String maskFailure(String detector, Experiment exp, String spotName, String detail) {
		StringBuilder message = new StringBuilder();
		message.append(detector).append(": failed to build mask for spot ").append(spotName);
		MeasurementProgress.ListPosition position = MeasurementProgress.listPosition();
		if (position != null && position.index > 0)
			message.append(", experiment ").append(position.index).append('/').append(position.size);
		message.append(", directory ").append(directoryLabel(exp));
		if (detail != null && !detail.isEmpty())
			message.append(" - ").append(detail);
		return message.toString();
	}

	static String directoryLabel(Experiment exp) {
		String path = imageDirectory(exp);
		if (path == null)
			path = experimentDirectoryFromResults(exp);
		if (path == null)
			return "unknown";
		String name = new File(path).getName();
		if (name == null || name.isEmpty() || name.equals(path))
			return path;
		return name + " (" + path + ")";
	}

	private static String imageDirectory(Experiment exp) {
		if (exp == null)
			return null;
		String images = blankToNull(exp.getImagesDirectory());
		if (images == null && exp.getSeqCamData() != null)
			images = blankToNull(exp.getSeqCamData().getImagesDirectory());
		return images;
	}

	private static String experimentDirectoryFromResults(Experiment exp) {
		if (exp == null)
			return null;
		String results = blankToNull(exp.getResultsDirectory());
		if (results == null)
			return null;
		File parent = new File(results).getParentFile();
		return parent == null ? results : parent.getPath();
	}

	private static String blankToNull(String value) {
		if (value == null)
			return null;
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
