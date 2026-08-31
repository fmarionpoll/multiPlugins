package plugins.fmp.multitools.service.tracking;

import java.awt.geom.Point2D;

/** Immutable 3x3 planar transform, normalized so m[2][2] is one. */
public final class PlanarTransform {
	public enum Model { TRANSLATION, SIMILARITY, AFFINE, PROJECTIVE }

	private final Model model;
	private final double[][] matrix;

	PlanarTransform(Model model, double[][] matrix) {
		this.model = model;
		this.matrix = new double[3][3];
		for (int r = 0; r < 3; r++)
			System.arraycopy(matrix[r], 0, this.matrix[r], 0, 3);
	}

	public Model getModel() { return model; }

	public Point2D transform(Point2D point) {
		double x = point.getX();
		double y = point.getY();
		double w = matrix[2][0] * x + matrix[2][1] * y + matrix[2][2];
		if (Math.abs(w) < 1e-12)
			return new Point2D.Double(Double.NaN, Double.NaN);
		return new Point2D.Double((matrix[0][0] * x + matrix[0][1] * y + matrix[0][2]) / w,
				(matrix[1][0] * x + matrix[1][1] * y + matrix[1][2]) / w);
	}

	public double[][] getMatrix() {
		double[][] copy = new double[3][3];
		for (int r = 0; r < 3; r++)
			System.arraycopy(matrix[r], 0, copy[r], 0, 3);
		return copy;
	}
}
