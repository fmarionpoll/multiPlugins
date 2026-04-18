package plugins.fmp.multicafe.dlg.hosts;

import java.util.List;

import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ExperimentDirectories;
import plugins.fmp.multitools.experiment.GenerationMode;
import plugins.fmp.multitools.experiment.ui.host.IntervalsHost;

public class MultiCafeIntervalsHost extends MultiCafeDialogHost implements IntervalsHost {

	public MultiCafeIntervalsHost(MultiCAFE plugin) {
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
		MultiCAFE p = getPlugin();
		p.paneBrowse.panelLoadSave.closeCurrentExperiment();
		List<String> imagesList = ExperimentDirectories
				.getImagesListFromPathV2(exp.getSeqCamData().getImageLoader().getImagesDirectory(), "jpg");
		exp.getSeqCamData().loadImageList(imagesList);
		p.paneExperiment.updateDialogs(exp);
		p.paneExperiment.updateViewerForSequenceCam(exp);
		p.paneExperiment.tabOptions.applyCentralViewOptionsToCamViewer(exp);
	}

	@Override
	public void onFirstImageIndexChanged(Experiment exp) {
		MultiCAFE p = getPlugin();
		p.paneExperiment.updateViewerForSequenceCam(exp);
		p.paneExperiment.tabOptions.applyCentralViewOptionsToCamViewer(exp);
	}

	@Override
	public GenerationMode coerceGenerationMode(GenerationMode gm) {
		return gm;
	}
}
