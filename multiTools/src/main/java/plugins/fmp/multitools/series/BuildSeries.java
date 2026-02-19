package plugins.fmp.multitools.series;

import java.awt.Color;
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
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
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
			System.out.println(
					"BuildSeries (" + index + " / " + expList.index1 + "):doInBackground process ended - duration: "
							+ ((endTime2InNs - startTimeInNs) / 1000000000f) + " s");

			// Force multiple GC passes with delays to help free memory for next experiment
			for (int i = 0; i < 3; i++) {
				System.gc();
				try {
					Thread.sleep(200); // Give GC time to work
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
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
		exp.getCages().detectBin_Ms = options.t_Ms_BinDuration;
		if (options.isFrameFixed) {
			exp.getCages().detectFirst_Ms = options.t_Ms_First;
			exp.getCages().detectLast_Ms = options.t_Ms_Last;
			if (exp.getCages().detectLast_Ms > exp.getSeqCamData().getLastImageMs())
				exp.getCages().detectLast_Ms = exp.getSeqCamData().getLastImageMs();
		} else {
			exp.getCages().detectFirst_Ms = exp.getSeqCamData().getFirstImageMs();
			exp.getCages().detectLast_Ms = exp.getSeqCamData().getLastImageMs();
		}
		exp.getCages().detect_threshold = options.threshold;

		boolean flag = true;
		if (exp.getCages().cagesList.size() < 1) {
			System.out.println(
					"BuildSeries:checkBoundsForCages ! skipped experiment with no cage: " + exp.getResultsDirectory());
			flag = false;
		}
		return flag;
	}

	protected void getTimeLimitsOfSequence(Experiment exp) {
		exp.getFileIntervalsFromSeqCamData();
		if (exp.getSeqCamData().getTimeManager().getBinImage_ms() == 0)
			exp.loadFileIntervalsFromSeqCamData();
		exp.getSeqCamData().getTimeManager().setBinDurationMs(exp.getSeqCamData().getTimeManager().getBinImage_ms());

		if (options.isFrameFixed) {
			exp.getSeqCamData().getTimeManager().setBinFirst_ms(options.t_Ms_First);
			exp.getSeqCamData().getTimeManager().setBinLast_ms(options.t_Ms_Last);
			if (exp.getSeqCamData().getTimeManager().getBinLast_ms() + exp.getSeqCamData().getFirstImageMs() > exp
					.getSeqCamData().getLastImageMs())
				exp.getSeqCamData().getTimeManager()
						.setBinLast_ms(exp.getSeqCamData().getLastImageMs() - exp.getSeqCamData().getFirstImageMs());
		} else {
			exp.getSeqCamData().getTimeManager().setBinFirst_ms(0);
			exp.getSeqCamData().getTimeManager()
					.setBinLast_ms(exp.getSeqCamData().getLastImageMs() - exp.getSeqCamData().getFirstImageMs());
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
		exp.getSeqCamData().attachSequence(exp.getSeqCamData().getImageLoader()
				.initSequenceFromFirstImage(exp.getSeqCamData().getImagesList(true)));
		boolean flag = exp.load_cages_description_and_measures();
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

	// -------------------------- used by flydetection classes

	void displayRectanglesAsROIs1(Sequence seq, List<Rectangle2D> listRectangles, boolean eraseOldPoints) {
		if (eraseOldPoints)
			seq.removeAllROI();

		for (Rectangle2D rectangle : listRectangles) {
			ROI2DRectangle flyRectangle = new ROI2DRectangle(rectangle);
			flyRectangle.setColor(Color.YELLOW);
			seq.addROI(flyRectangle);
		}
	}

	void openFlyDetectViewers1(Experiment exp) {
		try {
			SwingUtilities.invokeAndWait(new Runnable() {
				public void run() {
					// Close any existing sequence and viewer before creating new ones
					closeSequence(seqNegative);
					closeViewer(vNegative);
					seqNegative = newSequence("detectionImage", exp.getSeqCamData().getReferenceImage());
					vNegative = new ViewerFMP(seqNegative, false, true);
					vNegative.setVisible(true);
				}
			});
		} catch (InvocationTargetException | InterruptedException e) {
			e.printStackTrace();
		}
	}

}
