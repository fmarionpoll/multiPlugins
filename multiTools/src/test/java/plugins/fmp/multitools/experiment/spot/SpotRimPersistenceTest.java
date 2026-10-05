package plugins.fmp.multitools.experiment.spot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.Color;

import org.junit.Test;

public class SpotRimPersistenceTest {

	@Test
	public void descriptionRoundTripKeepsOutline() {
		Spot spot = describedSpot();
		spot.getRimGeometry().setRimWidthPx(3);
		spot.getRimGeometry().setOuterPx(6);
		spot.getRimGeometry().setFloorWidthPx(7);
		spot.getRimGeometry().setOutline(new double[] { 10.5, 20.25, 15 }, new double[] { 30, 30.5, 40.125 });

		Spot loaded = new Spot();
		SpotPersistence.csvImportSpotDescription(loaded, fieldsOf(spot));

		assertEquals("spotA", loaded.getProperties().getName());
		assertEquals(3, loaded.getRimGeometry().getRimWidthPx());
		assertEquals(6, loaded.getRimGeometry().getOuterPx());
		assertEquals(7, loaded.getRimGeometry().getFloorWidthPx());
		assertTrue(loaded.getRimGeometry().hasOutline());
		assertEquals(3, loaded.getRimGeometry().vertexCount());
		assertEquals(10.5, loaded.getRimGeometry().outlineX()[0], 0.001);
		assertEquals(40.125, loaded.getRimGeometry().outlineY()[2], 0.001);
	}

	@Test
	public void outlineWithoutFloorWidthKeepsDefault() {
		Spot spot = describedSpot();
		spot.getRimGeometry().setOutline(new double[] { 10, 20, 15 }, new double[] { 30, 30, 40 });
		String[] fields = fieldsOf(spot);
		String[] older = java.util.Arrays.copyOf(fields, fields.length - 1);

		Spot loaded = new Spot();
		SpotPersistence.csvImportSpotDescription(loaded, older);

		assertTrue(loaded.getRimGeometry().hasOutline());
		assertEquals(SpotRimGeometry.DEFAULT_FLOOR_WIDTH_PX, loaded.getRimGeometry().getFloorWidthPx());
	}

	@Test
	public void olderDescriptionWithoutRimColumnsLoads() {
		String[] data = new String[] { "spotA", "0", "1", "0", "0", "0", "0.5", "10", "0", "water", "1", "0", "0",
				"255", "0", "" };
		Spot loaded = new Spot();
		SpotPersistence.csvImportSpotDescription(loaded, data);
		assertEquals("spotA", loaded.getProperties().getName());
		assertFalse(loaded.getRimGeometry().hasOutline());
		assertEquals(SpotRimGeometry.DEFAULT_RIM_WIDTH_PX, loaded.getRimGeometry().getRimWidthPx());
		assertEquals(SpotRimGeometry.DEFAULT_OUTER_PX, loaded.getRimGeometry().getOuterPx());
		assertEquals(SpotRimGeometry.DEFAULT_FLOOR_WIDTH_PX, loaded.getRimGeometry().getFloorWidthPx());
	}

	private static Spot describedSpot() {
		Spot spot = new Spot();
		SpotProperties props = spot.getProperties();
		props.setName("spotA");
		props.setSpotArrayIndex(0);
		props.setCageID(1);
		props.setCagePosition(0);
		props.setCageColumn(0);
		props.setCageRow(0);
		props.setSpotVolume(0.5);
		props.setSpotNPixels(10);
		props.setSpotRadius(0);
		props.setStimulus("water");
		props.setConcentration("1");
		props.setColor(new Color(0, 0, 255));
		return spot;
	}

	private static String[] fieldsOf(Spot spot) {
		String line = SpotPersistence.csvExportSpotDescription(spot, ";").trim();
		return line.split(";", -1);
	}
}
