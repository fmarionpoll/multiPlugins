package plugins.fmp.multitools.tools.NHDistance;

/**
 * The Interface Distance. author Nicolas HERVE
 */

public interface NHDistance<T> {
	double computeDistance(T s1, T s2) throws NHFeatureException;
}