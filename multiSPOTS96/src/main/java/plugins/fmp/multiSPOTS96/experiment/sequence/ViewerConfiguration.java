package plugins.fmp.multiSPOTS96.experiment.sequence;

import java.awt.Rectangle;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformEnums;

/**
 * Configuration class for viewer settings and operations.
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 */
public class ViewerConfiguration {
    private final Rectangle displayRectangle;
    private final boolean showOverlay;
    private final int threshold;
    private final ImageTransformEnums transform;
    private final boolean ifGreater;
    
    private ViewerConfiguration(Builder builder) {
        this.displayRectangle = builder.displayRectangle;
        this.showOverlay = builder.showOverlay;
        this.threshold = builder.threshold;
        this.transform = builder.transform;
        this.ifGreater = builder.ifGreater;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static ViewerConfiguration defaultConfig() {
        return builder().build();
    }
    
    public static ViewerConfiguration withOverlay(int threshold, ImageTransformEnums transform, boolean ifGreater) {
        return builder()
            .showOverlay(true)
            .threshold(threshold)
            .transform(transform)
            .ifGreater(ifGreater)
            .build();
    }
    
    public Rectangle getDisplayRectangle() { return displayRectangle; }
    public boolean isShowOverlay() { return showOverlay; }
    public int getThreshold() { return threshold; }
    public ImageTransformEnums getTransform() { return transform; }
    public boolean isIfGreater() { return ifGreater; }
    
    public static class Builder {
        private Rectangle displayRectangle;
        private boolean showOverlay = false;
        private int threshold = 0;
        private ImageTransformEnums transform;
        private boolean ifGreater = true;
        
        public Builder displayRectangle(Rectangle rectangle) {
            this.displayRectangle = rectangle;
            return this;
        }
        
        public Builder showOverlay(boolean showOverlay) {
            this.showOverlay = showOverlay;
            return this;
        }
        
        public Builder threshold(int threshold) {
            this.threshold = threshold;
            return this;
        }
        
        public Builder transform(ImageTransformEnums transform) {
            this.transform = transform;
            return this;
        }
        
        public Builder ifGreater(boolean ifGreater) {
            this.ifGreater = ifGreater;
            return this;
        }
        
        public ViewerConfiguration build() {
            return new ViewerConfiguration(this);
        }
    }
} 