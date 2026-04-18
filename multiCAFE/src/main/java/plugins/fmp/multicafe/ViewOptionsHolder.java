package plugins.fmp.multicafe;

import icy.preferences.XMLPreferences;
import plugins.fmp.multitools.ViewOptionsHolderBase;
import plugins.fmp.multitools.experiment.cafe.CafeViewOptionsDTO;

/**
 * Central holder for view options (cam and kymos). Persisted in the UI via
 * XMLPreferences, not with the experiment. Applied when viewer T changes or
 * when a new experiment is loaded.
 */
public class ViewOptionsHolder extends ViewOptionsHolderBase {

	private static final String KEY_VIEW_CAPILLARIES = "viewCapillaries";
	private static final String KEY_VIEW_FLIES_CENTER = "viewFliesCenter";
	private static final String KEY_VIEW_FLIES_RECT = "viewFliesRect";
	private static final String KEY_VIEW_TOPLEVELS = "viewTopLevels";
	private static final String KEY_VIEW_BOTTOMLEVELS = "viewBottomLevels";
	private static final String KEY_VIEW_DERIVATIVE = "viewDerivative";
	private static final String KEY_VIEW_GULPS = "viewGulps";

	private boolean viewCapillaries = true;
	private boolean viewFliesCenter = false;
	private boolean viewFliesRect = false;
	private boolean viewTopLevels = true;
	private boolean viewBottomLevels = true;
	private boolean viewDerivative = true;
	private boolean viewGulps = true;

	public boolean isViewCapillaries() {
		return viewCapillaries;
	}

	public void setViewCapillaries(boolean viewCapillaries) {
		this.viewCapillaries = viewCapillaries;
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

	public boolean isViewTopLevels() {
		return viewTopLevels;
	}

	public void setViewTopLevels(boolean viewTopLevels) {
		this.viewTopLevels = viewTopLevels;
	}

	public boolean isViewBottomLevels() {
		return viewBottomLevels;
	}

	public void setViewBottomLevels(boolean viewBottomLevels) {
		this.viewBottomLevels = viewBottomLevels;
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

	@Override
	protected void loadPluginFields(XMLPreferences prefs) {
		viewCapillaries = readBool(prefs, KEY_VIEW_CAPILLARIES, true);
		viewFliesCenter = readBool(prefs, KEY_VIEW_FLIES_CENTER, false);
		viewFliesRect = readBool(prefs, KEY_VIEW_FLIES_RECT, false);
		viewTopLevels = readBool(prefs, KEY_VIEW_TOPLEVELS, true);
		viewBottomLevels = readBool(prefs, KEY_VIEW_BOTTOMLEVELS, true);
		viewDerivative = readBool(prefs, KEY_VIEW_DERIVATIVE, true);
		viewGulps = readBool(prefs, KEY_VIEW_GULPS, true);
	}

	@Override
	protected void savePluginFields(XMLPreferences prefs) {
		prefs.put(KEY_VIEW_CAPILLARIES, String.valueOf(viewCapillaries));
		prefs.put(KEY_VIEW_FLIES_CENTER, String.valueOf(viewFliesCenter));
		prefs.put(KEY_VIEW_FLIES_RECT, String.valueOf(viewFliesRect));
		prefs.put(KEY_VIEW_TOPLEVELS, String.valueOf(viewTopLevels));
		prefs.put(KEY_VIEW_BOTTOMLEVELS, String.valueOf(viewBottomLevels));
		prefs.put(KEY_VIEW_DERIVATIVE, String.valueOf(viewDerivative));
		prefs.put(KEY_VIEW_GULPS, String.valueOf(viewGulps));
	}

	public CafeViewOptionsDTO toCafeViewOptionsDTO() {
		CafeViewOptionsDTO dto = new CafeViewOptionsDTO();
		dto.setViewCapillaries(viewCapillaries);
		dto.setViewCages(viewCages);
		dto.setViewFliesCenter(viewFliesCenter);
		dto.setViewFliesRect(viewFliesRect);
		dto.setViewTopLevels(viewTopLevels);
		dto.setViewBottomLevels(viewBottomLevels);
		dto.setViewDerivative(viewDerivative);
		dto.setViewGulps(viewGulps);
		return dto;
	}
}
