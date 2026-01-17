package plugins.fmp.multitools.experiment.ids;

import java.util.Objects;

/**
 * Immutable unique identifier for a Spot.
 * Uses a simple integer ID that is unique across all spots.
 */
public final class SpotID {
	private final int id;

	public SpotID(int id) {
		if (id < 0) {
			throw new IllegalArgumentException("SpotID must be >= 0, got: " + id);
		}
		this.id = id;
	}

	public int getId() {
		return id;
	}

	@Deprecated
	public int getCageID() {
		throw new UnsupportedOperationException("getCageID() is deprecated. Use getId() for unique spot ID.");
	}

	@Deprecated
	public int getPosition() {
		throw new UnsupportedOperationException("getPosition() is deprecated. Use getId() for unique spot ID.");
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null || getClass() != obj.getClass())
			return false;
		SpotID spotID = (SpotID) obj;
		return id == spotID.id;
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}

	@Override
	public String toString() {
		return "SpotID{id=" + id + "}";
	}
}



