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
	private static final BasicStroke CURSOR_STROKE = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
			10, new float[] { 4, 4 }, 0);
	private final IntakeChartHost host;
	private BarColor intakeColor = BarColor.YELLOW;

	enum BarColor {
		YELLOW("Yellow", Color.YELLOW),
		BLUE("Blue", CapillaryIntakes.ROI_COLOR);

		private final String label;
		private final Color color;

		BarColor(String label, Color color) {
			this.label = label;
			this.color = color;
		}

		Color color() {
			return color;
		}

		@Override
		public String toString() {
			return label;
		}
	}

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

	void setIntakeColor(BarColor color) {
		intakeColor = color;
		repaint();
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
		return cropBounds(host.placementPreview(), host.candidateAmplitudes());
	}

	private int[] cropBounds(IntakePlacementPreview placement, double[] amplitudes) {
		CorrelationGulpPreview preview = preview();
		int height = preview.image.getHeight();
		int min = height - 1;
		int max = 0;
		for (double v : preview.raw)
			if (Double.isFinite(v)) {
				min = Math.min(min, (int) v - 8);
				max = Math.max(max, (int) v + 9);
			}
		if (placement.shiftedTopraw != null)
			for (double v : placement.shiftedTopraw)
				if (Double.isFinite(v)) {
					min = Math.min(min, (int) v - 2);
					max = Math.max(max, (int) Math.ceil(v) + 3);
				}
		double[] bottoms = placement.bottoms;
		for (int i = 1; i < amplitudes.length; i++)
			if (amplitudes[i] > 0 && Double.isFinite(bottoms[i])) {
				min = Math.min(min, (int) Math.floor(bottoms[i] - amplitudes[i]) - 2);
				max = Math.max(max, (int) Math.ceil(bottoms[i]) + 3);
				if (placement.previousBottoms != null && Double.isFinite(placement.previousBottoms[i])) {
					min = Math.min(min, (int) Math.floor(placement.previousBottoms[i] - amplitudes[i]) - 2);
					max = Math.max(max, (int) Math.ceil(placement.previousBottoms[i]) + 3);
				}
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
		IntakePlacementPreview placement = host.placementPreview();
		double[] amplitudes = host.candidateAmplitudes();
		int[] crop = cropBounds(placement, amplitudes);
		int cropHeight = Math.max(1, crop[1] - crop[0]) * COLUMN_PX;
		int width = imageWidth();
		g.setColor(Color.DARK_GRAY);
		String legend;
		if (placement.shiftedTopraw != null)
			legend = "Orange = topraw  |  Green = shifted topraw  |  " + intakeColor
					+ " = intake  |  White = previous bar position";
		else if (placement.previousBottoms != null)
			legend = "Offset preview   White = previous position  |  " + intakeColor
					+ " = preview  |  Grey = evaporation + cumulative intake";
		else
			legend = intakeColor + " = intake  |  Orange dot = no local shift  |  Grey = evaporation + cumulative intake";
		g.drawString(legend, LEFT, 18);
		g.drawImage(preview.image, LEFT, 25, LEFT + width, 25 + cropHeight, 0, crop[0], preview.image.getWidth(),
				crop[1], null);
		drawIntakeOverlay(g, placement, amplitudes, crop, 25, cropHeight);
		Graphics2D cursor = (Graphics2D) g.create();
		cursor.setColor(Color.YELLOW);
		cursor.setStroke(CURSOR_STROKE);
		int selectedX = xOf(host.selectedFrame());
		cursor.drawLine(selectedX, 25, selectedX, 25 + cropHeight);
		cursor.dispose();
		g.setColor(Color.DARK_GRAY);
		g.drawString(
				"Time axis: at least 4 pixels per column; scroll horizontally for longer kymographs. Grey columns: missing level or image border.",
				LEFT, getHeight() - 7);
	}

	private void drawIntakeOverlay(Graphics graphics, IntakePlacementPreview placement, double[] amplitudes,
			int[] crop, int top, int height) {
		CorrelationGulpPreview preview = preview();
		double[] bottoms = placement.bottoms;
		Graphics2D g = (Graphics2D) graphics.create();
		g.clipRect(LEFT, top, imageWidth(), height);
		g.setColor(IntakeEvaporationOverlay.COLOR);
		g.setStroke(new BasicStroke(1));
		List<List<java.awt.geom.Point2D>> paths = preview.hasEvaporationReference
				? IntakeEvaporationOverlay.paths(amplitudes, preview.raw, preview.evaporation,
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
		double scale = (double) height / (crop[1] - crop[0]);
		if (placement.shiftedTopraw != null) {
			g.setColor(new Color(180, 90, 0));
			drawTopraw(g, preview.raw, crop[0], top, scale);
			g.setColor(new Color(0, 120, 35));
			drawTopraw(g, placement.shiftedTopraw, crop[0], top, scale);
		}
		if (placement.previousBottoms != null) {
			g.setColor(Color.WHITE);
			for (int i = 1; i < amplitudes.length; i++)
				if (Double.isFinite(placement.previousBottoms[i])) {
					int x = xOf(i) - 2;
					double bottom = top + scale * (placement.previousBottoms[i] - crop[0]);
					g.draw(new java.awt.geom.Line2D.Double(x, bottom, x, bottom - scale * amplitudes[i]));
				}
		}
		g.setColor(intakeColor.color);
		g.setStroke(new BasicStroke(2));
		for (int i = 1; i < amplitudes.length; i++)
			if (amplitudes[i] > 0 && Double.isFinite(bottoms[i])) {
				double bottom = top + scale * (bottoms[i] - crop[0]);
				double tip = bottom - scale * amplitudes[i];
				g.draw(new java.awt.geom.Line2D.Double(xOf(i), bottom, xOf(i), tip));
				if (placement.uncertain[i]) {
					g.setColor(new Color(190, 100, 0));
					g.fillOval(xOf(i) - 2, (int) Math.round(tip) - 6, 5, 5);
					g.setColor(intakeColor.color);
				}
			}
		g.dispose();
	}

	private void drawTopraw(Graphics2D g, double[] levels, int firstRow, int top, double scale) {
		for (int i = 1; i < levels.length; i++)
			if (Double.isFinite(levels[i - 1]) && Double.isFinite(levels[i]))
				g.draw(new java.awt.geom.Line2D.Double(xOf(i - 1), top + scale * (levels[i - 1] - firstRow),
						xOf(i), top + scale * (levels[i] - firstRow)));
	}
}
