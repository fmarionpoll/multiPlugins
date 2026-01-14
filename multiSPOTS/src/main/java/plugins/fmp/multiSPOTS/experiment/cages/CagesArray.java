package plugins.fmp.multiSPOTS.experiment.cages;

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

import icy.roi.ROI;
import icy.roi.ROI2D;
import icy.sequence.Sequence;
import icy.type.geom.Polygon2D;
import icy.util.XMLUtil;
import plugins.fmp.multiSPOTS.experiment.Experiment;
import plugins.fmp.multiSPOTS.experiment.SequenceCamData;
import plugins.fmp.multiSPOTS.experiment.spots.Spot;
import plugins.fmp.multiSPOTS.experiment.spots.SpotsArray;
import plugins.fmp.multiSPOTS.tools.Comparators;
import plugins.fmp.multiSPOTS.tools.JComponents.Dialog;
import plugins.fmp.multiSPOTS.tools.ROI2D.ROIUtilities;
import plugins.kernel.roi.roi2d.ROI2DArea;
import plugins.kernel.roi.roi2d.ROI2DPolygon;
import plugins.kernel.roi.roi2d.ROI2DShape;

public class CagesArray {
	public ArrayList<Cage> cagesList = new ArrayList<Cage>();
	public int nCagesAlongX = 6;
	public int nCagesAlongY = 8;

	// ---------- not saved to xml:
	public long detectFirst_Ms = 0;
	public long detectLast_Ms = 0;
	public long detectBin_Ms = 60000;
	public int detect_threshold = 0;
	public int detect_nframes = 0;

	// ----------------------------

	private final String ID_CAGES = "Cages";
	private final String ID_NCAGES = "n_cages";
	private final String ID_DROSOTRACK = "drosoTrack";
	private final String ID_NBITEMS = "nb_items";
	private final String ID_CAGELIMITS = "Cage_Limits";
	private final String ID_FLYDETECTED = "Fly_Detected";

	private final static String ID_MCDROSOTRACK_XML = "MCdrosotrack.xml";

	public void clearAllMeasures(int option_detectCage) {
		for (Cage cage : cagesList) {
			int cagenb = cage.getCageNumberInteger();
			if (option_detectCage < 0 || option_detectCage == cagenb)
				cage.clearMeasures();
		}
	}

	public void removeCages() {
		cagesList.clear();
	}

	public void mergeLists(CagesArray cagesm) {
		for (Cage cagem : cagesm.cagesList) {
			if (!isPresent(cagem))
				cagesList.add(cagem);
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

	public boolean xmlWriteCagesToFileNoQuestion(String tempname) {
		if (tempname == null)
			return false;
		final Document doc = XMLUtil.createDocument(true);
		if (doc == null)
			return false;

		Node node = XMLUtil.addElement(XMLUtil.getRootElement(doc), ID_DROSOTRACK);
		if (node == null)
			return false;

		int index = 0;
		Element xmlVal = XMLUtil.addElement(node, ID_CAGES);
		int ncages = cagesList.size();
		XMLUtil.setAttributeIntValue(xmlVal, ID_NCAGES, ncages);
		for (Cage cage : cagesList) {
			cage.xmlSaveCage(xmlVal, index);
			index++;
		}
		return XMLUtil.saveDocument(doc, tempname);
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
		filedummy = Dialog.selectFiles(directory, "xml");
		boolean wasOk = false;
		if (filedummy != null) {
			for (int i = 0; i < filedummy.length; i++) {
				String csFile = filedummy[i];
				wasOk &= xmlReadCagesFromFileNoQuestion(csFile, exp);
			}
		}
		return wasOk;
	}

	public boolean xmlReadCagesFromFileNoQuestion(String tempname) {
		if (tempname == null)
			return false;
		final Document doc = XMLUtil.loadDocument(tempname);
		if (doc == null)
			return false;

		if (xmlLoadCages(doc)) {
			return true;
		} else {
			System.out.println("Cages:xmlReadCagesFromFileNoQuestion() failed to load cages from file");
			return false;
		}
	}

	public boolean xmlReadCagesFromFileNoQuestion(String tempname, Experiment exp) {
		if (tempname == null)
			return false;
		final Document doc = XMLUtil.loadDocument(tempname);
		if (doc == null)
			return false;

		if (xmlLoadCages(doc)) {
			transferCagesToSequenceAsROIs(exp.seqCamData.seq);
			return true;
		} else {
			System.out.println("Cages:xmlReadCagesFromFileNoQuestion() failed to load cages from file");
			return false;
		}
	}

	private boolean xmlLoadCages(Document doc) {
		Node node = XMLUtil.getElement(XMLUtil.getRootElement(doc), ID_DROSOTRACK);
		if (node == null)
			return false;

		cagesList.clear();
		Element xmlVal = XMLUtil.getElement(node, ID_CAGES);
		if (xmlVal != null) {
			int ncages = XMLUtil.getAttributeIntValue(xmlVal, ID_NCAGES, 0);
			for (int index = 0; index < ncages; index++) {
				Cage cage = new Cage();
				cage.xmlLoadCage(xmlVal, index);
				cagesList.add(cage);
			}
		} else {
			List<ROI2D> cageLimitROIList = new ArrayList<ROI2D>();
			if (xmlLoadCagesLimits_v0(node, cageLimitROIList)) {
				List<FlyPositions> flyPositionsList = new ArrayList<FlyPositions>();
				xmlLoadFlyPositions_v0(node, flyPositionsList);
				transferDataToCages_v0(cageLimitROIList, flyPositionsList);
			} else
				return false;
		}
		return true;
	}

	// --------------

	public void copy(CagesArray cag) {
//		detect.copyParameters(cag.detect);	
		cagesList.clear();
		for (Cage ccag : cag.cagesList) {
			Cage cagi = new Cage();
			cagi.copyCage(ccag);
			cagesList.add(cagi);
		}
	}

	// --------------

	private void transferDataToCages_v0(List<ROI2D> cageLimitROIList, List<FlyPositions> flyPositionsList) {
		cagesList.clear();
		Collections.sort(cageLimitROIList, new Comparators.ROI2D_Name_Comparator());
		int ncages = cageLimitROIList.size();
		for (int index = 0; index < ncages; index++) {
			Cage cage = new Cage();
			cage.setRoi((ROI2DShape) cageLimitROIList.get(index));
			cage.flyPositions = flyPositionsList.get(index);
			cagesList.add(cage);
		}
	}

	private boolean xmlLoadCagesLimits_v0(Node node, List<ROI2D> cageLimitROIList) {
		if (node == null)
			return false;
		Element xmlVal = XMLUtil.getElement(node, ID_CAGELIMITS);
		if (xmlVal == null)
			return false;
		cageLimitROIList.clear();
		int nb_items = XMLUtil.getAttributeIntValue(xmlVal, ID_NBITEMS, 0);
		for (int i = 0; i < nb_items; i++) {
			ROI2DPolygon roi = (ROI2DPolygon) ROI.create("plugins.kernel.roi.roi2d.ROI2DPolygon");
			Element subnode = XMLUtil.getElement(xmlVal, "cage" + i);
			roi.loadFromXML(subnode);
			cageLimitROIList.add((ROI2D) roi);
		}
		return true;
	}

	private boolean xmlLoadFlyPositions_v0(Node node, List<FlyPositions> flyPositionsList) {
		if (node == null)
			return false;
		Element xmlVal = XMLUtil.getElement(node, ID_FLYDETECTED);
		if (xmlVal == null)
			return false;
		flyPositionsList.clear();
		int nb_items = XMLUtil.getAttributeIntValue(xmlVal, ID_NBITEMS, 0);
		int ielement = 0;
		for (int i = 0; i < nb_items; i++) {
			Element subnode = XMLUtil.getElement(xmlVal, "cage" + ielement);
			FlyPositions pos = new FlyPositions();
			pos.loadXYTseriesFromXML(subnode);
			flyPositionsList.add(pos);
			ielement++;
		}
		return true;
	}

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

	public List<ROI2D> getRoisWithCageName(Sequence seq) {
		List<ROI2D> roiList = seq.getROI2Ds();
		List<ROI2D> cageList = new ArrayList<ROI2D>();
		for (ROI2D roi : roiList) {
			String csName = roi.getName();
			if ((roi instanceof ROI2DPolygon) || (roi instanceof ROI2DArea)) {
				if ((csName.length() > 4 && csName.substring(0, 4).contains("cage") || csName.contains("Polygon2D")))
					cageList.add(roi);
			}
		}
		return cageList;
	}

	// --------------

	public void transferCagesToSequenceAsROIs(Sequence seq) {
		seq.removeROIs(ROIUtilities.getROIsContainingString("cage", seq), false);
		List<ROI2D> cageROIList = new ArrayList<ROI2D>(cagesList.size());
		for (Cage cage : cagesList)
			cageROIList.add(cage.getRoi());
		seq.addROIs(cageROIList, true);
	}

	public void transferROIsFromSequenceToCages(Sequence seq) {
		List<ROI2D> roiList = getRoisWithCageName(seq);
		Collections.sort(roiList, new Comparators.ROI2D_Name_Comparator());
		addMissingCages(roiList);
		removeOrphanCages(roiList);
		Collections.sort(cagesList, new Comparators.Cage_Name_Comparator());
	}

	public void removeAllRoiDetFromSequence(SequenceCamData seqCamData) {
		ArrayList<ROI2D> seqlist = seqCamData.seq.getROI2Ds();
		for (ROI2D roi : seqlist) {
			if (!(roi instanceof ROI2DShape))
				continue;
			if (!roi.getName().contains("det"))
				continue;
			seqCamData.seq.removeROI(roi);
		}
	}

	public void transferNFliesFromCagesToSpots(SpotsArray spotsArray) {
		for (Spot spot : spotsArray.spotsList) {
			for (Cage cage : cagesList) {
				int cagenb = cage.getCageNumberInteger();
				if (spot.cageID != cagenb)
					continue;
				spot.spotNFlies = cage.cageNFlies;
			}
		}
	}

	public void transferNFliesFromSpotsToCages(SpotsArray spotsArray) {
		for (Cage cage : cagesList) {
			int cagenb = cage.getCageNumberInteger();
			for (Spot spot : spotsArray.spotsList) {
				if (spot.cageID != cagenb)
					continue;
				cage.cageNFlies = spot.spotNFlies;
			}
		}
	}

	public void setCageNbFromSpotsArray(SpotsArray spotsArray) {
		spotsArray.updatePlateIndexToCageIndexes(spotsArray.nColumnsPerCage, spotsArray.nRowsPerCage);
	}

	public Cage getCageFromNumber(int number) {
		Cage cageFound = null;
		for (Cage cage : cagesList) {
			if (number == cage.getCageNumberInteger()) {
				cageFound = cage;
				break;
			}
		}
		return cageFound;
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
			Collections.sort(cage.flyPositions.flyPositionList, new Comparators.XYTaValue_Tindex_Comparator());
	}

	public void initFlyPositions(int option_cagenumber) {
		int nbcages = cagesList.size();
		for (int i = 0; i < nbcages; i++) {
			Cage cage = cagesList.get(i);
			if (option_cagenumber != -1 && cage.getCageNumberInteger() != option_cagenumber)
				continue;
			if (cage.cageNFlies > 0) {
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
		updateArrayIndexes();
		Polygon2D polygon = getCoordinatesOfROI(cagesList.get(0).getRoi());
		for (Cage cage : cagesList) {
			int col = cage.arrayColumn;
			int row = cage.arrayRow;
			Polygon2D n = getCoordinatesOfROI(cage.getRoi());
			if (col == 0 && row == 0) {
				transferPointToPolygon(0, polygon, n);
			} else if (col == (nCagesAlongX - 1) && row == 0) {
				transferPointToPolygon(3, polygon, n);
			} else if (col == (nCagesAlongX - 1) && row == (nCagesAlongY - 1)) {
				transferPointToPolygon(2, polygon, n);
			} else if (col == 0 && row == (nCagesAlongY - 1)) {
				transferPointToPolygon(1, polygon, n);
			}
		}
		return polygon;
	}

	public void updateArrayIndexes() {
		int upleft = 0;
		int downleft = 1;
		int upright = 3;
		Polygon2D polygon = getCoordinatesOfROI(cagesList.get(0).getRoi());
		double width0 = polygon.xpoints[upright] - polygon.xpoints[upleft];
		double height0 = polygon.ypoints[downleft] - polygon.ypoints[upleft];

		nCagesAlongX = 1;
		nCagesAlongY = 1;
		for (Cage cage : cagesList) {
			Polygon2D n = getCoordinatesOfROI(cage.getRoi());

			int arrayColumn = (int) Math.round((n.xpoints[upright] - polygon.xpoints[upleft]) / width0);
			cage.arrayColumn = arrayColumn - 1;
			if (nCagesAlongX < arrayColumn)
				nCagesAlongX = arrayColumn;

			int arrayRow = (int) Math.round((n.ypoints[downleft] - polygon.ypoints[upleft]) / height0);
			cage.arrayRow = arrayRow - 1;
			if (nCagesAlongY < arrayRow)
				nCagesAlongY = arrayRow;
		}
	}

	private void transferPointToPolygon(int i, Polygon2D dest, Polygon2D source) {
		dest.xpoints[i] = source.xpoints[i];
		dest.ypoints[i] = source.ypoints[i];
	}

	private Polygon2D getCoordinatesOfROI(ROI2D roi) {
		Polygon2D polygon = null;
		if (roi instanceof ROI2DPolygon) {
			polygon = ((ROI2DPolygon) roi).getPolygon2D();
		} else {
			Rectangle rect = roi.getBounds();
			polygon = new Polygon2D(rect);
		}
		return polygon;
	}

}
