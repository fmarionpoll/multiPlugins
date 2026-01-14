package plugins.fmp.multiSPOTS96.experiment.sequence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import icy.canvas.Canvas2D;
import icy.canvas.IcyCanvas;
import icy.canvas.Layer;
import icy.gui.viewer.Viewer;
import icy.roi.ROI;
import icy.roi.ROI2D;
import icy.sequence.Sequence;
import plugins.fmp.multiSPOTS96.tools.Comparators;

public class ROIManager {
    private static final Logger LOGGER = Logger.getLogger(ROIManager.class.getName());
    
    public ROIManager() {
    }
    
    public void displaySpecificROIs(Sequence seq, boolean isVisible, String pattern) {
        if (seq == null) {
            LOGGER.warning("Cannot display ROIs: sequence is null");
            return;
        }
        
        Viewer v = seq.getFirstViewer();
        if (v == null) {
            LOGGER.warning("Cannot display ROIs: viewer is null");
            return;
        }
        
        IcyCanvas canvas = v.getCanvas();
        List<Layer> layers = canvas.getLayers(false);
        if (layers == null) {
            return;
        }
        
        for (Layer layer : layers) {
            ROI roi = layer.getAttachedROI();
            if (roi == null) {
                continue;
            }
            String name = roi.getName();
            if (name != null && name.contains(pattern)) {
                layer.setVisible(isVisible);
            }
        }
    }
    
    public ArrayList<ROI2D> getROIsContainingString(Sequence seq, String pattern) {
        if (seq == null) {
            LOGGER.warning("Cannot get ROIs: sequence is null");
            return new ArrayList<>();
        }
        
        ArrayList<ROI2D> roiList = seq.getROI2Ds();
        Collections.sort(roiList, new Comparators.ROI_Name());
        
        ArrayList<ROI2D> matchingROIs = new ArrayList<>();
        for (ROI2D roi : roiList) {
            if (roi.getName() != null && roi.getName().contains(pattern)) {
                matchingROIs.add(roi);
            }
        }
        return matchingROIs;
    }
    
    public void removeROIsContainingString(Sequence seq, String pattern) {
        if (seq == null) {
            LOGGER.warning("Cannot remove ROIs: sequence is null");
            return;
        }
        
        List<ROI> roiList = seq.getROIs();
        Collections.sort(roiList, new Comparators.ROI_Name());
        
        List<ROI> matchingROIs = new ArrayList<>();
        for (ROI roi : roiList) {
            if (roi.getName() != null && roi.getName().contains(pattern)) {
                matchingROIs.add(roi);
            }
        }
        
        if (!matchingROIs.isEmpty()) {
            seq.removeROIs(matchingROIs, false);
        }
    }
    
    public void centerOnRoi(Sequence seq, ROI2D roi) {
        if (seq == null || roi == null) {
            LOGGER.warning("Cannot center on ROI: sequence or ROI is null");
            return;
        }
        
        Viewer v = seq.getFirstViewer();
        if (v == null) {
            LOGGER.warning("Cannot center on ROI: viewer is null");
            return;
        }
        
        try {
            Canvas2D canvas = (Canvas2D) v.getCanvas();
            canvas.centerOn(roi.getBounds());
        } catch (ClassCastException e) {
            LOGGER.warning("Cannot center on ROI: canvas is not Canvas2D");
        }
    }
    
    public void selectRoi(Sequence seq, ROI2D roi, boolean select) {
        if (seq == null || roi == null) {
            LOGGER.warning("Cannot select ROI: sequence or ROI is null");
            return;
        }
        
        if (select) {
            seq.setSelectedROI(roi);
        } else {
            seq.setSelectedROI(null);
        }
    }
    
    public void clearAllROIs(Sequence seq) {
        if (seq == null) {
            LOGGER.warning("Cannot clear ROIs: sequence is null");
            return;
        }
        seq.removeAllROI();
    }
    
    public void addROI(Sequence seq, ROI roi) {
        if (seq == null || roi == null) {
            LOGGER.warning("Cannot add ROI: sequence or ROI is null");
            return;
        }
        seq.addROI(roi);
    }
} 