package plugins.fmp.multitools.tools.chart;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.results.ResultsOptions;

/**
 * Factory interface for creating chart interaction handlers. Handlers are
 * created after the chart array is built, so they can reference it.
 */
public interface ChartInteractionHandlerFactory {
	/**
	 * Creates an interaction handler for the given experiment and options.
	 * 
	 * @param exp        the experiment
	 * @param options    the results options
	 * @param chartArray the chart panel array (may be null if not yet created)
	 * @return the interaction handler, or null if no handler is needed
	 */
	ChartInteractionHandler createHandler(Experiment exp, ResultsOptions options, ChartCagePair[][] chartArray);
}
