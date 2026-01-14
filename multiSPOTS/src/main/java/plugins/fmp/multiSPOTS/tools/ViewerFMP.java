package plugins.fmp.multiSPOTS.tools;

import icy.gui.viewer.Viewer;
import icy.sequence.Sequence;

public class ViewerFMP extends Viewer {
	public boolean inhibitRefreshViewerTitle = false;

	public ViewerFMP(Sequence sequence, boolean visible, boolean inhibit) {
		super(sequence, visible);
		inhibitRefreshViewerTitle = inhibit;
	}

	@Override
	public void refreshViewerTitle() {
		if (!inhibitRefreshViewerTitle)
			super.refreshViewerTitle();
	}

}
