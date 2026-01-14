package plugins.fmp.multitools.fmp_experiment.ids;

import java.util.Objects;

/**
 * Immutable identifier for a Capillary.
 * Wraps the kymographIndex (0-19) which represents the capillary's position from left to right.
 */
public final class CapillaryID {
	private final int kymographIndex;

	public CapillaryID(int kymographIndex) {
		if (kymographIndex < 0) {
			throw new IllegalArgumentException("CapillaryID kymographIndex must be >= 0, got: " + kymographIndex);
		}
		this.kymographIndex = kymographIndex;
	}

	public int getKymographIndex() {
		return kymographIndex;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null || getClass() != obj.getClass())
			return false;
		CapillaryID other = (CapillaryID) obj;
		return kymographIndex == other.kymographIndex;
	}

	@Override
	public int hashCode() {
		return Objects.hash(kymographIndex);
	}

	@Override
	public String toString() {
		return "CapillaryID{kymographIndex=" + kymographIndex + "}";
	}
}



