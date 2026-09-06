package plugins.fmp.multitools.service.tracking;

import static org.junit.Assert.*;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import javax.imageio.ImageIO;
import org.junit.Test;
import plugins.fmp.multitools.service.FrameSupportBarDetector;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillary.geometry.CapillaryPhaseGeometryPersistence;

public class RackTranslationTrackerTest {
    @org.junit.BeforeClass public static void initializeIcy() { icy.preferences.IcyPreferences.init(); }
    private static final int W = 800, H = 320;

    private double[] scene(int dx, int dy, boolean clutter) {
        double[] image = new double[W*H]; Arrays.fill(image, 210);
        for (int y=0; y<H; y++) for (int x=0; x<W; x++) {
            int u=x-dx, v=y-dy;
            if (u>=70 && u<=670 && v>=65 && v<=88) image[y*W+x]=50;
            for (int k=0;k<=10;k++)
                if (Math.abs(u-(70+k*60))<=6 && v>88) image[y*W+x]=80;
            // Paired capillary walls, deliberately away from rack junctions.
            for (int k=0;k<10;k++)
                if ((Math.abs(u-(97+k*60))<=1 || Math.abs(u-(103+k*60))<=1) && v>=20 && v<=140)
                    image[y*W+x]=110;
            if (clutter && ((x-285)*(x-285)+(y-140)*(y-140)<180
                    || (x-100)*(x-100)+(y-25)*(y-25)<120)) image[y*W+x]=15;
        }
        return image;
    }

    private RackTranslationTracker tracker() {
        double[] reference=scene(0,0,false);
        FrameSupportBarDetector.Result rack=new FrameSupportBarDetector().detectUsingFrameGrid(
                reference,W,H,30,270,new double[]{100,640,60,10});
        return new RackTranslationTracker(reference,W,H,rack);
    }

    @Test public void followsSlowDriftThenStableWithoutAccumulatingError() {
        RackTranslationTracker tracker=tracker();
        for (int t=0;t<16;t++) {
            int dy=Math.min(t,10);
            RackTranslationTracker.Estimate e=tracker.estimate(scene(3,dy,true),W,H);
            assertTrue("frame "+t+": "+e.reason,e.reliable);
            assertEquals(3,e.dx,.01); assertEquals(dy,e.dy,.01);
        }
    }

    @Test public void stableAndBrightnessChangedRemainStationary() {
        double[] image=scene(0,0,true);
        for(int i=0;i<image.length;i++) image[i]=image[i]*.6+20;
        RackTranslationTracker.Estimate e=tracker().estimate(image,W,H);
        assertTrue(e.reason,e.reliable); assertEquals(0,e.dx,.01); assertEquals(0,e.dy,.01);
    }

    @Test public void rejectsBlankDimensionChangeAndSearchLimit() {
        RackTranslationTracker tracker=tracker();
        assertFalse(tracker.estimate(new double[W*H],W,H).reliable);
        assertFalse(tracker.estimate(scene(0,0,false),W/2,H*2).reliable);
        assertFalse(tracker.estimate(scene(0,25,false),W,H).reliable);
    }

    @Test public void rejectsNonTranslationalRackMotion() {
        double[] image=scene(0,0,false), sheared=new double[W*H]; Arrays.fill(sheared,210);
        for(int x=0;x<W;x++) for(int y=0;y<H;y++) {
            int sourceY=y-(x-400)/30;
            if(sourceY>=0 && sourceY<H) sheared[y*W+x]=image[sourceY*W+x];
        }
        assertFalse(tracker().estimate(sheared,W,H).reliable);
    }

    @Test public void compressesPhasesRetainsUncertainAndRoundTripsWithoutTouchingGroundTruth() throws Exception {
        Capillary cap=new Capillary(); cap.setKymographName("line01");
        Line2D blue=new Line2D.Double(100,20,104,140);
        cap.getPhaseGeometry().initialize(0,new Line2D.Double(107,0,111,160),blue);
        cap.getPhaseGeometry().putBlue(99,new Line2D.Double(200,20,204,140));
        RackTrackingService.Scan scan=new RackTrackingService.Scan();
        Map<Long,Line2D> phases=new TreeMap<>(); phases.put(0L,blue); scan.geometry.put(cap,phases);
        for(int t=1;t<=12;t++) scan.accept(t,new RackTranslationTracker.Estimate(0,Math.min(t,10),1,true,"test"),2);
        scan.accept(13,RackTranslationTracker.Estimate.uncertain("occluded"),2);
        scan.frames=14; scan.apply();
        assertEquals(6,scan.phases); assertEquals(1,scan.uncertain);
        assertNull(cap.getPhaseGeometry().getBlueStartingAt(99));
        assertEquals(30,cap.getPhaseGeometry().getBlueAt(13).getY1(),.001);
        assertEquals(blue.getP1().distance(blue.getP2()),cap.getPhaseGeometry().getBlueAt(13).getP1()
                .distance(cap.getPhaseGeometry().getBlueAt(13).getP2()),1e-9);
        assertEquals(100,cap.getPhaseGeometry().getBlueAt(0).getX1(),.001);
        java.nio.file.Path dir=Files.createTempDirectory("rack-phases");
        java.nio.file.Path truth=dir.resolve("CapillariesDescription-groundtruth.csv");
        Files.write(truth,Arrays.asList("manual reference")); byte[] original=Files.readAllBytes(truth);
        Capillaries caps=new Capillaries(); caps.addCapillary(cap);
        assertTrue(CapillaryPhaseGeometryPersistence.save(caps,dir.toString()));
        assertArrayEquals(original,Files.readAllBytes(truth));
        assertTrue(CapillaryPhaseGeometryPersistence.load(caps,dir.toString()));
        assertEquals(6,cap.getPhaseGeometry().getBlueKeyframes().size());
        assertEquals(Long.valueOf(10),cap.getPhaseGeometry().getBluePhaseStartAt(13));
    }

    @Test public void cancelledScanCannotApply() {
        RackTrackingService.Scan scan=new RackTrackingService.Scan(); scan.cancelled=true; scan.frames=10;
        try { scan.apply(); fail("cancelled scan applied"); } catch(IllegalStateException expected) { }
    }

    @Test public void scansJpegStackAndReportsEveryFrameBeforeApplying() throws Exception {
        java.nio.file.Path root=Files.createTempDirectory("rack-jpeg-stack");
        plugins.fmp.multitools.experiment.Experiment exp=new plugins.fmp.multitools.experiment.Experiment();
        exp.setResultsDirectory(root.resolve("results").toString());
        java.util.List<String> paths=new java.util.ArrayList<>();
        for(int t=0;t<4;t++) {
            double[] p=scene(0,Math.min(t*2,4),true);
            BufferedImage image=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
            for(int y=0;y<H;y++) for(int x=0;x<W;x++) {
                int v=(int)p[y*W+x]; image.setRGB(x,y,new Color(v,v,v).getRGB());
            }
            java.nio.file.Path path=root.resolve("frame"+t+".jpg");
            ImageIO.write(image,"jpg",path.toFile()); paths.add(path.toString());
        }
        exp.getSeqCamData().setImagesList(paths);
        for(int k=0;k<10;k++) {
            Capillary cap=new Capillary(); cap.setCageID(k);
            cap.setKymographName("line"+k);
            Line2D blue=new Line2D.Double(100+k*60,20,100+k*60,140);
            cap.getPhaseGeometry().initialize(0,new Line2D.Double(105+k*60,0,105+k*60,160),blue);
            exp.getCapillaries().addCapillary(cap);
        }
        RackTrackingService.Scan scan=new RackTrackingService().scan(exp,2,()->false,frame->{});
        assertEquals(4,scan.frames); assertEquals(0,scan.uncertain); assertEquals(3,scan.phases);
        assertEquals(5,Files.readAllLines(scan.reportPath).size());
        assertEquals(1,exp.getCapillaries().getList().get(0).getPhaseGeometry().getBlueKeyframes().size());
        scan.apply();
        assertEquals(24,exp.getCapillaries().getList().get(0).getPhaseGeometry().getBlueAt(3).getY1(),.01);
    }

    @Test public void writesSyntheticVisualValidation() throws Exception {
        BufferedImage montage=new BufferedImage(W,2*(H+36),BufferedImage.TYPE_INT_RGB);
        Graphics2D g=montage.createGraphics();
        for(int panel=0;panel<2;panel++) {
            int dx=panel*3,dy=panel*10,offset=panel*(H+36);
            double[] pixels=scene(dx,dy,panel==1);
            RackTranslationTracker.Estimate e=tracker().estimate(pixels,W,H);
            assertTrue(e.reason,e.reliable);
            for(int y=0;y<H;y++) for(int x=0;x<W;x++) {
                int v=(int)pixels[y*W+x]; montage.setRGB(x,y+offset+36,new Color(v,v,v).getRGB());
            }
            g.setColor(Color.WHITE);
            g.drawString(panel==0 ? "Synthetic image 0: reference blue geometry" :
                    "Synthetic drift + clutter: recovered dx="+e.dx+", dy="+e.dy+" px",15,offset+23);
            g.setColor(new Color(70,160,255));
            for(int k=0;k<10;k++) g.drawLine(100+k*60+dx,20+dy+offset+36,100+k*60+dx,140+dy+offset+36);
        }
        g.dispose(); Files.createDirectories(Paths.get("target"));
        ImageIO.write(montage,"png",Paths.get("target","rack-tracking-validation.png").toFile());
    }
}
