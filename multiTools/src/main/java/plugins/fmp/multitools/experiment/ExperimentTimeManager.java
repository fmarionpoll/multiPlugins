package plugins.fmp.multitools.experiment;

import java.nio.file.attribute.FileTime;

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
				Logger.warn("ExperimentTimeManager:loadFileIntervalsFromSeqCamData() - No frames available (nTotalFrames="
						+ nTotalFrames + ")");
				return;
			}
			firstImage_FileTime = seqCamData.getFileTimeFromStructuredName(0);
			lastImage_FileTime = seqCamData.getFileTimeFromStructuredName(nTotalFrames - 1);

			if (firstImage_FileTime != null && lastImage_FileTime != null) {
				camImageFirst_ms = firstImage_FileTime.toMillis();
				camImageLast_ms = lastImage_FileTime.toMillis();
				if (seqCamData.getImageLoader().getNTotalFrames() > 1)
					camImageBin_ms = (camImageLast_ms - camImageFirst_ms)
							/ (seqCamData.getImageLoader().getNTotalFrames() - 1);
				else
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
