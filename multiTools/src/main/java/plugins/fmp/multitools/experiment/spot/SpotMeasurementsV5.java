package plugins.fmp.multitools.experiment.spot;

import java.util.ArrayList;
import java.util.List;

import icy.roi.ROI2D;

/**
 * Spot measures for the parallel V5 pipeline ({@code AREA_COUNT_V5}, {@code GREY_SUM_V5}).
 * Kept separate from legacy {@link Spot} inner measurements so legacy CSV and behavior stay isolated.
 */
public final class SpotMeasurementsV5 {

	private final SpotMeasure areaCount;
	private final SpotMeasure greySum;

	public SpotMeasurementsV5() {
		this.areaCount = new SpotMeasure("areaCountV5");
		this.greySum = new SpotMeasure("greySumV5");
	}

	public SpotMeasurementsV5(SpotMeasurementsV5 source, boolean includeData) {
		this.areaCount = new SpotMeasure("areaCountV5");
		this.greySum = new SpotMeasure("greySumV5");
		if (includeData && source != null) {
			copyFrom(source);
		}
	}

	public void copyFrom(SpotMeasurementsV5 source) {
		if (source == null) {
			return;
		}
		areaCount.copyMeasures(source.areaCount);
		greySum.copyMeasures(source.greySum);
	}

	public void addFrom(SpotMeasurementsV5 source) {
		if (source == null) {
			return;
		}
		areaCount.addMeasures(source.areaCount);
		greySum.addMeasures(source.greySum);
	}

	public void computePI(SpotMeasurementsV5 m1, int n1, SpotMeasurementsV5 m2, int n2) {
		if (m1 == null || m2 == null) {
			return;
		}
		areaCount.computePI(m1.areaCount, m2.areaCount);
		greySum.computePI(m1.greySum, m2.greySum);
	}

	public void computeSUM(SpotMeasurementsV5 m1, int n1, SpotMeasurementsV5 m2, int n2) {
		if (m1 == null || m2 == null) {
			return;
		}
		areaCount.computeSUM(m1.areaCount, n1, m2.areaCount, n2);
		greySum.computeSUM(m1.greySum, n1, m2.greySum, n2);
	}

	public void normalizeMeasures() {
		areaCount.normalizeValues();
		greySum.normalizeValues();
	}

	public SpotMeasure getAreaCount() {
		return areaCount;
	}

	public SpotMeasure getGreySum() {
		return greySum;
	}

	public void restoreClippedMeasures() {
		restoreClippedMeasure(areaCount);
		restoreClippedMeasure(greySum);
	}

	private static void restoreClippedMeasure(SpotMeasure measure) {
		if (measure != null) {
			measure.getSpotLevel2D().restoreCroppedLevel2D();
		}
	}

	public void transferMeasuresToLevel2D() {
		if (areaCount != null) {
			areaCount.transferValuesToLevel2D();
		}
		if (greySum != null) {
			greySum.transferValuesToLevel2D();
		}
	}

	public void transferRoiMeasuresToLevel2D() {
		if (areaCount != null) {
			areaCount.getSpotLevel2D().transferROItoLevel2D();
		}
		if (greySum != null) {
			greySum.getSpotLevel2D().transferROItoLevel2D();
		}
	}

	public void adjustLevel2DMeasuresToImageWidth(int imageWidth) {
		if (areaCount != null) {
			areaCount.getSpotLevel2D().adjustLevel2DToImageWidth(imageWidth);
		}
		if (greySum != null) {
			greySum.getSpotLevel2D().adjustLevel2DToImageWidth(imageWidth);
		}
	}

	public void cropLevel2DMeasuresToImageWidth(int imageWidth) {
		if (areaCount != null) {
			areaCount.getSpotLevel2D().cropLevel2DToNPoints(imageWidth);
		}
		if (greySum != null) {
			greySum.getSpotLevel2D().cropLevel2DToNPoints(imageWidth);
		}
	}

	public void initializeLevel2DMeasures() {
		if (areaCount != null) {
			areaCount.getSpotLevel2D().clearLevel2D();
		}
		if (greySum != null) {
			greySum.getSpotLevel2D().clearLevel2D();
		}
	}

	public List<ROI2D> transferLevel2DToRois(int imageHeight) {
		List<ROI2D> rois = new ArrayList<>();
		if (areaCount != null) {
			ROI2D roi = areaCount.getSpotLevel2D().getROIForImage("areaCountV5", 0, imageHeight);
			if (roi != null) {
				rois.add(roi);
			}
		}
		return rois;
	}

	public void transferRoiToMeasures(ROI2D roi, int imageHeight) {
		if (roi != null && areaCount != null) {
			areaCount.getSpotLevel2D().transferROItoLevel2D();
		}
		if (roi != null && greySum != null) {
			greySum.getSpotLevel2D().transferROItoLevel2D();
		}
	}
}
