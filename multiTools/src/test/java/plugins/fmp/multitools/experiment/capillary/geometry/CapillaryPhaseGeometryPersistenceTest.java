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
				new Line2D.Double(10, 10, 10, 110), 8.5);
		capillary.getPhaseGeometry().putBlue(50, new Line2D.Double(20, 15, 20, 117), 9.);
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
		assertEquals(8.5, loadedCapillary.getPhaseGeometry().getWidthAt(0), 0.);
		assertEquals(9., loadedCapillary.getPhaseGeometry().getWidthAt(60), 0.);
	}

	@Test
	public void legacyFileLoadsWithUnknownWidth() throws Exception {
		Path dir = Files.createTempDirectory("legacy-phase-geometry");
		String name = java.util.Base64.getEncoder().encodeToString("line01".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		Files.write(dir.resolve(CapillaryPhaseGeometryPersistence.FILE_NAME),
				("# multiCAFE coordinated capillary phase geometry v1\n"
				+ "type;capillary_base64;frame_or_upper;lower_or_x1;y1;x2;y2\n"
				+ "RATIO;" + name + ";0.1;0.2;;;\n"
				+ "BLUE;" + name + ";0;10;10;10;110\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
		Capillary cap = new Capillary();
		cap.setKymographName("line01");
		Capillaries caps = new Capillaries();
		caps.addCapillary(cap);
		assertTrue(CapillaryPhaseGeometryPersistence.load(caps, dir.toString()));
		assertTrue(cap.getPhaseGeometry().isInitialized());
		assertTrue(Double.isNaN(cap.getPhaseGeometry().getWidthAt(0)));
		assertTrue(CapillaryPhaseGeometryPersistence.save(caps, dir.toString()));
		assertTrue(CapillaryPhaseGeometryPersistence.load(caps, dir.toString()));
		assertTrue(Double.isNaN(cap.getPhaseGeometry().getWidthAt(0)));
	}
}
