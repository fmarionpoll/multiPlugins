package plugins.fmp.multiSPOTS.tools.ROI2D;

import java.awt.Point;
import java.util.ArrayList;

import org.w3c.dom.Node;

import icy.file.xml.XMLPersistent;
import icy.roi.BooleanMask2D;
import icy.roi.ROI2D;
import icy.util.XMLUtil;

public class ROI2DAlongT implements XMLPersistent {
	private int index = 0;
	private long t = 0;
	private ArrayList<ArrayList<int[]>> masksList = null;

	private ROI2D roi_in = null;
	public BooleanMask2D mask2D_in = null;
	public BooleanMask2D mask2D_in_nofly = null;
	public Point[] mask2DPoints_in = null;

//	private ROI2D roi_out = null;	
//	private BooleanMask2D mask2D_out = null;	
//	public Point[] mask2DPoints_out = null;

	private final String ID_META = "metaT";
	private final String ID_INDEX = "indexT";
	private final String ID_START = "startT";

	public ROI2DAlongT(long t, ROI2D roi) {
		setRoi_in(roi);
		this.t = t;
	}

	public ROI2DAlongT() {
	}

	public long getT() {
		return t;
	}

	public void setT(long t) {
		this.t = t;
	}

	public ROI2D getRoi_in() {
		return roi_in;
	}

	public void setRoi_in(ROI2D roi) {
		this.roi_in = (ROI2D) roi.getCopy();
	}

	public ArrayList<ArrayList<int[]>> getMasksList() {
		return masksList;
	}

	public void setMasksList(ArrayList<ArrayList<int[]>> masksList) {
		this.masksList = masksList;
	}

	public void buildMask2DFromRoi_in() {
		try {
			mask2D_in = roi_in.getBooleanMask2D(0, 0, 1, true); // z, t, c, inclusive
			mask2DPoints_in = mask2D_in.getPoints();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public BooleanMask2D getMask2D_in() {
		return mask2D_in;
	}

	public void setMask2D_in(BooleanMask2D mask2D) {
		mask2D_in = mask2D;
	}

	@Override
	public boolean loadFromXML(Node node) {
		final Node nodeMeta = XMLUtil.getElement(node, ID_META);
		if (nodeMeta == null)
			return false;

		index = XMLUtil.getElementIntValue(nodeMeta, ID_INDEX, 0);
		t = XMLUtil.getElementLongValue(nodeMeta, ID_START, 0);
		roi_in = Utilities.loadFromXML_ROI(nodeMeta);
		return true;
	}

	@Override
	public boolean saveToXML(Node node) {
		final Node nodeMeta = XMLUtil.setElement(node, ID_META);
		if (nodeMeta == null)
			return false;
		XMLUtil.setElementIntValue(nodeMeta, ID_INDEX, index);
		XMLUtil.setElementLongValue(nodeMeta, ID_START, t);
		Utilities.saveToXML_ROI(nodeMeta, roi_in);
		return true;
	}

}
