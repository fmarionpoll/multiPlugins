package plugins.fmp.multiSPOTS96.tools.toExcel;

import java.awt.Point;

import org.apache.poi.xssf.streaming.SXSSFCell;
import org.apache.poi.xssf.streaming.SXSSFRow;
import org.apache.poi.xssf.streaming.SXSSFSheet;

import plugins.fmp.multiSPOTS96.experiment.ExperimentProperties;

public class XLSUtils {
	public static void setValue(SXSSFSheet sheet, Point pt, boolean transpose, int ivalue) {
		SXSSFCell cell = getCell(sheet, pt, transpose);
		cell.setCellValue(ivalue);
	}

	public static void setValue(SXSSFSheet sheet, Point pt, boolean transpose, String string) {
		SXSSFCell cell = getCell(sheet, pt, transpose);
		cell.setCellValue(string);
	}

	public static void setValue(SXSSFSheet sheet, int x, int y, boolean transpose, String string) {
		Point pt = new Point(x, y);
		SXSSFCell cell = getCell(sheet, pt, transpose);
		cell.setCellValue(string);
	}

	public static void setValue(SXSSFSheet sheet, int x, int y, boolean transpose, int ivalue) {
		Point pt = new Point(x, y);
		SXSSFCell cell = getCell(sheet, pt, transpose);
		cell.setCellValue(ivalue);
	}

	public static void setValue(SXSSFSheet sheet, int x, int y, boolean transpose, double value) {
		Point pt = new Point(x, y);
		SXSSFCell cell = getCell(sheet, pt, transpose);
		cell.setCellValue(value);
	}

	public static void setValue(SXSSFSheet sheet, Point pt, boolean transpose, double value) {
		SXSSFCell cell = getCell(sheet, pt, transpose);
		cell.setCellValue(value);
	}

	public static double getValueDouble(SXSSFSheet sheet, Point pt, boolean transpose) {
		return getCell(sheet, pt, transpose).getNumericCellValue();
	}

	public static SXSSFCell getCell(SXSSFSheet sheet, int rownum, int colnum) {
		SXSSFRow row = getSheetRow(sheet, rownum);
		SXSSFCell cell = getRowCell(row, colnum);
		return cell;
	}

	public static SXSSFRow getSheetRow(SXSSFSheet sheet, int rownum) {
		SXSSFRow row = sheet.getRow(rownum);
		if (row == null)
			row = sheet.createRow(rownum);
		return row;
	}

	public static SXSSFCell getRowCell(SXSSFRow row, int cellnum) {
		SXSSFCell cell = row.getCell(cellnum);
		if (cell == null)
			cell = row.createCell(cellnum);
		return cell;
	}

	public static SXSSFCell getCell(SXSSFSheet sheet, Point point, boolean transpose) {
		Point pt = new Point(point);
		if (transpose) {
			int dummy = pt.x;
			pt.x = pt.y;
			pt.y = dummy;
		}
		return getCell(sheet, pt.y, pt.x);
	}

	public static void setFieldValue(SXSSFSheet sheet, int x, int y, boolean transpose, ExperimentProperties expDesc,
			EnumXLSColumnHeader field) {
		setValue(sheet, x, y + field.getValue(), transpose, expDesc.getExperimentField(field));
	}

}
