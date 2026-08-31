package plugins.fmp.multitools.service.tracking;

import java.util.Collections;
import java.util.List;

/** Transform fit plus diagnostics used for model selection and UI review. */
public final class PlanarTransformFit {
	private final PlanarTransform transform;
	private final double rms;
	private final List<Integer> inlierIndices;

	PlanarTransformFit(PlanarTransform transform, double rms, List<Integer> inlierIndices) {
		this.transform = transform;
		this.rms = rms;
		this.inlierIndices = Collections.unmodifiableList(inlierIndices);
	}

	public PlanarTransform getTransform() { return transform; }
	public double getRms() { return rms; }
	public List<Integer> getInlierIndices() { return inlierIndices; }
}
