# Local correlation trial

This prototype measures signed image motion and reconstructs a separate raw
meniscus curve. It does not change multiCAFE or any experiment files, and it
does not yet classify additional gulps.

## Data and method

Tested `17-10-26_Quinine/cam01_1mM`: 1L, 1R and confirmed controls 0L, 0R,
9L, 9R. Each has 141 samples at one-minute intervals. The root measurements
are used, matching application loading precedence; the TIFFs are in bin_60.

For each adjacent column pair:

1. Extract 21 vertical samples, centered on the PREVIOUS saved meniscus
   position. The target uses the SAME spatial center, plus a candidate shift.
2. Compute normalized cross-correlation on G-R and B-R profiles. Remove each
   channel's mean and normalize combined energy. This reduces sensitivity to
   neutral intensity changes while retaining the cyan liquid boundary.
3. Search displacements from -6 to +6 pixels in 0.1-pixel increments, with
   linear spatial interpolation and fixed sample count at every displacement.
4. Repeat in reverse, centering on the current saved position. Positive forward
   displacement means downward motion. Use (forward - reverse)/2.
5. Reject pairs at an image/search boundary, low contrast (<2 intensity units),
   weak correlation (<0.90), broad peaks (<0.005 separation from alternatives
   at least one pixel away), or forward/reverse disagreement (>0.75 pixels).

These confidence rules are exploratory. They do not provide calibrated error
probabilities. High correlation cannot prove that the correct feature moved.
Interpolation spacing is not measurement accuracy; the symmetric output can
have 0.05-pixel increments without implying that precision is justified.

The refined curve minimizes squared error against accepted image displacement
constraints plus weak Huber-robust absolute anchors to topraw (weight 0.05,
Huber transition 1 pixel). There is no smoothness or monotonicity penalty.
Rejected pairs have no motion constraint, so their positions are driven by
the saved anchors and any neighboring accepted constraints. Evaporation is
retained in this physical trajectory. Its model is used only for diagnostic
increment comparisons, never to force the refined curve's slope.

## Observations

With a +/-10-pixel window, 825 of 840 adjacent pairs pass the quality rules:
139/140 on 1L, 140 on 1R, 133 on 0L, 137 on 0R, 139 on 9L, and 137 on 9R.

The standard deviation of successive refined-level increments changes as
follows in the empty controls:

- 0R: 0.594 to 0.438 pixels (26% lower).
- 9L: 0.526 to 0.368 pixels (30% lower).
- 9R: 0.677 to 0.455 pixels (33% lower).
- 0L: 0.522 to 0.540 pixels (3% higher).

This is a variation metric, not proof of greater absolute accuracy. Small
negative increments remain, and their count above a 0.25-pixel magnitude can
increase as integer quantization is removed. The procedure does not guarantee
a monotonically descending control curve.

Large 1L steps remain: at minute 34 the correlation shift is 5.25 pixels,
compared with a saved increment of 5 pixels. The anchored increment is 5.29.
The refined curve's RMS difference from saved 1L is 0.338 pixels, with a maximum
adjustment of 0.855 pixels. The result is also stable in a small anchor-weight
check (0.01, 0.05, 0.2): the minute-34 step ranges from 5.27 to 5.30 pixels.

For two smaller 1L increments that are both exactly one pixel in topraw:

- Minute 57: correlation shift 1.40 pixels; anchored shift 1.39 pixels.
- Minute 28: correlation shift 0.20 pixels.

Thus local registration distinguishes image motion that the integer series
does not distinguish. Neither interval is automatically declared a gulp.
The saved data have no exact human labels for these small events.

Unconstrained accumulation is unsafe: the sum of accepted correlation shifts
minus the corresponding raw increments differs by -4.55 pixels on 1R and
-3.73 pixels on 9L. For traces with rejected pairs this is the sum over valid
pieces, not a complete reconstructed trajectory. It demonstrates the need
for absolute anchors, while not establishing which estimate is correct.
The maximum adjustment on 1R is 2.74 pixels, occurring at its beginning;
the diagnostic plots use the SAME saved starting position for both curves
so this correction is visible rather than hidden by independent zeroing.

Windows of +/-5 and +/-15 pixels were also explored. The shorter window has
higher residual displacement variation in all four controls than +/-10; the
larger window provides little additional reduction and rejects more pairs.
These are development observations on one experiment, not held-out validation.

## Outputs and checks

`correlation.json` retains every forward/reverse correlation, signed shift,
quality decision, reason for rejection, original level and refined level.
`refined_levels.csv` contains the same core measurements for inspection and
also the saved gulp amplitudes. `refined_levels.png` compares the six curves.
`small_changes.png` shows unmarked TIFF crops next to raw and correlation
increments. These images are diagnostic plots, not modified source images.

Seven correlation tests pass: signed fractional motion, brightness/contrast
changes, flat profiles/borders, search limits, preservation of a sharp step,
absence of motion evidence, and a synthetic color movie with known motion.
Together with the earlier prototype tests, 14 tests pass. Synthetic tests
verify implementation behavior, not accuracy on biological recordings.

```powershell
python tools/gulp_correlation.py --experiment '<experiment-directory>' --output '<new-output-directory>'
python -m unittest discover -s tools -p 'test_gulp*.py' -v
```

Use `--radius 5` or `--radius 15` for window comparisons and separate output
directories. Requirements: NumPy and Pillow. The current data loader is the
restricted first-prototype loader, requiring native, regularly sampled,
non-normalized kymographs. It is not yet a general batch import mechanism.

The result supports further development of image registration. Next steps
are to investigate 0L's residual fluctuations, evaluate confidence on pale
and artifact-rich images, and test evaporation-corrected motion as a gulp
signal with independently annotated events. The application dialog remains
unchanged until this is validated.
