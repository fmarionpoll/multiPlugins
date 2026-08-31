package plugins.fmp.multitools.experiment.capillary.geometry;

import static org.junit.Assert.*;

import java.awt.geom.Line2D;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillary.Capillary;

public class CapillaryPhaseGeometryPersistenceTest {
	@Test
	public void roundTripsRatiosAndBlueKeyframes() throws Exception {
		Capillaries source = new Capillaries();
		Capillary capillary = new Capillary();
		capillary.setKymographName("line01");
		capillary.getPhaseGeometry().initialize(0, new Line2D.Double(10, 0, 10, 130),
				new Line2D.Double(10, 10, 10, 110));
		capillary.getPhaseGeometry().putBlue(50, new Line2D.Double(20, 15, 20, 117));
		source.addCapillary(capillary);
		Path dir = Files.createTempDirectory("phase-geometry");
		assertTrue(CapillaryPhaseGeometryPersistence.save(source, dir.toString()));

		Capillaries loaded = new Capillaries();
		Capillary loadedCapillary = new Capillary();
		loadedCapillary.setKymographName("line01");
		loaded.addCapillary(loadedCapillary);
		assertTrue(CapillaryPhaseGeometryPersistence.load(loaded, dir.toString()));
		assertEquals(0.10, loadedCapillary.getPhaseGeometry().getExtensions().getUpper(), 1e-9);
		assertEquals(102, loadedCapillary.getPhaseGeometry().getBlueStartingAt(50).getP1().distance(
				loadedCapillary.getPhaseGeometry().getBlueStartingAt(50).getP2()), 1e-9);
	}
}
