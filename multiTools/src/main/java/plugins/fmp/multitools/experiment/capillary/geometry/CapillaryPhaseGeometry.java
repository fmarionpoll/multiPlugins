package plugins.fmp.multitools.experiment.capillary.geometry;

import java.awt.geom.Line2D;

/** Coordinated green observation corridor and blue physical line for one phase. */
public final class CapillaryPhaseGeometry {
	private final long phaseStart;
	private final Line2D greenCorridor;
	private final Line2D bluePhysical;

	public CapillaryPhaseGeometry(long phaseStart, Line2D greenCorridor, Line2D bluePhysical) {
		this.phaseStart = phaseStart;
		this.greenCorridor = copy(greenCorridor);
		this.bluePhysical = copy(bluePhysical);
	}

	public long getPhaseStart() { return phaseStart; }
	public Line2D getGreenCorridor() { return copy(greenCorridor); }
	public Line2D getBluePhysical() { return copy(bluePhysical); }

	static Line2D copy(Line2D line) {
		return line == null ? null : new Line2D.Double(line.getP1(), line.getP2());
	}
}
