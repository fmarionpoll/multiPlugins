package plugins.fmp.multiSPOTS96.tools.imageTransform.transforms;

import icy.image.IcyBufferedImage;
import icy.type.DataType;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformFunctionAbstract;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformOptions;

public class YDifferenceL extends ImageTransformFunctionAbstract implements ImageTransformInterface {
	int spanx = 0;
	int deltax = 0;
	int spany = 4;
	int deltay = 0;
	boolean computeL2 = true;

	public YDifferenceL(int spanx, int deltax, int spany, int deltay, boolean computeL2) {
		this.spanx = spanx;
		this.deltax = deltax;
		this.spany = spany;
		this.deltay = deltay;
		this.computeL2 = computeL2;
	}

	@Override
	public IcyBufferedImage getTransformedImage(IcyBufferedImage sourceImage, ImageTransformOptions options) {
		IcyBufferedImage img2 = new IcyBufferedImage(sourceImage.getWidth(), sourceImage.getHeight(), 3,
				sourceImage.getDataType_());
		double[] Rn = Array1DUtil.arrayToDoubleArray(sourceImage.getDataXY(0), sourceImage.isSignedDataType());
		double[] Gn = Array1DUtil.arrayToDoubleArray(sourceImage.getDataXY(1), sourceImage.isSignedDataType());
		double[] Bn = Array1DUtil.arrayToDoubleArray(sourceImage.getDataXY(2), sourceImage.isSignedDataType());
		double[] outValues = (double[]) Array1DUtil.createArray(DataType.DOUBLE, Rn.length);
		int imageSizeX = sourceImage.getSizeX();
		int imageSizeY = sourceImage.getSizeY();

		for (int ix = 0; ix < imageSizeX; ix++) {
			for (int iy = spany; iy < imageSizeY - spany; iy++) {
				RGBasDouble d1 = getSpanSumRGB(Rn, Gn, Bn, ix, iy, spanx, 0, -spany, -deltay, imageSizeX, imageSizeY);
				RGBasDouble d2 = getSpanSumRGB(Rn, Gn, Bn, ix, iy, spanx, 0, spany, deltay, imageSizeX, imageSizeY);

				int kx = ix + iy * imageSizeX;
				double dr = (d1.R / d1.n - d2.R / d2.n);
				double dg = (d1.G / d1.n - d2.G / d2.n);
				double db = (d1.B / d1.n - d2.B / d2.n);
				if (computeL2)
					outValues[kx] = (int) Math.sqrt(dr * dr + dg * dg + db * db);
				else
					outValues[kx] = (int) Math.abs(dr) + Math.abs(dg) + Math.abs(db);
			}
		}

		copyExGDoubleToIcyBufferedImage(outValues, img2, options.copyResultsToThe3planes);
		return img2;
	}

	private class RGBasDouble {
		public double R = 0.;
		public double G = 0.;
		public double B = 0.;
		public double n = 0;
	}

	private int Max(int a, int b) {
		return a >= b ? a : b;
	}

	private int Min(int a, int b) {
		return a <= b ? a : b;
	}

	private RGBasDouble getSpanSumRGB(double[] Rn, double[] Gn, double[] Bn, int ix, int iy, int spanx, int deltax,
			int spany, int deltay, int imageSizeX, int imageSizeY) {
		RGBasDouble d = new RGBasDouble();
		int iymax = Max(iy + deltay, iy + spany + deltay);
		int iymin = Min(iy + deltay, iy + spany + deltay);

		int ixmax = Max(ix + deltax, ix + spanx + deltax);
		int ixmin = Min(ix + deltax, ix + spanx + deltax);

		for (int iiy = iymin; iiy <= iymax; iiy++) {
			if (iiy < 0 || iiy >= imageSizeY)
				continue;
			int iiydelta = iiy * imageSizeX;

			for (int iix = ixmin; iix <= ixmax; iix++) {
				if (iix < 0 || iix >= imageSizeX)
					continue;
				int kx1 = iiydelta + iix;
				d.R += Rn[kx1];
				d.G += Gn[kx1];
				d.B += Bn[kx1];
				d.n++;
			}
		}
		return d;
	}
}
