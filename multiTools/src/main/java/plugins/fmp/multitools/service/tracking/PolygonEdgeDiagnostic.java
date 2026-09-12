package plugins.fmp.multitools.service.tracking;

import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;

/** Candidate edges only: no motion estimation or geometry updates. */
public final class PolygonEdgeDiagnostic {
    private PolygonEdgeDiagnostic() {}

    public static List<Point2D> detect(BufferedImage image, List<Point2D> corners) {
        if (corners.size() != 4) throw new IllegalArgumentException("Use exactly four corners.");
        Path2D polygon = new Path2D.Double();
        double cx=0, cy=0, sign=0;
        for (int i=0;i<4;i++) {
            Point2D a=corners.get(i), b=corners.get((i+1)%4), c=corners.get((i+2)%4);
            if (!Double.isFinite(a.getX()+a.getY())) throw new IllegalArgumentException("Invalid corner.");
            double cross=(b.getX()-a.getX())*(c.getY()-b.getY())-(b.getY()-a.getY())*(c.getX()-b.getX());
            if (Math.abs(cross)<1e-6 || (sign!=0 && cross*sign<0))
                throw new IllegalArgumentException("Use a convex, non-crossing four-corner band.");
            sign=cross;
            if(i==0) polygon.moveTo(a.getX(),a.getY()); else polygon.lineTo(a.getX(),a.getY());
            cx+=a.getX()/4; cy+=a.getY()/4;
        }
        polygon.closePath();
        double xx=0,xy=0,yy=0;
        for(Point2D p:corners) { double x=p.getX()-cx,y=p.getY()-cy; xx+=x*x;xy+=x*y;yy+=y*y; }
        double theta=.5*Math.atan2(2*xy,xx-yy), ux=Math.cos(theta),uy=Math.sin(theta), nx=-uy,ny=ux;
        double s0=Double.POSITIVE_INFINITY,s1=-s0,r0=s0,r1=-s0;
        for(Point2D p:corners) {
            double x=p.getX()-cx,y=p.getY()-cy,s=x*ux+y*uy,r=x*nx+y*ny;
            s0=Math.min(s0,s);s1=Math.max(s1,s);r0=Math.min(r0,r);r1=Math.max(r1,r);
        }
        if(s1-s0<2*(r1-r0)) throw new IllegalArgumentException("Draw a narrow band along ONE structural edge.");
        List<Point2D> result=new ArrayList<>();
        for(double s=s0;s<=s1;s+=2) {
            double best=6; Point2D candidate=null;
            for(double r=r0;r<=r1;r+=.5) {
                double x=cx+s*ux+r*nx,y=cy+s*uy+r*ny;
                if(x<2||y<2||x>=image.getWidth()-2||y>=image.getHeight()-2||!polygon.contains(x,y))continue;
                double gx=(grey(image,x+1,y)-grey(image,x-1,y))/2;
                double gy=(grey(image,x,y+1)-grey(image,x,y-1))/2;
                double normal=Math.abs(gx*nx+gy*ny), tangent=Math.abs(gx*ux+gy*uy);
                if(normal>best && normal>1.5*tangent) {best=normal;candidate=new Point2D.Double(x,y);}
            }
            if(candidate!=null)result.add(candidate);
        }
        return result;
    }
    private static double grey(BufferedImage b,double x,double y) {
        int p=b.getRGB((int)Math.round(x),(int)Math.round(y));
        return (((p>>16)&255)+((p>>8)&255)+(p&255))/3.;
    }
}
