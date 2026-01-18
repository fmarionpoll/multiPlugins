package plugins.fmp.multitools.experiment.capillary;

import org.w3c.dom.Node;

import icy.util.XMLUtil;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;

/**
 * Encapsulates capillary properties (metadata not directly related to image
 * processing).
 */
public class CapillaryProperties {

	// === FIELDS ===

	private String stimulus = "..";
	private String concentration = "..";
	private String side = ".";
	private int nFlies = 1;
	private int cageID = 0;
	private double volume = 5.;
	private int pixels = 5;
	private boolean descriptionOK = false;
	private boolean valid = true;
	private int versionInfos = 0;
	private BuildSeriesOptions limitsOptions = new BuildSeriesOptions();

	// === XML CONSTANTS ===

	private static final String ID_NFLIES = "nflies";
	private static final String ID_CAGENB = "cage_number";
	private static final String ID_CAPVOLUME = "capillaryVolume";
	private static final String ID_CAPPIXELS = "capillaryPixels";
	private static final String ID_STIML = "stimulus";
	private static final String ID_CONCL = "concentration";
	private static final String ID_SIDE = "side";
	private static final String ID_DESCOK = "descriptionOK";
	private static final String ID_VERSIONINFOS = "versionInfos";

	// === METHODS ===

	public CapillaryProperties() {
	}

	public CapillaryProperties(CapillaryProperties source) {
		this.stimulus = source.stimulus;
		this.concentration = source.concentration;
		this.side = source.side;
		this.nFlies = source.nFlies;
		this.cageID = source.cageID;
		this.volume = source.volume;
		this.pixels = source.pixels;
		this.descriptionOK = source.descriptionOK;
		this.versionInfos = source.versionInfos;
		this.limitsOptions = source.limitsOptions; // BuildSeriesOptions might need deep copy if mutable and shared
	}

	public void copyFrom(CapillaryProperties source) {
		this.stimulus = source.stimulus;
		this.concentration = source.concentration;
		this.side = source.side;
		this.nFlies = source.nFlies;
		this.cageID = source.cageID;
		this.volume = source.volume;
		this.pixels = source.pixels;
		this.descriptionOK = source.descriptionOK;
		this.versionInfos = source.versionInfos;
		this.limitsOptions = source.limitsOptions;
	}

	// === XML LOAD/SAVE ===

	public boolean loadFromXml(Node nodeMeta) {
		if (nodeMeta == null)
			return false;

		descriptionOK = XMLUtil.getElementBooleanValue(nodeMeta, ID_DESCOK, false);
		versionInfos = XMLUtil.getElementIntValue(nodeMeta, ID_VERSIONINFOS, 0);
		nFlies = XMLUtil.getElementIntValue(nodeMeta, ID_NFLIES, nFlies);
		cageID = XMLUtil.getElementIntValue(nodeMeta, ID_CAGENB, cageID);
		volume = XMLUtil.getElementDoubleValue(nodeMeta, ID_CAPVOLUME, Double.NaN);
		pixels = XMLUtil.getElementIntValue(nodeMeta, ID_CAPPIXELS, 5);
		stimulus = XMLUtil.getElementValue(nodeMeta, ID_STIML, ID_STIML);
		concentration = XMLUtil.getElementValue(nodeMeta, ID_CONCL, ID_CONCL);
		side = XMLUtil.getElementValue(nodeMeta, ID_SIDE, ".");

		limitsOptions.loadFromXML(nodeMeta);

		return true;
	}

	public boolean saveToXml(Node nodeMeta) {
		if (nodeMeta == null)
			return false;

		XMLUtil.setElementBooleanValue(nodeMeta, ID_DESCOK, descriptionOK);
		XMLUtil.setElementIntValue(nodeMeta, ID_VERSIONINFOS, versionInfos);
		XMLUtil.setElementIntValue(nodeMeta, ID_NFLIES, nFlies);
		XMLUtil.setElementIntValue(nodeMeta, ID_CAGENB, cageID);
		XMLUtil.setElementDoubleValue(nodeMeta, ID_CAPVOLUME, volume);
		XMLUtil.setElementIntValue(nodeMeta, ID_CAPPIXELS, pixels);
		XMLUtil.setElementValue(nodeMeta, ID_STIML, stimulus);
		XMLUtil.setElementValue(nodeMeta, ID_SIDE, side);
		XMLUtil.setElementValue(nodeMeta, ID_CONCL, concentration);

		// Note: limitsOptions save logic is handled here too implicitly in legacy code,
		// but typically options save to the same node.
		// limitsOptions.saveToXML(nodeMeta); // Assuming BuildSeriesOptions has
		// saveToXML?
		// Checked legacy code: limitsOptions.loadFromXML(nodeMeta) is called, but
		// saving?
		// Legacy code didn't seem to explicitly save limitsOptions in
		// saveToXml_CapillaryOnly?
		// Wait, checked Capillary.java line 660: boolean flag =
		// xmlSave_Intervals(node);
		// Ah, limitsOptions seems to be missing an explicit save call in the original
		// Capillary.java xmlSave_CapillaryOnly?
		// Let's re-read carefully.
		// Line 599: limitsOptions.loadFromXML(nodeMeta);
		// Line 636: xmlSave_CapillaryOnly...
		// It doesn't seem to call limitsOptions.saveToXML(nodeMeta).
		// This might be a pre-existing issue or it uses separate storage.
		// For now I will strictly move existing logic.

		return true;
	}

	// === GETTERS/SETTERS ===

	public String getStimulus() {
		return stimulus;
	}

	public void setStimulus(String stimulus) {
		this.stimulus = stimulus;
	}

	public String getConcentration() {
		return concentration;
	}

	public void setConcentration(String concentration) {
		this.concentration = concentration;
	}

	public String getSide() {
		return side;
	}

	public void setSide(String side) {
		this.side = side;
	}

	public int getNFlies() {
		return nFlies;
	}

	public void setNFlies(int nFlies) {
		this.nFlies = nFlies;
	}

	public int getCageID() {
		return cageID;
	}

	public void setCageID(int cageID) {
		this.cageID = cageID;
	}

	public double getVolume() {
		return volume;
	}

	public void setVolume(double volume) {
		this.volume = volume;
	}

	public int getPixels() {
		return pixels;
	}

	public void setPixels(int pixels) {
		this.pixels = pixels;
	}

	public boolean isDescriptionOK() {
		return descriptionOK;
	}

	public void setDescriptionOK(boolean descriptionOK) {
		this.descriptionOK = descriptionOK;
	}

	public int getVersionInfos() {
		return versionInfos;
	}

	public void setVersionInfos(int versionInfos) {
		this.versionInfos = versionInfos;
	}

	public boolean getValid() {
		return valid;
	}

	public void setValid(boolean valid) {
		this.valid = valid;
	}

	public BuildSeriesOptions getLimitsOptions() {
		return limitsOptions;
	}

	public void setLimitsOptions(BuildSeriesOptions limitsOptions) {
		this.limitsOptions = limitsOptions;
	}
}
