package plugins.fmp.multitools.experiment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import plugins.fmp.multitools.experiment.capillaries.DetectionProvenanceSupport;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryPersistence;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.series.options.BuildSeriesOptions.GulpDetectionMethod;
import plugins.fmp.multitools.series.options.GulpThresholdMethod;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;

public class DetectionProvenanceSupportTest {

	@Test
	public void fullBatchLevelRequiresWholeKymoRange() {
		BuildSeriesOptions o = new BuildSeriesOptions();
		o.detectSelectedKymo = false;
		o.analyzePartOnly = false;
		assertTrue(DetectionProvenanceSupport.isFullBatchLevelDetection(o));
		o.detectSelectedKymo = true;
		assertFalse(DetectionProvenanceSupport.isFullBatchLevelDetection(o));
	}

	@Test
	public void levelAndGulpRecipesRoundTripOnCapillary() {
		BuildSeriesOptions src = new BuildSeriesOptions();
		src.pass1 = true;
		src.pass2 = true;
		src.transform01 = ImageTransformEnums.RGB;
		src.transform02 = ImageTransformEnums.G_RGB;
		src.detectLevel1Threshold = 11;
		src.detectLevel2Threshold = 22;
		src.directionUp1 = true;
		src.directionUp2 = false;
		src.jitter2 = 3;
		src.sourceCamDirect = true;
		src.gulpDetectionMethod = GulpDetectionMethod.XDIFFN_REF;
		src.transformForGulps = ImageTransformEnums.YDIFFN;
		src.spanDiffForGulps = 7;
		src.thresholdMethod = GulpThresholdMethod.MEAN_PLUS_SD;
		src.thresholdSdMultiplier = 2.5;
		src.detectGulpsThreshold_uL = 0.05;

		Capillary cap = new Capillary();
		DetectionProvenanceSupport.copyLevelRecipeTo(cap.getProperties().getLimitsOptions(), src);
		DetectionProvenanceSupport.copyGulpRecipeTo(cap.getProperties().getLimitsOptions(), src);

		List<String> row = new ArrayList<>();
		DetectionProvenanceSupport.appendCapillaryProvenanceColumns(row, cap.getProperties().getLimitsOptions());
		assertEquals(DetectionProvenanceSupport.CAPILLARY_PROVENANCE_COLUMNS.size(), row.size());

		Capillary loaded = new Capillary();
		DetectionProvenanceSupport.importCapillaryProvenance(loaded, row.toArray(new String[0]), 0);
		BuildSeriesOptions opts = loaded.getProperties().getLimitsOptions();
		assertEquals(11, opts.detectLevel1Threshold);
		assertEquals(22, opts.detectLevel2Threshold);
		assertEquals(GulpDetectionMethod.XDIFFN_REF, opts.gulpDetectionMethod);
		assertEquals(7, opts.spanDiffForGulps);
		assertTrue(opts.sourceCamDirect);
	}

	@Test
	public void capillaryCsvHeaderIncludesProvenanceColumns() {
		String header = CapillaryPersistence.csvExportCapillarySubSectionHeader(";");
		for (String col : DetectionProvenanceSupport.CAPILLARY_PROVENANCE_COLUMNS) {
			assertTrue("missing column " + col, header.contains(col));
		}
	}

	@Test
	public void idexptColumnsIncludeFlyMethodAndVersions() {
		assertTrue(DetectionProvenanceSupport.IDEXPT_PROVENANCE_COLUMNS.contains("fly_detect_method"));
		assertTrue(DetectionProvenanceSupport.IDEXPT_PROVENANCE_COLUMNS.contains("multicafe_version"));
		assertTrue(DetectionProvenanceSupport.IDEXPT_PROVENANCE_COLUMNS.contains("level_pass1"));
	}

	@Test
	public void idexptValuesBlankWhenExperimentNull() {
		List<Object> values = DetectionProvenanceSupport.idexptProvenanceValues(null);
		assertEquals(DetectionProvenanceSupport.IDEXPT_PROVENANCE_COLUMNS.size(), values.size());
		for (Object v : values) {
			assertEquals("", v);
		}
	}
}