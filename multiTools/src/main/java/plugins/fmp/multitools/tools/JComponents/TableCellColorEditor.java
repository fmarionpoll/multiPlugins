package plugins.fmp.multitools.tools.JComponents;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.AbstractCellEditor;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;

/**
 * A table cell editor that provides color selection functionality.
 * This class has been separated from the renderer for better separation of concerns.
 */
public class TableCellColorEditor extends AbstractCellEditor implements TableCellEditor, ActionListener {
	private static final long serialVersionUID = 1L;
	
	private Color currentColor;
	private final JButton button;
	private final JColorChooser colorChooser;
	private final JDialog dialog;

	/**
	 * Creates a new table cell color editor.
	 */
	public TableCellColorEditor() {
		button = new JButton();
		button.setActionCommand(JComponentConstants.TableCell.EDIT_COMMAND);
		button.addActionListener(this);
		button.setBorderPainted(false);
		button.setText(JComponentConstants.TableCell.BUTTON_TEXT);

		// Set up the color chooser dialog
		colorChooser = new JColorChooser();
		dialog = JColorChooser.createDialog(
			button, 
			JComponentConstants.COLOR_PICKER_TITLE, 
			true, // modal
			colorChooser, 
			this, // OK button handler
			null  // no CANCEL button handler
		);
	}

	/**
	 * Handles action events from the button and color chooser dialog.
	 * 
	 * @param e The action event
	 */
	@Override
	public void actionPerformed(ActionEvent e) {
		if (JComponentConstants.TableCell.EDIT_COMMAND.equals(e.getActionCommand())) {
			// User clicked the cell button - show color picker
			if (currentColor != null) {
				button.setBackground(currentColor);
				colorChooser.setColor(currentColor);
			}
			dialog.setVisible(true);
			fireEditingStopped(); // Make the editor finish editing

		} else { 
			// User pressed dialog's "OK" button
			currentColor = colorChooser.getColor();
		}
	}

	/**
	 * Returns the current color value from the editor.
	 * 
	 * @return The selected color
	 */
	@Override
	public Object getCellEditorValue() {
		return currentColor;
	}

	/**
	 * Returns the component used for editing the table cell.
	 * 
	 * @param table The JTable being edited
	 * @param value The current value of the cell
	 * @param isSelected Whether the cell is selected
	 * @param row The row index
	 * @param column The column index
	 * @return The editor component
	 */
	@Override
	public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
		currentColor = (Color) value;
		return button;
	}
	
	/**
	 * Cleanup method to properly dispose of resources.
	 */
	public void dispose() {
		if (dialog != null) {
			dialog.dispose();
		}
	}
}
