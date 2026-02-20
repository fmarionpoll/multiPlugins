package plugins.fmp.multicafe;

import icy.preferences.XMLPreferences;
import plugins.fmp.multitools.experiment.ViewOptionsDTO;

/**
 * Central holder for view options (cam and kymos). Persisted in the UI via
 * XMLPreferences, not with the experiment. Applied when viewer T changes or when
 * a new experiment is loaded.
 */
public class ViewOptionsHolder {

	private static final String KEY_VIEW_CAPILLARIES = "viewCapillaries";
	private static final String KEY_VIEW_CAGES = "viewCages";
	private static final String KEY_VIEW_FLIES_CENTER = "viewFliesCenter";
	private static final String KEY_VIEW_FLIES_RECT = "viewFliesRect";
	private static final String KEY_VIEW_LEVELS = "viewLevels";
	private static final String KEY_VIEW_DERIVATIVE = "viewDerivative";
	private static final String KEY_VIEW_GULPS = "viewGulps";

	private boolean viewCapillaries = true;
	private boolean viewCages = true;
	private boolean viewFliesCenter = false;
	private boolean viewFliesRect = false;
	private boolean viewLevels = true;
	private boolean viewDerivative = true;
	private boolean viewGulps = true;

	public boolean isViewCapillaries() {
		return viewCapillaries;
	}

	public void setViewCapillaries(boolean viewCapillaries) {
		this.viewCapillaries = viewCapillaries;
	}

	public boolean isViewCages() {
		return viewCages;
	}

	public void setViewCages(boolean viewCages) {
		this.viewCages = viewCages;
	}

	public boolean isViewFliesCenter() {
		return viewFliesCenter;
	}

	public void setViewFliesCenter(boolean viewFliesCenter) {
		this.viewFliesCenter = viewFliesCenter;
	}

	public boolean isViewFliesRect() {
		return viewFliesRect;
	}

	public void setViewFliesRect(boolean viewFliesRect) {
		this.viewFliesRect = viewFliesRect;
	}

	public boolean isViewLevels() {
		return viewLevels;
	}

	public void setViewLevels(boolean viewLevels) {
		this.viewLevels = viewLevels;
	}

	public boolean isViewDerivative() {
		return viewDerivative;
	}

	public void setViewDerivative(boolean viewDerivative) {
		this.viewDerivative = viewDerivative;
	}

	public boolean isViewGulps() {
		return viewGulps;
	}

	public void setViewGulps(boolean viewGulps) {
		this.viewGulps = viewGulps;
	}

	public void load(XMLPreferences prefs) {
		if (prefs == null)
			return;
		viewCapillaries = "true".equalsIgnoreCase(prefs.get(KEY_VIEW_CAPILLARIES, "true"));
		viewCages = "true".equalsIgnoreCase(prefs.get(KEY_VIEW_CAGES, "true"));
		viewFliesCenter = "true".equalsIgnoreCase(prefs.get(KEY_VIEW_FLIES_CENTER, "false"));
		viewFliesRect = "true".equalsIgnoreCase(prefs.get(KEY_VIEW_FLIES_RECT, "false"));
		viewLevels = "true".equalsIgnoreCase(prefs.get(KEY_VIEW_LEVELS, "true"));
		viewDerivative = "true".equalsIgnoreCase(prefs.get(KEY_VIEW_DERIVATIVE, "true"));
		viewGulps = "true".equalsIgnoreCase(prefs.get(KEY_VIEW_GULPS, "true"));
	}

	public void save(XMLPreferences prefs) {
		if (prefs == null)
			return;
		prefs.put(KEY_VIEW_CAPILLARIES, String.valueOf(viewCapillaries));
		prefs.put(KEY_VIEW_CAGES, String.valueOf(viewCages));
		prefs.put(KEY_VIEW_FLIES_CENTER, String.valueOf(viewFliesCenter));
		prefs.put(KEY_VIEW_FLIES_RECT, String.valueOf(viewFliesRect));
		prefs.put(KEY_VIEW_LEVELS, String.valueOf(viewLevels));
		prefs.put(KEY_VIEW_DERIVATIVE, String.valueOf(viewDerivative));
		prefs.put(KEY_VIEW_GULPS, String.valueOf(viewGulps));
	}

	public ViewOptionsDTO toViewOptionsDTO() {
		ViewOptionsDTO dto = new ViewOptionsDTO();
		dto.setViewCapillaries(viewCapillaries);
		dto.setViewCages(viewCages);
		dto.setViewFliesCenter(viewFliesCenter);
		dto.setViewFliesRect(viewFliesRect);
		dto.setViewLevels(viewLevels);
		dto.setViewDerivative(viewDerivative);
		dto.setViewGulps(viewGulps);
		return dto;
	}
}
