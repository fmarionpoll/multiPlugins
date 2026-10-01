package plugins.fmp.multitools.tools.toExcel.csv;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

/**
 * Spot and spot-group CSV writers for normalized export. Experiment and cage
 * descriptors stay in {@link CsvNormalizedExportSupport}.
 */
public final class CsvNormalizedSpotExportSupport implements AutoCloseable {

	public static final String IDSPOT = "idspot";
	public static final String IDSPOTGROUP = "idspotgroup";
	public static final String MEASURE_SPOT_RAW = "measure_spot_raw";
	public static final String MEASURE_SPOT_KYMO_RAW = "measure_spot_kymo_raw";
	public static final String MEASURE_SPOTGROUP_RAW = "measure_spotgroup_raw";
	public static final String MEASURE_SPOTGROUP_KYMO_RAW = "measure_spotgroup_kymo_raw";

	public static final String COL_SPOT_ID = "spot_id";
	public static final String COL_SPOT_LABEL = "spot_label";
	public static final String COL_CAGE_ROW = "cage_row";
	public static final String COL_CAGE_COLUMN = "cage_column";
	public static final String COL_SPOT_VOLUME = "spot_volume";
	public static final String COL_SPOT_N_PIXELS = "spot_n_pixels";
	public static final String COL_SPOT_STIMULUS = "spot_stimulus";
	public static final String COL_SPOT_CONCENTRATION = "spot_concentration";
	public static final String COL_SPOT_COLOR = "spot_color";
	public static final String COL_SPOT_N_FLIES = "spot_n_flies";
	public static final String COL_STIMULUS_INDEX = "stimulus_index";
	public static final String COL_GROUP_ID = "group_id";
	public static final String COL_N_SPOTS = "n_spots";

	private final Path folder;
	private final long binStepMs;
	private final boolean writeBinFiles;
	private final String binDescriptor;
	private final List<String> cameraColumns;
	private final List<String> kymoColumns;
	private final List<String> groupColumns;
	private final boolean groupKymo;

	private final Set<String> writtenSpots = new LinkedHashSet<>();
	private final Set<String> writtenGroups = new LinkedHashSet<>();

	private CSVPrinter idspotPrinter;
	private CSVPrinter idspotGroupPrinter;
	private CSVPrinter measureSpotRawPrinter;
	private CSVPrinter measureSpotBinPrinter;
	private CSVPrinter measureSpotKymoRawPrinter;
	private CSVPrinter measureSpotKymoBinPrinter;
	private CSVPrinter measureGroupRawPrinter;
	private CSVPrinter measureGroupBinPrinter;

	public CsvNormalizedSpotExportSupport(Path folder, List<String> cameraColumns, List<String> kymoColumns,
			List<String> groupColumns, boolean groupKymo, long binStepMs, boolean writeBinFiles) throws IOException {
		this.folder = folder;
		this.cameraColumns = copy(cameraColumns);
		this.kymoColumns = copy(kymoColumns);
		this.groupColumns = copy(groupColumns);
		this.groupKymo = groupKymo;
		this.binStepMs = binStepMs;
		this.writeBinFiles = writeBinFiles && binStepMs > 0;
		int binSec = (int) Math.max(1L, Math.round(binStepMs / 1000.0));
		this.binDescriptor = "bin" + binSec;
		Files.createDirectories(folder);
	}

	public String getBinDescriptor() {
		return binDescriptor;
	}

	public void ensureIdspot(String expKey, int cageId, String spotId, String label, int cageRow, int cageColumn,
			double volume, int nPixels, String stimulus, String concentration, String color, int nFlies,
			String stimulusIndex) throws IOException {
		String key = expKey + "|" + cageId + "|" + spotId;
		if (!writtenSpots.add(key)) {
			return;
		}
		idspotPrinter().printRecord(expKey, cageId, spotId, empty(label), cageRow, cageColumn, finiteOrNull(volume),
				nPixels, empty(stimulus), empty(concentration), empty(color), nFlies, empty(stimulusIndex));
	}

	public void ensureIdspotGroup(String expKey, int cageId, String groupId, String stimulus, String concentration,
			int nSpots, double volume) throws IOException {
		String key = expKey + "|" + cageId + "|" + groupId;
		if (!writtenGroups.add(key)) {
			return;
		}
		idspotGroupPrinter().printRecord(expKey, cageId, groupId, empty(stimulus), empty(concentration), nSpots,
				finiteOrNull(volume));
	}

	public void writeSpotRow(boolean kymo, boolean bin, String expKey, int cageId, String spotId, double tMinutes,
			Map<String, Double> values) throws IOException {
		List<String> cols = kymo ? kymoColumns : cameraColumns;
		if (cols.isEmpty() || (bin && !writeBinFiles)) {
			return;
		}
		CSVPrinter printer = kymo ? (bin ? measureSpotKymoBinPrinter() : measureSpotKymoRawPrinter())
				: (bin ? measureSpotBinPrinter() : measureSpotRawPrinter());
		printMeasureRow(printer, cols, expKey, cageId, spotId, tMinutes, values);
	}

	public void writeGroupRow(boolean bin, String expKey, int cageId, String groupId, double tMinutes,
			Map<String, Double> values) throws IOException {
		if (groupColumns.isEmpty() || (bin && !writeBinFiles)) {
			return;
		}
		CSVPrinter printer = bin ? measureGroupBinPrinter() : measureGroupRawPrinter();
		printMeasureRow(printer, groupColumns, expKey, cageId, groupId, tMinutes, values);
	}

	private static void printMeasureRow(CSVPrinter printer, List<String> cols, String expKey, int cageId, String entityId,
			double tMinutes, Map<String, Double> values) throws IOException {
		List<Object> row = new ArrayList<>(4 + cols.size());
		row.add(expKey);
		row.add(cageId);
		row.add(entityId);
		row.add(tMinutes);
		boolean any = false;
		for (String col : cols) {
			Double v = values != null ? values.get(col) : null;
			if (v != null && !Double.isNaN(v)) {
				row.add(v);
				any = true;
			} else {
				row.add(null);
			}
		}
		if (any) {
			printer.printRecord(row);
		}
	}

	private CSVPrinter idspotPrinter() throws IOException {
		if (idspotPrinter == null) {
			idspotPrinter = openPrinter(IDSPOT, CsvNormalizedExportSupport.COL_EXPERIMENT_ID,
					CsvNormalizedExportSupport.COL_CAGE_ID, COL_SPOT_ID, COL_SPOT_LABEL, COL_CAGE_ROW, COL_CAGE_COLUMN,
					COL_SPOT_VOLUME, COL_SPOT_N_PIXELS, COL_SPOT_STIMULUS, COL_SPOT_CONCENTRATION, COL_SPOT_COLOR,
					COL_SPOT_N_FLIES, COL_STIMULUS_INDEX);
		}
		return idspotPrinter;
	}

	private CSVPrinter idspotGroupPrinter() throws IOException {
		if (idspotGroupPrinter == null) {
			idspotGroupPrinter = openPrinter(IDSPOTGROUP, CsvNormalizedExportSupport.COL_EXPERIMENT_ID,
					CsvNormalizedExportSupport.COL_CAGE_ID, COL_GROUP_ID, COL_SPOT_STIMULUS, COL_SPOT_CONCENTRATION,
					COL_N_SPOTS, COL_SPOT_VOLUME);
		}
		return idspotGroupPrinter;
	}

	private CSVPrinter measureSpotRawPrinter() throws IOException {
		if (measureSpotRawPrinter == null) {
			measureSpotRawPrinter = openMeasurePrinter(MEASURE_SPOT_RAW, COL_SPOT_ID, cameraColumns);
		}
		return measureSpotRawPrinter;
	}

	private CSVPrinter measureSpotBinPrinter() throws IOException {
		if (measureSpotBinPrinter == null) {
			measureSpotBinPrinter = openMeasurePrinter("measure_spot_" + binDescriptor, COL_SPOT_ID, cameraColumns);
		}
		return measureSpotBinPrinter;
	}

	private CSVPrinter measureSpotKymoRawPrinter() throws IOException {
		if (measureSpotKymoRawPrinter == null) {
			measureSpotKymoRawPrinter = openMeasurePrinter(MEASURE_SPOT_KYMO_RAW, COL_SPOT_ID, kymoColumns);
		}
		return measureSpotKymoRawPrinter;
	}

	private CSVPrinter measureSpotKymoBinPrinter() throws IOException {
		if (measureSpotKymoBinPrinter == null) {
			measureSpotKymoBinPrinter = openMeasurePrinter("measure_spot_kymo_" + binDescriptor, COL_SPOT_ID,
					kymoColumns);
		}
		return measureSpotKymoBinPrinter;
	}

	private CSVPrinter measureGroupRawPrinter() throws IOException {
		if (measureGroupRawPrinter == null) {
			String name = groupKymo ? MEASURE_SPOTGROUP_KYMO_RAW : MEASURE_SPOTGROUP_RAW;
			measureGroupRawPrinter = openMeasurePrinter(name, COL_GROUP_ID, groupColumns);
		}
		return measureGroupRawPrinter;
	}

	private CSVPrinter measureGroupBinPrinter() throws IOException {
		if (measureGroupBinPrinter == null) {
			String name = (groupKymo ? "measure_spotgroup_kymo_" : "measure_spotgroup_") + binDescriptor;
			measureGroupBinPrinter = openMeasurePrinter(name, COL_GROUP_ID, groupColumns);
		}
		return measureGroupBinPrinter;
	}

	private CSVPrinter openMeasurePrinter(String descriptor, String entityColumn, List<String> measureColumns)
			throws IOException {
		List<String> header = new ArrayList<>();
		header.add(CsvNormalizedExportSupport.COL_EXPERIMENT_ID);
		header.add(CsvNormalizedExportSupport.COL_CAGE_ID);
		header.add(entityColumn);
		header.add(CsvNormalizedExportSupport.COL_TIME_MIN);
		header.addAll(measureColumns);
		return openPrinter(descriptor, header.toArray(new String[0]));
	}

	private CSVPrinter openPrinter(String descriptor, String... header) throws IOException {
		Path file = folder.resolve(descriptor + ".csv");
		BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
		CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(header).setSkipHeaderRecord(false).build();
		return new CSVPrinter(writer, format);
	}

	private static List<String> copy(List<String> cols) {
		return cols != null ? new ArrayList<>(cols) : new ArrayList<>();
	}

	private static String empty(String s) {
		return s != null ? s : "";
	}

	private static Double finiteOrNull(double v) {
		return Double.isFinite(v) ? v : null;
	}

	@Override
	public void close() throws IOException {
		closeQuietly(idspotPrinter);
		closeQuietly(idspotGroupPrinter);
		closeQuietly(measureSpotRawPrinter);
		closeQuietly(measureSpotBinPrinter);
		closeQuietly(measureSpotKymoRawPrinter);
		closeQuietly(measureSpotKymoBinPrinter);
		closeQuietly(measureGroupRawPrinter);
		closeQuietly(measureGroupBinPrinter);
	}

	private static void closeQuietly(CSVPrinter printer) throws IOException {
		if (printer != null) {
			printer.flush();
			printer.close();
		}
	}
}
