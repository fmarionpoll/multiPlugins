package plugins.fmp.multitools.fmp_tools.JComponents;

import java.awt.Component;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.ListModel;

/**
 * A list cell renderer that displays sequence names with index information.
 * Shows format: "[index:total] sequence_name" with automatic truncation for long names.
 */
public class SequenceNameListRenderer extends DefaultListCellRenderer {
	private static final long serialVersionUID = 7571369946954820177L;

	/**
	 * Returns a component that renders the list cell with index and name information.
	 * 
	 * @param list The JList being rendered
	 * @param value The value to assign to the cell
	 * @param index The cell index
	 * @param isSelected Whether the cell is selected
	 * @param cellHasFocus Whether the cell has focus
	 * @return The component for rendering the cell
	 */
	@Override
	public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
			boolean cellHasFocus) {
		Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
		
		ListModel<?> model = list.getModel();
		int totalItems = model.getSize();
		
		// Use selected index if index is invalid
		if (index < 0) {
			index = list.getSelectedIndex();
		}
		
		// Create the index prefix
		String indexPrefix = String.format(JComponentConstants.ListRendering.INDEX_FORMAT, 
										  index + 1, totalItems);
		
		String displayText = indexPrefix;
		
		if (value != null) {
			String valueText = value.toString();
			if (valueText != null) {
				displayText += truncateIfNeeded(valueText, indexPrefix.length());
			}
		}
		
		setText(displayText);
		return c;
	}
	
	/**
	 * Truncates the text if it would exceed the maximum display length.
	 * 
	 * @param text The text to potentially truncate
	 * @param prefixLength The length of the prefix already used
	 * @return The original text or a truncated version with ellipsis
	 */
	private String truncateIfNeeded(String text, int prefixLength) {
		int availableLength = JComponentConstants.ListRendering.MAX_DISPLAY_LENGTH - prefixLength;
		
		if (text.length() <= availableLength) {
			return text;
		}
		
		// Calculate how many characters we can show after the ellipsis
		int charactersToShow = availableLength - JComponentConstants.ListRendering.TRUNCATION_BUFFER;
		
		if (charactersToShow <= 0) {
			return JComponentConstants.ListRendering.TRUNCATION_INDICATOR;
		}
		
		// Show the end of the string with ellipsis prefix
		return JComponentConstants.ListRendering.TRUNCATION_INDICATOR + 
			   text.substring(text.length() - charactersToShow);
	}
}
