package plugins.fmp.multitools.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.awt.geom.Point2D;

import org.junit.Test;

import plugins.fmp.multitools.service.CapillaryLengthDetector.ImageData;

/*
 * CODEX
 */
public class CapillaryGlassTipDetectorTest {
	@Test
	public void greenLiquidHasIndependentRimAndLiquidBoundary() {
		ImageData image=neck(16,20,true,true);
		for(int y=20;y<140;y++) for(int x=37;x<44;x++) {
			int p=x+y*image.width;
			image.channels[0][p]=140.; image.channels[1][p]=150.; image.channels[2][p]=60.;
		}
		CapillaryGlassTipDetector.Evidence e=CapillaryGlassTipDetector.find(image,
				new Point2D.Double(40,34),new Point2D.Double(40,135),3.625);
		assertNotNull(e.glassTip); assertEquals(16.,e.glassTip.getY(),2.);
		assertNotNull(e.liquidTop); assertEquals(20.,e.liquidTop.getY(),5.);
	}
	@Test
	public void shortEmptyNeckNearImageTopHasItsOwnRim() {
		for (boolean bright : new boolean[] {false,true}) {
			CapillaryGlassTipDetector.Evidence e = CapillaryGlassTipDetector.find(neck(16,20,true,bright),
					new Point2D.Double(40,34),new Point2D.Double(40,135),3.625);
			assertNotNull("rim with a truncated search window",e.glassTip);
			assertEquals(16.,e.glassTip.getY(),2.);
			assertNotNull(e.liquidTop);
			assertEquals(20.,e.liquidTop.getY(),5.);
		}
	}

	@Test
	public void brightEmptyWallsWithoutADarkCrossbarStillEndAboveLiquid() {
		CapillaryGlassTipDetector.Evidence e = CapillaryGlassTipDetector.find(neck(33,50,false,true),
				new Point2D.Double(40,40),new Point2D.Double(40,135),3.625);
		assertNotNull(e.glassTip);
		assertEquals(33.,e.glassTip.getY(),2.);
		assertNotNull(e.liquidTop);
		assertEquals(50.,e.liquidTop.getY(),5.);
	}

	@Test
	public void backgroundCrossbarAndShortReflectionsAreNotAGlassRim() {
		ImageData image = neck(16,20,true,true);
		for (int c=0;c<3;c++) for (int x=0;x<image.width;x++) image.channels[c][x+16*image.width]=110.;
		CapillaryGlassTipDetector.Evidence e = CapillaryGlassTipDetector.find(image,
				new Point2D.Double(40,34),new Point2D.Double(40,135),3.625);
		assertNull("a background line is not a localized rim",e.glassTip);
		assertNotNull(e.liquidTop);
	}

	private ImageData neck(int rim,int liquid,boolean crossbar,boolean bright) {
		int width=80,height=180;
		double[][] pixels=new double[3][width*height];
		for(int y=0;y<height;y++) for(int x=0;x<width;x++) for(int c=0;c<3;c++) {
			double value=180.;
			if(y>=liquid&&y<140&&x>36&&x<44) value=c==0?70.:150.;
			if(y>=rim&&y<liquid&&(x==36||x==44)) value=bright?230.:110.;
			if(crossbar&&y==rim&&x>=36&&x<=44) value=110.;
			pixels[c][x+y*width]=value;
		}
		return new ImageData(width,height,pixels);
	}
	@Test
	public void glassRimIsIndependentOfFillingLevel() {
		for (int liquid : new int[] { 40, 52, 60 }) {
			CapillaryGlassTipDetector.Evidence e = CapillaryGlassTipDetector.find(tube(liquid, true),
					new Point2D.Double(40, 48), new Point2D.Double(40, 135), 4.);
			assertNotNull(e.glassTip);
			assertEquals(40., e.glassTip.getY(), 2.);
			assertNotNull(e.liquidTop);
			assertEquals(liquid, e.liquidTop.getY(), 5.);
		}
	}

	@Test
	public void colourBoundaryWithoutWallsIsNotAGlassTip() {
		CapillaryGlassTipDetector.Evidence e = CapillaryGlassTipDetector.find(tube(52, false),
				new Point2D.Double(40, 48), new Point2D.Double(40, 135), 4.);
		assertNull(e.glassTip);
		assertNotNull(e.liquidTop);
	}

	@Test
	public void imageBorderIsUncertainRatherThanClamped() {
		assertNull(CapillaryGlassTipDetector.find(tube(52, true), new Point2D.Double(2, 48), new Point2D.Double(2, 135),
				4.).glassTip);
	}

	private ImageData tube(int liquid, boolean walls) {
		int width = 80, height = 180;
		double[][] pixels = new double[3][width * height];
		for (int y = 0; y < height; y++)
			for (int x = 0; x < width; x++)
				for (int c = 0; c < 3; c++) {
					double v = 180.;
					if (y >= liquid && y < 140 && x > 36 && x < 44)
						v = c == 0 ? 70. : 150.;
					if (walls && y >= 40 && y < 140 && (x == 36 || x == 44))
						v = 120.;
					pixels[c][x + y * width] = v;
				}
		return new ImageData(width, height, pixels);
	}
}
