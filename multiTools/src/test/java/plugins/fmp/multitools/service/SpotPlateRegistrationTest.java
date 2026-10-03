package plugins.fmp.multitools.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import plugins.fmp.multitools.service.SpotPlateRegistration.Pose;
import plugins.fmp.multitools.service.SpotPlateRegistration.Step;
import plugins.fmp.multitools.service.SpotRimAnalyzer.EllipseGeom;

public class SpotPlateRegistrationTest {

	@Test
	public void onePixelShiftIsRecovered() {
		int width = 100;
		int height = 80;
		EllipseGeom[] spots = new EllipseGeom[] { spot(25, 25), spot(70, 25), spot(25, 55), spot(70, 55) };
		int[] prev = image(width, height, spots, 0, 0);
		int[] curr = image(width, height, spots, 1, 0);
		Step step = fit(width, height, prev, curr, spots);
		assertEquals(1.0, step.dx, 0.35);
		assertEquals(0.0, step.dy, 0.35);
		assertEquals(0.0, step.angleRad, 0.01);
		double[] pivot = SpotPlateRegistration.pivot(Arrays.asList(spots));
		int[] back = SpotPlateRegistration.warp(curr, width, height,
				Pose.identity(pivot[0], pivot[1]).compose(step));
		assertTrue(back[25 * width + 25] > 100);
	}

	@Test
	public void oneDisagreeingSpotDoesNotMoveTheMedian() {
		int width = 120;
		int height = 90;
		EllipseGeom[] spots = new EllipseGeom[] { spot(25, 25), spot(60, 25), spot(95, 25), spot(25, 60),
				spot(70, 60) };
		int[] prev = image(width, height, spots, 0, 0);
		int[] curr = Arrays.copyOf(prev, prev.length);
		Arrays.fill(curr, 30);
		paint(curr, width, spots[0], 1, 0);
		paint(curr, width, spots[1], 1, 0);
		paint(curr, width, spots[2], 1, 0);
		paint(curr, width, spots[3], 1, 0);
		paint(curr, width, spots[4], 3, 0);
		Step step = fit(width, height, prev, curr, spots);
		assertEquals(1.0, step.dx, 0.35);
		assertEquals(0.0, step.dy, 0.35);
	}

	@Test
	public void oppositeTangentialShiftsRecoverASmallAngle() {
		int width = 400;
		int height = 400;
		EllipseGeom top = spot(200, 50);
		EllipseGeom bottom = spot(200, 350);
		EllipseGeom left = spot(50, 200);
		EllipseGeom right = spot(350, 200);
		EllipseGeom[] spots = new EllipseGeom[] { top, bottom, left, right };
		int[] prev = image(width, height, spots, 0, 0);
		int[] curr = blank(width, height);
		paint(curr, width, top, 1, 0);
		paint(curr, width, bottom, -1, 0);
		paint(curr, width, left, 0, -1);
		paint(curr, width, right, 0, 1);
		Step step = fit(width, height, prev, curr, spots);
		assertEquals(0.0, step.dx, 0.35);
		assertEquals(0.0, step.dy, 0.35);
		assertEquals(1.0 / 150.0, step.angleRad, 0.003);
	}

	@Test
	public void identicalFramesStayAtZero() {
		int width = 100;
		int height = 80;
		EllipseGeom[] spots = new EllipseGeom[] { spot(25, 25), spot(70, 25), spot(25, 55), spot(70, 55) };
		int[] prev = image(width, height, spots, 0, 0);
		Step step = fit(width, height, prev, prev, spots);
		assertEquals(0.0, step.dx, 1e-9);
		assertEquals(0.0, step.dy, 1e-9);
		assertEquals(0.0, step.angleRad, 1e-9);
	}

	private static Step fit(int width, int height, int[] prev, int[] curr, EllipseGeom[] spots) {
		List<EllipseGeom> ellipses = Arrays.asList(spots);
		double[] pivot = SpotPlateRegistration.pivot(ellipses);
		return SpotPlateRegistration.fit(width, height, prev, prev, prev, curr, curr, curr, null, null, ellipses,
				pivot[0], pivot[1]);
	}

	private static EllipseGeom spot(double cx, double cy) {
		return new EllipseGeom(cx, cy, 10, 10);
	}

	private static int[] image(int width, int height, EllipseGeom[] spots, int dx, int dy) {
		int[] pixels = blank(width, height);
		for (EllipseGeom spot : spots) {
			paint(pixels, width, spot, dx, dy);
		}
		return pixels;
	}

	private static int[] blank(int width, int height) {
		int[] pixels = new int[width * height];
		Arrays.fill(pixels, 30);
		return pixels;
	}

	private static void paint(int[] pixels, int width, EllipseGeom spot, int dx, int dy) {
		int height = pixels.length / width;
		double cx = spot.cx + dx;
		double cy = spot.cy + dy;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				if (Math.hypot(x - cx, y - cy) <= 6) {
					pixels[y * width + x] = 180;
				}
			}
		}
	}
}
