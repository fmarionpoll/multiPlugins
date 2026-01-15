package plugins.fmp.multiSPOTS96.experiment.cages;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Objects;
import java.util.Optional;

import icy.roi.BooleanMask2D;
import icy.roi.ROI2D;

/**
 * Immutable data class representing cage information with proper encapsulation.
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 */
public final class CageData {
	private final BooleanMask2D cageMask2D;
	private final CageProperties properties;
	private final boolean valid;
	private final boolean detectEnabled;
	private final boolean initialFlyRemoved;
	private final ROI2D roi;

	private CageData(Builder builder) {
		this.cageMask2D = builder.cageMask2D;
		this.properties = Objects.requireNonNull(builder.properties, "Properties cannot be null");
		this.valid = builder.valid;
		this.detectEnabled = builder.detectEnabled;
		this.initialFlyRemoved = builder.initialFlyRemoved;
		this.roi = Objects.requireNonNull(builder.roi, "ROI cannot be null");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static CageData createValid(ROI2D roi, CageProperties properties) {
		return builder().withRoi(roi).withProperties(properties).valid(true).detectEnabled(true).build();
	}

	public static CageData createInvalid(ROI2D roi, String reason) {
		return builder().withRoi(roi).withProperties(new CageProperties()).valid(false).detectEnabled(false).build();
	}

	// Getters with validation
	public Optional<BooleanMask2D> getCageMask2D() {
		return Optional.ofNullable(cageMask2D);
	}

	public CageProperties getProperties() {
		return properties;
	}

	public boolean isValid() {
		return valid;
	}

	public boolean isDetectEnabled() {
		return detectEnabled;
	}

	public boolean isInitialFlyRemoved() {
		return initialFlyRemoved;
	}

	public ROI2D getRoi() {
		return roi;
	}

	// Computed properties
	public String getName() {
		return Optional.ofNullable(roi.getName()).orElse("unnamed_cage");
	}

	public Rectangle2D getBounds() {
		return roi.getBounds2D();
	}

	public Point2D getCenterTop() {
		Rectangle2D rect = getBounds();
		return new Point2D.Double(rect.getX() + rect.getWidth() / 2, rect.getY());
	}

	public boolean hasValidBounds() {
		Rectangle2D bounds = getBounds();
		return bounds.getWidth() > 0 && bounds.getHeight() > 0;
	}

	public boolean hasMask() {
		return cageMask2D != null;
	}

	// Transformation methods (return new instances)
	public CageData withValidation(boolean valid) {
		return builder().from(this).valid(valid).build();
	}

	public CageData withDetection(boolean detectEnabled) {
		return builder().from(this).detectEnabled(detectEnabled).build();
	}

	public CageData withMask(BooleanMask2D mask) {
		return builder().from(this).withCageMask2D(mask).build();
	}

	public CageData withProperties(CageProperties properties) {
		return builder().from(this).withProperties(properties).build();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null || getClass() != obj.getClass())
			return false;
		CageData cageData = (CageData) obj;
		return Objects.equals(getName(), cageData.getName())
				&& Objects.equals(properties.getCageID(), cageData.properties.getCageID());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getName(), properties.getCageID());
	}

	@Override
	public String toString() {
		return String.format("CageData{name='%s', cageID=%d, valid=%b, detectEnabled=%b}", getName(),
				properties.getCageID(), valid, detectEnabled);
	}

	public static class Builder {
		private BooleanMask2D cageMask2D;
		private CageProperties properties;
		private boolean valid = true;
		private boolean detectEnabled = true;
		private boolean initialFlyRemoved = false;
		private ROI2D roi;

		public Builder withCageMask2D(BooleanMask2D cageMask2D) {
			this.cageMask2D = cageMask2D;
			return this;
		}

		public Builder withProperties(CageProperties properties) {
			this.properties = properties;
			return this;
		}

		public Builder valid(boolean valid) {
			this.valid = valid;
			return this;
		}

		public Builder detectEnabled(boolean detectEnabled) {
			this.detectEnabled = detectEnabled;
			return this;
		}

		public Builder initialFlyRemoved(boolean initialFlyRemoved) {
			this.initialFlyRemoved = initialFlyRemoved;
			return this;
		}

		public Builder withRoi(ROI2D roi) {
			this.roi = roi;
			return this;
		}

		public Builder from(CageData cageData) {
			this.cageMask2D = cageData.cageMask2D;
			this.properties = cageData.properties;
			this.valid = cageData.valid;
			this.detectEnabled = cageData.detectEnabled;
			this.initialFlyRemoved = cageData.initialFlyRemoved;
			this.roi = cageData.roi;
			return this;
		}

		public CageData build() {
			return new CageData(this);
		}
	}
}