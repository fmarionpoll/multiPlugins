package plugins.fmp.multiSPOTS96.experiment.sequence;

import icy.roi.ROI2D;

/**
 * Represents an operation to be performed on ROIs.
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 */
public class ROIOperation {
    
    public enum Type {
        DISPLAY,
        REMOVE,
        CENTER,
        SELECT
    }
    
    private final Type type;
    private final String pattern;
    private final ROI2D roi;
    private final boolean visible;
    private final boolean selected;
    
    private ROIOperation(Builder builder) {
        this.type = builder.type;
        this.pattern = builder.pattern;
        this.roi = builder.roi;
        this.visible = builder.visible;
        this.selected = builder.selected;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static ROIOperation displayROIs(String pattern, boolean visible) {
        return builder().type(Type.DISPLAY).pattern(pattern).visible(visible).build();
    }
    
    public static ROIOperation removeROIs(String pattern) {
        return builder().type(Type.REMOVE).pattern(pattern).build();
    }
    
    public static ROIOperation centerOnROI(ROI2D roi) {
        return builder().type(Type.CENTER).roi(roi).build();
    }
    
    public static ROIOperation selectROI(ROI2D roi, boolean selected) {
        return builder().type(Type.SELECT).roi(roi).selected(selected).build();
    }
    
    public Type getType() { return type; }
    public String getPattern() { return pattern; }
    public ROI2D getRoi() { return roi; }
    public boolean isVisible() { return visible; }
    public boolean isSelected() { return selected; }
    
    public static class Builder {
        private Type type;
        private String pattern;
        private ROI2D roi;
        private boolean visible;
        private boolean selected;
        
        public Builder type(Type type) {
            this.type = type;
            return this;
        }
        
        public Builder pattern(String pattern) {
            this.pattern = pattern;
            return this;
        }
        
        public Builder roi(ROI2D roi) {
            this.roi = roi;
            return this;
        }
        
        public Builder visible(boolean visible) {
            this.visible = visible;
            return this;
        }
        
        public Builder selected(boolean selected) {
            this.selected = selected;
            return this;
        }
        
        public ROIOperation build() {
            if (type == null) {
                throw new IllegalStateException("Operation type must be specified");
            }
            return new ROIOperation(this);
        }
    }
} 