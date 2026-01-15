package plugins.fmp.multiSPOTS96.tools.JComponents;

import java.util.Arrays;
import java.util.Collections;
import java.util.Vector;

import javax.swing.DefaultComboBoxModel;

public class SortedComboBoxModel extends DefaultComboBoxModel<String> {

	private static final long serialVersionUID = -729889390560323340L;

	public SortedComboBoxModel() {
		super();
	}

	public SortedComboBoxModel(String[] items) {
		Arrays.sort(items);
		int size = items.length;
		for (int i = 0; i < size; i++) {
			super.addElement(items[i]);
		}
		setSelectedItem(items[0]);
	}

	public SortedComboBoxModel(Vector<String> items) {
		Collections.sort(items);
		int size = items.size();
		for (int i = 0; i < size; i++) {
			super.addElement(items.elementAt(i));
		}
		setSelectedItem(items.elementAt(0));
	}

	@Override
	public void addElement(String text) {
		insertElementAt(text, 0);
	}

	@Override
	public void insertElementAt(String text, int index) {
		int size = getSize();
		for (index = 0; index < size; index++) {
			Comparable<String> c = (Comparable<String>) getElementAt(index);
			if (
//					c == null 
//					|| 
			c.compareTo(text) > 0)
				break;
		}
		super.insertElementAt(text, index);
		fireContentsChanged(text, -1, -1);
	}

}
