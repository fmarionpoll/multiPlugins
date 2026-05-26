package plugins.fmp.multiSPOTS96.dlg.kymograph;

/**
 * Shared kymograph diagnostic settings (Charts + Analysis). Mutable; read on Analyze and when painting overlays.
 */
public final class KymoDiagnosticsOptions {

	private volatile boolean includeDiagnosticsOnAnalyze;
	private volatile boolean showGapFillColumnsOnKymograph;

	public boolean isIncludeDiagnosticsOnAnalyze() {
		return includeDiagnosticsOnAnalyze;
	}

	public void setIncludeDiagnosticsOnAnalyze(boolean includeDiagnosticsOnAnalyze) {
		this.includeDiagnosticsOnAnalyze = includeDiagnosticsOnAnalyze;
	}

	public boolean isShowGapFillColumnsOnKymograph() {
		return showGapFillColumnsOnKymograph;
	}

	public void setShowGapFillColumnsOnKymograph(boolean showGapFillColumnsOnKymograph) {
		this.showGapFillColumnsOnKymograph = showGapFillColumnsOnKymograph;
	}
}
