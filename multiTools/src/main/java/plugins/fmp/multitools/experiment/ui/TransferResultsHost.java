package plugins.fmp.multitools.experiment.ui;

import java.util.List;

public interface TransferResultsHost {
	void closeAllExperimentsForTransfer();

	void reloadExperimentsFromExperimentXml(List<String> experimentXmlPaths);
}

