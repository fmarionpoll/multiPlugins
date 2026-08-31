package plugins.fmp.multitools.service.tracking;

import static org.junit.Assert.*;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import plugins.fmp.multitools.service.tracking.PlanarTransform.Model;

public class PlanarTransformFitterTest {
	@Test
	public void choosesTranslationForRigidShift() {
		List<LandmarkMatch> matches = grid((x, y) -> new Point2D.Double(x + 12, y - 7));
		PlanarTransformFit fit = new PlanarTransformFitter().fitSimplest(matches);
		assertEquals(Model.TRANSLATION, fit.getTransform().getModel());
		assertEquals(0, fit.getRms(), 1e-8);
	}

	@Test
	public void choosesSimilarityForZoomAndRotation() {
		double angle = Math.toRadians(2.0), scale = 1.025;
		List<LandmarkMatch> matches = grid((x, y) -> new Point2D.Double(
				scale * (Math.cos(angle) * x - Math.sin(angle) * y) + 6,
				scale * (Math.sin(angle) * x + Math.cos(angle) * y) - 4));
		PlanarTransformFit fit = new PlanarTransformFitter().fitSimplest(matches);
		assertEquals(Model.SIMILARITY, fit.getTransform().getModel());
		assertEquals(0, fit.getRms(), 1e-7);
	}

	@Test
	public void choosesProjectiveForPositionDependentScaleAndRejectsOutlier() {
		List<LandmarkMatch> matches = grid((x, y) -> {
			double w = 1 + 0.00015 * x - 0.00022 * y;
			return new Point2D.Double((1.01 * x + 0.006 * y + 5) / w,
					(-0.004 * x + 1.02 * y - 3) / w);
		});
		matches.add(new LandmarkMatch(new Point2D.Double(400, 300), new Point2D.Double(900, 20), 1));
		PlanarTransformFit fit = new PlanarTransformFitter().fitSimplest(matches);
		assertEquals(Model.PROJECTIVE, fit.getTransform().getModel());
		assertTrue(fit.getInlierIndices().size() < matches.size());
		assertEquals(0, fit.getRms(), 1e-5);
	}

	private interface Mapping { Point2D apply(double x, double y); }

	private static List<LandmarkMatch> grid(Mapping mapping) {
		List<LandmarkMatch> matches = new ArrayList<LandmarkMatch>();
		for (int y : new int[] { 80, 350, 650 })
			for (int x : new int[] { 100, 450, 850, 1200 })
				matches.add(new LandmarkMatch(new Point2D.Double(x, y), mapping.apply(x, y), 1));
		return matches;
	}
}
