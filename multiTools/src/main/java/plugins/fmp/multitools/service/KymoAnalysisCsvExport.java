package plugins.fmp.multitools.service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.tools.Logger;

/**
 * Writes per-spot kymograph analysis series (green height and h/h_max) to a CSV file in the kymographs bin.
 */
public final class KymoAnalysisCsvExport {

	private KymoAnalysisCsvExport() {
	}

	public static final String DEFAULT_FILENAME = "kymo_analysis_green.csv";

	/**
	 * @return written file path, or null on failure
	 */
	public static Path write(Path outputFile, KymoAnalysisResult result) {
		if (outputFile == null || result == null || result.byCageId.isEmpty()) {
			return null;
		}
		double[] xMin = result.xAxisMinutes;
		try {
			Path parent = outputFile.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			try (BufferedWriter w = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
				w.write(
						"cage_id,spot_index,spot_name,stimulus,concentration,time_min,green_height_rows,green_height_ratio,fraction");
				w.newLine();
				for (Map.Entry<Integer, List<KymoAnalysisResult.SpotKymoSeries>> e : result.byCageId.entrySet()) {
					int cageId = e.getKey();
					for (KymoAnalysisResult.SpotKymoSeries row : e.getValue()) {
						writeSpotRows(w, cageId, row, xMin);
					}
				}
			}
			return outputFile;
		} catch (IOException ex) {
			Logger.warn("KymoAnalysisCsvExport: write failed: " + outputFile, ex);
			return null;
		}
	}

	private static void writeSpotRows(BufferedWriter w, int cageId, KymoAnalysisResult.SpotKymoSeries row,
			double[] xMin) throws IOException {
		if (row == null) {
			return;
		}
		String spotName = row.spot != null && row.spot.getName() != null ? row.spot.getName() : "";
		String stimulus = "";
		String concentration = "";
		if (row.spot != null && row.spot.getProperties() != null) {
			stimulus = row.spot.getProperties().getStimulus() != null ? row.spot.getProperties().getStimulus() : "";
			concentration = row.spot.getProperties().getConcentration() != null
					? row.spot.getProperties().getConcentration()
					: "";
		}
		int n = xMin != null ? xMin.length : 0;
		n = Math.min(n, row.greenHeight.length);
		n = Math.min(n, row.greenHeightRatio.length);
		n = Math.min(n, row.fraction.length);
		for (int j = 0; j < n; j++) {
			w.write(Integer.toString(cageId));
			w.write(',');
			w.write(Integer.toString(row.indexInCage));
			w.write(',');
			w.write(csvEscape(spotName));
			w.write(',');
			w.write(csvEscape(stimulus));
			w.write(',');
			w.write(csvEscape(concentration));
			w.write(',');
			w.write(formatDouble(xMin[j]));
			w.write(',');
			w.write(Integer.toString(row.greenHeight[j]));
			w.write(',');
			w.write(formatDouble(row.greenHeightRatio[j]));
			w.write(',');
			w.write(formatDouble(row.fraction[j]));
			w.newLine();
		}
	}

	private static String csvEscape(String s) {
		if (s == null) {
			return "";
		}
		if (s.indexOf(',') < 0 && s.indexOf('"') < 0) {
			return s;
		}
		return '"' + s.replace("\"", "\"\"") + '"';
	}

	private static String formatDouble(double v) {
		if (Double.isFinite(v)) {
			return Double.toString(v);
		}
		return "";
	}
}
