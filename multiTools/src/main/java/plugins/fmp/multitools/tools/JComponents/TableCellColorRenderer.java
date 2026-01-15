package plugins.fmp.multiSPOTS96.tools.JComponents;

import java.awt.Color;
import java.awt.Component;

/* 
 * ColorRenderer.java (compiles with releases 1.2, 1.3, and 1.4) is used by 
 * TableDialogEditDemo.java.
 */

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.border.Border;
import javax.swing.table.TableCellRenderer;

public class TableCellColorRenderer extends JLabel implements TableCellRenderer {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	Border unselectedBorder = null;
	Border selectedBorder = null;
	boolean isBordered = true;

	public TableCellColorRenderer(boolean isBordered) {
		this.isBordered = isBordered;
		setOpaque(true); // MUST do this for background to show up.
	}

	public Component getTableCellRendererComponent(JTable table, Object color, boolean isSelected, boolean hasFocus,
			int row, int column) {
		Color newColor = (Color) color;
		if (color == null)
			return null;

		setBackground(newColor);
		if (isBordered) {
			if (isSelected) {
				if (selectedBorder == null) {
					selectedBorder = BorderFactory.createMatteBorder(
						JComponentConstants.TableCell.BORDER_THICKNESS, 
						JComponentConstants.TableCell.BORDER_MARGIN, 
						JComponentConstants.TableCell.BORDER_THICKNESS, 
						JComponentConstants.TableCell.BORDER_MARGIN, 
						table.getSelectionBackground()
					);
				}
				setBorder(selectedBorder);
			} else {
				if (unselectedBorder == null) {
					unselectedBorder = BorderFactory.createMatteBorder(
						JComponentConstants.TableCell.BORDER_THICKNESS, 
						JComponentConstants.TableCell.BORDER_MARGIN, 
						JComponentConstants.TableCell.BORDER_THICKNESS, 
						JComponentConstants.TableCell.BORDER_MARGIN, 
						table.getBackground()
					);
				}
				setBorder(unselectedBorder);
			}
		}

		setToolTipText(String.format(JComponentConstants.ColorRendering.RGB_TOOLTIP_FORMAT,
			newColor.getRed(), newColor.getGreen(), newColor.getBlue()));
		return this;
	}
}