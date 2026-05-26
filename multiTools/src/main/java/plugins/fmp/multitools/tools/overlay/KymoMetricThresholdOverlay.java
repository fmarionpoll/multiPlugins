package plugins.fmp.multitools.tools.overlay;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.function.Supplier;

import icy.canvas.IcyCanvas;
import icy.canvas.IcyCanvas2D;
import icy.image.IcyBufferedImage;
import icy.painter.Overlay;
import icy.sequence.Sequence;
import icy.sequence.SequenceEvent;
import icy.sequence.SequenceEvent.SequenceEventSourceType;
import icy.sequence.SequenceEvent.SequenceEventType;
import icy.sequence.SequenceListener;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multitools.service.CageKymoAnalyzer;
import plugins.fmp.multitools.service.KymoImageTransforms;
import plugins.fmp.multitools.tools.Logger;

/**
 * Semi-transparent mask on the kymograph sequence: pixels counted in the metric fraction
 * (valid sum RGB and metric &gt; threshold), using the same transform as {@link CageKymoAnalyzer}.
 */
public final class KymoMetricThresholdOverlay extends Overlay implements SequenceListener {

	private static final String OVERLAY_NAME = "KymoMetricThreshold";
	private static final float DEFAULT_OPACITY = 0.35f;
	private static final int MASK_ARGB = 0xB4FF0000;

	private final Supplier<CageKymoAnalyzer.Params> paramsSupplier;
	private Sequence localSequence;
	private float opacity = DEFAULT_OPACITY;

	public KymoMetricThresholdOverlay(Sequence sequence, Supplier<CageKymoAnalyzer.Params> paramsSupplier) {
		super(OVERLAY_NAME);
		this.paramsSupplier = paramsSupplier;
		if (sequence != null) {
			setSequence(sequence);
		}
	}

	public void setOpacity(float opacity) {
		if (opacity >= 0f && opacity <= 1f) {
			this.opacity = opacity;
		}
	}

	public void setSequence(Sequence sequence) {
		if (localSequence != null) {
			localSequence.removeListener(this);
		}
		this.localSequence = sequence;
		if (sequence != null) {
			sequence.addListener(this);
		}
	}

	@Override
	public void paint(Graphics2D graphics, Sequence sequence, IcyCanvas canvas) {
		if (graphics == null || sequence == null || canvas == null || !(canvas instanceof IcyCanvas2D)) {
			return;
		}
		CageKymoAnalyzer.Params p = paramsSupplier != null ? paramsSupplier.get() : null;
		if (p == null) {
			return;
		}
		try {
			int t = canvas.getPositionT();
			IcyBufferedImage rgb = sequence.getImage(t, 0);
			if (rgb == null) {
				return;
			}
			IcyBufferedImage metricImg = KymoImageTransforms.applyMetricTransform(rgb, p.metricTransform,
					p.useGpuTransforms);
			if (metricImg == null) {
				return;
			}
			double[] metric = KymoImageTransforms.channel0AsDouble(metricImg);
			if (metric == null || metric.length == 0) {
				return;
			}
			int w = rgb.getSizeX();
			int h = rgb.getSizeY();
			int nC = Math.max(1, rgb.getSizeC());
			int[] r = nC > 0 ? Array1DUtil.arrayToIntArray(rgb.getDataXY(0), rgb.isSignedDataType()) : null;
			int[] g = nC > 1 ? Array1DUtil.arrayToIntArray(rgb.getDataXY(1), rgb.isSignedDataType()) : null;
			int[] b = nC > 2 ? Array1DUtil.arrayToIntArray(rgb.getDataXY(2), rgb.isSignedDataType()) : null;
			int len = w * h;
			double thr = p.metricThreshold;
			int minSum = p.minSumRgbForValidPixel;
			BufferedImage argb = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			for (int i = 0; i < len && i < metric.length; i++) {
				int rv = sampleChan(r, i);
				int gv = nC > 1 ? sampleChan(g, i) : rv;
				int bv = nC > 2 ? sampleChan(b, i) : rv;
				int sum = rv + gv + bv;
				if (sum < minSum) {
					continue;
				}
				double m = metric[i];
				if (Double.isFinite(m) && m > thr) {
					argb.setRGB(i % w, i / w, MASK_ARGB);
				}
			}
			Composite prev = graphics.getComposite();
			graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
			graphics.drawImage(argb, 0, 0, null);
			graphics.setComposite(prev);
		} catch (Exception e) {
			Logger.warn("KymoMetricThresholdOverlay: paint failed", e);
		}
	}

	private static int sampleChan(int[] ch, int idx) {
		if (ch == null || idx < 0 || idx >= ch.length) {
			return 0;
		}
		return ch[idx];
	}

	@Override
	public void sequenceChanged(SequenceEvent sequenceEvent) {
		if (sequenceEvent == null) {
			return;
		}
		if (sequenceEvent.getSourceType() != SequenceEventSourceType.SEQUENCE_OVERLAY) {
			return;
		}
		if (sequenceEvent.getSource() == this && sequenceEvent.getType() == SequenceEventType.REMOVED) {
			cleanup(sequenceEvent.getSequence());
		}
	}

	@Override
	public void sequenceClosed(Sequence sequence) {
		if (sequence != null) {
			cleanup(sequence);
		}
	}

	private void cleanup(Sequence sequence) {
		if (sequence != null) {
			sequence.removeListener(this);
		}
		remove();
	}
}
