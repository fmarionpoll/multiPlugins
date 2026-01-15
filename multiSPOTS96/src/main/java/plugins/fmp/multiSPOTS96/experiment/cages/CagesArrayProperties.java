package plugins.fmp.multiSPOTS96.experiment.cages;

import java.util.List;

/**
 * Immutable data class containing comprehensive cages array information.
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 */
public final class CagesArrayProperties {
	private final int totalCages;
	private final int validCages;
	private final int activeCages;
	private final int cagesWithSpots;
	private final int gridSize;
	private final List<String> cageNames;
	private List<String> cageSex;
	private List<String> cageStrain;
	private List<String> cageAge;
	private List<String> spotStimulus;
	private List<String> spotConcentration;
	private List<String> spotVolume;

	private CagesArrayProperties(Builder builder) {
		this.totalCages = builder.totalCages;
		this.validCages = builder.validCages;
		this.activeCages = builder.activeCages;
		this.cagesWithSpots = builder.cagesWithSpots;
		this.gridSize = builder.gridSize;
		this.cageNames = builder.cageNames != null ? List.copyOf(builder.cageNames) : List.of();
		this.cageSex = builder.cageSex != null ? List.copyOf(builder.cageSex) : List.of();
		this.cageStrain = builder.cageStrain != null ? List.copyOf(builder.cageStrain) : List.of();
		this.cageAge = builder.cageAge != null ? List.copyOf(builder.cageAge) : List.of();
		this.spotStimulus = builder.spotStimulus != null ? List.copyOf(builder.spotStimulus) : List.of();
		this.spotConcentration = builder.spotConcentration != null ? List.copyOf(builder.spotConcentration) : List.of();
		this.spotVolume = builder.spotVolume != null ? List.copyOf(builder.spotVolume) : List.of();

	}

	public static Builder builder() {
		return new Builder();
	}

	public int getTotalCages() {
		return totalCages;
	}

	public int getValidCages() {
		return validCages;
	}

	public int getActiveCages() {
		return activeCages;
	}

	public int getCagesWithSpots() {
		return cagesWithSpots;
	}

	public int getGridSize() {
		return gridSize;
	}

	public List<String> getCageNames() {
		return cageNames;
	}

	public List<String> getCageSexList() {
		return cageSex;
	}

	public List<String> getCageStrainList() {
		return cageStrain;
	}

	public List<String> getCageAgeList() {
		return cageAge;
	}

	public List<String> getSpotsStimulusList() {
		return spotStimulus;
	}

	public List<String> getSpotConcentrationList() {
		return spotConcentration;
	}

	public List<String> getSpotVolumeList() {
		return spotVolume;
	}

	// Computed properties
	public boolean hasValidCages() {
		return validCages > 0;
	}

	public double getValidCagesRatio() {
		return totalCages > 0 ? (double) validCages / totalCages : 0.0;
	}

	public double getActiveCagesRatio() {
		return totalCages > 0 ? (double) activeCages / totalCages : 0.0;
	}

	public double getSpotsCompletionRatio() {
		return totalCages > 0 ? (double) cagesWithSpots / totalCages : 0.0;
	}

	public boolean isGridComplete() {
		return totalCages == gridSize;
	}

	public boolean isReadyForAnalysis() {
		return activeCages > 0 && validCages > 0;
	}

	public boolean hasAnySpots() {
		return cagesWithSpots > 0;
	}

	@Override
	public String toString() {
		return String.format("CagesArrayInfo{total=%d, valid=%d, active=%d, withSpots=%d, grid=%d}", totalCages,
				validCages, activeCages, cagesWithSpots, gridSize);
	}

	public static class Builder {
		private int totalCages;
		private int validCages;
		private int activeCages;
		private int cagesWithSpots;
		private int gridSize;
		private List<String> cageNames;
		private List<String> cageSex;
		private List<String> cageStrain;
		private List<String> cageAge;
		private List<String> spotStimulus;
		private List<String> spotConcentration;
		private List<String> spotVolume;

		public Builder totalCages(int totalCages) {
			this.totalCages = totalCages;
			return this;
		}

		public Builder validCages(int validCages) {
			this.validCages = validCages;
			return this;
		}

		public Builder activeCages(int activeCages) {
			this.activeCages = activeCages;
			return this;
		}

		public Builder cagesWithSpots(int cagesWithSpots) {
			this.cagesWithSpots = cagesWithSpots;
			return this;
		}

		public Builder gridSize(int gridSize) {
			this.gridSize = gridSize;
			return this;
		}

		public Builder cageNames(List<String> cageNames) {
			this.cageNames = cageNames;
			return this;
		}

		public Builder cageSex(List<String> cageSex) {
			this.cageSex = cageSex;
			return this;
		}

		public Builder cageStrain(List<String> cageStrain) {
			this.cageStrain = cageStrain;
			return this;
		}

		public Builder cageAge(List<String> cageAge) {
			this.cageAge = cageAge;
			return this;
		}

		public Builder spotStimulus(List<String> spotStimulus) {
			this.spotStimulus = spotStimulus;
			return this;
		}

		public Builder spotConcentration(List<String> spotConcentration) {
			this.spotConcentration = spotConcentration;
			return this;
		}

		public Builder spotVolume(List<String> spotVolume) {
			this.spotVolume = spotVolume;
			return this;
		}

		public CagesArrayProperties build() {
			return new CagesArrayProperties(this);
		}
	}
}