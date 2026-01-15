package plugins.fmp.multiSPOTS96.experiment.sequence;

import java.awt.Rectangle;
import java.util.List;

/**
 * Immutable data class containing kymograph information and statistics.
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 */
public class KymographInfo {
    private final int totalImages;
    private final int maxWidth;
    private final int maxHeight;
    private final int validImages;
    private final int invalidImages;
    private final boolean isLoading;
    private final Rectangle maxDimensions;
    private final List<String> imageNames;
    
    private KymographInfo(Builder builder) {
        this.totalImages = builder.totalImages;
        this.maxWidth = builder.maxWidth;
        this.maxHeight = builder.maxHeight;
        this.validImages = builder.validImages;
        this.invalidImages = builder.invalidImages;
        this.isLoading = builder.isLoading;
        this.maxDimensions = new Rectangle(0, 0, builder.maxWidth, builder.maxHeight);
        this.imageNames = builder.imageNames != null ? List.copyOf(builder.imageNames) : List.of();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public int getTotalImages() { return totalImages; }
    public int getMaxWidth() { return maxWidth; }
    public int getMaxHeight() { return maxHeight; }
    public int getValidImages() { return validImages; }
    public int getInvalidImages() { return invalidImages; }
    public boolean isLoading() { return isLoading; }
    public Rectangle getMaxDimensions() { return new Rectangle(maxDimensions); }
    public List<String> getImageNames() { return imageNames; }
    
    public boolean hasValidImages() {
        return validImages > 0;
    }
    
    public double getValidImageRatio() {
        return totalImages > 0 ? (double) validImages / totalImages : 0.0;
    }
    
    @Override
    public String toString() {
        return String.format("KymographInfo{total=%d, valid=%d, invalid=%d, maxDim=%dx%d, loading=%b}", 
                           totalImages, validImages, invalidImages, maxWidth, maxHeight, isLoading);
    }
    
    public static class Builder {
        private int totalImages;
        private int maxWidth;
        private int maxHeight;
        private int validImages;
        private int invalidImages;
        private boolean isLoading;
        private List<String> imageNames;
        
        public Builder totalImages(int totalImages) {
            this.totalImages = totalImages;
            return this;
        }
        
        public Builder maxWidth(int maxWidth) {
            this.maxWidth = maxWidth;
            return this;
        }
        
        public Builder maxHeight(int maxHeight) {
            this.maxHeight = maxHeight;
            return this;
        }
        
        public Builder validImages(int validImages) {
            this.validImages = validImages;
            return this;
        }
        
        public Builder invalidImages(int invalidImages) {
            this.invalidImages = invalidImages;
            return this;
        }
        
        public Builder isLoading(boolean isLoading) {
            this.isLoading = isLoading;
            return this;
        }
        
        public Builder imageNames(List<String> imageNames) {
            this.imageNames = imageNames;
            return this;
        }
        
        public KymographInfo build() {
            return new KymographInfo(this);
        }
    }
} 