package plugins.fmp.multitools.tools.chart.style;

import java.awt.Color;
import java.util.Optional;

import org.jfree.chart.ChartColor;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import plugins.fmp.multitools.tools.Logger;

/**
 * Centralizes the legacy encoding/decoding of series metadata in
 * {@link XYSeries#setDescription(String)}.
 *
 * <p>
 * Today, renderer styling and some plot background logic depends on parsing a
 * colon-delimited string. We keep that format stable here to avoid churn while
 * migrating call sites.
 * </p>
 */
public final class SeriesStyleCodec {

	// Legacy format produced in chart code: "ID:..:Pos:..:nflies:..:R:..:G:..:B:.."
	private static final String DELIM = ":";
	private static final String KEY_NFLIES = "nflies";
	private static final String KEY_R = "R";
	private static final String KEY_G = "G";
	private static final String KEY_B = "B";

	private SeriesStyleCodec() {
	}

	public static String buildDescription(int cageId, int cagePosition, int nFlies, Color color) {
		if (color == null)
			color = Color.BLACK;

		return "ID" + DELIM + cageId + DELIM + "Pos" + DELIM + cagePosition + DELIM + KEY_NFLIES + DELIM + nFlies
				+ DELIM + KEY_R + DELIM + color.getRed() + DELIM + KEY_G + DELIM + color.getGreen() + DELIM + KEY_B
				+ DELIM + color.getBlue();
	}

	public static Optional<Integer> tryParseNFlies(String description) {
		return tryParseIntValue(description, KEY_NFLIES);
	}

	public static Optional<Color> tryParseColor(String description) {
		try {
			Optional<Integer> r = tryParseIntValue(description, KEY_R);
			Optional<Integer> g = tryParseIntValue(description, KEY_G);
			Optional<Integer> b = tryParseIntValue(description, KEY_B);
			if (r.isEmpty() || g.isEmpty() || b.isEmpty())
				return Optional.empty();
			return Optional.of(new Color(r.get(), g.get(), b.get()));
		} catch (Exception e) {
			return Optional.empty();
		}
	}

	private static Optional<Integer> tryParseIntValue(String description, String key) {
		if (description == null || description.isEmpty())
			return Optional.empty();
		String[] tokens = description.split(DELIM);
		for (int i = 0; i + 1 < tokens.length; i++) {
			if (key.equals(tokens[i])) {
				try {
					return Optional.of(Integer.parseInt(tokens[i + 1]));
				} catch (NumberFormatException e) {
					return Optional.empty();
				}
			}
		}
		return Optional.empty();
	}

	public static int getNFliesOrDefault(XYSeriesCollection dataset, int defaultValue) {
		if (dataset == null || dataset.getSeriesCount() == 0)
			return defaultValue;
		XYSeries series = dataset.getSeries(0);
		if (series == null)
			return defaultValue;
		return tryParseNFlies(series.getDescription()).orElse(defaultValue);
	}

	public static void applySeriesPaintsFromDescription(XYSeriesCollection dataset, XYLineAndShapeRenderer renderer) {
		if (dataset == null || renderer == null)
			return;

		for (int i = 0; i < dataset.getSeriesCount(); i++) {
			try {
				String description = dataset.getSeries(i).getDescription();
				Color c = tryParseColor(description).orElse(Color.BLACK);
				renderer.setSeriesPaint(i, new ChartColor(c.getRed(), c.getGreen(), c.getBlue()));
			} catch (Exception e) {
				Logger.debug("Failed to style series " + i + ": " + e.getMessage());
				renderer.setSeriesPaint(i, ChartColor.BLACK);
			}
		}
	}
}
