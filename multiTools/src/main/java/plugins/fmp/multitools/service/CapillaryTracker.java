package plugins.fmp.multitools.service;

import java.awt.Rectangle;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.vecmath.Vector2d;

import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;
import icy.roi.ROI2D;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.tools.ROI2D.AlongT;
import plugins.fmp.multitools.tools.ROI2D.ROI2DUtilities;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.registration.GaspardRigidRegistration;

/**
 * Tracks capillary ROIs across frames using local phase correlation on cropped
 * regions. Translation-only; no rotation. For use by TrackCapillaries dialog.
 */
public class CapillaryTracker {

	public static final int DEFAULT_CROP_MARGIN_PX = 25;
	public static final int CHANNEL = 0;

	/**
	 * Tracks a capillary from t0 to tEnd (inclusive). Uses ROI at t0 as reference;
	 * for each subsequent frame, crops around the previous ROI, runs phase
	 * correlation, and translates the ROI.
	 *
	 * @param seqCamData camera sequence with image loader and file list
	 * @param cap       capillary to track
	 * @param t0        reference frame (ROI from getAlongTAtT(t0) is the seed)
	 * @param tEnd      last frame to track (inclusive)
	 * @param marginPx  crop margin around ROI bounds
	 * @return map of frame index -> tracked ROI (includes t0)
	 */
	public Map<Long, ROI2D> track(Capillary cap, SequenceCamData seqCamData, int t0, int tEnd, int marginPx) {
		Map<Long, ROI2D> result = new LinkedHashMap<>();
		SequenceLoaderService loadSvc = new SequenceLoaderService();

		AlongT at0 = cap.getAlongTAtT(t0);
		if (at0 == null || at0.getRoi() == null) {
			Logger.warn("CapillaryTracker: no ROI at t0=" + t0);
			return result;
		}

		ROI2D roiPrev = (ROI2D) at0.getRoi().getCopy();
		result.put((long) t0, roiPrev);

		String pathPrev = seqCamData.getFileNameFromImageList(t0);
		IcyBufferedImage imgPrev = pathPrev != null ? loadSvc.imageIORead(pathPrev) : null;
		if (imgPrev == null) {
			Logger.warn("CapillaryTracker: cannot load image at t=" + t0);
			return result;
		}

		int step = tEnd >= t0 ? 1 : -1;
		for (int t = t0 + step; (step > 0 && t <= tEnd) || (step < 0 && t >= tEnd); t += step) {
			String pathCurr = seqCamData.getFileNameFromImageList(t);
			if (pathCurr == null)
				continue;
			IcyBufferedImage imgCurr = loadSvc.imageIORead(pathCurr);
			if (imgCurr == null)
				continue;

			Rectangle cropRect = cropRectForRoi(roiPrev, imgCurr.getWidth(), imgCurr.getHeight(), marginPx);
			if (cropRect.width < 16 || cropRect.height < 16) {
				Logger.debug("CapillaryTracker: crop too small at t=" + t + ", skipping");
				ROI2D copy = ROI2DUtilities.translateROI(roiPrev, 0, 0);
				if (copy != null) {
					copy.setName(roiPrev.getName());
					result.put((long) t, copy);
					roiPrev = copy;
				}
				imgPrev = imgCurr;
				continue;
			}

			IcyBufferedImage cropPrev = IcyBufferedImageUtil.getSubImage(imgPrev, cropRect.x, cropRect.y,
					cropRect.height, cropRect.width);
			IcyBufferedImage cropCurr = IcyBufferedImageUtil.getSubImage(imgCurr, cropRect.x, cropRect.y,
					cropRect.height, cropRect.width);

			Vector2d translation;
			try {
				translation = GaspardRigidRegistration.findTranslation2D(cropCurr, CHANNEL, cropPrev, CHANNEL);
			} catch (Exception e) {
				Logger.warn("CapillaryTracker: findTranslation2D failed at t=" + t + ": " + e.getMessage());
				ROI2D copy = (ROI2D) roiPrev.getCopy();
				copy.setName(roiPrev.getName());
				result.put((long) t, copy);
				roiPrev = copy;
				imgPrev = imgCurr;
				continue;
			}

			double dx = -translation.x;
			double dy = -translation.y;
			ROI2D roiTranslated = ROI2DUtilities.translateROI(roiPrev, dx, dy);
			if (roiTranslated != null) {
				roiTranslated.setName(roiPrev.getName());
				result.put((long) t, roiTranslated);
				roiPrev = roiTranslated;
			}

			imgPrev = imgCurr;
		}

		return result;
	}

	/**
	 * Convenience overload with default margin.
	 */
	public Map<Long, ROI2D> track(Capillary cap, SequenceCamData seqCamData, int t0, int tEnd) {
		return track(cap, seqCamData, t0, tEnd, DEFAULT_CROP_MARGIN_PX);
	}

	/**
	 * Tracks one frame step: given ROI at t-1 and images at t-1 and t, returns ROI
	 * at t. For frame-by-frame processing where images are loaded once and shared.
	 */
	public ROI2D trackOneFrame(ROI2D roiPrev, IcyBufferedImage imgPrev, IcyBufferedImage imgCurr) {
		return trackOneFrame(roiPrev, imgPrev, imgCurr, DEFAULT_CROP_MARGIN_PX);
	}

	public ROI2D trackOneFrame(ROI2D roiPrev, IcyBufferedImage imgPrev, IcyBufferedImage imgCurr, int marginPx) {
		if (roiPrev == null || imgPrev == null || imgCurr == null)
			return null;
		int w = imgCurr.getWidth();
		int h = imgCurr.getHeight();
		Rectangle cropRect = cropRectForRoi(roiPrev, w, h, marginPx);
		if (cropRect.width < 16 || cropRect.height < 16)
			return (ROI2D) roiPrev.getCopy();
		IcyBufferedImage cropPrev = IcyBufferedImageUtil.getSubImage(imgPrev, cropRect.x, cropRect.y, cropRect.height,
				cropRect.width);
		IcyBufferedImage cropCurr = IcyBufferedImageUtil.getSubImage(imgCurr, cropRect.x, cropRect.y, cropRect.height,
				cropRect.width);
		Vector2d translation;
		try {
			translation = GaspardRigidRegistration.findTranslation2D(cropCurr, CHANNEL, cropPrev, CHANNEL);
		} catch (Exception e) {
			return (ROI2D) roiPrev.getCopy();
		}
		double dx = -translation.x;
		double dy = -translation.y;
		ROI2D roiTranslated = ROI2DUtilities.translateROI(roiPrev, dx, dy);
		if (roiTranslated != null)
			roiTranslated.setName(roiPrev.getName());
		return roiTranslated;
	}

	private Rectangle cropRectForRoi(ROI2D roi, int imgW, int imgH, int margin) {
		Rectangle bounds = roi.getBounds();
		int w = Math.max(32, bounds.width + 2 * margin);
		int h = Math.max(32, bounds.height + 2 * margin);
		int x = Math.max(0, bounds.x - margin);
		int y = Math.max(0, bounds.y - margin);
		if (x + w > imgW)
			x = Math.max(0, imgW - w);
		if (y + h > imgH)
			y = Math.max(0, imgH - h);
		w = Math.min(w, imgW - x);
		h = Math.min(h, imgH - y);
		return new Rectangle(x, y, w, h);
	}
}
