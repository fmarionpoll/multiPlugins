package plugins.fmp.multitools.experiment.capillary;

import java.awt.Color;
import java.util.Locale;

import plugins.fmp.multitools.tools.csv.CsvNumberParsing;
import plugins.fmp.multitools.tools.polyline.Level2D;
import plugins.kernel.roi.roi2d.ROI2DLine;

/**
 * Gulps detected by vertical correlation, stored apart from the genuine gulps
 * together with the settings that produced them. Amplitudes are the downward
 * meniscus shift in pixels, indexed by kymograph column.
 */
public class CapillaryCorrelationGulps {
	/**
	 * ROI name token. Must not contain "gulp", "level" or "derivative": ROIs with
	 * those names are absorbed into the genuine measures.
	 */
	public static final String ROI_TOKEN = "xcorr";
	public static final Color ROI_COLOR = new Color(30, 110, 255);

	public static final double DEFAULT_MAX_SHIFT_PX = 6;

	private final CapillaryGulps events = new CapillaryGulps();
	private int windowRadius = 0;
	private double maxShiftPx = DEFAULT_MAX_SHIFT_PX;
	private double thresholdPxPerMin = Double.NaN;
	private boolean manualThreshold = false;

	public void copy(CapillaryCorrelationGulps source) {
		if (source == null)
			return;
		events.copy(source.events);
		windowRadius = source.windowRadius;
		maxShiftPx = source.maxShiftPx;
		thresholdPxPerMin = source.thresholdPxPerMin;
		manualThreshold = source.manualThreshold;
	}

	public void clear() {
		events.clear();
		windowRadius = 0;
		maxShiftPx = DEFAULT_MAX_SHIFT_PX;
		thresholdPxPerMin = Double.NaN;
		manualThreshold = false;
	}

	public void set(double[] amplitudes, int windowRadius, double maxShiftPx, double thresholdPxPerMin,
			boolean manualThreshold) {
		events.clear();
		events.ensureSize(amplitudes.length);
		for (int i = 0; i < amplitudes.length; i++)
			events.setAmplitudeAt(i, amplitudes[i]);
		this.windowRadius = windowRadius;
		this.maxShiftPx = maxShiftPx;
		this.thresholdPxPerMin = thresholdPxPerMin;
		this.manualThreshold = manualThreshold;
	}

	public boolean hasMeasures() {
		Level2D heights = events.getHeightSeries();
		return heights != null && heights.npoints > 0;
	}

	public int countEvents() {
		Level2D heights = events.getHeightSeries();
		int n = 0;
		if (heights != null)
			for (int i = 0; i < heights.npoints; i++)
				if (heights.ypoints[i] > 0)
					n++;
		return n;
	}

	public CapillaryGulps getEvents() {
		return events;
	}

	public int getWindowRadius() {
		return windowRadius;
	}

	public double getMaxShiftPx() {
		return maxShiftPx;
	}

	public double getThresholdPxPerMin() {
		return thresholdPxPerMin;
	}

	public boolean isManualThreshold() {
		return manualThreshold;
	}

	public void csvExportToRow(StringBuffer sbf, String sep) {
		sbf.append(windowRadius).append(sep).append(maxShiftPx).append(sep).append(thresholdPxPerMin).append(sep)
				.append(manualThreshold).append(sep);
		events.csvExportDataFlatToRow(sbf, sep);
	}

	/** withMaxShift: false for rows written before the max_shift_px column existed. */
	public void csvImportFromRow(String[] data, int startAt, boolean withMaxShift) {
		clear();
		int i = startAt;
		if (data == null || data.length <= i + (withMaxShift ? 4 : 3))
			return;
		windowRadius = (int) CsvNumberParsing.parseDouble(data[i++]);
		if (withMaxShift)
			maxShiftPx = CsvNumberParsing.parseDouble(data[i++]);
		thresholdPxPerMin = CsvNumberParsing.parseDouble(data[i++]);
		manualThreshold = Boolean.parseBoolean(data[i++].trim());
		events.csvImportDataFromRow(data, i);
	}

	/**
	 * Drawn half a column right of column x so a genuine (red) gulp at the same
	 * column stays visible.
	 */
	public static ROI2DLine createRoi(String kymographPrefix, int x, double yBottom, double amplitude, int t) {
		double xx = x + .5;
		ROI2DLine line = new ROI2DLine(xx, yBottom, xx, yBottom - Math.max(0, amplitude));
		line.setName(kymographPrefix + "_" + ROI_TOKEN + String.format(Locale.ROOT, "%07d", x));
		line.setColor(ROI_COLOR);
		line.setStroke(2);
		line.setT(t);
		line.setReadOnly(true);
		return line;
	}
}
