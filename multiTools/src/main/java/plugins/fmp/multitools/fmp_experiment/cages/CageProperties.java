package plugins.fmp.multitools.fmp_experiment.cages;

import java.awt.Color;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

import org.w3c.dom.Element;

import icy.util.XMLUtil;

public class CageProperties {
	// Property names for PropertyChange events
	public static final String PROPERTY_VERSION = "version";
	public static final String PROPERTY_CAGE_ID = "cageID";
	public static final String PROPERTY_CAGE_POSITION = "cagePosition";
	public static final String PROPERTY_COLOR = "color";
	public static final String PROPERTY_ARRAY_INDEX = "arrayIndex";
	public static final String PROPERTY_ARRAY_COLUMN = "arrayColumn";
	public static final String PROPERTY_ARRAY_ROW = "arrayRow";
	public static final String PROPERTY_CAGE_NFLIES = "cageNFlies";
	public static final String PROPERTY_FLY_AGE = "flyAge";
	public static final String PROPERTY_CHECKED = "checked";
	public static final String PROPERTY_COMMENT = "comment";
	public static final String PROPERTY_FLY_SEX = "flySex";
	public static final String PROPERTY_FLY_STRAIN = "flyStrain";
	public static final String PROPERTY_STR_CAGE_NUMBER = "strCageNumber";

	// PropertyChangeSupport for firing events
	private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

	private int version = 1;

	private int cageID = -1;
	private int cagePosition = 0;
	private Color color = Color.MAGENTA;

	private int arrayIndex = 0;
	private int arrayColumn = -1;
	private int arrayRow = -1;

	private int cageNFlies = 0;
	private int flyAge = 5;
	private boolean checked = true;

	private String comment = "..";
	private String flySex = "..";
	private String flyStrain = "..";
	private String strCageNumber = "0";
	private int countStim1 = 1;
	private int countStim2 = 1;

	private final String ID_NFLIES = "nflies";
	private final String ID_AGE = "age";
	private final String ID_COMMENT = "comment";
	private final String ID_SEX = "sex";
	private final String ID_STRAIN = "strain";
	private final String ID_CAGEID = "ID";
	private final String ID_CAGEPOSITION = "Pos";
	private final String ID_ARRAYINDEX = "aIndex";
	private final String ID_ARRAYCOLUMN = "aCol";
	private final String ID_ARRAYROW = "aRow";
	private final String ID_COLOR_R = "color_R";
	private final String ID_COLOR_G = "color_G";
	private final String ID_COLOR_B = "color_B";

	public boolean isSelected() {
		return cageNFlies > 0;
	}

	public void setSelected(boolean selected) {
		int oldValue = cageNFlies;
		cageNFlies = selected ? 1 : 0;
		propertyChangeSupport.firePropertyChange(PROPERTY_CAGE_NFLIES, oldValue, cageNFlies);
	}

	// PropertyChangeListener support methods
	public void addPropertyChangeListener(PropertyChangeListener listener) {
		propertyChangeSupport.addPropertyChangeListener(listener);
	}

	public void removePropertyChangeListener(PropertyChangeListener listener) {
		propertyChangeSupport.removePropertyChangeListener(listener);
	}

	public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
		propertyChangeSupport.addPropertyChangeListener(propertyName, listener);
	}

	public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
		propertyChangeSupport.removePropertyChangeListener(propertyName, listener);
	}

	public void copy(CageProperties propFrom) {
		int oldArrayIndex = arrayIndex;
		int oldArrayColumn = arrayColumn;
		int oldArrayRow = arrayRow;
		int oldCageID = cageID;
		int oldCagePosition = cagePosition;
		Color oldColor = color;
		int oldCageNFlies = cageNFlies;
		int oldFlyAge = flyAge;
		String oldComment = comment;
		String oldFlySex = flySex;
		String oldStrCageNumber = strCageNumber;
		String oldFlyStrain = flyStrain;

		arrayIndex = propFrom.arrayIndex;
		arrayColumn = propFrom.arrayColumn;
		arrayRow = propFrom.arrayRow;
		cageID = propFrom.cageID;
		cagePosition = propFrom.cagePosition;
		color = propFrom.color;
		cageNFlies = propFrom.cageNFlies;
		flyAge = propFrom.flyAge;
		comment = propFrom.comment;
		flySex = propFrom.flySex;
		strCageNumber = propFrom.strCageNumber;
		flyStrain = propFrom.flyStrain;
		countStim1 = propFrom.countStim1;
		countStim2 = propFrom.countStim2;

		// Fire PropertyChange events for all changed properties
		propertyChangeSupport.firePropertyChange(PROPERTY_ARRAY_INDEX, oldArrayIndex, arrayIndex);
		propertyChangeSupport.firePropertyChange(PROPERTY_ARRAY_COLUMN, oldArrayColumn, arrayColumn);
		propertyChangeSupport.firePropertyChange(PROPERTY_ARRAY_ROW, oldArrayRow, arrayRow);
		propertyChangeSupport.firePropertyChange(PROPERTY_CAGE_ID, oldCageID, cageID);
		propertyChangeSupport.firePropertyChange(PROPERTY_CAGE_POSITION, oldCagePosition, cagePosition);
		propertyChangeSupport.firePropertyChange(PROPERTY_COLOR, oldColor, color);
		propertyChangeSupport.firePropertyChange(PROPERTY_CAGE_NFLIES, oldCageNFlies, cageNFlies);
		propertyChangeSupport.firePropertyChange(PROPERTY_FLY_AGE, oldFlyAge, flyAge);
		propertyChangeSupport.firePropertyChange(PROPERTY_COMMENT, oldComment, comment);
		propertyChangeSupport.firePropertyChange(PROPERTY_FLY_SEX, oldFlySex, flySex);
		propertyChangeSupport.firePropertyChange(PROPERTY_STR_CAGE_NUMBER, oldStrCageNumber, strCageNumber);
		propertyChangeSupport.firePropertyChange(PROPERTY_FLY_STRAIN, oldFlyStrain, flyStrain);
	}

	public void paste(CageProperties propTo) {
		// Store old values for PropertyChange events
		int oldArrayIndex = propTo.arrayIndex;
		int oldArrayColumn = propTo.arrayColumn;
		int oldArrayRow = propTo.arrayRow;
		int oldCageID = propTo.cageID;
		int oldCagePosition = propTo.cagePosition;
		Color oldColor = propTo.color;
		int oldCageNFlies = propTo.cageNFlies;
		int oldFlyAge = propTo.flyAge;
		String oldComment = propTo.comment;
		String oldFlySex = propTo.flySex;
		String oldStrCageNumber = propTo.strCageNumber;
		String oldFlyStrain = propTo.flyStrain;

		propTo.arrayIndex = arrayIndex;
		propTo.arrayColumn = arrayColumn;
		propTo.arrayRow = arrayRow;
		propTo.cageID = cageID;
		propTo.cagePosition = cagePosition;
		propTo.color = color;
		propTo.cageNFlies = cageNFlies;
		propTo.flyAge = flyAge;
		propTo.comment = comment;
		propTo.flySex = flySex;
		propTo.strCageNumber = strCageNumber;
		propTo.flyStrain = flyStrain;
		propTo.countStim1 = countStim1;
		propTo.countStim2 = countStim2;

		// Fire PropertyChange events for all changed properties
		propTo.propertyChangeSupport.firePropertyChange(PROPERTY_ARRAY_INDEX, oldArrayIndex, propTo.arrayIndex);
		propTo.propertyChangeSupport.firePropertyChange(PROPERTY_ARRAY_COLUMN, oldArrayColumn, propTo.arrayColumn);
		propTo.propertyChangeSupport.firePropertyChange(PROPERTY_ARRAY_ROW, oldArrayRow, propTo.arrayRow);
		propTo.propertyChangeSupport.firePropertyChange(PROPERTY_CAGE_ID, oldCageID, propTo.cageID);
		propTo.propertyChangeSupport.firePropertyChange(PROPERTY_CAGE_POSITION, oldCagePosition, propTo.cagePosition);
		propTo.propertyChangeSupport.firePropertyChange(PROPERTY_COLOR, oldColor, propTo.color);
		propTo.propertyChangeSupport.firePropertyChange(PROPERTY_CAGE_NFLIES, oldCageNFlies, propTo.cageNFlies);
		propTo.propertyChangeSupport.firePropertyChange(PROPERTY_FLY_AGE, oldFlyAge, propTo.flyAge);
		propTo.propertyChangeSupport.firePropertyChange(PROPERTY_COMMENT, oldComment, propTo.comment);
		propTo.propertyChangeSupport.firePropertyChange(PROPERTY_FLY_SEX, oldFlySex, propTo.flySex);
		propTo.propertyChangeSupport.firePropertyChange(PROPERTY_STR_CAGE_NUMBER, oldStrCageNumber,
				propTo.strCageNumber);
		propTo.propertyChangeSupport.firePropertyChange(PROPERTY_FLY_STRAIN, oldFlyStrain, propTo.flyStrain);
	}

	public boolean xmlLoadCageParameters(Element xmlVal) {
		// Store old values for PropertyChange events
		int oldCageID = cageID;
		int oldCagePosition = cagePosition;
		int oldArrayIndex = arrayIndex;
		int oldArrayColumn = arrayColumn;
		int oldArrayRow = arrayRow;
		int oldCageNFlies = cageNFlies;
		int oldFlyAge = flyAge;
		String oldComment = comment;
		String oldFlySex = flySex;
		String oldFlyStrain = flyStrain;
		Color oldColor = color;

		cageID = XMLUtil.getElementIntValue(xmlVal, ID_CAGEID, cageID);
		cagePosition = XMLUtil.getElementIntValue(xmlVal, ID_CAGEPOSITION, cagePosition);
		arrayIndex = XMLUtil.getElementIntValue(xmlVal, ID_ARRAYINDEX, arrayIndex);
		arrayColumn = XMLUtil.getElementIntValue(xmlVal, ID_ARRAYCOLUMN, arrayColumn);
		arrayRow = XMLUtil.getElementIntValue(xmlVal, ID_ARRAYROW, arrayRow);
		cageNFlies = XMLUtil.getElementIntValue(xmlVal, ID_NFLIES, cageNFlies);
		flyAge = XMLUtil.getElementIntValue(xmlVal, ID_AGE, flyAge);

		comment = XMLUtil.getElementValue(xmlVal, ID_COMMENT, comment);
		flySex = XMLUtil.getElementValue(xmlVal, ID_SEX, flySex);
		flyStrain = XMLUtil.getElementValue(xmlVal, ID_STRAIN, flyStrain);
		int r = XMLUtil.getElementIntValue(xmlVal, ID_COLOR_R, color.getRed());
		int g = XMLUtil.getElementIntValue(xmlVal, ID_COLOR_G, color.getGreen());
		int b = XMLUtil.getElementIntValue(xmlVal, ID_COLOR_B, color.getBlue());
		color = new Color(r, g, b);

		// Fire PropertyChange events for all changed properties
		propertyChangeSupport.firePropertyChange(PROPERTY_CAGE_ID, oldCageID, cageID);
		propertyChangeSupport.firePropertyChange(PROPERTY_CAGE_POSITION, oldCagePosition, cagePosition);
		propertyChangeSupport.firePropertyChange(PROPERTY_ARRAY_INDEX, oldArrayIndex, arrayIndex);
		propertyChangeSupport.firePropertyChange(PROPERTY_ARRAY_COLUMN, oldArrayColumn, arrayColumn);
		propertyChangeSupport.firePropertyChange(PROPERTY_ARRAY_ROW, oldArrayRow, arrayRow);
		propertyChangeSupport.firePropertyChange(PROPERTY_CAGE_NFLIES, oldCageNFlies, cageNFlies);
		propertyChangeSupport.firePropertyChange(PROPERTY_FLY_AGE, oldFlyAge, flyAge);
		propertyChangeSupport.firePropertyChange(PROPERTY_COMMENT, oldComment, comment);
		propertyChangeSupport.firePropertyChange(PROPERTY_FLY_SEX, oldFlySex, flySex);
		propertyChangeSupport.firePropertyChange(PROPERTY_FLY_STRAIN, oldFlyStrain, flyStrain);
		propertyChangeSupport.firePropertyChange(PROPERTY_COLOR, oldColor, color);

		return true;
	}

	public boolean xmlSaveCageParameters(Element xmlVal) {
		XMLUtil.setElementIntValue(xmlVal, ID_CAGEID, cageID);
		XMLUtil.setElementIntValue(xmlVal, ID_CAGEPOSITION, cagePosition);
		XMLUtil.setElementIntValue(xmlVal, ID_ARRAYINDEX, arrayIndex);
		XMLUtil.setElementIntValue(xmlVal, ID_ARRAYCOLUMN, arrayColumn);
		XMLUtil.setElementIntValue(xmlVal, ID_ARRAYROW, arrayRow);
		XMLUtil.setElementIntValue(xmlVal, ID_NFLIES, cageNFlies);
		XMLUtil.setElementIntValue(xmlVal, ID_AGE, flyAge);

		XMLUtil.setElementValue(xmlVal, ID_COMMENT, comment);
		XMLUtil.setElementValue(xmlVal, ID_SEX, flySex);
		XMLUtil.setElementValue(xmlVal, ID_STRAIN, flyStrain);
		XMLUtil.setElementIntValue(xmlVal, ID_COLOR_R, color.getRed());
		XMLUtil.setElementIntValue(xmlVal, ID_COLOR_G, color.getGreen());
		XMLUtil.setElementIntValue(xmlVal, ID_COLOR_B, color.getBlue());
		return true;
	}

	public int getCagePosition() {
		return cagePosition;
	}

	public void setCagePosition(int pos) {
		int oldValue = this.cagePosition;
		this.cagePosition = pos;
		propertyChangeSupport.firePropertyChange(PROPERTY_CAGE_POSITION, oldValue, this.cagePosition);
	}

	public int getCageID() {
		return cageID;
	}

	public void setCageID(int cageID) {
		int oldValue = this.cageID;
		this.cageID = cageID;
		propertyChangeSupport.firePropertyChange(PROPERTY_CAGE_ID, oldValue, this.cageID);
	}

	public int getCageNFlies() {
		return cageNFlies;
	}

	public void setCageNFlies(int nFlies) {
		int oldValue = this.cageNFlies;
		this.cageNFlies = nFlies;
		propertyChangeSupport.firePropertyChange(PROPERTY_CAGE_NFLIES, oldValue, this.cageNFlies);
	}

	public int getVersion() {
		return version;
	}

	public void setVersion(int version) {
		int oldValue = this.version;
		this.version = version;
		propertyChangeSupport.firePropertyChange(PROPERTY_VERSION, oldValue, this.version);
	}

	public Color getColor() {
		return color;
	}

	public void setColor(Color color) {
		Color oldValue = this.color;
		this.color = color;
		propertyChangeSupport.firePropertyChange(PROPERTY_COLOR, oldValue, this.color);
	}

	public int getArrayIndex() {
		return arrayIndex;
	}

	public void setArrayIndex(int arrayIndex) {
		int oldValue = this.arrayIndex;
		this.arrayIndex = arrayIndex;
		propertyChangeSupport.firePropertyChange(PROPERTY_ARRAY_INDEX, oldValue, this.arrayIndex);
	}

	public int getArrayColumn() {
		return arrayColumn;
	}

	public void setArrayColumn(int arrayColumn) {
		int oldValue = this.arrayColumn;
		this.arrayColumn = arrayColumn;
		propertyChangeSupport.firePropertyChange(PROPERTY_ARRAY_COLUMN, oldValue, this.arrayColumn);
	}

	public int getArrayRow() {
		return arrayRow;
	}

	public void setArrayRow(int arrayRow) {
		int oldValue = this.arrayRow;
		this.arrayRow = arrayRow;
		propertyChangeSupport.firePropertyChange(PROPERTY_ARRAY_ROW, oldValue, this.arrayRow);
	}

	public int getFlyAge() {
		return flyAge;
	}

	public void setFlyAge(int flyAge) {
		int oldValue = this.flyAge;
		this.flyAge = flyAge;
		propertyChangeSupport.firePropertyChange(PROPERTY_FLY_AGE, oldValue, this.flyAge);
	}

	public boolean getChecked() {
		return checked;
	}

	public void setChecked(boolean checked) {
		boolean oldValue = this.checked;
		this.checked = checked;
		propertyChangeSupport.firePropertyChange(PROPERTY_CHECKED, oldValue, this.checked);
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		String oldValue = this.comment;
		this.comment = comment;
		propertyChangeSupport.firePropertyChange(PROPERTY_COMMENT, oldValue, this.comment);
	}

	public String getFlySex() {
		return flySex;
	}

	public void setFlySex(String flySex) {
		String oldValue = this.flySex;
		this.flySex = flySex;
		propertyChangeSupport.firePropertyChange(PROPERTY_FLY_SEX, oldValue, this.flySex);
	}

	public String getFlyStrain() {
		return flyStrain;
	}

	public void setFlyStrain(String flyStrain) {
		String oldValue = this.flyStrain;
		this.flyStrain = flyStrain;
		propertyChangeSupport.firePropertyChange(PROPERTY_FLY_STRAIN, oldValue, this.flyStrain);
	}

	public String getStrCageNumber() {
		return strCageNumber;
	}

	public void setStrCageNumber(String strCageNumber) {
		String oldValue = this.strCageNumber;
		this.strCageNumber = strCageNumber;
		propertyChangeSupport.firePropertyChange(PROPERTY_STR_CAGE_NUMBER, oldValue, this.strCageNumber);
	}

	public int getCountStim1() {
		return countStim1;
	}

	public void setCountStim1(int countSpotsStim1) {
		this.countStim1 = countSpotsStim1;
	}

	public int getCountStim2() {
		return countStim2;
	}

	public void setCountStim2(int countSpotsStim2) {
		this.countStim2 = countSpotsStim2;
	}
}
