package plugins.fmp.multitools.service.tracking;

import java.awt.geom.Point2D;

/** One capillary landmark correspondence between two camera frames. */
public final class LandmarkMatch {
	private final Point2D source;
	private final Point2D target;
	private final double weight;

	public LandmarkMatch(Point2D source, Point2D target, double weight) {
		if (source == null || target == null)
			throw new IllegalArgumentException("landmark coordinates are required");
		this.source = (Point2D) source.clone();
		this.target = (Point2D) target.clone();
		this.weight = weight > 0 && Double.isFinite(weight) ? weight : 1.0;
	}

	public Point2D getSource() { return (Point2D) source.clone(); }
	public Point2D getTarget() { return (Point2D) target.clone(); }
	public double getWeight() { return weight; }
}
