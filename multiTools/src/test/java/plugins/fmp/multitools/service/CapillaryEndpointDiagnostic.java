package plugins.fmp.multitools.service;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * CODEX Optional visual diagnostics only: annotations never enter the detector.
 */
final class CapillaryEndpointDiagnostic {
	static void export(File output, String root, File jpeg, Map<String, double[]> truth, Map<String, double[]> raw,
			List<CapillaryLengthResult.Measure> measures) throws Exception {
		String name = root.replaceAll("[^a-zA-Z0-9.-]", "_");
		File dir = new File(output, name);
		Files.createDirectories(dir.toPath());
		BufferedImage source = ImageIO.read(jpeg);
		List<String> rows = new ArrayList<>();
		rows.add("id\tend\trawErrorPx\tfinalErrorPx\tchangePx\timage");
		for (CapillaryLengthResult.Measure m : measures) {
			String id = m.getCapillary().getKymographName();
			double[] gt = truth.get(id), initial = raw.get(id);
			double[] fin = { m.getDetectedStart().getX(), m.getDetectedStart().getY(), m.getDetectedEnd().getX(),
					m.getDetectedEnd().getY() };
			for (int end = 0; end < 2; end++) {
				int k = end * 2;
				double re = Point2D.distance(initial[k], initial[k + 1], gt[k], gt[k + 1]);
				double fe = Point2D.distance(fin[k], fin[k + 1], gt[k], gt[k + 1]);
				String file = id.replaceAll("[^a-zA-Z0-9]", "_") + "-" + (end == 0 ? "top" : "bottom") + ".png";
				rows.add(String.format(Locale.ROOT, "%s\t%s\t%.4f\t%.4f\t%.4f\t%s", id, end == 0 ? "top" : "bottom", re,
						fe, fe - re, file));
				BufferedImage canvas = new BufferedImage(1000, 520, BufferedImage.TYPE_INT_RGB);
				Graphics2D g = canvas.createGraphics();
				g.setColor(Color.WHITE);
				g.fillRect(0, 0, 1000, 520);
				g.setFont(new Font("SansSerif", Font.PLAIN, 16));
				g.setColor(Color.BLACK);
				g.drawString(id + " " + (end == 0 ? "top" : "bottom")
						+ String.format(Locale.ROOT, "   raw %.2f px / final %.2f px", re, fe), 15, 23);
				g.drawString("Original (8x)", 15, 48);
				g.drawString("Ground truth: magenta / raw: orange / final: blue", 410, 48);
				int x0 = (int) Math.floor(gt[k]) - 24, y0 = (int) Math.floor(gt[k + 1]) - 24;
				for (int panel = 0; panel < 2; panel++) {
					int left = 15 + panel * 400;
					for (int y = 0; y < 48; y++)
						for (int x = 0; x < 48; x++) {
							int sx = x0 + x, sy = y0 + y;
							g.setColor(sx >= 0 && sy >= 0 && sx < source.getWidth() && sy < source.getHeight()
									? new Color(source.getRGB(sx, sy))
									: Color.GRAY);
							g.fillRect(left + x * 8, 60 + y * 8, 8, 8);
						}
					if (panel == 1) {
						Shape clip = g.getClip();
						g.clipRect(left, 60, 384, 384);
						double[][] lines = { initial, fin, gt };
						Color[] colors = { new Color(240, 135, 0), new Color(0, 100, 255), Color.MAGENTA };
						for (int j = 0; j < 3; j++) {
							double[] p = lines[j];
							g.setColor(colors[j]);
							g.setStroke(new BasicStroke(1.5f));
							g.draw(new Line2D.Double(left + (p[0] - x0 + .5) * 8, 60 + (p[1] - y0 + .5) * 8,
									left + (p[2] - x0 + .5) * 8, 60 + (p[3] - y0 + .5) * 8));
							double cx = left + (p[k] - x0 + .5) * 8, cy = 60 + (p[k + 1] - y0 + .5) * 8;
							g.draw(new Ellipse2D.Double(cx - 4, cy - 4, 8, 8));
						}
						g.setClip(clip);
					}
				}
				// Unsmoothed luminance along the annotated shaft: plot reference only.
				double dx = gt[2] - gt[0], dy = gt[3] - gt[1], len = Math.hypot(dx, dy);
				dx /= len;
				dy /= len;
				g.setColor(Color.BLACK);
				g.drawString("Intensity (0-255)", 810, 70);
				g.drawString("Along shaft (px)", 810, 435);
				g.drawLine(830, 90, 830, 400);
				g.drawLine(830, 400, 980, 400);
				int lastX = 0, lastY = 0;
				for (int t = -20; t <= 20; t++) {
					int x = (int) Math.round(gt[k] + t * dx), y = (int) Math.round(gt[k + 1] + t * dy);
					if (x < 0 || y < 0 || x >= source.getWidth() || y >= source.getHeight())
						continue;
					int rgb = source.getRGB(x, y);
					double value = .299 * ((rgb >> 16) & 255) + .587 * ((rgb >> 8) & 255) + .114 * (rgb & 255);
					int px = 830 + (t + 20) * 150 / 40, py = 400 - (int) (value * 310 / 255);
					g.setColor(Color.DARK_GRAY);
					if (t > -20)
						g.drawLine(lastX, lastY, px, py);
					lastX = px;
					lastY = py;
				}
				g.setColor(Color.MAGENTA);
				g.drawLine(905, 90, 905, 400);
				g.setColor(Color.BLACK);
				g.drawString("-20     0     +20", 820, 420);
				g.setFont(new Font("SansSerif", Font.PLAIN, 11));
				g.drawString(jpeg.toString(), 15, 465);
				g.drawString(
						"Pixel centres; unsmoothed nearest-neighbour enlargement. Profile zero = annotated endpoint; positive = down shaft.",
						15, 487);
				g.dispose();
				ImageIO.write(canvas, "png", new File(dir, file));
			}
		}
		Files.write(new File(dir, "index.tsv").toPath(), rows, StandardCharsets.UTF_8);
		System.out.println("DIAGNOSTIC|" + dir);
	}
}
