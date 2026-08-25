package plugins.fmp.multitools.tools.JComponents;

import java.awt.Component;
import java.awt.Dimension;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;

/**
 * A list cell renderer that displays sequence names with index information.
 * Shows format: "[index:total] sequence_name". The closed combo field truncates long
 * paths; dropdown list items show the full path.
 */
public class SequenceNameListRenderer extends DefaultListCellRenderer {
	private static final long serialVersionUID = 7571369946954820177L;

	private final boolean truncateInClosedField;

	public SequenceNameListRenderer() {
		this(true);
	}

	public SequenceNameListRenderer(boolean truncateInClosedField) {
		this.truncateInClosedField = truncateInClosedField;
	}

	/** Renderer for combo popup rows: always shows the full path. */
	public static SequenceNameListRenderer fullText() {
		return new SequenceNameListRenderer(false);
	}

	/**
	 * Installs a popup listener that widens the dropdown to fit full paths and
	 * sizes its height from {@link JComboBox#getMaximumRowCount()} (default 8).
	 */
	public static void installWidePopup(JComboBox<?> combo) {
		combo.addPopupMenuListener(new PopupMenuListener() {
			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
				SwingUtilities.invokeLater(() -> widenComboPopup(combo));
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
			}

			@Override
			public void popupMenuCanceled(PopupMenuEvent e) {
			}
		});
	}

	private static void widenComboPopup(JComboBox<?> combo) {
		JList<?> list = findPopupList(combo);
		if (list == null)
			return;

		SequenceNameListRenderer popupRenderer = fullText();
		list.setCellRenderer(popupRenderer);

		int itemCount = list.getModel().getSize();
		int maxWidth = combo.getWidth();
		int rowHeight = 16;
		for (int i = 0; i < itemCount; i++) {
			Object value = list.getModel().getElementAt(i);
			Component cell = popupRenderer.getListCellRendererComponent(list, value, i, false, false);
			Dimension pref = cell.getPreferredSize();
			maxWidth = Math.max(maxWidth, pref.width + 12);
			if (pref.height > 0)
				rowHeight = Math.max(rowHeight, pref.height);
		}

		int visibleRows = Math.min(Math.max(itemCount, 1), Math.max(1, combo.getMaximumRowCount()));
		int popupHeight = visibleRows * rowHeight + 4;

		list.setFixedCellWidth(maxWidth);
		list.setVisibleRowCount(visibleRows);
		resizePopupScrollPane(list, maxWidth, popupHeight);

		Object popupChild = combo.getAccessibleContext().getAccessibleChild(0);
		if (popupChild instanceof BasicComboPopup) {
			((BasicComboPopup) popupChild).setPopupSize(maxWidth, popupHeight);
		}
	}

	private static JList<?> findPopupList(JComboBox<?> combo) {
		Object child = combo.getAccessibleContext().getAccessibleChild(0);
		if (child instanceof ComboPopup)
			return ((ComboPopup) child).getList();
		return null;
	}

	private static void resizePopupScrollPane(JList<?> list, int width, int height) {
		Component parent = list.getParent();
		if (parent instanceof JViewport) {
			parent = parent.getParent();
		}
		if (parent instanceof JScrollPane) {
			JScrollPane scrollPane = (JScrollPane) parent;
			scrollPane.setPreferredSize(new Dimension(width, height));
			scrollPane.setMaximumSize(new Dimension(width, height));
			scrollPane.revalidate();
		}
	}

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

		boolean closedCombo = truncateInClosedField && index < 0 && !isComboPopupList(list) && !list.isShowing();
		int displayIndex = index >= 0 ? index : Math.max(list.getSelectedIndex(), 0);

		String indexPrefix = String.format(JComponentConstants.ListRendering.INDEX_FORMAT,
				displayIndex + 1, totalItems);

		String displayText = indexPrefix;

		if (value != null) {
			String valueText = value.toString();
			if (valueText != null) {
				if (closedCombo)
					displayText += truncateIfNeeded(valueText, indexPrefix.length());
				else
					displayText += valueText;
			}
		}

		setText(displayText);
		return c;
	}

	/** True when this list is the dropdown of a {@link javax.swing.JComboBox}. */
	static boolean isComboPopupList(JList<?> list) {
		for (Component c = list; c != null; c = c.getParent()) {
			if (c instanceof ComboPopup)
				return true;
		}
		return false;
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
