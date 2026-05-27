package plugins.fmp.multitools.tools.overlay;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Supplier;

import icy.canvas.IcyCanvas;
import icy.canvas.IcyCanvas2D;
import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;
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
import plugins.fmp.multitools.service.KymoStripPostLiftMetricMask;
import plugins.fmp.multitools.service.KymoStripRowwiseOcclusionFill;
import plugins.fmp.multitools.service.KymocageCageResolver;
import plugins.fmp.multitools.tools.Logger;

/**
 * Row-wise occlusion lift overlay: optional blend of lifted RGB inside bands, and green where the post-lift metric mask
 * passes trace cleanup (temporal gap fill, vertical bridge, left-anchored connectivity within each spot band).
 */
public final class KymoMergedRegionsOverlay extends Overlay implements SequenceListener {

	private static final String OVERLAY_NAME = "KymoMergedRegions";
	private static final float DEFAULT_OPACITY = 0.35f;
	private static final int DIFF_ARGB = 0xD000FF00;
	private static final float PREVIEW_BLEND = 0.42f;
	private static final float DIFF_BLEND = 0.55f;

	private final Supplier<CageKymoAnalyzer.Params> paramsSupplier;
	private final Supplier<Experiment> experimentSupplier;
	private Sequence localSequence;
	private float opacity = DEFAULT_OPACITY;

	public KymoMergedRegionsOverlay(Sequence sequence, Supplier<CageKymoAnalyzer.Params> paramsSupplier,
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
			if (exp == null || exp.getCages() == null || exp.getSpots() == null || exp.getSeqKymos() == null) {
				return;
			}
			String fn = exp.getSeqKymos().getFileNameFromImageList(t);
			Cage cage = KymocageCageResolver.resolveCageFromKymographPath(fn, exp.getCages());
			if (cage == null) {
				return;
			}
			int refW = w;
			int refH = h;
			if (exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null) {
				refW = exp.getSeqCamData().getSequence().getSizeX();
				refH = exp.getSeqCamData().getSequence().getSizeY();
			}
			List<CageKymographSpotBands> bands = CageKymographPickSupport.stackedSpotBands(exp, cage, exp.getSpots(),
					refW, refH, w);
			IcyBufferedImage work = p.rowwiseOcclusionFill ? KymoStripRowwiseOcclusionFill.apply(rgb0, bands, p) : rgb0;
			boolean haveLift = work != rgb0;

			Composite prevComp = graphics.getComposite();
			Shape prevClip = graphics.getClip();

			if (p.previewLiftedBands && haveLift) {
				BufferedImage argbWork = IcyBufferedImageUtil.getARGBImage(work);
				graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
						clamp01(PREVIEW_BLEND * opacity)));
				for (CageKymographSpotBands band : bands) {
					if (band == null || band.geometryMissing) {
						continue;
					}
					int y0 = Math.max(0, band.y0);
					int y1 = Math.min(h, band.y1Exclusive);
					if (y1 <= y0) {
						continue;
					}
					graphics.setClip(0, y0, w, y1 - y0);
					graphics.drawImage(argbWork, 0, 0, null);
				}
				graphics.setClip(prevClip);
			}

			if (haveLift) {
				BufferedImage postLiftMask = buildPostLiftThresholdMask(work, bands, p, w, h);
				if (postLiftMask != null) {
					graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
							clamp01(DIFF_BLEND * opacity)));
					graphics.drawImage(postLiftMask, 0, 0, null);
				}
			}

			graphics.setComposite(prevComp);
			graphics.setClip(prevClip);
		} catch (Exception e) {
			Logger.warn("KymoMergedRegionsOverlay: paint failed", e);
		}
	}

	private static float clamp01(float a) {
		if (a < 0f) {
			return 0f;
		}
		if (a > 1f) {
			return 1f;
		}
		return a;
	}

	private static BufferedImage buildPostLiftThresholdMask(IcyBufferedImage lifted,
			List<CageKymographSpotBands> bands, CageKymoAnalyzer.Params p, int w, int h) {
		if (lifted == null || bands == null || p == null) {
			return null;
		}
		boolean anyBand = false;
		for (CageKymographSpotBands b : bands) {
			if (b != null && !b.geometryMissing) {
				anyBand = true;
				break;
			}
		}
		if (!anyBand) {
			return null;
		}
		IcyBufferedImage metricImg = KymoImageTransforms.applyMetricTransform(lifted, p.metricTransform,
				p.useGpuTransforms);
		if (metricImg == null) {
			return null;
		}
		double[] metric = KymoImageTransforms.channel0AsDouble(metricImg);
		if (metric == null || metric.length == 0) {
			return null;
		}
		int nC = Math.max(1, lifted.getSizeC());
		int[] r = nC > 0 ? Array1DUtil.arrayToIntArray(lifted.getDataXY(0), lifted.isSignedDataType()) : null;
		int[] g = nC > 1 ? Array1DUtil.arrayToIntArray(lifted.getDataXY(1), lifted.isSignedDataType()) : null;
		int[] b = nC > 2 ? Array1DUtil.arrayToIntArray(lifted.getDataXY(2), lifted.isSignedDataType()) : null;
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		boolean any = false;
		for (CageKymographSpotBands band : bands) {
			if (band == null || band.geometryMissing) {
				continue;
			}
			boolean[] cleaned = KymoStripPostLiftMetricMask.buildWithMetric(lifted, band, p, w, h, metric, r, g, b, nC);
			int y0 = Math.max(0, band.y0);
			int y1 = Math.min(h, band.y1Exclusive);
			for (int y = y0; y < y1; y++) {
				int row = y * w;
				for (int x = 0; x < w; x++) {
					int idx = row + x;
					if (cleaned[idx]) {
						out.setRGB(x, y, DIFF_ARGB);
						any = true;
					}
				}
			}
		}
		return any ? out : null;
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
