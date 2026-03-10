package plugins.fmp.multitools.tools.overlay;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;

import icy.canvas.IcyCanvas;
import icy.canvas.IcyCanvas2D;
import icy.painter.Overlay;
import icy.sequence.Sequence;

/**
 * Overlay that draws a filled white rectangle on the sequence viewer when the
 * current frame is detected as "dark" (light status 0). Used with
 * DarkFrameDetector / CleanGaps to show dark frames directly on the cam viewer.
 */
public class OverlayDarkLightIndicator extends Overlay {

	private static final String OVERLAY_NAME = "DarkLightIndicator";
	private static final int RECT_SIZE = 40;
	private static final int RECT_MARGIN = 10;
	private static final float OPACITY = 0.6f;

	private int[] lightStatusPerFrame = null;

	public OverlayDarkLightIndicator() {
		this(null);
	}

	public OverlayDarkLightIndicator(Sequence sequence) {
		super(OVERLAY_NAME);
	}

	/**
	 * Sets the per-frame light status array (1 = light, 0 = dark). Pass null to
	 * clear. The overlay keeps a reference; after detection, pass the same array
	 * from SequenceCamData.getLightStatusPerFrame() and call painterChanged() so
	 * the viewer repaints.
	 */
	public void setLightStatusPerFrame(int[] lightStatusPerFrame) {
		this.lightStatusPerFrame = lightStatusPerFrame;
	}

	public int[] getLightStatusPerFrame() {
		return lightStatusPerFrame;
	}

	@Override
	public void paint(Graphics2D g, Sequence sequence, IcyCanvas canvas) {
		if (g == null || sequence == null || canvas == null)
			return;
		if (!(canvas instanceof IcyCanvas2D))
			return;
		if (lightStatusPerFrame == null || lightStatusPerFrame.length == 0)
			return;

		int t = canvas.getPositionT();
		if (t < 0 || t >= lightStatusPerFrame.length)
			return;
		if (lightStatusPerFrame[t] != 0)
			return;

		int x = RECT_MARGIN;
		int y = RECT_MARGIN;
		int size = Math.min(RECT_SIZE, Math.min(sequence.getSizeX(), sequence.getSizeY()) - 2 * RECT_MARGIN);
		if (size <= 0)
			return;

		Composite old = g.getComposite();
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, OPACITY));
		g.setColor(Color.WHITE);
		g.fillRect(x, y, size, size);
		g.setComposite(old);
	}
}
