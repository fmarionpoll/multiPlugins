package plugins.fmp.multitools.tools.registration;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.Arrays;

import javax.swing.SwingConstants;
import javax.vecmath.Vector2d;

import edu.emory.mathcs.jtransforms.fft.FloatFFT_2D;
import flanagan.complex.Complex;

import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;
import icy.type.DataType;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multitools.tools.Logger;

/**
 * Rigid registration utility for image alignment using FFT-based correlation.
 * Uses sub-pixel translation (parabolic fit) and optional sub-pixel apply
 * via AffineTransform. Rotation is estimated in log-polar space.
 */
public class GaspardRigidRegistration {

	private static final int DEFAULT_SIZE_THETA = 1080;
	private static final int DEFAULT_SIZE_RHO = 360;
	private static final double MIN_TRANSLATION_THRESHOLD = 0.001;
	private static final double MIN_ROTATION_THRESHOLD = 0.001;

	public static Vector2d findTranslation2D(IcyBufferedImage source, int sourceC, IcyBufferedImage target,
			int targetC) {
		if (source == null)
			throw new IllegalArgumentException("Source image cannot be null");
		if (target == null)
			throw new IllegalArgumentException("Target image cannot be null");
		if (sourceC < 0 || sourceC >= source.getSizeC())
			throw new IllegalArgumentException("Invalid source channel: " + sourceC);
		if (targetC < 0 || targetC >= target.getSizeC())
			throw new IllegalArgumentException("Invalid target channel: " + targetC);
		if (!source.getBounds().equals(target.getBounds()))
			throw new UnsupportedOperationException("Cannot register images of different size (yet)");

		int width = source.getWidth();
		int height = source.getHeight();
		Logger.debug("Finding translation between images: " + width + "x" + height);

		float[] _source = Array1DUtil.arrayToFloatArray(source.getDataXY(sourceC), source.isSignedDataType());
		float[] _target = Array1DUtil.arrayToFloatArray(target.getDataXY(targetC), target.isSignedDataType());

		float[] correlationMap = spectralCorrelation(_source, _target, width, height);

		// Find maximum correlation
		int argMax = argMax(correlationMap, correlationMap.length);

		int maxX = argMax % width;
		int maxY = argMax / width;

		// Sub-pixel refinement using parabolic fit
		double dx = 0;
		double dy = 0;

		// Fit along X
		float valX0 = correlationMap[maxY * width + maxX];
		float valX_minus = correlationMap[maxY * width + (maxX - 1 + width) % width];
		float valX_plus = correlationMap[maxY * width + (maxX + 1) % width];

		if (Math.abs(valX_minus - 2 * valX0 + valX_plus) > 1e-9) {
			dx = 0.5 * (valX_minus - valX_plus) / (valX_minus - 2 * valX0 + valX_plus);
		}

		// Fit along Y
		float valY_minus = correlationMap[((maxY - 1 + height) % height) * width + maxX];
		float valY_plus = correlationMap[((maxY + 1) % height) * width + maxX];

		if (Math.abs(valY_minus - 2 * valX0 + valY_plus) > 1e-9) {
			dy = 0.5 * (valY_minus - valY_plus) / (valY_minus - 2 * valX0 + valY_plus);
		}

		double finalX = maxX + dx;
		double finalY = maxY + dy;

		if (finalX > width / 2)
			finalX -= width;
		if (finalY > height / 2)
			finalY -= height;

		Vector2d translation = new Vector2d(-finalX, -finalY);
		Logger.debug("Found translation: (" + translation.x + ", " + translation.y + ")");
		return translation;
	}

	public static Vector2d getTranslation2D(IcyBufferedImage img, IcyBufferedImage ref, int referenceChannel) {
		if (img == null)
			throw new IllegalArgumentException("Image cannot be null");
		if (ref == null)
			throw new IllegalArgumentException("Reference image cannot be null");
		Vector2d translation = new Vector2d();
		int n = 0;
		int minC = referenceChannel == -1 ? 0 : referenceChannel;
		int maxC = referenceChannel == -1 ? img.getSizeC() - 1 : referenceChannel;
		for (int c = minC; c <= maxC; c++) {
			translation.add(findTranslation2D(img, c, ref, c));
			n++;
		}
		translation.scale(1.0 / n);
		return translation;
	}

	private static float[] spectralCorrelation(float[] a1, float[] a2, int width, int height) {
		if (a1 == null || a2 == null)
			throw new IllegalArgumentException("Input arrays cannot be null");
		if (width <= 0 || height <= 0)
			throw new IllegalArgumentException("Invalid dimensions: " + width + "x" + height);
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
		if (array == null || n <= 0 || n > array.length)
			throw new IllegalArgumentException("Invalid array or length");
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
		if (realData == null)
			throw new IllegalArgumentException("Real data cannot be null");
		float[] out = new float[realData.length * 2];

		// format the input as a complex array
		// => real and imaginary values are interleaved
		for (int i = 0, j = 0; i < realData.length; i++, j += 2)
			out[j] = realData[i];

		fft.complexForward(out);
		return out;
	}

	private static float[] inverseFFT(float[] cplxData, FloatFFT_2D fft) {
		if (cplxData == null)
			throw new IllegalArgumentException("Complex data cannot be null");
		float[] out = new float[cplxData.length / 2];

		fft.complexInverse(cplxData, true);

		// format the input as a real array
		// => skip imaginary values
		for (int i = 0, j = 0; i < cplxData.length; i += 2, j++)
			out[j] = cplxData[i];

		return out;
	}

	public static boolean correctTranslation2D(IcyBufferedImage img, IcyBufferedImage ref, int referenceChannel) {
		Vector2d translation = getTranslation2D(img, ref, referenceChannel);
		boolean change = false;
		if (translation.lengthSquared() > MIN_TRANSLATION_THRESHOLD) {
			change = true;
			img = applyTranslation2D(img, -1, translation, true);
			Logger.info("Applied translation correction: (" + translation.x + ", " + translation.y + ")");
		}
		return change;
	}

	public static IcyBufferedImage applyTranslation2D(IcyBufferedImage image, int channel, Vector2d vector,
			boolean preserveImageSize) {
		if (image == null)
			throw new IllegalArgumentException("Image cannot be null");
		if (vector == null)
			throw new IllegalArgumentException("Translation vector cannot be null");
		// Check for integer shift (with small tolerance)
		if (Math.abs(vector.x - Math.round(vector.x)) < 0.01 && Math.abs(vector.y - Math.round(vector.y)) < 0.01) {
			int dx = (int) Math.round(vector.x);
			int dy = (int) Math.round(vector.y);
			// System.out.println("GasparRigidRegistration:applyTranslation2D() dx=" + dx + " dy=" + dy);
			if (dx == 0 && dy == 0)
				return image;
			Logger.debug("Applying translation: dx=" + dx + " dy=" + dy);
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
		
		// Sub-pixel shift using AffineTransform
		AffineTransform transform = AffineTransform.getTranslateInstance(vector.x, vector.y);
		IcyBufferedImage newImg = new IcyBufferedImage(image.getWidth(), image.getHeight(),
				image.getSizeC(), image.getDataType_());

		for (int c = 0; c < image.getSizeC(); c++) {
			// Only transform the requested channel, or all if channel == -1
			if (channel != -1 && c != channel) {
				// Copy untransformed
				newImg.copyData(image, null, new Point(0, 0), c, c);
				continue;
			}

			BufferedImage awtSrc = image.getImage(c);
			int w = awtSrc.getWidth();
			int h = awtSrc.getHeight();
			int type = awtSrc.getType();
			if (type == 0 || type == BufferedImage.TYPE_CUSTOM) {
				type = BufferedImage.TYPE_INT_RGB;
			}
			BufferedImage awtDst = new BufferedImage(w, h, type);
			Graphics2D g2 = awtDst.createGraphics();
			
			// Enable bilinear interpolation
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

			g2.setTransform(transform);
			g2.drawImage(awtSrc, 0, 0, null);
			g2.dispose();

			IcyBufferedImage tempWrapper = IcyBufferedImage.createFrom(awtDst);
			newImg.setDataXY(c, tempWrapper.getDataXY(0));
		}
		
		return newImg;
	}

	public static double getRotation2D(IcyBufferedImage img, IcyBufferedImage ref, int referenceChannel) {
		if (img == null)
			throw new IllegalArgumentException("Image cannot be null");
		if (ref == null)
			throw new IllegalArgumentException("Reference image cannot be null");
		double angle = 0.0;
		int n = 0;
		int minC = referenceChannel == -1 ? 0 : referenceChannel;
		int maxC = referenceChannel == -1 ? ref.getSizeC() - 1 : referenceChannel;
		for (int c = minC; c <= maxC; c++) {
			angle += findRotation2D(img, c, ref, c);
			n++;
		}
		return n > 0 ? angle / n : 0.0;
	}

	public static boolean correctRotation2D(IcyBufferedImage img, IcyBufferedImage ref, int referenceChannel) {
		double angle = getRotation2D(img, ref, referenceChannel);
		boolean change = false;
		if (Math.abs(angle) > MIN_ROTATION_THRESHOLD) {
			change = true;
			img = applyRotation2D(img, -1, angle, true);
			Logger.info("Applied rotation correction: " + Math.toDegrees(angle) + " degrees");
		}
		return change;
	}

	public static double findRotation2D(IcyBufferedImage source, int sourceC, IcyBufferedImage target, int targetC) {
		return findRotation2D(source, sourceC, target, targetC, null);
	}

	public static double findRotation2D(IcyBufferedImage source, int sourceC, IcyBufferedImage target, int targetC,
			Vector2d previousTranslation) {
		if (source == null)
			throw new IllegalArgumentException("Source image cannot be null");
		if (target == null)
			throw new IllegalArgumentException("Target image cannot be null");
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
		double rotation = -rotX * 2 * Math.PI / width;
		Logger.debug("Found rotation: " + Math.toDegrees(rotation) + " degrees");
		return rotation;
	}

	private static IcyBufferedImage toLogPolar(IcyBufferedImage image) {
		return toLogPolar(image, image.getWidth() / 2, image.getHeight() / 2, DEFAULT_SIZE_THETA, DEFAULT_SIZE_RHO);
	}

	private static IcyBufferedImage toLogPolar(IcyBufferedImage image, int centerX, int centerY, int sizeTheta,
			int sizeRho) {
		if (image == null)
			throw new IllegalArgumentException("Image cannot be null");
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
		if (img == null)
			return 0f;
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
		if (img == null)
			throw new IllegalArgumentException("Image cannot be null");
		if (Math.abs(angle) < MIN_ROTATION_THRESHOLD) {
			Logger.debug("No rotation needed (angle too small)");
			return img;
		}

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
