package plugins.fmp.multitools.experiment.cages;

import static org.junit.Assert.*;
import org.junit.Test;
import plugins.fmp.multitools.tools.polyline.Level2D;

public class EvaporationCurveFitterTest {
    @Test public void originalZeroedModelStillFitsAnExponential() {
        double[] y = new double[120];
        for (int t = 0; t < y.length; t++) y[t] = 15*(1-Math.exp(-t/23.0));
        Level2D fit = EvaporationCurveFitter.fit(y);
        assertNotNull(fit); assertEquals(0,fit.ypoints[0],0);
        for (int t = 0; t < y.length; t++) assertEquals(y[t],fit.ypoints[t],.02);
    }

    @Test public void freeInterceptFitsEitherDirectionWithoutAnchoringToNoisyFirstPoint() {
        for (double sign : new double[] {-1,1}) {
            double[] y = new double[120];
            for (int t = 0; t < y.length; t++) y[t] = 40+sign*15*(1-Math.exp(-t/23.0))+.1*Math.sin(t*2);
            y[0] += 3; y[45] = Double.NaN;
            Level2D fit = EvaporationCurveFitter.fitWithOffset(y);
            assertNotNull(fit);
            for (int t = 0; t < y.length; t++) assertEquals(40+sign*15*(1-Math.exp(-t/23.0)),fit.ypoints[t],.25);
        }
    }

    @Test public void freeInterceptSupportsAFlatReference() {
        double[] y = new double[30]; java.util.Arrays.fill(y,42);
        Level2D fit = EvaporationCurveFitter.fitWithOffset(y);
        assertNotNull(fit);
        for (double value : fit.ypoints) assertEquals(42,value,1e-8);
    }
}
