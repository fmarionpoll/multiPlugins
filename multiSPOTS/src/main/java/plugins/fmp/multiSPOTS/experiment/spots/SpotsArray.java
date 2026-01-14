package plugins.fmp.multiSPOTS.experiment.spots;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import icy.roi.ROI;
import icy.roi.ROI2D;
import icy.sequence.Sequence;
import icy.type.geom.Polygon2D;
import icy.util.XMLUtil;
import plugins.fmp.multiSPOTS.experiment.KymoIntervals;
import plugins.fmp.multiSPOTS.series.BuildSeriesOptions;
import plugins.fmp.multiSPOTS.tools.Comparators;
import plugins.fmp.multiSPOTS.tools.ROI2D.ROI2DAlongT;
import plugins.fmp.multiSPOTS.tools.ROI2D.ROIUtilities;
import plugins.fmp.multiSPOTS.tools.polyline.Level2D;
import plugins.fmp.multiSPOTS.tools.toExcel.EnumXLSExportType;
import plugins.kernel.roi.roi2d.ROI2DPolygon;
import plugins.kernel.roi.roi2d.ROI2DShape;

public class SpotsArray {
	public SpotsDescription spotsDescription = new SpotsDescription();
//	public SpotsDescription desc_old = new SpotsDescription();
	public ArrayList<Spot> spotsList = new ArrayList<Spot>();
	public int nColumnsPerPlate = 12;
	public int nRowsPerPlate = 8;

	public int nColumnsPerCage = 2;
	public int nRowsPerCage = 1;
	private KymoIntervals spotsListTimeIntervals = null;

	private final static String ID_SPOTTRACK = "spotTrack";
	private final static String ID_NSPOTS = "N_spots";
	private final static String ID_NCOLUMNSPERPLATE = "N_columns";
	private final static String ID_NROWSPERPLATE = "N_rows";
	private final static String ID_NCOLUMNSPERCAGE = "N_columns_per_cage";
	private final static String ID_NROWSPERCAGE = "N_rows_per_cage";
	private final static String ID_LISTOFSPOTS = "List_of_spots";
	private final static String ID_SPOT_ = "spot_";
	private final static String ID_MCSPOTS_XML = "MCspots.xml";
	private final String csvFileName = "SpotsMeasures.csv";

	// ---------------------------------

	public boolean load_Measures(String directory) {
		boolean flag = false;
		try {
			flag = csvLoadSpots(directory, EnumSpotMeasures.SPOTS_MEASURES);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return flag;
	}

	public boolean load_Spots(String directory) {
		boolean flag = false;
		try {
			flag = csvLoadSpots(directory, EnumSpotMeasures.ALL);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return flag;
	}

	public boolean save_Spots(String directory) {
		boolean flag = false;
		try {
			flag = csvSaveSpots(directory);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return flag;
	}

	public boolean save_Measures(String directory) {
		if (directory == null)
			return false;

		csvSaveSpots(directory);
		return true;
	}

	// ---------------------------------

	public String getXMLSpotsName() {
		return ID_MCSPOTS_XML;
	}

	private boolean xmlSave_ListOfSpots(Document doc) {
		Node node = XMLUtil.getElement(XMLUtil.getRootElement(doc), ID_SPOTTRACK);
		if (node == null)
			return false;
		XMLUtil.setElementIntValue(node, "version", 2);
		Node nodeSpotsArray = XMLUtil.setElement(node, ID_LISTOFSPOTS);
		XMLUtil.setElementIntValue(nodeSpotsArray, ID_NSPOTS, spotsList.size());
		XMLUtil.setElementIntValue(nodeSpotsArray, ID_NCOLUMNSPERPLATE, nColumnsPerPlate);
		XMLUtil.setElementIntValue(nodeSpotsArray, ID_NROWSPERPLATE, nRowsPerPlate);
		XMLUtil.setElementIntValue(nodeSpotsArray, ID_NCOLUMNSPERCAGE, nColumnsPerCage);
		XMLUtil.setElementIntValue(nodeSpotsArray, ID_NROWSPERCAGE, nRowsPerCage);
		int i = 0;
		Collections.sort(spotsList);
		for (Spot spot : spotsList) {
			Node nodeSpot = XMLUtil.setElement(node, ID_SPOT_ + i);
			spot.saveToXML_SpotOnly(nodeSpot);
			i++;
		}
		return true;
	}

	public boolean xmlSave_MCSpots_Descriptors(String csFileName) {
		if (csFileName != null) {
			final Document doc = XMLUtil.createDocument(true);
			if (doc != null) {
				spotsDescription.xmlSaveSpotsDescription(doc);
				xmlSave_ListOfSpots(doc);
				return XMLUtil.saveDocument(doc, csFileName);
			}
		}
		return false;
	}

	public boolean xmlLoad_MCSpots_Descriptors(String csFileName) {
		boolean flag = false;
		if (csFileName == null)
			return flag;

		final Document doc = XMLUtil.loadDocument(csFileName);
		if (doc != null) {
			spotsDescription.xmlLoadSpotsDescription(doc);
			flag = xmlLoad_Spots_Only_v1(doc);
		}
		return flag;
	}

	private boolean xmlLoad_Spots_Only_v1(Document doc) {
		Node node = XMLUtil.getElement(XMLUtil.getRootElement(doc), ID_SPOTTRACK);
		if (node == null)
			return false;
		Node nodecaps = XMLUtil.getElement(node, ID_LISTOFSPOTS);
		int nitems = XMLUtil.getElementIntValue(nodecaps, ID_NSPOTS, 0);
		nColumnsPerPlate = XMLUtil.getElementIntValue(nodecaps, ID_NCOLUMNSPERPLATE, 12);
		nRowsPerPlate = XMLUtil.getElementIntValue(nodecaps, ID_NROWSPERPLATE, 8);
		nColumnsPerCage = XMLUtil.getElementIntValue(nodecaps, ID_NCOLUMNSPERCAGE, 2);
		nRowsPerCage = XMLUtil.getElementIntValue(nodecaps, ID_NROWSPERCAGE, 1);

		spotsList = new ArrayList<Spot>(nitems);
		for (int i = 0; i < nitems; i++) {
			Node nodecapillary = XMLUtil.getElement(node, ID_SPOT_ + i);
			Spot spot = new Spot();
			spot.loadFromXML_SpotOnly(nodecapillary);

			if (!isPresent(spot))
				spotsList.add(spot);
		}
		return true;
	}

	// ---------------------------------

	public void copy(SpotsArray sourceSpotArray) {
		spotsDescription.copy(sourceSpotArray.spotsDescription);
		spotsList.clear();
		for (Spot sourceSpot : sourceSpotArray.spotsList) {
			Spot spot = new Spot();
			spot.copySpot(sourceSpot);
			spotsList.add(spot);
		}
	}

	public boolean isPresent(Spot capNew) {
		boolean flag = false;
		for (Spot spot : spotsList) {
			if (spot.getRoi().getName().contentEquals(capNew.getRoi().getName())) {
				flag = true;
				break;
			}
		}
		return flag;
	}

	public void mergeLists(SpotsArray sourceSpotList) {
		for (Spot spot : sourceSpotList.spotsList) {
			if (!isPresent(spot))
				spotsList.add(spot);
		}
	}

	public void adjustSpotsLevel2DMeasuresToImageWidth(int imageWidth) {
		for (Spot spot : spotsList)
			spot.adjustLevel2DMeasuresToImageWidth(imageWidth);
	}

	public void cropSpotsLevel2DMeasuresToImageWidth(int imageWidth) {
		for (Spot spot : spotsList)
			spot.cropLevel2DMeasuresToImageWidth(imageWidth);
	}

	public Spot getSpotFromName(String name) {
		Spot spotFound = null;
		for (Spot spot : spotsList) {
			if (spot.getRoi().getName().equals(name)) {
				spotFound = spot;
				break;
			}
		}
		return spotFound;
	}

	public Spot getSpotContainingName(String name) {
		Spot spotFound = null;
		for (Spot spot : spotsList) {
			if (spot.getRoi().getName().contains(name)) {
				spotFound = spot;
				break;
			}
		}
		return spotFound;
	}

	public int getCageIndexFromPlateIndex(int plateIndex) {
		int plateColumn = plateIndex % nColumnsPerPlate;
		int cageColumn = plateColumn / nColumnsPerCage;

		int plateRow = plateIndex / nColumnsPerPlate;
		int cageRow = plateRow / nRowsPerCage;

		int nCagesAlongX = nColumnsPerPlate / nColumnsPerCage;
		int cageID = cageRow * nCagesAlongX + cageColumn;
		return cageID;
	}

	public void updatePlateIndexToCageIndexes(int nColsPerCage, int nRowsPerCage) {

		this.nColumnsPerCage = nColsPerCage;
		this.nRowsPerCage = nRowsPerCage;

		for (Spot spot : spotsList) {
			spot.cageID = getCageIndexFromPlateIndex(spot.plateIndex);
			int spotCageColumn = spot.plateColumn % nColsPerCage;
			int spotCageRow = spot.plateRow % nRowsPerCage;
			spot.cagePosition = spotCageRow * nColsPerCage + spotCageColumn;

			spot.setSpotRoi_InColorAccordingToSpotIndex(spot.cagePosition);
		}
	}

	public void transferROIsFromSequenceToSpots(Sequence seq) {
		List<ROI> listROISSpot = ROIUtilities.getROIsContainingString("spot", seq);
		Collections.sort(listROISSpot, new Comparators.ROI_Name_Comparator());
		for (Spot spot : spotsList) {
			spot.valid = false;
			String spotName = spot.getRoi().getName();
			Iterator<ROI> iterator = listROISSpot.iterator();
			while (iterator.hasNext()) {
				ROI roi = iterator.next();
				String roiName = roi.getName();
				if (roiName.equals(spotName) && (roi instanceof ROI2DShape)) {
					spot.setRoi((ROI2DShape) roi);
					spot.valid = true;
				}
				if (spot.valid) {
					iterator.remove();
					break;
				}
			}
		}

		Iterator<Spot> iterator = spotsList.iterator();
		while (iterator.hasNext()) {
			Spot spot = iterator.next();
			if (!spot.valid)
				iterator.remove();
		}
		if (listROISSpot.size() > 0) {
			for (ROI roi : listROISSpot) {
				Spot spot = new Spot((ROI2DShape) roi);
				if (!isPresent(spot))
					spotsList.add(spot);
			}
		}
		Collections.sort(spotsList);
		return;
	}

	public void transferROIsMeasuresFromSequenceToSpots() {
		for (Spot spot : spotsList) {
			spot.transferROIsMeasuresToLevel2D();
		}
	}

	public void transferSpotsToSequenceAsROIs(Sequence seq) {
		seq.removeROIs(ROIUtilities.getROIsContainingString("spot", seq), false);
		List<ROI2D> spotROIList = new ArrayList<ROI2D>(spotsList.size());
		for (Spot spot : spotsList)
			spotROIList.add(spot.getRoi());
		seq.addROIs(spotROIList, true);
	}

	public void transferSpotsMeasuresToSequenceAsROIs(Sequence seq) {
		List<ROI2D> seqRoisList = seq.getROI2Ds(false);
		ROIUtilities.removeROIsMissingChar(seqRoisList, '_');

		List<ROI2D> newRoisList = new ArrayList<ROI2D>();
		int nspots = spotsList.size();
		int height = seq.getHeight();
		for (int i = 0; i < nspots; i++) {
			List<ROI2D> listOfRois = spotsList.get(i).transferSpotMeasuresToROIs(height);
			for (ROI2D roi : listOfRois) {
				if (roi != null)
					roi.setT(i);
			}
			newRoisList.addAll(listOfRois);
		}
		ROIUtilities.mergeROIsListNoDuplicate(seqRoisList, newRoisList, seq);
		seq.removeAllROI();
		seq.addROIs(seqRoisList, false);
	}

	public void initSpotsWithNFlies(int nflies) {
		int spotArraySize = spotsList.size();
		for (int i = 0; i < spotArraySize; i++) {
			Spot spot = spotsList.get(i);
			spot.spotNFlies = nflies;
		}
	}

	public Polygon2D getPolygon2DEnclosingAllSpots() {
		if (spotsList.size() < 1)
			return null;

		List<Point2D> points = new ArrayList<Point2D>();
		int spotX = spotsList.get(0).spotXCoord;
		int spotY = spotsList.get(0).spotYCoord;
		points.add(new Point2D.Double(spotX, spotY));
		points.add(new Point2D.Double(spotX + 1, spotY));
		points.add(new Point2D.Double(spotX + 1, spotY + 1));
		points.add(new Point2D.Double(spotX, spotY + 1));
		Polygon2D polygon = new Polygon2D(points);

		for (Spot spot : spotsList) {
			int col = spot.plateColumn;
			int row = spot.plateRow;

			if (col == 0 && row == 0) {
				replaceItem(polygon, 0, spot);
			} else if (col == (nColumnsPerPlate - 1) && row == 0) {
				replaceItem(polygon, 1, spot);
			} else if (col == (nColumnsPerPlate - 1) && row == (nRowsPerPlate - 1)) {
				replaceItem(polygon, 2, spot);
			} else if (col == 0 && row == (nRowsPerPlate - 1)) {
				replaceItem(polygon, 3, spot);
			}
		}
		return polygon;
	}

	private void replaceItem(Polygon2D polygon, int index, Spot spot) {
		polygon.xpoints[index] = spot.spotXCoord;
		polygon.ypoints[index] = spot.spotYCoord;
	}

	public ArrayList<Spot> getSpotsEnclosed(ROI2DPolygon envelopeRoi) {
		ArrayList<Spot> enclosedSpots = new ArrayList<Spot>();
		if (envelopeRoi != null) {
			for (Spot spot : spotsList) {
				try {
					if (envelopeRoi.contains(spot.getRoi())) {
						spot.getRoi().setSelected(true);
						enclosedSpots.add(spot);
					}
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		} else {
			for (Spot spot : spotsList) {
				if (spot.getRoi().isSelected())
					enclosedSpots.add(spot);
			}
		}
		return enclosedSpots;
	}

	// ------------------------------------------------

	public double getScalingFactorToPhysicalUnits(EnumXLSExportType xlsoption) {
		double scalingFactorToPhysicalUnits = 1.;
//		switch (xlsoption) 
//		{
//			case AUTOCORREL:
//			case CROSSCORREL:
//			case CROSSCORREL_LR:
//				scalingFactorToPhysicalUnits = 1.;
//				break;
//			default:
//				scalingFactorToPhysicalUnits = spotsDescription.volume / spotsDescription.pixels;
//				break;
//		}
		return scalingFactorToPhysicalUnits;
	}

	public Polygon2D get2DPolygonEnclosingSpots() {
		Rectangle outerRectangle = null;
		for (Spot spot : spotsList) {
			Rectangle rect = spot.getRoi().getBounds();
			if (outerRectangle == null) {
				outerRectangle = rect;
			} else
				outerRectangle.add(rect);
		}
		if (outerRectangle == null)
			return null;

		return new Polygon2D(outerRectangle);
	}

	public void transferSumToSumClean() {
		int span = 10;
		for (Spot spot : spotsList) {
			if (spot.sum_in.values != null)
				spot.sum_clean.buildRunningMedian(span, spot.sum_in.values);
			else {
				Level2D level = spot.sum_in.getLevel2D();
				if (level != null && level.npoints > 0)
					spot.sum_clean.buildRunningMedian(span, level.ypoints);
			}
		}
	}

	public void initLevel2DMeasures() {
		for (Spot spot : spotsList)
			spot.initLevel2DMeasures();
	}

	public int getSpotIndexFromPlateCoordinates(String rowLetter, String columnNumber) {
		int index = 0;
		int col = 0;
		try {
			col = Integer.parseInt(columnNumber);
		} catch (NumberFormatException e1) {
			col = 0;
		}

		int row = getNumericReferenceNumber(rowLetter);
		index = row * nColumnsPerPlate + col;
		return index;
	}

	private int getNumericReferenceNumber(String str) {
		String result = "";
		for (int i = 0; i < str.length(); i++) {
			char ch = str.charAt(i);
			if (Character.isLetter(ch)) {
				char initialCharacter = Character.isUpperCase(ch) ? 'A' : 'a';
				result = result.concat(String.valueOf((ch - initialCharacter)));
			} else
				result = result + ch;
		}
		return Integer.parseInt(result);
	}

	public KymoIntervals getKymoIntervalsFromSpots() {
		if (spotsListTimeIntervals == null) {
			spotsListTimeIntervals = new KymoIntervals();
			for (Spot spot : spotsList) {
				for (ROI2DAlongT roiFK : spot.getROIAlongTList()) {
					Long[] interval = { roiFK.getT(), (long) -1 };
					spotsListTimeIntervals.addIfNew(interval);
				}
			}
		}
		return spotsListTimeIntervals;
	}

	public int findKymoROI2DIntervalStart(long intervalT) {
		return spotsListTimeIntervals.findStartItem(intervalT);
	}

	public long getKymoROI2DIntervalsStartAt(int selectedItem) {
		return spotsListTimeIntervals.get(selectedItem)[0];
	}

	public int addKymoROI2DInterval(long start) {
		Long[] interval = { start, (long) -1 };
		int item = spotsListTimeIntervals.addIfNew(interval);

		for (Spot spot : spotsList) {
			List<ROI2DAlongT> listROI2DForKymo = spot.getROIAlongTList();
			ROI2D roi = spot.getRoi();
			if (item > 0)
				roi = (ROI2D) listROI2DForKymo.get(item - 1).getRoi_in().getCopy();
			listROI2DForKymo.add(item, new ROI2DAlongT(start, roi));
		}
		return item;
	}

	public void deleteKymoROI2DInterval(long start) {
		spotsListTimeIntervals.deleteIntervalStartingAt(start);
		for (Spot spot : spotsList)
			spot.removeROIAlongTListItem(start);
	}

	// --------------------------------

	final String csvSep = ";";

	private boolean csvLoadSpots(String directory, EnumSpotMeasures option) throws Exception {
		String pathToCsv = directory + File.separator + csvFileName;
		File csvFile = new File(pathToCsv);
		if (!csvFile.isFile())
			return false;

		BufferedReader bufferedReader = new BufferedReader(new FileReader(pathToCsv));
		String row;
		String sep = ";";
		while ((row = bufferedReader.readLine()) != null) {
			if (row.charAt(0) == '#')
				sep = String.valueOf(row.charAt(1));

			String[] data = row.split(sep);
			if (data[0].equals("#")) {
				switch (data[1]) {
				case "DESCRIPTION":
					csvLoadDescription(bufferedReader, sep);
					break;
				case "SPOTS":
					csvLoadSpotsArray(bufferedReader, sep);
					break;
				case "AREA_SUM":
					csvLoadSpotsMeasures(bufferedReader, EnumSpotMeasures.AREA_SUM, sep);
					break;
				case "AREA_OUT":
					csvLoadSpotsMeasures(bufferedReader, EnumSpotMeasures.AREA_OUT, sep);
					break;
				case "AREA_DIFF":
					csvLoadSpotsMeasures(bufferedReader, EnumSpotMeasures.AREA_DIFF, sep);
					break;
				case "AREA_SUMCLEAN":
					csvLoadSpotsMeasures(bufferedReader, EnumSpotMeasures.AREA_SUMCLEAN, sep);
					break;
				case "AREA_FLYPRESENT":
					csvLoadSpotsMeasures(bufferedReader, EnumSpotMeasures.AREA_FLYPRESENT, sep);
					break;
				default:
					break;
				}
			}
		}
		bufferedReader.close();

		return true;
	}

	private String csvLoadSpotsArray(BufferedReader csvReader, String csvSep) {
		String row;
		try {
			row = csvReader.readLine();
			String[] data0 = row.split(csvSep);
			boolean dummyColumn = data0[0].contains("prefix");

			while ((row = csvReader.readLine()) != null) {
				String[] data = row.split(csvSep);
				if (data[0].equals("#"))
					return data[1];
				Spot spot = getSpotFromName(data[dummyColumn ? 2 : 1]);
				if (spot == null)
					spot = new Spot();
				spot.csvImportDescription(data, dummyColumn);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	private String csvLoadDescription(BufferedReader csvReader, String csvSep) {
		String row;
		try {
			row = csvReader.readLine();
			row = csvReader.readLine();
			String[] data = row.split(csvSep);
			spotsDescription.csvImportSpotsDescriptionData(data);
			row = csvReader.readLine();
			data = row.split(csvSep);
			if (data[0].substring(0, Math.min(data[0].length(), 5)).equals("n spot")) {
				int nspots = Integer.valueOf(data[1]);
				if (nspots >= spotsList.size())
					spotsList.ensureCapacity(nspots);
				else
					spotsList.subList(nspots, spotsList.size()).clear();
				row = csvReader.readLine();
				data = row.split(csvSep);
			}
			if (data[0].equals("#")) {
				return data[1];
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	private String csvLoadSpotsMeasures(BufferedReader csvReader, EnumSpotMeasures measureType, String csvSep) {
		String row;
		try {
			row = csvReader.readLine();
			boolean y = true;
			boolean x = row.contains("xi");
			while ((row = csvReader.readLine()) != null) {
				String[] data = row.split(csvSep);
				if (data[0].equals("#"))
					return data[1];

				Spot spot = getSpotFromName(data[0]);
				if (spot == null)
					spot = new Spot();
				spot.csvImportMeasures_OneType(measureType, data, x, y);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	// ---------------------------------

	private boolean csvSaveSpots(String directory) {
		Path path = Paths.get(directory);
		if (!Files.exists(path))
			return false;

		try {
			FileWriter csvWriter = new FileWriter(directory + File.separator + csvFileName);

			csvSave_DescriptionSection(csvWriter);
			csvSave_MeasuresSection(csvWriter, EnumSpotMeasures.AREA_SUM);
			csvSave_MeasuresSection(csvWriter, EnumSpotMeasures.AREA_SUMCLEAN);
			csvSave_MeasuresSection(csvWriter, EnumSpotMeasures.AREA_OUT);
			csvSave_MeasuresSection(csvWriter, EnumSpotMeasures.AREA_DIFF);
			csvSave_MeasuresSection(csvWriter, EnumSpotMeasures.AREA_FLYPRESENT);
			csvWriter.flush();
			csvWriter.close();

		} catch (IOException e) {
			e.printStackTrace();
		}

		return true;
	}

	private boolean csvSave_DescriptionSection(FileWriter csvWriter) {
		try {
			csvWriter.append(spotsDescription.csvExportSectionHeader(csvSep));
			csvWriter.append(spotsDescription.csvExportExperimentDescriptors(csvSep));
			csvWriter.append("n spots=" + csvSep + Integer.toString(spotsList.size()) + "\n");
			csvWriter.append("#" + csvSep + "#\n");

			if (spotsList.size() > 0) {
				csvWriter.append(spotsList.get(0).csvExportSpotArrayHeader(csvSep));
				for (Spot spot : spotsList)
					csvWriter.append(spot.csvExportDescription(csvSep));
				csvWriter.append("#" + csvSep + "#\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return true;
	}

	private boolean csvSave_MeasuresSection(FileWriter csvWriter, EnumSpotMeasures measureType) {
		try {
			if (spotsList.size() <= 1)
				return false;
			csvWriter.append(spotsList.get(0).csvExportMeasures_SectionHeader(measureType, csvSep));
			for (Spot spot : spotsList) {
				csvWriter.append(spot.csvExportMeasures_OneType(measureType, csvSep));
			}
			csvWriter.append("#" + csvSep + "#\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
		return true;
	}

	public void setFilterOfSpotsToAnalyze(boolean setFilter, BuildSeriesOptions options) {
		for (Spot spot : spotsList) {
			spot.okToAnalyze = true;
			if (!setFilter)
				continue;

			if (!options.detectL && spot.isL())
				spot.okToAnalyze = false;
			if (!options.detectR && spot.isR())
				spot.okToAnalyze = false;
			if (options.detectSelectedROIs && !spot.isIndexSelected(options.selectedIndexes))
				spot.okToAnalyze = false;
		}
	}

}