package plugins.fmp.multitools.experiment.ids;

import java.util.Objects;

/**
 * Immutable identifier for a Cage.
 * Wraps the cage ID which represents the cage's position (left-to-right or row/column grid).
 */
public final class CageID {
	private final int cageID;

	public CageID(int cageID) {
		if (cageID < 0) {
			throw new IllegalArgumentException("CageID must be >= 0, got: " + cageID);
		}
		this.cageID = cageID;
	}

	public int getCageID() {
		return cageID;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null || getClass() != obj.getClass())
			return false;
		CageID other = (CageID) obj;
		return cageID == other.cageID;
	}

	@Override
	public int hashCode() {
		return Objects.hash(cageID);
	}

	@Override
	public String toString() {
		return "CageID{cageID=" + cageID + "}";
	}
}



