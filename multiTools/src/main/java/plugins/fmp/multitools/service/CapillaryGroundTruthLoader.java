package plugins.fmp.multitools.service;

import java.awt.geom.Line2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillaries.CapillariesPersistence;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.kernel.roi.roi2d.ROI2DLine;

/**
 * CODEX Read-only file import followed by an explicitly requested, image-0-only
 * apply.
 */
public final class CapillaryGroundTruthLoader {
	private CapillaryGroundTruthLoader() {
	}

	public static File findFile(File directory) throws IOException {
		String[] names = { CapillariesPersistence.GROUND_TRUTH_CSV, "CapillariesDescription_groundtruth.csv",
				"CapillariesDescription_ground_truth.csv", "CapillariesDescription - Copy.csv" };
		for (String name : names) {
			File file = new File(directory, name);
			if (file.isFile())
				return file;
		}
		throw new IOException("No ground truth CSV found in " + directory);
	}

	public static final class Preview {
		private final Map<Capillary, Line2D> matches = new LinkedHashMap<>();
		private final Map<Capillary, Double> widths = new LinkedHashMap<>();
		private final List<String> warnings = new ArrayList<>();

		public int count() {
			return matches.size();
		}

		public String summary() {
			long knownWidths = widths.values().stream().filter(Double::isFinite).count();
			return count() + " capillary measurements matched, including " + knownWidths + " saved widths."
					+ (warnings.isEmpty() ? "" : "\n" + String.join("\n", warnings));
		}

		/**
		 * No files, green ROIs, biological descriptors or later blue keyframes are
		 * changed.
		 */
		public void apply() {
			for (Map.Entry<Capillary, Line2D> entry : matches.entrySet()) {
				Capillary cap = entry.getKey();
				Line2D blue = entry.getValue();
				double width = widths.getOrDefault(cap, Double.NaN);
				if (cap.getPhaseGeometry().isInitialized()) {
					if (Double.isFinite(width))
						cap.getPhaseGeometry().putBlue(0, blue, width);
					else
						cap.getPhaseGeometry().putBlue(0, blue);
				} else
					cap.getPhaseGeometry().initialize(0, ((ROI2DLine) cap.getRoi()).getLine(), blue, width);
				cap.getProperties().setMeasuredEndpoints(blue.getP1(), blue.getP2());
				cap.setPixels((int) Math.round(blue.getP1().distance(blue.getP2())));
				cap.getProperties().setPixelsAutoMeasured(true);
			}
		}
	}

	public static Preview read(File file, Capillaries caps) throws IOException {
		Preview preview = new Preview();
		Map<String, Line2D> tips = new LinkedHashMap<>();
		Map<String, Double> widths = new HashMap<>();
		Set<String> seen = new HashSet<>();
		boolean section = false, headerRead = false;
		CsvLayout layout = null;
		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			String row;
			while ((row = reader.readLine()) != null) {
				if (!section) {
					if (row.startsWith("#;CAPILLARIES;"))
						section = true;
					continue;
				}
				if (row.startsWith("#"))
					break;
				if (row.trim().isEmpty())
					continue;
				if (!headerRead) {
					layout = new CsvLayout(row);
					headerRead = true;
					continue;
				}
				String[] fields = row.split(";", -1);
				String id = fields[0].trim();
				if (!seen.add(id))
					throw new IOException("Duplicate capillary identifier: " + id);
				try {
					if (id.isEmpty())
						throw new IllegalArgumentException();
					tips.put(id, layout.endpoints(fields));
					widths.put(id, layout.width(fields));
				} catch (IllegalArgumentException ex) {
					preview.warnings.add("Invalid or missing endpoints: " + id);
				}
			}
		}
		if (!headerRead)
			throw new IOException("No capillary measurement section in " + file);
		Map<String, Integer> counts = new HashMap<>();
		for (Capillary cap : caps.getList())
			counts.merge(cap.getKymographPrefix(), 1, Integer::sum);
		for (Capillary cap : caps.getList()) {
			String id = cap.getKymographPrefix();
			Line2D line = tips.remove(id);
			if (id == null || counts.get(id) > 1)
				preview.warnings.add("Ambiguous current identifier: " + id);
			else if (line == null)
				preview.warnings.add("No valid ground truth for: " + id);
			else if (!(cap.getRoi() instanceof ROI2DLine))
				preview.warnings.add("Unsupported green ROI: " + id);
			else {
				preview.matches.put(cap, line);
				preview.widths.put(cap, widths.getOrDefault(id, Double.NaN));
			}
		}
		for (String id : tips.keySet())
			preview.warnings.add("Reference capillary not in this experiment: " + id);
		return preview;
	}

	/** Native rows embed ROI points after npoints and may omit four header-only aliases. */
	static final class CsvLayout {
		static final String WIDTH_COLUMN = "cap_width_px";
		final List<String> columns;
		final int endpointsColumn, firstEndpointsColumn, widthColumn, measuredColumn;
		final boolean aliases;

		CsvLayout(String header) throws IOException {
			columns = Arrays.asList(header.split(";", -1));
			measuredColumn = columns.indexOf("cap_measured_x1");
			int lengthColumn = columns.indexOf("cap_length_x1");
			endpointsColumn = lengthColumn >= 0 ? lengthColumn : measuredColumn;
			aliases = measuredColumn >= 0 && lengthColumn >= 0;
			firstEndpointsColumn = aliases ? Math.min(measuredColumn, lengthColumn) : endpointsColumn;
			widthColumn = columns.indexOf(WIDTH_COLUMN);
			String prefix = lengthColumn >= 0 ? "cap_length_" : "cap_measured_";
			if (columns.size() < 18 || !"cap_prefix".equals(columns.get(0))
					|| widthColumn != columns.lastIndexOf(WIDTH_COLUMN)
					|| !"npoints".equals(columns.get(13)) || endpointsColumn < 14
					|| endpointsColumn + 3 >= columns.size()
					|| !columns.subList(endpointsColumn, endpointsColumn + 4)
							.equals(Arrays.asList(prefix + "x1", prefix + "y1", prefix + "x2", prefix + "y2")))
				throw new IOException("Unsupported ground truth coordinate header");
		}

		int fieldIndex(int column, String[] fields) {
			if (fields.length < 18)
				throw new IllegalArgumentException("short capillary row");
			int points = Integer.parseInt(fields[13]);
			if (points < 0 || points > 10000)
				throw new IllegalArgumentException("invalid ROI point count");
			int omitted = aliases && fields.length == columns.size() + 2 * points - 4 ? 4 : 0;
			if (fields.length != columns.size() + 2 * points - omitted)
				throw new IllegalArgumentException("invalid capillary row length");
			return column + (column > 13 ? 2 * points : 0)
					- (omitted > 0 && column >= measuredColumn + 4 ? omitted : 0);
		}

		Line2D endpoints(String[] fields) {
			int first = fieldIndex(endpointsColumn, fields);
			double[] xy = new double[4];
			for (int i = 0; i < xy.length; i++) {
				xy[i] = Double.parseDouble(fields[first + i].trim());
				if (!Double.isFinite(xy[i]))
					throw new IllegalArgumentException("non-finite endpoint");
			}
			Line2D line = new Line2D.Double(xy[0], xy[1], xy[2], xy[3]);
			double length = line.getP1().distance(line.getP2());
			if (!Double.isFinite(length) || length < .5 || length > Integer.MAX_VALUE)
				throw new IllegalArgumentException("invalid capillary length");
			return line;
		}

		double width(String[] fields) {
			if (widthColumn < 0)
				return Double.NaN;
			try {
				double width = Double.parseDouble(fields[fieldIndex(widthColumn, fields)].trim());
				return Double.isFinite(width) && width > 0. ? width : Double.NaN;
			} catch (NumberFormatException e) {
				return Double.NaN;
			}
		}
	}
}
