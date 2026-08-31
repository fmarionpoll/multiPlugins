package plugins.fmp.multitools.service.tracking;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import plugins.fmp.multitools.service.tracking.PlanarTransform.Model;

/** Robust hierarchical fit of the simplest planar model supported by landmarks. */
public final class PlanarTransformFitter {
	public static final double DEFAULT_MIN_RMS_IMPROVEMENT_PX = 0.35;
	public static final double DEFAULT_MIN_RELATIVE_IMPROVEMENT = 0.15;

	public PlanarTransformFit fitSimplest(List<LandmarkMatch> matches) {
		if (matches == null || matches.isEmpty())
			throw new IllegalArgumentException("at least one landmark is required");
		PlanarTransformFit accepted = fitRobust(matches, Model.TRANSLATION);
		for (Model candidate : new Model[] { Model.SIMILARITY, Model.AFFINE, Model.PROJECTIVE }) {
			if (matches.size() < minimumPoints(candidate))
				continue;
			PlanarTransformFit next = fitRobust(matches, candidate);
			double improvement = accepted.getRms() - next.getRms();
			double relative = improvement / Math.max(1e-9, accepted.getRms());
			if (improvement >= DEFAULT_MIN_RMS_IMPROVEMENT_PX && relative >= DEFAULT_MIN_RELATIVE_IMPROVEMENT)
				accepted = next;
		}
		return accepted;
	}

	public PlanarTransformFit fitRobust(List<LandmarkMatch> matches, Model model) {
		if (matches == null || matches.size() < minimumPoints(model))
			throw new IllegalArgumentException("not enough landmarks for " + model);
		List<Integer> inliers = new ArrayList<Integer>();
		for (int i = 0; i < matches.size(); i++)
			inliers.add(i);
		PlanarTransform transform = consensusSeed(matches, inliers, model);
		double[] seedErrors = errors(matches, transform, inliers);
		double seedMedian = median(seedErrors);
		double[] seedDeviations = new double[seedErrors.length];
		for (int i = 0; i < seedErrors.length; i++)
			seedDeviations[i] = Math.abs(seedErrors[i] - seedMedian);
		double seedThreshold = seedMedian + Math.max(1.5, 3.0 * median(seedDeviations));
		List<Integer> seedInliers = new ArrayList<Integer>();
		for (int i = 0; i < seedErrors.length; i++)
			if (seedErrors[i] <= seedThreshold)
				seedInliers.add(i);
		if (seedInliers.size() >= minimumPoints(model))
			inliers = seedInliers;
		for (int iteration = 0; iteration < 4; iteration++) {
			transform = fit(matches, inliers, model);
			double[] errors = errors(matches, transform, inliers);
			double median = median(errors);
			double[] deviations = new double[errors.length];
			for (int i = 0; i < errors.length; i++)
				deviations[i] = Math.abs(errors[i] - median);
			double threshold = median + Math.max(1.5, 3.0 * median(deviations));
			List<Integer> kept = new ArrayList<Integer>();
			for (int i = 0; i < inliers.size(); i++)
				if (errors[i] <= threshold)
					kept.add(inliers.get(i));
			if (kept.size() < minimumPoints(model) || kept.size() == inliers.size())
				break;
			inliers = kept;
		}
		transform = fit(matches, inliers, model);
		double[] finalErrors = errors(matches, transform, inliers);
		double sum = 0;
		for (double error : finalErrors)
			sum += error * error;
		return new PlanarTransformFit(transform, Math.sqrt(sum / Math.max(1, finalErrors.length)),
				new ArrayList<Integer>(inliers));
	}

	private PlanarTransform consensusSeed(List<LandmarkMatch> matches, List<Integer> all, Model model) {
		int minimum = minimumPoints(model);
		if (matches.size() <= minimum)
			return fit(matches, all, model);
		PlanarTransform best = fit(matches, all, model);
		double bestScore = median(errors(matches, best, all));
		for (int omitted = 0; omitted < matches.size(); omitted++) {
			List<Integer> subset = new ArrayList<Integer>(all);
			subset.remove(Integer.valueOf(omitted));
			if (subset.size() < minimum)
				continue;
			try {
				PlanarTransform candidate = fit(matches, subset, model);
				double score = median(errors(matches, candidate, all));
				if (score < bestScore) {
					best = candidate;
					bestScore = score;
				}
			} catch (IllegalArgumentException ignored) {
				// Degenerate subset; another leave-one-out seed may still be valid.
			}
		}
		return best;
	}

	private PlanarTransform fit(List<LandmarkMatch> matches, List<Integer> indices, Model model) {
		if (model == Model.TRANSLATION)
			return fitTranslation(matches, indices);
		int parameters = model == Model.SIMILARITY ? 4 : model == Model.AFFINE ? 6 : 8;
		double[][] normal = new double[parameters][parameters + 1];
		for (int index : indices) {
			LandmarkMatch match = matches.get(index);
			Point2D s = match.getSource();
			Point2D t = match.getTarget();
			double x = s.getX(), y = s.getY(), u = t.getX(), v = t.getY();
			double[] rowU;
			double[] rowV;
			if (model == Model.SIMILARITY) {
				rowU = new double[] { x, -y, 1, 0 };
				rowV = new double[] { y, x, 0, 1 };
			} else if (model == Model.AFFINE) {
				rowU = new double[] { x, y, 1, 0, 0, 0 };
				rowV = new double[] { 0, 0, 0, x, y, 1 };
			} else {
				rowU = new double[] { x, y, 1, 0, 0, 0, -x * u, -y * u };
				rowV = new double[] { 0, 0, 0, x, y, 1, -x * v, -y * v };
			}
			accumulate(normal, rowU, u, match.getWeight());
			accumulate(normal, rowV, v, match.getWeight());
		}
		double[] p = solve(normal);
		double[][] m;
		if (model == Model.SIMILARITY)
			m = new double[][] { { p[0], -p[1], p[2] }, { p[1], p[0], p[3] }, { 0, 0, 1 } };
		else if (model == Model.AFFINE)
			m = new double[][] { { p[0], p[1], p[2] }, { p[3], p[4], p[5] }, { 0, 0, 1 } };
		else
			m = new double[][] { { p[0], p[1], p[2] }, { p[3], p[4], p[5] }, { p[6], p[7], 1 } };
		return new PlanarTransform(model, m);
	}

	private PlanarTransform fitTranslation(List<LandmarkMatch> matches, List<Integer> indices) {
		double dx = 0, dy = 0, weight = 0;
		for (int index : indices) {
			LandmarkMatch match = matches.get(index);
			Point2D s = match.getSource(), t = match.getTarget();
			dx += match.getWeight() * (t.getX() - s.getX());
			dy += match.getWeight() * (t.getY() - s.getY());
			weight += match.getWeight();
		}
		return new PlanarTransform(Model.TRANSLATION,
				new double[][] { { 1, 0, dx / weight }, { 0, 1, dy / weight }, { 0, 0, 1 } });
	}

	private static void accumulate(double[][] normal, double[] row, double value, double weight) {
		for (int r = 0; r < row.length; r++) {
			for (int c = 0; c < row.length; c++)
				normal[r][c] += weight * row[r] * row[c];
			normal[r][row.length] += weight * row[r] * value;
		}
	}

	private static double[] solve(double[][] augmented) {
		int n = augmented.length;
		double[][] a = new double[n][n + 1];
		for (int r = 0; r < n; r++)
			System.arraycopy(augmented[r], 0, a[r], 0, n + 1);
		for (int col = 0; col < n; col++) {
			int pivot = col;
			for (int r = col + 1; r < n; r++)
				if (Math.abs(a[r][col]) > Math.abs(a[pivot][col]))
					pivot = r;
			if (Math.abs(a[pivot][col]) < 1e-10)
				throw new IllegalArgumentException("degenerate landmark geometry");
			double[] tmp = a[col]; a[col] = a[pivot]; a[pivot] = tmp;
			double divisor = a[col][col];
			for (int c = col; c <= n; c++) a[col][c] /= divisor;
			for (int r = 0; r < n; r++) {
				if (r == col) continue;
				double factor = a[r][col];
				for (int c = col; c <= n; c++) a[r][c] -= factor * a[col][c];
			}
		}
		double[] result = new double[n];
		for (int i = 0; i < n; i++) result[i] = a[i][n];
		return result;
	}

	private static double[] errors(List<LandmarkMatch> matches, PlanarTransform transform, List<Integer> indices) {
		double[] errors = new double[indices.size()];
		for (int i = 0; i < indices.size(); i++) {
			LandmarkMatch match = matches.get(indices.get(i));
			Point2D predicted = transform.transform(match.getSource());
			errors[i] = predicted.distance(match.getTarget());
		}
		return errors;
	}

	private static double median(double[] values) {
		if (values.length == 0) return 0;
		double[] copy = values.clone();
		Arrays.sort(copy);
		return copy.length % 2 == 1 ? copy[copy.length / 2]
				: (copy[copy.length / 2 - 1] + copy[copy.length / 2]) / 2.0;
	}

	private static int minimumPoints(Model model) {
		switch (model) {
		case TRANSLATION: return 1;
		case SIMILARITY: return 2;
		case AFFINE: return 3;
		case PROJECTIVE: return 4;
		default: throw new IllegalArgumentException(model.name());
		}
	}
}
