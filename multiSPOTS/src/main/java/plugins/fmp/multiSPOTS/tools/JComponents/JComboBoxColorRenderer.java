package plugins.fmp.multiSPOTS.tools.JComponents;

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

	private Color getInvertedColor(Color argb) {

		int r = argb.getRed();
		int g = argb.getGreen();
		int b = argb.getBlue();
		int d = 0;

		// adapt color of text according to background
		// https://stackoverflow.com/questions/1855884/determine-font-color-based-on-background-color/34883645
		double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
		if (luminance > 0.5)
			d = 0; // bright colors - black font
		else
			d = 255; // dark colors - white font
		Color color = new Color(d, d, d);
		return color;
	}

	private String getColorAsText(Color argb) {
		int r = argb.getRed();
		int g = argb.getGreen();
		int b = argb.getBlue();
		return Integer.toString(r) + ":" + Integer.toString(g) + ":" + Integer.toString(b);
	}
}
