package plugins.fmp.multiSPOTS96.tools.NHDistance;

import java.awt.Color;

/**
 * NHClass ColorDistance.
 * 
 * @author Nicolas HERVE
 */
public abstract class NHDistanceColor implements NHDistance<Color> {
	/*
	 * (non-Javadoc)
	 * 
	 * @see plugins.nherve.toolbox.image.feature.Distance#computeDistance(java.lang.
	 * Object, java.lang.Object)
	 */
	public abstract double computeDistance(Color c1, Color c2);

	/**
	 * Gets the max distance.
	 * 
	 * @return the max distance
	 */
	public double getMaxDistance() {
		return computeDistance(new Color(0, 0, 0), new Color(255, 255, 255));
	}
}
