# Gulp detector development: first experiment

This is an exploratory benchmark, not a third production detection option.
No Java code, dialog defaults, or experiment files were changed.

## What was checked

The index is `F:/2020_Perrine/article_fmp/article1_court/data/CS/list32.txt`.
All 32 directories have root and bin_60 capillary-measure CSV files.
The first experiment is `17-10-26_Quinine/cam01_1mM`.

For that experiment:

- 141 native samples span 0–140 minutes, exactly 60 seconds apart.
- `line11.tiff` is RGB, 141 columns by 479 rows; its width matches `topraw`.
- Cage descriptions confirm controls 0L, 0R, 9L, 9R.
- 1L and most other traces are integer-valued. Some initial samples in 6R,
  7R and 9L are fractional; do not round or silently replace these.
- Root and bin_60 copies differ for those three capillaries. The prototype
  uses the root CSV, matching `Experiment.load_capillaries_description_and_measures()`.
- Existing calibration includes phase geometry, not just the integer
  `cap_npixel` field. The prototype deliberately reports native pixels and
  minutes rather than approximate microlitres.

## Prototype

`gulp_prototype.py` reads CSVs without instantiating the application (whose
loading paths can migrate/write files). It ports the existing
`EvaporationCurveFitter` exponential fit, including its grid refinements and
Huber-weight iterations. For the first experiment the fitted parameters are
A = 16.7361 pixels and tau = 68.1227 samples/minutes.

After evaporation correction, each boundary is assessed with 3, 4 and 6
samples on each side. A local straight line is compared with a straight line
plus a positive step. The score is the square root of the reduction in SSE,
in pixel units. A positive before/after median difference is also required.
The largest score across windows is retained. It is **not** a probability.
At least two observations on each side are required. Adjacent detections
within two samples are suppressed; that is an explicit experimental choice,
not a biological refractory period.

The threshold is the 99.5th percentile of control scores. Controls are scored
against evaporation fitted without themselves. The separate `holdout` results
also exclude the test control from threshold calibration and from every
training evaporation fit. This percentile is a development setting, not a
guaranteed event-level false-positive rate.

The optional image variant fits a sigmoid boundary to G-R and B-R profiles
around each saved meniscus position. Position is sampled at 0.1-pixel spacing;
that spacing does **not** imply 0.1-pixel accuracy. There is no temporal image
smoothing. Fits hitting the center search limit or maximum blur width are
flagged, and windows touching those samples are excluded from scoring and
threshold calibration. The evaporation reference still comes from the saved
traces. This is a preliminary quality gate, not a complete artifact detector.

## Results and decision

For 1L, the saved results mark 15 samples. Recomputing TOPRAW_DY using the
recorded first-experiment mean-plus-3-SD choice reproduces all 15 positions
and amplitudes. Its control-derived threshold is 1.79764 pixels: a one-pixel
positive increment cannot pass.

Both local-step variants return 14 candidate events. The trace-only variant
retains the saved positions except minute 76, which is merged with minute 75.
The image variant also shifts the candidate at minute 84 to minute 85.
Neither variant demonstrates recovery of the smaller missing gulps.
Saved marked samples and prototype candidates have different grouping rules;
their counts must not be interpreted as precision or recall.

In nested holdout on the first experiment, the trace-only variant makes five
false detections: 0L at 13, 27, 33, 114 minutes and 0R at 82 minutes.
The image variant also makes five: 0L at 81 and 0R at 19, 34, 58, 79 minutes.
The image variant evaluates only 93 of 138 possible interior boundaries on
0L because of fit quality exclusions; the other controls have 138, 134 and
134 evaluable boundaries. Thus silence must not be confused with successful
classification when a region is excluded.

The trace-only benchmark ran on 10 of the 32 experiments, with unchanged
windows and percentile but a separately calibrated threshold per experiment.
It produced 54 held-out control candidates across 41 control traces and 5,587
evaluated boundaries. No manually verified feeding labels were available, so
feeding recall and amplitude accuracy were not measured.

The remaining 22 datasets were explicitly skipped by the prototype:

- 18 failed its strictly regular timestamp requirement. For example, the
  third experiment contains 59, 60, 61 and 124-second intervals.
- Two failed its native/non-normalized bin metadata requirement.
- Two had nonfinite, missing or differently sized native topraw data.

These are prototype support limits, not declarations that those datasets are
invalid. The older XDIFFN detector was not rerun; saved derivatives and gulps
are retained, but the comparator here is saved output plus recomputed v2
(fixed mean-plus-3-SD). On other experiments that fixed comparator need not
match their recorded settings.

**Decision:** do not add this local-step implementation to the dialog as an
improved method yet. It has not achieved the desired sensitivity/control
tradeoff. This result refines, rather than confirms, the initial proposal.

Next development should compare short transition models directly in the
image with a smooth-drift model, explicitly representing quantization and
using actual timestamps. It should handle early boundary artifacts, retain
an unscorable state, and avoid a fixed suppression interval that merges
nearby events. Exact labels on the small 1L drops will be needed to quantify
improvement; additional screenshots are not needed to access the source.

## Reproduce

Requires Python with NumPy and Pillow. From the repository root:

```powershell
python tools/gulp_prototype.py --list 'F:/2020_Perrine/article_fmp/article1_court/data/CS/list32.txt' --output '<new-output-directory>' --limit 32
python tools/gulp_prototype.py --list 'F:/2020_Perrine/article_fmp/article1_court/data/CS/list32.txt' --output '<different-output-directory>' --limit 1 --refine-image
python -m unittest discover -s tools -p test_gulp_prototype.py -v
```

Seven numerical tests cover a known evaporation curve, evaporation-only data,
step timing/amplitude, isolated spikes, missing samples, fractional image
boundaries and malformed CSVs. All pass. They establish basic implementation
behavior, not biological validation.

Each output directory contains per-experiment JSON (traces, scores, windows,
candidates and holdout results), counts.csv, inventory.json and a first-
experiment SVG diagnostic. Use a separate output directory for each variant.
Source experiment files are opened read-only. Outputs are developmental and
are not imported into multiCAFE automatically.
