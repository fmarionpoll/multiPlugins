package plugins.fmp.multitools.tools.JComponents;

import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.basic.ComboPopup;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.Logger;

/**
 * Right-click menu on the browse experiment combo: copy the current directory,
 * or every directory in the list, without loading experiment data.
 */
public final class ExperimentListCopyMenu {

	private static final String INSTALLED = "experimentListCopyMenu";

	private ExperimentListCopyMenu() {
	}

	public static void install(JComboBoxExperimentLazy combo) {
		if (Boolean.TRUE.equals(combo.getClientProperty(INSTALLED)))
			return;
		if (combo.getToolTipText() == null)
			combo.setToolTipText("Right-click to copy the current experiment or the whole list.");

		MouseAdapter popup = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				showIfPopup(combo, e);
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				showIfPopup(combo, e);
			}
		};
		attach(combo, popup);
		combo.addPopupMenuListener(new PopupMenuListener() {
			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
				JList<?> list = findPopupList(combo);
				if (list != null)
					attach(list, popup);
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
			}

			@Override
			public void popupMenuCanceled(PopupMenuEvent e) {
			}
		});
	}

	private static void attach(Component component, MouseAdapter popup) {
		if (!(component instanceof JComponent))
			return;
		JComponent jc = (JComponent) component;
		if (Boolean.TRUE.equals(jc.getClientProperty(INSTALLED)))
			return;
		jc.putClientProperty(INSTALLED, Boolean.TRUE);
		jc.addMouseListener(popup);
		if (component instanceof Container) {
			Container container = (Container) component;
			for (Component child : container.getComponents())
				attach(child, popup);
			container.addContainerListener(new ContainerAdapter() {
				@Override
				public void componentAdded(ContainerEvent e) {
					attach(e.getChild(), popup);
				}
			});
		}
	}

	private static void showIfPopup(JComboBoxExperimentLazy combo, MouseEvent e) {
		if (!e.isPopupTrigger())
			return;
		int currentIndex = indexForEvent(combo, e);
		boolean clickedOtherRow = e.getSource() instanceof JList && currentIndex >= 0
				&& currentIndex != combo.getSelectedIndex();
		String currentName = nameAt(combo, currentIndex);
		String allNames = allNames(combo);

		JPopupMenu menu = new JPopupMenu();
		JMenuItem currentItem = new JMenuItem(clickedOtherRow ? "Copy this experiment" : "Copy current experiment");
		currentItem.setEnabled(currentName != null);
		if (currentName != null)
			currentItem.setToolTipText(currentName);
		currentItem.addActionListener(ev -> copyToClipboard(currentName));

		JMenuItem allItem = new JMenuItem("Copy all experiments");
		allItem.setEnabled(allNames != null);
		allItem.setToolTipText("Full directory of each experiment in this list, one per line.");
		allItem.addActionListener(ev -> copyToClipboard(allNames));

		menu.add(currentItem);
		menu.add(allItem);
		menu.show(e.getComponent(), e.getX(), e.getY());
		e.consume();
	}

	private static int indexForEvent(JComboBoxExperimentLazy combo, MouseEvent e) {
		if (e.getSource() instanceof JList) {
			JList<?> list = (JList<?>) e.getSource();
			int row = list.locationToIndex(e.getPoint());
			if (row >= 0) {
				Rectangle bounds = list.getCellBounds(row, row);
				if (bounds != null && bounds.contains(e.getPoint()))
					return row;
			}
		}
		return combo.getSelectedIndex();
	}

	static String nameAt(JComboBoxExperimentLazy combo, int index) {
		if (combo == null || index < 0 || index >= combo.getItemCount())
			return null;
		Experiment exp = combo.getItemAtNoLoad(index);
		if (exp == null)
			return null;
		String name = exp.toString();
		if (name == null)
			return null;
		name = name.trim();
		return name.isEmpty() ? null : name;
	}

	static String allNames(JComboBoxExperimentLazy combo) {
		if (combo == null)
			return null;
		StringBuilder sb = new StringBuilder();
		int n = combo.getItemCount();
		for (int i = 0; i < n; i++) {
			String name = nameAt(combo, i);
			if (name == null)
				continue;
			if (sb.length() > 0)
				sb.append(System.lineSeparator());
			sb.append(name);
		}
		return sb.length() == 0 ? null : sb.toString();
	}

	private static void copyToClipboard(String text) {
		if (text == null || text.isEmpty())
			return;
		try {
			StringSelection selection = new StringSelection(text);
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
		} catch (IllegalStateException ex) {
			Logger.warn("Could not copy the experiment list to the clipboard.", ex);
		}
	}

	private static JList<?> findPopupList(JComboBoxExperimentLazy combo) {
		Object child = combo.getAccessibleContext().getAccessibleChild(0);
		if (child instanceof ComboPopup)
			return ((ComboPopup) child).getList();
		return null;
	}
}
