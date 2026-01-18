package plugins.fmp.multitools.experiment;

import java.util.ArrayList;

import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.FlyPositions;

public class CombinedExperiment extends Experiment {
	ArrayList<Experiment> experimentList = null;
	boolean collateExperiments = false;

//	public CombinedExperiment(Experiment exp) {
//		groupedExperiment = new ArrayList<Experiment>(1);
//		groupedExperiment.add(exp);
//	}

	public CombinedExperiment(Experiment exp, boolean collate) {
		experimentList = new ArrayList<Experiment>(1);
		experimentList.add(exp);
		this.collateExperiments = collate;
		if (collateExperiments)
			setGroupedExperiment(exp);
	}

	public void loadCombinedExperimentDescriptors() {
		Experiment expi = experimentList.get(0);
		copyExperimentFields(expi);
		copyOtherExperimentFields(expi);
		setFirstImage_FileTime(expi.getFirstImage_FileTime());
		expi = experimentList.get(experimentList.size() - 1);
		setLastImage_FileTime(expi.getLastImage_FileTime());
		// TODO: load capillaries descriptors and load cages descriptors
		// loadMCCapillaries_Descriptors(filename)
	}

	public void loadExperimentCamFileNames() {
		Experiment expi = experimentList.get(0);
		while (expi != null) {
			getSeqCamData().getImagesList().addAll(expi.getSeqCamData().getImagesList());
			expi = expi.chainToNextExperiment;
		}
	}

	private void copyOtherExperimentFields(Experiment source) {
		setImagesDirectory(source.getImagesDirectory());
		setExperimentDirectory(source.getExperimentDirectory());
		setBinSubDirectory(source.getBinSubDirectory());
	}

	private void setGroupedExperiment(Experiment exp) {
		Experiment expi = exp.getFirstChainedExperiment(true);
		experimentList = new ArrayList<Experiment>(1);
		while (expi != null) {
			experimentList.add(expi);
			expi = expi.chainToNextExperiment;
		}
	}

	public void setSingleExperiment() {
		Experiment expi = experimentList.get(0);
		experimentList = new ArrayList<Experiment>(1);
		experimentList.add(expi);
	}

	public void loadCapillaryMeasures() {
		// convert T into Tms (add time_first? - time_first expi(0))

	}

	public void loadFlyPositions() {
		long time_start_ms = getFirstImage_FileTime().toMillis();
		Experiment exp = experimentList.get(0);
		exp.initTmsForFlyPositions(time_start_ms);
		getCages().getCageList().addAll(exp.getCages().getCageList());

		for (int i = 1; i < experimentList.size(); i++) {
			Experiment expi = experimentList.get(i);
			expi.initTmsForFlyPositions(time_start_ms);
			for (Cage cage : getCages().getCageList()) {
				String cageName = cage.getRoi().getName();
				FlyPositions flyPos = cage.getFlyPositions();
				for (Cage cage_i : expi.getCages().getCageList()) {
					if (!cageName.equals(cage_i.getRoi().getName()))
						continue;
					FlyPositions flypos_i = cage_i.getFlyPositions();
					flyPos.getFlyPositionList().addAll(flypos_i.getFlyPositionList());
				}
			}
		}

	}

}
