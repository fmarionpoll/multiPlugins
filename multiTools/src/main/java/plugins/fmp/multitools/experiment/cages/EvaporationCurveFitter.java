package plugins.fmp.multitools.experiment.cages;

import plugins.fmp.multitools.tools.polyline.Level2D;

/**
 * Fits a t0-zeroed evaporation series to Y(t) = A * (1 - exp(-t/tau)).
 * Unknown delay before the first sample is absorbed into A. Returns null when
 * the series is too short or the fit is degenerate so callers can keep the raw
 * average.
 */
public final class EvaporationCurveFitter {

	private static final int MIN_POINTS = 8;
	private static final int HUBER_ROUNDS = 2;
	private static final double HUBER_K = 2.5;

	private EvaporationCurveFitter() {
	}

	/**
	 * @param yAlreadyZeroAtT0 series with Y(0) approximately 0 (column index = t)
	 * @return model Level2D, or null on failure
	 */
	public static Level2D fit(double[] yAlreadyZeroAtT0) {
		if (yAlreadyZeroAtT0 == null || yAlreadyZeroAtT0.length < MIN_POINTS)
			return null;
		int n = yAlreadyZeroAtT0.length;
		double[] y = yAlreadyZeroAtT0;
		double[] weights = new double[n];
		for (int i = 0; i < n; i++)
			weights[i] = 1.0;

		double bestTau = Double.NaN;
		double bestA = Double.NaN;
		double bestCost = Double.POSITIVE_INFINITY;

		double tauMin = Math.max(2.0, n / 50.0);
		double tauMax = Math.max(tauMin * 2.0, 5.0 * n);

		for (int round = 0; round <= HUBER_ROUNDS; round++) {
			bestTau = Double.NaN;
			bestA = Double.NaN;
			bestCost = Double.POSITIVE_INFINITY;

			double lo = tauMin;
			double hi = tauMax;
			for (int refine = 0; refine < 3; refine++) {
				int steps = refine == 0 ? 40 : 20;
				double step = (hi - lo) / steps;
				if (!(step > 0) || !Double.isFinite(step))
					break;
				for (int s = 0; s <= steps; s++) {
					double tau = lo + s * step;
					if (!(tau > 0) || !Double.isFinite(tau))
						continue;
					double a = solveA(y, weights, tau);
					if (!Double.isFinite(a))
						continue;
					double cost = weightedSse(y, weights, a, tau);
					if (cost < bestCost) {
						bestCost = cost;
						bestTau = tau;
						bestA = a;
					}
				}
				if (!Double.isFinite(bestTau))
					break;
				lo = Math.max(tauMin, bestTau - 2.0 * step);
				hi = Math.min(tauMax, bestTau + 2.0 * step);
			}

			if (!Double.isFinite(bestTau) || !Double.isFinite(bestA))
				return null;

			if (round < HUBER_ROUNDS)
				updateHuberWeights(y, weights, bestA, bestTau);
		}

		if (!Double.isFinite(bestTau) || !Double.isFinite(bestA))
			return null;
		if (Math.abs(bestA) < 1e-12 && residualRms(y) > 1e-6)
			return null;

		double[] x = new double[n];
		double[] model = new double[n];
		for (int t = 0; t < n; t++) {
			x[t] = t;
			model[t] = bestA * (1.0 - Math.exp(-t / bestTau));
		}
		return new Level2D(x, model, n);
	}

	/** Fit a t0-zeroed Level2D; returns null on failure. */
	public static Level2D fit(Level2D series) {
		if (series == null || series.npoints < MIN_POINTS || series.ypoints == null)
			return null;
		double[] y = new double[series.npoints];
		System.arraycopy(series.ypoints, 0, y, 0, series.npoints);
		return fit(y);
	}

	private static double solveA(double[] y, double[] w, double tau) {
		double num = 0;
		double den = 0;
		for (int t = 0; t < y.length; t++) {
			if (!Double.isFinite(y[t]) || !(w[t] > 0))
				continue;
			double u = 1.0 - Math.exp(-t / tau);
			num += w[t] * y[t] * u;
			den += w[t] * u * u;
		}
		if (!(den > 1e-18))
			return Double.NaN;
		return num / den;
	}

	private static double weightedSse(double[] y, double[] w, double a, double tau) {
		double sse = 0;
		for (int t = 0; t < y.length; t++) {
			if (!Double.isFinite(y[t]) || !(w[t] > 0))
				continue;
			double pred = a * (1.0 - Math.exp(-t / tau));
			double r = y[t] - pred;
			sse += w[t] * r * r;
		}
		return sse;
	}

	private static void updateHuberWeights(double[] y, double[] w, double a, double tau) {
		double sumSq = 0;
		int n = 0;
		for (int t = 0; t < y.length; t++) {
			if (!Double.isFinite(y[t]))
				continue;
			double r = y[t] - a * (1.0 - Math.exp(-t / tau));
			sumSq += r * r;
			n++;
		}
		if (n < 2)
			return;
		double rms = Math.sqrt(sumSq / n);
		double delta = HUBER_K * Math.max(rms, 1e-6);
		for (int t = 0; t < y.length; t++) {
			if (!Double.isFinite(y[t])) {
				w[t] = 0;
				continue;
			}
			double r = Math.abs(y[t] - a * (1.0 - Math.exp(-t / tau)));
			w[t] = r <= delta ? 1.0 : delta / r;
		}
	}

	private static double residualRms(double[] y) {
		double sum = 0;
		int n = 0;
		for (double v : y) {
			if (!Double.isFinite(v))
				continue;
			sum += v * v;
			n++;
		}
		return n > 0 ? Math.sqrt(sum / n) : 0;
	}
}