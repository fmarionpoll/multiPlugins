# Correlation intake prototype

Open **Correlation prototype...** from the main intake detection dialog after
loading native RGB kymographs and saved top meniscus measures. All runtime
computation is Java; the Python files are optional research utilities.

## Comparison options

- **Expand search at limit** (default on): the shift spinner is the initial
  displacement range. A match at a configured boundary triggers a 1.5-fold
  expansion, at most four times the initial range and at most 64 pixels.
  Image borders do not trigger expansion. Forward/reverse agreement, contrast,
  correlation and peak-distinctness checks still apply. Turning this off
  restores the fixed-range search. The profile window is a separate radius:
  matching uses 2 * radius + 1 vertically sampled points at 0.1-pixel lags.
- **Persistent episodes (1-6 bins)** (default on): signed displacements minus
  expected evaporation accumulate over 1 through 6 consecutive valid bins.
  Each duration is compared against the 99.5th percentile of same-duration
  signed sums from held-out empty capillaries, with the automatic single-bin
  threshold as an additional floor. At least 30 control windows are required
  for a duration longer than one bin. The threshold slider scales all duration
  thresholds together; its red horizontal line represents only the single-bin
  threshold. Red points may therefore lie below it in episode mode.
  Negative increments within a candidate window count fully. Negative
  increments in the preceding/following two bins reduce its persistent
  amplitude. Missing correlations cannot be bridged. A median before/after
  check on the raw-anchored refined level minus evaporation also limits the
  amplitude. Highest-scoring non-overlapping windows become episodes.
  Turning this off restores the original single-bin threshold detector.
- **Local evaporation** (default off): optional scaling of the shared
  evaporation shape using quiet runs of at least five intervals. Each run
  fits raw position versus modeled evaporation with a separate intercept;
  the median run slope supplies the scale. Require at least three runs,
  20 quiet intervals, 0.5 pixel reference displacement in each run, and
  median absolute slope deviation at most 0.25. Slopes outside 0.2-2 are
  excluded. Uncertain estimates retain scale one and are labelled explicitly.
  Quiet selection and these stability limits are experimental heuristics,
  not confidence intervals. This cannot establish that a bitter capillary
  was untouched, nor identify evaporation during uninterrupted feeding.
  Held-out noise controls undergo the same optional adjustment.

The graph always shows motion minus the active evaporation model in pixels
per minute. Actual timestamps supply interval durations. The automatic
single-bin estimate remains quiet-period median + 3 robust noise SD, with an
empty-capillary noise floor. Quality exclusions appear as crosses.

## Amplitudes, display and saving

In single-bin mode amplitudes remain total positive image displacement.
In episode mode (method version 2), accepted bin boundaries partition the
refined evaporation-corrected levels into plateaus. Plateau medians, weighted
by their observed sample counts, are fitted jointly under a nondecreasing
constraint using isotonic regression. Reversals are pooled; accepted boundaries
that yield zero movement disappear. Net amplitudes are differences of these
shared fitted positions, independently of the signed-noise acceptance penalty.
Evaporation over each selected interval is added back for the existing total
amplitude convention. A bar's bottom is the fitted level plus evaporation;
its top is the preceding fitted level plus preceding evaporation. Successive
bars therefore cannot overlap when modeled evaporation is nondecreasing.
Neither endpoint is forced to agree with the measured recording endpoint.
Adjacent bins can share a boundary and are not necessarily resolved gulps.
Legacy single-bin mode remains unchanged for comparison.

**Show unexplained gaps** (default on) adds amber review spans between accepted
movements, including the start and end of the record. On spans of at least
four samples, the median first/last 2-3 evaporation-corrected measured levels
are compared against same-duration empty-control signed displacement sums.
The tolerance is at least one pixel and scales with the threshold. Gaps with
fewer than 30 usable control windows or unreliable intervening correlations
are explicitly uncertain (question mark in the panel; `_uncertain` in native
ROI names; a reliability field in CSV). This is a review heuristic, not proof
of feeding. It can reflect local evaporation, tracking error, or missed intake.

A flagged gap permits an unassigned baseline change at its strongest local
median change, so it is not silently absorbed into neighbouring event
amplitudes. No intake is created there and it does not enter consumption.
Toggling gap visibility changes only display. Gap analysis and ordered fitting
remain active whenever persistent episodes are enabled.

The grey reconstruction starts at raw t=0 and adds the evaporation curve
plus cumulative intake. **Subtract evaporation** (default on) subtracts the
per-bin evaporation from intake steps only, avoiding double counting. Turning
it off displays the stored total amplitudes. It does not change detection.

The status compares net detected displacement with endpoint raw displacement
minus evaporation and flags a difference greater than 3 pixels or 20% of the
latter (whichever is larger). This is a diagnostic, never an endpoint constraint.

**Save** stores correlation events separately; **Copy to gulps...** replaces
classic gulps after confirmation. Raw levels are unchanged. Saved correlation
rows append optional `bottom_positions=` (colon-separated fitted positions)
and `method_options=` trailers after their dense amplitude values, preserving
old row compatibility and recording method version, option flags and
scale. Fitted positions are also copied independently into classic gulps and
persisted with their flat CSV series, so saving/reloading or copying does not
re-anchor these bars to noisy raw positions. Editing amplitudes invalidates
the saved fitted positions. Existing red legacy results retain their display. Old rows load with the old method flags off.
CSV export records flags, active reference, amplitudes, actual search range,
fitted meniscus, intake top/bottom positions, and gap excess/reliability.
Gap excess is recorded at the last frame of the flagged span.

## Validation and limitations

JUnit covers signed fractional registration, adaptive search limits, noise
returns, grouped intake, missing data, anchored-level checks, local scale
recovery/fallback, joint non-overlapping geometry, gaps that never become
intake, preservation of unassigned movement, overlays and saved-position round
trips (including copy to classic gulps). Optional real-data
registration tests use `-Dcorrelation.fixture=<experiment directory>`.

`CorrelationPanelSmoke.java` loads actual data read-only with isolated memory
preferences and exercises the real Swing panel offscreen: threshold dragging,
mode switches, evaporation overlays, CSV, ROI cleanup and unchanged raw levels.
It is not a manual test in the installed Icy application.

`CorrelationMethodsBenchmark.java` uses the same Java pipeline on listed
experiments, holding out each empty capillary from its evaporation/noise fit.
The initial evaluation uses experiments 1-2 and checks experiments 3-6 without
changing parameters (radius 10, initial shift 7, automatic threshold, common
evaporation). Some saved series do not match native timestamp lengths and
are explicitly skipped.

Examples recovered include cam01 5L's groups at 43-48, 115-116 and 128-130
minutes, and cam02 1L's first large drop (about 7.9 pixels; range expands to
10.5). Cam02 0L and 0R's isolated artefacts disappear. Other records still show
under/overestimated cumulative intake, and cam02 9R retains an apparent event
with the common evaporation model. These checks do not establish event-level
sensitivity or specificity: no independent annotated ground truth is available.
The 1-6-bin range, two-bin persistence context, empirical percentile and local
scale limits remain prototype choices; sustained/longer feeding needs separate
review. Fewer false events must be weighed against losing genuine isolated
small intakes near tracking fluctuations.

## Tooltip layout workaround

While the prototype is open, a scoped listener guards newly attached Flamingo rich-tooltip panels against transient widths too narrow for their text. Such layouts are deferred; normal preferred sizing and the installed look-and-feel layout are retained. This avoids the reproduced JDK LineBreakMeasurer index-minus-one exception without suppressing application exceptions or changing intake calculations. The listener is removed when the panel closes. Narrow-title, normal-layout and listener-lifecycle regression tests cover the workaround.

## Overlay ownership and legacy ROI validation

Correlation bars, evaporation polylines and gap markers carry an explicit
`fmp.verticalCorrelationOverlay` property as well as their `_xcorr` names.
Legacy kymograph validators skip them rather than renaming unknown polylines
as manual gulps. Gulp import excludes owned overlays even if their names have
changed, and prototype cleanup recognizes ownership too. Genuine manual
polylines are still converted into manual gulp ROIs by the existing workflow.
Old renamed overlays created before this fix have no ownership metadata and
may remain until the kymographs are reloaded; no bulk deletion by gulp name is
performed, since those names can also identify genuine manual edits.
