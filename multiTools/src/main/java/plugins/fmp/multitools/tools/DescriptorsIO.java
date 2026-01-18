package plugins.fmp.multitools.tools;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import icy.util.XMLUtil;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ExperimentProperties;
import plugins.fmp.multitools.experiment.cages.cage.Cage;
import plugins.fmp.multitools.experiment.capillaries.capillary.Capillary;
import plugins.fmp.multitools.experiment.spots.spot.Spot;
import plugins.fmp.multitools.tools.toExcel.enums.EnumXLSColumnHeader;

public class DescriptorsIO {

	// New v2 format filename
	private static final String V2_FILE_NAME = "v2_Descriptors.xml";
	// Legacy filename (for fallback)
	private static final String FILE_NAME = "MS96_descriptors.xml";
	private static final String ROOT = "MS96_DESCRIPTORS";
	private static final String VERSION_ATTR = "version";
	private static final String VERSION = "1.0";
	private static final String DICTS = "DICTS";
	private static final String DICT = "DICT";
	private static final String NAME = "name";
	private static final String VAL = "VAL";

	public static String getDescriptorsFullName(String resultsDirectory) {
		// Try v2_ format first
		String v2Path = resultsDirectory + File.separator + V2_FILE_NAME;
		File v2File = new File(v2Path);
		if (v2File.exists()) {
			return v2Path;
		}
		// Fallback to legacy format
		return resultsDirectory + File.separator + FILE_NAME;
	}

	public static Map<EnumXLSColumnHeader, List<String>> readDescriptors(String resultsDirectory) {
		// Priority 1: Try v2_ format
		String v2Path = resultsDirectory + File.separator + V2_FILE_NAME;
		Document doc = XMLUtil.loadDocument(v2Path);
		
		// Priority 2: Fallback to legacy format
		if (doc == null) {
			String legacyPath = resultsDirectory + File.separator + FILE_NAME;
			doc = XMLUtil.loadDocument(legacyPath);
		}
		
		if (doc == null)
			return null;

		Node root = XMLUtil.getRootElement(doc);
		if (root == null || !ROOT.equals(root.getNodeName()))
			return null;

		Node dicts = XMLUtil.getElement(root, DICTS);
		if (dicts == null)
			return null;

		Map<EnumXLSColumnHeader, List<String>> map = new HashMap<EnumXLSColumnHeader, List<String>>();

		for (EnumXLSColumnHeader field : EnumXLSColumnHeader.values()) {
			List<String> values = readDict(dicts, field.name());
			if (values != null && !values.isEmpty())
				map.put(field, values);
		}
		return map;
	}

	private static List<String> readDict(Node dictsNode, String dictName) {
		Node dict = findChildByNameAttr(dictsNode, DICT, dictName);
		if (dict == null)
			return null;
		List<String> out = new ArrayList<String>();
		List<Node> vals = XMLUtil.getChildren(dict, VAL);
		for (Node v : vals) {
			String t = XMLUtil.getElementValue(v, "", "");
			if (t != null && !t.isEmpty())
				out.add(t);
		}
		return out;
	}

	private static Node findChildByNameAttr(Node parent, String childName, String nameAttrValue) {
		List<Node> children = XMLUtil.getChildren(parent, childName);
		for (Node n : children) {
			if (n instanceof Element) {
				String attr = ((Element) n).getAttribute(NAME);
				if (nameAttrValue.equals(attr))
					return n;
			}
		}
		return null;
	}

	public static boolean writeDescriptors(String resultsDirectory, EnumMap<EnumXLSColumnHeader, Set<String>> dicts) {
		try {
			Document doc = XMLUtil.createDocument(true);
			Element root = doc.getDocumentElement();
			if (root == null) {
				root = doc.createElement(ROOT);
				doc.appendChild(root);
			}
			root.setAttribute(VERSION_ATTR, VERSION);

			Node dictsNode = XMLUtil.setElement(root, DICTS);

			for (Map.Entry<EnumXLSColumnHeader, Set<String>> e : dicts.entrySet()) {
				if (e.getValue() == null || e.getValue().isEmpty())
					continue;
				Element dictNode = doc.createElement(DICT);
				dictNode.setAttribute(NAME, e.getKey().name());
				dictsNode.appendChild(dictNode);
				for (String v : e.getValue()) {
					Element valNode = doc.createElement(VAL);
					valNode.setTextContent(v);
					dictNode.appendChild(valNode);
				}
			}

			// Always save to v2_ format
			String path = resultsDirectory + File.separator + V2_FILE_NAME;
			return XMLUtil.saveDocument(doc, path);
		} catch (Exception ex) {
			return false;
		}
	}

	public static boolean buildFromExperiment(Experiment exp) {
		if (exp == null)
			return false;

		EnumMap<EnumXLSColumnHeader, Set<String>> dicts = new EnumMap<EnumXLSColumnHeader, Set<String>>(
				EnumXLSColumnHeader.class);
		for (EnumXLSColumnHeader f : EnumXLSColumnHeader.values())
			dicts.put(f, new HashSet<String>());

		// experiment-level
		ExperimentProperties p = exp.getProperties();
		if (p != null) {
			addIfNotEmpty(dicts.get(EnumXLSColumnHeader.EXP_EXPT), p.getField(EnumXLSColumnHeader.EXP_EXPT));
			addIfNotEmpty(dicts.get(EnumXLSColumnHeader.EXP_BOXID), p.getField(EnumXLSColumnHeader.EXP_BOXID));
			addIfNotEmpty(dicts.get(EnumXLSColumnHeader.EXP_STIM1), p.getField(EnumXLSColumnHeader.EXP_STIM1));
			addIfNotEmpty(dicts.get(EnumXLSColumnHeader.EXP_CONC1), p.getField(EnumXLSColumnHeader.EXP_CONC1));
			addIfNotEmpty(dicts.get(EnumXLSColumnHeader.EXP_STRAIN), p.getField(EnumXLSColumnHeader.EXP_STRAIN));
			addIfNotEmpty(dicts.get(EnumXLSColumnHeader.EXP_SEX), p.getField(EnumXLSColumnHeader.EXP_SEX));
			addIfNotEmpty(dicts.get(EnumXLSColumnHeader.EXP_STIM2), p.getField(EnumXLSColumnHeader.EXP_STIM2));
			addIfNotEmpty(dicts.get(EnumXLSColumnHeader.EXP_CONC2), p.getField(EnumXLSColumnHeader.EXP_CONC2));
		}

		// cages/spots
		try {
			exp.load_cages_description_and_measures();
			if (exp.getCages() != null && exp.getCages().cagesList != null) {
				for (Cage cage : exp.getCages().cagesList) {
					addIfNotEmpty(dicts.get(EnumXLSColumnHeader.CAGE_SEX), cage.getField(EnumXLSColumnHeader.CAGE_SEX));
					addIfNotEmpty(dicts.get(EnumXLSColumnHeader.CAGE_STRAIN),
							cage.getField(EnumXLSColumnHeader.CAGE_STRAIN));
					addIfNotEmpty(dicts.get(EnumXLSColumnHeader.CAGE_AGE), cage.getField(EnumXLSColumnHeader.CAGE_AGE));
					List<Spot> spots = cage.getSpotList(exp.getSpots());
					if (spots != null && !spots.isEmpty()) {
						for (Spot spot : spots) {
							addIfNotEmpty(dicts.get(EnumXLSColumnHeader.SPOT_STIM),
									spot.getField(EnumXLSColumnHeader.SPOT_STIM));
							addIfNotEmpty(dicts.get(EnumXLSColumnHeader.SPOT_CONC),
									spot.getField(EnumXLSColumnHeader.SPOT_CONC));
							addIfNotEmpty(dicts.get(EnumXLSColumnHeader.SPOT_VOLUME),
									spot.getField(EnumXLSColumnHeader.SPOT_VOLUME));
						}
					}
				}
			}
		} catch (Exception e) {
			// ignore
		}

		// capillaries
		try {
			exp.loadMCCapillaries_Only();
			if (exp.getCapillaries() != null && exp.getCapillaries().getList() != null) {
				for (Capillary cap : exp.getCapillaries().getList()) {
					addIfNotEmpty(dicts.get(EnumXLSColumnHeader.CAP_STIM), cap.getField(EnumXLSColumnHeader.CAP_STIM));
					addIfNotEmpty(dicts.get(EnumXLSColumnHeader.CAP_CONC), cap.getField(EnumXLSColumnHeader.CAP_CONC));
					addIfNotEmpty(dicts.get(EnumXLSColumnHeader.CAP_VOLUME),
							cap.getField(EnumXLSColumnHeader.CAP_VOLUME));
				}
			}
		} catch (Exception e) {
			// ignore
		}

		return writeDescriptors(exp.getResultsDirectory(), dicts);
	}

	private static void addIfNotEmpty(Set<String> set, String value) {
		if (set == null)
			return;
		if (value != null && !value.isEmpty())
			set.add(value);
	}
}
