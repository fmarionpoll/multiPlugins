package plugins.fmp.multiSPOTS96.experiment.cages;

import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import icy.roi.ROI2D;
import icy.sequence.Sequence;
import icy.type.geom.Polygon2D;
import icy.util.XMLUtil;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.sequence.ROIOperation;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.experiment.sequence.TIntervalsArray;
import plugins.fmp.multitools.experiment.spots.Spot;
import plugins.fmp.multitools.experiment.spots.SpotString;
import plugins.fmp.multitools.experiment.spots.SpotsArray;
import plugins.fmp.multitools.series.BuildSeriesOptions;
import plugins.fmp.multitools.tools.Comparators;
import plugins.fmp.multitools.tools.JComponents.Dialog;
import plugins.fmp.multitools.tools.JComponents.exceptions.FileDialogException;
import plugins.fmp.multitools.tools.ROI2D.Utilities;
import plugins.kernel.roi.roi2d.ROI2DArea;
import plugins.kernel.roi.roi2d.ROI2DPolygon;
import plugins.kernel.roi.roi2d.ROI2DShape;

public class CagesArray {
	public ArrayList<Cage> cagesList = new ArrayList<Cage>();
	private TIntervalsArray cagesListTimeIntervals = null;

	public int nCagesAlongX = 6;
	public int nCagesAlongY = 8;
	public int nColumnsPerCage = 2;
	public int nRowsPerCage = 1;

	// ---------- not saved to xml:
	public long detectFirst_Ms = 0;
	public long detectLast_Ms = 0;
	public long detectBin_Ms = 60000;
	public int detect_threshold = 0;
	public int detect_nframes = 0;

	// ----------------------------

	private final String ID_CAGES = "Cages";
	private final String ID_NCAGES = "n_cages";

	private final String ID_NCAGESALONGX = "N_cages_along_X";
	private final String ID_NCAGESALONGY = "N_cages_along_Y";
	private final String ID_NCOLUMNSPERCAGE = "N_columns_per_cage";
	private final String ID_NROWSPERCAGE = "N_rows_per_cage";

	private final String ID_MCDROSOTRACK_XML = "MCdrosotrack.xml";
	public final String ID_MS96_cages_XML = "MS96_cages.xml";
	public final String ID_MS96_spotsMeasures_XML = "MS96_spotsMeasures.xml";
	public final String ID_MS96_fliesPositions_XML = "MS96_fliesPositions.xml";

	public CagesArray() {
	}

	public CagesArray(ArrayList<Cage> cagesListFrom) {
		copyCagesInfos(cagesListFrom);
	}

	public CagesArray(int ncolumns, int nrows) {
		nCagesAlongX = ncolumns;
		nCagesAlongY = nrows;
		cagesList = new ArrayList<Cage>(ncolumns * nrows);
	}

	public void clearAllMeasures(int option_detectCage) {
		for (Cage cage : cagesList) {
			if (option_detectCage < 0 || option_detectCage == cage.getProperties().getCageID())
				cage.clearMeasures();
		}
	}

	public void removeCages() {
		cagesList.clear();
	}

	public void mergeLists(CagesArray cageArrayToMerge) {
		for (Cage cageAdded : cageArrayToMerge.cagesList) {
			if (!isPresent(cageAdded))
				cagesList.add(cageAdded);
		}
	}

	public void copyCagesInfos(ArrayList<Cage> cagesListFrom) {
		copyCages(cagesListFrom, false);
	}

	public void copyCages(ArrayList<Cage> cagesListFrom, boolean bMeasures) {
		cagesList.clear();
		nCagesAlongX = 0;
		nCagesAlongY = 0;
		for (Cage cageFrom : cagesListFrom) {
			Cage cage = new Cage();
			cage.copyCage(cageFrom, bMeasures);
			cagesList.add(cage);
			if (nCagesAlongX < cageFrom.getProperties().getArrayColumn())
				nCagesAlongX = cageFrom.getProperties().getArrayColumn();
			if (nCagesAlongY < cageFrom.getProperties().getArrayRow())
				nCagesAlongY = cageFrom.getProperties().getArrayRow();
		}
	}

	public void pasteCagesInfos(ArrayList<Cage> cagesListTo) {
		pasteCages(cagesListTo, false);
	}

	public void pasteCages(ArrayList<Cage> cagesListTo, boolean bMeasures) {
		for (Cage cageTo : cagesListTo) {
			int fromID = cageTo.getProperties().getCageID();
			for (Cage cage : cagesList) {
				if (cage.getProperties().getCageID() == fromID) {
					cage.pasteCage(cageTo, bMeasures);
					break;
				}
			}
		}
	}

	// -------------

	public boolean saveCagesMeasures(String directory) {
		csvSaveCagesMeasures(directory);
		String tempName = directory + File.separator + ID_MCDROSOTRACK_XML;
		xmlWriteCagesToFileNoQuestion(tempName);
		return true;
	}

	public boolean loadCagesMeasures(String directory) {
		// csvLoadCagesMeasures(directory);
		String tempName = directory + File.separator + ID_MCDROSOTRACK_XML;
		xmlReadCagesFromFileNoQuestion(tempName);
		return true;
	}

	// -----------------------------------------------------

	final String csvSep = ";";

	private boolean csvSaveCagesMeasures(String directory) {
		try {
			FileWriter csvWriter = new FileWriter(directory + File.separator + "CagesMeasures.csv");
			csvSaveDescriptionSection(csvWriter);
			csvSaveMeasuresSection(csvWriter, EnumCageMeasures.POSITION);
			csvWriter.flush();
			csvWriter.close();

		} catch (IOException e) {
			e.printStackTrace();
		}
		return true;
	}

	private boolean csvSaveDescriptionSection(FileWriter csvWriter) {
		try {
			csvWriter.append("#" + csvSep + "DESCRIPTION" + csvSep + "Cages data\n");
			csvWriter.append("n cages=" + csvSep + Integer.toString(cagesList.size()) + "\n");
			if (cagesList.size() > 0)
				for (Cage cage : cagesList)
					csvWriter.append(cage.csvExportCageDescription(csvSep));

			csvWriter.append("#" + csvSep + "#\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
		return true;
	}

	private boolean csvSaveMeasuresSection(FileWriter csvWriter, EnumCageMeasures measuresType) {
		try {
//			csvWriter.append("#" + csvSep + "DESCRIPTION" + csvSep + "Cages data\n");
//			csvWriter.append("n cages=" + csvSep + Integer.toString(cagesList.size()) + "\n");
//			if (cagesList.size() > 0) {
//				for (Cage cage : cagesList)
//					csvWriter.append(cage.csvExportCageDescription(csvSep));
//			}
			csvWriter.append("#" + csvSep + "#\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
		return true;
	}

	// ----------------------------------------------------

	public boolean xmlReadCagesFromFile(Experiment exp) {
		String[] filedummy = null;
		String filename = exp.getResultsDirectory();
		File file = new File(filename);
		String directory = file.getParentFile().getAbsolutePath();
		try {
			filedummy = Dialog.selectFiles(directory, "xml");
		} catch (FileDialogException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		boolean wasOk = false;
		if (filedummy != null) {
			for (int i = 0; i < filedummy.length; i++) {
				String csFile = filedummy[i];
				wasOk &= xmlReadCagesFromFileNoQuestion(csFile);
			}
		}
		return wasOk;
	}

	public boolean xmlReadCagesFromFileNoQuestion(String tempname) {
		// Memory monitoring before loading
//		long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
		// System.out.println("=== XML LOADING: CagesArray ===");
		// System.out.println("Loading file: " + tempname);
		// System.out.println("Memory before loading: " + (startMemory / 1024 / 1024) + " MB");
		
		try {
			final Document doc = XMLUtil.loadDocument(tempname);
			if (doc == null) {
				System.err.println("ERROR: Could not load XML document from " + tempname);
				return false;
			}
			
			// Schema validation removed as requested
			
			boolean success = xmlLoadCages(XMLUtil.getRootElement(doc));
			
			// Memory monitoring after loading
//			long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
//			long memoryIncrease = endMemory - startMemory;
					// System.out.println("Memory after loading: " + (endMemory / 1024 / 1024) + " MB");
		// System.out.println("Memory increase: " + (memoryIncrease / 1024 / 1024) + " MB");
		// System.out.println("Loaded cages: " + cagesList.size());
		// System.out.println("=== XML LOADING COMPLETE ===");
			
			return success;
			
		} catch (Exception e) {
			System.err.println("ERROR during cages XML loading: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	public boolean xmlWriteCagesToFileNoQuestion(String tempname) {
		// Memory monitoring before saving
//		long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
		// System.out.println("=== XML SAVING: CagesArray ===");
		// System.out.println("Saving file: " + tempname);
		// System.out.println("Memory before saving: " + (startMemory / 1024 / 1024) + " MB");
		// System.out.println("Cages to save: " + cagesList.size());
		
		try {
			final Document doc = XMLUtil.createDocument(true);
			if (doc == null) {
				System.err.println("ERROR: Could not create XML document");
				return false;
			}
			
			Node node = XMLUtil.getRootElement(doc);
			boolean success = xmlSaveCages(node);
			
			if (success) {
				success = XMLUtil.saveDocument(doc, tempname);
			}
			
			// Memory monitoring after saving
//			long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
//			long memoryIncrease = endMemory - startMemory;
					// System.out.println("Memory after saving: " + (endMemory / 1024 / 1024) + " MB");
		// System.out.println("Memory increase: " + (memoryIncrease / 1024 / 1024) + " MB");
		// System.out.println("Save success: " + success);
		// System.out.println("=== XML SAVING COMPLETE ===");
			
			return success;
			
		} catch (Exception e) {
			System.err.println("ERROR during cages XML saving: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	private boolean xmlSaveCages(Node node) {
		try {
			int index = 0;
			Element xmlVal = XMLUtil.addElement(node, ID_CAGES);
			int ncages = cagesList.size();
			XMLUtil.setAttributeIntValue(xmlVal, ID_NCAGES, ncages);
			XMLUtil.setAttributeIntValue(xmlVal, ID_NCAGESALONGX, nCagesAlongX);
			XMLUtil.setAttributeIntValue(xmlVal, ID_NCAGESALONGY, nCagesAlongY);
			XMLUtil.setAttributeIntValue(xmlVal, ID_NCOLUMNSPERCAGE, nColumnsPerCage);
			XMLUtil.setAttributeIntValue(xmlVal, ID_NROWSPERCAGE, nRowsPerCage);
			
			// System.out.println("Saving " + ncages + " cages with layout " + nCagesAlongX + "x" + nCagesAlongY);
			
			for (Cage cage : cagesList) {
				if (cage == null) {
					System.err.println("WARNING: Null cage at index " + index);
					continue;
				}
				
				boolean cageSuccess = cage.xmlSaveCage(xmlVal, index);
				if (!cageSuccess) {
					System.err.println("ERROR: Failed to save cage at index " + index);
				}
				index++;
			}
			
			return true;
			
		} catch (Exception e) {
			System.err.println("ERROR during xmlSaveCages: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	private boolean xmlLoadCages(Node node) {
		try {
			cagesList.clear();
			Element xmlVal = XMLUtil.getElement(node, ID_CAGES);
			if (xmlVal == null) {
				System.err.println("ERROR: Could not find Cages element in XML");
				return false;
			}

			int ncages = XMLUtil.getAttributeIntValue(xmlVal, ID_NCAGES, 0);
			if (ncages <= 0) {
				System.err.println("ERROR: Invalid number of cages: " + ncages);
				return false;
			}
			
			nCagesAlongX = XMLUtil.getAttributeIntValue(xmlVal, ID_NCAGESALONGX, nCagesAlongX);
			nCagesAlongY = XMLUtil.getAttributeIntValue(xmlVal, ID_NCAGESALONGY, nCagesAlongY);
			nColumnsPerCage = XMLUtil.getAttributeIntValue(xmlVal, ID_NCOLUMNSPERCAGE, nColumnsPerCage);
			nRowsPerCage = XMLUtil.getAttributeIntValue(xmlVal, ID_NROWSPERCAGE, nRowsPerCage);
			
			// System.out.println("Loading " + ncages + " cages with layout " + nCagesAlongX + "x" + nCagesAlongY);
			
			int loadedCages = 0;
			for (int index = 0; index < ncages; index++) {
				try {
					Cage cage = new Cage();
					boolean cageSuccess = cage.xmlLoadCage(xmlVal, index);
					if (cageSuccess) {
						cagesList.add(cage);
						loadedCages++;
					} else {
						System.err.println("WARNING: Failed to load cage at index " + index);
					}
				} catch (Exception e) {
					System.err.println("ERROR loading cage at index " + index + ": " + e.getMessage());
				}
			}
			
			// System.out.println("Successfully loaded " + loadedCages + " out of " + ncages + " cages");
			return loadedCages > 0; // Return true if at least one cage was loaded
			
		} catch (Exception e) {
			System.err.println("ERROR during xmlLoadCages: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	// --------------

	private boolean isPresent(Cage cagenew) {
		boolean flag = false;
		for (Cage cage : cagesList) {
			if (cage.getRoi().getName().contentEquals(cagenew.getRoi().getName())) {
				flag = true;
				break;
			}
		}
		return flag;
	}

	private void addMissingCages(List<ROI2D> roiList) {
		for (ROI2D roi : roiList) {
			boolean found = false;
			if (roi.getName() == null)
				break;
			for (Cage cage : cagesList) {
				if (cage.getRoi() == null)
					break;
				if (roi.getName().equals(cage.getRoi().getName())) {
					found = true;
					break;
				}
			}
			if (!found) {
				Cage cage = new Cage();
				cage.setRoi((ROI2DShape) roi);
				cagesList.add(cage);
			}
		}
	}

	private void removeOrphanCages(List<ROI2D> roiList) {
		// remove cages with names not in the list
		Iterator<Cage> iterator = cagesList.iterator();
		while (iterator.hasNext()) {
			Cage cage = iterator.next();
			boolean found = false;
			if (cage.getRoi() != null) {
				String cageRoiName = cage.getRoi().getName();
				for (ROI2D roi : roiList) {
					if (roi.getName().equals(cageRoiName)) {
						found = true;
						break;
					}
				}
			}
			if (!found)
				iterator.remove();
		}
	}

	public List<ROI2D> getROIsWithCageName(SequenceCamData seqCamData) {
		List<ROI2D> roiList = seqCamData.getSequence().getROI2Ds();
		List<ROI2D> roisCageList = new ArrayList<ROI2D>();
		for (ROI2D roi : roiList) {
			String csName = roi.getName();
			if ((roi instanceof ROI2DPolygon) || (roi instanceof ROI2DArea)) {
				if ((csName.length() > 4 && csName.substring(0, 4).contains("cage") || csName.contains("Polygon2D")))
					roisCageList.add(roi);
			}
		}
		return roisCageList;
	}

	public Cage getCageFromRowColCoordinates(int row, int column) {
		Cage cage_found = null;
		for (Cage cage : cagesList) {
			if (cage.getProperties().getArrayColumn() == column && cage.getProperties().getArrayRow() == row) {
				cage_found = cage;
				break;
			}
		}
		return cage_found;
	}

	public Cage findFirstSelectedCage() {
		Cage cageFound = null;
		for (Cage cage : cagesList) {
			ROI2D roi = cage.getRoi();
			if (roi.isSelected()) {
				cageFound = cage;
				break;
			}
		}
		return cageFound;
	}

	public Cage findFirstCageWithSelectedSpot() {
		Cage cageFound = null;
		for (Cage cage : cagesList) {
			for (Spot spot : cage.spotsArray.getSpotsList()) {
				ROI2D roi = spot.getRoi();
				if (roi.isSelected()) {
					return cage;
				}
			}
		}
		return cageFound;
	}

	public Cage getCageFromNumber(int number) {
		Cage cageFound = null;
		for (Cage cage : cagesList) {
			if (number == cage.getProperties().getCageID()) {
				cageFound = cage;
				break;
			}
		}
		return cageFound;
	}

	public Cage getCageFromID(int cageID) {
		for (Cage cage : cagesList) {
			if (cage.getProperties().getCageID() == cageID)
				return cage;
		}
		return null;
	}

	public Cage getCageFromName(String name) {
		for (Cage cage : cagesList) {
			if (cage.getRoi().getName().equals(name))
				return cage;
		}
		return null;
	}

	public Cage getCageFromSpotName(String name) {
		int cageID = SpotString.getCageIDFromSpotName(name);
		return getCageFromID(cageID);
	}

	public Cage getCageFromSpotROIName(String name) {
		for (Cage cage : cagesList) {
			for (Spot spot : cage.spotsArray.getSpotsList()) {
				if (spot.getRoi().getName().contains(name))
					return cage;
			}
		}
		return null;
	}

	// --------------

	public void transferCagesToSequenceAsROIs(SequenceCamData seqCamData) {
		// Use modern ROI operation for removing existing cage ROIs
		seqCamData.processROIs(ROIOperation.removeROIs("cage"));

		List<ROI2D> cageROIList = new ArrayList<ROI2D>(cagesList.size());
		for (Cage cage : cagesList)
			cageROIList.add(cage.getRoi());
		Sequence sequence = seqCamData.getSequence();
		if (sequence != null)
			sequence.addROIs(cageROIList, true);
	}

	public void transferROIsFromSequenceToCages(SequenceCamData seqCamData) {
		// Use modern ROI finding API
		List<ROI2D> roiList = seqCamData.findROIs("cage");
		Collections.sort(roiList, new Comparators.ROI2D_Name());
		addMissingCages(roiList);
		removeOrphanCages(roiList);
		Collections.sort(cagesList, new Comparators.Cage_Name());
	}

	public void removeAllRoiDetFromSequence(SequenceCamData seqCamData) {
		ArrayList<ROI2D> seqlist = seqCamData.getSequence().getROI2Ds();
		for (ROI2D roi : seqlist) {
			if (!(roi instanceof ROI2DShape))
				continue;
			if (!roi.getName().contains("det"))
				continue;
			seqCamData.getSequence().removeROI(roi);
		}
	}

	public List<ROI2D> getPositionsAsListOfROI2DRectanglesAtT(int t) {
		List<ROI2D> roiRectangleList = new ArrayList<ROI2D>(cagesList.size());
		for (Cage cage : cagesList) {
			ROI2D roiRectangle = cage.getRoiRectangleFromPositionAtT(t);
			if (roiRectangle != null)
				roiRectangleList.add(roiRectangle);
		}
		return roiRectangleList;
	}

	public void orderFlyPositions() {
		for (Cage cage : cagesList)
			Collections.sort(cage.flyPositions.flyPositionList, new Comparators.XYTaValue_Tindex());
	}

	public void initFlyPositions(int option_cagenumber) {
		int nbcages = cagesList.size();
		for (int i = 0; i < nbcages; i++) {
			Cage cage = cagesList.get(i);
			if (option_cagenumber != -1 && cage.getProperties().getCageID() != option_cagenumber)
				continue;
			if (cage.getProperties().getCageNFlies() > 0) {
				cage.flyPositions = new FlyPositions();
				cage.flyPositions.ensureCapacity(detect_nframes);
			}
		}
	}

	// ----------------

	public void computeBooleanMasksForCages() {
		for (Cage cage : cagesList) {
			try {
				cage.computeCageBooleanMask2D();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public int getLastIntervalFlyAlive(int cagenumber) {
		int flypos = -1;
		for (Cage cage : cagesList) {
			String cagenumberString = cage.getRoi().getName().substring(4);
			if (Integer.valueOf(cagenumberString) == cagenumber) {
				flypos = cage.flyPositions.getLastIntervalAlive();
				break;
			}
		}
		return flypos;
	}

	public boolean isFlyAlive(int cagenumber) {
		boolean isalive = false;
		for (Cage cage : cagesList) {
			String cagenumberString = cage.getRoi().getName().substring(4);
			if (Integer.valueOf(cagenumberString) == cagenumber) {
				isalive = (cage.flyPositions.getLastIntervalAlive() > 0);
				break;
			}
		}
		return isalive;
	}

	public boolean isDataAvailable(int cagenumber) {
		boolean isavailable = false;
		for (Cage cage : cagesList) {
			String cagenumberString = cage.getRoi().getName().substring(4);
			if (Integer.valueOf(cagenumberString) == cagenumber) {
				isavailable = true;
				break;
			}
		}
		return isavailable;
	}

	public int getHorizontalSpanOfCages() {
		int leftPixel = -1;
		int rightPixel = -1;

		for (Cage cage : cagesList) {
			ROI2D roiCage = cage.getRoi();
			Rectangle2D rect = roiCage.getBounds2D();
			int left = (int) rect.getX();
			int right = left + (int) rect.getWidth();
			if (leftPixel < 0 || left < leftPixel)
				leftPixel = left;
			if (right > rightPixel)
				rightPixel = right;
		}

		return rightPixel - leftPixel;
	}

	public Polygon2D getPolygon2DEnclosingAllCages() {
		if (cagesList.size() < 1 || cagesList.get(0).getRoi() == null)
			return null;
		Polygon2D polygon = getROIPolygon2D(cagesList.get(0).getRoi());
		for (Cage cage : cagesList) {
			int col = cage.getProperties().getArrayColumn();
			int row = cage.getProperties().getArrayRow();
			Polygon2D n = getROIPolygon2D(cage.getRoi());
			if (col == 0 && row == 0) {
				transferPointToPolygon(0, polygon, n);
			} else if (col >= (nCagesAlongX - 1) && row == 0) {
				transferPointToPolygon(3, polygon, n);
			} else if (col == (nCagesAlongX - 1) && row == (nCagesAlongY - 1)) {
				transferPointToPolygon(2, polygon, n);
			} else if (col == 0 && row >= (nCagesAlongY - 1)) {
				transferPointToPolygon(1, polygon, n);
			}
		}
		return polygon;
	}

	private void transferPointToPolygon(int i, Polygon2D dest, Polygon2D source) {
		dest.xpoints[i] = source.xpoints[i];
		dest.ypoints[i] = source.ypoints[i];
	}

	private Polygon2D getROIPolygon2D(ROI2D roi) {
		Polygon2D polygon = null;
		if (roi instanceof ROI2DPolygon) {
			polygon = ((ROI2DPolygon) roi).getPolygon2D();
		} else {
			Rectangle rect = roi.getBounds();
			polygon = new Polygon2D(rect);
		}
		return polygon;
	}

	// --------------------------------------------------------

	public void transferCageSpotsToSequenceAsROIs(SequenceCamData seqCamData) {
		if (cagesList.size() > 0) {
			List<ROI2D> spotROIList = new ArrayList<ROI2D>(
					cagesList.get(0).spotsArray.getSpotsList().size() * cagesList.size());
			for (Cage cage : cagesList) {
				for (Spot spot : cage.spotsArray.getSpotsList())
					spotROIList.add(spot.getRoi());
			}
			Collections.sort(spotROIList, new Comparators.ROI2D_Name());
			seqCamData.getSequence().addROIs(spotROIList, true);
		}
	}

	public void transferROIsFromSequenceToCageSpots(SequenceCamData seqCamData) {
		// Use modern ROI finding API
		List<ROI2D> listSeqRois = seqCamData.findROIs("spot");
//		int T = 0;
//		Viewer v = seqCamData.getSequence().getFirstViewer();
//		if (v != null)
//			T = v.getPositionT();
		Collections.sort(listSeqRois, new Comparators.ROI_Name());
		for (Cage cage : cagesList) {
			Iterator<Spot> iteratorSpots = cage.spotsArray.getSpotsList().iterator();
			while (iteratorSpots.hasNext()) {
				Spot spot = iteratorSpots.next();
				String spotRoiName = spot.getRoi().getName();
				boolean found = false;

				Iterator<ROI2D> iteratorSeqRois = listSeqRois.iterator();
				while (iteratorSeqRois.hasNext()) {
					ROI2D roi = iteratorSeqRois.next();
					String roiName = roi.getName();
					if (roiName.equals(spotRoiName)) {
						spot.setRoi((ROI2DShape) roi);
						found = true;
						iteratorSeqRois.remove();
						break;
					}
				}
				if (!found)
					iteratorSpots.remove();
			}
		}
	}

	public Spot getSpotFromROIName(String name) {
		for (Cage cage : cagesList) {
			for (Spot spot : cage.spotsArray.getSpotsList()) {
				if (spot.getRoi().getName().contains(name))
					return spot;
			}
		}
		return null;
	}

	public void initCagesAndSpotsWithNFlies(int nflies) {
		for (Cage cage : cagesList) {
			cage.getProperties().setCageNFlies(nflies);
			cage.setNFlies(nflies);
		}
	}

	public ArrayList<Spot> getSpotsEnclosed(ROI2DPolygon envelopeRoi) {
		if (envelopeRoi == null)
			return getSpotsSelected();

		ArrayList<Spot> enclosedSpots = new ArrayList<Spot>();
		for (Cage cage : cagesList) {
			for (Spot spot : cage.spotsArray.getSpotsList()) {
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
		}
		return enclosedSpots;
	}

	public ArrayList<Spot> getSpotsSelected() {
		ArrayList<Spot> enclosedSpots = new ArrayList<Spot>();
		for (Cage cage : cagesList) {
			for (Spot spot : cage.spotsArray.getSpotsList()) {
				if (spot.getRoi().isSelected())
					enclosedSpots.add(spot);
			}
		}
		return enclosedSpots;
	}

	public SpotsArray getAllSpotsArray() {
		SpotsArray spotsArray = new SpotsArray();
		for (Cage cage : cagesList) {
			for (Spot spot : cage.spotsArray.getSpotsList()) {
				spotsArray.getSpotsList().add(spot);
			}
		}
		return spotsArray;
	}

	public Spot getSpotAtGlobalIndex(int indexT) {
		int i = 0;
		for (Cage cage : cagesList) {
			int count = cage.spotsArray.getSpotsList().size();
			if (i + count - 1 < indexT) {
				i += count;
				continue;
			}
			Spot spot = cage.getSpotsArray().getSpotsList().get(indexT - i);
			return spot;
		}
		return null;
	}

	public int getSpotGlobalPosition(Spot spot) {
		int i = 0;
		int cageID = spot.getProperties().getCageID();
		for (Cage cage : cagesList) {
			int count = cage.getSpotsArray().getSpotsList().size();
			if (cageID != cage.getProperties().getCageID()) {
				i += count;
				continue;
			}
			String name = spot.getRoi().getName();
			for (int j = 0; j < cage.getSpotsArray().getSpotsList().size(); j++) {
				if (name.equals(cage.getSpotsArray().getSpotsList().get(j).getRoi().getName())) {
					return i + j;
				}
			}
		}
		return 0;
	}

	public int getTotalNumberOfSpots() {
		int nspots = 0;
		for (Cage cage : cagesList) {
			nspots += cage.getSpotsArray().getSpotsList().size();
		}
		return nspots;
	}

	public TIntervalsArray getCagesListTimeIntervals() {
		return cagesListTimeIntervals;
	}

	public void mergeSpotsLists(CagesArray arrayToMerge) {
		for (Cage cage : cagesList) {
			for (Cage cageToMerge : arrayToMerge.cagesList) {
				if (cage.getProperties().getCagePosition() != cageToMerge.getProperties().getCagePosition())
					continue;
				cage.getSpotsArray().mergeSpots(cageToMerge.getSpotsArray());
			}
		}
	}

	public void setReadyToAnalyze(boolean setFilter, BuildSeriesOptions options) {
		for (Cage cage : cagesList) {
			cage.getSpotsArray().setReadyToAnalyze(setFilter, options);
		}
	}

	public void medianFilterFromSumToSumClean() {
		for (Cage cage : cagesList) {
			cage.getSpotsArray().medianFilterFromSumToSumClean();
		}
	}

	public void normalizeSpotMeasures() {
		for (Cage cage : cagesList)
			cage.normalizeSpotMeasures();
	}

	public void transferMeasuresToLevel2D() {
		for (Cage cage : cagesList) {
			cage.getSpotsArray().transferMeasuresToLevel2D();
		}
	}

	public void initLevel2DMeasures() {
		for (Cage cage : cagesList) {
			cage.getSpotsArray().initializeLevel2DMeasures();
		}
	}

	public boolean zzload_Spots(String resultsDirectory) {
		return false;
	}

	public void transferROIsMeasuresFromSequenceToSpots() {
		for (Cage cage : cagesList) {
			for (Spot spot : cage.getSpotsArray().getSpotsList()) {
				spot.transferRoiMeasuresToLevel2D();
			}
		}
	}

	public void transferSpotsMeasuresToSequenceAsROIs(Sequence seq) {
		List<ROI2D> seqRoisList = seq.getROI2Ds(false);
		Utilities.removeROI2DsMissingChar(seqRoisList, '_');
		List<ROI2D> newRoisList = new ArrayList<ROI2D>();
		int height = seq.getHeight();
		int i = 0;
		for (Cage cage : cagesList) {
			for (Spot spot : cage.getSpotsArray().getSpotsList()) {
				List<ROI2D> listOfRois = spot.transferMeasuresToRois(height);
				for (ROI2D roi : listOfRois) {
					if (roi != null)
						roi.setT(i);
				}
				newRoisList.addAll(listOfRois);
				i++;
			}
		}
		Utilities.mergeROI2DsListNoDuplicate(seqRoisList, newRoisList, seq);
		seq.removeAllROI();
		seq.addROIs(seqRoisList, false);
	}

	// ------------------------------------------------

	public boolean load_SpotsMeasures(String directory) {
		SpotsArray spotsArray = getSpotsArrayFromAllCages();
		boolean flag = spotsArray.loadSpotsMeasures(directory);
		return flag;
	}

	public boolean load_SpotsAll(String directory) {
		boolean flag = getSpotsArrayFromAllCages().loadSpotsAll(directory);
		return flag;
	}

	public boolean save_SpotsAll(String directory) {
		boolean flag = getSpotsArrayFromAllCages().saveSpotsAll(directory);
		return flag;
	}

	public boolean save_SpotsMeasures(String directory) {
		if (directory == null)
			return false;
		SpotsArray localSpotsArray = getSpotsArrayFromAllCages();
		localSpotsArray.saveSpotsMeasuresOptimized(directory);
		return true;
	}

	public SpotsArray getSpotsArrayFromAllCages() {
		SpotsArray spotsArray = new SpotsArray();
		if (cagesList.size() > 0) {
			for (Cage cage : cagesList) {
				List<Spot> spotsList = cage.getSpotsArray().getSpotsList();
				spotsArray.getSpotsList().addAll(spotsList);
			}
		}
		return spotsArray;
	}

	public void mapSpotsToCagesColumnRow() {
		for (Cage cage : cagesList) {
			cage.mapSpotsToCageColumnRow();
		}
	}

	public void cleanUpSpotNames() {
		for (Cage cage : cagesList) {
			cage.cleanUpSpotNames();
		}
	}
}
