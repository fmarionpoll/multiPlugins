package plugins.fmp.multitools.service;

import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import icy.gui.frame.progress.ProgressFrame;
import icy.roi.ROI2D;
import icy.image.IcyBufferedImage;
import icy.sequence.Sequence;
import icy.system.SystemUtil;
import icy.system.thread.Processor;
import icy.type.DataType;
import icy.type.collection.array.Array1DUtil;
import loci.formats.FormatException;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.GenerationMode;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cages.Cages;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.Comparators;
import plugins.fmp.multitools.tools.Logger;

/**
 * Builds one stacked vertical-line kymograph per cage from camera frames (multiSPOTS96
 * experimental). Spatial averaging matches {@link KymographBuilder} capillary strips
 * (horizontal neighborhood at each sample point). Stacking uses one horizontal strip per
 * cage spot in name order, including a one-pixel placeholder row when a spot ROI has no valid bounds.
 */
public class CageSpotKymographBuilder {

	private static final class CageKymoPlan {
		final List<Spot> spots;
		final int stackHeight;
		final String fileBaseName;
		final ArrayList<int[]> channelBuffers;
		final int imageWidth;

		CageKymoPlan(List<Spot> spots, int stackHeight, String fileBaseName, ArrayList<int[]> channelBuffers,
				int imageWidth) {
			this.spots = spots;
			this.stackHeight = stackHeight;
			this.fileBaseName = fileBaseName;
			this.channelBuffers = channelBuffers;
			this.imageWidth = imageWidth;
		}
	}

	private static final class PlansBundle {
		final List<CageKymoPlan> plans;
		final int globalHeight;

		PlansBundle(List<CageKymoPlan> plans, int globalHeight) {
			this.plans = plans;
			this.globalHeight = globalHeight;
		}
	}

	public boolean buildCageSpotKymographs(Experiment exp, BuildSeriesOptions options) {
		Cages cages = exp.getCages();
		Spots spots = exp.getSpots();
		if (cages == null || cages.cagesList == null || cages.cagesList.isEmpty()) {
			Logger.warn("CageSpotKymographBuilder: no cages");
			return false;
		}
		if (spots == null) {
			Logger.warn("CageSpotKymographBuilder: no spots");
			return false;
		}

		SequenceCamData seqCamData = exp.getSeqCamData();
		if (seqCamData == null || seqCamData.getSequence() == null) {
			Logger.warn("CageSpotKymographBuilder: no camera sequence");
			return false;
		}

		SequenceLoaderService loader = new SequenceLoaderService();
		long camReferenceMs = seqCamData.getFirstValidFrameEpochMs();
		if (camReferenceMs < 0) {
			camReferenceMs = exp.getCamImageFirst_ms();
		}
		if (camReferenceMs < 0) {
			camReferenceMs = 0;
		}
		exp.build_MsTimeIntervalsArray_From_SeqCamData_FileNamesList(camReferenceMs);

		// Use experiment kymo window (set by BuildKymosFromCageSpots.getTimeLimitsOfSequence). The
		// sequence {@code TimeManager} bin last/first is often never updated for SPOTS workflows,
		// unlike multiCAFE intervals UI — relying on it yields last_ms==0 and broken frame lookup.
		long first_ms = exp.getKymoFirst_ms();
		long last_ms = exp.getKymoLast_ms();
		if (last_ms <= first_ms) {
			Logger.warn("CageSpotKymographBuilder: invalid kymo window on experiment; falling back to sequence / camera");
			first_ms = 0;
			last_ms = seqCamData.getTimeManager().getBinLast_ms();
			if (last_ms <= first_ms && exp.getCamImageLast_ms() > exp.getCamImageFirst_ms()) {
				last_ms = exp.getCamImageLast_ms() - exp.getCamImageFirst_ms();
			}
			if (last_ms <= first_ms) {
				last_ms = first_ms + 1;
			}
		}
		long step_ms = exp.getKymoBin_ms();

		int nTotalFrames = seqCamData.getImageLoader().getNTotalFrames();
		int lowIndex = 0;
		int highIndex = (nTotalFrames > 0) ? (nTotalFrames - 1) : 0;
		if (highIndex < lowIndex) {
			highIndex = lowIndex;
		}
		long[] camImages_ms = exp.getCamImages_ms();
		if (camImages_ms != null && highIndex < camImages_ms.length) {
			last_ms = Math.min(last_ms, camImages_ms[highIndex]);
		}

		int expectedWidth = (step_ms > 0) ? Math.max(1, 1 + (int) Math.ceil((last_ms - first_ms) / (double) step_ms))
				: 1;

		Sequence seq = seqCamData.getSequence();
		final int refSizex = seq.getSizeX();
		final int refSizey = seq.getSizeY();
		final int kymoSizeC = Math.max(1, seq.getSizeC());

		PlansBundle bundle = buildPlans(cages, spots, expectedWidth, kymoSizeC, refSizex, refSizey);
		List<CageKymoPlan> plans = bundle.plans;
		int globalHeight = bundle.globalHeight;
		if (plans.isEmpty()) {
			Logger.warn("CageSpotKymographBuilder: no cages with spots to process");
			return false;
		}

		/*
		 * Columns must be processed sequentially: queuing one async task per column
		 * retains every loaded frame (and each fillColumn allocates full int[] rasters)
		 * until all futures complete — long time windows exhaust heap. One frame at a
		 * time keeps peak memory ~O(1) in column count.
		 */
		ProgressFrame progress = new ProgressFrame("Cage kymographs");
		int sourceLastImageIndex = nTotalFrames;
		for (int col = 0; col < expectedWidth; col++) {
			long ii_ms = first_ms + col * step_ms;
			int sourceImageIndex = exp.findNearestIntervalWithBinarySearch(ii_ms, lowIndex, highIndex);
			if (sourceImageIndex < 0) {
				continue;
			}

			progress.setMessage("Column " + (col + 1) + " / " + expectedWidth + " (frame " + (sourceImageIndex + 1)
					+ " / " + sourceLastImageIndex + ")");

			IcyBufferedImage sourceImage = null;
			try {
				sourceImage = loader.imageIORead(seqCamData.getFileNameFromImageList(sourceImageIndex));
				for (CageKymoPlan plan : plans) {
					fillColumn(plan, sourceImage, col, globalHeight, refSizex, refSizey, kymoSizeC, options);
				}
			} finally {
				sourceImage = null;
			}
		}
		progress.close();

		if (options.doCreateBinDir) {
			String previousBinDir = exp.getBinSubDirectory();
			String binDir = KymographBuilder.chooseWritableBinSubDirectoryForKymograph(exp,
					exp.getBinNameFromKymoFrameStep(), options);
			exp.setBinSubDirectory(binDir);
			exp.setGenerationMode(GenerationMode.KYMOGRAPH);
			exp.saveBinDescription(binDir);
			if (previousBinDir != null && !previousBinDir.equals(binDir)) {
				KymographBuilder.copyMeasuresBetweenBinsForExperiment(exp, previousBinDir, binDir);
			}
		}

		String directory = exp.getDirectoryToSaveResults();
		if (directory == null) {
			Logger.warn("CageSpotKymographBuilder: no results directory (set experiment folder or open descriptors); "
					+ "resultsDirectory=" + exp.getResultsDirectory());
			return false;
		}

		exp.releaseKymographSequence();
		if (SystemUtil.isWindows()) {
			try {
				Thread.sleep(200L);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		exportPlans(plans, globalHeight, kymoSizeC, directory);
		CageKymographStripLayoutCsv.write(directory, cages, spots, refSizex, refSizey, expectedWidth, first_ms,
				last_ms, step_ms);
		return true;
	}

	private PlansBundle buildPlans(Cages cages, Spots allSpots, int imageWidth, int kymoSizeC, int refSizex,
			int refSizey) {
		List<CageKymoPlan> out = new ArrayList<>();
		int idx = 0;
		for (Cage cage : cages.cagesList) {
			List<Spot> raw = cage.getSpotList(allSpots);
			if (raw.isEmpty()) {
				idx++;
				continue;
			}
			ArrayList<Spot> sorted = new ArrayList<>(raw);
			Collections.sort(sorted, new Comparators.Spot_Name());

			int stackHeight = 0;
			for (Spot s : sorted) {
				Rectangle b = getSpotBounds(s, refSizex, refSizey);
				if (b == null || b.height <= 0 || b.width <= 0) {
					stackHeight += 1;
				} else {
					stackHeight += b.height;
				}
			}
			if (stackHeight <= 0) {
				Logger.warn("CageSpotKymographBuilder: cage " + cage.prop.getCageID() + " has no valid spot bounds");
				idx++;
				continue;
			}
			int cid = cage.prop.getCageID();
			String fileBase = "kymocage_" + (cid >= 0 ? String.valueOf(cid) : "i" + idx);
			ArrayList<int[]> buffers = new ArrayList<>(kymoSizeC);
			out.add(new CageKymoPlan(sorted, stackHeight, fileBase, buffers, imageWidth));
			idx++;
		}

		int globalHeight = 0;
		for (CageKymoPlan p : out) {
			if (p.stackHeight > globalHeight) {
				globalHeight = p.stackHeight;
			}
		}
		int len = imageWidth * globalHeight;
		for (CageKymoPlan p : out) {
			p.channelBuffers.clear();
			for (int c = 0; c < kymoSizeC; c++) {
				p.channelBuffers.add(new int[len]);
			}
		}
		return new PlansBundle(out, globalHeight);
	}

	private static Rectangle getSpotBounds(Spot spot, int refW, int refH) {
		if (spot == null) {
			return null;
		}
		ROI2D roi = spot.getRoi();
		if (roi == null) {
			return null;
		}
		Rectangle b = roi.getBounds();
		if (b == null) {
			return null;
		}
		int x0 = Math.max(0, b.x);
		int y0 = Math.max(0, b.y);
		int x1 = Math.min(refW - 1, b.x + b.width - 1);
		int y1 = Math.min(refH - 1, b.y + b.height - 1);
		if (x1 < x0 || y1 < y0) {
			return null;
		}
		return new Rectangle(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
	}

	private void fillColumn(CageKymoPlan plan, IcyBufferedImage sourceImage, int kymographColumn, int globalHeight,
			int refSizex, int refSizey, int kymoSizeC, BuildSeriesOptions options) {
		int diskR = Math.max(0, options.diskRadius);
		int imgW = sourceImage.getWidth();
		int imgH = sourceImage.getHeight();
		if (imgW != refSizex || imgH != refSizey) {
			Logger.warn("CageSpotKymographBuilder: image size " + imgW + "x" + imgH + " differs from sequence "
					+ refSizex + "x" + refSizey);
		}
		int W = plan.imageWidth;
		final int srcSizeC = Math.max(1, sourceImage.getSizeC());
		int[] src0 = Array1DUtil.arrayToIntArray(sourceImage.getDataXY(0), sourceImage.isSignedDataType());
		int[] src1 = srcSizeC > 1
				? Array1DUtil.arrayToIntArray(sourceImage.getDataXY(1), sourceImage.isSignedDataType())
				: null;
		int[] src2 = srcSizeC > 2
				? Array1DUtil.arrayToIntArray(sourceImage.getDataXY(2), sourceImage.isSignedDataType())
				: null;

		int row = 0;
		for (Spot spot : plan.spots) {
			Rectangle b = getSpotBounds(spot, refSizex, refSizey);
			if (b == null || b.height <= 0 || b.width <= 0) {
				int dst = row * W + kymographColumn;
				for (int ch = 0; ch < plan.channelBuffers.size(); ch++) {
					plan.channelBuffers.get(ch)[dst] = 0;
				}
				row++;
				continue;
			}
			int cx = (int) Math.round(b.getCenterX());

			for (int y = b.y; y <= b.y + b.height - 1 && row < globalHeight; y++) {
				ArrayList<int[]> mask = horizontalStripMask(cx, y, diskR, refSizex, refSizey);
				if (mask.isEmpty()) {
					copyPreviousColumn(plan.channelBuffers, kymographColumn, row, W, globalHeight, kymoSizeC);
				} else {
					long sum0 = 0;
					long sum1 = 0;
					long sum2 = 0;
					for (int[] m : mask) {
						int pix = m[0] + m[1] * imgW;
						sum0 += src0[pix];
						if (src1 != null) {
							sum1 += src1[pix];
						}
						if (src2 != null) {
							sum2 += src2[pix];
						}
					}
					int n = mask.size();
					int dst = row * W + kymographColumn;
					if (!plan.channelBuffers.isEmpty() && n > 0) {
						plan.channelBuffers.get(0)[dst] = (int) (sum0 / n);
					}
					if (kymoSizeC > 1 && src1 != null && plan.channelBuffers.size() > 1 && n > 0) {
						plan.channelBuffers.get(1)[dst] = (int) (sum1 / n);
					}
					if (kymoSizeC > 2 && src2 != null && plan.channelBuffers.size() > 2 && n > 0) {
						plan.channelBuffers.get(2)[dst] = (int) (sum2 / n);
					}
				}
				row++;
			}
		}
		while (row < globalHeight) {
			copyPreviousColumn(plan.channelBuffers, kymographColumn, row, W, globalHeight, kymoSizeC);
			row++;
		}
	}

	private static void copyPreviousColumn(ArrayList<int[]> channelBuffers, int col, int row, int W, int globalHeight,
			int kymoSizeC) {
		if (col <= 0 || row < 0 || row >= globalHeight) {
			return;
		}
		int dst = row * W + col;
		int src = row * W + (col - 1);
		int nch = Math.min(kymoSizeC, channelBuffers.size());
		for (int ch = 0; ch < nch; ch++) {
			channelBuffers.get(ch)[dst] = channelBuffers.get(ch)[src];
		}
	}

	private void exportPlans(List<CageKymoPlan> plans, int globalHeight, int kymoSizeC, String directory) {
		if (SystemUtil.isWindows()) {
			for (CageKymoPlan p : plans) {
				IcyBufferedImage img = new IcyBufferedImage(p.imageWidth, globalHeight, kymoSizeC, DataType.UBYTE);
				boolean signed = img.isSignedDataType();
				for (int ch = 0; ch < Math.min(kymoSizeC, p.channelBuffers.size()); ch++) {
					Object dest = img.getDataXY(ch);
					Array1DUtil.intArrayToSafeArray(p.channelBuffers.get(ch), 0, dest, 0, -1, signed, signed);
					img.setDataXY(ch, dest);
				}
				File outFile = new File(directory + File.separator + p.fileBaseName + ".tiff");
				try {
					KymographBuilder.saveKymographTiffSafely(img, outFile);
				} catch (FormatException | IOException e) {
					Logger.error("CageSpotKymographBuilder: export failed " + outFile, e);
				}
			}
			return;
		}

		Processor processor = new Processor(SystemUtil.getNumberOfCPUs());
		processor.setThreadName("exportCageKymograph");
		processor.setPriority(Processor.NORM_PRIORITY);
		ArrayList<Future<?>> tasks = new ArrayList<>();

		for (CageKymoPlan plan : plans) {
			final CageKymoPlan p = plan;
			tasks.add(processor.submit(() -> {
				IcyBufferedImage img = new IcyBufferedImage(p.imageWidth, globalHeight, kymoSizeC, DataType.UBYTE);
				boolean signed = img.isSignedDataType();
				for (int ch = 0; ch < Math.min(kymoSizeC, p.channelBuffers.size()); ch++) {
					Object dest = img.getDataXY(ch);
					Array1DUtil.intArrayToSafeArray(p.channelBuffers.get(ch), 0, dest, 0, -1, signed, signed);
					img.setDataXY(ch, dest);
				}
				File outFile = new File(directory + File.separator + p.fileBaseName + ".tiff");
				try {
					KymographBuilder.saveKymographTiffSafely(img, outFile);
				} catch (FormatException | IOException e) {
					Logger.error("CageSpotKymographBuilder: export failed " + outFile, e);
				}
			}));
		}
		waitFutures(processor, tasks);
	}

	private static void waitFutures(Processor processor, ArrayList<Future<?>> tasks) {
		while (!tasks.isEmpty()) {
			Future<?> f = tasks.remove(tasks.size() - 1);
			try {
				f.get();
			} catch (ExecutionException e) {
				Logger.error("CageSpotKymographBuilder: task failed", e);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				Logger.warn("CageSpotKymographBuilder: interrupted");
			}
		}
	}

	/** Same geometry as {@link KymographBuilder} capillary sampling (horizontal strip at fixed y). */
	private static ArrayList<int[]> horizontalStripMask(int cx, int y, int diskRadius, int sizex, int sizey) {
		ArrayList<int[]> maskAroundPixel = new ArrayList<>();
		double m1 = cx;
		double m2 = y;
		double radiusSquared = (double) diskRadius * diskRadius;
		int minX = clip(cx - diskRadius, 0, sizex - 1);
		int maxX = clip(cx + diskRadius, minX, sizex - 1);
		int minY = y;
		int maxY = y;
		for (int x = minX; x <= maxX; x++) {
			for (int yy = minY; yy <= maxY; yy++) {
				double dx = x - m1;
				double dy = yy - m2;
				if (dx * dx + dy * dy <= radiusSquared) {
					maskAroundPixel.add(new int[] { x, yy });
				}
			}
		}
		return maskAroundPixel;
	}

	private static int clip(int v, int min, int max) {
		if (v < min) {
			return min;
		}
		if (v > max) {
			return max;
		}
		return v;
	}
}
