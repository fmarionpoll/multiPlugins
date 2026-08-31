package plugins.fmp.multitools.experiment.capillaries.tracking;

/** Inclusive frame interval derived from adjacent tracking boundaries. */
public final class TrackingSegment {
	private final int startFrame;
	private final int endFrame;
	private final TrackingBoundary openingBoundary;

	TrackingSegment(int startFrame, int endFrame, TrackingBoundary openingBoundary) {
		this.startFrame = startFrame;
		this.endFrame = endFrame;
		this.openingBoundary = openingBoundary;
	}

	public int getStartFrame() { return startFrame; }
	public int getEndFrame() { return endFrame; }
	public TrackingBoundary getOpeningBoundary() { return openingBoundary; }

	public boolean contains(int frame) {
		return frame >= startFrame && frame <= endFrame;
	}
}
