package plugins.fmp.multiSPOTS96.tools.chart.strategies;

import java.awt.event.ActionListener;

import javax.swing.JPanel;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

/**
 * Factory interface for creating UI controls for chart displays.
 * Different implementations can provide comboboxes, checkboxes, or no controls.
 */
public interface ChartUIControlsFactory {
	
	/**
	 * Creates a top panel with controls for selecting/changing chart options.
	 * 
	 * @param currentOptions the current results options
	 * @param changeListener listener to call when options change
	 * @return the top panel with controls, or null if no controls needed
	 */
	JPanel createTopPanel(ResultsOptions currentOptions, ActionListener changeListener);
	
	/**
	 * Creates a bottom panel (typically for legends or additional info).
	 * 
	 * @param currentOptions the current results options
	 * @param experiment the experiment (can be null if not needed)
	 * @return the bottom panel, or null if no bottom panel needed
	 */
	JPanel createBottomPanel(ResultsOptions currentOptions, Experiment experiment);
	
	/**
	 * Updates the UI controls when the result type changes.
	 * 
	 * @param newResultType the new result type
	 * @param currentOptions the current options (will be updated)
	 */
	void updateControls(EnumResults newResultType, ResultsOptions currentOptions);
}

