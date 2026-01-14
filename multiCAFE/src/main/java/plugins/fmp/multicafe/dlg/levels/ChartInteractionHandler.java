package plugins.fmp.multicafe.dlg.levels;

import org.jfree.chart.ChartMouseListener;

/**
 * Interface for chart interaction handlers that manage user interactions with
 * chart displays. Different implementations handle different types of
 * measurements (spots, capillaries, etc.).
 * 
 * <p>
 * This interface allows ChartCageArrayFrame to delegate chart interaction
 * logic to specialized handlers, enabling extensibility for new measurement
 * types.
 * </p>
 * 
 * @author MultiCAFE
 */
public interface ChartInteractionHandler {
	/**
	 * Creates and returns a ChartMouseListener appropriate for this handler's
	 * measurement type.
	 * 
	 * @return a ChartMouseListener implementation
	 */
	ChartMouseListener createMouseListener();
}

