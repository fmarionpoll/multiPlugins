package plugins.fmp.multitools.experiment.cafe;

/**
 * DTO for multiCAFE-specific view options (cam and kymos). Passed from UI to
 * {@code Experiment.onViewerTPositionChanged} so visibility can be applied
 * after syncing ROIs. Null means do not apply visibility.
 *
 * <p>Named explicitly to mark it as multiCAFE-only: multiSPOTS96 passes
 * {@code null} at all call sites (it has no analogous cluttered-viewer
 * problem yet). If multiSPOTS96 later needs its own visibility overrides,
 * introduce a sibling class rather than widening this one.
 */
public class CafeViewOptionsDTO {

	private boolean viewCapillaries = true;
	private boolean viewCages = true;
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
}
