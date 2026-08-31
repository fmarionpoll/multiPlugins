package plugins.fmp.multitools.experiment.capillaries.tracking;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Editable tracking boundaries. Segments are deliberately derived rather than
 * stored, so moving or deleting a boundary never destroys capillary ROIs.
 */
public final class TrackingTimeline {
	private final NavigableMap<Integer, TrackingBoundary> boundaries = new TreeMap<Integer, TrackingBoundary>();

	public synchronized TrackingBoundary put(TrackingBoundary boundary) {
		if (boundary == null)
			throw new IllegalArgumentException("boundary is null");
		return boundaries.put(boundary.getFrame(), boundary);
	}

	public synchronized TrackingBoundary addManual(int frame, String reason) {
		TrackingBoundary boundary = new TrackingBoundary(frame, TrackingBoundary.Origin.MANUAL,
				TrackingBoundary.Status.CONFIRMED, reason, Double.NaN);
		put(boundary);
		return boundary;
	}

	public synchronized TrackingBoundary remove(int frame) {
		return boundaries.remove(frame);
	}

	public synchronized TrackingBoundary move(int oldFrame, int newFrame) {
		TrackingBoundary old = boundaries.remove(oldFrame);
		if (old == null)
			return null;
		TrackingBoundary moved = old.atFrame(newFrame);
		boundaries.put(newFrame, moved);
		return moved;
	}

	public synchronized TrackingBoundary get(int frame) {
		return boundaries.get(frame);
	}

	public synchronized void clear() {
		boundaries.clear();
	}

	public synchronized List<TrackingBoundary> getBoundaries() {
		return Collections.unmodifiableList(new ArrayList<TrackingBoundary>(boundaries.values()));
	}

	public synchronized void replaceAll(Collection<TrackingBoundary> replacements) {
		boundaries.clear();
		if (replacements != null)
			for (TrackingBoundary boundary : replacements)
				put(boundary);
	}

	public synchronized List<TrackingSegment> segments(int lastFrameInclusive) {
		if (lastFrameInclusive < 0)
			return Collections.emptyList();
		List<TrackingSegment> result = new ArrayList<TrackingSegment>();
		int start = 0;
		TrackingBoundary opening = null;
		for (TrackingBoundary boundary : boundaries.values()) {
			if (boundary.getFrame() > lastFrameInclusive)
				break;
			if (boundary.getFrame() <= start)
				continue;
			result.add(new TrackingSegment(start, boundary.getFrame() - 1, opening));
			start = boundary.getFrame();
			opening = boundary;
		}
		result.add(new TrackingSegment(start, lastFrameInclusive, opening));
		return Collections.unmodifiableList(result);
	}
}
