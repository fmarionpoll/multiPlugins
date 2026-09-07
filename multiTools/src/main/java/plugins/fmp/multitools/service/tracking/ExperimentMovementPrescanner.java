package plugins.fmp.multitools.service.tracking;

import java.awt.Rectangle;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;
import java.util.function.BiConsumer;

import icy.image.IcyBufferedImage;
import icy.roi.ROI2D;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.LazyExperiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillaries.tracking.TrackingBoundary;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.service.CapillaryTracker;
import plugins.fmp.multitools.service.SequenceLoaderService;
import plugins.fmp.multitools.service.tracking.PlanarTransform.Model;
import plugins.fmp.multitools.tools.ROI2D.ROI2DUtilities;
import plugins.fmp.multitools.tools.ROI2D.AlongT;
import plugins.kernel.roi.roi2d.ROI2DLine;

/** Read-only sparse movement assessment; it never modifies tracked ROI geometry. */
public final class ExperimentMovementPrescanner {
	public enum Assessment { MOVEMENT, UNCERTAIN, BELOW_THRESHOLD, UNSCORED }
	public enum TrackingStatus {
		NOT_TRACKED("not tracked"), TRACKED("tracked"), MANUALLY_SEGMENTED("manually segmented / edited");
		private final String label;
		TrackingStatus(String label) { this.label = label; }
		@Override public String toString() { return label; }
	}
	private static final int SEARCH_MARGIN_PX = 24;
	private final SequenceLoaderService loader = new SequenceLoaderService();
	private final CapillaryTracker tracker = new CapillaryTracker();
	private final PlanarTransformFitter fitter = new PlanarTransformFitter();

	public Result scan(Experiment experiment, int requestedSamples) {
		return scan(experiment, requestedSamples, () -> false);
	}

	public Result scan(Experiment experiment, int requestedSamples, BooleanSupplier cancelled) {
		if (cancelled != null && cancelled.getAsBoolean())
			return Result.failed(experiment, "cancelled");
		if (experiment instanceof LazyExperiment)
			((LazyExperiment) experiment).loadIfNeeded();
		TrackingStatus trackingStatus = inspectTrackingStatus(experiment);
		SequenceCamData sequence = experiment == null ? null : experiment.getSeqCamData();
		if (sequence == null || sequence.getImageLoader() == null)
			return Result.failed(experiment, "no camera sequence");
		int nFrames = sequence.getImageLoader().getNTotalFrames();
		if (nFrames < 2)
			return Result.failed(experiment, "fewer than two frames");

		if (experiment.getCages().getCageList().isEmpty())
			experiment.getCages().loadDescriptions(experiment.getResultsDirectory());
		List<ROI2DLine> landmarks = cageLandmarks(experiment);
		if (landmarks.size() < 4) {
			if (experiment.getCapillaries().getList().isEmpty())
				experiment.loadMCCapillaries_Only();
			landmarks = capillaryLayoutLandmarks(experiment);
		}
		if (landmarks.size() < 4)
			return Result.failed(experiment, "fewer than two cage/capillary landmark pairs");

		IcyBufferedImage reference = load(sequence, 0);
		if (reference == null)
			return Result.failed(experiment, "cannot load frame 0");
		Result result = new Result(experiment);
		result.trackingStatus = trackingStatus;
		for (int frame : sampleFrames(nFrames, requestedSamples)) {
			if (cancelled != null && cancelled.getAsBoolean())
				break;
			if (frame == 0)
				continue;
			IcyBufferedImage current = load(sequence, frame);
			if (current == null) {
				result.failedSamples++;
				continue;
			}
			FrameMetrics metrics = compareFrame(landmarks, reference, current, frame);
			if (metrics == null) {
				result.failedSamples++;
				continue;
			}
			result.accept(metrics);
		}
		if (result.sampledFrames == 0)
			return Result.failed(experiment, "no sampled frame could be registered");
		result.confidence = result.minimumInlierFraction * result.sampledFrames
				/ Math.max(1.0, result.sampledFrames + result.failedSamples);
		return result;
	}

	public TrackingStatus inspectTrackingStatus(Experiment experiment) {
		if (experiment == null)
			return TrackingStatus.NOT_TRACKED;
		if (experiment instanceof LazyExperiment)
			((LazyExperiment) experiment).loadIfNeeded();
		if (experiment.getCapillaries().getList().isEmpty())
			experiment.loadMCCapillaries_Only();
		for (TrackingBoundary boundary : experiment.getCapillaries().getTrackingTimeline().getBoundaries())
			if (boundary.getOrigin() == TrackingBoundary.Origin.MANUAL)
				return TrackingStatus.MANUALLY_SEGMENTED;
		for (Capillary cap : experiment.getCapillaries().getList()) {
			Map<Long, Line2D> keyframes = cap.getPhaseGeometry().getBlueKeyframes();
			if (keyframes.size() > 1)
				return TrackingStatus.TRACKED;
			for (Long frame : keyframes.keySet())
				if (frame != null && frame.longValue() > 0)
					return TrackingStatus.TRACKED;
		}
		return TrackingStatus.NOT_TRACKED;
	}

	/**
	 * Densely compares consecutive frames and returns local movement peaks. This is
	 * read-only: no capillary geometry or tracking timeline is modified.
	 */
	public TransitionAnalysis analyzeTransitions(Experiment experiment, int fromFrame, int toFrame,
			double minimumMovementPx, BooleanSupplier cancelled) {
		return analyzeTransitions(experiment, fromFrame, toFrame, minimumMovementPx, cancelled, null);
	}

	public TransitionAnalysis analyzeTransitions(Experiment experiment, int fromFrame, int toFrame,
			double minimumMovementPx, BooleanSupplier cancelled, BiConsumer<Integer, Integer> progress) {
		TransitionAnalysis analysis = new TransitionAnalysis();
		if (experiment == null) {
			analysis.error = "no experiment";
			return analysis;
		}
		if (experiment instanceof LazyExperiment)
			((LazyExperiment) experiment).loadIfNeeded();
		SequenceCamData sequence = experiment.getSeqCamData();
		if (sequence == null || sequence.getImageLoader() == null) {
			analysis.error = "no camera sequence";
			return analysis;
		}
		int nFrames = sequence.getImageLoader().getNTotalFrames();
		int first = Math.max(0, Math.min(fromFrame, nFrames - 1));
		int last = Math.max(first, Math.min(toFrame, nFrames - 1));
		if (last <= first) {
			analysis.error = "range contains fewer than two frames";
			return analysis;
		}
		if (experiment.getCages().getCageList().isEmpty())
			experiment.getCages().loadDescriptions(experiment.getResultsDirectory());
		List<ROI2DLine> landmarks = cageLandmarks(experiment);
		if (landmarks.size() < 4) {
			if (experiment.getCapillaries().getList().isEmpty())
				experiment.loadMCCapillaries_Only();
			landmarks = capillaryLayoutLandmarks(experiment);
		}
		if (landmarks.size() < 4) {
			analysis.error = "fewer than two cage/capillary landmark pairs";
			return analysis;
		}
		final int[] lags = { 1, 3, 5, 10 };
		TreeMap<Integer, IcyBufferedImage> recent = new TreeMap<Integer, IcyBufferedImage>();
		IcyBufferedImage firstImage = load(sequence, first);
		recent.put(first, firstImage);
		int anchorFrame = first;
		IcyBufferedImage anchorImage = firstImage;
		List<FrameObservation> observations = new ArrayList<FrameObservation>();
		List<FrameObservation> adjacentObservations = new ArrayList<FrameObservation>();
		List<FrameObservation> cumulativeSteps = new ArrayList<FrameObservation>();
		List<FrameObservation> baselineObservations = new ArrayList<FrameObservation>();
		for (int frame = first + 1; frame <= last; frame++) {
			if (cancelled != null && cancelled.getAsBoolean()) {
				analysis.cancelled = true;
				break;
			}
			IcyBufferedImage current = load(sequence, frame);
			if (current == null) {
				analysis.failedFrames++;
				continue;
			}
			recent.put(frame, current);
			FrameObservation best = null;
			for (int lag : lags) {
				IcyBufferedImage reference = recent.get(frame - lag);
				if (reference == null)
					continue;
				FrameMetrics value = compareFrame(landmarks, reference, current, frame);
				if (value != null) {
					FrameObservation candidate = new FrameObservation(frame - lag, frame, value);
					if (lag == 1)
						adjacentObservations.add(candidate);
					if (best == null || candidate.score > best.score)
						best = candidate;
				}
			}
			// A resetting local reference accumulates small frame-to-frame drift until it
			// becomes measurable, then starts a new local segment.
			if (anchorImage != null && frame - anchorFrame > 1) {
				FrameMetrics cumulative = compareFrame(landmarks, anchorImage, current, frame);
				if (cumulative != null) {
					FrameObservation candidate = new FrameObservation(anchorFrame, frame, cumulative);
					if (best == null || candidate.score > best.score)
						best = candidate;
					if (candidate.coherentScore >= Math.max(.5, minimumMovementPx * .5)) {
						cumulativeSteps.add(candidate);
						anchorFrame = frame;
						anchorImage = current;
					}
				}
			}
			// Preserve a frame-zero baseline as a conservative fallback for motion so
			// slow that it never produces enough resetting-anchor steps.
			if (firstImage != null && frame - first > 10) {
				FrameMetrics baseline = compareFrame(landmarks, firstImage, current, frame);
				if (baseline != null)
					baselineObservations.add(new FrameObservation(first, frame, baseline));
			}
			if (best == null)
				analysis.failedFrames++;
			else {
				observations.add(best);
				if (analysis.strongest == null || best.score > analysis.strongest.score)
					analysis.strongest = proposalFrom(best, best.fromFrame + 1, best.toFrame, minimumMovementPx);
			}
			while (!recent.isEmpty() && recent.firstKey() < frame - 10)
				recent.pollFirstEntry();
			if (progress != null)
				progress.accept(frame - first, last - first);
		}
		analysis.comparedFrames = observations.size();
		if (observations.isEmpty()) {
			analysis.error = "no frame pair could be registered";
			return analysis;
		}
		List<Double> ordered = new ArrayList<Double>();
		for (FrameObservation observation : adjacentObservations) ordered.add(observation.score);
		if (ordered.isEmpty())
			for (FrameObservation observation : observations) ordered.add(observation.score);
		Collections.sort(ordered);
		double median = ordered.get(ordered.size() / 2);
		List<Double> deviations = new ArrayList<Double>();
		for (double value : ordered) deviations.add(Math.abs(value - median));
		Collections.sort(deviations);
		double mad = deviations.get(deviations.size() / 2);
		double threshold = Math.max(minimumMovementPx, median + Math.max(.5, 4 * mad));
		analysis.thresholdUsed = threshold;
		int clusterStart = -1, clusterEnd = -1;
		FrameObservation clusterBest = null;
		for (FrameObservation observation : adjacentObservations) {
			if (observation.score < threshold)
				continue;
			int possibleStart = observation.fromFrame + 1;
			if (clusterBest == null || possibleStart > clusterEnd + 2) {
				if (clusterBest != null)
					analysis.proposals.add(proposalFrom(clusterBest, clusterStart, clusterEnd, threshold));
				clusterStart = possibleStart;
				clusterEnd = observation.toFrame;
				clusterBest = observation;
			} else {
				clusterStart = Math.min(clusterStart, possibleStart);
				clusterEnd = Math.max(clusterEnd, observation.toFrame);
				if (observation.score > clusterBest.score)
					clusterBest = observation;
			}
		}
		if (clusterBest != null)
			analysis.proposals.add(proposalFrom(clusterBest, clusterStart, clusterEnd, threshold));
		addGradualProposals(analysis.proposals, cumulativeSteps, minimumMovementPx);
		if (analysis.proposals.isEmpty())
			addPersistentBaselineProposal(analysis, baselineObservations, minimumMovementPx);
		Collections.sort(analysis.proposals, (a, b) -> Integer.compare(a.startFrame, b.startFrame));
		return analysis;
	}

	private static void addPersistentBaselineProposal(TransitionAnalysis analysis,
			List<FrameObservation> baseline, double minimumMovementPx) {
		if (baseline.size() < 5)
			return;
		int tailSize = Math.max(5, baseline.size() / 5);
		int tailStart = baseline.size() - tailSize;
		double supportThreshold = minimumMovementPx * .8;
		int supported = 0;
		FrameObservation peak = null;
		for (int i = 0; i < baseline.size(); i++) {
			FrameObservation observation = baseline.get(i);
			if (i >= tailStart && observation.coherentScore >= supportThreshold)
				supported++;
			if (peak == null || observation.coherentScore > peak.coherentScore)
				peak = observation;
		}
		analysis.baselineTailSupported = supported;
		analysis.baselineTailFrames = tailSize;
		if (peak != null) {
			analysis.baselinePeakScore = peak.coherentScore;
			analysis.baselinePeakFrame = peak.toFrame;
			analysis.baselinePeakInlierFraction = peak.metrics.inlierFraction;
		}
		// A real final displacement must be present in most of the final fifth,
		// not merely appear as a transient registration or illumination artefact.
		if (peak == null || peak.coherentScore < minimumMovementPx
				|| supported < Math.ceil(tailSize * .6) || peak.metrics.inlierFraction < .6)
			return;
		FrameMetrics m = peak.metrics;
		double confidence = Math.min(1, m.inlierFraction
				* Math.min(1.25, peak.coherentScore / Math.max(.001, minimumMovementPx)));
		analysis.proposals.add(new TransitionProposal(peak.toFrame, peak.fromFrame + 1, peak.toFrame,
				m.translationX, m.translationY, m.displacement, m.rotation, m.scalePercent,
				m.residual, confidence, peak.coherentScore));
	}

	private static void addGradualProposals(List<TransitionProposal> proposals,
			List<FrameObservation> cumulativeSteps, double minimumMovementPx) {
		List<FrameObservation> chain = new ArrayList<FrameObservation>();
		for (FrameObservation step : cumulativeSteps) {
			if (!chain.isEmpty() && !continuesCoherently(chain.get(chain.size() - 1), step)) {
				addGradualChain(proposals, chain, minimumMovementPx);
				chain.clear();
			}
			chain.add(step);
		}
		addGradualChain(proposals, chain, minimumMovementPx);
	}

	private static boolean continuesCoherently(FrameObservation previous, FrameObservation next) {
		if (next.fromFrame - previous.toFrame > 2)
			return false;
		double[] a = motionVector(previous.metrics), b = motionVector(next.metrics);
		double dot = 0, an2 = 0, bn2 = 0;
		for (int i = 0; i < a.length; i++) {
			dot += a[i] * b[i];
			an2 += a[i] * a[i];
			bn2 += b[i] * b[i];
		}
		double an = Math.sqrt(an2), bn = Math.sqrt(bn2);
		if (an < .25 || bn < .25)
			return true;
		// Minor rotation/scale noise can rotate a weak translation vector markedly.
		// Only split a gradual chain when two successive steps clearly oppose.
		return dot / (an * bn) >= -.15;
	}

	private static double[] motionVector(FrameMetrics metrics) {
		return new double[] { metrics.translationX, metrics.translationY,
				metrics.signedRotation * 20, metrics.signedScalePercent * 5 };
	}

	private static void addGradualChain(List<TransitionProposal> proposals, List<FrameObservation> chain,
			double minimumMovementPx) {
		if (chain.size() < 2)
			return;
		double dx = 0, dy = 0, path = 0, signedRotation = 0, signedScale = 0, residual = 0;
		double confidence = 1, score = 0;
		FrameObservation peak = chain.get(0);
		for (FrameObservation step : chain) {
			dx += step.metrics.translationX;
			dy += step.metrics.translationY;
			double[] vector = motionVector(step.metrics);
			double vectorNorm2 = 0;
			for (double component : vector) vectorNorm2 += component * component;
			path += Math.sqrt(vectorNorm2);
			signedRotation += step.metrics.signedRotation;
			signedScale += step.metrics.signedScalePercent;
			residual = Math.max(residual, step.metrics.residual);
			confidence = Math.min(confidence, step.metrics.inlierFraction);
			score = Math.max(score, step.score);
			if (step.score > peak.score) peak = step;
		}
		double translationNet = Math.hypot(dx, dy);
		double net = Math.sqrt(translationNet * translationNet + Math.pow(signedRotation * 20, 2)
				+ Math.pow(signedScale * 5, 2));
		if (net < minimumMovementPx * 1.2 || (path > 0 && net / path < .65))
			return;
		int start = chain.get(0).fromFrame + 1;
		int end = chain.get(chain.size() - 1).toFrame;
		for (TransitionProposal existing : proposals)
			if (existing.startFrame <= end && existing.endFrame >= start)
				return;
		proposals.add(new TransitionProposal(peak.toFrame, start, end, dx, dy, translationNet,
				Math.abs(signedRotation), Math.abs(signedScale),
				residual, confidence, Math.max(score, net)));
	}

	private static TransitionProposal proposalFrom(FrameObservation observation, int startFrame, int endFrame,
			double threshold) {
		FrameMetrics m = observation.metrics;
		double confidence = Math.min(1, m.inlierFraction * Math.min(1.5,
				observation.score / Math.max(.001, threshold)));
		return new TransitionProposal(m.frame, startFrame, endFrame, m.translationX, m.translationY,
				m.displacement, m.rotation, m.scalePercent, m.residual, confidence, observation.score);
	}

	private static double movementScore(FrameMetrics metrics) {
		return Math.max(metrics.displacement,
				Math.max(metrics.residual, Math.max(metrics.rotation * 20, metrics.scalePercent * 5)));
	}

	/**
	 * Motion that can legitimately advance a temporal reference. Image residual is
	 * deliberately excluded: illumination changes and flies may increase the RMS
	 * mismatch without describing any motion of the rigid capillary support.
	 */
	private static double coherentMovementScore(FrameMetrics metrics) {
		return Math.max(metrics.displacement, Math.max(Math.hypot(metrics.translationX, metrics.translationY),
				Math.max(metrics.rotation * 20, metrics.scalePercent * 5)));
	}

	private FrameMetrics compareFrame(List<ROI2DLine> rois, IcyBufferedImage reference, IcyBufferedImage current,
			int frame) {
		List<LandmarkMatch> matches = new ArrayList<LandmarkMatch>();
		List<Double> magnitudes = new ArrayList<Double>();
		for (ROI2DLine roi : rois) {
			ROI2D moved = tracker.trackOneFrame(roi, reference, current, SEARCH_MARGIN_PX);
			Point2D p0 = ROI2DUtilities.getRoiCentroid(roi);
			Point2D p1 = ROI2DUtilities.getRoiCentroid(moved);
			if (p0 == null || p1 == null)
				continue;
			double magnitude = p0.distance(p1);
			if (!Double.isFinite(magnitude) || magnitude > 60)
				continue;
			matches.add(new LandmarkMatch(p0, p1, 1));
			magnitudes.add(magnitude);
		}
		if (matches.size() < 4)
			return null;
		PlanarTransformFit fit;
		try {
			fit = fitter.fitRobust(matches, Model.SIMILARITY);
		} catch (IllegalArgumentException ex) {
			return null;
		}
		double[][] matrix = fit.getTransform().getMatrix();
		double scale = Math.hypot(matrix[0][0], matrix[1][0]);
		double rotationDeg = Math.toDegrees(Math.atan2(matrix[1][0], matrix[0][0]));
		double centerX = 0, centerY = 0;
		for (LandmarkMatch match : matches) {
			centerX += match.getSource().getX();
			centerY += match.getSource().getY();
		}
		centerX /= matches.size();
		centerY /= matches.size();
		Point2D center = new Point2D.Double(centerX, centerY);
		Point2D movedCenter = fit.getTransform().transform(center);
		double translationX = movedCenter.getX() - centerX;
		double translationY = movedCenter.getY() - centerY;
		double partialFrameDisplacement = consensusDisplacement(matches);
		// Report coherent motion predicted by the robust fit, not the largest
		// raw matches (which can include flies and mismatched cage features).
		magnitudes.clear();
		for (ROI2DLine roi : rois) {
			Point2D point = ROI2DUtilities.getRoiCentroid(roi);
			if (point != null) magnitudes.add(point.distance(fit.getTransform().transform(point)));
		}
		Collections.sort(magnitudes);
		int p90index = Math.min(magnitudes.size() - 1, (int) Math.ceil(magnitudes.size() * .9) - 1);
		double displacement90 = Math.max(magnitudes.get(Math.max(0, p90index)), partialFrameDisplacement);
		double inlierFraction = fit.getInlierIndices().size() / (double) matches.size();
		return new FrameMetrics(frame, displacement90, translationX, translationY, rotationDeg,
				(scale - 1) * 100, fit.getRms(), inlierFraction);
	}

	/**
	 * Detects a coherent movement confined to part of the rack (typically its upper
	 * branches). At least 30% of all landmarks must share the vector, preventing a
	 * changed background behind one cage from being classified as rack movement.
	 */
	private static double consensusDisplacement(List<LandmarkMatch> matches) {
		int required = Math.max(4, (int) Math.ceil(matches.size() * .30));
		double bestMagnitude = 0;
		for (LandmarkMatch seed : matches) {
			Point2D ss = seed.getSource(), st = seed.getTarget();
			double seedX = st.getX() - ss.getX(), seedY = st.getY() - ss.getY();
			double sumX = 0, sumY = 0;
			int count = 0;
			for (LandmarkMatch candidate : matches) {
				Point2D cs = candidate.getSource(), ct = candidate.getTarget();
				double dx = ct.getX() - cs.getX(), dy = ct.getY() - cs.getY();
				if (Math.hypot(dx - seedX, dy - seedY) <= 1.0) {
					sumX += dx;
					sumY += dy;
					count++;
				}
			}
			if (count >= required)
				bestMagnitude = Math.max(bestMagnitude, Math.hypot(sumX / count, sumY / count));
		}
		return bestMagnitude;
	}

	private List<ROI2DLine> cageLandmarks(Experiment experiment) {
		List<ROI2DLine> result = new ArrayList<ROI2DLine>();
		for (Cage cage : experiment.getCages().getCageList()) {
			if (cage == null || cage.getRoi() == null)
				continue;
			Rectangle b = cage.getRoi().getBounds();
			if (b.width < 20 || b.height < 20)
				continue;
			double inset = Math.min(12, b.width / 5.0);
			result.add(new ROI2DLine(new Line2D.Double(b.x + inset, b.y + 3, b.x + b.width - inset, b.y + 3)));
			result.add(new ROI2DLine(new Line2D.Double(b.x + inset, b.y + b.height - 4,
					b.x + b.width - inset, b.y + b.height - 4)));
		}
		return result;
	}

	/** Fallback for old recordings without persisted cage rectangles. */
	private List<ROI2DLine> capillaryLayoutLandmarks(Experiment experiment) {
		Map<Integer, List<Line2D>> byCage = new TreeMap<Integer, List<Line2D>>();
		for (Capillary cap : experiment.getCapillaries().getList()) {
			if (cap == null)
				continue;
			Line2D line = cap.getPhaseGeometry().getBlueAt(0);
			if (line == null) {
				AlongT along = cap.getAlongTAtT(0);
				if (along != null && along.getRoi() instanceof ROI2DLine)
					line = ((ROI2DLine) along.getRoi()).getLine();
			}
			if (line == null || line.getP1().distance(line.getP2()) < 10)
				continue;
			List<Line2D> cageLines = byCage.get(cap.getCageID());
			if (cageLines == null) {
				cageLines = new ArrayList<Line2D>();
				byCage.put(cap.getCageID(), cageLines);
			}
			cageLines.add(line);
		}
		List<ROI2DLine> result = new ArrayList<ROI2DLine>();
		for (List<Line2D> lines : byCage.values()) {
			double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
			double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
			for (Line2D line : lines) {
				minX = Math.min(minX, Math.min(line.getX1(), line.getX2()));
				maxX = Math.max(maxX, Math.max(line.getX1(), line.getX2()));
				minY = Math.min(minY, Math.min(line.getY1(), line.getY2()));
				maxY = Math.max(maxY, Math.max(line.getY1(), line.getY2()));
			}
			if (!Double.isFinite(minX) || maxY - minY < 10)
				continue;
			double halfExtra = Math.max(10, (maxX - minX) * .35);
			result.add(new ROI2DLine(new Line2D.Double(minX - halfExtra, minY, maxX + halfExtra, minY)));
			result.add(new ROI2DLine(new Line2D.Double(minX - halfExtra, maxY, maxX + halfExtra, maxY)));
		}
		return result;
	}

	private IcyBufferedImage load(SequenceCamData sequence, int frame) {
		String path = sequence.getFileNameFromImageList(frame);
		return path == null ? null : loader.imageIORead(path);
	}

	static List<Integer> sampleFrames(int nFrames, int requestedSamples) {
		Set<Integer> frames = new LinkedHashSet<Integer>();
		frames.add(0);
		if (nFrames > 1)
			frames.add(1);
		int count = Math.max(3, Math.min(requestedSamples, nFrames));
		for (int i = 1; i <= count - 2; i++)
			frames.add((int) Math.round(i * (nFrames - 1.0) / (count - 2.0)));
		return new ArrayList<Integer>(frames);
	}

	static final class FrameMetrics {
		final int frame;
		final double displacement, translationX, translationY, rotation, scalePercent, residual, inlierFraction;
		final double signedRotation, signedScalePercent;
		FrameMetrics(int frame, double displacement, double translationX, double translationY, double rotation,
				double scalePercent, double residual, double inlierFraction) {
			this.frame = frame; this.displacement = displacement;
			this.signedRotation = rotation; this.rotation = Math.abs(rotation);
			this.translationX = translationX; this.translationY = translationY;
			this.signedScalePercent = scalePercent; this.scalePercent = Math.abs(scalePercent);
			this.residual = residual; this.inlierFraction = inlierFraction;
		}
	}

	private static final class FrameObservation {
		final int fromFrame, toFrame;
		final FrameMetrics metrics;
		final double score, coherentScore;
		FrameObservation(int fromFrame, int toFrame, FrameMetrics metrics) {
			this.fromFrame = fromFrame;
			this.toFrame = toFrame;
			this.metrics = metrics;
			this.score = movementScore(metrics);
			this.coherentScore = coherentMovementScore(metrics);
		}
	}

	public static final class TransitionAnalysis {
		public final List<TransitionProposal> proposals = new ArrayList<TransitionProposal>();
		public TransitionProposal strongest;
		public int comparedFrames, failedFrames;
		public double thresholdUsed;
		public double baselinePeakScore, baselinePeakInlierFraction;
		public int baselinePeakFrame = -1, baselineTailSupported, baselineTailFrames;
		public boolean cancelled;
		public String error;
		public boolean succeeded() { return error == null && comparedFrames > 0; }
	}

	public static final class TransitionProposal {
		public final int frame, startFrame, endFrame;
		public final double translationX, translationY, displacement, rotationDeg, scalePercent, residualPx;
		public final double confidence, score;
		public TransitionProposal(int frame, int startFrame, int endFrame,
				double translationX, double translationY, double displacement,
				double rotationDeg, double scalePercent, double residualPx, double confidence, double score) {
			this.frame = frame; this.startFrame = startFrame; this.endFrame = endFrame;
			this.translationX = translationX; this.translationY = translationY;
			this.displacement = displacement; this.rotationDeg = rotationDeg; this.scalePercent = scalePercent;
			this.residualPx = residualPx; this.confidence = confidence; this.score = score;
		}
		public TransitionProposal atFrame(int newFrame) {
			int shift = newFrame - frame;
			return new TransitionProposal(newFrame, Math.max(1, startFrame + shift), Math.max(1, endFrame + shift),
					translationX, translationY, displacement, rotationDeg,
					scalePercent, residualPx, confidence, score);
		}
		public String temporalPattern() {
			int duration = Math.max(1, endFrame - startFrame + 1);
			return duration == 1 ? "abrupt" : duration <= 5 ? "short transition" : "gradual";
		}
		public String summary() {
			String location = startFrame == endFrame ? "T=" + frame
					: "T=" + startFrame + "–" + endFrame + " (peak " + frame + ")";
			return String.format("%s  %s, Δx=%.1f Δy=%.1f px, rot=%.3f°, scale=%.3f%%, residual=%.1f px, confidence=%.0f%%",
					location, temporalPattern(), translationX, translationY, rotationDeg, scalePercent, residualPx,
					confidence * 100);
		}
	}

	public static final class Result {
		public final Experiment experiment;
		public double maxDisplacementPx, maxRotationDeg, maxScalePercent, maxResidualPx;
		public double translationXPx, translationYPx;
		public double confidence = 0;
		public int worstFrame = -1, sampledFrames, failedSamples;
		public String error;
		public TrackingStatus trackingStatus = TrackingStatus.NOT_TRACKED;
		private double minimumInlierFraction = 1;
		private final List<FrameMetrics> frameEvidence = new ArrayList<FrameMetrics>();

		Result(Experiment experiment) { this.experiment = experiment; }
		static Result failed(Experiment experiment, String error) {
			Result result = new Result(experiment); result.error = error; return result;
		}
		void accept(FrameMetrics m) {
			frameEvidence.add(m);
			sampledFrames++;
			minimumInlierFraction = Math.min(minimumInlierFraction, m.inlierFraction);
			if (m.displacement > maxDisplacementPx) {
				maxDisplacementPx = m.displacement; worstFrame = m.frame;
				translationXPx = m.translationX; translationYPx = m.translationY;
			}
			maxRotationDeg = Math.max(maxRotationDeg, m.rotation);
			maxScalePercent = Math.max(maxScalePercent, m.scalePercent);
			maxResidualPx = Math.max(maxResidualPx, m.residual);
		}
		public boolean succeeded() { return error == null && sampledFrames > 0; }
		public boolean isCandidate(double displacementThresholdPx) {
			return assessment(displacementThresholdPx) == Assessment.MOVEMENT;
		}

		/** Poor registration is uncertainty, not evidence that the specimen moved. */
		public Assessment assessment(double threshold) {
			if (!succeeded()) return Assessment.UNSCORED;
			int supportedFrames = 0;
			boolean uncertain = failedSamples > 0;
			for (FrameMetrics m : frameEvidence) {
				boolean reliable = Double.isFinite(m.displacement) && Double.isFinite(m.residual)
						&& m.inlierFraction >= .75 && m.residual < threshold;
				if (reliable && m.displacement >= threshold) supportedFrames++;
				if (!reliable || m.displacement >= threshold) uncertain = true;
			}
			if (supportedFrames >= 2) return Assessment.MOVEMENT;
			// A single spike remains available for review rather than being discarded.
			if (uncertain || frameEvidence.isEmpty()) return Assessment.UNCERTAIN;
			return Assessment.BELOW_THRESHOLD;
		}
		public String detectedPattern(double displacementThresholdPx) {
			if (!succeeded()) return "unscored";
			double componentThreshold = Math.max(.75, displacementThresholdPx * .4);
			List<String> patterns = new ArrayList<String>();
			boolean x = Math.abs(translationXPx) >= componentThreshold;
			boolean y = Math.abs(translationYPx) >= componentThreshold;
			if (x && y) patterns.add("XY translation");
			else if (x) patterns.add("horizontal translation");
			else if (y) patterns.add("vertical translation");
			if (maxRotationDeg >= .05) patterns.add("rotation");
			if (maxScalePercent >= .15) patterns.add("scale change");
			if (maxResidualPx >= Math.max(1.0, displacementThresholdPx * .5))
				patterns.add("registration disagreement (not proof of deformation)");
			if (patterns.isEmpty()) patterns.add("local displacement");
			String joined = String.join(" + ", patterns);
			return confidence < .75 ? "uncertain: " + joined : joined;
		}
		public int reviewPriority(double displacementThresholdPx) {
			int priority = maxDisplacementPx > 15 ? 3 : maxDisplacementPx >= 8 ? 2
					: maxDisplacementPx >= 4 ? 1 : 0;
			boolean complex = maxRotationDeg >= .10 || maxScalePercent >= .15
					|| maxResidualPx >= Math.max(1.0, displacementThresholdPx * .5);
			return complex ? Math.min(3, priority + 1) : priority;
		}
		public String reviewPriorityLabel(double displacementThresholdPx) {
			String[] labels = { "Low", "Moderate", "High", "Very high" };
			String label = labels[reviewPriority(displacementThresholdPx)];
			return confidence < .75 ? label + " — uncertain" : label;
		}
		public String format() {
			if (!succeeded()) return "unscored: " + error;
			return String.format("move %.1f px, rot %.3f deg, scale %.3f%%, residual %.1f px, T=%d, confidence %.0f%%",
					maxDisplacementPx, maxRotationDeg, maxScalePercent, maxResidualPx, worstFrame, confidence * 100);
		}
	}
}
