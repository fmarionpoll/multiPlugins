package plugins.fmp.multitools.tools.toExcel.csv;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.util.CellReference;

import icy.roi.ROI2D;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.CageFoodDistanceMm;
import plugins.fmp.multitools.experiment.cage.FlyPosition;
import plugins.fmp.multitools.experiment.cage.FlyPositions;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.toExcel.NormalizedExportSupport;
import plugins.fmp.multitools.tools.toExcel.exceptions.ExcelExportException;

/** Exports one row per tracked fly and camera frame. */
public final class CsvNormalizedFlyPositionExport {
	private CsvNormalizedFlyPositionExport() {
	}

	public static void exportToFolder(Path folder, ResultsOptions options) throws ExcelExportException {
		JComboBoxExperimentLazy experiments = options.expList;
		int[] bounds = experiments.getExportExperimentIndexBounds(options);
		try {
			experiments.loadExperimentMeasuresForExportRange(false, true, bounds[0], bounds[1]);
			try (CsvNormalizedExportSupport csv = new CsvNormalizedExportSupport(folder, Collections.emptyList(), 0, false);
					CSVPrinter printer = csv.openPrinter("flyposition", headers(options))) {
				for (int i = bounds[0]; i <= bounds[1]; i++) {
					String series = CellReference.convertNumToColString(i - bounds[0]);
					writeExperiment(csv, printer, experiments.getItemAt(i), series, options);
				}
			}
		} catch (Exception e) {
			throw new ExcelExportException("Fly position CSV export failed", "csv_export", folder.toString(), e);
		}
	}

	static String[] headers(ResultsOptions options) {
		List<String> columns = new ArrayList<>(Arrays.asList(CsvNormalizedExportSupport.COL_EXPERIMENT_ID,
				CsvNormalizedExportSupport.COL_CAGE_ID, "fly_id", CsvNormalizedExportSupport.COL_TIME_MIN,
				CsvNormalizedExportSupport.COL_FRAME_INDEX));
		if (options.xyImage) {
			columns.addAll(Arrays.asList("x_px", "y_px", "width_px", "height_px"));
		}
		if (options.yVsFood) columns.add("distance_from_food_mm");
		if (options.distance) columns.add("distance_mm");
		if (options.alive) columns.add("alive");
		if (options.sleep) columns.add("sleep");
		if (options.illumPhase) columns.add("illum_phase");
		return columns.toArray(new String[0]);
	}

	static void writeExperiment(CsvNormalizedExportSupport csv, CSVPrinter printer, Experiment exp, String series,
			ResultsOptions options) throws IOException {
		double[] times = exp.getMeasureTimeMinutes();
		String expKey = NormalizedExportSupport.buildExpKey(exp, series);
		for (Cage cage : exp.getCages().getCageList()) {
			FlyPositions positions = cage == null ? null : cage.getFlyPositions();
			if (positions == null || positions.flyPositionList == null || positions.flyPositionList.isEmpty()) {
				continue;
			}
			csv.ensureDescriptors(exp, series, cage, null);
			ROI2D roi = cage.getRoi() != null ? cage.getRoi() : cage.getCageRoi2D();
			int cageId = cage.getProperties().getCageID();
			for (FlyPositions track : tracksByFly(positions)) {
				boolean hasSleep = options.sleep && track.flyPositionList.size() > 1
						&& track.flyPositionList.get(1).flyIndexT > track.flyPositionList.get(0).flyIndexT;
				if (options.alive || options.onlyalive) {
					track.computeIsAlive();
				} else if (options.distance && !hasSleep) {
					track.computeDistanceBetweenConsecutivePoints();
				}
				if (hasSleep) {
					track.computeSleep();
				}
				for (FlyPosition position : track.flyPositionList) {
					int frame = position.flyIndexT;
					if (times != null && frame >= times.length && exp.getSeqCamData() != null) {
						int validFrame = exp.getSeqCamData().absoluteToValidFrameIndex(frame);
						if (validFrame < times.length) frame = validFrame;
					}
					List<Object> row = new ArrayList<>();
					row.add(expKey);
					row.add(cageId);
					row.add(position.flyId);
					row.add(timeMinutes(times, frame, position.tMs));
					row.add(frame);

					boolean showMeasures = !options.onlyalive || position.bAlive;
					if (options.xyImage) {
						row.add(showMeasures ? finite(position.getX()) : null);
						row.add(showMeasures ? finite(position.getY()) : null);
						row.add(showMeasures ? finite(position.getW()) : null);
						row.add(showMeasures ? finite(position.getH()) : null);
					}
					if (options.yVsFood) {
						Point2D center = showMeasures ? position.getCenterRectangle() : null;
						row.add(finite(CageFoodDistanceMm.distanceFromFoodMm(roi, center, track.getMmPerPixelX(),
								track.getMmPerPixelY(), cage.getProperties().getFoodSide())));
					}
					if (options.distance) row.add(showMeasures ? finite(position.distanceMm) : null);
					if (options.alive) row.add(position.bAlive ? 1 : 0);
					if (options.sleep) row.add(showMeasures && hasSleep ? Integer.valueOf(position.bSleep ? 1 : 0) : null);
					if (options.illumPhase) row.add(position.illumPhase >= 0 ? Integer.valueOf(position.illumPhase) : null);
					printer.printRecord(row);
				}
			}
		}
	}

	private static Iterable<FlyPositions> tracksByFly(FlyPositions positions) {
		Map<Integer, FlyPositions> tracks = new LinkedHashMap<>();
		for (FlyPosition position : positions.flyPositionList) {
			if (position == null || position.flyIndexT < 0 || position.getRectangle2D() == null) {
				continue;
			}
			FlyPositions track = tracks.computeIfAbsent(Math.max(0, position.flyId), id -> {
				FlyPositions fly = new FlyPositions();
				fly.setMmPerPixelX(positions.getMmPerPixelX());
				fly.setMmPerPixelY(positions.getMmPerPixelY());
				fly.setMoveThreshold(positions.getMoveThreshold());
				fly.setSleepThreshold(positions.getSleepThreshold());
				return fly;
			});
			FlyPosition sample = new FlyPosition(position.flyIndexT, Math.max(0, position.flyId),
					position.getRectangle2D());
			sample.tMs = position.tMs;
			sample.illumPhase = position.illumPhase;
			track.flyPositionList.add(sample);
		}
		for (FlyPositions track : tracks.values()) {
			track.flyPositionList.sort(Comparator.comparingInt(FlyPosition::getFlyIndexT));
		}
		return tracks.values();
	}

	private static Double timeMinutes(double[] times, int frame, long recordedMs) {
		if (times != null && frame >= 0 && frame < times.length) {
			return times[frame];
		}
		return recordedMs != 0 || frame == 0 ? Double.valueOf(recordedMs / 60000.0) : null;
	}

	private static Double finite(double value) {
		return Double.isFinite(value) ? value : null;
	}
}
