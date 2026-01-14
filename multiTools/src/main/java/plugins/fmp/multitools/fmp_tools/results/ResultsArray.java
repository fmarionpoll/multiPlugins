package plugins.fmp.multitools.fmp_tools.results;

import java.util.ArrayList;
import java.util.Collections;

import plugins.fmp.multitools.fmp_tools.Comparators;

public class ResultsArray {
	protected ArrayList<Results> resultsList = null;
	String stim = null;
	String conc = null;
	double lowestPiAllowed = -1.2;
	double highestPiAllowed = 1.2;

	public ResultsArray(int size) {
		resultsList = new ArrayList<Results>(size);
	}

	public ResultsArray() {
		resultsList = new ArrayList<Results>();
	}

	public int size() {
		return resultsList.size();
	}

	public Results getRow(int index) {
		if (index >= resultsList.size())
			return null;
		return resultsList.get(index);
	}

	public void addRow(Results results) {
		resultsList.add(results);
	}

	public void sortRowsByName() {
		Collections.sort(resultsList, new Comparators.Results_Name());
	}

	public void subtractDeltaT(int i, int j) {
		for (Results row : resultsList)
			row.subtractDeltaT(1, 1); // options.buildExcelStepMs);
	}

	public ArrayList<Results> getList() {
		return resultsList;
	}

}
