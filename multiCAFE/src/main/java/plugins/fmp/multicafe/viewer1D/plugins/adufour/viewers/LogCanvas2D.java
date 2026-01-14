package plugins.fmp.multicafe.viewer1D.plugins.adufour.viewers;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import icy.canvas.Canvas2D;
import icy.canvas.IcyCanvas;
import icy.gui.viewer.Viewer;
import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;
import icy.plugin.abstract_.Plugin;
import icy.plugin.interface_.PluginCanvas;
import icy.sequence.DimensionId;
import icy.system.SystemUtil;
import icy.system.thread.Processor;
import icy.type.DataType;

/**
 * 2D canvas displaying a log-view of the image without changing the actual
 * data. Useful to observe images with large dynamics, e.g. Fourier transforms
 * or 16-bits images
 * 
 * @author Alexandre Dufour
 */
public class LogCanvas2D extends Plugin implements PluginCanvas {
	private final static ExecutorService service = new Processor(SystemUtil.getNumberOfCPUs());

	@Override
	public String getCanvasClassName() {
		return LogCanvas2D.class.getName();
	}

	@Override
	public IcyCanvas createCanvas(Viewer viewer) {
		return new Canvas2D(viewer) {
			private static final long serialVersionUID = 1L;
			private IcyBufferedImage logImage = createLogImage(
					getImage(getPositionT(), getPositionZ(), getPositionC()));

			@Override
			public IcyBufferedImage getImage(int t, int z, int c) {
				if (logImage == null)
					logImage = createLogImage(super.getImage(t, z, c));
				return logImage;
			}

			@Override
			protected void positionChanged(DimensionId dim) {
				if (dim == DimensionId.T || dim == DimensionId.Z) {
					logImage = createLogImage(super.getImage(getPositionT(), getPositionZ(), getPositionC()));
				}

				super.positionChanged(dim);
			}

			private IcyBufferedImage createLogImage(IcyBufferedImage image) {
				if (image == null)
					return null;

				final IcyBufferedImage logImage = (image.getDataType_() == DataType.DOUBLE)
						? IcyBufferedImageUtil.getCopy(image)
						: IcyBufferedImageUtil.convertToType(image, DataType.DOUBLE, false);
				final double[][] data_C_XY = logImage.getDataXYCAsDouble();
				final Future<?>[] tasks = new Future[data_C_XY.length];

				for (int i = 0; i < data_C_XY.length; i++) {
					final int channel = i;
					tasks[channel] = service.submit(new Runnable() {
						@Override
						public void run() {
							logImage.updateChannelsBounds();
							double max = logImage.getChannelMax(channel);
							double ratio = max / Math.log1p(max);
							double[] array = data_C_XY[channel];
							for (int i = 0; i < array.length; i++)
								array[i] = Math.log1p(array[i]) * ratio;
						}
					});
				}

				try {
					for (Future<?> task : tasks)
						task.get();
					return logImage;
				} catch (Exception e) {
					return image;
				}
			}
		};
	}

	@Override
	protected void finalize() throws Throwable {
//        service.shutdown();
	}
}
