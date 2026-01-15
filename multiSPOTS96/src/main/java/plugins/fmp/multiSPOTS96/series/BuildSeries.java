package plugins.fmp.multiSPOTS96.series;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import icy.gui.frame.progress.ProgressFrame;
import icy.gui.viewer.Viewer;
import icy.image.IcyBufferedImage;
import icy.sequence.Sequence;
import icy.system.thread.Processor;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.ViewerFMP;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;
import plugins.kernel.roi.roi2d.ROI2DRectangle;

public abstract class BuildSeries extends SwingWorker<Integer, Integer> {

	public BuildSeriesOptions options = new BuildSeriesOptions();
	public boolean stopFlag = false;
	public boolean threadRunning = false;
	int nframescomputed = 0;
	int nframestotal = 0;

	int selectedExperimentIndex = -1;
	Sequence seqNegative = null;
	ViewerFMP vNegative = null;
	public final String THREAD_ENDED = "thread_ended";
	public final String THREAD_DONE = "thread_done";

	@Override
	protected Integer doInBackground() throws Exception {
//		System.out.println("BuildSeries:doInBackground loop over experiments");
		threadRunning = true;
		int nbiterations = 0;
		JComboBoxExperimentLazy expList = options.expList;
		ProgressFrame progress = new ProgressFrame("Analyze series");
		selectedExperimentIndex = expList.getSelectedIndex();
		selectList(expList, -1);

		for (int index = expList.index0; index <= expList.index1; index++, nbiterations++) {
			if (stopFlag)
				break;
			long startTimeInNs = System.nanoTime();
			Experiment exp = expList.getItemAt(index);
			progress.setMessage("Processing file: " + (index + 1) + "//" + (expList.index1 + 1));
//			System.out.println("BuildSeries:doInBackground " + (index + 1) + ": " + exp.getResultsDirectory());

			analyzeExperiment(exp);
			long endTime2InNs = System.nanoTime();
			System.out.println("BuildSeries:doInBackground process ended - duration: "
					+ ((endTime2InNs - startTimeInNs) / 1000000000f) + " s");

			System.gc();
		}
		progress.close();
		threadRunning = false;

		selectList(expList, selectedExperimentIndex);
		return nbiterations;
	}

	private void selectList(JComboBoxExperimentLazy expList, int index) {
		try {
			SwingUtilities.invokeAndWait(new Runnable() {
				public void run() {
					expList.setSelectedIndex(index);
				}
			});
		} catch (InvocationTargetException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void done() {
		int statusMsg = 0;
		try {
			statusMsg = super.get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}

		if (!threadRunning || stopFlag)
			firePropertyChange(THREAD_ENDED, null, statusMsg);
		else
			firePropertyChange(THREAD_DONE, null, statusMsg);
	}

	abstract void analyzeExperiment(Experiment exp);

	protected void waitFuturesCompletion(Processor processor, ArrayList<Future<?>> futuresArray,
			ProgressFrame progressBar) {
		int frame = 1;
		nframescomputed = futuresArray.size();
		nframestotal = 0;

		while (!futuresArray.isEmpty()) {
			final Future<?> f = futuresArray.get(futuresArray.size() - 1);
			if (progressBar != null)
				progressBar.setMessage("Analyze frame: " + nframestotal + "//" + nframescomputed);
			try {
				f.get();
			} catch (ExecutionException e) {
				System.out.println("BuildSeries:waitFuturesCompletion - frame:" + frame + " Execution exception: " + e);
			} catch (InterruptedException e) {
				System.out.println("BuildSeries:waitFuturesCompletion - Interrupted exception: " + e);
			}
			futuresArray.remove(f);
			frame++;
		}
	}

	protected boolean checkBoundsForCages(Experiment exp) {
		exp.cagesArray.detectBin_Ms = options.t_Ms_BinDuration;
		if (options.isFrameFixed) {
			exp.cagesArray.detectFirst_Ms = options.t_Ms_First;
			exp.cagesArray.detectLast_Ms = options.t_Ms_Last;
			if (exp.cagesArray.detectLast_Ms > exp.seqCamData.getLastImageMs())
				exp.cagesArray.detectLast_Ms = exp.seqCamData.getLastImageMs();
		} else {
			exp.cagesArray.detectFirst_Ms = exp.seqCamData.getFirstImageMs();
			exp.cagesArray.detectLast_Ms = exp.seqCamData.getLastImageMs();
		}
		exp.cagesArray.detect_threshold = options.threshold;

		boolean flag = true;
		if (exp.cagesArray.cagesList.size() < 1) {
			System.out.println(
					"BuildSeries:checkBoundsForCages ! skipped experiment with no cage: " + exp.getResultsDirectory());
			flag = false;
		}
		return flag;
	}

	protected void getTimeLimitsOfSequence(Experiment exp) {
		exp.getFileIntervalsFromSeqCamData();
		if (exp.seqCamData.getTimeManager().getBinImage_ms() == 0)
			exp.loadFileIntervalsFromSeqCamData();
		exp.seqCamData.getTimeManager().setBinDurationMs(exp.seqCamData.getTimeManager().getBinImage_ms());

		if (options.isFrameFixed) {
			exp.seqCamData.getTimeManager().setBinFirst_ms(options.t_Ms_First);
			exp.seqCamData.getTimeManager().setBinLast_ms(options.t_Ms_Last);
			if (exp.seqCamData.getTimeManager().getBinLast_ms() + exp.seqCamData.getFirstImageMs() > exp.seqCamData
					.getLastImageMs())
				exp.seqCamData.getTimeManager()
						.setBinLast_ms(exp.seqCamData.getLastImageMs() - exp.seqCamData.getFirstImageMs());
		} else {
			exp.seqCamData.getTimeManager().setBinFirst_ms(0);
			exp.seqCamData.getTimeManager()
					.setBinLast_ms(exp.seqCamData.getLastImageMs() - exp.seqCamData.getFirstImageMs());
		}
	}

	public IcyBufferedImage imageIORead(String name) {
		BufferedImage image = null;
		try {
			image = ImageIO.read(new File(name));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return IcyBufferedImage.createFrom(image);
	}

	protected boolean loadSeqCamDataAndCages(Experiment exp) {
		exp.seqCamData.attachSequence(
				exp.seqCamData.getImageLoader().initSequenceFromFirstImage(exp.seqCamData.getImagesList(true)));
		boolean flag = exp.load_MS96_cages();
		return flag;
	}

	void closeSequence(Sequence seq) {
		if (seq != null) {
			seq.close();
			seq = null;
		}
	}

	void closeViewer(Viewer v) {
		if (v != null) {
			v.close();
			v = null;
		}
	}

	Sequence newSequence(String title, IcyBufferedImage image) {
		Sequence seq = new Sequence();
		seq.setName(title);
		seq.setImage(0, 0, image);
		return seq;
	}

	void displayRectanglesAsROIs(Sequence seq, List<Rectangle2D> listRectangles, boolean eraseOldPoints) {
		if (eraseOldPoints)
			seq.removeAllROI();

		for (Rectangle2D rectangle : listRectangles) {
			ROI2DRectangle flyPoint = new ROI2DRectangle(rectangle);
			seq.addROI(flyPoint);
		}
	}

	void openFlyDetectViewers(Experiment exp) {
		try {
			SwingUtilities.invokeAndWait(new Runnable() {
				public void run() {
					seqNegative = newSequence("detectionImage", exp.seqCamData.getReferenceImage());
					vNegative = new ViewerFMP(seqNegative, false, true);
					vNegative.setVisible(true);
				}
			});
		} catch (InvocationTargetException | InterruptedException e) {
			e.printStackTrace();
		}
	}
}
