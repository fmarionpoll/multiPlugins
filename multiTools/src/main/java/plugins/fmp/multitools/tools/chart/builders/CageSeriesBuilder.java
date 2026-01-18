package plugins.fmp.multitools.tools.chart.builders;

import org.jfree.data.xy.XYSeriesCollection;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.tools.results.ResultsOptions;

/**
 * Builds an {@link XYSeriesCollection} for a given cage and result options.
 *
 * Chart-layer abstraction: Spot and Capillary have different measurement models,
 * but the UI wants a uniform way to request “a dataset for this cage”.
 */
public interface CageSeriesBuilder {
	XYSeriesCollection build(Experiment exp, Cage cage, ResultsOptions options);
}


