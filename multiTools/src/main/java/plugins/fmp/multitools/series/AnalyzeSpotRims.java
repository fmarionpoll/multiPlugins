package plugins.fmp.multitools.series;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import icy.gui.frame.progress.ProgressFrame;
import icy.image.IcyBufferedImage;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ExperimentDirectories;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotMeasure;
import plugins.fmp.multitools.experiment.spot.SpotRimGeometry;
import plugins.fmp.multitools.service.KymoImageTransforms;
import plugins.fmp.multitools.service.KymoMetricGate;
import plugins.fmp.multitools.service.SequenceLoaderService;
import plugins.fmp.multitools.service.SpotLineDeficitAnalyzer;
import plugins.fmp.multitools.service.SpotLineDeficitAnalyzer.Layout;
import plugins.fmp.multitools.service.SpotRimAnalyzer;
import plugins.fmp.multitools.service.SpotRimAnalyzer.EllipseGeom;
import plugins.fmp.multitools.service.SpotRimAnalyzer.Params;
import plugins.fmp.multitools.tools.Logger;

/**
 * Samples the physical rim of each spot on the camera frames. The zero is the
 * outward band. Stores {@code KYMO_RIM_RATIO}. When {@link #detectOnly} is
 * set, only missing outlines are written.
 */
public class AnalyzeSpotRims extends BuildSeries {

	public Params analyzerParams;
	public boolean detectOnly;
	public volatile int lastCageCount;
	public volatile int lastBinCount;
	public volatile int lastOutlineCount;
	public volatile boolean lastSaved;

	@Override
	void analyzeExperiment(Experiment exp) {
		if (exp == null || analyzerParams == null) {
			return;
		}
		lastSaved = false;
		lastOutlineCount = 0;
		ProgressFrame progress = null;
		try {
			if (!prepareExperiment(exp)) {
				Logger.warn("AnalyzeSpotRims: could not prepare experiment " + exp.getResultsDirectory());
				return;
			}
			SequenceCamData seq = exp.getSeqCamData();
			int[] camera = cameraSize(seq);
			if (camera == null) {
				Logger.warn("AnalyzeSpotRims: camera size unknown for " + exp.getResultsDirectory());
				return;
			}
			int width = camera[0];
			int height = camera[1];
			List<Integer> frames = binFrames(exp);
			if (frames.isEmpty()) {
				Logger.warn("AnalyzeSpotRims: no frames for " + exp.getResultsDirectory());
				return;
			}
			List<CageRims> cages = cageRims(exp);
			if (cages.isEmpty()) {
				Logger.warn("AnalyzeSpotRims: no spots for " + exp.getResultsDirectory());
				return;
			}
			int nFrames = frames.size();
			int window = Math.min(analyzerParams.initialBins, nFrames);
			SequenceLoaderService loader = new SequenceLoaderService();
			progress = new ProgressFrame(detectOnly ? "Detect rims" : "Analyze rim ratio");
			FramePixels[] opening = new FramePixels[window];
			for (int t = 0; t < window; t++) {
				if (stopFlag) {
					return;
				}
				showFrame(progress, t, nFrames);
				opening[t] = readFrame(seq, loader, frames.get(t));
			}
			lastOutlineCount = ensureOutlines(cages, width, height, opening);
			lastCageCount = cages.size();
			lastBinCount = nFrames;
			boolean descriptions = saveDescriptions(exp);
			if (detectOnly) {
				lastSaved = descriptions;
				Logger.info("AnalyzeSpotRims: detected " + lastOutlineCount + " outline(s) — "
						+ exp.getResultsDirectory());
				return;
			}
			if (lastOutlineCount <= 0) {
				Logger.warn("AnalyzeSpotRims: no outline for " + exp.getResultsDirectory());
				return;
			}
			List<EllipseGeom> all = allEllipses(cages);
			for (CageRims cage : cages) {
				cage.layout = SpotRimAnalyzer.layoutFor(width, height, cage.spots, cage.ellipses, all);
				cage.flanks = new double[nFrames][];
				cage.integral = new double[cage.spots.size()][nFrames];
				cage.openingExcess = new double[cage.spots.size()][window];
			}
			for (int t = 0; t < window; t++) {
				sampleOpening(cages, opening[t], t);
			}
			for (CageRims cage : cages) {
				cage.noise = SpotLineDeficitAnalyzer.poolMad(cage.flanks, window, analyzerParams.madMultiplier);
				cage.dyeLevel = new double[cage.spots.size()];
				for (int s = 0; s < cage.spots.size(); s++) {
					cage.dyeLevel[s] = SpotLineDeficitAnalyzer.referenceExcess(cage.openingExcess[s]);
				}
			}
			for (int t = 0; t < window; t++) {
				integrateFrame(cages, opening[t], t);
			}
			for (int t = window; t < nFrames; t++) {
				if (stopFlag) {
					return;
				}
				showFrame(progress, t, nFrames);
				FramePixels frame = readFrame(seq, loader, frames.get(t));
				sampleOpening(cages, frame, t);
				integrateFrame(cages, frame, t);
			}
			for (CageRims cage : cages) {
				double[][] ratios = SpotLineDeficitAnalyzer.ratiosFromIntegrals(cage.integral,
						analyzerParams.initialBins, analyzerParams.smoothBins);
				for (int i = 0; i < cage.spots.size(); i++) {
					if (!cage.spots.get(i).getRimGeometry().hasOutline()) {
						continue;
					}
					copyDoubles(cage.spots.get(i).getKymoRimRatio(), i < ratios.length ? ratios[i] : null);
				}
			}
			if (exp.getCages() != null) {
				exp.getCages().clearSpotAggregatesCache();
			}
			ensureKymoBinDirectory(exp);
			lastSaved = exp.save_kymo_spot_measures();
			if (!lastSaved) {
				Logger.warn("AnalyzeSpotRims: ratios computed but SpotsMeasures.csv was not saved for "
						+ exp.getResultsDirectory());
			}
			Logger.info("AnalyzeSpotRims: " + lastCageCount + " cage(s), " + lastBinCount + " bin(s), "
					+ lastOutlineCount + " outline(s) — " + exp.getResultsDirectory());
		} finally {
			if (progress != null) {
				progress.close();
			}
			exp.releaseKymographSequence();
			exp.closeSequences();
		}
	}

	private int ensureOutlines(List<CageRims> cages, int width, int height, FramePixels[] opening) {
		int nFrames = opening.length;
		int[][] red = new int[nFrames][];
		int[][] green = new int[nFrames][];
		int[][] blue = new int[nFrames][];
		for (int t = 0; t < nFrames; t++) {
			FramePixels frame = opening[t];
			if (frame == null) {
				continue;
			}
			red[t] = frame.red;
			green[t] = frame.green;
			blue[t] = frame.blue;
		}
		List<EllipseGeom> all = allEllipses(cages);
		EllipseGeom[] containers = all.toArray(new EllipseGeom[0]);
		int outlines = 0;
		for (CageRims cage : cages) {
			for (int i = 0; i < cage.spots.size(); i++) {
				Spot spot = cage.spots.get(i);
				SpotRimGeometry rim = spot.getRimGeometry();
				rim.setRimWidthPx(analyzerParams.rimWidthPx);
				rim.setOuterPx(analyzerParams.outerPx);
				if (!rim.hasOutline()) {
					EllipseGeom ellipse = cage.ellipses.get(i);
					double[][] xy = SpotRimAnalyzer.detect(ellipse, width, height, red, green, blue, containers,
							analyzerParams.madMultiplier);
					if (xy != null) {
						rim.setOutline(xy[0], xy[1]);
					}
				}
				if (rim.hasOutline()) {
					outlines++;
				}
			}
		}
		return outlines;
	}

	private static void sampleOpening(List<CageRims> cages, FramePixels frame, int t) {
		for (CageRims cage : cages) {
			if (cage.layout == null || cage.flanks == null) {
				continue;
			}
			cage.flanks[t] = SpotLineDeficitAnalyzer.sampleFlanks(cage.layout, frame.red, frame.green, frame.blue,
					frame.insect);
			if (cage.openingExcess != null && t < cage.openingExcess[0].length) {
				double[] floors = SpotLineDeficitAnalyzer.spotFloors(cage.layout, cage.flanks[t]);
				for (int s = 0; s < cage.openingExcess.length; s++) {
					double floor = floors != null && s < floors.length ? floors[s] : 0.0;
					cage.openingExcess[s][t] = SpotLineDeficitAnalyzer.medianSignalExcess(cage.layout, s, frame.red,
							frame.green, frame.blue, floor, frame.insect);
				}
			}
		}
	}

	private static void integrateFrame(List<CageRims> cages, FramePixels frame, int t) {
		for (CageRims cage : cages) {
			if (cage.layout == null) {
				continue;
			}
			double[] floors = SpotLineDeficitAnalyzer.spotFloors(cage.layout, cage.flanks[t]);
			SpotLineDeficitAnalyzer.integrate(cage.layout, frame.red, frame.green, frame.blue, floors, cage.noise,
					cage.integral, t, frame.insect, cage.dyeLevel);
		}
	}

	private static boolean saveDescriptions(Experiment exp) {
		if (exp.getSpots() == null) {
			return false;
		}
		String dir = exp.getResultsDirectory();
		if (dir == null) {
			return false;
		}
		return exp.getSpots().getPersistence().saveDescriptions(exp.getSpots(), dir);
	}

	private static List<EllipseGeom> allEllipses(List<CageRims> cages) {
		List<EllipseGeom> all = new ArrayList<>();
		for (CageRims cage : cages) {
			all.addAll(cage.ellipses);
		}
		return all;
	}

	private static List<CageRims> cageRims(Experiment exp) {
		List<CageRims> out = new ArrayList<>();
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
			List<EllipseGeom> ellipses = new ArrayList<>(spots.size());
			for (Spot spot : spots) {
				ellipses.add(SpotRimAnalyzer.ellipseOf(spot));
			}
			out.add(new CageRims(spots, ellipses));
		}
		return out;
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
			Logger.warn("AnalyzeSpotRims: could not read frame " + frameIndex);
			return new FramePixels(null, null, null, null);
		}
		int nC = Math.max(1, img.getSizeC());
		boolean signed = img.isSignedDataType();
		int[] red = Array1DUtil.arrayToIntArray(img.getDataXY(0), signed);
		int[] green = nC > 1 ? Array1DUtil.arrayToIntArray(img.getDataXY(1), signed) : red;
		int[] blue = nC > 2 ? Array1DUtil.arrayToIntArray(img.getDataXY(2), signed) : red;
		return new FramePixels(red, green, blue, insectMask(img));
	}

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

	private boolean prepareExperiment(Experiment exp) {
		applySessionBinName(exp);
		exp.adoptBinSubdirectoryContainingCageKymographTiffs();
		exp.load_cages_description_and_measures();
		exp.load_spots_description_and_measures();

		SequenceCamData seqData = exp.getSeqCamData();
		if (seqData == null) {
			Logger.warn("AnalyzeSpotRims: seqCamData is null for " + exp.getResultsDirectory());
			return false;
		}
		ensureImageList(exp, seqData);
		if (seqData.getImageLoader().getNTotalFrames() <= 0) {
			Logger.warn("AnalyzeSpotRims: no camera images for " + exp.getResultsDirectory());
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
			Logger.warn("AnalyzeSpotRims: could not create kymograph bin " + bin);
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

	private static final class CageRims {
		final List<Spot> spots;
		final List<EllipseGeom> ellipses;
		Layout layout;
		double[][] flanks;
		double[][] integral;
		double noise;
		double[] dyeLevel;
		double[][] openingExcess;

		CageRims(List<Spot> spots, List<EllipseGeom> ellipses) {
			this.spots = spots;
			this.ellipses = ellipses;
		}
	}
}
