package plugins.fmp.multitools.experiment.spot;

/**
 * Per-spot kymograph strip measures (one sample per kymograph column).
 */
public final class SpotKymographMeasurements {

	private final SpotMeasure kymoFract;
	private final SpotMeasure kymoAbsDelta;
	private final SpotMeasure kymoGreenHeight;
	private final SpotMeasure kymoGreenHeightRatio;
	private final SpotMeasure kymoLineRatio;
	private final SpotMeasure kymoRimRatio;

	public SpotKymographMeasurements() {
		this.kymoFract = new SpotMeasure("kymoFract");
		this.kymoAbsDelta = new SpotMeasure("kymoAbsDelta");
		this.kymoGreenHeight = new SpotMeasure("kymoGreenHeight");
		this.kymoGreenHeightRatio = new SpotMeasure("kymoGreenHeightRatio");
		this.kymoLineRatio = new SpotMeasure("kymoLineRatio");
		this.kymoRimRatio = new SpotMeasure("kymoRimRatio");
	}

	public SpotKymographMeasurements(SpotKymographMeasurements source, boolean includeData) {
		this.kymoFract = new SpotMeasure("kymoFract");
		this.kymoAbsDelta = new SpotMeasure("kymoAbsDelta");
		this.kymoGreenHeight = new SpotMeasure("kymoGreenHeight");
		this.kymoGreenHeightRatio = new SpotMeasure("kymoGreenHeightRatio");
		this.kymoLineRatio = new SpotMeasure("kymoLineRatio");
		this.kymoRimRatio = new SpotMeasure("kymoRimRatio");
		if (includeData && source != null) {
			copyFrom(source);
		}
	}

	public void copyFrom(SpotKymographMeasurements source) {
		if (source == null) {
			return;
		}
		kymoFract.copyMeasures(source.kymoFract);
		kymoAbsDelta.copyMeasures(source.kymoAbsDelta);
		kymoGreenHeight.copyMeasures(source.kymoGreenHeight);
		kymoGreenHeightRatio.copyMeasures(source.kymoGreenHeightRatio);
		kymoLineRatio.copyMeasures(source.kymoLineRatio);
		kymoRimRatio.copyMeasures(source.kymoRimRatio);
	}

	public SpotMeasure getAboveThresholdStripRowFraction() {
		return kymoFract;
	}

	public SpotMeasure getAbsoluteStripRowFractionChange() {
		return kymoAbsDelta;
	}

	public SpotMeasure getGreenMaskHeight() {
		return kymoGreenHeight;
	}

	public SpotMeasure getGreenMaskHeightToPeakRatio() {
		return kymoGreenHeightRatio;
	}

	public SpotMeasure getCrossLineSignalToBaselineRatio() {
		return kymoLineRatio;
	}

	public SpotMeasure getRimCoverageToBaselineRatio() {
		return kymoRimRatio;
	}

	public void restoreClippedMeasures() {
		restoreClippedMeasure(kymoFract);
		restoreClippedMeasure(kymoAbsDelta);
		restoreClippedMeasure(kymoGreenHeight);
		restoreClippedMeasure(kymoGreenHeightRatio);
		restoreClippedMeasure(kymoLineRatio);
		restoreClippedMeasure(kymoRimRatio);
	}

	private static void restoreClippedMeasure(SpotMeasure measure) {
		if (measure != null) {
			measure.getSpotLevel2D().restoreCroppedLevel2D();
		}
	}

	public void transferRoiMeasuresToLevel2D() {
		// kymo measures are not edited via ROI Level2D
	}

	public void adjustLevel2DMeasuresToImageWidth(int imageWidth) {
		// kymo measures are not tied to camera frame width
	}

	public boolean hasAnyData() {
		return hasData(kymoFract) || hasData(kymoAbsDelta) || hasData(kymoGreenHeight)
				|| hasData(kymoGreenHeightRatio) || hasData(kymoLineRatio) || hasData(kymoRimRatio);
	}

	private static boolean hasData(SpotMeasure m) {
		return m != null && m.getCount() > 0;
	}
}
