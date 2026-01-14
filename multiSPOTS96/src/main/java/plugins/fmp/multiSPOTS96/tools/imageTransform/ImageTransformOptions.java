package plugins.fmp.multiSPOTS96.tools.imageTransform;

import java.awt.Color;
import java.util.ArrayList;

import icy.image.IcyBufferedImage;

public class ImageTransformOptions {
	public ImageTransformEnums transformOption;
	public IcyBufferedImage backgroundImage = null;
	public IcyBufferedImage secondImage = null;
	public int npixels_changed = 0;
	public boolean copyResultsToThe3planes = true;

	public int xfirst;
	public int xlast;
	public int yfirst;
	public int ylast;
	public int channel0;
	public int channel1;
	public int channel2;
	public int w0 = 1;
	public int w1 = 1;
	public int w2 = 1;
	public int spanDiff = 3;
	public int simplethreshold = 255;
	public int background_delta = 50;
	public int background_jitter = 1;

	public int colorthreshold = 0;
	public int colordistanceType = 0;
	public boolean ifGreater = true;

	public final byte byteFALSE = 0;
	public final byte byteTRUE = (byte) 0xFF;
	public ArrayList<Color> colorarray = null;

	public void setSingleThreshold(int simplethreshold, boolean ifGreater) {
		this.simplethreshold = simplethreshold;
		this.ifGreater = ifGreater;
	}

	public void setColorArrayThreshold(int colordistanceType, int colorthreshold, ArrayList<Color> colorarray) {
		transformOption = ImageTransformEnums.THRESHOLD_COLORS;
		this.colordistanceType = colordistanceType;
		this.colorthreshold = colorthreshold;
		this.colorarray = colorarray;
	}
}
