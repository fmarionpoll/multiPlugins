package plugins.fmp.multiSPOTS96.dlg.hosts;

import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.GenerationMode;
import plugins.fmp.multitools.experiment.ui.host.IntervalsHost;

public class MultiSpots96IntervalsHost extends MultiSpots96DialogHost implements IntervalsHost {

	public MultiSpots96IntervalsHost(MultiSPOTS96 plugin) {
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
		MultiSPOTS96 p = getPlugin();
		p.dlgBrowse.loadSaveExperiment.closeCurrentExperiment();
		p.dlgBrowse.loadSaveExperiment.openSelectedExperiment(exp);
	}

	@Override
	public GenerationMode coerceGenerationMode(GenerationMode gm) {
		return gm == GenerationMode.UNKNOWN ? GenerationMode.DIRECT_FROM_STACK : gm;
	}
}
