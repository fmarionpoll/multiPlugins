package plugins.fmp.multitools.fmp_tools.JComponents;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.swing.DefaultComboBoxModel;

/**
 * A combo box model that maintains its elements in sorted order. Provides
 * automatic sorting when elements are added.
 */
public class JComboBoxModelSorted extends DefaultComboBoxModel<String> {

	private static final long serialVersionUID = -729889390560323340L;

	/**
	 * Creates an empty sorted combo box model.
	 */
	public JComboBoxModelSorted() {
		super();
	}

	/**
	 * Creates a sorted combo box model with the specified array of items. The items
	 * are sorted automatically.
	 * 
	 * @param items The initial items to add
	 */
	public JComboBoxModelSorted(String[] items) {
		super();
		if (items != null && items.length > 0) {
			Arrays.sort(items);
			for (String item : items) {
				super.addElement(item);
			}
			setSelectedItem(items[0]);
		}
	}

	/**
	 * Creates a sorted combo box model with the specified list of items. The items
	 * are sorted automatically.
	 * 
	 * @param items The initial items to add
	 */
	public JComboBoxModelSorted(List<String> items) {
		super();
		if (items != null && !items.isEmpty()) {
			Collections.sort(items);
			for (String item : items) {
				super.addElement(item);
			}
			setSelectedItem(items.get(0));
		}
	}

	/**
	 * Adds an element to the model in its proper sorted position.
	 * 
	 * @param text The element to add
	 */
	@Override
	public void addElement(String text) {
		insertElementAt(text, findInsertionIndex(text));
	}

	/**
	 * Inserts an element at the correct sorted position, ignoring the provided
	 * index. The index parameter is ignored to maintain sorted order.
	 * 
	 * @param text  The element to insert
	 * @param index Ignored - the element is inserted at the correct sorted position
	 */
	@Override
	public void insertElementAt(String text, int index) {
		if (text == null) {
			return;
		}

		int correctIndex = findInsertionIndex(text);
		super.insertElementAt(text, correctIndex);
		fireContentsChanged(this, correctIndex, correctIndex);
	}

	/**
	 * Finds the correct insertion index to maintain sorted order using binary
	 * search.
	 * 
	 * @param text The text to find the insertion point for
	 * @return The index where the text should be inserted
	 */
	private int findInsertionIndex(String text) {
		int size = getSize();

		// Binary search for insertion point
		int low = 0;
		int high = size;

		while (low < high) {
			int mid = (low + high) / 2;
			String midElement = getElementAt(mid);

			if (midElement == null || midElement.compareTo(text) > 0) {
				high = mid;
			} else {
				low = mid + 1;
			}
		}

		return low;
	}

	/**
	 * Removes all elements that match the specified text.
	 * 
	 * @param text The text to remove
	 * @return true if any elements were removed
	 */
	public boolean removeElement(String text) {
		if (text == null) {
			return false;
		}

		boolean removed = false;
//		int initialSize = getSize();

		// Remove all occurrences by repeatedly checking if element exists and removing
		// it
		while (getIndexOf(text) != -1) {
			super.removeElement(text);
			removed = true;
		}

		return removed;
	}
}
