package plugins.fmp.multitools.experiment.ui;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import icy.gui.frame.IcyFrame;
import icy.preferences.XMLPreferences;

/**
 * Persists {@link IcyFrame} position and size under an {@link XMLPreferences} prefix (e.g.
 * {@code selectFiles.} → {@code selectFiles.x}, {@code selectFiles.y}, {@code selectFiles.w},
 * {@code selectFiles.h}).
 */
public final class FrameGeometryPreferences {

	public static final String KEY_X = "x";
	public static final String KEY_Y = "y";
	public static final String KEY_W = "w";
	public static final String KEY_H = "h";

	private FrameGeometryPreferences() {
	}

	/**
	 * Restores bounds if stored width/height meet minimums.
	 *
	 * @return true if bounds were applied (caller should skip {@code center()})
	 */
	public static boolean restore(IcyFrame frame, XMLPreferences prefs, String keyPrefix, int minWidth,
			int minHeight) {
		if (frame == null || prefs == null) {
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
		Rectangle r = new Rectangle(x, y, w, h);
		frame.setBounds(clampToUsableScreen(r));
		return true;
	}

	public static void save(IcyFrame frame, XMLPreferences prefs, String keyPrefix) {
		if (frame == null || prefs == null) {
			return;
		}
		String p = keyPrefix == null ? "" : keyPrefix;
		Rectangle b = frame.getBoundsInternal();
		if (b == null || b.width < 20 || b.height < 20) {
			return;
		}
		prefs.put(p + KEY_X, String.valueOf(b.x));
		prefs.put(p + KEY_Y, String.valueOf(b.y));
		prefs.put(p + KEY_W, String.valueOf(b.width));
		prefs.put(p + KEY_H, String.valueOf(b.height));
	}

	public static void installAutoSave(IcyFrame frame, XMLPreferences prefs, String keyPrefix) {
		if (frame == null || prefs == null) {
			return;
		}
		frame.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentMoved(ComponentEvent e) {
				save(frame, prefs, keyPrefix);
			}

			@Override
			public void componentResized(ComponentEvent e) {
				save(frame, prefs, keyPrefix);
			}

			@Override
			public void componentHidden(ComponentEvent e) {
				save(frame, prefs, keyPrefix);
			}
		});
	}

	private static Rectangle clampToUsableScreen(Rectangle r) {
		Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
		int w = Math.min(Math.max(r.width, 120), usable.width);
		int h = Math.min(Math.max(r.height, 80), usable.height);
		int x = r.x;
		int y = r.y;
		if (x + w < usable.x + 40) {
			x = usable.x;
		}
		if (y + h < usable.y + 30) {
			y = usable.y;
		}
		if (x > usable.x + usable.width - 40) {
			x = usable.x + usable.width - w;
		}
		if (y > usable.y + usable.height - 30) {
			y = usable.y + usable.height - h;
		}
		return new Rectangle(x, y, w, h);
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
