package plugins.fmp.multitools.service;

import static org.junit.Assert.*;
import org.junit.Test;
import plugins.fmp.multitools.experiment.timebase.FrameTimeScale;
import plugins.fmp.multitools.tools.polyline.Level2D;

public class NativeToprawValidationTest {
    private FrameTimeScale times(int n) {
        long[] t=new long[n];for(int i=0;i<n;i++)t[i]=i*60000;
        return FrameTimeScale.fromArrays(0,t,null,0,n);
    }
    private void rejected(double[] x,int width,int timestamps,String message) {
        try {
            CorrelationGulpPreview.validateNativeTopraw(new Level2D(x,new double[x.length],x.length),
                    times(timestamps),width,"7L");fail("Invalid grid accepted");
        } catch(IllegalArgumentException e) {assertTrue(e.getMessage(),e.getMessage().contains(message));}
    }
    @Test public void nativeGridAllowsExplicitMissingY() {
        CorrelationGulpPreview.validateNativeTopraw(new Level2D(new double[]{0,1,2},
                new double[]{10,Double.NaN,12},3),times(3),3,"7L");
    }
    @Test public void partialCurveReportsActualCountsAndRepairAction() {
        rejected(new double[]{1,2},3,3,"Validate/save");
    }
    @Test public void correctCountDoesNotHideDisplacedOrReversedX() {
        rejected(new double[]{0,1,1.9},3,3,"expected native column 2");
        rejected(new double[]{0,2,1},3,3,"expected native column 1");
    }
    @Test public void wrongImageTimePairingHasDistinctDiagnostic() {
        rejected(new double[]{0,1},2,3,"kymograph has 2 columns");
    }
}
