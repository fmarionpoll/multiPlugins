package plugins.fmp.multiSPOTS96.series;

import java.awt.Point;
import java.awt.geom.Point2D;
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
import plugins.fmp.multitools.experiment.cages.Cage;
import plugins.fmp.multitools.experiment.spots.Spot;
import plugins.fmp.multitools.experiment.spots.SpotsArray;
import plugins.kernel.roi.roi2d.ROI2DArea;
import plugins.kernel.roi.roi2d.ROI2DPolygon;

public class DetectSpotsTools {

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

		exp.cagesArray.computeBooleanMasksForCages();
		final ROI2DArea binarizedImageRoi = binarizeImage(workimage, options);

		for (Cage cage : exp.cagesArray.cagesList) {
			if (!options.selectedIndexes.contains(cage.getProperties().getCageID()))
				continue;

			cage.spotsArray = new SpotsArray();
			int spotID = 0;
			BooleanMask2D[] blobs;
			try {
				blobs = findBlobs(binarizedImageRoi, cage.cageMask2D);
				if (blobs == null) {
					System.out.println("no blobs found for cage " + cage.getRoi().getName());
					continue;
				} else {
					System.out.println(cage.getRoi().getName() + " n blobs=" + blobs.length);
				}

				for (int i = 0; i < blobs.length; i++) {
					int npoints = blobs[i].getNumberOfPoints();
					if (npoints < 2)
						continue;

					List<Point> points;
					try {
						points = blobs[i].getConnectedContourPoints();
						if (points != null) {
							List<Point2D> points2s = points.stream()
									.map(point -> new Point2D.Double(point.getX(), point.getY()))
									.collect(Collectors.toList());
							ROI2DPolygon roi = new ROI2DPolygon(points2s);
							Spot spot = new Spot(roi);
							spot.setName(cage.getProperties().getCageID(), spotID);
							spot.getProperties().setCageID(cage.getProperties().getCageID());
							cage.spotsArray.addSpot(spot);
							spotID++;
						}
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
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
				System.out
						.println("FlyDetectTools:waitDetectCompletion - frame:" + frame + " Execution exception: " + e);
			} catch (InterruptedException e) {
				System.out.println("FlyDetectTools:waitDetectCompletion - Interrupted exception: " + e);
			}
			futuresArray.remove(f);
			frame++;
		}
	}

}
