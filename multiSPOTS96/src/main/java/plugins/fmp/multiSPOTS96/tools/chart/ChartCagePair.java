package plugins.fmp.multiSPOTS96.tools.chart;

import java.util.logging.Logger;

import org.jfree.chart.ChartPanel;

import plugins.fmp.multiSPOTS96.experiment.cages.Cage;

/**
 * Container class that pairs a chart panel with its associated cage. This class
 * provides a convenient way to maintain the relationship between JFreeChart
 * panels and the cage data they represent.
 * 
 * <p>
 * The CageChartPair is used primarily in chart display systems where multiple
 * charts are generated for different cages in an experiment. It provides a
 * clean way to associate chart UI components with their underlying data
 * sources.
 * </p>
 * 
 * <p>
 * Usage example:
 * 
 * <pre>
 * ChartPanel chartPanel = new ChartPanel(chart);
 * Cage cage = experiment.getCage(0);
 * CageChartPair pair = new CageChartPair(chartPanel, cage);
 * 
 * // Access the components
 * ChartPanel panel = pair.getChartPanel();
 * Cage cageData = pair.getCage();
 * </pre>
 * 
 * @author MultiSPOTS96
 * @see org.jfree.chart.ChartPanel
 * @see plugins.fmp.multiSPOTS96.experiment.cages.Cage
 */
public class ChartCagePair {

	/** Logger for this class */
	private static final Logger LOGGER = Logger.getLogger(ChartCagePair.class.getName());

	/** The chart panel associated with this pair */
	private ChartPanel chartPanel;

	/** The cage data associated with this pair */
	private Cage cage;

	/**
	 * Creates a new CageChartPair with the specified chart panel and cage.
	 * 
	 * @param chartPanel the chart panel to associate with the cage
	 * @param cage       the cage data to associate with the chart panel
	 * @throws IllegalArgumentException if either parameter is null
	 */
	public ChartCagePair(ChartPanel chartPanel, Cage cage) {
//		if (chartPanel == null) {
//			throw new IllegalArgumentException("Chart panel cannot be null");
//		}
//		if (cage == null) {
//			throw new IllegalArgumentException("Cage cannot be null");
//		}

		this.chartPanel = chartPanel;
		this.cage = cage;

//		LOGGER.fine("Created CageChartPair for cage ID: " + cage.getProperties().getCageID());
	}

	/**
	 * Gets the chart panel associated with this pair.
	 * 
	 * @return the chart panel, or null if not set
	 */
	public ChartPanel getChartPanel() {
		return chartPanel;
	}

	/**
	 * Sets the chart panel for this pair.
	 * 
	 * @param chartPanel the chart panel to set
	 * @throws IllegalArgumentException if chartPanel is null
	 */
	public void setChartPanel(ChartPanel chartPanel) {
		if (chartPanel == null) {
			throw new IllegalArgumentException("Chart panel cannot be null");
		}

		this.chartPanel = chartPanel;
//		LOGGER.fine("Updated chart panel for cage ID: " + cage.getProperties().getCageID());
	}

	/**
	 * Gets the cage associated with this pair.
	 * 
	 * @return the cage, or null if not set
	 */
	public Cage getCage() {
		return cage;
	}

	/**
	 * Sets the cage for this pair.
	 * 
	 * @param cage the cage to set
	 * @throws IllegalArgumentException if cage is null
	 */
	public void setCage(Cage cage) {
		if (cage == null) {
			throw new IllegalArgumentException("Cage cannot be null");
		}

		this.cage = cage;
//		LOGGER.fine("Updated cage for chart panel, new cage ID: " + cage.getProperties().getCageID());
	}

	/**
	 * Checks if this pair has both a chart panel and cage set.
	 * 
	 * @return true if both components are set, false otherwise
	 */
	public boolean isComplete() {
		return chartPanel != null && cage != null;
	}

	/**
	 * Gets the cage ID associated with this pair.
	 * 
	 * @return the cage ID, or -1 if cage is not set
	 */
	public int getCageID() {
		return cage != null ? cage.getProperties().getCageID() : -1;
	}

	/**
	 * Gets the cage position associated with this pair.
	 * 
	 * @return the cage position, or -1 if cage is not set
	 */
	public int getCagePosition() {
		return cage != null ? cage.getProperties().getCagePosition() : -1;
	}

	/**
	 * Returns a string representation of this CageChartPair.
	 * 
	 * @return a string describing this pair
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("CageChartPair[");
		if (cage != null) {
			sb.append("cageID=").append(cage.getProperties().getCageID());
			sb.append(", cagePosition=").append(cage.getProperties().getCagePosition());
		} else {
			sb.append("cage=null");
		}
		sb.append(", chartPanel=").append(chartPanel != null ? "set" : "null");
		sb.append("]");
		return sb.toString();
	}

	/**
	 * Checks if this pair equals another object. Two CageChartPairs are considered
	 * equal if they have the same cage ID.
	 * 
	 * @param obj the object to compare with
	 * @return true if the objects are equal, false otherwise
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}

		ChartCagePair other = (ChartCagePair) obj;
		return this.getCageID() == other.getCageID();
	}

	/**
	 * Returns a hash code for this CageChartPair.
	 * 
	 * @return a hash code value
	 */
	@Override
	public int hashCode() {
		return Integer.hashCode(getCageID());
	}
}
