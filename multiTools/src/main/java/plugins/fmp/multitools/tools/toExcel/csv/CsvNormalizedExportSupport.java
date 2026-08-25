package plugins.fmp.multitools.tools.toExcel.csv;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillaries.DetectionProvenanceSupport;
import plugins.fmp.multitools.experiment.ExperimentProperties;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.CageProperties;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.tools.toExcel.NormalizedExportSupport;
import plugins.fmp.multitools.tools.toExcel.enums.EnumXLSColumnHeader;

/**
 * Folder + CSV writers for normalized relational export.
 * Measure files: {@code measure_cap_raw.csv} / {@code measure_cap_binN.csv}
 * (and cage equivalents). Bin files only when {@code writeBinFiles} is true.
 */
public final class CsvNormalizedExportSupport implements AutoCloseable {

	public static final String IDEXPT = "idexpt";
	public static final String IDCAGE = "idcage";
	public static final String IDCAP = "idcap";
	public static final String MEASURE_CAP_RAW = "measure_cap_raw";
	public static final String MEASURE_CAGE_RAW = "measure_cage_raw";
	public static final String GULP_EVENTS = "gulpevents";

	public static final String COL_EXPERIMENT_ID = "experiment_id";
	public static final String COL_EXPERIMENT_DATE = "experiment_date";
	public static final String COL_CAMERA_ID = "camera_id";
	public static final String COL_CAMERA_SAMPLE_INTERVAL_S = "camera_sample_interval_s";
	public static final String COL_ANALYSIS_BIN_S = "analysis_bin_s";
	public static final String COL_ANALYSIS_N_FRAMES = "analysis_n_frames";
	public static final String COL_DEVICE_LABEL = "device_label";
	public static final String COL_EXPERIMENT_DESCRIPTOR = "experiment_descriptor";
	public static final String COL_STIMULUS_1 = "stimulus_1";
	public static final String COL_CONCENTRATION_1 = "concentration_1";
	public static final String COL_STIMULUS_2 = "stimulus_2";
	public static final String COL_CONCENTRATION_2 = "concentration_2";
	public static final String COL_STRAIN = "strain";
	public static final String COL_SEX = "sex";
	public static final String COL_CAGE_ID = "cage_id";
	public static final String COL_CAGE_N_FLIES = "cage_n_flies";
	public static final String COL_CAGE_STRAIN = "cage_strain";
	public static final String COL_CAGE_SEX = "cage_sex";
	public static final String COL_CAGE_AGE_DAYS = "cage_age_days";
	public static final String COL_CAGE_COMMENT = "cage_comment";
	public static final String COL_CAPILLARY_ID = "capillary_id";
	public static final String COL_CAPILLARY_LABEL = "capillary_label";
	public static final String COL_TIME_MIN = "time_min";
	public static final String COL_CAPILLARY_VOLUME_UL = "capillary_volume_uL";
	public static final String COL_CAPILLARY_LENGTH_PX = "capillary_length_px";
	public static final String COL_CAPILLARY_STIMULUS = "capillary_stimulus";
	public static final String COL_CAPILLARY_CONCENTRATION = "capillary_concentration";
	public static final String COL_CAPILLARY_N_FLIES = "capillary_n_flies";
	public static final String COL_CONSUMPTION_RAW_UL = "consumption_raw_uL";
	public static final String COL_CONSUMPTION_CORRECTED_UL = "consumption_corrected_uL";
	public static final String COL_CONSUMPTION_RAW00_UL = "consumption_raw00_uL";
	public static final String COL_CONSUMPTION_CORRECTED00_UL = "consumption_corrected00_uL";
	public static final String COL_BOTTOM_LEVEL_UL = "bottom_level_uL";
	public static final String COL_CONSUMPTION_FROM_GULPS_UL = "consumption_from_gulps_uL";
	public static final String COL_GULP_AMPLITUDE = "gulp_amplitude_uL";

	private final Path folder;
	private final long binStepMs;
	private final boolean writeBinFiles;
	private final String binDescriptor; // e.g. bin60
	private final Set<String> writtenExps = new LinkedHashSet<>();
	private final Set<String> writtenCages = new LinkedHashSet<>();
	private final Set<String> writtenCaps = new LinkedHashSet<>();

	private CSVPrinter idexptPrinter;
	private CSVPrinter idcagePrinter;
	private CSVPrinter idcapPrinter;
	private CSVPrinter measureCapRawPrinter;
	private CSVPrinter measureCapBinPrinter;
	private CSVPrinter measureCageRawPrinter;
	private CSVPrinter measureCageBinPrinter;
	private CSVPrinter gulpEventsPrinter;

	private final List<String> measureCapColumns;
	private boolean gulpEventsOpen;

	public CsvNormalizedExportSupport(Path folder, List<String> measureCapColumns, long binStepMs,
			boolean writeBinFiles) throws IOException {
		this.folder = folder;
		this.measureCapColumns = measureCapColumns != null ? new ArrayList<>(measureCapColumns) : new ArrayList<>();
		this.binStepMs = binStepMs;
		this.writeBinFiles = writeBinFiles && binStepMs > 0;
		int binSec = (int) Math.max(1L, Math.round(binStepMs / 1000.0));
		this.binDescriptor = "bin" + binSec;
		Files.createDirectories(folder);
	}

	public static Path resolveCsvFolder(String chosenPath) {
		if (chosenPath == null || chosenPath.isEmpty()) {
			return null;
		}
		Path p = Paths.get(chosenPath);
		String name = p.getFileName().toString();
		String lower = name.toLowerCase(Locale.ROOT);
		if (lower.endsWith(".csv")) {
			name = name.substring(0, name.length() - 4);
			Path parent = p.getParent();
			return (parent != null ? parent : Paths.get(".")).resolve(name);
		}
		if (lower.endsWith(".xlsx")) {
			name = name.substring(0, name.length() - 5);
			Path parent = p.getParent();
			return (parent != null ? parent : Paths.get(".")).resolve(name);
		}
		return p;
	}

	public Path getFolder() {
		return folder;
	}

	public boolean isWriteBinFiles() {
		return writeBinFiles;
	}

	public long getBinStepMs() {
		return binStepMs;
	}

	public String getBinDescriptor() {
		return binDescriptor;
	}

	public void ensureDescriptors(Experiment exp, String charSeries, Cage cage, Capillary capillary)
			throws IOException {
		String expKey = NormalizedExportSupport.buildExpKey(exp, charSeries);
		ensureIdexpt(exp, charSeries, expKey);
		if (cage != null) {
			ensureIdcage(expKey, cage);
			if (capillary != null) {
				ensureIdcap(expKey, cage, capillary);
			}
		}
	}

	private void ensureIdexpt(Experiment exp, String charSeries, String expKey) throws IOException {
		if (!writtenExps.add(expKey)) {
			return;
		}
		CSVPrinter p = idexptPrinter();
		exp.loadExperimentDescriptors();
		String path = exp.getExperimentField(EnumXLSColumnHeader.PATH);
		String date = exp.getExperimentField(EnumXLSColumnHeader.DATE);
		String cam = exp.getExperimentField(EnumXLSColumnHeader.CAM);
		Double camSec = exp.getCameraSampleIntervalSec() > 0 ? exp.getCameraSampleIntervalSec() : null;
		Double analysisSec = exp.getAnalysisBinSec() > 0 ? exp.getAnalysisBinSec() : null;
		Integer nFrames = exp.getAnalysisFrameCount() > 0 ? exp.getAnalysisFrameCount() : null;
		ExperimentProperties props = exp.getProperties();
		String expId = props != null ? props.getField(EnumXLSColumnHeader.EXP_ID) : "";
		if (charSeries != null && !charSeries.isEmpty() && expId != null) {
			expId = expId + "_" + charSeries;
		}
		p.printRecord(buildIdexptRecord(expKey, path, date, cam, camSec, analysisSec, nFrames, expId, props, exp));
	}

	private Object[] buildIdexptRecord(String expKey, String path, String date, String cam, Double camSec,
			Double analysisSec, Integer nFrames, String expId, ExperimentProperties props, Experiment exp) {
		List<Object> row = new ArrayList<>();
		row.add(expKey);
		row.add(nullToEmpty(path));
		row.add(nullToEmpty(date));
		row.add(nullToEmpty(cam));
		row.add(camSec);
		row.add(analysisSec);
		row.add(nFrames);
		row.add(nullToEmpty(expId));
		row.add(prop(props, EnumXLSColumnHeader.EXP_EXPT));
		row.add(prop(props, EnumXLSColumnHeader.EXP_STIM1));
		row.add(prop(props, EnumXLSColumnHeader.EXP_CONC1));
		row.add(prop(props, EnumXLSColumnHeader.EXP_STIM2));
		row.add(prop(props, EnumXLSColumnHeader.EXP_CONC2));
		row.add(prop(props, EnumXLSColumnHeader.EXP_STRAIN));
		row.add(prop(props, EnumXLSColumnHeader.EXP_SEX));
		row.addAll(DetectionProvenanceSupport.idexptProvenanceValues(exp));
		return row.toArray();
	}

	private void ensureIdcage(String expKey, Cage cage) throws IOException {
		CageProperties cp = cage.getProperties();
		int cageId = cp.getCageID();
		String key = expKey + "|" + cageId;
		if (!writtenCages.add(key)) {
			return;
		}
		CSVPrinter p = idcagePrinter();
		p.printRecord(expKey, cageId, cp.getCageNFlies(), nullToEmpty(cp.getFlyStrain()),
				nullToEmpty(cp.getFlySex()), cp.getFlyAge(), nullToEmpty(cp.getComment()));
	}

	private void ensureIdcap(String expKey, Cage cage, Capillary capillary) throws IOException {
		int cageId = cage.getProperties().getCageID();
		String capId = NormalizedExportSupport.buildCapId(capillary);
		String key = expKey + "|" + cageId + "|" + capId;
		if (!writtenCaps.add(key)) {
			return;
		}
		CSVPrinter p = idcapPrinter();
		String name = capillary.getKymographName();
		if (name == null || name.isEmpty()) {
			name = capId;
		}
		p.printRecord(expKey, cageId, capId, name, capillary.getVolume(), capillary.getPixels(),
				nullToEmpty(capillary.getStimulus()), nullToEmpty(capillary.getConcentration()),
				capillary.getNFlies());
	}

	public void writeMeasureCapRowRaw(String expKey, int cageId, String capId, double tMinutes,
			java.util.Map<String, Double> values) throws IOException {
		writeMeasureCapRow(measureCapRawPrinter(), expKey, cageId, capId, tMinutes, values);
	}

	public void writeMeasureCapRowBin(String expKey, int cageId, String capId, double tMinutes,
			java.util.Map<String, Double> values) throws IOException {
		if (!writeBinFiles) {
			return;
		}
		writeMeasureCapRow(measureCapBinPrinter(), expKey, cageId, capId, tMinutes, values);
	}

	private void writeMeasureCapRow(CSVPrinter p, String expKey, int cageId, String capId, double tMinutes,
			java.util.Map<String, Double> values) throws IOException {
		if (measureCapColumns.isEmpty()) {
			return;
		}
		List<Object> row = new ArrayList<>(4 + measureCapColumns.size());
		row.add(expKey);
		row.add(cageId);
		row.add(capId);
		row.add(tMinutes);
		for (String col : measureCapColumns) {
			Double v = values != null ? values.get(col) : null;
			if (COL_GULP_AMPLITUDE.equals(col)) {
				row.add(v != null && !Double.isNaN(v) ? v : 0.0);
			} else {
				row.add(v != null && !Double.isNaN(v) ? v : null);
			}
		}
		p.printRecord(row);
	}

	public void writeMeasureCageRowRaw(String expKey, int cageId, double tMinutes, double sum, double pi,
			double sumTopraw, double piTopraw, double sumGulps, double piGulps) throws IOException {
		writeMeasureCageRow(measureCageRawPrinter(), expKey, cageId, tMinutes, sum, pi, sumTopraw, piTopraw, sumGulps,
				piGulps);
	}

	public void writeMeasureCageRowBin(String expKey, int cageId, double tMinutes, double sum, double pi,
			double sumTopraw, double piTopraw, double sumGulps, double piGulps) throws IOException {
		if (!writeBinFiles) {
			return;
		}
		writeMeasureCageRow(measureCageBinPrinter(), expKey, cageId, tMinutes, sum, pi, sumTopraw, piTopraw, sumGulps,
				piGulps);
	}

	private void writeMeasureCageRow(CSVPrinter p, String expKey, int cageId, double tMinutes, double sum, double pi,
			double sumTopraw, double piTopraw, double sumGulps, double piGulps) throws IOException {
		p.printRecord(expKey, cageId, tMinutes, nanToNull(sum), nanToNull(pi), nanToNull(sumTopraw),
				nanToNull(piTopraw), nanToNull(sumGulps), nanToNull(piGulps));
	}

	private static Double nanToNull(double v) {
		return Double.isNaN(v) ? null : v;
	}

	public void writeGulpEvent(String expKey, int cageId, String capId, double tMinutes, double amplitude)
			throws IOException {
		CSVPrinter p = gulpEventsPrinter();
		p.printRecord(expKey, cageId, capId, tMinutes, Double.isNaN(amplitude) ? null : amplitude);
	}

	private CSVPrinter idexptPrinter() throws IOException {
		if (idexptPrinter == null) {
			List<String> header = new ArrayList<>();
			header.add(COL_EXPERIMENT_ID);
			header.add("path");
			header.add(COL_EXPERIMENT_DATE);
			header.add(COL_CAMERA_ID);
			header.add(COL_CAMERA_SAMPLE_INTERVAL_S);
			header.add(COL_ANALYSIS_BIN_S);
			header.add(COL_ANALYSIS_N_FRAMES);
			header.add(COL_DEVICE_LABEL);
			header.add(COL_EXPERIMENT_DESCRIPTOR);
			header.add(COL_STIMULUS_1);
			header.add(COL_CONCENTRATION_1);
			header.add(COL_STIMULUS_2);
			header.add(COL_CONCENTRATION_2);
			header.add(COL_STRAIN);
			header.add(COL_SEX);
			header.addAll(DetectionProvenanceSupport.IDEXPT_PROVENANCE_COLUMNS);
			idexptPrinter = openPrinter(IDEXPT, header.toArray(new String[0]));
		}
		return idexptPrinter;
	}

	private CSVPrinter idcagePrinter() throws IOException {
		if (idcagePrinter == null) {
			idcagePrinter = openPrinter(IDCAGE, COL_EXPERIMENT_ID, COL_CAGE_ID, COL_CAGE_N_FLIES, COL_CAGE_STRAIN,
					COL_CAGE_SEX, COL_CAGE_AGE_DAYS, COL_CAGE_COMMENT);
		}
		return idcagePrinter;
	}

	private CSVPrinter idcapPrinter() throws IOException {
		if (idcapPrinter == null) {
			idcapPrinter = openPrinter(IDCAP, COL_EXPERIMENT_ID, COL_CAGE_ID, COL_CAPILLARY_ID, COL_CAPILLARY_LABEL,
					COL_CAPILLARY_VOLUME_UL, COL_CAPILLARY_LENGTH_PX, COL_CAPILLARY_STIMULUS,
					COL_CAPILLARY_CONCENTRATION, COL_CAPILLARY_N_FLIES);
		}
		return idcapPrinter;
	}

	private CSVPrinter measureCapRawPrinter() throws IOException {
		if (measureCapRawPrinter == null) {
			measureCapRawPrinter = openMeasureCapPrinter(MEASURE_CAP_RAW);
		}
		return measureCapRawPrinter;
	}

	private CSVPrinter measureCapBinPrinter() throws IOException {
		if (measureCapBinPrinter == null) {
			measureCapBinPrinter = openMeasureCapPrinter("measure_cap_" + binDescriptor);
		}
		return measureCapBinPrinter;
	}

	private CSVPrinter openMeasureCapPrinter(String descriptor) throws IOException {
		List<String> header = new ArrayList<>();
		header.add(COL_EXPERIMENT_ID);
		header.add(COL_CAGE_ID);
		header.add(COL_CAPILLARY_ID);
		header.add(COL_TIME_MIN);
		header.addAll(measureCapColumns);
		return openPrinter(descriptor, header.toArray(new String[0]));
	}

	private CSVPrinter measureCageRawPrinter() throws IOException {
		if (measureCageRawPrinter == null) {
			measureCageRawPrinter = openPrinter(MEASURE_CAGE_RAW, COL_EXPERIMENT_ID, COL_CAGE_ID, COL_TIME_MIN, "sum",
					"pi", "sum_topraw", "pi_topraw", "sum_gulps", "pi_gulps");
		}
		return measureCageRawPrinter;
	}

	private CSVPrinter measureCageBinPrinter() throws IOException {
		if (measureCageBinPrinter == null) {
			measureCageBinPrinter = openPrinter("measure_cage_" + binDescriptor, COL_EXPERIMENT_ID, COL_CAGE_ID,
					COL_TIME_MIN, "sum", "pi", "sum_topraw", "pi_topraw", "sum_gulps", "pi_gulps");
		}
		return measureCageBinPrinter;
	}

	private CSVPrinter gulpEventsPrinter() throws IOException {
		if (!gulpEventsOpen) {
			gulpEventsPrinter = openPrinter(GULP_EVENTS, COL_EXPERIMENT_ID, COL_CAGE_ID, COL_CAPILLARY_ID, COL_TIME_MIN,
					"amplitude");
			gulpEventsOpen = true;
		}
		return gulpEventsPrinter;
	}

	private CSVPrinter openPrinter(String descriptor, String... header) throws IOException {
		Path file = folder.resolve(descriptor + ".csv");
		BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
		CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(header).setSkipHeaderRecord(false).build();
		return new CSVPrinter(writer, format);
	}

	private static String prop(ExperimentProperties props, EnumXLSColumnHeader field) {
		if (props == null) {
			return "";
		}
		return nullToEmpty(props.getField(field));
	}

	private static String nullToEmpty(String s) {
		return s != null ? s : "";
	}

	@Override
	public void close() throws IOException {
		closeQuietly(idexptPrinter);
		closeQuietly(idcagePrinter);
		closeQuietly(idcapPrinter);
		closeQuietly(measureCapRawPrinter);
		closeQuietly(measureCapBinPrinter);
		closeQuietly(measureCageRawPrinter);
		closeQuietly(measureCageBinPrinter);
		closeQuietly(gulpEventsPrinter);
	}

	private static void closeQuietly(CSVPrinter p) throws IOException {
		if (p != null) {
			p.flush();
			p.close();
		}
	}
}