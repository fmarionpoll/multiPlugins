package plugins.fmp.multitools.tools.overlay;

import java.awt.event.MouseEvent;

import icy.canvas.IcyCanvas;
import icy.canvas.IcyCanvas2D;
import icy.painter.Overlay;
import icy.sequence.Sequence;
import icy.type.point.Point5D;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.sequence.SequenceKymos;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.service.CageKymographPickSupport;
import plugins.fmp.multitools.tools.chart.interaction.SpotChartRoiFocus;

/**
 * Left-click on a stacked cage kymograph ({@code kymocage_*.tif*}) selects the spot band under the
 * cursor and jumps the camera viewer to the nearest frame for that kymograph column.
 */
public final class CageKymographSpotPickOverlay extends Overlay {

	private final Experiment experiment;

	public CageKymographSpotPickOverlay(Experiment experiment) {
		super("CageKymographSpotPick");
		this.experiment = experiment;
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

		int w = seqKymo.getSizeX();
		int h = seqKymo.getSizeY();
		if (w <= 0 || h <= 0) {
			return;
		}

		int col = CageKymographPickSupport.clampColumn((int) Math.floor(imagePoint.getX()), w);
		Integer camT = CageKymographPickSupport.cameraFrameForKymographColumn(experiment, col);
		Spot spot = CageKymographPickSupport.spotAtImageY(cage, experiment.getSpots(), w, h, imagePoint.getY());
		if (spot == null || camT == null) {
			return;
		}

		spot.setSpotCamDataT(camT);
		SpotChartRoiFocus.moveViewerToSpotTAndSelectRoi(experiment.getSeqCamData(), spot);
		event.consume();
	}
}
