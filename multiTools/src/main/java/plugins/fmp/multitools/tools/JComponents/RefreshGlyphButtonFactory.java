package plugins.fmp.multitools.tools.JComponents;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.UIManager;

/**
 * Icon-only refresh control (circular arrows) for table toolbars. Centralises
 * painting and sizing so plugin dialogs stay consistent.
 */
public final class RefreshGlyphButtonFactory {

	private static final int DEFAULT_ICON_SIZE = 16;
	private static final Dimension DEFAULT_BUTTON_SIZE = new Dimension(26, 26);
	private static final Insets DEFAULT_MARGIN = new Insets(1, 1, 1, 1);

	private RefreshGlyphButtonFactory() {
	}

	public static JButton createTableRefreshButton(String toolTipText) {
		JButton button = new JButton(createRefreshIcon(DEFAULT_ICON_SIZE));
		button.setToolTipText(toolTipText);
		button.setMargin(DEFAULT_MARGIN);
		button.setFocusable(false);
		button.setPreferredSize(DEFAULT_BUTTON_SIZE);
		return button;
	}

	public static Icon createRefreshIcon(int size) {
		return new Icon() {
			@Override
			public void paintIcon(Component c, Graphics g, int x, int y) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.translate(x, y);
				Color color = resolveIconColor(c);
				g2.setColor(color);
				g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				int pad = 2;
				int w = size - 2 * pad;
				int h = size - 2 * pad;
				int arcExtent = 210;
				g2.drawArc(pad, pad, w, h, 35, arcExtent);
				drawArrowHead(g2, pad, pad, w, h, 35 + arcExtent);
				g2.drawArc(pad, pad, w, h, 215, arcExtent);
				drawArrowHead(g2, pad, pad, w, h, 215 + arcExtent);
				g2.dispose();
			}

			@Override
			public int getIconWidth() {
				return size;
			}

			@Override
			public int getIconHeight() {
				return size;
			}
		};
	}

	private static Color resolveIconColor(Component c) {
		if (!c.isEnabled()) {
			Color disabled = UIManager.getColor("Label.disabledForeground");
			return disabled != null ? disabled : Color.GRAY;
		}
		if (c instanceof AbstractButton) {
			AbstractButton b = (AbstractButton) c;
			if (b.getModel().isPressed()) {
				return c.getForeground().darker();
			}
		}
		return c.getForeground();
	}

	private static void drawArrowHead(Graphics2D g2, int pad, int padY, int w, int h, double angleDeg) {
		double a = Math.toRadians(angleDeg);
		double cx = pad + w / 2.0;
		double cy = padY + h / 2.0;
		double px = cx + (w / 2.0) * Math.cos(a);
		double py = cy + (h / 2.0) * Math.sin(a);
		int len = 4;
		Path2D arrow = new Path2D.Double();
		arrow.moveTo(px, py);
		arrow.lineTo(px - len * Math.cos(a - 0.55), py - len * Math.sin(a - 0.55));
		arrow.moveTo(px, py);
		arrow.lineTo(px - len * Math.cos(a + 0.55), py - len * Math.sin(a + 0.55));
		g2.draw(arrow);
	}
}
