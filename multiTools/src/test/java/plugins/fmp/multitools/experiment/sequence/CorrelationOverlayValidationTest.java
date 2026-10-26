package plugins.fmp.multitools.experiment.sequence;

import static org.junit.Assert.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import org.junit.BeforeClass;
import org.junit.Test;
import icy.image.IcyBufferedImage;
import icy.roi.ROI2D;
import icy.sequence.Sequence;
import plugins.fmp.multitools.experiment.capillary.CapillaryCorrelationGulps;
import plugins.fmp.multitools.experiment.capillary.CapillaryGulps;
import plugins.fmp.multitools.experiment.capillary.IntakeEvaporationOverlay;
import plugins.fmp.multitools.tools.polyline.Level2D;
import plugins.kernel.roi.roi2d.ROI2DPolyLine;

public class CorrelationOverlayValidationTest {
    @Test public void refinementComparisonsSurviveFrameSyncAndNeverBecomeGulps() {
        Sequence seq=new Sequence();
        seq.setImage(0,0,IcyBufferedImage.createFrom(new BufferedImage(10,80,BufferedImage.TYPE_INT_RGB)));
        ROI2DPolyLine preview=new ROI2DPolyLine(new Level2D(new double[]{0,1,2},new double[]{20,21,24},3));
        preview.setName("line2L_xcorr_refinement_proposed");preview.setT(0);preview.setReadOnly(true);
        CapillaryCorrelationGulps.markOverlay(preview);
        preview.setProperty(plugins.fmp.multitools.service.MeniscusRefinementPreview.ROI_PROPERTY,"true");
        seq.addROI(preview);
        MeasureRoiSync.updateMeasureROIsAt(1,seq,MeasureRoiSync.MeasureRoiFilter.CAPILLARY_MEASURES,java.util.Collections.emptyList());
        assertTrue(seq.getROIs().contains(preview));
        SequenceKymos kymos=new SequenceKymos();kymos.setSequence(seq);
        CapillaryKymographMeasureBridge bridge=new CapillaryKymographMeasureBridge(kymos);
        bridge.validateROIs();bridge.validateRoisAtT(0);
        assertEquals("line2L_xcorr_refinement_proposed",preview.getName());
        CapillaryGulps gulps=new CapillaryGulps();gulps.transferROIsToMeasures(Arrays.asList(preview),10);
        assertEquals(0,Arrays.stream(gulps.getHeightSeries().ypoints).sum(),0);
        seq.close();
    }
    @BeforeClass public static void initializeIcy() { icy.preferences.IcyPreferences.init(); }

    private ROI2D curve() {
        return IntakeEvaporationOverlay.createRois("line2L",new double[]{0,2,0,1},
                new double[]{18,20,21,22},new double[]{0,.2,.4,.6},0,true).get(0);
    }

    @Test public void bothValidatorsPreserveEvaporationCurveAndManualGulpsStillWork() {
        Sequence seq=new Sequence();
        seq.setImage(0,0,IcyBufferedImage.createFrom(new BufferedImage(10,80,BufferedImage.TYPE_INT_RGB)));
        SequenceKymos kymos=new SequenceKymos(); kymos.setSequence(seq);
        ROI2D overlay=curve(); String name=overlay.getName(); Color color=overlay.getColor();
        ROI2DPolyLine manual=new ROI2DPolyLine(new Level2D(new double[]{3,3},new double[]{30,27},2));
        manual.setName("User drawing"); manual.setT(0);
        seq.addROI(overlay); seq.addROI(manual);
        CapillaryKymographMeasureBridge bridge=new CapillaryKymographMeasureBridge(kymos);
        bridge.validateROIs(); bridge.validateRoisAtT(0);
        assertEquals(name,overlay.getName()); assertEquals(color,overlay.getColor());
        assertTrue(overlay.isReadOnly()); assertEquals("gulp0000003",manual.getName());
        assertEquals(Color.red,manual.getColor());
        // Prototype curves must not acquire a frame merely by validating a different view.
        overlay.setT(-1); bridge.validateRoisAtT(1); assertEquals(-1,overlay.getT());
        seq.close();
    }

    @Test public void ownershipPreventsRenamedOverlayFromBecomingAMeasure() {
        ROI2D overlay=curve(); overlay.setName("gulp0000000");
        assertTrue(CapillaryCorrelationGulps.isOverlay(overlay));
        CapillaryGulps gulps=new CapillaryGulps();
        gulps.transferROIsToMeasures(Arrays.asList(overlay),10);
        assertEquals(0,Arrays.stream(gulps.getHeightSeries().ypoints).sum(),0);
    }
}
