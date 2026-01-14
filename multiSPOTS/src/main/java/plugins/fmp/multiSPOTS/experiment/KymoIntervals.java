package plugins.fmp.multiSPOTS.experiment;

import java.util.ArrayList;

public class KymoIntervals {

	public ArrayList<Long[]> intervals = new ArrayList<Long[]>();

	public int addIfNew(Long[] interval) {
		for (int i = 0; i < intervals.size(); i++) {
			if (interval[0] == intervals.get(i)[0])
				return i;
			if (interval[0] < intervals.get(i)[0]) {
				intervals.add(i, interval);
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
			if (start == intervals.get(i)[0])
				return i;
		}
		return -1;
	}

	public Long[] get(int i) {
		return intervals.get(i);
	}

}
