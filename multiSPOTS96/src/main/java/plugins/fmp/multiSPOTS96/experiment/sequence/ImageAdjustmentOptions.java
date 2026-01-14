package plugins.fmp.multiSPOTS96.experiment.sequence;

import java.awt.Rectangle;

/**
 * Configuration options for image adjustment operations.
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 */
public class ImageAdjustmentOptions {
    private final boolean adjustSize;
    private final boolean showProgress;
    private final Rectangle targetDimensions;
    private final String progressMessage;
    private final boolean preserveAspectRatio;
    
    private ImageAdjustmentOptions(Builder builder) {
        this.adjustSize = builder.adjustSize;
        this.showProgress = builder.showProgress;
        this.targetDimensions = builder.targetDimensions;
        this.progressMessage = builder.progressMessage;
        this.preserveAspectRatio = builder.preserveAspectRatio;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static ImageAdjustmentOptions defaultOptions() {
        return builder().build();
    }
    
    public static ImageAdjustmentOptions withSizeAdjustment(Rectangle targetDimensions) {
        return builder()
            .adjustSize(true)
            .targetDimensions(targetDimensions)
            .showProgress(true)
            .progressMessage("Adjusting image dimensions...")
            .build();
    }
    
    public static ImageAdjustmentOptions noAdjustment() {
        return builder()
            .adjustSize(false)
            .showProgress(false)
            .build();
    }
    
    public boolean isAdjustSize() { return adjustSize; }
    public boolean isShowProgress() { return showProgress; }
    public Rectangle getTargetDimensions() { return targetDimensions != null ? new Rectangle(targetDimensions) : null; }
    public String getProgressMessage() { return progressMessage; }
    public boolean isPreserveAspectRatio() { return preserveAspectRatio; }
    
    @Override
    public String toString() {
        return String.format("ImageAdjustmentOptions{adjustSize=%b, showProgress=%b, targetDim=%s, preserveAspect=%b}", 
                           adjustSize, showProgress, targetDimensions, preserveAspectRatio);
    }
    
    public static class Builder {
        private boolean adjustSize = false;
        private boolean showProgress = true;
        private Rectangle targetDimensions;
        private String progressMessage = "Processing images...";
        private boolean preserveAspectRatio = false;
        
        public Builder adjustSize(boolean adjustSize) {
            this.adjustSize = adjustSize;
            return this;
        }
        
        public Builder showProgress(boolean showProgress) {
            this.showProgress = showProgress;
            return this;
        }
        
        public Builder targetDimensions(Rectangle targetDimensions) {
            this.targetDimensions = targetDimensions;
            return this;
        }
        
        public Builder progressMessage(String progressMessage) {
            this.progressMessage = progressMessage;
            return this;
        }
        
        public Builder preserveAspectRatio(boolean preserveAspectRatio) {
            this.preserveAspectRatio = preserveAspectRatio;
            return this;
        }
        
        public ImageAdjustmentOptions build() {
            return new ImageAdjustmentOptions(this);
        }
    }
} 