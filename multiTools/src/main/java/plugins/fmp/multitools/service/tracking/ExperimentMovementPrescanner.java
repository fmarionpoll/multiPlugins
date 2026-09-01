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
		IcyBufferedImage previous = load(sequence, first);
		List<FrameMetrics> metrics = new ArrayList<FrameMetrics>();
		for (int frame = first + 1; frame <= last; frame++) {
			if (cancelled != null && cancelled.getAsBoolean()) {
				analysis.cancelled = true;
				break;
			}
			IcyBufferedImage current = load(sequence, frame);
			if (previous == null || current == null) {
				analysis.failedFrames++;
				previous = current;
				continue;
			}
			FrameMetrics value = compareFrame(landmarks, previous, current, frame);
			if (value == null)
				analysis.failedFrames++;
			else
				metrics.add(value);
			previous = current;
		}
		analysis.comparedFrames = metrics.size();
		if (metrics.isEmpty()) {
			analysis.error = "no consecutive frame pair could be registered";
			return analysis;
		}
		double[] scores = new double[metrics.size()];
		List<Double> ordered = new ArrayList<Double>();
		for (int i = 0; i < metrics.size(); i++) {
			FrameMetrics m = metrics.get(i);
			scores[i] = Math.max(m.displacement, Math.max(m.residual, Math.max(m.rotation * 20, m.scalePercent * 5)));
			ordered.add(scores[i]);
		}
		Collections.sort(ordered);
		double median = ordered.get(ordered.size() / 2);
		List<Double> deviations = new ArrayList<Double>();
		for (double value : ordered) deviations.add(Math.abs(value - median));
		Collections.sort(deviations);
		double mad = deviations.get(deviations.size() / 2);
		double threshold = Math.max(minimumMovementPx, median + Math.max(.5, 4 * mad));
		analysis.thresholdUsed = threshold;
		for (int i = 0; i < metrics.size(); i++) {
			if (scores[i] < threshold)
				continue;
			boolean left = i == 0 || scores[i] >= scores[i - 1];
			boolean right = i == metrics.size() - 1 || scores[i] > scores[i + 1];
			if (!left || !right)
				continue;
			FrameMetrics m = metrics.get(i);
			double confidence = Math.min(1, m.inlierFraction * Math.min(1.5, scores[i] / Math.max(.001, threshold)));
			analysis.proposals.add(new TransitionProposal(m.frame, m.translationX, m.translationY, m.displacement,
					m.rotation, m.scalePercent, m.residual, confidence, scores[i]));
		}
		return analysis;
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
		Collections.sort(magnitudes);
		int p90index = Math.min(magnitudes.size() - 1, (int) Math.ceil(magnitudes.size() * .9) - 1);
		double displacement90 = magnitudes.get(Math.max(0, p90index));
		double inlierFraction = fit.getInlierIndices().size() / (double) matches.size();
		return new FrameMetrics(frame, displacement90, translationX, translationY, Math.abs(rotationDeg),
				Math.abs(scale - 1) * 100, fit.getRms(), inlierFraction);
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

	private static final class FrameMetrics {
		final int frame;
		final double displacement, translationX, translationY, rotation, scalePercent, residual, inlierFraction;
		FrameMetrics(int frame, double displacement, double translationX, double translationY, double rotation,
				double scalePercent, double residual, double inlierFraction) {
			this.frame = frame; this.displacement = displacement; this.rotation = rotation;
			this.translationX = translationX; this.translationY = translationY;
			this.scalePercent = scalePercent; this.residual = residual; this.inlierFraction = inlierFraction;
		}
	}

	public static final class TransitionAnalysis {
		public final List<TransitionProposal> proposals = new ArrayList<TransitionProposal>();
		public int comparedFrames, failedFrames;
		public double thresholdUsed;
		public boolean cancelled;
		public String error;
		public boolean succeeded() { return error == null && comparedFrames > 0; }
	}

	public static final class TransitionProposal {
		public final int frame;
		public final double translationX, translationY, displacement, rotationDeg, scalePercent, residualPx;
		public final double confidence, score;
		public TransitionProposal(int frame, double translationX, double translationY, double displacement,
				double rotationDeg, double scalePercent, double residualPx, double confidence, double score) {
			this.frame = frame; this.translationX = translationX; this.translationY = translationY;
			this.displacement = displacement; this.rotationDeg = rotationDeg; this.scalePercent = scalePercent;
			this.residualPx = residualPx; this.confidence = confidence; this.score = score;
		}
		public TransitionProposal atFrame(int newFrame) {
			return new TransitionProposal(newFrame, translationX, translationY, displacement, rotationDeg,
					scalePercent, residualPx, confidence, score);
		}
		public String summary() {
			return String.format("T=%d  Δx=%.1f Δy=%.1f px, rot=%.3f°, scale=%.3f%%, residual=%.1f px, confidence=%.0f%%",
					frame, translationX, translationY, rotationDeg, scalePercent, residualPx, confidence * 100);
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

		private Result(Experiment experiment) { this.experiment = experiment; }
		static Result failed(Experiment experiment, String error) {
			Result result = new Result(experiment); result.error = error; return result;
		}
		void accept(FrameMetrics m) {
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
			return succeeded() && (maxDisplacementPx >= displacementThresholdPx
					|| maxResidualPx >= displacementThresholdPx);
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
				patterns.add("local deformation / perspective");
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
