package plugins.fmp.multitools.experiment.sequence;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import icy.gui.dialog.MessageDialog;
import icy.roi.ROI;
import icy.roi.ROI2D;
import icy.sequence.Sequence;
import icy.type.geom.Polyline2D;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillaries.CapillariesKymosMapper;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryMeasure;
import plugins.fmp.multitools.tools.Comparators;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.ROI2D.ROI2DUtilities;
import plugins.kernel.roi.roi2d.ROI2DPolyLine;

/**
 * Capillary measure ROIs on the kymograph {@link Sequence}: validate, transfer to/from
 * {@link Capillaries}, and sync the ICY sequence with the model. Extracted from
 * {@link SequenceKymos} (Phase C).
 */
public final class CapillaryKymographMeasureBridge {

	private final SequenceKymos kymos;

	public CapillaryKymographMeasureBridge(SequenceKymos kymos) {
		this.kymos = kymos;
	}

	public ImageProcessingResult validateROIs() {
		long startTime = System.currentTimeMillis();
		int processed = 0;
		int failed = 0;

		List<ROI2D> roiList = kymos.getSequence().getROI2Ds();
		int sequenceWidth = kymos.getSequence().getWidth();

		for (ROI2D roi : roiList) {
			if (!(roi instanceof ROI2DPolyLine)) {
				continue;
			}

			try {
				if (roi.getName() != null && roi.getName().contains("level")) {
					ROI2DUtilities.interpolateMissingPointsAlongXAxis((ROI2DPolyLine) roi, sequenceWidth);
					processed++;
					continue;
				}
				if (roi.getName() != null && roi.getName().contains("derivative")) {
					continue;
				}
				if (roi.getName() != null && roi.getName().contains("gulp")) {
					continue;
				}
				ROI2DPolyLine roiLine = (ROI2DPolyLine) roi;
				Polyline2D line = roiLine.getPolyline2D();
				roi.setName("gulp" + String.format("%07d", (int) line.xpoints[0]));
				roi.setColor(Color.red);

			} catch (Exception e) {
				Logger.warn("Failed to process ROI: " + roi.getName(), e);
				failed++;
			}
		}

		Collections.sort(roiList, new Comparators.ROI2D_Name());

		long processingTime = System.currentTimeMillis() - startTime;

		return ImageProcessingResult.builder().success(failed == 0).processedCount(processed).failedCount(failed)
				.processingTimeMs(processingTime)
				.message(String.format("Processed %d ROIs, %d failed", processed, failed)).build();
	}

	public void validateRoisAtT(int t) {
		List<ROI2D> listRois = kymos.getSequence().getROI2Ds();
		int width = kymos.getSequence().getWidth();
		for (ROI2D roi : listRois) {
			if (!(roi instanceof ROI2DPolyLine))
				continue;
			if (roi.getT() == -1)
				roi.setT(t);
			if (roi.getT() != t)
				continue;
			if (roi.getName().contains("level") || roi.getName().contains("derivative")) {
				ROI2DPolyLine roiLine = (ROI2DPolyLine) roi;
				ROI2DUtilities.interpolateMissingPointsAlongXAxis(roiLine, width);
				continue;
			}
			if (roi.getName().contains("gulp")) {
				continue;
			}
			ROI2DPolyLine roiLine = (ROI2DPolyLine) roi;
			Polyline2D line = roiLine.getPolyline2D();
			roi.setName("gulp" + String.format("%07d", (int) line.xpoints[0]));
			roi.setColor(Color.red);
		}
		Collections.sort(listRois, new Comparators.ROI2D_Name());
	}

	public void removeROIsPolylineAtT(int t) {
		List<ROI2D> listRois = kymos.getSequence().getROI2Ds();
		for (ROI2D roi : listRois) {
			if (!(roi instanceof ROI2DPolyLine))
				continue;
			if (roi.getT() == t)
				kymos.getSequence().removeROI(roi);
		}
	}

	public void updateROIFromCapillaryMeasure(Capillary cap, CapillaryMeasure caplimits) {
		int t = cap.getKymographIndex();
		List<ROI2D> listRois = kymos.getSequence().getROI2Ds();
		for (ROI2D roi : listRois) {
			if (!(roi instanceof ROI2DPolyLine))
				continue;
			if (roi.getT() != t)
				continue;
			if (!roi.getName().contains(caplimits.capName))
				continue;

			((ROI2DPolyLine) roi).setPolyline2D(caplimits.polylineLevel);
			roi.setName(caplimits.capName);
			kymos.getSequence().roiChanged(roi);
			break;
		}
	}

	public boolean transferKymosRoisToCapillaries_Measures(Capillaries capillaries) {
		List<ROI> allRois = kymos.getSequence().getROIs();
		if (allRois.size() < 1)
			return false;

		for (int kymo = 0; kymo < kymos.getSequence().getSizeT(); kymo++) {
			List<ROI> roisAtT = new ArrayList<ROI>();
			for (ROI roi : allRois) {
				if (roi instanceof ROI2D && ((ROI2D) roi).getT() == kymo)
					roisAtT.add(roi);
			}
			if (capillaries.getList().size() <= kymo) {
				capillaries.getList().add(new Capillary());
			}

			final int i = kymo;
			Capillary cap = capillaries.getList().stream() //
					.filter(c -> c.getKymographIndex() == i) //
					.findFirst() //
					.orElse(null);
			if (cap != null) {
				cap.transferROIsToAllMeasures(roisAtT);
			}
		}
		return true;
	}

	public boolean transferKymosRoi_at_T_To_Capillaries_Measures(int t, Capillary cap) {
		List<ROI> allRois = kymos.getSequence().getROIs();
		if (allRois.size() < 1)
			return false;

		List<ROI> roisAtT = new ArrayList<ROI>();
		for (ROI roi : allRois) {
			if (roi instanceof ROI2D && ((ROI2D) roi).getT() == t)
				roisAtT.add(roi);
		}
		cap.transferROIsToAllMeasures(roisAtT);
		return true;
	}

	public boolean transferKymosRoi_at_T_To_Capillaries_Measures(Capillary cap) {
		int t = resolveCurrentFrame();
		return t >= 0 && transferKymosRoi_at_T_To_Capillaries_Measures(t, cap);
	}

	private int resolveCurrentFrame() {
		int t = kymos.getCurrentFrame();
		if (t < 0 && kymos.getSequence() != null && kymos.getSequence().getFirstViewer() != null)
			t = kymos.getSequence().getFirstViewer().getPositionT();
		return t;
	}

	public boolean validateLinearROIsAtT(int t, Capillary cap) {
		Sequence seq = kymos.getSequence();
		if (seq == null)
			return false;

		seq.beginUpdate();
		try {
			List<ROI> allRois = seq.getROIs();
			if (allRois.size() < 1)
				return false;

			int width = seq.getWidth();
			for (ROI roi : allRois) {
				if (!roi.getName().contains("level") && !roi.getName().contains("derivative"))
					continue;
				if (!(roi instanceof ROI2D) || ((ROI2D) roi).getT() != t)
					continue;

				ROI2DUtilities.interpolateMissingPointsAlongXAxis((ROI2DPolyLine) roi, width);
				cap.transferROIToLinearMeasure(roi);
				seq.removeROI(roi);
			}

			seq.addROIs(cap.getLinearROIsForCapillaryAtT(t), false);
			return true;
		} finally {
			seq.endUpdate();
		}
	}

	public boolean validateLinearROIsAtT(Capillary cap) {
		int t = resolveCurrentFrame();
		return t >= 0 && validateLinearROIsAtT(t, cap);
	}

	public void validateGulpROIsAtT(Experiment exp, int t) {
		Capillary cap = getCapillaryForFrame(t, exp.getCapillaries());
		if (cap == null) {
			MessageDialog.showDialog("Capillary not found for current frame.", MessageDialog.WARNING_MESSAGE);
			return;
		}
		validateGulpROIsAtT(t, cap);
	}

	public void validateGulpROIsAtT(Capillary cap) {
		int t = resolveCurrentFrame();
		if (t >= 0)
			validateGulpROIsAtT(t, cap);
	}

	public void validateGulpROIsAtT(int t, Capillary cap) {
		if (cap == null)
			return;

		List<ROI2D> allRois = kymos.getSequence().getROI2Ds();
		ArrayList<ROI2D> gulpRois = new ArrayList<>();
		for (ROI2D r : allRois) {
			if (r.getT() == t && r.getName() != null && r.getName().contains("gulp"))
				gulpRois.add(r);
		}
		int npoints = (cap.getTopLevel() != null) ? cap.getTopLevel().getNPoints() : 0;
		cap.getGulps().transferROIsToMeasures(gulpRois, npoints);

		kymos.getSequence().removeROIs(gulpRois, false);
		kymos.getSequence().addROIs(cap.getGulpROIsForCapillaryAtT(t), false);
		cap.setGulpMeasuresDirty(false);
	}

	public void transferCapillariesMeasuresToKymos(Capillaries capillaries) {
		List<ROI2D> allROIs = kymos.getSequence().getROI2Ds();
		List<ROI2D> roisToRemove = new ArrayList<ROI2D>();
		for (ROI2D roi : allROIs) {
			if (roi instanceof ROI2DPolyLine && roi.getName() != null) {
				String name = roi.getName();
				if (name.contains("_") && (name.contains("toplevel") || name.contains("bottomlevel")
						|| name.contains("derivative") || name.contains("gulps"))) {
					roisToRemove.add(roi);
				}
			}
		}
		if (!roisToRemove.isEmpty()) {
			kymos.getSequence().removeROIs(roisToRemove, false);
		}

		List<ROI2D> newRoisList = new ArrayList<ROI2D>();
		int ncapillaries = capillaries.getList().size();
		List<String> imagesList = kymos.getImagesList();

		for (int i = 0; i < ncapillaries; i++) {
			List<ROI2D> listOfRois = capillaries.getList().get(i).transferMeasuresToROIs(imagesList);
			if (listOfRois != null) {
				newRoisList.addAll(listOfRois);
			}
		}

		kymos.getSequence().addROIs(newRoisList, false);
	}

	public Capillary getCapillaryForFrame(int t, Capillaries capillaries) {
		return CapillaryKymographNameResolution.resolve(kymos.getFileNameFromImageList(t), t, capillaries);
	}

	public void syncROIsForCurrentFrame(int t, Capillaries capillaries) {
		if (kymos.getSequence() == null || capillaries == null)
			return;
		if (t == kymos.getCurrentFrame())
			return;
		Capillary cap = getCapillaryForFrame(t, capillaries);
		List<ROI2D> roisForT = null;
		if (cap != null) {
			roisForT = cap.transferMeasuresToROIs(kymos.getImagesList());
			if (roisForT != null)
				for (ROI2D roi : roisForT)
					roi.setT(t);
		}
		MeasureRoiSync.updateMeasureROIsAt(t, kymos.getSequence(), MeasureRoiSync.MeasureRoiFilter.CAPILLARY_MEASURES,
				roisForT);
		kymos.setCurrentFrame(t);
		icy.gui.viewer.Viewer v = kymos.getSequence().getFirstViewer();
		if (v != null && v.getCanvas() != null)
			v.getCanvas().refresh();
	}

	public void replaceCapillaryMeasureRoisAtT(int t, Capillaries capillaries) {
		if (kymos.getSequence() == null || capillaries == null || t < 0)
			return;
		Capillary cap = getCapillaryForFrame(t, capillaries);
		List<ROI2D> roisForT = null;
		if (cap != null) {
			roisForT = cap.transferMeasuresToROIs(kymos.getImagesList());
			if (roisForT != null) {
				for (ROI2D roi : roisForT)
					roi.setT(t);
			}
		}
		MeasureRoiSync.updateMeasureROIsAt(t, kymos.getSequence(), MeasureRoiSync.MeasureRoiFilter.CAPILLARY_MEASURES,
				roisForT);
		icy.gui.viewer.Viewer v = kymos.getSequence().getFirstViewer();
		if (v != null && v.getCanvas() != null)
			v.getCanvas().refresh();
	}

	public void saveKymosCurvesToCapillariesMeasures(Experiment exp) {
		if (exp == null) {
			return;
		}
		CapillariesKymosMapper.pullCapillaryMeasuresFromKymos(exp.getCapillaries(), kymos);
		exp.save_capillaries_description_and_measures();
	}
}
