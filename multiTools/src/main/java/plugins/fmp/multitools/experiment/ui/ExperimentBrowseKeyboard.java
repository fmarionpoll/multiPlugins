package plugins.fmp.multitools.experiment.ui;

import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.function.Supplier;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.InputMap;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * Ctrl+arrow navigation for experiment browse panels (prev/next), with a global
 * key dispatcher so shortcuts work when focus is outside text fields.
 */
public final class ExperimentBrowseKeyboard {

	private static final String DISPATCHER_PROP = ExperimentBrowseKeyboard.class.getName() + ".dispatcher";

	private ExperimentBrowseKeyboard() {
	}

	public static void install(JComponent bindingRoot, JButton previousButton, JButton nextButton,
			Supplier<Boolean> isHostActive) {
		InputMap im = bindingRoot.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		ActionMap am = bindingRoot.getActionMap();

		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.CTRL_DOWN_MASK), "browseNext");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.CTRL_DOWN_MASK), "browsePrevious");

		if (bindingRoot.getClientProperty(DISPATCHER_PROP) == null) {
			KeyEventDispatcher dispatcher = new KeyEventDispatcher() {
				@Override
				public boolean dispatchKeyEvent(KeyEvent e) {
					if (e.getID() != KeyEvent.KEY_PRESSED) {
						return false;
					}
					if (!e.isControlDown()) {
						return false;
					}
					if (!bindingRoot.isDisplayable()) {
						return false;
					}
					Boolean active = isHostActive.get();
					if (active == null || !active) {
						return false;
					}

					java.awt.Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager()
							.getFocusOwner();
					if (focusOwner instanceof JTextComponent) {
						return false;
					}

					int code = e.getKeyCode();
					if (code == KeyEvent.VK_UP) {
						SwingUtilities.invokeLater(() -> {
							if (previousButton.isEnabled()) {
								previousButton.doClick();
							}
						});
						return true;
					}
					if (code == KeyEvent.VK_DOWN) {
						SwingUtilities.invokeLater(() -> {
							if (nextButton.isEnabled()) {
								nextButton.doClick();
							}
						});
						return true;
					}
					return false;
				}
			};

			bindingRoot.putClientProperty(DISPATCHER_PROP, dispatcher);
			KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher);

			bindingRoot.addHierarchyListener(new HierarchyListener() {
				@Override
				public void hierarchyChanged(HierarchyEvent e) {
					if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) == 0) {
						return;
					}
					if (!bindingRoot.isDisplayable()) {
						Object d = bindingRoot.getClientProperty(DISPATCHER_PROP);
						if (d instanceof KeyEventDispatcher) {
							KeyboardFocusManager.getCurrentKeyboardFocusManager()
									.removeKeyEventDispatcher((KeyEventDispatcher) d);
							bindingRoot.putClientProperty(DISPATCHER_PROP, null);
						}
					}
				}
			});
		}

		am.put("browseNext", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				if (nextButton.isEnabled()) {
					nextButton.doClick();
				}
			}
		});

		am.put("browsePrevious", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				if (previousButton.isEnabled()) {
					previousButton.doClick();
				}
			}
		});
	}
}
