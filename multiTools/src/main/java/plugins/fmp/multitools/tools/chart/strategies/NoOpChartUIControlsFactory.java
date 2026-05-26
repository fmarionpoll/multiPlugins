package plugins.fmp.multitools.tools.chart.strategies;

import java.awt.event.ActionListener;

import javax.swing.JPanel;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

/**
 * Chart frame controls: no combobox or legend strip (used for kymograph-only viewers).
 */
public final class NoOpChartUIControlsFactory implements ChartUIControlsFactory {

	@Override
	public JPanel createTopPanel(ResultsOptions currentOptions, ActionListener changeListener) {
		return null;
	}

	@Override
	public JPanel createBottomPanel(ResultsOptions currentOptions, Experiment experiment) {
		return null;
	}

	@Override
	public void updateControls(EnumResults newResultType, ResultsOptions currentOptions) {
	}
}
