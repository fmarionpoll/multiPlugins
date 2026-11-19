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
