package plugins.fmp.multitools.service.tracking;

import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.junit.Assume;
import org.junit.Test;

/* CODEX */
public class TipPatchRealDataTest {
	@Test
	public void inspectSavedAnchor() throws Exception {
		String path = System.getProperty("tip.recording");
		Assume.assumeTrue(path != null);
		File grabs = new File(path, "grabs");
		File[] images = grabs.listFiles((d, n) -> n.toLowerCase().endsWith(".jpg"));
		Arrays.sort(images, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
		String row = Files.readAllLines(new File(grabs, "results/CapillaryPhaseGeometry.csv").toPath()).stream()
				.filter(s -> s.startsWith("BLUE;bGluZTBM;0;")).findFirst().get();
		String[] parts = row.split(";");
		Point2D anchor = new Point2D.Double(Double.parseDouble(parts[3]), Double.parseDouble(parts[4]));
		BufferedImage ref = ImageIO.read(images[0]);
		double[] reference = channel(ref);
		TipPatchTracker tracker = new TipPatchTracker();
		TipPatchTracker.AcceptedPositions history = new TipPatchTracker.AcceptedPositions(
				new java.awt.geom.Line2D.Double(anchor, new Point2D.Double(anchor.getX(), 480)));
		double previous = anchor.getY();
		for (int t = 1; t < images.length; t++) {
			TipPatchTracker.Match m = tracker.track(anchor, reference, channel(ImageIO.read(images[t])), ref.getWidth(),
					ref.getHeight());
			double y = history.update(m, m).getY1();
			if (Math.abs(y - previous) > 3.)
				System.out.println(
						"REPLAY JUMP t=" + t + " previous=" + previous + " y=" + y + " accepted=" + m.reliable);
			if (t == 167)
				System.out.println("REPLAY T167 y=" + y + " accepted=" + m.reliable);
			previous = y;
		}
		for (int t : new int[] { 41, 42, 100, 166, 167, 168, 279 }) {
			TipPatchTracker.Match m = tracker.track(anchor, reference, channel(ImageIO.read(images[t])), ref.getWidth(),
					ref.getHeight());
			System.out.println("TIPCHECK t=" + t + " accepted=" + m.reliable + " y=" + m.position.getY() + " score="
					+ m.correlation + " roundtrip=" + m.roundTripError);
		}
	}

	private double[] channel(BufferedImage image) {
		double[] out = new double[image.getWidth() * image.getHeight()];
		for (int y = 0; y < image.getHeight(); y++)
			for (int x = 0; x < image.getWidth(); x++)
				out[x + y * image.getWidth()] = (image.getRGB(x, y) >> 16) & 255;
		return out;
	}
}
