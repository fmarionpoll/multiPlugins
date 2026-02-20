package plugins.fmp.multitools.experiment;

/**
 * DTO for view options (cam and kymos). Passed from UI to
 * Experiment.onViewerTPositionChanged so visibility can be applied after
 * syncing ROIs. Null means do not apply visibility.
 */
public class ViewOptionsDTO {

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
}
