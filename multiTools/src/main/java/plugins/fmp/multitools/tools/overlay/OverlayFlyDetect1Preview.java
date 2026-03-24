package plugins.fmp.multitools.tools.overlay;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import icy.canvas.IcyCanvas;
import icy.canvas.IcyCanvas2D;
import icy.image.IcyBufferedImage;
import icy.painter.Overlay;
import icy.roi.BooleanMask2D;
import icy.sequence.Sequence;
import icy.sequence.SequenceEvent;
import icy.sequence.SequenceEvent.SequenceEventSourceType;
import icy.sequence.SequenceEvent.SequenceEventType;
import icy.sequence.SequenceListener;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.series.DetectFlyTools;
import plugins.fmp.multitools.series.FlyDetect1;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.Logger;

/**
 * Preview overlay for FlyDetect1: same transform pipeline, binarization, and per-cage blob
 * filters as detection (no writes to fly positions).
 */
public class OverlayFlyDetect1Preview extends Overlay implements SequenceListener {

	private static final String OVERLAY_NAME = "FlyDetect1Preview";
	private static final float DEFAULT_OPACITY = 0.35f;

	private float opacity = DEFAULT_OPACITY;
	private Sequence localSequence;
	private Experiment experiment;
	private BuildSeriesOptions previewOptions;
	private final DetectFlyTools previewTools = new DetectFlyTools();

	public OverlayFlyDetect1Preview(Sequence sequence) {
		super(OVERLAY_NAME);
		if (sequence != null)
			setSequence(sequence);
	}

	public void setOpacity(float opacity) {
		if (opacity < 0f || opacity > 1f)
			throw new IllegalArgumentException("opacity");
		this.opacity = opacity;
	}

	public float getOpacity() {
		return opacity;
	}

	public void setSequence(Sequence sequence) {
		if (sequence == null)
			throw new IllegalArgumentException("Sequence cannot be null");
		if (localSequence != null)
			localSequence.removeListener(this);
		this.localSequence = sequence;
		sequence.addListener(this);
	}

	public Sequence getSequence() {
		return localSequence;
	}

	/**
	 * Refreshes cage masks and options used for painting. Does not clear fly measures.
	 */
	public void setPreviewState(Experiment exp, BuildSeriesOptions options) {
		this.experiment = exp;
		this.previewOptions = options;
		previewTools.options = options;
		previewTools.cages = exp != null ? exp.getCages() : null;
		if (previewTools.cages != null)
			previewTools.cages.computeBooleanMasksForCages();
	}

	@Override
	public void paint(Graphics2D graphics, Sequence sequence, IcyCanvas canvas) {
		if (graphics == null || sequence == null || canvas == null)
			return;
		if (!(canvas instanceof IcyCanvas2D))
			return;
		if (experiment == null || previewOptions == null || previewTools.cages == null)
			return;

		try {
			int t = canvas.getPositionT();
			IcyBufferedImage neg = FlyDetect1.transformFrameForFlyDetect1(experiment, previewOptions, t);
			if (neg == null)
				return;
			BooleanMask2D union = previewTools.unionFilteredFlyBlobs(neg, t);
			if (union == null)
				return;
			BufferedImage bi = maskToArgb(union);
			if (bi == null)
				return;
			Composite prev = graphics.getComposite();
			graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
			graphics.drawImage(bi, 0, 0, null);
			graphics.setComposite(prev);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			Logger.warn("OverlayFlyDetect1Preview paint failed", e);
		}
	}

	private static BufferedImage maskToArgb(BooleanMask2D union) throws InterruptedException {
		Rectangle r = union.bounds;
		if (r.width <= 0 || r.height <= 0)
			return null;
		BufferedImage bi = new BufferedImage(r.width, r.height, BufferedImage.TYPE_INT_ARGB);
		int argb = 0xFFFF0000;
		for (java.awt.Point p : union.getPoints()) {
			int lx = p.x - r.x;
			int ly = p.y - r.y;
			if (lx >= 0 && lx < r.width && ly >= 0 && ly < r.height)
				bi.setRGB(lx, ly, argb);
		}
		return bi;
	}

	@Override
	public void sequenceChanged(SequenceEvent sequenceEvent) {
		if (sequenceEvent == null)
			return;
		if (sequenceEvent.getSourceType() != SequenceEventSourceType.SEQUENCE_OVERLAY)
			return;
		if (sequenceEvent.getSource() == this && sequenceEvent.getType() == SequenceEventType.REMOVED)
			cleanupSequenceListener(sequenceEvent.getSequence());
	}

	@Override
	public void sequenceClosed(Sequence sequence) {
		if (sequence != null)
			cleanupSequenceListener(sequence);
	}

	private void cleanupSequenceListener(Sequence sequence) {
		if (sequence != null)
			sequence.removeListener(this);
		remove();
	}
}
