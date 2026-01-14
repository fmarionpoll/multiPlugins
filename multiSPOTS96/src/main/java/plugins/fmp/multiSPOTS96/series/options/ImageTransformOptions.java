package plugins.fmp.multiSPOTS96.series.options;

import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformEnums;

/**
 * Configuration for image transformation parameters.
 * Replaces image transform-related fields from BuildSeriesOptions.
 */
public class ImageTransformOptions {
    private final ImageTransformEnums transform01;
    private final ImageTransformEnums transform02;
    private final ImageTransformEnums overlayTransform;
    private final ImageTransformEnums transformop;
    private final boolean overlayIfGreater;
    private final boolean trackWhite;
    private final boolean limitLow;
    private final boolean limitUp;
    private final boolean forceBuildBackground;
    private final boolean backgroundSubtraction;
    private final boolean buildDerivative;
    private final boolean compensateBackground;
    private final boolean pass1;
    private final boolean pass2;
    private final boolean directionUp2;
    private final int overlayThreshold;
    private final int thresholdDiff;
    private final int limitLowValue;
    private final int limitUpValue;
    private final int limitRatio;
    private final int jitter;
    private final int backgroundDelta;
    private final int backgroundJitter;
    
    private ImageTransformOptions(Builder builder) {
        this.transform01 = validateTransform(builder.transform01, "transform01");
        this.transform02 = validateTransform(builder.transform02, "transform02");
        this.overlayTransform = validateTransform(builder.overlayTransform, "overlayTransform");
        this.transformop = validateTransform(builder.transformop, "transformop");
        this.overlayIfGreater = builder.overlayIfGreater;
        this.trackWhite = builder.trackWhite;
        this.limitLow = builder.limitLow;
        this.limitUp = builder.limitUp;
        this.forceBuildBackground = builder.forceBuildBackground;
        this.backgroundSubtraction = builder.backgroundSubtraction;
        this.buildDerivative = builder.buildDerivative;
        this.compensateBackground = builder.compensateBackground;
        this.pass1 = builder.pass1;
        this.pass2 = builder.pass2;
        this.directionUp2 = builder.directionUp2;
        this.overlayThreshold = validateThreshold(builder.overlayThreshold, "overlayThreshold");
        this.thresholdDiff = validateThreshold(builder.thresholdDiff, "thresholdDiff");
        this.limitLowValue = validateLimitValue(builder.limitLowValue, "limitLowValue");
        this.limitUpValue = validateLimitValue(builder.limitUpValue, "limitUpValue");
        this.limitRatio = validateLimitRatio(builder.limitRatio);
        this.jitter = validateJitter(builder.jitter);
        this.backgroundDelta = validateBackgroundDelta(builder.backgroundDelta);
        this.backgroundJitter = validateBackgroundJitter(builder.backgroundJitter);
        
        // Validate relationships
        validateLimitRelationships();
    }
    
	// Getters
    public ImageTransformEnums getTransform01() { return transform01; }
    public ImageTransformEnums getTransform02() { return transform02; }
    public ImageTransformEnums getOverlayTransform() { return overlayTransform; }
    public ImageTransformEnums getTransformop() { return transformop; }
    public boolean isOverlayIfGreater() { return overlayIfGreater; }
    public boolean isTrackWhite() { return trackWhite; }
    public boolean isLimitLow() { return limitLow; }
    public boolean isLimitUp() { return limitUp; }
    public boolean isForceBuildBackground() { return forceBuildBackground; }
    public boolean isBackgroundSubtraction() { return backgroundSubtraction; }
    public boolean isBuildDerivative() { return buildDerivative; }
    public boolean isCompensateBackground() { return compensateBackground; }
    public boolean isPass1() { return pass1; }
    public boolean isPass2() { return pass2; }
    public boolean isDirectionUp2() { return directionUp2; }
    public int getOverlayThreshold() { return overlayThreshold; }
    public int getThresholdDiff() { return thresholdDiff; }
    public int getLimitLowValue() { return limitLowValue; }
    public int getLimitUpValue() { return limitUpValue; }
    public int getLimitRatio() { return limitRatio; }
    public int getJitter() { return jitter; }
    public int getBackgroundDelta() { return backgroundDelta; }
    public int getBackgroundJitter() { return backgroundJitter; }
    
    // Validation methods
    private ImageTransformEnums validateTransform(ImageTransformEnums transform, String fieldName) {
        if (transform == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
        return transform;
    }
    
    private int validateThreshold(int threshold, String fieldName) {
        if (threshold < 0 || threshold > 255) {
            throw new IllegalArgumentException(fieldName + " must be between 0 and 255, got: " + threshold);
        }
        return threshold;
    }
    
    private int validateLimitValue(int limitValue, String fieldName) {
        if (limitValue < 0 || limitValue > 255) {
            throw new IllegalArgumentException(fieldName + " must be between 0 and 255, got: " + limitValue);
        }
        return limitValue;
    }
    
    private int validateLimitRatio(int limitRatio) {
        if (limitRatio < 1 || limitRatio > 100) {
            throw new IllegalArgumentException("Limit ratio must be between 1 and 100, got: " + limitRatio);
        }
        return limitRatio;
    }
    
    private int validateJitter(int jitter) {
        if (jitter < 0 || jitter > 50) {
            throw new IllegalArgumentException("Jitter must be between 0 and 50, got: " + jitter);
        }
        return jitter;
    }
    
    private int validateBackgroundDelta(int backgroundDelta) {
        if (backgroundDelta < 0 || backgroundDelta > 255) {
            throw new IllegalArgumentException("Background delta must be between 0 and 255, got: " + backgroundDelta);
        }
        return backgroundDelta;
    }
    
    private int validateBackgroundJitter(int backgroundJitter) {
        if (backgroundJitter < 0 || backgroundJitter > 10) {
            throw new IllegalArgumentException("Background jitter must be between 0 and 10, got: " + backgroundJitter);
        }
        return backgroundJitter;
    }
    
    private void validateLimitRelationships() {
        if (limitLow && limitUp && limitLowValue > limitUpValue) {
            throw new IllegalArgumentException("Low limit value (" + limitLowValue + 
                ") cannot be greater than high limit value (" + limitUpValue + ")");
        }
    }
    
    // Builder pattern
    public static class Builder {
        private ImageTransformEnums transform01 = ImageTransformEnums.R_RGB;
        private ImageTransformEnums transform02 = ImageTransformEnums.L1DIST_TO_1RSTCOL;
        private ImageTransformEnums overlayTransform = ImageTransformEnums.NONE;
        private ImageTransformEnums transformop = ImageTransformEnums.NONE;
        private boolean overlayIfGreater = true;
        private boolean trackWhite = false;
        private boolean limitLow = false;
        private boolean limitUp = false;
        private boolean forceBuildBackground = false;
        private boolean backgroundSubtraction = false;
        private boolean buildDerivative = true;
        private boolean compensateBackground = false;
        private boolean pass1 = true;
        private boolean pass2 = false;
        private boolean directionUp2 = true;
        private int overlayThreshold = 0;
        private int thresholdDiff = 100;
        private int limitLowValue = 0;
        private int limitUpValue = 1;
        private int limitRatio = 4;
        private int jitter = 10;
        private int backgroundDelta = 50;
        private int backgroundJitter = 1;
        
        public Builder transform01(ImageTransformEnums transform01) {
            this.transform01 = transform01;
            return this;
        }
        
        public Builder transform02(ImageTransformEnums transform02) {
            this.transform02 = transform02;
            return this;
        }
        
        public Builder overlayTransform(ImageTransformEnums overlayTransform) {
            this.overlayTransform = overlayTransform;
            return this;
        }
        
        public Builder transformop(ImageTransformEnums transformop) {
            this.transformop = transformop;
            return this;
        }
        
        public Builder overlayIfGreater(boolean overlayIfGreater) {
            this.overlayIfGreater = overlayIfGreater;
            return this;
        }
        
        public Builder trackWhite(boolean trackWhite) {
            this.trackWhite = trackWhite;
            return this;
        }
        
        public Builder limitLow(boolean limitLow) {
            this.limitLow = limitLow;
            return this;
        }
        
        public Builder limitUp(boolean limitUp) {
            this.limitUp = limitUp;
            return this;
        }
        
        public Builder forceBuildBackground(boolean forceBuildBackground) {
            this.forceBuildBackground = forceBuildBackground;
            return this;
        }
        
        public Builder backgroundSubtraction(boolean backgroundSubtraction) {
            this.backgroundSubtraction = backgroundSubtraction;
            return this;
        }
        
        public Builder buildDerivative(boolean buildDerivative) {
            this.buildDerivative = buildDerivative;
            return this;
        }
        
        public Builder compensateBackground(boolean compensateBackground) {
            this.compensateBackground = compensateBackground;
            return this;
        }
        
        public Builder pass1(boolean pass1) {
            this.pass1 = pass1;
            return this;
        }
        
        public Builder pass2(boolean pass2) {
            this.pass2 = pass2;
            return this;
        }
        
        public Builder directionUp2(boolean directionUp2) {
            this.directionUp2 = directionUp2;
            return this;
        }
        
        public Builder overlayThreshold(int overlayThreshold) {
            this.overlayThreshold = overlayThreshold;
            return this;
        }
        
        public Builder thresholdDiff(int thresholdDiff) {
            this.thresholdDiff = thresholdDiff;
            return this;
        }
        
        public Builder limitLowValue(int limitLowValue) {
            this.limitLowValue = limitLowValue;
            return this;
        }
        
        public Builder limitUpValue(int limitUpValue) {
            this.limitUpValue = limitUpValue;
            return this;
        }
        
        public Builder limitRatio(int limitRatio) {
            this.limitRatio = limitRatio;
            return this;
        }
        
        public Builder jitter(int jitter) {
            this.jitter = jitter;
            return this;
        }
        
        public Builder backgroundDelta(int backgroundDelta) {
            this.backgroundDelta = backgroundDelta;
            return this;
        }
        
        public Builder backgroundJitter(int backgroundJitter) {
            this.backgroundJitter = backgroundJitter;
            return this;
        }
        
        public ImageTransformOptions build() {
            return new ImageTransformOptions(this);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Create from legacy BuildSeriesOptions
    public static ImageTransformOptions fromLegacyOptions(plugins.fmp.multiSPOTS96.series.BuildSeriesOptions legacy) {
        return builder()
            .transform01(legacy.transform01)
            .transform02(legacy.transform02)
            .overlayTransform(legacy.overlayTransform)
            .transformop(legacy.transformop)
            .overlayIfGreater(legacy.overlayIfGreater)
            .trackWhite(legacy.btrackWhite)
            .limitLow(legacy.blimitLow)
            .limitUp(legacy.blimitUp)
            .forceBuildBackground(legacy.forceBuildBackground)
            .backgroundSubtraction(legacy.backgroundSubstraction)
            .buildDerivative(legacy.buildDerivative)
            .compensateBackground(legacy.compensateBackground)
            .pass1(legacy.pass1)
            .pass2(legacy.pass2)
            .directionUp2(legacy.directionUp2)
            .overlayThreshold(legacy.overlayThreshold)
            .thresholdDiff(legacy.thresholdDiff)
            .limitLowValue(legacy.limitLow)
            .limitUpValue(legacy.limitUp)
            .limitRatio(legacy.limitRatio)
            .jitter(legacy.jitter)
            .backgroundDelta(legacy.background_delta)
            .backgroundJitter(legacy.background_jitter)
            .build();
    }
} 