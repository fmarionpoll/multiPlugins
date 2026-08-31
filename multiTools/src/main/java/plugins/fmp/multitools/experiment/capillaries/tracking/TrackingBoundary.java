package plugins.fmp.multitools.experiment.capillaries.tracking;

/**
 * Boundary between two independently trackable intervals. A boundary at T means
 * that the preceding segment ends at T-1 and the next segment begins at T.
 */
public final class TrackingBoundary implements Comparable<TrackingBoundary> {

	public enum Origin { AUTOMATIC, MANUAL }
	public enum Status { SUGGESTED, CONFIRMED, UNRESOLVED }

	private final int frame;
	private final Origin origin;
	private final Status status;
	private final String reason;
	private final double score;

	public TrackingBoundary(int frame, Origin origin, Status status, String reason, double score) {
		if (frame < 1)
			throw new IllegalArgumentException("A boundary must start at frame 1 or later");
		this.frame = frame;
		this.origin = origin != null ? origin : Origin.AUTOMATIC;
		this.status = status != null ? status : Status.SUGGESTED;
		this.reason = reason != null ? reason : "";
		this.score = score;
	}

	public int getFrame() { return frame; }
	public Origin getOrigin() { return origin; }
	public Status getStatus() { return status; }
	public String getReason() { return reason; }
	public double getScore() { return score; }

	public TrackingBoundary atFrame(int newFrame) {
		return new TrackingBoundary(newFrame, origin, status, reason, score);
	}

	public TrackingBoundary withStatus(Status newStatus) {
		return new TrackingBoundary(frame, origin, newStatus, reason, score);
	}

	@Override
	public int compareTo(TrackingBoundary other) {
		return Integer.compare(frame, other.frame);
	}
}
