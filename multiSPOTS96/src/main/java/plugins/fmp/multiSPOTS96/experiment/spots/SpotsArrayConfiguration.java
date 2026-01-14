package plugins.fmp.multiSPOTS96.experiment.spots;

import java.util.List;

/**
 * Configuration class for spots array operations and settings.
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 */
public class SpotsArrayConfiguration {
    private final boolean validateSpots;
    private final boolean enableProgressReporting;
    private final boolean autoSortSpots;
    private final boolean createBackups;
    private final List<EnumSpotMeasures> enabledMeasureTypes;
    private final int maxConcurrentOperations;
    private final boolean strictValidation;
    private final String csvSeparator;
    
    private SpotsArrayConfiguration(Builder builder) {
        this.validateSpots = builder.validateSpots;
        this.enableProgressReporting = builder.enableProgressReporting;
        this.autoSortSpots = builder.autoSortSpots;
        this.createBackups = builder.createBackups;
        this.enabledMeasureTypes = builder.enabledMeasureTypes != null ? 
            List.copyOf(builder.enabledMeasureTypes) : 
            List.of(EnumSpotMeasures.AREA_SUM, EnumSpotMeasures.AREA_SUMCLEAN, 
                   EnumSpotMeasures.AREA_OUT, EnumSpotMeasures.AREA_DIFF, EnumSpotMeasures.AREA_FLYPRESENT);
        this.maxConcurrentOperations = builder.maxConcurrentOperations;
        this.strictValidation = builder.strictValidation;
        this.csvSeparator = builder.csvSeparator;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static SpotsArrayConfiguration defaultConfiguration() {
        return builder().build();
    }
    
    public static SpotsArrayConfiguration highPerformance() {
        return builder()
            .validateSpots(false)
            .autoSortSpots(false)
            .enableProgressReporting(false)
            .strictValidation(false)
            .maxConcurrentOperations(Runtime.getRuntime().availableProcessors() * 2)
            .build();
    }
    
    public static SpotsArrayConfiguration qualityAssurance() {
        return builder()
            .validateSpots(true)
            .autoSortSpots(true)
            .enableProgressReporting(true)
            .strictValidation(true)
            .createBackups(true)
            .maxConcurrentOperations(1)
            .build();
    }
    
    public static SpotsArrayConfiguration dataImportExport() {
        return builder()
            .validateSpots(true)
            .enableProgressReporting(true)
            .createBackups(true)
            .strictValidation(true)
            .build();
    }
    
    public boolean isValidateSpots() { return validateSpots; }
    public boolean isEnableProgressReporting() { return enableProgressReporting; }
    public boolean isAutoSortSpots() { return autoSortSpots; }
    public boolean isCreateBackups() { return createBackups; }
    public List<EnumSpotMeasures> getEnabledMeasureTypes() { return enabledMeasureTypes; }
    public int getMaxConcurrentOperations() { return maxConcurrentOperations; }
    public boolean isStrictValidation() { return strictValidation; }
    public String getCsvSeparator() { return csvSeparator; }
    
    @Override
    public String toString() {
        return String.format("SpotsArrayConfiguration{validate=%b, progress=%b, sort=%b, backup=%b, strict=%b, concurrent=%d}", 
                           validateSpots, enableProgressReporting, autoSortSpots, createBackups, strictValidation, maxConcurrentOperations);
    }
    
    public static class Builder {
        private boolean validateSpots = true;
        private boolean enableProgressReporting = true;
        private boolean autoSortSpots = true;
        private boolean createBackups = false;
        private List<EnumSpotMeasures> enabledMeasureTypes;
        private int maxConcurrentOperations = Runtime.getRuntime().availableProcessors();
        private boolean strictValidation = true;
        private String csvSeparator = ";";
        
        public Builder validateSpots(boolean validateSpots) {
            this.validateSpots = validateSpots;
            return this;
        }
        
        public Builder enableProgressReporting(boolean enableProgressReporting) {
            this.enableProgressReporting = enableProgressReporting;
            return this;
        }
        
        public Builder autoSortSpots(boolean autoSortSpots) {
            this.autoSortSpots = autoSortSpots;
            return this;
        }
        
        public Builder createBackups(boolean createBackups) {
            this.createBackups = createBackups;
            return this;
        }
        
        public Builder enabledMeasureTypes(List<EnumSpotMeasures> enabledMeasureTypes) {
            this.enabledMeasureTypes = enabledMeasureTypes;
            return this;
        }
        
        public Builder maxConcurrentOperations(int maxConcurrentOperations) {
            this.maxConcurrentOperations = maxConcurrentOperations;
            return this;
        }
        
        public Builder strictValidation(boolean strictValidation) {
            this.strictValidation = strictValidation;
            return this;
        }
        
        public Builder csvSeparator(String csvSeparator) {
            this.csvSeparator = csvSeparator;
            return this;
        }
        
        public SpotsArrayConfiguration build() {
            return new SpotsArrayConfiguration(this);
        }
    }
} 