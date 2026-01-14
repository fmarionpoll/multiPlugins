package plugins.fmp.multiSPOTS.tools;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.Arrays;

import javax.swing.SwingConstants;
import javax.vecmath.Vector2d;

import edu.emory.mathcs.jtransforms.fft.FloatFFT_2D;
import flanagan.complex.Complex;

import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;
import icy.type.DataType;
import icy.type.collection.array.Array1DUtil;

public class GaspardRigidRegistration {

	public static Vector2d findTranslation2D(IcyBufferedImage source, int sourceC, IcyBufferedImage target,
			int targetC) {
		if (!source.getBounds().equals(target.getBounds()))
			throw new UnsupportedOperationException("Cannot register images of different size (yet)");

		int width = source.getWidth();
		int height = source.getHeight();

		float[] _source = Array1DUtil.arrayToFloatArray(source.getDataXY(sourceC), source.isSignedDataType());
		float[] _target = Array1DUtil.arrayToFloatArray(target.getDataXY(targetC), target.isSignedDataType());

		float[] correlationMap = spectralCorrelation(_source, _target, width, height);

		// Find maximum correlation

		int argMax = argMax(correlationMap, correlationMap.length);

		int transX = argMax % width;
		int transY = argMax / width;

		if (transX > width / 2)
			transX -= width;
		if (transY > height / 2)
			transY -= height;

		// recover (x,y)
		return new Vector2d(-transX, -transY);
	}

	private static float[] spectralCorrelation(float[] a1, float[] a2, int width, int height) {
		// JTransforms's FFT takes dimensions as (rows, columns)
		FloatFFT_2D fft = new FloatFFT_2D(height, width);

		return spectralCorrelation(a1, a2, width, height, fft);
	}

	private static float[] spectralCorrelation(float[] a1, float[] a2, int width, int height, FloatFFT_2D fft) {
		// FFT on images
		float[] sourceFFT = forwardFFT(a1, fft);
		float[] targetFFT = forwardFFT(a2, fft);

		// Compute correlation

		Complex c1 = new Complex(), c2 = new Complex();
		for (int i = 0; i < sourceFFT.length; i += 2) {
			c1.setReal(sourceFFT[i]);
			c1.setImag(sourceFFT[i + 1]);

			c2.setReal(targetFFT[i]);
			c2.setImag(targetFFT[i + 1]);

			// correlate c1 and c2 (no need to normalize)
			c1.timesEquals(c2.conjugate());

			sourceFFT[i] = (float) c1.getReal();
			sourceFFT[i + 1] = (float) c1.getImag();
		}

		// IFFT

		return inverseFFT(sourceFFT, fft);
	}

	private static int argMax(float[] array, int n) {
		int argMax = 0;
		float max = array[0];
		for (int i = 1; i < n; i++) {
			float val = array[i];
			if (val > max) {
				max = val;
				argMax = i;
			}
		}
		return argMax;
	}

	private static float[] forwardFFT(float[] realData, FloatFFT_2D fft) {
		float[] out = new float[realData.length * 2];

		// format the input as a complex array
		// => real and imaginary values are interleaved
		for (int i = 0, j = 0; i < realData.length; i++, j += 2)
			out[j] = realData[i];

		fft.complexForward(out);
		return out;
	}

	private static float[] inverseFFT(float[] cplxData, FloatFFT_2D fft) {
		float[] out = new float[cplxData.length / 2];

		fft.complexInverse(cplxData, true);

		// format the input as a real array
		// => skip imaginary values
		for (int i = 0, j = 0; i < cplxData.length; i += 2, j++)
			out[j] = cplxData[i];

		return out;
	}

	public static boolean correctTranslation2D(IcyBufferedImage img, IcyBufferedImage ref, int referenceChannel) {
		boolean change = false;
		Vector2d translation = new Vector2d();
		int n = 0;
		int minC = referenceChannel == -1 ? 0 : referenceChannel;
		int maxC = referenceChannel == -1 ? img.getSizeC() - 1 : referenceChannel;

		for (int c = minC; c <= maxC; c++) {
			translation.add(findTranslation2D(img, c, ref, c));
			n++;
		}

		translation.scale(1.0 / n);
		if (translation.lengthSquared() != 0) {
			change = true;
			img = applyTranslation2D(img, -1, translation, true);
		}
		return change;
	}

	public static IcyBufferedImage applyTranslation2D(IcyBufferedImage image, int channel, Vector2d vector,
			boolean preserveImageSize) {
		int dx = (int) Math.round(vector.x);
		int dy = (int) Math.round(vector.y);
		System.out.println("GasparRigidRegistration:applyTranslation2D() dx=" + dx + " dy=" + dy);
		if (dx == 0 && dy == 0)
			return image;

		Rectangle newSize = image.getBounds();
		newSize.width += Math.abs(dx);
		newSize.height += Math.abs(dy);

		Point dstPoint_shiftedChannel = new Point(Math.max(0, dx), Math.max(0, dy));
		Point dstPoint_otherChannels = new Point(Math.max(0, -dx), Math.max(0, -dy));

		IcyBufferedImage newImage = new IcyBufferedImage(newSize.width, newSize.height, image.getSizeC(),
				image.getDataType_());
		for (int c = 0; c < image.getSizeC(); c++) {
			Point dstPoint = (channel == -1 || c == channel) ? dstPoint_shiftedChannel : dstPoint_otherChannels;
			newImage.copyData(image, null, dstPoint, c, c);
		}

		if (preserveImageSize) {
			newSize = image.getBounds();
			newSize.x = Math.max(0, -dx);
			newSize.y = Math.max(0, -dy);

			return IcyBufferedImageUtil.getSubImage(newImage, newSize);
		}
		return newImage;
	}

	public static boolean correctRotation2D(IcyBufferedImage img, IcyBufferedImage ref, int referenceChannel) {
		boolean change = false;
		double angle = 0.0;
		int n = 0;

		int minC = referenceChannel == -1 ? 0 : referenceChannel;
		int maxC = referenceChannel == -1 ? ref.getSizeC() : referenceChannel;

		for (int c = minC; c <= maxC; c++) {
			angle += findRotation2D(img, c, ref, c);
			n++;
		}

		angle /= n;
		if (angle != 0.0) {
			change = true;
			img = applyRotation2D(img, -1, angle, true);
		}
		return change;
	}

	public static double findRotation2D(IcyBufferedImage source, int sourceC, IcyBufferedImage target, int targetC) {
		return findRotation2D(source, sourceC, target, targetC, null);
	}

	public static double findRotation2D(IcyBufferedImage source, int sourceC, IcyBufferedImage target, int targetC,
			Vector2d previousTranslation) {
		if (!source.getBounds().equals(target.getBounds())) {
			// Both sizes are different. What to do?

			if (previousTranslation != null) {
				// the source has most probably been translated previously, let's grow the
				// target
				// accordingly
				// (just need to know where the original data has to go)
				int xAlign = previousTranslation.x > 0 ? SwingConstants.LEFT : SwingConstants.RIGHT;
				int yAlign = previousTranslation.y > 0 ? SwingConstants.TOP : SwingConstants.BOTTOM;
				target = IcyBufferedImageUtil.scale(target, source.getSizeX(), source.getSizeY(), false, xAlign,
						yAlign);
			}

			else
				throw new UnsupportedOperationException("Cannot register images of different size (yet)");
		}

		// Convert to Log-Polar

		IcyBufferedImage sourceLogPol = toLogPolar(source.getImage(sourceC));
		IcyBufferedImage targetLogPol = toLogPolar(target.getImage(targetC));

		int width = sourceLogPol.getWidth(), height = sourceLogPol.getHeight();

		float[] _sourceLogPol = sourceLogPol.getDataXYAsFloat(0);
		float[] _targetLogPol = targetLogPol.getDataXYAsFloat(0);

		// Compute spectral correlation

		float[] correlationMap = spectralCorrelation(_sourceLogPol, _targetLogPol, width, height);

		// Find maximum correlation (=> rotation)

		int argMax = argMax(correlationMap, correlationMap.length / 2);

		// rotation is given along the X axis
		int rotX = argMax % width;

		if (rotX > width / 2)
			rotX -= width;

		return -rotX * 2 * Math.PI / width;
	}

	private static IcyBufferedImage toLogPolar(IcyBufferedImage image) {
		return toLogPolar(image, image.getWidth() / 2, image.getHeight() / 2, 1080, 360);
	}

	private static IcyBufferedImage toLogPolar(IcyBufferedImage image, int centerX, int centerY, int sizeTheta,
			int sizeRho) {
		int sizeC = image.getSizeC();

		// create the log-polar image (X = theta, Y = rho)

		// theta: number of sectors
		double theta = 0.0, dtheta = 2 * Math.PI / sizeTheta;
		// pre-compute all sine/cosines
		float[] cosTheta = new float[sizeTheta];
		float[] sinTheta = new float[sizeTheta];
		for (int thetaIndex = 0; thetaIndex < sizeTheta; thetaIndex++, theta += dtheta) {
			cosTheta[thetaIndex] = (float) Math.cos(theta);
			sinTheta[thetaIndex] = (float) Math.sin(theta);
		}

		// rho: number of rings
		float drho = (float) (Math.sqrt(centerX * centerX + centerY * centerY) / sizeRho);

		IcyBufferedImage logPol = new IcyBufferedImage(sizeTheta, sizeRho, sizeC, DataType.FLOAT);

		for (int c = 0; c < sizeC; c++) {
			float[] out = logPol.getDataXYAsFloat(c);

			// first ring (rho=0): center value
			Array1DUtil.fill(out, 0, sizeTheta, getPixelValue(image, centerX, centerY, c));

			// Other rings
			float rho = drho;
			int outOffset = sizeTheta;
			for (int rhoIndex = 1; rhoIndex < sizeRho; rhoIndex++, rho += drho)
				for (int thetaIndex = 0; thetaIndex < sizeTheta; thetaIndex++, outOffset++) {
					double x = centerX + rho * cosTheta[thetaIndex];
					double y = centerY + rho * sinTheta[thetaIndex];
					out[outOffset] = getPixelValue(image, x, y, c);
				}
		}

		logPol.updateChannelsBounds();
		return logPol;
	}

	private static float getPixelValue(IcyBufferedImage img, double x, double y, int c) {
		int width = img.getWidth();
		int height = img.getHeight();
		Object data = img.getDataXY(c);
		DataType type = img.getDataType_();

		// "center" the coordinates to the center of the pixel
		x -= 0.5;
		y -= 0.5;

		int i = (int) Math.floor(x);
		int j = (int) Math.floor(y);

		if (i <= 0 || i >= width - 1 || j <= 0 || j >= height - 1)
			return 0f;

		float value = 0;

		final int offset = i + j * width;
		final int offset_plus_1 = offset + 1; // saves 1 addition

		x -= i;
		y -= j;

		final double mx = 1 - x;
		final double my = 1 - y;

		value += mx * my * Array1DUtil.getValueAsFloat(data, offset, type);
		value += x * my * Array1DUtil.getValueAsFloat(data, offset_plus_1, type);
		value += mx * y * Array1DUtil.getValueAsFloat(data, offset + width, type);
		value += x * y * Array1DUtil.getValueAsFloat(data, offset_plus_1 + width, type);

		return value;
	}

	public static IcyBufferedImage applyRotation2D(IcyBufferedImage img, int channel, double angle,
			boolean preserveImageSize) {
		if (angle == 0.0)
			return img;

		// start with the rotation to calculate the largest bounds
		IcyBufferedImage rotImg = IcyBufferedImageUtil.rotate(img.getImage(channel), angle);

		// calculate the difference in bounds
		Rectangle oldSize = img.getBounds();
		Rectangle newSize = rotImg.getBounds();
		int dw = (newSize.width - oldSize.width) / 2;
		int dh = (newSize.height - oldSize.height) / 2;

		if (channel == -1 || img.getSizeC() == 1) {
			if (preserveImageSize) {
				oldSize.translate(dw, dh);
				return IcyBufferedImageUtil.getSubImage(rotImg, oldSize);
			}
			return rotImg;
		}

		IcyBufferedImage[] newImages = new IcyBufferedImage[img.getSizeC()];

		if (preserveImageSize) {
			for (int c = 0; c < newImages.length; c++)
				if (c == channel) {
					// crop the rotated channel
					oldSize.translate(dw, dh);
					newImages[c] = IcyBufferedImageUtil.getSubImage(rotImg, oldSize);
				} else
					newImages[c] = img.getImage(c);
		} else {
			for (int c = 0; c < newImages.length; c++)
				if (c != channel) {
					// enlarge and center the non-rotated channels
					newImages[c] = new IcyBufferedImage(newSize.width, newSize.height, 1, img.getDataType_());
					newImages[c].copyData(img.getImage(c), null, new Point(dw, dh));
				} else
					newImages[channel] = rotImg;
		}

		return IcyBufferedImage.createFrom(Arrays.asList(newImages));
	}

}
