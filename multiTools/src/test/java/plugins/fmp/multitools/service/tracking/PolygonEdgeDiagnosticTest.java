package plugins.fmp.multitools.service.tracking;

import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PolygonEdgeDiagnosticTest {
    private List<Point2D> band() {return Arrays.asList(new Point2D.Double(10,20),new Point2D.Double(110,40),new Point2D.Double(110,52),new Point2D.Double(10,32));}
    @Test public void followsTiltedEdgeInsideBand() {
        BufferedImage image=new BufferedImage(130,80,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<80;y++)for(int x=0;x<130;x++)image.setRGB(x,y,y>24+.2*x?0xffffff:0);
        List<Point2D> result=PolygonEdgeDiagnostic.detect(image,band());
        assertTrue(result.size()>40);
        for(Point2D p:result)assertTrue(Math.abs(p.getY()-(24+.2*p.getX()))<2);
    }
    @Test public void blankImageDoesNotInventEdges() {
        assertTrue(PolygonEdgeDiagnostic.detect(new BufferedImage(130,80,BufferedImage.TYPE_INT_RGB),band()).isEmpty());
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsCrossedPolygon() {
        List<Point2D> p=new ArrayList<>(band());Collections.swap(p,1,2);
        PolygonEdgeDiagnostic.detect(new BufferedImage(130,80,BufferedImage.TYPE_INT_RGB),p);
    }
}
