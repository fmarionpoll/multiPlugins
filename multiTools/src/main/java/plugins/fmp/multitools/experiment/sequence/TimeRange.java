package plugins.fmp.multiSPOTS96.experiment.sequence;

/**
 * Immutable data class representing a time range with duration information.
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 */
public class TimeRange {
    private final long firstImageMs;
    private final long lastImageMs;
    private final long binDurationMs;
    
    public TimeRange(long firstImageMs, long lastImageMs, long binDurationMs) {
        this.firstImageMs = firstImageMs;
        this.lastImageMs = lastImageMs;
        this.binDurationMs = binDurationMs;
    }
    
    public long getFirstImageMs() { return firstImageMs; }
    public long getLastImageMs() { return lastImageMs; }
    public long getBinDurationMs() { return binDurationMs; }
    
    public long getTotalDurationMs() {
        return lastImageMs - firstImageMs;
    }
    
    public boolean isValid() {
        return firstImageMs <= lastImageMs && binDurationMs > 0;
    }
    
    @Override
    public String toString() {
        return String.format("TimeRange{first=%d, last=%d, binDuration=%d ms}", 
                           firstImageMs, lastImageMs, binDurationMs);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        TimeRange timeRange = (TimeRange) obj;
        return firstImageMs == timeRange.firstImageMs && 
               lastImageMs == timeRange.lastImageMs && 
               binDurationMs == timeRange.binDurationMs;
    }
    
    @Override
    public int hashCode() {
        int result = Long.hashCode(firstImageMs);
        result = 31 * result + Long.hashCode(lastImageMs);
        result = 31 * result + Long.hashCode(binDurationMs);
        return result;
    }
} 