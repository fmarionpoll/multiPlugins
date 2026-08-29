package plugins.fmp.multitools.service;

import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.Assume;
import org.junit.Test;

import plugins.fmp.multitools.service.CapillaryLengthDetector.AxisMeasure;
import plugins.fmp.multitools.service.CapillaryLengthDetector.ImageData;
import plugins.fmp.multitools.service.CapillaryLengthDetector.Geometry;

/**
 * Optional benchmark against manually placed image-0 endpoints. Enable with
 * {@code -Dcapillary.benchmark.roots=resultsDir1::resultsDir2}. It is deliberately
 * data-independent during normal CI runs.
 */
public class CapillaryLengthRealDataBenchmarkTest {

	@Test
	public void benchmarkImageZeroGroundTruth() throws Exception {
		String property = System.getProperty("capillary.benchmark.roots");
		Assume.assumeTrue("real-data roots not supplied", property != null && !property.trim().isEmpty());
		CapillaryLengthDetectorOptions options = new CapillaryLengthDetectorOptions();
		List<Double> allTips = new ArrayList<Double>();
		List<Double> allLengths = new ArrayList<Double>();
		List<double[]> allDetections = new ArrayList<double[]>();
		int totalFound = 0;
		int totalCaps = 0;

		for (String value : property.split("::")) {
			File results = new File(value);
			Map<String, double[]> rois = readCoordinates(new File(results, "CapillariesDescription.csv"), false);
			Map<String, double[]> truth = readCoordinates(groundTruthFile(results), true);
			File imageFile = firstJpeg(results.getParentFile());
			ImageData image = readImage(imageFile);
			List<Double> tips = new ArrayList<Double>();
			List<Double> lengths = new ArrayList<Double>();
			int found = 0;

			for (Map.Entry<String, double[]> entry : rois.entrySet()) {
				double[] gt = truth.get(entry.getKey());
				if (gt == null)
					continue;
				totalCaps++;
				ArrayList<int[]> axis = line(entry.getValue());
				Geometry geometry = CapillaryLengthDetector.estimateGeometry(axis, image, options);
				AxisMeasure measured = CapillaryLengthDetector.locateAlongAxis(axis, image, options);
				if (!measured.found)
					continue;
				found++;
				totalFound++;
				double[] a = point(axis, measured.startFrac);
				double[] b = point(axis, measured.endFrac);
				double direct = distance(a, gt, 0) + distance(b, gt, 2);
				double reverse = distance(a, gt, 2) + distance(b, gt, 0);
				double tip = 0.5 * Math.min(direct, reverse);
				double length = Math.abs(distance(a, b) - distance(gt, 0, gt, 2));
				tips.add(tip);
				lengths.add(length);
				allTips.add(tip);
				allLengths.add(length);
				allDetections.add(new double[] { measured.startFrac, measured.endFrac, gt[0], gt[1], gt[2], gt[3],
						axis.get(0)[0], axis.get(0)[1], axis.get(axis.size() - 1)[0], axis.get(axis.size() - 1)[1],
						geometry.halfWidth });
			}
			System.out.printf(Locale.US, "BENCHMARK %s found=%d/%d tipMAE=%.3f lengthMAE=%.3f%n",
					results.getParentFile().getParentFile().getName(), found, rois.size(), mean(tips), mean(lengths));
		}
		System.out.printf(Locale.US, "BENCHMARK ALL found=%d/%d tipMAE=%.3f tipP90=%.3f lengthMAE=%.3f%n",
				totalFound, totalCaps, mean(allTips), percentile(allTips, .9), mean(allLengths));
		for (int inset = 0; inset <= 8; inset++)
			System.out.printf(Locale.US, "BENCHMARK INSET %d tipMAE=%.3f lengthMAE=%.3f%n", inset,
					insetErrors(allDetections, inset, true), insetErrors(allDetections, inset, false));
		for (double scale = .5; scale <= 1.25; scale += .25)
			System.out.printf(Locale.US, "BENCHMARK WIDTH %.2f tipMAE=%.3f lengthMAE=%.3f%n", scale,
					widthInsetErrors(allDetections, scale, true), widthInsetErrors(allDetections, scale, false));
		assertTrue("at least one annotated capillary must be measured", totalFound > 0);
	}

	private static Map<String, double[]> readCoordinates(File file, boolean groundTruth) throws Exception {
		List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
		Map<String, double[]> result = new LinkedHashMap<String, double[]>();
		int header = -1;
		boolean named = false;
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).startsWith("cap_prefix;")) {
				header = i;
				named = Arrays.asList(lines.get(i).split(";", -1)).contains("cap_length_x1");
				break;
			}
		}
		if (header < 0)
			throw new IllegalArgumentException("No capillary table in " + file);
		for (int i = header + 1; i < lines.size() && !lines.get(i).startsWith("#"); i++) {
			String[] fields = lines.get(i).split(";", -1);
			if (fields.length < 18)
				continue;
			int first = groundTruth && named ? fields.length - 4 : 14;
			result.put(fields[0], new double[] { number(fields[first]), number(fields[first + 1]),
					number(fields[first + 2]), number(fields[first + 3]) });
		}
		return result;
	}

	private static File groundTruthFile(File results) {
		String[] names = { "CapillariesDescription - Copy.csv", "CapillariesDescription_groundtruth.csv",
				"CapillariesDescription_ground_truth.csv" };
		for (String name : names) {
			File candidate = new File(results, name);
			if (candidate.isFile())
				return candidate;
		}
		throw new IllegalArgumentException("No ground-truth CSV in " + results);
	}

	private static double number(String value) {
		return Double.parseDouble(value.trim());
	}

	private static File firstJpeg(File grabs) {
		File[] files = grabs.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jpg")
				|| name.toLowerCase(Locale.ROOT).endsWith(".jpeg"));
		if (files == null || files.length == 0)
			throw new IllegalArgumentException("No JPEG in " + grabs);
		Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
		return files[0];
	}

	private static ImageData readImage(File file) throws Exception {
		BufferedImage source = ImageIO.read(file);
		int width = source.getWidth(), height = source.getHeight();
		double[][] channels = new double[3][width * height];
		for (int y = 0; y < height; y++)
			for (int x = 0; x < width; x++) {
				int rgb = source.getRGB(x, y), p = x + y * width;
				channels[0][p] = (rgb >> 16) & 255;
				channels[1][p] = (rgb >> 8) & 255;
				channels[2][p] = rgb & 255;
			}
		return new ImageData(width, height, channels);
	}

	private static ArrayList<int[]> line(double[] p) {
		int x0 = (int) Math.round(p[0]), y0 = (int) Math.round(p[1]);
		int x1 = (int) Math.round(p[2]), y1 = (int) Math.round(p[3]);
		int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
		ArrayList<int[]> axis = new ArrayList<int[]>();
		for (int i = 0; i <= n; i++) {
			double t = n == 0 ? 0. : i / (double) n;
			axis.add(new int[] { (int) Math.round(x0 + t * (x1 - x0)), (int) Math.round(y0 + t * (y1 - y0)) });
		}
		return axis;
	}

	private static double[] point(ArrayList<int[]> axis, double index) {
		int lo = Math.max(0, Math.min(axis.size() - 1, (int) Math.floor(index)));
		int hi = Math.min(axis.size() - 1, lo + 1);
		double f = index - lo;
		return new double[] { axis.get(lo)[0] + f * (axis.get(hi)[0] - axis.get(lo)[0]),
				axis.get(lo)[1] + f * (axis.get(hi)[1] - axis.get(lo)[1]) };
	}

	private static double distance(double[] p, double[] q) { return Math.hypot(p[0] - q[0], p[1] - q[1]); }
	private static double distance(double[] p, double[] q, int q0) { return Math.hypot(p[0] - q[q0], p[1] - q[q0 + 1]); }
	private static double distance(double[] p, int p0, double[] q, int q0) { return Math.hypot(p[p0] - q[q0], p[p0 + 1] - q[q0 + 1]); }
	private static double mean(List<Double> values) {
		double sum = 0.; for (Double value : values) sum += value.doubleValue();
		return values.isEmpty() ? Double.NaN : sum / values.size();
	}
	private static double percentile(List<Double> values, double fraction) {
		if (values.isEmpty()) return Double.NaN;
		double[] sorted = new double[values.size()];
		for (int i = 0; i < sorted.length; i++) sorted[i] = values.get(i).doubleValue();
		Arrays.sort(sorted);
		double position = fraction * (sorted.length - 1);
		int lo = (int) Math.floor(position), hi = (int) Math.ceil(position);
		return sorted[lo] + (position - lo) * (sorted[hi] - sorted[lo]);
	}

	private static double insetErrors(List<double[]> detections, double inset, boolean tips) {
		double sum = 0.;
		for (double[] d : detections) {
			double dx = d[8] - d[6], dy = d[9] - d[7], n = Math.hypot(dx, dy);
			dx /= n; dy /= n;
			double[] a = { d[6] + (d[0] + inset) * dx, d[7] + (d[0] + inset) * dy };
			double[] b = { d[6] + (d[1] - inset) * dx, d[7] + (d[1] - inset) * dy };
			double direct = distance(a, d, 2) + distance(b, d, 4);
			double reverse = distance(a, d, 4) + distance(b, d, 2);
			sum += tips ? 0.5 * Math.min(direct, reverse)
					: Math.abs(distance(a, b) - distance(d, 2, d, 4));
		}
		return sum / detections.size();
	}

	private static double widthInsetErrors(List<double[]> detections, double scale, boolean tips) {
		double sum = 0.;
		for (double[] d : detections) {
			double inset = Math.max(2., Math.min(6., scale * d[10]));
			double dx = d[8] - d[6], dy = d[9] - d[7], n = Math.hypot(dx, dy);
			dx /= n; dy /= n;
			double[] a = { d[6] + (d[0] + inset) * dx, d[7] + (d[0] + inset) * dy };
			double[] b = { d[6] + (d[1] - inset) * dx, d[7] + (d[1] - inset) * dy };
			double direct = distance(a, d, 2) + distance(b, d, 4);
			double reverse = distance(a, d, 4) + distance(b, d, 2);
			sum += tips ? 0.5 * Math.min(direct, reverse)
					: Math.abs(distance(a, b) - distance(d, 2, d, 4));
		}
		return sum / detections.size();
	}
}
