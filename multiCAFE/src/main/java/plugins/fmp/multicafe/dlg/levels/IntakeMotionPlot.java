package plugins.fmp.multicafe.dlg.levels;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import plugins.fmp.multitools.service.CorrelationGulpPreview;
import plugins.fmp.multitools.service.IntakeDiagnostics;

final class IntakeMotionPlot extends JPanel {
	private static final long serialVersionUID = 1L;
	private final IntakeChartHost host;
	private double low, high;
	private boolean dragging;
	private int editStart = -1;

	IntakeMotionPlot(IntakeChartHost host) {
		this.host = host;
		setBackground(Color.WHITE);
		setPreferredSize(new Dimension(1050, 300));
		MouseAdapter mouse = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				CorrelationGulpPreview preview = host.preview();
				if (preview == null)
					return;
				if (host.addGulpArmed() && SwingUtilities.isLeftMouseButton(e) && e.getX() >= 65
						&& e.getX() <= getWidth() - 25) {
					editStart = frameAt(e.getX());
					return;
				}
				dragging = host.thresholdEditable() && Math.abs(e.getY() - y(host.thresholdValue())) < 12;
				if (!dragging) {
					double time = preview.minutes[0] + (e.getX() - 65.) / Math.max(1, getWidth() - 90) * duration();
					host.selectFrame(closest(time));
				}
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				if (host.preview() != null && dragging && host.thresholdEditable())
					host.dragThreshold(Math.max(0, Math.min(10000,
							high - (e.getY() - 35.) / Math.max(1, getHeight() - 85) * (high - low))));
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				if (editStart >= 0 && host.preview() != null) {
					int end = frameAt(e.getX()), start = editStart;
					editStart = -1;
					host.editGulpRange(Math.min(start, end), Math.max(start, end));
				}
				editStart = -1;
				dragging = false;
				repaint();
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
	}

	private CorrelationGulpPreview preview() {
		return host.preview();
	}

	private double duration() {
		CorrelationGulpPreview preview = preview();
		return Math.max(1e-6, preview.minutes[preview.minutes.length - 1] - preview.minutes[0]);
	}

	private int frameAt(int x) {
		return closest(preview().minutes[0] + (x - 65.) / Math.max(1, getWidth() - 90) * duration());
	}

	private int x(int i) {
		CorrelationGulpPreview preview = preview();
		return 65 + (int) ((getWidth() - 90) * (preview.minutes[i] - preview.minutes[0]) / duration());
	}

	private int y(double v) {
		return 35 + (int) ((getHeight() - 85) * (high - v) / (high - low));
	}

	private int closest(double time) {
		CorrelationGulpPreview preview = preview();
		int best = 0;
		for (int i = 1; i < preview.minutes.length; i++)
			if (Math.abs(preview.minutes[i] - time) < Math.abs(preview.minutes[best] - time))
				best = i;
		return best;
	}

	private double evaporationRate(int i) {
		CorrelationGulpPreview preview = preview();
		if (preview == null || i <= 0 || i >= preview.minutes.length || i >= preview.evaporation.length)
			return Double.NaN;
		double dt = preview.minutes[i] - preview.minutes[i - 1];
		if (!(dt > 0))
			return Double.NaN;
		double increment = preview.evaporation[i] - preview.evaporation[i - 1];
		return Double.isFinite(increment) ? increment / dt : Double.NaN;
	}

	@Override
	protected void paintComponent(Graphics graphics) {
		super.paintComponent(graphics);
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(Color.DARK_GRAY);
		g.drawString(host.persistentEpisodes()
				? "Blue: motion minus evaporation; red: detected intake bins; line: threshold setting (controls and persistence also apply)"
				: "Blue: accepted-level motion minus evaporation; grey: evaporation model (pixels/min); red: candidate intervals",
				65, 20);
		CorrelationGulpPreview preview = preview();
		if (preview == null) {
			g.dispose();
			return;
		}
		if (!dragging) {
			low = -.5;
			high = Math.max(1, host.thresholdValue() * 1.2);
			for (double v : preview.residualRate)
				if (Double.isFinite(v)) {
					low = Math.min(low, v - .1);
					high = Math.max(high, v + .2);
				}
			for (int i = 1; i < preview.raw.length; i++) {
				double rate = evaporationRate(i);
				if (Double.isFinite(rate)) {
					low = Math.min(low, rate - .1);
					high = Math.max(high, rate + .2);
				}
			}
		}
		if (host.persistentEpisodes() && host.showDiagnostics())
			for (IntakeDiagnostics.Warning warning : host.diagnostics()) {
				Color color = host.diagnosticColor(warning);
				g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 25));
				g.fillRect(x(warning.first), 35, Math.max(2, x(warning.last) - x(warning.first)), getHeight() - 85);
			}
		for (int tick = 0; tick <= 4; tick++) {
			double v = low + (high - low) * tick / 4;
			int yy = y(v);
			g.setColor(new Color(225, 225, 225));
			g.drawLine(65, yy, getWidth() - 25, yy);
			g.setColor(Color.DARK_GRAY);
			g.drawString(host.formatNumber(v), 12, yy + 4);
		}
		g.setColor(Color.GRAY);
		for (int i = 2; i < preview.raw.length; i++) {
			double previous = evaporationRate(i - 1);
			double current = evaporationRate(i);
			if (Double.isFinite(previous) && Double.isFinite(current))
				g.drawLine(x(i - 1), y(previous), x(i), y(current));
		}
		boolean[] chosen = host.candidates();
		for (int i = 1; i < preview.raw.length; i++) {
			if (!preview.motion.reliable[i] || !Double.isFinite(preview.residualRate[i])) {
				g.setColor(chosen[i] ? Color.RED : Color.GRAY);
				g.drawString("x", x(i) - 3, y(0));
				continue;
			}
			g.setColor(new Color(35, 100, 165));
			if (i > 1 && preview.motion.reliable[i - 1] && Double.isFinite(preview.residualRate[i - 1]))
				g.drawLine(x(i - 1), y(preview.residualRate[i - 1]), x(i), y(preview.residualRate[i]));
			g.setColor(chosen[i] ? Color.RED : new Color(35, 100, 165));
			g.fillOval(x(i) - 3, y(preview.residualRate[i]) - 3, 6, 6);
		}
		g.setColor(new Color(180, 30, 30));
		g.setStroke(new BasicStroke(2));
		g.drawLine(65, y(host.thresholdValue()), getWidth() - 25, y(host.thresholdValue()));
		g.drawString((host.thresholdEditable() ? "Drag threshold setting: " : "Threshold setting: ")
				+ host.formatNumber(host.thresholdValue()), 75, Math.max(35, y(host.thresholdValue()) - 6));
		g.setColor(Color.GRAY);
		g.setStroke(new BasicStroke(1));
		g.drawLine(x(host.selectedFrame()), 35, x(host.selectedFrame()), getHeight() - 50);
		for (int tick = 0; tick <= 5; tick++) {
			double t = preview.minutes[0] + duration() * tick / 5;
			g.setColor(Color.DARK_GRAY);
			g.drawString(host.formatNumber(t), Math.min(getWidth() - 60, 65 + (getWidth() - 90) * tick / 5 - 8),
					getHeight() - 28);
		}
		g.drawString("Elapsed time (minutes)", getWidth() / 2 - 65, getHeight() - 7);
		g.dispose();
	}
}
