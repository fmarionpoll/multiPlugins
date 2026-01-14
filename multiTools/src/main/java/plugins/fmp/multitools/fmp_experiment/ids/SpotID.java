package plugins.fmp.multitools.fmp_experiment.ids;

import java.util.Objects;

/**
 * Immutable composite identifier for a Spot.
 * Combines cageID and position within the cage to create a unique identifier.
 */
public final class SpotID {
	private final int cageID;
	private final int position;

	public SpotID(int cageID, int position) {
		if (cageID < 0) {
			throw new IllegalArgumentException("SpotID cageID must be >= 0, got: " + cageID);
		}
		if (position < 0) {
			throw new IllegalArgumentException("SpotID position must be >= 0, got: " + position);
		}
		this.cageID = cageID;
		this.position = position;
	}

	public int getCageID() {
		return cageID;
	}

	public int getPosition() {
		return position;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null || getClass() != obj.getClass())
			return false;
		SpotID other = (SpotID) obj;
		return cageID == other.cageID && position == other.position;
	}

	@Override
	public int hashCode() {
		return Objects.hash(cageID, position);
	}

	@Override
	public String toString() {
		return "SpotID{cageID=" + cageID + ", position=" + position + "}";
	}
}



