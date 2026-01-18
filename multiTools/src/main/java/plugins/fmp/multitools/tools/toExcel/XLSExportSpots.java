package plugins.fmp.multitools.tools.toExcel;

import java.awt.Point;

import org.apache.poi.xssf.streaming.SXSSFSheet;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.toExcel.enums.EnumXLSColumnHeader;
import plugins.fmp.multitools.tools.toExcel.utils.XLSUtils;

public abstract class XLSExportSpots extends XLSExport {

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

		XLSUtils.setValueAtColumn(sheet, pt, EnumXLSColumnHeader.DUM4, transpose, spot.getProperties().getStimulusI());
	}
}

