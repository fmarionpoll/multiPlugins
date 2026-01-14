package plugins.fmp.multiSPOTS.tools;

public class MaxMinDouble {
	public double max = 0;
	public double min = 0;

	public MaxMinDouble() {
	}

	public MaxMinDouble(double val1, double val2) {
		if (val1 >= val2) {
			max = val1;
			min = val2;
		} else {
			min = val1;
			max = val2;
		}
	}

	public MaxMinDouble getMaxMin(double value1, double value2) {
		getMaxMin(value1);
		getMaxMin(value2);
		return this;
	}

	public MaxMinDouble getMaxMin(MaxMinDouble val) {
		getMaxMin(val.min);
		getMaxMin(val.max);
		return this;
	}

	public MaxMinDouble getMaxMin(double value) {
		if (value > max)
			max = value;
		if (value < min)
			min = value;
		return this;
	}

}
