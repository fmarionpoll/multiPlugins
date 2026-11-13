package plugins.fmp.multitools.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.TreeMap;

import org.junit.Test;

import icy.image.IcyBufferedImage;
import icy.type.DataType;

public class KymographAlignmentTest {
	@Test
	public void nearbyEdgesRejectAnIndentationThatMovesIndependently() {
		BufferedImage image = new BufferedImage(12, 72, BufferedImage.TYPE_INT_RGB);
		for (int x = 0; x < image.getWidth(); x++) {
			int motion = x >= 6 ? 2 : 0;
			int indentation = x >= 8 ? 3 : 0;
			for (int y = 0; y < image.getHeight(); y++) {
				boolean dark = Math.abs(y - 16 - motion) <= 1 || Math.abs(y - 26 - motion) <= 1
						|| Math.abs(y - 36 - motion - indentation) <= 1 || Math.abs(y - 46 - motion) <= 1
						|| Math.abs(y - 56 - motion) <= 1;
				image.setRGB(x, y, (dark ? 30 : 190) * 0x010101);
			}
		}
		KymographAlignment.Result result = KymographAlignment.analyze(image, Collections.singletonMap(2, 36.0),
				0, 11, KymographAlignment.Target.HORIZONTAL);
		assertEquals(12, result.matchedColumns);
		assertEquals(38, result.tracked[10], 0);
		KymographAlignment.Result single = KymographAlignment.analyze(image, Collections.singletonMap(2, 36.0),
				0, 11, KymographAlignment.Target.HORIZONTAL, false);
		assertEquals(41, single.tracked[10], 0);
	}

	@Test
	public void addsRangeAndInterpolatedCurveMovesBeforeResampling() {
		IcyBufferedImage image = new IcyBufferedImage(6, 12, 1, DataType.UBYTE);
		byte[] source = image.getDataXYAsByte(0);
		for (int y = 0; y < 12; y++) {
			for (int x = 0; x < 6; x++)
				source[y * 6 + x] = (byte) (10 * y);
		}
		image.dataChanged();
		TreeMap<Integer, Double> curve = new TreeMap<>();
		curve.put(2, 0.0);
		curve.put(4, -2.0);
		KymographAlignment.Result moves = KymographAlignment.addManualShifts(null,
				new double[] { 0, 2, 2, 0, 0, 0 }, curve);
		byte[] corrected = KymographAlignment.align(image, moves).getDataXYAsByte(0);
		int row = 5 * 6;
		assertEquals(50, corrected[row]);
		assertEquals(30, corrected[row + 1]);
		assertEquals(30, corrected[row + 2]);
		assertEquals(60, corrected[row + 3]);
		assertEquals(70, corrected[row + 4]);
		assertEquals(50, corrected[row + 5]);
	}

	@Test
	public void correctsAbruptVibrationAndResumesAfterMissingFeature() {
		BufferedImage image = new BufferedImage(10, 40, BufferedImage.TYPE_INT_RGB);
		int[] centers = { 20, 20, 17, 23, 20, -1, 20, 23, 17, 20 };
		for (int x = 0; x < centers.length; x++) {
			for (int y = 0; y < image.getHeight(); y++) {
				int gray = Math.abs(y - centers[x]) <= 1 && centers[x] >= 0 ? 40 : 180;
				image.setRGB(x, y, gray * 0x010101);
			}
		}

		KymographAlignment.Result result = KymographAlignment.analyze(image, Collections.singletonMap(4, 20.0),
				1, 8, KymographAlignment.Target.HORIZONTAL);
		assertEquals(7, result.matchedColumns);
		assertEquals(3, result.maxShift, 1e-9);
		assertTrue(Double.isNaN(result.tracked[5]));
		assertEquals(23, result.tracked[7], 1e-9);

		IcyBufferedImage source = new IcyBufferedImage(10, 40, 2, DataType.USHORT);
		for (int c = 0; c < source.getSizeC(); c++) {
			short[] pixels = source.getDataXYAsShort(c);
			for (int y = 0; y < 40; y++) {
				for (int x = 0; x < 10; x++)
					pixels[y * 10 + x] = (short) (40000 + 100 * c + y);
			}
		}
		source.dataChanged();
		IcyBufferedImage aligned = KymographAlignment.align(source, result);
		assertEquals(DataType.USHORT, aligned.getDataType_());
		assertEquals(2, aligned.getSizeC());
		short[] pixels = aligned.getDataXYAsShort(1);
		assertEquals(40120, Short.toUnsignedInt(pixels[200]));
		assertEquals(40117, Short.toUnsignedInt(pixels[202]));
		assertEquals(40123, Short.toUnsignedInt(pixels[203]));
		assertEquals(40120, Short.toUnsignedInt(pixels[205]));
		assertEquals(40100, Short.toUnsignedInt(pixels[2]));
		assertEquals(40120, Short.toUnsignedInt(source.getDataXYAsShort(1)[202]));
	}

	@Test
	public void preservesTheOverallSlopeWhenFittingAStraightReference() {
		BufferedImage image = new BufferedImage(21, 40, BufferedImage.TYPE_INT_RGB);
		for (int x = 0; x < image.getWidth(); x++) {
			double row = 15 + .25 * x + (x % 2 == 0 ? 0 : 2);
			for (int y = 0; y < image.getHeight(); y++) {
				int gray = (int) Math.round(180 - 100 * Math.exp(-.5 * Math.pow((y - row) / 1.4, 2)));
				image.setRGB(x, y, gray * 0x010101);
			}
		}
		KymographAlignment.Result result = KymographAlignment.analyze(image, Collections.singletonMap(8, 17.0),
				0, 20, KymographAlignment.Target.STRAIGHT);
		assertEquals(21, result.matchedColumns);
		assertEquals(5, result.target[20] - result.target[0], .15);
		assertEquals(result.target[20] - result.target[10], result.target[10] - result.target[0], 1e-9);
	}
}
