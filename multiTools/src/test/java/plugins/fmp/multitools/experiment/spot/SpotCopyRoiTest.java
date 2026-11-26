package plugins.fmp.multitools.experiment.spot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.awt.geom.Ellipse2D;

import org.junit.ClassRule;
import org.junit.Test;

import plugins.fmp.multitools.test.InMemoryImageRule;
import plugins.kernel.roi.roi2d.ROI2DEllipse;

public class SpotCopyRoiTest {

	@ClassRule
	public static final InMemoryImageRule imageMemory = new InMemoryImageRule();

	@Test
	public void copyFromSelfKeepsTheLiveRoi() {
		ROI2DEllipse ellipse = new ROI2DEllipse(new Ellipse2D.Double(10, 20, 8, 8));
		Spot spot = new Spot(ellipse);
		spot.getProperties().setName("spot_001");
		spot.copyFrom(spot, false);
		assertSame(ellipse, spot.getRoiDirect());
		assertEquals("spot_001", spot.getProperties().getName());
	}

	@Test
	public void copyFromSpotWithoutRoiKeepsTargetOutline() {
		ROI2DEllipse ellipse = new ROI2DEllipse(new Ellipse2D.Double(10, 20, 8, 8));
		Spot target = new Spot(ellipse);
		target.getProperties().setName("spot_002");
		Spot source = new Spot();
		source.getProperties().setName("spot_002");
		source.getProperties().setStimulus("quinine");
		target.copyFrom(source, false);
		assertSame(ellipse, target.getRoiDirect());
		assertEquals("quinine", target.getProperties().getStimulus());
	}
}
