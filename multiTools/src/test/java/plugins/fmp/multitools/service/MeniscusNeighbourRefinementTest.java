package plugins.fmp.multitools.service;

import static org.junit.Assert.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import org.junit.Test;
import plugins.fmp.multitools.tools.polyline.Level2D;

public class MeniscusNeighbourRefinementTest {
    private BufferedImage movie(double[] y,boolean clutter) {
        BufferedImage image=new BufferedImage(y.length,110,BufferedImage.TYPE_INT_RGB);
        for(int t=0;t<y.length;t++) for(int row=0;row<image.getHeight();row++) {
            double f=1/(1+Math.exp(-(row-y[t])/1.4));
            double stripe=clutter?Math.exp(-.5*Math.pow((row-(y[0]-18+.25*t))/1.2,2)):0;
            int red=(int)Math.round(180-45*f-100*stripe);
            int green=(int)Math.round(180-5*f),blue=(int)Math.round(170-5*f);
            image.setRGB(t,row,(red<<16)|(green<<8)|blue);
        }
        return image;
    }
    private double error(double[] actual,double[] expected) {
        double squared=0;for(int i=0;i<actual.length;i++) squared+=Math.pow(actual[i]-expected[i],2);
        return Math.sqrt(squared/actual.length);
    }

    @Test public void independentNeighboursCorrectNoisyPositionsWithoutFlatteningRealSteps() {
        double[] edge=new double[50],raw=new double[50];
        for(int i=0;i<edge.length;i++) {
            edge[i]=40+.15*i+(i>=20?5:0)-(i>=35?3:0);
            raw[i]=edge[i]+.7*Math.sin(i*2.3);
        }
        raw[12]+=3;
        boolean[] pin=new boolean[raw.length];pin[0]=true;
        VerticalCorrelation.Result r=MeniscusNeighbourRefinement.analyze(movie(edge,false),raw,22,6,true,pin);
        assertTrue("RMSE "+error(r.refined,edge),error(r.refined,edge)<.35);
        assertEquals(5.15,r.refined[20]-r.refined[19],.25);
        assertEquals(-2.85,r.refined[35]-r.refined[34],.25);
        assertTrue(r.neighbourSupport[25]>=4);
    }

    @Test public void blankNeighbourDoesNotInvalidateOtherIndependentImageMatches() {
        double[] edge=new double[25];for(int i=0;i<edge.length;i++) edge[i]=40+.1*i;
        double[] raw=edge.clone();raw[11]+=2;
        BufferedImage image=movie(edge,false);
        for(int y=0;y<image.getHeight();y++)image.setRGB(10,y,0x999999);
        boolean[] pin=new boolean[raw.length];pin[0]=true;
        VerticalCorrelation.Result r=MeniscusNeighbourRefinement.analyze(image,raw,22,6,true,pin);
        assertEquals(edge[11],r.refined[11],.35);
        assertTrue(r.neighbourSupport[11]>=3);
        assertEquals(raw[10],r.refined[10],0);
        assertEquals(0,r.neighbourSupport[10]);
    }

    @Test public void centreWeightingResistsMovingTextureAwayFromMeniscus() {
        double[] edge=new double[35],raw=new double[35];
        for(int i=0;i<edge.length;i++){edge[i]=45+.03*i;raw[i]=edge[i]+.6*Math.sin(i*2);}
        boolean[] pin=new boolean[raw.length];pin[0]=true;
        VerticalCorrelation.Result r=MeniscusNeighbourRefinement.analyze(movie(edge,true),raw,26,13,true,pin);
        assertTrue("RMSE "+error(r.refined,edge),error(r.refined,edge)<.4);
    }

    @Test public void manualAnchorsAreExactAndSurviveApplyingAndRerunningCorrelation() {
        for(MeniscusRefinement.Method method:MeniscusRefinement.Method.values()) checkManualAnchors(method);
    }

    private void checkManualAnchors(MeniscusRefinement.Method method) {
        double[] edge=new double[25];Arrays.fill(edge,40);
        double[] x=new double[edge.length];for(int i=0;i<x.length;i++)x[i]=i;
        BufferedImage image=movie(edge,false);
        MeniscusRefinement.Proposal draft=MeniscusRefinement.editable(new Level2D(x,edge,edge.length),image,null);
        draft.addOffset(12,12,1.25);
        double chosen=draft.refined()[12];
        for(int run=0;run<2;run++) {
            MeniscusRefinement.Proposal correlated=MeniscusRefinement.propose(
                    new Level2D(x,draft.refined(),edge.length),image,22,6,true,draft.placedColumns(),method);
            assertEquals(chosen,correlated.refined()[12],0);
            draft.replaceCorrelation(correlated);
            assertEquals(chosen,draft.refined()[12],0);
            assertTrue(draft.placedColumns()[12]);
        }
    }

    @Test public void defaultMethodExactlyRetainsPreviousAdjacentRegistration() {
        double[] edge=new double[30],raw=new double[30],x=new double[30];
        for(int i=0;i<edge.length;i++){x[i]=i;edge[i]=40+.08*i;raw[i]=edge[i]+.7*Math.sin(i*2.3);}
        BufferedImage image=movie(edge,true);
        VerticalCorrelation.Result previous=VerticalCorrelation.analyzeSeeded(image,raw,18,13,true);
        MeniscusRefinement.Proposal selected=MeniscusRefinement.propose(new Level2D(x,raw,raw.length),image,18,13,true);
        assertArrayEquals(previous.refined,selected.refined(),0);
        assertArrayEquals(previous.shift,selected.motion.shift,0);
        assertArrayEquals(previous.reliable,selected.quality());
        assertArrayEquals(previous.correlation,selected.motion.correlation,0);
    }

    @Test public void missingMeasurementsAndUntexturedImagesDoNotInventPositions() {
        double[] raw={40,41,Double.NaN,42,43};
        VerticalCorrelation.Result r=MeniscusNeighbourRefinement.analyze(new BufferedImage(5,100,BufferedImage.TYPE_INT_RGB),raw,10,3,true,null);
        assertArrayEquals(raw,r.refined,0);
        for(boolean reliable:r.reliable)assertFalse(reliable);
    }

    @Test public void medianProfilesAlignAppearanceWithoutAveragingFeedingStepsOrMovingPins() {
        int n=55;
        double[] edge=new double[n],raw=new double[n];
        for(int i=0;i<n;i++) {
            edge[i]=40+.05*i+(i>=20?7:0)-(i>=37?4:0);
            raw[i]=edge[i]+.8*Math.sin(i*2.1);
        }
        BufferedImage image=movie(edge,false);
        double[][][] color=new double[n][2][image.getHeight()];
        for(int t=0;t<n;t++) for(int y=0;y<image.getHeight();y++) {
            int rgb=image.getRGB(t,y),red=(rgb>>16)&255;
            color[t][0][y]=((rgb>>8)&255)-red;
            color[t][1][y]=(rgb&255)-red;
        }
        VerticalCorrelation.Result result=new VerticalCorrelation.Result(n);
        System.arraycopy(raw,0,result.refined,0,n);
        Arrays.fill(result.neighbourSupport,6);
        boolean[] pinned=new boolean[n];pinned[10]=true;
        MeniscusTemplateRefinement.refine(color,raw,18,13,pinned,result);
        assertTrue("Appearance RMSE "+error(result.refined,edge)+" vs "+error(raw,edge),error(result.refined,edge)<error(raw,edge)*.6);
        assertEquals(7.05,result.refined[20]-result.refined[19],.3);
        assertEquals(-3.95,result.refined[37]-result.refined[36],.3);
        assertEquals(raw[10],result.refined[10],0);
        assertTrue(result.reason[25].contains("median neighbouring profile confirmed"));
    }
}
