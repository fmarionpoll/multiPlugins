package plugins.fmp.multitools.tools.chart.builders;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class KymoSpotChartSupportTest {

	@Test
	public void sixtyFourMinutesAtTwentyOneSecondsIsFrame183() {
		assertEquals(183, KymoSpotChartSupport.binIndexAtMinute(64.0, 0L, 21_000L, 755));
	}

	@Test
	public void oneMinuteBinsKeepTheMinuteAsTheFrameIndex() {
		assertEquals(64, KymoSpotChartSupport.binIndexAtMinute(64.0, 0L, 60_000L, 755));
	}
}
