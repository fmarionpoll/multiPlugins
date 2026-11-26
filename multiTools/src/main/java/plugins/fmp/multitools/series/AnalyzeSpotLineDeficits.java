package plugins.fmp.multitools.series;

import java.awt.Rectangle;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import icy.gui.frame.progress.ProgressFrame;
import icy.image.IcyBufferedImage;
import icy.roi.ROI2D;
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
import plugins.fmp.multitools.service.KymoImageTransforms;
import plugins.fmp.multitools.service.KymoMetricGate;
import plugins.fmp.multitools.service.SequenceLoaderService;
import plugins.fmp.multitools.service.SpotLineDeficitAnalyzer;
import plugins.fmp.multitools.service.SpotBackgroundEstimator;
import plugins.fmp.multitools.service.SpotBackgroundEstimator.Sample;
import plugins.fmp.multitools.service.SpotLineDyeReference;
import plugins.fmp.multitools.service.SpotLineDeficitAnalyzer.Layout;
import plugins.fmp.multitools.service.SpotLineDeficitAnalyzer.Params;
import plugins.fmp.multitools.service.SpotLineDeficitAnalyzer.SpotGeom;
import plugins.fmp.multitools.tools.Logger;

/**
 * Samples each spot's X on camera frames. Background comes from clean branches,
 * nearby cage-interior patches, or a validated opening reference, in that order.
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
			double[] primaryTimes = SpotAcquisitionTimeline.camera(exp, frames);
			for (CageLines cage : cages) {
				cage.flanks = new double[nFrames][];
				cage.backgrounds = new Sample[nFrames];
				cage.integral = new double[cage.spots.size()][nFrames];
				cage.primary = new SpotMethodRecording[cage.spots.size()];
				cage.insectGate = analyzerParams.insectGate;
				for (int s = 0; s < cage.primary.length; s++) {
					cage.primary[s] = new SpotMethodRecording(SpotMeasurementMethod.X_LINES, primaryTimes, "occupancy_fraction")
							.parameter("implementation", getClass().getSimpleName())
							.parameter("timeline_basis", "camera_acquisition_times")
							.parameter("insect_gate", String.valueOf(analyzerParams.insectGate))
							.parameter("insect_transform", String.valueOf(analyzerParams.insectTransform))
							.parameter("insect_threshold", String.valueOf(analyzerParams.insectThreshold))
							.parameter("insect_above", String.valueOf(analyzerParams.insectAbove))
							.parameter("initial_bins", String.valueOf(analyzerParams.initialBins))
							.parameter("mad_multiplier", String.valueOf(analyzerParams.madMultiplier))
							.parameter("band_width_px", String.valueOf(analyzerParams.bandWidthPx))
							.parameter("flank_px", String.valueOf(analyzerParams.flankPx))
							.parameter("occlusion_fraction", String.valueOf(SpotLineDeficitAnalyzer.FLY_OCCUPANCY_FRACTION))
							.quantity("signal_fraction", "fraction").quantity("visible_signal_fraction", "fraction")
							.quantity("background", "color_deficit_units");
				}
			}
			SequenceLoaderService loader = new SequenceLoaderService();
			progress = new ProgressFrame("Analyze line ratio");
			MeasurementProgress.phase("Finding initial spot references");
			if (!readMeasures(seq, loader, frames, cages, progress)) {
				return;
			}
			for (CageLines cage : cages) {
				double[][] ratios = SpotLineDeficitAnalyzer.ratiosFromDyeIntegrals(cage.integral,
						analyzerParams.initialBins, analyzerParams.smoothBins);
				for (int i = 0; i < cage.spots.size(); i++) {
					double[] row = i < ratios.length ? ratios[i] : null;
					if (row != null) {
						double dye = cage.dyeLevel != null && i < cage.dyeLevel.length ? cage.dyeLevel[i] : Double.NaN;
						SpotLineDeficitAnalyzer.finalizeDyeRatio(row, dye, cage.noise,
								cage.spots.get(i).isConsumedBeforeRecording());
					}
					copyDoubles(cage.spots.get(i).getKymographMeasurements().getCrossLineSignalFraction(), cage.integral[i]);
					copyDoubles(cage.spots.get(i).getCrossLineSignalToBaselineRatio(), row);
					cage.primary[i].parameter("noise", String.valueOf(cage.noise))
							.parameter("dye_level", String.valueOf(cage.dyeLevel[i]))
							.parameter("first_clear_frame", String.valueOf(cage.reference.firstClearFrame(i)));
					cage.spots.get(i).getMethodMeasurements().put(cage.primary[i].build());
				}
			}
			if (exp.getCages() != null) {
				exp.getCages().clearSpotAggregatesCache();
			}
			String binDir = exp.getResultsDirectory();
			MeasurementProgress.phase("Saving measurements");
			lastSaved = exp.save_image_spot_measures();
			if (lastSaved) {
				writeBackgroundDiagnostics(binDir, frames, cages);
				SpotDetectionProvenance.recordCameraLine(exp, SpotDetectionProvenance.Family.KYMO_CROSS,
						getClass().getSimpleName(), "initial_bins", analyzerParams.initialBins,
						"smoothing_bins", analyzerParams.smoothBins, "mad_multiplier", analyzerParams.madMultiplier,
						"insect_gate", analyzerParams.insectGate, "insect_transform", analyzerParams.insectTransform,
						"insect_threshold", analyzerParams.insectThreshold, "insect_threshold_above",
						analyzerParams.insectAbove, "flank_px", analyzerParams.flankPx, "band_width_px", analyzerParams.bandWidthPx,
						"background_method", "branches_then_cage_patches_v1", "background_patch_size", SpotBackgroundEstimator.PATCH_SIZE,
						"background_roi_clearance", SpotBackgroundEstimator.ROI_CLEARANCE,
						"background_patch_step", SpotBackgroundEstimator.PATCH_STEP,
						"background_min_patches", SpotBackgroundEstimator.MIN_PATCHES,
						"background_max_patches", SpotBackgroundEstimator.MAX_PATCHES,
						"background_min_clean_branch_fraction", SpotBackgroundEstimator.MIN_CLEAN_BRANCH_FRACTION,
						"signal_method", "opening_dye_footprint_v1",
						"signal_mask_dilate_px", SpotLineDyeReference.SIGNAL_MASK_DILATE_PX,
						"signal_movement_margin_px", SpotLineDyeReference.MOVEMENT_MARGIN_PX,
						"signal_min_dye_stations", SpotLineDyeReference.MIN_DYE_STATIONS,
						"signal_min_dye_excess", SpotLineDyeReference.MIN_DYE_EXCESS);
			}
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
		for (CageLines cage : cages) {
			cage.reference = new SpotLineDyeReference(cage.layout, window);
			for (double[] values : cage.integral) {
				Arrays.fill(values, Double.NaN);
			}
		}
		MeasurementProgress.phase("Reading initial images");
		for (int t = 0; t < window; t++) {
			if (!sampleFlanksAt(seq, loader, frames, cages, t)) {
				return false;
			}
			showFrame(progress, t, n);
		}
		for (CageLines cage : cages) {
			cage.noise = SpotLineDeficitAnalyzer.poolMad(cage.flanks, window, analyzerParams.madMultiplier);
			cage.refFloor = openingBackground(cage, window);
		}
		// Each spot gets its own opening window of unobscured frames. A fly may
		// remain on one spot longer than the nominal opening window.
		MeasurementProgress.phase("Finding clear spot references");
		for (int t = 0; t < n; t++) {
			if (!fillOpeningReference(seq, loader, frames, cages, t)) {
				return false;
			}
			showFrame(progress, t, n);
			boolean complete = true;
			for (CageLines cage : cages) {
				for (int s = 0; s < cage.spots.size(); s++) {
					if (!cage.spots.get(s).isConsumedBeforeRecording() && !cage.reference.complete(s)) {
						complete = false;
					}
				}
			}
			if (complete) {
				break;
			}
		}
		for (CageLines cage : cages) {
			int nSpots = cage.spots.size();
			cage.dyeLevel = new double[nSpots];
			for (int s = 0; s < nSpots; s++) {
				cage.dyeLevel[s] = cage.reference.dyeLevel(s);
			}
		}
		MeasurementProgress.phase("Measuring images");
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
						frame.blue, frame.backgroundInsect);
				cage.backgrounds[t] = cage.background.sample(frame.red, frame.green, frame.blue, frame.backgroundInsect, cage.flanks[t]);
				double[] floors = cage.backgrounds[t].withReference(cage.refFloor);
				measureFrame(cage, frame, floors, t);
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
					frame.backgroundInsect);
			cage.backgrounds[t] = cage.background.sample(frame.red, frame.green, frame.blue, frame.backgroundInsect, cage.flanks[t]);
		}
		return true;
	}

	/** Calibrate only from frame backgrounds that passed the same checks used later. */
	private static double[] openingBackground(CageLines cage, int window) {
		double[] result = new double[cage.spots.size()];
		Arrays.fill(result, Double.NaN);
		for (int s = 0; s < result.length; s++) {
			double[] values = new double[window];
			int n = 0;
			for (int t = 0; t < window; t++) {
				double floor = cage.backgrounds[t].floors[s];
				if (Double.isFinite(floor)) values[n++] = floor;
			}
			if (n > 0) {
				Arrays.sort(values, 0, n);
				result[s] = (values[(n - 1) / 2] + values[n / 2]) * 0.5;
			}
		}
		return result;
	}

	private static void writeBackgroundDiagnostics(String binDir, List<Integer> frames, List<CageLines> cages) {
		if (binDir == null) return;
		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(binDir, "XLineBackground.csv"), StandardCharsets.UTF_8)) {
			writer.write("camera_frame,spot,background_source,background_deficit,clean_branch_stations,total_branch_stations,candidate_patches,clean_patches,selected_patch_centers,raw_signal_fraction,dye_reference,footprint_stations,first_clear_frame,first_dye_frame,calibration_frames\n");
			for (CageLines cage : cages) {
				for (int t = 0; t < frames.size(); t++) {
					Sample sample = cage.backgrounds[t];
					double[] floors = sample.withReference(cage.refFloor);
					for (int s = 0; s < cage.spots.size(); s++) {
						StringBuilder centers = new StringBuilder();
						for (int patch : sample.selectedPatches[s]) {
							java.awt.Point p = cage.background.patchCenter(patch);
							if (centers.length() > 0) centers.append(';');
							centers.append(p.x).append(':').append(p.y);
						}
						writer.write(frames.get(t) + ",\"" + cage.spots.get(s).getName().replace("\"", "\"\"") + "\","
								+ sample.sourceWithReference(s, cage.refFloor) + ","
								+ (Double.isFinite(floors[s]) ? Double.toString(floors[s]) : "") + ","
								+ sample.cleanBranches[s] + "," + sample.totalBranches[s] + "," + cage.background.patchCount() + "," + sample.cleanPatches
								+ ",\"" + centers + "\"," + finiteCsv(cage.integral[s][t]) + "," + finiteCsv(cage.reference.dyeLevel(s))
								+ "," + cage.reference.footprintStations(s) + "," + cage.reference.firstClearFrame(s)
								+ "," + cage.reference.firstDyeFrame(s) + "," + cage.reference.acceptedFrames(s) + "\n");
					}
				}
			}
		} catch (IOException e) {
			Logger.warn("AnalyzeSpotLineDeficits: could not save background diagnostics: " + e.getMessage());
		}
	}

	private static String finiteCsv(double value) {
		return Double.isFinite(value) ? Double.toString(value) : "";
	}

	private boolean fillOpeningReference(SequenceCamData seq, SequenceLoaderService loader, List<Integer> frames,
		List<CageLines> cages, int t) {
		if (stopFlag) {
			return false;
		}
		FramePixels frame = readFrame(seq, loader, frames.get(t));
		for (CageLines cage : cages) {
			if (cage.reference == null) {
				continue;
			}
			if (cage.flanks[t] == null) {
				cage.flanks[t] = SpotLineDeficitAnalyzer.sampleFlanks(cage.layout, frame.red, frame.green,
						frame.blue, frame.backgroundInsect);
				cage.backgrounds[t] = cage.background.sample(frame.red, frame.green, frame.blue, frame.backgroundInsect, cage.flanks[t]);
			}
			// Recover a missing opening floor once a clear view of the arm exists.
			double[] currentFloors = cage.backgrounds[t].floors;
			for (int s = 0; s < cage.refFloor.length; s++) {
				if (!Double.isFinite(cage.refFloor[s]) && Double.isFinite(currentFloors[s])) {
					cage.refFloor[s] = currentFloors[s];
				}
			}
			double[] floors = cage.backgrounds[t].withReference(cage.refFloor);
			for (int s = 0; s < cage.spots.size(); s++) {
				if (cage.reference.complete(s) || cage.spots.get(s).isConsumedBeforeRecording()) {
					continue;
				}
				double floor = floors != null && s < floors.length ? floors[s] : Double.NaN;
				if (!Double.isFinite(floor)) {
					continue;
				}
				cage.reference.observe(s, t, frame.red, frame.green, frame.blue, floor, cage.noise, frame.insect);
			}
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
			double[] floors = cage.backgrounds[t].withReference(cage.refFloor);
			measureFrame(cage, frame, floors, t);
		}
		return true;
	}

	private static void measureFrame(CageLines cage, FramePixels frame, double[] floors, int t) {
		for (int s = 0; s < cage.spots.size(); s++) {
			cage.integral[s][t] = cage.reference.measure(s, frame.red, frame.green, frame.blue, floors[s], cage.noise, frame.insect);
			double raw = cage.reference.measure(s, frame.red, frame.green, frame.blue, floors[s], cage.noise, null);
			double occupancy = frame.insect != null ? cage.reference.flyOccupancy(s, frame.insect) : Double.NaN;
			int flags = frame.red == null ? SpotSampleFlags.MISSING_IMAGE : 0;
			if (!Double.isFinite(floors[s])) { flags |= SpotSampleFlags.INVALID_BACKGROUND; }
			if (Double.isFinite(occupancy) && occupancy >= SpotLineDeficitAnalyzer.FLY_OCCUPANCY_FRACTION) { flags |= SpotSampleFlags.OCCLUDED; }
			if (frame.red != null && frame.insect == null && cage.insectGate) {
				flags |= SpotSampleFlags.QUALITY_UNKNOWN;
			}
			cage.primary[s].recordFlyEvidence(t, occupancy);
			cage.primary[s].record("signal_fraction", t, raw, flags);
			cage.primary[s].record("visible_signal_fraction", t, cage.integral[s][t], flags);
			cage.primary[s].record("background", t, floors[s], frame.red == null ? SpotSampleFlags.MISSING_IMAGE : 0);
		}
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
			Logger.warn("AnalyzeSpotLineDeficits: could not read frame " + frameIndex);
			return new FramePixels(null, null, null, null, null);
		}
		int nC = Math.max(1, img.getSizeC());
		boolean signed = img.isSignedDataType();
		int[] red = Array1DUtil.arrayToIntArray(img.getDataXY(0), signed);
		int[] green = nC > 1 ? Array1DUtil.arrayToIntArray(img.getDataXY(1), signed) : red;
		int[] blue = nC > 2 ? Array1DUtil.arrayToIntArray(img.getDataXY(2), signed) : red;
		boolean[] insect = insectMask(img);
		return new FramePixels(red, green, blue,
				SpotLineDeficitAnalyzer.dilateInsects(insect, img.getSizeX(), img.getSizeY(), SpotLineDyeReference.SIGNAL_MASK_DILATE_PX),
				SpotLineDeficitAnalyzer.dilateInsects(insect, img.getSizeX(), img.getSizeY(), SpotLineDeficitAnalyzer.FLY_FLOOR_DILATE_PX));
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
		final boolean[] backgroundInsect;

		FramePixels(int[] red, int[] green, int[] blue, boolean[] insect, boolean[] backgroundInsect) {
			this.red = red;
			this.green = green;
			this.blue = blue;
			this.insect = insect;
			this.backgroundInsect = backgroundInsect;
		}
	}

	private boolean prepareExperiment(Experiment exp) {

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

	static List<Integer> binFrames(Experiment exp) {
		return plugins.fmp.multitools.experiment.spot.SpotLineSampling.cameraFrames(exp);
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
			Layout layout = SpotLineDeficitAnalyzer.layout(geoms, width, height, params.flankPx, params.bandWidthPx);
			SpotBackgroundEstimator background = new SpotBackgroundEstimator(layout, geoms, height,
					cage.getRoi() != null ? (x, y) -> cage.getRoi().contains(x, y) : null);
			out.add(new CageLines(spots, layout, background));
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
		measure.setCameraSampled(true);
	}

	private static final class CageLines {
		final List<Spot> spots;
		final Layout layout;
		final SpotBackgroundEstimator background;
		Sample[] backgrounds;
		double[][] flanks;
		double[][] integral;
		double noise;
		double[] refFloor;
		double[] dyeLevel;
		SpotLineDyeReference reference;
		SpotMethodRecording[] primary;
		boolean insectGate;

		CageLines(List<Spot> spots, Layout layout, SpotBackgroundEstimator background) {
			this.spots = spots;
			this.layout = layout;
			this.background = background;
		}
	}
}
