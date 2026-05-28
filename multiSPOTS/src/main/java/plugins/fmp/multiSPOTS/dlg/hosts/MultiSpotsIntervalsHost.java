package plugins.fmp.multiSPOTS.dlg.hosts;

import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.GenerationMode;
import plugins.fmp.multitools.experiment.ui.host.IntervalsHost;

public class MultiSpotsIntervalsHost extends MultiSpotsDialogHost implements IntervalsHost {

	public MultiSpotsIntervalsHost(MultiSPOTS plugin) {
		super(plugin);
	}

	@Override
	public int getDefaultNominalIntervalSec() {
		return getPlugin().viewOptions.getDefaultNominalIntervalSec();
	}

	@Override
	public void setDefaultNominalIntervalSec(int sec) {
		getPlugin().viewOptions.setDefaultNominalIntervalSec(sec);
	}

	@Override
	public void saveViewOptions() {
		getPlugin().viewOptions.save(getPlugin().getPreferences("viewOptions"));
	}

	@Override
	public void onAfterIntervalsApply(Experiment exp) {
		MultiSPOTS p = getPlugin();
		p.dlgBrowse.loadSaveExperiment.closeCurrentExperiment();
		p.dlgBrowse.loadSaveExperiment.openSelectedExperiment(exp);
	}

	@Override
	public GenerationMode coerceGenerationMode(GenerationMode gm) {
		return gm == GenerationMode.UNKNOWN ? GenerationMode.DIRECT_FROM_STACK : gm;
	}
}
