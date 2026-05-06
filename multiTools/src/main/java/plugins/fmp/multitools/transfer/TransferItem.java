package plugins.fmp.multitools.transfer;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

public final class TransferItem {
	public final Path src;
	public final Path rel;
	public final long sizeBytes;
	public final FileTime lastModified;

	public TransferItem(Path src, Path rel, long sizeBytes, FileTime lastModified) {
		this.src = src;
		this.rel = rel;
		this.sizeBytes = sizeBytes;
		this.lastModified = lastModified;
	}
}

