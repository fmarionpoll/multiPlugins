package plugins.fmp.multitools.experiment.capillary.geometry;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

/**
 * CODEX Coordinated green observation corridor and blue physical line for one
 * phase.
 */
public final class CapillaryPhaseGeometry {
	private final long phaseStart;
	private final Line2D greenCorridor;
	private final Line2D bluePhysical;
	private final double widthPixels;

	public CapillaryPhaseGeometry(long phaseStart, Line2D greenCorridor, Line2D bluePhysical) {
		this(phaseStart, greenCorridor, bluePhysical, Double.NaN);
	}

	public CapillaryPhaseGeometry(long phaseStart, Line2D greenCorridor, Line2D bluePhysical, double widthPixels) {
		this.phaseStart = phaseStart;
		this.greenCorridor = copy(greenCorridor);
		this.bluePhysical = copy(bluePhysical);
		this.widthPixels = Double.isFinite(widthPixels) && widthPixels > 0. ? widthPixels : Double.NaN;
	}

	public double getWidthPixels() {
		return widthPixels;
	}

	/** Four wall corners in perimeter order, or null when width is unknown. */
	public Point2D[] getPhysicalCorners() {
		if (bluePhysical == null || !Double.isFinite(widthPixels))
			return null;
		double length = bluePhysical.getP1().distance(bluePhysical.getP2());
		if (!(length > 0.))
			return null;
		double nx = -(bluePhysical.getY2() - bluePhysical.getY1()) / length * widthPixels / 2.;
		double ny = (bluePhysical.getX2() - bluePhysical.getX1()) / length * widthPixels / 2.;
		return new Point2D[] {
				new Point2D.Double(bluePhysical.getX1() + nx, bluePhysical.getY1() + ny),
				new Point2D.Double(bluePhysical.getX2() + nx, bluePhysical.getY2() + ny),
				new Point2D.Double(bluePhysical.getX2() - nx, bluePhysical.getY2() - ny),
				new Point2D.Double(bluePhysical.getX1() - nx, bluePhysical.getY1() - ny) };
	}

	public long getPhaseStart() {
		return phaseStart;
	}

	public Line2D getGreenCorridor() {
		return copy(greenCorridor);
	}

	public Line2D getBluePhysical() {
		return copy(bluePhysical);
	}

	static Line2D copy(Line2D line) {
		return line == null ? null : new Line2D.Double(line.getP1(), line.getP2());
	}
}
