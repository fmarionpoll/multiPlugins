package plugins.fmp.multiSPOTS.dlg.export;

import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.util.function.Function;

import javax.swing.AbstractAction;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import plugins.fmp.multitools.tools.results.EnumResults;

/** Checkbox list shared by spot and aggregate export panels. Selection is checked state. */
final class MeasureList extends JList<EnumResults> {
	private static final long serialVersionUID = 1L;
	private final Function<EnumResults, String> tooltip;

	MeasureList(EnumResults[] measures) {
		this(measures, EnumResults::toDisplayLabel);
	}

	MeasureList(EnumResults[] measures, Function<EnumResults, String> tooltip) {
		super(measures);
		this.tooltip = tooltip;
		setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		JCheckBox checkBox = new JCheckBox();
		DefaultListCellRenderer labels = new DefaultListCellRenderer();
		setCellRenderer((list, value, index, selected, focused) -> {
			JLabel label = (JLabel) labels.getListCellRendererComponent(list, value, index, selected, focused);
			checkBox.setText(label.getText());
			checkBox.setSelected(selected);
			checkBox.setEnabled(list.isEnabled());
			checkBox.setFont(label.getFont());
			checkBox.setForeground(label.getForeground());
			checkBox.setBackground(label.getBackground());
			checkBox.setBorder(label.getBorder());
			checkBox.setBorderPainted(true);
			checkBox.setOpaque(true);
			checkBox.setComponentOrientation(list.getComponentOrientation());
			return checkBox;
		});
		setVisibleRowCount(Math.max(1, measures.length));
		setToolTipText("");
		bindKey("SPACE", "toggleMeasure", () -> toggle(getLeadSelectionIndex()));
		bindKey("UP", "previousMeasure", () -> moveFocus(-1));
		bindKey("DOWN", "nextMeasure", () -> moveFocus(1));
		bindKey("HOME", "firstMeasure", () -> focus(0));
		bindKey("END", "lastMeasure", () -> focus(getModel().getSize() - 1));
	}

	private void bindKey(String key, String name, Runnable action) {
		getInputMap().put(KeyStroke.getKeyStroke(key), name);
		getActionMap().put(name, new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent event) {
				if (isEnabled())
					action.run();
			}
		});
	}

	private void moveFocus(int offset) {
		int lead = getLeadSelectionIndex();
		focus(lead < 0 ? 0 : lead + offset);
	}

	private void focus(int index) {
		if (getModel().getSize() == 0)
			return;
		index = Math.max(0, Math.min(index, getModel().getSize() - 1));
		((DefaultListSelectionModel) getSelectionModel()).moveLeadSelectionIndex(index);
		ensureIndexIsVisible(index);
		repaint();
	}

	private void toggle(int index) {
		if (index < 0 || index >= getModel().getSize())
			return;
		if (isSelectedIndex(index))
			removeSelectionInterval(index, index);
		else
			addSelectionInterval(index, index);
		focus(index);
	}

	@Override
	protected void processMouseEvent(MouseEvent event) {
		// The renderer only paints checkboxes; the list owns their checked state.
		if (SwingUtilities.isLeftMouseButton(event)) {
			if (isEnabled() && event.getID() == MouseEvent.MOUSE_PRESSED) {
				requestFocusInWindow();
				int index = locationToIndex(event.getPoint());
				Rectangle cell = index < 0 ? null : getCellBounds(index, index);
				if (cell != null && cell.contains(event.getPoint()))
					toggle(index);
			}
			return;
		}
		super.processMouseEvent(event);
	}

	@Override
	protected void processMouseMotionEvent(MouseEvent event) {
		if (event.getID() != MouseEvent.MOUSE_DRAGGED)
			super.processMouseMotionEvent(event);
	}

	@Override
	public String getToolTipText(MouseEvent event) {
		int index = locationToIndex(event.getPoint());
		if (index < 0)
			return null;
		Rectangle cell = getCellBounds(index, index);
		if (cell == null || !cell.contains(event.getPoint()))
			return null;
		EnumResults measure = getModel().getElementAt(index);
		return measure == null ? null : tooltip.apply(measure);
	}
}
