package plugins.fmp.multitools.series;

import java.awt.Rectangle;
import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import icy.gui.frame.progress.ProgressFrame;
import icy.image.IcyBufferedImage;
import icy.roi.ROI2D;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ExperimentDirectories;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotMeasure;
import plugins.fmp.multitools.service.KymoImageTransforms;
import plugins.fmp.multitools.service.KymoMetricGate;
import plugins.fmp.multitools.service.SequenceLoaderService;
import plugins.fmp.multitools.service.SpotLineDeficitAnalyzer;
import plugins.fmp.multitools.service.SpotLineDeficitAnalyzer.Layout;
import plugins.fmp.multitools.service.SpotLineDeficitAnalyzer.Params;
import plugins.fmp.multitools.service.SpotLineDeficitAnalyzer.SpotGeom;
import plugins.fmp.multitools.tools.Logger;

/**
 * Samples each spot disk on the camera frames. The zero is the floor on the
 * extended row line, outside the circles. Stores {@code KYMO_LINE_RATIO}.
 */
public class AnalyzeSpotLineDeficits extends BuildSeries {

	public Params analyzerParams;
	public volatile int lastCageCount;
	public volatile int lastBinCount;
	public volatile boolean lastSaved;

	@Override
	void analyzeExperiment(Experiment exp) {
		if (exp == null || analyzerParams == null) {
			return;
		}
		lastSaved = false;
		ProgressFrame progress = null;
		try {
			if (!prepareExperiment(exp)) {
				Logger.warn("AnalyzeSpotLineDeficits: could not prepare experiment " + exp.getResultsDirectory());
				return;
			}
			SequenceCamData seq = exp.getSeqCamData();
			int[] camera = cameraSize(seq);
			if (camera == null) {
				Logger.warn("AnalyzeSpotLineDeficits: camera size unknown for " + exp.getResultsDirectory());
				return;
			}
			int width = camera[0];
			int height = camera[1];
			List<Integer> frames = binFrames(exp);
			if (frames.isEmpty()) {
				Logger.warn("AnalyzeSpotLineDeficits: no frames for " + exp.getResultsDirectory());
				return;
			}
			List<CageLines> cages = cageLines(exp, width, height, analyzerParams);
			if (cages.isEmpty()) {
				Logger.warn("AnalyzeSpotLineDeficits: no spots for " + exp.getResultsDirectory());
				return;
			}
			int nFrames = frames.size();
			for (CageLines cage : cages) {
				cage.flanks = new double[nFrames][];
				cage.integral = new double[cage.spots.size()][nFrames];
			}
			SequenceLoaderService loader = new SequenceLoaderService();
			progress = new ProgressFrame("Analyze line ratio");
			if (!readMeasures(seq, loader, frames, cages, progress)) {
				return;
			}
			for (CageLines cage : cages) {
				double[][] ratios = SpotLineDeficitAnalyzer.ratiosFromIntegrals(cage.integral,
						analyzerParams.initialBins, analyzerParams.smoothBins);
				for (int i = 0; i < cage.spots.size(); i++) {
					copyDoubles(cage.spots.get(i).getKymoLineRatio(), i < ratios.length ? ratios[i] : null);
				}
			}
			if (exp.getCages() != null) {
				exp.getCages().clearSpotAggregatesCache();
			}
			ensureKymoBinDirectory(exp);
			String binDir = exp.getKymosBinFullDirectory();
			lastSaved = exp.save_kymo_spot_measures();
			lastCageCount = cages.size();
			lastBinCount = nFrames;
			if (!lastSaved) {
				Logger.warn("AnalyzeSpotLineDeficits: ratios computed but SpotsMeasures.csv was not saved for "
						+ exp.getResultsDirectory() + " (bin " + binDir + ")");
			}
			Logger.info("AnalyzeSpotLineDeficits: " + lastCageCount + " cage(s), " + lastBinCount + " bin(s) — "
					+ exp.getResultsDirectory() + " -> " + binDir);
		} finally {
			if (progress != null) {
				progress.close();
			}
			exp.releaseKymographSequence();
			exp.closeSequences();
		}
	}

	private boolean readMeasures(SequenceCamData seq, SequenceLoaderService loader, List<Integer> frames,
		List<CageLines> cages, ProgressFrame progress) {
		int n = frames.size();
		int window = Math.min(analyzerParams.initialBins, n);
		for (int t = 0; t < window; t++) {
			if (!sampleFlanksAt(seq, loader, frames, cages, t)) {
				return false;
			}
			showFrame(progress, t, n);
		}
		for (CageLines cage : cages) {
			cage.noise = SpotLineDeficitAnalyzer.poolMad(cage.flanks, window, analyzerParams.madMultiplier);
		}
		for (int t = 0; t < window; t++) {
			if (!integrateAt(seq, loader, frames, cages, t)) {
				return false;
			}
		}
		for (int t = window; t < n; t++) {
			if (stopFlag) {
				return false;
			}
			showFrame(progress, t, n);
			FramePixels frame = readFrame(seq, loader, frames.get(t));
			for (CageLines cage : cages) {
				cage.flanks[t] = SpotLineDeficitAnalyzer.sampleFlanks(cage.layout, frame.red, frame.green,
					frame.blue, frame.insect);
				double[] floors = SpotLineDeficitAnalyzer.spotFloors(cage.layout, cage.flanks[t]);
				SpotLineDeficitAnalyzer.integrate(cage.layout, frame.red, frame.green, frame.blue, floors,
					cage.noise, cage.integral, t, frame.insect);
			}
		}
		return true;
	}

	private boolean sampleFlanksAt(SequenceCamData seq, SequenceLoaderService loader, List<Integer> frames,
		List<CageLines> cages, int t) {
		if (stopFlag) {
			return false;
		}
		FramePixels frame = readFrame(seq, loader, frames.get(t));
		for (CageLines cage : cages) {
			cage.flanks[t] = SpotLineDeficitAnalyzer.sampleFlanks(cage.layout, frame.red, frame.green, frame.blue,
				frame.insect);
		}
		return true;
	}

	private boolean integrateAt(SequenceCamData seq, SequenceLoaderService loader, List<Integer> frames,
		List<CageLines> cages, int t) {
		if (stopFlag) {
			return false;
		}
		FramePixels frame = readFrame(seq, loader, frames.get(t));
		for (CageLines cage : cages) {
			double[] floors = SpotLineDeficitAnalyzer.spotFloors(cage.layout, cage.flanks[t]);
			SpotLineDeficitAnalyzer.integrate(cage.layout, frame.red, frame.green, frame.blue, floors, cage.noise,
				cage.integral, t, frame.insect);
		}
		return true;
	}

	private static void showFrame(ProgressFrame progress, int t, int n) {
		if (progress != null) {
			progress.setMessage("Analyze frame: " + (t + 1) + "//" + n);
		}
	}
	private FramePixels readFrame(SequenceCamData seq, SequenceLoaderService loader, int frameIndex) {
		String name = seq.getFileNameFromImageList(frameIndex);
		IcyBufferedImage img = name != null ? loader.imageIORead(name) : null;
		if (img == null) {
			Logger.warn("AnalyzeSpotLineDeficits: could not read frame " + frameIndex);
			return new FramePixels(null, null, null, null);
		}
		int nC = Math.max(1, img.getSizeC());
		boolean signed = img.isSignedDataType();
		int[] red = Array1DUtil.arrayToIntArray(img.getDataXY(0), signed);
		int[] green = nC > 1 ? Array1DUtil.arrayToIntArray(img.getDataXY(1), signed) : red;
		int[] blue = nC > 2 ? Array1DUtil.arrayToIntArray(img.getDataXY(2), signed) : red;
		return new FramePixels(red, green, blue, insectMask(img));
	}

	/** Same rule as the kymograph insect filter. Null when the filter is off. */
	private boolean[] insectMask(IcyBufferedImage img) {
		Params p = analyzerParams;
		if (p == null || !p.insectGate || img == null) {
			return null;
		}
		double[] metric = KymoImageTransforms.channel0AsDouble(
				KymoImageTransforms.applyMetricTransform(img, p.insectTransform, false));
		if (metric == null) {
			return null;
		}
		boolean[] insect = new boolean[metric.length];
		for (int i = 0; i < metric.length; i++) {
			insect[i] = KymoMetricGate.directedFinite(metric[i], p.insectThreshold, p.insectAbove);
		}
		return insect;
	}

	private static final class FramePixels {
		final int[] red;
		final int[] green;
		final int[] blue;
		final boolean[] insect;

		FramePixels(int[] red, int[] green, int[] blue, boolean[] insect) {
			this.red = red;
			this.green = green;
			this.blue = blue;
			this.insect = insect;
		}
	}

	private boolean prepareExperiment(Experiment exp) {
		applySessionBinName(exp);
		exp.adoptBinSubdirectoryContainingCageKymographTiffs();
		exp.load_cages_description_and_measures();
		exp.load_spots_description_and_measures();

		SequenceCamData seqData = exp.getSeqCamData();
		if (seqData == null) {
			Logger.warn("AnalyzeSpotLineDeficits: seqCamData is null for " + exp.getResultsDirectory());
			return false;
		}
		// SequenceCamData() installs an empty Sequence, so size stays 0 until images
		// are attached. This measure reads one JPEG at a time and must not load the
		// whole stack (that exhausts memory across a series).
		ensureImageList(exp, seqData);
		if (seqData.getImageLoader().getNTotalFrames() <= 0) {
			Logger.warn("AnalyzeSpotLineDeficits: no camera images for " + exp.getResultsDirectory());
			return false;
		}
		exp.getFileIntervalsFromSeqCamData();
		long firstValidEpochMs = seqData.getFirstValidFrameEpochMs();
		if (firstValidEpochMs < 0) {
			firstValidEpochMs = exp.getCamImageFirst_ms();
		}
		if (firstValidEpochMs < 0) {
			firstValidEpochMs = 0;
		}
		exp.build_MsTimeIntervalsArray_From_SeqCamData_FileNamesList(firstValidEpochMs);
		return true;
	}

	/**
	 * The session bin is often the absolute folder of the experiment that was open
	 * when kymographs were built. Storing that path on every later experiment makes
	 * {@code save_kymo_spot_measures} overwrite the first experiment's CSV.
	 */
	private void applySessionBinName(Experiment exp) {
		if (options == null || options.expList == null) {
			return;
		}
		String sessionBin = options.expList.expListBinSubDirectory;
		if (sessionBin == null || sessionBin.isEmpty()) {
			return;
		}
		String binName = Paths.get(sessionBin).getFileName().toString();
		if (binName.isEmpty() || ".".equals(binName)) {
			return;
		}
		exp.setBinSubDirectory(binName);
	}

	private static void ensureImageList(Experiment exp, SequenceCamData seqData) {
		List<String> current = seqData.getImagesList();
		if (current == null || current.isEmpty()) {
			String dir = seqData.getImagesDirectory();
			if (dir == null || dir.isEmpty()) {
				dir = exp.getImagesDirectory();
			}
			if (dir != null && !dir.isEmpty()) {
				seqData.setImagesDirectory(dir);
				List<String> images = ExperimentDirectories.getImagesListFromPathV2(dir, "jpg");
				if (images != null && !images.isEmpty()) {
					seqData.setImagesList(images);
				}
			}
		}
		seqData.getImageLoader().getNTotalFrames();
	}

	/** Sequence size when the viewer is open; otherwise the first JPEG. */
	private static int[] cameraSize(SequenceCamData seq) {
		if (seq.getSequence() != null && seq.getSequence().getSizeX() > 0 && seq.getSequence().getSizeY() > 0) {
			return new int[] { seq.getSequence().getSizeX(), seq.getSequence().getSizeY() };
		}
		String name = seq.getFileNameFromImageList(0);
		if (name == null) {
			List<String> images = seq.getImagesList(true);
			if (images != null && !images.isEmpty()) {
				name = images.get(0);
			}
		}
		if (name == null) {
			return null;
		}
		IcyBufferedImage img = new SequenceLoaderService().imageIORead(name);
		if (img == null || img.getSizeX() <= 0 || img.getSizeY() <= 0) {
			return null;
		}
		return new int[] { img.getSizeX(), img.getSizeY() };
	}

	private static void ensureKymoBinDirectory(Experiment exp) {
		String bin = exp.getKymosBinFullDirectory();
		if (bin == null) {
			return;
		}
		File dir = new File(bin);
		if (dir.isDirectory()) {
			return;
		}
		if (!dir.mkdirs()) {
			Logger.warn("AnalyzeSpotLineDeficits: could not create kymograph bin " + bin);
		}
	}

	static List<Integer> binFrames(Experiment exp) {
		SequenceCamData seq = exp.getSeqCamData();
		int nTotal = seq.getImageLoader().getNTotalFrames();
		long[] cam = exp.getCamImages_ms();
		long first = exp.getKymoFirst_ms();
		long last = exp.getKymoLast_ms();
		long step = exp.getKymoBin_ms();
		if (step <= 0) {
			step = 60_000L;
		}
		List<Integer> frames = new ArrayList<>();
		if (last > first && cam != null && nTotal > 0) {
			int previous = -1;
			for (long t = first; t <= last; t += step) {
				int idx = closestFrame(cam, nTotal, t);
				if (idx >= 0 && idx != previous) {
					frames.add(idx);
					previous = idx;
				}
			}
		}
		if (frames.isEmpty()) {
			for (int i = 0; i < nTotal; i++) {
				frames.add(i);
			}
		}
		return frames;
	}

	private static int closestFrame(long[] camMs, int nTotal, long t) {
		int n = Math.min(nTotal, camMs.length);
		int best = -1;
		long bestDist = Long.MAX_VALUE;
		for (int i = 0; i < n; i++) {
			long dist = Math.abs(camMs[i] - t);
			if (dist < bestDist) {
				bestDist = dist;
				best = i;
			}
		}
		return best;
	}

	private static List<CageLines> cageLines(Experiment exp, int width, int height, Params params) {
		List<CageLines> out = new ArrayList<>();
		if (exp.getCages() == null || exp.getCages().cagesList == null || exp.getSpots() == null) {
			return out;
		}
		for (Cage cage : exp.getCages().cagesList) {
			if (cage == null) {
				continue;
			}
			List<Spot> spots = cage.getSpotList(exp.getSpots());
			if (spots == null || spots.isEmpty()) {
				continue;
			}
			List<SpotGeom> geoms = new ArrayList<>(spots.size());
			for (Spot spot : spots) {
				geoms.add(geomOf(spot));
			}
			Layout layout = SpotLineDeficitAnalyzer.layout(geoms, width, height, params.flankPx);
			out.add(new CageLines(spots, layout));
		}
		return out;
	}

	public static SpotGeom geomOf(Spot spot) {
		if (spot == null) {
			return new SpotGeom(0, 0, 0);
		}
		ROI2D roi = spot.getRoi();
		if (roi != null) {
			Rectangle rect = roi.getBounds();
			if (rect != null && rect.width > 0 && rect.height > 0) {
				int cx = (int) Math.round(rect.getCenterX());
				int cy = (int) Math.round(rect.getCenterY());
				int radius = (int) Math.round(Math.max(rect.getWidth(), rect.getHeight()) / 2.0);
				return new SpotGeom(cx, cy, Math.max(1, radius));
			}
		}
		int radius = spot.getProperties() != null ? spot.getProperties().getSpotRadius() : 0;
		int x = spot.getProperties() != null ? spot.getProperties().getSpotXCoord() : 0;
		int y = spot.getProperties() != null ? spot.getProperties().getSpotYCoord() : 0;
		return new SpotGeom(x, y, radius);
	}

	private static void copyDoubles(SpotMeasure measure, double[] src) {
		if (measure == null) {
			return;
		}
		if (src == null || src.length == 0) {
			measure.setValues(new double[0]);
			return;
		}
		measure.setValues(src.clone());
	}

	private static final class CageLines {
		final List<Spot> spots;
		final Layout layout;
		double[][] flanks;
		double[][] integral;
		double noise;

		CageLines(List<Spot> spots, Layout layout) {
			this.spots = spots;
			this.layout = layout;
		}
	}
}
