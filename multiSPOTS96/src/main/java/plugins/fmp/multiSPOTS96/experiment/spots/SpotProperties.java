package plugins.fmp.multiSPOTS96.experiment.spots;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import icy.util.XMLUtil;

/**
 * Encapsulates properties of a spot with clean access patterns and validation.
 * 
 * <p>
 * This class provides a clean interface for managing spot properties including
 * identification, positioning, appearance, and experimental parameters.
 * </p>
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 */
public class SpotProperties {

	// === CONSTANTS ===
	private static final String IDS_SPOTPROPS = "spotProperties";
	private static final String ID_DESCOK = "descriptionOK";
	private static final String ID_VERSIONINFOS = "versionInfos";
	private static final String ID_SPOTVOLUME = "volume";
	private static final String ID_PIXELS = "pixels";
	private static final String ID_RADIUS = "radius";
	private static final String ID_XCOORD = "spotXCoord";
	private static final String ID_YCOORD = "spotYCoord";
	private static final String ID_SPOTSTIMULUS = "stimulus";
	private static final String ID_CONCENTRATION = "concentration";
	private static final String ID_CAGEID = "cageID";
	private static final String ID_CAGEPOSITION = "cagePosition";
	private static final String ID_CAGECOLUMN = "cageColumn";
	private static final String ID_CAGEROW = "cageRow";
	private static final String ID_SPOTARRAYINDEX = "spotArrayIndex";
	private static final String ID_COLOR_R = "spotColor_R";
	private static final String ID_COLOR_G = "spotColor_G";
	private static final String ID_COLOR_B = "spotColor_B";

	private static final String DEFAULT_STIMULUS = "..";
	private static final String DEFAULT_CONCENTRATION = "..";
	private static final double DEFAULT_SPOT_VOLUME = 0.5;
	private static final int DEFAULT_SPOT_N_PIXELS = 1;
	private static final int DEFAULT_SPOT_RADIUS = 30;
	private static final int DEFAULT_SPOT_X_COORD = -1;
	private static final int DEFAULT_SPOT_Y_COORD = -1;
	private static final Color DEFAULT_COLOR = Color.GREEN;
	private static final int DEFAULT_VERSION = 1;
	private static final int DEFAULT_VERSION_INFOS = 0;

	// === CORE FIELDS ===
	private int version;
	private String name;
	private int cageID;
	private int cagePosition;
	private int cageRow;
	private int cageColumn;
	private int spotArrayIndex;
	private Color color;
	private String stimulus;
	private String concentration;
	private String stimulusI;
	private double spotVolume;
	private int spotNPixels;
	private int countAggregatedSpots;
	private int spotRadius;
	private int spotXCoord;
	private int spotYCoord;
	private boolean descriptionOK;
	private int versionInfos;

	// === CONSTRUCTORS ===

	/**
	 * Creates a new SpotProperties with default values.
	 */
	public SpotProperties() {
		this.version = DEFAULT_VERSION;
		this.stimulus = DEFAULT_STIMULUS;
		this.concentration = DEFAULT_CONCENTRATION;
		this.stimulusI = DEFAULT_STIMULUS;
		this.spotVolume = DEFAULT_SPOT_VOLUME;
		this.spotNPixels = DEFAULT_SPOT_N_PIXELS;
		this.spotRadius = DEFAULT_SPOT_RADIUS;
		this.spotXCoord = DEFAULT_SPOT_X_COORD;
		this.spotYCoord = DEFAULT_SPOT_Y_COORD;
		this.countAggregatedSpots = 1;
		this.color = DEFAULT_COLOR;
		this.cageID = -1;
		this.cagePosition = 0;
		this.cageRow = -1;
		this.cageColumn = -1;
		this.spotArrayIndex = -1;
		this.descriptionOK = false;
		this.versionInfos = DEFAULT_VERSION_INFOS;
	}

	/**
	 * Creates a copy of the specified SpotProperties.
	 * 
	 * @param source the source properties to copy from
	 * @throws IllegalArgumentException if source is null
	 */
	public SpotProperties(SpotProperties source) {
		Objects.requireNonNull(source, "Source properties cannot be null");
		copyFrom(source);
	}

	// === CORE OPERATIONS ===

	/**
	 * Copies properties from another SpotProperties instance.
	 * 
	 * @param source the source properties
	 * @throws IllegalArgumentException if source is null
	 */
	public void copyFrom(SpotProperties source) {
		Objects.requireNonNull(source, "Source properties cannot be null");

		this.version = source.version;
		this.name = source.name;
		this.cageID = source.cageID;
		this.cagePosition = source.cagePosition;
		this.cageRow = source.cageRow;
		this.cageColumn = source.cageColumn;
		this.spotArrayIndex = source.spotArrayIndex;
		this.color = source.color;
		this.stimulus = source.stimulus;
		this.concentration = source.concentration;
		this.stimulusI = source.stimulusI;
		this.spotVolume = source.spotVolume;
		this.spotNPixels = source.spotNPixels;
		this.spotRadius = source.spotRadius;
		this.spotXCoord = source.spotXCoord;
		this.spotYCoord = source.spotYCoord;
		this.countAggregatedSpots = source.countAggregatedSpots;
		this.descriptionOK = source.descriptionOK;
		this.versionInfos = source.versionInfos;
	}

	/**
	 * Checks if these properties have changed compared to another instance.
	 * 
	 * @param other the other properties to compare with
	 * @return true if there are changes
	 */
	public boolean hasChanged(SpotProperties other) {
		if (other == null) {
			return true;
		}

		return !Objects.equals(this.name, other.name) || this.cageID != other.cageID
				|| this.cagePosition != other.cagePosition || this.cageRow != other.cageRow
				|| this.cageColumn != other.cageColumn || this.spotArrayIndex != other.spotArrayIndex
				|| !Objects.equals(this.color, other.color) || !Objects.equals(this.stimulus, other.stimulus)
				|| !Objects.equals(this.concentration, other.concentration)
				|| !Objects.equals(this.stimulusI, other.stimulusI)
				|| Double.compare(this.spotVolume, other.spotVolume) != 0 || this.spotNPixels != other.spotNPixels
				|| this.spotRadius != other.spotRadius || this.spotXCoord != other.spotXCoord
				|| this.countAggregatedSpots != other.countAggregatedSpots
				|| this.spotYCoord != other.spotYCoord || this.descriptionOK != other.descriptionOK
				|| this.versionInfos != other.versionInfos;
	}

	// === IDENTIFICATION ===

	/**
	 * Gets the source name of this spot.
	 * 
	 * @return the source name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the source name of this spot.
	 * 
	 * @param sourceName the source name
	 */
	public void setName(String sourceName) {
		this.name = sourceName;
	}

	/**
	 * Gets the cage ID.
	 * 
	 * @return the cage ID
	 */
	public int getCageID() {
		return cageID;
	}

	/**
	 * Sets the cage ID.
	 * 
	 * @param cageID the cage ID
	 */
	public void setCageID(int cageID) {
		this.cageID = cageID;
	}

	/**
	 * Gets the cage position.
	 * 
	 * @return the cage position
	 */
	public int getCagePosition() {
		return cagePosition;
	}

	/**
	 * Sets the cage position.
	 * 
	 * @param cagePosition the cage position
	 */
	public void setCagePosition(int cagePosition) {
		this.cagePosition = cagePosition;
	}

	/**
	 * Gets the cage row.
	 * 
	 * @return the cage row
	 */
	public int getCageRow() {
		return cageRow;
	}

	/**
	 * Sets the cage row.
	 * 
	 * @param cageRow the cage row
	 */
	public void setCageRow(int cageRow) {
		this.cageRow = cageRow;
	}

	/**
	 * Gets the cage column.
	 * 
	 * @return the cage column
	 */
	public int getCageColumn() {
		return cageColumn;
	}

	/**
	 * Sets the cage column.
	 * 
	 * @param cageColumn the cage column
	 */
	public void setCageColumn(int cageColumn) {
		this.cageColumn = cageColumn;
	}

	/**
	 * Gets the spot array index.
	 * 
	 * @return the spot array index
	 */
	public int getSpotArrayIndex() {
		return spotArrayIndex;
	}

	/**
	 * Sets the spot array index.
	 * 
	 * @param spotArrayIndex the spot array index
	 */
	public void setSpotArrayIndex(int spotArrayIndex) {
		this.spotArrayIndex = spotArrayIndex;
	}

	// === APPEARANCE ===

	/**
	 * Gets the color of this spot.
	 * 
	 * @return the color
	 */
	public Color getColor() {
		return color;
	}

	/**
	 * Sets the color of this spot.
	 * 
	 * @param color the color
	 */
	public void setColor(Color color) {
		this.color = Objects.requireNonNull(color, "Color cannot be null");
	}

	// === EXPERIMENTAL PARAMETERS ===

	/**
	 * Gets the stimulus.
	 * 
	 * @return the stimulus
	 */
	public String getStimulus() {
		return stimulus;
	}

	/**
	 * Sets the stimulus.
	 * 
	 * @param stimulus the stimulus
	 */
	public void setStimulus(String stimulus) {
		this.stimulus = Objects.requireNonNull(stimulus, "Stimulus cannot be null");
	}

	/**
	 * Gets the concentration.
	 * 
	 * @return the concentration
	 */
	public String getConcentration() {
		return concentration;
	}

	/**
	 * Sets the concentration.
	 * 
	 * @param concentration the concentration
	 */
	public void setConcentration(String concentration) {
		this.concentration = Objects.requireNonNull(concentration, "Concentration cannot be null");
	}

	/**
	 * Gets the stimulus I.
	 * 
	 * @return the stimulus I
	 */
	public String getStimulusI() {
		return stimulusI;
	}

	/**
	 * Sets the stimulus I.
	 * 
	 * @param stimulusI the stimulus I
	 */
	public void setStimulusI(String stimulusI) {
		this.stimulusI = Objects.requireNonNull(stimulusI, "Stimulus I cannot be null");
	}

	// === SPOT DIMENSIONS ===

	/**
	 * Gets the spot volume.
	 * 
	 * @return the spot volume
	 */
	public double getSpotVolume() {
		return spotVolume;
	}

	/**
	 * Sets the spot volume.
	 * 
	 * @param spotVolume the spot volume
	 */
	public void setSpotVolume(double spotVolume) {
		this.spotVolume = spotVolume;
	}

	/**
	 * Gets the number of pixels in the spot.
	 * 
	 * @return the number of pixels
	 */
	public int getSpotNPixels() {
		return spotNPixels;
	}

	/**
	 * Sets the number of pixels in the spot.
	 * 
	 * @param spotNPixels the number of pixels
	 */
	public void setSpotNPixels(int spotNPixels) {
		this.spotNPixels = spotNPixels;
	}

	/**
	 * Gets the spot radius.
	 * 
	 * @return the spot radius
	 */
	public int getSpotRadius() {
		return spotRadius;
	}

	/**
	 * Sets the spot radius.
	 * 
	 * @param spotRadius the spot radius
	 */
	public void setSpotRadius(int spotRadius) {
		this.spotRadius = spotRadius;
	}

	/**
	 * Gets the spot X coordinate.
	 * 
	 * @return the spot X coordinate
	 */
	public int getSpotXCoord() {
		return spotXCoord;
	}

	/**
	 * Sets the spot X coordinate.
	 * 
	 * @param spotXCoord the spot X coordinate
	 */
	public void setSpotXCoord(int spotXCoord) {
		this.spotXCoord = spotXCoord;
	}

	/**
	 * Gets the spot Y coordinate.
	 * 
	 * @return the spot Y coordinate
	 */
	public int getSpotYCoord() {
		return spotYCoord;
	}

	/**
	 * Sets the spot Y coordinate.
	 * 
	 * @param spotYCoord the spot Y coordinate
	 */
	public void setSpotYCoord(int spotYCoord) {
		this.spotYCoord = spotYCoord;
	}

	public int getCountAggregatedSpots() {
		return this.countAggregatedSpots;
	}
	
	public void setCountAggregatedSpots(int spotsCombined) {
		this.countAggregatedSpots = spotsCombined;
	}
	
	// === STATUS ===

	/**
	 * Checks if the description is OK.
	 * 
	 * @return true if description is OK
	 */
	public boolean isDescriptionOK() {
		return descriptionOK;
	}

	/**
	 * Sets whether the description is OK.
	 * 
	 * @param descriptionOK the description OK flag
	 */
	public void setDescriptionOK(boolean descriptionOK) {
		this.descriptionOK = descriptionOK;
	}

	/**
	 * Gets the version.
	 * 
	 * @return the version
	 */
	public int getVersion() {
		return version;
	}

	/**
	 * Sets the version.
	 * 
	 * @param version the version
	 */
	public void setVersion(int version) {
		this.version = version;
	}

	/**
	 * Gets the version infos.
	 * 
	 * @return the version infos
	 */
	public int getVersionInfos() {
		return versionInfos;
	}

	/**
	 * Sets the version infos.
	 * 
	 * @param versionInfos the version infos
	 */
	public void setVersionInfos(int versionInfos) {
		this.versionInfos = versionInfos;
	}

	// === VALIDATION ===

	/**
	 * Validates these properties.
	 * 
	 * @return true if valid
	 */
	public boolean isValid() {
		return name != null && !name.trim().isEmpty() && cageID >= -1 && cagePosition >= 0 && cageRow >= -1
				&& cageColumn >= -1 && spotArrayIndex >= -1 && color != null && stimulus != null
				&& !stimulus.trim().isEmpty() && concentration != null && !concentration.trim().isEmpty()
				&& stimulusI != null && !stimulusI.trim().isEmpty() && spotVolume > 0 && spotNPixels > 0
				&& spotRadius > 0;
	}

	/**
	 * Gets validation errors for these properties.
	 * 
	 * @return list of validation error messages
	 */
	public List<String> getValidationErrors() {
		List<String> errors = new java.util.ArrayList<>();

		if (name == null || name.trim().isEmpty()) {
			errors.add("Source name is required");
		}

		if (cageID < -1) {
			errors.add("Cage ID must be >= -1");
		}

		if (cagePosition < 0) {
			errors.add("Cage position must be >= 0");
		}

		if (cageRow < -1) {
			errors.add("Cage row must be >= -1");
		}

		if (cageColumn < -1) {
			errors.add("Cage column must be >= -1");
		}

		if (spotArrayIndex < -1) {
			errors.add("Spot array index must be >= -1");
		}

		if (color == null) {
			errors.add("Color is required");
		}

		if (stimulus == null || stimulus.trim().isEmpty()) {
			errors.add("Stimulus is required");
		}

		if (concentration == null || concentration.trim().isEmpty()) {
			errors.add("Concentration is required");
		}

		if (stimulusI == null || stimulusI.trim().isEmpty()) {
			errors.add("Stimulus I is required");
		}

		if (spotVolume <= 0) {
			errors.add("Spot volume must be > 0");
		}

		if (spotNPixels <= 0) {
			errors.add("Spot number of pixels must be > 0");
		}

		if (spotRadius <= 0) {
			errors.add("Spot radius must be > 0");
		}

		return errors;
	}

	// === XML SERIALIZATION ===

	/**
	 * Saves spot description to XML.
	 * 
	 * @param node the XML node
	 * @return true if successful
	 */
	public boolean saveSpotDescriptionToXml(Node node) {
		Node propertiesNode = XMLUtil.addElement(node, IDS_SPOTPROPS);
		if (propertiesNode == null) {
			return false;
		}
		XMLUtil.setElementIntValue(propertiesNode, "version", DEFAULT_VERSION);
		return true;
	}

	/**
	 * Loads spot description from XML.
	 * 
	 * @param doc the XML document
	 * @return true if successful
	 */
	public boolean loadSpotDescriptionFromXml(Document doc) {
		if (doc == null) {
			return false;
		}

		Node node = XMLUtil.getElement(XMLUtil.getRootElement(doc), IDS_SPOTPROPS);
		if (node == null) {
			return false;
		}

		this.version = XMLUtil.getElementIntValue(node, "version", DEFAULT_VERSION);
		return loadFromXml(node);
	}

	/**
	 * Loads properties from XML.
	 * 
	 * @param node the XML node
	 * @return true if successful
	 */
	public boolean loadFromXml(Node node) {
		Element nodeParameters = XMLUtil.getElement(node, IDS_SPOTPROPS);
		if (nodeParameters == null) {
			return false;
		}

		try {
			this.spotArrayIndex = XMLUtil.getElementIntValue(nodeParameters, ID_SPOTARRAYINDEX, spotArrayIndex);
			this.cageID = XMLUtil.getElementIntValue(nodeParameters, ID_CAGEID, cageID);
			this.cagePosition = XMLUtil.getElementIntValue(nodeParameters, ID_CAGEPOSITION, cagePosition);
			this.cageColumn = XMLUtil.getElementIntValue(nodeParameters, ID_CAGECOLUMN, cageColumn);
			this.cageRow = XMLUtil.getElementIntValue(nodeParameters, ID_CAGEROW, cageRow);
			this.descriptionOK = XMLUtil.getElementBooleanValue(nodeParameters, ID_DESCOK, false);
			this.spotVolume = XMLUtil.getElementDoubleValue(nodeParameters, ID_SPOTVOLUME, Double.NaN);
			this.spotNPixels = XMLUtil.getElementIntValue(nodeParameters, ID_PIXELS, DEFAULT_SPOT_N_PIXELS);
			this.spotRadius = XMLUtil.getElementIntValue(nodeParameters, ID_RADIUS, DEFAULT_SPOT_RADIUS);
			this.spotXCoord = XMLUtil.getElementIntValue(nodeParameters, ID_XCOORD, DEFAULT_SPOT_X_COORD);
			this.spotYCoord = XMLUtil.getElementIntValue(nodeParameters, ID_YCOORD, DEFAULT_SPOT_Y_COORD);
			this.stimulus = XMLUtil.getElementValue(nodeParameters, ID_SPOTSTIMULUS, DEFAULT_STIMULUS);
			this.concentration = XMLUtil.getElementValue(nodeParameters, ID_CONCENTRATION, DEFAULT_CONCENTRATION);

			int r = XMLUtil.getElementIntValue(nodeParameters, ID_COLOR_R, color.getRed());
			int g = XMLUtil.getElementIntValue(nodeParameters, ID_COLOR_G, color.getGreen());
			int b = XMLUtil.getElementIntValue(nodeParameters, ID_COLOR_B, color.getBlue());
			this.color = new Color(r, g, b);

			return true;
		} catch (Exception e) {
			System.err.println("Error loading properties from XML: " + e.getMessage());
			return false;
		}
	}

	/**
	 * Saves properties to XML.
	 * 
	 * @param node the XML node
	 * @return true if successful
	 */
	public boolean saveToXml(Node node) {
		final Node nodeParameters = XMLUtil.setElement(node, IDS_SPOTPROPS);
		if (nodeParameters == null) {
			return false;
		}

		try {
			XMLUtil.setElementIntValue(nodeParameters, ID_SPOTARRAYINDEX, spotArrayIndex);
			XMLUtil.setElementIntValue(nodeParameters, ID_CAGEID, cageID);
			XMLUtil.setElementIntValue(nodeParameters, ID_CAGEPOSITION, cagePosition);
			XMLUtil.setElementIntValue(nodeParameters, ID_CAGECOLUMN, cageColumn);
			XMLUtil.setElementIntValue(nodeParameters, ID_CAGEROW, cageRow);
			XMLUtil.setElementIntValue(nodeParameters, ID_VERSIONINFOS, versionInfos);
			XMLUtil.setElementBooleanValue(nodeParameters, ID_DESCOK, descriptionOK);
			XMLUtil.setElementDoubleValue(nodeParameters, ID_SPOTVOLUME, spotVolume);
			XMLUtil.setElementIntValue(nodeParameters, ID_PIXELS, spotNPixels);
			XMLUtil.setElementIntValue(nodeParameters, ID_RADIUS, spotRadius);
			XMLUtil.setElementIntValue(nodeParameters, ID_XCOORD, spotXCoord);
			XMLUtil.setElementIntValue(nodeParameters, ID_YCOORD, spotYCoord);
			XMLUtil.setElementValue(nodeParameters, ID_SPOTSTIMULUS, stimulus);
			XMLUtil.setElementValue(nodeParameters, ID_CONCENTRATION, concentration);
			XMLUtil.setElementIntValue(nodeParameters, ID_COLOR_R, color.getRed());
			XMLUtil.setElementIntValue(nodeParameters, ID_COLOR_G, color.getGreen());
			XMLUtil.setElementIntValue(nodeParameters, ID_COLOR_B, color.getBlue());

			return true;
		} catch (Exception e) {
			System.err.println("Error saving properties to XML: " + e.getMessage());
			return false;
		}
	}

	// === CSV IMPORT/EXPORT ===

	/**
	 * Imports properties from CSV data.
	 * 
	 * @param data the CSV data array
	 * @throws IllegalArgumentException if data is null or invalid
	 */
	public void importFromCsv(String[] data) {
		Objects.requireNonNull(data, "CSV data cannot be null");

		if (data.length < 6) {
			throw new IllegalArgumentException("CSV data must have at least 6 elements");
		}

		try {
			int index = 0;
			this.name = data[index++];
			this.spotArrayIndex = Integer.parseInt(data[index++]);
			this.cageID = Integer.parseInt(data[index++]);

			if (this.cageID < 0) {
				this.cageID = SpotString.getCageIDFromSpotName(this.name);
			}

			this.cagePosition = Integer.parseInt(data[index++]);

			if (data.length >= 11) {
				this.cageColumn = Integer.parseInt(data[index++]);
				this.cageRow = Integer.parseInt(data[index++]);
			}

			if (data.length == 10) {
				this.spotRadius = Integer.parseInt(data[index++]); // dummy read
			}

			this.spotVolume = Double.parseDouble(data[index++]);
			this.spotNPixels = Integer.parseInt(data[index++]);
			this.spotRadius = Integer.parseInt(data[index++]);
			this.stimulus = data[index++];
			this.concentration = data[index++];

		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid numeric value in CSV data", e);
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new IllegalArgumentException("Insufficient data in CSV array", e);
		}
	}

	/**
	 * Exports spot properties header to CSV.
	 * 
	 * @param csvSeparator the CSV separator
	 * @return the CSV header string
	 */
	public static String exportHeaderToCsv(String csvSeparator) {
		Objects.requireNonNull(csvSeparator, "CSV separator cannot be null");

		StringBuilder sbf = new StringBuilder();
		sbf.append("#").append(csvSeparator).append("#\n");
		sbf.append("#").append(csvSeparator).append("SPOTS").append(csvSeparator).append("multiSPOTS96 data\n");

		List<String> row2 = Arrays.asList("name", "index", "cageID", "cagePos", "cageColumn", "cageRow", "volume",
				"npixels", "radius", "stim", "conc");
		sbf.append(String.join(csvSeparator, row2));
		sbf.append("\n");
		return sbf.toString();
	}

	/**
	 * Exports spot properties to CSV.
	 * 
	 * @param csvSeparator the CSV separator
	 * @return the CSV data string
	 */
	public String exportToCsv(String csvSeparator) {
		Objects.requireNonNull(csvSeparator, "CSV separator cannot be null");

		StringBuilder sbf = new StringBuilder();
		List<String> row = Arrays.asList(name != null ? name : "", String.valueOf(spotArrayIndex),
				String.valueOf(cageID), String.valueOf(cagePosition), String.valueOf(cageColumn),
				String.valueOf(cageRow), String.valueOf(spotVolume), String.valueOf(spotNPixels),
				String.valueOf(spotRadius), stimulus != null ? stimulus.replace(",", ".") : "",
				concentration != null ? concentration.replace(",", ".") : "");
		sbf.append(String.join(csvSeparator, row));
		sbf.append("\n");
		return sbf.toString();
	}

	// === UTILITY METHODS ===

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null || getClass() != obj.getClass())
			return false;
		SpotProperties other = (SpotProperties) obj;
		return Objects.equals(name, other.name) && cageID == other.cageID
				&& cagePosition == other.cagePosition && cageRow == other.cageRow && cageColumn == other.cageColumn
				&& spotArrayIndex == other.spotArrayIndex && Objects.equals(color, other.color)
				&& Objects.equals(stimulus, other.stimulus) && Objects.equals(concentration, other.concentration)
				&& Objects.equals(stimulusI, other.stimulusI) && Double.compare(spotVolume, other.spotVolume) == 0
				&& spotNPixels == other.spotNPixels && spotRadius == other.spotRadius && spotXCoord == other.spotXCoord
				&& spotYCoord == other.spotYCoord && descriptionOK == other.descriptionOK
				&& versionInfos == other.versionInfos;
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, cageID, cagePosition, cageRow, cageColumn, spotArrayIndex, color, stimulus,
				concentration, stimulusI, spotVolume, spotNPixels, spotRadius, spotXCoord, spotYCoord, descriptionOK,
				versionInfos);
	}

	@Override
	public String toString() {
		return String.format(
				"SpotProperties{name='%s', cageID=%d, position=%d, stimulus='%s', concentration='%s', volume=%.2f}",
				name, cageID, cagePosition, stimulus, concentration, spotVolume);
	}
}
