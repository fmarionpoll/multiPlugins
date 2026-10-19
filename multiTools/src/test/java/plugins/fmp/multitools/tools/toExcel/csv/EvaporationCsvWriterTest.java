package plugins.fmp.multitools.tools.toExcel.csv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Test;

public class EvaporationCsvWriterTest {

	@Test
	public void fileNameKeepsExperimentIdentityAndDropsPathCharacters() {
		assertEquals("evaporation_exp12_A.csv", EvaporationCsvWriter.fileName("exp12_A"));
		assertEquals("evaporation_C_data_box_2024-01-02_cam1_B.csv",
				EvaporationCsvWriter.fileName("C:\\data\\box|2024-01-02:cam1_B"));
		assertEquals("evaporation_experiment.csv", EvaporationCsvWriter.fileName("   "));
	}

	@Test
	public void writePutsModelValuesOnTheSameTimeColumnAndLeavesGapsEmpty() throws Exception {
		Path dir = Files.createTempDirectory("evap-csv");
		Path file = dir.resolve("evaporation_exp1_A.csv");
		try {
			EvaporationCsvWriter.write(file, "exp1_A", new double[] { 0.0, 1.0, 2.0 },
					new double[] { 0.0, 0.2, Double.NaN }, new double[] { 0.0, 0.1, 0.3 }, null,
					new double[] { 0.0, 4.0, Double.NaN }, new double[] { 0.0, 2.0, 6.0 },
					new double[] { Double.NaN, Double.NaN, Double.NaN }, "model", "model", "");

			List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
			assertEquals(
					"experiment_id,time_min,evaporation_uL,evaporation_L_uL,evaporation_R_uL,evaporation_px,evaporation_L_px,evaporation_R_px,fit,fit_L,fit_R",
					lines.get(0));
			assertTrue(lines.get(1).startsWith("exp1_A,0.0,0.0,0.0,,0.0,0.0,,model,model,"));
			assertTrue(lines.get(3).contains(",2.0,,0.3,,"));
			assertTrue(lines.get(3).endsWith("model,model,"));
		} finally {
			Files.deleteIfExists(file);
			Files.deleteIfExists(dir);
		}
	}
}
