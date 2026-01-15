package plugins.fmp.multitools.tools;

/**
 * Utility class for tracking minimum and maximum double values. This class
 * provides a convenient way to maintain running min/max values and can be used
 * for calculating ranges, bounds checking, and data analysis.
 * 
 * <p>
 * MaxMinDouble is commonly used in image processing and data analysis to track
 * the extrema of datasets, calculate ranges for normalization, and provide
 * bounds information for visualization.
 * </p>
 * 
 * <p>
 * Usage example:
 * 
 * <pre>
 * MaxMinDouble range = new MaxMinDouble();
 * range.getMaxMin(10.5);
 * range.getMaxMin(5.2);
 * range.getMaxMin(15.8);
 * 
 * System.out.println("Range: [" + range.min + ", " + range.max + "]");
 * // Output: Range: [5.2, 15.8]
 * </pre>
 * 
 * @author MultiSPOTS96
 */
public class MaxMinDouble {

	/** Maximum value tracked */
	private double max = 0;

	/** Minimum value tracked */
	private double min = 0;

	/** Flag indicating if any values have been added */
	private boolean hasValues = false;

	/**
	 * Creates a new MaxMinDouble with default values. The min and max will be set
	 * to 0 until values are added.
	 */
	public MaxMinDouble() {
		this.max = 0;
		this.min = 0;
		this.hasValues = false;
	}

	/**
	 * Creates a new MaxMinDouble with the specified values. The values will be
	 * automatically ordered (min <= max).
	 * 
	 * @param val1 the first value
	 * @param val2 the second value
	 */
	public MaxMinDouble(double val1, double val2) {
		if (val1 >= val2) {
			this.max = val1;
			this.min = val2;
		} else {
			this.min = val1;
			this.max = val2;
		}
		this.hasValues = true;
	}

	/**
	 * Updates the min/max values with two new values.
	 * 
	 * @param value1 the first value to consider
	 * @param value2 the second value to consider
	 * @return this MaxMinDouble for method chaining
	 */
	public MaxMinDouble getMaxMin(double value1, double value2) {
		getMaxMin(value1);
		getMaxMin(value2);
		return this;
	}

	/**
	 * Updates the min/max values with the values from another MaxMinDouble.
	 * 
	 * @param val the MaxMinDouble containing values to consider
	 * @return this MaxMinDouble for method chaining
	 * @throws IllegalArgumentException if val is null
	 */
	public MaxMinDouble getMaxMin(MaxMinDouble val) {
		if (val == null) {
			throw new IllegalArgumentException("MaxMinDouble cannot be null");
		}

		getMaxMin(val.min);
		getMaxMin(val.max);
		return this;
	}

	/**
	 * Updates the min/max values with a new value.
	 * 
	 * @param value the value to consider
	 * @return this MaxMinDouble for method chaining
	 */
	public MaxMinDouble getMaxMin(double value) {
		if (!hasValues) {
			this.max = value;
			this.min = value;
			this.hasValues = true;
		} else {
			if (value > max) {
				this.max = value;
			}
			if (value < min) {
				this.min = value;
			}
		}
		return this;
	}

	/**
	 * Gets the maximum value tracked.
	 * 
	 * @return the maximum value
	 */
	public double getMax() {
		return max;
	}

	/**
	 * Gets the minimum value tracked.
	 * 
	 * @return the minimum value
	 */
	public double getMin() {
		return min;
	}

	/**
	 * Gets the range (difference between max and min).
	 * 
	 * @return the range value
	 */
	public double getRange() {
		return max - min;
	}

	/**
	 * Gets the midpoint between min and max.
	 * 
	 * @return the midpoint value
	 */
	public double getMidpoint() {
		return (min + max) / 2.0;
	}

	/**
	 * Checks if any values have been added to this tracker.
	 * 
	 * @return true if values have been added, false otherwise
	 */
	public boolean hasValues() {
		return hasValues;
	}

	/**
	 * Checks if the current range is valid (min <= max).
	 * 
	 * @return true if the range is valid, false otherwise
	 */
	public boolean isValid() {
		return min <= max;
	}

	/**
	 * Checks if a value is within the current range (inclusive).
	 * 
	 * @param value the value to check
	 * @return true if the value is within range, false otherwise
	 */
	public boolean contains(double value) {
		return value >= min && value <= max;
	}

	/**
	 * Resets the tracker to initial state.
	 */
	public void reset() {
		this.max = 0;
		this.min = 0;
		this.hasValues = false;
	}

	/**
	 * Returns a string representation of this MaxMinDouble.
	 * 
	 * @return a string describing the current range
	 */
	@Override
	public String toString() {
		if (!hasValues) {
			return "MaxMinDouble[no values]";
		}
		return String.format("MaxMinDouble[min=%.6f, max=%.6f, range=%.6f]", min, max, getRange());
	}

	/**
	 * Checks if this MaxMinDouble equals another object. Two MaxMinDouble objects
	 * are equal if they have the same min and max values.
	 * 
	 * @param obj the object to compare with
	 * @return true if the objects are equal, false otherwise
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}

		MaxMinDouble other = (MaxMinDouble) obj;
		return Double.compare(this.min, other.min) == 0 && Double.compare(this.max, other.max) == 0
				&& this.hasValues == other.hasValues;
	}

	/**
	 * Returns a hash code for this MaxMinDouble.
	 * 
	 * @return a hash code value
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Double.hashCode(min);
		result = prime * result + Double.hashCode(max);
		result = prime * result + Boolean.hashCode(hasValues);
		return result;
	}
}
