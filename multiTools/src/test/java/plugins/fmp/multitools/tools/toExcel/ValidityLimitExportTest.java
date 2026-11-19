package plugins.fmp.multitools.tools.toExcel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.Point;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.test.InMemoryImageRule;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.toExcel.csv.CsvNormalizedExportSupport;
import plugins.fmp.multitools.tools.toExcel.enums.EnumXLSColumnHeader;
import plugins.fmp.multitools.tools.toExcel.utils.XLSUtils;
import plugins.kernel.roi.roi2d.ROI2DLine;

public class ValidityLimitExportTest {

	@ClassRule
	public static final InMemoryImageRule imageMemory = new InMemoryImageRule();
	@Rule
	public TemporaryFolder output = new TemporaryFolder();
	private Experiment exp;
	private Cage cage;
	private Capillary left;
	private Capillary right;
	private final int[][] cutoffs = { { 100, 50 }, { -1, 50 }, { -1, -1 }, { 1, 100 }, { 0, 100 } };
	private final Integer[][] limits = { { 99, 49, 49 }, { -1, 49, 49 }, { -1, -1, -1 }, { 0, 99, 0 },
			{ 0, 99, 0 } };

	@Before
	public void setUp() throws Exception {
		exp = new Experiment();
		exp.setResultsDirectory(output.newFolder("results").getAbsolutePath());
		cage = new Cage();
		cage.getProperties().setCageID(2);
		left = addCapillary("line2L", 0);
		right = addCapillary("line2R", 1);
	}

	@Test
	public void cageLimitFollowsTheEarliestCurrentCutoff() {
		Capillary unrelated = new Capillary();
		unrelated.setKymographIndex(2);
		unrelated.setNValid(1);
		exp.getCapillaries().getList().add(unrelated);
		for (int i = 0; i < cutoffs.length; i++) {
			setCutoffs(i);
			assertEquals(limits[i][0].intValue(), left.getLastValidFrame());
			assertEquals(limits[i][1].intValue(), right.getLastValidFrame());
			assertEquals(limits[i][2].intValue(), cage.getLastValidFrame(exp.getCapillaries()));
		}
		assertEquals(-1, new Cage().getLastValidFrame(exp.getCapillaries()));
	}

	@Test
	public void csvRepeatsTheCageLimitForEveryCapillary() throws Exception {
		for (int i = 0; i < cutoffs.length; i++) {
			setCutoffs(i);
			Path folder = output.newFolder().toPath();
			try (CsvNormalizedExportSupport csv = new CsvNormalizedExportSupport(folder,
					Collections.emptyList(), 0, false)) {
				csv.ensureDescriptors(exp, "A", cage, left);
				csv.ensureDescriptors(exp, "A", cage, right);
			}
			try (CSVParser parser = read(folder.resolve("idcap.csv"))) {
				List<CSVRecord> rows = parser.getRecords();
				assertEquals(2, rows.size());
				for (int cap = 0; cap < rows.size(); cap++) {
					assertTrue(rows.get(cap).isConsistent());
					assertEquals(csvValue(limits[i][cap]), rows.get(cap).get("cap_last_valid_frame"));
					assertEquals(csvValue(limits[i][2]), rows.get(cap).get("cage_last_valid_frame"));
				}
			}
			try (CSVParser parser = read(folder.resolve("idcage.csv"))) {
				CSVRecord row = parser.getRecords().get(0);
				assertTrue(row.isConsistent());
				assertEquals(csvValue(limits[i][2]), row.get("cage_last_valid_frame"));
			}
		}
	}

	@Test
	public void normalizedExcelIncludesBothLimitsAndTheCageAggregate() throws Exception {
		for (int i = 0; i < cutoffs.length; i++) {
			setCutoffs(i);
			try (SXSSFWorkbook workbook = new SXSSFWorkbook()) {
				SXSSFSheet sheet = NormalizedExportSupport.getOrCreateSeriesSheet(workbook);
				Set<String> written = new HashSet<>();
				NormalizedExportSupport.ensureSeriesRow(sheet, written, exp, "A", cage, left);
				NormalizedExportSupport.ensureSeriesRow(sheet, written, exp, "A", cage, right);
				NormalizedExportSupport.ensureSeriesRow(sheet, written, exp, "A", cage, null);
				int capColumn = headerColumn(sheet, "cap_last_valid_frame");
				int cageColumn = headerColumn(sheet, "cage_last_valid_frame");
				assertCell(limits[i][0], sheet.getRow(1).getCell(capColumn));
				assertCell(limits[i][1], sheet.getRow(2).getCell(capColumn));
				assertCell(null, sheet.getRow(3).getCell(capColumn));
				for (int row = 1; row <= 3; row++)
					assertCell(limits[i][2], sheet.getRow(row).getCell(cageColumn));
				workbook.dispose();
			}
		}
	}

	@Test
	public void wideLevelAndGulpExportsSupportBothOrientations() throws Exception {
		setCutoffs(0);
		for (boolean transpose : new boolean[] { false, true }) {
			for (XLSExport exporter : new XLSExport[] { new XLSExportMeasuresFromCapillary(),
					new XLSExportMeasuresFromGulp() }) {
				exporter.options = new ResultsOptions();
				exporter.options.transpose = transpose;
				try (SXSSFWorkbook workbook = new SXSSFWorkbook()) {
					SXSSFSheet sheet = workbook.createSheet("limits");
					exporter.writeTopRowDescriptors(sheet);
					for (int index = 0; index < 2; index++) {
						Capillary cap = index == 0 ? left : right;
						Point start = new Point(index + 1, 0);
						Point data;
						if (exporter instanceof XLSExportMeasuresFromCapillary)
							data = ((XLSExportMeasuresFromCapillary) exporter).writeExperimentCapInfos(sheet,
									start, exp, "A", cage, cap, EnumResults.TOPRAW);
						else
							data = ((XLSExportMeasuresFromGulp) exporter).writeExperimentGulpInfos(sheet,
									start, exp, "A", cage, cap, EnumResults.SUMGULPS);
						assertCell(limits[0][index], XLSUtils.getCell(sheet,
								new Point(index + 1, EnumXLSColumnHeader.CAP_LAST_VALID_FRAME.getValue()), transpose));
						assertCell(limits[0][2], XLSUtils.getCell(sheet,
								new Point(index + 1, EnumXLSColumnHeader.CAGE_LAST_VALID_FRAME.getValue()), transpose));
						assertEquals(EnumXLSColumnHeader.DUM4.getValue() + 1, data.y);
					}
					workbook.dispose();
				}
			}
		}
	}

	private Capillary addCapillary(String name, int index) {
		ROI2DLine roi = new ROI2DLine(0, 0, 0, 100);
		roi.setName(name);
		Capillary cap = new Capillary(roi);
		cap.setKymographIndex(index);
		cap.setCageID(cage.getCageID());
		exp.getCapillaries().getList().add(cap);
		cage.addCapillaryIfUnique(cap);
		return cap;
	}

	private void setCutoffs(int index) {
		left.setNValid(cutoffs[index][0]);
		right.setNValid(cutoffs[index][1]);
	}

	private static String csvValue(Integer value) {
		return value == null ? "" : value.toString();
	}

	private static CSVParser read(Path path) throws Exception {
		return CSVParser.parse(Files.newBufferedReader(path, StandardCharsets.UTF_8),
				CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build());
	}

	private static int headerColumn(SXSSFSheet sheet, String name) {
		for (Cell cell : sheet.getRow(0)) {
			if (name.equals(cell.getStringCellValue()))
				return cell.getColumnIndex();
		}
		throw new AssertionError("Missing header " + name);
	}

	private static void assertCell(Integer expected, Cell cell) {
		if (expected == null) {
			if (cell != null)
				assertEquals(CellType.BLANK, cell.getCellType());
			else
				assertNull(cell);
		} else {
			assertEquals(expected.doubleValue(), cell.getNumericCellValue(), 0);
		}
	}
}
