# Meniscus refinement and intake detection

The prototype has two independent steps. All runtime computation is Java.

## Phase 1: refine meniscus

Open **Refine topraw by correlation...** in the level-detection dialog.
Load RGB kymographs and detect
initial topraw first. This is an alternative refinement operation alongside
existing threshold pass 2, not another intake detector.

The **Apply to** selector offers **Current capillary**, **Current cage** (default),
and **All cages of this experiment**. The selected scope controls preview
preparation and validation. The strip, kymograph overlays, and comparison graph
show only the capillary selected in the combo. `<` and `>` change that capillary.
In experiment scope, all loaded capillaries (including no-fly controls) are
prepared, and selecting another cage reuses the prepared previews. Refinement previews start
automatically when the panel opens, when selecting another cage/capillary,
and when refinement settings or source levels change. Rapid settings changes
are grouped before recomputing. Correlation starts on its own. **Strip +/- px** (default 15, range 5 to 80) sets how many rows are sampled
above and below the proposed meniscus. Changing it redraws the strip and keeps
the vertical-drag scale matched to those rows; it does not rerun correlation or
drop manual offsets. The white dashed line is
the alignment origin; red ticks mark uncertain registrations, and grey pixels
indicate missing image support. No evaporation is subtracted in this phase.

**Move** is Range, Ramp, or Curve. Range drags horizontally to select columns.
A vertical drag, the mouse wheel, or **-1 px** / **+1 px** adds the same pixel
offset to that range. Positive moves the meniscus down in the image. During a
vertical drag only the selected columns slide; the white line stays put.
Dragging up moves that selection up. On release the strip is rebuilt around the
new proposal.

Ramp and Curve each take two profiles while the strip stays on the current proposal.
**Profile** selects **Source** (orange, the edge to move) or **Final** (blue, where that edge should land).
Ramp drags a straight segment for the selected profile. Curve clicks control points, drags a point to move it, and right-click removes one. Offsets between points are linear.
The column shift is final minus source. Where the source curve does not cover a column, the source is the white line, so a final curve alone is still measured from that line. A source curve alone does not preview.
Eight columns outside each end of a profile ramp back to the white line so the join is not a step.
**Preview alignment** shifts the strip by that gap, and the blue curve stays where it was drawn so the moved edge can meet it.
**Back** restores the picture and keeps both curves. Validate writes the difference into the proposal, then into topraw.

Columns you move are reliably placed. A later nudge back to the automatic
position restores the automatic flag. Changing the window, correction radius,
Expand, Apply to, or the capillary drops an unvalidated path. Validate writes
the adjusted positions into topraw for every capillary in the scope, then
recomputes correlation. Hand-placed columns stay on the levels that were
written. Columns you did not move can follow the new correlation. Undo
validation restores the previous topraw and holds that whole curve.

The separate Icy kymograph displays original topraw in orange, the committed
proposal in magenta, and the pending correction in red for the selected
capillary only. These read-only comparisons
survive switching frames and are removed on acceptance, cancellation, stale
input, or closing phase 1. They are not saved as measures or converted into
manual gulps.

Open **Cage graphs...** to inspect the selected capillary at a readable size.
The upper graph overlays its original curve with the proposed curve. The lower
graph plots proposed minus original in pixels on its own symmetric scale around
zero, exposing small corrections hidden by the cumulative volume scale. The
header reports the maximum absolute correction; positive means a lower position
in the image. Drag a chart region to zoom, or use its right-click zoom menu.
This window follows selection and preview changes. Acceptance starts a new
comparison from the validated topraw.

Existing **Capillary level measures: topraw** grids still include proposal
curves. All comparisons use the same clock, calibration and original t0 anchor
as the existing curves. Other result types do not include these comparisons.

Correlation searches around
`topraw[i] - topraw[i-1]`, initially within a configurable correction radius
(default 3 pixels). The image-profile window defaults to +/-10 pixels. Optional
expansion increases the correction radius by 1.5 up to four times its initial
value, capped at 64 pixels; image borders never trigger expansion. Existing
contrast, correlation-peak and forward/reverse checks still apply.

Each preview snapshots the current inputs and does not change measures.
**Validate** becomes available once every required preview is ready; the status
shows how many capillaries are ready. It installs all proposals in the selected
scope after checking that every source trajectory is unchanged. A missing or
stale preview prevents the whole validation; no capillaries are skipped.
**Undo validation** restores exactly the capillaries changed by this panel's
last validation, even after changing scope or current capillary/cage. It checks
all of those levels and their acceptance entries before restoring any, so later
edits or validation in another panel cannot be overwritten. The undo count is
shown in the button tooltip. Validation installs physical meniscus positions
before evaporation subtraction. Existing derivative/corrected/prototype-intake
results are invalidated; classic annotated gulps are kept and marked dirty.
Undo restores original topraw and its previous derived results.
A stale preview or undo cannot overwrite subsequent external edits.
Undo is available during the current application session. Use normal Levels
Save to persist accepted levels. Registration-quality flags persist in an optional `refinement_quality=` CSV
trailer (and XML metadata) bound to the exact trajectory. Older files and
subsequent level edits report quality unavailable. Accepted coordinates retain
full double precision in CSV so reopening does not change interval differences.

Refine the no-fly controls as well as the selected experimental capillaries
when comparing the full refined workflow: phase 2 uses their accepted trajectories
too. A saved smooth trajectory may give a much smaller noise estimate than image
registration did. This change requires scientific validation, not just UI tests.

## Phase 2: detect intake

The intake dialog follows the experiment browser and remains open for previous,
next, direct, or temporarily empty selections. Each experiment keeps its own
session and manual thresholds. Switching suspends preview workers, timers and
overlays for the old experiment, then readies the selected experiment. Descriptor
reloads refresh capillary references; reopened kymographs refresh image previews.
If images or accepted levels are not loaded yet, the panel waits and analyzes
when those inputs become available. Batch runs retain the browser lock and
graceful stop behavior. Closing the dialog releases every session and removes
its browser listener.

Select **accepted topraw intake (prototype)** in the intake detection dialog.
Existing stored method IDs remain readable. Phase 2 reads topraw without running
image correlation, and derives every signed increment from adjacent positions
of that same trajectory. It can run on existing saved topraw without phase 1.
Missing values and available refinement quality constrain usable increments.
Changing a threshold never refines levels. An open preview is invalidated when
selected or control levels/quality change.

The aligned-level strip and horizontal diagnostic ROIs have been removed.
Diagnostics appear as shaded time spans on the displacement graph and in
**Review interval...**. The raw kymograph retains intake bars and the grey
reconstruction for comparison.

## Intake options

The **Mode** selector offers **Manual - single capillary**,
**Automatic - all capillaries**, and **Batch - detect, copy & save**. Manual mode retains per-capillary threshold
editing, Save intake, and Copy to gulps. Single-capillary detection runs on
selection and relevant settings/source changes; the intake panel has no
refinement or Recompute button. Automatic mode exposes **Detect
all**, **Cancel**, **Save all intake**, and **Copy all to gulps...**. Detect all computes each capillary in the
current experiment (including no-fly controls), using a separate automatic
threshold and the selected episode/local evaporation options. Existing manual
thresholds are not used. Detection runs in the background with progress;
capillary selection and method settings are locked during computation.

Detection prepares results without changing saved measures. Once all previews
are ready, browse the capillaries to review results and choose Save all intake to
replace their correlation results in one experiment save. Classic gulps and
topraw are retained. **Copy all to gulps...** replaces ordinary gulp measures
for the whole batch after confirmation and saves them once. This is the
conversion needed to use the detected intake in ordinary gulp graphs and
exports. Within this panel, red graph points mark detected bins and blue
kymograph bars show their amplitudes. Cancellation or any capillary failure discards the batch;
there is no partial batch save. Changed source levels/quality or method settings
invalidate the prepared batch. Switching to manual mode permits individual
adjustments; changing a threshold also invalidates the prepared batch.

**Batch - detect, copy & save** runs the full pipeline without per-experiment
review: detect every capillary, copy intake into ordinary gulps, then save both
capillary descriptions and measures before continuing. It replaces existing
ordinary gulps, including manual edits. Choose **Current experiment**,
**Current experiment to end**, or **All experiments (0 to end)**, then
**Detect, copy to gulps & save**. Current is captured from the experiment
browser when the run starts; both range endpoints are included. Episode and
local evaporation options are captured once for the run; each capillary uses
its own automatic threshold. This mode never refines topraw.

The browser and detection settings are locked during the run. Experiments are
loaded as needed without changing browser selection; already loaded topraw
is retained. Kymographs opened by the batch are released after each experiment;
preexisting viewers remain open. Progress names the experiment and capillary.
**Stop** cancels before copying an unfinished experiment; once copying has
started, its save completes. A failure stops the series and reports the number
of experiments already saved. Earlier successful saves remain on disk.
Saving descriptions and measures uses the existing persistence writers, not
a transactional filesystem update; a save failure may require retrying the
current experiment. Closing the panel requests the same graceful stop.

`CorrelationIntakeBatchSmoke.java` exercises both detector modes on a synthetic
experiment, including parity with single-capillary amplitudes and geometry,
stale-save rejection, save-all persistence with a stubbed disk writer, failure,
cancellation, and return to manual editing. It also exercises the three series
ranges with actual CSV writes in temporary test directories, copying to ordinary
gulps, browser restoration, stopping before/during save, and detection/save failures.

- **Persistent episodes (1-6 bins)** (default on): signed displacements minus
  expected evaporation accumulate over 1 through 6 consecutive valid bins.
  Method v5 limits this signed amount by the sustained before/after level
  contrast. Overlapping three-point medians on each side reject one- and
  two-bin excursions without subtracting every negative noise fluctuation.
  Held-out no-fly controls undergo the same sustained-change test; each duration
  uses its 99.5th percentile, with the automatic setting as an additional floor.
  At least 30 control windows are required
  for a duration longer than one bin. The threshold slider scales all duration
  thresholds together; the red horizontal line is labelled as a **threshold
  setting**, not the complete acceptance rule. Red points may lie below it in
  episode mode. Negative increments inside a candidate count fully, and
  missing/excluded increments cannot be bridged. Unusable neighbouring context
  truncates the available flank instead of vetoing a valid event.
  Individually supported one-bin steps are selected before grouped windows.
  Remaining non-overlapping windows are ranked by evidence relative to their
  limit. Each group has a temporary representative boundary for fitting;
  intermediate transition samples are excluded from plateau observations.
  Its final fitted amplitude must still exceed the acceptance limit. Unsupported
  fitted events are removed and the remaining plateaus are refitted until stable.
  A retained group's fitted amplitude is distributed over its positive measured
  excess increments, preserving its time span, total volume and ordered geometry.
  The status distinguishes episodes from intake bins; small bars within one
  supported episode are not separately resolved biological gulps.
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

The cam02_1mM follow-up annotations show why this option matters: quiet-run
estimates use approximately 0.61 of the common evaporation for 2L, 0.64 for
3L, 0.37 for 6L, 0.49 for 7L, and 0.45 for 8L (all pass the estimator's
existing support/stability checks). A read-only common/local comparison
recovers 3L near minutes 17/35, 7L near 90, 8L near 48, and several grouped
6L movements when local scaling is enabled. This supports an evaporation-model
mismatch, not a universal recommendation to lower the detection threshold.
The 7L early fitted bar amplitudes also change and need image-based review.
Local scaling is a heuristic estimate from presumed quiet runs; it does not
prove that those runs contain no feeding, or establish the physical cause of
the difference from the controls. Keep the common/local comparison available.

The graph always shows motion minus the active evaporation model in pixels
per minute. Actual timestamps supply interval durations. The automatic
single-bin estimate remains quiet-period median + 3 robust noise SD, with an
no-fly control noise floor. Quality exclusions appear as crosses.

For v4/v5 validation, keep **Persistent episodes** enabled when comparing common
and local evaporation. Turning it off selects the legacy per-bin threshold
rule, bypassing plateau persistence and grouped fitting. The header and bottom
status explicitly name the active rule, in addition to the version. In
cam03_10mM, local evaporation plus single-bin mode reproduces 5R/6R/7R counts
of 9/9/7 and the 148.10-minute time axis. For 6L, its net single-bin total is
about 83.25 px versus 88.32 px observed minus evaporation; persistent mode gives
88.52 px. This explains that reconstruction mismatch without changing its t0
anchor. Persistent mode still produces some apparent events in quiet right
capillaries, so it is not a substitute for validating the evaporation model.

## Amplitudes, display and saving

In single-bin mode amplitudes remain total positive accepted-level displacement.
In episode mode (method version 5), accepted bin boundaries partition the
accepted evaporation-corrected levels into plateaus. Plateau medians, weighted
by their observed sample counts, are fitted jointly under a nondecreasing
constraint using isotonic regression. Reversals are pooled; accepted boundaries
that yield zero movement disappear. Net amplitudes are differences of these
shared fitted positions, independently of the signed-noise acceptance penalty.
Evaporation over each selected interval is added back for the existing total
amplitude convention. A bar's bottom is the fitted level plus evaporation;
its top is the preceding fitted level plus preceding evaporation. Successive
bars therefore cannot overlap when modeled evaporation is nondecreasing.
Neither endpoint is forced to agree with the measured recording endpoint.
Adjacent supported steps remain separate. A multi-bin group retains its temporal
support after fitting and does not establish how many individual gulps occurred.
Single-bin mode uses the same accepted-level increments for comparison.

**Show diagnostics** (default on) separates warning types:

- Amber: abrupt unexplained downward movement. A no-fly control is explicitly
  labelled as a control change, never feeding.
- Teal: gradual evaporation/model drift. Positive and negative discrepancies
  are tested against absolute same-duration control displacement sums.
- Purple: upward steps, sustained reconstruction offsets in either direction,
  and a separate uncertain initial anchor/early-intake warning.

The pause endpoints use median first/last 2-3 measured positions. A step-like
change must concentrate at least 65% of the pause's net movement locally.
A second local search compares three-point flanks after removing their local
trend, so a missed step can be flagged even when evaporation mismatch cancels
its total. Neighbouring local flags are suppressed within three bins. These
are review heuristics, not validated event probabilities. Quality flags reflect
available level support (registration quality is available for unchanged accepted refinements); control calibration is limited when too few windows exist.
No endpoint or initial anchor is automatically moved.

The underlying ordered fit can retain an unassigned baseline change inside an
unresolved span rather than absorbing it into adjacent intake amplitudes.
Diagnostics and review do not create intake or change consumption; hiding the
markers changes only display.

**Review interval...** opens a modeless estimate-only panel containing a snapshot
of the selected capillary and settings, so the source images can still be inspected. Select a diagnostic or
set the first and last interval bins manually (bin i means transition i-1 to i).
The panel estimates positions using up to three samples before/after the span,
then reports excess using the active evaporation model. An alternative estimate
uses robust pairwise slopes in the physical flanking positions and actual
acquisition times, independently of the shared evaporation slope. Unreliable,
insufficient, negative or inconsistent flank slopes are explicitly uncertain.
The panel also shows already detected net intake in the selected interval and
the remaining difference for both estimates, to avoid counting it twice.
Choose stable flanks using the source image: adjacent feeding and low contrast
can bias either estimate. This action deliberately does not save, accept or
copy the proposed interval into intake totals.

The grey reconstruction starts at raw t=0 and adds the evaporation curve
plus cumulative intake. **Subtract evaporation** (default on) subtracts the
per-bin evaporation from intake steps only, avoiding double counting. Turning
it off displays the stored total amplitudes. It does not change detection.

The status compares net detected displacement with endpoint raw displacement
minus evaporation and flags a difference greater than 3 pixels or 20% of the
latter (whichever is larger). This is a diagnostic, never an endpoint constraint.

**Save intake** stores correlation events separately; **Copy to gulps...** replaces
classic gulps after confirmation. Raw levels are unchanged. Saved correlation
rows append optional `bottom_positions=` (colon-separated fitted positions)
and `method_options=` trailers after their dense amplitude values, preserving
old row compatibility and recording method version, option flags and
scale. Fitted positions are also copied independently into classic gulps and
persisted with their flat CSV series, so saving/reloading or copying does not
re-anchor these bars to noisy raw positions. Editing amplitudes invalidates
the saved fitted positions. Existing red legacy results retain their display. Old rows load with the old method flags off.
CSV export records flags, active reference, amplitudes,
fitted meniscus, intake top/bottom positions, and gap excess/reliability. Legacy correlation-score/search-range columns contain NaN in phase 2.
Diagnostic types, signed excess and support are recorded at the last frame
of each flagged span; multiple types, amounts and support flags ending there
are aligned and separated by vertical bars.

## Validation and limitations

`IntakeMissInvestigation.java` replays saved accepted topraw with the production
description/measure readers (including refinement quality) and writes local
per-capillary traces plus every 1-6-bin acceptance gate. Arguments are an output
directory followed by experiment directories; no experiment save is performed.
Compile with the same classpath as `CorrelationPanelSmoke.java`.
Use `-Dintake.local=true` for a separate local-evaporation replay; the summary
and trace files record the estimated/applied scale, quiet sample count, and
estimator support flag. Output goes only to the specified local directory.

The reported misses in cam02_1mM (1L/3L/6L/7L/8L), cam03_10mM (4L/8L), and
cam04_10mM (2L/3L), under `17-10-26_Quinine`, provide a targeted regression set.
The initial October 28 replay reproduced the saved v3 event counts on all 60
capillaries using automatic thresholds, persistent episodes and common evaporation.
The v3 rejection explanations were:

- Cam02 1L, minutes 85-86: 2.10 px excess exceeds the two-bin 1.74 px limit,
  but subtracting neighbouring negative increments leaves 1.19 px, so it fails
  persistence. Cam02 3L at minute 35 and 7L at minute 90 fail the same gate.
- Cam03 4L, minute 75.08: 1.227 px excess falls just below the empirical
  single-bin limit of 1.245 px, even though its displayed automatic setting is
  1.036 px/min. The graph's setting is therefore not the full acceptance rule.
  Cam03 8L at minute 85.08 passes the amplitude limit but fails persistence.
- Cam04 3L, minute 8 is quality-excluded; its presence in the required context
  also rejects the otherwise valid 3.69 px excess at minute 7. Later small
  movements also fail persistence. A missing event interval must remain distinct
  from missing neighbouring context when considering any detector revision.
- Cam04 2L retains positive adjacent bins 6 and 7; the episode counter groups
  adjacent positive bins. Resolving close individual gulps requires separate
  segmentation validation, not just a lower detection threshold.

The v4 revision implements sustained plateau evidence, identical control
calibration, truncated quality context, separate supported single-bin steps,
and transition-aware plateau fits. The method version is stored in result
metadata; old v3 rows remain readable and are not treated as current v4 results.

The subsequent v5 revision checks each episode's fitted amplitude against the
limit that accepted it, removing unsupported events and refitting. Grouped intake
is distributed back over its observed positive increments after the shared
plateau fit; it is no longer rendered as one displaced bar. On cam03_10mM with
local evaporation and persistence, the extra 1R/2R/4R events become zero. The
3L/4L cascades retain their measured time span and unchanged total intake.
Some initial 0R events and events in 5R/6R/8R remain: these require further
startup/evaporation validation, not a claim that false detections are solved.
The six-experiment read-only comparison covers the same 119 usable capillaries;
no previously zero no-fly control becomes positive, and total detected control
displacement does not increase in any of those experiments. Old v3/v4 results
remain readable; v5 results carry distinct method metadata.
The rate-only API without accepted-level anchors retains its legacy rule.

A read-only old/new comparison covered 119 usable capillaries (23 controls)
across the three reported experiments plus cam01_1mM, cam03_0.1mM and
cam04_0.1mM. One capillary with a saved-level/timestamp length mismatch was
explicitly skipped. Recoveries include cam02 1L at 85-86 minutes, 6L around
69-74 and 90-94, 8L's post-step tail at 40-41, cam03 4L/8L near 75/85 minutes,
and cam04 3L at minute 7 and later early steps. Cam03 8L's fitted amplitude
remains small despite accepted local evidence; evaporation/plateau-fit bias
still needs image-based review. Cam02 3L around minute 35, 7L around minute 90,
and 6L around 35-38 remain too weak or inconsistent for the revised rule.

The comparison does not establish sensitivity or specificity. One previously
zero control (cam01 9R) acquires approximately 0.33 px of fitted intake; some
already-positive control amplitudes increase. Other zero controls remain zero.
Review the named capillaries and controls before applying the new method to
all experiments. No experimental measurements were saved by the comparison.

JUnit covers signed fractional registration, adaptive search limits, noise
returns, grouped intake, missing data, anchored-level checks, local scale
recovery/fallback, joint non-overlapping geometry, gaps that never become
intake, preservation of unassigned movement, overlays and saved-position round
trips (including copy to classic gulps). Optional real-data
registration tests use `-Dcorrelation.fixture=<experiment directory>`.

`CorrelationPanelSmoke.java` loads actual data read-only with isolated memory
preferences and exercises the real Swing panel offscreen: threshold dragging,
mode switches, evaporation overlays, CSV, ROI cleanup and unchanged raw levels.
It also previews, accepts and undoes refinement of cages 2/3/4 (both L/R) in
memory, verifies phase-2 differences, comparison datasets, frame-switch overlay
ownership and tooltip-listener cleanup, and renders stabilized and cage graph
views without writing experimental files.
It is not a manual test in the installed Icy application.

`CorrelationMethodsBenchmark.java` uses the same Java pipeline on listed
experiments, holding out each empty capillary from its evaporation/noise fit.
The initial evaluation uses experiments 1-2 and checks experiments 3-6 without
changing intake parameters (automatic threshold, common evaporation). It now
compares single-bin and episode detection from existing topraw; it does not
perform phase-1 refinement. Some saved series do not match native timestamp lengths and
are explicitly skipped.

Examples recovered include cam01 5L's groups at 43-48, 115-116 and 128-130
minutes, and cam02 1L's first large drop (about 7.9 pixels; range expands to
10.5). Cam02 0L and 0R's isolated artefacts disappear. Other records still show
under/overestimated cumulative intake, and cam02 9R retains an apparent event
with the common evaporation model. These checks do not establish event-level
sensitivity or specificity: no independent annotated ground truth is available.
The 1-6-bin range, overlapping three-point persistence flanks, empirical percentile and local
scale limits remain prototype choices; sustained/longer feeding needs separate
review. Fewer false events must be weighed against losing genuine isolated
small intakes near tracking fluctuations.

## Tooltip layout workaround

While either phase-1 or phase-2 panel is open, its scoped listener guards newly attached Flamingo rich-tooltip panels against transient widths too narrow for their text. Such layouts are deferred; normal preferred sizing and the installed look-and-feel layout are retained. This avoids the reproduced JDK LineBreakMeasurer index-minus-one exception without suppressing application exceptions or changing intake calculations. The listener is removed when the panel closes. Narrow-title, normal-layout and listener-lifecycle regression tests cover the workaround.

## Overlay ownership and legacy ROI validation

Intake bars and evaporation polylines carry an explicit
`fmp.verticalCorrelationOverlay` property as well as their `_xcorr` names.
Legacy kymograph validators skip them rather than renaming unknown polylines
as manual gulps. Gulp import excludes owned overlays even if their names have
changed, and prototype cleanup recognizes ownership too. Genuine manual
polylines are still converted into manual gulp ROIs by the existing workflow.
Old renamed overlays created before this fix have no ownership metadata and
may remain until the kymographs are reloaded; no bulk deletion by gulp name is
performed, since those names can also identify genuine manual edits.

## Resampled RGB kymographs

Spatially resampled, extended-blue RGB kymographs are supported. Positions,
profile radii and shifts remain in displayed kymograph rows; the native camera
pixel scale is not reused. Control conversion uses capillary volume times
(1 + extension ratio) / (actual image height - 1), since sampling includes both
endpoints of the expanded blue line. This works for common-height and
per-capillary-height arc-length resampling. It assumes the TIFF contains the
complete resampled line, not unrelated padded rows. Colour is not normalized
or recoloured. The same registration quality checks apply to pale cyan and
greenish dye. Non-RGB/unsupported intensities, mismatched saved level columns
or timestamps remain explanatory errors. Record 3 is part of the real-data
smoke/benchmark checks rather than being excluded.
