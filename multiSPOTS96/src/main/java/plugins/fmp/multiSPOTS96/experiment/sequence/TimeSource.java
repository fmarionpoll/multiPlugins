package plugins.fmp.multiSPOTS96.experiment.sequence;

/**
 * Enum representing different sources for obtaining file time information.
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 */
public enum TimeSource {
    /** Extract time from structured filename patterns */
    STRUCTURED_NAME,
    
    /** Use file system attributes (creation/modification time) */
    FILE_ATTRIBUTES,
    
    /** Extract time from JPEG metadata */
    JPEG_METADATA;
} 