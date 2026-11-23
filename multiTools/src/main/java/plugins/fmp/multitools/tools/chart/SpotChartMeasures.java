package plugins.fmp.multitools.tools.chart;

import plugins.fmp.multitools.tools.results.EnumResults;

/** One catalog for dialog controls, chart-window controls and request validation. */
public final class SpotChartMeasures {
	public enum Family {
		FILTER("Image filter", new EnumResults[] { EnumResults.FILTER_GREY,
				EnumResults.FILTER_GREY_FLY_MASKED, EnumResults.FILTER_GREY_SMOOTHED, EnumResults.FLY_PIXEL_COUNT },
				new EnumResults[] { EnumResults.AGG_FILTER_DEPLETION, EnumResults.CAGE_GREY_MEDIAN }),
		COLORS("Color distance", new EnumResults[] { EnumResults.COLOR_PIXEL_COUNT, EnumResults.COLOR_GREY,
				EnumResults.COLOR_GREY_PRE_GATE, EnumResults.COLOR_GREY_SMOOTHED, EnumResults.FLY_PIXEL_COUNT },
				new EnumResults[] { EnumResults.AGG_COLOR_DEPLETION, EnumResults.AGG_COLOR_PIXEL_SUM }),
		LINES("Lines", new EnumResults[] { EnumResults.KYMO_GREEN_HEIGHT_RATIO, EnumResults.KYMO_GREEN_HEIGHT,
				EnumResults.KYMO_ROW_FRACTION, EnumResults.KYMO_ROW_FRACTION_CHANGE, EnumResults.X_LINE_RATIO,
				EnumResults.RIM_LINE_RATIO }, new EnumResults[] { EnumResults.AGG_KYMO_HEIGHT_LOSS,
				EnumResults.AGG_X_LINE_CONSO, EnumResults.AGG_RIM_LINE_CONSO,
				EnumResults.KYMO_CAGE_MEAN_GREEN_HEIGHT_RATIO, EnumResults.KYMO_CAGE_MEAN_FRACT,
				EnumResults.KYMO_CAGE_MEAN_ABS_DELTA });

		public final String label;
		private final EnumResults[] spots;
		private final EnumResults[] summaries;

		Family(String label, EnumResults[] spots, EnumResults[] summaries) {
			this.label = label;
			this.spots = spots;
			this.summaries = summaries;
		}

		public EnumResults[] measures(boolean summary) {
			return (summary ? summaries : spots).clone();
		}
	}

	private SpotChartMeasures() { }

	public static EnumResults sourceOf(EnumResults result) {
		switch (result) {
		case AGG_FILTER_DEPLETION: return EnumResults.FILTER_GREY_SMOOTHED;
		case AGG_COLOR_DEPLETION: return EnumResults.COLOR_GREY_SMOOTHED;
		case AGG_COLOR_PIXEL_SUM: return EnumResults.COLOR_PIXEL_COUNT;
		case CAGE_GREY_MEDIAN: return EnumResults.FILTER_GREY;
		case AGG_KYMO_HEIGHT_LOSS:
		case KYMO_CAGE_MEAN_GREEN_HEIGHT_RATIO: return EnumResults.KYMO_GREEN_HEIGHT_RATIO;
		case AGG_X_LINE_CONSO: return EnumResults.X_LINE_RATIO;
		case AGG_RIM_LINE_CONSO: return EnumResults.RIM_LINE_RATIO;
		case KYMO_CAGE_MEAN_FRACT: return EnumResults.KYMO_ROW_FRACTION;
		case KYMO_CAGE_MEAN_ABS_DELTA: return EnumResults.KYMO_ROW_FRACTION_CHANGE;
		default: return result;
		}
	}

	public static String populationLabel(EnumResults result) {
		if (result == EnumResults.CAGE_GREY_MEDIAN)
			return "Whole cage: median grey signal, excluding fly-occluded samples";
		if (result == EnumResults.KYMO_CAGE_MEAN_GREEN_HEIGHT_RATIO
				|| result == EnumResults.KYMO_CAGE_MEAN_FRACT || result == EnumResults.KYMO_CAGE_MEAN_ABS_DELTA)
			return "Whole cage: mean across spots";
		return "Within each cage: sum by stimulus and concentration";
	}
}
