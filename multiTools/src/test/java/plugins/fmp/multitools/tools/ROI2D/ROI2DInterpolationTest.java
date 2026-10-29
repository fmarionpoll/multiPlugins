package plugins.fmp.multitools.tools.ROI2D;

import static org.junit.Assert.*;
import org.junit.Test;
import icy.type.geom.Polyline2D;
import plugins.fmp.multitools.tools.polyline.Level2D;
import plugins.kernel.roi.roi2d.ROI2DPolyLine;

public class ROI2DInterpolationTest {
    private Polyline2D sample(double[] x,double[] y,int width) {
        ROI2DPolyLine roi=new ROI2DPolyLine(new Level2D(x,y,x.length));
        ROI2DUtilities.interpolateMissingPointsAlongXAxis(roi,width);
        Polyline2D result=roi.getPolyline2D();
        assertEquals(width,result.npoints);
        for(int i=0;i<width;i++) assertEquals(i,result.xpoints[i],0);
        return result;
    }

    @Test public void fractionalLastHandleIsInterpolatedWithoutTruncationOrFractionalOutput() {
        double end=206.91282855432615;
        Polyline2D p=sample(new double[]{0,205,end},new double[]{10,70,79},208);
        assertEquals(70+9/(end-205),p.ypoints[206],1e-10);
        assertEquals(79,p.ypoints[207],0);
    }

    @Test public void missingFirstAndLastColumnsCopyNearestAnchorWithoutShiftingTime() {
        Polyline2D p=sample(new double[]{1,3},new double[]{10,30},5);
        assertArrayEquals(new double[]{10,10,20,30,30},p.ypoints,0);
    }

    @Test public void crossedAndDuplicateHandlesHaveDeterministicInterpolation() {
        Polyline2D p=sample(new double[]{3,1.5,1.5,0},new double[]{30,15,18,0},4);
        assertArrayEquals(new double[]{0,12,22,30},p.ypoints,1e-10);
    }

    @Test public void missingYValuesAreFilledWithoutBecomingZero() {
        Polyline2D p=sample(new double[]{0,1,2,3},new double[]{10,Double.NaN,Double.NaN,16},4);
        assertArrayEquals(new double[]{10,12,14,16},p.ypoints,0);
    }

    @Test public void noFiniteAnchorsRemainMissingOnNativeGrid() {
        Polyline2D p=sample(new double[]{1,3},new double[]{Double.NaN,Double.NaN},5);
        for(double y:p.ypoints) assertTrue(Double.isNaN(y));
    }

    @Test public void singleAnchorAlsoCoversSingleColumnImages() {
        assertEquals(42,sample(new double[]{3.7},new double[]{42},1).ypoints[0],0);
        assertArrayEquals(new double[]{42,42,42},sample(new double[]{1},new double[]{42},3).ypoints,0);
    }

    @Test public void alreadyCanonicalDataAndSubpixelYAreUnchanged() {
        double[] y={20.25,21.125,22.875};
        assertArrayEquals(y,sample(new double[]{0,1,2},y,3).ypoints,0);
    }

    @Test public void outsideAnchorsDoNotProduceOutsideColumns() {
        assertArrayEquals(new double[]{15,20,25},sample(new double[]{-1,3},new double[]{10,30},3).ypoints,0);
    }
}
