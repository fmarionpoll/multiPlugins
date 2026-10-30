package plugins.fmp.multitools.service;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

/** Image constraints from t +/- 1..3, robustly fitted without a temporal smoothing
 * penalty. Real feeding steps are supported by measured cross-step displacements.
 */
public final class MeniscusNeighbourRefinement {
    private static final int REACH=3;
    private MeniscusNeighbourRefinement() { }
    private static final class Edge {
        int a,b;
        double shift,weight,correlation,range;
        boolean rejected;
        VerticalCorrelation.Profile profile;
    }

    public static VerticalCorrelation.Result analyze(BufferedImage image,double[] raw,int radius,
            double correction,boolean adaptive,boolean[] pinned) {
        if(image==null || image.getWidth()!=raw.length || radius<2 || !(correction>=1)
                || !Double.isFinite(correction) || (pinned!=null && pinned.length!=raw.length))
            throw new IllegalArgumentException("Invalid neighbour-correlation inputs");
        int n=raw.length,h=image.getHeight();
        if(pinned!=null) for(int i=0;i<n;i++) if(pinned[i]&&!Double.isFinite(raw[i]))
            throw new IllegalArgumentException("Manual anchor has no finite meniscus position at column "+i);
        double[][][] color=new double[n][2][h];
        for(int t=0;t<n;t++) for(int y=0;y<h;y++) {
            int rgb=image.getRGB(t,y),red=(rgb>>16)&255;
            color[t][0][y]=((rgb>>8)&255)-red;
            color[t][1][y]=(rgb&255)-red;
        }
        List<Edge> edges=new ArrayList<>();
        VerticalCorrelation.Result result=new VerticalCorrelation.Result(n);
        for(int a=0;a<n;a++) {
            interrupted();
            if(!Double.isFinite(raw[a])) continue;
            for(int b=a+1;b<n && b<=a+REACH;b++) {
                if(!Double.isFinite(raw[b])) break; // Do not bridge missing measurements.
                VerticalCorrelation.Registration r=VerticalCorrelation.neighbourPair(color[a],color[b],
                        raw[a],raw[b],radius,correction,adaptive);
                if(!r.reason.isEmpty()) continue;
                Edge e=new Edge();e.a=a;e.b=b;e.shift=r.shift();e.correlation=r.correlation();e.range=r.range;
                e.profile=r.profile;
                e.weight=e.correlation*e.correlation*Math.min(1,r.separation()/.02)/(b-a);
                edges.add(e);
            }
        }
        double[] fitted=fit(raw,pinned,edges,raw.clone(),8);
        // A high pair correlation is not sufficient if its displacement conflicts
        // with the other independent image constraints.
        boolean removed=false;
        for(Edge e:edges) if(Math.abs(fitted[e.b]-fitted[e.a]-e.shift)>.75) {
            e.rejected=true;removed=true;
        }
        if(removed) fitted=fit(raw,pinned,edges,fitted,6);
        double[] sumCorrelation=new double[n],sumSquared=new double[n];
        for(Edge e:edges) {
            double residual=fitted[e.b]-fitted[e.a]-e.shift;
            if(e.rejected || Math.abs(residual)>.75) continue;
            for(int i:new int[]{e.a,e.b}) {
                result.neighbourSupport[i]++;
                sumCorrelation[i]+=e.correlation;sumSquared[i]+=residual*residual;
                result.searchRange[i]=Math.max(result.searchRange[i],e.range);
                if(result.profile[i]==VerticalCorrelation.Profile.NONE || e.profile!=VerticalCorrelation.Profile.FULL)
                    result.profile[i]=e.profile;
            }
        }
        for(int i=0;i<n;i++) {
            boolean fixed=pinned!=null && pinned[i] && Double.isFinite(raw[i]);
            int support=result.neighbourSupport[i];
            result.refined[i]=fixed || support==0 ? raw[i] : fitted[i];
            if(support>0) {
                result.correlation[i]=sumCorrelation[i]/support;
                result.fitResidual[i]=Math.sqrt(sumSquared[i]/support);
            }
            result.reason[i]=fixed ? "Manual anchor (fixed)" : support==0
                    ? "No reliable neighbouring image support; saved topraw retained"
                    : "Neighbour consensus: "+support+" image constraints";
            if(i>0) {
                result.shift[i]=result.refined[i]-result.refined[i-1];
                int required=Math.min(2,n-1);
                boolean previousFixed=pinned!=null && pinned[i-1] && Double.isFinite(raw[i-1]);
                result.reliable[i]=Double.isFinite(result.shift[i])
                        && (support>=required || fixed) && (result.neighbourSupport[i-1]>=required || previousFixed);
            }
            if(fixed) result.reliable[i]=true;
            if(!result.reliable[i] && Double.isFinite(raw[i]) && raw[i]>=0 && raw[i]<=h-1
                    && (raw[i]<=radius || raw[i]>=h-1-radius)) result.toprawFallback[i]=true;
        }
        MeniscusTemplateRefinement.refine(color,raw,radius,correction,pinned,result);
        return result;
    }

    private static double[] fit(double[] raw,boolean[] pinned,List<Edge> edges,double[] initial,int rounds) {
        int n=raw.length;
        double[] x=initial.clone();
        for(int round=0;round<rounds;round++) {
            interrupted();
            double[][] lower=new double[n][REACH+1];double[] rhs=new double[n];
            for(int i=0;i<n;i++) {
                double weight=Double.isFinite(raw[i]) ? .05/Math.max(1,Math.abs(x[i]-raw[i])) : 1;
                lower[i][0]=weight;
                rhs[i]=Double.isFinite(raw[i])?weight*raw[i]:0;
            }
            for(Edge e:edges) if(!e.rejected) {
                double residual=x[e.b]-x[e.a]-e.shift;
                double w=e.weight*Math.min(1,.5/Math.max(1e-12,Math.abs(residual)));
                lower[e.a][0]+=w;lower[e.b][0]+=w;lower[e.b][e.b-e.a]-=w;
                rhs[e.a]-=w*e.shift;rhs[e.b]+=w*e.shift;
            }
            // Eliminate fixed rows/columns exactly, rather than approximating a
            // manual constraint with a large penalty that may still move it.
            if(pinned!=null) {
                for(int b=0;b<n;b++) for(int lag=1;lag<=Math.min(REACH,b);lag++) {
                    int a=b-lag;
                    boolean pa=pinned[a]&&Double.isFinite(raw[a]),pb=pinned[b]&&Double.isFinite(raw[b]);
                    if(pa&&!pb) rhs[b]-=lower[b][lag]*raw[a];
                    if(pb&&!pa) rhs[a]-=lower[b][lag]*raw[b];
                    if(pa||pb) lower[b][lag]=0;
                }
                for(int i=0;i<n;i++) if(pinned[i]&&Double.isFinite(raw[i])) {lower[i][0]=1;rhs[i]=raw[i];}
            }
            x=solve(lower,rhs);
            for(int i=0;i<n;i++) if(!Double.isFinite(raw[i])) x[i]=Double.NaN;
        }
        return x;
    }

    /** Cholesky solve for the symmetric band of width three: O(n), not O(n^3). */
    private static double[] solve(double[][] a,double[] rhs) {
        int n=rhs.length;double[][] l=new double[n][REACH+1];
        for(int i=0;i<n;i++) for(int j=Math.max(0,i-REACH);j<=i;j++) {
            double value=a[i][i-j];
            for(int k=Math.max(0,i-REACH);k<j;k++) if(j-k<=REACH)
                value-=l[i][i-k]*l[j][j-k];
            if(i==j) {
                if(!(value>0)) throw new IllegalStateException("Invalid neighbour-fit matrix");
                l[i][0]=Math.sqrt(value);
            } else l[i][i-j]=value/l[j][0];
        }
        double[] x=rhs.clone();
        for(int i=0;i<n;i++) {
            for(int lag=1;lag<=Math.min(REACH,i);lag++) x[i]-=l[i][lag]*x[i-lag];
            x[i]/=l[i][0];
        }
        for(int i=n-1;i>=0;i--) {
            for(int j=i+1;j<n && j<=i+REACH;j++) x[i]-=l[j][j-i]*x[j];
            x[i]/=l[i][0];
        }
        return x;
    }

    private static void interrupted() {
        if(Thread.currentThread().isInterrupted()) throw new CancellationException();
    }
}
