package plugins.fmp.multicafe.dlg.levels;

import java.awt.image.BufferedImage;

/** Proposes a shared vertical translation for an already detected intake bar. */
final class IntakePlacementPreview {
    private static final int FLANK_COLUMNS = 3;
    private static final int SEARCH_RADIUS = 4;
    private static final double SEARCH_STEP = .5;
    private static final double MIN_CONTRAST = 3;
    private static final double STRONG_EDGE_FRACTION = .8;

    final double[] bottoms;
    final boolean[] uncertain;
    double[] previousBottoms;
    double[] shiftedTopraw;
    boolean outsideImage;
    int shiftedBars;

    IntakePlacementPreview(double[] bottoms) {
        this.bottoms = bottoms;
        uncertain = new boolean[bottoms.length];
    }

    static double offset(BufferedImage image, double[] raw, int bin, double top, double bottom) {
        Edge before = findEdge(image, raw, bin - FLANK_COLUMNS);
        Edge after = findEdge(image, raw, bin);
        if (before == null || after == null || before.cosine(after) < .8)
            return Double.NaN;

        // Each edge may cover several plausible rows. Shift only within their
        // shared support after accounting for the detected bar's endpoints.
        double above = raw[bin - 1] - top, below = raw[bin] - bottom;
        double min = Math.max(above + before.min, below + after.min);
        double max = Math.min(above + before.max, below + after.max);
        if (min > max)
            return Double.NaN;
        double shift = Math.max(min, Math.min(max, (above + before.offset + below + after.offset) / 2));
        if (!Double.isFinite(shift) || Math.abs(shift) > SEARCH_RADIUS
                || top + shift < 0 || bottom + shift > image.getHeight() - 1)
            return Double.NaN;
        return shift;
    }

    private static Edge findEdge(BufferedImage image, double[] raw, int first) {
        int end = first + FLANK_COLUMNS;
        if (first < 0 || end > raw.length)
            return null;
        for (int x = first; x < end; x++)
            if (!Double.isFinite(raw[x]) || raw[x] < SEARCH_RADIUS + 1
                    || raw[x] > image.getHeight() - SEARCH_RADIUS - 2)
                return null;

        int steps = (int) (SEARCH_RADIUS / SEARCH_STEP);
        double[] greens = new double[2 * steps + 1];
        double[] blues = new double[greens.length];
        double[] strengths = new double[greens.length];
        int best = 0;
        for (int i = 0; i < strengths.length; i++) {
            double offset = (i - steps) * SEARCH_STEP;
            double green = 0, blue = 0;
            for (int x = first; x < end; x++) {
                double row = raw[x] + offset;
                green += chroma(image, x, row + 1, 8) - chroma(image, x, row - 1, 8);
                blue += chroma(image, x, row + 1, 0) - chroma(image, x, row - 1, 0);
            }
            greens[i] = green / FLANK_COLUMNS;
            blues[i] = blue / FLANK_COLUMNS;
            strengths[i] = Math.hypot(greens[i], blues[i]);
            if (strengths[i] > strengths[best])
                best = i;
        }
        double peak = strengths[best];
        if (peak < MIN_CONTRAST)
            return null;
        double strong = STRONG_EDGE_FRACTION * peak;
        int low = best, high = best;
        while (low > 0 && strengths[low - 1] >= strong)
            low--;
        while (high + 1 < strengths.length && strengths[high + 1] >= strong)
            high++;
        if (low == 0 || high == strengths.length - 1 || (high - low) * SEARCH_STEP >= 2)
            return null;
        for (int i = 0; i < strengths.length; i++)
            if ((i < low || i > high) && strengths[i] >= strong)
                return null;

        int lastEqual = best;
        while (lastEqual < high && strengths[lastEqual + 1] == peak)
            lastEqual++;
        double center = ((best + lastEqual) / 2. - steps) * SEARCH_STEP;
        int supportingColumns = 0;
        for (int x = first; x < end; x++) {
            double row = raw[x] + center;
            double green = chroma(image, x, row + 1, 8) - chroma(image, x, row - 1, 8);
            double blue = chroma(image, x, row + 1, 0) - chroma(image, x, row - 1, 0);
            if ((green * greens[best] + blue * blues[best]) / peak >= MIN_CONTRAST)
                supportingColumns++;
        }
        return supportingColumns >= 2
                ? new Edge(center, (low - steps) * SEARCH_STEP, (high - steps) * SEARCH_STEP,
                        greens[best], blues[best], peak)
                : null;
    }

    private static double chroma(BufferedImage image, int x, double row, int channel) {
        int y = (int) row;
        int a = image.getRGB(x, y);
        int b = image.getRGB(x, Math.min(y + 1, image.getHeight() - 1));
        return difference(a, channel) + (row - y) * (difference(b, channel) - difference(a, channel));
    }

    private static int difference(int rgb, int channel) {
        return ((rgb >> channel) & 255) - ((rgb >> 16) & 255);
    }

    private static final class Edge {
        final double offset, min, max, green, blue, strength;

        Edge(double offset, double min, double max, double green, double blue, double strength) {
            this.offset = offset;
            this.min = min;
            this.max = max;
            this.green = green;
            this.blue = blue;
            this.strength = strength;
        }

        double cosine(Edge other) {
            return (green * other.green + blue * other.blue) / (strength * other.strength);
        }
    }
}
