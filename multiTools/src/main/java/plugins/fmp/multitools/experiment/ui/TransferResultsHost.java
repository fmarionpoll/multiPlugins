package plugins.fmp.multitools.experiment.ui;

import java.util.List;

public interface TransferResultsHost {
	void closeAllExperimentsForTransfer();

	void reloadExperimentsFromExperimentXml(List<String> experimentXmlPaths);

	/**
	 * Called after a transfer-triggered reload to restore a usable UI state.
	 * Implementations should open the experiment at {@code index} (typically 0) if possible.
	 */
	void openExperimentAtIndex(int index);
}

