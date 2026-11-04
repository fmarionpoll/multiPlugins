package plugins.fmp.multicafe.dlg.levels;

import java.awt.Color;
import java.util.List;

import plugins.fmp.multitools.service.CorrelationGulpPreview;
import plugins.fmp.multitools.service.IntakeDiagnostics;

/** What the motion plot and alignment view read and change. */
interface IntakeChartHost {
	CorrelationGulpPreview preview();

	double thresholdValue();

	boolean thresholdEditable();

	boolean addGulpArmed();

	int selectedFrame();

	void selectFrame(int frame);

	void dragThreshold(double pixelsPerMinute);

	void editGulpRange(int first, int last);

	boolean persistentEpisodes();

	boolean showDiagnostics();

	boolean compensateEvaporation();

	boolean[] candidates();

	double[] candidateAmplitudes();

	double[] candidateBottoms();

	List<IntakeDiagnostics.Warning> diagnostics();

	Color diagnosticColor(IntakeDiagnostics.Warning warning);

	String formatNumber(double value);
}
