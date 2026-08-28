package plugins.fmp.multitools.service;

import java.util.ArrayList;
import java.util.List;

import plugins.fmp.multitools.experiment.capillary.Capillary;

/**
 * Outcome of a {@link CapillaryLengthDetector} run over one experiment: one
 * {@link Measure} per capillary plus the cross-capillary statistics used to
 * spot detection failures.
 */
public class CapillaryLengthResult {

	public enum Status {
		OK("ok"),
		BORDER("reaches ROI end"),
		OUTLIER("outlier"),
		FAILED("not detected"),
		NO_ROI("no ROI");

		private final String label;

		Status(String label) {
			this.label = label;
		}

		public String getLabel() {
			return label;
		}

		/** True when the detected length is usable as a calibration value. */
		public boolean isUsable() {
			return this == OK || this == BORDER;
		}
	}

	public static class Measure {
		private final Capillary capillary;
		private final String name;
		private final int previousPixels;
		private double roiPixels = Double.NaN;
		private double detectedPixels = Double.NaN;
		private double fittedPixels = Double.NaN;
		private double centroidX = Double.NaN;
		private Status status = Status.FAILED;
		private String message = "";
		private boolean selected = false;

		public Measure(Capillary capillary, String name, int previousPixels) {
			this.capillary = capillary;
			this.name = name;
			this.previousPixels = previousPixels;
		}

		public Capillary getCapillary() {
			return capillary;
		}

		public String getName() {
			return name;
		}

		public int getPreviousPixels() {
			return previousPixels;
		}

		public double getRoiPixels() {
			return roiPixels;
		}

		public void setRoiPixels(double roiPixels) {
			this.roiPixels = roiPixels;
		}

		public double getDetectedPixels() {
			return detectedPixels;
		}

		public void setDetectedPixels(double detectedPixels) {
			this.detectedPixels = detectedPixels;
		}

		public double getFittedPixels() {
			return fittedPixels;
		}

		public void setFittedPixels(double fittedPixels) {
			this.fittedPixels = fittedPixels;
		}

		public double getCentroidX() {
			return centroidX;
		}

		public void setCentroidX(double centroidX) {
			this.centroidX = centroidX;
		}

		public Status getStatus() {
			return status;
		}

		public void setStatus(Status status) {
			this.status = status;
		}

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}

		public boolean isSelected() {
			return selected;
		}

		public void setSelected(boolean selected) {
			this.selected = selected;
		}

		public int getRoundedPixels() {
			return (int) Math.round(detectedPixels);
		}
	}

	private final List<Measure> measures = new ArrayList<Measure>();
	private double medianPixels = Double.NaN;
	private double minPixels = Double.NaN;
	private double maxPixels = Double.NaN;
	private double physicalLengthMm = Double.NaN;
	private String errorMessage = null;

	public List<Measure> getMeasures() {
		return measures;
	}

	public void addMeasure(Measure measure) {
		measures.add(measure);
	}

	public double getMedianPixels() {
		return medianPixels;
	}

	public void setMedianPixels(double medianPixels) {
		this.medianPixels = medianPixels;
	}

	public double getMinPixels() {
		return minPixels;
	}

	public void setMinPixels(double minPixels) {
		this.minPixels = minPixels;
	}

	public double getMaxPixels() {
		return maxPixels;
	}

	public void setMaxPixels(double maxPixels) {
		this.maxPixels = maxPixels;
	}

	public double getPhysicalLengthMm() {
		return physicalLengthMm;
	}

	public void setPhysicalLengthMm(double physicalLengthMm) {
		this.physicalLengthMm = physicalLengthMm;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public boolean hasError() {
		return errorMessage != null;
	}

	public int countSelected() {
		int n = 0;
		for (Measure m : measures) {
			if (m.isSelected())
				n++;
		}
		return n;
	}

	public int countUsable() {
		int n = 0;
		for (Measure m : measures) {
			if (m.getStatus().isUsable())
				n++;
		}
		return n;
	}

	/**
	 * Peak-to-peak variation of the detected lengths, in percent of the median.
	 * This is the magnitude of the geometric distortion across the image.
	 */
	public double getSpreadPercent() {
		if (!(medianPixels > 0) || !Double.isFinite(minPixels) || !Double.isFinite(maxPixels))
			return Double.NaN;
		return 100. * (maxPixels - minPixels) / medianPixels;
	}
}
