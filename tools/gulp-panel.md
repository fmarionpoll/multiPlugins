# Correlation gulp panel prototype

In multiCAFE's gulp detection dialog, click **Correlation prototype...**.
It opens a modeless Swing panel for the current experiment and initially
selects the displayed capillary. Load the experiment's kymographs and saved
top meniscus measures first.

Choose a capillary and window radius, then **Compute**. Computation runs in
a cancellable background worker. It uses the Java implementation of the
correlation trial: +/-6 pixels searched at 0.1-pixel spacing, forward/reverse
checks, quality exclusions, and an anchored refined physical trajectory.

The upper graph shows **correlation displacement minus predicted evaporation,
divided by elapsed minutes**. Positive values are excess downward movement.
Actual acquisition timestamps supply interval durations. This makes the
horizontal threshold comparable across unequal frame intervals; for the first
experiment each interval is exactly one minute. Grey crosses mark unavailable
correlations. Candidate intervals above the red bar are red.

Drag the red bar, or edit the threshold spinner, to update candidates and the
image markers immediately. **Automatic** restores the initial estimate.
Click the curve to mark a time point in the three image panels. The images
show the original crop, saved-topraw alignment, and correlation-refined
alignment. Both aligned views use identical vertical interpolation and a
shared constant display-origin adjustment from the average raw-aligned edge.
Evaporation remains in the refined physical position curve.

The initial threshold uses quiet stretches of at least four successive
reliable intervals. Their median plus three times 1.4826*MAD supplies the
record estimate, bounded below by the corresponding empty-capillary noise
estimate. A 0.05-pixel/min noise floor avoids degeneracy at the search-grid
resolution. The preliminary quiet selection keeps residuals within two noise
SD of the reference noise center. At least 12 quiet samples and 10% of the
record's valid intervals are required. These are prototype settings, not a
validated false-positive-rate guarantee.

When insufficient quiet stretches exist, the panel explicitly identifies the
special case and uses empty-capillary noise. If neither source is adequate,
the automatic value is unavailable and a manual threshold is required. If no
empty capillaries exist, the graph explicitly identifies that evaporation
correction is unavailable rather than silently treating the record as corrected.

Controls are converted to the selected capillary's native-pixel scale using
its physical pixel calibration at the first frame. The exponential evaporation
model is fitted on a uniformly sampled time grid and interpolated back to
actual acquisition times. Noise controls are evaluated with their own curve
excluded from the evaporation fit. When the selected capillary is empty it
is excluded from both the reference and noise training. At least two remaining
controls are needed to form these held-out noise samples.

**Export CSV...** records raw and refined positions, signed displacement,
evaporation increment, excess movement rate, correlation, reliability and
rejection reasons, automatic and chosen thresholds, manual/automatic state,
and candidate intervals. Candidate amplitude is the positive image shift in
native pixels; it includes the interval's evaporation. It is not a final
evaporation-corrected feeding volume. Individual candidate intervals are not
merged into feeding bouts. Amplitude and classification can therefore be
reviewed separately.

This is a preview/export panel. It does not replace saved topraw, temporal
change or gulp measures, and it does not add a new batch detector enum yet.
This keeps the prototype reviewable before adopting its threshold rules in
the existing detection pipeline. Runtime is entirely Java: no Python, external
process, or Python installation is needed.

Current support: native RGB kymographs with channel values in 0..255 and saved
frame timestamps matching their columns. Normalized blue or downsampled
kymographs produce an explanatory message. Missing levels are kept missing;
correlation does not bridge them.

## Validation

- Maven builds multiTools and multiCAFE successfully.
- Eight new JUnit tests pass, including a test against the real 1L TIFF/CSV
  enabled by the optional `correlation.fixture` property. Existing v2 detector
  tests also pass.
- `CorrelationPanelSmoke.java` creates the actual panel against the first
  experiment's real TIFFs and measures, with isolated in-memory preferences.
  It exercises 1L/7L computation, the Icy RGB adapter, threshold dragging,
  candidate updates, automatic reset and CSV output. It renders the panel
  offscreen for inspection and verifies that original raw levels stay intact.
  This is an offscreen integration check, not an installed-ICY manual test.

The earlier Python files remain optional research/reproduction utilities.
They generated the initial benchmarks and standalone bitmap comparisons;
the Java panel and Maven build do not depend on them.
