package plugins.fmp.multitools.service;

import java.awt.geom.Point2D;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import icy.image.IcyBufferedImage;
import icy.sequence.Sequence;
import icy.system.SystemUtil;
import icy.system.thread.Processor;
import icy.type.collection.array.Array1DUtil;
import icy.type.geom.Polyline2D;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryMeasure;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.series.options.GulpThresholdMethod;
import plugins.fmp.multitools.series.options.GulpThresholdSmoothing;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.SavitzkyGolayFilter;

public class GulpDetector {

	public void detectGulps(Experiment exp, BuildSeriesOptions options) {
		exp.getSeqKymos().getSequence().beginUpdate();

		if (options.buildDerivative) {
			buildDerivatives(exp, options);
		}

		CapillaryMeasure thresholdMeasure = null;
		if (options.buildGulps) {
			thresholdMeasure = computeThresholdFromEmptyCages(exp, options);
			detectGulps(exp, options, thresholdMeasure);
		}

		exp.save_capillaries_description_and_measures();
		exp.getSeqKymos().getSequence().endUpdate();
	}

	private void buildDerivatives(Experiment exp, BuildSeriesOptions options) {
		int jitter = options.jitter;
		int firstKymo = 0;
		int lastKymo = exp.getSeqKymos().getSequence().getSizeT() - 1;
		if (options.detectSelectedKymo) {
			firstKymo = options.kymoFirst;
			lastKymo = firstKymo;
		}

		int nframes = lastKymo - firstKymo + 1;
		final Processor processor = new Processor(SystemUtil.getNumberOfCPUs());
		processor.setThreadName("build_derivatives");
		processor.setPriority(Processor.NORM_PRIORITY);
		ArrayList<Future<?>> futures = new ArrayList<Future<?>>(nframes);
		futures.clear();

		final Sequence seqAnalyzed = exp.getSeqKymos().getSequence();

		for (int tKymo = firstKymo; tKymo <= lastKymo; tKymo++) {
			String fullPath = exp.getSeqKymos().getFileNameFromImageList(tKymo);
			String nameWithoutExt = new File(fullPath).getName().replaceFirst("[.][^.]+$", "");
			final Capillary capi = exp.getCapillaries().getCapillaryFromKymographName(nameWithoutExt);
			if (capi == null)
				continue;
			if (tKymo != capi.getKymographIndex())
				System.out.println(
						"discrepancy between t=" + tKymo + " and cap.kymographIndex=" + capi.getKymographIndex());
			capi.setGulpsOptions(options);
			futures.add(processor.submit(new Runnable() {
				@Override
				public void run() {
					capi.setDerivative(new CapillaryMeasure(capi.getLast2ofCapillaryName() + "_derivative",
							capi.getKymographIndex(), getDerivativeProfile(seqAnalyzed, capi, jitter)));
				}
			}));
		}
		waitFuturesCompletion(processor, futures);
		processor.shutdown();
	}

	private void detectGulps(Experiment exp, BuildSeriesOptions options, CapillaryMeasure thresholdMeasure) {
		int firstKymo = 0;
		int lastKymo = exp.getSeqKymos().getSequence().getSizeT() - 1;
		if (options.detectSelectedKymo) {
			firstKymo = options.kymoFirst;
			lastKymo = firstKymo;
		}

		int nframes = lastKymo - firstKymo + 1;
		final Processor processor = new Processor(SystemUtil.getNumberOfCPUs());
		processor.setThreadName("detect_gulps");
		processor.setPriority(Processor.NORM_PRIORITY);
		ArrayList<Future<?>> futures = new ArrayList<Future<?>>(nframes);
		futures.clear();

		for (int tKymo = firstKymo; tKymo <= lastKymo; tKymo++) {
			String fullPath = exp.getSeqKymos().getFileNameFromImageList(tKymo);
			String nameWithoutExt = new File(fullPath).getName().replaceFirst("[.][^.]+$", "");
			final Capillary capi = exp.getCapillaries().getCapillaryFromKymographName(nameWithoutExt);
			if (capi == null)
				continue;
			if (tKymo != capi.getKymographIndex())
				System.out.println(
						"discrepancy between t=" + tKymo + " and cap.kymographIndex=" + capi.getKymographIndex());
			capi.setGulpsOptions(options);
			final CapillaryMeasure threshold = thresholdMeasure;
			futures.add(processor.submit(new Runnable() {
				@Override
				public void run() {
					capi.initGulps();
					capi.detectGulps(threshold);
				}
			}));
		}
		waitFuturesCompletion(processor, futures);
		processor.shutdown();
	}

	private void waitFuturesCompletion(Processor processor, ArrayList<Future<?>> futuresArray) {
		while (!futuresArray.isEmpty()) {
			final Future<?> f = futuresArray.get(futuresArray.size() - 1);
			try {
				f.get();
			} catch (ExecutionException e) {
				Logger.error("GulpDetector:waitFuturesCompletion - Execution exception", e);
			} catch (InterruptedException e) {
				Logger.warn("GulpDetector:waitFuturesCompletion - Interrupted exception: " + e.getMessage());
			}
			futuresArray.remove(f);
		}
	}

	private CapillaryMeasure computeThresholdFromEmptyCages(Experiment exp, BuildSeriesOptions options) {
		List<Capillary> emptyCageCapillaries = new ArrayList<>();

		for (Capillary cap : exp.getCapillaries().getList()) {
			int cageID = cap.getCageID();
			plugins.fmp.multitools.experiment.cage.Cage cage = exp.getCages().getCageFromID(cageID);
			if (cage != null && cage.getCageNFlies() == 0) {
				if (cap.getDerivative() != null && cap.getDerivative().polylineLevel != null
						&& cap.getDerivative().polylineLevel.npoints > 0) {
					emptyCageCapillaries.add(cap);
				}
			}
		}

		if (emptyCageCapillaries.isEmpty()) {
			Logger.warn("GulpDetector:computeThresholdFromEmptyCages() - No empty cages found, using fixed threshold");
			return null;
		}

		if (emptyCageCapillaries.size() < 2) {
			Logger.warn("GulpDetector:computeThresholdFromEmptyCages() - Only " + emptyCageCapillaries.size()
					+ " empty cage(s) found, statistics may be unreliable");
		}

		Capillary firstEmptyCageCap = emptyCageCapillaries.get(0);
		int npoints = firstEmptyCageCap.getDerivative().polylineLevel.npoints;

		double[] thresholdValues = new double[npoints];
		double[] xpoints = new double[npoints];

		for (int t = 0; t < npoints; t++) {
			xpoints[t] = t;
			List<Double> derivativeValuesAtT = new ArrayList<>();

			for (Capillary cap : emptyCageCapillaries) {
				if (cap.getDerivative() != null && cap.getDerivative().polylineLevel != null
						&& t < cap.getDerivative().polylineLevel.npoints) {
					double derivValue = cap.getDerivative().polylineLevel.ypoints[t];
					derivativeValuesAtT.add(derivValue);
				}
			}

			if (derivativeValuesAtT.isEmpty()) {
				thresholdValues[t] = 0;
				continue;
			}

			thresholdValues[t] = computeThresholdAtT(derivativeValuesAtT, options);
		}

		thresholdValues = applySmoothing(thresholdValues, options);

		CapillaryMeasure thresholdMeasure = new CapillaryMeasure(
				firstEmptyCageCap.getLast2ofCapillaryName() + "_threshold");
		thresholdMeasure.capIndexKymo = firstEmptyCageCap.getKymographIndex();
		thresholdMeasure.polylineLevel = new plugins.fmp.multitools.tools.Level2D(xpoints, thresholdValues, npoints);

		firstEmptyCageCap.setThreshold(thresholdMeasure);

		Logger.info("GulpDetector:computeThresholdFromEmptyCages() - Computed threshold from "
				+ emptyCageCapillaries.size() + " empty cage(s)");

		return thresholdMeasure;
	}

	private double computeMean(List<Double> values) {
		if (values.isEmpty())
			return 0.0;
		double sum = 0.0;
		for (Double value : values) {
			sum += value;
		}
		return sum / values.size();
	}

	private double computeStdDev(List<Double> values, double mean) {
		if (values.size() < 2)
			return 0.0;
		double sumSquaredDiff = 0.0;
		for (Double value : values) {
			double diff = value - mean;
			sumSquaredDiff += diff * diff;
		}
		return Math.sqrt(sumSquaredDiff / values.size());
	}

	private double computeThresholdAtT(List<Double> values, BuildSeriesOptions options) {
		GulpThresholdMethod method = options.thresholdMethod;
		double k = options.thresholdSdMultiplier;

		switch (method) {
		case MEAN_PLUS_SD: {
			double avg = computeMean(values);
			double std = computeStdDev(values, avg);
			return avg + k * std;
		}
		case MEDIAN_PLUS_IQR: {
			double median = computeMedian(values);
			double iqr = computeIQR(values);
			return median + k * iqr;
		}
		case MEDIAN_PLUS_MAD: {
			double median = computeMedian(values);
			double mad = computeMAD(values, median);
			return median + k * mad;
		}
		default:
			return computeMean(values) + 3.0 * computeStdDev(values, computeMean(values));
		}
	}

	private double computeMedian(List<Double> values) {
		if (values.isEmpty())
			return 0.0;
		double[] sorted = new double[values.size()];
		int i = 0;
		for (Double v : values)
			sorted[i++] = v;
		java.util.Arrays.sort(sorted);
		int mid = sorted.length / 2;
		if (sorted.length % 2 == 0)
			return (sorted[mid - 1] + sorted[mid]) / 2;
		return sorted[mid];
	}

	private double computeMAD(List<Double> values, double median) {
		if (values.isEmpty())
			return 0.0;
		List<Double> absDevs = new ArrayList<>();
		for (Double v : values)
			absDevs.add(Math.abs(v - median));
		return computeMedian(absDevs);
	}

	private double computeIQR(List<Double> values) {
		if (values.size() < 2)
			return 0.0;
		double[] sorted = new double[values.size()];
		int i = 0;
		for (Double v : values)
			sorted[i++] = v;
		java.util.Arrays.sort(sorted);
		int q1idx = values.size() / 4;
		int q3idx = (3 * values.size()) / 4;
		if (q3idx >= sorted.length)
			q3idx = sorted.length - 1;
		return sorted[q3idx] - sorted[q1idx];
	}

	private double[] applySmoothing(double[] data, BuildSeriesOptions options) {
		if (options.thresholdSmoothing == GulpThresholdSmoothing.NONE)
			return data;
		if (options.thresholdSmoothingWindow < 1)
			return data;

		switch (options.thresholdSmoothing) {
		case BACKWARDS_RECURSION: {
			double alpha = Math.max(0.01, Math.min(0.99, options.thresholdSmoothingAlpha));
			double[] out = new double[data.length];
			for (int i = data.length - 1; i >= 0; i--) {
				if (i == data.length - 1)
					out[i] = data[i];
				else
					out[i] = alpha * out[i + 1] + (1 - alpha) * data[i];
			}
			return out;
		}
		case SAVITZKY_GOLAY: {
			int win = options.thresholdSmoothingWindow;
			if (win % 2 == 0)
				win++;
			int order = Math.min(2, win - 1);
			return SavitzkyGolayFilter.smooth(data, win, order);
		}
		default:
			return data;
		}
	}

	private List<Point2D> getDerivativeProfile(Sequence seq, Capillary cap, int jitter) {
		Polyline2D polyline = cap.getTopLevel().polylineLevel;
		if (polyline == null)
			return null;

		int z = seq.getSizeZ() - 1;
		int c = 0;
		IcyBufferedImage image = seq.getImage(cap.getKymographIndex(), z, c);
		List<Point2D> listOfMaxPoints = new ArrayList<>();
		int[] kymoImageValues = Array1DUtil.arrayToIntArray(image.getDataXY(c), image.isSignedDataType());
		int xwidth = image.getSizeX();
		int yheight = image.getSizeY();

		for (int ix = 1; ix < polyline.npoints; ix++) {
			// for each point of topLevelArray, define a bracket of rows to look at
			// ("jitter" = 10)
			int low = (int) polyline.ypoints[ix] - jitter;
			int high = low + 2 * jitter;
			if (low < 0)
				low = 0;
			if (high >= yheight)
				high = yheight - 1;
			int max = kymoImageValues[ix + low * xwidth];
			for (int iy = low + 1; iy < high; iy++) {
				int val = kymoImageValues[ix + iy * xwidth];
				if (max < val)
					max = val;
			}
			listOfMaxPoints.add(new Point2D.Double((double) ix, (double) max));
		}
		return listOfMaxPoints;
	}
}
