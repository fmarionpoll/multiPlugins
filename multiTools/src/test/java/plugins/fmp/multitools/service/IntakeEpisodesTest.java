package plugins.fmp.multitools.service;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class IntakeEpisodesTest {
    @Test public void adjacentLargeStepsSurviveDecliningBackgroundTogether() {
        double[] r=new double[100];Arrays.fill(r,-.12);r[0]=0;r[20]+=5;r[21]+=5;
        IntakeEpisodes.Detection d=accepted(r,valid(r.length));
        assertEquals(5,d.net[20],1e-8);assertEquals(5,d.net[21],1e-8);
        assertEquals(10,Arrays.stream(d.net).sum(),1e-8);
    }
    @Test public void adjacentUncertainIncrementRedistributesAlreadyCountedVolume() {
        double[] r=new double[60];r[20]=4;r[21]=2;
        boolean[] v=valid(r.length);v[21]=false;
        IntakeEpisodes.Detection d=accepted(r,v);
        assertEquals(4,d.net[20],1e-8);assertEquals(2,d.net[21],1e-8);
        assertEquals(6,Arrays.stream(d.net).sum(),1e-8);
        assertEquals(1,d.episodes);
    }
    @Test public void signedBackgroundCannotCancelEarlierPersistentStep() {
        double[] r=new double[100];
        Arrays.fill(r,-.12);r[0]=0;r[20]+=4;
        IntakeEpisodes.Detection d=accepted(r,valid(r.length));
        assertEquals(4,d.net[20],1e-8);
    }
    @Test public void uncertainStepCanBeRecoveredFromReliableFlanks() {
        double[] r=new double[60];r[20]=5;
        boolean[] v=valid(r.length);v[20]=false;
        IntakeEpisodes.Detection d=accepted(r,v);
        assertEquals(5,d.net[20],1e-8);
        assertEquals(d.net[20],d.position[20]-d.position[19],1e-8);
        assertFalse(v[20]); // Recovery does not rewrite saved quality flags.
    }
    @Test public void shortUncertainCascadeKeepsItsObservedTimeSpan() {
        double[] r=new double[60];r[20]=1;r[21]=4;
        boolean[] v=valid(r.length);v[20]=v[21]=false;
        IntakeEpisodes.Detection d=accepted(r,v);
        assertEquals(1,d.episodes);
        assertEquals(1,d.net[20],1e-8);assertEquals(4,d.net[21],1e-8);
        for(int i=20;i<=21;i++) assertEquals(d.net[i],d.position[i]-d.position[i-1],1e-8);
    }
    @Test public void longUncertainRunWithoutReliableFlanksIsNotBridged() {
        double[] r=new double[60];r[20]=5;
        boolean[] v=valid(r.length);Arrays.fill(v,17,25,false);
        assertEquals(0,Arrays.stream(accepted(r,v).net).sum(),0);
    }
    @Test public void irregularSamplingUsesElapsedTimeForLocalBackground() {
        double[] t=times(60),y=new double[60],r=new double[60];
        for(int i=0;i<t.length;i++) {
            t[i]=i*.7+(i>15?.3:0);y[i]=-.12*t[i]+(i>=20?4:0);
            if(i>0) r[i]=(y[i]-y[i-1])/(t[i]-t[i-1]);
        }
        boolean[] v=valid(r.length);v[20]=false;
        IntakeEpisodes.Detection d=IntakeEpisodes.detect(r,t,v,
                Collections.singletonList(new double[r.length]),1.4,1.4,y);
        assertEquals(4,d.net[20],1e-8);
    }
    @Test public void imageEvidenceCanVetoRescueWithoutRemovingEstablishedStep() {
        double[] r=new double[60];r[10]=4;r[30]=5;
        boolean[] v=valid(r.length);v[30]=false;
        IntakeEpisodes.Detection d=IntakeEpisodes.detect(r,times(r.length),v,
                Collections.singletonList(new double[r.length]),1.4,1.4,levels(r),i->false);
        assertEquals(4,d.net[10],1e-8);assertEquals(0,d.net[30],0);
    }
    @Test public void uncertainOneFrameExcursionIsNotRecoveredAsFeeding() {
        double[] r=new double[60];r[20]=5;r[21]=-5;
        boolean[] v=valid(r.length);v[20]=false;
        assertEquals(0,Arrays.stream(accepted(r,v).net).sum(),0);
    }
    @Test public void smoothSubthresholdStartupIsNotAnIntakeEpisode() {
        double[] r=new double[60];Arrays.fill(r,1,7,.4);
        assertEquals(0,Arrays.stream(accepted(r,valid(r.length)).net).sum(),0);
    }
    @Test public void startupGuardDoesNotEraseEarlyCascadeInFeedingTrace() {
        double[] r=new double[60];Arrays.fill(r,1,7,.4);r[20]=4;
        IntakeEpisodes.Detection d=accepted(r,valid(r.length));
        assertTrue(Arrays.stream(Arrays.copyOfRange(d.net,1,7)).sum()>1.4);
        assertEquals(4,d.net[20],1e-8);
    }
    private double[] levels(double[] rate) {
        double[] levels = new double[rate.length];
        for (int i = 1; i < rate.length; i++) levels[i] = levels[i - 1] + rate[i];
        return levels;
    }
    private IntakeEpisodes.Detection accepted(double[] rate, boolean[] valid) {
        return IntakeEpisodes.detect(rate, times(rate.length), valid,
                Collections.singletonList(new double[rate.length]), 1.4, 1.4, levels(rate));
    }
    @Test public void nearbySmallNoiseDoesNotErasePersistentStep() {
        double[] r = new double[60]; r[18] = -.5; r[19] = .4; r[20] = 1.6; r[21] = -.2; r[22] = .3;
        IntakeEpisodes.Detection d = accepted(r, valid(60));
        assertTrue(d.net[20] > 1.4);
    }
    @Test public void acceptedLevelsRejectOneAndTwoBinExcursionsInBothDirections() {
        for (int duration : new int[] { 1, 2 }) for (int direction : new int[] { -1, 1 }) {
            double[] r = new double[60]; r[20] = direction * 3; r[20 + duration] = -direction * 3;
            assertEquals(0, Arrays.stream(accepted(r, valid(60)).net).sum(), 0);
        }
    }
    @Test public void excludedNeighbourDoesNotVetoValidStepAndCannotBeBridged() {
        double[] r = new double[60]; r[19] = 4; boolean[] v = valid(60); v[20] = false;
        assertEquals(4, accepted(r, v).net[19], 1e-9);
        r[19] = .9; r[21] = .9;
        assertEquals(0, Arrays.stream(accepted(r, v).net).sum(), 0);
    }
    @Test public void closeIndividuallyResolvedStepsKeepSeparateBoundaries() {
        double[] r = new double[60]; r[20] = 4; r[21] = 3;
        IntakeEpisodes.Detection d = accepted(r, valid(60));
        assertEquals(4, d.net[20], 1e-9); assertEquals(3, d.net[21], 1e-9);
        assertEquals(2, d.episodes);
    }
    @Test public void groupedMovementPreservesItsTimeSpanAndOrderedGeometry() {
        double[] r = new double[60]; r[20] = .8; r[21] = .9; r[22] = -.1; r[23] = .7;
        IntakeEpisodes.Detection d = accepted(r, valid(60));
        assertEquals(2.3, Arrays.stream(d.net).sum(), 1e-9);
        assertEquals(3, Arrays.stream(d.net).filter(v -> v > 0).count());
        assertEquals(0, d.net[22], 0);
        for (int i = 20; i <= 23; i++)
            assertEquals(d.net[i], d.position[i] - d.position[i-1], 1e-9);
        assertEquals(1, d.episodes);
    }
    @Test public void groupedRampIsNotAPlateauAndDoesNotMergeWithFollowingStep() {
        double[] r = new double[60]; r[1] = .8; r[2] = .9; r[3] = .7; r[4] = 5;
        IntakeEpisodes.Detection d = accepted(r, valid(60));
        assertEquals(.8, d.net[1], 1e-9);
        assertEquals(.9, d.net[2], 1e-9);
        assertEquals(.7, d.net[3], 1e-9);
        assertEquals(5, d.net[4], 1e-9);
        assertEquals(7.4, Arrays.stream(d.net).sum(), 1e-9);
    }
    @Test public void locallyPlausibleEventIsRemovedIfFittedAmplitudeFailsItsLimit() {
        double[] r = new double[60]; r[20] = 4; r[25] = -3;
        IntakeEpisodes.Detection d = accepted(r, valid(60));
        assertEquals(0, Arrays.stream(d.net).sum(), 0);
        assertEquals(0, d.episodes);
    }
    @Test public void controlCalibrationUsesSustainedChangesRatherThanReturningSpikes() {
        double[] r = new double[80]; r[30] = 1.6;
        double[] control = new double[80]; control[20] = 3; control[21] = -3;
        control[45] = -3; control[46] = 3;
        IntakeEpisodes.Detection d = IntakeEpisodes.detect(r, times(80), valid(80),
                Collections.singletonList(control), 1.4, 1.4, levels(r));
        assertEquals(1.6, d.net[30], 1e-9);
    }
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
