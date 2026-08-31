package plugins.fmp.multitools.service.tracking;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Combines noisy per-capillary motion estimates into one rigid-frame planar
 * transform. Each capillary contributes its two physical glass endpoints.
 */
public final class CapillaryFrameRegistration {
	private final PlanarTransformFitter fitter = new PlanarTransformFitter();

	public Result fit(List<Line2D> sourcePhysical, List<Line2D> locallyTracked) {
		if (sourcePhysical == null || locallyTracked == null || sourcePhysical.size() != locallyTracked.size())
			throw new IllegalArgumentException("source and tracked capillary lists must have the same size");
		List<LandmarkMatch> matches = new ArrayList<LandmarkMatch>();
		List<Integer> capillaryByMatch = new ArrayList<Integer>();
		for (int i = 0; i < sourcePhysical.size(); i++) {
			Line2D source = sourcePhysical.get(i);
			Line2D target = locallyTracked.get(i);
			if (!valid(source) || !valid(target))
				continue;
			target = orientLike(target, source);
			matches.add(new LandmarkMatch(source.getP1(), target.getP1(), 1));
			capillaryByMatch.add(i);
			matches.add(new LandmarkMatch(source.getP2(), target.getP2(), 1));
			capillaryByMatch.add(i);
		}
		if (matches.isEmpty())
			throw new IllegalArgumentException("no valid capillary landmarks");
		PlanarTransformFit fit = fitter.fitSimplest(matches);
		Set<Integer> inlierCaps = new LinkedHashSet<Integer>();
		for (int matchIndex : fit.getInlierIndices())
			inlierCaps.add(capillaryByMatch.get(matchIndex));
		List<Line2D> registered = new ArrayList<Line2D>(sourcePhysical.size());
		for (Line2D source : sourcePhysical)
			registered.add(valid(source) ? transform(source, fit.getTransform()) : null);
		return new Result(fit, registered, new ArrayList<Integer>(inlierCaps), matches.size());
	}

	private static boolean valid(Line2D line) {
		return line != null && line.getP1().distance(line.getP2()) > 1e-6;
	}

	private static Line2D orientLike(Line2D candidate, Line2D reference) {
		double rdx = reference.getX2() - reference.getX1();
		double rdy = reference.getY2() - reference.getY1();
		double cdx = candidate.getX2() - candidate.getX1();
		double cdy = candidate.getY2() - candidate.getY1();
		return rdx * cdx + rdy * cdy >= 0 ? candidate : new Line2D.Double(candidate.getP2(), candidate.getP1());
	}

	private static Line2D transform(Line2D line, PlanarTransform transform) {
		Point2D p1 = transform.transform(line.getP1());
		Point2D p2 = transform.transform(line.getP2());
		return new Line2D.Double(p1, p2);
	}

	public static final class Result {
		private final PlanarTransformFit fit;
		private final List<Line2D> registeredLines;
		private final List<Integer> inlierCapillaryIndices;
		private final int landmarkCount;

		Result(PlanarTransformFit fit, List<Line2D> registeredLines, List<Integer> inlierCaps, int landmarkCount) {
			this.fit = fit;
			this.registeredLines = Collections.unmodifiableList(registeredLines);
			this.inlierCapillaryIndices = Collections.unmodifiableList(inlierCaps);
			this.landmarkCount = landmarkCount;
		}

		public PlanarTransformFit getFit() { return fit; }
		public List<Line2D> getRegisteredLines() { return registeredLines; }
		public List<Integer> getInlierCapillaryIndices() { return inlierCapillaryIndices; }
		public int getLandmarkCount() { return landmarkCount; }
	}
}
