package plugins.fmp.multitools.experiment.capillary;

import static org.junit.Assert.*;
import org.junit.Test;
import org.w3c.dom.Document;
import icy.util.XMLUtil;
import plugins.fmp.multitools.tools.polyline.Level2D;

public class CapillaryRefinementQualityTest {
    private CapillaryMeasure source() {
        CapillaryMeasure measure=new CapillaryMeasure("topraw");
        measure.polylineLevel=new Level2D(new double[]{0,1,2},new double[]{30.123456789012345,31.23456789012345,35.34567890123456},3);
        measure.setRefinementQuality(new boolean[]{false,true,false});return measure;
    }
    @Test public void yCsvRoundTripPreservesExactPositionsAndQuality() {
        CapillaryMeasure original=source(),loaded=new CapillaryMeasure("topraw");
        StringBuffer csv=new StringBuffer();original.cvsExportYDataToRow(csv,";");
        assertTrue(loaded.csvImportYDataFromRow(csv.toString().split(";"),0));
        assertArrayEquals(original.polylineLevel.ypoints,loaded.polylineLevel.ypoints,0);
        assertArrayEquals(original.getRefinementQuality(),loaded.getRefinementQuality());
    }
    @Test public void xyCsvRoundTripPreservesQuality() {
        CapillaryMeasure original=source(),loaded=new CapillaryMeasure("topraw");
        StringBuffer csv=new StringBuffer();original.cvsExportXYDataToRow(csv,";");
        assertTrue(loaded.csvImportXYDataFromRow(csv.toString().split(";"),0));
        assertArrayEquals(original.polylineLevel.ypoints,loaded.polylineLevel.ypoints,0);
        assertArrayEquals(original.getRefinementQuality(),loaded.getRefinementQuality());
    }
    @Test public void xmlRoundTripPreservesQuality() {
        CapillaryMeasure original=source(),loaded=new CapillaryMeasure("topraw");
        Document document=XMLUtil.createDocument(true);
        original.saveCapillaryLimit2XML(XMLUtil.getRootElement(document,true),"topraw");
        loaded.loadCapillaryLimitFromXML(XMLUtil.getRootElement(document,true),"topraw","");
        assertArrayEquals(original.polylineLevel.ypoints,loaded.polylineLevel.ypoints,0);
        assertArrayEquals(original.getRefinementQuality(),loaded.getRefinementQuality());
    }
    @Test public void editsInvalidateQualityAndLegacyImportsClearIt() {
        CapillaryMeasure original=source();original.polylineLevel.ypoints[1]+=1;
        assertNull(original.getRefinementQuality());
        StringBuffer csv=new StringBuffer();original.cvsExportYDataToRow(csv,";");
        assertFalse(csv.toString().contains("refinement_quality="));
        CapillaryMeasure loaded=source();loaded.csvImportYDataFromRow(new String[]{"3","10","11","12"},0);
        assertNull(loaded.getRefinementQuality());
    }
    @Test public void copiedQualityDoesNotAliasOriginal() {
        CapillaryMeasure original=source(),copy=new CapillaryMeasure("topraw");copy.copy(original);
        assertArrayEquals(original.getRefinementQuality(),copy.getRefinementQuality());
        copy.polylineLevel.ypoints[0]+=1;assertNull(copy.getRefinementQuality());assertNotNull(original.getRefinementQuality());
    }
}
