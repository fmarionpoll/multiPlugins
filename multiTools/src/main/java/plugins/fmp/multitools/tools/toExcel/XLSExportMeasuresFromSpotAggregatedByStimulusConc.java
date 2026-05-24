package plugins.fmp.multitools.tools.toExcel;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.xssf.streaming.SXSSFSheet;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.CageSpotAggregateSeries;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotProperties;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.Results;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.toExcel.enums.EnumXLSColumnHeader;
import plugins.fmp.multitools.tools.toExcel.utils.SpotExcelTimeline;
import plugins.fmp.multitools.tools.toExcel.utils.XLSUtils;

/**
 * Excel export for spot measures aggregated at cage level by (stimulus, concentration).
 *
 * For AREA_SUMCLEAN, builds the sum of per-spot normalized consumption series on the native
 * spot/camera sample index, then resamples it onto the requested Excel export grid:
 *   (maxBaseline - v(t)) / maxBaseline
 * where maxBaseline is computed from the beginning of the series (see {@link ResultsOptions} parameters).
 */
public class XLSExportMeasuresFromSpotAggregatedByStimulusConc extends XLSExportSpots {

	private static final EnumXLSColumnHeader[] AGGREGATE_DESCRIPTORS = { EnumXLSColumnHeader.PATH,
			EnumXLSColumnHeader.DATE, EnumXLSColumnHeader.EXP_ID, EnumXLSColumnHeader.CAM,
			EnumXLSColumnHeader.EXP_EXPT, EnumXLSColumnHeader.EXP_STIM1, EnumXLSColumnHeader.EXP_CONC1,
			EnumXLSColumnHeader.EXP_STIM2, EnumXLSColumnHeader.EXP_CONC2, EnumXLSColumnHeader.EXP_STRAIN,
			EnumXLSColumnHeader.EXP_SEX, EnumXLSColumnHeader.CAGEID, EnumXLSColumnHeader.CAGEPOS,
			EnumXLSColumnHeader.CAGE_NFLIES, EnumXLSColumnHeader.CAGE_STRAIN, EnumXLSColumnHeader.CAGE_SEX,
			EnumXLSColumnHeader.CAGE_AGE, EnumXLSColumnHeader.CAGE_COMMENT, EnumXLSColumnHeader.SPOT_VOLUME,
			EnumXLSColumnHeader.SPOT_STIM, EnumXLSColumnHeader.SPOT_CONC, EnumXLSColumnHeader.DUM5 };

	@Override
	protected int exportExperimentData(Experiment exp, ResultsOptions resultsOptions, int startColumn, String charSeries)
			throws plugins.fmp.multitools.tools.toExcel.exceptions.ExcelExportException {
		if (resultsOptions == null || exp == null) {
			return startColumn;
		}
		boolean aggregateExport = resultsOptions.spotAggregateByStimulusConc
				|| resultsOptions.resultType == EnumResults.AGG_SUMCLEAN
				|| resultsOptions.resultType == EnumResults.AGG_SUMCLEAN_V5
				|| resultsOptions.resultType == EnumResults.AGG_AREA_COUNT_V5;
		if (!resultsOptions.spotAreas || !aggregateExport) {
			return startColumn;
		}
		EnumResults gridProbe;
		if (resultsOptions.resultType == EnumResults.AGG_SUMCLEAN_V5) {
			gridProbe = EnumResults.GREY_SUM_CLEAN_V5;
		} else if (resultsOptions.resultType == EnumResults.AGG_AREA_COUNT_V5) {
			gridProbe = EnumResults.AREA_COUNT_V5;
		} else {
			gridProbe = EnumResults.AREA_SUMCLEAN;
		}
		int colmax = exportResultType(exp, startColumn, charSeries, gridProbe, "spot_aggregate");
		return colmax;
	}

	@Override
	protected int exportResultTypeToSheet(Experiment exp, SXSSFSheet sheet, EnumResults resultType, int col0,
			String charSeries) {
		if (resultType != EnumResults.AREA_SUMCLEAN && resultType != EnumResults.GREY_SUM_V5
				&& resultType != EnumResults.GREY_SUM_CLEAN_V5 && resultType != EnumResults.AREA_COUNT_V5
				&& resultType != EnumResults.AGG_SUMCLEAN && resultType != EnumResults.AGG_SUMCLEAN_V5
				&& resultType != EnumResults.AGG_AREA_COUNT_V5) {
			return col0;
		}
		Point pt = new Point(col0, 0);
		pt = writeExperimentSeparator(sheet, pt);

		exp.getCages().prepareSpotAggregates(exp, options);
		SpotExcelTimeline.SpotExcelGrid spotGrid = SpotExcelTimeline.buildForSpotExport(exp, options);

		Spots allSpots = exp.getSpots();
		EnumResults savedRt = options.resultType;
		for (Cage cage : exp.getCages().cagesList) {
			if (options.onlyalive && cage.getProperties().getCageNFlies() < 1) {
				continue;
			}
			if (cage.getSpotList(allSpots).isEmpty()) {
				continue;
			}

			List<CageSpotAggregateSeries> series = cage.getSpotAggregates().getEntries();
			if (series == null || series.isEmpty()) {
				continue;
			}
			for (CageSpotAggregateSeries s : series) {
				if (s == null || s.getMeasure() == null) {
					continue;
				}
				ArrayList<Double> values = new ArrayList<>(s.getMeasure().getValuesResampledToExcelGrid(spotGrid));
				if (values.isEmpty()) {
					continue;
				}
				pt.y = 0;

				Spot synthetic = buildSyntheticSpotForAggregate(cage, s, allSpots);
				pt = writeExperimentSpotInfos(sheet, pt, exp, charSeries, cage, synthetic, resultType);

				Results r = new Results(cage.getProperties(), synthetic.getProperties(), values.size());
				r.setDataValues(values);
				r.initValuesOutArray(values.size(), Double.NaN);
				// Keep scaling consistent with existing spot exports.
				double scaling = allSpots.getScalingFactorToPhysicalUnits(resultType);
				r.transferDataValuesToValuesOut(scaling, resultType);
				writeXLSResult(sheet, pt, r);
				pt.x++;
			}
		}
		options.resultType = savedRt;

		Logger.info("XLSExportMeasuresFromSpotAggregatedByStimulusConc: exported columns up to " + pt.x);
		return pt.x;
	}

	private Spot buildSyntheticSpotForAggregate(Cage cage, CageSpotAggregateSeries s, Spots allSpots) {
		Spot spot = new Spot();
		SpotProperties p = spot.getProperties();
		p.setCageID(cage.getProperties().getCageID());
		p.setCagePosition(-1);
		p.setCageRow(-1);
		p.setCageColumn(-1);
		p.setStimulus(s.getKey().stimulus);
		p.setConcentration(s.getKey().concentration);
		p.setSpotVolume(meanSpotVolumeForAggregate(cage, s, allSpots));
		p.setName("AGG");
		p.setStimulusI(Integer.toString(s.getNSpotsExposed()));
		return spot;
	}

	private double meanSpotVolumeForAggregate(Cage cage, CageSpotAggregateSeries s, Spots allSpots) {
		if (cage == null || s == null || s.getKey() == null || allSpots == null) {
			return Double.NaN;
		}
		double sum = 0.0;
		int n = 0;
		for (Spot spot : cage.getSpotList(allSpots)) {
			if (spot == null || spot.getProperties() == null) {
				continue;
			}
			SpotProperties p = spot.getProperties();
			boolean sameGroup = s.getKey().stimulus.equals(p.getStimulus())
					&& s.getKey().concentration.equals(p.getConcentration());
			if (!sameGroup || !Double.isFinite(p.getSpotVolume())) {
				continue;
			}
			sum += p.getSpotVolume();
			n++;
		}
		return n > 0 ? sum / n : Double.NaN;
	}

	@Override
	protected int getDescriptorRowCount() {
		return AGGREGATE_DESCRIPTORS.length;
	}

	@Override
	protected int writeTopRowDescriptors(SXSSFSheet sheet) {
		for (int i = 0; i < AGGREGATE_DESCRIPTORS.length; i++) {
			XLSUtils.setValue(sheet, 0, i, options.transpose, AGGREGATE_DESCRIPTORS[i].getName());
		}
		return AGGREGATE_DESCRIPTORS.length;
	}

	@Override
	protected Point writeExperimentSpotInfos(SXSSFSheet sheet, Point pt, Experiment exp, String charSeries, Cage cage,
			Spot spot, EnumResults resultType) {
		for (int i = 0; i < AGGREGATE_DESCRIPTORS.length; i++) {
			String value = aggregateDescriptorValue(AGGREGATE_DESCRIPTORS[i], exp, cage, spot);
			if (value != null && !value.isEmpty()) {
				XLSUtils.setValue(sheet, pt.x, pt.y + i, options.transpose, value);
			}
		}
		pt.y += getDescriptorRowCount();
		return pt;
	}

	private String aggregateDescriptorValue(EnumXLSColumnHeader header, Experiment exp, Cage cage, Spot spot) {
		switch (header) {
		case PATH:
		case DATE:
		case CAM:
			return exp.getExperimentField(header);
		case EXP_ID:
		case EXP_EXPT:
		case EXP_STIM1:
		case EXP_CONC1:
		case EXP_STIM2:
		case EXP_CONC2:
		case EXP_STRAIN:
		case EXP_SEX:
			exp.loadExperimentDescriptors();
			return exp.getProperties().getField(header);
		case CAGEID:
			return Integer.toString(cage.getProperties().getCageID());
		case CAGEPOS:
			return Integer.toString(cage.getProperties().getCageID());
		case CAGE_NFLIES:
			return Integer.toString(cage.getProperties().getCageNFlies());
		case CAGE_STRAIN:
			return cage.getProperties().getFlyStrain();
		case CAGE_SEX:
			return cage.getProperties().getFlySex();
		case CAGE_AGE:
			return Integer.toString(cage.getProperties().getFlyAge());
		case CAGE_COMMENT:
			return cage.getProperties().getComment();
		case SPOT_VOLUME:
			double volume = spot.getProperties().getSpotVolume();
			return Double.isFinite(volume) ? Double.toString(volume) : "";
		case SPOT_STIM:
			return spot.getProperties().getStimulus();
		case SPOT_CONC:
			return spot.getProperties().getConcentration();
		case DUM5:
			return spot.getProperties().getStimulusI();
		default:
			return "";
		}
	}
}

