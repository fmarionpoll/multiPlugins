package plugins.fmp.multitools.service;

import static org.junit.Assert.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import org.junit.Test;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.tools.polyline.Level2D;

public class MeniscusRefinementTest {
    @Test public void editableDraftValidatesOnlyManualChangesAndCanBeUndone() {
        double[] positions = {30, 30.3, 31, 32};
        Capillary c = cap(positions);
        MeniscusRefinement.Proposal draft = MeniscusRefinement.editable(c.getTopRaw().polylineLevel,
                image(positions), null);
        assertArrayEquals(positions, draft.refined(), 0);
        draft.addOffset(1, 2, 2);
        assertArrayEquals(positions, c.getTopRaw().polylineLevel.ypoints, 0);
        double[] edited = {30, 32.3, 33, 32};
        MeniscusRefinement.Validation validation = MeniscusRefinement.validateAll(
                java.util.Collections.singletonMap(c, draft));
        assertArrayEquals(edited, c.getTopRaw().polylineLevel.ypoints, 0);
        MeniscusRefinement.undo(validation);
        assertArrayEquals(positions, c.getTopRaw().polylineLevel.ypoints, 0);
    }

    @Test public void applyingCorrelationReplacesAccumulatedOffsetsAndQuality() {
        double[] positions = {30, 30.3, 31, 32};
        Capillary c = cap(positions);
        MeniscusRefinement.Proposal draft = MeniscusRefinement.editable(c.getTopRaw().polylineLevel,
                image(positions), null);
        draft.addOffset(0, 3, 1000);
        double[] chosen = {29, 30, 31, 33};
        boolean[] quality = {false, true, false, true};
        draft.replaceLevels(chosen, quality);
        assertArrayEquals(chosen, draft.refined(), 0);
        assertArrayEquals(quality, draft.quality());
        assertArrayEquals(positions, draft.original(), 0);
    }

    @Test public void validationUndoCapturesExactBatchAndRejectsReplacementWithIdenticalCoordinates() {
        double[] positions={30,30.3,31,32};
        Capillary left=cap(positions), right=cap(positions), anotherCage=cap(positions);
        java.util.Map<Capillary,MeniscusRefinement.Proposal> batch=new java.util.LinkedHashMap<>();
        for(Capillary cap:Arrays.asList(left,right,anotherCage))
            batch.put(cap,MeniscusRefinement.propose(cap.getTopRaw().polylineLevel,image(positions),10,3,true));
        MeniscusRefinement.Validation validation=MeniscusRefinement.validateAll(batch);
        assertEquals(3,validation.size());assertTrue(MeniscusRefinement.canUndo(validation));
        MeniscusRefinement.undo(validation);
        for(Capillary cap:batch.keySet())assertArrayEquals(positions,cap.getTopRaw().polylineLevel.ypoints,0);
        assertFalse(MeniscusRefinement.canUndo(validation));

        MeniscusRefinement.Validation single=MeniscusRefinement.validateAll(java.util.Collections.singletonMap(left,batch.get(left)));
        MeniscusRefinement.Proposal replacement=MeniscusRefinement.propose(left.getTopRaw().polylineLevel,image(positions),10,3,true);
        MeniscusRefinement.accept(left,replacement);
        assertFalse(MeniscusRefinement.canUndo(single));
        try { MeniscusRefinement.undo(single);fail("Undid an acceptance replaced by another operation"); }
        catch(IllegalStateException expected) { }
        assertArrayEquals(replacement.refined(),left.getTopRaw().polylineLevel.ypoints,0);
        assertArrayEquals(positions,right.getTopRaw().polylineLevel.ypoints,0);
    }

    @Test public void staleExperimentValidationAndUndoDoNotPartiallyChangeOtherCages() {
        double[] positions={30,30.3,31,32};
        Capillary first=cap(positions), second=cap(positions), third=cap(positions);
        java.util.Map<Capillary,MeniscusRefinement.Proposal> batch=new java.util.LinkedHashMap<>();
        for(Capillary cap:Arrays.asList(first,second,third))
            batch.put(cap,MeniscusRefinement.propose(cap.getTopRaw().polylineLevel,image(positions),10,3,true));
        third.getTopRaw().polylineLevel.ypoints[1]+=1;
        try { MeniscusRefinement.validateAll(batch);fail("Validated stale experiment"); }
        catch(IllegalStateException expected) { }
        assertArrayEquals(positions,first.getTopRaw().polylineLevel.ypoints,0);
        assertArrayEquals(positions,second.getTopRaw().polylineLevel.ypoints,0);
        third.getTopRaw().polylineLevel.ypoints[1]=positions[1];
        MeniscusRefinement.Validation validation=MeniscusRefinement.validateAll(batch);
        third.getTopRaw().polylineLevel.ypoints[1]+=1;
        assertFalse(MeniscusRefinement.canUndo(validation));
        try { MeniscusRefinement.undo(validation);fail("Undid edited experiment"); }
        catch(IllegalStateException expected) { }
        assertArrayEquals(batch.get(first).refined(),first.getTopRaw().polylineLevel.ypoints,0);
        assertArrayEquals(batch.get(second).refined(),second.getTopRaw().polylineLevel.ypoints,0);
    }
    @Test public void cageAcceptAndUndoRejectStaleSideBeforeChangingEither() {
        double[] positions={30,30.3,31,32};
        Capillary left=cap(positions), right=cap(positions);
        java.util.Map<Capillary,MeniscusRefinement.Proposal> batch=new java.util.LinkedHashMap<>();
        for(Capillary cap:Arrays.asList(left,right))
            batch.put(cap,MeniscusRefinement.propose(cap.getTopRaw().polylineLevel,image(positions),10,3,true));
        right.getTopRaw().polylineLevel.ypoints[1]+=2;
        try { MeniscusRefinement.acceptAll(batch); fail("Accepted stale cage"); } catch(IllegalStateException expected) { }
        assertArrayEquals(positions,left.getTopRaw().polylineLevel.ypoints,0);
        assertFalse(MeniscusRefinement.canUndo(left));
        right.getTopRaw().polylineLevel.ypoints[1]=positions[1];
        MeniscusRefinement.acceptAll(batch);
        assertTrue(MeniscusRefinement.canUndo(left));assertTrue(MeniscusRefinement.canUndo(right));
        double[] accepted=left.getTopRaw().polylineLevel.ypoints.clone();
        right.getTopRaw().polylineLevel.ypoints[1]+=2;
        try { MeniscusRefinement.undoAll(batch.keySet()); fail("Undid changed cage"); } catch(IllegalStateException expected) { }
        assertArrayEquals(accepted,left.getTopRaw().polylineLevel.ypoints,0);
        right.getTopRaw().polylineLevel.ypoints=batch.get(right).refined();
        MeniscusRefinement.undoAll(batch.keySet());
        assertArrayEquals(positions,left.getTopRaw().polylineLevel.ypoints,0);
        assertArrayEquals(positions,right.getTopRaw().polylineLevel.ypoints,0);
    }

    @Test public void stabilizationSamplesAtFractionalMeniscusAndMarksMissingSupport() {
        BufferedImage source=new BufferedImage(2,8,BufferedImage.TYPE_INT_RGB);
        for(int x=0;x<2;x++) for(int y=0;y<8;y++) source.setRGB(x,y,(y*20)*0x010101);
        BufferedImage aligned=MeniscusRefinement.stabilized(source,new double[]{2.5,0},2);
        assertEquals(0x323232,aligned.getRGB(0,2)&0xffffff);
        assertEquals(0x555555,aligned.getRGB(1,0)&0xffffff);
        assertEquals(0,aligned.getRGB(1,2)&0xffffff);
        assertEquals(0x282828,source.getRGB(0,2)&0xffffff);
    }

    @Test public void comparisonRegistryRejectsStaleDataAndRespectsOwnership() {
        double[] positions={30,30.3,31,32};Capillary cap=cap(positions);
        MeniscusRefinement.Proposal p=MeniscusRefinement.propose(cap.getTopRaw().polylineLevel,image(positions),10,3,true);
        Object owner=new Object(), other=new Object();
        MeniscusRefinementPreview.publish(owner,java.util.Collections.singletonMap(cap,p));
        MeniscusRefinementPreview.clear(other);assertSame(p,MeniscusRefinementPreview.get(cap));
        cap.getTopRaw().polylineLevel.ypoints[1]+=1;assertNull(MeniscusRefinementPreview.get(cap));
        cap.getTopRaw().polylineLevel.ypoints[1]=positions[1];
        MeniscusRefinementPreview.clear(owner);assertNull(MeniscusRefinementPreview.get(cap));
    }
    private static BufferedImage image(double[] positions) {
        BufferedImage image=new BufferedImage(positions.length,100,BufferedImage.TYPE_INT_RGB);
        for(int t=0;t<positions.length;t++)for(int y=0;y<100;y++){
            double f=1/(1+Math.exp(-(y-positions[t])/1.4));
            int r=(int)Math.round(150-65*f),g=(int)Math.round(155-5*f),b=(int)Math.round(145-5*f);
            image.setRGB(t,y,(r<<16)|(g<<8)|b);
        }
        return image;
    }
    private static Capillary cap(double[] positions) {
        Capillary cap=new Capillary();double[] x=new double[positions.length];
        for(int i=0;i<x.length;i++)x[i]=i;
        cap.getTopRaw().polylineLevel=new Level2D(x,positions.clone(),positions.length);
        return cap;
    }
    @Test public void largePredictedStepNeedsOnlySmallCorrectionSearch() {
        double[] positions={30,30.2,48.2,48.4};
        double[] raw={30,30,48,48};
        VerticalCorrelation.Result result=VerticalCorrelation.analyzeSeeded(image(positions),raw,10,2,false);
        assertTrue(result.reliable[2]);assertEquals(18,result.shift[2],.15);
        assertEquals(2,result.searchRange[2],0);
        assertFalse(VerticalCorrelation.analyze(image(positions),raw,10,2,false).reliable[2]);
    }
    @Test public void previewIsRepeatableAcceptanceAndUndoAreExplicit() {
        double[] positions={30,30.3,30.6,35.1,35.4,35.7};
        double[] raw=Arrays.stream(positions).map(Math::rint).toArray();
        Capillary cap=cap(raw);
        MeniscusRefinement.Proposal first=MeniscusRefinement.propose(cap.getTopRaw().polylineLevel,image(positions),10,3,true);
        MeniscusRefinement.Proposal second=MeniscusRefinement.propose(cap.getTopRaw().polylineLevel,image(positions),10,3,true);
        assertArrayEquals(raw,cap.getTopRaw().polylineLevel.ypoints,0);
        assertArrayEquals(first.refined(),second.refined(),0);
        MeniscusRefinement.accept(cap,first);
        assertArrayEquals(first.refined(),cap.getTopRaw().polylineLevel.ypoints,0);
        assertNotNull(MeniscusRefinement.quality(cap));assertTrue(MeniscusRefinement.canUndo(cap));
        VerticalCorrelation.Result motion=VerticalCorrelation.fromLevels(cap.getTopRaw().polylineLevel.ypoints,MeniscusRefinement.quality(cap));
        double total=0;for(int i=1;i<raw.length;i++)total+=motion.shift[i];
        assertEquals(first.refined()[raw.length-1]-first.refined()[0],total,1e-10);
        MeniscusRefinement.undo(cap);
        assertArrayEquals(raw,cap.getTopRaw().polylineLevel.ypoints,0);assertNull(MeniscusRefinement.quality(cap));
    }
    @Test public void acceptanceInvalidatesDerivedResultsAndUndoRestoresThem() {
        double[] values={30,31,35,36};Capillary cap=cap(values);
        cap.getDerivative().polylineLevel=new Level2D(new double[]{0,1},new double[]{4,5},2);
        cap.getTopCorrected().polylineLevel=new Level2D(new double[]{0,1},new double[]{1,2},2);
        cap.getCorrelationGulps().set(new double[]{0,1,4,1},10,6,1,false);
        cap.setGulpMeasuresDirty(false);
        MeniscusRefinement.Proposal p=MeniscusRefinement.propose(cap.getTopRaw().polylineLevel,image(values),10,3,true);
        MeniscusRefinement.accept(cap,p);
        assertFalse(cap.getCorrelationGulps().hasMeasures());assertEquals(0,cap.getDerivative().polylineLevel.npoints);
        assertEquals(0,cap.getTopCorrected().polylineLevel.npoints);assertTrue(cap.isGulpMeasuresDirty());
        MeniscusRefinement.undo(cap);
        assertEquals(3,cap.getCorrelationGulps().countEvents());assertEquals(2,cap.getDerivative().polylineLevel.npoints);
        assertEquals(2,cap.getTopCorrected().polylineLevel.npoints);assertFalse(cap.isGulpMeasuresDirty());
    }
    @Test public void externalEditRejectsStaleProposalAndUndo() {
        double[] positions={30,30.3,31,32};Capillary cap=cap(positions);
        MeniscusRefinement.Proposal proposal=MeniscusRefinement.propose(cap.getTopRaw().polylineLevel,image(positions),10,3,true);
        cap.getTopRaw().polylineLevel.ypoints[1]+=2;
        try{MeniscusRefinement.accept(cap,proposal);fail("Stale proposal accepted");}catch(IllegalStateException expected){}
        cap.getTopRaw().polylineLevel.ypoints[1]=positions[1];MeniscusRefinement.accept(cap,proposal);
        cap.getTopRaw().polylineLevel.ypoints[1]+=2;
        assertNull(MeniscusRefinement.quality(cap));assertFalse(MeniscusRefinement.canUndo(cap));
        try{MeniscusRefinement.undo(cap);fail("External edit overwritten");}catch(IllegalStateException expected){}
    }
    @Test public void rangeOffsetChangesOnlySelectedColumnsAndPersistsOnValidate() {
        double[] positions={30,30.3,31,32,33};
        Capillary cap=cap(positions);
        MeniscusRefinement.Proposal proposal=MeniscusRefinement.propose(cap.getTopRaw().polylineLevel,image(positions),10,3,true);
        double[] before=proposal.refined();
        boolean[] automatic=proposal.motion.reliable.clone();
        proposal.addOffset(1,2,1.5);
        double[] shifted=proposal.refined();
        assertEquals(before[0],shifted[0],0);
        assertEquals(before[3],shifted[3],0);
        assertEquals(before[4],shifted[4],0);
        assertEquals(before[1]+1.5,shifted[1],0);
        assertEquals(before[2]+1.5,shifted[2],0);
        boolean[] edited=proposal.quality();
        assertTrue(edited[1]);assertTrue(edited[2]);
        assertEquals(automatic[0],edited[0]);
        assertEquals(automatic[3],edited[3]);
        assertEquals(automatic[4],edited[4]);
        proposal.addOffset(1,2,-1.5);
        assertArrayEquals(before,proposal.refined(),0);
        assertArrayEquals(automatic,proposal.quality());
        proposal.addOffset(1,2,1.5);
        assertTrue(MeniscusRefinement.isCurrent(cap,proposal));
        MeniscusRefinement.accept(cap,proposal);
        assertArrayEquals(proposal.refined(),cap.getTopRaw().polylineLevel.ypoints,0);
        assertTrue(MeniscusRefinement.quality(cap)[1]);
        assertTrue(MeniscusRefinement.quality(cap)[2]);
        assertEquals(automatic[0],MeniscusRefinement.quality(cap)[0]);
    }
    @Test public void holdValidatedKeepsSavedLevelsUntilTheNextManualOffset() {
        double[] raw={30,30,48,48};
        double[] edge={30,30.2,48.2,48.4};
        Capillary cap=cap(raw);
        MeniscusRefinement.Proposal placed=MeniscusRefinement.propose(cap.getTopRaw().polylineLevel,image(edge),10,3,true);
        placed.addOffset(2,3,1.5);
        MeniscusRefinement.accept(cap,placed);
        double[] saved=cap.getTopRaw().polylineLevel.ypoints.clone();
        MeniscusRefinement.Proposal again=MeniscusRefinement.propose(cap.getTopRaw().polylineLevel,image(edge),10,3,true);
        Arrays.fill(again.motion.refined, 0);
        again.holdValidated(saved,MeniscusRefinement.quality(cap));
        assertArrayEquals(saved,again.refined(),0);
        assertTrue(again.quality()[2]);
        assertTrue(again.quality()[3]);
        again.addOffset(1,1,2);
        assertEquals(saved[0],again.refined()[0],0);
        assertEquals(saved[1]+2,again.refined()[1],0);
        assertEquals(saved[2],again.refined()[2],0);
        assertEquals(saved[3],again.refined()[3],0);
    }
    @Test public void perColumnOffsetsLeaveOtherColumnsUnchanged() {
        double[] positions={30,30.3,31,32,33};
        Capillary cap=cap(positions);
        MeniscusRefinement.Proposal proposal=MeniscusRefinement.propose(cap.getTopRaw().polylineLevel,image(positions),10,3,true);
        double[] before=proposal.refined();
        double[] delta=new double[positions.length];
        Arrays.fill(delta, Double.NaN);
        delta[1]=2;
        delta[3]=-1;
        proposal.addOffsets(delta);
        assertEquals(before[0], proposal.refined()[0], 0);
        assertEquals(before[1] + 2, proposal.refined()[1], 0);
        assertEquals(before[2], proposal.refined()[2], 0);
        assertEquals(before[3] - 1, proposal.refined()[3], 0);
        assertEquals(before[4], proposal.refined()[4], 0);
        assertFalse(proposal.placedColumns()[0]);
        assertTrue(proposal.placedColumns()[1]);
        assertFalse(proposal.placedColumns()[2]);
        assertTrue(proposal.placedColumns()[3]);
        assertFalse(proposal.placedColumns()[4]);
    }
    @Test public void holdValidatedPinsOnlyPlacedColumns() {
        double[] raw={30,30,48,48};
        double[] edge={30,30.2,48.2,48.4};
        Capillary cap=cap(raw);
        MeniscusRefinement.Proposal placed=MeniscusRefinement.propose(cap.getTopRaw().polylineLevel,image(edge),10,3,true);
        placed.addOffset(2, 2, 1.5);
        MeniscusRefinement.accept(cap, placed);
        double[] saved=cap.getTopRaw().polylineLevel.ypoints.clone();
        MeniscusRefinement.Proposal again=MeniscusRefinement.propose(cap.getTopRaw().polylineLevel,image(edge),10,3,true);
        Arrays.fill(again.motion.refined, 0);
        boolean[] mask=new boolean[saved.length];
        mask[2]=true;
        again.holdValidated(saved, MeniscusRefinement.quality(cap), mask);
        assertEquals(0, again.refined()[0], 0);
        assertEquals(0, again.refined()[1], 0);
        assertEquals(saved[2], again.refined()[2], 0);
        assertEquals(0, again.refined()[3], 0);
        assertTrue(Math.abs(saved[2]) > 1);
        assertTrue(again.quality()[2]);
    }
    @Test public void phaseTwoPreservesSignedDifferencesAndQualityWithoutInventedCorrelationScores() {
        double[] levels={10,13,12,Double.NaN,18,20};boolean[] quality={false,true,false,false,false,true};
        VerticalCorrelation.Result result=VerticalCorrelation.fromLevels(levels,quality);
        assertEquals(3,result.shift[1],0);assertEquals(-1,result.shift[2],0);
        assertFalse(result.reliable[2]);assertFalse(result.reliable[3]);assertFalse(result.reliable[4]);
        assertTrue(result.reliable[5]);assertTrue(Double.isNaN(result.correlation[1]));
        VerticalCorrelation.Result saved=VerticalCorrelation.fromLevels(levels,null);
        assertTrue(saved.reliable[2]);assertTrue(saved.reason[2].contains("unavailable"));
        assertArrayEquals(levels,saved.refined,0);
    }
}
