package plugins.fmp.multitools.tools.toExcel.csv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import icy.preferences.IcyPreferences;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.tools.polyline.Level2D;

public class CsvNormalizedCapillaryExportTest {

	@Rule
	public TemporaryFolder output = new TemporaryFolder();

	@BeforeClass
	public static void initializeIcy() {
		IcyPreferences.init();
	}

	@Test
	public void cageSummaryKeepsConsumptionAndIntakeColumnsAligned() throws Exception {
		Path folder = output.getRoot().toPath();
		double[] values = { 1, 0.1, 2, 0.2, 3, 0.3, 4, 0.4, 5, 0.5 };
		try (CsvNormalizedExportSupport csv = new CsvNormalizedExportSupport(folder,
				Collections.emptyList(), 60000, true)) {
			csv.writeMeasureCageSummary(true, "experiment", 2, 0, 0, values, 0.25);
			csv.writeMeasureCageSummary(false, "experiment", 2, 0, 0, values, Double.NaN);
		}
		String[] columns = { "sum", "pi", "sum_topraw", "pi_topraw", "sum_gulps", "pi_gulps",
				"sum_intake_raw_uL", "pi_intake_raw", "sum_intake_corr_uL", "pi_intake_corr" };
		for (String file : new String[] { "measure_cage_raw.csv", "measure_cage_bin60.csv" }) {
			try (CSVParser parser = read(folder.resolve(file))) {
				CSVRecord row = parser.getRecords().get(0);
				assertTrue(row.isConsistent());
				assertEquals(file.contains("raw") ? 15 : 14, parser.getHeaderMap().size());
				for (int i = 0; i < columns.length; i++)
					assertEquals(values[i], Double.parseDouble(row.get(columns[i])), 0);
				if (file.contains("raw"))
					assertEquals(0.25, Double.parseDouble(row.get("evaporation_uL")), 0);
			}
		}
	}

	@Test
	public void initialColumnLengthUsesMeasuredFrameZeroPositions() throws Exception {
		Path folder = output.getRoot().toPath();
		Experiment exp = new Experiment();
		exp.setResultsDirectory(output.newFolder("results").getAbsolutePath());
		Cage cage = new Cage();
		Capillary cap = new Capillary();
		cap.setKymographIndex(0);
		cap.getTopRaw().polylineLevel = new Level2D(new double[] { 0 }, new double[] { 10 }, 1);
		cap.getBottomRaw().polylineLevel = new Level2D(new double[] { 0 }, new double[] { 400 }, 1);
		cap.setBottomBaselineY(999);
		try (CsvNormalizedExportSupport csv = new CsvNormalizedExportSupport(folder,
				Collections.emptyList(), 0, false)) {
			csv.ensureDescriptors(exp, "A", cage, cap);
		}
		try (CSVParser parser = read(folder.resolve("idcap.csv"))) {
			CSVRecord row = parser.getRecords().get(0);
			assertTrue(row.isConsistent());
			assertEquals(390, Double.parseDouble(row.get("t0_liquid_column_length_px")), 0);
		}
	}

	private static CSVParser read(Path path) throws Exception {
		return CSVParser.parse(Files.newBufferedReader(path, StandardCharsets.UTF_8),
				CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build());
	}
}
