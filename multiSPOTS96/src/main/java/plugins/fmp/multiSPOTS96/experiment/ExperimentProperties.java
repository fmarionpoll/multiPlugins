package plugins.fmp.multitools.experiment;

import java.util.Arrays;
import java.util.List;

import org.w3c.dom.Node;

import icy.util.XMLUtil;
import plugins.fmp.multitools.tools.toExcel.EnumXLSColumnHeader;

public class ExperimentProperties {

	public String ffield_boxID = new String("..");
	public String ffield_experiment = new String("..");
	public String field_stim1 = new String("..");
	public String field_conc1 = new String("..");
	public String field_comment1 = new String("..");
	public String field_comment2 = new String("..");
	public String field_strain = new String("..");
	public String field_sex = new String("..");
	public String field_stim2 = new String("..");
	public String field_conc2 = new String("..");

	private final static String ID_BOXID = "boxID";
	private final static String ID_EXPERIMENT = "experiment";
	private final static String ID_STIM = "stim";
	private final static String ID_CONC = "conc";

	private final static String ID_COMMENT1 = "comment";
	private final static String ID_COMMENT2 = "comment2";
	private final static String ID_STRAIN = "strain";
	private final static String ID_SEX = "sex";
	private final static String ID_COND1 = "cond1";
	private final static String ID_COND2 = "cond2";

	public void saveXML_Properties(Node node) {
		XMLUtil.setElementValue(node, ID_BOXID, ffield_boxID);
		XMLUtil.setElementValue(node, ID_EXPERIMENT, ffield_experiment);
		XMLUtil.setElementValue(node, ID_STIM, field_stim1);
		XMLUtil.setElementValue(node, ID_CONC, field_conc1);

		XMLUtil.setElementValue(node, ID_COMMENT1, field_comment1);
		XMLUtil.setElementValue(node, ID_COMMENT2, field_comment2);
		XMLUtil.setElementValue(node, ID_STRAIN, field_strain);
		XMLUtil.setElementValue(node, ID_SEX, field_sex);
		XMLUtil.setElementValue(node, ID_COND1, field_stim2);
		XMLUtil.setElementValue(node, ID_COND2, field_conc2);
	}

	public void loadXML_Properties(Node node) {
		ffield_boxID = XMLUtil.getElementValue(node, ID_BOXID, "..");
		ffield_experiment = XMLUtil.getElementValue(node, ID_EXPERIMENT, "..");
		field_stim1 = XMLUtil.getElementValue(node, ID_STIM, "..");
		field_conc1 = XMLUtil.getElementValue(node, ID_CONC, "..");

		field_comment1 = XMLUtil.getElementValue(node, ID_COMMENT1, "..");
		field_comment2 = XMLUtil.getElementValue(node, ID_COMMENT2, "..");
		field_strain = XMLUtil.getElementValue(node, ID_STRAIN, "..");
		field_sex = XMLUtil.getElementValue(node, ID_SEX, "..");
		field_stim2 = XMLUtil.getElementValue(node, ID_COND1, "..");
		field_conc2 = XMLUtil.getElementValue(node, ID_COND2, "..");
	}

	public String getExperimentField(EnumXLSColumnHeader fieldEnumCode) {
		String strField = null;
		switch (fieldEnumCode) {
		case EXP_STIM1:
			strField = field_stim1;
			break;
		case EXP_CONC1:
			strField = field_conc1;
			break;
		case EXP_EXPT:
			strField = ffield_experiment;
			break;
		case EXP_BOXID:
			strField = ffield_boxID;
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

	public void setExperimentFieldNoTest(EnumXLSColumnHeader fieldEnumCode, String newValue) {
		switch (fieldEnumCode) {
		case EXP_STIM1:
			field_stim1 = newValue;
			break;
		case EXP_CONC1:
			field_conc1 = newValue;
			break;
		case EXP_EXPT:
			ffield_experiment = newValue;
			break;
		case EXP_BOXID:
			ffield_boxID = newValue;
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

	public void copyExperimentFieldsFrom(ExperimentProperties expSource) {
		copyExperimentalField(expSource, EnumXLSColumnHeader.EXP_EXPT);
		copyExperimentalField(expSource, EnumXLSColumnHeader.EXP_BOXID);
		copyExperimentalField(expSource, EnumXLSColumnHeader.EXP_STIM1);
		copyExperimentalField(expSource, EnumXLSColumnHeader.EXP_CONC1);
		copyExperimentalField(expSource, EnumXLSColumnHeader.EXP_STRAIN);
		copyExperimentalField(expSource, EnumXLSColumnHeader.EXP_SEX);
		copyExperimentalField(expSource, EnumXLSColumnHeader.EXP_STIM2);
		copyExperimentalField(expSource, EnumXLSColumnHeader.EXP_CONC2);
	}

	private void copyExperimentalField(ExperimentProperties expSource, EnumXLSColumnHeader fieldEnumCode) {
		String newValue = expSource.getExperimentField(fieldEnumCode);
		setExperimentFieldNoTest(fieldEnumCode, newValue);
	}

	public boolean isSameProperties(ExperimentProperties expi) {
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
		return expi.getExperimentField(fieldEnumCode).equals(this.getExperimentField(fieldEnumCode));
	}

	public String csvExportExperimentSectionHeader(String csvSep) {
		StringBuffer sbf = new StringBuffer();
		sbf.append("#" + csvSep + "DESCRIPTION" + csvSep + "multiSPOTS96 data\n");
		List<String> row2 = Arrays.asList(ID_BOXID, ID_EXPERIMENT, ID_STIM, ID_CONC, ID_COMMENT1, ID_COMMENT2,
				ID_STRAIN, ID_SEX, ID_COND1, ID_COND2);
		sbf.append(String.join(csvSep, row2));
		sbf.append("\n");
		return sbf.toString();
	}

	public String csvExportExperimentProperties(String csvSep) {
		StringBuffer sbf = new StringBuffer();
		List<String> row3 = Arrays.asList(ffield_boxID, ffield_experiment, field_stim1, field_conc1, field_comment1,
				field_comment2, field_strain, field_sex, field_stim2, field_conc2);
		sbf.append(String.join(csvSep, row3));
		sbf.append("\n");
		return sbf.toString();
	}

	public void csvImportExperimentProperties(String[] data) {
		int i = 0;
		ffield_boxID = data[i];
		i++;
		ffield_experiment = data[i];
		i++;
		field_stim1 = data[i];
		i++;
		field_conc1 = data[i];
		i++;
		field_comment1 = data[i];
		i++;
		field_comment2 = data[i];
		i++;
		field_strain = data[i];
		i++;
		field_sex = data[i];
		i++;
		field_stim2 = data[i];
		i++;
		field_conc2 = data[i];
	}

	// ================ getters / setters

	public String getFfield_boxID() {
		return ffield_boxID;
	}

	public String getFfield_experiment() {
		return ffield_experiment;
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
}
