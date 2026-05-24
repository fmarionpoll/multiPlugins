package plugins.fmp.multitools.tools.toExcel;

import java.awt.Color;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.xssf.streaming.SXSSFSheet;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.tools.ColorUtils;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.toExcel.enums.EnumColumnType;
import plugins.fmp.multitools.tools.toExcel.enums.EnumXLSColumnHeader;
import plugins.fmp.multitools.tools.toExcel.utils.SpotExcelTimeline;
import plugins.fmp.multitools.tools.toExcel.utils.XLSUtils;

public abstract class XLSExportSpots extends XLSExport {

	@Override
	protected long transposeValidationTimeBinCount() {
		if (options != null && expList != null) {
			int nBins = SpotExcelTimeline.maxSpotExcelBinCountAcrossExportRange(expList, options);
			if (nBins >= 1) {
				return nBins;
			}
		}
		return super.transposeValidationTimeBinCount();
	}

	private static boolean usesSpotSubsamplingTimeline(EnumResults resultType) {
		switch (resultType) {
			case AREA_SUM:
			case AREA_SUMNOFLY:
			case AREA_SUMCLEAN:
			case AREA_SUM_V2:
			case AREA_SUMNOFLY_V2:
			case AREA_SUMCLEAN_V2:
			case AREA_SUMCLEAN_V3:
			case AREA_FLYPRESENT:
			case AREA_COUNT_V5:
			case GREY_SUM_V5:
			case GREY_SUM_CLEAN_V5:
			case AGG_SUMCLEAN:
			case AGG_SUMCLEAN_V5:
			case AGG_AREA_COUNT_V5:
			case AGG_MEDIANREF:
				return true;
			default:
				return false;
		}
	}

	@Override
	protected void writeTopRowTimeIntervals(SXSSFSheet sheet, int row, EnumResults resultType) {
		if (resultType == null || !usesSpotSubsamplingTimeline(resultType)) {
			super.writeTopRowTimeIntervals(sheet, row, resultType);
			return;
		}
		boolean transpose = options.transpose;
		Point pt = new Point(0, row);
		SpotExcelTimeline.SpotExcelGrid headerGrid = SpotExcelTimeline.buildForSpotExport(expAll, options);
		int nBins = expList != null ? SpotExcelTimeline.maxSpotExcelBinCountAcrossExportRange(expList, options)
				: headerGrid.getNBins();
		for (int k = 0; k < nBins; k++) {
			long elapsedMs = headerGrid.getHeaderElapsedMs(k);
			XLSUtils.setValue(sheet, pt, transpose,
					SpotExcelTimeline.formatElapsedColumnHeader(elapsedMs, options.buildExcelUnitMs));
			pt.y++;
		}
	}

	protected boolean hasSpotMeasuresSelectedForExport(ResultsOptions o) {
		return o.spotAreas && (o.sum || o.spotSumNoFly || o.spotSumClean || o.sumV2 || o.spotSumNoFlyV2
				|| o.spotSumCleanV2 || o.spotAggregateByStimulusConc || o.spotAreaCountV5 || o.spotGreySumV5
				|| o.spotGreySumCleanV5);
	}

	protected EnumResults[] enabledSpotMeasureTypesForExport(ResultsOptions o) {
		List<EnumResults> list = new ArrayList<>(9);
		if (o.sum) {
			list.add(EnumResults.AREA_SUM);
		}
		if (o.spotSumNoFly) {
			list.add(EnumResults.AREA_SUMNOFLY);
		}
		if (o.spotSumClean) {
			list.add(EnumResults.AREA_SUMCLEAN);
		}
		if (o.sumV2) {
			list.add(EnumResults.AREA_SUM_V2);
		}
		if (o.spotSumNoFlyV2) {
			list.add(EnumResults.AREA_SUMNOFLY_V2);
		}
		if (o.spotSumCleanV2) {
			list.add(EnumResults.AREA_SUMCLEAN_V2);
		}
		if (o.spotAreaCountV5) {
			list.add(EnumResults.AREA_COUNT_V5);
		}
		if (o.spotGreySumV5) {
			list.add(EnumResults.GREY_SUM_V5);
		}
		if (o.spotGreySumCleanV5) {
			list.add(EnumResults.GREY_SUM_CLEAN_V5);
		}
		if (o.sum || o.spotSumNoFly || o.spotSumClean || o.sumV2 || o.spotSumNoFlyV2 || o.spotSumCleanV2
				|| o.spotAreaCountV5 || o.spotGreySumV5 || o.spotGreySumCleanV5) {
			list.add(EnumResults.AREA_FLYPRESENT);
		}
		return list.toArray(new EnumResults[0]);
	}

	@Override
	protected int getDescriptorRowCount() {
		return EnumXLSColumnHeader.DUM5.getValue() + 1;
	}

	/**
	 * Writes only the COMMON and SPOT descriptors for spot exports.
	 * 
	 * This overrides the default implementation in XLSExport, which writes all
	 * headers (COMMON, CAP, SPOT, etc.) for every sheet. For spot-oriented exports
	 * this is confusing, so we restrict the top-row labels to the relevant types.
	 */
	@Override
	protected int writeTopRowDescriptors(SXSSFSheet sheet) {
		int nextcol = -1;

		for (EnumXLSColumnHeader header : EnumXLSColumnHeader.values()) {
			EnumColumnType type = header.toType();
			if (type != EnumColumnType.COMMON && type != EnumColumnType.SPOT) {
				continue;
			}
			XLSUtils.setValue(sheet, 0, header.getValue(), options.transpose, header.getName());
			if (nextcol < header.getValue()) {
				nextcol = header.getValue();
			}
		}

		return nextcol + 1;
	}

	/**
	 * Writes experiment spot information to the sheet.
	 * 
	 * @param sheet      The sheet to write to
	 * @param pt         The starting point
	 * @param exp        The experiment
	 * @param charSeries The series identifier
	 * @param cage       The cage
	 * @param spot       The spot
	 * @param resultType The export type
	 * @return The updated point
	 */
	protected Point writeExperimentSpotInfos(SXSSFSheet sheet, Point pt, Experiment exp, String charSeries, Cage cage,
			Spot spot, EnumResults resultType) {
		boolean transpose = options.transpose;

		writeFileInformation(sheet, pt, transpose, exp);
		writeExperimentProperties(sheet, pt, transpose, exp, null);
		writeCageProperties(sheet, pt, transpose, cage);
		writeSpotProperties(sheet, pt, transpose, spot, cage, charSeries, resultType);

		pt.y = pt.y + getDescriptorRowCount();
		return pt;
	}

	/**
	 * Writes spot properties to the sheet.
	 */
	protected void writeSpotProperties(SXSSFSheet sheet, Point pt, boolean transpose, Spot spot, Cage cage,
			String charSeries, EnumResults resultType) {
		writeFiniteDoubleAtColumn(sheet, pt, EnumXLSColumnHeader.SPOT_VOLUME, transpose,
				spot.getProperties().getSpotVolume());
		XLSUtils.setValueAtColumn(sheet, pt, EnumXLSColumnHeader.SPOT_PIXELS, transpose,
				spot.getProperties().getSpotNPixels());

		XLSUtils.setValueAtColumn(sheet, pt, EnumXLSColumnHeader.SPOT_STIM, transpose,
				spot.getProperties().getStimulus());
		XLSUtils.setValueAtColumn(sheet, pt, EnumXLSColumnHeader.SPOT_CONC, transpose,
				spot.getProperties().getConcentration());

		XLSUtils.setValueAtColumn(sheet, pt, EnumXLSColumnHeader.SPOT_CAGEROW, transpose,
				spot.getProperties().getCageRow());
		XLSUtils.setValueAtColumn(sheet, pt, EnumXLSColumnHeader.SPOT_CAGECOL, transpose,
				spot.getProperties().getCageColumn());

		XLSUtils.setValueAtColumn(sheet, pt, EnumXLSColumnHeader.SPOT_NFLIES, transpose,
				cage.getProperties().getCageNFlies());
		
		XLSUtils.setValueAtColumn(sheet, pt, EnumXLSColumnHeader.SPOT_COLOR, transpose,
				ColorUtils.getFriendlyColorName(resolveSpotColor(spot)));

		XLSUtils.setValueAtColumn(sheet, pt, EnumXLSColumnHeader.DUM5, transpose, spot.getProperties().getStimulusI());
	}

	protected void writeFiniteDoubleAtColumn(SXSSFSheet sheet, Point pt, EnumXLSColumnHeader column,
			boolean transpose, double value) {
		if (Double.isFinite(value)) {
			XLSUtils.setValueAtColumn(sheet, pt, column, transpose, value);
		}
	}

	private Color resolveSpotColor(Spot spot) {
		if (spot != null && spot.getRoi() != null && spot.getRoi().getColor() != null) {
			return spot.getRoi().getColor();
		}
		return spot != null && spot.getProperties() != null ? spot.getProperties().getColor() : null;
	}
}

