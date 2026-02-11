package plugins.fmp.multitools.experiment.capillary;

import java.awt.geom.Line2D;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.w3c.dom.Node;

import icy.roi.ROI2D;
import icy.type.geom.Polyline2D;
import icy.util.XMLUtil;
import plugins.fmp.multitools.experiment.capillaries.EnumCapillaryMeasures;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.ROI2D.AlongT;
import plugins.fmp.multitools.tools.ROI2D.ROI2DUtilities;
import plugins.fmp.multitools.tools.ROI2D.ROIPersistenceUtils;
import plugins.fmp.multitools.tools.ROI2D.ROIType;
import plugins.kernel.roi.roi2d.ROI2DLine;
import plugins.kernel.roi.roi2d.ROI2DPolyLine;

/**
 * Handles persistence (XML loading/saving, CSV export/import) for Capillary.
 */
public class CapillaryPersistence {

	private static final String ID_META = "metaMC";
	private static final String ID_VERSION = "version";
	private static final String ID_VERSIONNUM = "2.0.0";
	private static final String ID_INDEXIMAGE = "indexImageMC";
	private static final String ID_NAME = "nameMC";
	private static final String ID_NAMETIFF = "filenameTIFF";

	private static final String ID_INTERVALS = "INTERVALS";
	private static final String ID_NINTERVALS = "nintervals";
	private static final String ID_INTERVAL = "interval_";

	private static final String ID_TOPLEVEL = "toplevel";
	private static final String ID_BOTTOMLEVEL = "bottomlevel";
	private static final String ID_DERIVATIVE = "derivative";
	private static final String ID_TOPLEVEL_CORRECTED = "toplevel_corrected";
	private static final String ID_THRESHOLD = "threshold";

	/**
	 * Loads capillary configuration from XML (Meta).
	 * 
	 * @param node The XML node to load from.
	 * @param cap  The capillary to populate.
	 * @return true if successful.
	 */
	public static boolean xmlLoadCapillary(Node node, Capillary cap) {
		final Node nodeMeta = XMLUtil.getElement(node, ID_META);
		boolean flag = (nodeMeta != null);
		if (flag) {
			cap.version = XMLUtil.getElementValue(nodeMeta, ID_VERSION, "0.0.0");
			cap.setKymographIndex(XMLUtil.getElementIntValue(nodeMeta, ID_INDEXIMAGE, cap.getKymographIndex()));
			cap.setKymographName(XMLUtil.getElementValue(nodeMeta, ID_NAME, cap.getKymographName()));
			cap.setKymographFileName(XMLUtil.getElementValue(nodeMeta, ID_NAMETIFF, cap.getKymographFileName()));

			// Load properties
			cap.getProperties().loadFromXml(nodeMeta);
			cap.setRoi(ROI2DUtilities.loadFromXML_ROI(nodeMeta));
			xmlLoadIntervals(node, cap);
		}
		return flag;
	}

	private static boolean xmlLoadIntervals(Node node, Capillary cap) {
		cap.getAlongTList().clear();
		final Node nodeMeta2 = XMLUtil.getElement(node, ID_INTERVALS);
		if (nodeMeta2 == null)
			return false;

		int nitems = XMLUtil.getElementIntValue(nodeMeta2, ID_NINTERVALS, 0);
		if (nitems > 0) {
			for (int i = 0; i < nitems; i++) {
				Node node_i = XMLUtil.setElement(nodeMeta2, ID_INTERVAL + i);
				// Depending on how Capillary exposes internal ROI list logic
				// We need to add to cap.roisForKymo.
				// Since we are moving logic out, we might need access methods.
				// For now assuming we can add via a getter that returns the list or specific
				// add method.
				// Checking Capillary structure plan: it will have delegating methods.

				// Re-implementing logic:
				AlongT roiInterval = new AlongT();
				roiInterval.loadFromXML(node_i);
				cap.getAlongTList().add(roiInterval);

				if (i == 0) {
					cap.setRoi(cap.getAlongTList().get(0).getRoi());
				}
			}
		}
		return true;
	}

	public static boolean xmlSaveCapillary(Node node, Capillary cap) {
		final Node nodeMeta = XMLUtil.setElement(node, ID_META);
		if (nodeMeta == null)
			return false;

		if (cap.version == null)
			cap.version = ID_VERSIONNUM;

		XMLUtil.setElementValue(nodeMeta, ID_VERSION, cap.version);
		XMLUtil.setElementIntValue(nodeMeta, ID_INDEXIMAGE, cap.getKymographIndex());
		XMLUtil.setElementValue(nodeMeta, ID_NAME, cap.getKymographName());

		if (cap.getKymographFileName() != null) {
			String filename = Paths.get(cap.getKymographFileName()).getFileName().toString();
			XMLUtil.setElementValue(nodeMeta, ID_NAMETIFF, filename);
		}

		// Save properties
		cap.getProperties().saveToXml(nodeMeta);

		ROI2DUtilities.saveToXML_ROI(nodeMeta, cap.getRoi());

		return xmlSaveIntervals(node, cap);
	}

	private static boolean xmlSaveIntervals(Node node, Capillary cap) {
		final Node nodeMeta2 = XMLUtil.setElement(node, ID_INTERVALS);
		if (nodeMeta2 == null)
			return false;

		List<AlongT> rois = cap.getAlongTList();
		int nitems = rois.size();
		XMLUtil.setElementIntValue(nodeMeta2, ID_NINTERVALS, nitems);
		if (nitems > 0) {
			for (int i = 0; i < nitems; i++) {
				Node node_i = XMLUtil.setElement(nodeMeta2, ID_INTERVAL + i);
				rois.get(i).saveToXML(node_i);
			}
		}
		return true;
	}

	public static boolean xmlLoadMeasures(Node node, Capillary cap) {
		String header = cap.getLast2ofCapillaryName() + "_";
		boolean result = cap.getTopLevel().loadCapillaryLimitFromXML(node, ID_TOPLEVEL, header) > 0;
		result |= cap.getBottomLevel().loadCapillaryLimitFromXML(node, ID_BOTTOMLEVEL, header) > 0;
		result |= cap.getDerivative().loadCapillaryLimitFromXML(node, ID_DERIVATIVE, header) > 0;
		result |= cap.getTopCorrected().loadCapillaryLimitFromXML(node, ID_TOPLEVEL_CORRECTED, header) > 0;
		result |= cap.getThreshold().loadCapillaryLimitFromXML(node, ID_THRESHOLD, header) > 0;
		result |= cap.getGulps().loadGulpsFromXML(node);
		return result;
	}

	// === CSV EXPORT/IMPORT ===

	public static String csvExportCapillarySubSectionHeader(String sep) {
		StringBuffer sbf = new StringBuffer();
		sbf.append("#" + sep + "CAPILLARIES" + sep + "describe each capillary\n");
		List<String> row2 = Arrays.asList("cap_prefix", "kymoIndex", "roiName", "kymoFile", "cap_cage", "cap_nflies",
				"cap_volume", "cap_npixel", "cap_stim", "cap_conc", "cap_side", "ROIname", "roiType", "npoints");
		sbf.append(String.join(sep, row2));
		sbf.append("\n");
		return sbf.toString();
	}

	public static String csvExportCapillaryDescription(Capillary cap, String sep) {
		StringBuffer sbf = new StringBuffer();
		// Access properties via getter
		CapillaryProperties props = cap.getProperties();

		// Ensure cap_prefix is never null - derive from ROI name or kymograph name
		String capPrefix = cap.getKymographPrefix();
		if (capPrefix == null || capPrefix.isEmpty()) {
			// Try to derive from ROI name first (format: "line0L" -> "0L")
			String roiName = cap.getRoiName();
			if (roiName != null && roiName.startsWith("line")) {
				// Extract number and L/R suffix (e.g., "line0L" -> "0L")
				String suffix = roiName.substring(4); // Skip "line"
				capPrefix = suffix;
			} else {
				// Fallback to kymograph name (format: "line01" -> "01", but we want "0L" or
				// "0R")
				String kymoName = cap.getKymographName();
				if (kymoName != null && kymoName.length() >= 2) {
					// If kymograph name ends with "1" or "2", convert to "L" or "R"
					String lastChar = kymoName.substring(kymoName.length() - 1);
					if (lastChar.equals("1")) {
						capPrefix = kymoName.substring(kymoName.length() - 2, kymoName.length() - 1) + "L";
					} else if (lastChar.equals("2")) {
						capPrefix = kymoName.substring(kymoName.length() - 2, kymoName.length() - 1) + "R";
					} else {
						// Just use last 2 characters
						capPrefix = kymoName.substring(kymoName.length() - 2);
					}
				}
			}
		}
		// If still null, use empty string
		if (capPrefix == null) {
			capPrefix = "";
		}

		// Build base row data
		List<String> row = new ArrayList<>(Arrays.asList(capPrefix, Integer.toString(cap.getKymographIndex()),
				cap.getKymographName(), cap.getKymographFileName(), Integer.toString(props.getCageID()),
				Integer.toString(props.getNFlies()), Double.toString(props.getVolume()),
				Integer.toString(props.getPixels()), props.getStimulus(), props.getConcentration(), props.getSide()));

		// Add ROI name and type (v2.1 format)
		String roiName = (cap.getRoi() != null && cap.getRoi().getName() != null) ? cap.getRoi().getName() : "";
		row.add(roiName);

		// Add ROI type
		ROIType roiType = ROIPersistenceUtils.detectROIType(cap.getRoi());
		row.add(roiType.toCsvString());

		// Extract ROI points (for ROI2DPolyLine or ROI2DLine)
		int npoints = 0;
		if (cap.getRoi() != null) {
			if (cap.getRoi() instanceof ROI2DPolyLine) {
				ROI2DPolyLine polyLineRoi = (ROI2DPolyLine) cap.getRoi();
				Polyline2D polyline = polyLineRoi.getPolyline2D();
				npoints = polyline.npoints;
				row.add(Integer.toString(npoints));
				// Add x, y coordinates for each point
				for (int i = 0; i < npoints; i++) {
					row.add(Integer.toString((int) polyline.xpoints[i]));
					row.add(Integer.toString((int) polyline.ypoints[i]));
				}
			} else if (cap.getRoi() instanceof ROI2DLine) {
				ROI2DLine lineRoi = (ROI2DLine) cap.getRoi();
				Line2D line = lineRoi.getLine();
				npoints = 2; // Line has 2 points
				row.add(Integer.toString(npoints));
				// Add x, y coordinates for both points
				row.add(Integer.toString((int) line.getX1()));
				row.add(Integer.toString((int) line.getY1()));
				row.add(Integer.toString((int) line.getX2()));
				row.add(Integer.toString((int) line.getY2()));
			} else {
				row.add("0"); // No points if ROI type not supported
			}
		} else {
			row.add("0"); // No ROI
		}

		sbf.append(String.join(sep, row));
		sbf.append("\n");
		return sbf.toString();
	}

	public static String csvExportMeasureSectionHeader(EnumCapillaryMeasures measureType, String sep) {
		StringBuffer sbf = new StringBuffer();
		String explanation1 = "columns=" + sep + "name" + sep + "index" + sep + "npts" + sep + "yi\n";
		String explanation2 = "columns=" + sep + "name" + sep + "index" + sep + " n_gulps(i)" + sep + " ..." + sep
				+ " gulp_i" + sep + " .npts(j)" + sep + "." + sep + "(xij" + sep + "yij))\n";
		switch (measureType) {
		case TOPRAW:
			sbf.append("#" + sep + "TOPRAW" + sep + explanation1);
			break;
		case TOPLEVEL:
			sbf.append("#" + sep + "TOPLEVEL_CORRECTED" + sep + explanation1);
			break;
		case BOTTOMLEVEL:
			sbf.append("#" + sep + "BOTTOMLEVEL" + sep + explanation1);
			break;
		case TOPDERIVATIVE:
			sbf.append("#" + sep + "TOPDERIVATIVE" + sep + explanation1);
			break;
		case THRESHOLD:
			sbf.append("#" + sep + "THRESHOLD" + sep + explanation1);
			break;
		case GULPS:
			sbf.append("#" + sep + "GULPS_FLAT" + sep + explanation2);
			break;
		default:
			sbf.append("#" + sep + "UNDEFINED------------\n");
			break;
		}
		return sbf.toString();
	}

	public static String csvExportMeasuresOneType(Capillary cap, EnumCapillaryMeasures measureType, String sep) {
		StringBuffer sbf = new StringBuffer();
		sbf.append(cap.getKymographPrefix() + sep + cap.getKymographIndex() + sep);

		switch (measureType) {
		case TOPRAW:
			cap.getTopLevel().cvsExportYDataToRow(sbf, sep);
			break;
		case TOPLEVEL:
			if (cap.getTopCorrected() != null && cap.getTopCorrected().isThereAnyMeasuresDone())
				cap.getTopCorrected().cvsExportYDataToRow(sbf, sep);
			break;
		case BOTTOMLEVEL:
			cap.getBottomLevel().cvsExportYDataToRow(sbf, sep);
			break;
		case TOPDERIVATIVE:
			cap.getDerivative().cvsExportYDataToRow(sbf, sep);
			break;
		case THRESHOLD:
			if (cap.getThreshold() != null && cap.getThreshold().isThereAnyMeasuresDone())
				cap.getThreshold().cvsExportYDataToRow(sbf, sep);
			break;
		case GULPS:
			cap.getGulps().csvExportDataFlatToRow(sbf, sep);
			break;
		default:
			break;
		}
		sbf.append("\n");
		return sbf.toString();
	}

	public static String csvExportAlongTRow(Capillary cap, AlongT at, String sep) {
		if (at == null || at.getRoi() == null)
			return "";
		// String prefix = cap.getKymographPrefix() != null ? cap.getKymographPrefix() :
		// "";
		String prefix = cap.getRoiName();
		List<String> row = new ArrayList<>();
		row.add(prefix);
		row.add(Long.toString(at.getStart()));
		ROIType roiType = ROIPersistenceUtils.detectROIType(at.getRoi());
		row.add(roiType.toCsvString());
		ROI2D roi = at.getRoi();
		if (roi instanceof ROI2DPolyLine) {
			Polyline2D polyline = ((ROI2DPolyLine) roi).getPolyline2D();
			row.add(Integer.toString(polyline.npoints));
			for (int i = 0; i < polyline.npoints; i++) {
				row.add(Integer.toString((int) polyline.xpoints[i]));
				row.add(Integer.toString((int) polyline.ypoints[i]));
			}
		} else if (roi instanceof ROI2DLine) {
			Line2D line = ((ROI2DLine) roi).getLine();
			row.add("2");
			row.add(Integer.toString((int) line.getX1()));
			row.add(Integer.toString((int) line.getY1()));
			row.add(Integer.toString((int) line.getX2()));
			row.add(Integer.toString((int) line.getY2()));
		} else {
			return "";
		}
		return String.join(sep, row) + "\n";
	}

	public static boolean csvImportAlongTRow(Capillary cap, String[] data) {
		if (data == null || data.length < 5)
			return false;
		int i = 1; // 0 = cap_prefix (used by caller to find cap)
		long start = Long.parseLong(data[i].trim());
		i++;
		String roiTypeStr = data.length > i ? data[i] : "";
		i++;
		int npoints = Integer.parseInt(data[i]);
		i++;
		if (i + npoints * 2 > data.length)
			return false;
		ROI2D roi = null;
		if (npoints == 2 && (roiTypeStr.equalsIgnoreCase("line") || roiTypeStr.isEmpty())) {
			int x1 = Integer.parseInt(data[i]);
			int y1 = Integer.parseInt(data[i + 1]);
			int x2 = Integer.parseInt(data[i + 2]);
			int y2 = Integer.parseInt(data[i + 3]);
			Line2D.Double line = new Line2D.Double(x1, y1, x2, y2);
			ROI2DLine roiLine = new ROI2DLine(line);
			roiLine.setName(cap.getRoiName());
			roi = roiLine;
		} else if (npoints > 2) {
			double[] xpoints = new double[npoints];
			double[] ypoints = new double[npoints];
			for (int j = 0; j < npoints; j++) {
				xpoints[j] = Integer.parseInt(data[i + j * 2]);
				ypoints[j] = Integer.parseInt(data[i + j * 2 + 1]);
			}
			Polyline2D polyline = new Polyline2D(xpoints, ypoints, npoints);
			ROI2DPolyLine roiPolyline = new ROI2DPolyLine(polyline);
			roiPolyline.setName(cap.getRoiName());
			roi = roiPolyline;
		}
		if (roi != null) {
			AlongT at = new AlongT(start, roi);
			cap.addAlongTFromImport(at);
			return true;
		}
		return false;
	}

	public static void csvImportCapillaryDescription(Capillary cap, String[] data) {
		if (data == null || data.length < 3) {
			Logger.warn("CapillaryPersistence:csvImportCapillaryDescription() Insufficient data fields: "
					+ (data != null ? data.length : 0) + " (minimum 3 required)");
			return;
		}

		int i = 0;
		// Required fields (minimum 3: prefix, index, name)
		cap.setKymographPrefix(i < data.length ? data[i] : "");
		i++;
		try {
			cap.setKymographIndex(i < data.length && !data[i].isEmpty() ? Integer.valueOf(data[i]) : -1);
		} catch (NumberFormatException e) {
			cap.setKymographIndex(-1);
		}
		i++;
		cap.setKymographName(i < data.length ? data[i] : "");
		i++;

		// Optional fields (legacy format may only have 3 fields)
		cap.setKymographFileName(i < data.length && data[i] != null && !data[i].isEmpty() ? data[i] : "");
		i++;
		CapillaryProperties props = cap.getProperties();
		try {
			props.setCageID(i < data.length && data[i] != null && !data[i].isEmpty() ? Integer.valueOf(data[i]) : 0);
		} catch (NumberFormatException e) {
			props.setCageID(0);
		}
		i++;
		try {
			props.setNFlies(i < data.length && data[i] != null && !data[i].isEmpty() ? Integer.valueOf(data[i]) : 0);
		} catch (NumberFormatException e) {
			props.setNFlies(0);
		}
		i++;
		try {
			props.setVolume(i < data.length && data[i] != null && !data[i].isEmpty() ? Double.valueOf(data[i]) : 0.0);
		} catch (NumberFormatException e) {
			props.setVolume(0.0);
		}
		i++;
		try {
			props.setPixels(i < data.length && data[i] != null && !data[i].isEmpty() ? Integer.valueOf(data[i]) : 0);
		} catch (NumberFormatException e) {
			props.setPixels(0);
		}
		i++;
		props.setStimulus(i < data.length && data[i] != null ? data[i] : "");
		i++;
		props.setConcentration(i < data.length && data[i] != null ? data[i] : "");
		i++;
		props.setSide(i < data.length && data[i] != null ? data[i] : "");
		i++;

		// Load ROI information if present (new format with ROI coordinates)
		if (i < data.length && data[i] != null && !data[i].isEmpty()) {
			String roiName = data[i];
			i++;

			// Read ROI type (v2.1 format)
			@SuppressWarnings("unused")
			String roiTypeStr = "";
			if (i < data.length && !isNumeric(data[i])) {
				roiTypeStr = data[i];
				i++;
			}
			// Note: roiTypeStr is read for v2.1 format compatibility
			// Current reconstruction logic infers type from npoints (2=LINE, >2=POLYLINE)

			// Read number of points
			if (i < data.length) {
				int npoints = 0;
				try {
					npoints = Integer.valueOf(data[i]);
					i++;

					// Reconstruct ROI from coordinates if npoints > 0
					if (npoints > 0 && i + (npoints * 2) <= data.length) {
						if (npoints == 2) {
							// Line: 2 points
							int x1 = Integer.valueOf(data[i]);
							int y1 = Integer.valueOf(data[i + 1]);
							int x2 = Integer.valueOf(data[i + 2]);
							int y2 = Integer.valueOf(data[i + 3]);

							Line2D line = new Line2D.Double(x1, y1, x2, y2);
							ROI2DLine roiLine = new ROI2DLine(line);
							roiLine.setName(roiName);
							cap.setRoi(roiLine);
						} else {
							// Polyline: multiple points
							double[] xpoints = new double[npoints];
							double[] ypoints = new double[npoints];
							for (int j = 0; j < npoints; j++) {
								xpoints[j] = Integer.valueOf(data[i + j * 2]);
								ypoints[j] = Integer.valueOf(data[i + j * 2 + 1]);
							}

							Polyline2D polyline = new Polyline2D(xpoints, ypoints, npoints);
							ROI2DPolyLine roiPolyline = new ROI2DPolyLine(polyline);
							roiPolyline.setName(roiName);
							cap.setRoi(roiPolyline);
						}
					}
				} catch (NumberFormatException e) {
					// Invalid npoints, skip ROI reconstruction
				}
			}
		}
	}

	/**
	 * Checks if a string represents a numeric value.
	 * 
	 * @param str the string to check
	 * @return true if the string is numeric
	 */
	private static boolean isNumeric(String str) {
		if (str == null || str.trim().isEmpty()) {
			return false;
		}
		try {
			Integer.parseInt(str.trim());
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	public static void csvImportCapillaryData(Capillary cap, EnumCapillaryMeasures measureType, String[] data,
			boolean x, boolean y) {
		try {
			switch (measureType) {
			case TOPRAW:
				if (x && y)
					cap.getTopLevel().csvImportXYDataFromRow(data, 2);
				else if (!x && y)
					cap.getTopLevel().csvImportYDataFromRow(data, 2);
				break;
			case TOPLEVEL:
				if (x && y)
					cap.getTopCorrected().csvImportXYDataFromRow(data, 2);
				else if (!x && y)
					cap.getTopCorrected().csvImportYDataFromRow(data, 2);
				break;
			case BOTTOMLEVEL:
				if (x && y)
					cap.getBottomLevel().csvImportXYDataFromRow(data, 2);
				else if (!x && y)
					cap.getBottomLevel().csvImportYDataFromRow(data, 2);
				break;
			case TOPDERIVATIVE:
				if (x && y)
					cap.getDerivative().csvImportXYDataFromRow(data, 2);
				else if (!x && y)
					cap.getDerivative().csvImportYDataFromRow(data, 2);
				break;
			case THRESHOLD:
				if (x && y)
					cap.getThreshold().csvImportXYDataFromRow(data, 2);
				else if (!x && y)
					cap.getThreshold().csvImportYDataFromRow(data, 2);
				break;
			case GULPS:
				cap.getGulps().csvImportDataFromRow(data, 2);
				break;
			default:
				break;
			}
		} catch (Exception e) {
			String capId = cap.getKymographPrefix() != null ? cap.getKymographPrefix() : "unknown";
			String errorMsg = "CapillaryPersistence:csvImportCapillaryData() Error importing " + measureType
					+ " for capillary " + capId + " (data.length=" + data.length + "): " + e.getMessage();
			Logger.error(errorMsg, e);
			System.out.println("ERROR: " + errorMsg);
			e.printStackTrace();
		}
	}
}
