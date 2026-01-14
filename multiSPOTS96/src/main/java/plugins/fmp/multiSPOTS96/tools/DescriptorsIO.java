package plugins.fmp.multiSPOTS96.tools;

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
import plugins.fmp.multiSPOTS96.experiment.Experiment;
import plugins.fmp.multiSPOTS96.experiment.ExperimentProperties;
import plugins.fmp.multiSPOTS96.experiment.cages.Cage;
import plugins.fmp.multiSPOTS96.experiment.spots.Spot;
import plugins.fmp.multiSPOTS96.tools.toExcel.EnumXLSColumnHeader;

public class DescriptorsIO {

	private static final String FILE_NAME = "MS96_descriptors.xml";
	private static final String ROOT = "MS96_DESCRIPTORS";
	private static final String VERSION_ATTR = "version";
	private static final String VERSION = "1.0";
	private static final String DICTS = "DICTS";
	private static final String DICT = "DICT";
	private static final String NAME = "name";
	private static final String VAL = "VAL";

	public static String getDescriptorsFullName(String resultsDirectory) {
		return resultsDirectory + File.separator + FILE_NAME;
	}

	public static Map<EnumXLSColumnHeader, List<String>> readDescriptors(String resultsDirectory) {
		String path = getDescriptorsFullName(resultsDirectory);
		Document doc = XMLUtil.loadDocument(path);
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

			String path = getDescriptorsFullName(resultsDirectory);
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
			addIfNotEmpty(dicts.get(EnumXLSColumnHeader.EXP_EXPT), p.getExperimentField(EnumXLSColumnHeader.EXP_EXPT));
			addIfNotEmpty(dicts.get(EnumXLSColumnHeader.EXP_BOXID),
					p.getExperimentField(EnumXLSColumnHeader.EXP_BOXID));
			addIfNotEmpty(dicts.get(EnumXLSColumnHeader.EXP_STIM1),
					p.getExperimentField(EnumXLSColumnHeader.EXP_STIM1));
			addIfNotEmpty(dicts.get(EnumXLSColumnHeader.EXP_CONC1),
					p.getExperimentField(EnumXLSColumnHeader.EXP_CONC1));
			addIfNotEmpty(dicts.get(EnumXLSColumnHeader.EXP_STRAIN),
					p.getExperimentField(EnumXLSColumnHeader.EXP_STRAIN));
			addIfNotEmpty(dicts.get(EnumXLSColumnHeader.EXP_SEX), p.getExperimentField(EnumXLSColumnHeader.EXP_SEX));
			addIfNotEmpty(dicts.get(EnumXLSColumnHeader.EXP_STIM2),
					p.getExperimentField(EnumXLSColumnHeader.EXP_STIM2));
			addIfNotEmpty(dicts.get(EnumXLSColumnHeader.EXP_CONC2),
					p.getExperimentField(EnumXLSColumnHeader.EXP_CONC2));
		}

		// cages/spots
		try {
			exp.load_MS96_cages();
			if (exp.cagesArray != null && exp.cagesArray.cagesList != null) {
				for (Cage cage : exp.cagesArray.cagesList) {
					addIfNotEmpty(dicts.get(EnumXLSColumnHeader.CAGE_SEX), cage.getField(EnumXLSColumnHeader.CAGE_SEX));
					addIfNotEmpty(dicts.get(EnumXLSColumnHeader.CAGE_STRAIN),
							cage.getField(EnumXLSColumnHeader.CAGE_STRAIN));
					addIfNotEmpty(dicts.get(EnumXLSColumnHeader.CAGE_AGE), cage.getField(EnumXLSColumnHeader.CAGE_AGE));
					if (cage.spotsArray != null && cage.spotsArray.getSpotsList() != null) {
						for (Spot spot : cage.spotsArray.getSpotsList()) {
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

		return writeDescriptors(exp.getResultsDirectory(), dicts);
	}

	private static void addIfNotEmpty(Set<String> set, String value) {
		if (set == null)
			return;
		if (value != null && !value.isEmpty())
			set.add(value);
	}
}
