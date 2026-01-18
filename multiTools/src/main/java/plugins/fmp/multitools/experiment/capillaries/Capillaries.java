package plugins.fmp.multitools.experiment.capillaries;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import icy.roi.ROI2D;
import icy.sequence.Sequence;
import icy.type.geom.Polygon2D;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.tools.Comparators;
import plugins.fmp.multitools.tools.ROI2D.AlongT;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.kernel.roi.roi2d.ROI2DShape;

public class Capillaries {

	private CapillariesDescription capillariesDescription = new CapillariesDescription();
	private CapillariesDescription desc_old = new CapillariesDescription();
	private List<Capillary> capillariesList = new ArrayList<Capillary>();
	private KymoIntervals capillariesListTimeIntervals = null;
	private CapillariesPersistence persistence = new CapillariesPersistence();

	public CapillariesDescription getCapillariesDescription() {
		return capillariesDescription;
	}

	public void setCapillariesDescription(CapillariesDescription capillariesDescription) {
		this.capillariesDescription = capillariesDescription;
	}

	public CapillariesDescription getDesc_old() {
		return desc_old;
	}

	public void setDesc_old(CapillariesDescription desc_old) {
		this.desc_old = desc_old;
	}

	public List<Capillary> getList() {
		return capillariesList;
	}

	public void setCapillariesList(List<Capillary> capillariesList) {
		this.capillariesList = capillariesList;
	}

	public boolean addCapillary(Capillary capillary) {
		return this.capillariesList.add(capillary);
	}

	// ---------------------------------

	public CapillariesPersistence getPersistence() {
		return persistence;
	}

	// ---------------------------------

	public String getXMLNameToAppend() {
		return persistence.getXMLNameToAppend();
	}

	public boolean xmlSaveCapillaries_Descriptors(String csFileName) {
		return persistence.xmlSaveCapillaries_Descriptors(this, csFileName);
	}

	// ---------------------------------

	public void copy(Capillaries cap) {
		capillariesDescription.copy(cap.capillariesDescription);
		getList().clear();
		for (Capillary ccap : cap.getList()) {
			if (ccap == null || ccap.getRoi() == null)
				continue;
			Capillary capi = new Capillary();
			capi.copy(ccap);
			getList().add(capi);
		}
	}

	public boolean isPresent(Capillary capNew) {
		boolean flag = false;
		for (Capillary cap : getList()) {
			if (cap.getKymographName().contentEquals(capNew.getKymographName())) {
				flag = true;
				break;
			}
		}
		return flag;
	}

	public void mergeLists(Capillaries caplist) {
		for (Capillary capm : caplist.getList()) {
			if (!isPresent(capm))
				getList().add(capm);
		}
	}

	public void adjustToImageWidth(int imageWidth) {
		for (Capillary cap : getList())
			cap.adjustToImageWidth(imageWidth);
	}

	public void cropToImageWidth(int imageWidth) {
		for (Capillary cap : getList())
			cap.cropToImageWidth(imageWidth);
	}

	public void transferDescriptionToCapillaries() {
		for (Capillary cap : getList()) {
			transferCapGroupCageIDToCapillary(cap);
			cap.setVolumeAndPixels(capillariesDescription.getVolume(), capillariesDescription.getPixels());
		}
	}

	public void clearAllMeasures(int first, int last) {
		for (Capillary cap : getList()) {
			int i = cap.getKymographIndex();
			if (first < 0 || last < 0 || (i >= first && i <= last))
				cap.clearAllMeasures();
		}
	}

	private void transferCapGroupCageIDToCapillary(Capillary cap) {
		if (capillariesDescription.getGrouping() != 2)
			return;

		String name = cap.getRoiName();
		String letter = name.substring(name.length() - 1);
		cap.getProperties().setSide(letter);
		if (letter.equals("R")) {
			String nameL = name.substring(0, name.length() - 1) + "L";
			Capillary cap0 = getCapillaryFromRoiName(nameL);
			if (cap0 != null) {
				cap.setCageID(cap0.getCageID());
			}
		}
	}

	public Capillary getCapillaryFromRoiName(String name) {
		Capillary capFound = null;
		for (Capillary cap : getList()) {
			if (cap.getRoiName().equals(name)) {
				capFound = cap;
				break;
			}
		}
		return capFound;
	}

	public Capillary getCapillaryFromKymographName(String name) {
		if (name == null)
			return null;
		Capillary capFound = null;
		for (Capillary cap : getList()) {
			String capName = cap.getKymographName();
			if (capName != null && capName.equals(name)) {
				capFound = cap;
				break;
			}
		}
		return capFound;
	}

	public Capillary getCapillaryFromRoiNamePrefix(String name) {
		Capillary capFound = null;
		for (Capillary cap : getList()) {
			if (cap.getKymographPrefix().equals(name)) {
				capFound = cap;
				break;
			}
		}
		return capFound;
	}

	public Capillary getCapillaryAtT(int t) {
		Capillary capFound = null;
		for (Capillary cap : getList()) {
			if (cap.getKymographIndex() == t) {
				capFound = cap;
				break;
			}
		}
		return capFound;
	}

	public void updateCapillariesFromSequence(SequenceCamData seqCamData) {
		List<ROI2D> listROISCap = seqCamData.findROIsMatchingNamePattern("line");
		Collections.sort(listROISCap, new Comparators.ROI2D_Name());

		// Capillary-driven approach: Only update existing capillaries with ROIs from
		// sequence
		// Do NOT create new capillaries from ROIs - capillaries should come from saved
		// data
		for (Capillary cap : getList()) {
			cap.getProperties().setValid(false);
			String capName = Capillary.replace_LR_with_12(cap.getRoiName());
			Iterator<ROI2D> iterator = listROISCap.iterator();
			while (iterator.hasNext()) {
				ROI2D roi = iterator.next();
				String roiName = Capillary.replace_LR_with_12(roi.getName());
				if (roiName.equals(capName)) {
					cap.setRoi((ROI2DShape) roi);
					cap.getProperties().setValid(true);
					iterator.remove();
					break;
				}
			}
		}

		// Remove capillaries that don't have matching ROIs in the sequence
		// (This allows users to delete capillaries by removing their ROIs)
		Iterator<Capillary> iterator = getList().iterator();
		while (iterator.hasNext()) {
			Capillary cap = iterator.next();
			if (!cap.getProperties().getValid())
				iterator.remove();
		}

		// Do NOT create new capillaries from remaining ROIs - this would be
		// file-driven, not capillary-driven
		// Capillaries should only be created explicitly by the user or loaded from
		// saved data

		Collections.sort(getList());
		return;
	}

	public void transferCapillaryRoiToSequence(Sequence seq) {
		// Remove only capillary ROIs (containing "line"), preserving cages and other
		// ROIs
		List<ROI2D> allROIs = seq.getROI2Ds();
		List<ROI2D> toRemove = new ArrayList<>();
		for (ROI2D roi : allROIs) {
			if (roi.getName() != null && roi.getName().contains("line")) {
				toRemove.add(roi);
			}
		}
		for (ROI2D roi : toRemove) {
			seq.removeROI(roi);
		}
		// Add capillary ROIs to sequence
		for (Capillary cap : getList()) {
			if (cap.getRoi() != null) {
				seq.addROI(cap.getRoi());
			}
		}
	}

	public void initCapillariesWith10Cages(int nflies, boolean optionZeroFlyFirstLastCapillary) {
		int capArraySize = getList().size();
		for (int i = 0; i < capArraySize; i++) {
			Capillary cap = getList().get(i);
			cap.getProperties().setNFlies(nflies);
			if (optionZeroFlyFirstLastCapillary && (i <= 1 || i >= capArraySize - 2))
				cap.getProperties().setNFlies(0);
			cap.setCageID(i / 2);
		}
	}

	public void initCapillariesWith6Cages(int nflies) {
		int capArraySize = getList().size();
		for (int i = 0; i < capArraySize; i++) {
			Capillary cap = getList().get(i);
			cap.getProperties().setNFlies(1);
			if (i <= 1) {
				cap.getProperties().setNFlies(0);
				cap.setCageID(0);
			} else if (i >= capArraySize - 2) {
				cap.getProperties().setNFlies(0);
				cap.setCageID(5);
			} else {
				cap.getProperties().setNFlies(nflies);
				cap.setCageID(1 + (i - 2) / 4);
			}
		}
	}

	// -------------------------------------------------

	public KymoIntervals getKymoIntervalsFromCapillaries() {
		if (capillariesListTimeIntervals == null) {
			capillariesListTimeIntervals = new KymoIntervals();

			for (Capillary cap : getList()) {
				for (AlongT roiFK : cap.getROIsForKymo()) {
					Long[] interval = { roiFK.getStart(), (long) -1 };
					capillariesListTimeIntervals.addIfNew(interval);
				}
			}
		}
		return capillariesListTimeIntervals;
	}

	public int addKymoROI2DInterval(long start) {
		Long[] interval = { start, (long) -1 };
		int item = capillariesListTimeIntervals.addIfNew(interval);

		for (Capillary cap : getList()) {
			List<AlongT> listROI2DForKymo = cap.getROIsForKymo();
			ROI2D roi = cap.getRoi();
			if (item > 0)
				roi = (ROI2D) listROI2DForKymo.get(item - 1).getRoi().getCopy();
			listROI2DForKymo.add(item, new AlongT(start, roi));
		}
		return item;
	}

	public void deleteKymoROI2DInterval(long start) {
		capillariesListTimeIntervals.deleteIntervalStartingAt(start);
		for (Capillary cap : getList())
			cap.removeROI2DIntervalStartingAt(start);
	}

	public int findKymoROI2DIntervalStart(long intervalT) {
		return getKymoIntervalsFromCapillaries().findStartItem(intervalT);
	}

	public long getKymoROI2DIntervalsStartAt(int selectedItem) {
		return getKymoIntervalsFromCapillaries().get(selectedItem)[0];
	}

	public double getScalingFactorToPhysicalUnits(EnumResults resultType) {
		double scalingFactorToPhysicalUnits;
		switch (resultType) {
		case NBGULPS:
		case TTOGULP:
		case TTOGULP_LR:
		case AUTOCORREL:
		case CROSSCORREL:
		case CROSSCORREL_LR:
			scalingFactorToPhysicalUnits = 1.;
			break;
		default:
			scalingFactorToPhysicalUnits = capillariesDescription.getVolume() / capillariesDescription.getPixels();
			break;
		}
		return scalingFactorToPhysicalUnits;
	}

	public Polygon2D get2DPolygonEnclosingCapillaries() {
		Capillary cap0 = getList().get(0);

		Point2D upperLeft = (Point2D) cap0.getCapillaryROIFirstPoint().clone();
		Point2D lowerLeft = (Point2D) cap0.getCapillaryROILastPoint().clone();
		Point2D upperRight = (Point2D) upperLeft.clone();
		Point2D lowerRight = (Point2D) lowerLeft.clone();

		for (Capillary cap : getList()) {
			Point2D capFirst = (Point2D) cap.getCapillaryROIFirstPoint();
			Point2D capLast = (Point2D) cap.getCapillaryROILastPoint();

			if (capFirst.getX() < upperLeft.getX())
				upperLeft.setLocation(capFirst.getX(), upperLeft.getY());
			if (capFirst.getY() < upperLeft.getY())
				upperLeft.setLocation(upperLeft.getX(), capFirst.getY());

			if (capLast.getX() < lowerLeft.getX())
				lowerLeft.setLocation(capLast.getX(), lowerLeft.getY());
			if (capLast.getY() > lowerLeft.getY())
				lowerLeft.setLocation(lowerLeft.getX(), capLast.getY());

			if (capFirst.getX() > upperRight.getX())
				upperRight.setLocation(capFirst.getX(), upperRight.getY());
			if (capFirst.getY() < upperRight.getY())
				upperRight.setLocation(upperRight.getX(), capFirst.getY());

			if (capLast.getX() > lowerRight.getX())
				lowerRight.setLocation(capLast.getX(), lowerRight.getY());
			if (capLast.getY() > lowerRight.getY())
				lowerRight.setLocation(lowerRight.getX(), capLast.getY());
		}

		List<Point2D> listPoints = new ArrayList<Point2D>(4);
		listPoints.add(upperLeft);
		listPoints.add(lowerLeft);
		listPoints.add(lowerRight);
		listPoints.add(upperRight);
		return new Polygon2D(listPoints);
	}

	public void deleteAllCapillaries() {
		getList().clear();
	}

	public int getSelectedCapillary() {
		int selected = -1;
		if (getList().size() > 0) {
			for (Capillary cap : capillariesList) {
				ROI2D roi = cap.getRoi();
				if (roi != null && roi.isSelected()) {
					selected = cap.getKymographIndex();
					break;
				}
			}
		}
		return selected;
	}

	// -------------------------------------------------

}