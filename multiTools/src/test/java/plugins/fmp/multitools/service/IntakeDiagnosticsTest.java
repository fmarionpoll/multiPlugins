package plugins.fmp.multitools.service;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class IntakeDiagnosticsTest {
    private double[] time(int n) { double[] t=new double[n]; for(int i=0;i<n;i++)t[i]=i; return t; }
    private boolean[] valid(int n) { boolean[] v=new boolean[n]; Arrays.fill(v,true);v[0]=false;return v; }
    private List<IntakeDiagnostics.Warning> warnings(double[] level, boolean control) {
        int n=level.length; double[] e=new double[n]; boolean[] v=valid(n);
        IntakeEpisodes.Detection d=IntakeEpisodes.ordered(new double[n],level,time(n),v,
                Collections.nCopies(10,new double[n]),1,1);
        return IntakeDiagnostics.analyze(level,level,e,time(n),v,d,Collections.nCopies(10,new double[n]),1,control);
    }
    @Test public void separatesAbruptChangeFromGradualDrift() {
        double[] step=new double[60],drift=new double[60];
        for(int i=0;i<60;i++){step[i]=i>=30?4:0;drift[i]=.1*i;}
        assertTrue(warnings(step,false).stream().anyMatch(w->w.kind==IntakeDiagnostics.Kind.STEP));
        List<IntakeDiagnostics.Warning> result=warnings(drift,false);
        assertTrue(result.stream().anyMatch(w->w.kind==IntakeDiagnostics.Kind.DRIFT));
        assertFalse(result.stream().anyMatch(w->w.kind==IntakeDiagnostics.Kind.STEP));
        assertFalse(result.stream().anyMatch(w->w.kind==IntakeDiagnostics.Kind.INITIAL_ANCHOR));
    }
    @Test public void detectsOffsetsInBothDirectionsAndUsesControlLabels() {
        for(int sign:new int[]{-1,1}) {
            double[] level=new double[60]; for(int i=20;i<60;i++)level[i]=sign*4;
            List<IntakeDiagnostics.Warning> result=warnings(level,true);
            assertTrue(result.stream().anyMatch(w->w.kind==IntakeDiagnostics.Kind.RECONSTRUCTION_OFFSET
                    &&Math.signum(w.excess)==sign));
            assertTrue(result.stream().filter(w->w.kind==IntakeDiagnostics.Kind.STEP).allMatch(w->w.noFly&&w.label().startsWith("Control:")));
        }
    }
    @Test public void findsStepEvenWhenEvaporationDriftCancelsTheTotal() {
        double[] level=new double[60];
        for(int i=0;i<60;i++)level[i]=-4.0*i/59+(i>=30?4:0);
        assertEquals(0,level[59]-level[0],1e-9);
        assertTrue(warnings(level,false).stream().anyMatch(w->w.kind==IntakeDiagnostics.Kind.STEP
                &&w.first<=30&&w.last>=30&&w.excess>3));
    }
    @Test public void initialDiscrepancyIsFlaggedWithoutMovingTheAnchor() {
        double[] level=new double[40];Arrays.fill(level,4);level[0]=0;
        List<IntakeDiagnostics.Warning> result=warnings(level,false);
        assertTrue(result.stream().anyMatch(w->w.kind==IntakeDiagnostics.Kind.INITIAL_ANCHOR));
        assertEquals(0,level[0],0);
    }
    @Test public void localStepScanUsesMatchingControlStatistic() {
        int n=60;double[] level=new double[n],rate=new double[n],e=new double[n];boolean[] v=valid(n);
        for(int i=0;i<n;i++){level[i]=Math.sin(.7*i)+.3*Math.cos(1.3*i);if(i>0)rate[i]=level[i]-level[i-1];}
        List<double[]> controls=Collections.nCopies(10,rate);
        IntakeEpisodes.Detection d=IntakeEpisodes.ordered(new double[n],level,time(n),v,controls,1,1);
        List<IntakeDiagnostics.Warning> result=IntakeDiagnostics.analyze(level,level,e,time(n),v,d,controls,1,true);
        assertFalse(result.stream().anyMatch(w->w.kind==IntakeDiagnostics.Kind.STEP));
    }
    @Test public void episodeEstimateUsesFlanksAndSubtractsEvaporationDespiteMissingInteriorMotion() {
        double[] corrected=new double[30], e=new double[30]; boolean[] v=valid(30);
        for(int i=0;i<30;i++){corrected[i]=i>=12?44:40;e[i]=i*.2;}
        v[11]=false;
        IntakeDiagnostics.Estimate estimate=IntakeDiagnostics.estimate(corrected,e,v,10,14);
        assertEquals(4,estimate.net,1e-9); assertEquals(1,estimate.evaporation,1e-9);
        assertTrue(estimate.reliable);
        assertFalse(IntakeDiagnostics.estimate(corrected,e,v,1,14).reliable);
    }
    @Test public void missingFlankingPositionsStayUncertain() {
        double[] corrected=new double[20],e=new double[20]; boolean[] v=valid(20);
        Arrays.fill(corrected,0,10,Double.NaN);
        IntakeDiagnostics.Estimate estimate=IntakeDiagnostics.estimate(corrected,e,v,8,12);
        assertTrue(Double.isNaN(estimate.net)); assertFalse(estimate.reliable);
    }
    @Test public void localFlanksRecoverStepWhenTheSharedEvaporationIsTooFast() {
        double[] corrected=new double[30],e=new double[30],t=time(30);boolean[] v=valid(30);
        for(int i=0;i<30;i++){t[i]=i*.5;e[i]=.4*t[i];corrected[i]=40+.1*t[i]+(i>=12?4:0)-e[i];}
        IntakeDiagnostics.Estimate result=IntakeDiagnostics.estimate(corrected,e,t,v,10,14);
        assertEquals(4,result.localNet,1e-9);assertEquals(.1,result.localRate,1e-9);
        assertTrue(result.localReliable);assertTrue(result.net<result.localNet);
    }
    @Test public void resampledScaleUsesInclusiveEndpointsAndTheExpansion() {
        double scale=CorrelationGulpPreview.normalizedUlPerPixel(5,.1,483);
        assertEquals(5,scale*(482/1.1),1e-12);
        assertEquals(2,CorrelationGulpPreview.normalizedUlPerPixel(5,.1,242)/scale,1e-12);
    }
    @Test public void chromaticRegistrationHandlesPaleCyanAndGreenProfiles() {
        for(int[] liquid:new int[][]{{150,164,158},{140,156,144}}) {
            java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(20,100,java.awt.image.BufferedImage.TYPE_INT_RGB);
            double[] raw=new double[20];
            for(int t=0;t<20;t++) {
                double level=40+.1*t+(t>=10?3:0);raw[t]=Math.rint(level);
                for(int y=0;y<100;y++) {
                    double f=1/(1+Math.exp(-(y-level)/1.4)); int rgb=0;
                    int[] air={160,165,160};
                    for(int c=0;c<3;c++)rgb=(rgb<<8)|(int)Math.round(air[c]+f*(liquid[c]-air[c]));
                    image.setRGB(t,y,rgb);
                }
            }
            VerticalCorrelation.Result result=VerticalCorrelation.analyze(image,raw,10,6,true);
            assertTrue(result.reason[10],result.reliable[10]);assertEquals(3.1,result.shift[10],.3);
        }
    }
}
