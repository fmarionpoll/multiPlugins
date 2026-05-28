package plugins.fmp.multitools.experiment.ui;

import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import icy.gui.viewer.Viewer;
import icy.preferences.XMLPreferences;

/**
 * Persists {@link Viewer} position and size under an {@link XMLPreferences} prefix.
 */
public final class ViewerGeometryPreferences {

	public static final String KEY_X = FrameGeometryPreferences.KEY_X;
	public static final String KEY_Y = FrameGeometryPreferences.KEY_Y;
	public static final String KEY_W = FrameGeometryPreferences.KEY_W;
	public static final String KEY_H = FrameGeometryPreferences.KEY_H;

	private static final Set<Viewer> autoSaveInstalled = Collections
			.newSetFromMap(new WeakHashMap<Viewer, Boolean>());

	private ViewerGeometryPreferences() {
	}

	/**
	 * Restores bounds if stored width/height meet minimums.
	 *
	 * @return true if bounds were applied
	 */
	public static boolean restore(Viewer viewer, XMLPreferences prefs, String keyPrefix, int minWidth,
			int minHeight) {
		if (viewer == null || prefs == null) {
			return false;
		}
		String p = keyPrefix == null ? "" : keyPrefix;
		int w = readInt(prefs, p + KEY_W, 0);
		int h = readInt(prefs, p + KEY_H, 0);
		if (w < minWidth || h < minHeight) {
			return false;
		}
		int x = readInt(prefs, p + KEY_X, 0);
		int y = readInt(prefs, p + KEY_Y, 0);
		viewer.setBounds(FrameGeometryPreferences.clampToUsableScreen(new Rectangle(x, y, w, h)));
		return true;
	}

	public static void save(Viewer viewer, XMLPreferences prefs, String keyPrefix) {
		if (viewer == null || prefs == null) {
			return;
		}
		String p = keyPrefix == null ? "" : keyPrefix;
		Rectangle b = viewer.getBounds();
		if (b == null || b.width < 20 || b.height < 20) {
			return;
		}
		prefs.put(p + KEY_X, String.valueOf(b.x));
		prefs.put(p + KEY_Y, String.valueOf(b.y));
		prefs.put(p + KEY_W, String.valueOf(b.width));
		prefs.put(p + KEY_H, String.valueOf(b.height));
	}

	public static void installAutoSave(Viewer viewer, XMLPreferences prefs, String keyPrefix) {
		if (viewer == null || prefs == null || !autoSaveInstalled.add(viewer)) {
			return;
		}
		viewer.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentMoved(ComponentEvent e) {
				save(viewer, prefs, keyPrefix);
			}

			@Override
			public void componentResized(ComponentEvent e) {
				save(viewer, prefs, keyPrefix);
			}

			@Override
			public void componentHidden(ComponentEvent e) {
				save(viewer, prefs, keyPrefix);
			}
		});
	}

	private static int readInt(XMLPreferences prefs, String key, int defaultValue) {
		String s = prefs.get(key, String.valueOf(defaultValue));
		if (s == null || s.isEmpty()) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}
}
