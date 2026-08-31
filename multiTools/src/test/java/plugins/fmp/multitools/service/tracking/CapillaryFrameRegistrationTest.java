package plugins.fmp.multitools.service.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import plugins.fmp.multitools.service.tracking.PlanarTransform.Model;

public class CapillaryFrameRegistrationTest {
	@Test
	public void translationUsesAllCapillaryEnds() {
		List<Line2D> source = grid();
		List<Line2D> target = map(source, p -> new Point2D.Double(p.getX() + 7, p.getY() - 3));
		CapillaryFrameRegistration.Result result = new CapillaryFrameRegistration().fit(source, target);
		assertEquals(Model.TRANSLATION, result.getFit().getTransform().getModel());
		assertEquals(0, result.getFit().getRms(), 1e-8);
		assertEquals(source.size() * 2, result.getLandmarkCount());
	}

	@Test
	public void selectsSimilarityForRotationAndZoom() {
		List<Line2D> source = grid();
		double a = Math.toRadians(3), scale = 1.04;
		List<Line2D> target = map(source, p -> new Point2D.Double(
				20 + scale * (Math.cos(a) * p.getX() - Math.sin(a) * p.getY()),
				-8 + scale * (Math.sin(a) * p.getX() + Math.cos(a) * p.getY())));
		CapillaryFrameRegistration.Result result = new CapillaryFrameRegistration().fit(source, target);
		assertEquals(Model.SIMILARITY, result.getFit().getTransform().getModel());
		assertTrue(result.getFit().getRms() < 1e-7);
	}

	@Test
	public void rejectsOneDetachedCapillary() {
		List<Line2D> source = grid();
		List<Line2D> target = map(source, p -> new Point2D.Double(p.getX() + 4, p.getY() + 2));
		Line2D bad = target.get(2);
		target.set(2, new Line2D.Double(bad.getX1() + 80, bad.getY1() - 50, bad.getX2() + 80, bad.getY2() - 50));
		CapillaryFrameRegistration.Result result = new CapillaryFrameRegistration().fit(source, target);
		assertFalse(result.getInlierCapillaryIndices().contains(2));
		assertTrue(result.getFit().getRms() < 1e-7);
	}

	private static List<Line2D> grid() {
		List<Line2D> lines = new ArrayList<Line2D>();
		for (int row = 0; row < 2; row++)
			for (int col = 0; col < 5; col++) {
				double x = 100 + col * 120, y = 80 + row * 300;
				lines.add(new Line2D.Double(x, y, x + 4, y + 180));
			}
		return lines;
	}

	private interface Mapping { Point2D apply(Point2D point); }

	private static List<Line2D> map(List<Line2D> source, Mapping mapping) {
		List<Line2D> result = new ArrayList<Line2D>();
		for (Line2D line : source)
			result.add(new Line2D.Double(mapping.apply(line.getP1()), mapping.apply(line.getP2())));
		return result;
	}
}
