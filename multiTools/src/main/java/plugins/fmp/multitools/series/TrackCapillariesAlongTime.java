package plugins.fmp.multitools.series;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import icy.roi.ROI2D;
import icy.system.SystemUtil;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.service.CapillaryTracker;

/**
 * Orchestrates capillary tracking over a frame range for all capillaries in an
 * experiment. Uses parallel execution and reports progress via ProgressReporter.
 */
public class TrackCapillariesAlongTime {

	private final CapillaryTracker tracker = new CapillaryTracker();

	/**
	 * Tracks capillaries from tStart to tEnd (inclusive). For each capillary with
	 * kymograph enabled, runs tracking in parallel, injects results into AlongT,
	 * and reports progress.
	 */
	public void run(Experiment exp, int tStart, int tEnd, ProgressReporter progress) {
		Capillaries capillaries = exp.getCapillaries();
		List<Capillary> caps = capillaries.getList();
		if (caps.isEmpty()) {
			progress.completed();
			return;
		}

		SequenceCamData seqCamData = exp.getSeqCamData();
		if (seqCamData == null) {
			progress.failed("No sequence data");
			return;
		}

		int t0 = Math.min(tStart, tEnd);
		int t1 = Math.max(tStart, tEnd);

		List<Integer> indices = new ArrayList<>();
		for (int i = 0; i < caps.size(); i++) {
			if (caps.get(i).getKymographBuild())
				indices.add(i);
		}
		int total = indices.size();
		if (total == 0) {
			progress.updateMessage("No capillaries with kymograph enabled");
			progress.completed();
			return;
		}

		int nThreads = Math.min(total, Math.max(1, SystemUtil.getNumberOfCPUs()));
		ExecutorService exec = Executors.newFixedThreadPool(nThreads);
		ExecutorCompletionService<AbstractMap.SimpleEntry<Integer, Map<Long, ROI2D>>> ecs = new ExecutorCompletionService<>(
				exec);

		for (int capIndex : indices) {
			Capillary cap = caps.get(capIndex);
			ecs.submit(new Callable<AbstractMap.SimpleEntry<Integer, Map<Long, ROI2D>>>() {
				@Override
				public AbstractMap.SimpleEntry<Integer, Map<Long, ROI2D>> call() {
					Map<Long, ROI2D> tracked = tracker.track(cap, seqCamData, t0, t1);
					return new AbstractMap.SimpleEntry<>(capIndex, tracked);
				}
			});
		}

		int done = 0;
		try {
			while (done < total) {
				if (progress.isCancelled())
					break;
				Future<AbstractMap.SimpleEntry<Integer, Map<Long, ROI2D>>> f = ecs.take();
				AbstractMap.SimpleEntry<Integer, Map<Long, ROI2D>> e = f.get();
				capillaries.injectTrackedRoisForCapillary(e.getKey(), t0, t1, e.getValue());
				done++;
				progress.updateProgress("Capillary " + done + "/" + total, done, total);
			}
		} catch (Exception ex) {
			progress.failed(ex.getMessage());
		} finally {
			exec.shutdown();
		}

		if (progress.isCancelled())
			progress.failed("Cancelled");
		else
			progress.completed();
	}
}
