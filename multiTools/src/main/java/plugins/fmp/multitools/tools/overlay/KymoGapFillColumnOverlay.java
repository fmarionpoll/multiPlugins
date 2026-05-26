package plugins.fmp.multitools.tools.overlay;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.BooleanSupplier;
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
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.service.CageKymographSpotBands;
import plugins.fmp.multitools.service.KymoAnalysisResult;
import plugins.fmp.multitools.service.KymoAnalysisResult.SpotKymoSeries;
import plugins.fmp.multitools.service.KymocageCageResolver;
import plugins.fmp.multitools.tools.Logger;

/**
 * Highlights kymograph columns where gap-fill interpolation changed the stored fraction (per spot band).
 */
public final class KymoGapFillColumnOverlay extends Overlay implements SequenceListener {

	private static final String OVERLAY_NAME = "KymoGapFillColumns";
	private static final float DEFAULT_OPACITY = 0.45f;
	private static final int MARKER_ARGB = 0xCC00FFFF;

	private final Supplier<Experiment> experimentSupplier;
	private final Supplier<KymoAnalysisResult> resultSupplier;
	private final BooleanSupplier showSupplier;
	private Sequence localSequence;
	private float opacity = DEFAULT_OPACITY;

	public KymoGapFillColumnOverlay(Sequence sequence, Supplier<Experiment> experimentSupplier,
			Supplier<KymoAnalysisResult> resultSupplier, BooleanSupplier showSupplier) {
		super(OVERLAY_NAME);
		this.experimentSupplier = experimentSupplier;
		this.resultSupplier = resultSupplier;
		this.showSupplier = showSupplier;
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
		if (showSupplier == null || !showSupplier.getAsBoolean()) {
			return;
		}
		Experiment exp = experimentSupplier != null ? experimentSupplier.get() : null;
		KymoAnalysisResult res = resultSupplier != null ? resultSupplier.get() : null;
		if (exp == null || res == null || exp.getSeqKymos() == null || exp.getCages() == null
				|| exp.getSpots() == null) {
			return;
		}
		try {
			int t = canvas.getPositionT();
			IcyBufferedImage rgb = sequence.getImage(t, 0);
			if (rgb == null) {
				return;
			}
			String fn = exp.getSeqKymos().getFileNameFromImageList(t);
			Cage cage = KymocageCageResolver.resolveCageFromKymographPath(fn, exp.getCages());
			if (cage == null || cage.getProperties() == null) {
				return;
			}
			int cageId = cage.getProperties().getCageID();
			List<SpotKymoSeries> rows = res.curvesForCage(cageId);
			if (rows.isEmpty()) {
				return;
			}
			int w = rgb.getWidth();
			int h = rgb.getHeight();
			int refW = w;
			int refH = h;
			if (exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null) {
				refW = exp.getSeqCamData().getSequence().getSizeX();
				refH = exp.getSeqCamData().getSequence().getSizeY();
			}
			List<CageKymographSpotBands> bands = CageKymographSpotBands.layout(cage, exp.getSpots(), refW, refH);
			BufferedImage argb = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			for (SpotKymoSeries row : rows) {
				double[] before = row.fractionBeforeGapFill;
				double[] after = row.fraction;
				if (before == null || after == null) {
					continue;
				}
				CageKymographSpotBands band = findBand(bands, row.spot);
				if (band == null) {
					continue;
				}
				if (band.geometryMissing) {
					continue;
				}
				int y0 = Math.max(0, band.y0);
				int y1 = Math.min(h, band.y1Exclusive);
				int n = Math.min(w, Math.min(before.length, after.length));
				for (int x = 0; x < n; x++) {
					double a = before[x];
					double b = after[x];
					boolean changed = (Double.isFinite(b) && !Double.isFinite(a))
							|| (Double.isFinite(a) && Double.isFinite(b) && Math.abs(b - a) > 1e-9);
					if (!changed) {
						continue;
					}
					for (int y = y0; y < y1; y++) {
						argb.setRGB(x, y, MARKER_ARGB);
					}
				}
			}
			Composite prev = graphics.getComposite();
			graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
			graphics.drawImage(argb, 0, 0, null);
			graphics.setComposite(prev);
		} catch (Exception e) {
			Logger.warn("KymoGapFillColumnOverlay: paint failed", e);
		}
	}

	private static CageKymographSpotBands findBand(List<CageKymographSpotBands> bands,
			plugins.fmp.multitools.experiment.spot.Spot spot) {
		if (bands == null || spot == null) {
			return null;
		}
		for (CageKymographSpotBands b : bands) {
			if (b != null && b.spot == spot) {
				return b;
			}
			if (b != null && spot.getName() != null && b.spot != null && spot.getName().equals(b.spot.getName())) {
				return b;
			}
		}
		return null;
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
