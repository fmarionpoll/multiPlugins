# Calculate spot fractions

## Frozen design decisions — 26 November 2026

This section records the agreed target workflow and persistence responsibilities.
It takes precedence over the descriptions of the current interface below.
The primary-measurement CSV change is implemented; the interface reorganization,
targeted fraction corrections and fraction-result CSV persistence remain to be built.

### Interface and workflow

The section will have three main tabs:

- **Calculate fractions**, with three numbered buttons opening dedicated panels:
  1. **Find hmax — initial reference**: establish and approve the original,
     unconsumed-deposit reference for each spot, method and measured quantity.
     Here hmax means the initial reference, not the largest observation over the
     recording. Distinguish an obstructed start, pre-consumption, a failed deposit
     and an unresolved reference. A delayed observation cannot recover earlier
     consumption. Borrowed or external references retain their justification.
  2. **Handle occlusions**: apply general rules for internal gaps with similar or
     changed endpoints, initial gaps and final gaps. Make tolerance, maximum gap
     duration and the distinction between measurements and estimates explicit.
  3. **Edit/view fractions**: provide targeted, semi-automatic correction procedures
     for exceptional spots or image ranges, following the correction-toolbox model
     of the multiCAFE Edit tab and the fly-detection Edit tab. This supplements the
     general rules in step 2. It is more than a numerical value editor.
- **Charts**: display individual and aggregated fractions and optional correction
  diagnostics. Move the main fraction chart out of the calculation panel. Share
  the selected experiment, method, quantity and spot with the calculation panels.
- **Load/save**: restore and save fraction results, correction operations and
  calculation decisions. Primary-measurement loading belongs to the respective
  collection subsections; the ambiguous **Load saved** action will be replaced.

The panels share a working state. Users can return to earlier steps without
intermediate saving. Correction tools show their scope and preview, with
**Validate** and **Restore** actions. Validation updates the working result;
writing to disk is a separate Load/save action. Show unsaved changes and provide
tooltips explaining each operation and its consequences. Preserve primary data.
Keep optional drift correction and smoothing in an advanced area.

### Persistence

- Original measurements are one version-1 CSV per acquisition method:
  `results/SpotsPrimaryFilter.csv`, `results/SpotsPrimaryColor.csv`,
  `results/SpotsPrimaryXLine.csv`, `results/SpotsPrimaryRimLine.csv`, and
  `results/bin_xx/SpotsPrimaryVertical.csv`. Camera and line collection, loading
  and batch fraction input use those files. The short-lived primary-measurement
  XML formats, and the recent combined `SpotsPrimaryMeasurements.csv`, are no
  longer read or written; rerun collection to generate the per-method CSVs.
  Existing XML and combined files are left untouched. Cleaned compatibility
  series in `SpotsMeasures.csv` cannot substitute for original measurements.
- Direct-image methods (filters, colors, X-lines and rim-lines) use their own
  primary CSV, fraction settings and future fraction CSV in `results`,
  independently of kymograph bin selection. Only vertical measurements derived
  from kymographs use `SpotsPrimaryVertical.csv` in the corresponding `bin_xx`.
  No relocation is implemented for the recent combined file or for X/rim files
  saved in bins; rerun those measurements. A missing method file means that
  method is unavailable.
- **`SpotsFractionMeasurements.csv`** will store calculated remaining and consumed
  fractions, identified by spot, method, quantity and native timestamp. Preserve
  missing values and distinguish observed contributions from estimated ones.
  The file represents the validated calculation result and is refreshed on
  recalculation; the source observations remain in the primary CSV.
- **`SpotFractionSettings.xml`** may remain the small parameter/decision file for
  processing settings, initial-reference values and their approval/provenance,
  spot overrides and pending review items. XML is acceptable for small settings
  files, not the bulk measurement arrays. Each category has one authoritative
  location rather than independently maintained CSV and XML copies.

The fraction CSV will include a dedicated section for active, validated correction
operations. With no special corrections, that section has no data rows. Each entry
identifies the spot and inclusive image range (`t1`, `t2`; equal for one image),
the structured operation and parameters needed to reapply it, and a readable
comment. Identify method and quantity in the enclosing section or explicit fields.
Use original recording image indices and document the numbering convention.
Generate an operation description automatically and allow a user explanation.
Free text alone is not sufficient to reproduce corrections after recalculation.
Restoring/removing a correction removes its active entry. This is an active
correction list, not a complete historical audit of undone actions.

The exact correction-tool inventory, CSV section layout, image-index base and
ordering/overlap rules for multiple operations still need implementation design.
These details must preserve the responsibilities and behavior agreed above.

## Current implementation (before the planned interface reorganization)

Measurement collection and conversion to fractions are now separate stages.
The three method panels are labelled **Collect measurements**. The new
**Calculate spot fractions** section opens a resizable calculation workspace.
Collection chart menus now show only primary measurements with gaps and
method-specific fly presence. Legacy result identifiers remain available to
compatibility builders and exports; exports have not yet been switched to these
fraction calculations.

## Starting with one experiment

1. Save spot definitions and annotations, and collect primary measurements with
   filters, colors, vertical lines, X lines or rim lines. Historical cleaned CSVs
   cannot substitute for the original measurements; reanalysis is necessary if
   the primary-data sidecar is missing.
   Each method has its own primary CSV: filter, color, X-line and rim-line files
   in `results`, and `SpotsPrimaryVertical.csv` in the selected bin. The recent
   combined CSV and measurement XML files are ignored; rerun collection to
   generate the per-method CSVs. Small calculation decisions remain in
   `SpotFractionSettings.xml`.
2. Open the calculation workspace and choose the method and measured quantity.
   The experimental vertical color integral can be selected separately; it reads
   the trial primary sidecar without replacing standard vertical measurements.
3. Use **Load saved** to recover the experiment's choices and calculate from them.
   **Preview current** applies the controls without writing files.
4. Inspect the spot list and fraction chart. The main display offers fraction
   remaining and fraction consumed. Observations are solid; estimates and optional
   display-only connections are dashed. Missing values and negative apparent
   consumption are preserved. Drift factors and supporting counts are on a
   separate **Correction diagnostics** tab. Inspect measured values and fly
   presence from the collection chart panels.
5. Resolve initial references where necessary. Clicking a valid positive chart
   sample fills the reference fields from its original measurement, not from the
   plotted normalized or reconstructed value. Approve it, enter a justified
   external reference, choose comparable donors, or identify a failed deposit.
   Reference decisions are staged until saved.
6. Use **Calculate & save current** to persist processing choices, references and
   pending review items. Primary values are not modified.

## Charts by processing stage

The collection chart panels contain one acquisition-only selector. Filters and
colors expose their native signal/area measures and fly evidence; the line family
exposes vertical height/coverage, X coverage and rim coverage with independent
fly traces for each geometry. The terms "coverage" and "row coverage" here name
direct image measurements, not normalization to the initial spot. Rejected values
are null samples in the plots, including internal and edge occlusions. Native
method timestamps and units are preserved. There are no smoothing, baseline,
relative-to-maximum or aggregate controls in these panels. Use experiment, cage
or selected-spot scope, with optional matching-color dashed fly traces on a second
axis. Primary data are required: cleaned legacy series are not substituted.

**Aggregated fractions** belongs to the calculation workspace. It offers sums
(spot equivalents) and means (fractions) of remaining or consumed signal by cage,
stimulus and concentration. Each group's membership is fixed to the configured
spots, excluding explicitly failed deposits. Missing primary data, missing
observations and unapproved or stale references do not silently drop members:
the complete total and mean remain missing until every expected member contributes.
An aligned lower plot shows available and expected counts. Values align at exact
native timestamps; no resampling or interpolation is introduced by aggregation.
Estimates from filling or borrowed initial references are marked. Visual-only
connections never count as measured or calculated contributions. Spots without
an assigned cage are not pooled into an invented group.

The fraction conversion is explicitly linear: remaining = signal / reference,
consumed = 1 - remaining. It is a fraction inferred from the selected image
quantity, not an independently calibrated volume measurement. Different
quantities retain separate references and settings.

## Reference handling

- A positive valid first observation establishes a fixed initial reference. A
  global maximum never replaces it, and smoothing never changes it.
- When the first usable view occurs later, the proposed reference is delayed and
  requires approval. Its actual time and source remain visible even after
  approval. Consumption before that reference is not recovered.
- If the first clear view shows zero or a negative value, a later positive
  deposit does not become the automatic baseline. This case stays on the review
  list until explained.
- An existing pre-consumed flag authorizes an estimated reference from the median
  of valid, approved initial observations of comparable spots in the same cage,
  stimulus and concentration. Donor names are saved. Without suitable donors or
  a known cage, review is required. The legacy untyped grey reference is not
  reused across quantities with different units.
- Manual borrowing shows donor references and conditions before selection.
  Failed deposits are excluded, not converted to apparent consumption.
- Source fingerprints include primary values, validity, timestamps, acquisition
  parameters and relevant spot metadata. Changes to these or donor measurements
  require reapproval rather than silently changing an established reference.

## Gap and drift controls

The starting controls explicitly show linear interpolation for bracketed gaps,
an 8% tolerance relative to the fixed reference, a ten-minute maximum bracket
duration, no smoothing, and missing trailing values. These are starting choices
for new calculations, not changes to previously saved experiments.

Similar endpoints and changing endpoints have independent fill policies. A gap
is eligible only when it consists solely of occluded observations with usable
brackets and meets the maximum duration. Missing images, failed detections and
mixed rejection reasons are not silently treated as visits. Filled samples keep
their original flags and estimate markers. Changing intervals remain marked.
Leading intervals are never extrapolated. A final gap can stay missing, be held
from its last valid value as an explicit estimate, or request review.

Tolerance can use a percentage of the reference, absolute measurement units, or
three sigma estimated from the MAD of adjacent valid differences (at least five
pairs). Optional visual connections never change the underlying NaNs. Optional
median smoothing operates within finite runs and does not establish a baseline.

The optional common-change correction is multiplicative and **off by default**.
It estimates per-interval median ratios from aligned, valid, positive measurements
with approved unchanged references. It assumes at most two changing signals per
cage per interval, including any consumption or deposits. At least five valid
contributors are required for one cage; experiment-wide correction requires a
strict majority even after allowing two changing signals per participating cage
(4 × cages + 1 contributors). Values below 5% of their initial reference are
excluded from ratio estimation. Initial factors are anchored at one. Insufficient
support makes the subsequent absolute correction unknown and adds a review item;
the previous factor is not silently carried forward. Reference values observed
later are corrected at their own reference time. Inspect factors and supporting
counts, and validate this optional estimator on representative recordings before
using it as a biological correction. Additive or method-specific nonlinear
illumination effects are not modelled by this estimate.

## Many experiments and spot exceptions

Settings are saved per experiment, method and measured quantity. Selecting
**Settings for selected spot only** creates a local override. Removing that
override restores inheritance from the experiment's settings. Batch application
updates experiment settings and preserves existing spot overrides and references.

Choose an inclusive, 1-based experiment range and use **Apply & save range**.
Only controls are transferred; approved references are never copied between
experiments. Each experiment is read from saved descriptions and primary series,
calculated and saved independently. No images or full lazy experiments are
loaded. Unresolved cases do not stop the batch. Errors are reported per experiment.
**Stop after current experiment** retains completed work and allows a later range
to resume processing.

**Saved review list** scans the selected experiments' calculation files, including
the active vertical-kymograph bin, without loading primary series or images. These are
the pending items from the last saved calculation; newly acquired or changed data
need recalculation to refresh them. Open an item to recover its method, quantity
and spot, and use **Locate spot** with the existing image viewer when needed.
Loading only a subset of experiments does not alter the others' saved decisions.

## Persistence and verification

`SpotFractionSettings.xml` is a versioned XML properties file beside the primary
measurements: in the results directory for filters/colors/X-lines/rim-lines and
in the selected bin for vertical kymograph measurements. It contains experiment settings, spot overrides, reference
values/times/sources/donors/approvals/fingerprints, and unresolved review reasons.
Derived curves are recomputed. Writes use a temporary file and atomic replacement
where supported, and reject unsupported or unreadable existing files. Saving one
method/quantity preserves the others. Trial and standard quantities remain
separate.

The shared API is `SpotFractionCalculator.calculate(inputs, selection, plan)`.
It is independent of Swing and available to the later export integration.
`SpotFractionData` loads batch input without images. `SpotFractionStore` reads and
writes calculation decisions independently of raw acquisition.

In Eclipse, run these JUnit 4 classes:

- `SpotFractionCalculatorTest`
- `SpotFractionStoreTest`
- `CalculateSpotFractionsPanelTest`
- `SpotSeriesProcessorTest`

Tests cover reference review, source changes, donor provenance, pre-consumed and
failed deposits, quiet/changing/internal/trailing gaps, raw immutability, negative
consumption, drift support and two-changing-spot cases, persistence, local settings,
and charts that distinguish estimates and display-only connections. Native Icy
workspace/viewer interaction and validation against biological recordings still
need testing in the application.
