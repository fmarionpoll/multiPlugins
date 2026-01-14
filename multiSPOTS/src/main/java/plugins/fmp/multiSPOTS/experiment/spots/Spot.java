package plugins.fmp.multiSPOTS.experiment.spots;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.w3c.dom.Node;

import icy.image.IcyBufferedImage;
import icy.roi.BooleanMask2D;
import icy.roi.ROI2D;
import icy.util.XMLUtil;
import plugins.fmp.multiSPOTS.series.BuildSeriesOptions;
import plugins.fmp.multiSPOTS.tools.ROI2D.ROI2DAlongT;
import plugins.fmp.multiSPOTS.tools.ROI2D.Utilities;
import plugins.fmp.multiSPOTS.tools.polyline.Level2D;
import plugins.fmp.multiSPOTS.tools.toExcel.EnumXLSColumnHeader;
import plugins.fmp.multiSPOTS.tools.toExcel.EnumXLSExportType;
import plugins.kernel.roi.roi2d.ROI2DPolyLine;
import plugins.kernel.roi.roi2d.ROI2DShape;

public class Spot implements Comparable<Spot> {

	private ROI2DShape spotRoi2D = null;
	private ROI2DShape spotRoi_old = null;
	private ArrayList<ROI2DAlongT> listRoiAlongT = new ArrayList<ROI2DAlongT>();
	public int kymographIndex = -1;

	public BooleanMask2D mask2DSpot = null;

	public int cageID = -1;
	public int cagePosition = 0;
	public int plateIndex = 0;
	public int plateColumn = 0;
	public int plateRow = 0;

	public String version = null;
	public String spotStim = new String("..");
	public String spotConc = new String("..");
	public int spotNFlies = 1;
	public double spotVolume = 1;
	public int spotNPixels = 1;
	public int spotRadius = 30;

	public int spotXCoord = -1;
	public int spotYCoord = -1;

	public boolean descriptionOK = false;
	public int versionInfos = 0;

	public BuildSeriesOptions limitsOptions = new BuildSeriesOptions();

	public int spot_CamData_T = -1;
	public int spot_Kymograph_T = -1;
	public String spot_filenameTIFF = null;
	public IcyBufferedImage spot_Image = null;

	public SpotMeasure sum_in = new SpotMeasure("sum");
	public SpotMeasure sum_clean = new SpotMeasure("clean");
	public SpotMeasure flyPresent = new SpotMeasure("flyPresent");
	public boolean valid = true;
	public boolean okToAnalyze = true;

	private final String ID_META = "metaMC";
	private final String ID_NFLIES = "nflies";

	private final String ID_CAGE = "cage_id";
	private final String ID_CAGEINDEX = "cage_index";
	private final String ID_PLATEINDEX = "plate_index";
	private final String ID_PLATECOL = "plate_col";
	private final String ID_PLATEROW = "plate_row";

	private final String ID_SPOTVOLUME = "volume";
	private final String ID_PIXELS = "pixels";
	private final String ID_RADIUS = "radius";
	private final String ID_XCOORD = "spotXCoord";
	private final String ID_YCOORD = "spotYCoord";
	private final String ID_STIMULUS = "stimulus";
	private final String ID_CONCENTRATION = "concentration";
	private final String ID_DESCOK = "descriptionOK";
	private final String ID_VERSIONINFOS = "versionInfos";
	private final String ID_INTERVALS = "INTERVALS";
	private final String ID_NINTERVALS = "nintervals";
	private final String ID_INTERVAL = "interval_";
	private final String ID_INDEXIMAGE = "indexImageMC";
	private final String ID_VERSION = "version";
	private final String ID_VERSIONNUM = "1.0.0";

	private Color[] spotColors = new Color[] { new Color(0xFF, 0x55, 0x55), new Color(0x55, 0x55, 0xFF),
			new Color(0x55, 0xFF, 0x55), new Color(0xFF, 0xFF, 0x55), new Color(0xFF, 0x55, 0xFF),
			new Color(0x55, 0xFF, 0xFF), Color.pink, Color.gray };

	// ----------------------------------------------------

	public Spot(ROI2DShape roi) {
		this.spotRoi2D = roi;
	}

	public Spot() {
	}

	@Override
	public int compareTo(Spot o) {
		if (o != null)
			return (this.spotRoi2D.getName()).compareTo(o.spotRoi2D.getName());
		return 1;
	}

	// ------------------------------------------

	public void copySpot(Spot spotFrom) {
		version = spotFrom.version;
		spotRoi2D = (ROI2DShape) spotFrom.spotRoi2D.getCopy();

		plateIndex = spotFrom.plateIndex;
		plateColumn = spotFrom.plateColumn;
		plateRow = spotFrom.plateRow;

		cageID = spotFrom.cageID;
		cagePosition = spotFrom.cagePosition;

		spotNFlies = spotFrom.spotNFlies;
		spotVolume = spotFrom.spotVolume;
		spotStim = spotFrom.spotStim;
		spotConc = spotFrom.spotConc;

		spotNPixels = spotFrom.spotNPixels;
		spotRadius = spotFrom.spotRadius;
		spotXCoord = spotFrom.spotXCoord;
		spotYCoord = spotFrom.spotYCoord;
		limitsOptions = spotFrom.limitsOptions;

		sum_in.copyLevel2D(spotFrom.sum_in);
		sum_clean.copyLevel2D(spotFrom.sum_clean);
//		sum_out.copyLevel2D(spotFrom.sum_out);
//		sum_diff.copyLevel2D(spotFrom.sum_diff);
		flyPresent.copyLevel2D(spotFrom.flyPresent);
	}

	public ROI2D getRoi() {
		return spotRoi2D;
	}

	public ROI2D getRoi_old() {
		return spotRoi_old;
	}

	public void setRoi(ROI2DShape roi) {
		this.spotRoi2D = roi;
		listRoiAlongT.clear();
	}

	public void setRoi_old(ROI2DShape roi) {
		this.spotRoi_old = roi;
	}

	public String getPlateCoordinatesAsString() {
		String plateCoords = getCharForNumber(plateRow) + "_" + Integer.toString(plateColumn);
		return plateCoords;
	}

	private String getCharForNumber(int i) {
		return i > -1 && i < 26 ? String.valueOf((char) (i + 'A')) : null;
	}

	public void setSpotRoi_InColorAccordingToSpotIndex(int index) {
		Color value = spotColors[index % 8];
		spotRoi2D.setColor(value);
	}

	public String getCagePosition(EnumXLSExportType xlsExportOption) {
		String value = null;
		switch (xlsExportOption) {
		case DISTANCE:
		case ISALIVE:
			value = String.valueOf(cagePosition) + "(T=B)";
			break;
		case TOPLEVELDELTA_LR:
		case TOPLEVEL_LR:
			if (cagePosition == 0)
				value = "sum";
			else if (cagePosition == 1)
				value = "PI";
			break;
		case XYIMAGE:
		case XYTOPCAGE:
		case XYTIPCAPS:
			if (cagePosition == 0)
				value = "x";
			else
				value = "y";
			break;
		default:
			value = String.valueOf(cagePosition);
			break;
		}
		return value;
	}

	public String getSpotField(EnumXLSColumnHeader fieldEnumCode) {
		String stringValue = null;
		switch (fieldEnumCode) {
		case CAP_STIM:
			stringValue = spotStim;
			break;
		case CAP_CONC:
			stringValue = spotConc;
			break;
		default:
			break;
		}
		return stringValue;
	}

	public void setSpotField(EnumXLSColumnHeader fieldEnumCode, String stringValue) {
		switch (fieldEnumCode) {
		case CAP_STIM:
			spotStim = stringValue;
			break;
		case CAP_CONC:
			spotConc = stringValue;
			break;
		default:
			break;
		}
	}

	public Point2D getSpotCenter() {
		Point pt = spotRoi2D.getPosition();
		Rectangle rect = spotRoi2D.getBounds();
		pt.translate(rect.height / 2, rect.width / 2);
		return pt;
	}

	private SpotMeasure getSpotArea(EnumXLSExportType option) {
		switch (option) {
		case AREA_SUM:
		case AREA_SUM_LR:
			return sum_in;
		case AREA_SUMCLEAN:
		case AREA_SUMCLEAN_LR:
			return sum_clean;
//		case AREA_OUT:
//			return sum_out;
//		case AREA_DIFF:
//			return sum_diff;
		case AREA_FLYPRESENT:
			return flyPresent;
		default:
			return null;
		}
	}

	public boolean isL() {
		int i = plateIndex % 2;
		return (0 == i);
	}

	public boolean isR() {
		int i = plateIndex % 2;
		return (1 == i);
	}

	public boolean isIndexSelected(List<Integer> selectedIndexes) {
		if (selectedIndexes == null || selectedIndexes.size() < 1)
			return true;

		for (int i : selectedIndexes) {
			if (i == plateIndex) {
				return true;
			}
		}
		return false;
	}

	// -----------------------------------------

	public boolean isThereAnyMeasuresDone(EnumXLSExportType option) {
		SpotMeasure spotArea = getSpotArea(option);
		if (spotArea != null)
			return spotArea.isThereAnyMeasuresDone();
		return false;
	}

	public ArrayList<Double> getSpotMeasuresForXLSPass1(EnumXLSExportType option, long seriesBinMs, long outputBinMs) {
		SpotMeasure spotArea = getSpotArea(option);
		if (spotArea != null)
			return spotArea.getLevel2D_Y_subsampled(seriesBinMs, outputBinMs);
		return null;
	}

	public void cropSpotMeasuresToNPoints(int npoints) {
		cropSpotMeasureToNPoints(sum_in, npoints);
		cropSpotMeasureToNPoints(sum_clean, npoints);
//		cropSpotMeasureToNPoints(sum_out, npoints);
//		cropSpotMeasureToNPoints(sum_diff, npoints);
		cropSpotMeasureToNPoints(flyPresent, npoints);
	}

	private void cropSpotMeasureToNPoints(SpotMeasure spotMeasure, int npoints) {
		if (spotMeasure.getLevel2DNPoints() > 0)
			spotMeasure.cropLevel2DToNPoints(npoints);
	}

	public void restoreClippedSpotMeasures() {
		restoreClippedMeasures(sum_in);
		restoreClippedMeasures(sum_clean);
//		restoreClippedMeasures(sum_out);
//		restoreClippedMeasures(sum_diff);
		restoreClippedMeasures(flyPresent);
	}

	private void restoreClippedMeasures(SpotMeasure spotMeasure) {
		if (spotMeasure.getLevel2DNPoints() > 0)
			spotMeasure.restoreCroppedLevel2D();
	}

	public void transferROIsMeasuresToLevel2D() {
		sum_in.transferROItoLevel2D();
		sum_clean.transferROItoLevel2D();
//		sum_out.transferROItoLevel2D();
//		sum_diff.transferROItoLevel2D();
		flyPresent.transferROItoLevel2D();
	}

	// -----------------------------------------------------------------------------

	public boolean loadFromXML_SpotOnly(Node node) {
		final Node nodeMeta = XMLUtil.getElement(node, ID_META);
		boolean flag = (nodeMeta != null);
		if (flag) {
			version = XMLUtil.getElementValue(nodeMeta, ID_VERSION, "0.0.0");
			cageID = XMLUtil.getElementIntValue(nodeMeta, ID_INDEXIMAGE, cageID);

			descriptionOK = XMLUtil.getElementBooleanValue(nodeMeta, ID_DESCOK, false);
			versionInfos = XMLUtil.getElementIntValue(nodeMeta, ID_VERSIONINFOS, 0);
			spotNFlies = XMLUtil.getElementIntValue(nodeMeta, ID_NFLIES, spotNFlies);

			cageID = XMLUtil.getElementIntValue(nodeMeta, ID_CAGE, cageID);
			cagePosition = XMLUtil.getElementIntValue(nodeMeta, ID_CAGEINDEX, cagePosition);

			plateIndex = XMLUtil.getElementIntValue(nodeMeta, ID_PLATEINDEX, plateIndex);
			plateColumn = XMLUtil.getElementIntValue(nodeMeta, ID_PLATECOL, plateColumn);
			plateRow = XMLUtil.getElementIntValue(nodeMeta, ID_PLATEROW, plateRow);

			spotVolume = XMLUtil.getElementDoubleValue(nodeMeta, ID_SPOTVOLUME, Double.NaN);
			spotNPixels = XMLUtil.getElementIntValue(nodeMeta, ID_PIXELS, 5);
			spotRadius = XMLUtil.getElementIntValue(nodeMeta, ID_RADIUS, 30);
			spotXCoord = XMLUtil.getElementIntValue(nodeMeta, ID_XCOORD, -1);
			spotYCoord = XMLUtil.getElementIntValue(nodeMeta, ID_YCOORD, -1);
			spotStim = XMLUtil.getElementValue(nodeMeta, ID_STIMULUS, ID_STIMULUS);
			spotConc = XMLUtil.getElementValue(nodeMeta, ID_CONCENTRATION, ID_CONCENTRATION);

			spotRoi2D = (ROI2DShape) Utilities.loadFromXML_ROI(nodeMeta);
			setSpotRoi_InColorAccordingToSpotIndex(cagePosition);
			limitsOptions.loadFromXML(nodeMeta);

			loadFromXML_SpotAlongT(node);
		}
		return flag;
	}

	private boolean loadFromXML_SpotAlongT(Node node) {
		listRoiAlongT.clear();
		final Node nodeMeta2 = XMLUtil.getElement(node, ID_INTERVALS);
		if (nodeMeta2 == null)
			return false;
		int nitems = XMLUtil.getElementIntValue(nodeMeta2, ID_NINTERVALS, 0);
		if (nitems > 0) {
			for (int i = 0; i < nitems; i++) {
				Node node_i = XMLUtil.setElement(nodeMeta2, ID_INTERVAL + i);
				ROI2DAlongT roiInterval = new ROI2DAlongT();
				roiInterval.loadFromXML(node_i);
				listRoiAlongT.add(roiInterval);

				if (i == 0) {
					spotRoi2D = (ROI2DShape) listRoiAlongT.get(0).getRoi_in();
				}
			}
		}
		return true;
	}

	public boolean saveToXML_SpotOnly(Node node) {
		final Node nodeMeta = XMLUtil.setElement(node, ID_META);
		if (nodeMeta == null)
			return false;
		if (version == null)
			version = ID_VERSIONNUM;
		XMLUtil.setElementValue(nodeMeta, ID_VERSION, version);
		XMLUtil.setElementIntValue(nodeMeta, ID_INDEXIMAGE, cageID);

		XMLUtil.setElementBooleanValue(nodeMeta, ID_DESCOK, descriptionOK);
		XMLUtil.setElementIntValue(nodeMeta, ID_VERSIONINFOS, versionInfos);
		XMLUtil.setElementIntValue(nodeMeta, ID_NFLIES, spotNFlies);

		XMLUtil.setElementIntValue(nodeMeta, ID_CAGE, cageID);
		XMLUtil.setElementIntValue(nodeMeta, ID_CAGEINDEX, cagePosition);

		XMLUtil.setElementIntValue(nodeMeta, ID_PLATEINDEX, plateIndex);
		XMLUtil.setElementIntValue(nodeMeta, ID_PLATECOL, plateColumn);
		XMLUtil.setElementIntValue(nodeMeta, ID_PLATEROW, plateRow);

		XMLUtil.setElementDoubleValue(nodeMeta, ID_SPOTVOLUME, spotVolume);
		XMLUtil.setElementIntValue(nodeMeta, ID_PIXELS, spotNPixels);
		XMLUtil.setElementIntValue(nodeMeta, ID_RADIUS, spotRadius);
		XMLUtil.setElementIntValue(nodeMeta, ID_XCOORD, spotXCoord);
		XMLUtil.setElementIntValue(nodeMeta, ID_YCOORD, spotYCoord);
		XMLUtil.setElementValue(nodeMeta, ID_STIMULUS, spotStim);
		XMLUtil.setElementValue(nodeMeta, ID_CONCENTRATION, spotConc);

		Utilities.saveToXML_ROI(nodeMeta, spotRoi2D);

		boolean flag = saveToXML_SpotAlongT(node);
		return flag;
	}

	private boolean saveToXML_SpotAlongT(Node node) {
		final Node nodeMeta2 = XMLUtil.setElement(node, ID_INTERVALS);
		if (nodeMeta2 == null)
			return false;
		int nitems = listRoiAlongT.size();
		XMLUtil.setElementIntValue(nodeMeta2, ID_NINTERVALS, nitems);
		if (nitems > 0) {
			for (int i = 0; i < nitems; i++) {
				Node node_i = XMLUtil.setElement(nodeMeta2, ID_INTERVAL + i);
				listRoiAlongT.get(i).saveToXML(node_i);
			}
		}
		return true;
	}

	// --------------------------------------------

	public List<ROI2DAlongT> getROIAlongTList() {
		if (listRoiAlongT.size() < 1)
			initROIAlongTList();
		return listRoiAlongT;
	}

	public ROI2DAlongT getROIAtT(long t) {
		if (listRoiAlongT.size() < 1)
			initROIAlongTList();

		ROI2DAlongT spotRoi = null;
		for (ROI2DAlongT item : listRoiAlongT) {
			if (t < item.getT())
				break;
			spotRoi = item;
		}
		return spotRoi;
	}

	public void removeROIAlongTListItem(long t) {
		ROI2DAlongT itemFound = null;
		for (ROI2DAlongT item : listRoiAlongT) {
			if (t != item.getT())
				continue;
			itemFound = item;
		}
		if (itemFound != null)
			listRoiAlongT.remove(itemFound);
	}

	private void initROIAlongTList() {
		listRoiAlongT.add(new ROI2DAlongT(0, spotRoi2D));
	}

	// --------------------------------------------

	public void adjustLevel2DMeasuresToImageWidth(int imageWidth) {
		sum_in.adjustLevel2DToImageWidth(imageWidth);
		sum_clean.adjustLevel2DToImageWidth(imageWidth);
//		sum_out.adjustLevel2DToImageWidth(imageWidth);
//		sum_diff.adjustLevel2DToImageWidth(imageWidth);
		flyPresent.adjustLevel2DToImageWidth(imageWidth);
	}

	public void cropLevel2DMeasuresToImageWidth(int imageWidth) {
		sum_in.cropLevel2DToNPoints(imageWidth);
		sum_clean.cropLevel2DToNPoints(imageWidth);
//		sum_out.cropLevel2DToNPoints(imageWidth);
//		sum_diff.cropLevel2DToNPoints(imageWidth);
		flyPresent.cropLevel2DToNPoints(imageWidth);
	}

	public void initLevel2DMeasures() {
		sum_in.initLevel2D_fromMeasureValues(getRoi().getName());
		sum_clean.initLevel2D_fromMeasureValues(getRoi().getName());
//		sum_out.initLevel2D_fromMeasureValues(getRoi_in().getName());
//		sum_diff.initLevel2D_fromMeasureValues(getRoi_in().getName());
		flyPresent.initLevel2D_fromBooleans(getRoi().getName());
	}

	public void buildRunningMedianFromSumLevel2D(int imageHeight) {
		int span = 10;
		if (sum_in.values != null)
			sum_clean.buildRunningMedian(span, sum_in.values);
		else
			sum_clean.buildRunningMedian(span, sum_in.getLevel2D().ypoints);
		sum_clean.initLevel2D_fromMeasureValues(sum_clean.getName());
	}

	public List<ROI2D> transferSpotMeasuresToROIs(int imageHeight) {
		List<ROI2D> measuresRoisList = new ArrayList<ROI2D>();
		if (sum_in.getLevel2DNPoints() != 0)
			measuresRoisList.add(sum_in.getROIForImage(spotRoi2D.getName(), spot_Kymograph_T, imageHeight));
		if (sum_clean.getLevel2DNPoints() != 0)
			measuresRoisList.add(sum_clean.getROIForImage(spotRoi2D.getName(), spot_Kymograph_T, imageHeight));
//		if (sum_out.getLevel2DNPoints() != 0)
//			measuresRoisList.add(sum_out.getROIForImage(spotRoi_in.getName(), spot_Kymograph_T, imageHeight));
//		if (sum_diff.getLevel2DNPoints() != 0)
//			measuresRoisList.add(sum_diff.getROIForImage(spotRoi_in.getName(), spot_Kymograph_T, imageHeight));
		if (flyPresent.getLevel2DNPoints() != 0)
			measuresRoisList.add(flyPresent.getROIForImage(spotRoi2D.getName(), spot_Kymograph_T, 10));
		return measuresRoisList;
	}

	public void transferROItoMeasures(ROI2D roi, int imageHeight) {
		String name = roi.getName();
		if (name.contains(sum_in.getName())) {
			transferROItoMeasureValue(roi, imageHeight, sum_in);
		} else if (name.contains(sum_clean.getName())) {
			transferROItoMeasureValue(roi, imageHeight, sum_clean);
//		} else if (name.contains(sum_out.getName())) {
//			transferROItoMeasureValue(roi, imageHeight, sum_out);
//		} else if (name.contains(sum_diff.getName())) {
//			transferROItoMeasureValue(roi, imageHeight, sum_diff);
		} else if (name.contains(flyPresent.getName())) {
			transferROItoMeasureBoolean(roi, flyPresent);
		}
	}

	private void transferROItoMeasureValue(ROI2D roi, int imageHeight, SpotMeasure spotMeasure) {
		if (roi instanceof ROI2DPolyLine) {
			Level2D level2D = new Level2D(((ROI2DPolyLine) roi).getPolyline2D());
			level2D.multiply_Y(imageHeight);
			spotMeasure.setLevel2D(level2D);
		}
	}

	private void transferROItoMeasureBoolean(ROI2D roi, SpotMeasure spotMeasure) {
		if (roi instanceof ROI2DPolyLine) {
			Level2D level2D = new Level2D(((ROI2DPolyLine) roi).getPolyline2D());
			level2D.threshold_Y(1.);
			spotMeasure.setLevel2D(level2D);
		}
	}

	// -----------------------------------------------------------------------------

	public String csvExportSpotArrayHeader(String csvSep) {
		StringBuffer sbf = new StringBuffer();
		sbf.append("#" + csvSep + "SPOTS" + csvSep + "describe each spot\n");
		List<String> row2 = Arrays.asList("index", "name", "cage", "nflies", "volume", "npixel", "radius", "stim",
				"conc", "side");
		sbf.append(String.join(csvSep, row2));
		sbf.append("\n");
		return sbf.toString();
	}

	public String csvExportDescription(String csvSep) {
		StringBuffer sbf = new StringBuffer();
		List<String> row = Arrays.asList(String.valueOf(plateIndex), getRoi().getName(), String.valueOf(cageID),
				String.valueOf(spotNFlies), String.valueOf(spotVolume), String.valueOf(spotNPixels),
				String.valueOf(spotRadius), spotStim.replace(",", "."), spotConc.replace(",", "."),
				String.valueOf(cagePosition));
		sbf.append(String.join(csvSep, row));
		sbf.append("\n");
		return sbf.toString();
	}

	public String csvExportMeasures_SectionHeader(EnumSpotMeasures measureType, String csvSep) {
		StringBuffer sbf = new StringBuffer();
		List<String> listExplanation1 = Arrays.asList("\n name", "index", "npts", "yi", "\n");
		String explanation1 = String.join(csvSep, listExplanation1);
		switch (measureType) {
		case AREA_SUM:
		case AREA_SUMCLEAN:
		case AREA_FLYPRESENT:
		case AREA_OUT:
		case AREA_DIFF:
			sbf.append("#" + csvSep + measureType.toString() + csvSep + explanation1);
			break;
		default:
			sbf.append("#" + csvSep + "UNDEFINED" + csvSep + "------------\n");
			break;
		}
		return sbf.toString();
	}

	public String csvExportMeasures_OneType(EnumSpotMeasures measureType, String csvSep) {
		StringBuffer sbf = new StringBuffer();
		sbf.append(spotRoi2D.getName() + csvSep + plateIndex + csvSep);
		switch (measureType) {
		case AREA_SUM:
			sum_in.cvsExportYDataToRow(sbf, csvSep);
			break;
		case AREA_SUMCLEAN:
			sum_clean.cvsExportYDataToRow(sbf, csvSep);
			break;
//		case AREA_OUT:
//			sum_out.cvsExportYDataToRow(sbf, csvSep);
//			break;
//		case AREA_DIFF:
//			sum_diff.cvsExportYDataToRow(sbf, csvSep);
//			break;
		case AREA_FLYPRESENT:
			flyPresent.cvsExportYDataToRow(sbf, csvSep);
			break;
		default:
			break;
		}
		sbf.append("\n");
		return sbf.toString();
	}

	public void csvImportDescription(String[] data, boolean dummyColumn) {
		int i = dummyColumn ? 1 : 0;
		plateIndex = Integer.valueOf(data[i]);
		i++;
		spotRoi2D.setName(data[i]);
		i++;
		cageID = Integer.valueOf(data[i]);
		i++;
		spotNFlies = Integer.valueOf(data[i]);
		i++;
		spotVolume = Double.valueOf(data[i]);
		i++;
		spotNPixels = Integer.valueOf(data[i]);
		i++;
		spotRadius = Integer.valueOf(data[i]);
		i++;
		spotStim = data[i];
		i++;
		spotConc = data[i];
		i++;
		cagePosition = Integer.valueOf(data[i]);
	}

	public void csvImportMeasures_OneType(EnumSpotMeasures measureType, String[] data, boolean x, boolean y) {
		if (x && y) {
			switch (measureType) {
			case AREA_SUM:
				sum_in.csvImportXYDataFromRow(data, 2);
				break;
			case AREA_SUMCLEAN:
				sum_clean.csvImportXYDataFromRow(data, 2);
				break;
//			case AREA_OUT:
//				sum_out.csvImportXYDataFromRow(data, 2);
//				break;
//			case AREA_DIFF:
//				sum_diff.csvImportXYDataFromRow(data, 2);
//				break;
			case AREA_FLYPRESENT:
				flyPresent.csvImportXYDataFromRow(data, 2);
				break;
			default:
				break;
			}
		} else if (!x && y) {
			switch (measureType) {
			case AREA_SUM:
				sum_in.csvImportYDataFromRow(data, 2);
				break;
			case AREA_SUMCLEAN:
				sum_clean.csvImportYDataFromRow(data, 2);
				break;
//			case AREA_OUT:
//				sum_out.csvImportYDataFromRow(data, 2);
//				break;
//			case AREA_DIFF:
//				sum_diff.csvImportYDataFromRow(data, 2);
//				break;
			case AREA_FLYPRESENT:
				flyPresent.csvImportYDataFromRow(data, 2);
				break;
			default:
				break;
			}
		}
	}

}
