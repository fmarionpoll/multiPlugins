package plugins.fmp.multicafe.dlg.cages;

import java.awt.event.MouseEvent;

import icy.canvas.IcyCanvas;
import icy.canvas.IcyCanvas2D;
import icy.painter.Overlay;
import icy.type.point.Point5D;

/**
 * Forwards image clicks to {@link Host} while add-fly mode is armed.
 */
public final class AddFlyOverlay extends Overlay {

	public interface Host {
		void onAddFlyImageClick(double imageX, double imageY);
	}

	private final Host host;

	public AddFlyOverlay(Host host) {
		super("MultiCAFE_AddFlyOverlay");
		this.host = host;
	}

	@Override
	public void mouseClick(MouseEvent event, Point5D.Double imagePoint, IcyCanvas canvas) {
		if (event == null || imagePoint == null || canvas == null)
			return;
		if (!(canvas instanceof IcyCanvas2D) || canvas.getSequence() == null)
			return;
		if (event.getButton() != MouseEvent.BUTTON1)
			return;
		host.onAddFlyImageClick(imagePoint.getX(), imagePoint.getY());
		event.consume();
	}
}
