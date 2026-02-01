package plugins.fmp.multitools.tools;

public class SavitzkyGolayFilter {

	public static double[] smooth(double[] data, int windowSize, int polynomialOrder) {
		if (data == null || data.length == 0)
			return data;
		if (windowSize < 3 || windowSize % 2 == 0)
			windowSize = 5;
		if (polynomialOrder >= windowSize)
			polynomialOrder = windowSize - 1;

		double[] coeffs = computeCoefficients(windowSize, polynomialOrder);
		double[] result = new double[data.length];
		int half = windowSize / 2;

		for (int i = 0; i < data.length; i++) {
			double sum = 0;
			for (int j = -half; j <= half; j++) {
				int idx = i + j;
				if (idx < 0)
					idx = 0;
				else if (idx >= data.length)
					idx = data.length - 1;
				sum += coeffs[j + half] * data[idx];
			}
			result[i] = sum;
		}
		return result;
	}

	private static double[] computeCoefficients(int windowSize, int polyOrder) {
		int half = windowSize / 2;
		double[] x = new double[windowSize];
		for (int i = 0; i < windowSize; i++)
			x[i] = i - half;

		int rows = windowSize;
		int cols = polyOrder + 1;
		double[][] J = new double[rows][cols];
		for (int i = 0; i < rows; i++) {
			double v = 1;
			for (int j = 0; j < cols; j++) {
				J[i][j] = v;
				v *= x[i];
			}
		}

		double[][] Jt = transpose(J, rows, cols);
		double[][] JtJ = matMul(Jt, cols, rows, J, rows, cols);
		double[][] JtJinv = invert(JtJ, cols);
		double[][] pinv = matMul(JtJinv, cols, cols, Jt, cols, rows);

		double[] coeffs = new double[windowSize];
		for (int j = 0; j < windowSize; j++)
			coeffs[j] = pinv[0][j];
		return coeffs;
	}

	private static double[][] transpose(double[][] a, int rows, int cols) {
		double[][] t = new double[cols][rows];
		for (int i = 0; i < rows; i++)
			for (int j = 0; j < cols; j++)
				t[j][i] = a[i][j];
		return t;
	}

	private static double[][] matMul(double[][] a, int ra, int ca, double[][] b, int rb, int cb) {
		if (ca != rb)
			throw new IllegalArgumentException();
		double[][] c = new double[ra][cb];
		for (int i = 0; i < ra; i++)
			for (int j = 0; j < cb; j++) {
				double sum = 0;
				for (int k = 0; k < ca; k++)
					sum += a[i][k] * b[k][j];
				c[i][j] = sum;
			}
		return c;
	}

	private static double[][] invert(double[][] a, int n) {
		double[][] aug = new double[n][2 * n];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++)
				aug[i][j] = a[i][j];
			aug[i][n + i] = 1;
		}
		for (int col = 0; col < n; col++) {
			int maxRow = col;
			for (int row = col + 1; row < n; row++)
				if (Math.abs(aug[row][col]) > Math.abs(aug[maxRow][col]))
					maxRow = row;
			double[] tmp = aug[col];
			aug[col] = aug[maxRow];
			aug[maxRow] = tmp;
			double pivot = aug[col][col];
			if (Math.abs(pivot) < 1e-12)
				return identity(n);
			for (int j = 0; j < 2 * n; j++)
				aug[col][j] /= pivot;
			for (int row = 0; row < n; row++) {
				if (row != col) {
					double factor = aug[row][col];
					for (int j = 0; j < 2 * n; j++)
						aug[row][j] -= factor * aug[col][j];
				}
			}
		}
		double[][] inv = new double[n][n];
		for (int i = 0; i < n; i++)
			for (int j = 0; j < n; j++)
				inv[i][j] = aug[i][n + j];
		return inv;
	}

	private static double[][] identity(int n) {
		double[][] i = new double[n][n];
		for (int k = 0; k < n; k++)
			i[k][k] = 1;
		return i;
	}
}
