package plugins.fmp.multitools.tools.JComponents;

import java.awt.Component;
import java.awt.Dimension;

import javax.swing.JTabbedPane;

/** Keeps the width of all tabs, but requests only the selected tab's height. */
public class SelectedTabHeightPane extends JTabbedPane {
	private static final long serialVersionUID = 1L;

	@Override
	public Dimension getPreferredSize() {
		return selectedHeight(super.getPreferredSize(), false);
	}

	@Override
	public Dimension getMinimumSize() {
		return selectedHeight(super.getMinimumSize(), true);
	}

	private Dimension selectedHeight(Dimension size, boolean minimum) {
		Component selected = getSelectedComponent();
		if (selected == null || (minimum ? isMinimumSizeSet() : isPreferredSizeSet()))
			return size;
		int tallest = 0;
		for (int i = 0; i < getTabCount(); i++) {
			Component content = getComponentAt(i);
			if (content != null)
				tallest = Math.max(tallest, height(content, minimum));
		}
		// Retain the look-and-feel's tab headers, borders and insets.
		return new Dimension(size.width, size.height - tallest + height(selected, minimum));
	}

	private static int height(Component component, boolean minimum) {
		return (minimum ? component.getMinimumSize() : component.getPreferredSize()).height;
	}
}
