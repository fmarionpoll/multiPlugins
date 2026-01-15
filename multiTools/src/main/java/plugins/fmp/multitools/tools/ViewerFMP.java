package plugins.fmp.multiSPOTS96.tools;

import icy.gui.viewer.Viewer;
import icy.sequence.Sequence;

/**
 * Custom viewer class for MultiSPOTS96 with title refresh control. This class
 * extends the standard Icy Viewer to provide additional functionality for
 * controlling when the viewer title is refreshed.
 * 
 * <p>
 * ViewerFMP is used in the MultiSPOTS96 plugin to prevent unwanted title
 * updates during certain operations, such as when loading data or performing
 * batch operations.
 * </p>
 * 
 * <p>
 * Usage example:
 * 
 * <pre>
 * Sequence sequence = new Sequence();
 * ViewerFMP viewer = new ViewerFMP(sequence, true, false);
 * viewer.setInhibitRefreshViewerTitle(true);
 * // Perform operations that shouldn't trigger title updates
 * viewer.setInhibitRefreshViewerTitle(false);
 * </pre>
 * 
 * @author MultiSPOTS96
 * @see icy.gui.viewer.Viewer
 * @see icy.sequence.Sequence
 */
public class ViewerFMP extends Viewer {

	/** Flag to control whether title refresh is inhibited */
	private boolean inhibitRefreshViewerTitle = false;

	/**
	 * Creates a new ViewerFMP with the specified sequence and visibility.
	 * 
	 * @param sequence the sequence to display
	 * @param visible  whether the viewer should be visible
	 * @param inhibit  whether to inhibit title refresh initially
	 * @throws IllegalArgumentException if sequence is null
	 */
	public ViewerFMP(Sequence sequence, boolean visible, boolean inhibit) {
		super(sequence, visible);

		if (sequence == null) {
			throw new IllegalArgumentException("Sequence cannot be null");
		}

		this.inhibitRefreshViewerTitle = inhibit;
	}

	/**
	 * Overrides the parent's refreshViewerTitle method to respect the inhibit flag.
	 * Only calls the parent method if title refresh is not inhibited.
	 */
	@Override
	public void refreshViewerTitle() {
		if (!inhibitRefreshViewerTitle) {
			super.refreshViewerTitle();
		}
	}

	/**
	 * Sets whether title refresh should be inhibited.
	 * 
	 * @param inhibit true to inhibit title refresh, false to allow it
	 */
	public void setInhibitRefreshViewerTitle(boolean inhibit) {
		this.inhibitRefreshViewerTitle = inhibit;
	}

	/**
	 * Gets whether title refresh is currently inhibited.
	 * 
	 * @return true if title refresh is inhibited, false otherwise
	 */
	public boolean isInhibitRefreshViewerTitle() {
		return inhibitRefreshViewerTitle;
	}

	/**
	 * Temporarily inhibits title refresh for the duration of the provided
	 * operation. This is a convenience method for performing operations without
	 * title updates.
	 * 
	 * @param operation the operation to perform with inhibited title refresh
	 * @throws Exception if the operation throws an exception
	 */
	public void performWithoutTitleRefresh(Runnable operation) throws Exception {
		if (operation == null) {
			throw new IllegalArgumentException("Operation cannot be null");
		}

		boolean originalInhibit = this.inhibitRefreshViewerTitle;
		try {
			this.inhibitRefreshViewerTitle = true;
			operation.run();
		} finally {
			this.inhibitRefreshViewerTitle = originalInhibit;
		}
	}

	/**
	 * Returns a string representation of this ViewerFMP.
	 * 
	 * @return a string describing this viewer
	 */
	@Override
	public String toString() {
		return String.format("ViewerFMP[inhibitRefresh=%s, visible=%s]", inhibitRefreshViewerTitle, isVisible());
	}
}
