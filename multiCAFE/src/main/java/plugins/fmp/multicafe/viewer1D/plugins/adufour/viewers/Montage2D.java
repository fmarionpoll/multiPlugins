package plugins.fmp.multicafe.viewer1D.plugins.adufour.viewers;

import icy.canvas.Canvas2D;
import icy.canvas.IcyCanvas;
import icy.gui.viewer.Viewer;
import icy.image.IcyBufferedImage;
import icy.plugin.abstract_.PluginActionable;
import icy.plugin.interface_.PluginCanvas;
import icy.sequence.Sequence;
import icy.system.IcyHandledException;
import icy.type.DataType;

import java.awt.Point;

/**
 * Class providing a plug-in and a viewer for 3D images and sequence to perform
 * a montage of all Z slices in a single 2D plane. The size of the montage is
 * the closest possible to a square (landscape orientation if not square)
 * 
 * @author Alexandre Dufour
 * 
 */
public class Montage2D extends PluginActionable implements PluginCanvas {
	/**
	 * Creates a montage of the given Sequence into a 2D sequence. The size of the
	 * montage is the closest possible to a square (landscape orientation if not
	 * square)
	 * 
	 * @param sequence the 3D sequence
	 * @return a 2D image containing assembled z-stacks of the original image
	 */
	public static Sequence makeMontage2D(Sequence sequence) {
		int oldWidth = sequence.getSizeX();
		int oldHeight = sequence.getSizeY();
		int oldDepth = sequence.getSizeZ();

		int xCount = (int) Math.ceil(Math.sqrt(oldDepth));
		int yCount = (int) Math.rint(Math.sqrt(oldDepth));

		Sequence result = new Sequence();

		for (int t = 0; t < sequence.getSizeT(); t++) {
			IcyBufferedImage montage = new IcyBufferedImage(oldWidth * xCount, oldHeight * yCount, sequence.getSizeC(),
					sequence.getDataType_());

			for (int z = 0; z < oldDepth; z++) {
				montage.copyData(sequence.getImage(t, z), null,
						new Point(oldWidth * (z % xCount), oldHeight * ((int) Math.floor(z / xCount))));
			}

			result.setImage(t, 0, montage);
		}

		return result;
	}

	@Override
	public void run() {
		final Sequence input = getActiveSequence();
		if (input == null)
			return;

		addSequence(makeMontage2D(getActiveSequence()));
	}

	@Override
	public String getCanvasClassName() {
		return Montage2DCanvas.class.getName();
	}

	@Override
	public IcyCanvas createCanvas(Viewer viewer) {
		return new Montage2DCanvas(viewer);
	}

	public class Montage2DCanvas extends Canvas2D {
		private static final long serialVersionUID = 1L;

		private Sequence sequence;
		private int oldWidth, oldHeight, oldDepth;
		private int xCount, yCount;
		private int newWidth, newHeight;

		public Montage2DCanvas(Viewer viewer) {
			super(viewer);
			updateInternals(getSequence());
		}

		private void updateInternals(Sequence newSequence) {
			sequence = newSequence;
			oldWidth = sequence.getWidth();
			oldHeight = sequence.getHeight();
			oldDepth = sequence.getSizeZ();
			xCount = (int) Math.ceil(Math.sqrt(oldDepth));
			yCount = (int) Math.rint(Math.sqrt(oldDepth));
			newWidth = oldWidth * xCount;
			newHeight = oldHeight * yCount;
		}

		@SuppressWarnings("deprecation")
		@Override
		public IcyBufferedImage getImage(int t, int z, int c) {
			if (z != -1)
				return super.getImage(t, z, c);

			if (getSequence() == null)
				return null;

			if (sequence != getSequence() || oldDepth != getSequence().getSizeZ())
				updateInternals(getSequence());

			DataType type = sequence.getDataType_();

			if (type == DataType.UNDEFINED)
				throw new IcyHandledException("Unsupported data type (" + type
						+ "). Try converting the sequence to another data type first.");

			IcyBufferedImage montage = new IcyBufferedImage(newWidth, newHeight, sequence.getSizeC(), type);

			Point point = new Point();

			for (int k = 0; k < oldDepth; k++) {
				IcyBufferedImage src = sequence.getImage(t, k);
				point.x = oldWidth * (k % xCount);
				point.y = oldHeight * (k / xCount);// ((int) Math.floor(k / xCount));

				montage.copyData(src, null, point);
			}

			return montage;
		}

		/**
		 * Get image size X
		 */
		public int getImageSizeX() {
			return newWidth;
		}

		/**
		 * Get image size Y
		 */
		public int getImageSizeY() {
			return newHeight;
		}

		/**
		 * Get image size Z
		 */
		public int getImageSizeZ() {
			return 1;
		}

		@Override
		public int getPositionZ() {
			return -1;
		}

		/**
		 * Get maximum Z value
		 */
		public int getMaxZ() {
			return -1;
		}

		@Override
		public double getMouseImagePosX() {
			// TODO Auto-generated method stub
			return super.getMouseImagePosX();
		}

		@Override
		public double getMouseImagePosY() {
			// TODO Auto-generated method stub
			return super.getMouseImagePosY();
		}

		@Override
		public double getMouseImagePosZ() {
			// TODO Auto-generated method stub
			return super.getMouseImagePosZ();
		}
	}
}
