package plugins.fmp.multitools.fmp_tools.chart.builders;

import org.jfree.data.xy.XYSeriesCollection;

import plugins.fmp.multitools.fmp_experiment.Experiment;
import plugins.fmp.multitools.fmp_experiment.cages.Cage;
import plugins.fmp.multitools.fmp_tools.results.ResultsOptions;

/**
 * Builds an {@link XYSeriesCollection} for a given cage and result options.
 *
 * Chart-layer abstraction: Spot and Capillary have different measurement models,
 * but the UI wants a uniform way to request “a dataset for this cage”.
 */
public interface CageSeriesBuilder {
	XYSeriesCollection build(Experiment exp, Cage cage, ResultsOptions options);
}


