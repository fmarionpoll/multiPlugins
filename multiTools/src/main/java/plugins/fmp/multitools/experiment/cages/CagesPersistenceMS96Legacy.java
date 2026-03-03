package plugins.fmp.multitools.experiment.cages;

import java.io.File;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import icy.roi.ROI;
import icy.roi.ROI2D;
import icy.type.geom.Polygon2D;
import icy.util.XMLUtil;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.ids.SpotID;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotPersistence;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.ROI2D.ROI2DUtilities;
import plugins.kernel.roi.roi2d.ROI2DPolygon;

/**
 * Persistence for MultiSPOTS96 legacy format (MS96_cages.xml). Handles loading
 * cages, cage ROIs, and spots from the MS96 XML structure.
 */
public class CagesPersistenceMS96Legacy {

	private static final String ID_MS96_CAGES_XML = "MS96_cages.xml";
	private static final String ID_CAGES = "Cages";
	private static final String ID_NCAGES = "n_cages";
	private static final String ID_LISTOFSPOTS = "List_of_spots";
	private static final String ID_NSPOTS = "N_spots";
	private static final String ID_SPOT_ = "spot_";
	private static final String ID_CAGELIMITS = "CageLimits";
	private static final String ID_CAGE_LIMITS = "Cage_Limits";

	/**
	 * Parses MS96 CageLimits format where points are stored as
	 * &lt;points&gt;&lt;point&gt;&lt;pos_x&gt;...&lt;/pos_x&gt;&lt;pos_y&gt;...&lt;/pos_y&gt;&lt;/point&gt;...
	 */
	public static ROI2D parseCageLimitsFromPointsFormat(Element cageLimitsEl) {
		Element pointsEl = XMLUtil.getElement(cageLimitsEl, "points");
		if (pointsEl == null)
			return null;
		NodeList pointNodes = pointsEl.getElementsByTagName("point");
		int n = pointNodes.getLength();
		if (n < 3)
			return null;
		double[] x = new double[n];
		double[] y = new double[n];
		for (int i = 0; i < n; i++) {
			Node pt = pointNodes.item(i);
			if (pt.getNodeType() == Node.ELEMENT_NODE) {
				Element ptEl = (Element) pt;
				x[i] = XMLUtil.getElementDoubleValue(ptEl, "pos_x", 0);
				y[i] = XMLUtil.getElementDoubleValue(ptEl, "pos_y", 0);
			}
		}
		Polygon2D polygon = new Polygon2D(x, y, n);
		ROI2DPolygon roi = new ROI2DPolygon(polygon);
		String name = XMLUtil.getElementValue(cageLimitsEl, "name", null);
		if (name != null && !name.isEmpty())
			roi.setName(name);
		return roi;
	}

	/**
	 * Loads cage descriptions and measures from MS96_cages.xml only.
	 */
	public static boolean loadFromMS96CagesXml(Cages cages, String resultsDirectory) {
		if (resultsDirectory == null)
			return false;
		String path = resultsDirectory + File.separator + ID_MS96_CAGES_XML;
		File file = new File(path);
		boolean usedFallback = false;
		if (!file.isFile()) {
			File parent = new File(resultsDirectory).getParentFile();
			if (parent != null) {
				path = parent.getAbsolutePath() + File.separator + ID_MS96_CAGES_XML;
				file = new File(path);
				usedFallback = file.isFile();
			}
		}
		if (!file.isFile())
			return false;
		if (usedFallback)
			Logger.info("CagesPersistenceMS96Legacy: Found " + ID_MS96_CAGES_XML + " in experiment root");
		boolean loaded = CagesPersistenceLegacy.xmlReadCagesFromMS96CagesXml(cages, path);
		if (loaded)
			Logger.info("CagesPersistenceMS96Legacy:loadFromMS96CagesXml() Loaded from " + ID_MS96_CAGES_XML);
		return loaded;
	}

	/**
	 * Loads spot ROIs (color, size, shape) from MS96_cages.xml.
	 */
	public static boolean loadSpotsFromMS96CagesXml(Spots spots, String resultsDirectory) {
		if (spots == null || resultsDirectory == null)
			return false;
		String path = resultsDirectory + File.separator + ID_MS96_CAGES_XML;
		File file = new File(path);
		if (!file.isFile()) {
			File parent = new File(resultsDirectory).getParentFile();
			if (parent != null) {
				path = parent.getAbsolutePath() + File.separator + ID_MS96_CAGES_XML;
				file = new File(path);
			}
		}
		if (!file.isFile())
			return false;
		path = file.getAbsolutePath();
		try {
			Document doc = XMLUtil.loadDocument(path);
			if (doc == null)
				return false;
			Node rootNode = XMLUtil.getRootElement(doc);
			Element xmlCages = XMLUtil.getElement(rootNode, ID_CAGES);
			if (xmlCages == null)
				return false;
			int ncages = XMLUtil.getAttributeIntValue(xmlCages, ID_NCAGES, 0);
			int totalLoaded = 0;
			for (int cageIndex = 0; cageIndex < ncages; cageIndex++) {
				Element cageElement = XMLUtil.getElement(xmlCages, "Cage" + cageIndex);
				if (cageElement == null)
					continue;
				Node listNode = XMLUtil.getElement(cageElement, ID_LISTOFSPOTS);
				if (listNode == null)
					continue;
				int nspots = XMLUtil.getElementIntValue(listNode, ID_NSPOTS, 0);
				for (int i = 0; i < nspots; i++) {
					Node spotNode = XMLUtil.getElement(cageElement, ID_SPOT_ + i);
					if (spotNode == null)
						continue;
					Spot spot = new Spot();
					if (SpotPersistence.xmlLoadSpot(spotNode, spot)) {
						int uniqueID = spots.getNextUniqueSpotID();
						spot.setSpotUniqueID(new SpotID(uniqueID));
						spots.getSpotList().add(spot);
						totalLoaded++;
					}
				}
			}
			if (totalLoaded > 0)
				Logger.info("CagesPersistenceMS96Legacy:loadSpotsFromMS96CagesXml() Loaded " + totalLoaded
						+ " spots from " + ID_MS96_CAGES_XML);
			return totalLoaded > 0;
		} catch (Exception e) {
			Logger.error("CagesPersistenceMS96Legacy:loadSpotsFromMS96CagesXml() Error: " + e.getMessage(), e);
			return false;
		}
	}

	/**
	 * Loads cage ROIs from MS96_cages.xml and assigns them to cages.
	 */
	public static boolean loadCageROIsFromMS96CagesXml(Cages cages, String resultsDirectory) {
		if (cages == null || resultsDirectory == null || cages.cagesList.isEmpty())
			return false;
		String path = resultsDirectory + File.separator + ID_MS96_CAGES_XML;
		File file = new File(path);
		if (!file.isFile()) {
			File parent = new File(resultsDirectory).getParentFile();
			if (parent != null) {
				path = parent.getAbsolutePath() + File.separator + ID_MS96_CAGES_XML;
				file = new File(path);
			}
		}
		if (!file.isFile())
			return false;
		try {
			Document doc = XMLUtil.loadDocument(file.getAbsolutePath());
			if (doc == null)
				return false;
			Node rootNode = XMLUtil.getRootElement(doc);
			Element xmlCages = XMLUtil.getElement(rootNode, ID_CAGES);
			if (xmlCages == null)
				return false;
			int ncages = XMLUtil.getAttributeIntValue(xmlCages, ID_NCAGES, 0);
			int loaded = 0;
			String[] roiTags = { ID_CAGELIMITS, ID_CAGE_LIMITS };
			for (int i = 0; i < ncages && i < cages.cagesList.size(); i++) {
				Element cageEl = XMLUtil.getElement(xmlCages, "Cage" + i);
				if (cageEl == null)
					cageEl = XMLUtil.getElement(xmlCages, "cage" + i);
				if (cageEl == null)
					continue;
				Cage cage = cages.cagesList.get(i);
				for (String tag : roiTags) {
					Element roiEl = XMLUtil.getElement(cageEl, tag);
					if (roiEl == null)
						continue;
					ROI2D roi = ROI2DUtilities.loadFromXML_ROI(roiEl);
					if (roi == null)
						roi = (ROI2D) ROI.createFromXML(roiEl);
					if (roi == null && roiEl.hasChildNodes()) {
						Node child = roiEl.getFirstChild();
						while (child != null) {
							if (child.getNodeType() == Node.ELEMENT_NODE) {
								roi = (ROI2D) ROI.createFromXML(child);
								if (roi != null)
									break;
							}
							child = child.getNextSibling();
						}
					}
					if (roi == null)
						roi = parseCageLimitsFromPointsFormat(roiEl);
					if (roi != null && !roi.getBounds().isEmpty()) {
						roi.setSelected(false);
						if (cage.getProperties().getColor() != null)
							roi.setColor(cage.getProperties().getColor());
						CagesPersistenceLegacy.ensureCageRoiNameForDisplay(roi, i);
						cage.setCageRoi(roi);
						loaded++;
						break;
					}
				}
			}
			if (loaded > 0)
				Logger.info("CagesPersistenceMS96Legacy:loadCageROIsFromMS96CagesXml() Loaded " + loaded + " cage ROIs");
			return loaded > 0;
		} catch (Exception e) {
			Logger.error("CagesPersistenceMS96Legacy:loadCageROIsFromMS96CagesXml() Error: " + e.getMessage(), e);
			return false;
		}
	}

	public static String getMs96CagesXmlFilename() {
		return ID_MS96_CAGES_XML;
	}
}
