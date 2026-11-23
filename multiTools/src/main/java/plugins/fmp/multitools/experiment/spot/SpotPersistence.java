package plugins.fmp.multitools.experiment.spot;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.w3c.dom.Node;

import icy.roi.ROI2D;
import icy.util.XMLUtil;
import plugins.fmp.multitools.tools.ROI2D.ROI2DUtilities;
import plugins.fmp.multitools.tools.ROI2D.ROIPersistenceUtils;
import plugins.fmp.multitools.tools.ROI2D.ROIType;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.kernel.roi.roi2d.ROI2DShape;
import plugins.fmp.multitools.tools.csv.CsvNumberParsing;

/**
 * Handles persistence (XML loading/saving, CSV export/import) for Spot.
 */
public class SpotPersistence {

	private static final String ID_META = "metaMC";
	private static final int DATA_OFFSET = 3;

	public static boolean xmlLoadSpot(Node node, Spot spot) {
		if (node == null) {
			return false;
		}
		final Node spotPropertiesNode = XMLUtil.getElement(node, SpotProperties.IDS_SPOTPROPS);
		if (spotPropertiesNode == null || !spot.getProperties().loadFromXml(spotPropertiesNode)) {
			return false;
		}
		final Node nodeMeta = XMLUtil.getElement(node, ID_META);
		if (nodeMeta != null) {
			ROI2D loaded = ROI2DUtilities.loadFromXML_ROI(nodeMeta);
			ROI2DShape roi = (loaded instanceof ROI2DShape) ? (ROI2DShape) loaded : null;
			spot.setRoi(roi);
			if (roi != null) {
				roi.setColor(spot.getProperties().getColor());
				spot.getProperties().setName(roi.getName());
			}
		}
		if (spot.isConsumedBeforeRecording()) {
			SpotPreConsumedSupport.applyPreConsumedRoiStyle(spot);
		}
		return true;
	}

	public static boolean xmlLoadMeasures(Node node, Spot spot) {
		return spot.loadMeasurementsFromXml(node);
	}

	public static boolean xmlSaveSpot(Node node, Spot spot) {
		if (node == null) {
			return false;
		}
		final Node spotPropertiesNode = XMLUtil.setElement(node, SpotProperties.IDS_SPOTPROPS);
		if (spotPropertiesNode == null || !spot.getProperties().saveToXml(spotPropertiesNode)) {
			return false;
		}
		if (!spot.saveMeasurementsToXml(node)) {
			return false;
		}
		final Node nodeMeta = XMLUtil.setElement(node, ID_META);
		if (nodeMeta != null && spot.getRoi() != null) {
			boolean preConsumed = spot.isConsumedBeforeRecording();
			if (preConsumed) {
				SpotPreConsumedSupport.syncRoiStrokeForXmlSave(spot);
			}
			ROI2DUtilities.saveToXML_ROI(nodeMeta, spot.getRoi());
			if (preConsumed) {
				SpotPreConsumedSupport.applyPreConsumedRoiStyle(spot);
			}
		}
		return true;
	}

	// === CSV EXPORT/IMPORT ===

	public static String csvExportSpotSubSectionHeader(String sep) {
		return "#" + sep + "SPOTS" + sep + "multiSPOTS data\n" + "name" + sep + "index" + sep + "cageID" + sep
				+ "cagePos" + sep + "cageColumn" + sep + "cageRow" + sep + "volume" + sep + "npixels" + sep + "radius"
				+ sep 				+ "stim" + sep + "conc" + sep + "colorR" + sep + "colorG" + sep + "colorB" + sep + "preConsumed" + sep
				+ "preConsumedBaseStroke" + sep + "roiType" + sep + "roiData" + sep + "rimWidth" + sep
				+ "outerPx" + sep + "outlineType" + sep + "outlineData" + sep + "floorWidth\n";
	}

	public static String csvExportSpotDescription(Spot spot, String sep) {
		SpotProperties props = spot.getProperties();
		StringBuilder sbf = new StringBuilder();
		List<String> row = Arrays.asList(props.getName() != null ? props.getName() : "",
				String.valueOf(props.getSpotArrayIndex()), String.valueOf(props.getCageID()),
				String.valueOf(props.getCagePosition()), String.valueOf(props.getCageColumn()),
				String.valueOf(props.getCageRow()), String.valueOf(props.getSpotVolume()),
				String.valueOf(props.getSpotNPixels()), String.valueOf(props.getSpotRadius()),
				props.getStimulus() != null ? props.getStimulus().replace(",", ".") : "",
				props.getConcentration() != null ? props.getConcentration().replace(",", ".") : "");
		sbf.append(String.join(sep, row));

		Color c = props.getColor();
		int r = c != null ? c.getRed() : 0;
		int g = c != null ? c.getGreen() : 0;
		int b = c != null ? c.getBlue() : 255;
		sbf.append(sep).append(r).append(sep).append(g).append(sep).append(b);
		sbf.append(sep).append(props.isConsumedBeforeRecording() ? 1 : 0);
		if (props.hasRoiStrokeBeforePreConsumed()) {
			sbf.append(sep).append(props.getRoiStrokeBeforePreConsumed());
		} else {
			sbf.append(sep);
		}

		// Add ROI type and data columns (v2.1 format)
		sbf.append(sep);
		if (spot.getRoi() != null) {
			String roiExport = ROIPersistenceUtils.exportROITypeAndData(spot.getRoi(), sep);
			sbf.append(roiExport);
		} else {
			sbf.append(sep); // Empty roiType and roiData
		}

		SpotRimGeometry rim = spot.getRimGeometry();
		sbf.append(sep).append(rim.getRimWidthPx());
		sbf.append(sep).append(rim.getOuterPx());
		if (rim.hasOutline()) {
			sbf.append(sep).append(csvOutline(rim, sep));
		}
		sbf.append(sep).append(rim.getFloorWidthPx());

		sbf.append("\n");
		return sbf.toString();
	}

	private static String csvOutline(SpotRimGeometry rim, String sep) {
		double[] xs = rim.outlineX();
		double[] ys = rim.outlineY();
		StringBuilder sb = new StringBuilder();
		sb.append(ROIType.POLYLINE.toCsvString());
		sb.append(sep).append(xs.length);
		for (int i = 0; i < xs.length; i++) {
			sb.append(sep).append(String.format(Locale.ROOT, "%.3f", xs[i]));
			sb.append(sep).append(String.format(Locale.ROOT, "%.3f", ys[i]));
		}
		return sb.toString();
	}

	public static String csvExportMeasureSectionHeader(EnumResults measureType, String sep) {
		if (!measureType.isPersistedIn(EnumResults.PersistenceDomain.SPOT)) {
			return "#" + sep + "UNDEFINED\n";
		}
		return "#" + sep + "#\n" + "#" + sep + measureType.toPersistenceKey() + sep + "v0\n" + "name" + sep
				+ "index" + sep + "npts" + sep + "yi\n";
	}

	public static String csvExportMeasuresOneType(Spot spot, EnumResults measureType, String sep) {
		StringBuilder sbf = new StringBuilder();
		String name = spot.getProperties().getName() != null ? spot.getProperties().getName() : "";
		sbf.append(name).append(sep).append(spot.getProperties().getSpotArrayIndex()).append(sep);
		switch (measureType) {
		case FILTER_GREY:
			spot.getRoiNormalizedGreySignal().exportYDataToCsv(sbf, sep);
			break;
		case FILTER_GREY_FLY_MASKED:
			spot.getFlyPixelExcludedGreySignal().exportYDataToCsv(sbf, sep);
			break;
		case FILTER_GREY_SMOOTHED:
			spot.getSmoothedFlyPixelExcludedGreySignal().exportYDataToCsv(sbf, sep);
			break;
		case FLY_PIXEL_COUNT:
			spot.getFlyCoveredPixelCount().exportIsPresentToCsv(sbf, sep);
			break;
		case COLOR_PIXEL_COUNT:
			spot.getColorDistanceSelectedPixelCount().exportYDataToCsv(sbf, sep);
			break;
		case COLOR_GREY:
			spot.getColorDistanceGreySignal().exportYDataToCsv(sbf, sep);
			break;
		case COLOR_GREY_PRE_GATE:
			spot.getColorDistanceGreySignalBeforeOccupancyCheck().exportYDataToCsv(sbf, sep);
			break;
		case COLOR_GREY_SMOOTHED:
			spot.getSmoothedColorDistanceGreySignal().exportYDataToCsv(sbf, sep);
			break;
		case KYMO_ROW_FRACTION:
			spot.getAboveThresholdStripRowFraction().exportYDataToCsv(sbf, sep);
			break;
		case KYMO_ROW_FRACTION_CHANGE:
			spot.getAbsoluteStripRowFractionChange().exportYDataToCsv(sbf, sep);
			break;
		case KYMO_GREEN_HEIGHT:
			spot.getGreenMaskHeight().exportYDataToCsv(sbf, sep);
			break;
		case KYMO_GREEN_HEIGHT_RATIO:
			spot.getGreenMaskHeightToPeakRatio().exportYDataToCsv(sbf, sep);
			break;
		case X_LINE_RATIO:
			spot.getCrossLineSignalToBaselineRatio().exportYDataToCsv(sbf, sep);
			break;
		case RIM_LINE_RATIO:
			spot.getRimCoverageToBaselineRatio().exportYDataToCsv(sbf, sep);
			break;
		default:
			break;
		}
		sbf.append("\n");
		return sbf.toString();
	}

	public static void csvImportSpotDescription(Spot spot, String[] data) {
		if (data == null || data.length < 6) {
			throw new IllegalArgumentException("CSV data must have at least 6 elements");
		}

		SpotProperties props = spot.getProperties();
		try {
			int index = 0;
			props.setName(data[index++]);
			props.setSpotArrayIndex(Integer.parseInt(data[index++]));
			props.setCageID(Integer.parseInt(data[index++]));

			if (props.getCageID() < 0) {
				props.setCageID(SpotString.getCageIDFromSpotName(props.getName()));
			}

			props.setCagePosition(Integer.parseInt(data[index++]));

			if (data.length >= 6) {
				props.setCageColumn(Integer.parseInt(data[index++]));
			}
			if (data.length >= 7) {
				props.setCageRow(Integer.parseInt(data[index++]));
			}

			if (data.length == 10) {
				Integer.parseInt(data[index++]);
			}

			props.setSpotVolume(CsvNumberParsing.parseDouble(data[index++]));
			props.setSpotNPixels(Integer.parseInt(data[index++]));
			props.setSpotRadius(Integer.parseInt(data[index++]));
			props.setStimulus(data[index++]);
			props.setConcentration(data[index++]);

			if (index + 3 <= data.length && isColorFormat(data, index)) {
				int r = parseIntClamped(data[index++], 0, 255);
				int g = parseIntClamped(data[index++], 0, 255);
				int b = parseIntClamped(data[index++], 0, 255);
				props.setColor(new Color(r, g, b));
			}

			if (index < data.length && isPreConsumedFlagField(data[index])) {
				props.setConsumedBeforeRecording("1".equals(data[index++].trim()));
				if (index < data.length) {
					String strokeField = data[index].trim();
					if (props.isConsumedBeforeRecording() && !strokeField.isEmpty()) {
						try {
							double baseStroke = Double.parseDouble(strokeField);
							if (Double.isFinite(baseStroke) && baseStroke > 0.0) {
								props.setRoiStrokeBeforePreConsumed((float) baseStroke);
							}
						} catch (NumberFormatException ignored) {
							// optional column
						}
					}
					index++;
				}
			}

			if (index < data.length) {
				String roiType = data[index++];
				int roiFields = roiDataFieldCount(roiType, data, index);
				if (roiFields < 0) {
					spot.regenerateROIFromCoordinates();
				} else {
					String roiData = roiFields == 0 ? ""
							: String.join(";", Arrays.copyOfRange(data, index, index + roiFields));
					index += roiFields;
					if (roiType != null && !roiType.trim().isEmpty() && !roiType.equals("unknown")) {
						ROI2D reconstructedROI = ROIPersistenceUtils.importROIFromCSV(roiType, roiData, props.getName());
						if (reconstructedROI instanceof ROI2DShape) {
							spot.setRoi((ROI2DShape) reconstructedROI);
						} else if (reconstructedROI != null) {
							System.err.println(
									"Warning: Reconstructed ROI is not ROI2DShape for spot: " + props.getName());
							spot.regenerateROIFromCoordinates();
						} else {
							spot.regenerateROIFromCoordinates();
						}
					} else {
						spot.regenerateROIFromCoordinates();
					}
				}
			} else {
				spot.regenerateROIFromCoordinates();
			}
			importRimGeometry(spot, data, index);

			if (props.getColor() != null && spot.getRoi() != null) {
				spot.getRoi().setColor(props.getColor());
			}
			if (spot.isConsumedBeforeRecording()) {
				SpotPreConsumedSupport.applyPreConsumedRoiStyle(spot);
			}

		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid numeric value in CSV data", e);
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new IllegalArgumentException("Insufficient data in CSV array", e);
		}
	}

	private static int roiDataFieldCount(String roiType, String[] data, int index) {
		int remain = data.length - index;
		if (remain < 0) {
			return -1;
		}
		ROIType type = ROIType.fromString(roiType);
		switch (type) {
		case ELLIPSE:
			if (remain >= 4) {
				return 4;
			}
			return remain >= 3 ? 3 : -1;
		case LINE:
		case RECTANGLE:
			return remain >= 4 ? 4 : -1;
		case POLYLINE:
		case POLYGON:
			if (remain < 1) {
				return -1;
			}
			try {
				int n = Integer.parseInt(data[index].trim());
				int need = 1 + n * 2;
				return remain >= need ? need : -1;
			} catch (NumberFormatException e) {
				return -1;
			}
		default:
			if (remain > 0 && data[index].trim().isEmpty()) {
				return 1;
			}
			return 0;
		}
	}

	private static void importRimGeometry(Spot spot, String[] data, int index) {
		if (data == null || index + 1 >= data.length) {
			return;
		}
		SpotRimGeometry rim = spot.getRimGeometry();
		try {
			rim.setRimWidthPx(Integer.parseInt(data[index].trim()));
			rim.setOuterPx(Integer.parseInt(data[index + 1].trim()));
			index += 2;
		} catch (NumberFormatException e) {
			return;
		}
		if (index >= data.length || !ROIType.POLYLINE.toCsvString().equalsIgnoreCase(data[index].trim())) {
			readFloorWidth(rim, data, index);
			return;
		}
		index++;
		if (index >= data.length) {
			return;
		}
		int n;
		try {
			n = Integer.parseInt(data[index].trim());
			index++;
		} catch (NumberFormatException e) {
			return;
		}
		if (n < 3 || index + n * 2 > data.length) {
			return;
		}
		double[] xs = new double[n];
		double[] ys = new double[n];
		try {
			for (int i = 0; i < n; i++) {
				xs[i] = Double.parseDouble(data[index++].trim().replace(',', '.'));
				ys[i] = Double.parseDouble(data[index++].trim().replace(',', '.'));
			}
		} catch (NumberFormatException e) {
			return;
		}
		rim.setOutline(xs, ys);
		readFloorWidth(rim, data, index);
	}

	/** Trailing integer after the outline, or just after outerPx when there is no outline. */
	private static void readFloorWidth(SpotRimGeometry rim, String[] data, int index) {
		if (data == null || index >= data.length) {
			return;
		}
		try {
			rim.setFloorWidthPx(Integer.parseInt(data[index].trim()));
		} catch (NumberFormatException e) {
			return;
		}
	}

	private static boolean isPreConsumedFlagField(String field) {
		if (field == null) {
			return false;
		}
		String t = field.trim();
		return "0".equals(t) || "1".equals(t);
	}

	private static boolean isColorFormat(String[] data, int index) {
		if (index + 3 > data.length)
			return false;
		try {
			int r = Integer.parseInt(data[index].trim());
			int g = Integer.parseInt(data[index + 1].trim());
			int b = Integer.parseInt(data[index + 2].trim());
			return r >= 0 && r <= 255 && g >= 0 && g <= 255 && b >= 0 && b <= 255;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private static int parseIntClamped(String s, int min, int max) {
		int v = Integer.parseInt(s.trim());
		return Math.max(min, Math.min(max, v));
	}

	private static void importKymoY(SpotMeasure measure, String[] data, boolean x, boolean y) {
		if (measure == null) {
			return;
		}
		if (x && y) {
			measure.importXYDataFromCsv(data, DATA_OFFSET);
		} else if (!x && y) {
			measure.importYDataFromCsv(data, DATA_OFFSET);
		}
	}

	public static void csvImportSpotData(Spot spot, EnumResults measureType, String[] data, boolean x, boolean y) {
		switch (measureType) {
		case FILTER_GREY:
			if (x && y) {
				spot.getRoiNormalizedGreySignal().importXYDataFromCsv(data, DATA_OFFSET);
			} else if (!x && y) {
				spot.getRoiNormalizedGreySignal().importYDataFromCsv(data, DATA_OFFSET);
			}
			break;
		case FILTER_GREY_FLY_MASKED:
			if (x && y) {
				spot.getFlyPixelExcludedGreySignal().importXYDataFromCsv(data, DATA_OFFSET);
			} else if (!x && y) {
				spot.getFlyPixelExcludedGreySignal().importYDataFromCsv(data, DATA_OFFSET);
			}
			break;
		case FILTER_GREY_SMOOTHED:
			if (x && y) {
				spot.getSmoothedFlyPixelExcludedGreySignal().importXYDataFromCsv(data, DATA_OFFSET);
			} else if (!x && y) {
				spot.getSmoothedFlyPixelExcludedGreySignal().importYDataFromCsv(data, DATA_OFFSET);
			}
			break;
		case FLY_PIXEL_COUNT:
			if (x && y) {
				// FlyPresent is stored as presence array; ignore X and import presence.
				spot.getFlyCoveredPixelCount().importIsPresentFromCsv(data, DATA_OFFSET);
			} else if (!x && y) {
				spot.getFlyCoveredPixelCount().importIsPresentFromCsv(data, DATA_OFFSET);
			}
			break;
		case COLOR_PIXEL_COUNT:
			if (x && y) {
				spot.getColorDistanceSelectedPixelCount().importXYDataFromCsv(data, DATA_OFFSET);
			} else if (!x && y) {
				spot.getColorDistanceSelectedPixelCount().importYDataFromCsv(data, DATA_OFFSET);
			}
			break;
		case COLOR_GREY:
			if (x && y) {
				spot.getColorDistanceGreySignal().importXYDataFromCsv(data, DATA_OFFSET);
			} else if (!x && y) {
				spot.getColorDistanceGreySignal().importYDataFromCsv(data, DATA_OFFSET);
			}
			spot.getColorDistanceMeasurements().rebuildGreySumCleanFromGreySum();
			break;
		case COLOR_GREY_PRE_GATE:
			if (x && y) {
				spot.getColorDistanceGreySignalBeforeOccupancyCheck().importXYDataFromCsv(data, DATA_OFFSET);
			} else if (!x && y) {
				spot.getColorDistanceGreySignalBeforeOccupancyCheck().importYDataFromCsv(data, DATA_OFFSET);
			}
			break;
		case COLOR_GREY_SMOOTHED:
			if (x && y) {
				spot.getSmoothedColorDistanceGreySignal().importXYDataFromCsv(data, DATA_OFFSET);
			} else if (!x && y) {
				spot.getSmoothedColorDistanceGreySignal().importYDataFromCsv(data, DATA_OFFSET);
			}
			break;
		case KYMO_ROW_FRACTION:
			importKymoY(spot.getAboveThresholdStripRowFraction(), data, x, y);
			break;
		case KYMO_ROW_FRACTION_CHANGE:
			importKymoY(spot.getAbsoluteStripRowFractionChange(), data, x, y);
			break;
		case KYMO_GREEN_HEIGHT:
			importKymoY(spot.getGreenMaskHeight(), data, x, y);
			break;
		case KYMO_GREEN_HEIGHT_RATIO:
			importKymoY(spot.getGreenMaskHeightToPeakRatio(), data, x, y);
			break;
		case X_LINE_RATIO:
			importKymoY(spot.getCrossLineSignalToBaselineRatio(), data, x, y);
			break;
		case RIM_LINE_RATIO:
			importKymoY(spot.getRimCoverageToBaselineRatio(), data, x, y);
			break;
		default:
			break;
		}
	}
}