package plugins.fmp.multitools.series.options;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import icy.util.XMLUtil;
import plugins.fmp.multitools.series.options.BuildSeriesOptions.GulpDetectionMethod;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;

public class BuildSeriesOptionsProvenanceXmlTest {

	@Test
	public void limitsOptionsXmlRoundTripIncludesLevelAndGulpFields() {
		BuildSeriesOptions src = new BuildSeriesOptions();
		src.pass1 = true;
		src.pass2 = false;
		src.detectLevel1Threshold = 33;
		src.detectLevel2Threshold = 44;
		src.jitter2 = 6;
		src.sourceCamDirect = true;
		src.gulpDetectionMethod = GulpDetectionMethod.TOPRAW_DY;
		src.spanDiffForGulps = 5;
		src.thresholdSdMultiplier = 4.0;
		src.flyDetectSourceTransform = ImageTransformEnums.G_RGB;

		Document doc = XMLUtil.createDocument(true);
		Node root = XMLUtil.getRootElement(doc, true);
		assertTrue(src.saveToXML(root));

		BuildSeriesOptions loaded = new BuildSeriesOptions();
		assertTrue(loaded.loadFromXML(root));

		assertEquals(33, loaded.detectLevel1Threshold);
		assertEquals(44, loaded.detectLevel2Threshold);
		assertEquals(5, loaded.spanDiffForGulps);
		assertEquals(GulpDetectionMethod.TOPRAW_DY, loaded.gulpDetectionMethod);
		assertEquals(ImageTransformEnums.G_RGB, loaded.flyDetectSourceTransform);
		assertTrue(loaded.sourceCamDirect);
	}
}