package plugins.fmp.multiSPOTS96.experiment.spots;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.Objects;
import java.util.Optional;

import icy.image.IcyBufferedImage;
import icy.roi.BooleanMask2D;
import plugins.kernel.roi.roi2d.ROI2DShape;

/**
 * Immutable data class representing spot information with proper encapsulation.
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 */
public final class SpotData {
	private final int kymographIndex;
	private final int spotCamDataT;
	private final int spotKymographT;
	private final String spotFilenameTIFF;
	private final IcyBufferedImage spotImage;
	private final BooleanMask2D mask2DSpot;
	private final SpotProperties properties;
	private final boolean valid;
	private final boolean okToAnalyze;
	private final ROI2DShape roi;

	private SpotData(Builder builder) {
		this.kymographIndex = builder.kymographIndex;
		this.spotCamDataT = builder.spotCamDataT;
		this.spotKymographT = builder.spotKymographT;
		this.spotFilenameTIFF = builder.spotFilenameTIFF;
		this.spotImage = builder.spotImage;
		this.mask2DSpot = builder.mask2DSpot;
		this.properties = builder.properties != null ? builder.properties : new SpotProperties();
		this.valid = builder.valid;
		this.okToAnalyze = builder.okToAnalyze;
		this.roi = Objects.requireNonNull(builder.roi, "ROI cannot be null");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static SpotData createValid(ROI2DShape roi, SpotProperties properties) {
		return builder().withRoi(roi).withProperties(properties).valid(true).okToAnalyze(true).build();
	}

	public static SpotData createInvalid(ROI2DShape roi, String reason) {
		return builder().withRoi(roi).valid(false).okToAnalyze(false).build();
	}

	// Getters with validation
	public int getKymographIndex() {
		return kymographIndex;
	}

	public int getSpotCamDataT() {
		return spotCamDataT;
	}

	public int getSpotKymographT() {
		return spotKymographT;
	}

	public Optional<String> getSpotFilenameTIFF() {
		return Optional.ofNullable(spotFilenameTIFF);
	}

	public Optional<IcyBufferedImage> getSpotImage() {
		return Optional.ofNullable(spotImage);
	}

	public Optional<BooleanMask2D> getMask2DSpot() {
		return Optional.ofNullable(mask2DSpot);
	}

	public SpotProperties getProperties() {
		return properties;
	}

	public boolean isValid() {
		return valid;
	}

	public boolean isOkToAnalyze() {
		return okToAnalyze;
	}

	public ROI2DShape getRoi() {
		return roi;
	}

	// Computed properties
	public String getName() {
		return Optional.ofNullable(properties.getName()).orElse(roi.getName());
	}

	public Point2D getCenter() {
		Point pt = roi.getPosition();
		Rectangle rect = roi.getBounds();
		pt.translate(rect.height / 2, rect.width / 2);
		return pt;
	}

	public Rectangle getBounds() {
		return roi.getBounds();
	}

	public boolean hasValidBounds() {
		Rectangle bounds = getBounds();
		return bounds.width > 0 && bounds.height > 0;
	}

	public boolean hasImage() {
		return spotImage != null;
	}

	public boolean hasMask() {
		return mask2DSpot != null;
	}

	// Transformation methods (return new instances)
	public SpotData withValidation(boolean valid) {
		return builder().from(this).valid(valid).build();
	}

	public SpotData withAnalysisFlag(boolean okToAnalyze) {
		return builder().from(this).okToAnalyze(okToAnalyze).build();
	}

	public SpotData withProperties(SpotProperties properties) {
		return builder().from(this).withProperties(properties).build();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null || getClass() != obj.getClass())
			return false;
		SpotData spotData = (SpotData) obj;
		return Objects.equals(getName(), spotData.getName())
				&& Objects.equals(properties.getCageID(), spotData.properties.getCageID());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getName(), properties.getCageID());
	}

	@Override
	public String toString() {
		return String.format("SpotData{name='%s', cageID=%d, valid=%b, okToAnalyze=%b}", getName(),
				properties.getCageID(), valid, okToAnalyze);
	}

	public static class Builder {
		private int kymographIndex = -1;
		private int spotCamDataT = -1;
		private int spotKymographT = -1;
		private String spotFilenameTIFF;
		private IcyBufferedImage spotImage;
		private BooleanMask2D mask2DSpot;
		private SpotProperties properties;
		private boolean valid = true;
		private boolean okToAnalyze = true;
		private ROI2DShape roi;

		public Builder withKymographIndex(int kymographIndex) {
			this.kymographIndex = kymographIndex;
			return this;
		}

		public Builder withSpotCamDataT(int spotCamDataT) {
			this.spotCamDataT = spotCamDataT;
			return this;
		}

		public Builder withSpotKymographT(int spotKymographT) {
			this.spotKymographT = spotKymographT;
			return this;
		}

		public Builder withSpotFilenameTIFF(String spotFilenameTIFF) {
			this.spotFilenameTIFF = spotFilenameTIFF;
			return this;
		}

		public Builder withSpotImage(IcyBufferedImage spotImage) {
			this.spotImage = spotImage;
			return this;
		}

		public Builder withMask2DSpot(BooleanMask2D mask2DSpot) {
			this.mask2DSpot = mask2DSpot;
			return this;
		}

		public Builder withProperties(SpotProperties properties) {
			this.properties = properties;
			return this;
		}

		public Builder valid(boolean valid) {
			this.valid = valid;
			return this;
		}

		public Builder okToAnalyze(boolean okToAnalyze) {
			this.okToAnalyze = okToAnalyze;
			return this;
		}

		public Builder withRoi(ROI2DShape roi) {
			this.roi = roi;
			return this;
		}

		public Builder from(SpotData spotData) {
			this.kymographIndex = spotData.kymographIndex;
			this.spotCamDataT = spotData.spotCamDataT;
			this.spotKymographT = spotData.spotKymographT;
			this.spotFilenameTIFF = spotData.spotFilenameTIFF;
			this.spotImage = spotData.spotImage;
			this.mask2DSpot = spotData.mask2DSpot;
			this.properties = spotData.properties;
			this.valid = spotData.valid;
			this.okToAnalyze = spotData.okToAnalyze;
			this.roi = spotData.roi;
			return this;
		}

		public SpotData build() {
			return new SpotData(this);
		}
	}
}