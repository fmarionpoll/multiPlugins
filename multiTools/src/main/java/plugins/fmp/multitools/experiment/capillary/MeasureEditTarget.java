package plugins.fmp.multitools.experiment.capillary;

/**
 * Identifies which capillary measure (level, derivative, gulps) is the target of
 * an edit or transfer operation. Used by EditLevels, SequenceKymos, and Capillary
 * so the active measure is specified explicitly instead of inferred from ROI names.
 */
public enum MeasureEditTarget {
	TOP_LEVEL, BOTTOM_LEVEL, TOP_AND_BOTTOM, DERIVATIVE, GULPS
}
