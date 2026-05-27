package plugins.fmp.multitools.tools.overlay;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
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
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.service.CageKymoAnalyzer;
import plugins.fmp.multitools.service.CageKymographPickSupport;
import plugins.fmp.multitools.service.CageKymographSpotBands;
import plugins.fmp.multitools.service.KymoImageTransforms;
import plugins.fmp.multitools.service.KymoMetricGate;
import plugins.fmp.multitools.service.KymoStripRowwiseOcclusionFill;
import plugins.fmp.multitools.service.KymocageCageResolver;
import plugins.fmp.multitools.tools.Logger;

/**
 * Semi-transparent mask where the insect metric passes its directed threshold (same rule as exclusion from the spot
 * mask). Uses the same RGB as the metric overlay (lifted when row lift is on).
 */
public final class KymoInsectMetricOverlay extends Overlay implements SequenceListener {

	private static final String OVERLAY_NAME = "KymoInsectMetric";
	private static final float DEFAULT_OPACITY = 0.38f;
	private static final int MASK_ARGB = 0xB4FF00FF;

	private final Supplier<CageKymoAnalyzer.Params> paramsSupplier;
	private final Supplier<Experiment> experimentSupplier;
	private Sequence localSequence;
	private float opacity = DEFAULT_OPACITY;

	public KymoInsectMetricOverlay(Sequence sequence, Supplier<CageKymoAnalyzer.Params> paramsSupplier,
			Supplier<Experiment> experimentSupplier) {
		super(OVERLAY_NAME);
		this.paramsSupplier = paramsSupplier;
		this.experimentSupplier = experimentSupplier;
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
			IcyBufferedImage rgb0 = sequence.getImage(t, 0);
			if (rgb0 == null) {
				return;
			}
			int w = rgb0.getWidth();
			int h = rgb0.getHeight();
			Experiment exp = experimentSupplier != null ? experimentSupplier.get() : null;
			IcyBufferedImage rgb = rgb0;
			Cage cage = null;
			List<CageKymographSpotBands> bands = null;
			if (exp != null && exp.getCages() != null && exp.getSpots() != null && exp.getSeqKymos() != null) {
				String fn = exp.getSeqKymos().getFileNameFromImageList(t);
				cage = KymocageCageResolver.resolveCageFromKymographPath(fn, exp.getCages());
				int refW = w;
				int refH = h;
				if (exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null) {
					refW = exp.getSeqCamData().getSequence().getSizeX();
					refH = exp.getSeqCamData().getSequence().getSizeY();
				}
				if (cage != null) {
					bands = CageKymographPickSupport.stackedSpotBands(exp, cage, exp.getSpots(), refW, refH, w);
					if (p.rowwiseOcclusionFill) {
						rgb = KymoStripRowwiseOcclusionFill.apply(rgb0, bands, p);
					}
				}
			}
			IcyBufferedImage insImg = KymoImageTransforms.applyMetricTransform(rgb, p.insectMetricTransform,
					p.useGpuTransforms);
			double[] insectMetric = insImg != null ? KymoImageTransforms.channel0AsDouble(insImg) : null;
			if (insectMetric == null || insectMetric.length == 0) {
				return;
			}
			int nC = Math.max(1, rgb.getSizeC());
			int[] r = nC > 0 ? Array1DUtil.arrayToIntArray(rgb.getDataXY(0), rgb.isSignedDataType()) : null;
			int[] g = nC > 1 ? Array1DUtil.arrayToIntArray(rgb.getDataXY(1), rgb.isSignedDataType()) : null;
			int[] b = nC > 2 ? Array1DUtil.arrayToIntArray(rgb.getDataXY(2), rgb.isSignedDataType()) : null;
			int minSum = p.minSumRgbForValidPixel;
			BufferedImage argb = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

			if (cage != null && bands != null && !bands.isEmpty()) {
				for (CageKymographSpotBands band : bands) {
					if (band.geometryMissing) {
						continue;
					}
					int y0 = Math.max(0, band.y0);
					int y1 = Math.min(h, band.y1Exclusive);
					for (int x = 0; x < w; x++) {
						for (int y = y0; y < y1; y++) {
							int idx = y * w + x;
							if (idx < 0 || idx >= insectMetric.length) {
								continue;
							}
							int rv = sampleChan(r, idx);
							int gv = nC > 1 ? sampleChan(g, idx) : rv;
							int bv = nC > 2 ? sampleChan(b, idx) : rv;
							if (rv + gv + bv < minSum) {
								continue;
							}
							if (KymoMetricGate.directedFinite(insectMetric[idx], p.insectMetricThreshold,
									p.insectMetricThresholdUp)) {
								argb.setRGB(x, y, MASK_ARGB);
							}
						}
					}
				}
			} else {
				paintFullImageLegacy(w, h, nC, r, g, b, insectMetric, p, argb);
			}
			Composite prev = graphics.getComposite();
			graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
			graphics.drawImage(argb, 0, 0, null);
			graphics.setComposite(prev);
		} catch (Exception e) {
			Logger.warn("KymoInsectMetricOverlay: paint failed", e);
		}
	}

	private static void paintFullImageLegacy(int w, int h, int nC, int[] r, int[] g, int[] b, double[] insectMetric,
			CageKymoAnalyzer.Params p, BufferedImage argb) {
		int len = w * h;
		int minSum = p.minSumRgbForValidPixel;
		for (int i = 0; i < len && i < insectMetric.length; i++) {
			int rv = sampleChan(r, i);
			int gv = nC > 1 ? sampleChan(g, i) : rv;
			int bv = nC > 2 ? sampleChan(b, i) : rv;
			if (rv + gv + bv < minSum) {
				continue;
			}
			if (KymoMetricGate.directedFinite(insectMetric[i], p.insectMetricThreshold, p.insectMetricThresholdUp)) {
				argb.setRGB(i % w, i / w, MASK_ARGB);
			}
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
