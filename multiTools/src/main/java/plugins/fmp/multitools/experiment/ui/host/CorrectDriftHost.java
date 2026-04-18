package plugins.fmp.multitools.experiment.ui.host;

/**
 * Host contract for {@link plugins.fmp.multitools.experiment.ui.CorrectDriftPanel}.
 * Currently a marker interface: the panel only needs the base
 * {@link DialogHost} contract (experiments combo) but is declared
 * separately so future drift-specific hooks can be added without
 * touching the base interface.
 */
public interface CorrectDriftHost extends DialogHost {
}
