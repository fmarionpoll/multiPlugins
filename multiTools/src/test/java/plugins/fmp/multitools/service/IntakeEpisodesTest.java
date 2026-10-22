package plugins.fmp.multitools.service;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class IntakeEpisodesTest {
    private double[] times(int n) { double[] t = new double[n]; for (int i=0;i<n;i++) t[i]=i; return t; }
    private boolean[] valid(int n) { boolean[] v = new boolean[n]; Arrays.fill(v,true); v[0]=false; return v; }
    private IntakeEpisodes.Detection detect(double[] r, boolean[] valid) {
        double[] control = new double[r.length]; control[0]=Double.NaN;
        for(int i=1;i<control.length;i++) control[i] = (i%2==0 ? .15 : -.15);
        return IntakeEpisodes.detect(r,times(r.length),valid,Collections.singletonList(control),1.4,1.4);
    }
    @Test public void rejectsReturnFromUpwardFluctuationInEitherOrder() {
        for(boolean reverse : new boolean[]{true,false}) {
            double[] r = new double[60]; r[20]=reverse ? 1.6 : -1.5; r[21]=reverse ? -1.5 : 1.6;
            assertEquals(0,Arrays.stream(detect(r,valid(r.length)).net).sum(),0);
        }
    }
    @Test public void recoversSmallGroupedMovementWithoutDoubleCounting() {
        double[] r = new double[60]; r[20]=.8; r[21]=.9; r[22]=-.1; r[23]=.7;
        IntakeEpisodes.Detection d=detect(r,valid(r.length));
        assertEquals(2.3,Arrays.stream(d.net).sum(),1e-9);
        assertEquals(1,d.episodes);
    }
    @Test public void retainsLargeStepAndDoesNotBridgeMissingIntervals() {
        double[] r=new double[60]; r[20]=4;
        assertEquals(4,Arrays.stream(detect(r,valid(r.length)).net).sum(),1e-9);
        r[20]=.9; r[22]=.9; boolean[] v=valid(r.length); v[21]=false;
        assertEquals(0,Arrays.stream(detect(r,v).net).sum(),0);
    }
    @Test public void anchoredLevelsRejectCorrelationDrift() {
        double[] r=new double[60]; r[20]=.9; r[21]=.9;
        double[] flat=new double[60];
        IntakeEpisodes.Detection d=IntakeEpisodes.detect(r,times(60),valid(60),
                Collections.singletonList(flat),1.4,1.4,flat);
        assertEquals(0,Arrays.stream(d.net).sum(),0);
        for(int i=21;i<flat.length;i++) flat[i]=1.7;
        d=IntakeEpisodes.detect(r,times(60),valid(60),Collections.singletonList(new double[60]),1.4,1.4,flat);
        assertEquals(1.7,Arrays.stream(d.net).sum(),1e-9);
    }
    @Test public void acceptedAmplitudeComesFromLevelsNotTheNoisePenalty() {
        double[] r=new double[60], levels=new double[60];
        r[19]=-.5; r[20]=4; r[21]=-.4;
        for(int i=20;i<60;i++) levels[i]=4;
        IntakeEpisodes.Detection d=IntakeEpisodes.detect(r,times(60),valid(60),
                Collections.singletonList(new double[60]),1.4,1.4,levels);
        assertEquals(4,Arrays.stream(d.net).sum(),1e-9);
    }
    @Test public void jointPositionsPoolReversalsAndPreventOverlappingBars() {
        double[] levels=new double[40], accepted=new double[40];
        for(int i=0;i<40;i++) levels[i]=i<10 ? 40 : i==10 ? 44 : i<20 ? 43 : 48;
        accepted[10]=4; accepted[11]=1; accepted[20]=5;
        IntakeEpisodes.Detection d=IntakeEpisodes.ordered(accepted,levels,times(40),valid(40),
                Collections.singletonList(new double[40]),1.4,1.4);
        assertEquals(0,d.net[11],1e-9);
        double previousBottom=Double.NEGATIVE_INFINITY;
        for(int i=1;i<40;i++) if(d.net[i]>0) {
            // Include evaporation in both the endpoint positions and total bars.
            double bottom=d.position[i]+.2*i;
            double top=bottom-d.net[i]-.2;
            assertTrue(top+1e-9>=previousBottom);
            previousBottom=bottom;
        }
        assertEquals(8,Arrays.stream(d.net).sum(),1e-9);
    }
    @Test public void unexplainedGapIsFlaggedWithoutInventingAnIntake() {
        double[] levels=new double[60];
        for(int i=30;i<60;i++) levels[i]=2.8;
        IntakeEpisodes.Detection d=IntakeEpisodes.ordered(new double[60],levels,times(60),valid(60),
                Collections.nCopies(10,new double[60]),1.4,1.4);
        assertEquals(0,Arrays.stream(d.net).sum(),0);
        assertEquals(1,d.gaps.size()); assertEquals(2.8,d.gaps.get(0).excess,1e-9);
        assertTrue(d.gaps.get(0).reliable);
        levels[30]=3; Arrays.fill(levels,31,60,0);
        d=IntakeEpisodes.ordered(new double[60],levels,times(60),valid(60),
                Collections.singletonList(new double[60]),1.4,1.4);
        assertTrue(d.gaps.isEmpty());
    }
    @Test public void gapAcrossUnreliableMotionIsExplicitlyUncertain() {
        double[] levels=new double[60]; boolean[] v=valid(60); v[29]=false;
        for(int i=30;i<60;i++) levels[i]=3;
        IntakeEpisodes.Detection d=IntakeEpisodes.ordered(new double[60],levels,times(60),v,
                Collections.singletonList(new double[60]),1.4,1.4);
        assertEquals(1,d.gaps.size()); assertFalse(d.gaps.get(0).reliable);
        assertEquals(0,Arrays.stream(d.net).sum(),0);
    }
    @Test public void missingMovementIsNotAbsorbedIntoNeighbouringIntake() {
        double[] levels=new double[60], accepted=new double[60];
        for(int i=10;i<60;i++) levels[i]=i<30 ? 3 : 6;
        accepted[10]=3;
        IntakeEpisodes.Detection d=IntakeEpisodes.ordered(accepted,levels,times(60),valid(60),
                Collections.singletonList(new double[60]),1.4,1.4);
        assertEquals(1,d.gaps.size());
        assertEquals(3,d.net[10],1e-9);
        assertEquals(3,Arrays.stream(d.net).sum(),1e-9);
        assertEquals(6,d.position[59]-d.position[0],1e-9);
    }
    @Test public void insufficientQuietDataDoesNotInventLocalEvaporation() {
        double[] raw=new double[30], motion=new double[30], e=new double[30];
        for(int i=0;i<30;i++) { e[i]=i*.2; raw[i]=40+i*2; motion[i]=2; }
        IntakeEpisodes.EvaporationScale s=IntakeEpisodes.evaporationScale(raw,motion,e,times(30),valid(30),.2);
        assertFalse(s.reliable); assertEquals(1,s.factor,0);
    }
    @Test public void quietRunsRecoverKnownEvaporationScaleDespiteFeedingOffsets() {
        double[] raw=new double[80], motion=new double[80], e=new double[80];
        for(int i=0;i<80;i++) { e[i]=i*.2; raw[i]=40+.65*e[i]+(i/20)*4; motion[i]=i>0 ? raw[i]-raw[i-1] : 0; }
        IntakeEpisodes.EvaporationScale s=IntakeEpisodes.evaporationScale(raw,motion,e,times(80),valid(80),.15);
        assertTrue(s.reliable); assertEquals(.65,s.factor,1e-9);
    }
}
