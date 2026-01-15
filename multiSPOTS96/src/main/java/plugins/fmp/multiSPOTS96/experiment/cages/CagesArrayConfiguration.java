package plugins.fmp.multiSPOTS96.experiment.cages;

/**
 * Configuration class for cages array operations and settings.
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 */
public final class CagesArrayConfiguration {
    private final int nCagesAlongX;
    private final int nCagesAlongY;
    private final int nColumnsPerCage;
    private final int nRowsPerCage;
    private final long detectFirstMs;
    private final long detectLastMs;
    private final long detectBinMs;
    private final int detectThreshold;
    private final int detectNFrames;
    private final boolean enableThreadSafety;
    private final boolean enablePerformanceOptimization;
    private final boolean validateInputs;
    private final boolean enableProgressReporting;
    
    private CagesArrayConfiguration(Builder builder) {
        this.nCagesAlongX = validatePositive(builder.nCagesAlongX, "nCagesAlongX");
        this.nCagesAlongY = validatePositive(builder.nCagesAlongY, "nCagesAlongY");
        this.nColumnsPerCage = validatePositive(builder.nColumnsPerCage, "nColumnsPerCage");
        this.nRowsPerCage = validatePositive(builder.nRowsPerCage, "nRowsPerCage");
        this.detectFirstMs = builder.detectFirstMs;
        this.detectLastMs = builder.detectLastMs;
        this.detectBinMs = validatePositive(builder.detectBinMs, "detectBinMs");
        this.detectThreshold = builder.detectThreshold;
        this.detectNFrames = validateNonNegative(builder.detectNFrames, "detectNFrames");
        this.enableThreadSafety = builder.enableThreadSafety;
        this.enablePerformanceOptimization = builder.enablePerformanceOptimization;
        this.validateInputs = builder.validateInputs;
        this.enableProgressReporting = builder.enableProgressReporting;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static CagesArrayConfiguration defaultConfiguration() {
        return builder().build();
    }
    
    public static CagesArrayConfiguration highPerformance() {
        return builder()
            .enableThreadSafety(true)
            .enablePerformanceOptimization(true)
            .validateInputs(false)
            .enableProgressReporting(false)
            .build();
    }
    
    public static CagesArrayConfiguration qualityAssurance() {
        return builder()
            .enableThreadSafety(true)
            .enablePerformanceOptimization(false)
            .validateInputs(true)
            .enableProgressReporting(true)
            .build();
    }
    
    public static CagesArrayConfiguration research(int nCagesX, int nCagesY) {
        return builder()
            .nCagesAlongX(nCagesX)
            .nCagesAlongY(nCagesY)
            .enableThreadSafety(true)
            .enablePerformanceOptimization(true)
            .validateInputs(true)
            .build();
    }
    
    // Getters
    public int getNTotalCages() { return nCagesAlongX * nCagesAlongY; }
    public int getNCagesAlongX() { return nCagesAlongX; }
    public int getNCagesAlongY() { return nCagesAlongY; }
    public int getNColumnsPerCage() { return nColumnsPerCage; }
    public int getNRowsPerCage() { return nRowsPerCage; }
    public long getDetectFirstMs() { return detectFirstMs; }
    public long getDetectLastMs() { return detectLastMs; }
    public long getDetectBinMs() { return detectBinMs; }
    public int getDetectThreshold() { return detectThreshold; }
    public int getDetectNFrames() { return detectNFrames; }
    public boolean isEnableThreadSafety() { return enableThreadSafety; }
    public boolean isEnablePerformanceOptimization() { return enablePerformanceOptimization; }
    public boolean isValidateInputs() { return validateInputs; }
    public boolean isEnableProgressReporting() { return enableProgressReporting; }
    
    // Computed properties
    public boolean isValidConfiguration() {
        return nCagesAlongX > 0 && nCagesAlongY > 0 && 
               nColumnsPerCage > 0 && nRowsPerCage > 0 &&
               detectBinMs > 0;
    }
    
    public long getDetectDurationMs() {
        return detectLastMs - detectFirstMs;
    }
    
    @Override
    public String toString() {
        return String.format("CagesArrayConfiguration{grid=%dx%d, cells=%dx%d, threadSafe=%b, optimized=%b}", 
                           nCagesAlongX, nCagesAlongY, nColumnsPerCage, nRowsPerCage, 
                           enableThreadSafety, enablePerformanceOptimization);
    }
    
    private static int validatePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive, got: " + value);
        }
        return value;
    }
    
    private static long validatePositive(long value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive, got: " + value);
        }
        return value;
    }
    
    private static int validateNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must be non-negative, got: " + value);
        }
        return value;
    }
    
    public static class Builder {
        private int nCagesAlongX = 6;
        private int nCagesAlongY = 8;
        private int nColumnsPerCage = 2;
        private int nRowsPerCage = 1;
        private long detectFirstMs = 0;
        private long detectLastMs = 0;
        private long detectBinMs = 60000;
        private int detectThreshold = 0;
        private int detectNFrames = 0;
        private boolean enableThreadSafety = true;
        private boolean enablePerformanceOptimization = true;
        private boolean validateInputs = true;
        private boolean enableProgressReporting = true;
        
        public Builder nCagesAlongX(int nCagesAlongX) {
            this.nCagesAlongX = nCagesAlongX;
            return this;
        }
        
        public Builder nCagesAlongY(int nCagesAlongY) {
            this.nCagesAlongY = nCagesAlongY;
            return this;
        }
        
        public Builder nColumnsPerCage(int nColumnsPerCage) {
            this.nColumnsPerCage = nColumnsPerCage;
            return this;
        }
        
        public Builder nRowsPerCage(int nRowsPerCage) {
            this.nRowsPerCage = nRowsPerCage;
            return this;
        }
        
        public Builder detectFirstMs(long detectFirstMs) {
            this.detectFirstMs = detectFirstMs;
            return this;
        }
        
        public Builder detectLastMs(long detectLastMs) {
            this.detectLastMs = detectLastMs;
            return this;
        }
        
        public Builder detectBinMs(long detectBinMs) {
            this.detectBinMs = detectBinMs;
            return this;
        }
        
        public Builder detectThreshold(int detectThreshold) {
            this.detectThreshold = detectThreshold;
            return this;
        }
        
        public Builder detectNFrames(int detectNFrames) {
            this.detectNFrames = detectNFrames;
            return this;
        }
        
        public Builder enableThreadSafety(boolean enableThreadSafety) {
            this.enableThreadSafety = enableThreadSafety;
            return this;
        }
        
        public Builder enablePerformanceOptimization(boolean enablePerformanceOptimization) {
            this.enablePerformanceOptimization = enablePerformanceOptimization;
            return this;
        }
        
        public Builder validateInputs(boolean validateInputs) {
            this.validateInputs = validateInputs;
            return this;
        }
        
        public Builder enableProgressReporting(boolean enableProgressReporting) {
            this.enableProgressReporting = enableProgressReporting;
            return this;
        }
        
        public CagesArrayConfiguration build() {
            return new CagesArrayConfiguration(this);
        }
    }
} 