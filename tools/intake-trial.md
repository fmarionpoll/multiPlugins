# Offline intake trial

`intake_trial_detector.py` is an experimental reference implementation. It is
not imported by Java code and does not install or save any experimental measures.
It reads the archived CSV/PNG inputs prepared by `analyze_intake_review_cohort.py`.

It proposes robust step offsets on a quadratic local drift, and temporary
faster-decline episodes lasting 5–10 native intervals. Proposals need at least
five usable observations on each flank, sufficient fitted amount/score, actual
movement at the proposed boundary or interval, and aligned meniscus image
profiles before/after. A long episode needs movement distributed over at least
three bins. Scores are calibrated on development no-fly controls. Positive
corrected increments allocate an episode's fitted amount without increasing
its total. This allocation does not resolve individual gulps.

A conservative stationary-dark-row proximity flag is an image safeguard,
not a classifier for glass or tape. Ambiguous adjacent/overlapping automatic
events are deferred; all existing automatic events are preserved. Consequently
existing automatic false positives also remain.

The conservative profile uses a noise prior of one third of the archived
rate threshold times median interval duration; the sensitive profile uses
one tenth. Robust fit residuals can increase the noise estimate. The sensitive
profile is a stress test rather than a recommended setting.

## Experiment split and results

Development experiments: 006, 007, 010, 011, 012, 014, 015, 016, 143, 146.
Reserved check: 008, 009, 013, 017, 021, 147. Earlier descriptive analysis covered
this entire cohort, so the reserved check is not wholly unseen external data.
Both parameter configurations were frozen before running that check. The
detector source hash and configuration hashes guard against changes between
development and check execution.

On development data, the conservative profile recovered 27 of 531 added bins
at their exact positions, with no new control bins. On the reserved check it
recovered 6 of 171, created 9 new unedited bins (including 8 in controls), and
preserved all original automatic positive bins. The sensitive profile recovered
13 of 171 on that check, with 65 new unedited bins, including 14 control bins.
Neither profile is ready to replace the current plugin detector. Presence
recovery does not establish correct amplitude or independent event accuracy.

## Human review: tape bleeding and continuity

`intake_trial_feedback.json` records the user's review after evaluation. For
case 1 (021/5R, bin 89), the edited right-hand panel is preferred: the trial
includes a blue-green/yellow region attributed to bleeding near a horizontal
tape line. The frozen trial net amount is 12.10 px versus 6.951 px in the edit.
This is a located event with an incorrect trial amplitude, not a confirmed
quantitative recovery. The simple dark-row mask did not flag this boundary;
the meniscus-aligned image-profile correlation was about 0.829 and passed.
Dark-pixel proximity and profile similarity therefore do not address the
full influence zone around an interfering band.

The user also described continuity from a previous intake bottom to the next
top. Bin 70's manually positioned bottom is 27.667 px; bin 89's edited top is
28.167 px. Projecting the bin-70 bottom with model evaporation through bin 88
gives 28.583 px, while the frozen trial starts at 23.013 px. Bin 70 is only a
candidate trusted anchor, not a confirmed identity supplied by the user.
A later edited bin (88) follows the raw trace at 24.390 px, showing why simply
using the latest positive bin is not a sufficient continuity constraint.

This feedback motivates tracking a trustworthy physical meniscus trajectory
through the halo around a stationary band, with uncertainty and evaporation
allowed. It must not force exact equality or reinterpret the review as a new
independent validation result. The frozen trial and production detector have
not been changed by this annotation.

`build_intake_trial_review.py` renders six source-image comparisons, including
recoveries, an unconfirmed extra, a control failure, an unresolved edited intake,
and a dark-band warning. The images print current/trial/reference net amounts.
They are selected diagnostic examples, not a random validation sample.

## Reproduction

Use the bundled Python runtime with NumPy and Pillow. For each profile,
run `intake_trial_detector.py <cohort-dir> <trial-profile-dir> development <profile>`,
then the same command with `check` in place of `development`. Profile is
`conservative` or `sensitive`. The cohort directory must contain `cohort.json`
and the extracted original archive CSVs/PNGs.

Run synthetic regressions with
`python -m unittest discover -s tools -p test_intake_trial_detector.py -v`.
Coverage includes a step on drifting background, an eight-interval episode,
quiet drift, a transient excursion, uncertain tracking, a stationary dark band,
incorrect timing, conserved amplitude allocation and baseline preservation.

## Step 2: measurement uncertainty and illumination

User policy: from record 25/370 onward, Local evaporation will consistently be
off. Earlier settings were not consistently monitored and were probably often
on; use each export's metadata rather than assuming a uniform historical setting.
Steps 2–7 are agent analysis and development work. Human input is needed mainly
to judge physically ambiguous image examples, not to implement the analysis.

`estimate_intake_measurement_noise.py` reads the supplied cohort ZIPs and the
023 reviewed ZIP directly. It verifies the older ZIPs against the cohort
inventory. The current 021 ZIP differs from its recorded hash and is excluded
without replacing the old cohort or its frozen trial results. Thus this run
uses 16 experiments, 318 capillaries, including 023 once. All analysis is
read-only with respect to the experiments and archives.

For each capillary, estimate robust scatter after fitting independent local
linear trends in contiguous stretches of 8–21 points. For fly capillaries,
exclude automatic and reviewed positive bins, both positional endpoints and
two guard columns on each side. Controls retain apparent events so known
tracking failures are not silently removed. Finite positions must be at least
10 rows from the image boundaries. Quality flags are not used as a filter in
the primary estimate; a reliable-only estimate is shown as a sensitivity check.
Report MAD-based residual position scatter with an approximate trend-parameter
correction, first-difference scatter divided by sqrt(2), residual correlation,
and large-increment percentiles. The difference interpretation requires
independent position errors; saved trajectory smoothing may violate this.

Brightness is median digital RGB intensity within ±10 rows of topraw. Meniscus
contrast is the RMS RGB difference between narrow bands above and below it.
These are processed-image descriptors, not irradiance measurements. Compare
dark and bright quartiles within each experiment to reduce box-to-box mixing;
this descriptive association does not isolate illumination as a causal factor.
Edge location and no-fly-control status are confounded in much of the data.

Initial findings: 315 traces have usable stretches. Median residual position
scatter is 0.295 px (middle half 0.252–0.334); the difference-based median is
0.284 px. Restricting to at least 40 points and three blocks leaves 292 traces
with median 0.296 px. Using at most 15-point blocks gives median 0.285 px.
The darkest quartile is noisier in 10/16 experiments, with median dark/bright
scatter ratio 1.071. This is not a sufficient basis for a universal
brightness-to-noise correction.

For 023: 0L brightness 102.1 / scatter 0.415 px; 9R 115.4 / 0.340 px;
1L 148.1 / 0.208 px. The 1L detrended increment scatter is 0.251 px, compared
with the approximately 0.02 px early-interval local-evaporation adjustment
discussed with the user. There are only 34 selected quiet points for 1L, so
its estimate is preliminary. Quietness does not establish correct absolute
meniscus position, and fly stretches may include unresolved feeding.

These values describe the complete measurement chain, not isolated camera
noise. The current kymograph builder selects source frames and averages
spatial camera-pixel masks. Checked 023 originals are 1280×720 JPEGs at roughly
one-minute intervals; the inspected first file has no EXIF settings. Exposure,
gain, illumination variation, compression, tracking, residual physical motion
and any saved smoothing cannot be separated from this analysis alone. A later
sensor characterization would require controlled stationary repeated images,
dark images and illumination levels with fixed camera settings, following an
appropriate EMVA 1288 protocol and accounting for the processing pipeline.

Reproduce with the bundled Python runtime:
`python tools/estimate_intake_measurement_noise.py`, then
`python tools/build_intake_noise_canvas.py`.
Outputs are in `multiCAFE/target/intake-measurement-noise` and the conversation
visualization directory. Seven synthetic checks cover independent noise,
drift and constant offset, event masking, missing stretches, correlated errors,
brightness/contrast, and tied ranks. No detector parameters were adjusted.

## Step 3: local-position development study on 023

`intake_local_position_trial.py` reads the three 023 snapshots and verifies
their hashes against the prior comparison. It uses only the original image
and original topraw seeds for localization. Step-2 per-capillary uncertainty
sets a weak continuity scale. Search ±8 rows for a dark-ridge centre, with
subpixel interpolation and a bounded preference for slowly changing offsets.
Flag weak ridges, competing ridges, hits near the search boundary, and
proximity to persistent horizontal image features. A stationary meniscus can
also satisfy the latter test, so the warning does not identify physical tape.
This locates a consistent image feature, not a proven physical liquid edge.

This is an inspected development example, not held-out validation. The
recalculated trajectory and manually edited positive bottoms are comparison
annotations. They are not used to optimize positions and are not assumed
to be independent ground truth. Source/config hashes are recorded before
the comparison pass; no production Java code or experimental files change.

Of 3,660 columns, 2,148 (58.7%) pass the provisional image checks. The median
of per-capillary median absolute separations from recalculated topraw drops
from 1.830 px for original positions to 0.674 px for all proposed positions.
The supported subset reaches 0.359 px, but that selective result must not be
presented as whole-record accuracy. Some traces become worse, especially 9L;
2R's original and recalculated traces are identical, yet the search moves it.

For 1L, all 183 columns pass these provisional checks. Median separation
from recalculated positions falls from 2.776 to 0.455 px. But the median
absolute disagreement in consecutive increments rises from 0.088 to 0.289 px.
The trial quiet scatter falls to 0.069 px, demonstrating that apparent
smoothness alone does not establish correct amplitudes. A diagnostic constant
translation of -3.147 px gives median position separation 0.372 px while
preserving every original increment exactly. This benchmark is not an
accepted correction and cannot fix a varying offset or a tape lock.

The trial is not ready to replace intake measurements. A retained-original
fallback is supplied for inspecting point locations only: transitions between
corrected and uncorrected coordinates could create artificial steps, so this
mixed trace must never be passed directly to intake detection. Increment
comparisons instead use only adjacent pairs with both endpoints supported.
Before adoption, an event-level method must compare consistent before/after
image profiles and distinguish fixed placement offsets from changing tracking
errors, preserving reliable displacement evidence.

Reproduce with `python tools/intake_local_position_trial.py` and
`python tools/build_intake_local_position_html.py`. Results/configuration are
in `multiCAFE/target/intake-local-position`; the browser-readable report is
`intake-local-position-023.html` in the conversation visualization directory.
Image overlays account for pixel-centre coordinates. Six synthetic tests
cover moving ridges, step timing, flat images, persistent features, missing
seeds and constant-translation amplitude preservation. HTML JavaScript and
all 20 capillary views execute successfully in the minimal DOM smoke check;
that check does not validate actual browser layout.

### User feedback: image sharpness and the role of 023

The user finds the original-image overlays useful and identifies 023 as a
difficult example: contrast is limited and contours are blurred, obscuring
high-frequency events. Keep source images in subsequent reports. Treat 023
as a difficult development case, not the primary example on which to optimize
the method. Establish behaviour on clearer visible steps before interpreting
performance on this blurred sequence.

The current ridge-support checks are not a calibrated sharpness or physical
edge-confidence measure: 1L passes them even though the user finds the image
ambiguous. A high RGB contrast score alone is also insufficient; preliminary
inspection of higher-contrast 147/2R and 010/6R shows why feature sharpness and
the presence of informative visible steps need separate assessment. No new
detector result or independent validation is claimed from those inspections.
Distinguish vertical edge blur from temporal sampling. The 023 columns are
about one minute apart; do not assume this is temporal averaging, and do not
claim individually resolved sub-minute events from these kymographs. Quiet
trace scatter is not a measure of the bias or uncertainty of a blurred edge.

Next selected development example: archive 143, capillary 6L, initially
20–65 minutes. Source experiment is
`CS/30+_vs_30/17-10-26_Caffeine/cam01_1mM/results`.
Inspection of original PNGs for 147/5L, 146/7L and 143/6L favours 143/6L for
this first comparison: stronger visible contrast than 023/1L and distinct
steps around minutes 39 and 52 with neighbouring stretches to compare.
The lower stationary band crosses the meniscus later in the record, so the
initial selected interval avoids that crossing. This selection is qualitative,
not a calibrated sharpness ranking, and 143 remains development data.

The user notes that 143/6L's lower meniscus rises, potentially affecting apparent
evaporation. Current `CorrelationGulpPreview.levels` reads only `getTopRaw()`;
its common evaporation reference is built from no-fly top-level trajectories,
not from upper-minus-lower liquid-column lengths. Do not interpret this
example's slow top drift as a validated measure of total evaporation.

Read-only inspection of the current source experiment's saved BOTTOMLEVEL
series for 6L finds 186 points: median first ten 421 px, median last ten 418 px,
with individual values ranging 409–421. The selected 20–65 min interval also
has a small upward trend (medians 421 for bins 20–29, 420 for 55–65). These
are noisy saved tracking values from the current source directory, not a
bottom-level snapshot contained in the archived review ZIP. They support
investigating the observation but do not prove a physical evaporation rate.

Use 143/6L for short-interval image localization and visible-step comparisons;
evaluate slow drift separately. If comparing total liquid-column shortening,
with downward image coordinates, length is bottom minus top and loss is
delta(top) minus delta(bottom), subject to consistent tracking and bore scale.
Do not automatically add lower-meniscus motion to feeding or subtract it as
evaporation: motion and tracking errors require separate checks, and controls
may have different lower-interface behaviour.

## Step 3 follow-up: 143/6L step registration and lower-level checks

`intake_143_step_study.py` compares preselected visible steps at minutes 39
and 52, plus comparison windows 33 and 46. Both visible steps were already
detected in the archive; this is measurement development, not recovery or
independent detection validation. Median RGB profiles before and after each
boundary are matched over a ±5 px correction to the seed displacement.
Intensity and vertical-gradient correlations are combined. Per-frame image
registration within each flank estimates local drift, so final displacement
does not use topraw increments as the measured amplitude. Saved levels guide
the image search. Robust parallel-trend fits and immediate topraw increments
are included only as comparators. Drift is not labelled physical evaporation.

Using five columns on each flank and an eight-row profile half-width, the
image-derived steps after fitted local image drift are 4.978 and 3.787 px.
The other tested flank/profile choices give approximately 4.84–4.95 and
3.57–3.74 px, respectively. The archived fitted gross values are 4.073 and
3.018 px, but there is no independent amplitude ground truth, and the
estimators do not use identical drift/background conventions. Do not claim
that the larger image-based amounts are established corrections.

The comparison at minute 33 changes drift between flanks and is flagged;
its negative fitted step is not feeding. The minute-46 comparison gives
about +0.414 px, despite no archived intake there. Eight targeted no-fly
comparisons at the same two boundaries give approximately -0.168 to +0.426 px.
High correlations also occur in these controls. Correlation is not a feeding
probability. Flank drift disagreement has a provisional diagnostic tolerance
informed by position scatter; this is not a calibrated statistical test or
an intake acceptance threshold. A full scan/false-positive assessment remains
necessary before using the method to recover small events.

For 6L and controls 0L/0R/9L/9R, current source TIFF pixels, native frame times
and saved TOPRAW values match the archived image/time/top series exactly.
The current BOTTOMLEVEL series is separately hashed and recorded because it
is absent from the review ZIP. Over 185 min, first/last-ten-point medians
change by -3 px for 6L, 0 for 0L, 0 for 0R, -1 for 9L, and 0 for 9R. The
9L and 9R tracks occupy the last three image rows for 97.8% and 100% of points;
0L and 0R are exactly constant. Those saved properties limit physical
interpretation. They do not establish matching lower-meniscus evaporation
across controls and 6L. Median lower values are 421 px on both flanks of
each visible step, which shows no resolved coincident lower-level jump but
does not exclude subpixel motion. No evaporation correction is installed.

Reproduce with `python tools/intake_143_step_study.py`, then
`python tools/build_intake_143_step_html.py`. Numeric results and source/config
hashes are in `multiCAFE/target/intake-143-step-study`. The self-contained
`intake-143-step-study.html` report in the conversation visualization folder
includes native source-image overlays for the upper steps and all five lower
tracks, before/after profiles, sensitivity results and control comparisons.
Nine synthetic checks pass: offsets, illumination offsets, known step/drift,
changed drift, flat profiles, image boundaries, native timing and saved-series
parsing/border diagnostics. No experiment files or Java detector code change.

## Step 4A: systematic abrupt-step supplement

`intake_profile_registration.py` vectorizes the image-profile method without
changing the earlier measured results. Synthetic and actual-window comparisons
match the scalar reference to numerical precision. Every eligible boundary has
five before and five after columns. Additional scan gates require a positive
step of at least 0.4 px, at least max(0.25 px, half the fitted amount) of
image-derived movement at that boundary, and separation from persistent
horizontal gradient rows. Registration, within-flank matches, slope consistency
and search limits are checked. These remain provisional image safeguards, not
proof of the physical meniscus edge or a feeding probability.

Candidate scores normalize the image-derived step by within-flank image-fit
residual scatter (floor 0.08 px) and the parallel-step design factor. The floor
is regularization, not a camera-noise calibration. An unannotated robust
interpolation-residual estimate from the full raw trajectory sets the drift
consistency tolerance; it does not use edited feeding locations. Feeding edits
enter only evaluation. Selection preserves all existing automatic bins, defers
new proposals within one bin of them, and avoids neighbouring supplements.
No original amplitudes are replaced and no new net-volume values are saved.

Development: 006, 007, 010, 011, 012, 014, 015, 016, 143, 146 (198 capillaries,
42 no-fly controls). Separate check: 008, 009, 013, 017, 147, 023 (120 caps,
24 controls). These experiments were inspected in earlier analyses, so this
is not untouched external validation. 021 remains excluded because its current
archive differs from the inventory. All other source archive hashes match.

Two limits were frozen before the check: the 99.9th percentile of gated
development control-window scores, minimum 5 (learned 7.373360), and 1.01 times
the maximum such score, minimum 5 (learned 10.506720). Rejected control windows
contribute zero to calibration. Cached all-boundary outputs are keyed by source
archive, configuration and all engine dependency hashes. The check verifies
the frozen implementation and limits; no retuning followed its results.

Development control-tail: 71/531 missing edited bins recovered exactly,
49 new unannotated fly bins, 7 new control bins. Development control-maximum:
39/531 recovered, 13 new unannotated fly bins, no new control bins.

Separate check control-tail: 47/228 missing edited bins recovered exactly,
47 with one-to-one within-one-bin matching; 20 new unannotated fly bins and
2 new control bins. Control-maximum: 25/228 recovered, 8 new unannotated fly
bins and no new control bins. Five of the 228 annotations lack full usable
flanks. Both configurations retain all 1,469 baseline positive check bins,
including six control bins with explicit zero overrides in the review.
Consequently zero NEW controls is not zero control detections overall.
Unannotated fly proposals are unconfirmed, not automatically false positives.

The two new control-tail proposals are 009/9R bin 62 (~0.76 px) and 147/9L
bin 25 (~0.87 px). Neither passes the stricter limit. For blurred 023, the
control-tail configuration recovers 11/93 missing edited bins; the maximum
configuration recovers 4/93. These are bin-presence comparisons, not a measure
of independent feeding bouts or absolute amplitude accuracy.

Of 228 check added bins under the control-tail configuration: 47 recovered,
136 fail image or movement checks, 39 fall below the control limit, five have
no usable full window, and one is protected by an existing automatic neighbour.
Overlapping failure counts include movement not concentrated at the annotated
boundary (85), small/nonpositive image-derived steps (78), proximity to a
persistent horizontal edge (60), weak profile matches (55), and differing
flank drifts (34). These are diagnostic checks, not proven causal labels.
Distributed movement, shifted annotation timing and tracking instability can
all fail the boundary-concentration condition. Lowering a score threshold
alone cannot resolve those failures. A separate 5–10-interval episode model
and better handling of image-feature changes near tape are still needed.

Reproduce with `python tools/intake_profile_scan.py development`, then
`python tools/intake_profile_scan.py check`, followed by
`python tools/build_intake_profile_scan_html.py`. Frozen manifests, per-cap
all-boundary caches and numeric summaries live under
`multiCAFE/target/intake-profile-scan`. The self-contained report
`intake-profile-scan.html` in the conversation visualization directory includes
six source-image examples: a baseline step, an added bin recovered only by the
tail limit, a bin recovered by both limits, a control failure, an unannotated
proposal, and a missed edited addition. Examples were selected after evaluation,
not sampled randomly. A native-pixel display option separates browser smoothing
from blur already in the source images. Blue selection markers follow the
chosen limit, while both result statuses remain visible.

Six scan-specific checks pass: scalar/vectorized equivalence, boundary timing,
unannotated noise estimation, baseline/neighbour protection, one-to-one recovery
matching and persistent-edge deferral. All 28 report selector combinations run
in the JavaScript smoke check; this does not test browser layout. No Java
detector code, source ZIPs or experimental measures were changed.
