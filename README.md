# Multi Plugins - Maven Multi-Module Project

This repository contains three related [Icy](https://icy.bioimageanalysis.org/) plugins and libraries for analyzing fly behavior and feeding experiments. Common experiment, image-processing, persistence, charting, and export code is shared through `multiTools`.

## Modules

```
multiPlugins/
|-- pom.xml                    # Parent Maven project
|-- multiTools/                # Shared library
|   `-- src/main/java/plugins/fmp/multitools/
|       |-- experiment/        # Experiment, cage, spot, and capillary models
|       |-- series/            # Image-series and kymograph processing
|       |-- service/           # Detection, tracking, and analysis services
|       |-- tools/             # Charts, Excel export, registration, and utilities
|       |-- transfer/          # Experiment data transfer support
|       `-- workinprogress_gpu/# Experimental GPU code; not production functionality
|-- multiCAFE/                 # Capillary-feeding analysis plugin
|   `-- src/main/java/plugins/fmp/multicafe/
|       |-- dlg/               # Analysis workflows and dialogs
|       `-- viewer1D/          # One-dimensional data viewers
`-- multiSPOTS/                # Liquid-spot feeding analysis plugin
    `-- src/main/java/plugins/fmp/multiSPOTS/
        `-- dlg/               # Analysis workflows and dialogs
```

`multiCAFE` and `multiSPOTS` both depend on `multiTools`. The experimental GPU classes are retained from earlier investigations but are unfinished and are not part of the supported analysis workflow.

## Requirements

- Maven
- A JDK capable of compiling Java 8 source and target bytecode
- Access to the dependency repositories declared in the parent POM

The project currently targets Java 8 for compatibility with Icy 2.5.x installations.

## Building

Build and test all modules from the repository root:

```bash
mvn clean install
```

Build one plugin together with any required upstream modules:

```bash
mvn -pl multiCAFE -am clean install
mvn -pl multiSPOTS -am clean install
```

The generated JAR files are placed in each module's `target/` directory.

## multiCAFE intake exports

Gulps and intakes are independent capillary datasets: `getGulps()` accesses ordinary gulp measures, while `getIntakes()` accesses intake events and their detector settings. Both store double-precision pixel amplitudes indexed by native kymograph time bin. `GULP_BIN_RAW` / `GULP_BIN_CORR` and their `SUM_` variants use gulps; `INTAKE_BIN_RAW` / `INTAKE_BIN_CORR` and their `SUM_` variants use intakes. The computation entry points are `computeGulpBins()` and `computeIntakeBins()`. “Save intake” preserves the gulp dataset; “Replace gulps” explicitly copies intakes into it with `copyIntakesToGulps()`.

The former gulp-derived `INTAKE_BIN_*` results are now named `GULP_BIN_*`; the former `SAVED_INTAKE_*` results are now `INTAKE_BIN_*` (also for `SUM_` variants). Export labels and headers follow these names. Existing experiment files remain compatible: the historical `GULPS_CORR` section and `xcorr` overlay identifiers are retained for intake persistence.

In **Export > Gulps**, select **intake** to export intake alongside ordinary gulps. Save the intake measurements in the intake editor first. This option reads the separate intake; it does not copy intake into ordinary gulps.

CSV exports add `intakeevents.csv`, with raw and evaporation-corrected volume in uL and their cumulative totals. `intakeevents_metadata.csv` records the detector settings and method options, including saved manual edits. Ordinary gulps are consolidated in `gulpevents.csv`, including fractional raw volumes (`gulp_raw_uL`), corrected volumes (`gulp_corr_uL`), and cumulative totals (`sum_gulp_raw_uL`, `sum_gulp_corr_uL`). The redundant `amplitude` column has been removed; use `gulp_raw_uL` for raw gulp volume. These values are included when either **number/bin** or **amplitude/bin** is selected. The separate `legacy_gulps.csv` / `intakebins.csv` export and its checkbox have been removed.

The metadata CSV contains one row per capillary, linked by experiment, cage and capillary IDs; it does not duplicate event rows. CSV event exports automatically include the cage event summary files described below; the **L+R & ratio** checkbox controls Excel summaries. The `measure_cage_raw.csv` and binned `measure_cage_*` files are exported only from the Levels tab. The **amplitude/bin** checkbox controls a separate Excel summary; it is not needed to include volumes in CSV events when **number/bin** is selected.

Both `intakeevents.csv` and `gulpevents.csv` include `interval_start_min` and `interval_end_min`, the timestamps of the preceding and current images on the native measurement timeline. Missing images produce longer intervals. The start is blank if there is no preceding image. When acquisition timestamps are unavailable, the export uses the kymograph timeline. The `frame_index` after `interval_end_min` identifies the ending frame.

The same event exports automatically write `cage_intakeevents.csv` (when **intake** is selected) and `cage_gulpevents.csv` (when **number/bin** or **amplitude/bin** is selected). Each file contains one row per cage and native measurement time, with `experiment_id`, `cage_id`, `time_min`, and `frame_index`. Cumulative capillary volumes are summed per side, then exported as L+R (`sum_intake_raw_uL` / `sum_intake_corr_uL`, or `sum_gulp_raw_uL` / `sum_gulp_corr_uL`) and `(L-R)/(L+R)` (`pi_intake_raw` / `pi_intake_corr`, or `pi_gulp_raw` / `pi_gulp_corr`). Every frame with valid observations for all capillaries in the cage is exported, up to the earliest capillary validity cutoff. Empty gulp/intake datasets contribute explicit zero consumption, including cages with no events at all. Frames after a cutoff or beyond the available observations are not filled with zeros. Missing corrections and ratios with a zero denominator are blank. These files use cumulative event volumes, including times with no new event, and retain the native timeline independently of the analysis bin interval.

Levels and gulp/intake CSVs use the same validity policy. Cage measurements retain observed camera frames up to and including `cage_last_valid_frame`, using the earliest capillary cutoff and requiring finite level observations for every capillary in the cage. No-fly controls are retained, including cages with a single measured side. Capillary level measurements retain frames up to and including their own `cap_last_valid_frame`; paired-choice analyses must additionally apply `cage_last_valid_frame` to those capillary level records. Individual gulp/intake events must satisfy both their capillary cutoff and the common cage observation boundary, so their ending frames are present in the cumulative cage exports. A boundary of `-1` adds no truncation. Internally, `nValid` is exclusive: frame `< nValid` is retained; `nValid = 0` excludes all frames even though the legacy descriptor reports last-valid-frame `0`. Cage SUM values of zero are exported explicitly and their PI is blank. Binned Levels files resample only the retained observations and do not create bins starting after the final retained observation. Independent analysis pipelines should verify descriptor boundaries and cage/frame coverage before performing paired analyses. Previously exported files must be regenerated to apply these rules.

Levels, Gulps, and Fly Positions CSVs that report `time_min` also report the zero-based camera `frame_index` for that time. A binned row has a blank `frame_index` if its start time does not coincide with a measured frame. Select the shared CSV export format and save from the Fly Positions tab to create `flyposition.csv`, with one row per tracked fly and frame.

Excel exports include separate intake raw/corrected and cumulative sheets, an `intake_metadata` sheet, and an `intake_intervals` sheet with both boundaries at native resolution, including zero-intake intervals. Summary sheets still follow the selected export bin size. Corrected values are blank when no evaporation correction is available; raw values remain available.

## Overlaying cage summary measures

In **Charts > Cage summaries**, use **Measures…**, tick the summaries to compare,
then click **Apply**. The same checklist is available in the chart window. It
works in both **Cage** and **Experiment (all cages)** views, and the selection is
retained when switching cages or refreshing. Choosing the ordinary Summary
dropdown returns to a single measure.

Stimulus/concentration colors are retained; line patterns and full legend labels
distinguish measures. Consumption summaries share a spot-equivalent axis. Measures
with different units have separate labelled axes, with consistent ranges across
the cages in the experiment view. Each measure uses its own native timestamps and
aggregate computation; this display does not resample or change saved measures.

## Experimental kymograph analysis

In **Measure: lines > Kymo**, the existing **Analyze** button runs the original
height method. **Analyze new (current)** runs the experimental color integral on
the selected experiment and opens an old/new comparison for each cage or spot.
The original panel is retained as `KymoAnalysisPanel_old`; the stable panel entry
point now extends `KymoAnalysisPanel_new`. The original `CageKymoAnalyzer` remains
unchanged, with explicit `_old` and `_new` entry points alongside it.

Choose a verified period with no feeding for **Baseline (min)**. The initial
10–20 minute selection is a trial default, not an automatically detected baseline.
The new method sums color above the median strip-edge background and divides by
the median of at least three observed baseline columns. `S(diffRGB)` is evaluated
without 8-bit saturation. It does not threshold spot pixels or normalize to a peak.
The insect controls exclude entire contaminated columns; enabling row lift allows
time-weighted interpolation of short bounded gaps in the integral, without RGB
lifting or leading/trailing extrapolation. Negative losses are retained.

Each new cage curve uses a fixed cohort of spots with usable baselines. By default,
the green curve shows the **observed subtotal**, excluding unavailable members;
this is not an estimate of the full cage total. A lower plot shows the number of
missing baseline spots. Line segments are broken whenever the contributing spots
change, including swaps that leave the same count. **Require all baseline spots**
switches back to the complete-cohort total, which is missing if any member is
unobserved after permitted gap interpolation. The comparison also includes the old
curve for the same baseline cohort when exclusions occur. CSVs retain the complete
total and add the observed subtotal, missing count and membership-change flag.
Separate `kymo_analysis_new_spots.csv`, `kymo_analysis_new_cages.csv`, and
`kymo_analysis_new.properties` files are written into the kymograph bin; these trial
measures are not substituted into the standard Charts/Excel measures. This trial
changes analysis only; it does not change drift correction or rebuild source images.

## Eclipse Setup

1. Select **File > Import > Existing Maven Projects**.
2. Choose this repository's root directory (the directory containing the parent `pom.xml`).
3. Import the parent and all three discovered modules.

Keeping the modules in one workspace allows changes in `multiTools` to be resolved immediately by both plugins and supports refactoring across module boundaries.

## Package Naming

- Shared code: `plugins.fmp.multitools.*`
- MultiCAFE-specific code: `plugins.fmp.multicafe.*`
- multiSPOTS-specific code: `plugins.fmp.multiSPOTS.*`

## License

GNU GPLv3. See the Maven project metadata for organization and developer information.
