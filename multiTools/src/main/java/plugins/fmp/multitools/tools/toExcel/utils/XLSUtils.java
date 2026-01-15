package plugins.fmp.multitools.tools.toExcel.utils;

import java.awt.Point;

import org.apache.poi.xssf.streaming.SXSSFCell;
import org.apache.poi.xssf.streaming.SXSSFRow;
import org.apache.poi.xssf.streaming.SXSSFSheet;

import plugins.fmp.multitools.experiment.ExperimentProperties;
import plugins.fmp.multitools.tools.toExcel.enums.EnumXLSColumnHeader;

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
		String text = expDesc.getField(field);
		setValue(sheet, x, y + field.getValue(), transpose, text);
	}

	/**
	 * Sets a value at a specific column position using Point and
	 * EnumXLSColumnHeader. This encapsulates the arithmetic (y + column.getValue())
	 * for better readability.
	 * 
	 * @param sheet     The sheet to write to
	 * @param pt        The starting point (x, y)
	 * @param column    The column header enum
	 * @param transpose Whether to transpose coordinates
	 * @param value     The value to write
	 */
	public static void setValueAtColumn(SXSSFSheet sheet, Point pt, EnumXLSColumnHeader column, boolean transpose,
			String value) {
		setValue(sheet, pt.x, pt.y + column.getValue(), transpose, value);
	}

	/**
	 * Sets a value at a specific column position using Point and
	 * EnumXLSColumnHeader.
	 * 
	 * @param sheet     The sheet to write to
	 * @param pt        The starting point (x, y)
	 * @param column    The column header enum
	 * @param transpose Whether to transpose coordinates
	 * @param value     The value to write
	 */
	public static void setValueAtColumn(SXSSFSheet sheet, Point pt, EnumXLSColumnHeader column, boolean transpose,
			int value) {
		setValue(sheet, pt.x, pt.y + column.getValue(), transpose, value);
	}

	/**
	 * Sets a value at a specific column position using Point and
	 * EnumXLSColumnHeader.
	 * 
	 * @param sheet     The sheet to write to
	 * @param pt        The starting point (x, y)
	 * @param column    The column header enum
	 * @param transpose Whether to transpose coordinates
	 * @param value     The value to write
	 */
	public static void setValueAtColumn(SXSSFSheet sheet, Point pt, EnumXLSColumnHeader column, boolean transpose,
			double value) {
		setValue(sheet, pt.x, pt.y + column.getValue(), transpose, value);
	}

	/**
	 * Sets a field value from ExperimentProperties at a specific column position.
	 * 
	 * @param sheet     The sheet to write to
	 * @param pt        The starting point (x, y)
	 * @param transpose Whether to transpose coordinates
	 * @param expDesc   The experiment properties
	 * @param field     The field enum
	 */
	public static void setFieldValueAtColumn(SXSSFSheet sheet, Point pt, boolean transpose,
			ExperimentProperties expDesc, EnumXLSColumnHeader field) {
		String text = expDesc.getField(field);
		setValueAtColumn(sheet, pt, field, transpose, text);
	}

	public static void setFieldValueAtColumn(SXSSFSheet sheet, Point pt, boolean transpose,
			ExperimentProperties expDesc, EnumXLSColumnHeader field, String charString) {
		String text;
		if (charString == null)
			text = expDesc.getField(field);
		else
			text = expDesc.getField(field) + "_" + charString;
		setValueAtColumn(sheet, pt, field, transpose, text);
	}

}
