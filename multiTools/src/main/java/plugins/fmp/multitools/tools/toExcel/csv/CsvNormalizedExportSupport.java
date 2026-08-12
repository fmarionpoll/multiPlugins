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
		p.printRecord(expKey, nullToEmpty(path), nullToEmpty(date), nullToEmpty(cam), camSec, analysisSec, nFrames,
				nullToEmpty(expId), prop(props, EnumXLSColumnHeader.EXP_EXPT),
				prop(props, EnumXLSColumnHeader.EXP_STIM1), prop(props, EnumXLSColumnHeader.EXP_CONC1),
				prop(props, EnumXLSColumnHeader.EXP_STIM2), prop(props, EnumXLSColumnHeader.EXP_CONC2),
				prop(props, EnumXLSColumnHeader.EXP_STRAIN), prop(props, EnumXLSColumnHeader.EXP_SEX));
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
			row.add(v != null && !Double.isNaN(v) ? v : null);
		}
		p.printRecord(row);
	}

	public void writeMeasureCageRowRaw(String expKey, int cageId, double tMinutes, String measure, double sum,
			double pi) throws IOException {
		writeMeasureCageRow(measureCageRawPrinter(), expKey, cageId, tMinutes, measure, sum, pi);
	}

	public void writeMeasureCageRowBin(String expKey, int cageId, double tMinutes, String measure, double sum,
			double pi) throws IOException {
		if (!writeBinFiles) {
			return;
		}
		writeMeasureCageRow(measureCageBinPrinter(), expKey, cageId, tMinutes, measure, sum, pi);
	}

	private void writeMeasureCageRow(CSVPrinter p, String expKey, int cageId, double tMinutes, String measure,
			double sum, double pi) throws IOException {
		p.printRecord(expKey, cageId, tMinutes, measure, Double.isNaN(sum) ? null : sum,
				Double.isNaN(pi) ? null : pi);
	}

	public void writeGulpEvent(String expKey, int cageId, String capId, double tMinutes, double amplitude)
			throws IOException {
		CSVPrinter p = gulpEventsPrinter();
		p.printRecord(expKey, cageId, capId, tMinutes, Double.isNaN(amplitude) ? null : amplitude);
	}

	private CSVPrinter idexptPrinter() throws IOException {
		if (idexptPrinter == null) {
			idexptPrinter = openPrinter(IDEXPT, "exp_key", "path", "date", "cam", "Cam_sample_s", "Analysis_bin_s",
					"Analysis_nframes", "Exp_ID", "Expmt", "Stim1", "Conc1", "Stim2", "Conc2", "Strain", "Sex");
		}
		return idexptPrinter;
	}

	private CSVPrinter idcagePrinter() throws IOException {
		if (idcagePrinter == null) {
			idcagePrinter = openPrinter(IDCAGE, "exp_key", "cage_id", "Cage_nflies", "Cage_strain", "Cage_sex",
					"Cage_age", "Cage_comment");
		}
		return idcagePrinter;
	}

	private CSVPrinter idcapPrinter() throws IOException {
		if (idcapPrinter == null) {
			idcapPrinter = openPrinter(IDCAP, "exp_key", "cage_id", "cap_id", "Cap", "Cap_ul", "Cap_npixels",
					"Cap_stimulus", "Cap_concentration", "Cap_nflies");
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
		header.add("exp_key");
		header.add("cage_id");
		header.add("cap_id");
		header.add("t_minutes");
		header.addAll(measureCapColumns);
		return openPrinter(descriptor, header.toArray(new String[0]));
	}

	private CSVPrinter measureCageRawPrinter() throws IOException {
		if (measureCageRawPrinter == null) {
			measureCageRawPrinter = openPrinter(MEASURE_CAGE_RAW, "exp_key", "cage_id", "t_minutes", "measure", "sum",
					"pi");
		}
		return measureCageRawPrinter;
	}

	private CSVPrinter measureCageBinPrinter() throws IOException {
		if (measureCageBinPrinter == null) {
			measureCageBinPrinter = openPrinter("measure_cage_" + binDescriptor, "exp_key", "cage_id", "t_minutes",
					"measure", "sum", "pi");
		}
		return measureCageBinPrinter;
	}

	private CSVPrinter gulpEventsPrinter() throws IOException {
		if (!gulpEventsOpen) {
			gulpEventsPrinter = openPrinter(GULP_EVENTS, "exp_key", "cage_id", "cap_id", "t_minutes", "amplitude");
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