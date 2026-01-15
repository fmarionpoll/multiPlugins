package plugins.fmp.multitools.tools.imageTransform.transforms;

import icy.image.IcyBufferedImage;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformFunctionAbstract;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformOptions;

public class RGBtoHSV extends ImageTransformFunctionAbstract implements ImageTransformInterface {
	int channelOut = 0;
	double h, s, v;

	public RGBtoHSV(int channelOut) {
		this.channelOut = channelOut;
	}

	@Override
	public IcyBufferedImage getTransformedImage(IcyBufferedImage sourceImage, ImageTransformOptions options) {
		IcyBufferedImage img = functionRGBtoHSB(sourceImage);
		if (channelOut >= 0)
			img = functionRGB_keepOneChan(img, channelOut);
		return img;
	}

	protected IcyBufferedImage functionRGBtoHSB(IcyBufferedImage sourceImage) {
		IcyBufferedImage img2 = new IcyBufferedImage(sourceImage.getWidth(), sourceImage.getHeight(), 3,
				sourceImage.getDataType_());

		double[] tabValuesR = Array1DUtil.arrayToDoubleArray(sourceImage.getDataXY(0), sourceImage.isSignedDataType());
		double[] tabValuesG = Array1DUtil.arrayToDoubleArray(sourceImage.getDataXY(1), sourceImage.isSignedDataType());
		double[] tabValuesB = Array1DUtil.arrayToDoubleArray(sourceImage.getDataXY(2), sourceImage.isSignedDataType());

		double[] outValuesH = Array1DUtil.arrayToDoubleArray(img2.getDataXY(0), img2.isSignedDataType());
		double[] outValuesS = Array1DUtil.arrayToDoubleArray(img2.getDataXY(1), img2.isSignedDataType());
		double[] outValuesV = Array1DUtil.arrayToDoubleArray(img2.getDataXY(2), img2.isSignedDataType());

		// compute values
		for (int ky = 0; ky < tabValuesR.length; ky++) {
			NH_RGB_to_HSV(tabValuesR[ky], tabValuesG[ky], tabValuesB[ky]);
			outValuesH[ky] = h * 100;
			outValuesS[ky] = s * 100;
			outValuesV[ky] = v * 100;
		}

		Array1DUtil.doubleArrayToSafeArray(outValuesH, img2.getDataXY(0), false); // img2.isSignedDataType());
		img2.setDataXY(0, img2.getDataXY(0));

		Array1DUtil.doubleArrayToSafeArray(outValuesS, img2.getDataXY(1), false); // img2.isSignedDataType());
		img2.setDataXY(1, img2.getDataXY(1));

		Array1DUtil.doubleArrayToSafeArray(outValuesV, img2.getDataXY(2), false); // img2.isSignedDataType());
		img2.setDataXY(2, img2.getDataXY(2));
		return img2;
	}

	// From:
	/*
	 * Copyright 2010, 2011 Institut Pasteur.
	 * 
	 * This file is part of NHerve Main Toolbox, which is an ICY plugin.
	 * 
	 * NHerve Main Toolbox is free software: you can redistribute it and/or modify
	 * it under the terms of the GNU General Public License as published by the Free
	 * Software Foundation, either version 3 of the License, or (at your option) any
	 * later version.
	 * 
	 * NHerve Main Toolbox is distributed in the hope that it will be useful, but
	 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
	 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
	 * details.
	 * 
	 * You should have received a copy of the GNU General Public License along with
	 * NHerve Main Toolbox. If not, see <http://www.gnu.org/licenses/>.
	 */
	// package plugins.nherve.toolbox.image.toolboxes;

	/**
	 * The Class Conversion.
	 * 
	 * @author Nicolas HERVE - nicolas.herve@pasteur.fr
	 */

	final int UNDEFINED = -1;

	/* Fast conversion of Foley p. 592 */
	/* Given: R,G,B, each in [0,1] */
	/* Out: H in [0, 360), S and V in [0,1] except if s=0, then H=UNDEFINED, */
	/* which is some constant defined with a value outside the interval [0,360] */
	/**
	 * rgb _to_ hsv.
	 * 
	 * @param r the r
	 * @param g the g
	 * @param b the b
	 * @param h the h
	 * @param s the s
	 * @param v the v
	 */

	void NH_RGB_to_HSV(double r, double g, double b) {
		double max;
		double min;
		double delta;

		max = Math.max(b, Math.max(r, g));
		min = Math.min(b, Math.min(r, g));

		v = max; /* This is a value v */
		/*
		 * Next calculate saturation, S. Saturation is 0 if red, green and blue are all
		 * 0
		 */
		s = (max != 0.0) ? ((max - min) / max) : 0.0;
		if (s == 0.0)
			h = UNDEFINED;
		else { /* Chromatic case: Saturation is not 0 */
			delta = max - min; /* so determine hue */
			if (r == max)
				h = (g - b) / delta; /*
										 * Resulting color is between yellow and magenta
										 */
			else if (g == max)
				h = 2.0 + (b - r) / delta; /*
											 * Resulting color is between cyan and yellow
											 */
			else if (b == max)
				h = 4.0 + (r - g) / delta; /*
											 * Resulting color is between magenta and cyan
											 */
			h *= 60.0; /* Convert hue to degrees */
			if (h < 0.0)
				h += 360.0; /* Make sure hue is nonnegative */
		} /* Chromatic case */
	}

}
