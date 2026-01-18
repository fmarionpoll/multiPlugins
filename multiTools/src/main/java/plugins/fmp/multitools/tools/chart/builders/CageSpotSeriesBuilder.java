package plugins.fmp.multitools.tools.chart.builders;

import java.awt.Color;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.CageProperties;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotMeasure;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.chart.ChartCageBuild;
import plugins.fmp.multitools.tools.chart.style.SeriesStyleCodec;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.util.List;

/**
 * Builds cage datasets from Spot measurements.
 */
public class CageSpotSeriesBuilder implements CageSeriesBuilder {

	@Override
	public XYSeriesCollection build(Experiment exp, Cage cage, ResultsOptions options) {
		Spots allSpots = exp.getSpots();
		if (cage == null || allSpots == null) {
			Logger.debug("No spot data for cage");
			return new XYSeriesCollection();
		}
		List<Spot> spots = cage.getSpotList(allSpots);
		if (spots.isEmpty()) {
			Logger.debug("No spot data for cage");
			return new XYSeriesCollection();
		}

		XYSeriesCollection dataset = new XYSeriesCollection();
		for (Spot spot : spots) {
			XYSeries series = createXYSeriesFromSpotMeasure(exp, spot, options);
			if (series == null)
				continue;
			series.setDescription(buildSeriesDescription(cage, spot));
			dataset.addSeries(series);
		}

		ChartCageBuild.updateGlobalExtremaFromDataset(dataset);
		return dataset;
	}

	private static String buildSeriesDescription(Cage cage, Spot spot) {
		CageProperties cageProp = cage.getProperties();
		Color color = spot.getProperties().getColor();
		return SeriesStyleCodec.buildDescription(cageProp.getCageID(), cageProp.getCagePosition(), cageProp.getCageNFlies(),
				color);
	}

	private static XYSeries createXYSeriesFromSpotMeasure(Experiment exp, Spot spot, ResultsOptions resultOptions) {
		if (exp == null || spot == null || resultOptions == null)
			return null;
		XYSeries seriesXY = new XYSeries(spot.getName(), false);

		if (exp.getSeqCamData().getTimeManager().getCamImagesTime_Ms() == null)
			exp.getSeqCamData().build_MsTimesArray_From_FileNamesList();
		double[] camImages_time_min = exp.getSeqCamData().getTimeManager().getCamImagesTime_Minutes();

		SpotMeasure spotMeasure = spot.getMeasurements(resultOptions.resultType);
		if (spotMeasure == null)
			return null;

		double divider = 1.0;
		if (resultOptions.relativeToMaximum && resultOptions.resultType != EnumResults.AREA_FLYPRESENT) {
			divider = spotMeasure.getMaximumValue();
			if (divider == 0)
				divider = 1.0;
		}

		int npoints = spotMeasure.getCount();
		if (camImages_time_min != null && npoints > camImages_time_min.length)
			npoints = camImages_time_min.length;

		for (int j = 0; j < npoints; j++) {
			double x = camImages_time_min != null ? camImages_time_min[j] : j;
			double y = spotMeasure.getValueAt(j) / divider;
			seriesXY.add(x, y);
		}
		return seriesXY;
	}
}


