package plugins.fmp.multitools.service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cages.Cages;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.tools.Comparators;
import plugins.fmp.multitools.tools.Logger;

/**
 * Persists vertical strip layout (stacked spot band heights) for cage kymograph TIFFs in the bin
 * directory next to {@code kymocage_*.tif*}. Written when kymographs are built; pick/analysis read
 * it so layout stays correct if spot ROIs are edited later. Falls back to
 * {@link CageKymographSpotBands#layout} when the file is missing or does not match the experiment.
 * <p>
 * Meta also stores the kymograph column time grid ({@code kymo_first_ms}, {@code kymo_last_ms},
 * {@code kymo_step_ms}) used at export so column→camera-frame mapping stays correct when the
 * camera interval differs from the kymograph bin (nearest-frame sampling at build time).
 * <p>
 * Spot names must not contain {@code ';'}.
 */
public final class CageKymographStripLayoutCsv {

	public static final String FILENAME = "CageKymographStripLayout.csv";

	private static final String VERSION_LINE = "#;version;2";

	/** Time grid and width from {@link CageKymographStripLayoutCsv} when present and matching image width. */
	public static final class PersistedKymoGrid {
		public final long firstMs;
		public final long lastMs;
		public final long stepMs;
		public final int columnCount;

		public PersistedKymoGrid(long firstMs, long lastMs, long stepMs, int columnCount) {
			this.firstMs = firstMs;
			this.lastMs = lastMs;
			this.stepMs = stepMs;
			this.columnCount = columnCount;
		}
	}

	private CageKymographStripLayoutCsv() {
	}

	private static final class Meta {
		final int refCamWidth;
		final int refCamHeight;
		final int kymographColumnCount;
		final long kymoFirstMs;
		final long kymoLastMs;
		final long kymoStepMs;

		Meta(int refCamWidth, int refCamHeight, int kymographColumnCount, long kymoFirstMs, long kymoLastMs,
				long kymoStepMs) {
			this.refCamWidth = refCamWidth;
			this.refCamHeight = refCamHeight;
			this.kymographColumnCount = kymographColumnCount;
			this.kymoFirstMs = kymoFirstMs;
			this.kymoLastMs = kymoLastMs;
			this.kymoStepMs = kymoStepMs;
		}
	}

	private static final class Row {
		final int stripIndex;
		final String spotName;
		final int heightPx;
		final boolean geometryMissing;

		Row(int stripIndex, String spotName, int heightPx, boolean geometryMissing) {
			this.stripIndex = stripIndex;
			this.spotName = spotName;
			this.heightPx = heightPx;
			this.geometryMissing = geometryMissing;
		}
	}

	/**
	 * Writes layout for every cage that has at least one spot (same cages as
	 * {@link CageSpotKymographBuilder} exports). Persists the kymograph time grid used for each column
	 * (same values as {@link CageSpotKymographBuilder}).
	 */
	public static void write(String binDirectory, Cages cages, Spots spots, int refCamWidth, int refCamHeight,
			int kymographColumnCount, long kymoFirstMs, long kymoLastMs, long kymoStepMs) {
		if (binDirectory == null || cages == null || cages.cagesList == null || spots == null) {
			return;
		}
		if (refCamWidth <= 0 || refCamHeight <= 0 || kymographColumnCount <= 0) {
			return;
		}
		if (kymoStepMs <= 0 || kymoLastMs <= kymoFirstMs) {
			Logger.warn("CageKymographStripLayoutCsv: skip write, invalid kymograph time grid");
			return;
		}
		Path out = Paths.get(binDirectory, FILENAME);
		try {
			Files.createDirectories(out.getParent());
		} catch (IOException e) {
			Logger.warn("CageKymographStripLayoutCsv: cannot create directory " + out.getParent(), e);
			return;
		}
		try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
			w.write(VERSION_LINE);
			w.newLine();
			w.write("#;META;ref_cam_width;" + refCamWidth + ";ref_cam_height;" + refCamHeight
					+ ";kymograph_column_count;" + kymographColumnCount + ";kymo_first_ms;" + kymoFirstMs
					+ ";kymo_last_ms;" + kymoLastMs + ";kymo_step_ms;" + kymoStepMs);
			w.newLine();
			w.write(
					"#;file_base;strip_index;spot_name;height_px;geometry_missing (1 = placeholder row at build time)");
			w.newLine();
			for (Cage cage : cages.cagesList) {
				List<Spot> raw = cage != null ? cage.getSpotList(spots) : null;
				if (raw == null || raw.isEmpty()) {
					continue;
				}
				String fileBase = KymocageCageResolver.kymocageFileBaseForCage(cage, cages);
				if (fileBase == null) {
					continue;
				}
				List<CageKymographSpotBands> bands = CageKymographSpotBands.layout(cage, spots, refCamWidth,
						refCamHeight);
				int si = 0;
				for (CageKymographSpotBands b : bands) {
					String name = b.spot != null ? b.spot.getName() : "";
					if (name.indexOf(';') >= 0) {
						Logger.warn("CageKymographStripLayoutCsv: spot name contains ';', skipping layout file write");
						return;
					}
					int h = b.height();
					w.write(fileBase + ";" + si + ";" + name + ";" + h + ";" + (b.geometryMissing ? 1 : 0));
					w.newLine();
					si++;
				}
			}
		} catch (IOException e) {
			Logger.warn("CageKymographStripLayoutCsv: write failed " + out, e);
		}
	}

	/**
	 * Bands for one cage kymograph file, or null to fall back to ROI-based {@link CageKymographSpotBands#layout}.
	 *
	 * @param kymographColumnCount logical kymograph width (columns); when {@code refCamWidth} or
	 *            {@code refCamHeight} are &lt;= 0, only column count is checked against the file meta.
	 */
	public static List<CageKymographSpotBands> readBandsOrNull(String binDirectory, String kymocageFileBase,
			Cage cage, Spots spots, int refCamWidth, int refCamHeight, int kymographColumnCount) {
		if (binDirectory == null || kymocageFileBase == null || cage == null || spots == null
				|| kymographColumnCount <= 0) {
			return null;
		}
		Path path = Paths.get(binDirectory, FILENAME);
		if (!Files.isRegularFile(path)) {
			return null;
		}
		Parsed parsed = parseFile(path);
		if (parsed == null || parsed.meta == null) {
			return null;
		}
		if (!metaMatches(parsed.meta, refCamWidth, refCamHeight, kymographColumnCount)) {
			return null;
		}
		List<Row> rows = parsed.rowsByBase.get(kymocageFileBase);
		if (rows == null || rows.isEmpty()) {
			return null;
		}
		rows.sort(Comparator.comparingInt(r -> r.stripIndex));
		ArrayList<Spot> sorted = new ArrayList<>(cage.getSpotList(spots));
		if (sorted.isEmpty()) {
			return null;
		}
		Collections.sort(sorted, new Comparators.Spot_Name());
		if (rows.size() != sorted.size()) {
			return null;
		}
		List<CageKymographSpotBands> out = new ArrayList<>(rows.size());
		int y = 0;
		for (int i = 0; i < rows.size(); i++) {
			Row r = rows.get(i);
			if (r.stripIndex != i) {
				return null;
			}
			Spot spot = sorted.get(i);
			if (spot == null || !r.spotName.equals(spot.getName())) {
				return null;
			}
			if (r.heightPx <= 0) {
				return null;
			}
			int y0 = y;
			y += r.heightPx;
			out.add(new CageKymographSpotBands(spot, y0, y, r.geometryMissing));
		}
		return out;
	}

	/**
	 * Kymograph column time parameters stored next to cage kymograph TIFFs, or null if the file is
	 * missing, is legacy without time fields, or {@code kymographImageWidth} does not match.
	 */
	/**
	 * Camera width/height stored when kymographs were built ({@code ref_cam_width/height} in
	 * {@link #FILENAME}), or {@code null} if the layout file is missing.
	 */
	public static int[] readRefCameraDimensionsOrNull(String binDirectory) {
		if (binDirectory == null || binDirectory.isEmpty()) {
			return null;
		}
		Path path = Paths.get(binDirectory, FILENAME);
		if (!Files.isRegularFile(path)) {
			return null;
		}
		Parsed parsed = parseFile(path);
		if (parsed == null || parsed.meta == null) {
			return null;
		}
		if (parsed.meta.refCamWidth <= 0 || parsed.meta.refCamHeight <= 0) {
			return null;
		}
		return new int[] { parsed.meta.refCamWidth, parsed.meta.refCamHeight };
	}

	public static PersistedKymoGrid readPersistedKymoGridOrNull(String binDirectory, int kymographImageWidth) {
		if (binDirectory == null || binDirectory.isEmpty() || kymographImageWidth <= 0) {
			return null;
		}
		Path path = Paths.get(binDirectory, FILENAME);
		if (!Files.isRegularFile(path)) {
			return null;
		}
		Parsed parsed = parseFile(path);
		if (parsed == null || parsed.meta == null) {
			return null;
		}
		Meta m = parsed.meta;
		if (m.kymographColumnCount != kymographImageWidth) {
			return null;
		}
		if (m.kymoStepMs <= 0 || m.kymoLastMs <= m.kymoFirstMs) {
			return null;
		}
		return new PersistedKymoGrid(m.kymoFirstMs, m.kymoLastMs, m.kymoStepMs, m.kymographColumnCount);
	}

	private static boolean metaMatches(Meta meta, int refCamWidth, int refCamHeight, int kymographColumnCount) {
		if (meta.kymographColumnCount != kymographColumnCount) {
			return false;
		}
		if (refCamWidth > 0 && refCamHeight > 0) {
			return meta.refCamWidth == refCamWidth && meta.refCamHeight == refCamHeight;
		}
		return true;
	}

	private static final class Parsed {
		final Meta meta;
		final Map<String, List<Row>> rowsByBase;

		Parsed(Meta meta, Map<String, List<Row>> rowsByBase) {
			this.meta = meta;
			this.rowsByBase = rowsByBase;
		}
	}

	private static Parsed parseFile(Path path) {
		try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			String line;
			Meta meta = null;
			Map<String, List<Row>> byBase = new HashMap<>();
			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}
				if (line.startsWith("#;version;")) {
					continue;
				}
				if (line.startsWith("#;META;")) {
					meta = parseMetaLine(line);
					continue;
				}
				if (line.startsWith("#;")) {
					continue;
				}
				String[] p = line.split(";", -1);
				if (p.length != 5) {
					Logger.warn("CageKymographStripLayoutCsv: bad row in " + path);
					return null;
				}
				try {
					int si = Integer.parseInt(p[1]);
					int h = Integer.parseInt(p[3]);
					int gm = Integer.parseInt(p[4]);
					if (gm != 0 && gm != 1) {
						return null;
					}
					Row row = new Row(si, p[2], h, gm == 1);
					byBase.computeIfAbsent(p[0], k -> new ArrayList<>()).add(row);
				} catch (NumberFormatException e) {
					Logger.warn("CageKymographStripLayoutCsv: bad number in " + path, e);
					return null;
				}
			}
			if (meta == null) {
				return null;
			}
			return new Parsed(meta, byBase);
		} catch (IOException e) {
			Logger.warn("CageKymographStripLayoutCsv: read failed " + path, e);
			return null;
		}
	}

	private static Meta parseMetaLine(String line) {
		String[] p = line.split(";", -1);
		try {
			int rw = -1;
			int rh = -1;
			int kc = -1;
			long kFirst = -1L;
			long kLast = -1L;
			long kStep = -1L;
			for (int i = 2; i + 1 < p.length; i += 2) {
				switch (p[i]) {
				case "ref_cam_width":
					rw = Integer.parseInt(p[i + 1]);
					break;
				case "ref_cam_height":
					rh = Integer.parseInt(p[i + 1]);
					break;
				case "kymograph_column_count":
					kc = Integer.parseInt(p[i + 1]);
					break;
				case "kymo_first_ms":
					kFirst = Long.parseLong(p[i + 1]);
					break;
				case "kymo_last_ms":
					kLast = Long.parseLong(p[i + 1]);
					break;
				case "kymo_step_ms":
					kStep = Long.parseLong(p[i + 1]);
					break;
				default:
					break;
				}
			}
			if (rw > 0 && rh > 0 && kc > 0) {
				return new Meta(rw, rh, kc, kFirst, kLast, kStep);
			}
		} catch (NumberFormatException ignored) {
		}
		return null;
	}
}
