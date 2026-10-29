package plugins.fmp.multitools.service;

import java.util.ArrayList;
import java.util.List;

/** Local confirmation of steps hidden by a declining correction residual.
 * A quadratic nuisance trend may move in either direction. Only the step is intake.
 * Saved quality flags exclude observations from the flanks, not the whole interval.
 */
final class IntakeLocalSteps {
    private IntakeLocalSteps() { }

    private static final class Fit {
        double jump, score, bottom;
    }

    static IntakeEpisodes.Detection refine(IntakeEpisodes.Detection baseline, double[] levels,
            double[] times, boolean[] valid, List<double[]> controls, double automatic, double threshold,
            IntakeEpisodes.StepEvidence evidence) {
        int n = levels.length;
        if(n<7 || baseline.position==null) return baseline;
        double[] net = baseline.net.clone(), position = baseline.position.clone();
        double unit = (times[n-1]-times[0])/Math.max(1,n-1);
        double scale = automatic > 0 ? threshold/automatic : 1;
        double[] limits=new double[4];
        for(int width=1;width<=3;width++) {
            List<Double> background = new ArrayList<>();
            for (double[] control : controls) {
            double[] y = new double[n];
            boolean[] usable = new boolean[n];
            usable[0] = true;
            for (int i=1;i<n;i++) {
                usable[i] = Double.isFinite(control[i]);
                y[i] = (Double.isFinite(y[i-1]) ? y[i-1] : 0)
                        + (usable[i] ? control[i]*(times[i]-times[i-1]) : 0);
            }
            for (int i=6;i+5<n;i++) {
                if(i+width+4>=n) continue;
                Fit f = fit(y,times,usable,i,i+width-1);
                if (f != null) background.add(f.score);
            }
            }
            background.sort(Double::compare);
            // A rescue needs independent, duration-matched control support.
            if (background.size()<30) {limits[width]=Double.POSITIVE_INFINITY;continue;}
            double empirical = background.get(Math.min(background.size()-1,
                    (int)Math.ceil(.995*background.size())-1));
            limits[width] = Math.max(empirical*scale, threshold*unit/Math.sqrt(2));
        }
        List<Integer> candidates = new ArrayList<>();
        for(int i=3;i+2<n;i++) {
            double delta = levels[i]-levels[i-1];
            if (Double.isFinite(delta) && delta>threshold*(times[i]-times[i-1])) candidates.add(i);
        }
        candidates.sort((a,b)->Double.compare(levels[b]-levels[b-1],levels[a]-levels[a-1]));
        int added=0;
        for(int i:candidates) {
            int first=i,last=i;
            // Bridge short uncertain runs, and include a directly adjacent
            // resolved-size increment that the constant fit may have erased.
            while(first>1 && i-first<2 && levels[first-1]>levels[first-2]
                    && (!valid[first-1] || levels[first-1]-levels[first-2]
                            >threshold*(times[first-1]-times[first-2]))) first--;
            while(last+1<n && last-first<2 && levels[last+1]>levels[last]
                    && (!valid[last+1] || levels[last+1]-levels[last]
                            >threshold*(times[last+1]-times[last]))) last++;
            // Keep already separated boundaries. A short span with one accepted
            // boundary and one missing increment is fitted jointly, not vetoed.
            boolean occupied=false;
            int existing=0;
            double established=0;
            for(int k=Math.max(1,first-1);k<=Math.min(n-1,last+1);k++)
                if(net[k]>0) {
                    if(k<first || k>last) occupied=true;
                    else {existing++;established+=net[k];}
                }
            if (occupied || existing>1) continue;
            Fit f = fit(levels,times,valid,first,last);
            if(f==null || !(f.score>limits[last-first+1]) || !(f.jump>threshold*unit)) continue;
            double delta=levels[last]-levels[first-1];
            boolean returns=false;
            for(int k=last+1;k<=Math.min(n-1,last+6);k++)
                if(levels[k]-levels[k-1]<-.6*f.jump) returns=true;
            if(returns) continue;
            for(int k=Math.max(1,first-6);k<first;k++)
                if(levels[k]-levels[k-1]<-.6*f.jump) returns=true;
            if(returns || (evidence!=null && !evidence.supports(i))) continue;
            // A sharp change must actually occur at the proposed sample; a distant
            // neighbouring step cannot supply its amplitude through the flank fit.
            if(!(delta>0) || f.jump>1.5*delta) continue;
            // Preserve established v5 amplitudes unless their fit lost most of a
            // separately confirmed local step. No global refit can erase other events.
            boolean redistribute=last>first && existing==1;
            if(existing>0 && established>=.65*f.jump && !redistribute) continue;
            // An adjacent increment may already be included in the old bar's
            // volume. Redistribute that volume unless extra local displacement
            // independently clears the amount threshold; never count it twice.
            double amount=redistribute && f.jump-established<=threshold*unit
                    ? established : Math.max(established,f.jump);
            double weight=0;
            for(int k=first;k<=last;k++) weight+=Math.max(0,levels[k]-levels[k-1]);
            if(!(amount>threshold*unit) || !(weight>0)) continue;
            if(existing==0) added++;
            double bottom=f.bottom-amount;
            position[first-1]=bottom;
            for(int k=first;k<=last;k++) {
                net[k]=amount*Math.max(0,levels[k]-levels[k-1])/weight;
                bottom+=net[k];position[k]=bottom;
            }
        }
        return new IntakeEpisodes.Detection(net,baseline.episodes+added,position,baseline.gaps);
    }

    /** Project a step off a quadratic background using reliable flanking samples.
     * At least three observations on each side prevent a one/two-frame excursion
     * or an unsupported endpoint from becoming a rescued intake.
     */
    private static Fit fit(double[] y,double[] time,boolean[] valid,int boundary,int end) {
        List<Integer> samples=new ArrayList<>();
        int before=0,after=0;
        for(int i=Math.max(0,boundary-6);i<=Math.min(y.length-1,end+5);i++) {
            if(i>=boundary && i<end) continue;
            if((i==0 || valid[i]) && Double.isFinite(y[i])) {
                samples.add(i);
                if(i<boundary) before++; else after++;
            }
        }
        if(before<3 || after<3) return null;
        double origin=(time[boundary-1]+time[end])/2;
        double span=time[samples.get(samples.size()-1)]-time[samples.get(0)];
        if(!(span>0)) return null;
        double[][] gram=new double[4][4];
        double[] rhs=new double[4];
        for(int i:samples) {
            double x=(time[i]-origin)/span;
            double[] basis={1,x,x*x,i>=boundary?1:0};
            for(int j=0;j<4;j++) {
                rhs[j]+=basis[j]*y[i];
                for(int k=0;k<4;k++) gram[j][k]+=basis[j]*basis[k];
            }
        }
        // Solve together with the step unit vector to obtain its information.
        double[][] a=new double[4][6];
        for(int j=0;j<4;j++) {
            System.arraycopy(gram[j],0,a[j],0,4);a[j][4]=rhs[j];a[j][5]=j==3?1:0;
        }
        for(int k=0;k<4;k++) {
            int pivot=k;
            for(int j=k+1;j<4;j++) if(Math.abs(a[j][k])>Math.abs(a[pivot][k])) pivot=j;
            if(Math.abs(a[pivot][k])<1e-10) return null;
            double[] row=a[k];a[k]=a[pivot];a[pivot]=row;
            double divisor=a[k][k];for(int j=k;j<6;j++) a[k][j]/=divisor;
            for(int i=0;i<4;i++) if(i!=k) {
                double factor=a[i][k];for(int j=k;j<6;j++) a[i][j]-=factor*a[k][j];
            }
        }
        if(!(a[3][5]>0)) return null;
        Fit f=new Fit();f.jump=a[3][4];f.score=f.jump/Math.sqrt(a[3][5]);
        double x=(time[end]-origin)/span;
        f.bottom=a[0][4]+a[1][4]*x+a[2][4]*x*x+f.jump;
        return f;
    }
}
