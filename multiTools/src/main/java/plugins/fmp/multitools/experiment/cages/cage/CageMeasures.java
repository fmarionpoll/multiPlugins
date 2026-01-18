package plugins.fmp.multitools.experiment.cages.cage;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import icy.util.XMLUtil;
import plugins.fmp.multitools.experiment.capillaries.capillary.CapillaryMeasure;

/**
 * Stores cage-level measures (SUM, PI, etc.).
 */
public class CageMeasures {

	public CapillaryMeasure sum = new CapillaryMeasure("SUM");
	public CapillaryMeasure pi = new CapillaryMeasure("PI");
	// Add other cage-level measures here if needed (e.g. sumClean)

	private static final String ID_CAGEMEASURES = "CageMeasures";
	private static final String ID_SUM = "SUM";
	private static final String ID_PI = "PI";

	public CageMeasures() {
	}

	public void clear() {
		if (sum != null)
			sum.clear();
		if (pi != null)
			pi.clear();
	}

	public boolean loadFromXml(Node node) {
		Element xmlVal = XMLUtil.getElement(node, ID_CAGEMEASURES);
		if (xmlVal == null)
			return false;

		boolean result = true;
		// Load SUM
		// Note: loadCapillaryLimitFromXML uses a header prefix.
		// For cage measures, we might not have a kymo-specific header prefix
		// or it might be passed in.
		// The CapillaryMeasure logic expects "header + name".
		// If we use empty header, the XML tag looked for is "name".

		result &= sum.loadCapillaryLimitFromXML(xmlVal, ID_SUM, "") > 0;
		result &= pi.loadCapillaryLimitFromXML(xmlVal, ID_PI, "") > 0;

		return result;
	}

	public boolean saveToXml(Node node) {
//		Element xmlVal = XMLUtil.addElement(node, ID_CAGEMEASURES);
		boolean result = true;
//		result &= sum.saveCapillaryLimit2XML(xmlVal, ID_SUM) > 0;
//		result &= pi.saveCapillaryLimit2XML(xmlVal, ID_PI) > 0;

		return result;
	}
}
