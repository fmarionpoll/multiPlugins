package plugins.fmp.multitools.tools;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility methods for working with {@link Color} instances.
 * <p>
 * Provides a simple mapping from standard AWT colors to short, human-readable
 * names, and falls back to a hex RGB string when no predefined name exists.
 */
public final class ColorUtils {

	private static final Map<Integer, String> STANDARD_COLOR_NAMES = new HashMap<>();

	static {
		register("black", Color.BLACK);
		register("blue", Color.BLUE);
		register("cyan", Color.CYAN);
		register("gray", Color.GRAY);
		register("dark_gray", Color.DARK_GRAY);
		register("light_gray", Color.LIGHT_GRAY);
		register("green", Color.GREEN);
		register("magenta", Color.MAGENTA);
		register("orange", Color.ORANGE);
		register("pink", Color.PINK);
		register("red", Color.RED);
		register("white", Color.WHITE);
		register("yellow", Color.YELLOW);
	}

	private ColorUtils() {
		// Utility class; prevent instantiation.
	}

	private static void register(String name, Color color) {
		if (color != null) {
			STANDARD_COLOR_NAMES.put(color.getRGB(), name);
		}
	}

	/**
	 * Returns a human-readable name for the given color.
	 * <ul>
	 * <li>If the color matches one of the standard AWT colors, returns a short
	 * name such as "red" or "gray".</li>
	 * <li>Otherwise, returns a hex RGB string like "#808080".</li>
	 * <li>If the color is {@code null}, returns an empty string.</li>
	 * </ul>
	 *
	 * @param color the color to describe
	 * @return a short name or hex RGB representation
	 */
	public static String getFriendlyColorName(Color color) {
		if (color == null)
			return "";

		String name = STANDARD_COLOR_NAMES.get(color.getRGB());
		if (name != null)
			return name;

		return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
	}
}

