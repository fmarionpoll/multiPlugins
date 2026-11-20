package plugins.fmp.multitools.service;

import java.awt.geom.Line2D;

import icy.roi.ROI2D;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.geometry.NormedBlueKymoGeometry;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.series.options.BuildSeriesOptions.DirectSampling;
import plugins.kernel.roi.roi2d.ROI2DLine;

/** Camera-frame sampling geometry; blue results use native pixels from the blue origin. */
public final class DirectCapillarySampling {
    private DirectCapillarySampling() { }

    public static ROI2D roiAt(Capillary cap, long frame, BuildSeriesOptions options) {
        if (options.directSampling == DirectSampling.GREEN)
            return cap.getRoiAtFrameT(frame);
        double expansion = expansion(options);
        Line2D line = NormedBlueKymoGeometry.expandedSamplingLine(cap, frame, expansion);
        if (line == null)
            throw new IllegalArgumentException("Missing blue line for " + cap.getRoiName() + " at camera frame " + frame);
        return new ROI2DLine(line);
    }

    public static double expansion(BuildSeriesOptions options) {
        if (options.directSampling != DirectSampling.EXPANDED_BLUE) return 0;
        double value = options.directBlueExpansionRatio;
        if (!Double.isFinite(value) || value < 0)
            throw new IllegalArgumentException("Blue-line expansion must be finite and non-negative");
        return value;
    }

    public static double pixelStep(ROI2D roi, int profileLength, BuildSeriesOptions options) {
        if (options.directSampling == DirectSampling.GREEN) return 1;
        Line2D line = NormedBlueKymoGeometry.lineFromRoi(roi);
        if (profileLength < 2) throw new IllegalArgumentException("Blue sampling profile is too short");
        return line.getP1().distance(line.getP2()) / (profileLength - 1);
    }

    public static double originOffset(ROI2D roi, BuildSeriesOptions options) {
        if (options.directSampling == DirectSampling.GREEN) return 0;
        Line2D line = NormedBlueKymoGeometry.lineFromRoi(roi);
        double e = expansion(options);
        return line.getP1().distance(line.getP2()) * e / (2 * (1 + e));
    }
}
