package plugins.fmp.multitools.tools.chart.builders;

import java.awt.Color;

import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.CageProperties;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.service.KymographChromaAnalysisResult;
import plugins.fmp.multitools.service.KymographChromaAnalysisResult.SpotChromaKymoSeries;
import plugins.fmp.multitools.tools.chart.ChartCageBuild;
import plugins.fmp.multitools.tools.chart.style.SeriesStyleCodec;
import plugins.fmp.multitools.tools.results.ResultsOptions;

/**
 * Builds {@link XYSeriesCollection} from a cached {@link KymographChromaAnalysisResult}.
 */
public final class CageKymographChromaSeriesBuilder implements CageSeriesBuilder {

	private final KymographChromaAnalysisResult result;
	private final boolean plotAbsDelta;

	public CageKymographChromaSeriesBuilder(KymographChromaAnalysisResult result, boolean plotAbsDelta) {
		this.result = result;
		this.plotAbsDelta = plotAbsDelta;
	}

	@Override
	public XYSeriesCollection build(Experiment exp, Cage cage, ResultsOptions options) {
		XYSeriesCollection dataset = new XYSeriesCollection();
		if (result == null || cage == null || cage.getProperties() == null) {
			return dataset;
		}
		int cageId = cage.getProperties().getCageID();
		double[] x = result.xAxisMinutes;
		if (x.length == 0) {
			return dataset;
		}
		for (SpotChromaKymoSeries row : result.curvesForCage(cageId)) {
			Spot spot = row.spot;
			if (spot == null) {
				continue;
			}
			String key = SpotChartSeriesKeys.key(spot, row.indexInCage);
			XYSeries series = new XYSeries(key, false);
			double[] yv = plotAbsDelta ? row.absDeltaFraction : row.fraction;
			int n = Math.min(x.length, yv.length);
			for (int j = 0; j < n; j++) {
				double y = yv[j];
				if (!Double.isFinite(y)) {
					continue;
				}
				series.add(x[j], y);
			}
			CageProperties cageProp = cage.getProperties();
			Color color = spot.getProperties().getColor();
			series.setDescription(SeriesStyleCodec.buildDescription(cageProp.getCageID(), cageProp.getCageID(),
					cageProp.getCageNFlies(), color));
			dataset.addSeries(series);
		}
		ChartCageBuild.updateGlobalExtremaFromDataset(dataset);
		return dataset;
	}
}
