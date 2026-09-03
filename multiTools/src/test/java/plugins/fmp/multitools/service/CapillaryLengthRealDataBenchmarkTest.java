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

    /** Exploratory diagnostics only: annotations are never fed into detection. */
    @Test public void diagnoseCorrectionFeatures() throws Exception {
        String roots = System.getProperty("capillary.benchmark.roots");
        Assume.assumeTrue("real-data roots not supplied", roots != null && !roots.trim().isEmpty());
        for (String root : roots.split("::")) {
            File directory = new File(root);
            Map<String, double[]> rois = readCoordinates(new File(directory, "CapillariesDescription.csv"), false);
            Map<String, double[]> truth = readCoordinates(groundTruthFile(directory), true);
            ImageData image = readImage(firstJpeg(directory.getParentFile()));
            CapillaryLengthDetectorOptions options = new CapillaryLengthDetectorOptions();
            options.tipInsetHalfWidthScale = 0.;
            double[] sums = new double[8]; int count = 0;
            for (Map.Entry<String, double[]> entry : rois.entrySet()) {
                double[] gt = truth.get(entry.getKey());
                if (gt == null) continue;
                double[] p = entry.getValue();
                ArrayList<int[]> axis = line(p);
                Geometry g = CapillaryLengthDetector.estimateGeometry(axis, image, options);
                double dx = p[2]-p[0], dy = p[3]-p[1], length = Math.hypot(dx,dy);
                dx /= length; dy /= length;
                boolean direct = Math.hypot(p[0]-gt[0],p[1]-gt[1])+Math.hypot(p[2]-gt[2],p[3]-gt[3])
                        <= Math.hypot(p[0]-gt[2],p[1]-gt[3])+Math.hypot(p[2]-gt[0],p[3]-gt[1]);
                for (int end = 0; end < 2; end++) {
                    int origin = end == 0 ? 0 : axis.size()-1, direction = end == 0 ? 1 : -1;
                    CapillaryLengthDetector.TipFind tip = CapillaryLengthDetector.findTip(axis,image,g,origin,direction,options);
                    if (!tip.found) continue;
                    int gi = direct ? 2*end : 2*(1-end);
                    double overhang = direction*((gt[gi]-p[2*end])*dx+(gt[gi+1]-p[2*end+1])*dy);
                    double[] scores = new double[Math.min(30,axis.size())];
                    for (int k=0;k<scores.length;k++) {
                        int i=origin+direction*k, i0=Math.max(0,i-8), i1=Math.min(axis.size()-1,i+8);
                        double tx=axis.get(i1)[0]-axis.get(i0)[0], ty=axis.get(i1)[1]-axis.get(i0)[1];
                        double norm=Math.hypot(tx,ty);
                        scores[k]=CapillaryLengthDetector.pairedWallScore(image,axis.get(i)[0],axis.get(i)[1],
                                new double[]{-ty/norm,tx/norm},g,options);
                    }
                    double outside=0, inside=0, sharpness=0;
                    for(int k=0;k<5;k++) outside+=scores[k]/5.;
                    for(int k=15;k<25;k++) inside+=scores[k]/10.;
                    for(int k=1;k<25;k++) sharpness=Math.max(sharpness,scores[k]-scores[k-1]);
                    double[] values={2*g.halfWidth,overhang,direction*(tip.axisFrac-origin),tip.atRoiEnd?1:0,
                            tip.confidence,outside,inside,sharpness};
                    for(int j=0;j<sums.length;j++) sums[j]+=values[j];
                    count++;
                }
            }
            System.out.print("FEATURES|"+root+"|"+count);
            for(double sum:sums) System.out.printf(Locale.US,"|%.6f",sum/count);
            System.out.println();
        }
    }

    @Test
    public void benchmarkFinalPhaseEndpoints() throws Exception {
        String roots = System.getProperty("capillary.benchmark.roots");
        Assume.assumeTrue("real-data roots not supplied", roots != null && !roots.trim().isEmpty());
        icy.preferences.IcyPreferences.init();
        for (double scale : new double[] { 0., 1.25 }) {
            List<Double> rawErrors = new ArrayList<Double>();
            List<Double> finalErrors = new ArrayList<Double>();
            List<Double> lengthErrors = new ArrayList<Double>();
            for (String root : roots.split("::")) {
                File directory = new File(root);
                Map<String, double[]> green = readCoordinates(new File(directory, "CapillariesDescription.csv"), false);
                Map<String, double[]> truth = readCoordinates(groundTruthFile(directory), true);
                ImageData image = readImage(firstJpeg(directory.getParentFile()));
                CapillaryLengthDetectorOptions options = new CapillaryLengthDetectorOptions();
                options.tipInsetHalfWidthScale = scale;
                CapillaryLengthResult result = new CapillaryLengthResult();
                CapillaryLengthDetector detector = new CapillaryLengthDetector();
                for (Map.Entry<String, double[]> entry : green.entrySet()) {
                    if (!truth.containsKey(entry.getKey())) continue;
                    double[] p = entry.getValue().clone();
                    // Test-only sensitivity check; source ROIs are never modified.
                    double extension = Double.parseDouble(System.getProperty("capillary.benchmark.roiExtensionPixels", "0"));
                    double axisLength = Math.hypot(p[2]-p[0],p[3]-p[1]);
                    double ex = extension*(p[2]-p[0])/axisLength, ey = extension*(p[3]-p[1])/axisLength;
                    p[0]-=ex; p[1]-=ey; p[2]+=ex; p[3]+=ey;
                    plugins.fmp.multitools.experiment.capillary.Capillary cap = new plugins.fmp.multitools.experiment.capillary.Capillary();
                    cap.setRoi(new plugins.kernel.roi.roi2d.ROI2DLine(new java.awt.geom.Line2D.Double(p[0], p[1], p[2], p[3])));
                    CapillaryLengthResult.Measure m = detector.measureOneCapillary(cap, image, options);
                    result.addMeasure(m);
                    if (m.getStatus().isUsable()) rawErrors.add(endpointError(m.getDetectedStart(), m.getDetectedEnd(), truth.get(entry.getKey())));
                    // Retain the annotation key independently of display naming.
                    cap.setKymographName(entry.getKey());
                }
                CapillaryLengthDetector.validate(result, image.width, options);
                CapillaryLengthDetector.apply(result, 0);
                List<Double> experimentErrors = new ArrayList<Double>();
                for (CapillaryLengthResult.Measure m : result.getMeasures()) {
                    if (!m.isSelected()) continue;
                    double[] gt = truth.get(m.getCapillary().getKymographName());
                    java.awt.geom.Line2D blue = m.getCapillary().getPhaseGeometry().getBlueAt(0);
                    assertTrue("accepted detection must have display geometry", blue != null);
                    double error = endpointError(blue.getP1(), blue.getP2(), gt);
                    finalErrors.add(error); experimentErrors.add(error);
                    lengthErrors.add(Math.abs(blue.getP1().distance(blue.getP2()) - Math.hypot(gt[2]-gt[0], gt[3]-gt[1])));
                }
                System.out.printf(Locale.US, "FINAL scale=%.2f %s tipMAE=%.3f n=%d%n", scale, root, mean(experimentErrors), experimentErrors.size());
            }
            System.out.printf(Locale.US, "FINAL ALL scale=%.2f rawTip=%.3f finalTip=%.3f finalLength=%.3f n=%d%n",
                    scale, mean(rawErrors), mean(finalErrors), mean(lengthErrors), finalErrors.size());
        }
    }

    private static double endpointError(java.awt.geom.Point2D a, java.awt.geom.Point2D b, double[] gt) {
        return .5 * Math.min(a.distance(gt[0], gt[1]) + b.distance(gt[2], gt[3]),
                a.distance(gt[2], gt[3]) + b.distance(gt[0], gt[1]));
    }

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

	static File groundTruthFile(File results) {
		// Explicit opt-in only: never silently treat operational detections as truth.
		String override = System.getProperty("capillary.benchmark.groundTruthName");
		if (override != null && !override.trim().isEmpty()) {
			File file = new File(results, override);
			if (!file.isFile()) throw new IllegalArgumentException("Missing ground truth: " + file);
			return file;
		}
		String[] names = { plugins.fmp.multitools.experiment.capillaries.CapillariesPersistence.GROUND_TRUTH_CSV,
				"CapillariesDescription_groundtruth.csv", "CapillariesDescription_ground_truth.csv",
				"CapillariesDescription - Copy.csv" };
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
