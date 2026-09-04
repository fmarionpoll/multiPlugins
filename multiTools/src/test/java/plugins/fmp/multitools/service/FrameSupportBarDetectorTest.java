package plugins.fmp.multitools.service;

import static org.junit.Assert.*;
import org.junit.Test;

public class FrameSupportBarDetectorTest {
    @Test public void derivesInternalSearchBoundsFromTwentyCapillaries() {
        java.util.ArrayList<Double> x=new java.util.ArrayList<Double>();
        for(int cage=0;cage<10;cage++){x.add(84.+cage*52);x.add(96.+cage*52);}
        int[] bounds=FrameSupportBarDetector.internalDividerSearchBounds(x,700);
        assertNotNull(bounds);
        // Internal dividers are at 116,168,...532; the modest margin excludes
        // outer frame edges at approximately 64 and 584.
        assertTrue(bounds[0]<116 && bounds[0]>90);
        assertTrue(bounds[1]>532 && bounds[1]<558);
        assertEquals(9,bounds[2]);
    }

    @Test public void doesNotInferFrameGeometryFromMissingCapillaries() {
        java.util.ArrayList<Double> x=new java.util.ArrayList<Double>();
        for(int cage=0;cage<8;cage++){x.add(84.+cage*60);x.add(96.+cage*60);}
        int[] bounds=FrameSupportBarDetector.internalDividerSearchBounds(x,700);
        assertNull(bounds);
    }

    @Test public void buildsGuideWhenOneCageHasOnlyOneCapillary() {
        java.util.ArrayList<Double> cages=new java.util.ArrayList<Double>();
        for(int cage=0;cage<10;cage++)cages.add(100.+cage*72);
        double[] guide=FrameSupportBarDetector.frameGridGuideFromCageCenters(cages,1280);
        assertNotNull(guide);
        assertEquals(72.,guide[2],.01);
        assertEquals(10.,guide[3],.01);
    }

    @Test public void findsElevenFrameBarsWhenTwoCagesHaveNoCapillaries() {
        int w=800,h=320,top=65,bottom=88;
        double[] p=new double[w*h]; java.util.Arrays.fill(p,210);
        for(int y=top;y<=bottom;y++)for(int x=70;x<=670;x++)p[y*w+x]=50;
        for(int k=0;k<=10;k++){int x=70+k*60;for(int y=bottom+1;y<h;y++)
            for(int dx=-6;dx<=6;dx++)p[y*w+x+dx]=80;}
        java.util.ArrayList<Double> capX=new java.util.ArrayList<Double>();
        // Only cages 2..9 contain their L/R capillary pair.
        for(int cage=2;cage<10;cage++){double centre=100+cage*60;capX.add(centre-7);capX.add(centre+7);}
        double[] guide=FrameSupportBarDetector.frameGridGuide(capX,w);
        FrameSupportBarDetector.Result r=new FrameSupportBarDetector().detectUsingFrameGrid(p,w,h,30,270,guide);
        assertEquals(9,r.dividers.size());
        assertEquals(70,r.frameLeft,8);
        assertEquals(670,r.frameRight,8);
        assertEquals(600,r.frameWidth,12);
    }

    @Test public void guidedSearchFindsSevenPaleDividersBelowGlobalThreshold() {
        int w=700,h=300,top=70,bottom=92;
        double[] p=new double[w*h]; java.util.Arrays.fill(p,210);
        for(int y=top;y<=bottom;y++)for(int x=80;x<=559;x++)p[y*w+x]=45;
        // Dark capillary-like clutter controls the global low percentile, while
        // the seven real dividers are much paler and require local refinement.
        for(int x=120;x<590;x+=60)for(int y=bottom+1;y<170;y++)
            for(int dx=-4;dx<=4;dx++)p[y*w+x+dx]=25;
        for(int i=1;i<=7;i++){int x=80+i*60;for(int y=bottom+1;y<h;y++)
            for(int dx=-6;dx<=6;dx++)p[y*w+x+dx]=145;}
        FrameSupportBarDetector.Result r=new FrameSupportBarDetector().detect(p,w,h,35,250,
                125,555,7);
        assertEquals(7,r.dividers.size());
        assertEquals(80,r.frameLeft,8);
        assertEquals(480,r.frameWidth,16);
    }

    @Test public void detectsContinuousUpperEdgeAndClipsLowerEdgeAtDividers() {
        int w=240,h=140,top=48,bottom=60;
        double[] p=new double[w*h];
        java.util.Arrays.fill(p, 200);
        for(int y=top;y<=bottom;y++) for(int x=15;x<=224;x++) p[y*w+x]=40;
        for(int x : new int[]{55,105,155,205}) for(int y=bottom+1;y<h;y++)
            for(int dx=-4;dx<=4;dx++) p[y*w+x+dx]=40;
        FrameSupportBarDetector.Result r=new FrameSupportBarDetector().detect(p,w,h,20,100);
        assertTrue(r.found());
        assertEquals(top, r.upperY, 1);
        assertEquals(210, r.frameWidth, 8);
        assertTrue("lower edge should be split around dividers", r.lower.size() >= 4);
        assertTrue(r.confidence > 2.5);
    }

    @Test public void rejectsBlankImage() {
        assertFalse(new FrameSupportBarDetector().detect(new double[100*80],100,80,10,70).found());
    }

    @Test public void rejectsShallowFalseLowerEdge() {
        int w=240,h=140,top=45,bottom=75;
        double[] p=new double[w*h]; java.util.Arrays.fill(p,200);
        for(int y=top;y<=bottom;y++) for(int x=15;x<=224;x++) p[y*w+x]=40;
        // A local internal transition is stronger, but occurs at the wrong bar thickness.
        for(int x=175;x<=224;x++) for(int y=top+10;y<=bottom;y++) p[y*w+x]=200;
        FrameSupportBarDetector.Result r=new FrameSupportBarDetector().detect(p,w,h,20,110);
        assertTrue(r.found());
        assertFalse(r.lower.isEmpty());
        for(java.awt.geom.Line2D line:r.lower) assertEquals(bottom, line.getY1(), 2);
    }

    @Test public void derivesFrameFromNinePersistentDividersWithoutCapillaries() {
        int w=600,h=220,top=60,bottom=82;
        double[] p=new double[w*h]; java.util.Arrays.fill(p,210);
        for(int y=top;y<=bottom;y++)for(int x=40;x<=559;x++)p[y*w+x]=45;
        for(int i=1;i<=9;i++){int x=40+i*52;for(int y=bottom+1;y<h;y++)for(int dx=-5;dx<=5;dx++)p[y*w+x+dx]=45;}
        // Wide capillary-like dark lines look plausible near the support but stop
        // before the deeper divider-validation band.
        for(int x=66;x<550;x+=52)for(int y=bottom+1;y<145;y++)
            for(int dx=-4;dx<=4;dx++)p[y*w+x+dx]=70;
        FrameSupportBarDetector.Result r=new FrameSupportBarDetector().detect(p,w,h,30,190);
        assertEquals(9,r.dividers.size());
        assertEquals(40,r.frameLeft,4);
        assertEquals(560,r.frameRight,4);
        assertEquals(520,r.frameWidth,8);
    }

    @Test public void roiGuidedSearchCanUseFaintShallowDividers() {
        int w=700,h=300,top=55,bottom=78;
        double[] p=new double[w*h]; java.util.Arrays.fill(p,210);
        for(int y=top;y<=bottom;y++)for(int x=70;x<=629;x++)p[y*w+x]=45;
        // All nine separators are clear near the support but disappear before the
        // deep validation band, as in a faint/obstructed real frame.
        for(int i=1;i<=9;i++){int x=70+i*56;for(int y=bottom+1;y<145;y++)
            for(int dx=-5;dx<=5;dx++)p[y*w+x+dx]=50;}
        FrameSupportBarDetector.Result r=new FrameSupportBarDetector().detect(p,w,h,25,250,110,590);
        assertEquals(9,r.dividers.size());
        assertEquals(70,r.frameLeft,5);
        assertEquals(630,r.frameRight,5);
    }

    @Test public void rejectsAlternatingCapillariesAndBroadDarkRightBackground() {
        int w=700,h=300,top=55,bottom=78;
        double[] p=new double[w*h]; java.util.Arrays.fill(p,210);
        for(int y=top;y<=bottom;y++)for(int x=70;x<=629;x++)p[y*w+x]=45;
        // The real nine dividers continue deep into the cages.
        for(int i=1;i<=9;i++){int x=70+i*56;for(int y=bottom+1;y<h;y++)
            for(int dx=-5;dx<=5;dx++)p[y*w+x+dx]=45;}
        // Ten capillaries alternate with the dividers and are just as wide/dark in
        // the shallow search region, reproducing the inconsistent real recording.
        for(int i=0;i<10;i++){int x=98+i*56;for(int y=bottom+1;y<155;y++)
            for(int dx=-5;dx<=5;dx++)p[y*w+x+dx]=55;}
        // A broad dark area at the right edge must not act as a narrow divider.
        for(int y=bottom+1;y<h;y++)for(int x=650;x<w;x++)p[y*w+x]=45;
        FrameSupportBarDetector.Result r=new FrameSupportBarDetector().detect(p,w,h,25,250);
        assertEquals(9,r.dividers.size());
        assertEquals(70,r.frameLeft,5);
        assertEquals(630,r.frameRight,5);
        assertEquals(560,r.frameWidth,10);
    }
}
