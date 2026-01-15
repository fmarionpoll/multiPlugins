package plugins.fmp.multiSPOTS.tools.toExcel;

import java.awt.Point;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;

import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import plugins.fmp.multiSPOTS.experiment.Experiment;
import plugins.fmp.multiSPOTS.experiment.cages.Cage;
import plugins.fmp.multiSPOTS.experiment.spots.Spot;
import plugins.fmp.multiSPOTS.tools.Comparators;
import plugins.fmp.multiSPOTS.tools.JComponents.JComboBoxExperiment;

public class XLSExport {
	protected XLSExportOptions options = null;
	protected Experiment expAll = null;

	XSSFCellStyle xssfCellStyle_red = null;
	XSSFCellStyle xssfCellStyle_blue = null;
	XSSFFont font_red = null;
	XSSFFont font_blue = null;
	XSSFWorkbook workbook = null;

	JComboBoxExperiment expList = null;

	// ------------------------------------------------

	protected Point writeExperiment_descriptors(Experiment exp, String charSeries, XSSFSheet sheet, Point pt,
			EnumXLSExportType xlsExportOption) {
		boolean transpose = options.transpose;
		int row = pt.y;
		int col0 = pt.x;
		XLSUtils.setValue(sheet, pt, transpose, "..");
		pt.x++;
		XLSUtils.setValue(sheet, pt, transpose, "..");
		pt.x++;
		int colseries = pt.x;
		int len = EnumXLSColumnHeader.values().length;
		for (int i = 0; i < len; i++) {
			XLSUtils.setValue(sheet, pt, transpose, "--");
			pt.x++;
		}
		pt.x = colseries;

		String filename = exp.getResultsDirectory();
		if (filename == null)
			filename = exp.seqCamData.getImagesDirectory();
		Path path = Paths.get(filename);

		SimpleDateFormat df = new SimpleDateFormat("MM/dd/yyyy");
		String date = df.format(exp.chainImageFirst_ms);

		String name0 = path.toString();
		int pos = name0.indexOf("cam");
		String cam = "-";
		if (pos > 0) {
			int pos5 = pos + 5;
			if (pos5 >= name0.length())
				pos5 = name0.length() - 1;
			cam = name0.substring(pos, pos5);
		}

		String sheetName = sheet.getSheetName();

		int rowmax = -1;
		for (EnumXLSColumnHeader dumb : EnumXLSColumnHeader.values()) {
			if (rowmax < dumb.getValue())
				rowmax = dumb.getValue();
		}

		List<Spot> spotsList = exp.spotsArray.spotsList;
		for (int t = 0; t < spotsList.size(); t++) {
			Spot spot = spotsList.get(t);
			String name = spot.getRoi().getName();
			int col = getRowIndexFromSpotName(name);
			if (col >= 0)
				pt.x = colseries + col;
			int x = pt.x;
			int y = row;
			XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.PATH.getValue(), transpose, name0);
			XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.DATE.getValue(), transpose, date);
			XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.CAM.getValue(), transpose, cam);

			XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.EXP_BOXID.getValue(), transpose,
					exp.expDesc.getExperimentField(EnumXLSColumnHeader.EXP_BOXID));
			XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.EXP_EXPT.getValue(), transpose,
					exp.expDesc.getExperimentField(EnumXLSColumnHeader.EXP_EXPT));
			XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.EXP_STIM.getValue(), transpose,
					exp.expDesc.getExperimentField(EnumXLSColumnHeader.EXP_STIM));
			XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.EXP_CONC.getValue(), transpose,
					exp.expDesc.getExperimentField(EnumXLSColumnHeader.EXP_CONC));
			XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.EXP_STRAIN.getValue(), transpose,
					exp.expDesc.getExperimentField(EnumXLSColumnHeader.EXP_STRAIN));
			XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.EXP_SEX.getValue(), transpose,
					exp.expDesc.getExperimentField(EnumXLSColumnHeader.EXP_SEX));
			XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.EXP_COND1.getValue(), transpose,
					exp.expDesc.getExperimentField(EnumXLSColumnHeader.EXP_COND1));
			XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.EXP_COND2.getValue(), transpose,
					exp.expDesc.getExperimentField(EnumXLSColumnHeader.EXP_COND2));

			XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.CAP_VOLUME.getValue(), transpose, spot.spotVolume); // exp.spotsArray.spotsDescription.volume);
			XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.CAP_PIXELS.getValue(), transpose, spot.spotNPixels); // exp.spotsArray.spotsDescription.pixels);

			XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.CAGEPOS.getValue(), transpose,
					spot.getCagePosition(xlsExportOption));
			outputStimAndConc_according_to_DataOption(sheet, xlsExportOption, spot, transpose, x, y);

			XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.SPOT_CAGEID.getValue(), transpose, spot.cageID);
			XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.CAGEID.getValue(), transpose, charSeries + spot.cageID);
			XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.SPOT_NFLIES.getValue(), transpose, spot.spotNFlies);

			XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.DUM4.getValue(), transpose, sheetName);
			XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.CHOICE_NOCHOICE.getValue(), transpose,
					desc_getChoiceTestType(spotsList, t));
			if (exp.getCages().cagesList.size() > t / 2) {
				Cage cage = exp.getCages().cagesList.get(t / 2); // cap.capCageID);
				XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.CAGE_STRAIN.getValue(), transpose,
						cage.strCageStrain);
				XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.CAGE_SEX.getValue(), transpose, cage.strCageSex);
				XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.CAGE_AGE.getValue(), transpose, cage.cageAge);
				XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.CAGE_COMMENT.getValue(), transpose,
						cage.strCageComment);
			}
		}
		pt.x = col0;
		pt.y = rowmax + 1;
		return pt;
	}

	private String desc_getChoiceTestType(List<Spot> spotsList, int t) {
		Spot spot = spotsList.get(t);
		String choiceText = "..";
		int side = spot.cagePosition;
		if (side == 0)
			t = t + 1;
		else
			t = t - 1;
		if (t >= 0 && t < spotsList.size()) {
			Spot othercap = spotsList.get(t);
			int otherSide = othercap.cagePosition;
			if (otherSide != side) {
				if (spot.spotStim.equals(othercap.spotStim) && spot.spotConc.equals(othercap.spotConc))
					choiceText = "no-choice";
				else
					choiceText = "choice";
			}
		}
		return choiceText;
	}

	private void outputStimAndConc_according_to_DataOption(XSSFSheet sheet, EnumXLSExportType xlsExportOption,
			Spot spot, boolean transpose, int x, int y) {
		switch (xlsExportOption) {
		case TOPLEVEL_LR:
		case TOPLEVELDELTA_LR:
			if (spot.cagePosition == 0)
				XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.CAP_STIM.getValue(), transpose, "L+R");
			else
				XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.CAP_STIM.getValue(), transpose, "(L-R)/(L+R)");
			XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.CAP_CONC.getValue(), transpose,
					spot.spotStim + ": " + spot.spotConc);
			break;

		default:
			XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.CAP_STIM.getValue(), transpose, spot.spotStim);
			XLSUtils.setValue(sheet, x, y + EnumXLSColumnHeader.CAP_CONC.getValue(), transpose, spot.spotConc);
			break;
		}
	}

	int writeTopRow_descriptors(XSSFSheet sheet) {
		Point pt = new Point(0, 0);
		int x = 0;
		boolean transpose = options.transpose;
		int nextcol = -1;
		for (EnumXLSColumnHeader dumb : EnumXLSColumnHeader.values()) {
			XLSUtils.setValue(sheet, x, dumb.getValue(), transpose, dumb.getName());
			if (nextcol < dumb.getValue())
				nextcol = dumb.getValue();
		}
		pt.y = nextcol + 1;
		return pt.y;
	}

	void writeTopRow_timeIntervals(XSSFSheet sheet, int row, EnumXLSExportType xlsExport) {
		switch (xlsExport) {
		case AUTOCORREL:
		case CROSSCORREL:
		case AUTOCORREL_LR:
		case CROSSCORREL_LR:
			writeTopRow_timeIntervals_Correl(sheet, row);
			break;
		default:
			writeTopRow_timeIntervals_Default(sheet, row);
			break;
		}
	}

	void writeTopRow_timeIntervals_Correl(XSSFSheet sheet, int row) {
		boolean transpose = options.transpose;
		Point pt = new Point(0, row);
		long interval = -options.nBinsCorrelation;
		while (interval < options.nBinsCorrelation) {
			int i = (int) interval;
			XLSUtils.setValue(sheet, pt, transpose, "t" + i);
			pt.y++;
			interval += 1;
		}
	}

	void writeTopRow_timeIntervals_Default(XSSFSheet sheet, int row) {
		boolean transpose = options.transpose;
		Point pt = new Point(0, row);
		long duration = expAll.seqCamData.lastImage_ms - expAll.seqCamData.firstImage_ms;
		long interval = 0;
		while (interval < duration) {
			int i = (int) (interval / options.buildExcelUnitMs);
			XLSUtils.setValue(sheet, pt, transpose, "t" + i);
			pt.y++;
			interval += options.buildExcelStepMs;
		}
	}

	protected int desc_getCageFromSpotRoiName(String name) {
		if (!name.contains("spot"))
			return -1;
		String num = name.substring(4, 6);
		int numFromName = Integer.valueOf(num);
		return numFromName;
	}

	protected int getRowIndexFromSpotName(String name) {
		if (!name.contains("spot"))
			return -1;
		String num = name.substring(4, 6);
		int numFromName = Integer.valueOf(num);
		String num2 = name.substring(7, 9);
		int numFromName2 = Integer.valueOf(num2);
		numFromName = numFromName * 2 + numFromName2;
		return numFromName;
	}

	protected int getRowIndexFromCageName(String name) {
		if (!name.contains("cage"))
			return -1;
		String num = name.substring(4, name.length());
		int numFromName = Integer.valueOf(num);
		return numFromName;
	}

	protected Point getCellXCoordinateFromDataName(XLSResults xlsResults, Point pt_main, int colseries) {
		int col = getRowIndexFromSpotName(xlsResults.name);
		if (col >= 0)
			pt_main.x = colseries + col;
		return pt_main;
	}

	protected int getCageFromKymoFileName(String name) {
		if (!name.contains("line") || !name.contains("spot"))
			return -1;
		return Integer.valueOf(name.substring(4, 5));
	}

	XSSFWorkbook xlsInitWorkbook() {
		XSSFWorkbook workbook = new XSSFWorkbook();
		workbook.setMissingCellPolicy(Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
		xssfCellStyle_red = workbook.createCellStyle();
		font_red = workbook.createFont();
		font_red.setColor(HSSFColor.HSSFColorPredefined.RED.getIndex());
		xssfCellStyle_red.setFont(font_red);

		xssfCellStyle_blue = workbook.createCellStyle();
		font_blue = workbook.createFont();
		font_blue.setColor(HSSFColor.HSSFColorPredefined.BLUE.getIndex());
		xssfCellStyle_blue.setFont(font_blue);
		return workbook;
	}

	XSSFSheet xlsInitSheet(String title, EnumXLSExportType xlsExport) {
		XSSFSheet sheet = workbook.getSheet(title);
		if (sheet == null) {
			sheet = workbook.createSheet(title);
			int row = writeTopRow_descriptors(sheet);
			writeTopRow_timeIntervals(sheet, row, xlsExport);
		}
		return sheet;
	}

	protected int getDataAndExport(Experiment exp, int col0, String charSeries, EnumXLSExportType exportType) {
		options.exportType = exportType;
		XLSResultsArray rowListForOneExp = getSpotsDataFromOneExperimentSeries(exp, options);
		XSSFSheet sheet = xlsInitSheet(exportType.toString(), exportType);
		int colmax = xlsExportResultsArrayToSheet(rowListForOneExp, sheet, exportType, col0, charSeries);
		if (options.onlyalive) {
			trimDeadsFromArrayList(rowListForOneExp, exp);
			sheet = xlsInitSheet(exportType.toString() + "_alive", exportType);
			xlsExportResultsArrayToSheet(rowListForOneExp, sheet, exportType, col0, charSeries);
		}
		if (options.sumPerCage) {
			combineDataForOneCage(rowListForOneExp, exp);
			sheet = xlsInitSheet(exportType.toString() + "_cage", exportType);
			xlsExportResultsArrayToSheet(rowListForOneExp, sheet, exportType, col0, charSeries);
		}
		return colmax;
	}

	private XLSResultsArray getSpotDescriptorsForOneExperiment(Experiment exp, EnumXLSExportType xlsOption) {
		if (expAll == null)
			return null;

		// loop to get all spots into expAll and init rows for this experiment
		expAll.getCages().copy(exp.getCages());
		expAll.spotsArray.copy(exp.spotsArray);
		expAll.chainImageFirst_ms = exp.chainImageFirst_ms;
		expAll.expDesc.copyExperimentFields(exp.expDesc);
		expAll.setResultsDirectory(exp.getResultsDirectory());

		Experiment expi = exp.chainToNextExperiment;
		while (expi != null) {
			expAll.spotsArray.mergeLists(expi.spotsArray);
			expi = expi.chainToNextExperiment;
		}

		int nFrames = (int) ((expAll.seqCamData.lastImage_ms - expAll.seqCamData.firstImage_ms)
				/ options.buildExcelStepMs + 1);
		int nspots = expAll.spotsArray.spotsList.size();
		XLSResultsArray rowListForOneExp = new XLSResultsArray(nspots);
		for (int i = 0; i < nspots; i++) {
			Spot spot = expAll.spotsArray.spotsList.get(i);
			XLSResults rowResults = new XLSResults(spot.getRoi().getName(), spot.spotNFlies, spot.cageID,
					spot.cagePosition, xlsOption, nFrames);
			rowResults.stimulus = spot.spotStim;
			rowResults.concentration = spot.spotConc;
			rowResults.cageID = spot.cageID;
			rowListForOneExp.resultsList.add(rowResults);
		}
		Collections.sort(rowListForOneExp.resultsList, new Comparators.XLSResults_Name_Comparator());
		return rowListForOneExp;
	}

	private XLSResultsArray getSpotsDescriptorsForOneExperiment(Experiment exp, EnumXLSExportType xlsOption) {
		if (expAll == null)
			return null;

		// loop to get all spots into expAll and init rows for this experiment
		expAll.getCages().copy(exp.getCages());
		expAll.spotsArray.copy(exp.spotsArray);
		expAll.chainImageFirst_ms = exp.chainImageFirst_ms;
		expAll.expDesc.copyExperimentFields(exp.expDesc);
		expAll.setResultsDirectory(exp.getResultsDirectory());

		Experiment expi = exp.chainToNextExperiment;
		while (expi != null) {
			expAll.spotsArray.mergeLists(expi.spotsArray);
			expi = expi.chainToNextExperiment;
		}

		int nFrames = (int) ((expAll.seqCamData.lastImage_ms - expAll.seqCamData.firstImage_ms)
				/ options.buildExcelStepMs + 1);
		int nspots = expAll.spotsArray.spotsList.size();
		XLSResultsArray rowListForOneExp = new XLSResultsArray(nspots);
		for (int i = 0; i < nspots; i++) {
			Spot spot = expAll.spotsArray.spotsList.get(i);
			XLSResults rowResults = new XLSResults(spot.getRoi().getName(), spot.spotNFlies, spot.cageID,
					spot.cagePosition, xlsOption, nFrames);
			rowResults.stimulus = spot.spotStim;
			rowResults.concentration = spot.spotConc;
			rowResults.cageID = spot.cageID;
			rowListForOneExp.resultsList.add(rowResults);
		}
		Collections.sort(rowListForOneExp.resultsList, new Comparators.XLSResults_Name_Comparator());
		return rowListForOneExp;
	}

	public XLSResultsArray getSpotDataFromOneExperiment(Experiment exp, EnumXLSExportType exportType,
			XLSExportOptions options) {
		this.options = options;
		expAll = new Experiment();
		expAll.seqCamData.lastImage_ms = exp.seqCamData.lastImage_ms;
		expAll.seqCamData.firstImage_ms = exp.seqCamData.firstImage_ms;
		return getSpotDataFromOneExperimentSeries(exp, exportType);
	}

	public XLSResultsArray getSpotsDataFromOneExperiment(Experiment exp, XLSExportOptions options) {
		this.options = options;
		expAll = new Experiment();
		expAll.seqCamData.lastImage_ms = exp.seqCamData.lastImage_ms;
		expAll.seqCamData.firstImage_ms = exp.seqCamData.firstImage_ms;
		return getSpotsDataFromOneExperimentSeries(exp, options);
	}

	private void exportError(Experiment expi, int nOutputFrames) {
		String error = "XLSExport:ExportError() ERROR in " + expi.getResultsDirectory() + "\n nOutputFrames="
				+ nOutputFrames + " kymoFirstCol_Ms=" + expi.seqCamData.binFirst_ms + " kymoLastCol_Ms="
				+ expi.seqCamData.binLast_ms;
		System.out.println(error);
	}

	private int getNOutputFrames(Experiment expi) {
		int nOutputFrames = (int) ((expi.seqCamData.binLast_ms - expi.seqCamData.binFirst_ms) / options.buildExcelStepMs
				+ 1);
		if (nOutputFrames <= 1) {
			if (expi.seqSpotKymos.imageWidthMax == 0)
				expi.loadKymographs();
			expi.seqCamData.binLast_ms = expi.seqCamData.binFirst_ms
					+ expi.seqSpotKymos.imageWidthMax * expi.seqCamData.binDuration_ms;
			if (expi.seqCamData.binLast_ms <= 0)
				exportError(expi, -1);
			nOutputFrames = (int) ((expi.seqCamData.binLast_ms - expi.seqCamData.binFirst_ms) / options.buildExcelStepMs
					+ 1);
			if (nOutputFrames <= 1) {
				nOutputFrames = expi.seqCamData.nTotalFrames;
				exportError(expi, nOutputFrames);
			}
		}
		return nOutputFrames;
	}

	private XLSResultsArray getSpotDataFromOneExperimentSeries(Experiment exp, EnumXLSExportType xlsExportType) {
		XLSResultsArray rowListForOneExp = getSpotDescriptorsForOneExperiment(exp, xlsExportType);
		Experiment expi = exp.getFirstChainedExperiment(true);

		while (expi != null) {
			int nOutputFrames = getNOutputFrames(expi);
			if (nOutputFrames > 1) {
				XLSResultsArray resultsArrayList = new XLSResultsArray(expi.spotsArray.spotsList.size());
				options.compensateEvaporation = false;
				resultsArrayList.getSpotsArrayResults1(expi.spotsArray, nOutputFrames, exp.seqCamData.binDuration_ms,
						options);
				addResultsTo_rowsForOneExp(rowListForOneExp, expi, resultsArrayList);
			}
			expi = expi.chainToNextExperiment;
		}

//		switch (xlsExportType) {
//			case AREA_NPIXELS_DELTA: // TODO
//				rowListForOneExp.subtractDeltaT(1, 1); //options.buildExcelStepMs);
//				break;
//			default:
//				break;
//		}
		return rowListForOneExp;
	}

	private XLSResultsArray getSpotsDataFromOneExperimentSeries(Experiment exp, XLSExportOptions options) {
		XLSResultsArray rowListForOneExp = getSpotsDescriptorsForOneExperiment(exp, options.exportType);
		Experiment expi = exp.getFirstChainedExperiment(true);
		while (expi != null) {
			int nOutputFrames = getNOutputFrames(expi);
			if (nOutputFrames > 1) {
				XLSResultsArray resultsArrayList = new XLSResultsArray(expi.spotsArray.spotsList.size());
				options.compensateEvaporation = false;
				resultsArrayList.getSpotsArrayResults1(expi.spotsArray, nOutputFrames, exp.seqCamData.binDuration_ms,
						options);
				addResultsTo_rowsForOneExp(rowListForOneExp, expi, resultsArrayList);
			}
			expi = expi.chainToNextExperiment;
		}
		return rowListForOneExp;
	}

	private XLSResults getResultsArrayWithThatName(String testname, XLSResultsArray resultsArrayList) {
		XLSResults resultsFound = null;
		for (XLSResults results : resultsArrayList.resultsList) {
			if (results.name.equals(testname)) {
				resultsFound = results;
				break;
			}
		}
		return resultsFound;
	}

	private void addResultsTo_rowsForOneExp(XLSResultsArray rowListForOneExp, Experiment expi,
			XLSResultsArray resultsArrayList) {
		if (resultsArrayList.resultsList.size() < 1)
			return;

		EnumXLSExportType xlsoption = resultsArrayList.getRow(0).exportType;

		long offsetChain = expi.seqCamData.firstImage_ms - expi.chainImageFirst_ms;
		long start_Ms = expi.seqCamData.binFirst_ms + offsetChain; // TODO check when collate?
		long end_Ms = expi.seqCamData.binLast_ms + offsetChain;
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

		// TODO check this
		final long from_first_Ms = start_Ms - offsetChain;
		final long from_lastMs = end_Ms - offsetChain;
		final int to_first_index = (int) (start_Ms / options.buildExcelStepMs);
		final int to_nvalues = (int) ((end_Ms - start_Ms) / options.buildExcelStepMs) + 1;

		for (int iRow = 0; iRow < rowListForOneExp.size(); iRow++) {
			XLSResults row = rowListForOneExp.getRow(iRow);
			XLSResults results = getResultsArrayWithThatName(row.name, resultsArrayList);
			if (results != null && results.valuesOut != null) {
				double dvalue = 0.;
				switch (xlsoption) {
				case TOPLEVEL:
				case TOPLEVEL_LR:
				case TOPLEVELDELTA:
				case TOPLEVELDELTA_LR:
					if (options.collateSeries && options.padIntervals && expi.chainToPreviousExperiment != null)
						dvalue = padWithLastPreviousValue(row, to_first_index);
					break;
				default:
					break;
				}

				int icolTo = 0;
				if (options.collateSeries || options.absoluteTime)
					icolTo = to_first_index;
				for (long fromTime = from_first_Ms; fromTime <= from_lastMs; fromTime += options.buildExcelStepMs, icolTo++) {
					int from_i = (int) Math
							.round(((double) (fromTime - from_first_Ms)) / ((double) options.buildExcelStepMs));
					if (from_i >= results.valuesOut.length)
						break;
					// TODO check how this can happen
					if (from_i < 0)
						continue;
					double value = results.valuesOut[from_i] + dvalue;
					if (icolTo >= row.valuesOut.length)
						break;
					row.valuesOut[icolTo] = value;
				}

			} else {
				if (options.collateSeries && options.padIntervals && expi.chainToPreviousExperiment != null) {
					double dvalue = padWithLastPreviousValue(row, to_first_index);
					int tofirst = (int) to_first_index;
					int tolast = (int) (tofirst + to_nvalues);
					if (tolast > row.valuesOut.length)
						tolast = row.valuesOut.length;
					for (int toi = tofirst; toi < tolast; toi++)
						row.valuesOut[toi] = dvalue;
				}
			}
		}
	}

	private double padWithLastPreviousValue(XLSResults row, long to_first_index) {
		double dvalue = 0;
		if (to_first_index >= row.valuesOut.length)
			return dvalue;

		int index = getIndexOfFirstNonEmptyValueBackwards(row, to_first_index);
		if (index >= 0) {
			dvalue = row.valuesOut[index];
			for (int i = index + 1; i < to_first_index; i++) {
				row.valuesOut[i] = dvalue;
				row.padded_out[i] = true;
			}
		}
		return dvalue;
	}

	private int getIndexOfFirstNonEmptyValueBackwards(XLSResults row, long fromindex) {
		int index = -1;
		int ifrom = (int) fromindex;
		for (int i = ifrom; i >= 0; i--) {
			if (!Double.isNaN(row.valuesOut[i])) {
				index = i;
				break;
			}
		}
		return index;
	}

	private void trimDeadsFromArrayList(XLSResultsArray rowListForOneExp, Experiment exp) {
		for (Cage cage : exp.getCages().cagesList) {
			String roiname = cage.getRoi().getName();
			if (roiname.length() < 4 || !roiname.substring(0, 4).contains("cage"))
				continue;

			String cagenumberString = roiname.substring(4);
			int cagenumber = Integer.valueOf(cagenumberString);
			int ilastalive = 0;
			if (cage.cageNFlies > 0) {
				Experiment expi = exp;
				while (expi.chainToNextExperiment != null
						&& expi.chainToNextExperiment.getCages().isFlyAlive(cagenumber)) {
					expi = expi.chainToNextExperiment;
				}
				int lastIntervalFlyAlive = expi.getCages().getLastIntervalFlyAlive(cagenumber);
				int lastMinuteAlive = (int) (lastIntervalFlyAlive * expi.seqCamData.binImage_ms
						+ (expi.seqCamData.firstImage_ms - expAll.seqCamData.firstImage_ms));
				ilastalive = (int) (lastMinuteAlive / expAll.seqCamData.binDuration_ms);
			}
			if (ilastalive > 0)
				ilastalive += 1;

			for (int iRow = 0; iRow < rowListForOneExp.size(); iRow++) {
				XLSResults row = rowListForOneExp.getRow(iRow);
				if (desc_getCageFromSpotRoiName(row.name) == cagenumber)
					row.clearValues(ilastalive);
			}
		}
	}

	private void combineDataForOneCage(XLSResultsArray rowListForOneExp, Experiment exp) {
		for (int iRow0 = 0; iRow0 < rowListForOneExp.size(); iRow0++) {
			XLSResults row_master = rowListForOneExp.getRow(iRow0);
			if (row_master.nflies == 0 || row_master.valuesOut == null)
				continue;

			for (int iRow = 0; iRow < rowListForOneExp.size(); iRow++) {
				XLSResults row = rowListForOneExp.getRow(iRow);
				if (row.nflies == 0 || row.valuesOut == null)
					continue;
				if (row.cageID != row_master.cageID)
					continue;
				if (row.name.equals(row_master.name))
					continue;
				if (row.stimulus.equals(row_master.stimulus) && row.concentration.equals(row_master.concentration)) {
					row_master.sumValues_out(row);
					row.clearAll();
				}
			}
		}
	}

	private int xlsExportResultsArrayToSheet(XLSResultsArray rowListForOneExp, XSSFSheet sheet,
			EnumXLSExportType xlsExportOption, int col0, String charSeries) {
		Point pt = new Point(col0, 0);
		writeExperiment_descriptors(expAll, charSeries, sheet, pt, xlsExportOption);
		pt = writeExperiment_data(rowListForOneExp, sheet, pt);
		return pt.x;
	}

	private Point writeExperiment_data(XLSResultsArray rowListForOneExp, XSSFSheet sheet, Point pt_main) {
		int rowSeries = pt_main.x + 2;
		int column_dataArea = pt_main.y;
		Point pt = new Point(pt_main);
		writeExperiment_data_simpleRows(rowListForOneExp, sheet, column_dataArea, rowSeries, pt);
		pt_main.x = pt.x + 1;
		return pt_main;
	}

	private void writeExperiment_data_simpleRows(XLSResultsArray rowListForOneExp, XSSFSheet sheet, int column_dataArea,
			int rowSeries, Point pt) {
		for (int iRow = 0; iRow < rowListForOneExp.size(); iRow++) {
			XLSResults row = rowListForOneExp.getRow(iRow);
			writeRow(sheet, column_dataArea, rowSeries, pt, row);
		}

	}

	private void writeRow(XSSFSheet sheet, int column_dataArea, int rowSeries, Point pt, XLSResults row) {
		boolean transpose = options.transpose;
		pt.y = column_dataArea;
		int col = getRowIndexFromSpotName(row.name);
		pt.x = rowSeries + col;
		if (row.valuesOut == null)
			return;

		for (long coltime = expAll.seqCamData.firstImage_ms; coltime < expAll.seqCamData.lastImage_ms; coltime += options.buildExcelStepMs, pt.y++) {
			int i_from = (int) ((coltime - expAll.seqCamData.firstImage_ms) / options.buildExcelStepMs);
			if (i_from >= row.valuesOut.length)
				break;
			double value = row.valuesOut[i_from];
			if (!Double.isNaN(value)) {
				XLSUtils.setValue(sheet, pt, transpose, value);
				if (i_from < row.padded_out.length && row.padded_out[i_from])
					XLSUtils.getCell(sheet, pt, transpose).setCellStyle(xssfCellStyle_red);
			}
		}
		pt.x++;
	}

}
