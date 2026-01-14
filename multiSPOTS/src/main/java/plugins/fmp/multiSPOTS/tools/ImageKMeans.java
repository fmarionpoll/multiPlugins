package plugins.fmp.multiSPOTS.tools;

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

public class ImageKMeans {

	public static Segmentation doClustering(Sequence seq, int nbc2, int nbi2, double stab2, int cs)
			throws SupportRegionException, SegmentationException, MaskException, NumberFormatException,
			SignatureException {
		Segmentation segmentation = null;
		segmentation = doClusteringKM(seq, nbc2, nbi2, stab2, cs);
		segmentation.reInitColors(seq.getImage(0, 0));
		return segmentation;
	}

	private static Segmentation doClusteringKM(Sequence currentSequence, int nbc2, int nbi2, double stab2, int cs)
			throws SupportRegionException, SegmentationException, MaskException, NumberFormatException,
			SignatureException {
		SegmentableIcyBufferedImage img = new SegmentableIcyBufferedImage(currentSequence.getFirstImage());

		KMeans km2 = new KMeans(nbc2, nbi2, stab2);
		km2.setLogEnabled(false);

		Segmentation seg = null;

		DefaultDescriptorImpl<SegmentableIcyBufferedImage, ? extends Signature> col = null;

		ColorPixel cd = new ColorPixel(false);
		cd.setColorSpace(cs);
		col = cd;

		col.setLogEnabled(false);

		GridFactory factory = new GridFactory(GridFactory.ALGO_ONLY_PIXELS);
		factory.setLogEnabled(false);
		List<IcySupportRegion> lRegions = factory.extractRegions(img);
		IcySupportRegion[] regions = new IcySupportRegion[lRegions.size()];
		int r = 0;
		for (IcySupportRegion sr : lRegions) {
			regions[r++] = sr;
		}

		seg = doSingleClustering(img, regions, col, km2);
		return seg;
	}

	private static Segmentation doSingleClustering(SegmentableIcyBufferedImage img, IcySupportRegion[] regions,
			DefaultDescriptorImpl<SegmentableIcyBufferedImage, ? extends Signature> descriptor,
			DefaultClusteringAlgorithmImpl<VectorSignature> algo) throws SupportRegionException, SegmentationException {
		DefaultSegmentationAlgorithm<SegmentableIcyBufferedImage> segAlgo = new DefaultSegmentationAlgorithm<SegmentableIcyBufferedImage>(
				descriptor, algo);
		segAlgo.setLogEnabled(false);

		Segmentation seg = segAlgo.segment(img, regions);
		return seg;
	}

}
