package plugins.fmp.multitools.tools;

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

/**
 * Rigid registration utility for image alignment. This class provides methods
 * for finding and applying 2D translations and rotations between images using
 * spectral correlation techniques.
 * 
 * <p>
 * GaspardRigidRegistration is used in the MultiSPOTS96 plugin for aligning
 * images from different time points or experimental conditions. It uses
 * FFT-based correlation methods for robust registration.
 * </p>
 * 
 * <p>
 * Usage example:
 * 
 * <pre>
 * Vector2d translation = GaspardRigidRegistration.findTranslation2D(source, 0, target, 0);
 * IcyBufferedImage aligned = GaspardRigidRegistration.applyTranslation2D(source, -1, translation, true);
 * </pre>
 * 
 * @author MultiSPOTS96
 * @see icy.image.IcyBufferedImage
 * @see javax.vecmath.Vector2d
 */
public class GaspardRigidRegistration {

	/** Default log-polar size for theta (angle) */
	private static final int DEFAULT_SIZE_THETA = 1080;

	/** Default log-polar size for rho (radius) */
	private static final int DEFAULT_SIZE_RHO = 360;

	/** Minimum threshold for considering a translation significant */
	private static final double MIN_TRANSLATION_THRESHOLD = 0.001;

	/** Minimum threshold for considering a rotation significant */
	private static final double MIN_ROTATION_THRESHOLD = 0.001;

	/**
	 * Finds the 2D translation between two images using spectral correlation.
	 * 
	 * @param sourceImage the source image
	 * @param sourceC     the source channel
	 * @param targetImage the target image
	 * @param targetC     the target channel
	 * @return the translation vector
	 * @throws IllegalArgumentException      if images are null or have different
	 *                                       sizes
	 * @throws UnsupportedOperationException if images have different dimensions
	 */
	public static Vector2d findTranslation2D(IcyBufferedImage sourceImage, int sourceC, IcyBufferedImage targetImage,
			int targetC) {
		if (sourceImage == null) {
			throw new IllegalArgumentException("Source image cannot be null");
		}
		if (targetImage == null) {
			throw new IllegalArgumentException("Target image cannot be null");
		}
		if (sourceC < 0 || sourceC >= sourceImage.getSizeC()) {
			throw new IllegalArgumentException("Invalid source channel: " + sourceC);
		}
		if (targetC < 0 || targetC >= targetImage.getSizeC()) {
			throw new IllegalArgumentException("Invalid target channel: " + targetC);
		}

		if (!sourceImage.getBounds().equals(targetImage.getBounds())) {
			throw new UnsupportedOperationException("Cannot register images of different size (yet)");
		}

		int width = sourceImage.getWidth();
		int height = sourceImage.getHeight();

		Logger.debug("Finding translation between images: " + width + "x" + height);

		float[] _source = Array1DUtil.arrayToFloatArray(sourceImage.getDataXY(sourceC), sourceImage.isSignedDataType());
		float[] _target = Array1DUtil.arrayToFloatArray(targetImage.getDataXY(targetC), targetImage.isSignedDataType());

		float[] correlationMap = spectralCorrelation(_source, _target, width, height);

		// Find maximum correlation
		int argMax = argMax(correlationMap, correlationMap.length);

		int transX = argMax % width;
		int transY = argMax / width;

		if (transX > width / 2) {
			transX -= width;
		}
		if (transY > height / 2) {
			transY -= height;
		}

		Vector2d translation = new Vector2d(-transX, -transY);
		Logger.debug("Found translation: (" + translation.x + ", " + translation.y + ")");

		return translation;
	}

	/**
	 * Computes spectral correlation between two arrays.
	 * 
	 * @param a1     the first array
	 * @param a2     the second array
	 * @param width  the width of the data
	 * @param height the height of the data
	 * @return the correlation map
	 */
	private static float[] spectralCorrelation(float[] a1, float[] a2, int width, int height) {
		if (a1 == null || a2 == null) {
			throw new IllegalArgumentException("Input arrays cannot be null");
		}
		if (width <= 0 || height <= 0) {
			throw new IllegalArgumentException("Invalid dimensions: " + width + "x" + height);
		}

		// JTransforms's FFT takes dimensions as (rows, columns)
		FloatFFT_2D fft = new FloatFFT_2D(height, width);
		return spectralCorrelation(a1, a2, width, height, fft);
	}

	/**
	 * Computes spectral correlation using a pre-configured FFT object.
	 * 
	 * @param a1     the first array
	 * @param a2     the second array
	 * @param width  the width of the data
	 * @param height the height of the data
	 * @param fft    the FFT object to use
	 * @return the correlation map
	 */
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

	/**
	 * Finds the index of the maximum value in an array.
	 * 
	 * @param array the array to search
	 * @param n     the number of elements to consider
	 * @return the index of the maximum value
	 */
	private static int argMax(float[] array, int n) {
		if (array == null || n <= 0 || n > array.length) {
			throw new IllegalArgumentException("Invalid array or length");
		}

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

	/**
	 * Performs forward FFT on real data.
	 * 
	 * @param realData the real data array
	 * @param fft      the FFT object to use
	 * @return the complex FFT result
	 */
	private static float[] forwardFFT(float[] realData, FloatFFT_2D fft) {
		if (realData == null) {
			throw new IllegalArgumentException("Real data cannot be null");
		}

		float[] out = new float[realData.length * 2];

		// format the input as a complex array
		// => real and imaginary values are interleaved
		for (int i = 0, j = 0; i < realData.length; i++, j += 2) {
			out[j] = realData[i];
		}

		fft.complexForward(out);
		return out;
	}

	/**
	 * Performs inverse FFT on complex data.
	 * 
	 * @param cplxData the complex data array
	 * @param fft      the FFT object to use
	 * @return the real inverse FFT result
	 */
	private static float[] inverseFFT(float[] cplxData, FloatFFT_2D fft) {
		if (cplxData == null) {
			throw new IllegalArgumentException("Complex data cannot be null");
		}

		float[] out = new float[cplxData.length / 2];

		fft.complexInverse(cplxData, true);

		// format the input as a real array
		// => skip imaginary values
		for (int i = 0, j = 0; i < cplxData.length; i += 2, j++) {
			out[j] = cplxData[i];
		}

		return out;
	}

	/**
	 * Corrects translation for an image based on a reference image.
	 * 
	 * @param img              the image to correct
	 * @param ref              the reference image
	 * @param referenceChannel the reference channel (-1 for all channels)
	 * @return true if a correction was applied, false otherwise
	 * @throws IllegalArgumentException if img or ref is null
	 */
	public static boolean correctTranslation2D(IcyBufferedImage img, IcyBufferedImage ref, int referenceChannel) {
		Vector2d translation = GaspardRigidRegistration.getTranslation2D(img, ref, referenceChannel);
		boolean change = false;
		if (translation.lengthSquared() > MIN_TRANSLATION_THRESHOLD) {
			change = true;
			img = GaspardRigidRegistration.applyTranslation2D(img, -1, translation, true);
			Logger.info("Applied translation correction: (" + translation.x + ", " + translation.y + ")");
		}
		return change;
	}

	public static Vector2d getTranslation2D(IcyBufferedImage img, IcyBufferedImage ref, int referenceChannel) {
		if (img == null) {
			throw new IllegalArgumentException("Image cannot be null");
		}
		if (ref == null) {
			throw new IllegalArgumentException("Reference image cannot be null");
		}

		Vector2d translation = new Vector2d();
		int n = 0;
		int minC = referenceChannel == -1 ? 0 : referenceChannel;
		int maxC = referenceChannel == -1 ? img.getSizeC() - 1 : referenceChannel;

		for (int c = minC; c <= maxC; c++) {
			Vector2d trans = findTranslation2D(img, c, ref, c);
			translation.add(trans);
			n++;
		}

		translation.scale(1.0 / n);
		return translation;
	}

	/**
	 * Applies a 2D translation to an image.
	 * 
	 * @param image             the image to translate
	 * @param channel           the channel to translate (-1 for all channels)
	 * @param vector            the translation vector
	 * @param preserveImageSize whether to preserve the original image size
	 * @return the translated image
	 * @throws IllegalArgumentException if image or vector is null
	 */
	public static IcyBufferedImage applyTranslation2D(IcyBufferedImage image, int channel, Vector2d vector,
			boolean preserveImageSize) {
		if (image == null) {
			throw new IllegalArgumentException("Image cannot be null");
		}
		if (vector == null) {
			throw new IllegalArgumentException("Translation vector cannot be null");
		}

		int dx = (int) Math.round(vector.x);
		int dy = (int) Math.round(vector.y);

		Logger.debug("Applying translation: dx=" + dx + " dy=" + dy);

		if (dx == 0 && dy == 0) {
			Logger.debug("No translation needed");
			return image;
		}

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

	/**
	 * Corrects rotation for an image based on a reference image.
	 * 
	 * @param img              the image to correct
	 * @param ref              the reference image
	 * @param referenceChannel the reference channel (-1 for all channels)
	 * @return true if a correction was applied, false otherwise
	 * @throws IllegalArgumentException if img or ref is null
	 */

	public static boolean correctRotation2D(IcyBufferedImage img, IcyBufferedImage ref, int referenceChannel) {
		boolean rotate = false;
		double angle = GaspardRigidRegistration.getRotation2D(img, ref, referenceChannel);
		if (Math.abs(angle) > MIN_ROTATION_THRESHOLD) {
			rotate = true;
			img = GaspardRigidRegistration.applyRotation2D(img, -1, angle, true);
			Logger.info("Applied rotation correction: " + Math.toDegrees(angle) + " degrees");
		}
		return rotate;
	}

	public static double getRotation2D(IcyBufferedImage img, IcyBufferedImage ref, int referenceChannel) {
		if (img == null) {
			throw new IllegalArgumentException("Image cannot be null");
		}
		if (ref == null) {
			throw new IllegalArgumentException("Reference image cannot be null");
		}

		double angle = 0.0;
		int n = 0;

		int minC = referenceChannel == -1 ? 0 : referenceChannel;
		int maxC = referenceChannel == -1 ? ref.getSizeC() : referenceChannel;

		for (int c = minC; c <= maxC; c++) {
			angle += findRotation2D(img, c, ref, c);
			n++;
		}
		angle /= n;

		return angle;
	}

	/**
	 * Finds the 2D rotation between two images.
	 * 
	 * @param source  the source image
	 * @param sourceC the source channel
	 * @param target  the target image
	 * @param targetC the target channel
	 * @return the rotation angle in radians
	 * @throws IllegalArgumentException if images are null
	 */
	public static double findRotation2D(IcyBufferedImage source, int sourceC, IcyBufferedImage target, int targetC) {
		return findRotation2D(source, sourceC, target, targetC, null);
	}

	/**
	 * Finds the 2D rotation between two images with optional previous translation.
	 * 
	 * @param sourceImage         the source image
	 * @param sourceC             the source channel
	 * @param targetImage         the target image
	 * @param targetC             the target channel
	 * @param previousTranslation the previous translation applied (can be null)
	 * @return the rotation angle in radians
	 * @throws IllegalArgumentException      if images are null
	 * @throws UnsupportedOperationException if images have different sizes and no
	 *                                       previous translation
	 */
	public static double findRotation2D(IcyBufferedImage sourceImage, int sourceC, IcyBufferedImage targetImage,
			int targetC, Vector2d previousTranslation) {
		if (sourceImage == null) {
			throw new IllegalArgumentException("Source image cannot be null");
		}
		if (targetImage == null) {
			throw new IllegalArgumentException("Target image cannot be null");
		}

		if (!sourceImage.getBounds().equals(targetImage.getBounds())) {
			// Both sizes are different. What to do?
			if (previousTranslation != null) {
				// the source has most probably been translated previously, let's grow the
				// target accordingly (just need to know where the original data has to go)
				int xAlign = previousTranslation.x > 0 ? SwingConstants.LEFT : SwingConstants.RIGHT;
				int yAlign = previousTranslation.y > 0 ? SwingConstants.TOP : SwingConstants.BOTTOM;
				targetImage = IcyBufferedImageUtil.scale(targetImage, sourceImage.getSizeX(), sourceImage.getSizeY(),
						false, xAlign, yAlign);
			} else {
				throw new UnsupportedOperationException("Cannot register images of different size (yet)");
			}
		}

		// Convert to Log-Polar
		IcyBufferedImage sourceLogPol = toLogPolar(sourceImage.getImage(sourceC));
		IcyBufferedImage targetLogPol = toLogPolar(targetImage.getImage(targetC));

		int width = sourceLogPol.getWidth(), height = sourceLogPol.getHeight();

		float[] _sourceLogPol = sourceLogPol.getDataXYAsFloat(0);
		float[] _targetLogPol = targetLogPol.getDataXYAsFloat(0);

		// Compute spectral correlation
		float[] correlationMap = spectralCorrelation(_sourceLogPol, _targetLogPol, width, height);

		// Find maximum correlation (=> rotation)
		int argMax = argMax(correlationMap, correlationMap.length / 2);

		// rotation is given along the X axis
		int rotX = argMax % width;

		if (rotX > width / 2) {
			rotX -= width;
		}

		double rotation = -rotX * 2 * Math.PI / width;
		Logger.debug("Found rotation: " + Math.toDegrees(rotation) + " degrees");

		return rotation;
	}

	/**
	 * Converts an image to log-polar coordinates.
	 * 
	 * @param image the image to convert
	 * @return the log-polar image
	 */
	private static IcyBufferedImage toLogPolar(IcyBufferedImage image) {
		if (image == null) {
			throw new IllegalArgumentException("Image cannot be null");
		}

		return toLogPolar(image, image.getWidth() / 2, image.getHeight() / 2, DEFAULT_SIZE_THETA, DEFAULT_SIZE_RHO);
	}

	/**
	 * Converts an image to log-polar coordinates with custom parameters.
	 * 
	 * @param image     the image to convert
	 * @param centerX   the center X coordinate
	 * @param centerY   the center Y coordinate
	 * @param sizeTheta the number of angle sectors
	 * @param sizeRho   the number of radius rings
	 * @return the log-polar image
	 */
	private static IcyBufferedImage toLogPolar(IcyBufferedImage image, int centerX, int centerY, int sizeTheta,
			int sizeRho) {
		if (image == null) {
			throw new IllegalArgumentException("Image cannot be null");
		}

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
			for (int rhoIndex = 1; rhoIndex < sizeRho; rhoIndex++, rho += drho) {
				for (int thetaIndex = 0; thetaIndex < sizeTheta; thetaIndex++, outOffset++) {
					double x = centerX + rho * cosTheta[thetaIndex];
					double y = centerY + rho * sinTheta[thetaIndex];
					out[outOffset] = getPixelValue(image, x, y, c);
				}
			}
		}

		logPol.updateChannelsBounds();
		return logPol;
	}

	/**
	 * Gets the pixel value at the specified coordinates using bilinear
	 * interpolation.
	 * 
	 * @param img the image
	 * @param x   the X coordinate
	 * @param y   the Y coordinate
	 * @param c   the channel
	 * @return the interpolated pixel value
	 */
	private static float getPixelValue(IcyBufferedImage img, double x, double y, int c) {
		if (img == null) {
			return 0f;
		}

		int width = img.getWidth();
		int height = img.getHeight();
		Object data = img.getDataXY(c);
		DataType type = img.getDataType_();

		// "center" the coordinates to the center of the pixel
		x -= 0.5;
		y -= 0.5;

		int i = (int) Math.floor(x);
		int j = (int) Math.floor(y);

		if (i <= 0 || i >= width - 1 || j <= 0 || j >= height - 1) {
			return 0f;
		}

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

	/**
	 * Applies a 2D rotation to an image.
	 * 
	 * @param img               the image to rotate
	 * @param channel           the channel to rotate (-1 for all channels)
	 * @param angle             the rotation angle in radians
	 * @param preserveImageSize whether to preserve the original image size
	 * @return the rotated image
	 * @throws IllegalArgumentException if img is null
	 */
	public static IcyBufferedImage applyRotation2D(IcyBufferedImage img, int channel, double angle,
			boolean preserveImageSize) {
		if (img == null) {
			throw new IllegalArgumentException("Image cannot be null");
		}

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
			for (int c = 0; c < newImages.length; c++) {
				if (c == channel) {
					// crop the rotated channel
					oldSize.translate(dw, dh);
					newImages[c] = IcyBufferedImageUtil.getSubImage(rotImg, oldSize);
				} else {
					newImages[c] = img.getImage(c);
				}
			}
		} else {
			for (int c = 0; c < newImages.length; c++) {
				if (c != channel) {
					// enlarge and center the non-rotated channels
					newImages[c] = new IcyBufferedImage(newSize.width, newSize.height, 1, img.getDataType_());
					newImages[c].copyData(img.getImage(c), null, new Point(dw, dh));
				} else {
					newImages[channel] = rotImg;
				}
			}
		}

		return IcyBufferedImage.createFrom(Arrays.asList(newImages));
	}
}
