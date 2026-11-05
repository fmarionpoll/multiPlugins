package plugins.fmp.multicafe.dlg.levels;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

import plugins.fmp.multitools.experiment.capillary.CapillaryIntakes;
import plugins.fmp.multitools.experiment.capillary.IntakeEvaporationOverlay;
import plugins.fmp.multitools.service.CorrelationGulpPreview;

final class IntakeAlignmentView extends JPanel {
	private static final long serialVersionUID = 1L;
	private static final int COLUMN_PX = 4;
	private static final int LEFT = 65;
	private static final int RIGHT = 25;
	private final IntakeChartHost host;

	IntakeAlignmentView(IntakeChartHost host) {
		this.host = host;
		setBackground(Color.WHITE);
		MouseAdapter mouse = new MouseAdapter() {
			private int start = -1;

			@Override
			public void mousePressed(MouseEvent e) {
				CorrelationGulpPreview preview = host.preview();
				if (preview == null || !SwingUtilities.isLeftMouseButton(e) || e.getX() < LEFT
						|| e.getX() >= getWidth() - RIGHT || e.getY() < 25
						|| e.getY() >= 25 + (cropBounds()[1] - cropBounds()[0]) * COLUMN_PX)
					return;
				host.selectFrame(frameAt(e.getX()));
				if (host.addGulpArmed())
					start = host.selectedFrame();
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				if (start < 0 || host.preview() == null) {
					start = -1;
					return;
				}
				int end = frameAt(e.getX()), first = start;
				start = -1;
				host.editGulpRange(Math.min(first, end), Math.max(first, end));
			}
		};
		addMouseListener(mouse);
	}

	private CorrelationGulpPreview preview() {
		return host.preview();
	}

	private int frameAt(int x) {
		CorrelationGulpPreview preview = preview();
		return Math.max(0, Math.min(preview.raw.length - 1,
				(int) ((x - LEFT) * (double) preview.image.getWidth() / imageWidth())));
	}

	@Override
	public Dimension getPreferredSize() {
		CorrelationGulpPreview preview = preview();
		int columns = preview == null ? 0 : preview.image.getWidth();
		int viewport = getParent() instanceof JViewport ? ((JViewport) getParent()).getWidth() : 1050;
		int imageWidth = Math.max(columns * COLUMN_PX, Math.max(1, viewport - LEFT - RIGHT));
		int cropRows = 17;
		if (preview != null) {
			int[] crop = cropBounds();
			cropRows = Math.max(1, crop[1] - crop[0]);
		}
		return new Dimension(LEFT + imageWidth + RIGHT, 25 + cropRows * COLUMN_PX + 36);
	}

	void revealSelection() {
		if (preview() == null)
			return;
		int x = xOf(host.selectedFrame());
		scrollRectToVisible(new Rectangle(Math.max(0, x - 40), 0, 80, Math.max(1, getHeight())));
	}

	private int[] cropBounds() {
		CorrelationGulpPreview preview = preview();
		int height = preview.image.getHeight();
		int min = height - 1;
		int max = 0;
		for (double v : preview.raw)
			if (Double.isFinite(v)) {
				min = Math.min(min, (int) v - 8);
				max = Math.max(max, (int) v + 9);
			}
		double[] amplitudes = host.candidateAmplitudes(), bottoms = host.candidateBottoms();
		for (int i = 1; i < amplitudes.length; i++)
			if (amplitudes[i] > 0 && Double.isFinite(bottoms[i])) {
				min = Math.min(min, (int) Math.floor(bottoms[i] - amplitudes[i]) - 2);
				max = Math.max(max, (int) Math.ceil(bottoms[i]) + 3);
			}
		min = Math.max(0, min);
		max = Math.min(height, Math.max(min + 1, max));
		return new int[] { min, max };
	}

	private int imageWidth() {
		return Math.max(1, getWidth() - LEFT - RIGHT);
	}

	private int xOf(int frame) {
		return LEFT + (int) (imageWidth() * (frame + .5) / preview().image.getWidth());
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		CorrelationGulpPreview preview = preview();
		if (preview == null)
			return;
		int[] crop = cropBounds();
		int cropHeight = Math.max(1, crop[1] - crop[0]) * COLUMN_PX;
		int width = imageWidth();
		boolean[] chosen = host.candidates();
		g.setColor(Color.DARK_GRAY);
		g.drawString("Kymograph: blue = intake; grey = evaporation + cumulative intake, anchored at t=0", LEFT, 18);
		g.drawImage(preview.image, LEFT, 25, LEFT + width, 25 + cropHeight, 0, crop[0], preview.image.getWidth(),
				crop[1], null);
		drawIntakeOverlay(g, chosen, crop, 25, cropHeight);
		g.setColor(Color.YELLOW);
		g.drawLine(xOf(host.selectedFrame()), 25, xOf(host.selectedFrame()), 25 + cropHeight);
		g.setColor(Color.DARK_GRAY);
		g.drawString(
				"Time axis: at least 4 pixels per column; scroll horizontally for longer kymographs. Grey columns: missing level or image border.",
				LEFT, getHeight() - 7);
	}

	private void drawIntakeOverlay(Graphics graphics, boolean[] chosen, int[] crop, int top, int height) {
		CorrelationGulpPreview preview = preview();
		double[] amplitudes = host.candidateAmplitudes();
		double[] bottoms = host.candidateBottoms();
		Graphics2D g = (Graphics2D) graphics.create();
		g.clipRect(LEFT, top, imageWidth(), height);
		g.setColor(IntakeEvaporationOverlay.COLOR);
		g.setStroke(new BasicStroke(1));
		List<List<java.awt.geom.Point2D>> paths = preview.hasEvaporationReference
				? IntakeEvaporationOverlay.paths(host.candidateAmplitudes(), preview.raw, preview.evaporation,
						host.compensateEvaporation())
				: java.util.Collections.emptyList();
		for (List<java.awt.geom.Point2D> points : paths) {
			java.awt.geom.Path2D path = new java.awt.geom.Path2D.Double();
			for (int i = 0; i < points.size(); i++) {
				java.awt.geom.Point2D point = points.get(i);
				double x = LEFT + imageWidth() * point.getX() / preview.image.getWidth();
				double y = top + height * (point.getY() - crop[0]) / (crop[1] - crop[0]);
				if (i == 0)
					path.moveTo(x, y);
				else
					path.lineTo(x, y);
			}
			g.draw(path);
		}
		g.setColor(CapillaryIntakes.ROI_COLOR);
		g.setStroke(new BasicStroke(2));
		for (int i = 1; i < chosen.length; i++)
			if (chosen[i] && Double.isFinite(bottoms[i])) {
				double bottom = top + height * (bottoms[i] - crop[0]) / (crop[1] - crop[0]);
				double tip = bottom - height * amplitudes[i] / (crop[1] - crop[0]);
				g.draw(new java.awt.geom.Line2D.Double(xOf(i), bottom, xOf(i), tip));
			}
		g.dispose();
	}
}
