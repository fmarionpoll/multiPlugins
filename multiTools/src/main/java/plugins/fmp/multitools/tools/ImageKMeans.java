package plugins.fmp.multitools.tools;

import java.util.List;

import icy.sequence.Sequence;
import plugins.nherve.toolbox.image.feature.DefaultClusteringAlgorithmImpl;
import plugins.nherve.toolbox.image.feature.IcySupportRegion;
import plugins.nherve.toolbox.image.feature.SegmentableIcyBufferedImage;
import plugins.nherve.toolbox.image.feature.Signature;
import plugins.nherve.toolbox.image.feature.clustering.KMeans;
import plugins.nherve.toolbox.image.feature.descriptor.ColorPixel;
import plugins.nherve.toolbox.image.feature.descriptor.DefaultDescriptorImpl;
import plugins.nherve.toolbox.image.feature.region.GridFactory;
import plugins.nherve.toolbox.image.feature.region.SupportRegionException;
import plugins.nherve.toolbox.image.feature.signature.SignatureException;
import plugins.nherve.toolbox.image.feature.signature.VectorSignature;
import plugins.nherve.toolbox.image.mask.MaskException;
import plugins.nherve.toolbox.image.segmentation.DefaultSegmentationAlgorithm;
import plugins.nherve.toolbox.image.segmentation.Segmentation;
import plugins.nherve.toolbox.image.segmentation.SegmentationException;

/**
 * Utility class for performing K-means clustering on image data. This class
 * provides functionality to segment images using K-means clustering algorithm
 * with various parameters for color space and clustering configuration.
 * 
 * <p>
 * ImageKMeans is used in the MultiSPOTS96 plugin for image segmentation and
 * analysis, particularly for identifying regions of interest in experimental
 * data.
 * </p>
 * 
 * <p>
 * Usage example:
 * 
 * <pre>
 * Sequence sequence = getImageSequence();
 * Segmentation result = ImageKMeans.doClustering(sequence, 5, 100, 0.01, 0);
 * result.reInitColors(sequence.getImage(0, 0));
 * </pre>
 * 
 * @author MultiSPOTS96
 * @see icy.sequence.Sequence
 * @see plugins.nherve.toolbox.image.segmentation.Segmentation
 */
public class ImageKMeans {

	/** Default number of clusters if not specified */
	private static final int DEFAULT_NUM_CLUSTERS = 5;

	/** Default number of iterations if not specified */
	private static final int DEFAULT_NUM_ITERATIONS = 100;

	/** Default stability threshold if not specified */
	private static final double DEFAULT_STABILITY_THRESHOLD = 0.01;

	/** Default color space if not specified */
	private static final int DEFAULT_COLOR_SPACE = 0;

	/**
	 * Performs K-means clustering on a sequence with the specified parameters.
	 * 
	 * @param seq   the sequence to cluster
	 * @param nbc2  the number of clusters
	 * @param nbi2  the number of iterations
	 * @param stab2 the stability threshold
	 * @param cs    the color space
	 * @return the segmentation result
	 * @throws SupportRegionException   if there's an error with support regions
	 * @throws SegmentationException    if there's an error during segmentation
	 * @throws MaskException            if there's an error with masks
	 * @throws NumberFormatException    if there's a number parsing error
	 * @throws SignatureException       if there's an error with signatures
	 * @throws IllegalArgumentException if seq is null or parameters are invalid
	 */
	public static Segmentation doClustering(Sequence seq, int nbc2, int nbi2, double stab2, int cs)
			throws SupportRegionException, SegmentationException, MaskException, NumberFormatException,
			SignatureException {

		if (seq == null) {
			throw new IllegalArgumentException("Sequence cannot be null");
		}
		if (nbc2 <= 0) {
			throw new IllegalArgumentException("Number of clusters must be positive: " + nbc2);
		}
		if (nbi2 <= 0) {
			throw new IllegalArgumentException("Number of iterations must be positive: " + nbi2);
		}
		if (stab2 < 0) {
			throw new IllegalArgumentException("Stability threshold cannot be negative: " + stab2);
		}
		Segmentation segmentation = null;
		try {
			segmentation = doClusteringKM(seq, nbc2, nbi2, stab2, cs);
			if (segmentation != null) {
				segmentation.reInitColors(seq.getImage(0, 0));
			} else {
				Logger.warn("K-means clustering returned null result");
			}
		} catch (Exception e) {
			Logger.error("Error during K-means clustering: " + e.getMessage());
			throw e;
		}

		return segmentation;
	}

	/**
	 * Performs the actual K-means clustering algorithm.
	 * 
	 * @param currentSequence the sequence to cluster
	 * @param nbc2            the number of clusters
	 * @param nbi2            the number of iterations
	 * @param stab2           the stability threshold
	 * @param cs              the color space
	 * @return the segmentation result
	 * @throws SupportRegionException if there's an error with support regions
	 * @throws SegmentationException  if there's an error during segmentation
	 * @throws MaskException          if there's an error with masks
	 * @throws NumberFormatException  if there's a number parsing error
	 * @throws SignatureException     if there's an error with signatures
	 */
	private static Segmentation doClusteringKM(Sequence currentSequence, int nbc2, int nbi2, double stab2, int cs)
			throws SupportRegionException, SegmentationException, MaskException, NumberFormatException,
			SignatureException {

		if (currentSequence == null) {
			throw new IllegalArgumentException("Current sequence cannot be null");
		}

		Logger.debug("Creating segmentable image from sequence");
		SegmentableIcyBufferedImage img = new SegmentableIcyBufferedImage(currentSequence.getFirstImage());

		Logger.debug("Configuring K-means algorithm");
		KMeans km2 = new KMeans(nbc2, nbi2, stab2);
		km2.setLogEnabled(false);

		Segmentation seg = null;
		DefaultDescriptorImpl<SegmentableIcyBufferedImage, ? extends Signature> col = null;

		Logger.debug("Setting up color pixel descriptor");
		ColorPixel cd = new ColorPixel(false);
		cd.setColorSpace(cs);
		col = cd;
		col.setLogEnabled(false);

		Logger.debug("Creating grid factory and extracting regions");
		GridFactory factory = new GridFactory(GridFactory.ALGO_ONLY_PIXELS);
		factory.setLogEnabled(false);

		List<IcySupportRegion> lRegions = factory.extractRegions(img);
		if (lRegions == null || lRegions.isEmpty()) {
			Logger.warn("No regions extracted from image");
			return null;
		}

		IcySupportRegion[] regions = new IcySupportRegion[lRegions.size()];
		int r = 0;
		for (IcySupportRegion sr : lRegions) {
			if (sr != null) {
				regions[r++] = sr;
			}
		}

		Logger.debug("Extracted " + r + " regions from image");
		if (r == 0) {
			Logger.warn("No valid regions found after filtering");
			return null;
		}

		seg = doSingleClustering(img, regions, col, km2);
		return seg;
	}

	/**
	 * Performs single clustering operation with the given parameters.
	 * 
	 * @param img        the segmentable image
	 * @param regions    the support regions
	 * @param descriptor the descriptor to use
	 * @param algo       the clustering algorithm
	 * @return the segmentation result
	 * @throws SupportRegionException if there's an error with support regions
	 * @throws SegmentationException  if there's an error during segmentation
	 */
	private static Segmentation doSingleClustering(SegmentableIcyBufferedImage img, IcySupportRegion[] regions,
			DefaultDescriptorImpl<SegmentableIcyBufferedImage, ? extends Signature> descriptor,
			DefaultClusteringAlgorithmImpl<VectorSignature> algo) throws SupportRegionException, SegmentationException {

		if (img == null) {
			throw new IllegalArgumentException("Image cannot be null");
		}
		if (regions == null || regions.length == 0) {
			throw new IllegalArgumentException("Regions cannot be null or empty");
		}
		if (descriptor == null) {
			throw new IllegalArgumentException("Descriptor cannot be null");
		}
		if (algo == null) {
			throw new IllegalArgumentException("Algorithm cannot be null");
		}

//		Logger.debug("Creating segmentation algorithm");
		DefaultSegmentationAlgorithm<SegmentableIcyBufferedImage> segAlgo = new DefaultSegmentationAlgorithm<SegmentableIcyBufferedImage>(
				descriptor, algo);
		segAlgo.setLogEnabled(false);

//		Logger.debug("Performing segmentation");
		Segmentation seg = segAlgo.segment(img, regions);

		if (seg == null) {
			Logger.warn("Segmentation returned null result");
//		} else {
//			Logger.debug("Segmentation completed successfully");
		}

		return seg;
	}

	/**
	 * Performs K-means clustering with default parameters.
	 * 
	 * @param seq the sequence to cluster
	 * @return the segmentation result
	 * @throws SupportRegionException   if there's an error with support regions
	 * @throws SegmentationException    if there's an error during segmentation
	 * @throws MaskException            if there's an error with masks
	 * @throws NumberFormatException    if there's a number parsing error
	 * @throws SignatureException       if there's an error with signatures
	 * @throws IllegalArgumentException if seq is null
	 */
	public static Segmentation doClustering(Sequence seq) throws SupportRegionException, SegmentationException,
			MaskException, NumberFormatException, SignatureException {
		return doClustering(seq, DEFAULT_NUM_CLUSTERS, DEFAULT_NUM_ITERATIONS, DEFAULT_STABILITY_THRESHOLD,
				DEFAULT_COLOR_SPACE);
	}

	/**
	 * Performs K-means clustering with custom number of clusters.
	 * 
	 * @param seq         the sequence to cluster
	 * @param numClusters the number of clusters
	 * @return the segmentation result
	 * @throws SupportRegionException   if there's an error with support regions
	 * @throws SegmentationException    if there's an error during segmentation
	 * @throws MaskException            if there's an error with masks
	 * @throws NumberFormatException    if there's a number parsing error
	 * @throws SignatureException       if there's an error with signatures
	 * @throws IllegalArgumentException if seq is null or numClusters is invalid
	 */
	public static Segmentation doClustering(Sequence seq, int numClusters) throws SupportRegionException,
			SegmentationException, MaskException, NumberFormatException, SignatureException {
		if (numClusters <= 0) {
			throw new IllegalArgumentException("Number of clusters must be positive: " + numClusters);
		}
		return doClustering(seq, numClusters, DEFAULT_NUM_ITERATIONS, DEFAULT_STABILITY_THRESHOLD, DEFAULT_COLOR_SPACE);
	}
}
