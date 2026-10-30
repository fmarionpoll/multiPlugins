package plugins.fmp.multitools.service;

import java.awt.image.BufferedImage;
import icy.image.IcyBufferedImage;
import icy.sequence.Sequence;
import icy.type.collection.array.Array1DUtil;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryCorrelationGulps;
import plugins.fmp.multitools.tools.polyline.Level2D;

/** Phase 1. Image-based refinement only: no evaporation or intake decisions. */
public final class MeniscusRefinement {
    private MeniscusRefinement() { }
    public enum Method {
        ADJACENT("Adjacent columns (previous)"), NEIGHBOURS("Neighbours + median (experimental)");
        private final String label;
        Method(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    private static final Map<Capillary, Accepted> accepted = new WeakHashMap<>();

    public static BufferedImage image(Sequence seq, Capillary cap, int width) {
        int index = cap.getKymographIndex();
        if (index < 0 || index >= seq.getSizeT()) throw new IllegalArgumentException("Kymograph not loaded: " + cap.getKymographName());
        return image(seq.getImage(index, 0), cap, width);
    }

    /**
     * Sample native RGB pixels at the saved topraw X coordinates. A trajectory
     * may cover only part of the image or omit columns; its point count is not
     * the image width. No levels are added or rescaled.
     */
    public static BufferedImage image(IcyBufferedImage input, Capillary cap, int width) {
        if (input == null)
            throw new IllegalArgumentException("Kymograph image is missing for " + cap.getKymographName());
        Level2D raw = cap.getTopRaw().polylineLevel;
        if (raw == null || raw.npoints != width || width < 1)
            throw new IllegalArgumentException("Topraw point count changed for " + cap.getKymographName());
        for (int i = 0; i < width; i++) {
            double x = raw.xpoints[i];
            if (!Double.isFinite(x) || x < 0 || x > input.getSizeX() - 1
                    || (i > 0 && x <= raw.xpoints[i - 1]))
                throw new IllegalArgumentException("Invalid topraw column for " + cap.getKymographName()
                        + ": point=" + i + ", x=" + x + ", image=" + input.getSizeX()
                        + " columns, topraw=" + width + " points; X coordinates must increase within the image");
        }
        if (input.getSizeC() < 3)
            throw new IllegalArgumentException("RGB correlation requires at least 3 channels for "
                    + cap.getKymographName() + "; image=" + input.getSizeX() + "x" + input.getSizeY()
                    + ", channels=" + input.getSizeC() + ", topraw=" + width + " points");
        double[][] channels = new double[3][];
        for (int c = 0; c < 3; c++) channels[c] = Array1DUtil.arrayToDoubleArray(input.getDataXY(c), input.isSignedDataType());
        BufferedImage output = new BufferedImage(width, input.getSizeY(), BufferedImage.TYPE_INT_RGB);
        int[] pixels = new int[width * input.getSizeY()];
        for (int y = 0; y < input.getSizeY(); y++) {
            for (int i = 0; i < width; i++) {
                double x = raw.xpoints[i];
                int left = (int) Math.floor(x), right = (int) Math.ceil(x);
                double fraction = x - left;
                int rgb = 0;
                for (int c = 0; c < 3; c++) {
                    double a = channels[c][y * input.getSizeX() + left];
                    double b = channels[c][y * input.getSizeX() + right];
                    if (!Double.isFinite(a) || a < 0 || a > 255
                            || !Double.isFinite(b) || b < 0 || b > 255)
                        throw new IllegalArgumentException("This prototype requires RGB values between 0 and 255.");
                    rgb = (rgb << 8) | (int) Math.round(a * (1 - fraction) + b * fraction);
                }
                pixels[y * width + i] = rgb;
            }
        }
        output.setRGB(0, 0, width, input.getSizeY(), pixels, 0, width);
        return output;
    }

    public static final class Proposal {
        private final double[] x, original, adjustment;
        private final boolean[] manualAnchors;
        private final int imageHeight;
        public final VerticalCorrelation.Result motion;
        private Proposal(Level2D source, BufferedImage image, int radius, double correction, boolean expand,boolean[] pinned,Method method) {
            x = Arrays.copyOf(source.xpoints, source.npoints);
            original = Arrays.copyOf(source.ypoints, source.npoints);
            adjustment = new double[source.npoints];
            manualAnchors=pinned==null?new boolean[source.npoints]:pinned.clone();
            imageHeight = image.getHeight();
            motion = method == Method.NEIGHBOURS
                    ? MeniscusNeighbourRefinement.analyze(image, original, radius, correction, expand,manualAnchors)
                    : VerticalCorrelation.analyzeSeeded(image, original, radius, correction, expand);
            if (method == Method.ADJACENT) {
                boolean hasPins = false;
                for (int i = 0; i < original.length; i++) if (manualAnchors[i]) {
                    hasPins = true;
                    motion.refined[i] = original[i];
                    motion.reliable[i] = true;
                    motion.reason[i] = "Manual anchor (fixed)";
                }
                for (int i = 1; hasPins && i < original.length; i++)
                    motion.shift[i] = motion.refined[i] - motion.refined[i - 1];
            }
        }
        private Proposal(Level2D source, BufferedImage image, boolean[] quality) {
            x = Arrays.copyOf(source.xpoints, source.npoints);
            original = Arrays.copyOf(source.ypoints, source.npoints);
            adjustment = new double[source.npoints];
            manualAnchors=new boolean[source.npoints];
            imageHeight = image.getHeight();
            motion = VerticalCorrelation.fromLevels(original, quality);
        }
        public int columnCount() { return adjustment.length; }
        public double[] original() { return original.clone(); }
        /** Correlation result plus any manual column offsets, clamped to the kymograph. */
        public double[] refined() {
            double[] y = new double[motion.refined.length];
            double max = Math.max(0, imageHeight - 1);
            for (int i = 0; i < y.length; i++) {
                double value = motion.refined[i] + adjustment[i];
                if (!Double.isFinite(value)) y[i] = value;
                else if (value < 0) y[i] = 0;
                else if (value > max) y[i] = max;
                else y[i] = value;
            }
            return y;
        }
        public double[] x() { return x.clone(); }
        /** Hand-placed columns count as reliable. A later nudge back to zero restores the automatic flag. */
        public boolean[] quality() {
            boolean[] flags = motion.reliable.clone();
            for (int i = 0; i < flags.length; i++)
                if (adjustment[i] != 0 || manualAnchors[i]) flags[i] = true;
            return flags;
        }
        /** Columns with a manual offset. Correlation may still move the others after Validate. */
        public boolean[] placedColumns() {
            boolean[] placed = new boolean[adjustment.length];
            for (int i = 0; i < adjustment.length; i++)
                placed[i] = adjustment[i] != 0 || manualAnchors[i];
            return placed;
        }

        /**
         * Keep saved columns on the next correlation. A null mask keeps every column.
         * A mask keeps only the hand-placed ones, so the dark band can be left behind
         * without freezing columns the user did not move.
         */
        public void holdValidated(double[] saved, boolean[] quality, boolean[] placed) {
            if (saved == null || saved.length != motion.refined.length)
                return;
            if (placed == null) {
                System.arraycopy(saved, 0, motion.refined, 0, saved.length);
                if (quality != null && quality.length == motion.reliable.length)
                    System.arraycopy(quality, 0, motion.reliable, 0, quality.length);
                return;
            }
            int n = Math.min(saved.length, placed.length);
            for (int i = 0; i < n; i++) {
                if (!placed[i])
                    continue;
                motion.refined[i] = saved[i];
                motion.reliable[i] = quality != null && i < quality.length ? quality[i] : true;
                manualAnchors[i]=true;
            }
        }

        public void holdValidated(double[] saved, boolean[] quality) {
            holdValidated(saved, quality, null);
        }

        /** Replace the working trajectory with an explicitly chosen result. */
        public void replaceLevels(double[] levels, boolean[] quality) {
            if (levels == null || levels.length != adjustment.length)
                throw new IllegalArgumentException("Level count must match the working trajectory.");
            Arrays.fill(adjustment, 0);
            Arrays.fill(manualAnchors,false);
            holdValidated(levels, quality);
        }

        /** Transfer an automatic preview while retaining hand-placed columns. */
        public void replaceCorrelation(Proposal correlated) {
            boolean[] fixed=placedColumns();
            double[] current=refined(),chosen=correlated.refined();
            boolean[] quality=correlated.quality();
            if(chosen.length!=current.length) throw new IllegalArgumentException("Correlation column count changed");
            for(int i=0;i<fixed.length;i++) if(fixed[i]) {chosen[i]=current[i];quality[i]=true;}
            replaceLevels(chosen,quality);
            System.arraycopy(fixed,0,manualAnchors,0,fixed.length);
        }

        /** Add one delta per column. NaN leaves that column alone. Positive moves the meniscus down. */
        public void addOffsets(double[] delta) {
            if (delta == null)
                return;
            int n = Math.min(adjustment.length, delta.length);
            for (int i = 0; i < n; i++)
                if (Double.isFinite(delta[i]))
                    adjustment[i] += delta[i];
        }

        /** Add the same pixel delta to an inclusive column range. Positive moves the meniscus down. */
        public void addOffset(int from, int to, double delta) {
            if (adjustment.length == 0 || delta == 0 || !Double.isFinite(delta)) return;
            int first = Math.max(0, Math.min(from, to));
            int last = Math.min(adjustment.length - 1, Math.max(from, to));
            for (int i = first; i <= last; i++) adjustment[i] += delta;
        }
    }

    private static final class Accepted {
        final Proposal proposal;
        final double[] values;
        final boolean[] quality, previousQuality;
        final Accepted previous;
        final Level2D derivative, corrected;
        final boolean gulpsDirty;
        final CapillaryCorrelationGulps events=new CapillaryCorrelationGulps();
        Accepted(Capillary cap, Proposal p, Accepted previous) {
            proposal=p; values=p.refined(); quality=p.quality(); this.previous=previous;
            derivative=copy(cap.getDerivative().polylineLevel);
            corrected=copy(cap.getTopCorrected().polylineLevel);
            gulpsDirty=cap.isGulpMeasuresDirty();events.copy(cap.getCorrelationGulps());
            previousQuality=cap.getTopRaw().getRefinementQuality();
        }
    }

    private static Level2D copy(Level2D level) {
        return level==null?new Level2D():new Level2D(Arrays.copyOf(level.xpoints,level.npoints),
                Arrays.copyOf(level.ypoints,level.npoints),level.npoints);
    }

    public static Proposal propose(Level2D original, BufferedImage image, int radius, double correction, boolean expand) {
        return propose(original,image,radius,correction,expand,null,Method.ADJACENT);
    }

    public static Proposal propose(Level2D original,BufferedImage image,int radius,double correction,
            boolean expand,boolean[] pinned) {
        return propose(original,image,radius,correction,expand,pinned,Method.ADJACENT);
    }

    public static Proposal propose(Level2D original,BufferedImage image,int radius,double correction,
            boolean expand,boolean[] pinned,Method method) {
        if(original==null || original.npoints<2 || (pinned!=null && pinned.length!=original.npoints))
            throw new IllegalArgumentException("Invalid topraw/manual anchors");
        if(method==null) throw new IllegalArgumentException("Select a correlation method");
        return new Proposal(original,image,radius,correction,expand,pinned,method);
    }

    /** Editable saved trajectory without running correlation. */
    public static Proposal editable(Level2D source, BufferedImage image, boolean[] quality) {
        if (source == null || source.npoints < 2 || image.getWidth() != source.npoints)
            throw new IllegalArgumentException("Detect topraw first; image columns must match levels.");
        return new Proposal(source, image, quality);
    }

    private static boolean matches(Capillary cap, double[] x, double[] y) {
        Level2D current=cap.getTopRaw().polylineLevel;
        return current != null && current.npoints == y.length
                && Arrays.equals(Arrays.copyOf(current.xpoints,current.npoints),x)
                && Arrays.equals(Arrays.copyOf(current.ypoints,current.npoints),y);
    }

    public static boolean isCurrent(Capillary cap, Proposal proposal) {
        return proposal != null && matches(cap, proposal.x, proposal.original);
    }

    /** The exact acceptance entries belonging to one user validation. */
    public static final class Validation {
        private final Map<Capillary, Accepted> entries = new java.util.LinkedHashMap<>();
        public int size() { return entries.size(); }
    }

    /** Validate the entire selection before changing any capillary. Called on the UI thread. */
    public static synchronized void acceptAll(Map<Capillary, Proposal> proposals) {
        validateAll(proposals);
    }

    public static synchronized Validation validateAll(Map<Capillary, Proposal> proposals) {
        if (proposals.isEmpty()) throw new IllegalStateException("Wait for the refinement previews first.");
        for (Map.Entry<Capillary, Proposal> entry : proposals.entrySet())
            if (!isCurrent(entry.getKey(), entry.getValue()))
                throw new IllegalStateException("Topraw changed since preview. Refresh the previews; no capillary was validated.");
        for (Map.Entry<Capillary, Proposal> entry : proposals.entrySet()) accept(entry.getKey(), entry.getValue());
        Validation validation = new Validation();
        for (Capillary cap : proposals.keySet()) validation.entries.put(cap, accepted.get(cap));
        return validation;
    }

    public static synchronized boolean canUndo(Validation validation) {
        if (validation == null || validation.entries.isEmpty()) return false;
        for (Map.Entry<Capillary, Accepted> entry : validation.entries.entrySet())
            if (accepted.get(entry.getKey()) != entry.getValue() || !canUndo(entry.getKey())) return false;
        return true;
    }

    /** Undo the captured validation, independently of the current UI selection. */
    public static synchronized void undo(Validation validation) {
        if (!canUndo(validation))
            throw new IllegalStateException("Levels from the last validation have changed; no capillary was restored.");
        undoAll(validation.entries.keySet());
    }

    public static synchronized void undoAll(java.util.Collection<Capillary> caps) {
        if (caps.isEmpty()) throw new IllegalStateException("No capillaries to undo.");
        for (Capillary cap : caps)
            if (!canUndo(cap)) throw new IllegalStateException("A capillary has changed or has no refinement to undo. No capillary was restored.");
        for (Capillary cap : caps) undo(cap);
    }

    /** Sample the image around each proposed position, without altering the source pixels. */
    public static BufferedImage stabilized(BufferedImage source, double[] levels, int radius) {
        if (levels.length != source.getWidth() || radius < 1)
            throw new IllegalArgumentException("Invalid stabilization dimensions.");
        BufferedImage aligned = new BufferedImage(source.getWidth(), 2 * radius + 1, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < levels.length; x++) {
            for (int row = 0; row < aligned.getHeight(); row++) {
                double y = levels[x] + row - radius;
                int rgb = 0x555555; // unavailable image support, not a replicated edge
                if (Double.isFinite(y) && y >= 0 && y <= source.getHeight() - 1) {
                    int lo = (int) Math.floor(y), hi = Math.min(lo + 1, source.getHeight() - 1);
                    double fraction = y - lo;
                    int a = source.getRGB(x, lo), b = source.getRGB(x, hi);
                    rgb = 0;
                    for (int shift = 0; shift <= 16; shift += 8)
                        rgb |= ((int) Math.round(((a >> shift) & 255) * (1 - fraction)
                                + ((b >> shift) & 255) * fraction)) << shift;
                }
                aligned.setRGB(x, row, rgb);
            }
        }
        return aligned;
    }

    /** Acceptance is explicit; stale proposals cannot overwrite edits made elsewhere. */
    public static synchronized void accept(Capillary cap, Proposal proposal) {
        if (!matches(cap,proposal.x,proposal.original))
            throw new IllegalStateException("Topraw changed since this preview. Close and reopen refinement.");
        Accepted previous=accepted.get(cap);
        if (previous != null && !matches(cap,previous.proposal.x,previous.values)) previous=null;
        Accepted entry=new Accepted(cap,proposal,previous);
        cap.getTopRaw().polyline_old=new Level2D(proposal.x.clone(),proposal.original.clone(),proposal.original.length);
        cap.getTopRaw().polylineLevel=new Level2D(proposal.x.clone(),entry.values.clone(),entry.values.length);
        cap.getTopRaw().setRefinementQuality(entry.quality);
        accepted.put(cap,entry);
        invalidateDerived(cap);
    }

    public static synchronized void undo(Capillary cap) {
        Accepted entry=accepted.get(cap);
        if (entry == null || !matches(cap,entry.proposal.x,entry.values))
            throw new IllegalStateException("No unchanged refinement to undo for this capillary.");
        Proposal p=entry.proposal;
        cap.getTopRaw().polylineLevel=new Level2D(p.x.clone(),p.original.clone(),p.original.length);
        if(entry.previous==null)accepted.remove(cap); else accepted.put(cap,entry.previous);
        cap.getDerivative().polylineLevel=copy(entry.derivative);
        cap.getTopCorrected().polylineLevel=copy(entry.corrected);
        cap.getCorrelationGulps().copy(entry.events);
        cap.setGulpMeasuresDirty(entry.gulpsDirty);
        cap.getTopRaw().setRefinementQuality(entry.previousQuality);
    }

    public static synchronized boolean canUndo(Capillary cap) {
        Accepted entry=accepted.get(cap);
        return entry!=null && matches(cap,entry.proposal.x,entry.values);
    }

    /** Persisted quality is valid only while the accepted trajectory is unchanged. */
    public static boolean[] quality(Capillary cap) {
        return cap.getTopRaw().getRefinementQuality();
    }

    private static void invalidateDerived(Capillary cap) {
        cap.getDerivative().clear();
        cap.getTopCorrected().clear();
        cap.setGulpMeasuresDirty(true);
        // Classic gulps may be manually annotated. Keep them, but require recalculation
        // of the prototype's derived events after the level input changes.
        cap.getCorrelationGulps().clear();
    }
}
