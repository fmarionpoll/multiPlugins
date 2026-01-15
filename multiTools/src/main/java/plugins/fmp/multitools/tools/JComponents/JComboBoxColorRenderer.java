package plugins.fmp.multitools.tools.JComponents;

import java.awt.Color;
import java.awt.Component;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;

public class JComboBoxColorRenderer extends JPanel implements ListCellRenderer<Object> {
	private static final long serialVersionUID = -1L;
	JPanel textPanel;
	JLabel text;

	public JComboBoxColorRenderer(JComboBox<Color> combo) {

		textPanel = new JPanel();
		textPanel.add(this);
		text = new JLabel();
		text.setOpaque(true);
		text.setFont(combo.getFont());
		text.setHorizontalAlignment(SwingConstants.CENTER);
		textPanel.add(text);
	}

	@Override
	public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
			boolean cellHasFocus) {

		Color argb = Color.white;
		Color colorfont = Color.black;
		if (value != null) {
			argb = (Color) value;
			text.setText(getColorAsText(argb));
			colorfont = getInvertedColor(argb);
			// System.out.print("output item index "+ Integer.toString(index) + " text= "+
			// getColorAsText(argb));
		} else
			text.setText("");
		text.setBackground(argb);
		text.setForeground(colorfont);
		return text;
	}

	/**
	 * Calculates the appropriate font color based on background luminance.
	 * Uses ITU-R BT.709 coefficients for luminance calculation.
	 * 
	 * @param backgroundColor The background color
	 * @return Black color for bright backgrounds, white for dark backgrounds
	 */
	private Color getInvertedColor(Color backgroundColor) {
		int r = backgroundColor.getRed();
		int g = backgroundColor.getGreen();
		int b = backgroundColor.getBlue();

		// Calculate luminance using ITU-R BT.709 coefficients
		double luminance = (JComponentConstants.ColorRendering.LUMINANCE_RED_COEFFICIENT * r + 
		                   JComponentConstants.ColorRendering.LUMINANCE_GREEN_COEFFICIENT * g + 
		                   JComponentConstants.ColorRendering.LUMINANCE_BLUE_COEFFICIENT * b) / 255.0;
		
		int fontColorValue;
		if (luminance > JComponentConstants.ColorRendering.LUMINANCE_THRESHOLD) {
			fontColorValue = JComponentConstants.ColorRendering.BRIGHT_BACKGROUND_FONT_COLOR; // Black for bright backgrounds
		} else {
			fontColorValue = JComponentConstants.ColorRendering.DARK_BACKGROUND_FONT_COLOR; // White for dark backgrounds
		}
		
		return new Color(fontColorValue, fontColorValue, fontColorValue);
	}

	/**
	 * Converts a color to its string representation in R:G:B format.
	 * 
	 * @param color The color to convert
	 * @return String representation in format "R:G:B"
	 */
	private String getColorAsText(Color color) {
		int r = color.getRed();
		int g = color.getGreen();
		int b = color.getBlue();
		return r + JComponentConstants.ColorRendering.COLOR_FORMAT_SEPARATOR + 
		       g + JComponentConstants.ColorRendering.COLOR_FORMAT_SEPARATOR + 
		       b;
	}
}
