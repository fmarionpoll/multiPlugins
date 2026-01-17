package plugins.fmp.multitools.experiment;

import java.util.Arrays;
import java.util.List;

import org.w3c.dom.Node;

import icy.util.XMLUtil;
import plugins.fmp.multitools.tools.toExcel.enums.EnumXLSColumnHeader;

public class ExperimentProperties {

	public String field_boxID = new String("..");
	public String field_experiment = new String("..");
	public String field_stim1 = new String("..");
	public String field_conc1 = new String("..");
	public String field_stim2 = new String("..");
	public String field_conc2 = new String("..");
	public String field_string1 = new String("..");
	public String field_string2 = new String("..");

	// Legacy fields kept for backward compatibility (read but not written in new
	// format)
	public String field_comment1 = new String("..");
	public String field_comment2 = new String("..");

	public String field_strain = new String("..");
	public String field_sex = new String("..");

	// New field names (human-readable)
	private final static String ID_STIM1_NEW = "stim1";
	private final static String ID_CONC1_NEW = "conc1";
	private final static String ID_STIM2_NEW = "stim2";
	private final static String ID_CONC2_NEW = "conc2";
	private final static String ID_STRING1 = "string1";
	private final static String ID_STRING2 = "string2";

	// Legacy field names (for backward compatibility)
	private final static String ID_BOXID = "boxID";
	private final static String ID_EXPERIMENT = "experiment";
	private final static String ID_STIM1_OLD = "stim"; // MS96Experiment.xml format
	private final static String ID_CONC1_OLD = "conc"; // MS96Experiment.xml format
	private final static String ID_STIM2_OLD = "cond1"; // MS96Experiment.xml format
	private final static String ID_CONC2_OLD = "cond2"; // MS96Experiment.xml format
	private final static String ID_STRAIN = "strain";
	private final static String ID_SEX = "sex";

	public void saveXML_Properties(Node node) {
		XMLUtil.setElementValue(node, ID_BOXID, field_boxID);
		XMLUtil.setElementValue(node, ID_EXPERIMENT, field_experiment);

		// Save new human-readable field names
		XMLUtil.setElementValue(node, ID_STIM1_NEW, field_stim1);
		XMLUtil.setElementValue(node, ID_CONC1_NEW, field_conc1);
		XMLUtil.setElementValue(node, ID_STIM2_NEW, field_stim2);
		XMLUtil.setElementValue(node, ID_CONC2_NEW, field_conc2);
		XMLUtil.setElementValue(node, ID_STRING1, field_string1);
		XMLUtil.setElementValue(node, ID_STRING2, field_string2);

		// Save other fields
		XMLUtil.setElementValue(node, ID_STRAIN, field_strain);
		XMLUtil.setElementValue(node, ID_SEX, field_sex);

		// Note: We don't save old comment1/comment2 fields in new format
		// to avoid confusion with the new field structure
	}

	public void loadXML_Properties(Node node) {
		field_boxID = XMLUtil.getElementValue(node, ID_BOXID, "..");
		field_experiment = XMLUtil.getElementValue(node, ID_EXPERIMENT, "..");

		// Load new fields first (if present in new format)
		// Check if new format fields exist by testing if element is present
		Node stim1Node = XMLUtil.getElement(node, ID_STIM1_NEW);
		if (stim1Node != null) {
			// New format: use new field names
			field_stim1 = XMLUtil.getElementValue(node, ID_STIM1_NEW, "..");
			field_conc1 = XMLUtil.getElementValue(node, ID_CONC1_NEW, "..");
			field_stim2 = XMLUtil.getElementValue(node, ID_STIM2_NEW, "..");
			field_conc2 = XMLUtil.getElementValue(node, ID_CONC2_NEW, "..");
			field_string1 = XMLUtil.getElementValue(node, ID_STRING1, "..");
			field_string2 = XMLUtil.getElementValue(node, ID_STRING2, "..");
		} else {
			// Old format: try to load from legacy field names
			// Try MS96Experiment.xml format first (stim/conc/cond1/cond2)
			Node stimOldNode = XMLUtil.getElement(node, ID_STIM1_OLD);
			if (stimOldNode != null) {
				// MS96Experiment.xml format
				field_stim1 = XMLUtil.getElementValue(node, ID_STIM1_OLD, "..");
				field_conc1 = XMLUtil.getElementValue(node, ID_CONC1_OLD, "..");
				field_stim2 = XMLUtil.getElementValue(node, ID_STIM2_OLD, "..");
				field_conc2 = XMLUtil.getElementValue(node, ID_CONC2_OLD, "..");
			}
		}

		field_strain = XMLUtil.getElementValue(node, ID_STRAIN, "..");
		field_sex = XMLUtil.getElementValue(node, ID_SEX, "..");
	}

	public String getField(EnumXLSColumnHeader fieldEnumCode) {
		String strField = null;
		switch (fieldEnumCode) {
		case EXP_STIM1:
			strField = field_stim1;
			break;
		case EXP_CONC1:
			strField = field_conc1;
			break;
		case EXP_EXPT:
			strField = field_experiment;
			break;
		case EXP_BOXID:
			strField = field_boxID;
			break;
		case EXP_STRAIN:
			strField = field_strain;
			break;
		case EXP_SEX:
			strField = field_sex;
			break;
		case EXP_STIM2:
			strField = field_stim2;
			break;
		case EXP_CONC2:
			strField = field_conc2;
			break;
		default:
			break;
		}
		return strField;
	}

	public void setFieldNoTest(EnumXLSColumnHeader fieldEnumCode, String newValue) {
		switch (fieldEnumCode) {
		case EXP_STIM1:
			field_stim1 = newValue;
			break;
		case EXP_CONC1:
			field_conc1 = newValue;
			break;
		case EXP_EXPT:
			field_experiment = newValue;
			break;
		case EXP_BOXID:
			field_boxID = newValue;
			break;
		case EXP_STRAIN:
			field_strain = newValue;
			break;
		case EXP_SEX:
			field_sex = newValue;
			break;
		case EXP_STIM2:
			field_stim2 = newValue;
			break;
		case EXP_CONC2:
			field_conc2 = newValue;
			break;
		default:
			break;
		}
	}

	public void copyFieldsFrom(ExperimentProperties expSource) {
		copyField(expSource, EnumXLSColumnHeader.EXP_EXPT);
		copyField(expSource, EnumXLSColumnHeader.EXP_BOXID);
		copyField(expSource, EnumXLSColumnHeader.EXP_STIM1);
		copyField(expSource, EnumXLSColumnHeader.EXP_CONC1);
		copyField(expSource, EnumXLSColumnHeader.EXP_STRAIN);
		copyField(expSource, EnumXLSColumnHeader.EXP_SEX);
		copyField(expSource, EnumXLSColumnHeader.EXP_STIM2);
		copyField(expSource, EnumXLSColumnHeader.EXP_CONC2);
	}

	private void copyField(ExperimentProperties expSource, EnumXLSColumnHeader fieldEnumCode) {
		String newValue = expSource.getField(fieldEnumCode);
		setFieldNoTest(fieldEnumCode, newValue);
	}

	public boolean areFieldsEqual(ExperimentProperties expi) {
		boolean flag = true;
		flag &= isFieldEqual(expi, EnumXLSColumnHeader.EXP_EXPT);
		flag &= isFieldEqual(expi, EnumXLSColumnHeader.EXP_BOXID);
		flag &= isFieldEqual(expi, EnumXLSColumnHeader.EXP_STIM1);
		flag &= isFieldEqual(expi, EnumXLSColumnHeader.EXP_CONC1);
		flag &= isFieldEqual(expi, EnumXLSColumnHeader.EXP_STRAIN);
		flag &= isFieldEqual(expi, EnumXLSColumnHeader.EXP_SEX);
		flag &= isFieldEqual(expi, EnumXLSColumnHeader.EXP_STIM2);
		flag &= isFieldEqual(expi, EnumXLSColumnHeader.EXP_CONC2);
		return flag;
	}

	private boolean isFieldEqual(ExperimentProperties expi, EnumXLSColumnHeader fieldEnumCode) {
		return expi.getField(fieldEnumCode).equals(this.getField(fieldEnumCode));
	}

	public String csvExportSectionHeader(String csvSep) {
		StringBuffer sbf = new StringBuffer();
		sbf.append("#" + csvSep + "DESCRIPTION" + csvSep + "multiSPOTS96 data\n");
		List<String> row2 = Arrays.asList(ID_BOXID, ID_EXPERIMENT, ID_STIM1_NEW, ID_CONC1_NEW, ID_STRING1, ID_STRING2,
				ID_STRAIN, ID_SEX, ID_STIM2_NEW, ID_CONC2_NEW);
		sbf.append(String.join(csvSep, row2));
		sbf.append("\n");
		return sbf.toString();
	}

	public String csvExportProperties(String csvSep) {
		StringBuffer sbf = new StringBuffer();
		List<String> row3 = Arrays.asList(field_boxID, field_experiment, field_stim1, field_conc1, field_string1,
				field_string2, field_strain, field_sex, field_stim2, field_conc2);
		sbf.append(String.join(csvSep, row3));
		sbf.append("\n");
		return sbf.toString();
	}

	public void csvImportProperties(String[] data) {
		int i = 0;
		field_boxID = data[i];
		i++;
		field_experiment = data[i];
		i++;
		field_stim1 = data[i];
		i++;
		field_conc1 = data[i];
		i++;
		field_string1 = (data.length > i) ? data[i] : "..";
		i++;
		field_string2 = (data.length > i) ? data[i] : "..";
		i++;
		field_strain = (data.length > i) ? data[i] : "..";
		i++;
		field_sex = (data.length > i) ? data[i] : "..";
		i++;
		field_stim2 = (data.length > i) ? data[i] : "..";
		i++;
		field_conc2 = (data.length > i) ? data[i] : "..";
		// Legacy fields: try to preserve if present in old format
		if (data.length > i + 1) {
			field_comment1 = data[i];
			field_comment2 = data[i + 1];
		}
	}

	// ================ getters / setters

	public String getFfield_boxID() {
		return field_boxID;
	}

	public String getFfield_experiment() {
		return field_experiment;
	}

	public String getField_stim1() {
		return field_stim1;
	}

	public String getField_conc1() {
		return field_conc1;
	}

	public String getField_comment1() {
		return field_comment1;
	}

	public String getField_comment2() {
		return field_comment2;
	}

	public String getField_strain() {
		return field_strain;
	}

	public String getField_sex() {
		return field_sex;
	}

	public String getField_stim2() {
		return field_stim2;
	}

	public String getField_conc2() {
		return field_conc2;
	}

	public String getField_string1() {
		return field_string1;
	}

	public String getField_string2() {
		return field_string2;
	}
}
