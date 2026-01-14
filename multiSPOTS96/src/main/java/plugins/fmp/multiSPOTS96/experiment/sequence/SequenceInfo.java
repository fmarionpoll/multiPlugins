package plugins.fmp.multiSPOTS96.experiment.sequence;

import plugins.fmp.multiSPOTS96.experiment.EnumStatus;

/**
 * Immutable data class containing sequence information.
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 */
public class SequenceInfo {
    private final String name;
    private final int currentFrame;
    private final int totalFrames;
    private final EnumStatus status;
    private final TimeRange timeRange;
    
    private SequenceInfo(Builder builder) {
        this.name = builder.name;
        this.currentFrame = builder.currentFrame;
        this.totalFrames = builder.totalFrames;
        this.status = builder.status;
        this.timeRange = builder.timeRange;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public String getName() { return name; }
    public int getCurrentFrame() { return currentFrame; }
    public int getTotalFrames() { return totalFrames; }
    public EnumStatus getStatus() { return status; }
    public TimeRange getTimeRange() { return timeRange; }
    
    @Override
    public String toString() {
        return String.format("SequenceInfo{name='%s', frame=%d/%d, status=%s}", 
                           name, currentFrame, totalFrames, status);
    }
    
    public static class Builder {
        private String name;
        private int currentFrame;
        private int totalFrames;
        private EnumStatus status;
        private TimeRange timeRange;
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder currentFrame(int currentFrame) {
            this.currentFrame = currentFrame;
            return this;
        }
        
        public Builder totalFrames(int totalFrames) {
            this.totalFrames = totalFrames;
            return this;
        }
        
        public Builder status(EnumStatus status) {
            this.status = status;
            return this;
        }
        
        public Builder timeRange(TimeRange timeRange) {
            this.timeRange = timeRange;
            return this;
        }
        
        public SequenceInfo build() {
            return new SequenceInfo(this);
        }
    }
} 