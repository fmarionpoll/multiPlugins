package plugins.fmp.multitools.experiment.spot;

/**
 * Per-spot kymograph strip measures (one sample per kymograph column).
 */
public final class SpotMeasurementsKymo {

	private final SpotMeasure kymoFract;
	private final SpotMeasure kymoAbsDelta;
	private final SpotMeasure kymoGreenHeight;
	private final SpotMeasure kymoGreenHeightRatio;

	public SpotMeasurementsKymo() {
		this.kymoFract = new SpotMeasure("kymoFract");
		this.kymoAbsDelta = new SpotMeasure("kymoAbsDelta");
		this.kymoGreenHeight = new SpotMeasure("kymoGreenHeight");
		this.kymoGreenHeightRatio = new SpotMeasure("kymoGreenHeightRatio");
	}

	public SpotMeasurementsKymo(SpotMeasurementsKymo source, boolean includeData) {
		this.kymoFract = new SpotMeasure("kymoFract");
		this.kymoAbsDelta = new SpotMeasure("kymoAbsDelta");
		this.kymoGreenHeight = new SpotMeasure("kymoGreenHeight");
		this.kymoGreenHeightRatio = new SpotMeasure("kymoGreenHeightRatio");
		if (includeData && source != null) {
			copyFrom(source);
		}
	}

	public void copyFrom(SpotMeasurementsKymo source) {
		if (source == null) {
			return;
		}
		kymoFract.copyMeasures(source.kymoFract);
		kymoAbsDelta.copyMeasures(source.kymoAbsDelta);
		kymoGreenHeight.copyMeasures(source.kymoGreenHeight);
		kymoGreenHeightRatio.copyMeasures(source.kymoGreenHeightRatio);
	}

	public SpotMeasure getKymoFract() {
		return kymoFract;
	}

	public SpotMeasure getKymoAbsDelta() {
		return kymoAbsDelta;
	}

	public SpotMeasure getKymoGreenHeight() {
		return kymoGreenHeight;
	}

	public SpotMeasure getKymoGreenHeightRatio() {
		return kymoGreenHeightRatio;
	}

	public void restoreClippedMeasures() {
		restoreClippedMeasure(kymoFract);
		restoreClippedMeasure(kymoAbsDelta);
		restoreClippedMeasure(kymoGreenHeight);
		restoreClippedMeasure(kymoGreenHeightRatio);
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
		return hasData(kymoFract) || hasData(kymoAbsDelta) || hasData(kymoGreenHeight) || hasData(kymoGreenHeightRatio);
	}

	private static boolean hasData(SpotMeasure m) {
		return m != null && m.getCount() > 0;
	}
}
