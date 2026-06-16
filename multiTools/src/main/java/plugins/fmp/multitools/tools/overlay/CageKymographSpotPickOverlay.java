package plugins.fmp.multitools.tools.overlay;

import java.awt.event.MouseEvent;

import icy.canvas.IcyCanvas;
import icy.canvas.IcyCanvas2D;
import icy.image.IcyBufferedImage;
import icy.painter.Overlay;
import icy.sequence.Sequence;
import icy.type.point.Point5D;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.sequence.SequenceKymos;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.service.CageKymographPickSupport;
import plugins.fmp.multitools.service.CageKymographPickSupport.KymoPickWindow;
import plugins.fmp.multitools.tools.chart.interaction.SpotChartRoiFocus;

/**
 * Left-click on a stacked cage kymograph ({@code kymocage_*.tif*}) selects the spot band under the
 * cursor and jumps the camera viewer to the nearest frame for that kymograph column. Band layout
 * prefers {@code CageKymographStripLayout.csv} in the kymograph bin (strip heights and column time
 * grid from export); otherwise spot ROI geometry and the experiment kymograph interval.
 */
public final class CageKymographSpotPickOverlay extends Overlay {

	private final Experiment experiment;
	private final java.util.function.Consumer<Spot> onSpotPicked;

	public CageKymographSpotPickOverlay(Experiment experiment) {
		this(experiment, null);
	}

	public CageKymographSpotPickOverlay(Experiment experiment, java.util.function.Consumer<Spot> onSpotPicked) {
		super("CageKymographSpotPick");
		this.experiment = experiment;
		this.onSpotPicked = onSpotPicked;
	}

	@Override
	public void mouseClick(MouseEvent event, Point5D.Double imagePoint, IcyCanvas canvas) {
		if (event == null || imagePoint == null || canvas == null) {
			return;
		}
		if (!(canvas instanceof IcyCanvas2D)) {
			return;
		}
		if (event.getButton() != MouseEvent.BUTTON1) {
			return;
		}
		if (experiment == null || experiment.getSeqKymos() == null || experiment.getSeqCamData() == null) {
			return;
		}
		SequenceKymos sk = experiment.getSeqKymos();
		Sequence seqKymo = sk.getSequence();
		Sequence seqCanvas = canvas.getSequence();
		if (seqKymo == null || seqCanvas == null || seqKymo.getId() != seqCanvas.getId()) {
			return;
		}

		int tKymo = canvas.getPositionT();
		if (!CageKymographPickSupport.isCageKymographFrame(experiment, tKymo)) {
			return;
		}

		Cage cage = CageKymographPickSupport.cageForKymographFrame(experiment, tKymo);
		if (cage == null) {
			return;
		}

		IcyBufferedImage kimg = seqKymo.getImage(tKymo, 0);
		int kymoW = kimg != null ? kimg.getWidth() : 0;
		KymoPickWindow win = CageKymographPickSupport.resolveKymoPickWindowForCageKymograph(experiment, kymoW);
		if (win == null || win.columnCount <= 0) {
			return;
		}

		int[] refCam = CageKymographPickSupport.cameraReferenceSize(experiment);
		int refW = refCam[0];
		int refH = refCam[1];
		if (refW <= 0 || refH <= 0) {
			return;
		}

		int stackBottom = CageKymographPickSupport.stackedContentHeightPixels(experiment, cage, experiment.getSpots(),
				refW, refH, win.columnCount);
		if (stackBottom <= 0) {
			return;
		}

		int ix = (int) Math.floor(imagePoint.getX());
		int iy = (int) Math.floor(imagePoint.getY());
		// Logical kymograph content is top-left in the sequence frame; right/bottom padding (max
		// canvas size across cages) must not map to a spot/column.
		if (ix < 0 || iy < 0 || ix >= win.columnCount || iy >= stackBottom) {
			return;
		}

		Integer camT = CageKymographPickSupport.cameraFrameForKymographColumn(experiment, ix, win);
		Spot spot = CageKymographPickSupport.spotAtStackedY(experiment, cage, experiment.getSpots(), refW, refH,
				win.columnCount, iy);
		if (spot == null || camT == null) {
			return;
		}

		spot.setSpotCamDataT(camT);
		SpotChartRoiFocus.moveViewerToSpotTAndSelectRoi(experiment.getSeqCamData(), spot);
		if (onSpotPicked != null) {
			onSpotPicked.accept(spot);
		}
		event.consume();
	}
}
