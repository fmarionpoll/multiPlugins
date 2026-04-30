package plugins.fmp.multitools.series;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import icy.gui.frame.progress.ProgressFrame;
import icy.image.IcyBufferedImage;
import icy.roi.BooleanMask2D;
import icy.sequence.Sequence;
import icy.system.thread.Processor;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.ids.SpotID;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.Logger;
import plugins.kernel.roi.roi2d.ROI2DArea;
import plugins.kernel.roi.roi2d.ROI2DPolygon;

public class DetectSpotsTools {

	private static volatile boolean codeSourceLogged = false;
	private static volatile boolean detectParamsLogged = false;
	private static volatile boolean firstBlobLogged = false;

	private static void logCodeSourceOnce() {
		if (codeSourceLogged)
			return;
		codeSourceLogged = true;
		try {
			String where = String.valueOf(DetectSpotsTools.class.getProtectionDomain().getCodeSource().getLocation());
			String user = System.getProperty("user.name");
			String java = System.getProperty("java.version");
			final String msg = "DetectSpotsTools loaded from: " + where + " | user=" + user + " | java=" + java;

			// Logger output depends on ICY / launch config (sometimes not visible).
			// Print to stdout as a fallback so users can always see it.
			System.out.println(msg);
			Logger.info(msg);
		} catch (Throwable t) {
			// never fail detection because of logging
		}
	}

	private static void logDetectParamsOnce(BuildSeriesOptions options, IcyBufferedImage workimage) {
		if (detectParamsLogged)
			return;
		detectParamsLogged = true;
		try {
			Rectangle b = (workimage != null) ? workimage.getBounds() : null;
			String bounds = (b != null) ? (b.x + "," + b.y + " " + b.width + "x" + b.height) : "null";
			String msg = "DetectSpotsTools params:"
					+ " threshold=" + (options != null ? options.threshold : "null")
					+ " btrackWhite=" + (options != null ? options.btrackWhite : "null")
					+ " videoChannel=" + (options != null ? options.videoChannel : "null")
					+ " transformop=" + (options != null ? String.valueOf(options.transformop) : "null")
					+ " workImageBounds=" + bounds;
			System.out.println(msg);
		} catch (Throwable t) {
			// ignore
		}
	}

	private static List<Point2D> toImageCoordinates(BooleanMask2D mask, List<Point> contourPoints) {
		if (mask == null || contourPoints == null || contourPoints.isEmpty()) {
			return null;
		}

		// ICY has had different behaviors here (depending on version / packaging):
		// - sometimes contour points are in absolute image coordinates
		// - sometimes they are relative to mask.bounds (0..w, 0..h)
		// Make it robust by detecting the coordinate frame.
		final Rectangle b = mask.bounds;
		if (b == null) {
			return contourPoints.stream()
					.map(p -> new Point2D.Double(p.getX(), p.getY()))
					.collect(Collectors.toList());
		}

		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
		for (Point p : contourPoints) {
			if (p == null)
				continue;
			minX = Math.min(minX, p.x);
			minY = Math.min(minY, p.y);
			maxX = Math.max(maxX, p.x);
			maxY = Math.max(maxY, p.y);
		}

		// If points look "relative" (fit within [0..w)×[0..h)), translate them.
		// Use <= to handle edge pixels at (w-1)/(h-1).
		final boolean looksRelative = (minX >= 0 && minY >= 0 && maxX <= (b.width - 1) && maxY <= (b.height - 1));
		final int ox = looksRelative ? b.x : 0;
		final int oy = looksRelative ? b.y : 0;

		if (!firstBlobLogged) {
			firstBlobLogged = true;
			try {
				Rectangle2D ob = mask.getOptimizedBounds();
				String mb = (b.x + "," + b.y + " " + b.width + "x" + b.height);
				String obb = (ob != null)
						? (String.format("%.1f,%.1f %.1fx%.1f", ob.getX(), ob.getY(), ob.getWidth(), ob.getHeight()))
						: "null";
				String msg = "DetectSpotsTools firstBlob:"
						+ " maskBounds=" + mb
						+ " optBounds=" + obb
						+ " contourMinMax=(" + minX + "," + minY + ")-(" + maxX + "," + maxY + ")"
						+ " looksRelative=" + looksRelative
						+ " offset=(" + ox + "," + oy + ")";
				System.out.println(msg);
			} catch (Throwable t) {
				// ignore
			}
		}

		return contourPoints.stream()
				.filter(p -> p != null)
				.map(p -> new Point2D.Double(p.x + ox, p.y + oy))
				.collect(Collectors.toList());
	}

	/**
	 * Drops all spots whose cage ID is in {@code options.selectedIndexes} and clears
	 * those cages' spot-ID lists. Uses cage ID on each {@link Spot}'s properties so
	 * orphans (not listed on {@link Cage#getSpotIDs()}) are removed too — avoids
	 * accumulating spots when re-running detection on an already analyzed experiment.
	 */
	private void clearExistingSpotsForSelectedCages(Experiment exp, BuildSeriesOptions options, Spots allSpots) {
		if (options.selectedIndexes == null || options.selectedIndexes.isEmpty()) {
			return;
		}
		ArrayList<Spot> toRemove = new ArrayList<>();
		for (Spot spot : allSpots.getSpotList()) {
			if (spot == null || spot.getProperties() == null) {
				continue;
			}
			if (options.selectedIndexes.contains(spot.getProperties().getCageID())) {
				toRemove.add(spot);
			}
		}
		for (Spot spot : toRemove) {
			allSpots.removeSpot(spot);
		}
		for (Cage cage : exp.getCages().cagesList) {
			if (options.selectedIndexes.contains(cage.getCageID())) {
				cage.getSpotIDs().clear();
			}
		}
	}

	BooleanMask2D[] findBlobs(ROI2DArea binarizedImageRoi, BooleanMask2D cageMask) throws InterruptedException {
		if (cageMask == null)
			return null;

		ROI2DArea roi = new ROI2DArea(binarizedImageRoi.getBooleanMask(true).getIntersection(cageMask));
		BooleanMask2D roiBooleanMask = roi.getBooleanMask(true);
		return roiBooleanMask.getComponents();
	}

	public ROI2DArea binarizeImage(IcyBufferedImage img, BuildSeriesOptions options) {
		if (img == null)
			return null;
		boolean[] mask = new boolean[img.getSizeX() * img.getSizeY()];
		if (options.btrackWhite) {
			byte[] arrayRed = img.getDataXYAsByte(0);
			byte[] arrayGreen = img.getDataXYAsByte(1);
			byte[] arrayBlue = img.getDataXYAsByte(2);
			for (int i = 0; i < arrayRed.length; i++) {
				float r = (arrayRed[i] & 0xFF);
				float g = (arrayGreen[i] & 0xFF);
				float b = (arrayBlue[i] & 0xFF);
				float intensity = (r + g + b) / 3f;
				mask[i] = (intensity) > options.threshold;
			}
		} else {
			byte[] arrayChan = img.getDataXYAsByte(options.videoChannel);
			for (int i = 0; i < arrayChan.length; i++)
				mask[i] = (((int) arrayChan[i]) & 0xFF) < options.threshold;
		}
		BooleanMask2D bmask = new BooleanMask2D(img.getBounds(), mask);
		return new ROI2DArea(bmask);
	}

	public void findSpots(Experiment exp, Sequence seqNegative, BuildSeriesOptions options, IcyBufferedImage workimage)
			throws InterruptedException {

		logCodeSourceOnce();
		logDetectParamsOnce(options, workimage);
		exp.getCages().computeBooleanMasksForCages();
		final ROI2DArea binarizedImageRoi = binarizeImage(workimage, options);

		Spots allSpots = exp.getSpots();
		clearExistingSpotsForSelectedCages(exp, options, allSpots);

		for (Cage cage : exp.getCages().cagesList) {
			if (!options.selectedIndexes.contains(cage.getProperties().getCageID()))
				continue;

			int cageID = cage.getCageID();
			int cagePosition = 0;
			BooleanMask2D[] blobs;
			try {
				blobs = findBlobs(binarizedImageRoi, cage.cageMask2D);
				if (blobs == null) {
					Logger.info("no blobs found for cage " + cage.getRoi().getName());
					continue;
				}

				for (int i = 0; i < blobs.length; i++) {
					int npoints = blobs[i].getNumberOfPoints();
					if (npoints < 2)
						continue;

					List<Point> points = blobs[i].getConnectedContourPoints();
					if (points == null || points.size() < 3) {
						continue;
					}
					List<Point2D> points2s = toImageCoordinates(blobs[i], points);
					if (points2s == null || points2s.size() < 3) {
						continue;
					}
					ROI2DPolygon roi = new ROI2DPolygon(points2s);

					Spot spot = new Spot(roi);
					int uniqueSpotID = allSpots.getNextUniqueSpotID();
					SpotID spotUniqueID = new SpotID(uniqueSpotID);
					spot.setSpotUniqueID(spotUniqueID);

					spot.setName(cageID, cagePosition);
					spot.getProperties().setCageID(cageID);
					spot.getProperties().setCagePosition(cagePosition);
					allSpots.addSpot(spot);
					// Add ID to cage
					cage.getSpotIDs().add(spotUniqueID);
					cagePosition++;
				}
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	protected void waitDetectCompletion(Processor processor, ArrayList<Future<?>> futuresArray,
			ProgressFrame progressBar) {
		int frame = 1;
		int nframes = futuresArray.size();

		while (!futuresArray.isEmpty()) {
			final Future<?> f = futuresArray.get(futuresArray.size() - 1);
			if (progressBar != null)
				progressBar.setMessage("Analyze frame: " + (frame) + "//" + nframes);
			try {
				f.get();
			} catch (ExecutionException e) {
				Logger.warn("FlyDetectTools:waitDetectCompletion - frame:" + frame + " Execution exception: " + e);
			} catch (InterruptedException e) {
				Logger.warn("FlyDetectTools:waitDetectCompletion - Interrupted exception: " + e);
			}
			futuresArray.remove(f);
			frame++;
		}
	}

}
