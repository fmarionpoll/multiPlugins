package plugins.fmp.multitools.tools.toExcel.csv;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillaries.ReferenceMeasures;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryMeasure;
import plugins.fmp.multitools.experiment.capillary.geometry.CapillaryPixelScale;
import plugins.fmp.multitools.experiment.cages.EvaporationCorrectionMethod;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.chart.builders.CageCapillarySeriesBuilder;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.toExcel.NormalizedExportSupport;

/**
 * One evaporation time series per experiment, written beside the pooled
 * measure CSVs. Values are the curve subtracted from topraw (exponential model,
 * or the no-fly mean when that fit is not used).
 */
final class EvaporationCsvWriter {

	static final String COL_EVAPORATION_UL = "evaporation_uL";
	static final String COL_EVAPORATION_L_UL = "evaporation_L_uL";
	static final String COL_EVAPORATION_R_UL = "evaporation_R_uL";
	static final String COL_EVAPORATION_PX = "evaporation_px";
	static final String COL_EVAPORATION_L_PX = "evaporation_L_px";
	static final String COL_EVAPORATION_R_PX = "evaporation_R_px";
	static final String COL_FIT = "fit";
	static final String COL_FIT_L = "fit_L";
	static final String COL_FIT_R = "fit_R";

	private static final String FIT_MODEL = "model";
	private static final String FIT_MEAN = "mean";

	private EvaporationCsvWriter() {
	}

	static void writeExperiment(Path folder, Experiment exp, ResultsOptions options, String charSeries, long seriesStepMs)
			throws IOException {
		if (folder == null || exp == null || exp.getCapillaries() == null || exp.getCages() == null)
			return;
		EvaporationCorrectionMethod method = EvaporationCorrectionMethod
				.fromOrDefault(options != null ? options.evaporationCorrectionMethod : null);
		exp.getCages().computeEvaporationCorrection(exp, method);
		ReferenceMeasures ref = exp.getCapillaries().getReferenceMeasures();
		if (ref == null || !ref.isEvaporationComputed()) {
			Logger.info("EvaporationCsvWriter: no empty-capillary curve for " + expKey(exp, charSeries));
			return;
		}

		int n = Math.max(nPoints(ref.getEvaporation()),
				Math.max(nPoints(ref.getEvaporationL()), nPoints(ref.getEvaporationR())));
		if (n <= 0)
			return;

		double[] timeMin = new double[n];
		double[] px = values(ref.getEvaporation(), n);
		double[] pxL = values(ref.getEvaporationL(), n);
		double[] pxR = values(ref.getEvaporationR(), n);
		double[] ul = toUl(exp, px);
		double[] ulL = toUl(exp, pxL);
		double[] ulR = toUl(exp, pxR);
		for (int i = 0; i < n; i++)
			timeMin[i] = CageCapillarySeriesBuilder.displayTimeMinutes(exp, i, seriesStepMs);

		String expKey = expKey(exp, charSeries);
		Path file = folder.resolve(fileName(expKey));
		write(file, expKey, timeMin, ul, ulL, ulR, px, pxL, pxR,
				fitLabel(ref.getEvaporation(), ref.isEvaporationFromModel()),
				fitLabel(ref.getEvaporationL(), ref.isEvaporationLFromModel()),
				fitLabel(ref.getEvaporationR(), ref.isEvaporationRFromModel()));
		Logger.info("EvaporationCsvWriter: " + file);
	}

	static String fileName(String expKey) {
		String base = expKey == null ? "" : expKey.trim();
		base = base.replaceAll("[^A-Za-z0-9._-]+", "_");
		base = base.replaceAll("^_+|_+$", "");
		if (base.isEmpty())
			base = "experiment";
		if (base.length() > 120) {
			String tail = base.substring(base.length() - 8);
			base = base.substring(0, 112) + tail;
		}
		return "evaporation_" + base + ".csv";
	}

	static void write(Path file, String expKey, double[] timeMin, double[] ul, double[] ulL, double[] ulR, double[] px,
			double[] pxL, double[] pxR, String fit, String fitL, String fitR) throws IOException {
		if (file == null || timeMin == null || timeMin.length == 0)
			return;
		Path parent = file.getParent();
		if (parent != null)
			Files.createDirectories(parent);
		try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
				CSVPrinter printer = new CSVPrinter(writer,
						CSVFormat.DEFAULT.builder()
								.setHeader(CsvNormalizedExportSupport.COL_EXPERIMENT_ID,
										CsvNormalizedExportSupport.COL_TIME_MIN, COL_EVAPORATION_UL,
										COL_EVAPORATION_L_UL, COL_EVAPORATION_R_UL, COL_EVAPORATION_PX,
										COL_EVAPORATION_L_PX, COL_EVAPORATION_R_PX, COL_FIT, COL_FIT_L, COL_FIT_R)
								.setSkipHeaderRecord(false).build())) {
			for (int i = 0; i < timeMin.length; i++) {
				printer.printRecord(expKey, timeMin[i], at(ul, i), at(ulL, i), at(ulR, i), at(px, i), at(pxL, i),
						at(pxR, i), fit, fitL, fitR);
			}
		}
	}

	private static String expKey(Experiment exp, String charSeries) {
		return NormalizedExportSupport.buildExpKey(exp, charSeries);
	}

	private static String fitLabel(CapillaryMeasure measure, boolean fromModel) {
		if (measure == null || measure.getNPoints() <= 0)
			return "";
		return fromModel ? FIT_MODEL : FIT_MEAN;
	}

	private static int nPoints(CapillaryMeasure measure) {
		return measure == null ? 0 : measure.getNPoints();
	}

	private static double[] values(CapillaryMeasure measure, int n) {
		double[] y = new double[n];
		int available = nPoints(measure);
		for (int i = 0; i < n; i++)
			y[i] = i < available ? measure.getValueAt(i) : Double.NaN;
		return y;
	}

	private static double[] toUl(Experiment exp, double[] px) {
		double[] ul = new double[px.length];
		Capillary scaleCap = firstCapillary(exp);
		if (scaleCap == null) {
			for (int i = 0; i < ul.length; i++)
				ul[i] = Double.NaN;
			return ul;
		}
		CapillaryPixelScale.MeasurePixelSource source = CapillaryPixelScale.sourceOf(exp);
		double expansion = CapillaryPixelScale.expansionRatioOf(exp);
		int factor = Math.max(1, exp.getKymoSubsampleFactor());
		for (int i = 0; i < px.length; i++) {
			if (!Double.isFinite(px[i]))
				ul[i] = Double.NaN;
			else
				ul[i] = CapillaryPixelScale.toUl(px[i], scaleCap, CapillaryPixelScale.frameOfColumn(i, factor), source,
						expansion, false);
		}
		return ul;
	}

	private static Capillary firstCapillary(Experiment exp) {
		if (exp.getCapillaries() == null || exp.getCapillaries().getList() == null)
			return null;
		for (Capillary cap : exp.getCapillaries().getList()) {
			if (cap != null)
				return cap;
		}
		return null;
	}

	private static Double at(double[] values, int index) {
		if (values == null || index < 0 || index >= values.length || !Double.isFinite(values[index]))
			return null;
		return values[index];
	}
}
