package plugins.fmp.multiSPOTS.tools.toExcel;

import java.awt.Point;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;

import icy.gui.frame.progress.ProgressFrame;
import plugins.fmp.multiSPOTS.experiment.Experiment;
import plugins.fmp.multiSPOTS.experiment.cages.Cage;
import plugins.fmp.multiSPOTS.experiment.cages.FlyPosition;
import plugins.fmp.multiSPOTS.experiment.cages.FlyPositions;
import plugins.fmp.multiSPOTS.tools.Comparators;
import plugins.fmp.multiSPOTS.tools.JComponents.JComboBoxExperiment;

public class XLSExportMoveResults extends XLSExport {
	JComboBoxExperiment expList = null;
	List<FlyPositions> rowsForOneExp = new ArrayList<FlyPositions>();

	public void exportToFile(String filename, XLSExportOptions opt) {
		System.out.println("XLSExportMove:exportToFile() start output");
		options = opt;
		expList = options.expList;

		boolean loadDrosoTrack = true;
		expList.loadListOfMeasuresFromAllExperiments(false, loadDrosoTrack);
		expList.chainExperimentsUsingKymoIndexes(options.collateSeries);
		expList.setFirstImageForAllExperiments(options.collateSeries);
		expAll = expList.get_MsTime_of_StartAndEnd_AllExperiments(options);

		ProgressFrame progress = new ProgressFrame("Export data to Excel");
		int nbexpts = expList.getItemCount();
		progress.setLength(nbexpts);

		try {
			int column = 1;
			int iSeries = 0;
			workbook = xlsInitWorkbook();
			for (int index = options.experimentIndexFirst; index <= options.experimentIndexLast; index++) {
				Experiment exp = expList.getItemAt(index);
				exp.load_SpotsMeasures();
				if (exp.chainToPreviousExperiment != null)
					continue;
				progress.setMessage("Export experiment " + (index + 1) + " of " + nbexpts);
				String charSeries = CellReference.convertNumToColString(iSeries);

				if (options.xyImage)
					getMoveDataAndExport(exp, column, charSeries, EnumXLSExportType.XYIMAGE);
				if (options.xyCage)
					getMoveDataAndExport(exp, column, charSeries, EnumXLSExportType.XYTOPCAGE);
				if (options.xyCapillaries)
					getMoveDataAndExport(exp, column, charSeries, EnumXLSExportType.XYTIPCAPS);
				if (options.ellipseAxes)
					getMoveDataAndExport(exp, column, charSeries, EnumXLSExportType.ELLIPSEAXES);
				if (options.distance)
					getMoveDataAndExport(exp, column, charSeries, EnumXLSExportType.DISTANCE);
				if (options.alive)
					getMoveDataAndExport(exp, column, charSeries, EnumXLSExportType.ISALIVE);
				if (options.sleep)
					getMoveDataAndExport(exp, column, charSeries, EnumXLSExportType.SLEEP);

				if (!options.collateSeries || exp.chainToPreviousExperiment == null)
					column += expList.maxSizeOfSpotsArrays + 2;
				iSeries++;
				progress.incPosition();
			}
			progress.setMessage("Save Excel file to disk... ");
			FileOutputStream fileOut = new FileOutputStream(filename);
			workbook.write(fileOut);
			fileOut.close();
			workbook.close();
			progress.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("XLSExportMove:exportToFile() - output finished");
	}

	private int getMoveDataAndExport(Experiment exp, int col0, String charSeries, EnumXLSExportType xlsExport) {
		getMoveDataFromOneSeriesOfExperiments(exp, xlsExport);
		XSSFSheet sheet = xlsInitSheet(xlsExport.toString(), xlsExport);
		int colmax = xlsExportResultsArrayToSheet(sheet, xlsExport, col0, charSeries);
		if (options.onlyalive) {
			trimDeadsFromRowMoveData(exp);
			sheet = xlsInitSheet(xlsExport.toString() + "_alive", xlsExport);
			xlsExportResultsArrayToSheet(sheet, xlsExport, col0, charSeries);
		}
		return colmax;
	}

	private void getMoveDescriptorsForOneExperiment(Experiment exp, EnumXLSExportType xlsOption) {
		// loop to get all capillaries into expAll and init rows for this experiment
		expAll.getCages().copy(exp.getCages());
		expAll.spotsArray.copy(exp.spotsArray);
		expAll.firstImage_FileTime = exp.firstImage_FileTime;
		expAll.lastImage_FileTime = exp.lastImage_FileTime;
		expAll.setResultsDirectory(exp.getResultsDirectory());
		expAll.expDesc.copyExperimentFields(exp.expDesc);

		Experiment expi = exp.chainToNextExperiment;
		while (expi != null) {
			expAll.getCages().mergeLists(expi.getCages());
			expAll.lastImage_FileTime = expi.lastImage_FileTime;
			expi = expi.chainToNextExperiment;
		}
		expAll.seqCamData.firstImage_ms = expAll.firstImage_FileTime.toMillis();
		expAll.seqCamData.lastImage_ms = expAll.lastImage_FileTime.toMillis();
		int nFrames = (int) ((expAll.seqCamData.lastImage_ms - expAll.seqCamData.firstImage_ms)
				/ options.buildExcelStepMs + 1);
		int ncages = expAll.getCages().cagesList.size();
		rowsForOneExp = new ArrayList<FlyPositions>(ncages);
		for (int i = 0; i < ncages; i++) {
			Cage cage = expAll.getCages().cagesList.get(i);
			FlyPositions row = new FlyPositions(cage.getRoi().getName(), xlsOption, nFrames, options.buildExcelStepMs);
			row.nflies = cage.cageNFlies;
			rowsForOneExp.add(row);
		}
		Collections.sort(rowsForOneExp, new Comparators.XYTaSeries_Name_Comparator());
	}

	private void getMoveDataFromOneSeriesOfExperiments(Experiment exp, EnumXLSExportType xlsOption) {
		getMoveDescriptorsForOneExperiment(exp, xlsOption);
		Experiment expi = exp.getFirstChainedExperiment(true);

		while (expi != null) {
			int len = 1
					+ (int) (expi.seqCamData.lastImage_ms - expi.seqCamData.firstImage_ms) / options.buildExcelStepMs;
			if (len == 0)
				continue;
			double pixelsize = 1.; // TODO 32. / expi.spotsArray.spotsList.get(0).pixels;

			List<FlyPositions> resultsArrayList = new ArrayList<FlyPositions>(expi.getCages().cagesList.size());
			for (Cage cage : expi.getCages().cagesList) {
				FlyPositions results = new FlyPositions(cage.getRoi().getName(), xlsOption, len,
						options.buildExcelStepMs);
				results.nflies = cage.cageNFlies;
				if (results.nflies > 0) {
					results.setPixelSize(pixelsize);

					switch (xlsOption) {
					case DISTANCE:
						results.excelComputeDistanceBetweenPoints(cage.flyPositions, (int) expi.seqCamData.binImage_ms,
								options.buildExcelStepMs);
						break;
					case ISALIVE:
						results.excelComputeIsAlive(cage.flyPositions, (int) expi.seqCamData.binImage_ms,
								options.buildExcelStepMs);
						break;
					case SLEEP:
						results.excelComputeSleep(cage.flyPositions, (int) expi.seqCamData.binImage_ms,
								options.buildExcelStepMs);
						break;
					case XYTOPCAGE:
						results.excelComputeNewPointsOrigin(cage.getCenterTopCage(), cage.flyPositions,
								(int) expi.seqCamData.binImage_ms, options.buildExcelStepMs);
						break;
//					case XYTIPCAPS:
//						results.excelComputeNewPointsOrigin(cage.getCenterTipCapillaries(exp.capillaries),
//								cage.flyPositions, (int) expi.seqCamData.binImage_ms, options.buildExcelStepMs);
//						break;
					case ELLIPSEAXES:
						results.excelComputeEllipse(cage.flyPositions, (int) expi.seqCamData.binImage_ms,
								options.buildExcelStepMs);
						break;
					case XYIMAGE:
					default:
						break;
					}

					results.convertPixelsToPhysicalValues();
					resultsArrayList.add(results);
				}
				// here add resultsArrayList to expAll
				addMoveResultsTo_rowsForOneExp(expi, resultsArrayList);
			}
			expi = expi.chainToNextExperiment;
		}
		for (FlyPositions row : rowsForOneExp)
			row.checkIsAliveFromAliveArray();
	}

	private FlyPositions getResultsArrayWithThatName(String testname, List<FlyPositions> resultsArrayList) {
		FlyPositions resultsFound = null;
		for (FlyPositions results : resultsArrayList) {
			if (!results.name.equals(testname))
				continue;
			resultsFound = results;
			break;
		}
		return resultsFound;
	}

	private void addMoveResultsTo_rowsForOneExp(Experiment expi, List<FlyPositions> resultsArrayList) {
		long start_Ms = expi.seqCamData.firstImage_ms - expAll.seqCamData.firstImage_ms;
		long end_Ms = expi.seqCamData.lastImage_ms - expAll.seqCamData.firstImage_ms;
		if (options.fixedIntervals) {
			if (start_Ms < options.startAll_Ms)
				start_Ms = options.startAll_Ms;
			if (start_Ms > expi.seqCamData.lastImage_ms)
				return;

			if (end_Ms > options.endAll_Ms)
				end_Ms = options.endAll_Ms;
			if (end_Ms > expi.seqCamData.firstImage_ms)
				return;
		}

		final long from_first_Ms = start_Ms + expAll.seqCamData.firstImage_ms;
		final long from_lastMs = end_Ms + expAll.seqCamData.firstImage_ms;
		final int to_first_index = (int) (from_first_Ms - expAll.seqCamData.firstImage_ms) / options.buildExcelStepMs;
		final int to_nvalues = (int) ((from_lastMs - from_first_Ms) / options.buildExcelStepMs) + 1;

		for (FlyPositions rowFlyPositions : rowsForOneExp) {
			FlyPositions results = getResultsArrayWithThatName(rowFlyPositions.name, resultsArrayList);
			if (results != null) {
				if (options.collateSeries && options.padIntervals && expi.chainToPreviousExperiment != null)
					padWithLastPreviousValue(rowFlyPositions, to_first_index);

				for (long fromTime = from_first_Ms; fromTime <= from_lastMs; fromTime += options.buildExcelStepMs) {
					int from_i = (int) ((fromTime - from_first_Ms) / options.buildExcelStepMs);
					if (from_i >= results.flyPositionList.size())
						break;
					FlyPosition aVal = results.flyPositionList.get(from_i);
					int to_i = (int) ((fromTime - expAll.seqCamData.firstImage_ms) / options.buildExcelStepMs);
					if (to_i >= rowFlyPositions.flyPositionList.size())
						break;
					if (to_i < 0)
						continue;
					rowFlyPositions.flyPositionList.get(to_i).copy(aVal);
				}

			} else {
				if (options.collateSeries && options.padIntervals && expi.chainToPreviousExperiment != null) {
					FlyPosition posok = padWithLastPreviousValue(rowFlyPositions, to_first_index);
					int nvalues = to_nvalues;
					if (posok != null) {
						if (nvalues > rowFlyPositions.flyPositionList.size())
							nvalues = rowFlyPositions.flyPositionList.size();
						int tofirst = to_first_index;
						int tolast = tofirst + nvalues;
						if (tolast > rowFlyPositions.flyPositionList.size())
							tolast = rowFlyPositions.flyPositionList.size();
						for (int toi = tofirst; toi < tolast; toi++)
							rowFlyPositions.flyPositionList.get(toi).copy(posok);
					}
				}
			}
		}
	}

	private FlyPosition padWithLastPreviousValue(FlyPositions row, int transfer_first_index) {
		FlyPosition posok = null;
		int index = getIndexOfFirstNonEmptyValueBackwards(row, transfer_first_index);
		if (index >= 0) {
			posok = row.flyPositionList.get(index);
			for (int i = index + 1; i < transfer_first_index; i++) {
				FlyPosition pos = row.flyPositionList.get(i);
				pos.copy(posok);
				pos.bPadded = true;
			}
		}
		return posok;
	}

	private int getIndexOfFirstNonEmptyValueBackwards(FlyPositions row, int fromindex) {
		int index = -1;
		for (int i = fromindex; i >= 0; i--) {
			FlyPosition pos = row.flyPositionList.get(i);
			if (!Double.isNaN(pos.rectPosition.getX())) {
				index = i;
				break;
			}
		}
		return index;
	}

	private void trimDeadsFromRowMoveData(Experiment exp) {
		for (Cage cage : exp.getCages().cagesList) {
			int cagenumber = Integer.valueOf(cage.getRoi().getName().substring(4));
			int ilastalive = 0;
			if (cage.cageNFlies > 0) {
				Experiment expi = exp;
				while (expi.chainToNextExperiment != null
						&& expi.chainToNextExperiment.getCages().isFlyAlive(cagenumber)) {
					expi = expi.chainToNextExperiment;
				}
				long lastIntervalFlyAlive_Ms = expi.getCages().getLastIntervalFlyAlive(cagenumber)
						* expi.getCages().detectBin_Ms;
				long lastMinuteAlive = lastIntervalFlyAlive_Ms + expi.seqCamData.firstImage_ms
						- expAll.seqCamData.firstImage_ms;
				ilastalive = (int) (lastMinuteAlive / options.buildExcelStepMs);
			}
			for (FlyPositions row : rowsForOneExp) {
				int rowCageNumber = Integer.valueOf(row.name.substring(4));
				if (rowCageNumber == cagenumber) {
					row.clearValues(ilastalive + 1);
				}
			}
		}
	}

	private int xlsExportResultsArrayToSheet(XSSFSheet sheet, EnumXLSExportType xlsExportOption, int col0,
			String charSeries) {
		Point pt = new Point(col0, 0);
		writeExperiment_descriptors(expAll, charSeries, sheet, pt, xlsExportOption);
		pt = writeData2(sheet, xlsExportOption, pt);
		return pt.x;
	}

	private Point writeData2(XSSFSheet sheet, EnumXLSExportType option, Point pt_main) {
		int rowseries = pt_main.x + 2;
		int columndataarea = pt_main.y;
		Point pt = new Point(pt_main);
		writeRows(sheet, columndataarea, rowseries, pt);
		pt_main.x = pt.x + 1;
		return pt_main;
	}

	private void writeRows(XSSFSheet sheet, int column_dataArea, int rowSeries, Point pt) {
		boolean transpose = options.transpose;
		for (FlyPositions row : rowsForOneExp) {
			pt.y = column_dataArea;
			int col = getRowIndexFromCageName(row.name) * 2;
			pt.x = rowSeries + col;
			if (row.nflies < 1)
				continue;

			long last = expAll.seqCamData.lastImage_ms - expAll.seqCamData.firstImage_ms;
			if (options.fixedIntervals)
				last = options.endAll_Ms - options.startAll_Ms;

			for (long coltime = 0; coltime <= last; coltime += options.buildExcelStepMs, pt.y++) {
				int i_from = (int) (coltime / options.buildExcelStepMs);
				if (i_from >= row.flyPositionList.size())
					break;

				double valueL = Double.NaN;
				double valueR = Double.NaN;
				FlyPosition pos = row.flyPositionList.get(i_from);

				switch (row.exportType) {
				case DISTANCE:
					valueL = pos.distance;
					valueR = valueL;
					break;
				case ISALIVE:
					valueL = pos.bAlive ? 1 : 0;
					valueR = valueL;
					break;
				case SLEEP:
					valueL = pos.bSleep ? 1 : 0;
					valueR = valueL;
					break;
				case XYTOPCAGE:
				case XYTIPCAPS:
				case XYIMAGE:
					valueL = pos.rectPosition.getX() + pos.rectPosition.getWidth() / 2.;
					valueR = pos.rectPosition.getY() + pos.rectPosition.getHeight() / 2.;
					break;
				case ELLIPSEAXES:
					valueL = pos.axis1;
					valueR = pos.axis2;
					break;
				default:
					break;
				}

				if (!Double.isNaN(valueL)) {
					XLSUtils.setValue(sheet, pt, transpose, valueL);
					if (pos.bPadded)
						XLSUtils.getCell(sheet, pt, transpose).setCellStyle(xssfCellStyle_red);
				}
				if (!Double.isNaN(valueR)) {
					pt.x++;
					XLSUtils.setValue(sheet, pt, transpose, valueR);
					if (pos.bPadded)
						XLSUtils.getCell(sheet, pt, transpose).setCellStyle(xssfCellStyle_red);
					pt.x--;
				}
			}
			pt.x += 2;
		}
	}

}
