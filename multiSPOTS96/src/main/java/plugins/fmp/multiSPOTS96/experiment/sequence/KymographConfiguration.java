package plugins.fmp.multiSPOTS96.experiment.sequence;

import java.util.List;

/**
 * Configuration class for kymograph operations and settings.
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 */
public class KymographConfiguration {
    private final boolean validateROIs;
    private final boolean adjustImageSizes;
    private final boolean showProgress;
    private final String baseDirectory;
    private final List<String> acceptedFileExtensions;
    private final boolean enableParallelProcessing;
    private final int maxConcurrentThreads;
    
    private KymographConfiguration(Builder builder) {
        this.validateROIs = builder.validateROIs;
        this.adjustImageSizes = builder.adjustImageSizes;
        this.showProgress = builder.showProgress;
        this.baseDirectory = builder.baseDirectory;
        this.acceptedFileExtensions = builder.acceptedFileExtensions != null ? 
            List.copyOf(builder.acceptedFileExtensions) : List.of("tiff", "tif");
        this.enableParallelProcessing = builder.enableParallelProcessing;
        this.maxConcurrentThreads = builder.maxConcurrentThreads;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static KymographConfiguration defaultConfiguration() {
        return builder().build();
    }
    
    public static KymographConfiguration fastProcessing() {
        return builder()
            .validateROIs(false)
            .adjustImageSizes(false)
            .showProgress(false)
            .enableParallelProcessing(true)
            .build();
    }
    
    public static KymographConfiguration qualityProcessing() {
        return builder()
            .validateROIs(true)
            .adjustImageSizes(true)
            .showProgress(true)
            .enableParallelProcessing(true)
            .build();
    }
    
    public boolean isValidateROIs() { return validateROIs; }
    public boolean isAdjustImageSizes() { return adjustImageSizes; }
    public boolean isShowProgress() { return showProgress; }
    public String getBaseDirectory() { return baseDirectory; }
    public List<String> getAcceptedFileExtensions() { return acceptedFileExtensions; }
    public boolean isEnableParallelProcessing() { return enableParallelProcessing; }
    public int getMaxConcurrentThreads() { return maxConcurrentThreads; }
    
    @Override
    public String toString() {
        return String.format("KymographConfiguration{validateROIs=%b, adjustSizes=%b, showProgress=%b, parallel=%b, threads=%d}", 
                           validateROIs, adjustImageSizes, showProgress, enableParallelProcessing, maxConcurrentThreads);
    }
    
    public static class Builder {
        private boolean validateROIs = true;
        private boolean adjustImageSizes = true;
        private boolean showProgress = true;
        private String baseDirectory;
        private List<String> acceptedFileExtensions;
        private boolean enableParallelProcessing = true;
        private int maxConcurrentThreads = Runtime.getRuntime().availableProcessors();
        
        public Builder validateROIs(boolean validateROIs) {
            this.validateROIs = validateROIs;
            return this;
        }
        
        public Builder adjustImageSizes(boolean adjustImageSizes) {
            this.adjustImageSizes = adjustImageSizes;
            return this;
        }
        
        public Builder showProgress(boolean showProgress) {
            this.showProgress = showProgress;
            return this;
        }
        
        public Builder baseDirectory(String baseDirectory) {
            this.baseDirectory = baseDirectory;
            return this;
        }
        
        public Builder acceptedFileExtensions(List<String> acceptedFileExtensions) {
            this.acceptedFileExtensions = acceptedFileExtensions;
            return this;
        }
        
        public Builder enableParallelProcessing(boolean enableParallelProcessing) {
            this.enableParallelProcessing = enableParallelProcessing;
            return this;
        }
        
        public Builder maxConcurrentThreads(int maxConcurrentThreads) {
            this.maxConcurrentThreads = maxConcurrentThreads;
            return this;
        }
        
        public KymographConfiguration build() {
            return new KymographConfiguration(this);
        }
    }
} 