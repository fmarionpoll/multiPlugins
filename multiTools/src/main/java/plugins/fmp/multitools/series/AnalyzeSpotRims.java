package plugins.fmp.multitools.series;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import icy.gui.frame.progress.ProgressFrame;
import icy.image.IcyBufferedImage;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.spot.SpotDetectionProvenance;
import plugins.fmp.multitools.experiment.ExperimentDirectories;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotMeasure;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMeasurementMethod;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMethodRecording;
import plugins.fmp.multitools.experiment.spot.measurement.SpotSampleFlags;
import plugins.fmp.multitools.service.SpotAcquisitionTimeline;
import plugins.fmp.multitools.experiment.spot.SpotRimGeometry;
import plugins.fmp.multitools.service.KymoImageTransforms;
import plugins.fmp.multitools.service.KymoMetricGate;
import plugins.fmp.multitools.service.SequenceLoaderService;
import plugins.fmp.multitools.service.SpotLineDeficitAnalyzer;
import plugins.fmp.multitools.service.SpotLineDeficitAnalyzer.Layout;
import plugins.fmp.multitools.service.SpotPlateRegistration;
import plugins.fmp.multitools.service.SpotRimAnalyzer;
import plugins.fmp.multitools.service.SpotRimAnalyzer.EllipseGeom;
import plugins.fmp.multitools.service.SpotRimAnalyzer.Params;
import plugins.fmp.multitools.tools.Logger;

/**
 * Samples the physical rim of each spot on the camera frames. The zero is the
 * outward band. Stores {@code KYMO_RIM_RATIO}. When {@link #detectOnly} is
 * set, outlines are detected again for the current experiment.
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
			MeasurementProgress.phase("Finding spot rims");
			FramePixels[] opening = new FramePixels[window];
			for (int t = 0; t < window; t++) {
				if (stopFlag) {
					return;
				}
				showFrame(progress, t, nFrames);
				opening[t] = readFrame(seq, loader, frames.get(t));
			}
			PlateTrack plate = alignOpening(cages, opening, width, height);
			List<String> concave = new ArrayList<>();
			lastOutlineCount = ensureOutlines(exp, cages, width, height, opening, detectOnly, concave);
			// Retry only outlines provisionally generated under a fly in this run.
			// Saved (possibly hand-edited) outlines are never candidates here.
			PlateTrack outlineTrack = plate.copy();
			for (int t = window; t < nFrames && hasPendingOutlines(cages); t++) {
				if (stopFlag) {
					return;
				}
				showFrame(progress, t, nFrames);
				FramePixels frame = outlineTrack.follow(readFrame(seq, loader, frames.get(t)), width, height);
				retryOutlines(cages, frame, width, height, analyzerParams.madMultiplier, concave);
			}
			reportConcave(exp, concave);
			lastCageCount = cages.size();
			lastBinCount = nFrames;
			boolean descriptions = saveDescriptions(exp);
			if (detectOnly) {
				lastSaved = descriptions;
				Logger.info("AnalyzeSpotRims: detected " + lastOutlineCount + " outline(s) — "
						+ exp.getResultsDirectory());
				return;
			}
			if (lastOutlineCount <= 0 && !hasPreConsumedSpots(cages)) {
				Logger.warn("AnalyzeSpotRims: no outline for " + exp.getResultsDirectory());
				return;
			}
			List<EllipseGeom> all = allEllipses(cages);
			MeasurementProgress.phase("Finding initial spot references");
			double[] primaryTimes = SpotAcquisitionTimeline.camera(exp, frames);
			for (CageRims cage : cages) {
				cage.layout = SpotRimAnalyzer.layoutFor(width, height, cage.spots, cage.ellipses, all);
				cage.flanks = new double[nFrames][];
				cage.integral = new double[cage.spots.size()][nFrames];
				cage.rawIntegral = new double[cage.spots.size()][nFrames];
				for (double[] values : cage.rawIntegral) { Arrays.fill(values, Double.NaN); }
				cage.insectGate = analyzerParams.insectGate;
				cage.primary = new SpotMethodRecording[cage.spots.size()];
				for (int s = 0; s < cage.primary.length; s++) {
					cage.primary[s] = new SpotMethodRecording(SpotMeasurementMethod.RIM_LINES, primaryTimes, "occupancy_fraction")
							.parameter("implementation", getClass().getSimpleName())
							.parameter("timeline_basis", "camera_acquisition_times")
							.parameter("insect_gate", String.valueOf(analyzerParams.insectGate))
							.parameter("insect_transform", String.valueOf(analyzerParams.insectTransform))
							.parameter("insect_threshold", String.valueOf(analyzerParams.insectThreshold))
							.parameter("insect_above", String.valueOf(analyzerParams.insectAbove))
							.parameter("initial_bins", String.valueOf(analyzerParams.initialBins))
							.parameter("mad_multiplier", String.valueOf(analyzerParams.madMultiplier))
							.parameter("rim_width_px", String.valueOf(analyzerParams.rimWidthPx))
							.parameter("outer_px", String.valueOf(analyzerParams.outerPx))
							.parameter("floor_width_px", String.valueOf(analyzerParams.floorWidthPx))
							.parameter("track_plate", String.valueOf(analyzerParams.trackPlate))
							.parameter("occlusion_fraction", String.valueOf(SpotLineDeficitAnalyzer.FLY_OCCUPANCY_FRACTION))
							.quantity("signal_fraction", "fraction").quantity("visible_signal_fraction", "fraction")
							.quantity("background", "color_deficit_units");
				}
				cage.openingExcess = new double[cage.spots.size()][window];
				cage.openingCount = new int[cage.spots.size()];
				for (double[] values : cage.integral) {
					Arrays.fill(values, Double.NaN);
				}
				for (double[] values : cage.openingExcess) {
					Arrays.fill(values, Double.NaN);
				}
			}
			for (int t = 0; t < window; t++) {
				sampleOpening(cages, opening[t], t);
			}
			for (CageRims cage : cages) {
				cage.refFloor = SpotLineDeficitAnalyzer.referenceFloors(cage.layout, cage.flanks, window);
			}
			for (int t = 0; t < window; t++) {
				collectOpening(cages, opening[t], t);
			}
			PlateTrack calibrationTrack = plate.copy();
			for (int t = window; t < nFrames && !openingComplete(cages, window); t++) {
				if (stopFlag) {
					return;
				}
				showFrame(progress, t, nFrames);
				FramePixels frame = calibrationTrack.follow(readFrame(seq, loader, frames.get(t)), width, height);
				collectOpening(cages, frame, t);
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
			MeasurementProgress.phase("Measuring images");
			for (int t = window; t < nFrames; t++) {
				if (stopFlag) {
					return;
				}
				showFrame(progress, t, nFrames);
				FramePixels frame = readFrame(seq, loader, frames.get(t));
				frame = plate.follow(frame, width, height);
				sampleOpening(cages, frame, t);
				integrateFrame(cages, frame, t);
			}
			for (CageRims cage : cages) {
				double[][] ratios = SpotLineDeficitAnalyzer.ratiosFromIntegrals(cage.integral,
						analyzerParams.initialBins, analyzerParams.smoothBins);
				for (int i = 0; i < cage.spots.size(); i++) {
					if (i < ratios.length) {
						SpotLineDeficitAnalyzer.finalizeRatio(ratios[i], cage.dyeLevel[i], cage.noise,
								cage.spots.get(i).isConsumedBeforeRecording());
					}
					copyDoubles(cage.spots.get(i).getKymographMeasurements().getRimCoverageFraction(), cage.integral[i]);
					copyDoubles(cage.spots.get(i).getRimCoverageToBaselineRatio(), i < ratios.length ? ratios[i] : null);
					cage.primary[i].parameter("noise", String.valueOf(cage.noise)).parameter("dye_level", String.valueOf(cage.dyeLevel[i]));
					cage.spots.get(i).getMethodMeasurements().put(cage.primary[i].build());
				}
			}
			if (exp.getCages() != null) {
				exp.getCages().clearSpotAggregatesCache();
			}
			ensureKymoBinDirectory(exp);
			MeasurementProgress.phase("Saving measurements");
			lastSaved = exp.save_kymo_spot_measures();
			if (lastSaved) {
				SpotDetectionProvenance.recordCameraLine(exp, SpotDetectionProvenance.Family.KYMO_RIM,
						getClass().getSimpleName(), "initial_bins", analyzerParams.initialBins,
						"smoothing_bins", analyzerParams.smoothBins, "mad_multiplier", analyzerParams.madMultiplier,
						"insect_gate", analyzerParams.insectGate, "insect_transform", analyzerParams.insectTransform,
						"insect_threshold", analyzerParams.insectThreshold, "insect_threshold_above",
						analyzerParams.insectAbove, "rim_width_px", analyzerParams.rimWidthPx, "outer_px", analyzerParams.outerPx,
						"floor_width_px", analyzerParams.floorWidthPx, "track_plate", analyzerParams.trackPlate);
			}
			if (!lastSaved) {
				Logger.warn("AnalyzeSpotRims: ratios computed but SpotsMeasures.csv was not saved for "
						+ exp.getResultsDirectory());
			}
			Logger.info("AnalyzeSpotRims: " + lastCageCount + " cage(s), " + lastBinCount + " bin(s), "
					+ lastOutlineCount + " outline(s) — " + exp.getResultsDirectory());
			if (plate.tracking) {
				Logger.info(String.format("AnalyzeSpotRims: plate pose dx=%.2f dy=%.2f angle=%.3f deg", plate.pose.dx,
						plate.pose.dy, Math.toDegrees(plate.pose.angleRad)));
			}
		} finally {
			if (progress != null) {
				progress.close();
			}
			exp.releaseKymographSequence();
			exp.closeSequences();
		}
	}

	/**
	 * When plate tracking is on, rewrites {@code opening} so every frame sits in
	 * the coordinates of frame 0. The returned tracker keeps the pose of the last
	 * opening frame and the raw pixels of that frame.
	 */
	private PlateTrack alignOpening(List<CageRims> cages, FramePixels[] opening, int width, int height) {
		List<EllipseGeom> ellipses = allEllipses(cages);
		double[] pivot = SpotPlateRegistration.pivot(ellipses);
		PlateTrack plate = new PlateTrack(analyzerParams != null && analyzerParams.trackPlate, ellipses,
				SpotPlateRegistration.Pose.identity(pivot[0], pivot[1]));
		if (!plate.tracking || opening.length == 0) {
			return plate;
		}
		plate.prevRaw = opening[0];
		for (int t = 1; t < opening.length; t++) {
			opening[t] = plate.follow(opening[t], width, height);
		}
		return plate;
	}

	private void reportConcave(Experiment exp, List<String> concave) {
		if (concave == null || concave.isEmpty()) {
			return;
		}
		Logger.info(experimentHeader(exp));
		Logger.info("Concave rims filled: " + String.join(", ", concave));
	}

	private static String directoryName(Experiment exp) {
		String dir = exp != null ? exp.getResultsDirectory() : null;
		if (dir == null || dir.isEmpty()) {
			return "";
		}
		String name = Paths.get(dir).getFileName().toString();
		return name != null ? name : dir;
	}

	private String experimentHeader(Experiment exp) {
		String dir = exp.getResultsDirectory() != null ? exp.getResultsDirectory() : "";
		int number = experimentNumber(exp);
		if (number > 0) {
			return "Experiment " + number + ": " + dir;
		}
		return "Experiment: " + dir;
	}

	private int experimentNumber(Experiment exp) {
		if (options == null || options.expList == null || exp == null) {
			return -1;
		}
		int n = options.expList.getItemCount();
		for (int i = 0; i < n; i++) {
			if (options.expList.getItemAtNoLoad(i) == exp) {
				return i + 1;
			}
		}
		return -1;
	}

	private int ensureOutlines(Experiment exp, List<CageRims> cages, int width, int height, FramePixels[] opening,
			boolean replace, List<String> concave) {
		int nFrames = opening.length;
		int[][] red = new int[nFrames][];
		int[][] green = new int[nFrames][];
		int[][] blue = new int[nFrames][];
		boolean[][] insect = new boolean[nFrames][];
		for (int t = 0; t < nFrames; t++) {
			FramePixels frame = opening[t];
			if (frame == null) {
				continue;
			}
			red[t] = frame.red;
			green[t] = frame.green;
			blue[t] = frame.blue;
			insect[t] = frame.insect;
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
				rim.setFloorWidthPx(analyzerParams.floorWidthPx);
				if (replace || !rim.hasOutline()) {
					EllipseGeom ellipse = cage.ellipses.get(i);
					SpotRimAnalyzer.Detection found = SpotRimAnalyzer.detect(ellipse, width, height, red, green, blue,
							insect, containers, analyzerParams.madMultiplier);
					if (found != null && found.flyFallback) {
						cage.retryOutline[i] = !spot.isConsumedBeforeRecording();
						rim.setOutline(found.x, found.y);
						Logger.warn(experimentNumber(exp) + " " + directoryName(exp) + " " + spot.getName()
								+ " fly covers this spot in every opening frame; provisional outline set to the spot ROI minus 30%; later frames will be checked");
					} else if (found != null) {
						double[][] filled = SpotRimGeometry.withoutInwardBite(found.x, found.y,
								SpotRimGeometry.INWARD_BITE_PX);
						if (filled != null) {
							rim.setOutline(filled[0], filled[1]);
							concave.add(cageLabel(cage) + " / " + spot.getName());
						} else {
							rim.setOutline(found.x, found.y);
						}
						rim.setRimWidthPx(found.pathWidthPx);
					}
				}
				if (rim.hasOutline()) {
					outlines++;
				}
			}
		}
		return outlines;
	}

	private static boolean hasPendingOutlines(List<CageRims> cages) {
		for (CageRims cage : cages) {
			for (boolean pending : cage.retryOutline) {
				if (pending) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean hasPreConsumedSpots(List<CageRims> cages) {
		for (CageRims cage : cages) {
			for (Spot spot : cage.spots) {
				if (spot.isConsumedBeforeRecording()) {
					return true;
				}
			}
		}
		return false;
	}

	static void retryOutlines(List<CageRims> cages, FramePixels frame, int width, int height,
			double madMultiplier, List<String> concave) {
		if (frame == null || frame.red == null) {
			return;
		}
		EllipseGeom[] containers = allEllipses(cages).toArray(new EllipseGeom[0]);
		for (CageRims cage : cages) {
			for (int s = 0; s < cage.spots.size(); s++) {
				if (!cage.retryOutline[s]) {
					continue;
				}
				SpotRimAnalyzer.Detection found = SpotRimAnalyzer.detect(cage.ellipses.get(s), width, height,
						new int[][] { frame.red }, new int[][] { frame.green }, new int[][] { frame.blue },
						new boolean[][] { frame.insect }, containers, madMultiplier);
				if (found != null && found.flyFallback) {
					continue;
				}
				// If the first clear view is empty, retain the provisional ellipse
				// so the spot can still be measured as empty.
				cage.retryOutline[s] = false;
				if (found != null) {
					Spot spot = cage.spots.get(s);
					double[][] filled = SpotRimGeometry.withoutInwardBite(found.x, found.y,
							SpotRimGeometry.INWARD_BITE_PX);
					spot.getRimGeometry().setOutline(filled != null ? filled[0] : found.x,
							filled != null ? filled[1] : found.y);
					spot.getRimGeometry().setRimWidthPx(found.pathWidthPx);
					if (filled != null) {
						concave.add(cageLabel(cage) + " / " + spot.getName());
					}
				}
			}
		}
	}

	private static String cageLabel(CageRims cage) {
		if (cage.cage == null) {
			return "cage ?";
		}
		String number = cage.cage.getCageNumberFromRoiName();
		if (number == null || number.isEmpty()) {
			number = cage.cage.formatCageNumberToString(cage.cage.getCageID());
		}
		return "cage " + number;
	}

	private static void sampleOpening(List<CageRims> cages, FramePixels frame, int t) {
		for (CageRims cage : cages) {
			if (cage.layout == null || cage.flanks == null) {
				continue;
			}
			cage.flanks[t] = SpotLineDeficitAnalyzer.sampleFlanks(cage.layout, frame.red, frame.green, frame.blue,
					frame.insect);
		}
	}

	static void collectOpening(List<CageRims> cages, FramePixels frame, int t) {
		sampleOpening(cages, frame, t);
		for (CageRims cage : cages) {
			if (cage.openingExcess != null) {
				double[] current = SpotLineDeficitAnalyzer.spotFloors(cage.layout, cage.flanks[t]);
				for (int s = 0; s < cage.refFloor.length; s++) {
					if (!Double.isFinite(cage.refFloor[s]) && Double.isFinite(current[s])) {
						cage.refFloor[s] = current[s];
					}
				}
				double[] floors = SpotLineDeficitAnalyzer.floorsWithReference(cage.layout, cage.flanks[t],
						cage.refFloor);
				for (int s = 0; s < cage.openingExcess.length; s++) {
					int count = cage.openingCount[s];
					if (count >= cage.openingExcess[s].length || cage.spots.get(s).isConsumedBeforeRecording()) {
						continue;
					}
					double floor = floors != null && s < floors.length ? floors[s] : Double.NaN;
					if (!Double.isFinite(floor)) {
						continue;
					}
					double excess = SpotLineDeficitAnalyzer.medianSignalExcess(cage.layout, s, frame.red,
							frame.green, frame.blue, floor, frame.insect);
					if (Double.isFinite(excess)) {
						cage.openingExcess[s][count] = excess;
						cage.openingCount[s]++;
					}
				}
			}
		}
	}

	static boolean openingComplete(List<CageRims> cages, int window) {
		for (CageRims cage : cages) {
			for (int s = 0; s < cage.spots.size(); s++) {
				if (cage.spots.get(s).getRimGeometry().hasOutline()
						&& !cage.spots.get(s).isConsumedBeforeRecording() && cage.openingCount[s] < window) {
					return false;
				}
			}
		}
		return true;
	}

	private static void integrateFrame(List<CageRims> cages, FramePixels frame, int t) {
		for (CageRims cage : cages) {
			if (cage.layout == null) {
				continue;
			}
			double[] floors = SpotLineDeficitAnalyzer.floorsWithReference(cage.layout, cage.flanks[t], cage.refFloor);
			SpotLineDeficitAnalyzer.integrate(cage.layout, frame.red, frame.green, frame.blue, floors, cage.noise,
					cage.integral, t, frame.insect, cage.dyeLevel);
			SpotLineDeficitAnalyzer.integrate(cage.layout, frame.red, frame.green, frame.blue, floors, cage.noise,
					cage.rawIntegral, t, null, cage.dyeLevel);
			for (int s = 0; s < cage.spots.size(); s++) {
				double occupancy = frame.insect != null ? SpotLineDeficitAnalyzer.flyOccupancy(cage.layout, s, frame.insect) : Double.NaN;
				int flags = frame.red == null ? SpotSampleFlags.MISSING_IMAGE : 0;
				if (!Double.isFinite(floors[s])) { flags |= SpotSampleFlags.INVALID_BACKGROUND; }
				if (Double.isFinite(occupancy) && occupancy >= SpotLineDeficitAnalyzer.FLY_OCCUPANCY_FRACTION) { flags |= SpotSampleFlags.OCCLUDED; }
				if (frame.red != null && frame.insect == null && cage.insectGate) { flags |= SpotSampleFlags.QUALITY_UNKNOWN; }
				cage.primary[s].recordFlyEvidence(t, occupancy);
				cage.primary[s].record("signal_fraction", t, cage.rawIntegral[s][t], flags);
				cage.primary[s].record("visible_signal_fraction", t, cage.integral[s][t], flags);
				cage.primary[s].record("background", t, floors[s], frame.red == null ? SpotSampleFlags.MISSING_IMAGE : 0);
			}
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
			out.add(new CageRims(cage, spots, ellipses));
		}
		return out;
	}

	private static void showFrame(ProgressFrame progress, int t, int n) {
		MeasurementProgress.images(t + 1, n);
		if (progress != null) {
			progress.setLength(n);
			progress.setPosition(t + 1);
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
		return SpotLineDeficitAnalyzer.dilateInsects(insect, img.getSizeX(), img.getSizeY(),
				SpotLineDeficitAnalyzer.FLY_FLOOR_DILATE_PX);
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
		return plugins.fmp.multitools.experiment.spot.SpotLineSampling.cameraFrames(exp);
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
		measure.setCameraSampled(true);
	}

	private static final class PlateTrack {
		final boolean tracking;
		final List<EllipseGeom> referenceEllipses;
		SpotPlateRegistration.Pose pose;
		FramePixels prevRaw;

		PlateTrack(boolean tracking, List<EllipseGeom> referenceEllipses, SpotPlateRegistration.Pose pose) {
			this.tracking = tracking;
			this.referenceEllipses = referenceEllipses;
			this.pose = pose;
		}

		PlateTrack copy() {
			PlateTrack copy = new PlateTrack(tracking, referenceEllipses, pose);
			copy.prevRaw = prevRaw;
			return copy;
		}

		FramePixels follow(FramePixels frame, int width, int height) {
			if (!tracking || frame == null || frame.red == null) {
				return frame;
			}
			if (prevRaw != null && prevRaw.red != null) {
				double[] pivot = pose.map(pose.cx, pose.cy);
				List<EllipseGeom> where = new ArrayList<>(referenceEllipses.size());
				for (EllipseGeom ellipse : referenceEllipses) {
					where.add(ellipse == null ? null : pose.mapEllipse(ellipse));
				}
				SpotPlateRegistration.Step step = SpotPlateRegistration.fit(width, height, prevRaw.red, prevRaw.green,
						prevRaw.blue, frame.red, frame.green, frame.blue, prevRaw.insect, frame.insect, where, pivot[0],
						pivot[1]);
				pose = pose.compose(step);
			}
			prevRaw = frame;
			if (pose.isIdentity()) {
				return frame;
			}
			return new FramePixels(SpotPlateRegistration.warp(frame.red, width, height, pose),
					SpotPlateRegistration.warp(frame.green, width, height, pose),
					SpotPlateRegistration.warp(frame.blue, width, height, pose),
					SpotPlateRegistration.warpMask(frame.insect, width, height, pose));
		}
	}

	static final class FramePixels {
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

	static final class CageRims {
		final Cage cage;
		final List<Spot> spots;
		final List<EllipseGeom> ellipses;
		Layout layout;
		double[][] flanks;
		double[][] integral;
		double[][] rawIntegral;
		SpotMethodRecording[] primary;
		boolean insectGate;
		double noise;
		double[] refFloor;
		double[] dyeLevel;
		double[][] openingExcess;
		int[] openingCount;
		final boolean[] retryOutline;

		CageRims(Cage cage, List<Spot> spots, List<EllipseGeom> ellipses) {
			this.cage = cage;
			this.spots = spots;
			this.ellipses = ellipses;
			this.retryOutline = new boolean[spots.size()];
		}
	}
}
