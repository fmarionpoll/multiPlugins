package plugins.fmp.multitools.tools.chart.builders;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.jfree.chart.ChartColor;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.CageProperties;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryMeasure;
import plugins.fmp.multitools.tools.chart.ChartCageBuild;
import plugins.fmp.multitools.tools.chart.style.SeriesStyleCodec;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

/**
 * Builds cage datasets from Capillary measurements (including LR Sum/PI
 * synthesis).
 */
public class CageCapillarySeriesBuilder implements CageSeriesBuilder {
	@Override
	public XYSeriesCollection build(Experiment exp, Cage cage, ResultsOptions options) {
		if (cage == null || exp == null || exp.getCapillaries() == null) {
			return new XYSeriesCollection();
		}
		if (options == null)
			return new XYSeriesCollection();

		Capillaries allCapillaries = exp.getCapillaries();
		List<Capillary> capillaries = cage.getCapillaries(allCapillaries);
		if (capillaries == null || capillaries.isEmpty()) {
			return new XYSeriesCollection();
		}

		if (ChartCageBuild.isLRType(options.resultType)) {
			return buildLR(exp, cage, options);
		}

		XYSeriesCollection dataset = new XYSeriesCollection();
		int i = 0;
		for (Capillary cap : capillaries) {
			XYSeries series = createXYSeriesFromCapillaryMeasure(exp, cap, options);
			if (series != null) {
				series.setDescription(buildSeriesDescription(cage, cap, i));
				dataset.addSeries(series);
			}
			i++;
		}

		if (options.resultType == EnumResults.DERIVEDVALUES) {
			XYSeries thresholdSeries = createThresholdSeries(exp, cage, options);
			if (thresholdSeries != null) {
				dataset.addSeries(thresholdSeries);
			}
		}

		if (options.resultType == EnumResults.TOPRAW) {
			XYSeries evaporationSeries = createEvaporationSeries(exp, cage, options);
			if (evaporationSeries != null) {
				dataset.addSeries(evaporationSeries);
			}
		}

		ChartCageBuild.updateGlobalExtremaFromDataset(dataset);
		return dataset;
	}

	private static XYSeriesCollection buildLR(Experiment exp, Cage cage, ResultsOptions options) {
		XYSeriesCollection result = new XYSeriesCollection();
		EnumResults baseType = getBaseType(options.resultType);

		ResultsOptions baseOptions = new ResultsOptions();
		baseOptions.copy(options);
		baseOptions.resultType = baseType;

		XYSeriesCollection parts = new CageCapillarySeriesBuilder().build(exp, cage, baseOptions);
		if (parts == null || parts.getSeriesCount() == 0)
			return result;

		XYSeriesCollection sumAndPI = buildSumAndPISeries(cage, parts);
		for (int i = 0; i < sumAndPI.getSeriesCount(); i++)
			result.addSeries(sumAndPI.getSeries(i));

		ChartCageBuild.updateGlobalExtremaFromDataset(result);
		return result;
	}

	private static EnumResults getBaseType(EnumResults resultType) {
		switch (resultType) {
		case TOPLEVEL_LR:
			return EnumResults.TOPLEVEL;
		case TOPLEVELDELTA_LR:
			return EnumResults.TOPLEVELDELTA;
		case SUMGULPS_LR:
			return EnumResults.SUMGULPS;
		default:
			return resultType;
		}
	}

	static XYSeriesCollection buildSumAndPISeries(Cage cage, XYSeriesCollection parts) {
		XYSeriesCollection result = new XYSeriesCollection();
		if (parts == null || parts.getSeriesCount() == 0)
			return result;

		List<XYSeries> listL = new ArrayList<>();
		List<XYSeries> listR = new ArrayList<>();
		for (int i = 0; i < parts.getSeriesCount(); i++) {
			XYSeries series = parts.getSeries(i);
			String key = (String) series.getKey();
			if (key != null && (key.endsWith("L") || key.endsWith("1")))
				listL.add(series);
			else if (key != null && (key.endsWith("R") || key.endsWith("2")))
				listR.add(series);
		}

		SortedSet<Double> allX = new TreeSet<>();
		for (int i = 0; i < parts.getSeriesCount(); i++) {
			XYSeries series = parts.getSeries(i);
			for (int j = 0; j < series.getItemCount(); j++)
				allX.add(series.getX(j).doubleValue());
		}

		XYSeries seriesSum = new XYSeries(cage.getCageID() + "_Sum", false);
		XYSeries seriesPI = new XYSeries(cage.getCageID() + "_PI", false);

		// Keep legacy side coloring semantics: Sum=Blue, PI=Red
		CageProperties cageProp = cage.getProperties();
		seriesSum.setDescription(SeriesStyleCodec.buildDescription(cageProp.getCageID(), cageProp.getCagePosition(),
				cageProp.getCageNFlies(), Color.BLUE));
		seriesPI.setDescription(SeriesStyleCodec.buildDescription(cageProp.getCageID(), cageProp.getCagePosition(),
				cageProp.getCageNFlies(), Color.RED));

		for (Double x : allX) {
			double sumL = 0;
			double sumR = 0;
			boolean hasL = false;
			boolean hasR = false;

			for (XYSeries s : listL) {
				int idx = s.indexOf(x);
				if (idx >= 0) {
					sumL += Math.abs(s.getY(idx).doubleValue());
					hasL = true;
				}
			}
			for (XYSeries s : listR) {
				int idx = s.indexOf(x);
				if (idx >= 0) {
					sumR += Math.abs(s.getY(idx).doubleValue());
					hasR = true;
				}
			}

			if (hasL || hasR) {
				double sum = sumL + sumR;
				double pi = (sum != 0) ? (sumL - sumR) / sum : 0;
				seriesSum.add(x.doubleValue(), sum);
				seriesPI.add(x.doubleValue(), pi);
			}
		}

		result.addSeries(seriesSum);
		result.addSeries(seriesPI);
		return result;
	}

	private static String buildSeriesDescription(Cage cage, Capillary cap, int i) {
		CageProperties cageProp = cage.getProperties();
		String side = cap.getCapillarySide();

		Color[] palette = { Color.BLUE, Color.RED, Color.GREEN, Color.MAGENTA, Color.CYAN, Color.ORANGE, Color.PINK,
				Color.LIGHT_GRAY };
		Color color = palette[i % palette.length];

		// Preserve the legacy semantic of L=Blue, R=Red
		if (side != null && (side.contains("L") || side.contains("1")))
			color = Color.BLUE;
		else if (side != null && (side.contains("R") || side.contains("2")))
			color = Color.RED;

		if (cap.getNFlies() < 0)
			color = Color.DARK_GRAY;

		return SeriesStyleCodec.buildDescription(cageProp.getCageID(), cageProp.getCagePosition(),
				cageProp.getCageNFlies(), color);
	}

	private static XYSeries createXYSeriesFromCapillaryMeasure(Experiment exp, Capillary cap, ResultsOptions options) {
		if (exp == null || cap == null || options == null)
			return null;

		XYSeries seriesXY = new XYSeries(cap.getCageID() + "_" + cap.getCapillarySide(), false);

		if (exp.getSeqCamData().getTimeManager().getCamImagesTime_Ms() == null)
			exp.getSeqCamData().build_MsTimesArray_From_FileNamesList();
		double[] camImages_time_min = exp.getSeqCamData().getTimeManager().getCamImagesTime_Minutes();

		CapillaryMeasure capMeasure = cap.getMeasurements(options.resultType, exp, options);
		if (capMeasure == null)
			return null;

		int npoints = capMeasure.getNPoints();
		if (camImages_time_min != null && npoints > camImages_time_min.length)
			npoints = camImages_time_min.length;

		double scalingFactor = 1.0;
		if ("volume (ul)".equals(options.resultType.toUnit())) {
			if (cap.getPixels() > 0)
				scalingFactor = cap.getVolume() / cap.getPixels();
		}

		for (int j = 0; j < npoints; j++) {
			double x = camImages_time_min != null ? camImages_time_min[j] : j;
			double y = capMeasure.getValueAt(j) * scalingFactor;
			seriesXY.add(x, y);
		}
		return seriesXY;
	}

	private static XYSeries createThresholdSeries(Experiment exp, Cage cage, ResultsOptions options) {
		if (exp == null || cage == null || options == null)
			return null;

		plugins.fmp.multitools.experiment.capillary.CapillaryMeasure thresholdMeasure = exp.getGulpThresholdMeasure();
		if (thresholdMeasure == null || thresholdMeasure.polylineLevel == null
				|| thresholdMeasure.polylineLevel.npoints == 0)
			return null;

		XYSeries thresholdSeries = new XYSeries(cage.getCageID() + "_threshold", false);

		if (exp.getSeqCamData().getTimeManager().getCamImagesTime_Ms() == null)
			exp.getSeqCamData().build_MsTimesArray_From_FileNamesList();
		double[] camImages_time_min = exp.getSeqCamData().getTimeManager().getCamImagesTime_Minutes();

		int npoints = thresholdMeasure.getNPoints();
		if (camImages_time_min != null && npoints > camImages_time_min.length)
			npoints = camImages_time_min.length;

		double scalingFactor = 1.0;
		if ("volume (ul)".equals(options.resultType.toUnit())) {
			List<plugins.fmp.multitools.experiment.capillary.Capillary> capillaries = cage
					.getCapillaries(exp.getCapillaries());
			if (capillaries != null && !capillaries.isEmpty()) {
				plugins.fmp.multitools.experiment.capillary.Capillary firstCap = capillaries.get(0);
				if (firstCap.getPixels() > 0)
					scalingFactor = firstCap.getVolume() / firstCap.getPixels();
			}
		}

		for (int j = 0; j < npoints; j++) {
			double x = camImages_time_min != null ? camImages_time_min[j] : j;
			double y = thresholdMeasure.getValueAt(j) * scalingFactor;
			thresholdSeries.add(x, y);
		}

		CageProperties cageProp = cage.getProperties();
		thresholdSeries.setDescription(SeriesStyleCodec.buildDescription(cageProp.getCageID(),
				cageProp.getCagePosition(), cageProp.getCageNFlies(), Color.BLACK));

		return thresholdSeries;
	}

	private static XYSeries createEvaporationSeries(Experiment exp, Cage cage, ResultsOptions options) {
		if (exp == null || cage == null || options == null || exp.getCapillaries() == null)
			return null;

		CapillaryMeasure evaporationMeasure = exp.getCapillaries().getReferenceMeasures().getEvaporationForDisplay();
		if (evaporationMeasure == null || evaporationMeasure.polylineLevel == null
				|| evaporationMeasure.polylineLevel.npoints == 0)
			return null;

		XYSeries evaporationSeries = new XYSeries(cage.getCageID() + "_evaporation", false);

		if (exp.getSeqCamData().getTimeManager().getCamImagesTime_Ms() == null)
			exp.getSeqCamData().build_MsTimesArray_From_FileNamesList();
		double[] camImages_time_min = exp.getSeqCamData().getTimeManager().getCamImagesTime_Minutes();

		int npoints = evaporationMeasure.getNPoints();
		if (camImages_time_min != null && npoints > camImages_time_min.length)
			npoints = camImages_time_min.length;

		double scalingFactor = 1.0;
		if ("volume (ul)".equals(options.resultType.toUnit())) {
			List<Capillary> capillaries = cage.getCapillaries(exp.getCapillaries());
			if (capillaries != null && !capillaries.isEmpty()) {
				Capillary firstCap = capillaries.get(0);
				if (firstCap.getPixels() > 0)
					scalingFactor = firstCap.getVolume() / firstCap.getPixels();
			}
		}

		for (int j = 0; j < npoints; j++) {
			double x = camImages_time_min != null ? camImages_time_min[j] : j;
			double y = evaporationMeasure.getValueAt(j) * scalingFactor;
			evaporationSeries.add(x, y);
		}

		CageProperties cageProp = cage.getProperties();
		evaporationSeries.setDescription(SeriesStyleCodec.buildDescription(cageProp.getCageID(),
				cageProp.getCagePosition(), cageProp.getCageNFlies(), ChartColor.BLACK)); // Color.BLACK));

		return evaporationSeries;
	}
}
