package plugins.fmp.multitools.experiment.capillary.geometry;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Time-dependent physical capillary geometry. Green pose is kept by AlongT;
 * this model stores blue keyframes and capillary-wide corridor extension ratios.
 */
public final class CapillaryPhaseGeometryModel {
	private final NavigableMap<Long, Line2D> blueByPhase = new TreeMap<Long, Line2D>();
	private CorridorExtensionRatios extensions;

	public synchronized boolean isInitialized() {
		return extensions != null && !blueByPhase.isEmpty();
	}

	public synchronized CorridorExtensionRatios getExtensions() { return extensions; }

	public synchronized void setExtensions(CorridorExtensionRatios extensions) {
		if (extensions == null)
			throw new IllegalArgumentException("extensions are required");
		this.extensions = extensions;
	}

	public synchronized void initialize(long phaseStart, Line2D green, Line2D blue) {
		if (green == null || blue == null || blue.getP1().distance(blue.getP2()) <= 0)
			throw new IllegalArgumentException("non-empty green and blue lines are required");
		Line2D orientedBlue = orientLike(blue, green);
		extensions = inferExtensions(green, orientedBlue);
		blueByPhase.put(phaseStart, CapillaryPhaseGeometry.copy(orientedBlue));
	}

	public synchronized void putBlue(long phaseStart, Line2D blue) {
		if (blue == null || blue.getP1().distance(blue.getP2()) <= 0)
			throw new IllegalArgumentException("a non-empty blue line is required");
		blueByPhase.put(phaseStart, CapillaryPhaseGeometry.copy(blue));
	}

	public synchronized Line2D getBlueAt(long frame) {
		Map.Entry<Long, Line2D> entry = blueByPhase.floorEntry(frame);
		if (entry == null)
			entry = blueByPhase.firstEntry();
		return entry == null ? null : CapillaryPhaseGeometry.copy(entry.getValue());
	}

	public synchronized Line2D getBlueStartingAt(long phaseStart) {
		return CapillaryPhaseGeometry.copy(blueByPhase.get(phaseStart));
	}

	public synchronized Map<Long, Line2D> getBlueKeyframes() {
		Map<Long, Line2D> copy = new LinkedHashMap<Long, Line2D>();
		for (Map.Entry<Long, Line2D> entry : blueByPhase.entrySet())
			copy.put(entry.getKey(), CapillaryPhaseGeometry.copy(entry.getValue()));
		return Collections.unmodifiableMap(copy);
	}

	public synchronized void clear() {
		blueByPhase.clear();
		extensions = null;
	}

	/** Phase-only pose change: blue length and shared extensions are preserved. */
	public synchronized Line2D alignPhase(long phaseStart, Line2D alignedGreenAxis) {
		ensureInitialized();
		Line2D oldBlue = getBlueAt(phaseStart);
		double blueLength = length(oldBlue);
		Vector axis = vector(alignedGreenAxis);
		Point2D greenMid = midpoint(alignedGreenAxis);
		double midpointOffset = (extensions.getLower() - extensions.getUpper()) * blueLength / 2.0;
		Point2D blueMid = new Point2D.Double(greenMid.getX() - midpointOffset * axis.x,
				greenMid.getY() - midpointOffset * axis.y);
		Line2D alignedBlue = centeredLine(blueMid, axis, blueLength);
		blueByPhase.put(phaseStart, alignedBlue);
		return CapillaryPhaseGeometry.copy(alignedBlue);
	}

	/** Global contextual extent change; blue keyframes are deliberately untouched. */
	public synchronized void changeExtensions(double upperDelta, double lowerDelta) {
		ensureInitialized();
		extensions = extensions.plus(upperDelta, lowerDelta);
	}

	/** Rebuilds the green corridor for a blue phase using the shared ratios. */
	public synchronized Line2D greenForBlue(Line2D blue) {
		ensureInitialized();
		Vector axis = vector(blue);
		double length = length(blue);
		Point2D p1 = blue.getP1(), p2 = blue.getP2();
		return new Line2D.Double(p1.getX() - extensions.getUpper() * length * axis.x,
				p1.getY() - extensions.getUpper() * length * axis.y,
				p2.getX() + extensions.getLower() * length * axis.x,
				p2.getY() + extensions.getLower() * length * axis.y);
	}

	/**
	 * Combined manual edit. Blue length is preserved, its midpoint is projected to
	 * the edited green axis, and the resulting extensions become capillary-wide.
	 */
	public synchronized Line2D applyManualGreenEdit(long phaseStart, Line2D editedGreen) {
		ensureInitialized();
		Line2D oldBlue = getBlueAt(phaseStart);
		double blueLength = length(oldBlue);
		Vector axis = vector(editedGreen);
		Point2D projectedMid = projectOntoLine(midpoint(oldBlue), editedGreen, axis);
		Line2D newBlue = centeredLine(projectedMid, axis, blueLength);
		extensions = inferExtensions(editedGreen, newBlue);
		blueByPhase.put(phaseStart, newBlue);
		return CapillaryPhaseGeometry.copy(newBlue);
	}

	private void ensureInitialized() {
		if (!isInitialized())
			throw new IllegalStateException("capillary phase geometry is not initialized");
	}

	private static CorridorExtensionRatios inferExtensions(Line2D green, Line2D blue) {
		Line2D orientedBlue = orientLike(blue, green);
		Vector axis = vector(green);
		double blueLength = length(orientedBlue);
		double upper = signedAlong(green.getP1(), orientedBlue.getP1(), axis) / blueLength;
		double lower = signedAlong(orientedBlue.getP2(), green.getP2(), axis) / blueLength;
		return new CorridorExtensionRatios(Math.max(0, upper), Math.max(0, lower));
	}

	private static Line2D orientLike(Line2D line, Line2D reference) {
		Vector ref = vector(reference);
		Vector candidate = vector(line);
		return ref.x * candidate.x + ref.y * candidate.y >= 0 ? CapillaryPhaseGeometry.copy(line)
				: new Line2D.Double(line.getP2(), line.getP1());
	}

	private static Point2D projectOntoLine(Point2D point, Line2D line, Vector axis) {
		double distance = signedAlong(line.getP1(), point, axis);
		return new Point2D.Double(line.getX1() + distance * axis.x, line.getY1() + distance * axis.y);
	}

	private static Line2D centeredLine(Point2D center, Vector axis, double length) {
		double half = length / 2.0;
		return new Line2D.Double(center.getX() - half * axis.x, center.getY() - half * axis.y,
				center.getX() + half * axis.x, center.getY() + half * axis.y);
	}

	private static Point2D midpoint(Line2D line) {
		return new Point2D.Double((line.getX1() + line.getX2()) / 2.0, (line.getY1() + line.getY2()) / 2.0);
	}

	private static double length(Line2D line) { return line.getP1().distance(line.getP2()); }

	private static double signedAlong(Point2D from, Point2D to, Vector axis) {
		return (to.getX() - from.getX()) * axis.x + (to.getY() - from.getY()) * axis.y;
	}

	private static Vector vector(Line2D line) {
		double dx = line.getX2() - line.getX1(), dy = line.getY2() - line.getY1();
		double length = Math.hypot(dx, dy);
		if (length <= 0)
			throw new IllegalArgumentException("line must have a direction");
		return new Vector(dx / length, dy / length);
	}

	private static final class Vector {
		final double x, y;
		Vector(double x, double y) { this.x = x; this.y = y; }
	}
}
