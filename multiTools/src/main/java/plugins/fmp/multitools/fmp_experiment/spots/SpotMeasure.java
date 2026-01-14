package plugins.fmp.multitools.fmp_experiment.spots;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class SpotMeasure {

	// === CONSTANTS ===
	private static final double DEFAULT_FACTOR = 1.0;

	// === CORE FIELDS ===
	private double[] values;
	private int[] isPresent;
	private double factor;
	private int measuredFromNSpots = 1;
	private String name;

	private SpotLevel2D spotLevel2D = null;

	// === CONSTRUCTORS ===

	/**
	 * Creates a new SpotMeasure with the specified name.
	 * 
	 * @param name the measure name
	 * @throws IllegalArgumentException if name is null
	 */
	public SpotMeasure(String name) {
		this.name = Objects.requireNonNull(name, "Name cannot be null");
		this.factor = DEFAULT_FACTOR;
		this.spotLevel2D = new SpotLevel2D(name);
	}

	// === CORE OPERATIONS ===

	public void copyMeasures(SpotMeasure source) {
		if (source == null) {
			return;
		}
		if (source.values != null && source.values.length > 0) {
			this.values = Arrays.copyOf(source.values, source.values.length);
		}
		if (source.isPresent != null && source.isPresent.length > 0) {
			this.isPresent = Arrays.copyOf(source.isPresent, source.isPresent.length);
		}
	}

	public void addMeasures(SpotMeasure source) {
		if (source == null) {
			return;
		}
		if (source.values != null && source.values.length > 0) {
			addValues(source.values);
		}
		if (source.isPresent != null && source.isPresent.length > 0) {
			addPresence(source.isPresent);
		}
		measuredFromNSpots++;
	}

	/**
	 * Computes PI (Performance Index) from two measures.
	 * 
	 * @param measure1 the first measure
	 * @param measure2 the second measure
	 */
	public void computePI(SpotMeasure measure1, SpotMeasure measure2) {
		if (measure1 == null || measure2 == null) {
			return;
		}
		// assume normalized - if not, compute it
		if (measure1.values != null && measure1.values.length > 0 && measure2.values != null
				&& measure2.values.length > 0) {
			this.values = new double[measure1.values.length];
			double max1 = measure1.getMaximumValue();
			double max2 = measure2.getMaximumValue();
			for (int i = 0; i < values.length; i++) {
				double value1 = measure1.values[i] / max1;
				double value2 = measure2.values[i] / max2;
				double sum = value1 + value2;
				this.values[i] = sum > 0 ? (value1 - value2) / sum : 0;
			}
		}
	}

	/**
	 * Computes sum from two measures.
	 * 
	 * @param measure1 the first measure
	 * @param measure2 the second measure
	 */
	public void computeSUM(SpotMeasure measure1, int n1, SpotMeasure measure2, int n2) {
		if (measure1 == null || measure2 == null) {
			return;
		}

		if (measure1.values != null && measure1.values.length > 0 && measure2.values != null
				&& measure2.values.length > 0) {
			this.values = new double[measure1.values.length];
			for (int i = 0; i < measure1.values.length; i++) {
				this.values[i] = measure1.values[i] / (double) n1 + measure2.values[i] / (double) n2;
			}
		}
	}

	public double getMaximumValue() {
		double maximum = 0.;
		for (int i = 0; i < values.length; i++) {
			if (this.values[i] > maximum)
				maximum = this.values[i];
		}
		return maximum;
	}

	public void normalizeValuesTo(double norm) {
		for (int i = 0; i < values.length; i++) {
			values[i] = values[i] / norm;
		}
	}

	public void normalizeValues() {
		double maximum = getMaximumValue();
		normalizeValuesTo(maximum);
	}

	/**
	 * Combines presence indicators from two measures.
	 * 
	 * @param measure1 the first measure
	 * @param measure2 the second measure
	 */
	public void combineIsPresent(SpotMeasure measure1, int n1, SpotMeasure measure2, int n2) {
		if (measure1 == null || measure2 == null) {
			return;
		}

		if (isPresent != null && measure1.isPresent != null && measure2.isPresent != null) {
			if (isPresent.length != measure1.isPresent.length) {
				isPresent = new int[measure1.isPresent.length];
			}
			for (int i = 0; i < isPresent.length; i++) {
				isPresent[i] = measure1.isPresent[i] + measure2.isPresent[i];
			}
		}
	}

	// === NAME MANAGEMENT ===

	/**
	 * Gets the name of this measure.
	 * 
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the name of this measure.
	 * 
	 * @param name the name
	 */
	public void setName(String name) {
		this.name = Objects.requireNonNull(name, "Name cannot be null");
	}

	// === DATA ACCESS ===

	public int getCount() {
		if (values == null)
			return 0;
		return values.length;
	}

	public double[] getValues() {
		return values;
	}

	public void setValues(double[] values) {
		this.values = values;
	}

	public void setValueAt(int index, double value) {
		if (values != null && index >= 0 && index < values.length) {
			values[index] = value;
		}
	}

	public double getValueAt(int index) {
		return values[index];
	}

	public int[] getIsPresent() {
		return isPresent;
	}

	public void setIsPresent(int[] isPresent) {
		this.isPresent = isPresent;
	}

	public int getIsPresentAt(int index) {
		return isPresent[index];
	}

	public void setIsPresentAt(int index, int value) {
		if (isPresent != null && index >= 0 && index < isPresent.length) {
			isPresent[index] = value;
		}
	}

	public double getFactor() {
		return factor;
	}

	public void setFactor(double factor) {
		this.factor = factor;
	}

	public int getMeasuredFromNSpots() {
		return measuredFromNSpots;
	}

	public void setMeasuredFromNSpots(int n) {
		this.measuredFromNSpots = n;
	}

	// == interactions with Level2D ====

	public SpotLevel2D getSpotLevel2D() {
		return spotLevel2D;
	}

	public void transferValuesToLevel2D() {
		spotLevel2D.transferValues(values);
	}

	public void transferIsPresentToLevel2D() {
		spotLevel2D.transferIsPresent(isPresent);
	}

	// === PRIVATE HELPER METHODS ===

	private void addValues(double[] sourceValues) {
		if (this.values == null) {
			this.values = new double[sourceValues.length];
		}

		for (int i = 0; i < sourceValues.length; i++) {
			this.values[i] += sourceValues[i];
		}
	}

	private void addPresence(int[] sourcePresence) {
		if (this.isPresent == null) {
			this.isPresent = new int[sourcePresence.length];
		}

		for (int i = 0; i < sourcePresence.length; i++) {
			this.isPresent[i] += sourcePresence[i];
		}
	}

	// === UTILITY METHODS ===

	public List<Double> getValuesAsSubsampledList(long seriesBinMs, long outputBinMs) {
		if (values == null || values.length == 0) {
			return new ArrayList<>();
		}
		long maxMs = (values.length - 1) * seriesBinMs;
		long npoints = (maxMs / outputBinMs) + 1;
		List<Double> result = new ArrayList<>();
		for (long i = 0; i < npoints; i++) {
			long timeMs = i * outputBinMs;
			int index = (int) (timeMs / seriesBinMs);
			if (index < values.length) {
				result.add(values[index]);
			} else {
				result.add(0.0);
			}
		}
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null || getClass() != obj.getClass())
			return false;
		SpotMeasure other = (SpotMeasure) obj;
		return Objects.equals(name, other.name) && Double.compare(factor, other.factor) == 0
				&& Arrays.equals(values, other.values) && Arrays.equals(isPresent, other.isPresent);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, factor, Arrays.hashCode(values), Arrays.hashCode(isPresent));
	}

	@Override
	public String toString() {
		return String.format("SpotMeasure{name='%s', factor=%.2f, hasValues=%b, hasPresence=%b}", name, factor,
				values != null, isPresent != null);
	}

	// === MEDIAN PROCESSING ===

	/**
	 * Builds running median.
	 * 
	 * @param span    the span
	 * @param yvalues the Y values
	 */
	public void buildRunningMedianFromValuesArray(int span, double[] yvalues) {
		if (yvalues == null || yvalues.length == 0) {
			return;
		}

		int npoints = yvalues.length;

		for (int i = 0; i < npoints; i++) {
			int start = Math.max(0, i - span / 2);
			int end = Math.min(npoints - 1, i + span / 2);
			int count = end - start + 1;

			double[] window = new double[count];
			for (int j = 0; j < count; j++) {
				window[j] = yvalues[start + j];
			}

			Arrays.sort(window);
			values[i] = window[count / 2];
		}

	}

	// === CSV EXPORT/IMPORT ===

	/**
	 * Exports Y data to CSV row.
	 * 
	 * @param sbf       the string buffer
	 * @param separator the separator
	 * @return true if successful
	 */
	public boolean exportYDataToCsv(StringBuilder sbf, String separator) {
		if (values == null || values.length < 1) {
			return false;
		}
		sbf.append(values.length);
		sbf.append(separator);
		for (int i = 0; i < values.length; i++) {
			if (i > 0) {
				sbf.append(separator);
			}
			sbf.append(values[i]);
		}

		return true;
	}

	/**
	 * Imports XY data from CSV row.
	 * 
	 * @param data    the CSV data
	 * @param startAt the starting index
	 * @return true if successful
	 */
	public boolean importXYDataFromCsv(String[] data, int startAt) {
		if (data == null || data.length < startAt + 2) {
			return false;
		}

		try {
			int npoints = (data.length - startAt) / 2;
			if (values == null || values.length != npoints)
				values = new double[npoints];

			for (int i = 0; i < npoints; i++) {
				values[i] = Double.parseDouble(data[startAt + i * 2 + 1]);
			}

			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	/**
	 * Imports Y data from CSV row.
	 * 
	 * @param data    the CSV data
	 * @param startAt the starting index
	 * @return true if successful
	 */
	public boolean importYDataFromCsv(String[] data, int startAt) {
		if (data == null || data.length < startAt + 1) {
			return false;
		}

		try {
			int npoints = data.length - startAt;
			if (values == null || values.length != npoints)
				values = new double[npoints];

			for (int i = 0; i < npoints; i++) {
				values[i] = Double.parseDouble(data[startAt + i]);
			}
			return true;

		} catch (NumberFormatException e) {
			return false;
		}
	}

}
