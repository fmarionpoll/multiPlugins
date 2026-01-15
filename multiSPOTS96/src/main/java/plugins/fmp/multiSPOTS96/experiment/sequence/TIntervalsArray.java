package plugins.fmp.multiSPOTS96.experiment.sequence;

import java.util.ArrayList;

public class TIntervalsArray {
	public ArrayList<TInterval> intervals = new ArrayList<TInterval>();

	public int addIfNew(TInterval interval) {
		for (int i = 0; i < intervals.size(); i++) {
			if (interval.start == intervals.get(i).start)
				return i;
			if (interval.start < intervals.get(i).start) {
				intervals.add(i, interval);
				if (i > 0)
					intervals.get(i-1).end = interval.start -1;
				return i;
			}
		}
		intervals.add(interval);
		return intervals.size() - 1;
	}

	public boolean deleteIntervalStartingAt(long start) {
		int index = findStartItem(start);
		if (index < 0)
			return false;
		intervals.remove(index);
		return true;
	}

	public int size() {
		return intervals.size();
	}

	public int findStartItem(long start) {
		for (int i = 0; i < intervals.size(); i++) {
			if (start == intervals.get(i).start)
				return i;
		}
		return -1;
	}

	public TInterval getTIntervalAt(int i) {
		return intervals.get(i);
	}

}
