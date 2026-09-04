package plugins.fmp.multitools.service;

import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Diagnostic detector for the dark horizontal support above the fly cages. */
public final class FrameSupportBarDetector {
    public static final class Result {
        public final List<Line2D> upper = new ArrayList<Line2D>();
        public final List<Line2D> lower = new ArrayList<Line2D>();
        public final List<Line2D> dividers = new ArrayList<Line2D>();
        /** Deeply validated candidates, useful for diagnosing an incomplete 9-divider fit. */
        public final List<Line2D> dividerCandidates = new ArrayList<Line2D>();
        public int upperY;
        public int lowerY;
        public int frameLeft;
        public int frameRight;
        public int expectedDividers = 9;
        public double frameWidth;
        public double confidence;
        public boolean found() { return !upper.isEmpty(); }
    }

	public Result detect(double[] pixels, int width, int height, int yMin, int yMax) {
		return detect(pixels, width, height, yMin, yMax, width / 40, width - width / 40, false, 9);
	}

	public Result detect(double[] pixels, int width, int height, int yMin, int yMax, int xMin, int xMax) {
		return detect(pixels, width, height, yMin, yMax, xMin, xMax, true, 9);
	}

	public Result detect(double[] pixels, int width, int height, int yMin, int yMax, int xMin, int xMax,
			int expectedDividers) {
		return detect(pixels, width, height, yMin, yMax, xMin, xMax, true, expectedDividers);
	}

    /**
     * Uses all twenty capillaries to infer ten cage centres and delimit the nine
     * internal separators. Incomplete ROI sets are not safe geometry guides.
     */
    public static int[] internalDividerSearchBounds(List<Double> capillaryX, int imageWidth) {
        if (capillaryX == null || capillaryX.size()!=20) return null;
        ArrayList<Double> sorted = new ArrayList<Double>(capillaryX);
        Collections.sort(sorted);
        ArrayList<Double> cage = new ArrayList<Double>();
        for (int i=0;i<sorted.size();i+=2) cage.add((sorted.get(i)+sorted.get(i+1))/2.);
        ArrayList<Double> pitches = new ArrayList<Double>();
        for (int i=1;i<cage.size();i++) pitches.add(cage.get(i)-cage.get(i-1));
        double pitch=percentile(pitches,.5);
        if (!(pitch>imageWidth/40. && pitch<imageWidth/4.)) return null;
        double first=(cage.get(0)+cage.get(1))/2.;
        int lastCage=cage.size()-1;
        double last=(cage.get(lastCage-1)+cage.get(lastCage))/2.;
        int margin=Math.max(3,(int)Math.round(.22*pitch));
        return new int[]{Math.max(2,(int)Math.floor(first)-margin),
                Math.min(imageWidth-3,(int)Math.ceil(last)+margin), 9};
    }

    /** Approximate first/last occupied cage centre, cage pitch, and occupied cage count. */
    public static double[] frameGridGuide(List<Double> capillaryX, int imageWidth) {
        if (capillaryX==null || capillaryX.size()<4 || capillaryX.size()%2!=0) return null;
        ArrayList<Double> sorted=new ArrayList<Double>(capillaryX);
        Collections.sort(sorted);
        ArrayList<Double> cage=new ArrayList<Double>();
        for(int i=0;i<sorted.size();i+=2)cage.add((sorted.get(i)+sorted.get(i+1))/2.);
        return frameGridGuideFromCageCenters(cage,imageWidth);
    }

    /** Builds a frame guide from cage centres, including cages represented by only one capillary. */
    public static double[] frameGridGuideFromCageCenters(List<Double> cageCenters, int imageWidth) {
        if(cageCenters==null || cageCenters.size()<2)return null;
        ArrayList<Double> cage=new ArrayList<Double>(cageCenters);
        Collections.sort(cage);
        ArrayList<Double> gaps=new ArrayList<Double>();
        for(int i=1;i<cage.size();i++)gaps.add(cage.get(i)-cage.get(i-1));
        Collections.sort(gaps);
        // Missing cages create multiples of the ordinary pitch, so estimate it
        // from the lower half of the observed inter-cage gaps.
        ArrayList<Double> lower=new ArrayList<Double>(gaps.subList(0,Math.max(1,(gaps.size()+1)/2)));
        double pitch=percentile(lower,.5);
        if(!(pitch>imageWidth/40.&&pitch<imageWidth/4.))return null;
        return new double[]{cage.get(0),cage.get(cage.size()-1),pitch,cage.size()};
    }

    /** Detects all eleven frame boundaries when some of the twenty capillaries are absent. */
    public Result detectUsingFrameGrid(double[] pixels, int width, int height, int yMin, int yMax,
            double[] guide) {
        Result result=detect(pixels,width,height,yMin,yMax);
        if(guide==null || result.lowerY<=0)return result;
        int[] bars=findElevenFrameBars(pixels,width,height,result.lowerY,guide);
        if(bars==null)return result;
        result.expectedDividers=9;
        result.dividers.clear(); result.dividerCandidates.clear();
        result.frameLeft=bars[0]; result.frameRight=bars[10];
        result.frameWidth=result.frameRight-result.frameLeft;
        int tickBottom=Math.min(height-1,result.lowerY+Math.max(25,height/18));
        for(int i=1;i<10;i++)result.dividers.add(new Line2D.Double(bars[i],result.lowerY+2,bars[i],tickBottom));
        return result;
    }

	private Result detect(double[] pixels, int width, int height, int yMin, int yMax,
			int xMin, int xMax, boolean trustedHorizontalBounds, int expectedDividers) {
        Result result = new Result();
        result.expectedDividers = Math.max(2, expectedDividers);
        if (pixels == null || width < 20 || height < 20 || pixels.length < width * height) return result;
        int xa = Math.max(2, xMin), xb = Math.min(width - 3, xMax);
        int ya = Math.max(2, yMin), yb = Math.min(height - 3, yMax);
        if (yb <= ya + 8) return result;
        double[] row = new double[yb - ya + 1];
        for (int y = ya; y <= yb; y++) {
            ArrayList<Double> gradients = new ArrayList<Double>();
            for (int x = xa; x <= xb; x += 3)
                gradients.add(pixels[(y + 1) * width + x] - pixels[(y - 1) * width + x]);
            row[y - ya] = percentile(gradients, .25); // dark upper edge survives clutter in part of the row
        }
        int upperY = ya;
        for (int y = ya + 1; y <= yb; y++) if (row[y - ya] < row[upperY - ya]) upperY = y;
        double noise = medianAbs(row);
        double strength = -row[upperY - ya];
        if (!(strength > Math.max(1., 2.5 * noise))) return result;

        // Illumination commonly falls off strongly across the frame. Use a permissive
        // threshold to establish the extent of the already-validated upper edge;
        // the lower edge remains stricter because flies and cage contents add clutter.
        double upperThreshold = Math.max(.5, strength * .05);
        double lowerThreshold = Math.max(1., strength * .20);
        boolean[] upperMask = new boolean[width];
        for (int x = xa; x <= xb; x++)
            upperMask[x] = pixels[(upperY - 1) * width + x] - pixels[(upperY + 1) * width + x]
                    > upperThreshold;
        closeSmallGaps(upperMask, Math.max(12, width / 12));
        int[] span = largestRun(upperMask);
        if (span[1] - span[0] < width / 3) return result;
        // xMin/xMax are an independent frame estimate (normally derived from all
        // capillary pairs). Contrast validates the edge but must not crop dim cages.
        if (trustedHorizontalBounds) {
            span[0] = xa;
            span[1] = xb;
        }
        result.upperY = upperY;
        result.frameWidth = span[1] - span[0] + 1;
        result.confidence = strength / (noise + 1e-6);
        int step = Math.max(8, width / 80);
        for (int x = span[0]; x < span[1]; x += step)
            result.upper.add(new Line2D.Double(x, upperY, Math.min(span[1], x + step), upperY));

        // The lower edge is retained only over openings: dividers have no local dark-to-light transition.
        int searchEnd = Math.min(yb, upperY + Math.max(50, height / 10));
        int[] lowerY = new int[width];
        boolean[] lowerMask = new boolean[width];
        for (int x = span[0]; x <= span[1]; x++) {
            double best = lowerThreshold;
            for (int y = upperY + 3; y <= searchEnd; y++) {
                double rise = pixels[(y + 1) * width + x] - pixels[(y - 1) * width + x];
                if (rise > best) { best = rise; lowerY[x] = y; lowerMask[x] = true; }
            }
        }
        // A real lower edge has a consistent bar thickness. Local shallower rises
        // occur at glass/capillary crossings and near the solid outer frame.
        int dominantLowerY = dominantLevel(lowerY, lowerMask, upperY + 3, searchEnd, 2);
        if (dominantLowerY > 0)
            for (int x = span[0]; x <= span[1]; x++)
                if (lowerMask[x] && Math.abs(lowerY[x] - dominantLowerY) > 4) lowerMask[x] = false;
        removeShortRuns(lowerMask, Math.max(5, width / 80));
        for (int x = span[0]; x <= span[1];) {
            while (x <= span[1] && !lowerMask[x]) x++;
            int start = x;
            while (x <= span[1] && lowerMask[x]) x++;
            int end = x - 1;
            if (end >= start) result.lower.add(new Line2D.Double(start, median(lowerY, start, end),
                    end, median(lowerY, start, end)));
        }
        result.lowerY = dominantLowerY;
        ArrayList<Integer> candidates = new ArrayList<Integer>();
        int[] dividers = findDividers(pixels, width, height, xa, xb, dominantLowerY, candidates,
                trustedHorizontalBounds, result.expectedDividers, span[0], span[1]);
        int tickBottom = Math.min(height-1, dominantLowerY + Math.max(25,height/18));
        for (int x : candidates)
            result.dividerCandidates.add(new Line2D.Double(x, dominantLowerY+2, x, tickBottom));
        if (dividers != null) {
            int leftPitch = dividers[1] - dividers[0];
            int last=dividers.length-1;
            int rightPitch = dividers[last] - dividers[last-1];
            result.frameLeft = Math.max(0, dividers[0] - leftPitch);
            result.frameRight = Math.min(width - 1, dividers[last] + rightPitch);
            result.frameWidth = result.frameRight - result.frameLeft;
            result.upper.clear();
            for (int x=result.frameLeft; x<result.frameRight; x+=step)
                result.upper.add(new Line2D.Double(x, upperY, Math.min(result.frameRight,x+step), upperY));
            for (int x : dividers)
                result.dividers.add(new Line2D.Double(x, dominantLowerY+2, x, tickBottom));
        }
        return result;
    }

    private static int[] findDividers(double[] p, int w, int h, int xa, int xb, int lowerY,
            List<Integer> displayedCandidates, boolean roiGuidedBounds, int expectedCount,
            int detectedFrameLeft, int detectedFrameRight) {
        if (lowerY <= 0) return null;
        int ya=Math.min(h-2,lowerY+5), yb=Math.min(h-2,ya+Math.max(35,h/12));
        if(yb<=ya+5)return null;
        double[] mean=new double[w]; ArrayList<Double> distribution=new ArrayList<Double>();
        for(int x=xa;x<=xb;x++) { double sum=0; for(int y=ya;y<=yb;y++)sum+=p[y*w+x];
            mean[x]=sum/(yb-ya+1); distribution.add(mean[x]); }
        double dark=percentile(distribution,.20), light=percentile(distribution,.65);
        double threshold=dark+.42*(light-dark); boolean[] mask=new boolean[w];
        for(int x=xa;x<=xb;x++)mask[x]=mean[x]<threshold;
        closeSmallGaps(mask,Math.max(2,w/500));
        ArrayList<Integer> shallowCandidates=new ArrayList<Integer>();
        ArrayList<Integer> candidates=new ArrayList<Integer>();
        int minimumWidth=Math.max(5,w/180);
        for(int x=xa;x<=xb;) {while(x<=xb&&!mask[x])x++;int s=x;while(x<=xb&&mask[x])x++;int e=x-1;
            if(e-s+1>=minimumWidth) {
                int center=(s+e)/2;
                shallowCandidates.add(center);
                if(persistsBelowBar(p,w,h,s,e,yb,lowerY,light-dark))candidates.add(center);
            }}
        int[] best=fitRegular(candidates,w,expectedCount);
        // With ten cage centres the search interval already excludes both outer
        // frame edges. In that safe interval, fall back to shallow structure when
        // genuine dividers are too faint or interrupted in the deep band.
        if(best==null && roiGuidedBounds) {
            candidates=shallowCandidates;
            best=fitRegular(candidates,w,expectedCount);
            // Pale cage dividers may not enter the global dark mask at all. The
            // capillary-pair geometry already predicts each internal separator,
            // so refine every predicted position independently on the image.
            if(best==null) {
                candidates=guidedLocalMinima(mean,xa,xb,expectedCount,minimumWidth);
                best=fitRegular(candidates,w,expectedCount);
            }
        }
        if(best==null && !roiGuidedBounds) {
            // With an incomplete capillary set, retain the known ten-cage frame
            // geometry. The continuous support edge supplies the frame limits;
            // refine one separator inside each of its nine expected positions.
            candidates=frameSpanLocalMinima(mean,detectedFrameLeft,detectedFrameRight,
                    expectedCount,minimumWidth);
            best=fitRegular(candidates,w,expectedCount);
        }
        displayedCandidates.addAll(candidates);
        return best;
    }

    private static ArrayList<Integer> guidedLocalMinima(double[] mean, int xa, int xb, int count,
            int averagingWidth) {
        ArrayList<Integer> result=new ArrayList<Integer>();
        // Bounds extend by 22% of one pitch on either side of the first/last
        // predicted internal separator (see internalDividerSearchBounds()).
        double pitch=(xb-xa)/(count-1.+.44);
        double first=xa+.22*pitch;
        int halfWindow=Math.max(3,(int)Math.round(.28*pitch));
        int halfAverage=Math.max(2,averagingWidth/2);
        for(int k=0;k<count;k++) {
            int expected=(int)Math.round(first+k*pitch);
            int from=Math.max(xa+halfAverage,expected-halfWindow);
            int to=Math.min(xb-halfAverage,expected+halfWindow);
            int bestX=expected; double bestMean=Double.POSITIVE_INFINITY;
            for(int x=from;x<=to;x++) {
                double sum=0.;
                for(int q=x-halfAverage;q<=x+halfAverage;q++)sum+=mean[q];
                if(sum<bestMean){bestMean=sum;bestX=x;}
            }
            result.add(bestX);
        }
        return result;
    }

    private static ArrayList<Integer> frameSpanLocalMinima(double[] mean, int frameLeft, int frameRight,
            int count, int averagingWidth) {
        ArrayList<Integer> result=new ArrayList<Integer>();
        double pitch=(frameRight-frameLeft)/(count+1.);
        int halfWindow=Math.max(3,(int)Math.round(.28*pitch));
        int halfAverage=Math.max(2,averagingWidth/2);
        for(int k=1;k<=count;k++) {
            int expected=(int)Math.round(frameLeft+k*pitch);
            int from=Math.max(frameLeft+halfAverage,expected-halfWindow);
            int to=Math.min(frameRight-halfAverage,expected+halfWindow);
            int bestX=expected; double bestMean=Double.POSITIVE_INFINITY;
            for(int x=from;x<=to;x++) {
                double sum=0.;
                for(int q=x-halfAverage;q<=x+halfAverage;q++)sum+=mean[q];
                if(sum<bestMean){bestMean=sum;bestX=x;}
            }
            result.add(bestX);
        }
        return result;
    }

    private static int[] fitRegular(List<Integer> candidates, int w, int count) {
        if(candidates.size()<count)return null;
        int[] best=null; double bestCost=Double.POSITIVE_INFINITY;
        for(int i=0;i<candidates.size();i++)for(int j=i+count-1;j<candidates.size();j++) {
            double pitch=(candidates.get(j)-candidates.get(i))/(count-1.); if(pitch<w/30.||pitch>w/5.)continue;
            int[] chosen=new int[count]; int previous=-1; double cost=0; boolean ok=true;
            for(int k=0;k<count;k++) {double expected=candidates.get(i)+k*pitch;int nearest=-1;double error=Double.MAX_VALUE;
                for(int q=previous+1;q<candidates.size();q++){double d=Math.abs(candidates.get(q)-expected);if(d<error){error=d;nearest=q;}}
                if(nearest<0||error>pitch*.28){ok=false;break;} chosen[k]=candidates.get(nearest);previous=nearest;cost+=error*error;}
            if(ok&&cost<bestCost){bestCost=cost;best=chosen;}
        }
        return best;
    }

    /**
     * Rejects capillaries and broad dark borders that happen to look like a divider
     * immediately below the support. A cage divider remains locally darker than the
     * cage interiors much farther down; a capillary ends, while a broad border has no
     * lighter region on both sides.
     */
    private static boolean persistsBelowBar(double[] p, int w, int h, int runStart, int runEnd,
            int nearBandEnd, int lowerY, double localRange) {
        int deepStart=Math.min(h-2,nearBandEnd+Math.max(8,h/100));
        int deepEnd=Math.min(h-2,lowerY+Math.max(160,h/3));
        if(deepEnd<=deepStart+12)return false;
        int center=(runStart+runEnd)/2;
        int half=Math.max(2,(runEnd-runStart+1)/2);
        int flankGap=Math.max(7,2*half+3);
        int flankWidth=Math.max(3,half);
        if(center-flankGap-flankWidth<1 || center+flankGap+flankWidth>=w-1)return false;
        double requiredContrast=Math.max(3.,.10*Math.max(0.,localRange));
        int persistent=0, usable=0;
        for(int y=deepStart;y<=deepEnd;y++) {
            double core=columnMean(p,w,y,center-half,center+half);
            double left=columnMean(p,w,y,center-flankGap-flankWidth,center-flankGap);
            double right=columnMean(p,w,y,center+flankGap,center+flankGap+flankWidth);
            // Both flanks must be lighter. This explicitly rejects a candidate at
            // the edge of a broad dark region.
            usable++;
            if(left-core>requiredContrast && right-core>requiredContrast)persistent++;
        }
        // Thirty percent permits partially hidden/cropped dividers while still
        // excluding capillaries that only enter the top of this deeper band.
        return persistent>=Math.max(10,(int)Math.ceil(.30*usable));
    }

    private static double columnMean(double[] p, int w, int y, int x1, int x2) {
        double sum=0.;
        for(int x=x1;x<=x2;x++)sum+=p[y*w+x];
        return sum/(x2-x1+1);
    }

    private static int[] findElevenFrameBars(double[] p, int w, int h, int lowerY, double[] guide) {
        int deepStart=Math.min(h-2,lowerY+Math.max(8,h/100));
        int deepEnd=Math.min(h-2,lowerY+Math.max(180,h/3));
        if(deepEnd<=deepStart+20)return null;
        double[] vertical=new double[w];
        for(int x=0;x<w;x++) {
            double sum=0.; for(int y=deepStart;y<=deepEnd;y++)sum+=p[y*w+x];
            vertical[x]=sum/(deepEnd-deepStart+1);
        }
        double firstCage=guide[0], basePitch=guide[2];
        int occupied=(int)Math.round(guide[3]);
        int missing=Math.max(0,10-occupied);
        int[] best=null; double bestScore=Double.NEGATIVE_INFINITY;
        // Capillary-pair spacing is the scale estimate. Optimizing the global
        // pitch against darkness systematically expands the grid toward the thick
        // outer walls; only individual bar positions are refined below.
        for(int scaleStep=0;scaleStep<=0;scaleStep++) {
            double pitch=basePitch*(1.+scaleStep*.01);
            int core=Math.max(2,w/300);
            int flank=Math.max(core+4,(int)Math.round(.20*pitch));
            for(int missingLeft=0;missingLeft<=missing;missingLeft++) {
                double predictedLeft=firstCage-.5*pitch-missingLeft*pitch;
                int[] bars=new int[11]; double score=0.; boolean ok=true;
                for(int k=0;k<11;k++) {
                    int expected=(int)Math.round(predictedLeft+k*pitch);
                    int search=Math.max(3,(int)Math.round((k==0||k==10?.40:.18)*pitch));
                    if(expected-search-flank-core<1 || expected+search+flank+core>=w-1){ok=false;break;}
                    int bestX=expected; double localBest=Double.NEGATIVE_INFINITY;
                    for(int x=expected-search;x<=expected+search;x++) {
                        double centre=rangeMean(vertical,x-core,x+core);
                        double sides=(rangeMean(vertical,x-flank-core,x-flank)
                                +rangeMean(vertical,x+flank,x+flank+core))/2.;
                        double contrast=sides-centre-.08*Math.abs(x-expected);
                        if(contrast>localBest){localBest=contrast;bestX=x;}
                    }
                    if(k>0 && bestX<=bars[k-1]){ok=false;break;}
                    bars[k]=bestX; score+=localBest;
                }
                if(ok && score>bestScore){bestScore=score;best=bars;}
            }
        }
        if(best==null || bestScore/11.<=2.)return null;
        // Thick/textured outer walls have several possible centres and edges.
        // Their midpoint locates the frame, while ten capillary-derived cage
        // pitches provide the stable physical span.
        double centre=(best[0]+best[10])/2.;
        double span=10.*basePitch;
        best[0]=(int)Math.round(centre-span/2.);
        best[10]=(int)Math.round(centre+span/2.);
        return best;
    }

    private static double rangeMean(double[] values, int from, int to) {
        double sum=0.; for(int i=from;i<=to;i++)sum+=values[i];
        return sum/(to-from+1);
    }

    private static double percentile(List<Double> values, double p) {
        Collections.sort(values); return values.get(Math.max(0, Math.min(values.size()-1,
                (int)Math.round(p * (values.size()-1)))));
    }
    private static double medianAbs(double[] values) {
        ArrayList<Double> v = new ArrayList<Double>(); for (double d : values) v.add(Math.abs(d));
        return percentile(v, .5);
    }
    private static int median(int[] a, int from, int to) {
        ArrayList<Integer> v = new ArrayList<Integer>(); for(int i=from;i<=to;i++) if(a[i]>0)v.add(a[i]);
        Collections.sort(v); return v.get(v.size()/2);
    }
    private static int dominantLevel(int[] levels, boolean[] valid, int min, int max, int radius) {
        int bestLevel=0,bestCount=0;
        for(int level=min;level<=max;level++) {
            int count=0;
            for(int x=0;x<levels.length;x++)
                if(valid[x] && Math.abs(levels[x]-level)<=radius) count++;
            if(count>bestCount || (count==bestCount && level>bestLevel)) {
                bestCount=count; bestLevel=level;
            }
        }
        return bestLevel;
    }
    private static void closeSmallGaps(boolean[] a, int max) {
        for(int i=1;i<a.length-1;){
            while(i<a.length-1 && a[i]) i++;
            int start=i;
            while(i<a.length && !a[i]) i++;
            if(start>0 && a[start-1] && i<a.length && i-start<=max)
                for(int k=start;k<i;k++) a[k]=true;
        }
    }
    private static void removeShortRuns(boolean[] a, int min) {
        for(int i=0;i<a.length;){while(i<a.length&&!a[i])i++;int s=i;while(i<a.length&&a[i])i++;
            if(i-s<min)for(int k=s;k<i;k++)a[k]=false;}
    }
    private static int[] largestRun(boolean[] a) {
        int bs=0,be=-1;for(int i=0;i<a.length;){while(i<a.length&&!a[i])i++;int s=i;while(i<a.length&&a[i])i++;
            if(i-s>be-bs+1){bs=s;be=i-1;}}return new int[]{bs,be};
    }
}
