package plugins.fmp.multitools.experiment.capillary.geometry;

import static org.junit.Assert.*;

import java.awt.geom.Line2D;
import java.util.Map;

import org.junit.Test;

public class CapillaryPhaseGeometryModelTest {
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
