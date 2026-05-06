package plugins.fmp.multitools.transfer;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public final class TransferPlan {
	public final Path localCommonRoot;
	public final List<Path> resultsRoots;
	public final List<TransferItem> items;

	public TransferPlan(Path localCommonRoot, List<Path> resultsRoots, List<TransferItem> items) {
		this.localCommonRoot = localCommonRoot;
		this.resultsRoots = (resultsRoots != null) ? Collections.unmodifiableList(resultsRoots) : Collections.emptyList();
		this.items = (items != null) ? Collections.unmodifiableList(items) : Collections.emptyList();
	}
}

