package plugins.fmp.multiSPOTS96.series.options;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for detection parameters.
 * Replaces detection-related fields from BuildSeriesOptions.
 */
public class DetectionOptions {
    private final int threshold;
    private final boolean thresholdUp;
    private final int flyThreshold;
    private final boolean flyThresholdUp;
    private final int backgroundThreshold;
    private final int spotThreshold;
    private final boolean spotThresholdUp;
    private final int detectCage;
    private final boolean detectTop;
    private final boolean detectBottom;
    private final boolean detectFlies;
    private final boolean detectSelectedROIs;
    private final boolean detectAllSeries;
    private final List<Integer> selectedIndexes;
    private final int nFliesPresent;
    private final int videoChannel;
    private final int spotRadius;
    private final int diskRadius;
    
    private DetectionOptions(Builder builder) {
        this.threshold = validateThreshold(builder.threshold);
        this.thresholdUp = builder.thresholdUp;
        this.flyThreshold = validateFlyThreshold(builder.flyThreshold);
        this.flyThresholdUp = builder.flyThresholdUp;
        this.backgroundThreshold = validateBackgroundThreshold(builder.backgroundThreshold);
        this.spotThreshold = validateSpotThreshold(builder.spotThreshold);
        this.spotThresholdUp = builder.spotThresholdUp;
        this.detectCage = builder.detectCage;
        this.detectTop = builder.detectTop;
        this.detectBottom = builder.detectBottom;
        this.detectFlies = builder.detectFlies;
        this.detectSelectedROIs = builder.detectSelectedROIs;
        this.detectAllSeries = builder.detectAllSeries;
        this.selectedIndexes = builder.selectedIndexes != null 
            ? Collections.unmodifiableList(new ArrayList<>(builder.selectedIndexes))
            : Collections.emptyList();
        this.nFliesPresent = validateNFliesPresent(builder.nFliesPresent);
        this.videoChannel = validateVideoChannel(builder.videoChannel);
        this.spotRadius = validateSpotRadius(builder.spotRadius);
        this.diskRadius = validateDiskRadius(builder.diskRadius);
    }
    
    // Getters
    public int getThreshold() { return threshold; }
    public boolean isThresholdUp() { return thresholdUp; }
    public int getFlyThreshold() { return flyThreshold; }
    public boolean isFlyThresholdUp() { return flyThresholdUp; }
    public int getBackgroundThreshold() { return backgroundThreshold; }
    public int getSpotThreshold() { return spotThreshold; }
    public boolean isSpotThresholdUp() { return spotThresholdUp; }
    public int getDetectCage() { return detectCage; }
    public boolean isDetectTop() { return detectTop; }
    public boolean isDetectBottom() { return detectBottom; }
    public boolean isDetectFlies() { return detectFlies; }
    public boolean isDetectSelectedROIs() { return detectSelectedROIs; }
    public boolean isDetectAllSeries() { return detectAllSeries; }
    public List<Integer> getSelectedIndexes() { return selectedIndexes; }
    public int getNFliesPresent() { return nFliesPresent; }
    public int getVideoChannel() { return videoChannel; }
    public int getSpotRadius() { return spotRadius; }
    public int getDiskRadius() { return diskRadius; }
    
    // Validation methods
    private int validateThreshold(int threshold) {
        if (threshold < -1 || threshold > 255) {
            throw new IllegalArgumentException("Threshold must be between -1 and 255, got: " + threshold);
        }
        return threshold;
    }
    
    private int validateFlyThreshold(int flyThreshold) {
        if (flyThreshold < 0 || flyThreshold > 255) {
            throw new IllegalArgumentException("Fly threshold must be between 0 and 255, got: " + flyThreshold);
        }
        return flyThreshold;
    }
    
    private int validateBackgroundThreshold(int backgroundThreshold) {
        if (backgroundThreshold < 0 || backgroundThreshold > 255) {
            throw new IllegalArgumentException("Background threshold must be between 0 and 255, got: " + backgroundThreshold);
        }
        return backgroundThreshold;
    }
    
    private int validateSpotThreshold(int spotThreshold) {
        if (spotThreshold < 0 || spotThreshold > 255) {
            throw new IllegalArgumentException("Spot threshold must be between 0 and 255, got: " + spotThreshold);
        }
        return spotThreshold;
    }
    
    private int validateNFliesPresent(int nFliesPresent) {
        if (nFliesPresent < 0) {
            throw new IllegalArgumentException("Number of flies present must be non-negative, got: " + nFliesPresent);
        }
        return nFliesPresent;
    }
    
    private int validateVideoChannel(int videoChannel) {
        if (videoChannel < 0 || videoChannel > 2) {
            throw new IllegalArgumentException("Video channel must be between 0 and 2, got: " + videoChannel);
        }
        return videoChannel;
    }
    
    private int validateSpotRadius(int spotRadius) {
        if (spotRadius < 1 || spotRadius > 50) {
            throw new IllegalArgumentException("Spot radius must be between 1 and 50, got: " + spotRadius);
        }
        return spotRadius;
    }
    
    private int validateDiskRadius(int diskRadius) {
        if (diskRadius < 1 || diskRadius > 50) {
            throw new IllegalArgumentException("Disk radius must be between 1 and 50, got: " + diskRadius);
        }
        return diskRadius;
    }
    
    // Builder pattern
    public static class Builder {
        private int threshold = -1;
        private boolean thresholdUp = true;
        private int flyThreshold = 60;
        private boolean flyThresholdUp = true;
        private int backgroundThreshold = 40;
        private int spotThreshold = 35;
        private boolean spotThresholdUp = true;
        private int detectCage = -1;
        private boolean detectTop = true;
        private boolean detectBottom = true;
        private boolean detectFlies = true;
        private boolean detectSelectedROIs = false;
        private boolean detectAllSeries = true;
        private List<Integer> selectedIndexes = new ArrayList<>();
        private int nFliesPresent = 1;
        private int videoChannel = 0;
        private int spotRadius = 5;
        private int diskRadius = 5;
        
        public Builder threshold(int threshold) {
            this.threshold = threshold;
            return this;
        }
        
        public Builder thresholdUp(boolean thresholdUp) {
            this.thresholdUp = thresholdUp;
            return this;
        }
        
        public Builder flyThreshold(int flyThreshold) {
            this.flyThreshold = flyThreshold;
            return this;
        }
        
        public Builder flyThresholdUp(boolean flyThresholdUp) {
            this.flyThresholdUp = flyThresholdUp;
            return this;
        }
        
        public Builder backgroundThreshold(int backgroundThreshold) {
            this.backgroundThreshold = backgroundThreshold;
            return this;
        }
        
        public Builder spotThreshold(int spotThreshold) {
            this.spotThreshold = spotThreshold;
            return this;
        }
        
        public Builder spotThresholdUp(boolean spotThresholdUp) {
            this.spotThresholdUp = spotThresholdUp;
            return this;
        }
        
        public Builder detectCage(int detectCage) {
            this.detectCage = detectCage;
            return this;
        }
        
        public Builder detectTop(boolean detectTop) {
            this.detectTop = detectTop;
            return this;
        }
        
        public Builder detectBottom(boolean detectBottom) {
            this.detectBottom = detectBottom;
            return this;
        }
        
        public Builder detectFlies(boolean detectFlies) {
            this.detectFlies = detectFlies;
            return this;
        }
        
        public Builder detectSelectedROIs(boolean detectSelectedROIs) {
            this.detectSelectedROIs = detectSelectedROIs;
            return this;
        }
        
        public Builder detectAllSeries(boolean detectAllSeries) {
            this.detectAllSeries = detectAllSeries;
            return this;
        }
        
        public Builder selectedIndexes(List<Integer> selectedIndexes) {
            this.selectedIndexes = selectedIndexes;
            return this;
        }
        
        public Builder nFliesPresent(int nFliesPresent) {
            this.nFliesPresent = nFliesPresent;
            return this;
        }
        
        public Builder videoChannel(int videoChannel) {
            this.videoChannel = videoChannel;
            return this;
        }
        
        public Builder spotRadius(int spotRadius) {
            this.spotRadius = spotRadius;
            return this;
        }
        
        public Builder diskRadius(int diskRadius) {
            this.diskRadius = diskRadius;
            return this;
        }
        
        public DetectionOptions build() {
            return new DetectionOptions(this);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Create from legacy BuildSeriesOptions
    public static DetectionOptions fromLegacyOptions(plugins.fmp.multiSPOTS96.series.BuildSeriesOptions legacy) {
        return builder()
            .threshold(legacy.threshold)
            .flyThreshold(legacy.flyThreshold)
            .flyThresholdUp(legacy.flyThresholdUp)
            .backgroundThreshold(legacy.backgroundThreshold)
            .spotThreshold(legacy.spotThreshold)
            .spotThresholdUp(legacy.spotThresholdUp)
            .detectCage(legacy.detectCage)
            .detectTop(legacy.detectTop)
            .detectBottom(legacy.detectBottom)
            .detectFlies(legacy.detectFlies)
            .detectSelectedROIs(legacy.detectSelectedROIs)
            .detectAllSeries(legacy.detectAllSeries)
            .selectedIndexes(legacy.selectedIndexes)
            .nFliesPresent(legacy.nFliesPresent)
            .videoChannel(legacy.videoChannel)
            .spotRadius(legacy.spotRadius)
            .diskRadius(legacy.diskRadius)
            .build();
    }
} 