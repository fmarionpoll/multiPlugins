package plugins.fmp.multiSPOTS.tools.ROI2D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.w3c.dom.Document;

import icy.roi.ROI;
import icy.roi.ROI2D;
import icy.sequence.Sequence;
import icy.util.XMLUtil;
import plugins.fmp.multiSPOTS.tools.Comparators;

public class ROIUtilities {

	public static void mergeROIsListNoDuplicate(List<ROI2D> seqList, List<ROI2D> listRois, Sequence seq) {
		if (seqList.isEmpty()) {
			for (ROI2D roi : listRois)
				if (roi != null)
					seqList.add(roi);
		}

		for (ROI2D seqRoi : seqList) {
			Iterator<ROI2D> iterator = listRois.iterator();
			while (iterator.hasNext()) {
				ROI2D roi = iterator.next();
				if (seqRoi == roi)
					iterator.remove();
				else if (seqRoi.getName().equals(roi.getName())) {
					seqRoi.copyFrom(roi);
					iterator.remove();
				}
			}
		}
	}

	public static void removeROIsMissingChar(List<ROI2D> listRois, char character) {
		Iterator<ROI2D> iterator = listRois.iterator();
		while (iterator.hasNext()) {
			ROI2D roi = iterator.next();
			if (roi.getName().indexOf(character) < 0)
				iterator.remove();
		}
	}

	public static List<ROI2D> loadROIsFromXML(Document doc) {
		List<ROI> localList = ROI.loadROIsFromXML(XMLUtil.getRootElement(doc));
		List<ROI2D> finalList = new ArrayList<ROI2D>(localList.size());
		for (ROI roi : localList)
			finalList.add((ROI2D) roi);
		return finalList;
	}

	public static List<ROI> getROIsContainingString(String string, Sequence seq) {
		List<ROI> roiList = seq.getROIs();
		Collections.sort(roiList, new Comparators.ROI_Name_Comparator());
		List<ROI> listROIsMatchingString = new ArrayList<ROI>();
		for (ROI roi : roiList) {
			if (roi.getName().contains(string))
				listROIsMatchingString.add(roi);
		}
		return listROIsMatchingString;
	}

}
