package plugins.fmp.multitools.experiment.cages;

import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import icy.roi.ROI2D;
import icy.sequence.Sequence;
import icy.type.geom.Polygon2D;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillaries.Capillary;
import plugins.fmp.multitools.experiment.ids.CapillaryID;
import plugins.fmp.multitools.experiment.ids.SpotID;
import plugins.fmp.multitools.experiment.sequence.ROIOperation;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.experiment.sequence.TIntervalsArray;
import plugins.fmp.multitools.experiment.spots.Spot;
import plugins.fmp.multitools.experiment.spots.SpotString;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.Comparators;
import plugins.fmp.multitools.tools.ROI2D.ROI2DUtilities;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.kernel.roi.roi2d.ROI2DArea;
import plugins.kernel.roi.roi2d.ROI2DPolygon;
import plugins.kernel.roi.roi2d.ROI2DShape;

public class Cages {
	public ArrayList<Cage> cagesList = new ArrayList<Cage>();
	private TIntervalsArray cagesListTimeIntervals = null;

	// Transient map to store CageCapillariesComputation instances for each cage
	// This allows efficient access to computed L+R measures
	private transient java.util.Map<Integer, CageCapillariesComputation> cageComputations = new java.util.HashMap<>();

	private CagesPersistence persistence = new CagesPersistence();

	public CagesPersistence getPersistence() {
		return persistence;
	}

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

	public final String ID_MS96_cages_XML = "MS96_cages.xml";
	public final String ID_MS96_spotsMeasures_XML = "MS96_spotsMeasures.xml";
	public final String ID_MS96_fliesPositions_XML = "MS96_fliesPositions.xml";

	public Cages() {
	}

	public ArrayList<Cage> getCageList() {
		return cagesList;
	}

	public void setCageList(ArrayList<Cage> cagesList) {
		this.cagesList = cagesList;
	}

	public long getDetectFirst_Ms() {
		return detectFirst_Ms;
	}

	public void setDetectFirst_Ms(long detectFirst_Ms) {
		this.detectFirst_Ms = detectFirst_Ms;
	}

	public long getDetectLast_Ms() {
		return detectLast_Ms;
	}

	public void setDetectLast_Ms(long detectLast_Ms) {
		this.detectLast_Ms = detectLast_Ms;
	}

	public long getDetectBin_Ms() {
		return detectBin_Ms;
	}

	public void setDetectBin_Ms(long detectBin_Ms) {
		this.detectBin_Ms = detectBin_Ms;
	}

	public int getDetect_threshold() {
		return detect_threshold;
	}

	public void setDetect_threshold(int detect_threshold) {
		this.detect_threshold = detect_threshold;
	}

	public int getDetect_nframes() {
		return detect_nframes;
	}

	public void setDetect_nframes(int detect_nframes) {
		this.detect_nframes = detect_nframes;
	}

	public Cages(ArrayList<Cage> cagesListFrom) {
		copyCagesInfos(cagesListFrom);
	}

	public Cages(int ncolumns, int nrows) {
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

	public void mergeLists(Cages cageArrayToMerge) {
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
		return persistence.save_Cages(this, directory);
	}

	public boolean loadCagesMeasures(String directory) {
		return persistence.load_Cages(this, directory);
	}

	// -----------------------------------------------------

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

	public Cage findFirstCageWithSelectedSpot(Spots allSpots) {
		if (allSpots == null) {
			return null;
		}
		for (Cage cage : cagesList) {
			List<Spot> spots = cage.getSpotList(allSpots);
			for (Spot spot : spots) {
				ROI2D roi = spot.getRoi();
				if (roi.isSelected()) {
					return cage;
				}
			}
		}
		return null;
	}

	public Cage findFirstCageWithSelectedCapillary(Capillaries allCapillaries) {
		if (allCapillaries == null) {
			return null;
		}
		for (Cage cage : cagesList) {
			List<Capillary> capillaries = cage.getCapillaries(allCapillaries);
			for (Capillary cap : capillaries) {
				ROI2D roi = cap.getRoi();
				if (roi != null && roi.isSelected()) {
					return cage;
				}
			}
		}
		return null;
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
			if (cage.getCageID() == cageID)
				return cage;
		}
		return null;
	}

	// -----------------------------------------
	// Reverse lookup helper methods (ID-based)

	/**
	 * Finds the cage containing the specified capillary ID. Uses simple iteration
	 * through all cages.
	 * 
	 * @param capID the CapillaryID to search for
	 * @return the Cage containing this capillary, or null if not found
	 */
	public Cage findCageContainingCapillary(CapillaryID capID) {
		if (capID == null) {
			return null;
		}
		for (Cage cage : cagesList) {
			if (cage.getCapillaryIDs().contains(capID)) {
				return cage;
			}
		}
		return null;
	}

	/**
	 * Finds all cages containing the specified capillary ID. Returns a list in case
	 * of many-to-many relationships.
	 * 
	 * @param capID the CapillaryID to search for
	 * @return list of Cages containing this capillary (may be empty)
	 */
	public List<Cage> findCagesContainingCapillary(CapillaryID capID) {
		List<Cage> result = new ArrayList<>();
		if (capID == null) {
			return result;
		}
		for (Cage cage : cagesList) {
			if (cage.getCapillaryIDs().contains(capID)) {
				result.add(cage);
			}
		}
		return result;
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

	public Cage getCageFromSpotROIName(String name, Spots allSpots) {
		if (allSpots == null) {
			return null;
		}
		for (Cage cage : cagesList) {
			List<Spot> spots = cage.getSpotList(allSpots);
			for (Spot spot : spots) {
				if (spot.getRoi().getName().contains(name))
					return cage;
			}
		}
		return null;
	}

	public void createEmptyCagesFromCapillaries(Capillaries capillaries) {
		for (Capillary cap : capillaries.getList()) {
			int cageID = cap.getCageID();
			Cage cage = getCageFromID(cageID);
			if (cage != null)
				continue;

			cage = new Cage();
			cage.setCageID(cageID);
			addCageIfUnique(cage);
		}
	}

	public boolean addCageIfUnique(Cage newCage) {
		boolean created = false;
		int newCageID = newCage.getCageID();
		Cage cage = getCageFromID(newCageID);
		if (cage == null) {
			created = true;
			cagesList.add(newCage);
		}
		return created;
	}

	// --------------

	public void transferCagesToSequenceAsROIs(SequenceCamData seqCamData) {
		// Use modern ROI operation for removing existing cage ROIs
		seqCamData.processROIs(ROIOperation.removeROIs("cage"));

		List<ROI2D> cageROIList = new ArrayList<ROI2D>(cagesList.size());
		for (Cage cage : cagesList) {
			ROI2D roi = cage.getRoi();
			if (roi != null)
				cageROIList.add(roi);
		}
		Sequence sequence = seqCamData.getSequence();
		if (sequence != null)
			sequence.addROIs(cageROIList, true);
	}

	public void updateCagesFromSequence(SequenceCamData seqCamData) {
		List<ROI2D> roiList = seqCamData.findROIsMatchingNamePattern("cage");
		Collections.sort(roiList, new Comparators.ROI2D_Name());
		transferROIsToCages(roiList);
		addMissingCages(roiList);
		Collections.sort(cagesList, new Comparators.Cage_Name());
	}

	private void transferROIsToCages(List<ROI2D> roiList) {
		if (cagesList.size() < 1)
			return;

		for (Cage cage : cagesList) {
			if (roiList.isEmpty())
				return;

			String strCageID = cage.formatCageNumberToString(cage.getCageID());
			Iterator<ROI2D> iterator = roiList.iterator();
			while (iterator.hasNext()) {
				ROI2D roi = iterator.next();
				String roiName = roi.getName();
				if (roiName != null && roiName.contains(strCageID)) {
					cage.setCageRoi(roi);
					iterator.remove();
					break;
				}
			}
		}
	}

	private void addMissingCages(List<ROI2D> roiList) {
		Iterator<ROI2D> iterator = roiList.iterator();
		while (iterator.hasNext()) {
			ROI2D roi = iterator.next();
			if (roi.getName() == null)
				continue;

			Cage cage = new Cage();
			cage.setCageRoi((ROI2DShape) roi);
			addCageIfUnique(cage);
			iterator.remove();
		}
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

	public void transferNFliesFromCapillariesToCageBox(List<Capillary> capList) {
		for (Cage cage : cagesList) {
			int cagenb = cage.getCageID();
			for (Capillary cap : capList) {
				if (cap.getCageID() == cagenb) {
					cage.setCageNFlies(cap.getProperties().nFlies);
					break;
				}
			}
		}
	}

	public void transferNFliesFromCagesToCapillaries(List<Capillary> capList) {
		for (Cage cage : cagesList) {
			int cageIndex = cage.getCageID();
			for (Capillary cap : capList) {
				if (cap.getCageID() != cageIndex)
					continue;
				cap.getProperties().nFlies = cage.getCageNFlies();
			}
		}
	}

	public void setCageNbFromName(List<Capillary> capList) {
		for (Capillary cap : capList) {
			int cageIndex = cap.getCageIndexFromRoiName();
			cap.setCageID(cageIndex);
		}
	}

	public void setFirstAndLastCageToZeroFly() {
		for (Cage cage : cagesList) {
			if (cage.getCageRoi2D().getName().contains("000") || cage.getCageRoi2D().getName().contains("009"))
				cage.setCageNFlies(0);
		}
	}

	public void cagesToROIs(SequenceCamData seqCamData) {
		transferCagesToSequenceAsROIs(seqCamData);
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

	public void initCagesTmsForFlyPositions(long[] camImages_ms) {
		if (camImages_ms == null)
			return;
		for (Cage cage : cagesList) {
			if (cage.flyPositions != null && cage.flyPositions.flyPositionList != null) {
				for (FlyPosition flyPos : cage.flyPositions.flyPositionList) {
					if (flyPos.flyIndexT >= 0 && flyPos.flyIndexT < camImages_ms.length) {
						flyPos.tMs = camImages_ms[flyPos.flyIndexT];
					}
				}
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

	public void transferCageSpotsToSequenceAsROIs(SequenceCamData seqCamData, Spots allSpots) {
		if (cagesList.size() > 0 && allSpots != null) {
			List<ROI2D> spotROIList = new ArrayList<ROI2D>();
			for (Cage cage : cagesList) {
				List<Spot> spots = cage.getSpotList(allSpots);
				for (Spot spot : spots)
					spotROIList.add(spot.getRoi());
			}
			Collections.sort(spotROIList, new Comparators.ROI2D_Name());
			seqCamData.getSequence().addROIs(spotROIList, true);
		}
	}

	public void transferROIsFromSequenceToCageSpots(SequenceCamData seqCamData, Spots allSpots) {
		if (allSpots == null) {
			return;
		}
		// Use modern ROI finding API
		List<ROI2D> listSeqRois = seqCamData.findROIsMatchingNamePattern("spot");
		Collections.sort(listSeqRois, new Comparators.ROI_Name());
		for (Cage cage : cagesList) {
			List<Spot> spots = cage.getSpotList(allSpots);
			Iterator<Spot> iteratorSpots = spots.iterator();
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
				if (!found) {
					// Remove spot ID if ROI not found
					cage.getSpotIDs().remove(spot.getSpotID());
					allSpots.getSpotList().remove(spot);
				}
			}
		}
	}

	public Spot getSpotFromROIName(String name, Spots allSpots) {
		if (allSpots == null) {
			return null;
		}
		for (Cage cage : cagesList) {
			List<Spot> spots = cage.getSpotList(allSpots);
			for (Spot spot : spots) {
				if (spot.getRoi().getName().contains(name))
					return spot;
			}
		}
		return null;
	}

	public ArrayList<Spot> getSpotsEnclosed(ROI2DPolygon envelopeRoi, Spots allSpots) {
		if (envelopeRoi == null)
			return getSpotsSelected(allSpots);

		ArrayList<Spot> enclosedSpots = new ArrayList<Spot>();
		if (allSpots == null) {
			return enclosedSpots;
		}
		for (Cage cage : cagesList) {
			List<Spot> spots = cage.getSpotList(allSpots);
			for (Spot spot : spots) {
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

	public ArrayList<Spot> getSpotsSelected(Spots allSpots) {
		ArrayList<Spot> enclosedSpots = new ArrayList<Spot>();
		if (allSpots == null) {
			return enclosedSpots;
		}
		for (Cage cage : cagesList) {
			List<Spot> spots = cage.getSpotList(allSpots);
			for (Spot spot : spots) {
				if (spot.getRoi().isSelected())
					enclosedSpots.add(spot);
			}
		}
		return enclosedSpots;
	}

	public Spots getAllSpotsArray(Spots allSpots) {
		if (allSpots == null) {
			return new Spots();
		}
		Spots result = new Spots();
		for (Cage cage : cagesList) {
			List<Spot> spots = cage.getSpotList(allSpots);
			for (Spot spot : spots) {
				result.getSpotList().add(spot);
			}
		}
		return result;
	}

	public Spot getSpotAtGlobalIndex(int indexT, Spots allSpots) {
		if (allSpots == null) {
			return null;
		}
		int i = 0;
		for (Cage cage : cagesList) {
			List<Spot> spots = cage.getSpotList(allSpots);
			int count = spots.size();
			if (i + count - 1 < indexT) {
				i += count;
				continue;
			}
			Spot spot = spots.get(indexT - i);
			return spot;
		}
		return null;
	}

	public int getSpotGlobalPosition(Spot spot, Spots allSpots) {
		if (allSpots == null) {
			return 0;
		}
		int i = 0;
		int cageID = spot.getProperties().getCageID();
		for (Cage cage : cagesList) {
			List<Spot> spots = cage.getSpotList(allSpots);
			int count = spots.size();
			if (cageID != cage.getProperties().getCageID()) {
				i += count;
				continue;
			}
			String name = spot.getRoi().getName();
			for (int j = 0; j < spots.size(); j++) {
				if (name.equals(spots.get(j).getRoi().getName())) {
					return i + j;
				}
			}
		}
		return 0;
	}

	public int getTotalNumberOfSpots(Spots allSpots) {
		if (allSpots == null) {
			return 0;
		}
		int nspots = 0;
		for (Cage cage : cagesList) {
			nspots += cage.getSpotList(allSpots).size();
		}
		return nspots;
	}

	public TIntervalsArray getCagesListTimeIntervals() {
		return cagesListTimeIntervals;
	}

	public void mergeSpotsLists(Cages arrayToMerge, Spots allSpots, Spots allSpotsToMerge) {
		if (allSpots == null || allSpotsToMerge == null) {
			return;
		}
		for (Cage cage : cagesList) {
			for (Cage cageToMerge : arrayToMerge.cagesList) {
				if (cage.getProperties().getCagePosition() != cageToMerge.getProperties().getCagePosition())
					continue;
				List<Spot> spots = cage.getSpotList(allSpots);
				List<Spot> spotsToMerge = cageToMerge.getSpotList(allSpotsToMerge);
				// Create temporary SpotsArray for merge operation
				Spots temp1 = new Spots();
				temp1.getSpotList().addAll(spots);
				Spots temp2 = new Spots();
				temp2.getSpotList().addAll(spotsToMerge);
				temp1.mergeSpots(temp2);
				// Update IDs after merge
				cage.getSpotIDs().clear();
				for (Spot spot : temp1.getSpotList()) {
					cage.getSpotIDs()
							.add(spot.getSpotID());
				}
			}
		}
	}

	public void setReadyToAnalyze(boolean setFilter, BuildSeriesOptions options, Spots allSpots) {
		if (allSpots == null) {
			return;
		}
		for (Cage cage : cagesList) {
			List<Spot> spots = cage.getSpotList(allSpots);
			Spots temp = new Spots();
			temp.getSpotList().addAll(spots);
			temp.setReadyToAnalyze(setFilter, options);
		}
	}

	public void medianFilterFromSumToSumClean(Spots allSpots) {
		if (allSpots == null) {
			return;
		}
		for (Cage cage : cagesList) {
			List<Spot> spots = cage.getSpotList(allSpots);
			Spots temp = new Spots();
			temp.getSpotList().addAll(spots);
			temp.medianFilterFromSumToSumClean();
		}
	}

	public void normalizeSpotMeasures(Spots allSpots) {
		if (allSpots == null) {
			return;
		}
		for (Cage cage : cagesList)
			cage.normalizeSpotMeasures(allSpots);
	}

	public void transferMeasuresToLevel2D(Spots allSpots) {
		if (allSpots == null) {
			return;
		}
		for (Cage cage : cagesList) {
			List<Spot> spots = cage.getSpotList(allSpots);
			Spots temp = new Spots();
			temp.getSpotList().addAll(spots);
			temp.transferMeasuresToLevel2D();
		}
	}

	public void initLevel2DMeasures(Spots allSpots) {
		if (allSpots == null) {
			return;
		}
		for (Cage cage : cagesList) {
			List<Spot> spots = cage.getSpotList(allSpots);
			Spots temp = new Spots();
			temp.getSpotList().addAll(spots);
			temp.initializeLevel2DMeasures();
		}
	}

	public boolean zzload_Spots(String resultsDirectory) {
		return false;
	}

	public void transferROIsMeasuresFromSequenceToSpots(Spots allSpots) {
		if (allSpots == null) {
			return;
		}
		for (Cage cage : cagesList) {
			List<Spot> spots = cage.getSpotList(allSpots);
			for (Spot spot : spots) {
				spot.transferRoiMeasuresToLevel2D();
			}
		}
	}

	public void transferSpotsMeasuresToSequenceAsROIs(Sequence seq, Spots allSpots) {
		if (allSpots == null) {
			return;
		}
		List<ROI2D> seqRoisList = seq.getROI2Ds(false);
		ROI2DUtilities.removeROI2DsMissingChar(seqRoisList, '_');
		List<ROI2D> newRoisList = new ArrayList<ROI2D>();
		int height = seq.getHeight();
		int i = 0;
		for (Cage cage : cagesList) {
			List<Spot> spots = cage.getSpotList(allSpots);
			for (Spot spot : spots) {
				List<ROI2D> listOfRois = spot.transferMeasuresToRois(height);
				for (ROI2D roi : listOfRois) {
					if (roi != null)
						roi.setT(i);
				}
				newRoisList.addAll(listOfRois);
				i++;
			}
		}
		ROI2DUtilities.mergeROI2DsListNoDuplicate(seqRoisList, newRoisList, seq);
		seq.removeAllROI();
		seq.addROIs(seqRoisList, false);
	}

	// ------------------------------------------------

	public boolean load_SpotsMeasures(String directory, Spots allSpots) {
		if (allSpots == null) {
			return false;
		}
		boolean flag = allSpots.loadSpotsMeasures(directory);
		return flag;
	}

	public boolean load_SpotsAll(String directory, Spots allSpots) {
		if (allSpots == null) {
			return false;
		}
		boolean flag = allSpots.loadSpotsAll(directory);
		return flag;
	}

	public boolean save_SpotsAll(String directory, Spots allSpots) {
		if (allSpots == null) {
			return false;
		}
		boolean flag = allSpots.saveSpotsAll(directory);
		return flag;
	}

	public boolean save_SpotsMeasures(String directory, Spots allSpots) {
		if (directory == null || allSpots == null)
			return false;
		allSpots.saveSpotsMeasuresOptimized(directory);
		return true;
	}

	public Spots getSpotsArrayFromAllCages(Spots allSpots) {
		if (allSpots == null) {
			return new Spots();
		}
		Spots result = new Spots();
		if (cagesList.size() > 0) {
			for (Cage cage : cagesList) {
				List<Spot> spotsList = cage.getSpotList(allSpots);
				result.getSpotList().addAll(spotsList);
			}
		}
		return result;
	}

	public void mapSpotsToCagesColumnRow(Spots allSpots) {
		if (allSpots == null) {
			return;
		}
		for (Cage cage : cagesList) {
			cage.mapSpotsToCageColumnRow(allSpots);
		}
	}

	public void cleanUpSpotNames(Spots allSpots) {
		if (allSpots == null) {
			return;
		}
		for (Cage cage : cagesList) {
			cage.cleanUpSpotNames(allSpots);
		}
	}

	// --------------------------------------------------------
	// Capillary measure computation methods - delegated to
	// CagesCapillariesComputation

	/**
	 * Computes evaporation correction for all capillaries across all cages.
	 * Delegates to CagesCapillariesComputation.
	 * 
	 * @param exp The experiment containing all capillaries
	 */
	/**
	 * Prepares computations for capillary measures based on the provided options.
	 * This includes dispatching capillaries to cages, computing evaporation
	 * correction, and computing L+R measures.
	 * 
	 * @param exp            The experiment
	 * @param resultsOptions The options defining which computations to perform
	 */
	public void prepareComputations(Experiment exp, ResultsOptions resultsOptions) {
		exp.dispatchCapillariesToCages();

		clearComputedMeasures(exp);

		// Ensure gulps/derivatives exist when charting gulp-based outputs.
		// NOTE: We do NOT auto-detect gulps here anymore.
		// Detection relies on thresholds that the user must adapt via the detection
		// dialog.
		// If data is missing, charts will show "(no data)", prompting the user to run
		// detection.
		/*
		 * if (isGulpBased(resultsOptions.resultType)) { // Check if any capillary is
		 * missing gulps boolean missingGulps = false; BuildSeriesOptions options =
		 * null; for (Cage cage : cagesList) { for (Capillary cap :
		 * cage.getCapillaries().getList()) { if
		 * (!cap.isThereAnyMeasuresDone(EnumResults.SUMGULPS)) { missingGulps = true; }
		 * if (options == null && cap.getGulpsOptions() != null) { options =
		 * cap.getGulpsOptions(); } } }
		 * 
		 * if (missingGulps) { try { if (options == null) options = new
		 * BuildSeriesOptions(); options.buildDerivative = true; options.buildGulps =
		 * true; options.detectAllGulps = true; // Force detection on all kymos for
		 * charting options.detectAllKymos = true; new GulpDetector().detectGulps(exp,
		 * options); } catch (Exception e) { // Keep charting resilient: if detection
		 * cannot run (missing kymos etc.), caller may show empty plots.
		 * System.err.println("Cages.prepareComputations: failed to detect gulps: " +
		 * e.getMessage()); } } }
		 */

		// TOPLEVEL is defined as evaporation-corrected and displayed as (t - t0).
		// TOPRAW is handled as (t - t0) at display/export level without evaporation.
		if (resultsOptions.resultType == EnumResults.TOPLEVEL) {
			computeEvaporationCorrection(exp);
		}

		// Compute L+R measures if needed (must be done after evaporation correction)
		if (resultsOptions.resultType == EnumResults.TOPLEVEL_LR) {
			computeEvaporationCorrection(exp);
			computeLRMeasures(exp, resultsOptions.lrPIThreshold);
		}
	}

//	private static boolean isGulpBased(EnumResults resultType) {
//		if (resultType == null)
//			return false;
//		switch (resultType) {
//		case SUMGULPS:
//		case SUMGULPS_LR:
//		case NBGULPS:
//		case AMPLITUDEGULPS:
//		case TTOGULP:
//		case TTOGULP_LR:
//		case AUTOCORREL:
//		case AUTOCORREL_LR:
//		case CROSSCORREL:
//		case CROSSCORREL_LR:
//			return true;
//		default:
//			return false;
//		}
//	}

	public void computeEvaporationCorrection(Experiment exp) {
		CagesCapillariesComputation computation = new CagesCapillariesComputation(this);
		computation.computeEvaporationCorrection(exp);
	}

	/**
	 * Computes L+R measures (SUM and PI) for capillaries within each cage.
	 * Delegates to CageCapillariesComputation for each cage. Stores computation
	 * instances in a transient map for later access.
	 * 
	 * @param exp       The experiment
	 * @param threshold Minimum SUM value required to compute PI
	 */
	public void computeLRMeasures(Experiment exp, double threshold) {
		if (exp == null)
			return;

		exp.dispatchCapillariesToCages();

		// Clear existing computations
		if (cageComputations != null) {
			cageComputations.clear();
		} else {
			cageComputations = new HashMap<>();
		}

		Capillaries allCapillaries = exp.getCapillaries();
		if (allCapillaries == null)
			return;

		for (Cage cage : cagesList) {
			CageCapillariesComputation cageComputation = new CageCapillariesComputation(cage);
			cageComputation.computeLRMeasures(allCapillaries, threshold);
			// Store for later access
			cageComputations.put(cage.getCageID(), cageComputation);
		}
	}

	/**
	 * Gets the CageCapillariesComputation for a specific cage ID. Returns null if
	 * computation hasn't been performed yet.
	 * 
	 * @param cageID The cage ID
	 * @return The CageCapillariesComputation instance, or null if not computed
	 */
	public CageCapillariesComputation getCageComputation(int cageID) {
		return cageComputations != null ? cageComputations.get(cageID) : null;
	}

	/**
	 * Clears all computed measures from capillaries in all cages.
	 * 
	 * @param exp The experiment containing all capillaries
	 */
	public void clearComputedMeasures(Experiment exp) {
		if (exp == null)
			return;

		CagesCapillariesComputation computation = new CagesCapillariesComputation(this);
		computation.clearComputedMeasures(exp);

		// Clear cage computations map
		Capillaries allCapillaries = exp.getCapillaries();
		if (cageComputations != null) {
			for (CageCapillariesComputation cageComp : cageComputations.values()) {
				cageComp.clearComputedMeasures(allCapillaries);
			}
			cageComputations.clear();
		}
	}

	public void checkAndCorrectCagePositions() {
		if (cagesList.size() == 0)
			return;

		boolean allInvalid = true;
		for (Cage cage : cagesList) {
			if (cage.getProperties().getArrayColumn() != -1 || cage.getProperties().getArrayRow() != -1) {
				allInvalid = false;
				break;
			}
		}

		if (allInvalid) {
			Collections.sort(cagesList, new java.util.Comparator<Cage>() {
				@Override
				public int compare(Cage c1, Cage c2) {
					return Integer.compare(c1.getCageID(), c2.getCageID());
				}
			});

			if (cagesList.size() == 10)
				nCagesAlongX = 5;

			if (nCagesAlongX > 0) {
				for (int i = 0; i < cagesList.size(); i++) {
					Cage cage = cagesList.get(i);
					int col = i % nCagesAlongX;
					int row = i / nCagesAlongX;

					cage.getProperties().setArrayColumn(col);
					cage.getProperties().setArrayRow(row);
				}
				nCagesAlongY = (cagesList.size() + nCagesAlongX - 1) / nCagesAlongX;
			}
		}
	}
}
