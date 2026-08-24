package plugins.fmp.multitools.experiment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import plugins.fmp.multitools.experiment.capillaries.DetectionProvenanceSupport;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryPersistence;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;

public class CapillaryCsvProvenanceImportTest {

	@Test
	public void csvImportSkipsRoiCoordinatesBeforeProvenanceColumns() {
		BuildSeriesOptions src = new BuildSeriesOptions();
		src.pass1 = true;
		src.transform01 = ImageTransformEnums.RGB_DIFFS;
		src.detectLevel1Threshold = 100;
		src.directionUp1 = true;

		List<String> row = new ArrayList<>(Arrays.asList("0L", "0", "line0L", "line0L.tif", "1", "1", "0.0", "0",
				"stim", "conc", "L", "line0L", "LINE", "2", "10", "20", "30", "40"));
		DetectionProvenanceSupport.appendCapillaryProvenanceColumns(row, src);

		Capillary cap = new Capillary();
		CapillaryPersistence.csvImportCapillaryDescription(cap, row.toArray(new String[0]));

		BuildSeriesOptions opts = cap.getProperties().getLimitsOptions();
		assertEquals(ImageTransformEnums.RGB_DIFFS, opts.transform01);
		assertEquals(100, opts.detectLevel1Threshold);
		assertTrue(opts.pass1);
		assertTrue(opts.directionUp1);
	}
}