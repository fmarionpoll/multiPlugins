package plugins.fmp.multitools.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Review diagnostics, deliberately separate from intake acceptance and volume. */
public final class IntakeDiagnostics {
    private IntakeDiagnostics() { }
    public enum Kind { STEP, DRIFT, INITIAL_ANCHOR, RECONSTRUCTION_OFFSET }
    public static final class Warning {
        public final Kind kind;
        public final int first, last;
        /** Positive: measured meniscus is farther down than the prediction. */
        public final double excess, tolerance;
        public final boolean reliable, noFly;
        Warning(Kind kind, int first, int last, double excess, double tolerance, boolean reliable, boolean noFly) {
            this.kind=kind; this.first=first; this.last=last; this.excess=excess;
            this.tolerance=tolerance; this.reliable=reliable; this.noFly=noFly;
        }
        public String label() {
            String label = kind == Kind.STEP ? (noFly ? "Control: abrupt " + (excess>0 ? "downward" : "upward") + " change"
                    : excess>0 ? "Abrupt downward step: possible missed intake" : "Abrupt upward step: tracking/model review")
                    : kind == Kind.DRIFT ? (noFly ? "Control: evaporation/tracking drift" : "Gradual evaporation/model drift")
                    : kind == Kind.INITIAL_ANCHOR ? "Initial meniscus anchor uncertain"
                    : excess > 0 ? "Reconstruction above measured meniscus" : "Reconstruction below measured meniscus";
            return label + (reliable ? "" : " (uncertain)");
        }
    }

    public static List<Warning> analyze(double[] raw, double[] corrected, double[] evaporation,
            double[] times, boolean[] valid, IntakeEpisodes.Detection detection,
            List<double[]> controls, double threshold, boolean noFly) {
        List<Warning> warnings=new ArrayList<>();
        int n=raw.length;
        if(n<4) return warnings;
        double base=Math.max(1, (Double.isFinite(threshold) ? threshold : 1)
                * (times[n-1]-times[0])/(n-1));
        double localTolerance=localTolerance(controls,times,base);
        int start=0;
        for(int i=1;i<=n;i++) {
            if(i<n && !(detection.net[i]>0)) continue;
            int last=i-1;
            if(last-start>=3) {
                int flank=Math.min(3,(last-start+1)/2);
                double excess=median(corrected,last-flank+1,last)-median(corrected,start,start+flank-1);
                double tolerance=tolerance(controls,times,last-start-flank+1,base);
                if(Double.isFinite(excess) && Math.abs(excess)>tolerance) {
                    double strongest=0;
                    for(int k=start+1;k<=last;k++) {
                        double step=median(corrected,k,Math.min(last,k+2))
                                -median(corrected,Math.max(start,k-3),k-1);
                        if(Math.signum(step)==Math.signum(excess)) strongest=Math.max(strongest,Math.abs(step));
                    }
                    boolean reliable=true;
                    for(int k=start+1;k<=last;k++) reliable &= valid[k];
                    Kind kind=strongest>=.65*Math.abs(excess) && strongest>tolerance ? Kind.STEP : Kind.DRIFT;
                    warnings.add(new Warning(kind,start,last,excess,tolerance,reliable,noFly));
                }
            }
            // Search locally even when evaporation drift cancels a missed step
            // over the whole pause. Estimate the trend from the two flanks rather
            // than requiring net excess over the shared evaporation model.
            List<Warning> local=new ArrayList<>();
            for(int k=start+3;k+2<=last;k++) {
                double before=median(corrected,k-3,k-1), after=median(corrected,k,k+2);
                double left=(corrected[k-1]-corrected[k-3])/(times[k-1]-times[k-3]);
                double right=(corrected[k+2]-corrected[k])/(times[k+2]-times[k]);
                double jump=after-before-(left+right)/2*(times[k+1]-times[k-2]);
                double tolerance=localTolerance;
                if(!Double.isFinite(jump) || Math.abs(jump)<=tolerance) continue;
                boolean covered=false;
                for(Warning warning:warnings)
                    if(warning.kind==Kind.STEP && warning.first<=k && warning.last>=k) covered=true;
                if(covered) continue;
                boolean reliable=true;
                for(int j=k-2;j<=k+2;j++) reliable &= valid[j];
                local.add(new Warning(Kind.STEP,k-3,k+2,jump,tolerance,reliable,noFly));
            }
            local.sort((a,b)->Double.compare(Math.abs(b.excess),Math.abs(a.excess)));
            List<Warning> chosen=new ArrayList<>();
            for(Warning candidate:local) {
                boolean overlaps=false;
                for(Warning other:chosen) if(Math.abs(other.first-candidate.first)<=3) overlaps=true;
                if(!overlaps) chosen.add(candidate);
            }
            warnings.addAll(chosen);
            start=i;
        }
        double[] residual=new double[n];
        double cumulative=0;
        for(int i=0;i<n;i++) {
            cumulative+=detection.net[i];
            residual[i]=corrected[i]-(raw[0]-evaporation[0]+cumulative);
        }
        // A local initial discrepancy is assessed separately from long-term drift.
        // It may be a bad first position OR unresolved early feeding; do not re-anchor.
        double anchor=median(residual,1,Math.min(n-1,3));
        if (!Double.isFinite(raw[0]) || !Double.isFinite(corrected[0])
                || (Double.isFinite(anchor) && Math.abs(anchor)>base))
            warnings.add(new Warning(Kind.INITIAL_ANCHOR,0,Math.min(n-1,4),anchor,base,false,noFly));
        // Both directions matter. A sustained global offset is not automatically
        // a missed gulp: it may be an anchor, evaporation or amplitude error.
        for(int sign:new int[]{-1,1}) {
            int first=-1;
            for(int i=0;i<=n;i++) {
                if(i<n && Double.isFinite(residual[i]) && sign*residual[i]>base) {
                    if(first<0) first=i;
                } else if(first>=0) {
                    if(i-first>=4) warnings.add(new Warning(Kind.RECONSTRUCTION_OFFSET,first,i-1,
                            median(residual,first,i-1),base,false,noFly));
                    first=-1;
                }
            }
        }
        return warnings;
    }

    private static double tolerance(List<double[]> controls,double[] times,int width,double floor) {
        List<Double> sums=new ArrayList<>();
        for(double[] control:controls) for(int a=1;a+width<=control.length;a++) {
            double amount=0;
            for(int i=a;i<a+width;i++) amount+=control[i]*(times[i]-times[i-1]);
            if(Double.isFinite(amount)) sums.add(Math.abs(amount));
        }
        if(sums.size()<30) return floor;
        sums.sort(Double::compare);
        return Math.max(floor,sums.get(Math.min(sums.size()-1,(int)Math.ceil(.995*sums.size())-1)));
    }

    /** Calibrate the same detrended flank statistic that is scanned in the
     * selected trace; ordinary sums underestimate its noise after slope fitting. */
    private static double localTolerance(List<double[]> controls,double[] times,double floor) {
        List<Double> changes=new ArrayList<>();
        for(double[] control:controls) {
            double[] level=new double[control.length];
            for(int i=1;i<level.length;i++) {
                if(!Double.isFinite(control[i]))level[i]=Double.NaN;
                else level[i]=(Double.isFinite(level[i-1])?level[i-1]:0)+control[i]*(times[i]-times[i-1]);
            }
            for(int k=3;k+2<level.length;k++) {
                boolean finite=true;
                for(int i=k-3;i<=k+2;i++)finite&=Double.isFinite(level[i]);
                if(!finite)continue;
                double left=(level[k-1]-level[k-3])/(times[k-1]-times[k-3]);
                double right=(level[k+2]-level[k])/(times[k+2]-times[k]);
                double jump=median(level,k,k+2)-median(level,k-3,k-1)
                        -(left+right)/2*(times[k+1]-times[k-2]);
                if(Double.isFinite(jump))changes.add(Math.abs(jump));
            }
        }
        if(changes.size()<30)return floor;
        changes.sort(Double::compare);
        return Math.max(floor,changes.get(Math.min(changes.size()-1,(int)Math.ceil(.995*changes.size())-1)));
    }

    public static final class Estimate {
        public final double before, after, evaporation, net;
        public final double localRate, localNet;
        public final int beforeSamples, afterSamples;
        public final boolean reliable, localReliable;
        Estimate(double before,double after,double evaporation,int beforeSamples,int afterSamples,boolean reliable,
                double localRate,double localNet,boolean localReliable) {
            this.before=before; this.after=after; this.evaporation=evaporation;
            net=after-before-evaporation; this.beforeSamples=beforeSamples; this.afterSamples=afterSamples;
            this.reliable=reliable;this.localRate=localRate;this.localNet=localNet;this.localReliable=localReliable;
        }
    }

    /** Estimate from flanking plateau positions, allowing failed correlations
     * inside the interval. It does not alter accepted events or consumption.
     */
    public static Estimate estimate(double[] corrected,double[] evaporation,boolean[] valid,int first,int last) {
        double[] time=new double[corrected.length];for(int i=0;i<time.length;i++)time[i]=i;
        return estimate(corrected,evaporation,time,valid,first,last);
    }

    public static Estimate estimate(double[] corrected,double[] evaporation,double[] times,boolean[] valid,int first,int last) {
        if(first<1 || last<first || last>=corrected.length)
            throw new IllegalArgumentException("Choose interval bins 1 through the last frame, in increasing order.");
        int a=Math.max(0,first-3),b=Math.min(corrected.length-1,last+2);
        double before=median(corrected,a,first-1)+evaporation[first-1];
        double after=median(corrected,last,b)+evaporation[last];
        int pre=first-a,post=b-last+1,goodPre=0,goodPost=0;
        for(int i=a;i<first;i++) if(i==0 || valid[i]) goodPre++;
        for(int i=last;i<=b;i++) if(valid[i]) goodPost++;
        double[] physical=corrected.clone();
        for(int i=0;i<physical.length;i++)physical[i]+=evaporation[i];
        List<Double> slopes=new ArrayList<>();
        addSlopes(slopes,physical,times,valid,a,first-1);
        addSlopes(slopes,physical,times,valid,last,b);
        double[] sorted=slopes.stream().mapToDouble(Double::doubleValue).toArray();
        double slope=sorted.length==0?Double.NaN:median(sorted,0,sorted.length-1);
        double rate=Math.max(0,slope);
        double localNet=median(physical,last,b)-median(physical,a,first-1)
                -rate*(median(times,last,b)-median(times,a,first-1));
        double[] deviations=Arrays.stream(sorted).map(s->Math.abs(s-slope)).toArray();
        boolean localReliable=pre>=3&&post>=3&&goodPre>=2&&goodPost>=2&&sorted.length>=4&&slope>=-.05
                &&median(deviations,0,deviations.length-1)<=Math.max(.1,.5*Math.abs(slope));
        return new Estimate(before,after,evaporation[last]-evaporation[first-1],pre,post,
                Double.isFinite(before) && Double.isFinite(after) && goodPre>=2 && goodPost>=2,
                rate,localNet,localReliable);
    }

    private static void addSlopes(List<Double> slopes,double[] physical,double[] times,boolean[] valid,int first,int last) {
        for(int a=first;a<=last;a++) for(int b=a+1;b<=last;b++)
            if((a==0||valid[a])&&valid[b]&&Double.isFinite(physical[a])&&Double.isFinite(physical[b])&&times[b]>times[a])
                slopes.add((physical[b]-physical[a])/(times[b]-times[a]));
    }

    private static double median(double[] values,int first,int last) {
        if(last<first)return Double.NaN;
        double[] finite=Arrays.stream(Arrays.copyOfRange(values,first,last+1)).filter(Double::isFinite).sorted().toArray();
        if(finite.length<Math.min(2,last-first+1)) return Double.NaN;
        return (finite[(finite.length-1)/2]+finite[finite.length/2])/2;
    }
}
