package plugins.fmp.multitools.experiment.spot;

import java.util.Arrays;
import java.util.List;

import org.w3c.dom.Node;

import icy.roi.ROI2D;
import icy.util.XMLUtil;
import plugins.fmp.multitools.experiment.ids.SpotID;
import plugins.fmp.multitools.experiment.spots.EnumSpotMeasures;
import plugins.fmp.multitools.tools.ROI2D.ROI2DUtilities;
import plugins.fmp.multitools.tools.ROI2D.ROIPersistenceUtils;
import plugins.kernel.roi.roi2d.ROI2DShape;

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
			ROI2DUtilities.saveToXML_ROI(nodeMeta, spot.getRoi());
		}
		return true;
	}

	// === CSV EXPORT/IMPORT ===

	public static String csvExportSpotSubSectionHeader(String sep) {
		return "#" + sep + "SPOTS" + sep + "multiSPOTS data\n" + "name" + sep + "index" + sep + "cageID" + sep
				+ "cagePos" + sep + "cageColumn" + sep + "cageRow" + sep + "volume" + sep + "npixels" + sep + "radius"
				+ sep + "stim" + sep + "conc" + sep + "roiType" + sep + "roiData\n";
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
		
		// Add ROI type and data columns (v2.1 format)
		sbf.append(sep);
		if (spot.getRoi() != null) {
			String roiExport = ROIPersistenceUtils.exportROITypeAndData(spot.getRoi(), sep);
			sbf.append(roiExport);
		} else {
			sbf.append(sep); // Empty roiType and roiData
		}
		
		sbf.append("\n");
		return sbf.toString();
	}

	public static String csvExportMeasureSectionHeader(EnumSpotMeasures measureType, String sep) {
		switch (measureType) {
		case AREA_SUM:
		case AREA_SUMCLEAN:
		case AREA_FLYPRESENT:
			return "#" + sep + "#\n" + "#" + sep + measureType.toString() + sep + "v0\n" + "name" + sep + "index" + sep
					+ "npts" + sep + "yi\n";
		default:
			return "#" + sep + "UNDEFINED\n";
		}
	}

	public static String csvExportMeasuresOneType(Spot spot, EnumSpotMeasures measureType, String sep) {
		StringBuilder sbf = new StringBuilder();
		String name = spot.getProperties().getName() != null ? spot.getProperties().getName() : "";
		sbf.append(name).append(sep).append(spot.getProperties().getSpotArrayIndex()).append(sep);
		switch (measureType) {
		case AREA_SUM:
			spot.getSum().exportYDataToCsv(sbf, sep);
			break;
		case AREA_SUMCLEAN:
			spot.getSumClean().exportYDataToCsv(sbf, sep);
			break;
		case AREA_FLYPRESENT:
			spot.getFlyPresent().exportYDataToCsv(sbf, sep);
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

			if (data.length >= 12) {
				int spotUniqueIDValue = Integer.parseInt(data[index++]);
				if (spotUniqueIDValue >= 0) {
					props.setSpotUniqueID(new SpotID(spotUniqueIDValue));
				}
			}

			if (data.length >= 11) {
				props.setCageColumn(Integer.parseInt(data[index++]));
				props.setCageRow(Integer.parseInt(data[index++]));
			}

			if (data.length == 10) {
				Integer.parseInt(data[index++]); // dummy read for radius
			}

			props.setSpotVolume(Double.parseDouble(data[index++]));
			props.setSpotNPixels(Integer.parseInt(data[index++]));
			props.setSpotRadius(Integer.parseInt(data[index++]));
			props.setStimulus(data[index++]);
			props.setConcentration(data[index++]);
			
			// Check for ROI data (v2.1 format with roiType and roiData columns)
			if (index < data.length && index + 1 < data.length) {
				String roiType = data[index++];
				String roiData = data[index++];
				
				if (roiType != null && !roiType.trim().isEmpty() && !roiType.equals("unknown")) {
					// Reconstruct ROI from CSV data
					ROI2D reconstructedROI = ROIPersistenceUtils.importROIFromCSV(
						roiType, roiData, props.getName());
					if (reconstructedROI instanceof ROI2DShape) {
						spot.setRoi((ROI2DShape) reconstructedROI);
					} else if (reconstructedROI != null) {
						System.err.println("Warning: Reconstructed ROI is not ROI2DShape for spot: " + props.getName());
						// Fall back to regeneration
						spot.regenerateROIFromCoordinates();
					} else {
						// Reconstruction failed, regenerate from coordinates
						spot.regenerateROIFromCoordinates();
					}
				} else {
					// No ROI data or unknown type, regenerate from coordinates
					spot.regenerateROIFromCoordinates();
				}
			} else {
				// Old format (v2.0 or earlier) without ROI columns
				// Regenerate ROI from stored coordinates (backward compatibility)
				spot.regenerateROIFromCoordinates();
			}

		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid numeric value in CSV data", e);
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new IllegalArgumentException("Insufficient data in CSV array", e);
		}
	}

	public static void csvImportSpotData(Spot spot, EnumSpotMeasures measureType, String[] data, boolean x, boolean y) {
		switch (measureType) {
		case AREA_SUM:
			if (x && y) {
				spot.getSum().importXYDataFromCsv(data, DATA_OFFSET);
			} else if (!x && y) {
				spot.getSum().importYDataFromCsv(data, DATA_OFFSET);
			}
			break;
		case AREA_SUMCLEAN:
			if (x && y) {
				spot.getSumClean().importXYDataFromCsv(data, DATA_OFFSET);
			} else if (!x && y) {
				spot.getSumClean().importYDataFromCsv(data, DATA_OFFSET);
			}
			break;
		case AREA_FLYPRESENT:
			if (x && y) {
				spot.getFlyPresent().importXYDataFromCsv(data, DATA_OFFSET);
			} else if (!x && y) {
				spot.getFlyPresent().importYDataFromCsv(data, DATA_OFFSET);
			}
			break;
		default:
			break;
		}
	}
}
