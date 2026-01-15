package plugins.fmp.multiSPOTS96.tools.JComponents;

import javax.swing.JComboBox;

/**
 * A specialized combo box for selecting time scale units.
 * Provides conversion from time scales to milliseconds.
 */
public class JComboBoxMs extends JComboBox<String> {
	private static final long serialVersionUID = -618283271585890700L;

	/**
	 * Creates a new time scale combo box with all available time units.
	 */
	public JComboBoxMs() {
		super();
		for (String scale : JComponentConstants.TimeScales.ALL_SCALES) {
			addItem(scale);
		}
	}

	/**
	 * Gets the millisecond conversion value for the currently selected time unit.
	 * 
	 * @return The number of milliseconds in the selected time unit
	 */
	public int getMsUnitValue() {
		int selectedIndex = getSelectedIndex();
		
		switch (selectedIndex) {
			case 0: // milliseconds
				return JComponentConstants.TimeScales.MS_TO_MS;
			case 1: // seconds
				return JComponentConstants.TimeScales.SECONDS_TO_MS;
			case 2: // minutes
				return JComponentConstants.TimeScales.MINUTES_TO_MS;
			case 3: // hours
				return JComponentConstants.TimeScales.HOURS_TO_MS;
			case 4: // days
				return JComponentConstants.TimeScales.DAYS_TO_MS;
			default:
				return JComponentConstants.TimeScales.MS_TO_MS;
		}
	}
	
	/**
	 * Sets the selected time unit based on a string value.
	 * 
	 * @param timeUnit The time unit string (ms, s, min, h, day)
	 * @return true if the time unit was found and selected, false otherwise
	 */
	public boolean setSelectedTimeUnit(String timeUnit) {
		if (timeUnit == null) {
			return false;
		}
		
		for (int i = 0; i < getItemCount(); i++) {
			if (timeUnit.equals(getItemAt(i))) {
				setSelectedIndex(i);
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Gets the currently selected time unit as a string.
	 * 
	 * @return The selected time unit string, or null if nothing is selected
	 */
	public String getSelectedTimeUnit() {
		Object selected = getSelectedItem();
		return selected != null ? selected.toString() : null;
	}
}
