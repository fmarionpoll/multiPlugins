package plugins.fmp.multitools.experiment.capillary.geometry;

import static org.junit.Assert.*;

import java.awt.geom.Line2D;
import java.util.Map;

import org.junit.Test;

public class CapillaryPhaseGeometryModelTest {
	@Test
	public void poseEditsPreserveWidthAndDetectionCanReplaceItWithUnknown() {
		CapillaryPhaseGeometryModel model = initialized();
		model.putBlue(0, model.getBlueAt(0), 8.);
		model.alignPhase(50, new Line2D.Double(20, 0, 30, 130));
		assertEquals(8., model.getWidthAt(50), 0.);
		model.applyManualGreenEdit(50, new Line2D.Double(25, 0, 35, 130));
		assertEquals(8., model.getWidthAt(50), 0.);
		model.putBlue(100, model.getBlueAt(50), Double.NaN);
		assertTrue(Double.isNaN(model.getWidthAt(100)));
		assertEquals(8., model.getWidthAt(0), 0.);
		model.clear();
		assertTrue(Double.isNaN(model.getWidthAt(0)));
	}

	@Test
	public void outlineHasMeasuredWidthAndCenterlineThroughEndMidpoints() {
		Line2D line = new Line2D.Double(10, 20, 40, 60);
		java.awt.geom.Point2D[] corners = new CapillaryPhaseGeometry(0, null, line, 9.).getPhysicalCorners();
		assertEquals(9., corners[0].distance(corners[3]), 1e-9);
		assertEquals(50., corners[0].distance(corners[1]), 1e-9);
		assertEquals(line.getX1(), .5 * (corners[0].getX() + corners[3].getX()), 1e-9);
		assertEquals(line.getY2(), .5 * (corners[1].getY() + corners[2].getY()), 1e-9);
		assertNull(new CapillaryPhaseGeometry(0, null, line).getPhysicalCorners());
	}
	@Test
	public void initializationInfersUpperAndLowerExtensions() {
		CapillaryPhaseGeometryModel model = initialized();
		assertEquals(0.10, model.getExtensions().getUpper(), 1e-9);
		assertEquals(0.20, model.getExtensions().getLower(), 1e-9);
	}

	@Test
	public void alignmentChangesOnlySelectedPhasePoseAndPreservesBlueLength() {
		CapillaryPhaseGeometryModel model = initialized();
		model.putBlue(50, new Line2D.Double(20, 20, 20, 120));
		Line2D beforePhaseZero = model.getBlueStartingAt(0);
		Line2D aligned = model.alignPhase(50, new Line2D.Double(30, 5, 40, 135));
		assertEquals(100, aligned.getP1().distance(aligned.getP2()), 1e-9);
		assertEquals(beforePhaseZero.getP1(), model.getBlueStartingAt(0).getP1());
		assertEquals(0.10, model.getExtensions().getUpper(), 1e-9);
		assertEquals(0.20, model.getExtensions().getLower(), 1e-9);
	}

	@Test
	public void extensionChangesAreGlobalAndNeverMoveBlueKeyframes() {
		CapillaryPhaseGeometryModel model = initialized();
		model.putBlue(50, new Line2D.Double(20, 20, 20, 120));
		Map<Long, Line2D> before = model.getBlueKeyframes();
		model.changeExtensions(0.05, -0.05);
		assertEquals(0.15, model.getExtensions().getUpper(), 1e-9);
		assertEquals(0.15, model.getExtensions().getLower(), 1e-9);
		assertEquals(before.get(0L).getP1(), model.getBlueStartingAt(0).getP1());
		assertEquals(before.get(50L).getP2(), model.getBlueStartingAt(50).getP2());
		assertEquals(130, model.greenForBlue(model.getBlueStartingAt(50)).getP1().distance(
				model.greenForBlue(model.getBlueStartingAt(50)).getP2()), 1e-9);
	}

	@Test
	public void manualGreenEditPreservesBlueLengthAndUpdatesSharedMargins() {
		CapillaryPhaseGeometryModel model = initialized();
		Line2D blue = model.applyManualGreenEdit(0, new Line2D.Double(20, -20, 40, 140));
		assertEquals(100, blue.getP1().distance(blue.getP2()), 1e-9);
		assertTrue(model.getExtensions().getUpper() > 0.1);
		assertTrue(model.getExtensions().getLower() > 0.2);
	}

	private static CapillaryPhaseGeometryModel initialized() {
		CapillaryPhaseGeometryModel model = new CapillaryPhaseGeometryModel();
		model.initialize(0, new Line2D.Double(10, 0, 10, 130), new Line2D.Double(10, 10, 10, 110));
		return model;
	}
}
