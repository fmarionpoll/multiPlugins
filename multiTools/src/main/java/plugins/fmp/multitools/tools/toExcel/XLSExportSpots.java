package plugins.fmp.multitools.tools.toExcel;

import java.awt.Point;

import org.apache.poi.xssf.streaming.SXSSFSheet;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.tools.ColorUtils;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.toExcel.enums.EnumColumnType;
import plugins.fmp.multitools.tools.toExcel.enums.EnumXLSColumnHeader;
import plugins.fmp.multitools.tools.toExcel.utils.XLSUtils;

public abstract class XLSExportSpots extends XLSExport {

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

		pt.y = pt.y + EnumXLSColumnHeader.DUM4.getValue() + 1;
		return pt;
	}

	/**
	 * Writes spot properties to the sheet.
	 */
	protected void writeSpotProperties(SXSSFSheet sheet, Point pt, boolean transpose, Spot spot, Cage cage,
			String charSeries, EnumResults resultType) {
		XLSUtils.setValueAtColumn(sheet, pt, EnumXLSColumnHeader.SPOT_VOLUME, transpose,
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
				ColorUtils.getFriendlyColorName(spot.getProperties().getColor()));

		XLSUtils.setValueAtColumn(sheet, pt, EnumXLSColumnHeader.DUM5, transpose, spot.getProperties().getStimulusI());
	}
}

