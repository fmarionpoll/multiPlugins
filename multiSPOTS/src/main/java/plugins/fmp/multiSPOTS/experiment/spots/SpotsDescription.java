package plugins.fmp.multiSPOTS.experiment.spots;

import java.util.Arrays;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import icy.util.XMLUtil;
import plugins.fmp.multiSPOTS.experiment.ExperimentDescriptors;

public class SpotsDescription {
	public int version = 1;

	public double volume = 5.;
	public int pixels = 5;
	public String sourceName = null;

//	public String old_boxID = new String("..");
//	public String old_experiment = new String("..");
//	public String old_comment1 = new String("..");
//	public String old_comment2 = new String("..");
//	public String old_strain = new String("..");
//	public String old_sex = new String("..");
//	public String old_cond1 = new String("..");
//	public String old_cond2 = new String("..");

	public ExperimentDescriptors expDesc = new ExperimentDescriptors();

	public int grouping = 2;
	public String stimulusR = new String("..");
	public String concentrationR = new String("..");
	public String stimulusL = new String("..");
	public String concentrationL = new String("..");

	private final static String ID_SPOTTRACK = "spotTrack";
	private final static String ID_PARAMETERS = "Parameters";
	private final static String ID_FILE = "file";
	private final static String ID_ID = "ID";
	private final static String ID_DESCGROUPING = "Grouping";
	private final static String ID_DESCN = "n";
	private final static String ID_DESCCAPVOLUME = "capillaryVolume";
	private final static String ID_DESCVOLUMEUL = "volume_ul";
	private final static String ID_DESCCAPILLARYPIX = "capillaryPixels";
	private final static String ID_DESCNPIXELS = "npixels";

	private final static String ID_LRSTIMULUS = "LRstimulus";
	private final static String ID_STIMR = "stimR";
	private final static String ID_CONCR = "concR";
	private final static String ID_STIML = "stimL";
	private final static String ID_CONCL = "concL";
	private final static String ID_EXPERIMENT = "Experiment";
	private final static String ID_BOXID = "boxID";
	private final static String ID_EXPT = "expt";
	private final static String ID_COMMENT1 = "comment";
	private final static String ID_COMMENT2 = "comment2";
	private final static String ID_STRAIN = "strain";
	private final static String ID_SEX = "sex";
	private final static String ID_COND1 = "cond1";
	private final static String ID_COND2 = "cond2";

	public void copy(SpotsDescription desc) {
		volume = desc.volume;
		pixels = desc.pixels;
		grouping = desc.grouping;
		stimulusR = desc.stimulusR;
		stimulusL = desc.stimulusL;
		concentrationR = desc.concentrationR;
		concentrationL = desc.concentrationL;
	}

	public boolean isChanged(SpotsDescription desc) {
		boolean flag = false;
		flag |= (volume != desc.volume);
		flag |= (pixels != desc.pixels);
		flag |= (grouping != desc.grouping);
		flag |= (stimulusR != null && !stimulusR.equals(desc.stimulusR));
		flag |= (concentrationR != null && !concentrationR.equals(desc.concentrationR));
		flag |= (stimulusL != null && !stimulusL.equals(desc.stimulusL));
		flag |= (concentrationL != null && !concentrationL.equals(desc.concentrationL));
		return flag;
	}

	public boolean xmlSaveSpotsDescription(Document doc) {
		Node node = XMLUtil.addElement(XMLUtil.getRootElement(doc), ID_SPOTTRACK);
		if (node == null)
			return false;
		XMLUtil.setElementIntValue(node, "version", 2);

		Element xmlElement = XMLUtil.addElement(node, ID_PARAMETERS);

		XMLUtil.addElement(xmlElement, ID_FILE, sourceName);
		Element xmlVal = XMLUtil.addElement(xmlElement, "capillaries");
		XMLUtil.setElementIntValue(xmlVal, ID_DESCGROUPING, grouping);
		XMLUtil.setElementDoubleValue(xmlVal, ID_DESCVOLUMEUL, volume);
		XMLUtil.setElementIntValue(xmlVal, ID_DESCNPIXELS, pixels);

		xmlVal = XMLUtil.addElement(xmlElement, ID_EXPERIMENT);
		expDesc.saveXML_Descriptors(xmlVal);
		return true;
	}

	public boolean xmlLoadSpotsDescription(Document doc) {
		boolean flag = false;
		Node node = XMLUtil.getElement(XMLUtil.getRootElement(doc), ID_SPOTTRACK);
		if (node == null)
			return flag;
		version = XMLUtil.getElementIntValue(node, "version", 0);
		switch (version) {
		case 0:
			flag = xmlLoadSpotsDescriptionv0(node);
			break;
		case 1:
		default:
			flag = xmlLoadCSpotsDescriptionv1(node);
			break;
		}
		return flag;
	}

	private boolean xmlLoadSpotsDescriptionv0(Node node) {
		Element xmlElement = XMLUtil.getElement(node, ID_PARAMETERS);
		if (xmlElement == null)
			return false;

		Element xmlVal = XMLUtil.getElement(xmlElement, ID_FILE);
		sourceName = XMLUtil.getAttributeValue(xmlVal, ID_ID, null);

		xmlVal = XMLUtil.getElement(xmlElement, ID_DESCGROUPING);
		grouping = XMLUtil.getAttributeIntValue(xmlVal, ID_DESCN, 2);

		xmlVal = XMLUtil.getElement(xmlElement, ID_DESCCAPVOLUME);
		volume = XMLUtil.getAttributeDoubleValue(xmlVal, ID_DESCVOLUMEUL, Double.NaN);

		xmlVal = XMLUtil.getElement(xmlElement, ID_DESCCAPILLARYPIX);
		pixels = (int) XMLUtil.getAttributeDoubleValue(xmlVal, ID_DESCNPIXELS, Double.NaN);

		xmlVal = XMLUtil.getElement(xmlElement, ID_LRSTIMULUS);
		if (xmlVal != null) {
			stimulusR = XMLUtil.getAttributeValue(xmlVal, ID_STIMR, ID_STIMR);
			concentrationR = XMLUtil.getAttributeValue(xmlVal, ID_CONCR, ID_CONCR);
			stimulusL = XMLUtil.getAttributeValue(xmlVal, ID_STIML, ID_STIML);
			concentrationL = XMLUtil.getAttributeValue(xmlVal, ID_CONCL, ID_CONCL);
		}

		expDesc.loadXML_Descriptors(node);
		return true;
	}

	private boolean xmlLoadCSpotsDescriptionv1(Node node) {
		Element xmlElement = XMLUtil.getElement(node, ID_PARAMETERS);
		if (xmlElement == null)
			return false;

		sourceName = XMLUtil.getElementValue(xmlElement, ID_FILE, null);
		Element xmlVal = XMLUtil.getElement(xmlElement, "capillaries");
		if (xmlVal != null) {
			grouping = XMLUtil.getElementIntValue(xmlVal, ID_DESCGROUPING, 2);
			volume = XMLUtil.getElementDoubleValue(xmlVal, ID_DESCVOLUMEUL, Double.NaN);
			pixels = XMLUtil.getElementIntValue(xmlVal, ID_DESCNPIXELS, 5);
		}

		xmlVal = XMLUtil.getElement(xmlElement, ID_LRSTIMULUS);
		if (xmlVal != null) {
			stimulusR = XMLUtil.getElementValue(xmlVal, ID_STIMR, ID_STIMR);
			concentrationR = XMLUtil.getElementValue(xmlVal, ID_CONCR, ID_CONCR);
			stimulusL = XMLUtil.getElementValue(xmlVal, ID_STIML, ID_STIML);
			concentrationL = XMLUtil.getElementValue(xmlVal, ID_CONCL, ID_CONCL);
		}

		expDesc.saveXML_Descriptors(node);

		return true;
	}

	// --------------------------------------

	public String csvExportSectionHeader(String csvSep) {
		StringBuffer sbf = new StringBuffer();
		sbf.append("#" + csvSep + "DESCRIPTION" + csvSep + "multiSPOTS data\n");
		List<String> row2 = Arrays.asList(ID_DESCGROUPING, ID_DESCVOLUMEUL, ID_DESCNPIXELS, ID_STIMR, ID_CONCR,
				ID_STIML, ID_CONCL, ID_BOXID, ID_EXPT, ID_COMMENT1, ID_COMMENT2, ID_STRAIN, ID_SEX, ID_COND1, ID_COND2);
		sbf.append(String.join(csvSep, row2));
		sbf.append("\n");
		return sbf.toString();
	}

	public String csvExportExperimentDescriptors(String csvSep) {
		StringBuffer sbf = new StringBuffer();
		List<String> row3 = Arrays.asList(Integer.toString(grouping), Double.toString(volume), Integer.toString(pixels),
				stimulusR, concentrationR.replace(",", "."), stimulusL, concentrationL.replace(",", "."),
				expDesc.field_boxID, expDesc.field_experiment, expDesc.field_comment1, expDesc.field_comment2,
				expDesc.field_strain, expDesc.field_sex, expDesc.field_cond1, expDesc.field_cond2);
		sbf.append(String.join(csvSep, row3));
		sbf.append("\n");
		return sbf.toString();
	}

	public void csvImportSpotsDescriptionData(String[] data) {
		int i = 0;
		grouping = Integer.valueOf(data[i]);
		i++;
		volume = Double.valueOf(data[i]);
		i++;
		pixels = Integer.valueOf(data[i]);
		i++;
		stimulusR = data[i];
		i++;
		concentrationR = data[i];
		i++;
		stimulusL = data[i];
		i++;
		concentrationL = data[i];
		i++;
		expDesc.field_boxID = data[i];
		i++;
		expDesc.field_experiment = data[i];
		i++;
		expDesc.field_comment1 = data[i];
		i++;
		expDesc.field_comment2 = data[i];
		i++;
		expDesc.field_strain = data[i];
		i++;
		expDesc.field_sex = data[i];
		int nitems = data.length;
		if (i < nitems)
			expDesc.field_cond1 = data[i];
		i++;
		if (i < nitems)
			expDesc.field_cond2 = data[i];
	}

}
