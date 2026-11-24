package plugins.fmp.multitools.service;

import plugins.fmp.multitools.experiment.Experiment;
import icy.image.IcyBufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Explicit old-method entry point. The original analyzer and persistence stay unchanged. */
public final class CageKymoAnalyzer_old {
    private CageKymoAnalyzer_old() { }

    public static KymoAnalysisResult analyze(Experiment experiment, CageKymoAnalyzer.Params params) {
        return CageKymoAnalyzer.analyze(experiment, params);
    }

    public static List<CageKymoAnalyzer.ColumnMetrics> analyzeImage(IcyBufferedImage image,
            List<CageKymographSpotBands> bands, CageKymoAnalyzer.Params params) {
        IcyBufferedImage data = params.rowwiseOcclusionFill
                ? KymoStripRowwiseOcclusionFill.apply(image, bands, params) : image;
        List<CageKymoAnalyzer.ColumnMetrics> rows = new ArrayList<>();
        for (CageKymographSpotBands band : bands)
            rows.add(CageKymoAnalyzer.computeColumnMetrics(data, band, params, image.getWidth()));
        return rows;
    }
}
