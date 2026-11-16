package plugins.fmp.multitools.tools.toExcel.csv;

import static org.junit.Assert.assertEquals;

import java.awt.geom.Rectangle2D;
import java.io.StringWriter;
import java.util.Collections;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import icy.preferences.IcyPreferences;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.FlyPosition;
import plugins.fmp.multitools.experiment.timebase.FrameTimeScale;
import plugins.fmp.multitools.tools.results.ResultsOptions;

public class CsvNormalizedFlyPositionExportTest {
	@Rule
	public TemporaryFolder output = new TemporaryFolder();

	@BeforeClass
	public static void initializeIcy() {
		IcyPreferences.init();
	}

	@Test
	public void usesTrackedFrameAndAcquisitionTimeForSparseTracks() throws Exception {
		Experiment exp = new Experiment();
		exp.setFrameTimeScale(FrameTimeScale.fromArrays(0, new long[] { 0, 60000, 240000 },
				new String[] { "a.jpg", "b.jpg", "e.jpg" }, 0, 3));
		Cage cage = new Cage();
		cage.getProperties().setCageID(3);
		exp.getCages().getCageList().add(cage);
		cage.getFlyPositions().flyPositionList.add(new FlyPosition(2, 1, new Rectangle2D.Double(4, 5, 6, 7)));

		ResultsOptions options = new ResultsOptions();
		options.onlyalive = false;
		options.yVsFood = false;
		options.alive = false;
		options.sleep = true;
		CSVFormat format = CSVFormat.DEFAULT.builder()
				.setHeader(CsvNormalizedFlyPositionExport.headers(options)).build();
		StringWriter out = new StringWriter();
		try (CsvNormalizedExportSupport csv = new CsvNormalizedExportSupport(output.getRoot().toPath(),
				Collections.emptyList(), 0, false);
				CSVPrinter printer = new CSVPrinter(out, format)) {
			CsvNormalizedFlyPositionExport.writeExperiment(csv, printer, exp, "A", options);
		}
		try (CSVParser parser = CSVParser.parse(out.toString(),
				CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build())) {
			CSVRecord row = parser.getRecords().get(0);
			assertEquals("1", row.get("fly_id"));
			assertEquals("2", row.get("frame_index"));
			assertEquals("4.0", row.get("time_min"));
			assertEquals("4.0", row.get("x_px"));
			assertEquals("", row.get("sleep"));
		}
	}
}
