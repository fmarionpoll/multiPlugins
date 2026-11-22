package plugins.fmp.multitools.tools.chart.builders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;

import java.nio.file.Path;
import java.util.Arrays;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.service.CageKymographTimeline;

public class KymoSpotChartSupportTest {
	@ClassRule public static final plugins.fmp.multitools.test.InMemoryImageRule imageMemory =
			new plugins.fmp.multitools.test.InMemoryImageRule();
	@Rule public TemporaryFolder temporary = new TemporaryFolder();

	@Test
	public void nativeColumnsUseCameraSecondsInsteadOfNominalMinuteBins() {
		Experiment exp = new Experiment();
		exp.setCamImages_ms(new long[] { 0, 21000, 42000, 64000 });
		exp.setKymoFirst_ms(0);
		exp.setKymoLast_ms(64000);
		exp.setKymoBin_ms(60000);
		assertArrayEquals(new double[] { 0, 0.35, 0.7, 64000 / 60000.0 },
				KymoSpotChartSupport.buildKymoXAxisMinutes(exp, 4), 1e-9);
	}

	@Test
	public void clippedNativeColumnsKeepElapsedTimeOffset() {
		Experiment exp = new Experiment();
		exp.setCamImages_ms(new long[] { 0, 21000, 42000, 64000 });
		exp.setKymoFirst_ms(30000);
		exp.setKymoLast_ms(64000);
		assertArrayEquals(new double[] { 0.7, 64000 / 60000.0 },
				KymoSpotChartSupport.buildKymoXAxisMinutes(exp, 2), 1e-9);
	}

	@Test
	public void savedDownsampledTimestampsWorkWithoutCamera() throws Exception {
		Path directory = temporary.newFolder().toPath();
		CageKymographTimeline.write(directory.toString(), new long[] { 0, 21000, 43000, 64000, 86000 },
				Arrays.asList(0, 2, 4));
		Experiment exp = new Experiment() {
			@Override public String getKymosBinFullDirectory() { return directory.toString(); }
		};
		assertArrayEquals(new double[] { 0, 43000 / 60000.0, 86000 / 60000.0 },
				KymoSpotChartSupport.buildKymoXAxisMinutes(exp, 3), 1e-9);
	}

	@Test
	public void olderBinnedKymographsKeepTheirGridWhenCameraCountDiffers() {
		Experiment exp = new Experiment();
		exp.setCamImages_ms(new long[] { 0, 21000, 42000, 64000 });
		exp.setKymoFirst_ms(0);
		exp.setKymoLast_ms(64000);
		exp.setKymoBin_ms(60000);
		assertArrayEquals(new double[] { 0, 1 }, KymoSpotChartSupport.buildKymoXAxisMinutes(exp, 2), 1e-9);
	}

	@Test
	public void sixtyFourMinutesAtTwentyOneSecondsIsFrame183() {
		assertEquals(183, KymoSpotChartSupport.binIndexAtMinute(64.0, 0L, 21_000L, 755));
	}

	@Test
	public void oneMinuteBinsKeepTheMinuteAsTheFrameIndex() {
		assertEquals(64, KymoSpotChartSupport.binIndexAtMinute(64.0, 0L, 60_000L, 755));
	}
}
