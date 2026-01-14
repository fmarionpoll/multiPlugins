package plugins.fmp.multiSPOTS.tools.overlay;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

import icy.canvas.IcyCanvas;
import icy.canvas.IcyCanvas2D;
import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;
import icy.painter.Overlay;
import icy.sequence.Sequence;
import icy.sequence.SequenceEvent;
import icy.sequence.SequenceEvent.SequenceEventSourceType;
import icy.sequence.SequenceEvent.SequenceEventType;
import plugins.fmp.multiSPOTS.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multiSPOTS.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multiSPOTS.tools.imageTransform.ImageTransformOptions;
import icy.sequence.SequenceListener;

public class OverlayThreshold extends Overlay implements SequenceListener {
	private float opacity = 0.3f;
	private OverlayColorMask map = new OverlayColorMask("overlaymask", new Color(0x00FF0000, true));
	private ImageTransformOptions imageTransformOptions = new ImageTransformOptions();
	private ImageTransformInterface imageTransformFunction = ImageTransformEnums.NONE.getFunction();
	private ImageTransformInterface imageThresholdFunction = ImageTransformEnums.NONE.getFunction();
	private Sequence localSeq = null;

	// ---------------------------------------------

	public OverlayThreshold() {
		super("ThresholdOverlay");
	}

	public OverlayThreshold(Sequence seq) {
		super("ThresholdOverlay");
		setSequence(seq);
	}

	public void setSequence(Sequence seq) {
		localSeq = seq;
		localSeq.addListener(this);
	}

	public void setThresholdSingle(int threshold, ImageTransformEnums transformop, boolean ifGreater) {
		setThresholdTransform(threshold, transformop, ifGreater);
	}

	public void setThresholdTransform(int threshold, ImageTransformEnums transformop, boolean ifGreater) {
		imageTransformOptions.setSingleThreshold(threshold, ifGreater);
		imageTransformOptions.transformOption = transformop;
		imageTransformFunction = transformop.getFunction();
		imageThresholdFunction = ImageTransformEnums.THRESHOLD_SINGLE.getFunction();
	}

	public void setReferenceImage(IcyBufferedImage referenceImage) {
		imageTransformOptions.backgroundImage = referenceImage;
	}

	public void setThresholdColor(ArrayList<Color> colorarray, int distancetype, int threshold) {
		imageTransformOptions.setColorArrayThreshold(distancetype, threshold, colorarray);
		imageTransformFunction = ImageTransformEnums.NONE.getFunction();
		imageThresholdFunction = ImageTransformEnums.THRESHOLD_COLORS.getFunction();
	}

	public IcyBufferedImage getTransformedImage(int t) {
		IcyBufferedImage img = localSeq.getImage(t, 0);
		IcyBufferedImage img2 = imageTransformFunction.getTransformedImage(img, imageTransformOptions);
		return imageThresholdFunction.getTransformedImage(img2, imageTransformOptions);
	}

	public IcyBufferedImage getTransformedImage(IcyBufferedImage img) {
		IcyBufferedImage img2 = imageTransformFunction.getTransformedImage(img, imageTransformOptions);
		return imageThresholdFunction.getTransformedImage(img2, imageTransformOptions);
	}

	@Override
	public void paint(Graphics2D g, Sequence sequence, IcyCanvas canvas) {
		if ((canvas instanceof IcyCanvas2D) && g != null) {
			int posT = canvas.getPositionT();
			IcyBufferedImage thresholdedImage = getTransformedImage(posT);
			if (thresholdedImage != null) {
				thresholdedImage.setColorMap(0, map);
				try {
					BufferedImage bufferedImage = IcyBufferedImageUtil.getARGBImage(thresholdedImage);
					Composite bck = g.getComposite();
					g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
					g.drawImage(bufferedImage, 0, 0, null);
					g.setComposite(bck);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
		}
	}

	@Override
	public void sequenceChanged(SequenceEvent sequenceEvent) {
		if (sequenceEvent.getSourceType() != SequenceEventSourceType.SEQUENCE_OVERLAY)
			return;
		if (sequenceEvent.getSource() == this && sequenceEvent.getType() == SequenceEventType.REMOVED) {
			sequenceEvent.getSequence().removeListener(this);
			remove();
		}
	}

	@Override
	public void sequenceClosed(Sequence sequence) {
		sequence.removeListener(this);
		remove();
	}

}
