package plugins.fmp.multitools.experiment;

import java.nio.file.attribute.FileTime;
import java.util.Arrays;

import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.tools.Logger;

public class ExperimentTimeManager {
	private FileTime firstImage_FileTime;
	private FileTime lastImage_FileTime;
	private long camImageFirst_ms = -1;
	private long camImageLast_ms = -1;
	private long camImageBin_ms = -1;
	private long[] camImages_ms = null;

	private long kymoFirst_ms = 0;
	private long kymoLast_ms = 0;
	private long kymoBin_ms = 60000;

	public void getFileIntervalsFromSeqCamData(SequenceCamData seqCamData, String imagesDirectory) {
		if (seqCamData != null) // && (camImageFirst_ms < 0 || camImageLast_ms < 0 || camImageBin_ms < 0))
			loadFileIntervalsFromSeqCamData(seqCamData, imagesDirectory);
	}

	public void loadFileIntervalsFromSeqCamData(SequenceCamData seqCamData, String imagesDirectory) {
		if (seqCamData != null) {
			seqCamData.setImagesDirectory(imagesDirectory);
			int nTotalFrames = seqCamData.getImageLoader().getNTotalFrames();
			if (nTotalFrames <= 0) {
				Logger.warn(
						"ExperimentTimeManager:loadFileIntervalsFromSeqCamData() - No frames available (nTotalFrames="
								+ nTotalFrames + ")");
				return;
			}
			firstImage_FileTime = seqCamData.getFileTimeFromStructuredName(0);
			lastImage_FileTime = seqCamData.getFileTimeFromStructuredName(nTotalFrames - 1);

			if (firstImage_FileTime != null && lastImage_FileTime != null) {
				camImageFirst_ms = firstImage_FileTime.toMillis();
				camImageLast_ms = lastImage_FileTime.toMillis();
				if (seqCamData.getImageLoader().getNTotalFrames() > 1) {
					long span_ms = camImageLast_ms - camImageFirst_ms;
					int nFrames = seqCamData.getImageLoader().getNTotalFrames();
					int nGaps = nFrames - 1;
					camImageBin_ms = span_ms / nGaps;

					long medianMs = computeMedianConsecutiveIntervalMs(seqCamData, nFrames);

					Logger.debug(String.format("ExperimentTimeManager:  %d : %d : %d : %.2f : %d : %d : %.2f", //
							span_ms, nGaps, camImageBin_ms, camImageBin_ms / 1000.0, nFrames, medianMs,
							medianMs / 1000.0));

				} else
					camImageBin_ms = 0;

				if (camImageBin_ms > 0) {
					seqCamData.getTimeManager().setBinImage_ms(camImageBin_ms);
				}

				if (camImageBin_ms == 0)
					Logger.warn("ExperimentTimeManager:loadFileIntervalsFromSeqCamData() error / file interval size");
			} else {
				Logger.warn("ExperimentTimeManager:loadFileIntervalsFromSeqCamData() error / file intervals of "
						+ seqCamData.getImagesDirectory());
			}
		}
	}

	/**
	 * Computes the median of consecutive frame-to-frame intervals from file
	 * timestamps. Robust to a few missing images (median stays near nominal
	 * interval). Returns -1 if not computed (e.g. too many frames, or missing
	 * timestamps).
	 */
	private long computeMedianConsecutiveIntervalMs(SequenceCamData seqCamData, int nFrames) {
		final int maxFramesForMedian = 10_000;
		if (nFrames < 2 || nFrames > maxFramesForMedian)
			return -1;
		long[] deltas = new long[nFrames - 1];
		long prevMs = -1;
		for (int i = 0; i < nFrames; i++) {
			FileTime ft = seqCamData.getFileTimeFromStructuredName(i);
			if (ft == null)
				return -1;
			long ms = ft.toMillis();
			if (i > 0)
				deltas[i - 1] = ms - prevMs;
			prevMs = ms;
		}
		Arrays.sort(deltas);
		int mid = deltas.length / 2;
		return (deltas.length % 2 == 1) ? deltas[mid] : (deltas[mid - 1] + deltas[mid]) / 2;
	}

	public long[] build_MsTimeIntervalsArray_From_SeqCamData_FileNamesList(SequenceCamData seqCamData,
			long firstImage_ms) {
		camImages_ms = new long[seqCamData.getImageLoader().getNTotalFrames()];
		for (int i = 0; i < seqCamData.getImageLoader().getNTotalFrames(); i++) {
			FileTime image_FileTime = seqCamData.getFileTimeFromStructuredName(i);
			long image_ms = image_FileTime.toMillis() - firstImage_ms;
			camImages_ms[i] = image_ms;
		}
		return camImages_ms;
	}

	public int findNearestIntervalWithBinarySearch(long value, int low, int high) {
		if (camImages_ms == null)
			return -1;
		int result = -1;
		if (high - low > 1) {
			int mid = (low + high) / 2;
			if (camImages_ms[mid] > value)
				result = findNearestIntervalWithBinarySearch(value, low, mid);
			else if (camImages_ms[mid] < value)
				result = findNearestIntervalWithBinarySearch(value, mid, high);
			else
				result = mid;
		} else
			result = Math.abs(value - camImages_ms[low]) < Math.abs(value - camImages_ms[high]) ? low : high;
		return result;
	}

	// -----------------------------------------

	public String getBinNameFromKymoFrameStep() {
		return Experiment.binDirectoryNameFromMs(kymoBin_ms);
	}

	public FileTime getFirstImage_FileTime() {
		return firstImage_FileTime;
	}

	public void setFirstImage_FileTime(FileTime firstImage_FileTime) {
		this.firstImage_FileTime = firstImage_FileTime;
	}

	public FileTime getLastImage_FileTime() {
		return lastImage_FileTime;
	}

	public void setLastImage_FileTime(FileTime lastImage_FileTime) {
		this.lastImage_FileTime = lastImage_FileTime;
	}

	public long getCamImageFirst_ms() {
		return camImageFirst_ms;
	}

	public void setCamImageFirst_ms(long camImageFirst_ms) {
		this.camImageFirst_ms = camImageFirst_ms;
	}

	public long getCamImageLast_ms() {
		return camImageLast_ms;
	}

	public void setCamImageLast_ms(long camImageLast_ms) {
		this.camImageLast_ms = camImageLast_ms;
	}

	public long getCamImageBin_ms() {
		return camImageBin_ms;
	}

	public void setCamImageBin_ms(long camImageBin_ms) {
		this.camImageBin_ms = camImageBin_ms;
	}

	public long[] getCamImages_ms() {
		return camImages_ms;
	}

	public void setCamImages_ms(long[] camImages_ms) {
		this.camImages_ms = camImages_ms;
	}

	public long getKymoFirst_ms() {
		return kymoFirst_ms;
	}

	public void setKymoFirst_ms(long kymoFirst_ms) {
		this.kymoFirst_ms = kymoFirst_ms;
	}

	public long getKymoLast_ms() {
		return kymoLast_ms;
	}

	public void setKymoLast_ms(long kymoLast_ms) {
		this.kymoLast_ms = kymoLast_ms;
	}

	public long getKymoBin_ms() {
		return kymoBin_ms;
	}

	public void setKymoBin_ms(long kymoBin_ms) {
		this.kymoBin_ms = kymoBin_ms;
	}

}
