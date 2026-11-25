# Spot measurement model: internal foundation

The shared model is in `plugins.fmp.multitools.experiment.spot.measurement`.
`Spot.getMethodMeasurements()` owns the method results for one spot. This is an
additive first stage: existing acquisition algorithms, CSV persistence, charts,
editing tools and exporters still use the legacy `SpotMeasure` containers.
They are not silently redirected to the new model. Consequently, the new
method results are currently in memory only; existing save/load operations do
not persist them. No experimental file format is changed by this stage.

## Ownership and acquisition

- `SpotMeasurements` contains independent results for filters, colors, vertical
  lines, X lines and rim lines. Re-running a method replaces that method's result.
- `SpotMethodResult` is immutable. Each method owns its timeline, raw fly evidence,
  fly-evidence unit, acquisition parameters, and named measured quantities.
- Times are elapsed seconds from a shared experiment origin, finite and strictly
  increasing. Do not substitute frame numbers for physical times. Different
  methods may have different sampling timelines.
- Each `SpotMeasuredQuantity` stores raw values, a unit and acquisition rejection
  flags. Several quantities may share the method's fly detector. Quantities may
  nevertheless have different validity flags.
- Fly evidence may be pixel counts, occupancy fractions or nonnegative detection
  scores; its unit must identify the interpretation. NaN means unknown. Fly
  evidence alone does not reject an observation: the acquiring method records its
  decision in the quantity's flags, using its own thresholds and sampling geometry.
- NaN raw values are unavailable observations. A NaN with no rejection reason is
  treated as a detection failure. Genuine zero measurements remain valid.
- Manual rejection is stored separately from acquisition flags and can be undone
  without changing the raw measurements. Accepting a baseline is a separate
  decision in the processing settings.
- `SpotMethodRecording` is an acquisition-task-owned builder. Unrecorded samples
  start as NaN/detection failed, including unknown fly evidence. Call `build()`
  after a complete acquisition and publish through `put()`. Built snapshots cannot
  be changed by continuing to edit the builder.

Spot copies with measurements retain immutable method results in separate
registries. Copies without measurements have an empty registry. Existing legacy
normalization cannot mutate these primary results.

## Shared post-processing

`SpotMeasurements.derive(method, quantityId, settings)` uses the common
`SpotSeriesProcessor`. Both future charts and exporters should use this API.
The original values, fly evidence and flags remain unchanged.

Processing order:

1. Mask rejected observations with NaN.
2. Select the baseline from valid original observations, before any filling or smoothing.
3. Classify missing intervals as internal, leading, trailing or entirely missing.
4. Apply the explicitly selected policy to eligible internal occlusion gaps.
5. Optionally apply a running median within finite runs.
6. Optionally divide by the selected baseline.

The default `OBSERVATIONS` settings retain all gaps, disable smoothing and do not
normalize. Internal filling is optional: linear interpolation uses physical time;
hold-previous retains the left bracket until the next observed point. Neither
option changes the right bracket observation. The maximum allowed duration is
the time between the two bracket observations, in seconds. It must be supplied
explicitly. Only intervals whose rejection reason is exclusively occlusion are
eligible. Missing images, failed detections, invalid backgrounds, unknown quality,
manual rejections and mixed causes are not filled by the occlusion policy.

Leading, trailing and entirely missing intervals are never extrapolated. Every
filled sample is marked estimated while retaining its original rejection flags.
Smoothing is separately identified on the result and cannot cross unfilled gaps.
If smoothing follows filling, it can use estimated samples, so a smoothed result
must not be presented as unprocessed observations.

For an internal occlusion, `Gap.connectableForDisplay` indicates whether the
bracket values differ by at most the supplied absolute noise tolerance and meet
the duration limit. This only supplies display metadata: it never fills the
stored NaNs or asserts that consumption did not occur. The caller supplies a
noise tolerance estimated independently in the quantity's original units; the
processor does not infer it from the hidden interval.

Normalization supports none, first valid observation or explicit baseline. It
never selects a global maximum. A delayed first-valid baseline records its real
time and requires review. An explicit positive reference needs a time and source;
it remains provisional until marked reviewed. A wholly missing recording still
requires review even with a reviewed external baseline. A nonpositive observed
baseline produces NaN normalized values and a review flag, never infinity.

Derived results retain settings, observation flags, gap classifications, estimate
markers, baseline value/time/source and review status. Arrays are defensively
copied. An LRU cache retains up to 32 derived series per spot. Replacing or removing
a method invalidates its cached results; raw values and manual decisions cannot
be edited behind the cache because method results are immutable.

## Example for acquisition integration

```java
SpotMethodRecording recording = new SpotMethodRecording(
        SpotMeasurementMethod.COLOR, timesSeconds, "covered_pixels")
        .parameter("fly_threshold", "20")
        .quantity("selected_area", "pixels")
        .quantity("grey_signal", "mean_RGB_intensity");

recording.recordFlyEvidence(i, flyPixelCount);
int flags = occluded ? SpotSampleFlags.OCCLUDED : 0;
recording.record("selected_area", i, rawSelectedPixelCount, flags);
recording.record("grey_signal", i, rawGreySignal, flags);

spot.getMethodMeasurements().put(recording.build());
SpotDerivedSeries observations = spot.getMethodMeasurements().derive(
        SpotMeasurementMethod.COLOR, "grey_signal", SpotProcessingSettings.OBSERVATIONS);
```

Image transforms, color calculations, spatial sampling and fly detection belong
to acquisition. Temporal filling, smoothing and normalization belong to this
derived layer. In particular, acquisition integration must capture measurements
before existing temporal pixel reconstruction or fly gating destroys information.
Old processed series cannot be relabeled as raw measurements, and a historical
shared fly series cannot be duplicated as independent per-method detections.

## Subsequent migration decisions

Acquisition integration and persistence need to be migrated together so newly
captured primary data are not lost at save/reload. The persistence discussion
must settle schema/versioning, independent method timelines, parameter and manual
decision storage, processing preferences, partial method updates and legacy files
that lack original data. Derived caches do not need to be persisted.

UI and export integration follows those decisions. Until migrated, legacy
cleanup behavior is unchanged; adding this API does not change existing results.

## Verification in Eclipse and Maven

The implementation uses Java 8 APIs and JUnit 4, matching the Maven project and
existing Eclipse project configuration. In Eclipse, refresh `multiTools` and run
the following test classes with **Run As > JUnit Test**:

- `SpotMeasurementModelTest`
- `SpotSeriesProcessorTest`
- `SpotMeasurementsPersistenceTest` (existing file-format compatibility)

From the repository root:

```text
mvn -o -pl multiTools -am test -Dtest=SpotMeasurementModelTest,SpotSeriesProcessorTest,SpotMeasurementsPersistenceTest -Dsurefire.failIfNoSpecifiedTests=false
```

These tests exercise physical-time interpolation, endpoint preservation,
occlusion versus other rejection causes, leading/trailing/entire gaps, display-only
connection metadata, baseline review, missing-data defaults, immutability,
manual decisions, method independence, copying and cache invalidation. Image/UI
validation belongs to the later acquisition and UI migrations.
