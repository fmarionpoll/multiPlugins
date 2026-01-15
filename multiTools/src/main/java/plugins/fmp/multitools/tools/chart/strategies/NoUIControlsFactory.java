package plugins.fmp.multitools.tools.chart.strategies;

import java.awt.event.ActionListener;

import javax.swing.JPanel;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

/**
 * UI controls factory that provides no UI controls.
 * This is useful for simple chart displays that don't need user interaction.
 */
public class NoUIControlsFactory implements ChartUIControlsFactory {

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
		// No controls to update
	}
}


