package plugins.fmp.multiSPOTS96.experiment.spots;

import java.util.List;

/**
 * Immutable data class containing comprehensive spots array information.
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 */
public class SpotsArrayInfo {
    private final int totalSpots;
    private final int validSpots;
    private final int spotsWithMeasures;
    private final int spotsReadyForAnalysis;
    private final List<String> spotNames;
    private final boolean hasTimeIntervals;
    private final int timeIntervalsCount;
    
    private SpotsArrayInfo(Builder builder) {
        this.totalSpots = builder.totalSpots;
        this.validSpots = builder.validSpots;
        this.spotsWithMeasures = builder.spotsWithMeasures;
        this.spotsReadyForAnalysis = builder.spotsReadyForAnalysis;
        this.spotNames = builder.spotNames != null ? List.copyOf(builder.spotNames) : List.of();
        this.hasTimeIntervals = builder.hasTimeIntervals;
        this.timeIntervalsCount = builder.timeIntervalsCount;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public int getTotalSpots() { return totalSpots; }
    public int getValidSpots() { return validSpots; }
    public int getSpotsWithMeasures() { return spotsWithMeasures; }
    public int getSpotsReadyForAnalysis() { return spotsReadyForAnalysis; }
    public List<String> getSpotNames() { return spotNames; }
    public boolean hasTimeIntervals() { return hasTimeIntervals; }
    public int getTimeIntervalsCount() { return timeIntervalsCount; }
    
    public boolean hasValidSpots() {
        return validSpots > 0;
    }
    
    public double getValidSpotsRatio() {
        return totalSpots > 0 ? (double) validSpots / totalSpots : 0.0;
    }
    
    public double getMeasuresCompletionRatio() {
        return totalSpots > 0 ? (double) spotsWithMeasures / totalSpots : 0.0;
    }
    
    public boolean isReadyForAnalysis() {
        return spotsReadyForAnalysis > 0;
    }
    
    @Override
    public String toString() {
        return String.format("SpotsArrayInfo{total=%d, valid=%d, withMeasures=%d, ready=%d, timeIntervals=%d}", 
                           totalSpots, validSpots, spotsWithMeasures, spotsReadyForAnalysis, timeIntervalsCount);
    }
    
    public static class Builder {
        private int totalSpots;
        private int validSpots;
        private int spotsWithMeasures;
        private int spotsReadyForAnalysis;
        private List<String> spotNames;
        private boolean hasTimeIntervals;
        private int timeIntervalsCount;
        
        public Builder totalSpots(int totalSpots) {
            this.totalSpots = totalSpots;
            return this;
        }
        
        public Builder validSpots(int validSpots) {
            this.validSpots = validSpots;
            return this;
        }
        
        public Builder spotsWithMeasures(int spotsWithMeasures) {
            this.spotsWithMeasures = spotsWithMeasures;
            return this;
        }
        
        public Builder spotsReadyForAnalysis(int spotsReadyForAnalysis) {
            this.spotsReadyForAnalysis = spotsReadyForAnalysis;
            return this;
        }
        
        public Builder spotNames(List<String> spotNames) {
            this.spotNames = spotNames;
            return this;
        }
        
        public Builder hasTimeIntervals(boolean hasTimeIntervals) {
            this.hasTimeIntervals = hasTimeIntervals;
            return this;
        }
        
        public Builder timeIntervalsCount(int timeIntervalsCount) {
            this.timeIntervalsCount = timeIntervalsCount;
            return this;
        }
        
        public SpotsArrayInfo build() {
            return new SpotsArrayInfo(this);
        }
    }
} 