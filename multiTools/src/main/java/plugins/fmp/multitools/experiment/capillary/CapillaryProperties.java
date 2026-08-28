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
	private boolean pixelsAutoMeasured = false;
	private double bottomBaselineY = Double.NaN;
	private double bottomBaselineMad = Double.NaN;
	private double bottomBaselineOutlierFrac = Double.NaN;
	private boolean descriptionOK = false;
	private boolean valid = true;
	private int versionInfos = 0;
	private BuildSeriesOptions limitsOptions = new BuildSeriesOptions();

	// === XML CONSTANTS ===

	private static final String ID_NFLIES = "nflies";
	private static final String ID_CAGENB = "cage_number";
	private static final String ID_CAPVOLUME = "capillaryVolume";
	private static final String ID_CAPPIXELS = "capillaryPixels";
	private static final String ID_CAPPIXELSAUTO = "capillaryPixelsAutoMeasured";
	private static final String ID_STIML = "stimulus";
	private static final String ID_CONCL = "concentration";
	private static final String ID_SIDE = "side";
	private static final String ID_DESCOK = "descriptionOK";
	private static final String ID_VERSIONINFOS = "versionInfos";
	private static final String ID_BOTTOM_BASELINE_Y = "bottomBaselineY";
	private static final String ID_BOTTOM_BASELINE_MAD = "bottomBaselineMad";
	private static final String ID_BOTTOM_BASELINE_OUTLIER_FRAC = "bottomBaselineOutlierFrac";

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
		this.pixelsAutoMeasured = source.pixelsAutoMeasured;
		this.bottomBaselineY = source.bottomBaselineY;
		this.bottomBaselineMad = source.bottomBaselineMad;
		this.bottomBaselineOutlierFrac = source.bottomBaselineOutlierFrac;
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
		this.pixelsAutoMeasured = source.pixelsAutoMeasured;
		this.bottomBaselineY = source.bottomBaselineY;
		this.bottomBaselineMad = source.bottomBaselineMad;
		this.bottomBaselineOutlierFrac = source.bottomBaselineOutlierFrac;
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
		pixelsAutoMeasured = XMLUtil.getElementBooleanValue(nodeMeta, ID_CAPPIXELSAUTO, false);
		stimulus = XMLUtil.getElementValue(nodeMeta, ID_STIML, ID_STIML);
		concentration = XMLUtil.getElementValue(nodeMeta, ID_CONCL, ID_CONCL);
		side = XMLUtil.getElementValue(nodeMeta, ID_SIDE, ".");
		bottomBaselineY = XMLUtil.getElementDoubleValue(nodeMeta, ID_BOTTOM_BASELINE_Y, Double.NaN);
		bottomBaselineMad = XMLUtil.getElementDoubleValue(nodeMeta, ID_BOTTOM_BASELINE_MAD, Double.NaN);
		bottomBaselineOutlierFrac = XMLUtil.getElementDoubleValue(nodeMeta, ID_BOTTOM_BASELINE_OUTLIER_FRAC,
				Double.NaN);

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
		XMLUtil.setElementBooleanValue(nodeMeta, ID_CAPPIXELSAUTO, pixelsAutoMeasured);
		XMLUtil.setElementValue(nodeMeta, ID_STIML, stimulus);
		XMLUtil.setElementValue(nodeMeta, ID_SIDE, side);
		XMLUtil.setElementValue(nodeMeta, ID_CONCL, concentration);
		XMLUtil.setElementDoubleValue(nodeMeta, ID_BOTTOM_BASELINE_Y, bottomBaselineY);
		XMLUtil.setElementDoubleValue(nodeMeta, ID_BOTTOM_BASELINE_MAD, bottomBaselineMad);
		XMLUtil.setElementDoubleValue(nodeMeta, ID_BOTTOM_BASELINE_OUTLIER_FRAC, bottomBaselineOutlierFrac);

		limitsOptions.saveToXML(nodeMeta);

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

	/**
	 * True when {@code pixels} was measured on the image for this capillary alone
	 * rather than copied from the experiment-wide description. Such values must
	 * survive routine saves and are only discarded by an explicit reset.
	 */
	public boolean isPixelsAutoMeasured() {
		return pixelsAutoMeasured;
	}

	public void setPixelsAutoMeasured(boolean pixelsAutoMeasured) {
		this.pixelsAutoMeasured = pixelsAutoMeasured;
	}

	public double getBottomBaselineY() {
		return bottomBaselineY;
	}

	public void setBottomBaselineY(double bottomBaselineY) {
		this.bottomBaselineY = bottomBaselineY;
	}

	public double getBottomBaselineMad() {
		return bottomBaselineMad;
	}

	public void setBottomBaselineMad(double bottomBaselineMad) {
		this.bottomBaselineMad = bottomBaselineMad;
	}

	public double getBottomBaselineOutlierFrac() {
		return bottomBaselineOutlierFrac;
	}

	public void setBottomBaselineOutlierFrac(double bottomBaselineOutlierFrac) {
		this.bottomBaselineOutlierFrac = bottomBaselineOutlierFrac;
	}

	public void clearBottomBaseline() {
		bottomBaselineY = Double.NaN;
		bottomBaselineMad = Double.NaN;
		bottomBaselineOutlierFrac = Double.NaN;
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
