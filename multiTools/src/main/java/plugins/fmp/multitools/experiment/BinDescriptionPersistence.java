package plugins.fmp.multitools.experiment;

import java.io.File;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import icy.util.XMLUtil;

public class BinDescriptionPersistence {

	private final static String ID_VERSION = "version";
	private final static String ID_VERSIONNUM = "2.0.0";
	private final static String ID_FIRSTKYMOCOLMS = "firstKymoColMs";
	private final static String ID_LASTKYMOCOLMS = "lastKymoColMs";
	private final static String ID_BINKYMOCOLMS = "binKymoColMs";
	private final static String ID_NOMINALINTERVALSEC = "nominalIntervalSec";
	private final static String ID_BINDESCRIPTION = "binDescription";

	public final static String ID_V2_BINDESCRIPTION_XML = "BinDescription.xml";

	// ========================================================================
	// Public API methods
	// ========================================================================

	/**
	 * Loads bin description from XML file in the specified bin directory.
	 * 
	 * @param binDescription the BinDescription to populate
	 * @param binDirectory   the full path to the bin directory
	 * @return true if successful
	 */
	public boolean load(BinDescription binDescription, String binDirectory) {
		if (binDirectory == null) {
			return false;
		}
		String filename = binDirectory + File.separator + ID_V2_BINDESCRIPTION_XML;
		boolean loaded = xmlLoadBinDescription(binDescription, filename);
		if (loaded && binDescription.getNominalIntervalSec() < 0) {
			String dirName = new File(binDirectory).getName();
			if (dirName.startsWith(Experiment.BIN)) {
				try {
					int parsed = Integer.parseInt(dirName.substring(Experiment.BIN.length()));
					if (parsed > 0)
						binDescription.setNominalIntervalSec(parsed);
				} catch (NumberFormatException e) {
					// ignore
				}
			}
		}
		return loaded;
	}

	/**
	 * Saves bin description to XML file in the specified bin directory.
	 * 
	 * @param binDescription the BinDescription to save
	 * @param binDirectory   the full path to the bin directory
	 * @return true if successful
	 */
	public boolean save(BinDescription binDescription, String binDirectory) {
		if (binDirectory == null) {
			return false;
		}
		File binDirFile = new File(binDirectory);
		String binSubDirName = binDirFile.getName();
		if (binSubDirName.startsWith(Experiment.BIN)) {
			String suffix = binSubDirName.substring(Experiment.BIN.length());
			try {
				Integer.parseInt(suffix);
			} catch (NumberFormatException e) {
				return false;
			}
		}
		File dir = new File(binDirectory);
		if (!dir.exists()) {
			dir.mkdirs();
		}
		String filename = binDirectory + File.separator + ID_V2_BINDESCRIPTION_XML;
		return xmlSaveBinDescription(binDescription, filename);
	}

	// ------------------------------------------

	private boolean xmlLoadBinDescription(BinDescription binDescription, String csFileName) {
		final Document doc = XMLUtil.loadDocument(csFileName);
		if (doc == null)
			return false;
		Node node = XMLUtil.getElement(XMLUtil.getRootElement(doc), ID_BINDESCRIPTION);
		if (node == null)
			return false;

		String version = XMLUtil.getElementValue(node, ID_VERSION, ID_VERSIONNUM);
		if (!version.equals(ID_VERSIONNUM))
			return false;

		long firstKymoColMs = XMLUtil.getElementLongValue(node, ID_FIRSTKYMOCOLMS, -1);
		long lastKymoColMs = XMLUtil.getElementLongValue(node, ID_LASTKYMOCOLMS, -1);
		long binKymoColMs = XMLUtil.getElementLongValue(node, ID_BINKYMOCOLMS, -1);
		int nominalIntervalSec = XMLUtil.getElementIntValue(node, ID_NOMINALINTERVALSEC, -1);

		if (firstKymoColMs >= 0)
			binDescription.setFirstKymoColMs(firstKymoColMs);
		if (lastKymoColMs >= 0)
			binDescription.setLastKymoColMs(lastKymoColMs);
		if (binKymoColMs >= 0)
			binDescription.setBinKymoColMs(binKymoColMs);
		if (nominalIntervalSec >= 0)
			binDescription.setNominalIntervalSec(nominalIntervalSec);

		return true;
	}

	private boolean xmlSaveBinDescription(BinDescription binDescription, String csFileName) {
		final Document doc = XMLUtil.createDocument(true);
		if (doc != null) {
			Node xmlRoot = XMLUtil.getRootElement(doc, true);
			Node node = XMLUtil.setElement(xmlRoot, ID_BINDESCRIPTION);
			if (node == null)
				return false;

			XMLUtil.setElementValue(node, ID_VERSION, ID_VERSIONNUM);
			XMLUtil.setElementLongValue(node, ID_FIRSTKYMOCOLMS, binDescription.getFirstKymoColMs());
			XMLUtil.setElementLongValue(node, ID_LASTKYMOCOLMS, binDescription.getLastKymoColMs());
			XMLUtil.setElementLongValue(node, ID_BINKYMOCOLMS, binDescription.getBinKymoColMs());
			if (binDescription.getNominalIntervalSec() >= 0)
				XMLUtil.setElementIntValue(node, ID_NOMINALINTERVALSEC, binDescription.getNominalIntervalSec());

			XMLUtil.saveDocument(doc, csFileName);
			return true;
		}
		return false;
	}
}
